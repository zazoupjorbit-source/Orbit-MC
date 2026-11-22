package fr.orbit.devcore.command;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.message.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/** /orbitdev reload | info | pack | feu. */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final OrbitDevCore plugin;

    public AdminCommand(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("orbitdev.admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        String sub = args.length == 0 ? "info" : args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                plugin.messages().send(sender, "general.reloaded");
            }
            case "info" -> plugin.messages().send(sender, "general.info",
                    Messages.ph("version", plugin.getPluginMeta().getVersion()),
                    Messages.ph("warps", plugin.locations().warps().size()),
                    Messages.ph("npcs", plugin.npcs().all().size()),
                    Messages.ph("proxy", plugin.connector().proxyEnabled() ? "on" : "off"));
            case "pack" -> {
                // Renvoie le pack du lobby à tous les joueurs connectés (après une mise à jour du jar).
                if (!plugin.lobbyPack().enabled()) {
                    plugin.messages().send(sender, "pack.disabled");
                    return true;
                }
                plugin.getServer().getOnlinePlayers().forEach(plugin.lobbyPack()::send);
                plugin.messages().send(sender, "pack.sent", Messages.ph("count", plugin.getServer().getOnlinePlayers().size()));
            }
            // Outils de test du mini-jeu feu d'artifice : cibles d'entraînement, série forcée.
            case "feu" -> plugin.fireworkGame().command(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            default -> plugin.messages().send(sender, "general.usage", Messages.ph("usage", "/orbitdev <reload|info|pack|feu>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("orbitdev.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return Completions.filter(List.of("reload", "info", "pack", "feu"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("feu")) {
            return Completions.filter(List.of("cible", "riposte", "retirer", "serie"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("feu") && args[1].equalsIgnoreCase("serie")) {
            return Completions.filter(List.of("2", "3", "5", "7", "10"), args[2]);
        }
        return List.of();
    }
}
