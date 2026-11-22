package fr.orbit.mmo.quest;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.quest.QuestDefs.QuestDef;
import fr.orbit.mmo.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /quetes : journal. /quetes passer : réplique suivante.
 * /quetes admin … : poser les points, tester les quêtes (permission orbitmmo.admin).
 */
public final class QuestCommand implements TabExecutor {

    private static final List<String> ADMIN = List.of("point", "points", "supprimer", "tp", "manquants", "liste",
            "demarrer", "etape", "terminer", "reset", "recharger");

    private final OrbitMMO plugin;
    private final QuestService svc;

    public QuestCommand(OrbitMMO plugin, QuestService svc) {
        this.plugin = plugin;
        this.svc = svc;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("journal")) {
            if (sender instanceof Player p) {
                QuestJournal.open(svc, p);
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("passer")) {
            if (sender instanceof Player p) {
                svc.skipDialogue(p);
            }
            return true;
        }
        if (!args[0].equalsIgnoreCase("admin")) {
            Text.msg(sender, "<#8C96A3>/quetes <white>— journal ·</white> /quetes passer <white>— réplique suivante");
            return true;
        }
        if (!sender.hasPermission("orbitmmo.admin")) {
            Text.msg(sender, "<#FF5C5C>Réservé à l'administration.");
            return true;
        }
        if (args.length < 2) {
            help(sender);
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "point" -> {
                if (!(sender instanceof Player p) || args.length < 3) {
                    Text.msg(sender, "<#8C96A3>/quetes admin point <nom> <white>— pose le point à votre position");
                    return true;
                }
                svc.points().set(args[2], p.getLocation());
                Text.msg(p, "<#5EE89A>Point <white>" + Text.esc(args[2]) + "</white> posé ici.");
            }
            case "supprimer" -> {
                if (args.length < 3) {
                    Text.msg(sender, "<#8C96A3>/quetes admin supprimer <nom>");
                    return true;
                }
                Text.msg(sender, svc.points().remove(args[2]) ? "<#5EE89A>Point supprimé." : "<#FF5C5C>Point inconnu.");
            }
            case "points" -> {
                Map<String, QuestPoints.Point> all = svc.points().all();
                Text.msg(sender, "<#FFD66E>" + all.size() + " point(s) posé(s) :");
                for (Map.Entry<String, QuestPoints.Point> e : all.entrySet()) {
                    QuestPoints.Point pt = e.getValue();
                    sender.sendMessage(Text.mm("  <white>" + Text.esc(e.getKey()) + "</white> <#8C96A3>" + Text.esc(pt.world())
                            + " " + Math.round(pt.x()) + " " + Math.round(pt.y()) + " " + Math.round(pt.z())));
                }
            }
            case "tp" -> {
                if (!(sender instanceof Player p) || args.length < 3) {
                    Text.msg(sender, "<#8C96A3>/quetes admin tp <point>");
                    return true;
                }
                Location l = svc.points().location(args[2]);
                if (l == null) {
                    Text.msg(p, "<#FF5C5C>Point inconnu ou monde non chargé.");
                } else {
                    p.teleport(l);
                }
            }
            case "manquants" -> {
                boolean any = false;
                for (QuestDef q : svc.registry().quests()) {
                    List<String> miss = svc.missingPoints(q);
                    if (!miss.isEmpty()) {
                        any = true;
                        Text.msg(sender, "<#FFD66E>" + Text.esc(q.name()) + "</#FFD66E> <#8C96A3>(" + q.id() + ") :</#8C96A3> <white>"
                                + Text.esc(String.join(", ", miss)));
                    }
                }
                if (!any) {
                    Text.msg(sender, "<#5EE89A>Tous les points des quêtes sont posés.");
                }
            }
            case "liste" -> {
                for (QuestDef q : svc.registry().quests()) {
                    String state = svc.ready(q) ? "<#5EE89A>prête" : "<#FF8A7A>points manquants";
                    sender.sendMessage(Text.mm("  <white>" + q.id() + "</white> <#8C96A3>· " + Text.esc(q.name())
                            + " · niv. " + q.level() + " · " + (q.faction() == null ? "toutes" : q.faction().label) + " · </#8C96A3>" + state));
                }
            }
            case "demarrer", "terminer", "reset", "etape" -> playerAction(sender, sub, args);
            case "recharger" -> {
                svc.reload();
                Text.msg(sender, "<#5EE89A>Quêtes et points rechargés.");
            }
            default -> help(sender);
        }
        return true;
    }

    private void playerAction(CommandSender sender, String sub, String[] args) {
        if (args.length < 3) {
            Text.msg(sender, "<#8C96A3>/quetes admin " + sub + " <joueur> " + (sub.equals("reset") ? "[quête]" : "<quête>")
                    + (sub.equals("etape") ? " <n°>" : ""));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            Text.msg(sender, "<#FF5C5C>Joueur introuvable.");
            return;
        }
        QuestDef q = args.length >= 4 ? svc.registry().quest(args[3]) : null;
        if (q == null && !sub.equals("reset")) {
            Text.msg(sender, "<#FF5C5C>Quête inconnue (voir /quetes admin liste).");
            return;
        }
        switch (sub) {
            case "demarrer" -> svc.start(target, q);
            case "terminer" -> svc.forceFinish(target, q);
            case "reset" -> svc.reset(target, q);
            case "etape" -> {
                int n;
                try {
                    n = Integer.parseInt(args.length >= 5 ? args[4] : "1");
                } catch (NumberFormatException e) {
                    Text.msg(sender, "<#FF5C5C>Numéro d'étape invalide.");
                    return;
                }
                svc.setStep(target, q, n - 1);
            }
            default -> {
                return;
            }
        }
        Text.msg(sender, "<#5EE89A>Fait.");
    }

    private void help(CommandSender s) {
        Text.msg(s, "<#FFD66E>Quêtes — administration");
        String[][] lines = {
                {"point <nom>", "pose un point à votre position"},
                {"supprimer <nom>", "retire un point"},
                {"points", "liste des points posés"},
                {"tp <nom>", "se téléporter à un point"},
                {"manquants", "points à poser pour chaque quête"},
                {"liste", "quêtes chargées et leur état"},
                {"demarrer <joueur> <quête>", "lance une quête"},
                {"etape <joueur> <quête> <n°>", "place à une étape"},
                {"terminer <joueur> <quête>", "termine (avec récompenses)"},
                {"reset <joueur> [quête]", "efface l'avancement"},
                {"recharger", "relit les fichiers de quêtes"}};
        for (String[] l : lines) {
            s.sendMessage(Text.mm("  <white>/quetes admin " + l[0] + "</white> <#8C96A3>— " + l[1]));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("journal", "passer"));
            if (sender.hasPermission("orbitmmo.admin")) {
                out.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("orbitmmo.admin")) {
            out.addAll(ADMIN);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            String sub = args[1].toLowerCase(Locale.ROOT);
            if (List.of("point", "supprimer", "tp").contains(sub)) {
                out.addAll(svc.points().all().keySet());
                if (sub.equals("point")) {
                    for (QuestDef q : svc.registry().quests()) {
                        out.addAll(svc.missingPoints(q));
                    }
                }
            } else if (List.of("demarrer", "terminer", "reset", "etape").contains(sub)) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    out.add(p.getName());
                }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin")) {
            for (QuestDef q : svc.registry().quests()) {
                out.add(q.id());
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out.stream().distinct().toList();
    }
}
