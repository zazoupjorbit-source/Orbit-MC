package fr.orbitgames.hcf.combat.command;

import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.combat.OrbitHCFCombatPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Locale;

/**
 * /pvp [time|enable] : consultation et retrait du PvP Timer.
 */
public class PvpCommand implements CommandExecutor {

    private final OrbitHCFCombatPlugin plugin;

    public PvpCommand(OrbitHCFCombatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.player-only");
            return true;
        }
        Duration remaining = plugin.getCombatManager().getPvpTimerRemaining(player.getUniqueId());
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "time";
        switch (sub) {
            case "enable", "off", "remove" -> {
                if (remaining.isZero()) {
                    plugin.getMessages().send(player, "pvptimer.none");
                } else {
                    plugin.getCombatManager().removePvpTimer(player.getUniqueId());
                    plugin.getMessages().send(player, "pvptimer.removed");
                }
            }
            default -> {
                if (remaining.isZero()) {
                    plugin.getMessages().send(player, "pvptimer.none");
                } else {
                    plugin.getMessages().send(player, "pvptimer.remaining",
                            "temps", DurationFormat.compact(remaining));
                }
            }
        }
        return true;
    }
}
