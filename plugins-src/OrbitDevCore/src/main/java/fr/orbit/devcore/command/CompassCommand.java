package fr.orbit.devcore.command;

import fr.orbit.devcore.OrbitDevCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** /boussole : redonne la boussole de téléportation. */
public final class CompassCommand implements CommandExecutor, TabCompleter {

    private final OrbitDevCore plugin;

    public CompassCommand(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.players-only");
            return true;
        }
        if (!player.hasPermission("orbitdev.compass")) {
            plugin.messages().send(player, "general.no-permission");
            return true;
        }
        if (!plugin.compass().enabled()) {
            plugin.messages().send(player, "compass.lobby-only");
            return true;
        }
        switch (plugin.compass().give(player)) {
            case GIVEN -> plugin.messages().send(player, "compass.given");
            case ALREADY -> plugin.messages().send(player, "compass.already");
            case INVENTORY_FULL -> plugin.messages().send(player, "compass.inventory-full");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
