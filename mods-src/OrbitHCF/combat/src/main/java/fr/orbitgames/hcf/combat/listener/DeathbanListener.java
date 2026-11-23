package fr.orbitgames.hcf.combat.listener;

import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.combat.OrbitHCFCombatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.time.Duration;

/**
 * Deathban : application a la mort, refus de connexion pendant le ban.
 */
public class DeathbanListener implements Listener {

    private final OrbitHCFCombatPlugin plugin;

    public DeathbanListener(OrbitHCFCombatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!plugin.getCombatManager().isDeathbanEnabled()
                || victim.hasPermission("orbithcf.combat.deathban.bypass")) {
            return;
        }
        Duration duration = Duration.ofSeconds(
                plugin.getDeathbanConfig().getLong("default-duration-seconds", 3600L));
        plugin.getCombatManager().deathban(victim.getUniqueId(), duration);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (victim.isOnline()) {
                victim.kickPlayer(plugin.getMessages().format("deathban.banned-kick",
                        "temps", DurationFormat.compact(duration)));
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        Duration remaining = plugin.getCombatManager().getDeathbanRemaining(event.getUniqueId());
        if (remaining.isZero()) {
            return;
        }
        if (!plugin.getCombatManager().isDeathbanEnabled()) {
            return;
        }
        int lives = plugin.getCombatManager().getLives(event.getUniqueId());
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                plugin.getMessages().format("deathban.banned-join",
                        "temps", DurationFormat.compact(remaining),
                        "vies", lives));
    }
}
