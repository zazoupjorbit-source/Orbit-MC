package fr.orbit.mmo.ui;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.player.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class MenuListener implements Listener {

    private final OrbitMMO plugin;

    public MenuListener(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu menu)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        if (e.getClickedInventory() != null && e.getClickedInventory() != top && menu.bottomClick != null) {
            int slot = e.getSlot();
            ClickType type = e.getClick();
            plugin.getServer().getScheduler().runTask(plugin, () -> menu.bottomClick.click(slot, type));
            return;
        }
        if (e.getClickedInventory() != top) {
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
        // Le joueur quitte les menus du PNJ (et n'en ouvre pas un autre) : sa carte s'en va avec.
        if (e.getReason() != InventoryCloseEvent.Reason.OPEN_NEW && e.getPlayer() instanceof Player closer) {
            plugin.hud().npcCardEnd(closer);
        }
        if (!(e.getInventory().getHolder() instanceof Menu menu) || !menu.forceOpen || !(e.getPlayer() instanceof Player p)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PlayerData d = plugin.players().loaded(p.getUniqueId());
            if (!p.isOnline() || d == null || p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                return;
            }
            if (!plugin.progression().valid(d)) {
                Menus.factionSelect(plugin, p);
            } else if (plugin.progression().spec(d) == null) {
                Menus.specSelect(plugin, p);
            }
        }, 2L);
    }
}
