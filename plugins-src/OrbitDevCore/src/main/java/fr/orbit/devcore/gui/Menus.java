package fr.orbit.devcore.gui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Style commun des menus ORBIT (double coffre, cadre en vitres colorées). */
public final class Menus {

    public static final int SIZE = 54;

    /** Les 28 cases intérieures (rangées 1 à 4, sans les colonnes du bord). */
    public static final int[] INNER_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    public static final Sound OPEN_SOUND = Sound.sound(Key.key("item.book.page_turn"), Sound.Source.MASTER, 1f, 1.2f);
    public static final Sound CLICK_SOUND = Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.6f, 1.4f);
    public static final Sound EQUIP_SOUND = Sound.sound(Key.key("item.armor.equip_generic"), Sound.Source.MASTER, 1f, 1.2f);
    public static final Sound DENY_SOUND = Sound.sound(Key.key("entity.villager.no"), Sound.Source.MASTER, 0.8f, 1f);

    // Dégradé ORBIT (cyan → violet) du bord vers le centre, pour les rangées du haut et du bas.
    private static final Material[] EDGE_GRADIENT = {
            Material.PURPLE_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE};

    private Menus() {
    }

    /** Fond noir, rangées du haut et du bas en dégradé, colonnes latérales violettes. */
    public static void frame(Inventory inv) {
        ItemStack background = decor(Material.BLACK_STAINED_GLASS_PANE, null);
        for (int slot = 0; slot < SIZE; slot++) {
            inv.setItem(slot, background);
        }
        for (int col = 0; col < 9; col++) {
            Material glass = EDGE_GRADIENT[Math.min(Math.min(col, 8 - col), EDGE_GRADIENT.length - 1)];
            inv.setItem(col, decor(glass, null));
            inv.setItem(45 + col, decor(glass, null));
        }
        ItemStack side = decor(Material.PURPLE_STAINED_GLASS_PANE, null);
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9, side);
            inv.setItem(row * 9 + 8, side);
        }
    }

    /** Vitre décorative : sans infobulle, ou avec un simple titre. */
    public static ItemStack decor(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (name == null) {
            meta.setHideTooltip(true);
        } else {
            meta.displayName(name);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack item(Material material, Component name, List<Component> lore, boolean glint) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        if (glint) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    public static Material icon(String name, Material fallback) {
        Material material = name == null ? null : Material.matchMaterial(name);
        return material != null && material.isItem() && !material.isAir() ? material : fallback;
    }
}
