package fr.orbit.survie;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Classements : les fortunes (permanent) et les classements du mois (table survie_stats) — contrats, XP de
 * métiers, poissons, monstres, ventes aux joueurs, duels. Deux panneaux sur la place les affichent (le second
 * tourne d'une catégorie à l'autre). Au changement de mois, les trois premiers de chaque catégorie touchent un lot.
 */
public final class Classements {

    public record Categorie(String cle, String titre, String unite, boolean argent) {}

    public static final List<Categorie> CATEGORIES = List.of(
            new Categorie("contrats", "Contrats livrés", "contrats", false),
            new Categorie("xp", "XP de métiers", "XP", false),
            new Categorie("poissons", "Poissons pêchés", "poissons", false),
            new Categorie("monstres", "Monstres tués", "monstres", false),
            new Categorie("ventes", "Ventes aux joueurs", "", true),
            new Categorie("duels", "Duels gagnés", "victoires", false));

    private final OrbitSurvie plugin;
    private int tour;

    public Classements(OrbitSurvie plugin) {
        this.plugin = plugin;
    }

    public static Categorie categorie(String cle) {
        for (Categorie c : CATEGORIES) {
            if (c.cle().equalsIgnoreCase(cle)) {
                return c;
            }
        }
        return null;
    }

    private static String valeur(Categorie c, double v) {
        return c.argent() ? Msg.argent(v) : "<white>" + Msg.nombre(v) + " <gray>" + c.unite();
    }

    private static String couleur(int rang) {
        return rang == 0 ? Msg.OR : rang == 1 ? "<#C9D3DD>" : rang == 2 ? "<#D08A4E>" : "<gray>";
    }

    // ------------------------------------------------------------------ Panneaux de la place

    /** Toutes les 15 s : le panneau des fortunes et le panneau du mois (catégorie suivante). */
    public void tic() {
        if (!plugin.db().available()) {
            return;
        }
        plugin.db().then(plugin.eco().classement(5), liste -> {
            StringBuilder t = new StringBuilder(Msg.OR + "<bold>✦ LES PLUS RICHES ✦</bold>");
            for (int i = 0; i < liste.size(); i++) {
                t.append("<newline>").append(couleur(i)).append(i + 1).append(". <white>").append(liste.get(i).nom())
                        .append(" <dark_gray>— ").append(Msg.argent(liste.get(i).solde()));
            }
            t.append("<newline><dark_gray>/richesses");
            plugin.pnj().texte("classement-fortune", t.toString());
        }, null);
        Categorie c = CATEGORIES.get(tour++ % CATEGORIES.size());
        plugin.db().then(plugin.stats().top(Stats.mois(), c.cle(), 5), liste -> {
            StringBuilder t = new StringBuilder(Msg.BLEU + "<bold>CLASSEMENT DU MOIS</bold><newline>" + Msg.OR + c.titre());
            if (liste.isEmpty()) {
                t.append("<newline><gray>Personne encore : à toi de jouer !");
            }
            for (int i = 0; i < liste.size(); i++) {
                t.append("<newline>").append(couleur(i)).append(i + 1).append(". <white>").append(liste.get(i).nom())
                        .append(" <dark_gray>— ").append(valeur(c, liste.get(i).valeur()));
            }
            t.append("<newline><dark_gray>/classement · lots en fin de mois");
            plugin.pnj().texte("classement-mois", t.toString());
        }, null);
    }

    // ------------------------------------------------------------------ /classement

