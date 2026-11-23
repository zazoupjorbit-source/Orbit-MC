package fr.orbitgames.hcf.core.listener;

import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Annule les warmups (home, stuck, logout) au mouvement ou aux degats.
 */
public class WarmupListener implements Listener {

    private final OrbitHCFCorePlugin plugin;

    public WarmupListener(OrbitHCFCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.getWarmupManager().hasAny(player.getUniqueId())) {
            plugin.getWarmupManager().cancelAll(player.getUniqueId(), true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (plugin.getWarmupManager().hasAny(player.getUniqueId())) {
            plugin.getWarmupManager().cancelAll(player.getUniqueId(), true);
        }
    }
}
