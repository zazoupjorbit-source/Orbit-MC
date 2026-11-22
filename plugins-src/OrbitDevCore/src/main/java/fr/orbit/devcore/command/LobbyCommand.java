package fr.orbit.devcore.command;

import fr.orbit.devcore.OrbitDevCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** /lobby : retour au lobby du proxy (remplace la boussole sur les serveurs où elle est désactivée). */
public final class LobbyCommand implements CommandExecutor, TabCompleter {

    private final OrbitDevCore plugin;

    public LobbyCommand(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.players-only");
            return true;
        }
        plugin.connector().send(player, plugin.getConfig().getString("proxy.lobby-server", "lobby"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
