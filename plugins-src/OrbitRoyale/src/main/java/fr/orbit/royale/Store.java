package fr.orbit.royale;

import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Sauvegarde du Siège Orbital : profils (royale_profils), déclaration au tableau des records de l'arcade
 * (arcade_jeux / arcade_scores, voir docs/arcade-jeux.md) et plafond quotidien d'Orbit Coins.
 */
final class Store {

    static final String JEU = "siege";

    record Result(int trophiesBefore, int trophies, int recordBefore, int record, long oc, long ocWanted, int cap) {
    }

    private final OrbitRoyale plugin;
    private final Db db;
    private final Map<UUID, Profile> cache = new ConcurrentHashMap<>();

    Store(OrbitRoyale plugin, Db db) {
        this.plugin = plugin;
        this.db = db;
    }

    void init() {
        if (!db.available()) {
            return;
        }
        db.run(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS royale_profils ("
                        + "uuid CHAR(36) NOT NULL PRIMARY KEY, nom VARCHAR(16) NOT NULL, "
                        + "trophees INT NOT NULL DEFAULT 0, record INT NOT NULL DEFAULT 0, "
                        + "victoires INT NOT NULL DEFAULT 0, defaites INT NOT NULL DEFAULT 0, nuls INT NOT NULL DEFAULT 0, "
                        + "deck VARCHAR(255) NULL, jour DATE NULL, gain_jour INT NOT NULL DEFAULT 0, "
                        + "maj TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                        + "KEY idx_trophees (trophees))");
                // tables partagées de l'arcade (même schéma qu'OrbitCasino, Arcade.createSharedTables)
                st.executeUpdate("CREATE TABLE IF NOT EXISTS arcade_jeux ("
                        + "jeu VARCHAR(16) NOT NULL PRIMARY KEY, titre VARCHAR(160) NOT NULL, unite VARCHAR(16) NOT NULL DEFAULT 'pts', "
                        + "ordre INT NOT NULL DEFAULT 100, actif TINYINT NOT NULL DEFAULT 1)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS arcade_scores ("
                        + "jeu VARCHAR(16) NOT NULL, uuid CHAR(36) NOT NULL, nom VARCHAR(16) NOT NULL, meilleur DOUBLE NOT NULL DEFAULT 0, "
                        + "parties INT NOT NULL DEFAULT 0, maj TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                        + "PRIMARY KEY (jeu, uuid), KEY idx_jeu_meilleur (jeu, meilleur))");
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO arcade_jeux (jeu, titre, unite, ordre) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE titre = VALUES(titre), unite = VALUES(unite), ordre = VALUES(ordre)")) {
                ps.setString(1, JEU);
                ps.setString(2, "<gradient:#3A7FFF:#E0B864><b>SIÈGE ORBITAL</b></gradient> <#5A6270>· trophées");
                ps.setString(3, "trophées");
                ps.setInt(4, 40);
                ps.executeUpdate();
            }
            return true;
        }).whenComplete((ok, err) -> {
            if (err == null) {
                plugin.getLogger().info("Siège Orbital déclaré au tableau des records de l'arcade.");
            }
        });
    }

    Profile cached(UUID uuid) {
        return cache.get(uuid);
    }

    void forget(UUID uuid) {
        cache.remove(uuid);
    }

    /** Profil du joueur (base, ou profil de départ non enregistré si la base est injoignable). */
    void load(Player p, Consumer<Profile> done) {
        Profile have = cache.get(p.getUniqueId());
        if (have != null) {
            done.accept(have);
            return;
        }
        UUID uuid = p.getUniqueId();
        String name = p.getName();
        Runnable offline = () -> {
            Profile pr = new Profile(uuid, name);
            pr.saved = false;
            pr.deckFromCsv(null);
            done.accept(pr);
        };
        if (!db.available()) {
            offline.run();
            return;
        }
        db.then(db.run(c -> {
            Profile pr = new Profile(uuid, name);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT trophees, record, victoires, defaites, nuls, deck FROM royale_profils WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        pr.trophies = rs.getInt(1);
                        pr.record = rs.getInt(2);
                        pr.wins = rs.getInt(3);
                        pr.losses = rs.getInt(4);
                        pr.draws = rs.getInt(5);
                        pr.deckFromCsv(rs.getString(6));
                        return pr;
                    }
                }
            }
            pr.deckFromCsv(null);
            try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO royale_profils (uuid, nom, deck) VALUES (?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setString(3, pr.deckCsv());
                ps.executeUpdate();
            }
            return pr;
        }), pr -> {
            cache.put(uuid, pr);
            done.accept(pr);
        }, offline);
    }

    void saveDeck(Profile pr) {
        if (!pr.saved || !db.available()) {
            return;
        }
        String csv = pr.deckCsv();
        db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE royale_profils SET deck = ? WHERE uuid = ?")) {
                ps.setString(1, csv);
                ps.setString(2, pr.uuid.toString());
                ps.executeUpdate();
            }
            return true;
        });
    }

    /**
     * Enregistre une partie : trophées, bilan, record, score d'arcade et Orbit Coins (plafond par jour).
     * outcome : 1 victoire, 0 égalité, -1 défaite.
     */
    void record(Profile pr, int outcome, int stars, Consumer<Result> done) {
        var cfg = plugin.getConfig();
        int plus = cfg.getInt("recompenses.trophees-victoire", 30), minus = cfg.getInt("recompenses.trophees-defaite", 15);
        int cap = cfg.getInt("recompenses.plafond-oc-jour", 600);
        long wanted = (outcome > 0 ? cfg.getInt("recompenses.oc-victoire", 60) : outcome == 0 ? 30 : cfg.getInt("recompenses.oc-defaite", 20))
                + (long) stars * cfg.getInt("recompenses.oc-par-etoile", 10);
        int before = pr.trophies, recBefore = pr.record;
        int after = Math.max(0, before + (outcome > 0 ? plus : outcome < 0 ? -minus : 0));
        pr.trophies = after;
        pr.record = Math.max(pr.record, after);
        if (outcome > 0) {
            pr.wins++;
        } else if (outcome < 0) {
            pr.losses++;
        } else {
            pr.draws++;
        }
        if (!pr.saved || !db.available()) {
            done.accept(new Result(before, after, recBefore, pr.record, 0, wanted, cap));
            return;
        }
        int rec = pr.record;
        db.then(db.transaction(c -> {
            int gainToday = 0;
            try (PreparedStatement ps = c.prepareStatement("SELECT gain_jour, jour = CURDATE() FROM royale_profils WHERE uuid = ? FOR UPDATE")) {
                ps.setString(1, pr.uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getBoolean(2)) {
                        gainToday = rs.getInt(1);
                    }
                }
            }
            long oc = Math.max(0, Math.min(wanted, cap - gainToday));
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO royale_profils (uuid, nom, trophees, record, victoires, defaites, nuls, deck, jour, gain_jour) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURDATE(), ?) ON DUPLICATE KEY UPDATE nom = VALUES(nom), trophees = VALUES(trophees), "
                    + "record = GREATEST(record, VALUES(record)), victoires = VALUES(victoires), defaites = VALUES(defaites), nuls = VALUES(nuls), "
                    + "deck = VALUES(deck), jour = CURDATE(), gain_jour = VALUES(gain_jour)")) {
                ps.setString(1, pr.uuid.toString());
                ps.setString(2, pr.name);
                ps.setInt(3, after);
                ps.setInt(4, rec);
                ps.setInt(5, pr.wins);
                ps.setInt(6, pr.losses);
                ps.setInt(7, pr.draws);
                ps.setString(8, pr.deckCsv());
                ps.setLong(9, gainToday + oc);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO arcade_scores (jeu, uuid, nom, meilleur, parties) VALUES (?, ?, ?, ?, 1) "
                    + "ON DUPLICATE KEY UPDATE nom = VALUES(nom), meilleur = GREATEST(meilleur, VALUES(meilleur)), parties = parties + 1")) {
                ps.setString(1, JEU);
                ps.setString(2, pr.uuid.toString());
                ps.setString(3, pr.name);
                ps.setDouble(4, after);
                ps.executeUpdate();
            }
            return oc;
        }), oc -> {
            if (oc > 0) {
                CasinoBridge.credit(pr.uuid, oc, (outcome > 0 ? "victoire " : outcome < 0 ? "défaite " : "égalité ") + stars + "★");
            }
            done.accept(new Result(before, after, recBefore, rec, CasinoBridge.available() ? oc : 0, wanted, cap));
        }, () -> done.accept(new Result(before, after, recBefore, rec, 0, wanted, cap)));
    }

    void top(Consumer<List<String[]>> done) {
        if (!db.available()) {
            done.accept(List.of());
            return;
        }
        db.then(db.run(c -> {
            List<String[]> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT nom, trophees, victoires FROM royale_profils WHERE trophees > 0 OR victoires > 0 ORDER BY trophees DESC, victoires DESC LIMIT 10");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new String[]{rs.getString(1), String.valueOf(rs.getInt(2)), String.valueOf(rs.getInt(3))});
                }
            }
            return list;
        }), done, () -> done.accept(List.of()));
    }
}
