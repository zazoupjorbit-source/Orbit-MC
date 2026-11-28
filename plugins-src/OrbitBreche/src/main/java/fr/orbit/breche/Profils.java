package fr.orbit.breche;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Profils BRÈCHE en base (table breche_joueurs) : agent préféré, statistiques, Orbit Coins gagnés dans la journée
 * (plafond par jour et par joueur, les OC ne s'achètent jamais), meilleur score de combat pour le tableau de l'arcade.
 */
final class Profils {

    private final OrbitBreche pl;
    private final Map<UUID, Agent> agents = new ConcurrentHashMap<>();

    Profils(OrbitBreche pl) {
        this.pl = pl;
    }

    void charger(Player p) {
        if (!pl.db().available()) {
            return;
        }
        UUID id = p.getUniqueId();
        pl.db().then(pl.db().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT agent FROM breche_joueurs WHERE uuid = ?")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : "";
                }
            }
        }), s -> {
            Agent a = Agent.of(s);
            if (a != null) {
                agents.put(id, a);
            }
        }, null);
    }

    Agent agentPrefere(Player p) {
        return agents.get(p.getUniqueId());
    }

    void retenirAgent(Player p, Agent a) {
        agents.put(p.getUniqueId(), a);
        if (!pl.db().available()) {
            return;
        }
        String id = p.getUniqueId().toString(), nom = p.getName();
        pl.db().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO breche_joueurs (uuid, nom, agent) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE nom = VALUES(nom), agent = VALUES(agent)")) {
                ps.setString(1, id);
                ps.setString(2, nom);
                ps.setString(3, a.id);
                return ps.executeUpdate();
            }
        });
    }

    /** Fin de partie d'un joueur : statistiques, score au tableau de l'arcade, Orbit Coins (plafond par jour). */
    void finPartie(Player p, Combattant c, boolean gagne, int manches) {
        if (!pl.db().available()) {
            Txt.send(p, "<#9AA4B2>Base indisponible : score non enregistré.");
            return;
        }
        String id = p.getUniqueId().toString(), nom = p.getName();
        int voulu = Math.max(0, c.score / Math.max(1, pl.getConfig().getInt("oc.points-par-oc", 25)) + (gagne ? pl.getConfig().getInt("oc.bonus-victoire", 50) : 15));
        int plafond = pl.getConfig().getInt("oc.plafond-jour", 1500);
        LocalDate auj = LocalDate.now();
        pl.db().then(pl.db().transaction(cn -> {
            int deja = 0;
            try (PreparedStatement ps = cn.prepareStatement("SELECT jour, gain_jour FROM breche_joueurs WHERE uuid = ? FOR UPDATE")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getDate(1) != null && rs.getDate(1).toLocalDate().equals(auj)) {
                        deja = rs.getInt(2);
                    }
                }
            }
            int gain = Math.max(0, Math.min(voulu, plafond - deja));
            try (PreparedStatement ps = cn.prepareStatement("INSERT INTO breche_joueurs (uuid, nom, parties, victoires, eliminations, morts, manches, poses, "
                    + "desamorcages, tetes, agent, jour, gain_jour) VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE nom = VALUES(nom), "
                    + "parties = parties + 1, victoires = victoires + VALUES(victoires), eliminations = eliminations + VALUES(eliminations), "
                    + "morts = morts + VALUES(morts), manches = manches + VALUES(manches), poses = poses + VALUES(poses), "
                    + "desamorcages = desamorcages + VALUES(desamorcages), tetes = tetes + VALUES(tetes), agent = VALUES(agent), "
                    + "gain_jour = IF(jour = VALUES(jour), gain_jour + ?, ?), jour = VALUES(jour)")) {
                ps.setString(1, id);
                ps.setString(2, nom);
                ps.setInt(3, gagne ? 1 : 0);
                ps.setInt(4, c.kills);
                ps.setInt(5, c.morts);
                ps.setInt(6, manches);
                ps.setInt(7, c.poses);
                ps.setInt(8, c.desamorcages);
                ps.setInt(9, c.tetes);
                ps.setString(10, c.agent.id);
                ps.setDate(11, java.sql.Date.valueOf(auj));
                ps.setInt(12, gain);
                ps.setInt(13, gain);
                ps.setInt(14, gain);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = cn.prepareStatement("INSERT INTO arcade_scores (jeu, uuid, nom, meilleur, parties) VALUES ('breche', ?, ?, ?, 1) "
                    + "ON DUPLICATE KEY UPDATE nom = VALUES(nom), meilleur = GREATEST(meilleur, VALUES(meilleur)), parties = parties + 1")) {
                ps.setString(1, id);
                ps.setString(2, nom);
                ps.setDouble(3, c.score);
                ps.executeUpdate();
            }
            return gain;
        }), gain -> {
            if (gain > 0 && Coins.credit(pl, p.getUniqueId(), gain, "breche " + c.score + " pts" + (gagne ? " victoire" : ""))) {
                Txt.send(p, "Gain : " + Txt.oc(gain) + " <#9AA4B2>(" + Txt.n(c.score) + " pts" + (gagne ? ", victoire" : "") + ").");
            } else if (gain <= 0 && voulu > 0) {
                Txt.send(p, "<#9AA4B2>Plafond du jour atteint (" + Txt.n(plafond) + " OC) : score enregistré, sans Orbit Coins.");
            }
        }, () -> Txt.send(p, "<#E0484A>Score non enregistré (base injoignable)."));
    }

    void top(CommandSender to) {
        if (!pl.db().available()) {
            Txt.send(to, "<#9AA4B2>Classement indisponible (base injoignable).");
            return;
        }
        pl.db().then(pl.db().run(c -> {
            List<String[]> l = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT s.nom, s.meilleur, s.parties, COALESCE(j.victoires, 0), COALESCE(j.eliminations, 0), "
                    + "COALESCE(j.morts, 0) FROM arcade_scores s LEFT JOIN breche_joueurs j ON j.uuid = s.uuid WHERE s.jeu = 'breche' "
                    + "ORDER BY s.meilleur DESC LIMIT 10")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        l.add(new String[]{rs.getString(1), String.valueOf((long) rs.getDouble(2)), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)});
                    }
                }
            }
            return l;
        }), list -> {
            to.sendMessage(Txt.mm(Txt.TITLE + " <#5A6270>·</#5A6270> <#E6EDF3>meilleurs scores de combat"));
            if (list.isEmpty()) {
                to.sendMessage(Txt.mm("  <#9AA4B2>Personne encore… à toi l'honneur !"));
            }
            int n = 1;
            for (String[] r : list) {
                to.sendMessage(Txt.mm("  <#5A6270>" + n++ + ".</#5A6270> <#E6EDF3>" + r[0] + "</#E6EDF3> <#E0B864>" + Txt.n(Long.parseLong(r[1]))
                        + " pts</#E0B864> <#5A6270>· " + r[2] + " parties · " + r[3] + " victoires · " + r[4] + "/" + r[5] + " K/M"));
            }
        }, () -> Txt.send(to, "<#E0484A>Classement indisponible."));
    }
}
