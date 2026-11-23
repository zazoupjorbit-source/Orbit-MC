package fr.orbit.mmo.quest;

import fr.orbit.mmo.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * /langue [fr | en | auto] (alias /language, /lang) : langue des dialogues et des quêtes.
 * Sans rien : bascule. « auto » : suit la langue du jeu du joueur (français, ou anglais pour toutes les autres).
 */
public final class LangCommand implements TabExecutor {

    private final QuestService svc;

    public LangCommand(QuestService svc) {
        this.svc = svc;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Text.msg(sender, "<#8C96A3>/langue [fr | en | auto] <white>— à utiliser en jeu");
            return true;
        }
        if (args.length == 0) {
            svc.cycleLang(p);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "fr", "francais", "français", "french" -> svc.setLang(p, "fr");
            case "en", "anglais", "english" -> svc.setLang(p, "en");
            case "auto" -> svc.setLang(p, null);
            default -> {
                Text.msg(p, "<#8C96A3>/langue fr · /langue en · /langue auto");
                return true;
            }
        }
        svc.langNotice(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String last = args[0].toLowerCase(Locale.ROOT);
        return List.of("fr", "en", "auto").stream().filter(s -> s.startsWith(last)).toList();
    }
}
