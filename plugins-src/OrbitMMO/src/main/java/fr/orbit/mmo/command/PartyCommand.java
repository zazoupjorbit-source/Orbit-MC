package fr.orbit.mmo.command;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.ui.Menus;
import fr.orbit.mmo.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /groupe [inviter <joueur>|accepter|quitter|expulser <joueur>] */
public final class PartyCommand implements TabExecutor {

    private final OrbitMMO plugin;

    public PartyCommand(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "inviter", "invite" -> {
                if (args.length < 2) {
                    Text.msg(p, "<#FF5C5C>Usage : /groupe inviter <joueur>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    Text.msg(p, "<#FF5C5C>Joueur introuvable.");
                    return true;
                }
                plugin.parties().invite(p, target);
            }
            case "accepter", "accept" -> plugin.parties().accept(p);
            case "quitter", "leave" -> plugin.parties().leave(p);
            case "expulser", "kick" -> {
                if (args.length < 2) {
                    Text.msg(p, "<#FF5C5C>Usage : /groupe expulser <joueur>");
                    return true;
                }
                plugin.parties().kick(p, args[1]);
            }
            default -> Menus.party(plugin, p);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Completions.filter(args[0], List.of("inviter", "accepter", "quitter", "expulser"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("inviter") || args[0].equalsIgnoreCase("expulser"))) {
            return Completions.players(args[1]);
        }
        return List.of();
    }
}
