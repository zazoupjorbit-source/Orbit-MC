package fr.orbit.extraction.command;

import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/** /raid et /planque. */
public final class PlayerCommands implements TabExecutor {

    private final OrbitExtraction plugin;

    public PlayerCommands(OrbitExtraction plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Text.msg(sender, "Commande réservée aux joueurs.");
            return true;
        }
        if (!p.hasPermission("extraction.play")) {
            Text.msg(p, "<#C0392B>Tu n'as pas la permission.");
            return true;
        }
        switch (cmd.getName()) {
            case "raid" -> {
                if (args.length > 0 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("stats"))) {
                    plugin.raid().info(p);
                    p.sendMessage(Text.mm("  <#8A919B>" + plugin.raid().stats(plugin.profiles().get(p))));
                } else {
                    plugin.raid().deploy(p);
                }
            }
            case "telephone" -> {
                plugin.phone().give(p);
                plugin.phoneMenu().open(p, fr.orbit.extraction.phone.PhoneMenu.Screen.HOME);
            }
            case "kit" -> {
                fr.orbit.extraction.rank.Kits.Kit k = args.length > 0 ? plugin.kits().byId(args[0]) : null;
                if (k != null) {
                    plugin.kits().claim(p, k);
                } else {
                    plugin.phoneMenu().open(p, fr.orbit.extraction.phone.PhoneMenu.Screen.KITS);
                }
            }
            case "carte" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("zoom")) {
                    plugin.miniMap().cycleZoom(p);
                } else {
                    plugin.miniMap().toggle(p);
                }
            }
            case "planque" -> {
                if (plugin.raid().inRaid(p)) {
                    Text.msg(p, "<#C0392B>Ton coffre est à la planque.</#C0392B> Extrais-toi d'abord.");
                } else {
                    plugin.stash().open(p);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equals("raid") && args.length == 1) {
            return List.of("info");
        }
        if (cmd.getName().equals("carte") && args.length == 1) {
            return List.of("zoom");
        }
        if (cmd.getName().equals("kit") && args.length == 1) {
            List<String> out = new java.util.ArrayList<>();
            plugin.kits().all().forEach(k -> out.add(k.id()));
            return out;
        }
        return List.of();
    }
}
