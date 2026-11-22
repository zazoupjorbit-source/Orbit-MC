package fr.orbit.devcore.command;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.Teleports;
import fr.orbit.devcore.message.Messages;
import fr.orbit.devcore.npc.NpcService;
import fr.orbit.devcore.npc.ServerNpc;
import fr.orbit.devcore.storage.StoredLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * /servernpc create|remove|list|tphere|tp|server|world|name|profession.
 * La cible d'un PNJ est un serveur du proxy (« hcf ») ou un monde de ce serveur (« monde:reborn »).
 * Utilisable par un joueur, ou depuis la console avec « execute as <joueur> at @s run servernpc ... ».
 */
public final class NpcCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("create", "remove", "list", "tphere", "tp", "server", "world", "name", "profession");
    private static final List<String> WORLD_PREFIXES = List.of("monde:", "world:");

    private final OrbitDevCore plugin;

    public NpcCommand(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("orbitdev.admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(sender);
            case "remove" -> withNpc(sender, args, 2, "/servernpc remove <id>", npc -> {
                plugin.npcs().remove(npc);
                plugin.messages().send(sender, "npc.removed", Messages.ph("id", npc.id()));
            });
            case "server" -> withNpc(sender, args, 3, "/servernpc server <id> <serveur>", npc -> {
                npc.targetServer(args[2]);
                plugin.npcs().update(npc);
                plugin.messages().send(sender, "npc.target-set", Messages.ph("id", npc.id()),
                        Messages.ph("target", npc.targetLabel()));
            });
            case "world" -> withNpc(sender, args, 3, "/servernpc world <id> <monde>", npc -> {
                npc.targetWorld(args[2]);
                plugin.npcs().update(npc);
                plugin.messages().send(sender, "npc.target-set", Messages.ph("id", npc.id()),
                        Messages.ph("target", npc.targetLabel()));
                warnIfWorldMissing(sender, args[2]);
            });
            case "name" -> withNpc(sender, args, 3, "/servernpc name <id> <nom>", npc -> {
                npc.displayName(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                plugin.npcs().update(npc);
                plugin.messages().send(sender, "npc.name-set", Messages.ph("id", npc.id()));
            });
            case "profession" -> withNpc(sender, args, 3, "/servernpc profession <id> <profession>", npc -> {
                String value = args[2].toLowerCase(Locale.ROOT);
                if (NpcService.profession(value) == null) {
                    plugin.messages().send(sender, "npc.unknown-profession", Messages.ph("profession", args[2]));
                    return;
                }
                npc.profession(value);
                plugin.npcs().update(npc);
                plugin.messages().send(sender, "npc.profession-set", Messages.ph("id", npc.id()),
                        Messages.ph("profession", value));
            });
            case "create", "tphere", "tp" -> {
                // Depuis la console : « @joueur » en dernier argument utilise la position de ce joueur.
                Player found = Senders.player(sender);
                String[] rest = args;
                String last = args[args.length - 1];
                if (args.length > 2 && last.startsWith("@") && last.length() > 1) {
                    found = Bukkit.getPlayerExact(last.substring(1));
                    if (found == null) {
                        plugin.messages().send(sender, "general.player-not-found", Messages.ph("player", last.substring(1)));
                        return true;
                    }
                    rest = Arrays.copyOf(args, args.length - 1);
                }
                if (found == null) {
                    plugin.messages().send(sender, "general.players-only");
                    return true;
                }
                final Player player = found;
                switch (sub) {
                    case "create" -> create(sender, player, rest);
                    case "tphere" -> withNpc(sender, rest, 2, "/servernpc tphere <id> [@joueur]", npc -> {
                        npc.location(StoredLocation.of(player.getLocation()));
                        plugin.npcs().update(npc);
                        plugin.messages().send(sender, "npc.moved", Messages.ph("id", npc.id()));
                    });
                    default -> withNpc(sender, rest, 2, "/servernpc tp <id> [@joueur]", npc -> {
                        Location target = npc.location().toLocation();
                        if (target == null) {
                            plugin.messages().send(sender, "npc.world-missing", Messages.ph("world", npc.location().world()));
                            return;
                        }
                        Teleports.teleport(plugin, player, target, "npc.teleported", Messages.ph("id", npc.id()));
                    });
                }
            }
            default -> plugin.messages().sendList(sender, "npc.help");
        }
        return true;
    }

    private void create(CommandSender sender, Player player, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "general.usage",
                    Messages.ph("usage", "/servernpc create <id> <serveur|monde:nom> [type]"));
            return;
        }
        String id = args[1];
        if (!NpcService.ID_PATTERN.matcher(id).matches()) {
            plugin.messages().send(sender, "npc.invalid-id");
            return;
        }
        if (plugin.npcs().get(id) != null) {
            plugin.messages().send(sender, "npc.exists", Messages.ph("id", id));
            return;
        }
        String typeName = args.length >= 4 ? args[3] : plugin.getConfig().getString("npc.default-type", "VILLAGER");
        EntityType type = NpcService.parseType(typeName);
        if (type == null) {
            plugin.messages().send(sender, "npc.invalid-type", Messages.ph("type", typeName));
            return;
        }
        String world = worldTarget(args[2]);
        String server = world == null ? args[2] : null;
        ServerNpc npc = plugin.npcs().create(id, server, world, player.getLocation(), type);
        plugin.messages().send(sender, "npc.created", Messages.ph("id", id), Messages.ph("target", npc.targetLabel()));
        if (world != null) {
            warnIfWorldMissing(sender, world);
        }
    }

    /** « monde:reborn » / « world:reborn » → « reborn » ; sinon {@code null} (cible serveur). */
    private static String worldTarget(String target) {
        String lower = target.toLowerCase(Locale.ROOT);
        for (String prefix : WORLD_PREFIXES) {
            if (lower.startsWith(prefix) && lower.length() > prefix.length()) {
                return target.substring(prefix.length());
            }
        }
        return null;
    }

    private void warnIfWorldMissing(CommandSender sender, String world) {
        if (Bukkit.getWorld(world) == null) {
            plugin.messages().send(sender, "npc.world-missing", Messages.ph("world", world));
        }
    }

    private void list(CommandSender sender) {
        if (plugin.npcs().all().isEmpty()) {
            plugin.messages().send(sender, "npc.list-empty");
            return;
        }
        plugin.messages().send(sender, "npc.list-header", Messages.ph("count", plugin.npcs().all().size()));
        for (ServerNpc npc : plugin.npcs().all()) {
            StoredLocation l = npc.location();
            plugin.messages().send(sender, "npc.list-entry",
                    Messages.ph("id", npc.id()), Messages.ph("target", npc.targetLabel()),
                    Messages.ph("type", npc.type().name().toLowerCase(Locale.ROOT)), Messages.ph("world", l.world()),
                    Messages.ph("x", l.blockX()), Messages.ph("y", l.blockY()), Messages.ph("z", l.blockZ()));
        }
    }

    private void withNpc(CommandSender sender, String[] args, int minArgs, String usage, Consumer<ServerNpc> action) {
        if (args.length < minArgs) {
            plugin.messages().send(sender, "general.usage", Messages.ph("usage", usage));
            return;
        }
        ServerNpc npc = plugin.npcs().get(args[1]);
        if (npc == null) {
            plugin.messages().send(sender, "npc.not-found", Messages.ph("id", args[1]));
            return;
        }
        action.accept(npc);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("orbitdev.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return Completions.filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && !sub.equals("create") && !sub.equals("list")) {
            return Completions.filter(plugin.npcs().ids(), args[1]);
        }
        if (args.length == 3 && sub.equals("create")) {
            List<String> targets = new ArrayList<>();
            for (String world : plugin.worlds().loadedNames()) {
                targets.add("monde:" + world);
            }
            return Completions.filter(targets, args[2]);
        }
        if (args.length == 3 && sub.equals("world")) {
            return Completions.filter(plugin.worlds().loadedNames(), args[2]);
        }
        if (args.length == 4 && sub.equals("create")) {
            return Completions.filter(NpcService.livingTypes(), args[3]);
        }
        if (args.length == 3 && sub.equals("profession")) {
            return Completions.filter(NpcService.professions(), args[2]);
        }
        return List.of();
    }
}
