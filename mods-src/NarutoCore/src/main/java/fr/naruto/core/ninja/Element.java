package fr.naruto.core.ninja;

import org.bukkit.Material;

/** Affinites elementaires (nature du chakra). */
public enum Element {
    KATON("Katon", "Feu", "#FF5533", Material.BLAZE_POWDER),
    SUITON("Suiton", "Eau", "#33AAFF", Material.PRISMARINE_CRYSTALS),
    FUTON("Futon", "Vent", "#8CF5C8", Material.FEATHER),
    DOTON("Doton", "Terre", "#C8863C", Material.DIRT),
    RAITON("Raiton", "Foudre", "#FFEE55", Material.GLOWSTONE_DUST),
    NONE("Aucun", "Aucun", "#AAAAAA", Material.BARRIER);

    private final String displayName;
    private final String nature;
    private final String color;
    private final Material icon;

    Element(String displayName, String nature, String color, Material icon) {
        this.displayName = displayName;
        this.nature = nature;
        this.color = color;
        this.icon = icon;
    }

    public String displayName() { return displayName; }
    public String nature() { return nature; }
    public String color() { return color; }
    public Material icon() { return icon; }

    public static Element byId(String id) {
        if (id == null) return NONE;
        try { return valueOf(id.toUpperCase()); } catch (IllegalArgumentException e) { return NONE; }
    }
}
