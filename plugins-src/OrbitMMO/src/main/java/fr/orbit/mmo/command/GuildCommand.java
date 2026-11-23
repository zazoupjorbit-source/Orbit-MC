package fr.orbit.mmo.command;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.guild.Guild;
import fr.orbit.mmo.guild.GuildService;
import fr.orbit.mmo.ui.GuildMenus;
import fr.orbit.mmo.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /guilde : créer, inviter, accepter, refuser, quitter, exclure, promouvoir, rétrograder, chef, dissoudre, chat,
 * banque, déposer, retirer, info, liste, classement, admin. /gc &lt;message&gt; : un message dans le canal de guilde.
 * Les lectures (info, liste, classement) et « admin » répondent aussi depuis la console.
 */
public final class GuildCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("creer", "inviter", "accepter", "refuser", "quitter", "exclure",
            "promouvoir", "retrograder", "chef", "dissoudre", "chat", "banque", "deposer", "retirer", "info", "liste",
            "classement", "aide");
    private static final List<String> ADMIN = List.of("dissoudre", "etage", "banque", "recharger");

    private final OrbitMMO plugin;

    public GuildCommand(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        GuildService guilds = plugin.guilds();
        if (command.getName().equals("gc")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Commande réservée aux joueurs.");
            } else if (args.length == 0) {
                guilds.toggleChat(p);
            } else {
                guilds.chat(p, String.join(" ", args));
            }
            return true;
        }
        String sub = args.length == 0 ? "" : plain(args[0]);
        // --- Lectures et administration : aussi depuis la console.
        switch (sub) {
            case "liste", "list" -> {
                list(sender);
                return true;
            }
            case "classement", "top" -> {
                ranking(sender);
                return true;
            }
            case "aide", "help" -> {
                help(sender);
                return true;
            }
            case "admin" -> {
                admin(sender, args);
                return true;
            }
            case "info" -> {
                Guild g = args.length > 1 ? guilds.find(rest(args, 1))
                        : sender instanceof Player p ? guilds.of(p.getUniqueId()) : null;
                if (g == null) {
                    Text.msg(sender, "<#FF5C5C>" + (args.length > 1 ? "Guilde introuvable." : "Usage : /guilde info <nom>"));
                } else {
                    guilds.info(sender, g);
                }
                return true;
            }
            default -> {
                // la suite demande un joueur
            }
        }
        if (!(sender instanceof Player p)) {
            if (!sub.isEmpty()) {
                Text.msg(sender, "<#FF8A3B>« " + Text.esc(args[0]) + " » se lance en jeu. Depuis la console : liste, classement, info <guilde>, admin.");
            }
            help(sender);
            return true;
        }
        switch (sub) {
            case "creer", "create", "fonder" -> {
                if (args.length < 2) {
                    Text.msg(p, "<#FF5C5C>Usage : /guilde creer <nom> <#8C96A3>(" + Text.num(guilds.createCost()) + " or)");
                } else {
                    guilds.create(p, rest(args, 1));
                }
            }
            case "inviter", "invite" -> {
                Player target = args.length < 2 ? null : Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    Text.msg(p, "<#FF5C5C>" + (args.length < 2 ? "Usage : /guilde inviter <joueur>" : "Joueur introuvable (il doit être en ligne)."));
                } else {
                    guilds.invite(p, target);
                }
            }
            case "accepter", "accept", "rejoindre" -> guilds.accept(p);
            case "refuser", "decline" -> guilds.decline(p);
            case "quitter", "leave" -> guilds.leave(p);
            case "exclure", "expulser", "kick" -> named(p, args, "exclure", () -> guilds.kick(p, args[1]));
            case "promouvoir", "promote" -> named(p, args, "promouvoir", () -> guilds.promote(p, args[1]));
            case "retrograder", "demote" -> named(p, args, "retrograder", () -> guilds.demote(p, args[1]));
            case "chef", "leader" -> named(p, args, "chef", () -> guilds.transfer(p, args[1]));
            case "dissoudre", "disband" -> {
                if (args.length > 1 && plain(args[1]).equals("confirmer")) {
                    guilds.disband(p);
                } else {
                    Text.msg(p, "<#FF8A3B>Dissoudre la guilde est définitif</#FF8A3B> (l'or de la banque vous revient). "
                            + "<click:run_command:'/guilde dissoudre confirmer'><#FF5C5C><b>[Confirmer]</b></#FF5C5C></click>");
                }
            }
            case "chat", "c" -> {
                if (args.length > 1) {
                    guilds.chat(p, rest(args, 1));
                } else {
                    guilds.toggleChat(p);
                }
            }
            case "banque", "bank" -> GuildMenus.bank(plugin, p);
            case "deposer", "deposit" -> money(p, args, "deposer", n -> guilds.deposit(p, n));
            case "retirer", "withdraw" -> money(p, args, "retirer", n -> guilds.withdraw(p, n));
            default -> GuildMenus.open(plugin, p);
        }
        return true;
    }

    private static String plain(String s) {
        return java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private static String rest(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    private void named(Player p, String[] args, String sub, Runnable action) {
        if (args.length < 2) {
            Text.msg(p, "<#FF5C5C>Usage : /guilde " + sub + " <joueur>");
        } else {
            action.run();
        }
    }

    /** Montant : un nombre entier, ou « tout ». */
    private void money(Player p, String[] args, String sub, java.util.function.LongConsumer action) {
        if (args.length >= 2) {
            if (plain(args[1]).equals("tout")) {
                action.accept(0);
                return;
            }
            try {
                long n = Long.parseLong(args[1]);
                if (n > 0) {
                    action.accept(n);
                    return;
                }
            } catch (NumberFormatException ignored) {
                // message d'usage ci-dessous
            }
        }
        Text.msg(p, "<#FF5C5C>Usage : /guilde " + sub + " <montant | tout>");
    }

    private void list(CommandSender to) {
        GuildService guilds = plugin.guilds();
        if (guilds.all().isEmpty()) {
            Text.msg(to, "<#8C96A3>Aucune guilde pour l'instant. <white>/guilde creer <nom></white> (" + Text.num(guilds.createCost()) + " or).");
            return;
        }
        Text.msg(to, "<" + GuildService.HEX + "><b>Guildes</b></" + GuildService.HEX + "> <#8C96A3>(" + guilds.all().size() + ")");
        for (Guild g : guilds.all()) {
            Guild.Member chef = g.leader();
            to.sendMessage(Text.mm("  " + g.tag() + " <#8C96A3>· " + g.faction().tag() + " <#8C96A3>· " + g.size() + "/" + guilds.maxMembers()
                    + " · chef <white>" + (chef == null ? "—" : Text.esc(chef.name())) + "</white> · étage <white>" + g.towerCleared()));
        }
    }

    private void ranking(CommandSender to) {
        List<Guild> top = plugin.guilds().ranking();
        if (top.isEmpty()) {
            Text.msg(to, "<#8C96A3>Aucune guilde au classement pour l'instant.");
            return;
        }
        Text.msg(to, "<#FFD66E><b>Classement des guildes</b></#FFD66E> <#8C96A3>· Pilier des Astres, étage franchi");
        int n = 0;
        for (Guild g : top) {
            if (++n > 10) {
                break;
            }
            to.sendMessage(Text.mm("  <#FFD66E>" + n + ".</#FFD66E> " + g.tag() + " <#8C96A3>· étage <white>" + g.towerCleared()
                    + "</white> · " + g.firsts() + " premier(s) passage(s) · " + g.faction().tag()));
        }
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("orbitmmo.admin")) {
            Text.msg(sender, "<#FF5C5C>Réservé aux administrateurs.");
            return;
        }
        GuildService guilds = plugin.guilds();
        String sub = args.length < 2 ? "" : plain(args[1]);
        try {
            switch (sub) {
                case "recharger", "reload" -> guilds.reload(sender);
                case "dissoudre" -> {
                    Guild g = guild(sender, rest(args, 2));
                    if (g != null) {
                        guilds.adminDisband(sender, g);
                    }
                }
                case "etage" -> {
                    int n = Integer.parseInt(args[2]);
                    Guild g = guild(sender, rest(args, 3));
                    if (g != null) {
                        guilds.adminFloor(sender, g, n);
                    }
                }
                case "banque" -> {
                    long n = Long.parseLong(args[2]);
                    Guild g = guild(sender, rest(args, 3));
                    if (g != null) {
                        guilds.adminBank(sender, g, n);
                    }
                }
                default -> adminHelp(sender);
            }
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            adminHelp(sender);
        }
    }

    private Guild guild(CommandSender sender, String name) {
        Guild g = name.isBlank() ? null : plugin.guilds().find(name);
        if (g == null) {
            Text.msg(sender, "<#FF5C5C>Guilde introuvable : « " + Text.esc(name) + " ». /guilde liste");
        }
        return g;
    }

    private void adminHelp(CommandSender s) {
        Text.msg(s, "<#FFD66E><b>/guilde admin</b>");
        for (String l : List.of("dissoudre <guilde>", "etage <n> <guilde> <#8C96A3>(étage franchi du Pilier)",
                "banque <+/-montant> <guilde>", "recharger <#8C96A3>(relit guildes.yml)")) {
            s.sendMessage(Text.mm("  <#D7DCE2>/guilde admin " + l));
        }
    }

    private void help(CommandSender s) {
        GuildService guilds = plugin.guilds();
        Text.msg(s, "<" + GuildService.HEX + "><b>/guilde</b></" + GuildService.HEX + "> <#8C96A3>· une faction par guilde, "
                + guilds.maxMembers() + " membres, chef · officiers · membres");
        String[] lines = {
                "<#8C96A3>(seul) ouvre le menu de guilde",
                "creer <nom> <#8C96A3>(" + Text.num(guilds.createCost()) + " or)",
                "inviter <joueur> <#8C96A3>· accepter · refuser · quitter",
                "exclure <joueur> <#8C96A3>(chef, officiers)",
                "promouvoir <joueur> <#8C96A3>· retrograder <joueur> · chef <joueur> (chef)",
                "dissoudre <#8C96A3>(chef)",
                "chat [message] <#8C96A3>· ou /gc <message>",
                "banque <#8C96A3>· deposer <montant|tout> · retirer <montant|tout> (chef, officiers)",
                "info [guilde] <#8C96A3>· liste · classement"};
        for (String l : lines) {
            s.sendMessage(Text.mm("  <#D7DCE2>/guilde " + l));
        }
        if (s.hasPermission("orbitmmo.admin")) {
            s.sendMessage(Text.mm("  <#D7DCE2>/guilde admin <#8C96A3>dissoudre · etage · banque · recharger"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("gc")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(SUBS);
            if (sender.hasPermission("orbitmmo.admin")) {
                subs.add("admin");
            }
            return Completions.filter(args[0], subs);
        }
        String sub = plain(args[0]);
        if (args.length == 2) {
            switch (sub) {
                case "inviter" -> {
                    return Completions.players(args[1]);
                }
                case "exclure", "promouvoir", "retrograder", "chef" -> {
                    Guild g = sender instanceof Player p ? plugin.guilds().of(p.getUniqueId()) : null;
                    List<String> names = new ArrayList<>();
                    if (g != null) {
                        for (Guild.Member m : g.members().values()) {
                            names.add(m.name());
                        }
                    }
                    return Completions.filter(args[1], names);
                }
                case "info" -> {
                    return Completions.filter(args[1], plugin.guilds().names());
                }
                case "deposer", "retirer" -> {
                    return Completions.filter(args[1], List.of("tout", "100", "1000"));
                }
                case "dissoudre" -> {
                    return Completions.filter(args[1], List.of("confirmer"));
                }
                case "admin" -> {
                    return sender.hasPermission("orbitmmo.admin") ? Completions.filter(args[1], ADMIN) : List.of();
                }
                default -> {
                    return List.of();
                }
            }
        }
        if (sub.equals("admin") && sender.hasPermission("orbitmmo.admin")) {
            String a = plain(args[1]);
            if ((a.equals("dissoudre") && args.length == 3) || ((a.equals("etage") || a.equals("banque")) && args.length == 4)) {
                return Completions.filter(args[args.length - 1], plugin.guilds().names());
            }
        }
        return List.of();
    }
}
