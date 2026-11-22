package fr.orbit.devcore.command;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.Teleports;
import fr.orbit.devcore.message.Messages;
import fr.orbit.devcore.storage.LocationStore;
import fr.orbit.devcore.storage.Warp;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /warp [nom], /setwarp <nom> [icône], /delwarp <nom>. */
public final class WarpCommand implements CommandExecutor, TabCompleter {

    private final OrbitDevCore plugin;

    public WarpCommand(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.players-only");
            return true;
        }
        switch (command.getName().toLowerCase()) {
            case "setwarp" -> setWarp(player, args);
            case "delwarp" -> delWarp(player, args);
            default -> warp(player, args);
        }
        return true;
    }

    private void warp(Player player, String[] args) {
        if (!player.hasPermission("orbitdev.warp")) {
            plugin.messages().send(player, "general.no-permission");
            return;
        }
        if (args.length == 0) {
            plugin.warpMenu().open(player);
            return;
        }
        Warp warp = plugin.locations().warp(args[0]);
        if (warp == null) {
            plugin.messages().send(player, "warp.not-found", Messages.ph("name", args[0]));
            return;
        }
        Location target = warp.location().toLocation();
        if (target == null) {
            plugin.messages().send(player, "warp.world-missing", Messages.ph("name", warp.name()));
            return;
        }
        Teleports.teleport(plugin, player, target, "warp.teleported", Messages.ph("name", warp.name()));
    }

    private void setWarp(Player player, String[] args) {
        if (!player.hasPermission("orbitdev.admin")) {
            plugin.messages().send(player, "general.no-permission");
            return;
        }
        if (args.length < 1) {
            plugin.messages().send(player, "general.usage", Messages.ph("usage", "/setwarp <nom> [icône]"));
            return;
        }
        String name = args[0];
        if (!LocationStore.NAME_PATTERN.matcher(name).matches()) {
            plugin.messages().send(player, "warp.invalid-name");
            return;
        }
        Material icon = null;
        if (args.length >= 2) {
            icon = Material.matchMaterial(args[1]);
            if (icon == null || !icon.isItem() || icon.isAir()) {
                plugin.messages().send(player, "warp.unknown-icon", Messages.ph("icon", args[1]));
                return;
            }
        }
        plugin.locations().setWarp(name, player.getLocation(), icon);
        plugin.messages().send(player, "warp.set", Messages.ph("name", name));
    }

    private void delWarp(Player player, String[] args) {
        if (!player.hasPermission("orbitdev.admin")) {
            plugin.messages().send(player, "general.no-permission");
            return;
        }
        if (args.length < 1) {
            plugin.messages().send(player, "general.usage", Messages.ph("usage", "/delwarp <nom>"));
            return;
        }
        if (plugin.locations().deleteWarp(args[0])) {
            plugin.messages().send(player, "warp.deleted", Messages.ph("name", args[0]));
        } else {
            plugin.messages().send(player, "warp.not-found", Messages.ph("name", args[0]));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if (args.length == 1 && !name.equals("setwarp")) {
            return Completions.filter(warpNames(), args[0]);
        }
        if (args.length == 2 && name.equals("setwarp")) {
            List<String> materials = new ArrayList<>();
            for (Material material : Material.values()) {
                if (!material.isLegacy() && material.isItem() && !material.isAir()) {
                    materials.add(material.name().toLowerCase());
                }
            }
            return Completions.filter(materials, args[1]);
        }
        return List.of();
    }

    private List<String> warpNames() {
        List<String> names = new ArrayList<>();
        for (Warp warp : plugin.locations().warps()) {
            names.add(warp.name());
        }
        return names;
    }
}
