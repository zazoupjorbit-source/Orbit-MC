package fr.naruto.core.command;

import fr.naruto.core.gui.Menus;
import fr.naruto.core.ninja.Ninja;
import fr.naruto.core.ninja.NinjaService;
import fr.naruto.core.text.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /ninja — profil ninja (ou selection si pas encore fait). */
public final class NinjaCommand implements CommandExecutor {

    private final NinjaService ninjas;

    public NinjaCommand(NinjaService ninjas) { this.ninjas = ninjas; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Reserve aux joueurs.");
            return true;
        }
        Ninja n = ninjas.get(player.getUniqueId());
        if (n == null) {
            player.sendMessage(Text.msg("<gray>Chargement du profil..."));
            return true;
        }
        if (!n.isComplete()) {
            player.sendMessage(Text.msg("<#FFB347>Choisis d'abord ton affinite et ton clan."));
            Menus.openElement(player);
            return true;
        }
        player.sendMessage(Text.raw("<gradient:#E8650A:#FFB347><bold>Profil Ninja</bold></gradient>"));
        player.sendMessage(Text.raw("<gray>Affinite : <" + n.element().color() + ">" + n.element().displayName()));
        player.sendMessage(Text.raw("<gray>Clan : <" + n.clan().color() + ">" + n.clan().displayName()
                + " <dark_gray>(" + n.clan().kekkeiGenkai() + ")"));
        player.sendMessage(Text.raw("<gray>Rang : <" + n.rank().color() + ">" + n.rank().displayName()));
        player.sendMessage(Text.raw("<gray>Chakra : <aqua>" + (int) n.chakra() + "<dark_gray>/" + n.maxChakra()));
        player.sendMessage(Text.raw("<gray>XP : <white>" + n.xp()));
        player.sendMessage(Text.raw("<dark_gray>» <yellow>/jutsu <dark_gray>pour tes techniques"));
        return true;
    }
}
