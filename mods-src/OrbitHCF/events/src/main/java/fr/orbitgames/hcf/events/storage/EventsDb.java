package fr.orbitgames.hcf.events.storage;

import fr.orbitgames.hcf.api.service.DatabaseService;
import org.bukkit.plugin.Plugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistance du module Events : classement des captures, historique
 * de la Citadel, etat des evenements (restauration apres redemarrage).
 */
public class EventsDb {

    /** Ligne du classement des captures. */
    public record CaptureEntry(String factionName, int captures) {
    }

    /** Ligne de l'historique Citadel. */
    public record CitadelEntry(long wonAt, String factionName) {
    }

    private final Plugin plugin;
    private final DatabaseService database;

    public EventsDb(Plugin plugin, DatabaseService database) {
        this.plugin = plugin;
        this.database = database;
    }

    private String prefix() {
        return database.getTablePrefix();
    }

    public void createTables() {
        String[] ddl = {
                "CREATE TABLE IF NOT EXISTS " + prefix() + "events_captures ("
                        + "faction_id VARCHAR(36) PRIMARY KEY, faction_name VARCHAR(32), "
                        + "captures INT NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS " + prefix() + "events_citadel_history ("
                        + "won_at BIGINT PRIMARY KEY, faction_name VARCHAR(32))",
                "CREATE TABLE IF NOT EXISTS " + prefix() + "events_state ("
                        + "k VARCHAR(64) PRIMARY KEY, v TEXT)"
        };
        synchronized (database) {
            try (Statement st = database.getConnection().createStatement()) {
                for (String sql : ddl) {
                    st.execute(sql);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Creation des tables Events impossible : " + e.getMessage());
            }
        }
    }

    private void update(String sql, Object... params) {
        synchronized (database) {
            try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur SQL Events (" + sql + ") : " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // Classement des captures
    // ------------------------------------------------------------------

    public void incrementCaptures(UUID factionId, String factionName) {
        synchronized (database) {
            int current = 0;
            try (PreparedStatement ps = database.getConnection().prepareStatement(
                    "SELECT captures FROM " + prefix() + "events_captures WHERE faction_id = ?")) {
                ps.setString(1, factionId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        current = rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur SQL Events : " + e.getMessage());
            }
            update("REPLACE INTO " + prefix() + "events_captures (faction_id, faction_name, captures) "
                    + "VALUES (?, ?, ?)", factionId.toString(), factionName, current + 1);
        }
    }

    public List<CaptureEntry> topCaptures(int limit) {
        List<CaptureEntry> result = new ArrayList<>();
        synchronized (database) {
            try (PreparedStatement ps = database.getConnection().prepareStatement(
                    "SELECT faction_name, captures FROM " + prefix()
                            + "events_captures ORDER BY captures DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new CaptureEntry(rs.getString(1), rs.getInt(2)));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur SQL Events : " + e.getMessage());
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Historique Citadel
    // ------------------------------------------------------------------

    public void addCitadelWin(String factionName) {
        update("REPLACE INTO " + prefix() + "events_citadel_history (won_at, faction_name) VALUES (?, ?)",
                System.currentTimeMillis(), factionName);
    }

    public List<CitadelEntry> citadelHistory(int limit) {
        List<CitadelEntry> result = new ArrayList<>();
        synchronized (database) {
            try (PreparedStatement ps = database.getConnection().prepareStatement(
                    "SELECT won_at, faction_name FROM " + prefix()
                            + "events_citadel_history ORDER BY won_at DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new CitadelEntry(rs.getLong(1), rs.getString(2)));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur SQL Events : " + e.getMessage());
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Etat des evenements (restauration apres redemarrage)
    // ------------------------------------------------------------------

    public void setState(String key, String value) {
        if (value == null) {
            update("DELETE FROM " + prefix() + "events_state WHERE k = ?", key);
        } else {
            update("REPLACE INTO " + prefix() + "events_state (k, v) VALUES (?, ?)", key, value);
        }
    }

    public String getState(String key) {
        synchronized (database) {
            try (PreparedStatement ps = database.getConnection().prepareStatement(
                    "SELECT v FROM " + prefix() + "events_state WHERE k = ?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur SQL Events : " + e.getMessage());
                return null;
            }
        }
    }
}
