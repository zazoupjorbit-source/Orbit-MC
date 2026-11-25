package fr.orbit.chasseurs;

import fr.orbit.chasseurs.Catalogue.Mode;
import fr.orbit.chasseurs.Catalogue.Paint;
import fr.orbit.chasseurs.Catalogue.ShipType;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Progression des pilotes (table chasseurs_pilotes) : expérience, déblocages, choix du hangar, plafond d'OC du jour. */
final class Profiles {

    static final class Profile {
        final UUID uuid;
        String name;
        int xp, games, wins, kills, deaths, bestWave, botLevel = 2;
        ShipType ship = ShipType.FLECHE;
        Paint paint = Paint.ORBIT;

        Profile(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        int level() {
            return levelOf(xp);
        }

        boolean has(ShipType t) {
            return level() >= t.level;
        }
    }

    record Outcome(int oc, double bestBefore, int levelBefore, int levelAfter, int waveBefore, Profile profile, boolean capped) {
    }

    static int levelOf(int xp) {
        return (int) Math.floor((Math.sqrt(1 + 8 * Math.max(0, xp) / 250.0) - 1) / 2) + 1;
    }

    static int xpFor(int level) {
        return 250 * (level - 1) * level / 2;
    }

    private final OrbitChasseurs plugin;
    private final Map<UUID, Profile> cache = new ConcurrentHashMap<>();

    Profiles(OrbitChasseurs plugin) {
        this.plugin = plugin;
    }

    Profile cached(Player p) {
        return cache.computeIfAbsent(p.getUniqueId(), u -> new Profile(u, p.getName()));
    }

    void forget(UUID uuid) {
        cache.remove(uuid);
    }

    /** Charge le profil (ou le crée en mémoire) puis rappelle sur le fil du serveur. Sans base : profil vierge. */
    void load(Player p, Consumer<Profile> done) {
        UUID uuid = p.getUniqueId();
        String name = p.getName();
        if (!plugin.db().available()) {
            done.accept(cached(p));
            return;
        }
        plugin.db().then(plugin.db().run(c -> {
            Profile pr = new Profile(uuid, name);
            try (PreparedStatement ps = c.prepareStatement("SELECT xp, parties, victoires, eliminations, morts, vague_max, vaisseau, couleur, niveau_bots "
                    + "FROM chasseurs_pilotes WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        read(pr, rs);
                    }
                }
            }
            return pr;
        }), pr -> {
            cache.put(uuid, pr);
            done.accept(pr);
        }, () -> done.accept(cached(p)));
    }

    private static void read(Profile pr, ResultSet rs) throws java.sql.SQLException {
        pr.xp = rs.getInt(1);
        pr.games = rs.getInt(2);
        pr.wins = rs.getInt(3);
        pr.kills = rs.getInt(4);
        pr.deaths = rs.getInt(5);
        pr.bestWave = rs.getInt(6);
        pr.ship = ShipType.of(rs.getString(7));
        pr.paint = Paint.of(rs.getString(8));
        pr.botLevel = Math.max(1, Math.min(4, rs.getInt(9)));
        if (!pr.has(pr.ship)) {
            pr.ship = ShipType.FLECHE;
        }
        if (!pr.paint.unlocked(pr)) {
            pr.paint = Paint.ORBIT;
        }
    }

