package fr.orbit.devcore.command;

import fr.orbit.devcore.OrbitDevCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** /boutique : ouvre le menu Boutique (aussi accessible depuis la boussole). */
public final class ShopCommand implements CommandExecutor, TabCompleter {

    private final OrbitDevCore plugin;

    public ShopCommand(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.players-only");
            return true;
        }
        if (!player.hasPermission("orbitdev.shop")) {
            plugin.messages().send(player, "general.no-permission");
            return true;
        }
        plugin.shopMenu().open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
