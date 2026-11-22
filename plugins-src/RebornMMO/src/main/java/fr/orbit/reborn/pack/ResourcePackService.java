package fr.orbit.reborn.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.orbit.reborn.RebornMMO;
import fr.orbit.reborn.util.Text;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
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
 * Pack de ressources de RebornMMO (armes, cristaux, HUD de la barre de vie).
 * Le pack est embarqué dans le jar ({@code pack.zip}) et servi par un petit serveur HTTP intégré,
 * sauf si une URL publique est configurée. Il est proposé pendant la phase de configuration
 * (écran de chargement), avant l'arrivée du joueur dans le monde.
 */
public final class ResourcePackService implements Listener {

    private static final long WAIT_SECONDS = 60;

    private final RebornMMO plugin;
    /** Joueurs dont le client a bien chargé le pack (le HUD dessiné n'est utilisé que pour eux). */
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();

    private volatile boolean enabled;
    private byte[] zip;
    private String hash;
    private UUID packId;
    private int port;
    private String publicUrl;
    private boolean required;
    private HttpServer http;
    private ExecutorService httpPool;

    public ResourcePackService(RebornMMO plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("resource-pack.enabled", true)) {
            plugin.getLogger().info("Pack de ressources désactivé (resource-pack.enabled).");
            return;
        }
        port = c.getInt("resource-pack.port", 8790);
        publicUrl = c.getString("resource-pack.public-url", "").trim();
        required = c.getBoolean("resource-pack.required", false);
        try (InputStream in = plugin.getResource("pack.zip")) {
            if (in == null) {
                plugin.getLogger().warning("pack.zip absent du jar : pack de ressources désactivé.");
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
        packId = UUID.nameUUIDFromBytes(("rebornmmo:" + hash).getBytes(StandardCharsets.UTF_8));
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
            Thread t = new Thread(r, "RebornMMO-pack-http");
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

    public boolean isLoaded(UUID player) {
        return loaded.contains(player);
    }

    // ------------------------------------------------------------------ Envoi

    private String url(String host) {
        String v = hash.substring(0, 12);
        if (!publicUrl.isEmpty()) {
            return publicUrl + (publicUrl.contains("?") ? "&" : "?") + "v=" + v;
        }
        return "http://" + host + ":" + port + "/rebornmmo-" + v + ".zip";
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

    private ResourcePackRequest request(UUID player, String host, CompletableFuture<Void> done) {
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(packId, URI.create(url(host)), hash);
        return ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .replace(false)
                .required(required)
                .prompt(Text.mm("<#7FD4FF><b>RebornMMO</b></#7FD4FF>\n<#D7DCE2>Armes, cristaux et barre de vie façon jeu virtuel."))
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

    /** Phase de configuration : le pack est chargé pendant l'écran de connexion. */
    @EventHandler
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
        Audience audience = conn.getAudience();
        audience.sendResourcePacks(request(player, host(conn.getVirtualHost()), done));
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

    /** Pour les joueurs déjà connectés (rechargement du plugin) ou via /mmo pack. */
    public void send(Player p) {
        if (!enabled) {
            Text.msg(p, "<#FF5C5C>Le pack de ressources est désactivé sur ce serveur.");
            return;
        }
        p.sendResourcePacks(request(p.getUniqueId(), host(p.getVirtualHost()), null));
    }

    public boolean enabled() {
        return enabled;
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
