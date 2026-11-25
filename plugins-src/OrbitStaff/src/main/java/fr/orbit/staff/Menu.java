package fr.orbit.staff;

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

import java.util.HashMap;
import java.util.Map;

/** Menu coffre simple : une action par case, tous les clics annulés. */
public class Menu implements InventoryHolder {

    public interface Action {
        void clic(Player p, ClickType type);
    }

    private final Inventory inv;
    private final Map<Integer, Action> actions = new HashMap<>();

    public Menu(int lignes, String titre) {
        this.inv = Bukkit.createInventory(this, lignes * 9, Txt.mm(titre));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void set(int slot, ItemStack it, Action a) {
        inv.setItem(slot, it);
        if (a == null) {
            actions.remove(slot);
        } else {
            actions.put(slot, a);
        }
    }

    public void set(int slot, ItemStack it) {
        set(slot, it, null);
    }

    /** Bordure de vitres sur la dernière ligne. */
    public void bas() {
        int debut = inv.getSize() - 9;
        for (int i = debut; i < inv.getSize(); i++) {
            set(i, Txt.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        }
    }

    public void ouvrir(Player p) {
        p.openInventory(inv);
    }

    /** Écoute unique pour tous les menus. */
    public static final class Ecoute implements Listener {
        @EventHandler
        public void onClick(InventoryClickEvent e) {
            if (!(e.getInventory().getHolder(false) instanceof Menu m)) {
                return;
            }
            e.setCancelled(true);
            if (e.getClickedInventory() != m.inv || !(e.getWhoClicked() instanceof Player p)) {
                return;
            }
            Action a = m.actions.get(e.getSlot());
            if (a != null) {
                a.clic(p, e.getClick());
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent e) {
            if (e.getInventory().getHolder(false) instanceof Menu) {
                e.setCancelled(true);
            }
        }
    }
}
