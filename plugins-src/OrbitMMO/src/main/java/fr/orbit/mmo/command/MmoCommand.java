package fr.orbit.mmo.command;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.ui.Menus;
import fr.orbit.mmo.ui.ProfileMenu;
import fr.orbit.mmo.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /mmo [sorts|spe|talents|etages|guide|pack] */
public final class MmoCommand implements TabExecutor {

    private final OrbitMMO plugin;

    public MmoCommand(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        if (command.getName().equals("lobby")) {
            plugin.toLobby(p);
            return true;
        }
        if (!plugin.progression().valid(plugin.players().get(p))) {
            Menus.factionSelect(plugin, p);
            return true;
        }
        if (command.getName().equals("perso")) {
            Player target = args.length == 0 ? p : Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                Text.msg(p, "<#FF5C5C>Joueur introuvable.");
            } else {
                ProfileMenu.open(plugin, p, target);
            }
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "perso", "profil", "stats" -> ProfileMenu.open(plugin, p, p);
            case "sorts", "spells" -> Menus.spellbook(plugin, p);
            case "spe", "specialisation" -> Menus.specSelect(plugin, p);
            case "talents" -> Menus.talents(plugin, p, null);
            case "etages", "floors" -> Menus.floors(plugin, p, false);
            case "pack" -> {
                Text.msg(p, "<#7FD4FF>Envoi du pack de ressources…");
                plugin.pack().send(p);
            }
            case "guide", "aide" -> {
                Text.msg(p, "<#FFD66E><b>Comment jouer</b>");
                for (String line : Menus.guide()) {
                    if (!line.isEmpty()) {
                        p.sendMessage(Text.mm("  " + line));
                    }
                }
            }
            default -> Menus.main(plugin, p);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("lobby")) {
            return List.of();
        }
        if (command.getName().equals("perso")) {
            return args.length == 1 ? null : List.of();
        }
        if (args.length == 1) {
            return Completions.filter(args[0], List.of("perso", "sorts", "spe", "talents", "etages", "guide", "pack"));
        }
        return List.of();
    }
}
