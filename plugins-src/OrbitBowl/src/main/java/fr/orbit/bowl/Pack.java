package fr.orbit.bowl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Pack d'ORBIT BOWL (quilles, boules, fléchettes, cibles, logo…), ajouté par-dessus celui du lobby
 * (replace = false) et servi par un serveur HTTP intégré (port pack.port, 8800). Sans le pack, les objets gardent
 * leur apparence d'origine : les icônes ne sont posées que pour les joueurs qui l'ont chargé.
 */
public final class Pack implements Listener {

    private final OrbitBowl pl;
    private final Set<UUID> charges = ConcurrentHashMap.newKeySet();
    private final Set<UUID> demandes = ConcurrentHashMap.newKeySet();
    private volatile boolean actif;
    private byte[] zip;
    private String hash;
    private UUID id;
    private int port;
    private HttpServer http;
    private ExecutorService pool;

    Pack(OrbitBowl pl) {
        this.pl = pl;
    }

    void activer() {
        if (!pl.getConfig().getBoolean("pack.actif", true)) {
            return;
        }
        port = pl.getConfig().getInt("pack.port", 8800);
        try (InputStream in = pl.getResource("pack.zip")) {
            if (in == null) {
                pl.getLogger().warning("pack.zip absent du jar : icônes d'origine.");
                return;
            }
            zip = in.readAllBytes();
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip));
        } catch (Exception e) {
            pl.getLogger().warning("Pack d'ORBIT BOWL illisible : " + e.getMessage());
            return;
        }
        id = UUID.nameUUIDFromBytes(("orbitbowl:" + hash).getBytes(StandardCharsets.UTF_8));
        try {
            http = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            pl.getLogger().warning("Port " + port + " indisponible pour le pack d'ORBIT BOWL : " + e.getMessage());
            return;
        }
        http.createContext("/", this::servir);
        pool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "OrbitBowl-pack-http");
            t.setDaemon(true);
            return t;
        });
        http.setExecutor(pool);
        http.start();
        actif = true;
        pl.getLogger().info("Pack d'ORBIT BOWL prêt (" + zip.length / 1024 + " Ko, sha1 " + hash.substring(0, 12) + ") sur le port " + port + ".");
    }

    private void servir(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }
            ex.getResponseHeaders().set("Content-Type", "application/zip");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, zip.length);
            try (OutputStream body = ex.getResponseBody()) {
                body.write(zip);
            }
        } finally {
            ex.close();
        }
    }

    void arreter() {
        actif = false;
        if (http != null) {
            http.stop(0);
        }
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    public boolean charge(Player p) {
        return actif && charges.contains(p.getUniqueId());
    }

    private static String hote(InetSocketAddress virtualHost) {
        String h = virtualHost == null ? null : virtualHost.getHostString();
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

    private ResourcePackRequest requete(UUID joueur, String hote, CompletableFuture<Void> fini) {
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(id,
                URI.create("http://" + hote + ":" + port + "/orbit-bowl-" + hash.substring(0, 12) + ".zip"), hash);
        return ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .replace(false)
                .required(false)
                .prompt(Txt.mm("<#62CFF4><b>ORBIT</b></#62CFF4>\n<#D7DCE2>Quilles, boules et décors d'ORBIT BOWL."))
                .callback((pid, status, audience) -> {
                    if (status.intermediate()) {
                        return;
                    }
                    if (status == ResourcePackStatus.SUCCESSFULLY_LOADED) {
                        charges.add(joueur);
                    } else {
                        charges.remove(joueur);
                    }
                    if (fini != null) {
                        fini.complete(null);
                    }
                })
                .build();
    }

    /** Après le pack du lobby (LOWEST, qui remplace tout) et celui du casino (HIGH) : ORBIT BOWL s'ajoute par-dessus. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        if (!actif) {
            return;
        }
        PlayerConfigurationConnection conn = event.getConnection();
        UUID joueur = conn.getProfile().getId();
        if (joueur == null) {
            return;
        }
        demandes.add(joueur);
        CompletableFuture<Void> fini = new CompletableFuture<>();
        conn.getAudience().sendResourcePacks(requete(joueur, hote(conn.getVirtualHost()), fini));
        try {
            fini.get(30, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // pas de réponse : connexion poursuivie, icônes d'origine
        }
    }

    /** Joueur arrivé avant l'installation du plugin : pack proposé une fois quand il rejoint une file. */
    void envoyerSiBesoin(Player p) {
        if (actif && !charges.contains(p.getUniqueId()) && demandes.add(p.getUniqueId())) {
            p.sendResourcePacks(requete(p.getUniqueId(), hote(p.getVirtualHost()), null));
        }
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        if (id == null || !id.equals(e.getID())) {
            return;
        }
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> charges.add(e.getPlayer().getUniqueId());
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> charges.remove(e.getPlayer().getUniqueId());
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        charges.remove(e.getPlayer().getUniqueId());
        demandes.remove(e.getPlayer().getUniqueId());
    }
}
