package fr.orbit.devcore.command;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.station.StationDesign;
import fr.orbit.devcore.station.StationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /station generer [zones] | ecarts | remettre &lt;rayon|tout&gt; | etancheite | apercu [coupe] | effacer oui | regle &lt;clé&gt; &lt;valeur&gt; | tp [point] | points | recharger. */
public final class StationCommand implements CommandExecutor, TabCompleter {

    private static final TextColor ICE = TextColor.color(0x62CFF4);
    private static final TextColor GOLD = TextColor.color(0xE0B864);
    private static final TextColor RED = TextColor.color(0xE0484A);
    private static final List<String> SUBS = List.of("generer", "ecarts", "remettre", "etancheite", "apercu", "effacer", "regle", "tp", "points", "recharger");
    /** Plus grand rayon de /station remettre &lt;rayon&gt; ; au-delà, c'est /station remettre tout. */
    private static final int MAX_RADIUS = 48;

    private final OrbitDevCore plugin;

    public StationCommand(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("orbitdev.admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        StationService station = plugin.station();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "generer", "generate" -> {
                List<String> zones = new ArrayList<>();
                for (int i = 1; i < args.length; i++) {
                    String zone = args[i].toLowerCase(Locale.ROOT);
                    if (!StationDesign.ZONES.contains(zone)) {
                        say(sender, "Zone inconnue : " + zone + ". Zones : " + String.join(", ", StationDesign.ZONES), RED);
                        return true;
                    }
                    zones.add(zone);
                }
                station.generate(sender, zones);
            }
            case "etancheite", "etanche", "seal" -> station.seal(sender);
            case "ecarts", "diff" -> station.diff(sender);
            case "remettre", "restaurer", "restore" -> restore(sender, station, args);
            case "effacer", "clear" -> {
                if (args.length < 2 || !args[1].equalsIgnoreCase("oui")) {
                    say(sender, "Ça vide toute la station, retouches faites à la main comprises. Pour confirmer : /station effacer oui", RED);
                    return true;
                }
                station.clear(sender);
            }
            case "regle", "set" -> {
                if (args.length < 3) {
                    say(sender, "Usage : /station regle <clé> <valeur>, par exemple atrium.rayon 30", RED);
                    return true;
                }
                if (station.busy()) {
                    say(sender, "Une génération est en cours : attends qu'elle se termine pour changer un réglage.", RED);
                } else if (station.set(args[1], args[2])) {
                    say(sender, args[1] + " = " + args[2] + ". Lance /station generer pour reconstruire.", GOLD);
                } else {
                    say(sender, "Réglage inconnu : " + args[1], RED);
                }
            }
            case "tp" -> {
                Player player = Senders.player(sender);
                if (player == null) {
                    plugin.messages().send(sender, "general.players-only");
                    return true;
                }
                Location at = station.points().get(args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "spawn");
                if (at == null) {
                    say(sender, "Point inconnu, ou station pas encore générée (/station generer).", RED);
                    return true;
                }
                player.teleportAsync(at);
            }
            case "points" -> {
                Map<String, Location> points = station.points();
                say(sender, "Points de la Station (" + points.size() + ") :", GOLD);
                for (Map.Entry<String, Location> e : points.entrySet()) {
                    Location l = e.getValue();
                    say(sender, String.format(Locale.ROOT, "  %s : %.1f %.1f %.1f (regard %.0f)", e.getKey(), l.getX(), l.getY(), l.getZ(), l.getYaw()), ICE);
                }
            }
            case "apercu", "preview" -> {
                Integer cut = null;
                if (args.length >= 2) {
                    try {
                        cut = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        say(sender, "Hauteur de coupe invalide : " + args[1] + ". Exemple : /station apercu 3", RED);
                        return true;
                    }
                }
                station.preview(sender, cut);
            }
            case "recharger", "reload" -> {
                station.load();
                say(sender, "station.yml rechargé.", GOLD);
            }
            default -> say(sender, "/station <generer [zones]|ecarts|remettre <rayon|tout>|etancheite|apercu [hauteur de coupe]|effacer oui|regle <clé> <valeur>|tp [point]|points|recharger>", ICE);
        }
        return true;
    }

    /**
     * /station remettre tout : toute la station redevient comme sur le plan, retouches faites à la main comprises.
     * /station remettre &lt;rayon&gt; : seulement autour de soi (depuis la console : &lt;rayon&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt;).
     */
    private void restore(CommandSender sender, StationService station, String[] args) {
        if (args.length < 2) {
            say(sender, "Usage : /station remettre <rayon> (autour de toi, 1 à " + MAX_RADIUS + " blocs) ou /station remettre tout."
                    + " Remet comme sur le plan, retouches faites à la main comprises.", RED);
            return;
        }
        if (args[1].equalsIgnoreCase("tout") || args[1].equalsIgnoreCase("all")) {
            station.restore(sender, null);
            return;
        }
        try {
            int radius = Integer.parseInt(args[1]);
            if (radius < 1 || radius > MAX_RADIUS) {
                say(sender, "Rayon entre 1 et " + MAX_RADIUS + " blocs. Pour toute la station : /station remettre tout.", RED);
                return;
            }
            if (args.length >= 5) {
                station.restore(sender, new StationService.Area(Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]), radius));
                return;
            }
            Player player = Senders.player(sender);
            if (player == null) {
                say(sender, "Depuis la console : /station remettre <rayon> <x> <y> <z>, ou /station remettre tout.", RED);
                return;
            }
            if (!player.getWorld().getName().equals(station.worldName())) {
                say(sender, "Va dans la station pour remettre un endroit comme sur le plan.", RED);
                return;
            }
            Location at = player.getLocation();
            station.restore(sender, new StationService.Area(at.getBlockX(), at.getBlockY(), at.getBlockZ(), radius));
        } catch (NumberFormatException e) {
            say(sender, "Nombre invalide. Exemples : /station remettre 5, /station remettre tout.", RED);
        }
    }

    private void say(CommandSender sender, String message, TextColor color) {
        sender.sendMessage(Component.text(message, color));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("orbitdev.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return Completions.filter(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String last = args[args.length - 1];
        return switch (sub) {
            case "generer" -> Completions.filter(StationDesign.ZONES, last);
            case "remettre" -> args.length == 2 ? Completions.filter(List.of("3", "5", "10", "tout"), last) : List.of();
            case "effacer" -> args.length == 2 ? Completions.filter(List.of("oui"), last) : List.of();
            case "tp" -> args.length == 2 ? Completions.filter(new ArrayList<>(plugin.station().points().keySet()), last) : List.of();
            case "regle" -> {
                if (args.length != 2) {
                    yield List.of();
                }
                List<String> keys = new ArrayList<>();
                for (String key : plugin.station().config().getKeys(true)) {
                    if (!plugin.station().config().isConfigurationSection(key)) {
                        keys.add(key);
                    }
                }
                yield Completions.filter(keys, last);
            }
            default -> List.of();
        };
    }
}
