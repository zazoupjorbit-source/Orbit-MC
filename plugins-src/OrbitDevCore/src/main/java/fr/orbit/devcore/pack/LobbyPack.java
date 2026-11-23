package fr.orbit.devcore.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.message.Messages;
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
 * Pack de ressources du lobby : textures des menus (boussole, boutique) et objet invisible des zones cliquables.
 * Embarqué dans le jar ({@code pack.zip}, construit par build.ps1 depuis le dossier pack/) et servi par un
 * petit serveur HTTP intégré, sauf si une URL publique est configurée. Proposé pendant l'écran de connexion.
 *
 * <p>Le pack ne contient que des ressources propres à ORBIT (police orbitdev:gui, modèle orbitdev:invisible) :
 * il peut rester chargé chez le joueur quand il change de serveur sans rien modifier ailleurs.
 * Les menus utilisent les textures seulement si {@link #isLoaded} ; sinon ils retombent sur des vitres colorées.
 */
public final class LobbyPack implements Listener {

    private static final long WAIT_SECONDS = 60;

    private final OrbitDevCore plugin;
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

    public LobbyPack(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        FileConfiguration c = plugin.getConfig();
        // Par défaut, seulement là où la boussole existe (le lobby).
        if (!c.getBoolean("resource-pack.enabled", plugin.compass().enabled())) {
            return;
        }
        port = c.getInt("resource-pack.port", 8792);
        publicUrl = c.getString("resource-pack.public-url", "").trim();
        required = c.getBoolean("resource-pack.required", false);
        try (InputStream in = plugin.getResource("pack.zip")) {
            if (in == null) {
                plugin.getLogger().warning("pack.zip absent du jar : menus sans textures (vitres colorées).");
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
        packId = UUID.nameUUIDFromBytes(("orbitdev:" + hash).getBytes(StandardCharsets.UTF_8));
        // serve-local: false (autres serveurs du réseau) : le pack, identique, est déjà servi par le lobby sur ce port.
        boolean serve = c.getBoolean("resource-pack.serve-local", true);
        if (publicUrl.isEmpty() && serve && !startHttp()) {
            return;
        }
        enabled = true;
        plugin.getLogger().info("Pack du lobby prêt (" + (zip.length / 1024) + " Ko, sha1 " + hash.substring(0, 12) + ") — "
                + (publicUrl.isEmpty() ? (serve ? "servi sur le port " : "servi par le lobby sur le port ") + port : "URL publique " + publicUrl) + ".");
    }

    private boolean startHttp() {
        try {
            http = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            plugin.getLogger().warning("Port " + port + " indisponible pour le pack du lobby (" + e.getMessage()
                    + ") : change resource-pack.port ou renseigne resource-pack.public-url.");
            return false;
        }
        http.createContext("/", this::serve);
        httpPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "OrbitDevCore-pack-http");
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
        loaded.clear();
        if (http != null) {
            http.stop(0);
            http = null;
        }
        if (httpPool != null) {
            httpPool.shutdownNow();
            httpPool = null;
        }
    }

    /** true : le client a le pack, les menus peuvent utiliser leurs textures. */
    public boolean isLoaded(Player player) {
        return enabled && loaded.contains(player.getUniqueId());
    }

    public boolean enabled() {
        return enabled;
    }

    // ------------------------------------------------------------------ envoi

    private String url(String host) {
        String v = hash.substring(0, 12);
        if (!publicUrl.isEmpty()) {
            return publicUrl + (publicUrl.contains("?") ? "&" : "?") + "v=" + v;
        }
        return "http://" + host + ":" + port + "/orbit-lobby-" + v + ".zip";
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

    private ResourcePackRequest request(UUID player, String host, CompletableFuture<Void> done, boolean replace) {
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(packId, URI.create(url(host)), hash);
        return ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .replace(replace)
                .required(required)
                .prompt(Messages.mini("<#8DB8FF><b>ORBIT</b></#8DB8FF>\n<#D7DCE2>Menus du lobby et anneaux-portails du réseau."))
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
    @EventHandler(priority = EventPriority.LOWEST)
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
        // Connexion à ce serveur : retire les packs des serveurs précédents (via le proxy, le client les garde),
        // le pack du mode éventuel s'ajoute ensuite par-dessus (son écouteur est en priorité HIGH).
        conn.getAudience().sendResourcePacks(request(player, host(conn.getVirtualHost()), done, true));
        try {
            done.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            plugin.getLogger().info("Pas de réponse au pack du lobby pour " + conn.getProfile().getName()
                    + " après " + WAIT_SECONDS + " s : connexion poursuivie.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ignored) {
            // jamais complété exceptionnellement
        }
    }

    /** Renvoi manuel (joueurs déjà connectés au rechargement, ou /orbitdev pack). */
    public void send(Player p) {
        if (enabled) {
            p.sendResourcePacks(request(p.getUniqueId(), host(p.getVirtualHost()), null, false));
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
