package fr.orbitgames.hcf.core.command;

import fr.orbitgames.hcf.api.TimerKeys;
import fr.orbitgames.hcf.api.event.FactionLeaveEvent;
import fr.orbitgames.hcf.api.model.DtrChangeReason;
import fr.orbitgames.hcf.api.model.Faction;
import fr.orbitgames.hcf.api.model.FactionRole;
import fr.orbitgames.hcf.api.model.FactionType;
import fr.orbitgames.hcf.api.model.Relation;
import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.api.util.Messages;
import fr.orbitgames.hcf.api.util.Text;
import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import fr.orbitgames.hcf.core.claim.ClaimManager;
import fr.orbitgames.hcf.core.faction.FactionImpl;
import fr.orbitgames.hcf.core.faction.FactionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Commande principale /f : factions, membres, claims, DTR, relations.
 */
public class FactionCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "create", "disband", "invite", "uninvite", "join", "leave", "kick",
            "promote", "demote", "leader", "home", "sethome", "claim", "unclaim", "unclaimall",
            "map", "who", "list", "ally", "unally", "focus", "unfocus", "stuck", "rename",
            "deposit", "withdraw", "chat");
    private static final List<String> ADMIN_SUBCOMMANDS = List.of(
            "setdtr", "freeze", "unfreeze", "points", "bypass");

    private final OrbitHCFCorePlugin plugin;
    private final FactionManager factions;
    private final Messages messages;

    public FactionCommand(OrbitHCFCorePlugin plugin) {
        this.plugin = plugin;
        this.factions = plugin.getFactionManager();
        this.messages = plugin.getMessages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "create" -> create(sender, args);
            case "disband" -> disband(sender, args);
            case "invite" -> invite(sender, args);
            case "uninvite" -> uninvite(sender, args);
            case "join", "accept" -> join(sender, args);
            case "leave" -> leave(sender);
            case "kick" -> kick(sender, args);
            case "promote" -> promoteOrDemote(sender, args, true);
            case "demote" -> promoteOrDemote(sender, args, false);
            case "leader" -> transferLeader(sender, args);
            case "home" -> home(sender);
            case "sethome" -> sethome(sender);
            case "claim" -> claim(sender, args);
            case "unclaim" -> unclaim(sender);
            case "unclaimall" -> unclaimAll(sender);
            case "map" -> map(sender);
            case "who", "show", "info", "f" -> who(sender, args);
            case "list" -> list(sender, args);
            case "ally" -> ally(sender, args);
            case "unally", "neutral" -> unally(sender, args);
            case "focus" -> focus(sender, args);
            case "unfocus" -> unfocus(sender);
            case "stuck" -> stuck(sender);
            case "rename" -> rename(sender, args);
            case "deposit", "d" -> bank(sender, args, true);
            case "withdraw", "w" -> bank(sender, args, false);
            case "chat", "c" -> chat(sender, args);
            case "setdtr" -> adminSetDtr(sender, args);
            case "freeze" -> adminFreeze(sender, args);
            case "unfreeze" -> adminUnfreeze(sender, args);
            case "points" -> adminPoints(sender, args);
            case "bypass" -> adminBypass(sender);
            default -> {
                // /f <faction|joueur> : raccourci vers who
                FactionImpl target = resolveFaction(sub);
                if (target != null) {
                    sendWho(sender, target);
                } else {
                    sendHelp(sender);
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Banque de faction et chat
    // ------------------------------------------------------------------

    private void say(CommandSender sender, String message) {
        sender.sendMessage(Text.color(messages.getPrefix() + message));
    }

    /** /f deposit <montant|all> (tout membre) et /f withdraw <montant|all> (officier et plus). */
    private void bank(CommandSender sender, String[] args, boolean deposit) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        FactionImpl faction = factionOf(player);
        if (faction == null) {
            return;
        }
        var economy = plugin.getEconomy();
        if (args.length < 2) {
            say(sender, "&eBanque de &6" + faction.getName() + "&e : &a" + economy.format(economy.getBalance(faction.getId()))
                    + " &7(/f deposit <montant|all>, /f withdraw <montant|all>)");
            return;
        }
        if (!deposit && !requireRole(player, faction, FactionRole.OFFICER)) {
            return;
        }
        UUID from = deposit ? player.getUniqueId() : faction.getId();
        UUID to = deposit ? faction.getId() : player.getUniqueId();
        long amount = args[1].equalsIgnoreCase("all") ? economy.getBalance(from) : MoneyCommand.parseAmount(args[1]);
        if (amount <= 0) {
            say(sender, args[1].equalsIgnoreCase("all") ? "&cRien a deplacer." : "&cMontant invalide : &f" + args[1]);
            return;
        }
        if (!economy.withdraw(from, amount)) {
            say(sender, "&cIl n'y a que &f" + economy.format(economy.getBalance(from)) + "&c.");
            return;
        }
        economy.deposit(to, amount);
        String line = Text.color(messages.getPrefix() + "&e" + player.getName() + (deposit ? " &aa depose &f" : " &ca retire &f")
                + economy.format(amount) + (deposit ? " &adans" : " &cde") + " la banque de faction &7("
                + economy.format(economy.getBalance(faction.getId())) + ")");
        for (Player online : faction.getOnlinePlayers()) {
            online.sendMessage(line);
        }
    }

    /** /f chat [f|a|p] : change de canal (sans argument : passe au suivant). */
    private void chat(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        FactionImpl faction = factionOf(player);
        if (faction == null) {
            return;
        }
        var chat = plugin.getChatManager();
        var current = chat.get(player.getUniqueId());
        fr.orbitgames.hcf.core.server.ChatManager.Channel next;
        if (args.length >= 2) {
            next = switch (args[1].toLowerCase(Locale.ROOT).charAt(0)) {
                case 'f' -> fr.orbitgames.hcf.core.server.ChatManager.Channel.FACTION;
                case 'a' -> fr.orbitgames.hcf.core.server.ChatManager.Channel.ALLY;
                default -> fr.orbitgames.hcf.core.server.ChatManager.Channel.PUBLIC;
            };
        } else {
            next = current == fr.orbitgames.hcf.core.server.ChatManager.Channel.PUBLIC
                    ? fr.orbitgames.hcf.core.server.ChatManager.Channel.FACTION
                    : fr.orbitgames.hcf.core.server.ChatManager.Channel.PUBLIC;
        }
        chat.set(player.getUniqueId(), next);
        say(sender, "&eCanal de chat : &6" + next.label + "&e. &7(« ! » devant un message = public, « @ » = faction)");
    }

    private void sendHelp(CommandSender sender) {
        messages.sendList(sender, "help", "page", 1, "pages", 1);
        say(sender, "&e/f deposit|withdraw <montant|all> &7banque de faction  &e/f chat [f|a|p] &7canal de chat");
        say(sender, "&e/balance &7ton argent  &e/pay <joueur> <montant>  &7Boutique : panneaux du spawn");
        if (sender.hasPermission("orbithcf.admin")) {
            messages.sendList(sender, "help-admin");
        }
    }

    // ------------------------------------------------------------------
    // Cycle de vie
    // ------------------------------------------------------------------

    private void create(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "help");
            return;
        }
        if (factions.getFactionByPlayer(player.getUniqueId()) != null) {
            messages.send(sender, "faction.already-in-faction");
            return;
        }
        String name = args[1];
        int min = plugin.getFactionsConfig().getInt("faction.min-name-length", 3);
        int max = plugin.getFactionsConfig().getInt("faction.max-name-length", 16);
        if (name.length() < min || name.length() > max) {
            messages.send(sender, "faction.name-length", "min", min, "max", max);
            return;
        }
        String pattern = plugin.getFactionsConfig().getString("faction.name-pattern", "[a-zA-Z0-9_]+");
        if (!Pattern.matches(pattern, name)) {
            messages.send(sender, "faction.name-invalid");
            return;
        }
        if (factions.getFactionByName(name) != null) {
            messages.send(sender, "faction.name-taken");
            return;
        }
        FactionImpl faction = factions.createFaction(player, name);
        if (faction != null) {
            messages.send(player, "faction.created", "faction", faction.getName());
            messages.broadcastRaw("faction.created-broadcast", "joueur", player.getName(), "faction", faction.getName());
        }
    }

    private void disband(CommandSender sender, String[] args) {
        FactionImpl faction;
        if (args.length >= 2 && sender.hasPermission("orbithcf.admin")) {
            faction = resolveFaction(args[1]);
            if (faction == null) {
                messages.send(sender, "general.unknown-faction", "faction", args[1]);
                return;
            }
        } else {
            Player player = asPlayer(sender);
            if (player == null) {
                return;
            }
            faction = factionOf(player);
            if (faction == null) {
                return;
            }
            if (!hasRole(player, faction, FactionRole.LEADER)) {
                messages.send(sender, "general.insufficient-rank");
                return;
            }
        }
        if (faction.isSystemFaction()) {
            messages.send(sender, "faction.system-faction");
            return;
        }
        String name = faction.getName();
        for (Player online : faction.getOnlinePlayers()) {
            messages.send(online, "faction.disbanded", "faction", name);
        }
        UUID bankOwner = faction.getLeader();
        UUID bankId = faction.getId();
        if (factions.disband(faction, sender)) {
            // La banque de la faction revient a son chef.
            long bank = plugin.getEconomy().getBalance(bankId);
            if (bank > 0 && bankOwner != null) {
                plugin.getEconomy().deposit(bankOwner, bank);
            }
            plugin.getEconomy().forget(bankId);
            messages.send(sender, "faction.disbanded", "faction", name);
            messages.broadcastRaw("faction.disbanded-broadcast", "faction", name, "joueur", sender.getName());
        }
    }

    private void invite(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null || args.length < 2) {
            return;
        }
        FactionImpl faction = factionOf(player);
        if (faction == null || !requireRole(player, faction, FactionRole.OFFICER)) {
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "general.unknown-player", "joueur", args[1]);
            return;
        }
        if (factions.getFactionByPlayer(target.getUniqueId()) != null) {
            messages.send(sender, "faction.target-already-in-faction");
            return;
        }
        if (faction.isInvited(target.getUniqueId())) {
            messages.send(sender, "faction.already-invited");
            return;
        }
        long expiry = System.currentTimeMillis()
                + plugin.getFactionsConfig().getLong("faction.invitation-expiry-seconds", 300L) * 1000L;
        faction.invite(target.getUniqueId(), expiry);
        messages.send(player, "faction.invited", "joueur", target.getName());
        messages.send(target, "faction.invite-received", "faction", faction.getName());
    }

    private void uninvite(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null || args.length < 2) {
            return;
        }
        FactionImpl faction = factionOf(player);
        if (faction == null || !requireRole(player, faction, FactionRole.OFFICER)) {
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        UUID targetId = target != null ? target.getUniqueId() : null;
        if (targetId == null) {
            messages.send(sender, "general.unknown-player", "joueur", args[1]);
            return;
        }
        if (!faction.isInvited(targetId)) {
            messages.send(sender, "faction.not-invited");
            return;
        }
        faction.revokeInvite(targetId);
        messages.send(player, "faction.invite-revoked", "joueur", args[1]);
    }

    private void join(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null || args.length < 2) {
            return;
        }
        if (factions.getFactionByPlayer(player.getUniqueId()) != null) {
            messages.send(sender, "faction.already-in-faction");
            return;
        }
        FactionImpl faction = resolveFaction(args[1]);
        if (faction == null || faction.isSystemFaction()) {
            messages.send(sender, "general.unknown-faction", "faction", args[1]);
            return;
        }
        if (!faction.isInvited(player.getUniqueId())) {
            messages.send(sender, "faction.no-invite");
            return;
        }
        int maxMembers = plugin.getFactionsConfig().getInt("faction.max-members", 20);
        if (faction.getMemberCount() >= maxMembers) {
            messages.send(sender, "faction.full", "max", maxMembers);
            return;
        }
        if (factions.join(faction, player)) {
            messages.send(player, "faction.joined", "faction", faction.getName());
            for (Player online : faction.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(player.getUniqueId())) {
                    messages.send(online, "faction.joined-broadcast", "joueur", player.getName());
                }
            }
        }
    }

    private void leave(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        FactionImpl faction = factionOf(player);
        if (faction == null) {
            return;
        }
        if (player.getUniqueId().equals(faction.getLeader())) {
            messages.send(sender, "faction.leader-cannot-leave");
            return;
        }
        if (factions.removeMember(faction, player.getUniqueId(), FactionLeaveEvent.Reason.LEAVE)) {
            messages.send(player, "faction.left", "faction", faction.getName());
            for (Player online : faction.getOnlinePlayers()) {
                messages.send(online, "faction.left-broadcast", "joueur", player.getName());
            }
        }
    }

    private void kick(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null || args.length < 2) {
            return;
        }
        FactionImpl faction = factionOf(player);
        if (faction == null || !requireRole(player, faction, FactionRole.OFFICER)) {
            return;
        }
        UUID targetId = findMember(faction, args[1]);
        if (targetId == null) {
            messages.send(sender, "faction.not-a-member");
            return;
        }
        FactionRole targetRole = faction.getRole(targetId);
        FactionRole senderRole = faction.getRole(player.getUniqueId());
        if (targetRole.isAtLeast(senderRole)) {
            messages.send(sender, "faction.cannot-kick-higher");
            return;
        }
        String targetName = plugin.getProfileManager().getNameOf(targetId);
        if (factions.removeMember(faction, targetId, FactionLeaveEvent.Reason.KICK)) {
            messages.send(player, "faction.kicked", "joueur", targetName);
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                messages.send(target, "faction.kicked-target", "faction", faction.getName());
            }
            for (Player online : faction.getOnlinePlayers()) {
                messages.send(online, "faction.kicked-broadcast", "joueur", targetName, "par", player.getName());
            }
        }
    }

    private void promoteOrDemote(CommandSender sender, String[] args, boolean promote) {
        Player player = asPlayer(sender);
        if (player == null || args.length < 2) {
            return;
        }
        FactionImpl faction = factionOf(player);
        if (faction == null || !requireRole(player, faction, FactionRole.COLEADER)) {
            return;
        }
        UUID targetId = findMember(faction, args[1]);
        if (targetId == null) {
            messages.send(sender, "faction.not-a-member");
            return;
        }
        FactionRole current = faction.getRole(targetId);
        if (current == FactionRole.LEADER) {
            messages.send(sender, promote ? "faction.cannot-promote" : "faction.cannot-demote");
            return;
        }
        FactionRole next = promote ? current.promoted() : current.demoted();
        if (next == null || next == FactionRole.LEADER) {
            messages.send(sender, promote ? "faction.cannot-promote" : "faction.cannot-demote");
            return;
        }
        factions.setRole(faction, targetId, next);
        String targetName = plugin.getProfileManager().getNameOf(targetId);
        messages.send(player, promote ? "faction.promoted" : "faction.demoted",
                "joueur", targetName, "rang", next.getDisplayName());
    }

    private void transferLeader(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null || args.length < 2) {
            return;
        }
        FactionImpl faction = factionOf(player);
        if (faction == null || !hasRole(player, faction, FactionRole.LEADER)) {
            messages.send(sender, "general.insufficient-rank");
            return;
        }
        UUID targetId = findMember(faction, args[1]);
        if (targetId == null) {
            messages.send(sender, "faction.not-a-member");
            return;
        }
        if (targetId.equals(player.getUniqueId())) {
            return;
        }
        factions.setLeader(faction, targetId);
        String targetName = plugin.getProfileManager().getNameOf(targetId);
        for (Player online : faction.getOnlinePlayers()) {
            messages.send(online, "faction.new-leader", "joueur", targetName);
        }
    }

    // ------------------------------------------------------------------
    // Home / stuck
    // ------------------------------------------------------------------

    private void home(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        FactionImpl faction = factionOf(player);
        if (faction == null) {
            return;
        }
        Location home = faction.getHome();
        if (home == null) {
            messages.send(sender, "home.none");
            return;
        }
        Duration warmup = Duration.ofSeconds(
                plugin.getFactionsConfig().getLong("timers.home-warmup-seconds", 10L));
        messages.send(player, "home.teleporting", "temps", DurationFormat.compact(warmup));
        plugin.getWarmupManager().start(player, TimerKeys.HOME, warmup, () -> {
            player.teleport(home);
            messages.send(player, "home.teleported");
        }, "home.cancelled");
    }

    private void sethome(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        FactionImpl faction = factionOf(player);
        if (faction == null || !requireRole(player, faction, FactionRole.COLEADER)) {
            return;
        }
        Faction at = plugin.getClaimManager().getFactionAt(player.getLocation());
        if (!at.getId().equals(faction.getId())) {
            messages.send(sender, "home.must-be-in-claim");
            return;
        }
        faction.setHome(player.getLocation());
        factions.saveFaction(faction);
        messages.send(player, "home.set");
    }

    private void stuck(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        Duration delay = Duration.ofSeconds(
                plugin.getFactionsConfig().getLong("timers.stuck-delay-seconds", 30L));
        messages.send(player, "stuck.start", "temps", DurationFormat.compact(delay));
        plugin.getWarmupManager().start(player, TimerKeys.STUCK, delay, () -> {
            Location safe = plugin.getClaimManager().findNearestWilderness(player.getLocation(), 24);
            if (safe == null) {
                messages.send(player, "stuck.failed");
                return;
            }
            player.teleport(safe);
            messages.send(player, "stuck.teleported");
        }, "stuck.cancelled");
    }

    // ------------------------------------------------------------------
    // Claims
    // ------------------------------------------------------------------

    /** /f claim : donne la baguette. /f claim valider : achete le terrain selectionne. */
    private void claim(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        FactionImpl faction = factionOf(player);
        if (faction == null) {
            return;
        }
        if (args.length >= 2 && (args[1].equalsIgnoreCase("valider") || args[1].equalsIgnoreCase("confirm"))) {
            plugin.getAreaManager().confirm(player);
        } else {
            plugin.getAreaManager().giveWand(player, faction);
        }
    }

    /** /f unclaim : rend le terrain sur lequel on se tient (une partie du prix revient a la banque). */
    private void unclaim(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        FactionImpl faction = factionOf(player);
        if (faction == null || !requireRole(player, faction, FactionRole.OFFICER)) {
            return;
        }
        var area = plugin.getAreaManager().areaAt(player.getLocation());
        if (area == null) {
            messages.send(sender, "claim.no-claim-here");
            return;
        }
        if (!area.factionId.equals(faction.getId())) {
            messages.send(sender, "claim.not-your-claim");
            return;
        }
        long back = plugin.getAreaManager().remove(area);
        if (faction.getHome() != null && area.contains(faction.getHome())) {
            say(sender, "&7Le home de faction etait sur ce terrain : pense a le reposer (&f/f sethome&7).");
        }
        say(sender, "&cTerrain abandonne (" + area.minX + ", " + area.minZ + " -> " + area.maxX + ", " + area.maxZ + "). &a"
                + plugin.getEconomy().format(back) + " &7rendus a la banque de faction.");
    }
    private void unclaimAll(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        FactionImpl faction = factionOf(player);
        if (faction == null || !hasRole(player, faction, FactionRole.LEADER)) {
            messages.send(sender, "general.insufficient-rank");
            return;
        }
        plugin.getClaimManager().unclaimAll(faction);
        messages.send(player, "claim.unclaimed-all");
    }

    private void map(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        Location location = player.getLocation();
        int centerCx = location.getBlockX() >> 4;
        int centerCz = location.getBlockZ() >> 4;
        FactionImpl playerFaction = factions.getImplByPlayer(player.getUniqueId());
        messages.sendRaw(player, "map.header", "x", location.getBlockX(), "z", location.getBlockZ());
        for (int dz = -4; dz <= 4; dz++) {
            StringBuilder row = new StringBuilder();
            for (int dx = -8; dx <= 8; dx++) {
                int cx = centerCx + dx;
                int cz = centerCz + dz;
                Location probe = new Location(location.getWorld(), (cx << 4) + 8, 64, (cz << 4) + 8);
                Faction at = plugin.getClaimManager().getFactionAt(probe);
                String color;
                switch (at.getType()) {
                    case PLAYER -> {
                        Relation relation = factions.getRelation(playerFaction, at);
                        color = relation == Relation.MEMBER ? "&a" : relation == Relation.ALLY ? "&d" : "&c";
                    }
                    case SAFEZONE, WARZONE, ROAD, EVENT -> color = "&6";
                    default -> color = "&8";
                }
                if (dx == 0 && dz == 0) {
                    row.append("&f");
                    row.append('◉');
                } else {
                    row.append(color).append('■');
                }
            }
            player.sendMessage(Text.color(row.toString()));
        }
        messages.sendRaw(player, "map.legend");
    }

    // ------------------------------------------------------------------
    // Informations
    // ------------------------------------------------------------------

    private void who(CommandSender sender, String[] args) {
        FactionImpl faction;
        if (args.length >= 2) {
            faction = resolveFaction(args[1]);
            if (faction == null) {
                messages.send(sender, "general.unknown-faction", "faction", args[1]);
                return;
            }
        } else {
            Player player = asPlayer(sender);
            if (player == null) {
                return;
            }
            faction = factionOf(player);
            if (faction == null) {
                return;
            }
        }
        sendWho(sender, faction);
    }

    private void sendWho(CommandSender sender, FactionImpl faction) {
        messages.sendRaw(sender, "who.header", "faction", faction.getName(),
                "online", faction.getOnlineCount(), "total", faction.getMemberCount());
        if (faction.isSystemFaction()) {
            messages.sendRaw(sender, "who.system", "type", faction.getType().name());
            return;
        }
        messages.sendRaw(sender, "who.leader",
                "joueur", plugin.getProfileManager().getNameOf(faction.getLeader()));
        String memberList = faction.getMembers().entrySet().stream()
                .sorted((a, b) -> b.getValue().getPriority() - a.getValue().getPriority())
                .map(entry -> {
                    String name = plugin.getProfileManager().getNameOf(entry.getKey());
                    boolean online = Bukkit.getPlayer(entry.getKey()) != null;
                    return (online ? "&a" : "&7") + entry.getValue().getPrefix() + name;
                })
                .collect(Collectors.joining("&f, "));
        messages.sendRaw(sender, "who.members", "liste", memberList);

        String dtrColor = faction.isRaidable() ? "&c"
                : faction.getDtr() >= faction.getMaxDtr() * 0.5D ? "&a" : "&e";
        String freezeSuffix = faction.isDtrFrozen()
                ? messages.format("who.dtr-frozen-suffix", "temps",
                        DurationFormat.compact(plugin.getDtrManager().getFreezeRemaining(faction)))
                : (faction.isRaidable() ? messages.format("who.raidable") : "");
        messages.sendRaw(sender, "who.dtr",
                "couleur", dtrColor,
                "dtr", String.format(Locale.US, "%.2f", faction.getDtr()),
                "max", String.format(Locale.US, "%.2f", faction.getMaxDtr()),
                "gel", freezeSuffix);

        Location home = faction.getHome();
        if (home != null) {
            messages.sendRaw(sender, "who.home", "coords",
                    home.getBlockX() + ", " + home.getBlockY() + ", " + home.getBlockZ());
        } else {
            messages.sendRaw(sender, "who.home-none");
        }
        messages.sendRaw(sender, "who.points", "points", faction.getPoints());
        sender.sendMessage(Text.color("&eBanque : &a" + plugin.getEconomy().format(plugin.getEconomy().getBalance(faction.getId()))));
        if (!faction.getAllies().isEmpty()) {
            String allies = faction.getAllies().stream()
                    .map(id -> {
                        Faction ally = factions.getFaction(id);
                        return ally != null ? ally.getName() : "?";
                    })
                    .collect(Collectors.joining(", "));
            messages.sendRaw(sender, "who.allies", "liste", allies);
        }
        if (faction.getFocusTarget() != null) {
            Faction focus = factions.getFaction(faction.getFocusTarget());
            if (focus != null) {
                messages.sendRaw(sender, "who.focus", "faction", focus.getName());
            }
        }
        messages.sendRaw(sender, "who.created", "date",
                new SimpleDateFormat("dd/MM/yyyy").format(new Date(faction.getCreatedAt())));
    }

    private void list(CommandSender sender, String[] args) {
        List<FactionImpl> sorted = factions.getPlayerFactions().stream()
                .map(f -> (FactionImpl) f)
                .sorted(Comparator.comparingInt(FactionImpl::getOnlineCount).reversed())
                .toList();
        if (sorted.isEmpty()) {
            messages.send(sender, "list.empty");
            return;
        }
        int perPage = 10;
        int pages = (sorted.size() + perPage - 1) / perPage;
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Math.max(1, Math.min(pages, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
            }
        }
        messages.sendRaw(sender, "list.header", "page", page, "pages", pages);
        for (int i = (page - 1) * perPage; i < Math.min(sorted.size(), page * perPage); i++) {
            FactionImpl faction = sorted.get(i);
            String dtrColor = faction.isRaidable() ? "&c"
                    : faction.getDtr() >= faction.getMaxDtr() * 0.5D ? "&a" : "&e";
            messages.sendRaw(sender, "list.entry",
                    "rang", i + 1,
                    "faction", faction.getName(),
                    "online", faction.getOnlineCount(),
                    "total", faction.getMemberCount(),
                    "dtr", dtrColor + String.format(Locale.US, "%.1f", faction.getDtr()));
        }
    }

    // ------------------------------------------------------------------
    // Relations / focus
    // ------------------------------------------------------------------

    private void ally(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null || args.length < 2) {
            return;
        }
        FactionImpl faction = factionOf(player);
        if (faction == null || !requireRole(player, faction, FactionRole.OFFICER)) {
            return;
        }
        FactionImpl target = resolveFaction(args[1]);
        if (target == null || target.isSystemFaction()) {
            messages.send(sender, "general.unknown-faction", "faction", args[1]);
            return;
        }
        if (target.getId().equals(faction.getId())) {
            messages.send(sender, "ally.self");
            return;
        }
        if (faction.getAllies().contains(target.getId())) {
            messages.send(sender, "ally.already");
            return;
        }
        int maxAllies = plugin.getFactionsConfig().getInt("faction.max-allies", 1);
        if (faction.getAllies().size() >= maxAllies) {
            messages.send(sender, "ally.max", "max", maxAllies);
            return;
        }
        if (factions.hasAllyRequest(target, faction)) {
            if (target.getAllies().size() >= maxAllies) {
                messages.send(sender, "ally.max", "max", maxAllies);
                return;
            }
            factions.addAlly(faction, target);
            for (Player online : faction.getOnlinePlayers()) {
                messages.send(online, "ally.added", "faction", target.getName());
            }
            for (Player online : target.getOnlinePlayers()) {
                messages.send(online, "ally.added", "faction", faction.getName());
            }
        } else {
            factions.addAllyRequest(faction, target);
            messages.send(player, "ally.request-sent", "faction", target.getName());
            for (Player online : target.getOnlinePlayers()) {
                messages.send(online, "ally.request-received", "faction", faction.getName());
            }
        }
    }

    private void unally(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null || args.length < 2) {
            return;
        }
        FactionImpl faction = factionOf(player);
        if (faction == null || !requireRole(player, faction, FactionRole.OFFICER)) {
            return;
        }
        FactionImpl target = resolveFaction(args[1]);
        if (target == null) {
            messages.send(sender, "general.unknown-faction", "faction", args[1]);
            return;
        }
        if (!faction.getAllies().contains(target.getId())) {
            messages.send(sender, "ally.not-ally");
            return;
        }
        factions.removeAlly(faction, target);
        for (Player online : faction.getOnlinePlayers()) {
            messages.send(online, "ally.removed", "faction", target.getName());
        }
        for (Player online : target.getOnlinePlayers()) {
            messages.send(online, "ally.removed", "faction", faction.getName());
        }
    }

    private void focus(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null || args.length < 2) {
            return;
        }
        if (!plugin.getFactionsConfig().getBoolean("focus.enabled", true)) {
            messages.send(sender, "focus.disabled");
            return;
        }
        FactionImpl faction = factionOf(player);
        if (faction == null) {
            return;
        }
        FactionImpl target = resolveFaction(args[1]);
        if (target == null || target.isSystemFaction() || target.getId().equals(faction.getId())) {
            messages.send(sender, "general.unknown-faction", "faction", args[1]);
            return;
        }
        factions.setFocus(faction, target.getId());
        for (Player online : faction.getOnlinePlayers()) {
            messages.send(online, "focus.set", "faction", target.getName());
        }
    }

    private void unfocus(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        FactionImpl faction = factionOf(player);
        if (faction == null) {
            return;
        }
        if (faction.getFocusTarget() == null) {
            messages.send(sender, "focus.none");
            return;
        }
        factions.setFocus(faction, null);
        messages.send(player, "focus.removed");
    }

    private void rename(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null || args.length < 2) {
            return;
        }
        FactionImpl faction = factionOf(player);
        if (faction == null || !hasRole(player, faction, FactionRole.LEADER)) {
            messages.send(sender, "general.insufficient-rank");
            return;
        }
        String name = args[1];
        int min = plugin.getFactionsConfig().getInt("faction.min-name-length", 3);
        int max = plugin.getFactionsConfig().getInt("faction.max-name-length", 16);
        if (name.length() < min || name.length() > max) {
            messages.send(sender, "faction.name-length", "min", min, "max", max);
            return;
        }
        String pattern = plugin.getFactionsConfig().getString("faction.name-pattern", "[a-zA-Z0-9_]+");
        if (!Pattern.matches(pattern, name)) {
            messages.send(sender, "faction.name-invalid");
            return;
        }
        if (factions.getFactionByName(name) != null) {
            messages.send(sender, "faction.name-taken");
            return;
        }
        factions.rename(faction, name);
        messages.send(player, "faction.renamed", "faction", name);
    }

    // ------------------------------------------------------------------
    // Administration
    // ------------------------------------------------------------------

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("orbithcf.admin")) {
            messages.send(sender, "general.no-permission");
            return false;
        }
        return true;
    }

    private void adminSetDtr(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length < 3) {
            return;
        }
        FactionImpl faction = resolveFaction(args[1]);
        if (faction == null) {
            messages.send(sender, "general.unknown-faction", "faction", args[1]);
            return;
        }
        double value;
        try {
            value = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            messages.send(sender, "general.invalid-number", "valeur", args[2]);
            return;
        }
        plugin.getDtrManager().setDtr(faction, value, DtrChangeReason.ADMIN);
        messages.send(sender, "admin.dtr-set", "faction", faction.getName(),
                "dtr", String.format(Locale.US, "%.2f", faction.getDtr()));
    }

    private void adminFreeze(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length < 3) {
            return;
        }
        FactionImpl faction = resolveFaction(args[1]);
        if (faction == null) {
            messages.send(sender, "general.unknown-faction", "faction", args[1]);
            return;
        }
        Duration duration = DurationFormat.parse(args[2]);
        if (duration.isZero()) {
            messages.send(sender, "general.invalid-duration", "valeur", args[2]);
            return;
        }
        plugin.getDtrManager().freeze(faction, duration);
        messages.send(sender, "admin.frozen", "faction", faction.getName(),
                "temps", DurationFormat.compact(duration));
    }

    private void adminUnfreeze(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length < 2) {
            return;
        }
        FactionImpl faction = resolveFaction(args[1]);
        if (faction == null) {
            messages.send(sender, "general.unknown-faction", "faction", args[1]);
            return;
        }
        plugin.getDtrManager().unfreeze(faction);
        messages.send(sender, "admin.unfrozen", "faction", faction.getName());
    }

    private void adminPoints(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length < 3) {
            return;
        }
        FactionImpl faction = resolveFaction(args[1]);
        if (faction == null) {
            messages.send(sender, "general.unknown-faction", "faction", args[1]);
            return;
        }
        int points;
        try {
            points = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            messages.send(sender, "general.invalid-number", "valeur", args[2]);
            return;
        }
        factions.addPoints(faction, points);
        messages.send(sender, "admin.points-added", "faction", faction.getName(), "points", points);
    }

    private void adminBypass(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null || !requireAdmin(sender)) {
            return;
        }
        boolean bypassing = plugin.toggleBypass(player.getUniqueId());
        messages.send(player, bypassing ? "admin.bypass-on" : "admin.bypass-off");
        player.sendMessage(Text.color(bypassing
                ? "&6Mode staff ACTIF : &epas de deathban, commandes du reseau, mode de jeu libre, construction partout."
                : "&aMode staff coupe : &etu joues comme un joueur (deathban, survie, pas de /spawn)."));
    }

    // ------------------------------------------------------------------
    // Aides internes
    // ------------------------------------------------------------------

    private Player asPlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        messages.send(sender, "general.player-only");
        return null;
    }

    private FactionImpl factionOf(Player player) {
        FactionImpl faction = factions.getImplByPlayer(player.getUniqueId());
        if (faction == null) {
            messages.send(player, "general.not-in-faction");
        }
        return faction;
    }

    private boolean hasRole(Player player, FactionImpl faction, FactionRole role) {
        FactionRole current = faction.getRole(player.getUniqueId());
        return current != null && current.isAtLeast(role);
    }

    private boolean requireRole(Player player, FactionImpl faction, FactionRole role) {
        if (!hasRole(player, faction, role)) {
            messages.send(player, "general.insufficient-rank");
            return false;
        }
        return true;
    }

    /** Resout une faction par nom de faction ou nom de joueur en ligne. */
    private FactionImpl resolveFaction(String input) {
        FactionImpl byName = factions.getImplByName(input);
        if (byName != null) {
            return byName;
        }
        Player player = Bukkit.getPlayerExact(input);
        if (player != null) {
            return factions.getImplByPlayer(player.getUniqueId());
        }
        return null;
    }

    /** Trouve un membre de la faction par nom (en ligne ou non). */
    private UUID findMember(FactionImpl faction, String name) {
        for (UUID member : faction.getMemberIds()) {
            if (plugin.getProfileManager().getNameOf(member).equalsIgnoreCase(name)) {
                return member;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Tab completion
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String start = args[0].toLowerCase(Locale.ROOT);
            SUBCOMMANDS.stream().filter(s -> s.startsWith(start)).forEach(result::add);
            if (sender.hasPermission("orbithcf.admin")) {
                ADMIN_SUBCOMMANDS.stream().filter(s -> s.startsWith(start)).forEach(result::add);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String start = args[1].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "invite", "uninvite", "kick", "promote", "demote", "leader" ->
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(start))
                                .forEach(result::add);
                case "join", "who", "show", "info", "ally", "unally", "focus",
                        "setdtr", "freeze", "unfreeze", "points", "disband" ->
                        factions.getPlayerFactions().stream()
                                .map(Faction::getName)
                                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(start))
                                .forEach(result::add);
                default -> {
                }
            }
        }
        return result;
    }
}
