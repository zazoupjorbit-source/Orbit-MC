package fr.orbitversus.arena;

import fr.orbitversus.db.VersusDb;
import org.bukkit.Location;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Gère les arènes : en mémoire (source de vérité), persistées en SQLite. */
public final class ArenaManager {

    private final VersusDb db;
    private final Map<String, Arena> arenas = new ConcurrentHashMap<>();

    public ArenaManager(VersusDb db) {
        this.db = db;
    }

    /** Charge toutes les arènes au démarrage (bloquant, appelé une fois). */
    public void loadAll() {
        try (PreparedStatement ps = db.connection().prepareStatement("SELECT * FROM versus_arena");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Arena a = new Arena(rs.getString("name"), rs.getString("world"),
                        rs.getDouble("ax"), rs.getDouble("ay"), rs.getDouble("az"), rs.getFloat("ayaw"),
                        rs.getDouble("bx"), rs.getDouble("by"), rs.getDouble("bz"), rs.getFloat("byaw"));
                arenas.put(a.name().toLowerCase(), a);
            }
        } catch (SQLException e) {
            db.warn("Chargement des arènes impossible : " + e.getMessage());
        }
    }

    public int count() {
        return arenas.size();
    }

    public List<Arena> all() {
        return new ArrayList<>(arenas.values());
    }

    public Arena get(String name) {
        return arenas.get(name.toLowerCase());
    }

    /** Crée/écrase une arène à partir de deux positions. */
    public void save(String name, Location a, Location b) {
        Arena arena = new Arena(name, a.getWorld().getName(),
                a.getX(), a.getY(), a.getZ(), a.getYaw(),
                b.getX(), b.getY(), b.getZ(), b.getYaw());
        arenas.put(name.toLowerCase(), arena);
        db.exec(() -> {
            String sql = "INSERT INTO versus_arena (name, world, ax, ay, az, ayaw, bx, by, bz, byaw) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?) "
                    + "ON CONFLICT(name) DO UPDATE SET world=excluded.world, "
                    + "ax=excluded.ax, ay=excluded.ay, az=excluded.az, ayaw=excluded.ayaw, "
                    + "bx=excluded.bx, by=excluded.by, bz=excluded.bz, byaw=excluded.byaw";
            try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
                ps.setString(1, arena.name());
                ps.setString(2, arena.world());
                ps.setDouble(3, arena.ax()); ps.setDouble(4, arena.ay()); ps.setDouble(5, arena.az());
                ps.setFloat(6, arena.ayaw());
                ps.setDouble(7, arena.bx()); ps.setDouble(8, arena.by()); ps.setDouble(9, arena.bz());
                ps.setFloat(10, arena.byaw());
                ps.executeUpdate();
            } catch (SQLException e) {
                db.warn("Sauvegarde arène impossible : " + e.getMessage());
            }
        });
    }

    public boolean remove(String name) {
        if (arenas.remove(name.toLowerCase()) == null) {
            return false;
        }
        db.exec(() -> {
            try (PreparedStatement ps = db.connection().prepareStatement("DELETE FROM versus_arena WHERE name = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            } catch (SQLException e) {
                db.warn("Suppression arène impossible : " + e.getMessage());
            }
        });
        return true;
    }

    /** Choisit une arène valide au hasard parmi celles non utilisées. */
    public Arena randomFree(java.util.Set<String> inUse) {
        List<Arena> free = new ArrayList<>();
        for (Arena a : arenas.values()) {
            if (a.isValid() && !inUse.contains(a.name().toLowerCase())) {
                free.add(a);
            }
        }
        if (free.isEmpty()) {
            return null;
        }
        return free.get(ThreadLocalRandom.current().nextInt(free.size()));
    }
}
