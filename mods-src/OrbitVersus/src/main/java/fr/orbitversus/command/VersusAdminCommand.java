package fr.orbitversus.command;

import fr.orbitversus.EconomyHook;
import fr.orbitversus.Msg;
import fr.orbitversus.arena.Arena;
import fr.orbitversus.arena.ArenaManager;
import fr.orbitversus.elo.EloStore;
import fr.orbitversus.match.QueueManager;
import fr.orbitversus.npc.NpcManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/** /versus — arena a|b|list|remove, npc, bet, top, reload. */
public final class VersusAdminCommand implements CommandExecutor {

    private static final String PERM = "orbitversus.admin";

    private final ArenaManager arenas;
    private final NpcManager npc;
    private final EloStore elo;
    private final QueueManager queue;
    private final EconomyHook economy;
    private final Map<String, Location> pendingA = new HashMap<>();

    public VersusAdminCommand(ArenaManager arenas, NpcManager npc, EloStore elo, QueueManager queue, EconomyHook economy) {
        this.arenas = arenas;
        this.npc = npc;
        this.elo = elo;
        this.queue = queue;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Msg.msg("<gray>/versus top · bet <montant> · arena · npc · reload"));
            return true;
        }
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "top" -> {
                elo.top(10).thenAccept(list -> {
                    sender.sendMessage(Msg.msg("<#FF3B3B><bold>Classement Elo</bold>"));
                    int rank = 1;
                    for (EloStore.Entry e : list) {
                        sender.sendMessage(Msg.raw("<dark_gray>" + (rank++) + ". <white>" + e.name
                                + " <dark_gray>- <#E8650A>" + e.elo + " Elo <dark_gray>(" + e.wins + "V/" + e.losses + "D)"));
                    }
                    if (list.isEmpty()) {
                        sender.sendMessage(Msg.raw("<gray>Aucun combat enregistré."));
                    }
                });
                return true;
            }
            case "bet" -> {
                if (!(sender instanceof Player p)) {
                    return true;
                }
                if (!economy.available()) {
                    p.sendMessage(Msg.msg("<red>Les paris nécessitent une économie (Vault)."));
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage(Msg.msg("<gray>/versus bet <montant> <dark_gray>(avant de rejoindre une file)"));
                    return true;
                }
                long amount;
                try {
                    amount = Long.parseLong(args[1]);
                } catch (NumberFormatException e) {
                    p.sendMessage(Msg.msg("<red>Montant invalide."));
                    return true;
                }
                if (amount < 0) {
                    p.sendMessage(Msg.msg("<red>Montant invalide."));
                    return true;
                }
                queue.setBet(p.getUniqueId(), amount);
                p.sendMessage(Msg.msg("<gray>Mise réglée à <#FF3B3B>" + amount
                        + "<gray>. Elle s'applique à ta prochaine recherche."));
                return true;
            }
            default -> { /* suite ci-dessous nécessite la permission */ }
        }

        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(Msg.msg("<red>Réservé au staff."));
            return true;
        }

        switch (sub) {
            case "arena" -> handleArena(sender, args);
            case "npc" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Msg.msg("<red>En jeu uniquement."));
                    return true;
                }
                npc.spawn(p.getLocation());
                p.sendMessage(Msg.msg("<green>PNJ Versus créé ici. Les joueurs cliquent dessus pour jouer."));
            }
            case "reload" -> {
                arenas.loadAll();
                sender.sendMessage(Msg.msg("<green>Arènes rechargées (" + arenas.count() + ")."));
            }
            default -> sender.sendMessage(Msg.msg("<gray>/versus top · bet · arena · npc · reload"));
        }
        return true;
    }

    private void handleArena(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Msg.msg("<red>En jeu uniquement."));
            return;
        }
        if (args.length < 2) {
            p.sendMessage(Msg.msg("<gray>/versus arena a <nom> · b <nom> · list · remove <nom>"));
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "a" -> {
                if (args.length < 3) { p.sendMessage(Msg.msg("<gray>/versus arena a <nom>")); return; }
                pendingA.put(args[2].toLowerCase(), p.getLocation());
                p.sendMessage(Msg.msg("<gray>Spawn <#E8650A>A <gray>défini pour <white>" + args[2]
                        + "<gray>. Place-toi à l'autre spawn puis <white>/versus arena b " + args[2]));
            }
            case "b" -> {
                if (args.length < 3) { p.sendMessage(Msg.msg("<gray>/versus arena b <nom>")); return; }
                Location a = pendingA.get(args[2].toLowerCase());
                if (a == null) {
                    p.sendMessage(Msg.msg("<red>Définis d'abord le spawn A : /versus arena a " + args[2]));
                    return;
                }
                arenas.save(args[2], a, p.getLocation());
                pendingA.remove(args[2].toLowerCase());
                p.sendMessage(Msg.msg("<green>Arène <white>" + args[2] + " <green>enregistrée !"));
            }
            case "list" -> {
                p.sendMessage(Msg.msg("<#FF3B3B>Arènes (" + arenas.count() + ") :"));
                for (Arena ar : arenas.all()) {
                    p.sendMessage(Msg.raw("<dark_gray>» <white>" + ar.name() + " <dark_gray>(" + ar.world() + ")"));
                }
            }
            case "remove" -> {
                if (args.length < 3) { p.sendMessage(Msg.msg("<gray>/versus arena remove <nom>")); return; }
                boolean ok = arenas.remove(args[2]);
                p.sendMessage(Msg.msg(ok ? "<green>Arène supprimée." : "<red>Arène introuvable."));
            }
            default -> p.sendMessage(Msg.msg("<gray>/versus arena a <nom> · b <nom> · list · remove <nom>"));
        }
    }
}
