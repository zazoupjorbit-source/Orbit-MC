package fr.orbitgames.hcf.core.storage;

import fr.orbitgames.hcf.api.service.DatabaseService;
import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Stockage OrbitHCF : SQLite par defaut, MySQL/MariaDB en option (database.yml).
 * Connexion unique partagee, toutes les operations sont synchronisees sur cette instance.
 */
public class Database implements DatabaseService {

    @FunctionalInterface
    public interface ResultReader<T> {
        T read(ResultSet rs) throws SQLException;
    }

    private final OrbitHCFCorePlugin plugin;
    private Connection connection;
    private boolean mysql;
    private String prefix = "orbithcf_";

    public Database(OrbitHCFCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void connect(FileConfiguration config) throws SQLException {
        String type = config.getString("type", "sqlite").toLowerCase();
        this.prefix = config.getString("table-prefix", "orbithcf_");
        this.mysql = type.equals("mysql") || type.equals("mariadb");
        if (mysql) {
            String host = config.getString("mysql.host", "localhost");
            int port = config.getInt("mysql.port", 3306);
            String db = config.getString("mysql.database", "orbithcf");
            String user = config.getString("mysql.user", "root");
            String password = config.getString("mysql.password", "");
            String params = config.getString("mysql.params", "?useSSL=false");
            String url = "jdbc:mysql://" + host + ":" + port + "/" + db + params;
            connection = DriverManager.getConnection(url, user, password);
            plugin.getLogger().info("Connecte a MySQL (" + host + ":" + port + "/" + db + ").");
        } else {
            File file = new File(plugin.getDataFolder(), config.getString("sqlite.file", "data.db"));
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL;");
            }
            plugin.getLogger().info("Connecte a SQLite (" + file.getName() + ").");
        }
        createTables();
    }

    private void createTables() throws SQLException {
        String[] ddl = {
                "CREATE TABLE IF NOT EXISTS " + prefix + "factions (" +
                        "id VARCHAR(36) PRIMARY KEY, name VARCHAR(32) NOT NULL, type VARCHAR(16) NOT NULL, " +
                        "leader VARCHAR(36), home_world VARCHAR(64), home_x DOUBLE, home_y DOUBLE, home_z DOUBLE, " +
                        "home_yaw FLOAT, home_pitch FLOAT, dtr DOUBLE NOT NULL DEFAULT 0, freeze_end BIGINT NOT NULL DEFAULT 0, " +
                        "points INT NOT NULL DEFAULT 0, focus VARCHAR(36), created BIGINT NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS " + prefix + "members (" +
                        "uuid VARCHAR(36) PRIMARY KEY, faction_id VARCHAR(36) NOT NULL, role VARCHAR(16) NOT NULL)",
                "CREATE TABLE IF NOT EXISTS " + prefix + "allies (" +
                        "a VARCHAR(36) NOT NULL, b VARCHAR(36) NOT NULL, PRIMARY KEY (a, b))",
                "CREATE TABLE IF NOT EXISTS " + prefix + "claims (" +
                        "world VARCHAR(64) NOT NULL, cx INT NOT NULL, cz INT NOT NULL, faction_id VARCHAR(36) NOT NULL, " +
                        "PRIMARY KEY (world, cx, cz))",
                "CREATE TABLE IF NOT EXISTS " + prefix + "profiles (" +
                        "uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(16), kills INT NOT NULL DEFAULT 0, " +
                        "deaths INT NOT NULL DEFAULT 0, first_join BIGINT NOT NULL DEFAULT 0, last_seen BIGINT NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS " + prefix + "timers (" +
                        "uuid VARCHAR(36) NOT NULL, timer_key VARCHAR(32) NOT NULL, expiry BIGINT NOT NULL DEFAULT 0, " +
                        "paused_remaining BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (uuid, timer_key))",
                "CREATE TABLE IF NOT EXISTS " + prefix + "global_timers (" +
                        "timer_key VARCHAR(32) PRIMARY KEY, expiry BIGINT NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS " + prefix + "kv (" +
                        "k VARCHAR(64) PRIMARY KEY, v TEXT)"
        };
        synchronized (this) {
            try (Statement st = connection.createStatement()) {
                for (String sql : ddl) {
                    st.execute(sql);
                }
            }
        }
    }

    /** Execute une mise a jour de maniere synchrone. */
    public synchronized void update(String sql, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur SQL (" + sql + ") : " + e.getMessage());
        }
    }

    /** Execute une mise a jour hors du thread principal si possible. */
    public void updateAsync(String sql, Object... params) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> update(sql, params));
        } else {
            update(sql, params);
        }
    }

    /** Execute une requete et transforme le ResultSet complet. */
    public synchronized <T> T query(String sql, ResultReader<T> reader, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return reader.read(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur SQL (" + sql + ") : " + e.getMessage());
            return null;
        }
    }

    private void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    // ------------------------------------------------------------------
    // Cle-valeur partage (restauration des evenements, etc.)
    // ------------------------------------------------------------------

    public void setKv(String key, String value) {
        if (value == null) {
            update("DELETE FROM " + prefix + "kv WHERE k = ?", key);
        } else {
            update("REPLACE INTO " + prefix + "kv (k, v) VALUES (?, ?)", key, value);
        }
    }

    public String getKv(String key) {
        return query("SELECT v FROM " + prefix + "kv WHERE k = ?",
                rs -> rs.next() ? rs.getString(1) : null, key);
    }

    public void close() {
        synchronized (this) {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Fermeture de la base impossible : " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // DatabaseService
    // ------------------------------------------------------------------

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public boolean isMySql() {
        return mysql;
    }

    @Override
    public String getTablePrefix() {
        return prefix;
    }
}
