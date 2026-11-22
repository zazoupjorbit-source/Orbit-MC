package fr.orbit.mmo.classes;

import org.bukkit.Material;

/** Les deux factions. Les classes à double forme (mage, prêtre, paladin) prennent celle de leur faction. */
public enum Faction {
    LUMIERE("lumiere", "Lumière", "#FFD66E", Material.SUNFLOWER, "Bastion de l'Aube",
            "Gardiens de l'ordre et de la lumière des étoiles.",
            "Feu, foudre et magie sacrée."),
    OMBRE("ombre", "Ombre", "#B36BFF", Material.WITHER_ROSE, "Citadelle d'Obsidienne",
            "Ceux qui ont choisi la puissance du néant.",
            "Magie noire, nécromancie et poisons.");

    public final String id;
    public final String label;
    public final String hex;
    public final Material icon;
    public final String capital;
    public final String[] lore;

    Faction(String id, String label, String hex, Material icon, String capital, String... lore) {
        this.id = id;
        this.label = label;
        this.hex = hex;
        this.icon = icon;
        this.capital = capital;
        this.lore = lore;
    }

    public static Faction byId(String id) {
        if (id == null) {
            return null;
        }
        for (Faction f : values()) {
            if (f.id.equalsIgnoreCase(id)) {
                return f;
            }
        }
        return null;
    }

    public String tag() {
        return "<" + hex + ">" + label + "</" + hex + ">";
    }
}
