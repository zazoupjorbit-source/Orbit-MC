package fr.orbit.bowl;

import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Records et gains : chaque jeu se déclare dans {@code arcade_jeux} (tableau des records de la salle d'arcade, relu
 * par OrbitCasino chaque minute), écrit le meilleur score de chaque joueur dans {@code arcade_scores} et verse ses
 * Orbit Coins par OrbitCasino avec un plafond par jour, par joueur et par jeu ({@code bowl_gains}).
 */
public final class Scores {

    private final OrbitBowl pl;

    Scores(OrbitBowl pl) {
        this.pl = pl;
    }

    void declarer() {
        if (!pl.db().available()) {
            return;
        }
        pl.db().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO arcade_jeux (jeu, titre, unite, ordre) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE titre = VALUES(titre), unite = VALUES(unite), ordre = VALUES(ordre)")) {
                for (Jeu j : Jeu.values()) {
                    if (j.record == null) {
                        continue;
                    }
                    ps.setString(1, j.record);
                    ps.setString(2, j.titre());
                    ps.setString(3, j.unite);
                    ps.setInt(4, j.ordre);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return true;
        });
    }

    public int plafond(Jeu j) {
        return pl.getConfig().getInt(j.id + ".plafond-oc-jour", 500);
    }

    /**
     * Enregistre la partie d'un joueur : record (si le jeu en a un et {@code record}), OC versés sous le plafond du jour,
     * puis bilan dans le chat avec les boutons Rejouer / Classement.
     */
    public void enregistrer(Player p, Jeu j, double score, int ocBrut, String resume, boolean record) {
        UUID uuid = p.getUniqueId();
        String nom = p.getName();
        int cap = plafond(j);
        if (!pl.db().available()) {
            bilan(p, j, score, 0, ocBrut, -1, cap, "<#E0484A>(base injoignable : rien d'enregistré)", record);
            return;
        }
        String code = j.record == null ? j.id : j.record;
        pl.db().then(pl.db().transaction(c -> {
            int deja = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT gain FROM bowl_gains WHERE uuid = ? AND jeu = ? AND jour = CURDATE() FOR UPDATE")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, j.id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        deja = rs.getInt(1);
                    }
                }
            }
            int oc = Math.max(0, Math.min(ocBrut, cap - deja));
            if (oc > 0) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO bowl_gains (uuid, jeu, jour, gain) VALUES (?, ?, CURDATE(), ?) "
                        + "ON DUPLICATE KEY UPDATE gain = gain + VALUES(gain)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, j.id);
                    ps.setInt(3, oc);
                    ps.executeUpdate();
                }
            }
            double ancien = -1;
            if (record && j.record != null) {
                try (PreparedStatement ps = c.prepareStatement("SELECT meilleur FROM arcade_scores WHERE jeu = ? AND uuid = ?")) {
                    ps.setString(1, code);
                    ps.setString(2, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            ancien = rs.getDouble(1);
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO arcade_scores (jeu, uuid, nom, meilleur, parties) VALUES (?, ?, ?, ?, 1) "
                        + "ON DUPLICATE KEY UPDATE nom = VALUES(nom), meilleur = GREATEST(meilleur, VALUES(meilleur)), parties = parties + 1")) {
                    ps.setString(1, code);
                    ps.setString(2, uuid.toString());
                    ps.setString(3, nom);
                    ps.setDouble(4, Math.max(0, score));
                    ps.executeUpdate();
                }
            }
            return new double[]{oc, ancien};
        }), r -> {
            int oc = (int) r[0];
            if (oc > 0) {
                Caisse.crediter(uuid, oc, j.id, resume.length() > 60 ? resume.substring(0, 60) : resume, false);
            }
            if (p.isOnline()) {
                bilan(p, j, score, oc, ocBrut, r[1], cap, "", record);
            }
        }, () -> {
            if (p.isOnline()) {
                bilan(p, j, score, 0, ocBrut, -1, cap, "<#E0484A>(erreur de base : rien d'enregistré)", record);
            }
        });
    }

    private void bilan(Player p, Jeu j, double score, int oc, int ocBrut, double ancien, int cap, String note, boolean record) {
        boolean nouveau = record && j.record != null && note.isEmpty() && (ancien >= 0 ? score > ancien : score > 0);
        Txt.send(p, j.grad("<b>" + j.nom + "</b>") + " <#5A6270>·</#5A6270> <b>" + Txt.n(Math.round(score)) + "</b> " + j.unite
                + " → " + (oc > 0 ? Txt.oc(oc) : "<#9AA4B2>0 OC")
                + (oc < ocBrut && note.isEmpty() ? " <#9AA4B2>(plafond de " + Txt.n(cap) + " OC par jour atteint)" : "")
                + (nouveau ? "  <#7CFF8A><b>NOUVEAU RECORD !</b>" : ancien >= 0 ? " <#5A6270>· record " + Txt.n(Math.round(ancien)) : "")
                + (note.isEmpty() ? "" : " " + note));
        var ligne = Txt.mm("  ").append(Txt.button("Rejouer", j.c1, "/bowl rejouer", "Relancer " + j.nom));
        if (j.record != null) {
            ligne = ligne.append(Txt.mm(" ")).append(Txt.button("Classement", "#E0B864", "/bowl top " + j.id, "Les 10 meilleurs"));
        }
        p.sendMessage(ligne);
        if (nouveau) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        }
    }

    /** Top 10 d'un jeu (chat). */
    public void top(CommandSender to, Jeu j) {
        if (j.record == null) {
            Txt.send(to, j.nom + " n'a pas de classement : c'est un duel.");
            return;
        }
        lire(j, 10, list -> {
            Txt.send(to, "Records " + j.grad("<b>" + j.nom + "</b>") + " <#5A6270>(" + j.unite + ")");
            if (list.isEmpty()) {
                to.sendMessage(Txt.mm("  <#9AA4B2>Personne encore… à toi l'honneur !"));
            }
            int i = 1;
            for (String[] row : list) {
                String col = i == 1 ? "#E0B864" : i <= 3 ? "#C9D4E0" : "#9AA4B2";
                to.sendMessage(Txt.mm("  <" + col + ">" + i++ + ". " + row[0] + "</" + col + "> <#E6EDF3>" + Txt.n(Long.parseLong(row[1]))
                        + " " + j.unite.toLowerCase(Locale.ROOT) + "</#E6EDF3> <#5A6270>(" + row[2] + " parties)"));
            }
        });
    }

    /** Lit les {@code n} meilleurs d'un jeu (nom, score, parties) ; liste vide si la base est injoignable. */
    public void lire(Jeu j, int n, Consumer<List<String[]>> fait) {
        if (j.record == null || !pl.db().available()) {
            fait.accept(List.of());
            return;
        }
        pl.db().then(pl.db().run(c -> {
            List<String[]> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT nom, meilleur, parties FROM arcade_scores WHERE jeu = ? AND meilleur > 0 ORDER BY meilleur DESC LIMIT " + n)) {
                ps.setString(1, j.record);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new String[]{rs.getString(1), String.valueOf(Math.round(rs.getDouble(2))), String.valueOf(rs.getInt(3))});
                    }
                }
            }
            return list;
        }), fait, () -> fait.accept(List.of()));
    }
}
