package fr.orbit.survie;

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
 * Accès à la base MariaDB du réseau (setup\db.properties). Une seule connexion, utilisée par un seul fil
 * dédié : jamais de requête sur le fil du serveur, et les opérations d'argent passent l'une après l'autre
 * (pas de double dépense possible).
 */
public final class Db {

    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private final Plugin plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OrbitSurvie-DB");
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
                    + "). Économie et hôtel des ventes coupés.");
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
                st.executeUpdate("CREATE TABLE IF NOT EXISTS survie_comptes ("
                        + "uuid CHAR(36) NOT NULL PRIMARY KEY, nom VARCHAR(16) NOT NULL, "
                        + "solde DECIMAL(15,2) NOT NULL DEFAULT 0, "
                        + "maj TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                        + "KEY idx_solde (solde), KEY idx_nom (nom))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS survie_hdv ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                        + "vendeur CHAR(36) NOT NULL, vendeur_nom VARCHAR(16) NOT NULL, "
                        + "objet MEDIUMTEXT NOT NULL, prix DECIMAL(15,2) NOT NULL, "
                        + "mise_le TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, expire_le TIMESTAMP NOT NULL, "
                        // en_vente → vendu | a_livrer (acheteur parti avant la remise) | retire
                        + "statut VARCHAR(12) NOT NULL DEFAULT 'en_vente', acheteur CHAR(36) NULL, vendu_le TIMESTAMP NULL, "
                        + "KEY idx_statut (statut, mise_le), KEY idx_vendeur (vendeur, statut), KEY idx_acheteur (acheteur, statut))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS survie_kits ("
                        + "uuid CHAR(36) NOT NULL, kit VARCHAR(24) NOT NULL, pris_le TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "PRIMARY KEY (uuid, kit))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS survie_bases ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, proprio CHAR(36) NOT NULL, proprio_nom VARCHAR(16) NOT NULL, "
                        + "monde VARCHAR(64) NOT NULL, x INT NOT NULL, y INT NOT NULL, z INT NOT NULL, niveau TINYINT NOT NULL DEFAULT 1, "
                        + "paye_jusqu_a TIMESTAMP NOT NULL, cree_le TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "KEY idx_proprio (proprio))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS survie_base_membres ("
                        + "base_id BIGINT NOT NULL, uuid CHAR(36) NOT NULL, nom VARCHAR(16) NOT NULL, "
                        + "PRIMARY KEY (base_id, uuid))");
                // Grades achetés en crédits : date de fin (LuckPerms porte le grade, ceci sert à l'afficher).
                st.executeUpdate("CREATE TABLE IF NOT EXISTS survie_grades ("
                        + "uuid CHAR(36) NOT NULL, grade VARCHAR(24) NOT NULL, expire_le DATETIME NOT NULL, "
                        + "PRIMARY KEY (uuid, grade))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS survie_contrats ("
                        + "uuid CHAR(36) NOT NULL, jour DATE NOT NULL, numero TINYINT NOT NULL, "
                        + "PRIMARY KEY (uuid, jour, numero))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS survie_metiers ("
                        + "uuid CHAR(36) NOT NULL, metier VARCHAR(16) NOT NULL, xp BIGINT NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY (uuid, metier))");
                // Compteurs du mois (contrats, XP de métiers, poissons, monstres, ventes, duels) pour les classements.
                st.executeUpdate("CREATE TABLE IF NOT EXISTS survie_stats ("
                        + "uuid CHAR(36) NOT NULL, nom VARCHAR(16) NOT NULL, mois CHAR(7) NOT NULL, cle VARCHAR(24) NOT NULL, "
                        + "valeur DOUBLE NOT NULL DEFAULT 0, PRIMARY KEY (uuid, mois, cle), KEY idx_top (mois, cle, valeur))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS survie_lots_mois ("
                        + "mois CHAR(7) NOT NULL PRIMARY KEY, paye_le DATETIME NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS survie_echoppes ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, proprio CHAR(36) NOT NULL, proprio_nom VARCHAR(16) NOT NULL, "
                        + "monde VARCHAR(64) NOT NULL, x INT NOT NULL, y INT NOT NULL, z INT NOT NULL, "
                        + "objet MEDIUMTEXT NOT NULL, lot INT NOT NULL, prix DECIMAL(15,2) NOT NULL, "
                        + "KEY idx_proprio (proprio))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS survie_homes ("
                        + "uuid CHAR(36) NOT NULL, nom VARCHAR(24) NOT NULL, monde VARCHAR(64) NOT NULL, "
                        + "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, yaw FLOAT NOT NULL, pitch FLOAT NOT NULL, "
                        + "PRIMARY KEY (uuid, nom))");
            }
            available = true;
            plugin.getLogger().info("Base MariaDB du réseau jointe : économie et hôtel des ventes actifs.");
        } catch (Throwable e) {
            plugin.getLogger().severe("Base injoignable (" + e.getMessage() + "). Économie et hôtel des ventes coupés.");
        }
    }

    private Connection open() throws SQLException {
        if (connection == null || !connection.isValid(2)) {
            connection = DriverManager.getConnection(url, user, password);
        }
        return connection;
    }

    /** Travail sur le fil de la base. Échoue si la base est absente. */
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

    /** Travail en transaction (tout ou rien). */
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
        future.whenComplete((value, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                if (onError != null) {
                    onError.run();
                }
            } else {
                onResult.accept(value);
            }
        }));
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
