package fr.orbit.casino;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Petit menu coffre : chaque case porte son action ; rien ne peut être pris ni déposé. */
public final class Menu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    private Runnable onClose;

    public Menu(int rows, String title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, Txt.mm(title));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Menu set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        if (action == null) {
            actions.remove(slot);
        } else {
            actions.put(slot, action);
        }
        return this;
    }

    public Menu set(int slot, ItemStack item) {
        return set(slot, item, null);
    }

    public Menu onClose(Runnable r) {
        this.onClose = r;
        return this;
    }

    public void fill(Material glass) {
        ItemStack pane = item(glass, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, pane);
            }
        }
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }

    public static ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        return named(item, name, lore);
    }

    public static ItemStack named(ItemStack item, String name, String... lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Txt.mm(name));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) {
            lines.add(Txt.mm(l));
        }
        meta.lore(lines);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /** Écouteur commun à tous les menus du plugin. */
    public static final class Clicks implements Listener {

        private final org.bukkit.plugin.Plugin plugin;

        public Clicks(org.bukkit.plugin.Plugin plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder() instanceof Menu menu)) {
                return;
            }
            event.setCancelled(true);
            if (event.getClickedInventory() != menu.inventory) {
                return;
            }
            Consumer<InventoryClickEvent> action = menu.actions.get(event.getRawSlot());
            if (action != null) {
                // au tick suivant : ouvrir ou fermer un inventaire pendant le clic est déconseillé
                Bukkit.getScheduler().runTask(plugin, () -> action.accept(event));
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (event.getInventory().getHolder() instanceof Menu) {
                event.setCancelled(true);
            }
        }

        @EventHandler
        public void onClose(InventoryCloseEvent event) {
            if (event.getInventory().getHolder() instanceof Menu menu && menu.onClose != null) {
                menu.onClose.run();
            }
        }
    }
}
