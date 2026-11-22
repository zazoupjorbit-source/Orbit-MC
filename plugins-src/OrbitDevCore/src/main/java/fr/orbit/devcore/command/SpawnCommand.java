package fr.orbit.devcore.command;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.Teleports;
import fr.orbit.devcore.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** /spawn [joueur] et /setspawn. */
public final class SpawnCommand implements CommandExecutor, TabCompleter {

    private final OrbitDevCore plugin;

    public SpawnCommand(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("setspawn")) {
            return setSpawn(sender);
        }
        return spawn(sender, args);
    }

    private boolean spawn(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 1) {
            if (!sender.hasPermission("orbitdev.spawn.others")) {
                plugin.messages().send(sender, "general.no-permission");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                plugin.messages().send(sender, "general.player-not-found", Messages.ph("player", args[0]));
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "general.usage", Messages.ph("usage", "/spawn <joueur>"));
                return true;
            }
            if (!sender.hasPermission("orbitdev.spawn")) {
                plugin.messages().send(sender, "general.no-permission");
                return true;
            }
            target = player;
        }
        Location spawn = plugin.locations().spawn();
        if (spawn == null) {
            plugin.messages().send(sender, "spawn.missing");
            return true;
        }
        Teleports.teleport(plugin, target, spawn, "spawn.teleported");
        if (target != sender) {
            plugin.messages().send(sender, "spawn.teleported-other", Messages.ph("player", target.getName()));
        }
        return true;
    }

    private boolean setSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.players-only");
            return true;
        }
        if (!player.hasPermission("orbitdev.admin")) {
            plugin.messages().send(player, "general.no-permission");
            return true;
        }
        plugin.locations().setSpawn(player.getLocation());
        player.getWorld().setSpawnLocation(player.getLocation());
        plugin.messages().send(player, "spawn.set");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("spawn") && args.length == 1 && sender.hasPermission("orbitdev.spawn.others")) {
            return Completions.players(args[0]);
        }
        return List.of();
    }
}
