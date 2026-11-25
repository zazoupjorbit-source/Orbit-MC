package fr.orbit.vol;

import org.bukkit.Material;

import java.util.List;

/** Améliorations de base : prix et effet de chaque niveau. Tout se paie en crédits du jeu. */
public final class Ameliorations {

    public record Amelioration(String id, String nom, Material icone, int case_, long[] prix, String[] effets, String explication) {
        public int max() {
            return prix.length;
        }
    }

    public static final List<Amelioration> TOUTES = List.of(
            new Amelioration("places", "<#62CFF4><b>Agrandir la base</b>", Material.SEA_LANTERN, 10,
                    new long[]{5_000, 50_000, 500_000, 5_000_000, 50_000_000},
                    new String[]{"10 places", "12 places", "14 places", "16 places", "18 places", "20 places"},
                    "Plus de places = plus d'Orbitos qui rapportent."),
            new Amelioration("bouclier", "<#62CFF4><b>Bouclier laser</b>", Material.LIGHT_BLUE_STAINED_GLASS, 11,
                    new long[]{20_000, 200_000, 2_000_000},
                    new String[]{"60 s, recharge 30 s", "90 s, recharge 25 s", "120 s, recharge 20 s", "150 s, recharge 15 s"},
                    "Ta base reste fermée plus longtemps."),
            new Amelioration("alarme", "<#FFD447><b>Alarme</b>", Material.BELL, 13,
                    new long[]{2_000},
                    new String[]{"aucune", "te prévient et fait briller l'intrus"},
                    "Tu sais tout de suite quand quelqu'un entre chez toi."),
            new Amelioration("generateur", "<#7CFC9A><b>Générateur stellaire</b>", Material.BEACON, 14,
                    new long[]{25_000, 250_000, 2_500_000, 25_000_000, 250_000_000},
                    new String[]{"+0 %", "+10 %", "+20 %", "+30 %", "+40 %", "+50 %"},
                    "Bonus sur tous les revenus de ta base."),
            new Amelioration("banque", "<#E0B864><b>Banque orbitale</b>", Material.GOLD_BLOCK, 15,
                    new long[]{15_000, 150_000, 1_500_000, 15_000_000},
                    new String[]{"rien hors ligne", "10 % pendant 2 h", "20 % pendant 4 h", "35 % pendant 8 h", "50 % pendant 12 h"},
                    "Gagne des crédits même quand tu n'es pas là.")
    );

    private Ameliorations() {
    }

    public static Amelioration get(String id) {
        for (Amelioration a : TOUTES) if (a.id().equals(id)) return a;
        return null;
    }

    public static long dureeBouclierMs(int n) {
        return new long[]{60, 90, 120, 150}[n] * 1000L;
    }

    public static long rechargeBouclierMs(int n) {
        return new long[]{30, 25, 20, 15}[n] * 1000L;
    }

    /** Tourelles désactivées le 24/11 (trop fortes) : prix payés, remboursés à la connexion. */
    public static final long[] PRIX_TOURELLE = {10_000, 100_000, 1_000_000, 10_000_000, 100_000_000};

    public static int cadenceTourelle(int n) {
        return 0;
    }

    public static double partBanque(int n) {
        return new double[]{0, 0.10, 0.20, 0.35, 0.50}[n];
    }

    public static long dureeBanqueMs(int n) {
        return new long[]{0, 2, 4, 8, 12}[n] * 3_600_000L;
    }

    // --------------------------------------------------- niveaux des Orbitos

    public static final int NIVEAU_MAX = 10;

    public static double multNiveau(int niveau) {
        return 1 + 0.3 * (niveau - 1);
    }

    /** Prix pour passer du niveau n au niveau n + 1. */
    public static double prixNiveau(double prixOrbito, int n) {
        return prixOrbito * 0.4 * n;
    }
}
