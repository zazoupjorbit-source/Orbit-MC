package fr.orbit.reborn.clazz;

import fr.orbit.reborn.Keys;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Catégorie d'arme. Une compétence d'épée n'est utilisable qu'avec la catégorie de sa classe. */
public enum WeaponType {
    ONE_HANDED("Épée à une main", 1.0,
            Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD),
    RAPIER("Rapière", 0.9,
            Material.GOLDEN_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD),
    DAGGER("Dague", 0.8,
            Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD),
    KATANA("Katana", 1.05,
            Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD),
    TWO_HANDED("Arme à deux mains", 1.25,
            Material.STONE_AXE, Material.IRON_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE),
    SPEAR("Lance", 1.05,
            Material.STONE_SPEAR, Material.IRON_SPEAR, Material.DIAMOND_SPEAR, Material.NETHERITE_SPEAR),
    MACE("Masse", 1.15,
            Material.MACE, Material.MACE, Material.MACE, Material.MACE),
    NONE("Aucune", 1.0);

    public final String label;
    public final double atkMult;
    private final Material[] tiers;

    WeaponType(String label, double atkMult, Material... tiers) {
        this.label = label;
        this.atkMult = atkMult;
        this.tiers = tiers;
    }

    public Material material(int tier) {
        return tiers.length == 0 ? Material.STICK : tiers[Math.max(0, Math.min(tiers.length - 1, tier))];
    }

    /** Palier correspondant à un matériau (0 s'il n'en fait pas partie). */
    public int tierOf(Material m) {
        for (int i = 0; i < tiers.length; i++) {
            if (tiers[i] == m) {
                return i;
            }
        }
        return 0;
    }

    /** Catégorie d'un item : tag posé par le plugin, sinon déduite du matériau. */
    public static WeaponType of(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return NONE;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String tag = meta.getPersistentDataContainer().get(Keys.WEAPON, PersistentDataType.STRING);
            if (tag != null) {
                try {
                    return valueOf(tag);
                } catch (IllegalArgumentException ignored) {
                    // tag inconnu : on retombe sur le matériau
                }
            }
        }
        Material m = item.getType();
        String n = m.name();
        if (m == Material.MACE) {
            return MACE;
        }
        if (n.endsWith("_SWORD")) {
            return ONE_HANDED;
        }
        if (n.endsWith("_SPEAR")) {
            return SPEAR;
        }
        if (n.endsWith("_AXE")) {
            return TWO_HANDED;
        }
        return NONE;
    }
}
