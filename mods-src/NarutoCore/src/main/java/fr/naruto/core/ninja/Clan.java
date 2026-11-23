package fr.naruto.core.ninja;

import org.bukkit.Material;

/**
 * Clans et leur Kekkei Genkai / passif.
 * chakraBonus = bonus plat de chakra max ; regenBonus = bonus de regen (chakra/tick).
 */
public enum Clan {
    UCHIHA("Uchiha", "Sharingan", "Percoit et esquive mieux ; jutsu de feu renforces.", "#E23B3B", Material.FIRE_CHARGE, 20, 0),
    HYUGA("Hyuga", "Byakugan", "Vision a 360, coups qui bloquent le chakra ennemi.", "#D8CFE8", Material.ENDER_EYE, 10, 0),
    UZUMAKI("Uzumaki", "Chakra colossal", "Reserve de chakra enorme et vitalite.", "#E8A23B", Material.GOLDEN_APPLE, 80, 0),
    NARA("Nara", "Kagemane", "Manipulation des ombres pour immobiliser.", "#5A5A6E", Material.INK_SAC, 15, 0),
    SENJU("Senju", "Vitalite Senju", "Regeneration de chakra et de vie superieure.", "#5FAE5A", Material.OAK_SAPLING, 30, 1),
    ABURAME("Aburame", "Kikaichu", "Insectes qui drainent le chakra adverse.", "#8C7A3B", Material.HONEYCOMB, 25, 0),
    NONE("Sans clan", "Aucun", "Aucun passif de clan.", "#AAAAAA", Material.BARRIER, 0, 0);

    private final String displayName;
    private final String kekkeiGenkai;
    private final String description;
    private final String color;
    private final Material icon;
    private final int chakraBonus;
    private final int regenBonus;

    Clan(String displayName, String kekkeiGenkai, String description, String color, Material icon, int chakraBonus, int regenBonus) {
        this.displayName = displayName;
        this.kekkeiGenkai = kekkeiGenkai;
        this.description = description;
        this.color = color;
        this.icon = icon;
        this.chakraBonus = chakraBonus;
        this.regenBonus = regenBonus;
    }

    public String displayName() { return displayName; }
    public String kekkeiGenkai() { return kekkeiGenkai; }
    public String description() { return description; }
    public String color() { return color; }
    public Material icon() { return icon; }
    public int chakraBonus() { return chakraBonus; }
    public int regenBonus() { return regenBonus; }

    public static Clan byId(String id) {
        if (id == null) return NONE;
        try { return valueOf(id.toUpperCase()); } catch (IllegalArgumentException e) { return NONE; }
    }
}
