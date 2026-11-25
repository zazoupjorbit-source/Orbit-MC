package fr.orbit.packrelay;

import com.google.inject.Inject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerResourcePackSendEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import org.slf4j.Logger;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * Relais des packs de ressources pour les joueurs qui passent par le tunnel playit.
 *
 * Chaque serveur du réseau sert son pack sur un port local (8790-8799) que le tunnel playit ne relaie pas.
 * Ce plugin :
 *  1. ouvre un petit relais HTTP sur 127.0.0.1:8780 : /<port>/<chemin> → http://127.0.0.1:<port>/<chemin> ;
 *  2. lance un tunnel Cloudflare gratuit vers ce relais, le surveille et le relance s'il tombe ;
 *  3. réécrit l'adresse de chaque pack envoyé à un joueur qui n'est PAS connecté en local
 *     (lobby, casino, arcade, MMO, vol, tycoon…) vers https://<tunnel>/<port>/<chemin>.
 * L'adresse du tunnel peut changer : les packs suivants utilisent la nouvelle, sans redémarrer de serveur.
 */
@Plugin(id = "orbitpackrelay", name = "OrbitPackRelay", version = "1.0", authors = {"ORBIT"})
public final class PackRelay {

    private static final int RELAY_PORT = 8780;
    private static final int PORT_MIN = 8790, PORT_MAX = 8799;
    /** Packs déjà publiés par un ancien tunnel (public-url) : nom du fichier → port local. */
    private static final Map<String, Integer> NAMED = Map.of(
            "orbitmmo", 8790, "orbitextraction", 8791, "orbittycoon", 8794, "orbitvol", 8795);
    private static final Pattern TUNNEL_URL = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");

    private final ProxyServer proxy;
    private final Logger log;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private HttpServer http;
    private volatile Process cloudflared;
    private volatile String publicBase;
    private volatile boolean running = true;
    private ScheduledExecutorService watch;
    private int fails;

