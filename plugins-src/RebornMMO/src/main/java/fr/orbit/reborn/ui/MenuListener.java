package fr.orbit.reborn.ui;

import fr.orbit.reborn.RebornMMO;
import fr.orbit.reborn.player.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class MenuListener implements Listener {

    private final RebornMMO plugin;

    public MenuListener(RebornMMO plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu menu)) {
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() != top || !(e.getWhoClicked() instanceof Player)) {
            return;
        }
        int slot = e.getRawSlot();
        ClickType type = e.getClick();
        // Action au tick suivant : ouvrir un autre menu pendant un clic est déconseillé.
        plugin.getServer().getScheduler().runTask(plugin, () -> menu.click(slot, type));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu menu) || !menu.forceOpen || !(e.getPlayer() instanceof Player p)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PlayerData d = plugin.players().loaded(p.getUniqueId());
            if (p.isOnline() && d != null && d.classId == null && !(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu)) {
                Menus.classSelect(plugin, p);
            }
        }, 2L);
    }
}