    public void afficher(CommandSender s, String cle) {
        if (!plugin.db().available()) {
            Msg.send(s, "<red>Classements indisponibles : la base de données est injoignable.");
            return;
        }
        plugin.stats().vider();
        if (cle == null) {
            Msg.send(s, Msg.OR + "<bold>Classements du mois<gray> — détail : <white>/classement <catégorie>");
            for (Categorie c : CATEGORIES) {
                plugin.db().then(plugin.stats().top(Stats.mois(), c.cle(), 1), liste -> s.sendMessage(Msg.raw(
                        "  " + Msg.BLEU + c.titre() + " <dark_gray>(" + c.cle() + ") <gray>: " + (liste.isEmpty() ? "<dark_gray>personne"
                                : "<white>" + liste.get(0).nom() + " <dark_gray>— " + valeur(c, liste.get(0).valeur())))), null);
            }
            double[] lots = lots();
            Msg.send(s, "Lots de fin de mois, par catégorie : " + Msg.argent(lots[0]) + ", " + Msg.argent(lots[1]) + ", " + Msg.argent(lots[2]) + ".");
            return;
        }
        Categorie c = categorie(cle);
        if (c == null) {
            Msg.send(s, "<red>Catégorie inconnue. <gray>Choix : <white>" + String.join(", ", cles()));
            return;
        }
        plugin.db().then(plugin.stats().top(Stats.mois(), c.cle(), 10), liste -> {
            Msg.send(s, Msg.OR + "<bold>" + c.titre() + "<gray> — ce mois-ci");
            if (liste.isEmpty()) {
                s.sendMessage(Msg.raw("  <gray>Personne encore."));
            }
            for (int i = 0; i < liste.size(); i++) {
                s.sendMessage(Msg.raw("  " + couleur(i) + (i + 1) + ". <white>" + liste.get(i).nom() + " <dark_gray>— "
                        + valeur(c, liste.get(i).valeur())));
            }
        }, null);
    }

    public static List<String> cles() {
        List<String> out = new ArrayList<>();
        CATEGORIES.forEach(c -> out.add(c.cle()));
        return out;
    }

    // ------------------------------------------------------------------ Lots de fin de mois

    private double[] lots() {
        List<Double> l = plugin.getConfig().getDoubleList("classements.lots-mensuels");
        double[] out = {3000, 1500, 750};
        for (int i = 0; i < 3 && i < l.size(); i++) {
            out[i] = l.get(i);
        }
        return out;
    }

    /** Paie, une seule fois, les lots du mois qui vient de finir (ligne dans survie_lots_mois). */
    public void lotsDuMoisPasse() {
        if (!plugin.db().available()) {
            return;
        }
        plugin.stats().vider();
        String passe = YearMonth.now().minusMonths(1).toString();
        double[] lots = lots();
        plugin.db().then(plugin.db().transaction(c -> {
            try (PreparedStatement st = c.prepareStatement("SELECT 1 FROM survie_lots_mois WHERE mois = ?")) {
                st.setString(1, passe);
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        return List.<String>of();
                    }
                }
            }
            try (PreparedStatement st = c.prepareStatement("INSERT INTO survie_lots_mois (mois, paye_le) VALUES (?, NOW())")) {
                st.setString(1, passe);
                st.executeUpdate();
            }
            List<String> annonces = new ArrayList<>();
            for (Categorie cat : CATEGORIES) {
                List<Stats.Ligne> top = Stats.top(c, passe, cat.cle(), 3);
                StringBuilder a = new StringBuilder();
                for (int i = 0; i < top.size(); i++) {
                    Economie.crediter(c, top.get(i).uuid(), top.get(i).nom(), lots[i]);
                    a.append(i == 0 ? "" : "<gray>, ").append(couleur(i)).append(top.get(i).nom());
                }
                if (!top.isEmpty()) {
                    annonces.add(Msg.BLEU + cat.titre() + " <dark_gray>: " + a);
                }
            }
            return annonces;
        }), annonces -> {
            if (annonces.isEmpty()) {
                return;
            }
            Bukkit.broadcast(Msg.msg(Msg.OR + "<bold>Classements de " + passe.toLowerCase(Locale.ROOT) + " : les lots sont versés !"));
            annonces.forEach(a -> Bukkit.broadcast(Msg.raw("  " + a)));
            plugin.getLogger().info("Lots mensuels de " + passe + " versés (" + annonces.size() + " catégories).");
        }, null);
    }
}
