package fr.orbitversus.history;

import fr.orbitversus.db.VersusDb;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Journalise et relit les combats récents d'un joueur. */
public final class HistoryStore {

    /** Une ligne d'historique côté joueur. */
    public record Record(long ts, String mode, String opponents, boolean won, int scoreSelf, int scoreOpp) {}

    private final VersusDb db;

    public HistoryStore(VersusDb db) {
        this.db = db;
    }

    /** Enregistre le combat pour un joueur (une ligne par participant). */
    public void record(UUID player, long ts, String mode, String opponents,
                       boolean won, int scoreSelf, int scoreOpp) {
        db.exec(() -> {
            String sql = "INSERT INTO versus_history (player, ts, mode, opponents, won, score_self, score_opp) "
                    + "VALUES (?,?,?,?,?,?,?)";
            try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
                ps.setString(1, player.toString());
                ps.setLong(2, ts);
                ps.setString(3, mode);
                ps.setString(4, opponents);
                ps.setInt(5, won ? 1 : 0);
                ps.setInt(6, scoreSelf);
                ps.setInt(7, scoreOpp);
                ps.executeUpdate();
            } catch (SQLException e) {
                db.warn("Écriture historique impossible : " + e.getMessage());
            }
        });
    }

    public CompletableFuture<List<Record>> recent(UUID player, int limit) {
        return db.query(() -> {
            List<Record> list = new ArrayList<>();
            String sql = "SELECT ts, mode, opponents, won, score_self, score_opp FROM versus_history "
                    + "WHERE player = ? ORDER BY ts DESC LIMIT ?";
            try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
                ps.setString(1, player.toString());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new Record(rs.getLong("ts"), rs.getString("mode"), rs.getString("opponents"),
                                rs.getInt("won") != 0, rs.getInt("score_self"), rs.getInt("score_opp")));
                    }
                }
            } catch (SQLException e) {
                db.warn("Lecture historique impossible : " + e.getMessage());
            }
            return list;
        });
    }
}
