package fr.orbitgames.hcf.classes.listener;

import fr.orbitgames.hcf.api.model.HcfClass;
import fr.orbitgames.hcf.classes.OrbitHCFClassesPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Capacites actives du Bard : clic droit avec un objet configure.
 */
public class BardListener implements Listener {

    private final OrbitHCFClassesPlugin plugin;

    public BardListener(OrbitHCFClassesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.getClassManager().getActiveClass(player.getUniqueId())
                .filter(c -> c == HcfClass.BARD).isEmpty()) {
            return;
        }
        if (plugin.getBardManager().getItem(player.getInventory().getItemInMainHand().getType()) == null) {
            return;
        }
        if (plugin.getBardManager().useActive(player)) {
            event.setCancelled(true);
        }
    }
}
