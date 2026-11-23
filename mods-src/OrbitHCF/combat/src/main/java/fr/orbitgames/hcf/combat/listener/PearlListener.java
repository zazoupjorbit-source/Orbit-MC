package fr.orbitgames.hcf.combat.listener;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.TimerKeys;
import fr.orbitgames.hcf.api.service.ClaimService;
import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.combat.OrbitHCFCombatPlugin;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.time.Duration;

/**
 * Ender pearls : cooldown au lancer, verification de la destination.
 */
public class PearlListener implements Listener {

    private final OrbitHCFCombatPlugin plugin;

    public PearlListener(OrbitHCFCombatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)
                || !(pearl.getShooter() instanceof Player player)) {
            return;
        }
        Duration remaining = OrbitHCF.timers().getRemaining(player.getUniqueId(), TimerKeys.ENDER_PEARL);
        if (!remaining.isZero()) {
            event.setCancelled(true);
            player.updateInventory();
            plugin.getMessages().send(player, "pearl.cooldown", "temps", DurationFormat.compact(remaining));
            return;
        }
        Duration cooldown = Duration.ofSeconds(
                plugin.getCombatConfig().getLong("ender-pearl.cooldown-seconds", 16L));
        OrbitHCF.timers().setTimer(player.getUniqueId(), TimerKeys.ENDER_PEARL, cooldown);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        if (!plugin.getCombatConfig().getBoolean("ender-pearl.block-safezone-when-tagged", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.getCombatManager().isCombatTagged(player.getUniqueId())) {
            return;
        }
        ClaimService claims = OrbitHCF.claims();
        if (claims != null && event.getTo() != null && claims.isSafezone(event.getTo())) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "pearl.safezone-blocked");
            if (plugin.getCombatConfig().getBoolean("ender-pearl.refund-on-cancel", true)) {
                OrbitHCF.timers().clearTimer(player.getUniqueId(), TimerKeys.ENDER_PEARL);
            }
        }
    }
}
