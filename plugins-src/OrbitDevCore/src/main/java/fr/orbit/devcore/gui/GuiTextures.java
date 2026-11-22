package fr.orbit.devcore.gui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Menus texturés (style « station » ORBIT) : le fond du menu est une image du pack du lobby,
 * affichée comme glyphes de la police {@code orbitdev:gui} dans le titre du coffre.
 *
 * <p>L'image (générée par tools/OrbitGuiGen.java) est découpée en 2 x 4 tuiles car une glyphe ne peut pas
 * dépasser 256 px. Chaque tuile avance de 99 unités ; le titre est donc :
 * {@code [-18] puis, pour chaque rangée : tuile gauche, [-1], tuile droite, [-197]}.
 * Les avances et les « ascent » sont dans pack/assets/orbitdev/font/gui.json.
 */
public final class GuiTextures {

    public static final Key FONT = Key.key("orbitdev", "gui");
    /** Modèle d'objet vide : zone cliquable avec infobulle, sans rien d'affiché par-dessus la texture. */
    public static final NamespacedKey INVISIBLE = new NamespacedKey("orbitdev", "invisible");

    /**
     * Un menu texturé = un bloc de 8 glyphes consécutifs dans gui.json.
     * Les mêmes valeurs sont dans tools/OrbitGuiGen.java (BASES), qui écrit gui.json.
     */
    public enum Screen {
        NAVIGATION(0xE200),
        SHOP(0xE210),
        COSMETICS(0xE220),
        COSMETICS_TRAIL(0xE230),
        COSMETICS_AURA(0xE240),
        COSMETICS_HAT(0xE250),
        COSMETICS_BACK(0xE260),
        COSMETICS_BALLOON(0xE270),
        COSMETICS_PET(0xE280),
        COSMETICS_MOUNT(0xE290);

        private final int base;

        Screen(int base) {
            this.base = base;
        }
    }

    private static final char START = '';      // -18 : de x=8 (titre) au bord gauche de l'image
    private static final char NEXT_TILE = '';  // -1  : la tuile droite commence à 98
    private static final char NEXT_ROW = '';   // -197 : retour au début de la rangée

    private GuiTextures() {
    }

    /** Titre du coffre qui dessine tout le fond du menu (blanc : la couleur du texte teinte les glyphes). */
    public static Component title(Screen screen) {
        StringBuilder b = new StringBuilder().append(START);
        for (int row = 0; row < 4; row++) {
            b.appendCodePoint(screen.base + row * 2).append(NEXT_TILE)
                    .appendCodePoint(screen.base + row * 2 + 1).append(NEXT_ROW);
        }
        return Component.text(b.toString()).font(FONT).color(NamedTextColor.WHITE);
    }

    /**
     * Zone cliquable : objet invisible (avec le pack) ou vitre colorée (sans le pack),
     * avec le même nom et la même infobulle dans les deux cas.
     */
    public static ItemStack zone(boolean textured, Material glass, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(textured ? Material.PAPER : glass);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        if (textured) {
            meta.setItemModel(INVISIBLE);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Zone cliquable invisible (menu texturé uniquement). */
    public static ItemStack zone(Component name, List<Component> lore) {
        return zone(true, Material.PAPER, name, lore);
    }

    /** Sans le pack : fond de vitres noires, comme les anciens menus. */
    public static void fallbackBackground(org.bukkit.inventory.Inventory inv) {
        ItemStack background = Menus.decor(Material.BLACK_STAINED_GLASS_PANE, null);
        for (int slot = 0; slot < inv.getSize(); slot++) {
            inv.setItem(slot, background);
        }
    }
}
