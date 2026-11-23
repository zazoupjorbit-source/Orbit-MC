package fr.orbitversus.command;

import fr.orbitversus.Msg;
import fr.orbitversus.match.MatchManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /spec <pseudo> — observer un combat ; /spec (sans argument) — arrêter d'observer. */
public final class SpecCommand implements CommandExecutor {

    private final MatchManager matches;

    public SpecCommand(MatchManager matches) {
        this.matches = matches;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("En jeu uniquement.");
            return true;
        }
        if (args.length == 0) {
            if (matches.isSpectating(player.getUniqueId())) {
                matches.stopSpectating(player.getUniqueId());
                player.sendMessage(Msg.msg("<gray>Tu as arrêté d'observer."));
            } else {
                player.sendMessage(Msg.msg("<gray>Usage : <white>/spec <pseudo>"));
            }
            return true;
        }
        if (matches.isBusy(player.getUniqueId())) {
            player.sendMessage(Msg.msg("<red>Impossible d'observer pendant un combat."));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(Msg.msg("<red>Joueur introuvable/hors-ligne."));
            return true;
        }
        if (!matches.spectate(player, target.getUniqueId())) {
            player.sendMessage(Msg.msg("<red>Ce joueur n'est pas en combat."));
        }
        return true;
    }
}
