package fr.orbitversus.command;

import fr.orbitversus.Msg;
import fr.orbitversus.match.QueueManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /2v2 <nom d'équipe> — rejoint/forme une équipe de 2 puis la file 2v2. */
public final class TwoVsTwoCommand implements CommandExecutor {

    private final QueueManager queue;

    public TwoVsTwoCommand(QueueManager queue) {
        this.queue = queue;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("En jeu uniquement.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Msg.msg("<gray>Usage : <white>/2v2 <nom d'équipe><gray>. "
                    + "Ton coéquipier tape le même nom pour te rejoindre."));
            return true;
        }
        if (args[0].equalsIgnoreCase("leave")) {
            queue.leave(player);
            return true;
        }
        String teamName = args[0];
        if (teamName.length() > 16) {
            teamName = teamName.substring(0, 16);
        }
        queue.queue2v2(player, teamName);
        return true;
    }
}
