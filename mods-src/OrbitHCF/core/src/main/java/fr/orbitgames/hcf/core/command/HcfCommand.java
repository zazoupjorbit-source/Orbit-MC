package fr.orbitgames.hcf.core.command;

import fr.orbitgames.hcf.api.util.Text;
import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /hcf : administration du serveur HCF (spawn, bordure, constructions generees). */
public class HcfCommand implements CommandExecutor, TabCompleter {

    private final OrbitHCFCorePlugin plugin;

    public HcfCommand(OrbitHCFCorePlugin plugin) {
        this.plugin = plugin;
    }

    private void say(CommandSender sender, String message) {
        sender.sendMessage(Text.color(plugin.getMessages().getPrefix() + message));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("orbithcf.admin")) {
            say(sender, "&cCommande reservee aux admins. Aide joueur : &f/f help");
            return true;
        }
        String sub = args.length == 0 ? "aide" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    say(sender, "&cEn jeu seulement.");
                    return true;
                }
                if (args.length >= 2 && args[1].equalsIgnoreCase("definir")) {
                    plugin.getServerManager().setSpawn(player.getLocation());
                    say(sender, "&aPoint de spawn pose ici.");
                } else {
                    player.teleport(plugin.getServerManager().spawn());
                }
            }
            case "generer" -> generate(sender, args);
            case "bordure" -> say(sender, "&aBordures : &f" + plugin.getServerManager().applyBorders());
            case "reload" -> {
                plugin.reloadServerConfig();
                say(sender, "&aserveur.yml relu. Bordures : &f" + plugin.getServerManager().applyBorders());
            }
            default -> {
                say(sender, "&e/hcf spawn &7- aller au spawn  &e/hcf spawn definir &7- le poser ici");
                say(sender, "&e/hcf generer spawn [forcer] &7- construire le spawn (Safezone)");
                say(sender, "&e/hcf generer routes &7- les 4 routes jusqu'au bord de la Warzone");
                say(sender, "&e/hcf generer koth <nom> [x z] &7- plateforme de KOTH + sa zone");
                say(sender, "&e/hcf bordure &7- reposer la bordure  &e/hcf reload &7- relire serveur.yml");
            }
        }
        return true;
    }

    private void generate(CommandSender sender, String[] args) {
        String what = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (what) {
            case "spawn" -> plugin.getWorldBuilder().buildSpawn(sender,
                    args.length >= 3 && args[2].equalsIgnoreCase("forcer"));
            case "routes" -> plugin.getWorldBuilder().buildRoads(sender);
            case "koth" -> {
                if (args.length < 3) {
                    say(sender, "&c/hcf generer koth <nom> [x z]");
                    return;
                }
                int x;
                int z;
                try {
                    if (args.length >= 5) {
                        x = Integer.parseInt(args[3]);
                        z = Integer.parseInt(args[4]);
                    } else if (sender instanceof Player player) {
                        x = player.getLocation().getBlockX();
                        z = player.getLocation().getBlockZ();
                    } else {
                        say(sender, "&cDepuis la console : /hcf generer koth <nom> <x> <z>");
                        return;
                    }
                } catch (NumberFormatException e) {
                    say(sender, "&cCoordonnees invalides.");
                    return;
                }
                plugin.getWorldBuilder().buildKoth(sender, args[2], x, z);
            }
            default -> say(sender, "&c/hcf generer <spawn|routes|koth>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!sender.hasPermission("orbithcf.admin")) {
            return result;
        }
        List<String> options = args.length == 1 ? List.of("spawn", "generer", "bordure", "reload")
                : args.length == 2 && args[0].equalsIgnoreCase("generer") ? List.of("spawn", "routes", "koth")
                : args.length == 2 && args[0].equalsIgnoreCase("spawn") ? List.of("definir") : List.of();
        String start = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.stream().filter(o -> o.startsWith(start)).forEach(result::add);
        return result;
    }
}
