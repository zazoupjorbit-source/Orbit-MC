package fr.orbit.mmo.item;

import java.util.Locale;

/**
 * Qualité d'un équipement. Fixe la couleur du nom, le nombre de bonus tirés au hasard,
 * le nombre de châsses à runes d'une arme et le bonus d'attaque.
 */
public enum Rarity {
    COMMUN("Commun", "#D7DCE2", 0, 1, 1.00, 1.00, 1.0),
    INHABITUEL("Inhabituel", "#5CE65C", 1, 1, 1.05, 1.00, 1.6),
    RARE("Rare", "#4FA3FF", 2, 2, 1.10, 1.15, 2.5),
    EPIQUE("Épique", "#B36BFF", 3, 2, 1.18, 1.30, 4.0),
    LEGENDAIRE("Légendaire", "#FF8A3B", 4, 3, 1.28, 1.50, 7.0);

    public final String label;
    public final String hex;
    /** Bonus tirés au hasard (en plus du bonus propre au modèle). */
    public final int affixes;
    /** Châsses à runes d'une arme. */
    public final int sockets;
    public final double atkMult;
    public final double statMult;
    /** Multiplicateur du prix de revente. */
    public final double priceMult;

    Rarity(String label, String hex, int affixes, int sockets, double atkMult, double statMult, double priceMult) {
        this.label = label;
        this.hex = hex;
        this.affixes = affixes;
        this.sockets = sockets;
        this.atkMult = atkMult;
        this.statMult = statMult;
        this.priceMult = priceMult;
    }

    public String tag() {
        return "<" + hex + ">" + label + "</" + hex + ">";
    }

    public boolean atLeast(Rarity other) {
        return ordinal() >= other.ordinal();
    }

    public static Rarity of(String s) {
        if (s == null) {
            return COMMUN;
        }
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return COMMUN;
        }
    }
}
