package fr.orbit.extraction.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.orbit.extraction.util.Text;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * Pack de ressources d'Orbit Extraction (modèles 3D des armes à feu et de la grenade).
 * Le pack est embarqué dans le jar ({@code pack.zip}, fabriqué par build.ps1 à partir du dossier pack\)
 * et servi par un petit serveur HTTP intégré, sauf si une URL publique est configurée.
 * Il est proposé pendant la phase de configuration (écran de chargement).
 */
public final class ResourcePackService implements Listener {

    private static final long WAIT_SECONDS = 60;

    private final JavaPlugin plugin;

    private volatile boolean enabled;
    private byte[] zip;
    private String hash;
    private UUID packId;
    private int port;
    private String publicUrl;
    private boolean required;
    private HttpServer http;
    private ExecutorService httpPool;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();

    public ResourcePackService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("resource-pack.enabled", true)) {
            plugin.getLogger().info("Pack de ressources désactivé (resource-pack.enabled) : les armes gardent l'apparence de leur objet de base.");
            return;
        }
        port = c.getInt("resource-pack.port", 8791);
        publicUrl = c.getString("resource-pack.public-url", "").trim();
        required = c.getBoolean("resource-pack.required", false);
        try (InputStream in = plugin.getResource("pack.zip")) {
            if (in == null) {
                plugin.getLogger().warning("pack.zip absent du jar (lance tools\\gen-pack.ps1 puis build.ps1) : pack de ressources désactivé.");
                return;
            }
            zip = in.readAllBytes();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Lecture de pack.zip impossible", e);
            return;
        }
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        packId = UUID.nameUUIDFromBytes(("orbitextraction:" + hash).getBytes(StandardCharsets.UTF_8));
        if (publicUrl.isEmpty() && !startHttp()) {
            return;
        }
        enabled = true;
        plugin.getLogger().info("Pack de ressources prêt (" + (zip.length / 1024) + " Ko, sha1 " + hash.substring(0, 12) + ") — "
                + (publicUrl.isEmpty() ? "servi sur le port " + port : "URL publique " + publicUrl) + ".");
    }

    private boolean startHttp() {
        try {
            http = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            plugin.getLogger().warning("Port " + port + " indisponible pour le pack de ressources (" + e.getMessage()
                    + ") : change resource-pack.port ou renseigne resource-pack.public-url.");
            return false;
        }
        http.createContext("/", this::serve);
        httpPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "OrbitExtraction-pack-http");
            t.setDaemon(true);
            return t;
        });
        http.setExecutor(httpPool);
        http.start();
        return true;
    }

    private void serve(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                ex.sendResponseHeaders(405, -1);
                return;
            }
            ex.getResponseHeaders().set("Content-Type", "application/zip");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            if ("HEAD".equalsIgnoreCase(method)) {
                ex.getResponseHeaders().set("Content-Length", String.valueOf(zip.length));
                ex.sendResponseHeaders(200, -1);
                return;
            }
            ex.sendResponseHeaders(200, zip.length);
            try (OutputStream body = ex.getResponseBody()) {
                body.write(zip);
            }
        } finally {
            ex.close();
        }
    }

    public void shutdown() {
        enabled = false;
        if (http != null) {
            http.stop(0);
            http = null;
        }
        if (httpPool != null) {
            httpPool.shutdownNow();
            httpPool = null;
        }
    }

    public boolean enabled() {
        return enabled;
    }

    // ------------------------------------------------------------------ Envoi

    private String url(String host) {
        String v = hash.substring(0, 12);
        if (!publicUrl.isEmpty()) {
            return publicUrl + (publicUrl.contains("?") ? "&" : "?") + "v=" + v;
        }
        return "http://" + host + ":" + port + "/orbitextraction-" + v + ".zip";
    }

    /** Adresse utilisée par le joueur pour se connecter : le pack est servi par la même machine. */
    private static String host(InetSocketAddress virtualHost) {
        if (virtualHost == null) {
            return "localhost";
        }
        String h = virtualHost.getHostString();
        if (h == null) {
            return "localhost";
        }
        int nul = h.indexOf('\0');
        if (nul >= 0) {
            h = h.substring(0, nul);
        }
        if (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        if (h.isEmpty() || !h.matches("[A-Za-z0-9.:\\-]+")) {
            return "localhost";
        }
        return h.contains(":") ? "[" + h + "]" : h;
    }

    private ResourcePackRequest request(String host, CompletableFuture<Void> done, UUID player) {
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(packId, URI.create(url(host)), hash);
        return ResourcePackRequest.resourcePackRequest()
                .packs(info)
                // Par-dessus le pack du lobby (envoyé avant, en LOWEST, qui retire ceux des serveurs précédents).
                .replace(false)
                .required(required)
                .prompt(Text.mm("<#C0392B><b>Orbit Extraction</b></#C0392B>\n<#D7DCE2>Armes à feu en 3D, infectés, écrans de la tablette. "
                        + "Sans le pack, les armes ressemblent à des armures de cheval et certains infectés n'ont pas de texture."))
                .callback((id, status, audience) -> {
                    if (status == ResourcePackStatus.SUCCESSFULLY_LOADED) {
                        loaded.add(player);
                    } else if (!status.intermediate()) {
                        loaded.remove(player);
                    }
                    if (!status.intermediate() && done != null) {
                        done.complete(null);
                    }
                })
                .build();
    }

    /** Vrai si ce joueur a chargé le pack : les menus texturés de la tablette ne s'affichent que dans ce cas. */
    public boolean isLoaded(UUID player) {
        return enabled && loaded.contains(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        loaded.remove(e.getPlayer().getUniqueId());
    }

    /** Phase de configuration : le pack est chargé pendant l'écran de connexion. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        if (!enabled) {
            return;
        }
        PlayerConfigurationConnection conn = event.getConnection();
        CompletableFuture<Void> done = new CompletableFuture<>();
        conn.getAudience().sendResourcePacks(request(host(conn.getVirtualHost()), done, conn.getProfile().getId()));
        try {
            done.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            plugin.getLogger().info("Pas de réponse au pack de ressources pour " + conn.getProfile().getName()
                    + " après " + WAIT_SECONDS + " s : connexion poursuivie.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ignored) {
            // jamais complété exceptionnellement
        }
    }

    /** Renvoi à un joueur déjà connecté (/extraction pack). */
    public void send(Player p) {
        if (!enabled) {
            Text.msg(p, "<#C0392B>Le pack de ressources est désactivé sur ce serveur.");
            return;
        }
        p.sendResourcePacks(request(host(p.getVirtualHost()), null, p.getUniqueId()));
    }
}
