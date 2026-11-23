package fr.orbitgames.hcf.events.command;

import fr.orbitgames.hcf.events.OrbitHCFEventsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Locale;

/**
 * /eotw : lancement et arret de la fin de saison.
 */
public class EotwCommand implements CommandExecutor {

    private final OrbitHCFEventsPlugin plugin;

    public EotwCommand(OrbitHCFEventsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }
        if (!ZoneEditor.checkAdmin(plugin, sender)) {
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                if (!plugin.getEotwManager().start()) {
                    plugin.getMessages().send(sender, "eotw.already-active");
                }
            }
            case "stop" -> {
                if (!plugin.getEotwManager().isActive()) {
                    plugin.getMessages().send(sender, "eotw.not-active");
                } else {
                    plugin.getEotwManager().stop();
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
