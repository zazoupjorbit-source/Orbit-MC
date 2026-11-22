package fr.orbit.devcore.compass;

import fr.orbit.devcore.OrbitDevCore;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Clic droit avec la boussole → menu des warps ; verrouillage de l'objet. */
public final class CompassListener implements Listener {

    private final OrbitDevCore plugin;

    public CompassListener(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    // ignoreCancelled = false : un clic droit dans le vide arrive déjà « annulé » par défaut.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!plugin.compass().isCompass(event.getItem())) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        Player player = event.getPlayer();
        if (!plugin.compass().enabled()) {
            // Boussole restée d'avant : elle n'a plus cours ici.
            plugin.compass().removeAll(player);
            plugin.messages().send(player, "compass.lobby-only");
            return;
        }
        if (!player.hasPermission("orbitdev.warp")) {
            plugin.messages().send(player, "general.no-permission");
            return;
        }
        plugin.warpMenu().open(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (locked() && plugin.compass().isCompass(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (locked()) {
            event.getDrops().removeIf(plugin.compass()::isCompass);
        }
    }

    private boolean locked() {
        return plugin.getConfig().getBoolean("compass.locked", true);
    }
}
