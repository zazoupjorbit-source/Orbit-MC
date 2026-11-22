package fr.orbit.reborn.command;

import fr.orbit.reborn.RebornMMO;
import fr.orbit.reborn.ui.Menus;
import fr.orbit.reborn.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /mmo [statut|competences|etages|guide] */
public final class MmoCommand implements TabExecutor {

    private final RebornMMO plugin;

    public MmoCommand(RebornMMO plugin) {
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
            case "statut", "stats" -> Menus.status(plugin, p);
            case "competences", "skills" -> Menus.skills(plugin, p);
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
        if (args.length == 1) {
            return Completions.filter(args[0], List.of("statut", "competences", "etages", "guide", "pack"));
        }
        return List.of();
    }
}
