package fr.orbit.chasseurs;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import org.bukkit.Bukkit;
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
 * Pack des Chasseurs (écran de table HD), ajouté par-dessus celui du lobby pendant la connexion (replace = false).
 * Servi par un petit serveur HTTP intégré (port casino.pack-port, 8793 par défaut). Sans le pack, les tables
 * gardent les boutons du chat.
 */
final class Pack implements Listener {

    private final OrbitChasseurs plugin;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private volatile boolean enabled;
    private byte[] zip;
    private String hash;
    private UUID packId;
    private int port;
    private HttpServer http;
    private ExecutorService pool;

    Pack(OrbitChasseurs plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        port = plugin.getConfig().getInt("pack-port", 8799);
        try (InputStream in = plugin.getResource("pack.zip")) {
            if (in == null) {
                plugin.getLogger().warning("pack.zip absent du jar : vaisseaux sans modèle 3D.");
                return;
            }
            zip = in.readAllBytes();
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip));
        } catch (Exception e) {
            plugin.getLogger().warning("Pack des Chasseurs illisible : " + e.getMessage());
            return;
        }
        packId = UUID.nameUUIDFromBytes(("orbitchasseurs:" + hash).getBytes(StandardCharsets.UTF_8));
        bind(true);
    }

    /** Ouvre le port du pack. S'il est pris (autre serveur lancé à côté), on réessaie toutes les 10 s : le pack arrive dès qu'il se libère. */
    private void bind(boolean first) {
        if (enabled || !plugin.isEnabled()) {
            return;
        }
        try {
            http = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            if (first) {
                plugin.getLogger().warning("Port " + port + " indisponible pour le pack des Chasseurs (" + e.getMessage() + ") : nouvel essai toutes les 10 s.");
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> bind(false), 200L);
            return;
        }
        http.createContext("/", this::serve);
        pool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "OrbitChasseurs-pack-http");
            t.setDaemon(true);
            return t;
        });
        http.setExecutor(pool);
        http.start();
        enabled = true;
        plugin.getLogger().info("Pack des Chasseurs prêt (" + zip.length / 1024 + " Ko, sha1 " + hash.substring(0, 12) + ") sur le port " + port + ".");
        for (Player p : Bukkit.getOnlinePlayers()) {
            send(p);
        }
    }

    private void serve(HttpExchange ex) throws IOException {
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

    public void shutdown() {
        enabled = false;
        if (http != null) {
            http.stop(0);
        }
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    public boolean isLoaded(Player p) {
        return enabled && loaded.contains(p.getUniqueId());
    }

    private static String host(InetSocketAddress virtualHost) {
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

    private ResourcePackRequest request(UUID player, String host, CompletableFuture<Void> done) {
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(packId,
                URI.create("http://" + host + ":" + port + "/orbit-chasseurs-" + hash.substring(0, 12) + ".zip"), hash);
        return ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .replace(false)
                .required(false)
                .prompt(Txt.mm("<#3A7FFF><b>ORBIT</b></#3A7FFF>\n<#D7DCE2>Vaisseaux de Chasseurs d'Orbite."))
                .callback((id, status, audience) -> {
                    if (status.intermediate()) {
                        return;
                    }
                    if (status == ResourcePackStatus.SUCCESSFULLY_LOADED) {
                        loaded.add(player);
                    } else {
                        loaded.remove(player);
                    }
                    if (done != null) {
                        done.complete(null);
                    }
                })
                .build();
    }

    /** Après le pack du lobby (priorité LOWEST, qui remplace tout) : le casino s'ajoute par-dessus. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        if (!enabled) {
            return;
        }
        PlayerConfigurationConnection conn = event.getConnection();
        UUID player = conn.getProfile().getId();
        if (player == null) {
            return;
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        conn.getAudience().sendResourcePacks(request(player, host(conn.getVirtualHost()), done));
        try {
            done.get(30, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // pas de réponse : connexion poursuivie, les tables passeront par le chat
        }
    }

    public void send(Player p) {
        if (enabled) {
            p.sendResourcePacks(request(p.getUniqueId(), host(p.getVirtualHost()), null));
        }
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        if (packId == null || !packId.equals(e.getID())) {
            return;
        }
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.add(e.getPlayer().getUniqueId());
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> loaded.remove(e.getPlayer().getUniqueId());
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        loaded.remove(e.getPlayer().getUniqueId());
    }
}
