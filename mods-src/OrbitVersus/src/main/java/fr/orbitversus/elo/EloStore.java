package fr.orbitversus.elo;

import fr.orbitversus.db.VersusDb;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classement Elo persistant. Elo de départ 1000, K=32. Pour le 2v2, on utilise l'Elo moyen d'équipe.
 * Cache mémoire (chargé à la connexion), écriture async.
 */
public final class EloStore {

    public static final int START_ELO = 1000;
    private static final int K = 32;

    /** Ligne de classement d'un joueur. */
    public static final class Entry {
        public String name;
        public int elo;
        public int wins;
        public int losses;

        public Entry(String name, int elo, int wins, int losses) {
            this.name = name;
            this.elo = elo;
            this.wins = wins;
            this.losses = losses;
        }
    }

    private final VersusDb db;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    public EloStore(VersusDb db) {
        this.db = db;
    }

    public void load(UUID uuid, String name) {
        db.query(() -> {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT elo, wins, losses FROM versus_elo WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Entry(name, rs.getInt("elo"), rs.getInt("wins"), rs.getInt("losses"));
                    }
                }
            } catch (SQLException e) {
                db.warn("Chargement Elo impossible : " + e.getMessage());
            }
            return new Entry(name, START_ELO, 0, 0);
        }).thenAccept(entry -> cache.put(uuid, entry));
    }

    public void unload(UUID uuid) {
        cache.remove(uuid);
    }

    public Entry get(UUID uuid) {
        return cache.getOrDefault(uuid, new Entry("?", START_ELO, 0, 0));
    }

    public int elo(UUID uuid) {
        return get(uuid).elo;
    }

    private static double expected(double me, double opp) {
        return 1.0 / (1.0 + Math.pow(10, (opp - me) / 400.0));
    }

    private static int teamAvg(List<UUID> team, EloStore store) {
        if (team.isEmpty()) {
            return START_ELO;
        }
        long sum = 0;
        for (UUID u : team) {
            sum += store.elo(u);
        }
        return (int) (sum / team.size());
    }

    /** Applique le résultat d'un match : met à jour Elo + victoires/défaites, persiste. */
    public void applyMatch(List<UUID> winners, List<UUID> losers) {
        int winTeam = teamAvg(winners, this);
        int loseTeam = teamAvg(losers, this);
        double eWin = expected(winTeam, loseTeam);
        int deltaWin = (int) Math.round(K * (1.0 - eWin));
        int deltaLose = (int) Math.round(K * (0.0 - (1.0 - eWin)));

        for (UUID u : winners) {
            Entry e = cache.computeIfAbsent(u, k -> new Entry("?", START_ELO, 0, 0));
            e.elo = Math.max(0, e.elo + deltaWin);
            e.wins++;
            persist(u, e);
        }
        for (UUID u : losers) {
            Entry e = cache.computeIfAbsent(u, k -> new Entry("?", START_ELO, 0, 0));
            e.elo = Math.max(0, e.elo + deltaLose);
            e.losses++;
            persist(u, e);
        }
    }

    private void persist(UUID uuid, Entry e) {
        db.exec(() -> {
            String sql = "INSERT INTO versus_elo (uuid, name, elo, wins, losses) VALUES (?,?,?,?,?) "
                    + "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, elo=excluded.elo, "
                    + "wins=excluded.wins, losses=excluded.losses";
            try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, e.name);
                ps.setInt(3, e.elo);
                ps.setInt(4, e.wins);
                ps.setInt(5, e.losses);
                ps.executeUpdate();
            } catch (SQLException ex) {
                db.warn("Sauvegarde Elo impossible : " + ex.getMessage());
            }
        });
    }

    public CompletableFuture<List<Entry>> top(int limit) {
        return db.query(() -> {
            List<Entry> list = new ArrayList<>();
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT name, elo, wins, losses FROM versus_elo ORDER BY elo DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new Entry(rs.getString("name"), rs.getInt("elo"),
                                rs.getInt("wins"), rs.getInt("losses")));
                    }
                }
            } catch (SQLException e) {
                db.warn("Classement Elo impossible : " + e.getMessage());
            }
            return list;
        });
    }
}
