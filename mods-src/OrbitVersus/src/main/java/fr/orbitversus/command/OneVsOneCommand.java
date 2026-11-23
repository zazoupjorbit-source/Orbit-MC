package fr.orbitversus.command;

import fr.orbitversus.Msg;
import fr.orbitversus.match.QueueManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /1v1 — file solo ; /1v1 leave ; /1v1 <pseudo> défi direct. */
public final class OneVsOneCommand implements CommandExecutor {

    private final QueueManager queue;

    public OneVsOneCommand(QueueManager queue) {
        this.queue = queue;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("En jeu uniquement.");
            return true;
        }
        if (args.length == 0) {
            queue.queue1v1(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("leave")) {
            queue.leave(player);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(Msg.msg("<red>Joueur introuvable/hors-ligne : " + args[0]));
            return true;
        }
        queue.challenge(player, target);
        return true;
    }
}
