package fr.orbit.staff;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Base MariaDB du réseau (setup\db.properties). Une connexion, un fil dédié : aucune requête
 * sur le fil du serveur. Les tables sont partagées par tous les serveurs (préfixe staff_).
 */
public final class Db {

    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private final Plugin plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OrbitStaff-DB");
        t.setDaemon(true);
        return t;
    });
    private String url;
    private String user;
    private String password;
    private Connection connection;
    private volatile boolean available;

    public Db(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean available() {
        return available;
    }

    public void connect(File properties) {
        if (!properties.isFile()) {
            plugin.getLogger().severe("Base : fichier d'accès introuvable (" + properties.getPath()
                    + "). Sanctions, stats et chat staff réseau coupés.");
            return;
        }
        Properties props = new Properties();
        try (Reader reader = new InputStreamReader(Files.newInputStream(properties.toPath()), StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (Exception e) {
            plugin.getLogger().severe("Base : lecture de " + properties.getPath() + " impossible : " + e.getMessage());
            return;
        }
        url = "jdbc:mysql://" + props.getProperty("host", "127.0.0.1").trim() + ":" + props.getProperty("port", "3306").trim()
                + "/" + props.getProperty("database", "orbit_mc").trim()
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&connectTimeout=4000";
        user = props.getProperty("user", "").trim();
        password = props.getProperty("password", "").trim();
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Statement st = open().createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS staff_joueurs ("
                        + "uuid CHAR(36) NOT NULL PRIMARY KEY, nom VARCHAR(16) NOT NULL, "
                        + "premiere TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, derniere TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "temps_jeu BIGINT NOT NULL DEFAULT 0, ip_hash CHAR(64) NULL, pays VARCHAR(8) NULL, "
                        + "serveur VARCHAR(32) NULL, en_ligne TINYINT NOT NULL DEFAULT 0, "
                        + "KEY idx_nom (nom), KEY idx_ip (ip_hash))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS staff_ips ("
                        + "uuid CHAR(36) NOT NULL, ip_hash CHAR(64) NOT NULL, vu TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "PRIMARY KEY (uuid, ip_hash), KEY idx_ip (ip_hash))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS staff_combat ("
                        + "uuid CHAR(36) NOT NULL, serveur VARCHAR(32) NOT NULL, kills INT NOT NULL DEFAULT 0, "
                        + "morts INT NOT NULL DEFAULT 0, PRIMARY KEY (uuid, serveur))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS staff_sanctions ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, uuid CHAR(36) NOT NULL, nom VARCHAR(16) NOT NULL, "
                        + "type VARCHAR(10) NOT NULL, motif VARCHAR(255) NOT NULL, staff VARCHAR(16) NOT NULL, "
                        + "serveur VARCHAR(32) NOT NULL, debut TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, fin TIMESTAMP NULL, "
                        + "actif TINYINT NOT NULL DEFAULT 1, leve_par VARCHAR(16) NULL, leve_motif VARCHAR(255) NULL, leve_le TIMESTAMP NULL, "
                        + "KEY idx_uuid (uuid, actif), KEY idx_type (type, actif))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS staff_notes ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, uuid CHAR(36) NOT NULL, texte VARCHAR(255) NOT NULL, "
                        + "staff VARCHAR(16) NOT NULL, serveur VARCHAR(32) NOT NULL, cree TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "KEY idx_uuid (uuid))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS staff_reports ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, uuid CHAR(36) NOT NULL, nom VARCHAR(16) NOT NULL, "
                        + "par VARCHAR(16) NOT NULL, motif VARCHAR(255) NOT NULL, serveur VARCHAR(32) NOT NULL, "
                        + "cree TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, traite_par VARCHAR(16) NULL, "
                        + "KEY idx_uuid (uuid), KEY idx_traite (traite_par))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS staff_alertes ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, uuid CHAR(36) NOT NULL, type VARCHAR(16) NOT NULL, "
                        + "detail VARCHAR(255) NOT NULL, serveur VARCHAR(32) NOT NULL, cree TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "KEY idx_uuid (uuid, type))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS staff_logs ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, staff VARCHAR(16) NOT NULL, action VARCHAR(24) NOT NULL, "
                        + "cible VARCHAR(16) NULL, detail VARCHAR(255) NULL, serveur VARCHAR(32) NOT NULL, "
                        + "cree TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_staff (staff), KEY idx_cible (cible))");
                // Fil réseau : chat staff, signalements, alertes, sanctions. Chaque serveur lit ce qui est nouveau.
                st.executeUpdate("CREATE TABLE IF NOT EXISTS staff_flux ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, type VARCHAR(12) NOT NULL, serveur VARCHAR(32) NOT NULL, "
                        + "cible CHAR(36) NULL, texte VARCHAR(512) NOT NULL, cree TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
                st.executeUpdate("DELETE FROM staff_flux WHERE cree < NOW() - INTERVAL 3 DAY");
            }
            available = true;
            plugin.getLogger().info("Base MariaDB du réseau jointe.");
        } catch (Throwable e) {
            plugin.getLogger().severe("Base injoignable (" + e.getMessage() + "). Sanctions, stats et chat staff réseau coupés.");
        }
    }

    private Connection open() throws SQLException {
        if (connection == null || !connection.isValid(2)) {
            connection = DriverManager.getConnection(url, user, password);
        }
        return connection;
    }

    public <T> CompletableFuture<T> run(Work<T> work) {
        if (!available) {
            return CompletableFuture.failedFuture(new SQLException("base indisponible"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return work.run(open());
            } catch (SQLException e) {
                plugin.getLogger().warning("Base : " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /** Écriture sans attendre de réponse. */
    public void fire(Work<?> work) {
        run(work).exceptionally(e -> null);
    }

    /** Ramène le résultat sur le fil du serveur. */
    public <T> void then(CompletableFuture<T> future, Consumer<T> onResult, Runnable onError) {
        future.whenComplete((value, error) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    if (onError != null) {
                        onError.run();
                    }
                } else {
                    onResult.accept(value);
                }
            });
        });
    }

    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ignored) {
        }
    }
}
