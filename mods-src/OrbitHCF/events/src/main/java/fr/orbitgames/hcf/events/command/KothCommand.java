package fr.orbitgames.hcf.events.command;

import fr.orbitgames.hcf.events.OrbitHCFEventsPlugin;
import fr.orbitgames.hcf.events.storage.EventsDb;
import fr.orbitgames.hcf.events.zone.CaptureZone;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /koth : gestion des zones KOTH, lancement, arret, classement.
 */
public class KothCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "pos1", "pos2", "time", "delete", "start", "stop", "list", "top");

    private final OrbitHCFEventsPlugin plugin;

    public KothCommand(OrbitHCFEventsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> ZoneEditor.list(plugin, sender);
            case "top" -> top(sender);
            case "create" -> {
                if (!ZoneEditor.checkAdmin(plugin, sender) || args.length < 2) {
                    return true;
                }
                ZoneEditor.create(plugin, sender, args[1], CaptureZone.ZoneType.KOTH,
                        plugin.getEventsConfig().getInt("koth.default-capture-seconds", 300));
            }
            // /koth zone <nom> <monde> <x1> <y1> <z1> <x2> <y2> <z2> : pose la zone sans etre sur place
            // (console, generateur de KOTH du Core). Cree la zone si elle n'existe pas.
            case "zone" -> {
                if (!ZoneEditor.checkAdmin(plugin, sender) || args.length < 9) {
                    return true;
                }
                CaptureZone zone = plugin.getZoneRegistry().get(args[1]);
                if (zone == null) {
                    zone = new CaptureZone(args[1], CaptureZone.ZoneType.KOTH,
                            plugin.getEventsConfig().getInt("koth.default-capture-seconds", 300));
                    plugin.getZoneRegistry().add(zone);
                }
                try {
                    int[] a = {Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5])};
                    int[] b = {Integer.parseInt(args[6]), Integer.parseInt(args[7]), Integer.parseInt(args[8])};
                    zone.setWorldName(args[2]);
                    zone.setPos1Raw(a);
                    zone.setPos2Raw(b);
                } catch (NumberFormatException e) {
                    sender.sendMessage("Coordonnees invalides.");
                    return true;
                }
                plugin.getZoneRegistry().save();
                sender.sendMessage("Zone KOTH " + zone.getName() + " posee dans " + args[2] + " : "
                        + args[3] + " " + args[4] + " " + args[5] + " -> " + args[6] + " " + args[7] + " " + args[8] + ".");
            }
            case "pos1", "pos2" -> {
                if (!ZoneEditor.checkAdmin(plugin, sender) || args.length < 2) {
                    return true;
                }
                Player player = ZoneEditor.asPlayer(plugin, sender);
                if (player != null) {
                    ZoneEditor.setPos(plugin, player, args[1], sub.equals("pos1"));
                }
            }
            case "time" -> {
                if (!ZoneEditor.checkAdmin(plugin, sender) || args.length < 3) {
                    return true;
                }
                ZoneEditor.setTime(plugin, sender, args[1], args[2]);
            }
            case "delete" -> {
                if (!ZoneEditor.checkAdmin(plugin, sender) || args.length < 2) {
                    return true;
                }
                ZoneEditor.delete(plugin, sender, args[1]);
            }
            case "start" -> {
                if (!ZoneEditor.checkAdmin(plugin, sender) || args.length < 2) {
                    return true;
                }
                CaptureZone zone = plugin.getZoneRegistry().get(args[1]);
                if (zone == null) {
                    plugin.getMessages().send(sender, "general.unknown-zone", "zone", args[1]);
                    return true;
                }
                if (!zone.isComplete()) {
                    plugin.getMessages().send(sender, "general.zone-incomplete", "zone", zone.getName());
                    return true;
                }
                if (!plugin.getKothManager().start(zone)) {
                    plugin.getMessages().send(sender, "koth.already-active");
                }
            }
            case "stop" -> {
                if (!ZoneEditor.checkAdmin(plugin, sender)) {
                    return true;
                }
                if (plugin.getKothManager().getActive() == null) {
                    plugin.getMessages().send(sender, "koth.none-active");
                } else {
                    plugin.getKothManager().stop(false);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void top(CommandSender sender) {
        List<EventsDb.CaptureEntry> top = plugin.getEventsDb().topCaptures(10);
        if (top.isEmpty()) {
            plugin.getMessages().send(sender, "koth.top-empty");
            return;
        }
        plugin.getMessages().sendRaw(sender, "koth.top-header");
        int rank = 1;
        for (EventsDb.CaptureEntry entry : top) {
            plugin.getMessages().sendRaw(sender, "koth.top-entry",
                    "rang", rank++, "faction", entry.factionName(), "captures", entry.captures());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String start = args[0].toLowerCase(Locale.ROOT);
            SUBCOMMANDS.stream().filter(s -> s.startsWith(start)).forEach(result::add);
        } else if (args.length == 2) {
            String start = args[1].toLowerCase(Locale.ROOT);
            plugin.getZoneRegistry().all().stream()
                    .map(CaptureZone::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(start))
                    .forEach(result::add);
        }
        return result;
    }
}
