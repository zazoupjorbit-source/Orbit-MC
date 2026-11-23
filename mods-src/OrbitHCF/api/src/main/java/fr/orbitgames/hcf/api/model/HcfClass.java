package fr.orbitgames.hcf.api.model;

/**
 * Classes HCF disponibles, detectees par l'armure portee.
 */
public enum HcfClass {

    DIAMOND("Diamond"),
    BARD("Bard"),
    ARCHER("Archer"),
    ROGUE("Rogue"),
    MINER("Miner");

    private final String displayName;

    HcfClass(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Recherche insensible a la casse, ou null si inconnue. */
    public static HcfClass fromName(String name) {
        if (name == null) {
            return null;
        }
        for (HcfClass value : values()) {
            if (value.name().equalsIgnoreCase(name) || value.displayName.equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }
}
