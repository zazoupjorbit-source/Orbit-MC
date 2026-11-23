package fr.naruto.core.command;

import fr.naruto.core.ninja.Ninja;
import fr.naruto.core.ninja.NinjaService;
import fr.naruto.core.text.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /chakra — affiche ton chakra ; /chakra full (op) recharge. */
public final class ChakraCommand implements CommandExecutor {

    private final NinjaService ninjas;

    public ChakraCommand(NinjaService ninjas) { this.ninjas = ninjas; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        Ninja n = ninjas.get(player.getUniqueId());
        if (n == null) return true;
        if (args.length >= 1 && args[0].equalsIgnoreCase("full") && player.hasPermission("naruto.admin")) {
            n.setChakra(n.maxChakra());
            ninjas.updateBar(player, n);
            player.sendMessage(Text.msg("<green>Chakra recharge."));
            return true;
        }
        player.sendMessage(Text.msg("<aqua>Chakra : " + (int) n.chakra() + "<dark_gray>/" + n.maxChakra()));
        return true;
    }
}
