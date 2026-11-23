package fr.naruto.core.command;

import fr.naruto.core.ninja.Clan;
import fr.naruto.core.ninja.Element;
import fr.naruto.core.ninja.Ninja;
import fr.naruto.core.ninja.NinjaService;
import fr.naruto.core.ninja.Rank;
import fr.naruto.core.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /narutoadmin <element|clan|rank|chakra|reset> <joueur> [valeur] — op. */
public final class NarutoAdminCommand implements CommandExecutor {

    private final NinjaService ninjas;

    public NarutoAdminCommand(NinjaService ninjas) { this.ninjas = ninjas; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("naruto.admin")) {
            sender.sendMessage(Text.msg("<red>Permission refusee."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.msg("<red>/narutoadmin <element|clan|rank|chakra|reset> <joueur> [valeur]"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(Text.msg("<red>Joueur hors ligne.")); return true; }
        Ninja n = ninjas.get(target.getUniqueId());
        if (n == null) { sender.sendMessage(Text.msg("<red>Profil non charge.")); return true; }

        boolean missing = false;
        switch (args[0].toLowerCase()) {
            case "element" -> { if (args.length < 3) missing = true; else { n.setElement(Element.byId(args[2])); done(sender, target, "affinite " + n.element().displayName()); } }
            case "clan" -> { if (args.length < 3) missing = true; else { n.setClan(Clan.byId(args[2])); done(sender, target, "clan " + n.clan().displayName()); } }
            case "rank" -> { if (args.length < 3) missing = true; else { n.setRank(Rank.byId(args[2])); done(sender, target, "rang " + n.rank().displayName()); } }
            case "chakra" -> {
                if (args.length >= 3 && args[2].equalsIgnoreCase("full")) n.setChakra(n.maxChakra());
                else if (args.length >= 3) { try { n.setChakra(Double.parseDouble(args[2])); } catch (NumberFormatException ignored) {} }
                done(sender, target, "chakra " + (int) n.chakra());
            }
            case "reset" -> {
                n.setElement(Element.NONE); n.setClan(Clan.NONE); n.setRank(Rank.GENIN); n.setXp(0);
                n.learnedJutsu().clear();
                done(sender, target, "profil reinitialise");
            }
            default -> sender.sendMessage(Text.msg("<red>Sous-commande inconnue."));
        }
        if (missing) { sender.sendMessage(Text.msg("<red>Valeur manquante.")); return true; }
        ninjas.save(n);
        ninjas.updateBar(target, n);
        return true;
    }

    private void done(CommandSender sender, Player target, String what) {
        sender.sendMessage(Text.msg("<green>" + target.getName() + " : " + what));
    }
}
