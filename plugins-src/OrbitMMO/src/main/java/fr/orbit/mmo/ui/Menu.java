package fr.orbit.mmo.ui;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.hud.MenuSkin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Menu en coffre : chaque case peut porter une action. Avec {@link #skin} et le pack chargé chez le joueur, le
 * coffre est rouvert avec le fond texturé de la DA MMO (titre {@link MenuSkin}) et sans vitres ; sinon le titre
 * texte et les vitres de {@link Menus#frame} sont gardés.
 */
public final class Menu implements InventoryHolder {

    private final int rows;
    /** Coffre d'origine (titre texte) : reçoit toujours les objets. */
    private final Inventory base;
    /** Coffre affiché : {@link #base} ou sa copie habillée. */
    private Inventory inventory;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
    /** Rouvre le menu s'il est fermé (choix de classe obligatoire). */
    boolean forceOpen;
    /** Vitres dans les cases vides (posées à l'ouverture, seulement sans habillage). */
    boolean framed;
    /** Clic sur une case de l'inventaire du joueur (null : ignoré). Reçoit l'index de la case. */
    BottomClick bottomClick;

    private MenuSkin.Screen skinScreen;
    private String skinTitle, skinSub, skinRight;

    public interface BottomClick {
        void click(int slot, ClickType type);
    }

    public void onBottomClick(BottomClick action) {
        this.bottomClick = action;
    }

    public Menu(int rows, Component title) {
        this.rows = rows;
        this.base = Bukkit.createInventory(this, rows * 9, title);
        this.inventory = base;
    }

    /** Habillage générique : titre dans la cartouche, sous-titre à gauche et texte doré à droite (null possibles). */
    public Menu skin(String title, String sub, String right) {
        this.skinTitle = title;
        this.skinSub = sub;
        this.skinRight = right;
        return this;
    }

    public Menu skin(String title) {
        return skin(title, null, null);
    }

    /** Habillage dessiné à la main pour ce menu. */
    public Menu skin(MenuSkin.Screen screen, String title) {
        this.skinScreen = screen;
        return skin(title, null, null);
    }

    public void set(int slot, ItemStack item, Consumer<ClickType> action) {
        base.setItem(slot, item);
        if (inventory != base) {
            inventory.setItem(slot, item);
        }
        if (action != null) {
            actions.put(slot, action);
        } else {
            actions.remove(slot);
        }
    }

    public void open(Player p) {
        if (skinTitle != null && MenuSkin.supports(rows)
                && OrbitMMO.getPlugin(OrbitMMO.class).pack().isLoaded(p.getUniqueId())) {
            if (inventory == base) {
                ItemStack[] items = base.getContents();
                inventory = Bukkit.createInventory(this, rows * 9,
                        MenuSkin.title(skinScreen, rows, skinTitle, skinSub, skinRight, items));
                inventory.setContents(items);
            }
        } else {
            if (framed) {
                Menus.fillPanes(base);
            }
            inventory = base;
        }
        p.openInventory(inventory);
    }

    void click(int slot, ClickType type) {
        Consumer<ClickType> action = actions.get(slot);
        if (action != null) {
            action.accept(type);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
