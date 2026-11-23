package fr.orbitgames.hcf.display.bar;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.TimerKeys;
import fr.orbitgames.hcf.api.service.TimerService;
import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.api.util.Text;
import fr.orbitgames.hcf.display.OrbitHCFDisplayPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Action bar : combat tag prioritaire, puis cooldown des perles.
 * Uniquement si OrbitHCF-Combat est present.
 */
public class ActionBarManager {

    private final OrbitHCFDisplayPlugin plugin;

    public ActionBarManager(OrbitHCFDisplayPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        if (!plugin.getScoreboardConfig().getBoolean("actionbar.enabled", true)) {
            return;
        }
        if (OrbitHCF.combat() == null) {
            return;
        }
        TimerService timers = OrbitHCF.timers();
        if (timers == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Duration combat = timers.getRemaining(player.getUniqueId(), TimerKeys.COMBAT_TAG);
            if (!combat.isZero()) {
                send(player, plugin.getScoreboardConfig().getString("actionbar.combat", ""), combat);
                continue;
            }
            Duration pearl = timers.getRemaining(player.getUniqueId(), TimerKeys.ENDER_PEARL);
            if (!pearl.isZero()) {
                send(player, plugin.getScoreboardConfig().getString("actionbar.pearl", ""), pearl);
            }
        }
    }

    private void send(Player player, String format, Duration duration) {
        if (format == null || format.isBlank()) {
            return;
        }
        String text = Text.color(Text.replace(format, "temps", DurationFormat.compact(duration)));
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
    }
}
