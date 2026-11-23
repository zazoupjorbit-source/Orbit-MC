package fr.orbitversus.match;

import fr.orbitversus.EconomyHook;
import fr.orbitversus.Msg;
import fr.orbitversus.arena.Arena;
import fr.orbitversus.arena.ArenaManager;
import fr.orbitversus.elo.EloStore;
import fr.orbitversus.history.HistoryStore;
import fr.orbitversus.kit.KitManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Orchestration des combats : démarrage, manches Bo3, éliminations, fin, récompenses, spectate. */
public final class MatchManager {

    private final Plugin plugin;
    private final ArenaManager arenas;
    private final EloStore elo;
    private final HistoryStore history;
    private final EconomyHook economy;
    private final KitManager kits;

    /** Inventaire du joueur avant le combat, restaure a la fin. */
    private final Map<UUID, ItemStack[]> savedInv = new ConcurrentHashMap<>();

    private final Map<UUID, Match> playerMatch = new ConcurrentHashMap<>();
    private final Map<UUID, Match> spectatorMatch = new ConcurrentHashMap<>();
    private final Map<UUID, Location> spectatorReturn = new ConcurrentHashMap<>();
    private final Set<String> arenasInUse = ConcurrentHashMap.newKeySet();

    public MatchManager(Plugin plugin, ArenaManager arenas, EloStore elo, HistoryStore history, EconomyHook economy, KitManager kits) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.elo = elo;
        this.history = history;
        this.economy = economy;
        this.kits = kits;
    }

    public Set<String> arenasInUse() {
        return arenasInUse;
    }

    public Match matchOf(UUID uuid) {
        return playerMatch.get(uuid);
    }

    public boolean isBusy(UUID uuid) {
        return playerMatch.containsKey(uuid) || spectatorMatch.containsKey(uuid);
    }

    // ===================== Démarrage =====================

    /** Démarre un combat. Renvoie false si aucune arène libre (les joueurs sont prévenus). */
    public boolean start(VersusMode mode, List<UUID> teamA, List<UUID> teamB, long stake) {
        Arena arena = arenas.randomFree(arenasInUse);
        if (arena == null) {
            message(teamA, "<red>Aucune arène libre pour le moment. Réessaie bientôt.");
            message(teamB, "<red>Aucune arène libre pour le moment. Réessaie bientôt.");
            return false;
        }
        arenasInUse.add(arena.name().toLowerCase());

        long matchedStake = economy.available() ? stake : 0;
        Match match = new Match(mode, teamA, teamB, arena, matchedStake, System.currentTimeMillis());
        for (UUID u : match.allPlayers()) {
            playerMatch.put(u, match);
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                match.rememberReturn(u, p.getLocation());
                savedInv.put(u, p.getInventory().getContents().clone()); // pour restaurer le kit lobby a la fin
            }
        }

        // Paris : on retire la mise de chaque joueur (le vainqueur récupère le pot à la fin).
        if (matchedStake > 0) {
            for (UUID u : match.allPlayers()) {
                economy.withdraw(Bukkit.getOfflinePlayer(u), matchedStake);
            }
        }

        announceOpponents(match, teamA, teamB);
        announceOpponents(match, teamB, teamA);
        startRound(match);
        return true;
    }

    private void announceOpponents(Match match, List<UUID> side, List<UUID> other) {
        String opp = other.stream().map(this::nameElo).collect(Collectors.joining(", "));
        String stakeInfo = match.stake() > 0 ? " <dark_gray>· <gray>mise <#FF3B3B>" + match.stake() : "";
        message(side, "<gray>Adversaire(s) : <white>" + opp + " <dark_gray>· <gray>arène <white>"
                + match.arena().name() + stakeInfo);
    }

    // ===================== Manches =====================

    private void startRound(Match match) {
        match.setState(Match.State.COUNTDOWN);
        match.resetRoundAlive();
        teleportTeam(match.teamA(), match.arena().spawnA());
        teleportTeam(match.teamB(), match.arena().spawnB());

        int round = match.scoreA() + match.scoreB() + 1;
        boolean first = round == 1;
        for (UUID u : match.allPlayers()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.showTitle(Title.title(
                        Msg.raw("<gradient:#E8650A:#FF3B3B><bold>MANCHE " + round + "</bold></gradient>"),
                        first ? Msg.raw("<gray>Choisis ton kit !") : Msg.raw("<gray>" + match.scoreA() + " - " + match.scoreB()),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1200), Duration.ofMillis(300))));
                if (first) {
                    kits.openMenu(p); // phase de selection du kit pendant le compte a rebours
                }
            }
        }
        // Compte a rebours : 7s au 1er round (selection du kit), 3s ensuite. Puis GO.
        scheduleCountdown(match, first ? 7 : 3);
    }

    private void scheduleCountdown(Match match, int n) {
        if (match.state() == Match.State.ENDED) {
            return;
        }
        if (n <= 0) {
            match.setState(Match.State.LIVE);
            for (UUID u : match.allPlayers()) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    kits.applyChosen(p); // ferme le menu + equipe le kit choisi (ou par defaut)
                    p.sendTitle("§6§lGO !", "", 0, 20, 10);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
                }
            }
            return;
        }
        for (UUID u : match.allPlayers()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.sendActionBar(Msg.raw("<#FF3B3B><bold>" + n + "</bold>"));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> scheduleCountdown(match, n - 1), 20L);
    }

    // ===================== Élimination / fin de manche =====================

    /** Un participant est éliminé (dégâts létaux annulés en amont). */
    public void eliminate(Player player) {
        Match match = playerMatch.get(player.getUniqueId());
        if (match == null || match.state() != Match.State.LIVE) {
            return;
        }
        heal(player);
        player.setGameMode(GameMode.SPECTATOR);
        if (match.arena().center() != null) {
            player.teleport(match.arena().center());
        }
        boolean teamWiped = match.eliminate(player.getUniqueId());
        if (teamWiped) {
            boolean teamAWon = !match.isTeamA(player.getUniqueId());
            roundOver(match, teamAWon);
        }
    }

    private void roundOver(Match match, boolean teamAWon) {
        match.setState(Match.State.COUNTDOWN);
        match.scoreRoundFor(teamAWon);
        String score = "<white>" + match.scoreA() + " <dark_gray>-</dark_gray> <white>" + match.scoreB();
        for (UUID u : match.allPlayers()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.sendMessage(Msg.msg("<gray>Manche remportée par l'équipe <#E8650A>"
                        + (teamAWon ? "A" : "B") + " <dark_gray>· " + score));
            }
        }
        if (match.isOver()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> endMatch(match), 40L);
        } else {
            // Apres chaque manche : retour immediat des 2 equipes aux spawns de l'arene.
            teleportTeam(match.teamA(), match.arena().spawnA());
            teleportTeam(match.teamB(), match.arena().spawnB());
            Bukkit.getScheduler().runTaskLater(plugin, () -> startRound(match), 60L);
        }
    }

    // ===================== Fin de match =====================

    private void endMatch(Match match) {
        if (match.state() == Match.State.ENDED) {
            return;
        }
        match.setState(Match.State.ENDED);
        boolean teamAWon = match.teamAWon();
        List<UUID> winners = teamAWon ? match.teamA() : match.teamB();
        List<UUID> losers = teamAWon ? match.teamB() : match.teamA();

        elo.applyMatch(winners, losers);

        // Paris : le pot (toutes les mises) est réparti entre les vainqueurs.
        if (match.stake() > 0 && economy.available() && !winners.isEmpty()) {
            long pot = match.stake() * (winners.size() + losers.size());
            long share = pot / winners.size();
            for (UUID u : winners) {
                economy.deposit(Bukkit.getOfflinePlayer(u), share);
            }
        }

        recordAndAnnounce(match, winners, losers, teamAWon);
        restoreAll(match);
        finishCleanup(match);
    }

    private void recordAndAnnounce(Match match, List<UUID> winners, List<UUID> losers, boolean teamAWon) {
        long now = System.currentTimeMillis();
        String mode = match.mode().label;
        int wScore = teamAWon ? match.scoreA() : match.scoreB();
        int lScore = teamAWon ? match.scoreB() : match.scoreA();
        String winnersNames = winners.stream().map(this::name).collect(Collectors.joining(", "));
        String losersNames = losers.stream().map(this::name).collect(Collectors.joining(", "));

        for (UUID u : winners) {
            history.record(u, now, mode, losersNames, true, wScore, lScore);
            tell(u, "<green><bold>VICTOIRE</bold> <gray>" + wScore + "-" + lScore
                    + " <dark_gray>· <gray>Elo <white>" + elo.elo(u)
                    + (match.stake() > 0 && economy.available() ? " <dark_gray>· <#FF3B3B>+gain" : ""));
        }
        for (UUID u : losers) {
            history.record(u, now, mode, winnersNames, false, lScore, wScore);
            tell(u, "<red><bold>DÉFAITE</bold> <gray>" + lScore + "-" + wScore
                    + " <dark_gray>· <gray>Elo <white>" + elo.elo(u));
        }
    }

    /** Un joueur quitte en plein combat : son équipe perd le match (forfait). */
    public void forfeit(UUID quitter) {
        Match match = playerMatch.get(quitter);
        if (match == null || match.state() == Match.State.ENDED) {
            return;
        }
        match.setState(Match.State.ENDED);
        boolean quitterInA = match.isTeamA(quitter);
        List<UUID> winners = quitterInA ? match.teamB() : match.teamA();
        List<UUID> losers = quitterInA ? match.teamA() : match.teamB();

        elo.applyMatch(winners, losers);
        if (match.stake() > 0 && economy.available() && !winners.isEmpty()) {
            long pot = match.stake() * (winners.size() + losers.size());
            long share = pot / winners.size();
            for (UUID u : winners) {
                economy.deposit(Bukkit.getOfflinePlayer(u), share);
            }
        }
        long now = System.currentTimeMillis();
        String w = winners.stream().map(this::name).collect(Collectors.joining(", "));
        String l = losers.stream().map(this::name).collect(Collectors.joining(", "));
        for (UUID u : winners) {
            history.record(u, now, match.mode().label + " (forfait)", l, true, Match.ROUNDS_TO_WIN, 0);
            tell(u, "<green>Ton adversaire a quitté — <bold>victoire par forfait</bold>.");
        }
        for (UUID u : losers) {
            history.record(u, now, match.mode().label + " (forfait)", w, false, 0, Match.ROUNDS_TO_WIN);
        }
        restoreAll(match);
        finishCleanup(match);
    }

    private void restoreAll(Match match) {
        for (UUID u : match.allPlayers()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                heal(p);
                p.setGameMode(GameMode.SURVIVAL);
                ItemStack[] inv = savedInv.get(u); // rendre l'inventaire du lobby (kit practice)
                if (inv != null) {
                    p.getInventory().setContents(inv);
                    p.updateInventory();
                }
                // Fin de combat : TP force des 2 equipes au spawn du monde de l'arene (le practice).
                Location spawn = (match.arena().spawnA() != null && match.arena().spawnA().getWorld() != null)
                        ? match.arena().spawnA().getWorld().getSpawnLocation() : match.returnLoc(u);
                if (spawn != null) {
                    p.teleport(spawn);
                }
            }
            savedInv.remove(u);
            kits.clearChoice(u);
        }
        // Rendre leur état aux spectateurs de ce match.
        for (UUID s : new ArrayList<>(match.spectators())) {
            stopSpectating(s);
        }
    }

    private void finishCleanup(Match match) {
        for (UUID u : match.allPlayers()) {
            playerMatch.remove(u);
        }
        arenasInUse.remove(match.arena().name().toLowerCase());
    }

    // ===================== Spectate =====================

    public boolean spectate(Player spectator, UUID target) {
        Match match = playerMatch.get(target);
        if (match == null) {
            return false;
        }
        spectatorReturn.put(spectator.getUniqueId(), spectator.getLocation());
        spectatorMatch.put(spectator.getUniqueId(), match);
        match.spectators().add(spectator.getUniqueId());
        spectator.setGameMode(GameMode.SPECTATOR);
        Location center = match.arena().center();
        if (center != null) {
            spectator.teleport(center);
        }
        spectator.sendMessage(Msg.msg("<gray>Tu observes le combat. <dark_gray>/spec pour quitter."));
        return true;
    }

    public void stopSpectating(UUID uuid) {
        Match match = spectatorMatch.remove(uuid);
        if (match != null) {
            match.spectators().remove(uuid);
        }
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            p.setGameMode(GameMode.SURVIVAL);
            Location back = spectatorReturn.remove(uuid);
            if (back != null) {
                p.teleport(back);
            }
        } else {
            spectatorReturn.remove(uuid);
        }
    }

    public boolean isSpectating(UUID uuid) {
        return spectatorMatch.containsKey(uuid);
    }

    // ===================== Helpers =====================

    private void teleportTeam(List<UUID> team, Location spawn) {
        if (spawn == null) {
            return;
        }
        for (UUID u : team) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.teleport(spawn);
                heal(p);
                p.setGameMode(GameMode.SURVIVAL);
            }
        }
    }

    private void heal(Player p) {
        var attr = p.getAttribute(Attribute.MAX_HEALTH);
        double max = attr != null ? attr.getValue() : 20.0;
        p.setHealth(max);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
    }

    private String name(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            return p.getName();
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        return off.getName() != null ? off.getName() : "?";
    }

    private String nameElo(UUID uuid) {
        return name(uuid) + " <dark_gray>(<#FF3B3B>" + elo.elo(uuid) + "<dark_gray>)";
    }

    private void message(List<UUID> team, String mini) {
        for (UUID u : team) {
            tell(u, mini);
        }
    }

    private void tell(UUID uuid, String mini) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            p.sendMessage(Msg.msg(mini));
        }
    }

    /** Termine tous les combats (à l'arrêt du plugin) : ramène les joueurs. */
    public void shutdown() {
        for (Match match : new ArrayList<>(playerMatch.values())) {
            if (match.state() != Match.State.ENDED) {
                match.setState(Match.State.ENDED);
                restoreAll(match);
            }
        }
        playerMatch.clear();
        for (UUID s : new ArrayList<>(spectatorMatch.keySet())) {
            stopSpectating(s);
        }
    }
}
