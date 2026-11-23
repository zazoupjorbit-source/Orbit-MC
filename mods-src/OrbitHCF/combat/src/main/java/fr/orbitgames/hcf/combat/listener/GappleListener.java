package fr.orbitgames.hcf.combat.listener;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.TimerKeys;
import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.combat.OrbitHCFCombatPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import java.time.Duration;

/**
 * Cooldowns des golden apples et enchanted golden apples.
 */
public class GappleListener implements Listener {

    private final OrbitHCFCombatPlugin plugin;

    public GappleListener(OrbitHCFCombatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Material type = event.getItem().getType();
        if (type == Material.GOLDEN_APPLE) {
            handle(event, TimerKeys.GOLDEN_APPLE,
                    plugin.getCombatConfig().getLong("golden-apple.cooldown-seconds", 15L),
                    "gapple.cooldown");
        } else if (type == Material.ENCHANTED_GOLDEN_APPLE) {
            handle(event, TimerKeys.ENCHANTED_GOLDEN_APPLE,
                    plugin.getCombatConfig().getLong("enchanted-golden-apple.cooldown-seconds", 21600L),
                    "gapple.egapple-cooldown");
        }
    }

    private void handle(PlayerItemConsumeEvent event, String key, long cooldownSeconds, String messageKey) {
        Player player = event.getPlayer();
        Duration remaining = OrbitHCF.timers().getRemaining(player.getUniqueId(), key);
        if (!remaining.isZero()) {
            event.setCancelled(true);
            plugin.getMessages().send(player, messageKey, "temps", DurationFormat.compact(remaining));
            return;
        }
        // L'egapple a un cooldown persistant (survit aux redemarrages)
        boolean persistent = TimerKeys.ENCHANTED_GOLDEN_APPLE.equals(key);
        OrbitHCF.timers().setTimer(player.getUniqueId(), key, Duration.ofSeconds(cooldownSeconds), persistent);
    }
}
