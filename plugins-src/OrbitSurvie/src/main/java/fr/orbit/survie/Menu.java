package fr.orbit.survie;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** Menu coffre du plugin : rien ne peut y être pris ni déposé, chaque case a son action. */
public final class Menu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();

    public Menu(int rows, String titre) {
        this.inventory = Bukkit.createInventory(this, rows * 9, Msg.raw(titre));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void set(int slot, ItemStack item, BiConsumer<Player, ClickType> action) {
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        } else {
            actions.remove(slot);
        }
    }

    public static ItemStack icone(Material m, String nom, String... lore) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(Msg.item(nom));
            meta.lore(Arrays.stream(lore).map(Msg::item).toList());
        });
        return it;
    }

    /** Bordure bleue, coins dorés (même cadre que le marché). */
    public static void cadre(Menu menu, int lignes) {
        for (int s = 0; s < lignes * 9; s++) {
            int ligne = s / 9, col = s % 9;
            boolean bord = ligne == 0 || ligne == lignes - 1 || col == 0 || col == 8;
            boolean coin = (ligne == 0 || ligne == lignes - 1) && (col == 0 || col == 8);
            if (bord) {
                menu.set(s, icone(coin ? Material.YELLOW_STAINED_GLASS_PANE : Material.BLUE_STAINED_GLASS_PANE, " "), null);
            }
        }
    }

    /** Barre de progression en 20 crans. */
    public static String barre(double part) {
        int pleins = (int) Math.round(Math.max(0, Math.min(1, part)) * 20);
        return "<#3DDCB0>" + "▌".repeat(pleins) + "<dark_gray>" + "▌".repeat(20 - pleins);
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }

    /** Écoute les clics de tous les menus du plugin. */
    public static final class Ecoute implements Listener {

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) {
                return;
            }
            event.setCancelled(true);
            if (event.getClickedInventory() != menu.inventory || !(event.getWhoClicked() instanceof Player p)) {
                return;
            }
            BiConsumer<Player, ClickType> action = menu.actions.get(event.getSlot());
            if (action != null) {
                action.accept(p, event.getClick());
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
                event.setCancelled(true);
            }
        }
    }
}
