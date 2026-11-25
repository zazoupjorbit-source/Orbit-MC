package fr.orbit.vol;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Petit menu à boutons : chaque case porte son action. Utilisé par les œufs, titres, base, échanges… */
final class Gui implements InventoryHolder {

    private final Inventory inv;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
    Runnable aLaFermeture;
    boolean verrouille;

    Gui(int taille, String titre) {
        this.inv = Bukkit.createInventory(this, taille, Txt.mm(titre));
    }

    Gui case_(int slot, ItemStack it, Consumer<Player> action) {
        inv.setItem(slot, it);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
        return this;
    }

    void ouvrir(Player p) {
        Objets.habiller(inv, p);
        p.openInventory(inv);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    static final class Ecoute implements Listener {
        @EventHandler
        public void onClic(InventoryClickEvent e) {
            if (!(e.getInventory().getHolder() instanceof Gui g)) return;
            e.setCancelled(true);
            if (g.verrouille || e.getClickedInventory() != e.getView().getTopInventory()) return;
            Consumer<Player> a = g.actions.get(e.getRawSlot());
            if (a != null && e.getWhoClicked() instanceof Player p) a.accept(p);
        }

        @EventHandler
        public void onGlisser(InventoryDragEvent e) {
            if (e.getInventory().getHolder() instanceof Gui) e.setCancelled(true);
        }

        @EventHandler
        public void onFermer(InventoryCloseEvent e) {
            if (e.getInventory().getHolder() instanceof Gui g && g.aLaFermeture != null) g.aLaFermeture.run();
        }
    }
}
