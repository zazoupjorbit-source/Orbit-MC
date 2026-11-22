package fr.orbit.devcore.command;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.Teleports;
import fr.orbit.devcore.message.Messages;
import fr.orbit.devcore.world.WorldService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /devworld create <nom> [flat|normal|void] | list | tp <nom>. */
public final class WorldCommand implements CommandExecutor, TabCompleter {

    private final OrbitDevCore plugin;

    public WorldCommand(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("orbitdev.admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> create(sender, args);
            case "tp" -> tp(sender, args);
            case "list" -> list(sender);
            default -> plugin.messages().send(sender, "general.usage",
                    Messages.ph("usage", "/devworld <create|list|tp> [nom] [flat|normal|void]"));
        }
        return true;
    }

    private void create(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "general.usage", Messages.ph("usage", "/devworld create <nom> [flat|normal|void]"));
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (!WorldService.NAME_PATTERN.matcher(name).matches()) {
            plugin.messages().send(sender, "world.invalid-name");
            return;
        }
        WorldService.Kind kind = WorldService.parseKind(args.length >= 3 ? args[2] : "flat");
        if (kind == null) {
            plugin.messages().send(sender, "world.invalid-type", Messages.ph("type", args[2]));
            return;
        }
        plugin.messages().send(sender, "world.creating", Messages.ph("world", name));
        World world = plugin.worlds().create(name, kind);
        if (world == null) {
            plugin.messages().send(sender, "world.failed", Messages.ph("world", name));
            return;
        }
        plugin.messages().send(sender, "world.created", Messages.ph("world", name),
                Messages.ph("type", kind.name().toLowerCase(Locale.ROOT)));
    }

    private void tp(CommandSender sender, String[] args) {
        Player player = Senders.player(sender);
        if (player == null) {
            plugin.messages().send(sender, "general.players-only");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "general.usage", Messages.ph("usage", "/devworld tp <nom>"));
            return;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            plugin.messages().send(sender, "npc.world-missing", Messages.ph("world", args[1]));
            return;
        }
        Teleports.teleport(plugin, player, plugin.worlds().spawnOf(world), "npc.world-teleported",
                Messages.ph("world", world.getName()));
    }

    private void list(CommandSender sender) {
        plugin.messages().send(sender, "world.list-header", Messages.ph("count", Bukkit.getWorlds().size()));
        Map<String, WorldService.Kind> managed = plugin.worlds().managed();
        for (World world : Bukkit.getWorlds()) {
            WorldService.Kind kind = managed.get(world.getName());
            plugin.messages().send(sender, "world.list-entry", Messages.ph("world", world.getName()),
                    Messages.ph("type", kind == null ? "serveur" : kind.name().toLowerCase(Locale.ROOT)),
                    Messages.ph("players", world.getPlayers().size()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("orbitdev.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return Completions.filter(List.of("create", "list", "tp"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && sub.equals("tp")) {
            return Completions.filter(plugin.worlds().loadedNames(), args[1]);
        }
        if (args.length == 3 && sub.equals("create")) {
            return Completions.filter(List.of("flat", "normal", "void"), args[2]);
        }
        return List.of();
    }
}
