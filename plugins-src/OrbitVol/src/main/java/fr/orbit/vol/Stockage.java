package fr.orbit.vol;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Profils en MariaDB (base orbit_mc du réseau, setup\db.properties) : table vol_profils, un texte YAML par joueur.
 * Une seule connexion sur un fil dédié ; jamais de requête sur le fil du serveur. Sans base, le mode tourne
 * sur les fichiers joueurs/*.yml (toujours écrits en secours).
 */
final class Stockage {

    static Stockage instance;

    private final Plugin plugin;
    private final ExecutorService fil = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OrbitVol-DB");
        t.setDaemon(true);
        return t;
    });
    private String url, user, password;
    private Connection connexion;
    volatile boolean jointe;

    Stockage(Plugin plugin) {
        this.plugin = plugin;
    }

    void connecter(File proprietes) {
        if (!proprietes.isFile()) {
            plugin.getLogger().warning("Base : " + proprietes.getPath() + " introuvable, profils en fichiers seulement.");
            return;
        }
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(Files.newInputStream(proprietes.toPath()), StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (Exception e) {
            plugin.getLogger().warning("Base : lecture impossible (" + e.getMessage() + ").");
            return;
        }
        url = "jdbc:mysql://" + p.getProperty("host", "127.0.0.1").trim() + ":" + p.getProperty("port", "3306").trim()
                + "/" + p.getProperty("database", "orbit_mc").trim()
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&connectTimeout=4000";
        user = p.getProperty("user", "").trim();
        password = p.getProperty("password", "").trim();
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            attendre(() -> {
                try (Statement st = ouvrir().createStatement()) {
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS vol_profils ("
                            + "uuid CHAR(36) NOT NULL PRIMARY KEY, pseudo VARCHAR(16) NOT NULL, donnees MEDIUMTEXT NOT NULL, "
                            + "revenu DOUBLE NOT NULL DEFAULT 0, vols BIGINT NOT NULL DEFAULT 0, ascension INT NOT NULL DEFAULT 0, "
                            + "maj TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                            + "KEY idx_revenu (revenu), KEY idx_vols (vols))");
                }
                return null;
            }, 8);
            jointe = true;
            instance = this;
            plugin.getLogger().info("Profils en MariaDB (table vol_profils).");
        } catch (Throwable e) {
            plugin.getLogger().warning("Base injoignable (" + e.getMessage() + ") : profils en fichiers seulement.");
        }
    }

    private Connection ouvrir() throws SQLException {
        if (connexion == null || !connexion.isValid(2)) connexion = DriverManager.getConnection(url, user, password);
        return connexion;
    }

    private <T> T attendre(Callable<T> c, int secondes) throws Exception {
        return fil.submit(c).get(secondes, TimeUnit.SECONDS);
    }

    /** Lecture bloquante (à appeler hors du fil du serveur). Null si absent ou base coupée. */
    String lire(UUID u) {
        if (!jointe) return null;
        try {
            return attendre(() -> {
                try (PreparedStatement ps = ouvrir().prepareStatement("SELECT donnees FROM vol_profils WHERE uuid = ?")) {
                    ps.setString(1, u.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? rs.getString(1) : null;
                    }
                }
            }, 5);
        } catch (Exception e) {
            plugin.getLogger().warning("Base : lecture de " + u + " impossible (" + e.getMessage() + ").");
            return null;
        }
    }

    /** Tous les profils (uuid → texte), lecture bloquante hors du fil du serveur. */
    Map<UUID, String> lireTous() {
        Map<UUID, String> m = new HashMap<>();
        if (!jointe) return m;
        try {
            return attendre(() -> {
                try (Statement st = ouvrir().createStatement(); ResultSet rs = st.executeQuery("SELECT uuid, donnees FROM vol_profils")) {
                    while (rs.next()) m.put(UUID.fromString(rs.getString(1)), rs.getString(2));
                }
                return m;
            }, 20);
        } catch (Exception e) {
            plugin.getLogger().warning("Base : lecture des profils impossible (" + e.getMessage() + ").");
            return m;
        }
    }

    /** Écriture en tâche de fond (le texte est préparé sur le fil du serveur). */
    void ecrire(Profil p) {
        if (!jointe) return;
        final String uuid = p.uuid.toString(), pseudo = p.pseudo, texte = p.versYaml().saveToString();
        final double revenu = p.revenu;
        final long vols = p.volsReussis;
        final int asc = p.ascension;
        fil.submit(() -> {
            try (PreparedStatement ps = ouvrir().prepareStatement("INSERT INTO vol_profils (uuid, pseudo, donnees, revenu, vols, ascension) "
                    + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE pseudo = VALUES(pseudo), donnees = VALUES(donnees), "
                    + "revenu = VALUES(revenu), vols = VALUES(vols), ascension = VALUES(ascension)")) {
                ps.setString(1, uuid);
                ps.setString(2, pseudo.length() > 16 ? pseudo.substring(0, 16) : pseudo);
                ps.setString(3, texte);
                ps.setDouble(4, revenu);
                ps.setLong(5, vols);
                ps.setInt(6, asc);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Base : profil de " + pseudo + " non écrit (" + e.getMessage() + "), le fichier de secours est à jour.");
            }
        });
    }

    void fermer() {
        fil.shutdown();
        try {
            fil.awaitTermination(8, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connexion != null) connexion.close();
        } catch (SQLException ignored) {
        }
        instance = null;
    }
}
