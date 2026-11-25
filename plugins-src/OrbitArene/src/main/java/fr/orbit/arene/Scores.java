package fr.orbit.arene;

import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Records et gains : chaque mode se déclare dans {@code arcade_jeux} (tableau des records de la salle, relu par
 * OrbitCasino chaque minute), écrit le meilleur score de chaque joueur dans {@code arcade_scores} et verse ses
 * Orbit Coins par OrbitCasino avec un plafond par jour, par joueur et par jeu ({@code arene_gains}).
 */
public final class Scores {

    private final OrbitArene pl;

    Scores(OrbitArene pl) {
        this.pl = pl;
    }

    void declarer() {
        if (!pl.db().available()) {
            return;
        }
        Set<String> faits = new HashSet<>();
        List<Jeu.Mode> modes = new ArrayList<>();
        for (Jeu j : Jeu.values()) {
            for (Jeu.Mode m : j.modes()) {
                if (faits.add(m.code())) {
                    modes.add(m);
                }
            }
        }
        pl.db().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO arcade_jeux (jeu, titre, unite, ordre) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE titre = VALUES(titre), unite = VALUES(unite), ordre = VALUES(ordre)")) {
                for (Jeu.Mode m : modes) {
                    ps.setString(1, m.code());
                    ps.setString(2, m.titre());
                    ps.setString(3, m.unite());
                    ps.setInt(4, m.ordre());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return true;
        });
    }

    public int plafond(Jeu.Mode m) {
        String path = switch (m.jeu()) {
            case NEBULEUSE -> "nebuleuse." + m.id() + ".plafond-oc-jour";
            case CHARGE -> "charge.plafond-oc-jour";
            case PALET -> "palet.plafond-oc-jour";
            case TURBO -> "turbo.plafond-oc-jour";
        };
        return pl.getConfig().getInt(path, 600);
    }

    /** Enregistre la partie d'un joueur, verse les OC (plafond du jour) et lui affiche le bilan avec les boutons. */
    public void enregistrer(Player p, Jeu.Mode m, double score, int ocBrut, String resume) {
        UUID uuid = p.getUniqueId();
        String nom = p.getName();
        int cap = plafond(m);
        if (!pl.db().available()) {
            bilan(p, m, score, 0, ocBrut, -1, cap, "<#E0484A>(base injoignable : rien d'enregistré)");
            return;
        }
        pl.db().then(pl.db().transaction(c -> {
            int deja = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT gain FROM arene_gains WHERE uuid = ? AND jeu = ? AND jour = CURDATE() FOR UPDATE")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, m.code());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        deja = rs.getInt(1);
                    }
                }
            }
            int oc = Math.max(0, Math.min(ocBrut, cap - deja));
            if (oc > 0) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO arene_gains (uuid, jeu, jour, gain) VALUES (?, ?, CURDATE(), ?) "
                        + "ON DUPLICATE KEY UPDATE gain = gain + VALUES(gain)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, m.code());
                    ps.setInt(3, oc);
                    ps.executeUpdate();
                }
            }
            double ancien = -1;
            try (PreparedStatement ps = c.prepareStatement("SELECT meilleur FROM arcade_scores WHERE jeu = ? AND uuid = ?")) {
                ps.setString(1, m.code());
                ps.setString(2, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        ancien = rs.getDouble(1);
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO arcade_scores (jeu, uuid, nom, meilleur, parties) VALUES (?, ?, ?, ?, 1) "
                    + "ON DUPLICATE KEY UPDATE nom = VALUES(nom), meilleur = GREATEST(meilleur, VALUES(meilleur)), parties = parties + 1")) {
                ps.setString(1, m.code());
                ps.setString(2, uuid.toString());
                ps.setString(3, nom);
                ps.setDouble(4, Math.max(0, score));
                ps.executeUpdate();
            }
            return new double[]{oc, ancien};
        }), r -> {
            int oc = (int) r[0];
            if (oc > 0) {
                Caisse.crediter(uuid, oc, m.code(), resume.length() > 60 ? resume.substring(0, 60) : resume);
            }
            if (p.isOnline()) {
                bilan(p, m, score, oc, ocBrut, r[1], cap, "");
            }
        }, () -> {
            if (p.isOnline()) {
                bilan(p, m, score, 0, ocBrut, -1, cap, "<#E0484A>(erreur de base : rien d'enregistré)");
            }
        });
    }

    private void bilan(Player p, Jeu.Mode m, double score, int oc, int ocBrut, double ancien, int cap, String note) {
        boolean record = ancien >= 0 && score > ancien || ancien < 0 && score > 0 && note.isEmpty();
        Txt.send(p, m.jeu().grad("<b>" + m.jeu().nom + "</b>") + " <#5A6270>·</#5A6270> " + m.nom() + " · <b>"
                + Txt.n(Math.round(score)) + "</b> " + m.unite() + " → " + (oc > 0 ? Txt.oc(oc) : "<#9AA4B2>0 OC")
                + (oc < ocBrut && note.isEmpty() ? " <#9AA4B2>(plafond de " + Txt.n(cap) + " OC par jour atteint)" : "")
                + (record ? "  <#7CFF8A><b>NOUVEAU RECORD !</b>" : ancien >= 0 ? " <#5A6270>· record " + Txt.n(Math.round(ancien)) : "")
                + (note.isEmpty() ? "" : " " + note));
        p.sendMessage(Txt.mm("  ").append(Txt.button("Rejouer", m.jeu().c1, "/arene rejouer", "Relancer " + m.jeu().nom + " · " + m.nom()))
                .append(Txt.mm(" ")).append(Txt.button("Classement", "#E0B864", "/arene top " + m.jeu().id + " " + m.id(), "Les 10 meilleurs")));
        if (record) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        }
    }

    public void top(CommandSender to, Jeu.Mode m) {
        if (!pl.db().available()) {
            Txt.send(to, "<#E0484A>Base injoignable.");
            return;
        }
        pl.db().then(pl.db().run(c -> {
            List<String[]> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT nom, meilleur, parties FROM arcade_scores WHERE jeu = ? AND meilleur > 0 ORDER BY meilleur DESC LIMIT 10")) {
                ps.setString(1, m.code());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new String[]{rs.getString(1), String.valueOf(Math.round(rs.getDouble(2))), String.valueOf(rs.getInt(3))});
                    }
                }
            }
            return list;
        }), list -> {
            Txt.send(to, "Records " + m.titre().replace("<b>", "").replace("</b>", "") + " <#5A6270>(" + m.unite() + ")");
            if (list.isEmpty()) {
                to.sendMessage(Txt.mm("  <#9AA4B2>Personne encore… à toi l'honneur !"));
            }
            int i = 1;
            for (String[] row : list) {
                String col = i == 1 ? "#E0B864" : i <= 3 ? "#C9D4E0" : "#9AA4B2";
                to.sendMessage(Txt.mm("  <" + col + ">" + i++ + ". " + row[0] + "</" + col + "> <#E6EDF3>" + Txt.n(Long.parseLong(row[1]))
                        + " " + m.unite().toLowerCase(Locale.ROOT) + "</#E6EDF3> <#5A6270>(" + row[2] + " parties)"));
            }
        }, () -> Txt.send(to, "<#E0484A>Erreur de base."));
    }
}
