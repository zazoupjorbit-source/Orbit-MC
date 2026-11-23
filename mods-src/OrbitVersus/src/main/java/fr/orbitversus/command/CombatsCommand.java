package fr.orbitversus.command;

import fr.orbitversus.Msg;
import fr.orbitversus.history.HistoryStore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /combats — affiche tes 8 derniers combats. */
public final class CombatsCommand implements CommandExecutor {

    private final HistoryStore history;

    public CombatsCommand(HistoryStore history) {
        this.history = history;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("En jeu uniquement.");
            return true;
        }
        history.recent(player.getUniqueId(), 8).thenAccept(list -> {
            player.sendMessage(Msg.msg("<#FF3B3B><bold>Tes derniers combats</bold>"));
            if (list.isEmpty()) {
                player.sendMessage(Msg.raw("<gray>Aucun combat pour le moment. Va au PNJ Versus !"));
                return;
            }
            for (HistoryStore.Record r : list) {
                String result = r.won() ? "<green>Victoire" : "<red>Défaite";
                player.sendMessage(Msg.raw("<dark_gray>» " + result + " <dark_gray>[" + r.mode() + "] "
                        + "<gray>vs <white>" + r.opponents()
                        + " <dark_gray>· <white>" + r.scoreSelf() + "-" + r.scoreOpp()));
            }
        });
        return true;
    }
}
