package fr.orbitgames.hcf.events.command;

import fr.orbitgames.hcf.events.OrbitHCFEventsPlugin;
import fr.orbitgames.hcf.events.storage.EventsDb;
import fr.orbitgames.hcf.events.zone.CaptureZone;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * /citadel : evenement majeur hebdomadaire, historique des vainqueurs.
 */
public class CitadelCommand implements CommandExecutor {

    private final OrbitHCFEventsPlugin plugin;

    public CitadelCommand(OrbitHCFEventsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "history" -> history(sender);
            case "create" -> {
                if (!ZoneEditor.checkAdmin(plugin, sender) || args.length < 2) {
                    return true;
                }
                ZoneEditor.create(plugin, sender, args[1], CaptureZone.ZoneType.CITADEL,
                        plugin.getEventsConfig().getInt("citadel.default-capture-seconds", 900));
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
                if (!ZoneEditor.checkAdmin(plugin, sender)) {
                    return true;
                }
                CaptureZone zone;
                if (args.length >= 2) {
                    zone = plugin.getZoneRegistry().get(args[1]);
                } else {
                    List<CaptureZone> citadels = plugin.getZoneRegistry().byType(CaptureZone.ZoneType.CITADEL);
                    zone = citadels.isEmpty() ? null : citadels.get(0);
                }
                if (zone == null) {
                    plugin.getMessages().send(sender, "general.unknown-zone",
                            "zone", args.length >= 2 ? args[1] : "citadel");
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

    private void history(CommandSender sender) {
        List<EventsDb.CitadelEntry> history = plugin.getEventsDb().citadelHistory(10);
        if (history.isEmpty()) {
            plugin.getMessages().send(sender, "citadel.history-empty");
            return;
        }
        plugin.getMessages().sendRaw(sender, "citadel.history-header");
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
        for (EventsDb.CitadelEntry entry : history) {
            plugin.getMessages().sendRaw(sender, "citadel.history-entry",
                    "date", format.format(new Date(entry.wonAt())),
                    "faction", entry.factionName());
        }
    }
}
