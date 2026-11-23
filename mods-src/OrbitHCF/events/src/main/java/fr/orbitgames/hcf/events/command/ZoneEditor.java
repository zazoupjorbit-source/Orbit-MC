package fr.orbitgames.hcf.events.command;

import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.events.OrbitHCFEventsPlugin;
import fr.orbitgames.hcf.events.zone.CaptureZone;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Operations partagees d'edition de zones (/koth, /citadel, /conquest).
 */
public final class ZoneEditor {

    private ZoneEditor() {
    }

    public static void create(OrbitHCFEventsPlugin plugin, CommandSender sender,
                              String name, CaptureZone.ZoneType type, int defaultSeconds) {
        CaptureZone zone = new CaptureZone(name, type, defaultSeconds);
        plugin.getZoneRegistry().add(zone);
        plugin.getMessages().send(sender, "zone.created", "zone", name,
                "type", type.name().toLowerCase());
    }

    public static void delete(OrbitHCFEventsPlugin plugin, CommandSender sender, String name) {
        if (plugin.getZoneRegistry().get(name) == null) {
            plugin.getMessages().send(sender, "general.unknown-zone", "zone", name);
            return;
        }
        plugin.getZoneRegistry().remove(name);
        plugin.getMessages().send(sender, "zone.deleted", "zone", name);
    }

    public static void setPos(OrbitHCFEventsPlugin plugin, Player player, String name, boolean first) {
        CaptureZone zone = plugin.getZoneRegistry().get(name);
        if (zone == null) {
            plugin.getMessages().send(player, "general.unknown-zone", "zone", name);
            return;
        }
        if (first) {
            zone.setPos1(player.getLocation());
        } else {
            zone.setPos2(player.getLocation());
        }
        plugin.getZoneRegistry().save();
        String coords = player.getLocation().getBlockX() + ", "
                + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ();
        plugin.getMessages().send(player, first ? "zone.pos1" : "zone.pos2",
                "zone", zone.getName(), "coords", coords);
    }

    public static void setTime(OrbitHCFEventsPlugin plugin, CommandSender sender, String name, String durationInput) {
        CaptureZone zone = plugin.getZoneRegistry().get(name);
        if (zone == null) {
            plugin.getMessages().send(sender, "general.unknown-zone", "zone", name);
            return;
        }
        Duration duration = DurationFormat.parse(durationInput);
        if (duration.isZero()) {
            plugin.getMessages().send(sender, "general.invalid-duration", "valeur", durationInput);
            return;
        }
        zone.setCaptureSeconds((int) duration.getSeconds());
        plugin.getZoneRegistry().save();
        plugin.getMessages().send(sender, "zone.time-set", "zone", zone.getName(),
                "temps", DurationFormat.compact(duration));
    }

    public static void list(OrbitHCFEventsPlugin plugin, CommandSender sender) {
        if (plugin.getZoneRegistry().all().isEmpty()) {
            plugin.getMessages().send(sender, "zone.list-empty");
            return;
        }
        plugin.getMessages().sendRaw(sender, "zone.list-header");
        for (CaptureZone zone : plugin.getZoneRegistry().all()) {
            plugin.getMessages().sendRaw(sender, "zone.list-entry",
                    "zone", zone.getName(),
                    "type", zone.getType().name().toLowerCase(),
                    "temps", DurationFormat.compact(Duration.ofSeconds(zone.getCaptureSeconds())));
        }
    }

    /** Verifie la permission d'administration des evenements. */
    public static boolean checkAdmin(OrbitHCFEventsPlugin plugin, CommandSender sender) {
        if (!sender.hasPermission("orbithcf.events.admin")) {
            plugin.getMessages().send(sender, "general.no-permission");
            return false;
        }
        return true;
    }

    public static Player asPlayer(OrbitHCFEventsPlugin plugin, CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        plugin.getMessages().send(sender, "general.player-only");
        return null;
    }
}
