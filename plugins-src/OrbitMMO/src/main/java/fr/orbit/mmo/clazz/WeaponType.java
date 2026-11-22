package fr.orbit.mmo.clazz;

import fr.orbit.mmo.Keys;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/** Catégorie d'arme. Un sort peut exiger une catégorie (ex. arme à deux mains pour Carnage). */
public enum WeaponType {
    ONE_HANDED("Épée à une main", 1.0,
            Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD),
    TWO_HANDED("Arme à deux mains", 1.3,
            Material.STONE_AXE, Material.IRON_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE),
    DAGGER("Dague", 0.8,
            Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD),
    SPEAR("Lance", 1.05,
            Material.STONE_SPEAR, Material.IRON_SPEAR, Material.DIAMOND_SPEAR, Material.NETHERITE_SPEAR),
    MACE("Masse", 1.15,
            Material.MACE, Material.MACE, Material.MACE, Material.MACE),
    BOW("Arc", 1.0,
            Material.BOW, Material.BOW, Material.BOW, Material.BOW),
    STAFF("Bâton", 1.0,
            Material.STICK, Material.BLAZE_ROD, Material.BREEZE_ROD, Material.BREEZE_ROD),
    WAND("Baguette", 1.0,
            Material.END_ROD, Material.END_ROD, Material.END_ROD, Material.END_ROD),
    GRIMOIRE("Grimoire", 1.0,
            Material.BOOK, Material.BOOK, Material.BOOK, Material.BOOK),
    NONE("Aucune", 1.0);

    public final String label;
    public final double atkMult;
    private final Material[] tiers;

    WeaponType(String label, double atkMult, Material... tiers) {
        this.label = label;
        this.atkMult = atkMult;
        this.tiers = tiers;
    }

    /** Arme de lanceur de sorts : l'attaque de base est un trait magique à distance, jamais un coup de mêlée. */
    public boolean magic() {
        return this == STAFF || this == WAND || this == GRIMOIRE;
    }

    public Material material(int tier) {
        return tiers.length == 0 ? Material.STICK : tiers[Math.max(0, Math.min(tiers.length - 1, tier))];
    }

    public int tierOf(Material m) {
        for (int i = 0; i < tiers.length; i++) {
            if (tiers[i] == m) {
                return i;
            }
        }
        return 0;
    }

    public static WeaponType byName(String s) {
        if (s == null) {
            return null;
        }
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Catégorie d'un item : tag posé par le plugin, sinon déduite du matériau. */
    public static WeaponType of(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return NONE;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String tag = meta.getPersistentDataContainer().get(Keys.WEAPON, PersistentDataType.STRING);
            WeaponType t = byName(tag);
            if (t != null) {
                return t;
            }
        }
        Material m = item.getType();
        String n = m.name();
        if (m == Material.MACE) {
            return MACE;
        }
        if (m == Material.BOW || m == Material.CROSSBOW) {
            return BOW;
        }
        if (m == Material.BLAZE_ROD || m == Material.BREEZE_ROD || m == Material.STICK) {
            return STAFF;
        }
        if (m == Material.END_ROD) {
            return WAND;
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
