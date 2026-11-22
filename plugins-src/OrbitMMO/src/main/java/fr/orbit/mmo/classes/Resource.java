package fr.orbit.mmo.classes;

/**
 * Ressource d'une classe. Toutes vont de 0 à {@link #max} (les orbes de 0 à 5).
 * regenCombat / regenRest : points par seconde (négatif = la ressource retombe).
 */
public enum Resource {
    RAGE("Rage", "#FF4A3D", 100, 0, -3, false),
    MANA("Mana", "#4FA3FF", 100, 1.5, 5, true),
    ENERGIE("Énergie", "#FFE066", 100, 10, 10, true),
    CONCENTRATION("Concentration", "#FF9A3B", 100, 6, 8, true),
    FERVEUR("Ferveur", "#FFE9A8", 100, 0, -2, false),
    ORBES("Orbes d'étoile", "#9AD8FF", 5, 0, -0.2, false);

    public final String label;
    public final String hex;
    public final double max;
    public final double regenCombat;
    public final double regenRest;
    /** true : la ressource est pleine au départ (mana, énergie) ; false : elle part de zéro (rage, ferveur, orbes). */
    public final boolean startsFull;

    Resource(String label, String hex, double max, double regenCombat, double regenRest, boolean startsFull) {
        this.label = label;
        this.hex = hex;
        this.max = max;
        this.regenCombat = regenCombat;
        this.regenRest = regenRest;
        this.startsFull = startsFull;
    }

    public static Resource byName(String s, Resource def) {
        if (s == null) {
            return def;
        }
        for (Resource r : values()) {
            if (r.name().equalsIgnoreCase(s)) {
                return r;
            }
        }
        return def;
    }
}
