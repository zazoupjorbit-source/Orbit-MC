package fr.orbitversus.db;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Persistance SQLite du Mode Versus. Toute I/O passe par un thread dédié (sérialisé, jamais sur
 * le thread principal). Requêtes 100 % paramétrées côté appelants. Tables créées au démarrage.
 */
public final class VersusDb {

    private final Plugin plugin;
    private final Logger logger;
    private final Connection connection;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OrbitVersus-DB");
        t.setDaemon(true);
        return t;
    });

    public VersusDb(Plugin plugin) throws SQLException {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            logger.warning("Impossible de créer le dossier de données.");
        }
        File db = new File(folder, "versus.db");
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
        createSchema();
    }

    private void createSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS versus_elo ("
                    + "uuid TEXT PRIMARY KEY, name TEXT, elo INTEGER NOT NULL DEFAULT 1000, "
                    + "wins INTEGER NOT NULL DEFAULT 0, losses INTEGER NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS versus_arena ("
                    + "name TEXT PRIMARY KEY, world TEXT NOT NULL, "
                    + "ax REAL, ay REAL, az REAL, ayaw REAL, "
                    + "bx REAL, by REAL, bz REAL, byaw REAL)");
            st.execute("CREATE TABLE IF NOT EXISTS versus_history ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, player TEXT NOT NULL, ts INTEGER NOT NULL, "
                    + "mode TEXT NOT NULL, opponents TEXT NOT NULL, won INTEGER NOT NULL, "
                    + "score_self INTEGER NOT NULL, score_opp INTEGER NOT NULL)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_history_player ON versus_history (player, ts)");
        }
    }

    public Connection connection() {
        return connection;
    }

    /** Exécute une tâche I/O hors du thread principal. */
    public void exec(Runnable task) {
        io.execute(task);
    }

    /** Exécute une requête hors du thread principal et renvoie le résultat (future). */
    public <T> CompletableFuture<T> query(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, io);
    }

    /** Revient sur le thread principal (API Bukkit). */
    public void main(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void warn(String message) {
        logger.warning(message);
    }

    public void close() {
        io.shutdown();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.warning("Fermeture base impossible : " + e.getMessage());
        }
    }
}
