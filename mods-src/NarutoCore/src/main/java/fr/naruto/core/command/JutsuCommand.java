package fr.naruto.core.command;

import fr.naruto.core.gui.Menus;
import fr.naruto.core.jutsu.JutsuService;
import fr.naruto.core.ninja.Ninja;
import fr.naruto.core.ninja.NinjaService;
import fr.naruto.core.text.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /jutsu — ouvre le menu des jutsu (ou lance directement /jutsu <id>). */
public final class JutsuCommand implements CommandExecutor {

    private final NinjaService ninjas;
    private final JutsuService jutsus;

    public JutsuCommand(NinjaService ninjas, JutsuService jutsus) {
        this.ninjas = ninjas;
        this.jutsus = jutsus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        Ninja n = ninjas.get(player.getUniqueId());
        if (n == null) { player.sendMessage(Text.msg("<gray>Chargement...")); return true; }
        if (!n.isComplete()) {
            player.sendMessage(Text.msg("<#FFB347>Choisis d'abord ton affinite et ton clan."));
            Menus.openElement(player);
            return true;
        }
        if (args.length >= 1) {
            jutsus.tryCast(player, args[0].toLowerCase());
            return true;
        }
        Menus.openJutsu(player, n, jutsus);
        return true;
    }
}
