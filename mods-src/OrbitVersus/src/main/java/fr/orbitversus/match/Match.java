package fr.orbitversus.match;

import fr.orbitversus.arena.Arena;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** État vivant d'un combat en Best-of-3 (premier à 2 manches gagne). */
public final class Match {

    public enum State { COUNTDOWN, LIVE, ENDED }

    public static final int ROUNDS_TO_WIN = 2;

    private final VersusMode mode;
    private final List<UUID> teamA;
    private final List<UUID> teamB;
    private final Arena arena;
    private final long stake;                 // mise par joueur (0 = pas de pari)

    private int scoreA;
    private int scoreB;
    private State state = State.COUNTDOWN;
    private final Set<UUID> aliveA = new HashSet<>();
    private final Set<UUID> aliveB = new HashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Map<UUID, Location> returnLoc = new HashMap<>();
    private final long startedAt;

    public Match(VersusMode mode, List<UUID> teamA, List<UUID> teamB, Arena arena, long stake, long now) {
        this.mode = mode;
        this.teamA = new ArrayList<>(teamA);
        this.teamB = new ArrayList<>(teamB);
        this.arena = arena;
        this.stake = stake;
        this.startedAt = now;
    }

    public VersusMode mode() { return mode; }
    public Arena arena() { return arena; }
    public long stake() { return stake; }
    public long startedAt() { return startedAt; }
    public State state() { return state; }
    public void setState(State s) { this.state = s; }
    public int scoreA() { return scoreA; }
    public int scoreB() { return scoreB; }
    public List<UUID> teamA() { return teamA; }
    public List<UUID> teamB() { return teamB; }
    public Set<UUID> spectators() { return spectators; }

    public List<UUID> allPlayers() {
        List<UUID> all = new ArrayList<>(teamA);
        all.addAll(teamB);
        return all;
    }

    public boolean isParticipant(UUID uuid) {
        return teamA.contains(uuid) || teamB.contains(uuid);
    }

    /** true si les deux joueurs sont dans la même équipe. */
    public boolean sameTeam(UUID a, UUID b) {
        return (teamA.contains(a) && teamA.contains(b)) || (teamB.contains(a) && teamB.contains(b));
    }

    public boolean isTeamA(UUID uuid) {
        return teamA.contains(uuid);
    }

    public Set<UUID> aliveOf(UUID uuid) {
        return teamA.contains(uuid) ? aliveA : aliveB;
    }

    public void resetRoundAlive() {
        aliveA.clear();
        aliveA.addAll(teamA);
        aliveB.clear();
        aliveB.addAll(teamB);
    }

    /** Élimine un joueur ; renvoie true si toute son équipe est maintenant éliminée. */
    public boolean eliminate(UUID uuid) {
        aliveOf(uuid).remove(uuid);
        return aliveOf(uuid).isEmpty();
    }

    public void scoreRoundFor(boolean teamA) {
        if (teamA) scoreA++; else scoreB++;
    }

    public boolean isOver() {
        return scoreA >= ROUNDS_TO_WIN || scoreB >= ROUNDS_TO_WIN;
    }

    public boolean teamAWon() {
        return scoreA >= ROUNDS_TO_WIN;
    }

    public void rememberReturn(UUID uuid, Location loc) {
        returnLoc.put(uuid, loc);
    }

    public Location returnLoc(UUID uuid) {
        return returnLoc.get(uuid);
    }
}
