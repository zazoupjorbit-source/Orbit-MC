package fr.orbitgames.hcf.core.command;

import fr.orbitgames.hcf.api.TimerKeys;
import fr.orbitgames.hcf.api.model.FactionType;
import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * /logout : deconnexion securisee. Instantanee en safezone,
 * sinon apres un delai annulable (mouvement / degats).
 */
public class LogoutCommand implements CommandExecutor {

    private final OrbitHCFCorePlugin plugin;

    public LogoutCommand(OrbitHCFCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.player-only");
            return true;
        }
        if (plugin.getClaimManager().getZoneType(player.getLocation()) == FactionType.SAFEZONE) {
            fr.orbitgames.hcf.api.HcfWorlds.eject(player, plugin.getMessages().format("logout.success"));
            return true;
        }
        if (plugin.getWarmupManager().has(player.getUniqueId(), TimerKeys.LOGOUT)) {
            plugin.getMessages().send(player, "logout.already");
            return true;
        }
        Duration delay = Duration.ofSeconds(
                plugin.getFactionsConfig().getLong("timers.logout-delay-seconds", 30L));
        plugin.getMessages().send(player, "logout.start", "temps", DurationFormat.compact(delay));
        plugin.getWarmupManager().start(player, TimerKeys.LOGOUT, delay,
                () -> fr.orbitgames.hcf.api.HcfWorlds.eject(player, plugin.getMessages().format("logout.success")),
                "logout.cancelled");
        return true;
    }
}
