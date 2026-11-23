package fr.orbit.practice;

import fr.orbit.practice.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Toutes les commandes du Practice. */
public final class Commands implements TabExecutor {

    private final OrbitPractice plugin;

    public Commands(OrbitPractice plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("hcf") || name.equals("hub")) {
            if (!(sender instanceof Player p)) {
                return true;
            }
            if (plugin.hcf() == null) {
                p.sendMessage(Msg.msg("<red>Le mode HCF n'est pas installé sur ce serveur."));
            } else if (name.equals("hcf")) {
                plugin.hcf().enter(p);
            } else {
                plugin.hcf().leave(p);
            }
            return true;
        }
        // En HCF, les commandes du Practice (sauf /practice top et l'admin) attendent le retour au hub.
        if (sender instanceof Player p && HcfZone.is(p) && !name.equals("practice")) {
            p.sendMessage(Msg.msg("<red>Tu es en HCF. <white>/hub<red> pour revenir au Practice."));
            return true;
        }
        switch (name) {
            case "practice" -> practice(sender, args);
            case "duel" -> duel(sender, args);
            case "party" -> party(sender, args);
            case "spec" -> spec(sender, args);
            case "quitter" -> {
                if (sender instanceof Player p) {
                    plugin.queue().leave(p, true);
                }
            }
            default -> { }
        }
        return true;
    }

    // ------------------------------------------------------------------ /practice

    private void practice(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("top")) {
            Ladder ladder = args.length > 1 ? Ladder.byId(args[1]) : null;
            if (ladder == null && sender instanceof Player self) {
                showProfile(self);
            } else {
                showTop(sender, ladder != null ? ladder : Ladder.NODEBUFF, false);
            }
            return;
        }
        if (!sender.hasPermission("practice.admin")) {
            sender.sendMessage(Msg.msg("<white>/practice top [kit]<gray>, <white>/duel<gray>, <white>/party<gray>, <white>/spec<gray>, <white>/quitter"));
            return;
        }
        switch (sub) {
            case "arene" -> arena(sender, args);
            case "hub" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("generer")) {
                    plugin.builder().buildHub();
                    plugin.lobby().setHub(plugin.lobby().generatedHub());
                    sender.sendMessage(Msg.msg("Hub construit dans le monde <white>" + plugin.builder().worldName()
                            + "<gray> : c'est maintenant le point d'arrivée."));
                } else if (args.length > 1 && args[1].equalsIgnoreCase("definir") && sender instanceof Player p) {
                    plugin.lobby().setHub(p.getLocation());
                    sender.sendMessage(Msg.msg("Point d'arrivée du hub posé ici."));
                } else if (sender instanceof Player p) {
                    plugin.lobby().send(p);
                }
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.arenas().load();
                sender.sendMessage(Msg.msg("Config et arènes rechargées (" + plugin.arenas().all().size() + " arènes)."));
            }
            default -> sender.sendMessage(Msg.msg("<white>/practice top<gray>, <white>arene <generer [forcer]|liste|tp <nom>><gray>, "
                    + "<white>hub [definir|generer]<gray>, <white>reload"));
        }
    }

    private void arena(CommandSender sender, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "liste";
        switch (sub) {
            case "generer" -> {
                boolean force = args.length > 2 && args[2].equalsIgnoreCase("forcer");
                int copies = plugin.getConfig().getInt("arenes.copies-par-theme", 2);
                List<String> built = new ArrayList<>(plugin.builder().generate(plugin.arenas(), copies, force));
                built.addAll(plugin.builder().generateSumo(plugin.arenas(),
                        plugin.getConfig().getInt("arenes.plateformes-sumo", 4), force));
                sender.sendMessage(Msg.msg(built.isEmpty()
                        ? "Aucune arène à construire : elles existent toutes. <white>/practice arene generer forcer <gray>les refait (retouches à la main perdues)."
                        : built.size() + " arène(s) construite(s) : <white>" + String.join(", ", built)));
            }
            case "tp" -> {
                Arena a = args.length > 2 ? plugin.arenas().get(args[2]) : null;
                Location loc = a == null ? null : a.spawnA(0);
                if (loc == null || !(sender instanceof Player p)) {
                    sender.sendMessage(Msg.msg("<red>Arène inconnue. <white>/practice arene liste"));
                } else {
                    p.teleport(loc);
                }
            }
            default -> {
                List<String> names = new ArrayList<>();
                for (Arena a : plugin.arenas().all()) {
                    names.add((a.isValid() ? "<white>" : "<red>") + a.name());
                }
                sender.sendMessage(Msg.msg(names.isEmpty() ? "Aucune arène. <white>/practice arene generer"
                        : names.size() + " arènes : " + String.join("<gray>, ", names)));
            }
        }
    }

    /** Affiche les 10 meilleurs Elo d'un type de combat (ou de tous si {@code all}). */
    private static List<String> ladderIds() {
        List<String> ids = new ArrayList<>();
        for (Ladder l : Ladder.values()) {
            ids.add(l.id);
        }
        return ids;
    }

    /** Le livre du hub : ton Elo dans chaque kit, et comment voir un classement. */
    public void showProfile(Player p) {
        p.sendMessage(Msg.raw(Msg.BLEU + "<bold>Ton Elo par kit"));
        StringBuilder line = new StringBuilder();
        int n = 0;
        for (Ladder l : Ladder.values()) {
            var e = plugin.elo().get(p.getUniqueId(), l);
            line.append("<gray>").append(l.label).append(" ").append(Msg.OR).append(e.elo)
                    .append(" <dark_gray>(").append(e.wins).append("V/").append(e.losses).append("D)");
            if (++n % 2 == 0) {
                p.sendMessage(Msg.raw("  " + line));
                line.setLength(0);
            } else {
                line.append("   ");
            }
        }
        if (line.length() > 0) {
            p.sendMessage(Msg.raw("  " + line));
        }
        p.sendMessage(Msg.raw("<gray>Top 10 d'un kit : <white>/practice top <kit> <dark_gray>(" + String.join(", ", ladderIds()) + ")"));
    }

    public void showTop(CommandSender sender, Ladder first, boolean all) {
        for (Ladder ladder : Ladder.values()) {
            if (!all && ladder != first) {
                continue;
            }
            plugin.elo().top(ladder, 10).thenAccept(list -> Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Msg.raw(Msg.BLEU + "<bold>Classement " + ladder.label));
                if (list.isEmpty()) {
                    sender.sendMessage(Msg.raw("<gray>  Personne n'a encore combattu."));
                }
                int rank = 1;
                for (EloStore.Entry e : list) {
                    sender.sendMessage(Msg.raw("<gray>  " + rank++ + ". <white>" + e.name + " <dark_gray>· " + Msg.OR + e.elo
                            + " <dark_gray>· <gray>" + e.wins + " V / " + e.losses + " D"));
                }
            }));
        }
    }

    // ------------------------------------------------------------------ /duel, /party, /spec

    private void duel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            return;
        }
        if (args.length == 0) {
            p.sendMessage(Msg.msg("<white>/duel <pseudo> [kit] <gray>ou <white>/duel accepter <pseudo>"));
            return;
        }
        boolean accept = args[0].equalsIgnoreCase("accepter") && args.length > 1;
        Player other = Bukkit.getPlayerExact(accept ? args[1] : args[0]);
        if (other == null) {
            p.sendMessage(Msg.msg("<red>Joueur introuvable."));
        } else if (HcfZone.is(other)) {
            p.sendMessage(Msg.msg("<red>" + other.getName() + " joue en HCF en ce moment."));
        } else if (accept) {
            plugin.queue().accept(p, other);
        } else {
            Ladder ladder = args.length > 1 ? Ladder.byId(args[1]) : Ladder.NODEBUFF;
            plugin.queue().challenge(p, other, ladder != null ? ladder : Ladder.NODEBUFF);
        }
    }

    private void party(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            return;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "inviter" -> {
                Player guest = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (guest == null) {
                    p.sendMessage(Msg.msg("<red>Joueur introuvable."));
                } else {
                    plugin.queue().invite(p, guest);
                }
            }
            case "accepter" -> plugin.queue().acceptInvite(p);
            case "quitter" -> {
                plugin.queue().leave(p, false);
                plugin.queue().leaveParty(p, true);
            }
            default -> p.sendMessage(Msg.msg("<white>/party inviter <pseudo><gray>, <white>/party accepter<gray>, <white>/party quitter"));
        }
    }

    private void spec(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            return;
        }
        if (plugin.matches().isSpectating(p.getUniqueId())) {
            plugin.matches().stopSpectating(p.getUniqueId());
            return;
        }
        if (args.length == 0) {
            p.sendMessage(Msg.msg("<white>/spec <pseudo>"));
            return;
        }
        if (plugin.matches().isBusy(p.getUniqueId())) {
            p.sendMessage(Msg.msg("<red>Pas pendant ton propre combat."));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        plugin.queue().leave(p, false);
        if (target == null || !plugin.matches().spectate(p, target.getUniqueId())) {
            p.sendMessage(Msg.msg("<red>Ce joueur n'est pas en combat."));
        }
    }

    // ------------------------------------------------------------------ Complétion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("practice")) {
            boolean admin = sender.hasPermission("practice.admin");
            if (args.length == 1) {
                options.add("top");
                if (admin) {
                    options.addAll(List.of("arene", "hub", "reload"));
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
                options.addAll(ladderIds());
            } else if (admin && args.length == 2 && args[0].equalsIgnoreCase("arene")) {
                options.addAll(List.of("generer", "liste", "tp"));
            } else if (admin && args.length == 2 && args[0].equalsIgnoreCase("hub")) {
                options.addAll(List.of("definir", "generer"));
            } else if (admin && args.length == 3 && args[1].equalsIgnoreCase("tp")) {
                plugin.arenas().all().forEach(a -> options.add(a.name()));
            } else if (admin && args.length == 3 && args[1].equalsIgnoreCase("generer")) {
                options.add("forcer");
            }
        } else if (name.equals("party") && args.length == 1) {
            options.addAll(List.of("inviter", "accepter", "quitter"));
        } else if (name.equals("duel") && args.length == 2 && !args[0].equalsIgnoreCase("accepter")) {
            options.addAll(ladderIds());
        } else if (name.equals("duel") && args.length == 1) {
            options.add("accepter");
            Bukkit.getOnlinePlayers().forEach(o -> options.add(o.getName()));
        } else if (args.length <= 2 && !name.equals("quitter")) {
            Bukkit.getOnlinePlayers().forEach(o -> options.add(o.getName()));
        }
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(typed));
        return options;
    }
}
