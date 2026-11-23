package fr.orbitgames.hcf.events.command;

import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.events.OrbitHCFEventsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Locale;

/**
 * /sotw : lancement et arret (admin), retrait volontaire de protection (/sotw off).
 */
public class SotwCommand implements CommandExecutor {

    private final OrbitHCFEventsPlugin plugin;

    public SotwCommand(OrbitHCFEventsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "time";
        switch (sub) {
            case "start" -> {
                if (!ZoneEditor.checkAdmin(plugin, sender) || args.length < 2) {
                    return args.length >= 2;
                }
                Duration duration = DurationFormat.parse(args[1]);
                if (duration.isZero()) {
                    plugin.getMessages().send(sender, "general.invalid-duration", "valeur", args[1]);
                    return true;
                }
                plugin.getSotwManager().start(duration);
            }
            case "stop" -> {
                if (!ZoneEditor.checkAdmin(plugin, sender)) {
                    return true;
                }
                if (!plugin.getSotwManager().isActive()) {
                    plugin.getMessages().send(sender, "sotw.not-active");
                } else {
                    plugin.getSotwManager().stop();
                }
            }
            case "off", "disable" -> {
                Player player = ZoneEditor.asPlayer(plugin, sender);
                if (player == null) {
                    return true;
                }
                if (!plugin.getSotwManager().isActive()) {
                    plugin.getMessages().send(player, "sotw.not-active");
                    return true;
                }
                if (plugin.getSotwManager().removeProtection(player.getUniqueId())) {
                    plugin.getMessages().send(player, "sotw.protection-removed");
                } else {
                    plugin.getMessages().send(player, "sotw.already-removed");
                }
            }
            default -> {
                if (!plugin.getSotwManager().isActive()) {
                    plugin.getMessages().send(sender, "sotw.not-active");
                } else {
                    plugin.getMessages().send(sender, "sotw.time",
                            "temps", DurationFormat.compact(plugin.getSotwManager().getRemaining()));
                }
            }
        }
        return true;
    }
}
