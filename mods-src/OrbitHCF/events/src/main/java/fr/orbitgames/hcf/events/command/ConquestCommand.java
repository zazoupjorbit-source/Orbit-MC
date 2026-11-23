package fr.orbitgames.hcf.events.command;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.model.Faction;
import fr.orbitgames.hcf.api.service.FactionService;
import fr.orbitgames.hcf.events.OrbitHCFEventsPlugin;
import fr.orbitgames.hcf.events.zone.CaptureZone;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /conquest : plusieurs points de capture, score en temps reel.
 */
public class ConquestCommand implements CommandExecutor {

    private final OrbitHCFEventsPlugin plugin;

    public ConquestCommand(OrbitHCFEventsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "score" -> score(sender);
            case "create" -> {
                if (!ZoneEditor.checkAdmin(plugin, sender) || args.length < 2) {
                    return true;
                }
                ZoneEditor.create(plugin, sender, args[1], CaptureZone.ZoneType.CONQUEST,
                        plugin.getEventsConfig().getInt("conquest.interval-seconds", 30));
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
                if (!plugin.getConquestManager().start()) {
                    plugin.getMessages().send(sender, plugin.getConquestManager().isRunning()
                            ? "koth.already-active" : "conquest.need-zones");
                }
            }
            case "stop" -> {
                if (!ZoneEditor.checkAdmin(plugin, sender)) {
                    return true;
                }
                if (!plugin.getConquestManager().isRunning()) {
                    plugin.getMessages().send(sender, "conquest.none-active");
                } else {
                    plugin.getConquestManager().stop(true);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void score(CommandSender sender) {
        if (!plugin.getConquestManager().isRunning()) {
            plugin.getMessages().send(sender, "conquest.none-active");
            return;
        }
        FactionService factions = OrbitHCF.factions();
        Map<UUID, Integer> scores = plugin.getConquestManager().getScores();
        plugin.getMessages().sendRaw(sender, "conquest.score-header");
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder()));
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            Faction faction = factions != null ? factions.getFaction(entry.getKey()) : null;
            plugin.getMessages().sendRaw(sender, "conquest.score-entry",
                    "rang", rank++,
                    "faction", faction != null ? faction.getName() : "?",
                    "points", entry.getValue());
        }
    }
}