    /** Enregistre les choix du hangar et le niveau des bots. */
    void saveChoices(Profile pr) {
        if (!plugin.db().available()) {
            return;
        }
        String ship = pr.ship.id, paint = pr.paint.id, name = pr.name;
        int bots = pr.botLevel;
        plugin.db().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO chasseurs_pilotes (uuid, nom, vaisseau, couleur, niveau_bots) VALUES (?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE nom = VALUES(nom), vaisseau = VALUES(vaisseau), couleur = VALUES(couleur), niveau_bots = VALUES(niveau_bots)")) {
                ps.setString(1, pr.uuid.toString());
                ps.setString(2, name);
                ps.setString(3, ship);
                ps.setString(4, paint);
                ps.setInt(5, bots);
                ps.executeUpdate();
            }
            return true;
        });
    }

    /** Fin de partie : progression, record de la salle (arcade_scores) et OC accordés sous le plafond du jour. */
    void record(UUID uuid, String name, Mode mode, int score, int kills, int deaths, boolean win, int wave, int ocWanted,
                Consumer<Outcome> done, Runnable failed) {
        if (!plugin.db().available()) {
            if (failed != null) {
                failed.run();
            }
            return;
        }
        int cap = plugin.getConfig().getInt("oc.plafond-jour", 1500);
        plugin.db().then(plugin.db().transaction(c -> {
            Profile pr = new Profile(uuid, name);
            int gainToday = 0;
            try (PreparedStatement ps = c.prepareStatement("SELECT xp, parties, victoires, eliminations, morts, vague_max, vaisseau, couleur, niveau_bots, "
                    + "gain_jour, jour = CURDATE() FROM chasseurs_pilotes WHERE uuid = ? FOR UPDATE")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        read(pr, rs);
                        gainToday = rs.getBoolean(11) ? rs.getInt(10) : 0;
                    }
                }
            }
            int levelBefore = pr.level(), waveBefore = pr.bestWave;
            int oc = Math.max(0, Math.min(ocWanted, cap - gainToday));
            pr.xp += Math.max(0, score);
            pr.games++;
            pr.wins += win ? 1 : 0;
            pr.kills += kills;
            pr.deaths += deaths;
            pr.bestWave = Math.max(pr.bestWave, wave);
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO chasseurs_pilotes (uuid, nom, xp, parties, victoires, eliminations, morts, vague_max, "
                    + "vaisseau, couleur, niveau_bots, jour, gain_jour) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURDATE(), ?) ON DUPLICATE KEY UPDATE "
                    + "nom = VALUES(nom), xp = VALUES(xp), parties = VALUES(parties), victoires = VALUES(victoires), eliminations = VALUES(eliminations), "
                    + "morts = VALUES(morts), vague_max = VALUES(vague_max), jour = CURDATE(), gain_jour = VALUES(gain_jour)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setInt(3, pr.xp);
                ps.setInt(4, pr.games);
                ps.setInt(5, pr.wins);
                ps.setInt(6, pr.kills);
                ps.setInt(7, pr.deaths);
                ps.setInt(8, pr.bestWave);
                ps.setString(9, pr.ship.id);
                ps.setString(10, pr.paint.id);
                ps.setInt(11, pr.botLevel);
                ps.setInt(12, gainToday + oc);
                ps.executeUpdate();
            }
            double best = 0;
            try (PreparedStatement ps = c.prepareStatement("SELECT meilleur FROM arcade_scores WHERE jeu = ? AND uuid = ?")) {
                ps.setString(1, mode.jeu);
                ps.setString(2, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        best = rs.getDouble(1);
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO arcade_scores (jeu, uuid, nom, meilleur, parties) VALUES (?, ?, ?, ?, 1) "
                    + "ON DUPLICATE KEY UPDATE nom = VALUES(nom), meilleur = GREATEST(meilleur, VALUES(meilleur)), parties = parties + 1")) {
                ps.setString(1, mode.jeu);
                ps.setString(2, uuid.toString());
                ps.setString(3, name);
                ps.setDouble(4, score);
                ps.executeUpdate();
            }
            return new Outcome(oc, best, levelBefore, pr.level(), waveBefore, pr, oc < ocWanted);
        }), out -> {
            Profile old = cache.get(uuid);
            if (old != null) {
                // les choix faits au hangar pendant l'écriture restent ceux du joueur
                out.profile().ship = old.ship;
                out.profile().paint = old.paint;
                out.profile().botLevel = old.botLevel;
            }
            cache.put(uuid, out.profile());
            done.accept(out);
        }, failed);
    }

    void top(Mode mode, Consumer<List<String[]>> done) {
        if (!plugin.db().available()) {
            done.accept(List.of());
            return;
        }
        plugin.db().then(plugin.db().run(c -> {
            List<String[]> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT nom, meilleur, parties FROM arcade_scores WHERE jeu = ? AND meilleur > 0 ORDER BY meilleur DESC LIMIT 10")) {
                ps.setString(1, mode.jeu);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new String[]{rs.getString(1), String.valueOf((long) rs.getDouble(2)), String.valueOf(rs.getInt(3))});
                    }
                }
            }
            return list;
        }), done, () -> done.accept(List.of()));
    }
}
