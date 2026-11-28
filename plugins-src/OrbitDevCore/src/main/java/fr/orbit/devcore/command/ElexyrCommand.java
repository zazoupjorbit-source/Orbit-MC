package fr.orbit.devcore.command;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.elexyr.Elexyr;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * /elexyr : fait apparaître Elxssir (l'easter egg aux noisettes) à sa position, tourné dans le sens du regard.
 * /elexyr apparaitre &lt;monde&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt; [lacet] : pareil à un endroit précis (marche depuis la console).
 * /elexyr retirer [tout] : retire le plus proche (12 blocs) ou tous. /elexyr liste.
 */
public final class ElexyrCommand implements CommandExecutor, TabCompleter {

    private static final TextColor GOLD = TextColor.color(0xE0B864);
    private static final TextColor GREY = TextColor.color(0xA9B4C8);
    private static final TextColor RED = TextColor.color(0xE0484A);

    private final OrbitDevCore plugin;

    public ElexyrCommand(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("orbitdev.admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        Elexyr elexyr = plugin.elexyr();
        if (elexyr == null || !elexyr.enabled()) {
            say(sender, "Elxssir n'est pas disponible (voir les journaux du serveur).", RED);
            return true;
        }
        String sub = args.length == 0 ? "apparaitre" : args[0].toLowerCase(Locale.ROOT);
        Player player = Senders.player(sender);
        switch (sub) {
            case "apparaitre", "apparaître", "spawn" -> {
                Location at;
                if (args.length >= 5) {
                    World w = Bukkit.getWorld(args[1]);
                    if (w == null) {
                        say(sender, "Monde introuvable : " + args[1], RED);
                        return true;
                    }
                    try {
                        at = new Location(w, num(args[2]), num(args[3]), num(args[4]), args.length > 5 ? (float) num(args[5]) : 0f, 0f);
                    } catch (NumberFormatException e) {
                        say(sender, "Usage : /elexyr apparaitre <monde> <x> <y> <z> [lacet]", RED);
                        return true;
                    }
                } else if (player != null) {
                    at = player.getLocation();
                } else {
                    say(sender, "Depuis la console : /elexyr apparaitre <monde> <x> <y> <z> [lacet]", RED);
                    return true;
                }
                Elexyr.Spot s = elexyr.add(at);
                say(sender, "Elxssir n° " + s.id() + " est en poste (" + coords(s) + "). Il a une question pour toi.", GOLD);
            }
            case "retirer", "remove" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("tout")) {
                    say(sender, elexyr.removeAll() + " Elxssir retiré(s).", GOLD);
                } else if (player == null) {
                    say(sender, "Depuis la console : /elexyr retirer tout", RED);
                } else {
                    Elexyr.Spot s = elexyr.removeNearest(player.getLocation(), 12);
                    say(sender, s == null ? "Aucun Elxssir à moins de 12 blocs." : "Elxssir n° " + s.id() + " retiré.", s == null ? RED : GOLD);
                }
            }
            case "liste", "list" -> {
                if (elexyr.all().isEmpty()) {
                    say(sender, "Aucun Elxssir. /elexyr pour en faire apparaître un.", GREY);
                }
                for (Elexyr.Spot s : elexyr.all()) {
                    say(sender, "n° " + s.id() + " — " + coords(s), GREY);
                }
            }
            default -> say(sender, "/elexyr [apparaitre | retirer [tout] | liste]", GREY);
        }
        return true;
    }

    private static double num(String s) {
        return Double.parseDouble(s.replace(',', '.'));
    }

    private static String coords(Elexyr.Spot s) {
        return s.world() + " " + Math.round(s.x()) + " " + Math.round(s.y()) + " " + Math.round(s.z());
    }

    private static void say(CommandSender to, String text, TextColor color) {
        to.sendMessage(Component.text(text, color));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("orbitdev.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return Completions.filter(List.of("apparaitre", "retirer", "liste"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("retirer")) {
            return Completions.filter(List.of("tout"), args[1]);
        }
        return List.of();
    }
}
