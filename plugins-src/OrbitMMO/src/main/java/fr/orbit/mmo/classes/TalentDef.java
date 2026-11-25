package fr.orbit.mmo.classes;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Map;

/**
 * Un talent d'un arbre de spécialisation. Palier 0 à 11, colonne 0 à 2. Les paliers 0 à 5 s'ouvrent tous les
 * 5 points dépensés dans l'arbre, les paliers avancés 6 à 8 à 35, 45 et 55 points, les paliers de maître 9 à 11
 * à 62, 70 et 78 points (81 points au niveau 90, arbres de ~100 rangs : on ne peut pas tout prendre).
 * Les effets sont donnés par rang.
 */
public final class TalentDef {

    /** Points à avoir dépensés dans l'arbre pour ouvrir chaque palier. */
    private static final int[] REQUIRED = {0, 5, 10, 15, 20, 25, 35, 45, 55, 62, 70, 78};
    public static final int TIERS = REQUIRED.length;
    /** Paliers affichés par page du menu (un coffre a 6 rangées). */
    public static final int TIERS_PER_PAGE = 6;

    public static int required(int tier) {
        return REQUIRED[Math.max(0, Math.min(TIERS - 1, tier))];
    }

    public final String id;
    public String name;
    public Material icon = Material.BOOK;
    public int tier;
    public int column;
    public int maxRank = 1;
    public String requires;
    /** Sort donné par le talent (null sinon). */
    public String spell;
    public Map<String, Double> effects = Map.of();
    public String desc = "";

    public TalentDef(String id) {
        this.id = id;
        this.name = id;
    }

    public int requiredPoints() {
        return required(tier);
    }

    /** Description où chaque {clé} est remplacée par la valeur du rang demandé. */
    public String describe(int rank) {
        String out = desc;
        int r = Math.max(1, rank);
        for (Map.Entry<String, Double> e : effects.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", format(e.getValue() * r));
        }
        return out;
    }

    public static String format(double v) {
        if (Math.abs(v - Math.rint(v)) < 1.0E-6) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }
}
