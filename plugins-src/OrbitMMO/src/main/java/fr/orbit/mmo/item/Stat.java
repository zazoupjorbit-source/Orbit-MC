package fr.orbit.mmo.item;

import fr.orbit.mmo.classes.TalentDef;

/**
 * Bonus d'un équipement ou d'une rune. Chaque bonus alimente une clé de Mods (voir classes/Mods),
 * comme les talents : l'effet en combat est donc déjà géré partout.
 */
public enum Stat {
    PUISSANCE("degats", "de dégâts", "de la Fureur", 1.0, 0.06, false),
    PRECISION("crit", "de chances de critique", "de l'Aigle", 0.6, 0.035, false),
    FEROCITE("critdeg", "de dégâts critiques", "du Fauve", 2.0, 0.15, false),
    VITALITE("pv", "de PV max", "de l'Ours", 1.5, 0.08, false),
    ROBUSTESSE("armure", "de dégâts subis", "du Rempart", 0.5, 0.03, true),
    AGILITE("esquive", "d'esquive", "du Renard", 0.5, 0.025, false),
    PARADE("blocage", "de blocage au bouclier", "de la Garde", 1.0, 0.06, false),
    VAMPIRISME("vol-de-vie", "de vol de vie", "du Vampire", 0.3, 0.02, false),
    CELERITE("vitesse", "de vitesse", "du Vent", 0.8, 0.04, false),
    FOCALISATION("ressource", "de ressource générée", "du Sage", 1.5, 0.08, false),
    EPINES("epines", "de dégâts renvoyés", "de la Ronce", 2.0, 0.12, false),
    GRACE("soins", "de soins prodigués", "de l'Aube", 1.0, 0.07, false);

    /** Clé de Mods. */
    public final String mod;
    public final String label;
    /** Suffixe du nom d'un objet dont c'est le bonus principal (« Espadon d'acier du Fauve »). */
    public final String suffix;
    private final double base;
    private final double perLevel;
    /** Affiché en négatif (dégâts subis en moins). */
    private final boolean minus;

    Stat(String mod, String label, String suffix, double base, double perLevel, boolean minus) {
        this.mod = mod;
        this.label = label;
        this.suffix = suffix;
        this.base = base;
        this.perLevel = perLevel;
        this.minus = minus;
    }

    /** Valeur de référence au niveau d'objet donné (avant rareté et hasard). */
    public double value(int level) {
        return base + perLevel * Math.max(1, level);
    }

    public String line(double v) {
        return (minus ? "−" : "+") + TalentDef.format(v) + " % " + label;
    }

    public static Stat byMod(String mod) {
        for (Stat s : values()) {
            if (s.mod.equals(mod)) {
                return s;
            }
        }
        return null;
    }
}
