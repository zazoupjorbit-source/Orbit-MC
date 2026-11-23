package fr.orbit.practice.kit;

import org.bukkit.Material;

/** Les classes HCF jouables au Practice. La classe se reconnaît à l'armure complète portée. */
public enum HcfClass {
    DIAMOND("Diamant", "DIAMOND", Material.DIAMOND_CHESTPLATE, "Le combattant : armure lourde, épée tranchante."),
    BARD("Barde", "GOLDEN", Material.GOLDEN_CHESTPLATE, "Soutien : tient un objet pour une aura, clic droit pour un effet fort."),
    ARCHER("Archer", "LEATHER", Material.LEATHER_CHESTPLATE, "Sa flèche marque la cible : elle subit 25 % de dégâts en plus."),
    ROGUE("Voleur", "CHAINMAIL", Material.CHAINMAIL_CHESTPLATE, "Coup dans le dos à l'épée en or : gros dégâts, l'épée casse.");

    public final String label;
    public final String armor;
    public final Material icon;
    public final String description;

    HcfClass(String label, String armor, Material icon, String description) {
        this.label = label;
        this.armor = armor;
        this.icon = icon;
        this.description = description;
    }
}
