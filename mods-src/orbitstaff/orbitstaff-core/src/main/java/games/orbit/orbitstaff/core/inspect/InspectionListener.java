package games.orbit.orbitstaff.core.inspect;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Applique la lecture seule sur les inventaires d'inspection : toute tentative
 * de modification est annulee.
 */
public final class InspectionListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof InspectionHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof InspectionHolder) {
            event.setCancelled(true);
        }
    }
}
