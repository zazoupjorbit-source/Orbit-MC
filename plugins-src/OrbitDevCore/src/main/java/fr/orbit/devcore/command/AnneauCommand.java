package fr.orbit.devcore.command;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.anneau.Anneaux;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /anneau creer &lt;serveur&gt; [rayon] [titre] : anneau-portail 3 blocs devant soi, tourné dans le sens du regard.
 * /anneau retour &lt;serveur&gt; [titre] : anneau posé tout seul près du spawn, à une place libre (marche depuis la console).
 * /anneau retirer : retire l'anneau le plus proche (posé par commande). /anneau liste.
 */
public final class AnneauCommand implements CommandExecutor, TabCompleter {

    private static final TextColor ICE = TextColor.color(0x62CFF4);
    private static final TextColor GOLD = TextColor.color(0xE0B864);
    private static final TextColor RED = TextColor.color(0xE0484A);
    private static final String DEFAULT_TITLE = "<gradient:#62CFF4:#E0B864><bold>%s</bold></gradient>\n<gray>Traverse l'anneau";

    private final OrbitDevCore plugin;

    public AnneauCommand(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("orbitdev.admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        Anneaux rings = plugin.anneaux();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "creer", "create" -> {
                if (!(sender instanceof Player p) || args.length < 2) {
                    say(sender, "Usage (en jeu) : /anneau creer <serveur> [rayon] [titre]", RED);
                    return true;
                }
                double r = 3.0;
                int titleFrom = 2;
                if (args.length > 2) {
                    try {
                        r = Math.max(1.5, Math.min(8, Double.parseDouble(args[2].replace(',', '.'))));
                        titleFrom = 3;
                    } catch (NumberFormatException ignored) {
                        // pas de rayon : le reste est le titre
                    }
                }
                Location at = p.getLocation();
                double[] n = cardinal(at.getYaw());
                double cx = Math.floor(at.getX() + n[0] * 3) + 0.5, cz = Math.floor(at.getZ() + n[1] * 3) + 0.5;
                rings.build(p.getWorld(), cx, Math.floor(at.getY()), cz, n[0], n[1], r, args[1], title(args, titleFrom, args[1]));
                say(sender, "Anneau vers « " + args[1] + " » posé devant toi.", GOLD);
            }
            case "retour", "auto" -> {
                if (args.length < 2) {
                    say(sender, "Usage : /anneau retour <serveur> [titre]", RED);
                    return true;
                }
                Location spawn = plugin.locations().spawn();
                if (spawn == null) {
                    say(sender, "Aucun monde chargé.", RED);
                    return true;
                }
                if (!place(sender, spawn, args[1], title(args, 2, args[1]))) {
                    say(sender, "Pas de place libre trouvée autour du spawn : pose-le en jeu avec /anneau creer.", RED);
                }
            }
            case "retirer", "remove" -> {
                Location near;
                if (sender instanceof Player p && args.length < 5) {
                    near = p.getLocation();
                } else if (args.length >= 5 && plugin.getServer().getWorld(args[1]) != null) {
                    // depuis la console : /anneau retirer <monde> <x> <y> <z> (position donnée par /anneau liste ou la pose)
                    try {
                        near = new Location(plugin.getServer().getWorld(args[1]), Double.parseDouble(args[2]),
                                Double.parseDouble(args[3]), Double.parseDouble(args[4]));
                    } catch (NumberFormatException e) {
                        say(sender, "Usage : /anneau retirer <monde> <x> <y> <z>", RED);
                        return true;
                    }
                    // charge le chunk et ses entités pour retrouver le disque
                    near.getChunk().getEntities();
                    rings.rescan();
                } else {
                    say(sender, "Usage : /anneau retirer (en jeu, à côté) ou /anneau retirer <monde> <x> <y> <z>", RED);
                    return true;
                }
                String server = rings.remove(near);
                say(sender, server == null ? "Aucun anneau posé par commande à moins de 12 blocs (ceux de La Station se règlent avec /station)."
                        : "Anneau vers « " + server + " » retiré.", server == null ? RED : GOLD);
            }
            case "liste", "list" -> {
                rings.rescan();
                List<String> lines = rings.describe();
                say(sender, lines.isEmpty() ? "Aucun anneau sur ce serveur." : lines.size() + " anneau(x) :", ICE);
                lines.forEach(l -> say(sender, "  " + l, ICE));
            }
            default -> say(sender, "Usage : /anneau <creer|retour|retirer|liste>", RED);
        }
        return true;
    }

    /**
     * Cherche une place libre autour du spawn : de 5 à 24 blocs, dans 16 directions, l'anneau tourné vers le spawn,
     * au niveau des pieds du spawn (à 1 bloc près), sinon sur le relief si le sol est à 3 blocs près de cette hauteur.
     */
    private boolean place(CommandSender sender, Location spawn, String server, Component title) {
        World w = spawn.getWorld();
        // un anneau plus petit si la place manque (salle basse de plafond)
        for (double r : new double[]{2.6, 2.0, 1.6}) {
        float yaw = spawn.getYaw();
        say(sender, String.format(Locale.ROOT, "Spawn de %s : %.1f %.1f %.1f, recherche d'une place...", w.getName(),
                spawn.getX(), spawn.getY(), spawn.getZ()), ICE);
        for (int d = 5; d <= 24; d++) {
            for (int k = 0; k < 16; k++) {
                // d'abord droit devant le regard du spawn, puis de plus en plus sur les côtés
                int step = (k + 1) / 2 * (k % 2 == 0 ? -1 : 1);
                double a = Math.toRadians(yaw + step * 22.5);
                double nx = -Math.sin(a), nz = Math.cos(a);
                double cx = Math.floor(spawn.getX() + nx * d) + 0.5, cz = Math.floor(spawn.getZ() + nz * d) + 0.5;
                // au niveau des pieds du spawn d'abord (salle, plateforme), sinon sur le relief tout proche
                int ground = w.getHighestBlockYAt((int) Math.floor(cx), (int) Math.floor(cz), org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
                int base = (int) Math.floor(spawn.getY());
                int[] levels = {base, base - 1, base + 1, ground + 1};
                for (int li = 0; li < levels.length; li++) {
                    double feet = levels[li];
                    if (li == 3 && Math.abs(feet - base) > 3) {
                        continue;
                    }
                    if (plugin.anneaux().fits(w, cx, feet, cz, nx, nz, r)) {
                        plugin.anneaux().build(w, cx, feet, cz, nx, nz, r, server, title);
                        say(sender, String.format(Locale.ROOT, "Anneau vers « %s » posé dans %s en %.1f %.0f %.1f (%d blocs du spawn).",
                                server, w.getName(), cx, feet, cz, d), GOLD);
                        return true;
                    }
                }
            }
        }
        }
        return false;
    }

    private static Component title(String[] args, int from, String server) {
        if (args.length > from) {
            return MiniMessage.miniMessage().deserialize(String.join(" ", Arrays.copyOfRange(args, from, args.length)).replace("\\n", "\n").replace("|", "\n"));
        }
        return MiniMessage.miniMessage().deserialize(String.format(DEFAULT_TITLE, server.toUpperCase(Locale.ROOT)));
    }

    /** Direction du regard arrondie au point cardinal le plus proche. */
    private static double[] cardinal(float yaw) {
        int q = Math.floorMod(Math.round(yaw / 90f), 4);
        return switch (q) {
            case 0 -> new double[]{0, 1};   // sud
            case 1 -> new double[]{-1, 0};  // ouest
            case 2 -> new double[]{0, -1};  // nord
            default -> new double[]{1, 0};  // est
        };
    }

    private static void say(CommandSender sender, String message, TextColor color) {
        sender.sendMessage(Component.text(message, color));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("creer", "retour", "retirer", "liste").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("creer") || args[0].equalsIgnoreCase("retour"))) {
            return List.of("lobby", "survie", "extraction", "practice", "reborn", "tycoon").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
