package fr.orbitgames.hcf.combat.storage;

import fr.orbitgames.hcf.api.service.DatabaseService;
import org.bukkit.plugin.Plugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Acces aux tables du module Combat via le stockage partage d'OrbitHCF-API.
 * Toutes les operations sont synchronisees sur le DatabaseService (connexion partagee).
 */
public class CombatDb {

    private final Plugin plugin;
    private final DatabaseService database;

    public CombatDb(Plugin plugin, DatabaseService database) {
        this.plugin = plugin;
        this.database = database;
    }

    private String prefix() {
        return database.getTablePrefix();
    }

    public void createTables() {
        String[] ddl = {
                "CREATE TABLE IF NOT EXISTS " + prefix() + "combat_deathbans ("
                        + "uuid VARCHAR(36) PRIMARY KEY, until_ts BIGINT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS " + prefix() + "combat_lives ("
                        + "uuid VARCHAR(36) PRIMARY KEY, lives INT NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS " + prefix() + "combat_loggers ("
                        + "uuid VARCHAR(36) PRIMARY KEY, logged_at BIGINT NOT NULL)"
        };
        synchronized (database) {
            try (Statement st = database.getConnection().createStatement()) {
                for (String sql : ddl) {
                    st.execute(sql);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Creation des tables Combat impossible : " + e.getMessage());
            }
        }
    }

    public void update(String sql, Object... params) {
        synchronized (database) {
            try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur SQL Combat (" + sql + ") : " + e.getMessage());
            }
        }
    }

    public long queryLong(String sql, long fallback, Object... params) {
        synchronized (database) {
            try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : fallback;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur SQL Combat (" + sql + ") : " + e.getMessage());
                return fallback;
            }
        }
    }

    // ------------------------------------------------------------------
    // Deathbans
    // ------------------------------------------------------------------

    public void setDeathban(UUID uuid, long until) {
        update("REPLACE INTO " + prefix() + "combat_deathbans (uuid, until_ts) VALUES (?, ?)",
                uuid.toString(), until);
    }

    public long getDeathbanUntil(UUID uuid) {
        return queryLong("SELECT until_ts FROM " + prefix() + "combat_deathbans WHERE uuid = ?",
                0L, uuid.toString());
    }

    public void removeDeathban(UUID uuid) {
        update("DELETE FROM " + prefix() + "combat_deathbans WHERE uuid = ?", uuid.toString());
    }

    // ------------------------------------------------------------------
    // Vies
    // ------------------------------------------------------------------

    public int getLives(UUID uuid) {
        return (int) queryLong("SELECT lives FROM " + prefix() + "combat_lives WHERE uuid = ?",
                0L, uuid.toString());
    }

    public void setLives(UUID uuid, int lives) {
        update("REPLACE INTO " + prefix() + "combat_lives (uuid, lives) VALUES (?, ?)",
                uuid.toString(), Math.max(0, lives));
    }

    // ------------------------------------------------------------------
    // Combat loggers
    // ------------------------------------------------------------------

    public void markLogger(UUID uuid) {
        update("REPLACE INTO " + prefix() + "combat_loggers (uuid, logged_at) VALUES (?, ?)",
                uuid.toString(), System.currentTimeMillis());
    }

    public boolean consumeLogger(UUID uuid) {
        long loggedAt = queryLong("SELECT logged_at FROM " + prefix() + "combat_loggers WHERE uuid = ?",
                0L, uuid.toString());
        if (loggedAt > 0L) {
            update("DELETE FROM " + prefix() + "combat_loggers WHERE uuid = ?", uuid.toString());
            return true;
        }
        return false;
    }
}
