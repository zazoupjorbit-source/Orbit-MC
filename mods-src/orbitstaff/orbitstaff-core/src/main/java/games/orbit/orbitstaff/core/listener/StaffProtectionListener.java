package games.orbit.orbitstaff.core.listener;

import games.orbit.orbitstaff.core.staff.StaffModeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * Protege l'environnement pendant le mode staff : pas de ramassage, pas de
 * modification de blocs, pas de perte d'objets de la hotbar staff.
 */
public final class StaffProtectionListener implements Listener {

    private final StaffModeManager staffModeManager;

    public StaffProtectionListener(StaffModeManager staffModeManager) {
        this.staffModeManager = staffModeManager;
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && staffModeManager.isStaff(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (staffModeManager.isStaff(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (staffModeManager.isStaff(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (staffModeManager.isStaff(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
