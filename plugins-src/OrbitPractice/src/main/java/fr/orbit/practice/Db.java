package fr.orbit.practice;

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

/**
 * Accès à la base MariaDB du réseau (setup\db.properties). Une seule connexion, utilisée par un seul
 * fil dédié : jamais de requête sur le fil du serveur. Base absente : {@link #available()} reste faux
 * et le mode tourne avec un Elo en mémoire.
 */
public final class Db {

    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private final Plugin plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OrbitPractice-DB");
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

    /** Lit le fichier d'accès, ouvre la connexion et crée les tables. Appelé une fois au démarrage. */
    public void connect(File properties) {
        if (!properties.isFile()) {
            plugin.getLogger().warning("Base : fichier d'accès introuvable (" + properties.getPath()
                    + "). L'Elo reste en mémoire.");
            return;
        }
        Properties props = new Properties();
        try (Reader reader = new InputStreamReader(Files.newInputStream(properties.toPath()), StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (Exception e) {
            plugin.getLogger().warning("Base : lecture de " + properties.getPath() + " impossible : " + e.getMessage());
            return;
        }
        url = "jdbc:mysql://" + props.getProperty("host", "127.0.0.1") + ":" + props.getProperty("port", "3306")
                + "/" + props.getProperty("database", "orbit_mc")
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&connectTimeout=4000";
        user = props.getProperty("user", "");
        password = props.getProperty("password", "");
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Statement st = open().createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS practice_elo ("
                        + "uuid CHAR(36) NOT NULL, ladder VARCHAR(24) NOT NULL, name VARCHAR(16) NOT NULL, "
                        + "elo INT NOT NULL, wins INT NOT NULL DEFAULT 0, losses INT NOT NULL DEFAULT 0, "
                        + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                        + "PRIMARY KEY (uuid, ladder), KEY idx_top (ladder, elo))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS practice_matchs ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                        + "played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "ladder VARCHAR(24) NOT NULL, mode VARCHAR(8) NOT NULL, ranked TINYINT NOT NULL, "
                        + "winners VARCHAR(80) NOT NULL, losers VARCHAR(80) NOT NULL, score VARCHAR(8) NOT NULL, "
                        + "forfeit TINYINT NOT NULL DEFAULT 0, arena VARCHAR(32) NOT NULL, KEY idx_date (played_at))");
            }
            available = true;
            plugin.getLogger().info("Base MariaDB du réseau jointe : Elo partagé.");
        } catch (Throwable e) {
            plugin.getLogger().warning("Base injoignable (" + e.getMessage() + "). L'Elo reste en mémoire.");
        }
    }

    private Connection open() throws SQLException {
        if (connection == null || !connection.isValid(2)) {
            connection = DriverManager.getConnection(url, user, password);
        }
        return connection;
    }

    /** Lecture hors du fil du serveur. Renvoie {@code fallback} si la base est absente ou en erreur. */
    public <T> CompletableFuture<T> query(Work<T> work, T fallback) {
        if (!available) {
            return CompletableFuture.completedFuture(fallback);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return work.run(open());
            } catch (SQLException e) {
                plugin.getLogger().warning("Base : " + e.getMessage());
                return fallback;
            }
        }, executor);
    }

    /** Écriture hors du fil du serveur. */
    public void exec(Work<?> work) {
        if (!available) {
            return;
        }
        executor.execute(() -> {
            try {
                work.run(open());
            } catch (SQLException e) {
                plugin.getLogger().warning("Base : " + e.getMessage());
            }
        });
    }

    /** Laisse finir les écritures en attente puis ferme la connexion. */
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
