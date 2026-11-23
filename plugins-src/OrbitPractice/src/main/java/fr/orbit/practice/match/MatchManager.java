package fr.orbit.practice.match;

import fr.orbit.practice.EloStore;
import fr.orbit.practice.Ladder;
import fr.orbit.practice.Lobby;
import fr.orbit.practice.Mode;
import fr.orbit.practice.Msg;
import fr.orbit.practice.arena.Arena;
import fr.orbit.practice.arena.ArenaStore;
import fr.orbit.practice.kit.Kits;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
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

/** Déroulé des combats : départ, manches, éliminations sans vraie mort, fin, Elo, spectateurs. */
public final class MatchManager {

    private static final double VANILLA_ATTACK_SPEED = 4.0;

    private final Plugin plugin;
    private final ArenaStore arenas;
    private final EloStore elo;
    private final Kits kits;
    private final Lobby lobby;

    private final Map<UUID, Match> playerMatch = new ConcurrentHashMap<>();
    private final Map<UUID, Match> spectatorMatch = new ConcurrentHashMap<>();
    private final Set<String> arenasInUse = ConcurrentHashMap.newKeySet();

    public MatchManager(Plugin plugin, ArenaStore arenas, EloStore elo, Kits kits, Lobby lobby) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.elo = elo;
        this.kits = kits;
        this.lobby = lobby;
    }

    public Match matchOf(UUID uuid) {
        return playerMatch.get(uuid);
    }

    public boolean isBusy(UUID uuid) {
        return playerMatch.containsKey(uuid) || spectatorMatch.containsKey(uuid);
    }

    public boolean isSpectating(UUID uuid) {
        return spectatorMatch.containsKey(uuid);
    }

    /** Nombre de joueurs en combat dans ce type et ce format (affiché dans le menu). */
    public int fighting(Ladder ladder, Mode mode) {
        int n = 0;
        for (Match m : playerMatch.values()) {
            if (m.ladder() == ladder && m.mode() == mode) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ Départ

    /** Démarre un combat. Renvoie false si aucune arène n'est libre (les joueurs sont prévenus). */
    public boolean start(Ladder ladder, Mode mode, boolean ranked, List<UUID> teamA, List<UUID> teamB) {
        Arena arena = arenas.randomFree(arenasInUse, ladder.rule == Ladder.Rule.SUMO);
        if (arena == null) {
            return false;
        }
        arenasInUse.add(arena.name().toLowerCase());
        Match match = new Match(ladder, mode, ranked, plugin.getConfig().getInt("combat.manches-gagnantes", 2),
                teamA, teamB, arena);
        boolean fast = plugin.getConfig().getBoolean("combat.sans-delai-attaque", true);
        for (UUID u : match.allPlayers()) {
            playerMatch.put(u, match);
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.closeInventory();
                if (fast) {
                    attackSpeed(p, 1024.0);
                }
            }
        }
        announce(match, teamA, teamB);
        announce(match, teamB, teamA);
        startRound(match);
        return true;
    }

    private void announce(Match match, List<UUID> side, List<UUID> other) {
        String opponents = other.stream()
                .map(u -> "<white>" + name(u) + (match.ranked()
                        ? " <dark_gray>(" + Msg.OR + elo.elo(u, match.ladder()) + "<dark_gray>)" : ""))
                .collect(Collectors.joining("<gray>, "));
        for (UUID u : side) {
            tell(u, Msg.BLEU + match.ladder().label + " " + match.mode().label + " <dark_gray>· <gray>contre "
                    + opponents + " <dark_gray>· <gray>arène <white>" + match.arena().name()
                    + (match.ranked() ? "" : " <dark_gray>· <gray>sans Elo"));
        }
    }

    // ------------------------------------------------------------------ Manches

    private void startRound(Match match) {
        if (match.state() == Match.State.ENDED) {
            return;
        }
        match.setState(Match.State.COUNTDOWN);
        match.resetRoundAlive();
        clean(match);
        placeTeam(match.teamA(), match.arena(), true);
        placeTeam(match.teamB(), match.arena(), false);

        int round = match.scoreA() + match.scoreB() + 1;
        boolean pickClass = round == 1 && match.ladder() == Ladder.HCF;
        for (Player p : online(match.allPlayers())) {
            p.getInventory().clear();
            p.setCooldown(Material.ENDER_PEARL, 0);
            String subtitle = pickClass ? "<gray>Choisis ta classe"
                    : match.roundsToWin() > 1 ? "<white>" + match.scoreA() + " <dark_gray>- <white>" + match.scoreB() : "";
            p.showTitle(Title.title(
                    Msg.raw(match.roundsToWin() > 1 ? Msg.BLEU + "<bold>MANCHE " + round : Msg.BLEU + "<bold>" + match.ladder().label),
                    Msg.raw(subtitle),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1200), Duration.ofMillis(300))));
            if (pickClass) {
                kits.openClassMenu(p);
            }
        }
        countdown(match, pickClass ? 8 : 4);
    }

    private void countdown(Match match, int n) {
        if (match.state() != Match.State.COUNTDOWN) {
            return;
        }
        if (n <= 0) {
            match.setState(Match.State.LIVE);
            for (Player p : online(match.allPlayers())) {
                kits.closeClassMenu(p);
                kits.equip(p, match.ladder());
                p.showTitle(Title.title(Msg.raw(Msg.OR + "<bold>GO !"), Msg.raw(""),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(300))));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
            }
            return;
        }
        for (Player p : online(match.allPlayers())) {
            p.sendActionBar(Msg.raw(Msg.OR + "<bold>" + n));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> countdown(match, n - 1), 20L);
    }

    // ------------------------------------------------------------------ Élimination

    /** Un combattant tombe (le coup mortel a été annulé en amont : pas de vraie mort, rien au sol). */
    public void eliminate(Player player) {
        UUID id = player.getUniqueId();
        Match match = playerMatch.get(id);
        if (match == null || match.state() != Match.State.LIVE || !match.isAlive(id)) {
            return;
        }
        heal(player);
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        Location center = match.arena().center();
        if (center != null) {
            player.teleport(center);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.4f);
        for (UUID u : match.allPlayers()) {
            tell(u, "<white>" + player.getName() + " <gray>est à terre.");
        }
        if (match.eliminate(id)) {
            roundOver(match, !match.isTeamA(id));
        }
    }

    private void roundOver(Match match, boolean teamAWon) {
        match.setState(Match.State.COUNTDOWN);
        match.scoreRoundFor(teamAWon);
        // Les survivants ne peuvent plus rien perdre : soignés tout de suite.
        online(match.allPlayers()).forEach(this::heal);
        if (match.isOver()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> endMatch(match, false, null), 40L);
            return;
        }
        String winners = (teamAWon ? match.teamA() : match.teamB()).stream().map(this::name).collect(Collectors.joining(", "));
        for (UUID u : match.allPlayers()) {
            tell(u, "Manche pour <white>" + winners + " <dark_gray>· <white>" + match.scoreA()
                    + " <dark_gray>- <white>" + match.scoreB());
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> startRound(match), 60L);
    }

    // ------------------------------------------------------------------ Fin

    /** Un joueur quitte le serveur en plein combat : son équipe perd par forfait. */
    public void forfeit(UUID quitter) {
        Match match = playerMatch.get(quitter);
        if (match != null && match.state() != Match.State.ENDED) {
            endMatch(match, true, quitter);
        }
    }

    private void endMatch(Match match, boolean forfeit, UUID quitter) {
        if (match.state() == Match.State.ENDED) {
            return;
        }
        match.setState(Match.State.ENDED);
        boolean teamAWon = forfeit ? !match.isTeamA(quitter) : match.teamAWon();
        List<UUID> winners = teamAWon ? match.teamA() : match.teamB();
        List<UUID> losers = teamAWon ? match.teamB() : match.teamA();
        int wScore = teamAWon ? match.scoreA() : match.scoreB();
        int lScore = teamAWon ? match.scoreB() : match.scoreA();
        String score = wScore + "-" + lScore;

        Map<UUID, String> names = new HashMap<>();
        match.allPlayers().forEach(u -> names.put(u, name(u)));
        int delta = match.ranked() ? elo.applyMatch(match.ladder(), winners, losers, names) : 0;
        elo.recordMatch(match.ladder(), match.mode(), match.ranked(),
                winners.stream().map(names::get).collect(Collectors.joining(",")),
                losers.stream().map(names::get).collect(Collectors.joining(",")),
                score, forfeit, match.arena().name());

        for (UUID u : winners) {
            tell(u, "<green><bold>VICTOIRE</bold> <gray>" + (forfeit ? "par forfait" : score)
                    + (match.ranked() ? " <dark_gray>· <gray>Elo <white>" + elo.elo(u, match.ladder())
                    + " <green>(+" + delta + ")" : ""));
        }
        for (UUID u : losers) {
            tell(u, "<red><bold>DÉFAITE</bold> <gray>" + (forfeit ? "par forfait" : lScore + "-" + wScore)
                    + (match.ranked() ? " <dark_gray>· <gray>Elo <white>" + elo.elo(u, match.ladder())
                    + " <red>(-" + delta + ")" : ""));
        }
        release(match);
    }

    /** Renvoie tout le monde au hub et libère l'arène. */
    private void release(Match match) {
        for (UUID s : new ArrayList<>(match.spectators())) {
            stopSpectating(s);
        }
        for (UUID u : match.allPlayers()) {
            playerMatch.remove(u);
            kits.clearChoice(u);
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                kits.closeClassMenu(p);
                attackSpeed(p, VANILLA_ATTACK_SPEED);
                p.setMaximumNoDamageTicks(20);
                lobby.send(p);
            }
        }
        clean(match);
        arenasInUse.remove(match.arena().name().toLowerCase());
    }

    // ------------------------------------------------------------------ Spectateurs

    public boolean spectate(Player spectator, UUID target) {
        Match match = playerMatch.get(target);
        if (match == null || match.state() == Match.State.ENDED) {
            return false;
        }
        spectatorMatch.put(spectator.getUniqueId(), match);
        match.spectators().add(spectator.getUniqueId());
        spectator.getInventory().clear();
        spectator.setGameMode(GameMode.SPECTATOR);
        Location center = match.arena().center();
        if (center != null) {
            spectator.teleport(center);
        }
        spectator.sendMessage(Msg.msg("Tu observes le combat. <dark_gray>/spec <gray>pour revenir au hub."));
        return true;
    }

    public void stopSpectating(UUID uuid) {
        Match match = spectatorMatch.remove(uuid);
        if (match != null) {
            match.spectators().remove(uuid);
        }
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            lobby.send(p);
        }
    }

    // ------------------------------------------------------------------ Outils

    private void placeTeam(List<UUID> team, Arena arena, boolean sideA) {
        for (int i = 0; i < team.size(); i++) {
            Player p = Bukkit.getPlayer(team.get(i));
            Location spawn = sideA ? arena.spawnA(i) : arena.spawnB(i);
            if (p != null && spawn != null) {
                p.teleport(spawn);
                heal(p);
                p.setGameMode(GameMode.ADVENTURE);
            }
        }
    }

    /** Retire ce qui traîne dans l'arène : flèches, perles en vol, potions, objets au sol. */
    private void clean(Match match) {
        // BuildUHC : on retire tout ce qui a été posé (blocs, eau, lave), le décor reste intact.
        for (Location loc : match.placed()) {
            loc.getBlock().setType(Material.AIR, false);
        }
        match.placed().clear();
        Location center = match.arena().center();
        if (center == null) {
            return;
        }
        double r = match.arena().radius() + 6;
        for (Entity e : center.getWorld().getNearbyEntities(center, r, 40, r)) {
            if (e instanceof AbstractArrow || e instanceof EnderPearl || e instanceof ThrownPotion
                    || e instanceof Item || e instanceof AreaEffectCloud) {
                e.remove();
            }
        }
    }

    // ------------------------------------------------------------------ Règles des kits

    /** BuildUHC : le joueur peut-il poser un bloc ici (dans son arène, en plein combat) ? */
    public boolean canBuild(Player p, org.bukkit.block.Block block) {
        Match match = playerMatch.get(p.getUniqueId());
        if (match == null || match.ladder().rule != Ladder.Rule.BUILD || match.state() != Match.State.LIVE
                || !match.isAlive(p.getUniqueId())) {
            return false;
        }
        Arena a = match.arena();
        double cx = (a.ax() + a.bx()) / 2, cz = (a.az() + a.bz()) / 2;
        double dx = block.getX() + 0.5 - cx, dz = block.getZ() + 0.5 - cz;
        return block.getWorld().getName().equals(a.world())
                && Math.sqrt(dx * dx + dz * dz) < a.radius() - 1
                && block.getY() >= a.ay() && block.getY() < a.ay() + 20;
    }

    public void recordPlaced(Player p, org.bukkit.block.Block block) {
        Match match = playerMatch.get(p.getUniqueId());
        if (match != null) {
            match.placed().add(block.getLocation());
        }
    }

    /** BuildUHC : seuls les blocs posés pendant le combat se cassent (ou se reprennent au seau). */
    public boolean canBreak(Player p, org.bukkit.block.Block block) {
        Match match = playerMatch.get(p.getUniqueId());
        return match != null && match.state() == Match.State.LIVE && match.placed().contains(block.getLocation());
    }

    /** Boxing : compte un coup ; à 100, l’adversaire est éliminé. */
    public void boxingHit(Match match, Player attacker, Player victim) {
        int n = match.hits().merge(attacker.getUniqueId(), 1, Integer::sum);
        int other = match.hits().getOrDefault(victim.getUniqueId(), 0);
        int goal = plugin.getConfig().getInt("kits.boxing-coups", 100);
        attacker.sendActionBar(Msg.raw(Msg.OR + "<bold>" + n + "</bold> <gray>/ " + goal + " coups <dark_gray>· <gray>"
                + victim.getName() + " <white>" + other));
        victim.sendActionBar(Msg.raw("<white>" + other + " <gray>/ " + goal + " coups <dark_gray>· <gray>"
                + attacker.getName() + " " + Msg.OR + "<bold>" + n));
        if (n >= goal) {
            eliminate(victim);
        }
    }

    /** Sumo : un joueur tombé de la plateforme est éliminé. */
    public void checkSumoFall(Player p) {
        Match match = playerMatch.get(p.getUniqueId());
        if (match != null && match.ladder().rule == Ladder.Rule.SUMO && match.state() == Match.State.LIVE
                && match.isAlive(p.getUniqueId()) && p.getLocation().getY() < match.arena().ay() - 2.5) {
            eliminate(p);
        }
    }

    public void heal(Player p) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max != null ? max.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setAbsorptionAmount(0);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
    }

    private static void attackSpeed(Player p, double value) {
        AttributeInstance speed = p.getAttribute(Attribute.ATTACK_SPEED);
        if (speed != null) {
            // Vieux combat : jamais de recharge, même au hub et après un combat.
            speed.setBaseValue(fr.orbit.practice.VieuxCombat.actif() ? fr.orbit.practice.VieuxCombat.VITESSE_LIBRE : value);
        }
    }

    /** Remet la vitesse d'attaque normale (joueur revenu après une coupure en plein combat). */
    public void resetAttackSpeed(Player p) {
        attackSpeed(p, VANILLA_ATTACK_SPEED);
    }

    private List<Player> online(List<UUID> ids) {
        List<Player> players = new ArrayList<>();
        for (UUID u : ids) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                players.add(p);
            }
        }
        return players;
    }

    private String name(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            return p.getName();
        }
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : "?";
    }

    private void tell(UUID uuid, String mini) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            p.sendMessage(Msg.msg(mini));
        }
    }

    /** Arrêt du plugin : tous les combats s'arrêtent sans résultat, tout le monde revient au hub. */
    public void shutdown() {
        for (Match match : new ArrayList<>(playerMatch.values())) {
            if (match.state() != Match.State.ENDED) {
                match.setState(Match.State.ENDED);
                release(match);
            }
        }
        for (UUID s : new ArrayList<>(spectatorMatch.keySet())) {
            stopSpectating(s);
        }
    }
}
