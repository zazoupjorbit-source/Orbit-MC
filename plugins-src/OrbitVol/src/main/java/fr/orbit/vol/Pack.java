package fr.orbit.vol;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pack de ressources HD de Vole mon Orbito (créatures, objets, HUD). Il remplace des textures de bêtes du jeu,
 * mais il n'est envoyé que sur ce serveur et remplace les packs des autres (replace) : en quittant, on les retrouve.
 * Servi par un petit serveur HTTP local (ou une URL publique configurée : pack.url-publique), proposé à la connexion.
 */
final class Pack implements Listener {
    private final OrbitVol plugin;
    /** Appelé quand un joueur a chargé le pack (habiller ses objets, afficher le HUD HD). */
    java.util.function.Consumer<Player> quandCharge = p -> { };
    private byte[] zip;
    private String hash;
    private UUID id;
    private String url;
    private HttpServer http;
    private ExecutorService pool;
    /** Joueurs dont le client a chargé le pack (menus habillés seulement pour eux). */
    private final Set<UUID> charges = new HashSet<>();

    Pack(OrbitVol plugin) { this.plugin = plugin; }

    void demarrer() {
        if (!plugin.getConfig().getBoolean("pack.actif", true)) return;
        try (InputStream in = plugin.getResource("pack.zip")) {
            if (in == null) { plugin.getLogger().warning("pack.zip absent du jar : pas de textures HD."); return; }
            zip = in.readAllBytes();
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip));
        } catch (Exception e) {
            plugin.getLogger().warning("Lecture de pack.zip impossible : " + e);
            return;
        }
        id = UUID.nameUUIDFromBytes(("orbitvol:" + hash).getBytes(StandardCharsets.UTF_8));
        int port = plugin.getConfig().getInt("pack.port", 8795);
        String publique = plugin.getConfig().getString("pack.url-publique", "").trim();
        if (publique.isEmpty() || plugin.getConfig().getBoolean("pack.serve-local", true)) {
            try {
                http = HttpServer.create(new InetSocketAddress(port), 0);
                http.createContext("/", this::servir);
                pool = Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "OrbitVol-pack"); t.setDaemon(true); return t; });
                http.setExecutor(pool);
                http.start();
            } catch (IOException e) {
                plugin.getLogger().warning("Port " + port + " indisponible pour le pack (" + e.getMessage() + ").");
                zip = null;
                return;
            }
            url = publique.isEmpty() ? "http://127.0.0.1:" + port + "/orbitvol.zip" : publique;
        } else url = publique;
        plugin.getLogger().info("Pack Vole mon Orbito prêt (" + zip.length / 1024 + " Ko) : " + url);
    }

    private void servir(HttpExchange ex) throws IOException {
        try {
            ex.getResponseHeaders().set("Content-Type", "application/zip");
            ex.sendResponseHeaders(200, zip.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(zip); }
        } finally { ex.close(); }
    }

    void envoyer(Player p) {
        if (zip == null || url == null) return;
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(id, URI.create(url), hash);
        // replace(false) : passe par-dessus le pack ORBIT (interface HD) envoyé par OrbitDevCore à la connexion.
        p.sendResourcePacks(ResourcePackRequest.resourcePackRequest().packs(info).replace(false).required(false)
                .prompt(Txt.mm("<gradient:#E0B864:#62CFF4><b>Vole mon Orbito</b></gradient> <gray>: textures HD des Orbitos, objets et HUD")).build());
    }

    boolean charge(Player p) { return charges.contains(p.getUniqueId()); }

    @EventHandler
    public void statut(PlayerResourcePackStatusEvent e) {
        if (id == null || !id.equals(e.getID())) return;
        Player p = e.getPlayer();
        plugin.getLogger().info("Pack de " + p.getName() + " : " + e.getStatus());
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> { charges.add(p.getUniqueId()); quandCharge.accept(p); }
            case DECLINED -> {
                charges.remove(p.getUniqueId());
                Txt.send(p, "<#FF4D6D>Pack de textures refusé.</#FF4D6D> <gray>Multijoueur → sélectionne le serveur → <white>Modifier</white> → <white>Packs de ressources : Activés</white>, puis tape <#62CFF4>/vol pack</#62CFF4>.");
            }
            case FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> {
                charges.remove(p.getUniqueId());
                Txt.send(p, "<#FF4D6D>Le pack de textures n'a pas pu se charger.</#FF4D6D> <gray>Tape <#62CFF4>/vol pack</#62CFF4> pour réessayer.");
            }
            default -> { }
        }
    }

    @EventHandler
    public void quitter(PlayerQuitEvent e) { charges.remove(e.getPlayer().getUniqueId()); }

    void arreter() {
        if (http != null) { http.stop(0); http = null; }
        if (pool != null) { pool.shutdownNow(); pool = null; }
    }
}
