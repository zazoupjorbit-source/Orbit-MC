package fr.orbit.vol;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Quêtes du jour : 3 par joueur, tirées chaque jour (minuit, heure du serveur).
 * Récompense en crédits proportionnelle au revenu du joueur ; les 3 finies ouvrent le coffre du jour
 * et allongent la série (+10 % de récompense par jour d'affilée, jusqu'à +100 %).
 */
public final class Quetes {

    public enum Type {
        ACHAT("Achète %s Orbitos sur le tapis"),
        ACHAT_RARE("Achète %s Orbito Épique ou mieux"),
        VOL("Réussis %s vols"),
        ENCAISSER("Encaisse %s ✦"),
        AMELIORER("Améliore tes Orbitos %s fois"),
        DEFENSE("Fais lâcher %s Orbitos à des voleurs"),
        BOUCLIER("Active ton bouclier %s fois"),
        MUTATION("Possède %s Orbito muté de plus");

        public final String texte;

        Type(String texte) {
            this.texte = texte;
        }
    }

    public static final class Quete {
        public Type type;
        public double objectif, progres, recompense;
        public boolean faite;

        String ligne() {
            return type.name() + "|" + objectif + "|" + progres + "|" + recompense + "|" + faite;
        }

        static Quete lire(String s) {
            String[] t = s.split("\\|");
            Quete q = new Quete();
            q.type = Type.valueOf(t[0]);
            q.objectif = Double.parseDouble(t[1]);
            q.progres = Double.parseDouble(t[2]);
            q.recompense = Double.parseDouble(t[3]);
            q.faite = Boolean.parseBoolean(t[4]);
            return q;
        }

        public String description() {
            String n = type == Type.ENCAISSER ? Txt.n(objectif) : String.valueOf((long) objectif);
            return String.format(type.texte, n);
        }
    }

    private Quetes() {
    }

    static String aujourdhui() {
        return LocalDate.now().toString();
    }

    /** Tire les quêtes du jour si la date a changé. */
    static void verifier(Profil pr, double revenu) {
        String jour = aujourdhui();
        if (jour.equals(pr.jourQuetes) && pr.quetes.size() == 3) return;
        // Série cassée si hier n'a pas été complété.
        if (!pr.dernierJourComplet.isEmpty() && !pr.dernierJourComplet.equals(LocalDate.now().minusDays(1).toString())
                && !pr.dernierJourComplet.equals(jour)) pr.serie = 0;
        pr.jourQuetes = jour;
        pr.coffreDuJour = false;
        pr.quetes.clear();
        List<Type> types = new ArrayList<>(List.of(Type.values()));
        Collections.shuffle(types);
        double base = Math.max(500, revenu * 180);
        for (int i = 0; i < 3; i++) {
            Quete q = new Quete();
            q.type = types.get(i);
            q.objectif = switch (q.type) {
                case ACHAT -> 3 + (int) (Math.random() * 4);
                case ACHAT_RARE, MUTATION -> 1;
                case VOL -> 2 + (int) (Math.random() * 3);
                case ENCAISSER -> Math.max(1_000, Math.round(revenu * 600 / 100) * 100.0);
                case AMELIORER -> 3 + (int) (Math.random() * 5);
                case DEFENSE -> 1 + (int) (Math.random() * 2);
                case BOUCLIER -> 2;
            };
            double difficulte = switch (q.type) {
                case VOL, DEFENSE, ACHAT_RARE -> 1.6;
                case MUTATION -> 1.4;
                default -> 1.0;
            };
            q.recompense = Math.round(base * difficulte);
            pr.quetes.add(q.ligne());
        }
    }

    static List<Quete> liste(Profil pr) {
        List<Quete> l = new ArrayList<>();
        for (String s : pr.quetes) {
            try {
                l.add(Quete.lire(s));
            } catch (RuntimeException ignore) {
            }
        }
        return l;
    }

    /** Fait avancer les quêtes d'un type ; renvoie les crédits gagnés. */
    static void avancer(Jeu jeu, Player p, Profil pr, Type type, double quantite) {
        verifier(pr, pr.revenu);
        List<Quete> l = liste(pr);
        boolean change = false;
        for (Quete q : l) {
            if (q.type != type || q.faite) continue;
            q.progres = Math.min(q.objectif, q.progres + quantite);
            change = true;
            if (q.progres >= q.objectif) {
                q.faite = true;
                double gain = q.recompense * (1 + 0.1 * Math.min(10, pr.serie));
                pr.credits += gain;
                jeu.hudJeu.banniere(p, "b_quete", "<#62CFF4><b>QUÊTE FINIE !</b>", q.description() + " · +" + Txt.n(gain) + " ✦");
                Txt.send(p, "<#62CFF4><b>Quête finie :</b></#62CFF4> " + q.description() + " → " + Txt.cr(gain));
                Txt.sound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.4f);
            }
        }
        if (!change) return;
        pr.quetes.clear();
        for (Quete q : l) pr.quetes.add(q.ligne());
        if (!pr.coffreDuJour && l.stream().allMatch(q -> q.faite)) {
            pr.coffreDuJour = true;
            pr.serie++;
            pr.dernierJourComplet = aujourdhui();
            double coffre = Math.max(2_000, pr.revenu * 300) * (1 + 0.1 * Math.min(10, pr.serie));
            pr.credits += coffre;
            Txt.send(p, "<#E0B864><b>Coffre du jour ouvert !</b></#E0B864> " + Txt.cr(coffre) + " — série de <b>" + pr.serie + "</b> jour(s), reviens demain pour la garder !");
            Txt.sound(p, Sound.ENTITY_PLAYER_LEVELUP, 0.8f);
        }
    }
}
