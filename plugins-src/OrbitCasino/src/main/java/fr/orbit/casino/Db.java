package fr.orbit.casino;

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
 * Base MariaDB du réseau (setup\db.properties). Une connexion, un seul fil dédié : aucune requête sur le fil
 * du serveur, et les mouvements d'Orbit Coins passent l'un après l'autre (pas de double dépense).
 */
public final class Db {

    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private final Plugin plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OrbitCasino-DB");
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

    /** Fichier d'accès : réglage {@code base.fichier}, ou recherche dans ../setup puis ../../setup. */
    public static File locate(Plugin plugin, String setting) {
        File server = new File(".").getAbsoluteFile();
        if (setting != null && !setting.isBlank() && !setting.equalsIgnoreCase("auto")) {
            File f = new File(setting);
            return f.isAbsolute() ? f : new File(server, setting);
        }
        for (String candidate : new String[]{"../setup/db.properties", "../../setup/db.properties"}) {
            File f = new File(server, candidate);
            if (f.isFile()) {
                return f;
            }
        }
        return new File(server, "../setup/db.properties");
    }

    public void connect(File properties) {
        if (!properties.isFile()) {
            plugin.getLogger().severe("Base : fichier d'accès introuvable (" + properties.getPath() + "). Orbit Coins coupés.");
            return;
        }
        Properties props = new Properties();
        try (Reader reader = new InputStreamReader(Files.newInputStream(properties.toPath()), StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (Exception e) {
            plugin.getLogger().severe("Base : lecture de " + properties.getPath() + " impossible : " + e.getMessage());
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
                st.executeUpdate("CREATE TABLE IF NOT EXISTS casino_comptes ("
                        + "uuid CHAR(36) NOT NULL PRIMARY KEY, nom VARCHAR(16) NOT NULL, "
                        + "solde BIGINT NOT NULL DEFAULT 0, "
                        // jetons posés sur une table de poker : rendus au démarrage si le serveur s'est arrêté net
                        + "en_jeu BIGINT NOT NULL DEFAULT 0, "
                        + "ration DATE NULL, minutes INT NOT NULL DEFAULT 0, titre VARCHAR(32) NULL, "
                        + "gagne BIGINT NOT NULL DEFAULT 0, "
                        + "cree_le TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "KEY idx_solde (solde), KEY idx_nom (nom))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS casino_cosmetiques ("
                        + "uuid CHAR(36) NOT NULL, type VARCHAR(16) NOT NULL, id VARCHAR(64) NOT NULL, "
                        + "prix BIGINT NOT NULL, achete_le TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "PRIMARY KEY (uuid, type, id))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS casino_jackpot ("
                        + "id TINYINT NOT NULL PRIMARY KEY, montant BIGINT NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS casino_journal ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, uuid CHAR(36) NOT NULL, "
                        + "jeu VARCHAR(16) NOT NULL, mouvement BIGINT NOT NULL, detail VARCHAR(64) NULL, "
                        + "quand TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_uuid (uuid, quand))");
            }
            available = true;
            plugin.getLogger().info("Base MariaDB du réseau jointe : Orbit Coins actifs.");
        } catch (Throwable e) {
            plugin.getLogger().severe("Base injoignable (" + e.getMessage() + "). Orbit Coins coupés.");
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

    public <T> CompletableFuture<T> transaction(Work<T> work) {
        return run(c -> {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        });
    }

    /** Ramène le résultat sur le fil du serveur. En cas d'erreur, {@code onError} (peut être null). */
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
                } else if (onResult != null) {
                    onResult.accept(value);
                }
            });
        });
    }

    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(8, TimeUnit.SECONDS);
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
