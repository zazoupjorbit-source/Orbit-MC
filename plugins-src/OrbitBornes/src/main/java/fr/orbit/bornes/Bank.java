package fr.orbit.bornes;

import fr.orbit.casino.Db;
import fr.orbit.casino.OrbitCasino;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Pont vers OrbitCasino : sa base (orbit_mc) et son portefeuille d'Orbit Coins. Seule classe qui touche aux classes
 * du casino : elle n'est créée que si OrbitCasino est chargé.
 * <ul>
 *   <li>arcade_jeux / arcade_scores : tableau des records de la salle (contrat docs\arcade-jeux.md) ;</li>
 *   <li>bornes_gains : OC versés par jour, par joueur et par jeu (plafond) ;</li>
 *   <li>bornes_cristaux : étoiles de chaque niveau de CRISTAUX D'ORBIT.</li>
 * </ul>
 */
final class Bank {

    /** Résultat d'une partie enregistrée : ancien record, OC versés, plafond atteint. */
    record Result(double best, int oc, boolean capped) {
    }

    private final OrbitBornes plugin;
    private final OrbitCasino casino;

    Bank(OrbitBornes plugin, OrbitCasino casino) {
        this.plugin = plugin;
        this.casino = casino;
    }

    private Db db() {
        return casino.db();
    }

    boolean available() {
        return db() != null && db().available();
    }

    /** Tables (mêmes schémas qu'Arcade.createSharedTables) et déclaration des 4 jeux au tableau des records. */
    void init() {
        if (!available()) {
            plugin.getLogger().warning("Base d'OrbitCasino injoignable : scores et Orbit Coins désactivés.");
            return;
        }
        db().run(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS arcade_jeux ("
                        + "jeu VARCHAR(16) NOT NULL PRIMARY KEY, titre VARCHAR(160) NOT NULL, unite VARCHAR(16) NOT NULL DEFAULT 'pts', "
                        + "ordre INT NOT NULL DEFAULT 100, actif TINYINT NOT NULL DEFAULT 1)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS arcade_scores ("
                        + "jeu VARCHAR(16) NOT NULL, uuid CHAR(36) NOT NULL, nom VARCHAR(16) NOT NULL, meilleur DOUBLE NOT NULL DEFAULT 0, "
                        + "parties INT NOT NULL DEFAULT 0, maj TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                        + "PRIMARY KEY (jeu, uuid), KEY idx_jeu_meilleur (jeu, meilleur))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS bornes_gains ("
                        + "uuid CHAR(36) NOT NULL, jeu VARCHAR(16) NOT NULL, jour DATE NOT NULL, gain INT NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY (uuid, jeu))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS bornes_cristaux ("
                        + "uuid CHAR(36) NOT NULL PRIMARY KEY, nom VARCHAR(16) NOT NULL, etoiles VARCHAR(255) NOT NULL DEFAULT '', "
                        + "total INT NOT NULL DEFAULT 0)");
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO arcade_jeux (jeu, titre, unite, ordre) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE titre = VALUES(titre), unite = VALUES(unite), ordre = VALUES(ordre)")) {
                for (GameType t : GameType.values()) {
                    ps.setString(1, t.id);
                    ps.setString(2, t.title());
                    ps.setString(3, t.unit);
                    ps.setInt(4, t.order);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return true;
        }).whenComplete((ok, err) -> {
            if (err == null) {
                plugin.getLogger().info("Bornes déclarées au tableau des records (arcade_jeux).");
            }
        });
    }

    /**
     * Enregistre une partie : meilleur score (arcade_scores), OC demandés limités par le plafond du jour, puis versés.
     * {@code done} est appelé sur le fil du serveur (null si la base est injoignable).
     */
    void record(UUID uuid, String name, GameType type, double score, int ocWanted, int cap, Consumer<Result> done) {
        if (!available()) {
            done.accept(null);
            return;
        }
        db().then(db().transaction(c -> {
            double best = 0;
            try (PreparedStatement ps = c.prepareStatement("SELECT meilleur FROM arcade_scores WHERE jeu = ? AND uuid = ?")) {
                ps.setString(1, type.id);
                ps.setString(2, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        best = rs.getDouble(1);
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO arcade_scores (jeu, uuid, nom, meilleur, parties) VALUES (?, ?, ?, ?, 1) "
                    + "ON DUPLICATE KEY UPDATE nom = VALUES(nom), meilleur = GREATEST(meilleur, VALUES(meilleur)), parties = parties + 1")) {
                ps.setString(1, type.id);
                ps.setString(2, uuid.toString());
                ps.setString(3, name);
                ps.setDouble(4, score);
                ps.executeUpdate();
            }
            int today = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT IF(jour = CURDATE(), gain, 0) FROM bornes_gains WHERE uuid = ? AND jeu = ? FOR UPDATE")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, type.id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        today = rs.getInt(1);
                    }
                }
            }
            int oc = Math.max(0, Math.min(ocWanted, cap - today));
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO bornes_gains (uuid, jeu, jour, gain) VALUES (?, ?, CURDATE(), ?) "
                    + "ON DUPLICATE KEY UPDATE jour = CURDATE(), gain = VALUES(gain)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, type.id);
                ps.setInt(3, today + oc);
                ps.executeUpdate();
            }
            return new Result(best, oc, oc < ocWanted);
        }), res -> {
            if (res.oc() > 0) {
                casino.wallet().credit(uuid, res.oc(), type.id, "score " + Math.round(score), false);
            }
            done.accept(res);
        }, () -> done.accept(null));
    }

    /** Les {@code limit} meilleurs : [nom, meilleur]. */
    void top(GameType type, int limit, Consumer<List<String[]>> done) {
        if (!available()) {
            done.accept(List.of());
            return;
        }
        db().then(db().run(c -> {
            List<String[]> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT nom, meilleur FROM arcade_scores WHERE jeu = ? AND meilleur > 0 ORDER BY meilleur DESC LIMIT ?")) {
                ps.setString(1, type.id);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new String[]{rs.getString(1), String.valueOf(Math.round(rs.getDouble(2)))});
                    }
                }
            }
            return list;
        }), done, () -> done.accept(List.of()));
    }

    /** Étoiles de chaque niveau de CRISTAUX (tableau de {@code levels} cases, 0 à 3). */
    void loadStars(UUID uuid, int levels, Consumer<int[]> done) {
        if (!available()) {
            done.accept(new int[levels]);
            return;
        }
        db().then(db().run(c -> {
            int[] stars = new int[levels];
            try (PreparedStatement ps = c.prepareStatement("SELECT etoiles FROM bornes_cristaux WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String s = rs.getString(1);
                        for (int i = 0; i < Math.min(levels, s.length()); i++) {
                            stars[i] = Math.max(0, Math.min(3, s.charAt(i) - '0'));
                        }
                    }
                }
            }
            return stars;
        }), done, () -> done.accept(new int[levels]));
    }

    void saveStars(UUID uuid, String name, int[] stars) {
        if (!available()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        int total = 0;
        for (int s : stars) {
            sb.append(s);
            total += s;
        }
        String value = sb.toString();
        int sum = total;
        db().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO bornes_cristaux (uuid, nom, etoiles, total) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE nom = VALUES(nom), etoiles = VALUES(etoiles), total = VALUES(total)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setString(3, value);
                ps.setInt(4, sum);
                ps.executeUpdate();
            }
            return true;
        });
    }
}