    @Inject
    public PackRelay(ProxyServer proxy, Logger log) {
        this.proxy = proxy;
        this.log = log;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent e) {
        try {
            http = HttpServer.create(new InetSocketAddress("127.0.0.1", RELAY_PORT), 32);
            http.createContext("/", this::relay);
            http.setExecutor(Executors.newFixedThreadPool(8));
            http.start();
            log.info("Relais des packs ouvert sur 127.0.0.1:{}", RELAY_PORT);
        } catch (IOException ex) {
            log.error("Relais des packs : port {} occupé, rien ne sera relayé", RELAY_PORT, ex);
            return;
        }
        watch = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "OrbitPackRelay-watch");
            t.setDaemon(true);
            return t;
        });
        watch.execute(this::startTunnel);
        watch.scheduleWithFixedDelay(this::check, 90, 60, TimeUnit.SECONDS);
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent e) {
        running = false;
        if (watch != null) watch.shutdownNow();
        stopTunnel();
        if (http != null) http.stop(0);
    }

    // ---------------------------------------------------------------- réécriture des packs

    @Subscribe
    public void onPack(ServerResourcePackSendEvent e) {
        Player p = e.getServerConnection().getPlayer();
        String vhost = p.getVirtualHost().map(InetSocketAddress::getHostString).orElse("");
        if (isLocal(vhost)) return;
        ResourcePackInfo pack = e.getProvidedResourcePack();
        String base = publicBase;
        if (base == null) {
            log.warn("Pack pour {} non relayé : tunnel pas encore prêt ({})", p.getUsername(), pack.getUrl());
            return;
        }
        String rewritten = rewrite(pack.getUrl(), base);
        if (rewritten == null) return;
        e.setProvidedResourcePack(pack.asBuilder(rewritten).build());
        log.info("Pack de {} relayé : {} -> {}", p.getUsername(), pack.getUrl(), rewritten);
    }

    private static boolean isLocal(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return h.isEmpty() || h.equals("localhost") || h.startsWith("127.") || h.equals("::1") || h.equals("0:0:0:0:0:0:0:1");
    }

    static String rewrite(String url, String base) {
        URI u;
        try {
            u = URI.create(url);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (u.getHost() == null) return null;
        String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
        String query = u.getRawQuery() == null ? "" : "?" + u.getRawQuery();
        int port = u.getPort();
        if (u.getHost().endsWith(".trycloudflare.com")) {
            if (url.startsWith(base)) return null; // déjà sur le bon tunnel
            port = -1;
            String file = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            for (Map.Entry<String, Integer> n : NAMED.entrySet())
                if (file.startsWith(n.getKey())) port = n.getValue();
        }
        if (port < PORT_MIN || port > PORT_MAX) return null;
        return base + "/" + port + path + query;
    }

    // ---------------------------------------------------------------- relais HTTP

    private void relay(HttpExchange ex) throws IOException {
        try (ex) {
            String raw = ex.getRequestURI().getRawPath();
            String query = ex.getRequestURI().getRawQuery();
            if (raw.equals("/sante")) {
                byte[] ok = "ok".getBytes();
                ex.sendResponseHeaders(200, ok.length);
                ex.getResponseBody().write(ok);
                return;
            }
            Matcher m = Pattern.compile("^/(\\d{4})(/.*)?$").matcher(raw);
            int port = m.matches() ? Integer.parseInt(m.group(1)) : -1;
            if (port < PORT_MIN || port > PORT_MAX || !ex.getRequestMethod().equals("GET") && !ex.getRequestMethod().equals("HEAD")) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            String rest = m.group(2) == null ? "/" : m.group(2);
            URI target = URI.create("http://127.0.0.1:" + port + rest + (query == null ? "" : "?" + query));
            HttpResponse<InputStream> r;
            try {
                r = client.send(HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(30)).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
            } catch (Exception err) {
                ex.sendResponseHeaders(502, -1);
                return;
            }
            try (InputStream in = r.body()) {
                r.headers().firstValue("Content-Type").ifPresent(v -> ex.getResponseHeaders().set("Content-Type", v));
                long len = r.headers().firstValueAsLong("Content-Length").orElse(0);
                boolean head = ex.getRequestMethod().equals("HEAD");
                ex.sendResponseHeaders(r.statusCode(), head ? -1 : len);
                if (!head) in.transferTo(ex.getResponseBody());
            }
        }
    }

    // ---------------------------------------------------------------- tunnel Cloudflare

    private Path cloudflaredExe() {
        Path here = Paths.get("").toAbsolutePath();
        for (Path p : List.of(here.resolve("../tools/cloudflared.exe"), here.resolve("tools/cloudflared.exe")))
            if (Files.isRegularFile(p)) return p.normalize();
        return null;
    }

    private synchronized void startTunnel() {
        if (!running) return;
        stopTunnel();
        Path exe = cloudflaredExe();
        if (exe == null) {
            log.error("cloudflared.exe introuvable (MinecraftDev\\tools) : packs non relayés");
            return;
        }
        try {
            Process pr = new ProcessBuilder(exe.toString(), "tunnel", "--no-autoupdate",
                    "--url", "http://127.0.0.1:" + RELAY_PORT).redirectErrorStream(true).start();
            cloudflared = pr;
            fails = 0;
            Path logFile = exe.getParent().resolve("cloudflared-relais.log");
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(pr.getInputStream()));
                     PrintWriter out = new PrintWriter(Files.newBufferedWriter(logFile,
                             StandardOpenOption.CREATE, StandardOpenOption.APPEND), true)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        out.println(line);
                        Matcher m = TUNNEL_URL.matcher(line);
                        if (m.find() && !m.group().equals(publicBase)) {
                            publicBase = m.group();
                            log.info("Tunnel des packs : {}", publicBase);
                            Files.writeString(exe.getParent().resolve("pack-relais-url.txt"), publicBase);
                        }
                    }
                } catch (IOException ignored) {
                }
            }, "OrbitPackRelay-cloudflared");
            reader.setDaemon(true);
            reader.start();
            log.info("cloudflared lancé (journal : {})", logFile);
        } catch (IOException ex) {
            log.error("Impossible de lancer cloudflared", ex);
        }
    }

    private synchronized void stopTunnel() {
        Process pr = cloudflared;
        cloudflared = null;
        publicBase = null;
        if (pr != null) {
            pr.descendants().forEach(ProcessHandle::destroyForcibly);
            pr.destroyForcibly();
        }
    }

    private void check() {
        if (!running) return;
        Process pr = cloudflared;
        if (pr == null || !pr.isAlive()) {
            log.warn("cloudflared arrêté : relance");
            startTunnel();
            return;
        }
        String base = publicBase;
        if (base == null) {
            if (++fails >= 3) startTunnel();
            return;
        }
        try {
            HttpResponse<String> r = client.send(HttpRequest.newBuilder(URI.create(base + "/sante"))
                    .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) {
                fails = 0;
                return;
            }
        } catch (Exception ignored) {
        }
        if (++fails >= 3) {
            log.warn("Tunnel des packs injoignable 3 fois de suite : relance");
            startTunnel();
        }
    }
}
