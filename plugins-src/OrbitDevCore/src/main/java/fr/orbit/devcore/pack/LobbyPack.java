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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
 * <p>Le pack contient surtout des ressources propres à ORBIT (police orbitdev:gui, modèle orbitdev:invisible),
 * plus l'habillage du menu Échap du réseau (boutons, fond, logo du lobby : scripts\gen-echap.ps1).
 * Les menus utilisent les textures seulement si {@link #isLoaded} ; sinon ils retombent sur des vitres colorées.
 *
 * <p>Menu Échap par mode : {@code menu-echap: survie} (ou extraction, practice, mmo) ajoute par-dessus le petit
 * pack {@code echap-<mode>.zip} (logo du mode). Tous ces packs sont servis par le serveur qui sert le pack du lobby.
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
    /** Theme d'interface de ce serveur (station, survie, extraction, practice, mmo, tycoon, vol). */
    private String theme = "station";
    /** Dossier des packs complets par theme (MinecraftDev/shared/ui-packs, écrits par tools/gen-ui.ps1), null si absent. */
    private Path uiDir;
    /** Cache des packs de thème servis : nom -> {date de modification, octets}. */
    private final Map<String, Object[]> uiCache = new ConcurrentHashMap<>();
    private ExecutorService httpPool;

    /** Packs « menu Échap » de chaque mode (tous chargés : le serveur qui sert les packs doit pouvoir tous les fournir). */
    private static final String[] ECHAP_MODES = {"survie", "extraction", "practice", "mmo"};
    private final Map<String, byte[]> echapZips = new HashMap<>();
    private final Map<String, String> echapHashes = new HashMap<>();
    /** Mode de ce serveur (config menu-echap), null si aucun. */
    private String echapMode;
    private UUID echapId;

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
        theme = c.getString("resource-pack.theme", "station").trim().toLowerCase(Locale.ROOT);
        if (!theme.matches("[a-z0-9_-]+")) {
            theme = "station";
        }
        uiDir = findUiDir(c.getString("resource-pack.ui-dir", ""));
        // Pack complet du thème (menus du lobby + interface HD du mode) ; sinon le pack de base du jar.
        byte[] ui = uiZip(theme);
        boolean themed = ui != null;
        if (themed) {
            zip = ui;
        } else {
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
            plugin.getLogger().warning("Interface HD « " + theme + " » introuvable (" + uiDir + ") : pack de base seulement."
                    + " Lance plugins-src/OrbitDevCore/tools/gen-ui.ps1.");
        }
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        packId = UUID.nameUUIDFromBytes(("orbitdev:" + hash).getBytes(StandardCharsets.UTF_8));
        loadEchap(c.getString("menu-echap", "").trim().toLowerCase(java.util.Locale.ROOT));
        // serve-local: false (autres serveurs du réseau) : le pack, identique, est déjà servi par le lobby sur ce port.
        boolean serve = c.getBoolean("resource-pack.serve-local", true);
        if (publicUrl.isEmpty() && serve && !startHttp()) {
            return;
        }
        enabled = true;
        plugin.getLogger().info("Pack ORBIT prêt (interface " + (themed ? theme : "de base") + ", " + (zip.length / 1024) + " Ko, sha1 " + hash.substring(0, 12) + ") — "
                + (publicUrl.isEmpty() ? (serve ? "servi sur le port " : "servi par le lobby sur le port ") + port : "URL publique " + publicUrl) + ".");
    }

    private void loadEchap(String mode) {
        for (String m : ECHAP_MODES) {
            try (InputStream in = plugin.getResource("echap-" + m + ".zip")) {
                if (in == null) {
                    continue;
                }
                byte[] data = in.readAllBytes();
                echapZips.put(m, data);
                echapHashes.put(m, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data)));
            } catch (IOException | NoSuchAlgorithmException e) {
                plugin.getLogger().log(Level.WARNING, "Lecture de echap-" + m + ".zip impossible", e);
            }
        }
        if (mode.isEmpty()) {
            return;
        }
        if (!echapZips.containsKey(mode)) {
            plugin.getLogger().warning("menu-echap: " + mode + " inconnu (modes : " + String.join(", ", ECHAP_MODES) + ").");
        } else if (!publicUrl.isEmpty()) {
            plugin.getLogger().warning("menu-echap ignoré : avec resource-pack.public-url, le pack du menu Échap n'est pas hébergé.");
        } else {
            echapMode = mode;
            echapId = UUID.nameUUIDFromBytes(("orbitdev-echap:" + echapHashes.get(mode)).getBytes(StandardCharsets.UTF_8));
            plugin.getLogger().info("Menu Échap du mode " + mode + " ajouté par-dessus le pack du lobby.");
        }
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
            byte[] data = zip;
            String path = ex.getRequestURI().getPath();
            int i = path.indexOf("/orbit-echap-");
            if (i >= 0) {
                // /orbit-echap-<mode>-<version>.zip : logo du menu Échap d'un mode (posé par-dessus le pack ORBIT).
                String rest = path.substring(i + "/orbit-echap-".length());
                int dash = rest.lastIndexOf('-');
                data = dash > 0 ? echapZips.get(rest.substring(0, dash)) : null;
                if (data == null) {
                    ex.sendResponseHeaders(404, -1);
                    return;
                }
            } else {
                // /orbit-<thème>-<version>.zip : pack du thème demandé (les autres serveurs du réseau passent par ce port).
                Matcher m = THEMED.matcher(path);
                if (m.matches() && !m.group(1).equals("lobby")) {
                    byte[] ui = uiZip(m.group(1));
                    if (ui != null) {
                        data = ui;
                    }
                }
            }
            ex.getResponseHeaders().set("Content-Type", "application/zip");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            if ("HEAD".equalsIgnoreCase(method)) {
                ex.getResponseHeaders().set("Content-Length", String.valueOf(data.length));
                ex.sendResponseHeaders(200, -1);
                return;
            }
            ex.sendResponseHeaders(200, data.length);
            try (OutputStream body = ex.getResponseBody()) {
                body.write(data);
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
        return "http://" + host + ":" + port + "/orbit-" + theme + "-" + v + ".zip";
    }

    private static final Pattern THEMED = Pattern.compile("/orbit-([a-z0-9_]+)-[0-9a-f]+\\.zip");

    /** Dossier shared/ui-packs : réglage ui-dir, sinon cherché au-dessus du dossier du serveur (lobby ou instance). */
    private Path findUiDir(String configured) {
        if (!configured.isBlank()) {
            return Path.of(configured);
        }
        Path dir = plugin.getServer().getWorldContainer().getAbsoluteFile().toPath().normalize();
        for (int i = 0; i < 4 && dir != null; i++) {
            Path p = dir.resolve("shared").resolve("ui-packs");
            if (Files.isDirectory(p)) {
                return p;
            }
            dir = dir.getParent();
        }
        return null;
    }

    /** Pack complet d'un thème, relu si le fichier a changé (régénération sans redémarrer le lobby). */
    private byte[] uiZip(String name) {
        if (uiDir == null || !name.matches("[a-z0-9_]+")) {
            return null;
        }
        Path f = uiDir.resolve(name + ".zip");
        try {
            if (!Files.isRegularFile(f)) {
                return null;
            }
            long t = Files.getLastModifiedTime(f).toMillis();
            Object[] c = uiCache.get(name);
            if (c != null && (long) c[0] == t) {
                return (byte[]) c[1];
            }
            byte[] b = Files.readAllBytes(f);
            uiCache.put(name, new Object[]{t, b});
            return b;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Lecture de " + f + " impossible", e);
            return null;
        }
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
        List<ResourcePackInfo> infos = new ArrayList<>();
        infos.add(ResourcePackInfo.resourcePackInfo(packId, URI.create(url(host)), hash));
        if (echapMode != null) {
            // Ajouté après le pack du lobby : il passe par-dessus (logo du mode dans le menu Échap).
            String v = echapHashes.get(echapMode).substring(0, 12);
            infos.add(ResourcePackInfo.resourcePackInfo(echapId,
                    URI.create("http://" + host + ":" + port + "/orbit-echap-" + echapMode + "-" + v + ".zip"), echapHashes.get(echapMode)));
        }
        AtomicInteger pending = new AtomicInteger(infos.size());
        return ResourcePackRequest.resourcePackRequest()
                .packs(infos)
                .replace(replace)
                .required(required)
                .prompt(Messages.mini("<#8DB8FF><b>ORBIT</b></#8DB8FF>\n<#D7DCE2>Interface HD du réseau : inventaires, menus et anneaux-portails."))
                .callback((id, status, audience) -> {
                    if (status.intermediate()) {
                        return;
                    }
                    if (packId.equals(id)) {
                        if (status == ResourcePackStatus.SUCCESSFULLY_LOADED) {
                            loaded.add(player);
                        } else {
                            loaded.remove(player);
                        }
                    }
                    if (pending.decrementAndGet() <= 0 && done != null) {
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
