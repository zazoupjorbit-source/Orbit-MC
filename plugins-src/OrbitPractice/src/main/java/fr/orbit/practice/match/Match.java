package fr.orbit.practice.match;

import fr.orbit.practice.Ladder;
import fr.orbit.practice.Mode;
import fr.orbit.practice.arena.Arena;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** État vivant d'un combat : équipes, score des manches, joueurs encore debout. */
public final class Match {

    public enum State { COUNTDOWN, LIVE, ENDED }

    private final Ladder ladder;
    private final Mode mode;
    private final boolean ranked;
    private final int roundsToWin;
    private final List<UUID> teamA;
    private final List<UUID> teamB;
    private final Arena arena;

    private int scoreA;
    private int scoreB;
    private State state = State.COUNTDOWN;
    private final Set<UUID> aliveA = new HashSet<>();
    private final Set<UUID> aliveB = new HashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    /** BuildUHC : blocs posés par les joueurs, retirés à chaque manche. */
    private final Set<org.bukkit.Location> placed = new HashSet<>();
    /** Boxing : coups portés dans la manche. */
    private final java.util.Map<UUID, Integer> hits = new java.util.HashMap<>();

    public Match(Ladder ladder, Mode mode, boolean ranked, int roundsToWin,
                 List<UUID> teamA, List<UUID> teamB, Arena arena) {
        this.ladder = ladder;
        this.mode = mode;
        this.ranked = ranked;
        this.roundsToWin = Math.max(1, roundsToWin);
        this.teamA = new ArrayList<>(teamA);
        this.teamB = new ArrayList<>(teamB);
        this.arena = arena;
    }

    public Ladder ladder() { return ladder; }
    public Mode mode() { return mode; }
    public boolean ranked() { return ranked; }
    public int roundsToWin() { return roundsToWin; }
    public Arena arena() { return arena; }
    public State state() { return state; }
    public void setState(State s) { this.state = s; }
    public int scoreA() { return scoreA; }
    public int scoreB() { return scoreB; }
    public List<UUID> teamA() { return teamA; }
    public List<UUID> teamB() { return teamB; }
    public Set<UUID> spectators() { return spectators; }
    public Set<org.bukkit.Location> placed() { return placed; }
    public java.util.Map<UUID, Integer> hits() { return hits; }

    public List<UUID> allPlayers() {
        List<UUID> all = new ArrayList<>(teamA);
        all.addAll(teamB);
        return all;
    }

    public boolean isTeamA(UUID uuid) {
        return teamA.contains(uuid);
    }

    public List<UUID> teamOf(UUID uuid) {
        return teamA.contains(uuid) ? teamA : teamB;
    }

    public List<UUID> enemiesOf(UUID uuid) {
        return teamA.contains(uuid) ? teamB : teamA;
    }

    public boolean sameTeam(UUID a, UUID b) {
        return teamOf(a) == teamOf(b);
    }

    public boolean isAlive(UUID uuid) {
        return aliveA.contains(uuid) || aliveB.contains(uuid);
    }

    public void resetRoundAlive() {
        hits.clear();
        aliveA.clear();
        aliveA.addAll(teamA);
        aliveB.clear();
        aliveB.addAll(teamB);
    }

    /** Élimine un joueur ; renvoie true si toute son équipe est maintenant à terre. */
    public boolean eliminate(UUID uuid) {
        Set<UUID> alive = teamA.contains(uuid) ? aliveA : aliveB;
        alive.remove(uuid);
        return alive.isEmpty();
    }

    public void scoreRoundFor(boolean teamA) {
        if (teamA) {
            scoreA++;
        } else {
            scoreB++;
        }
    }

    public boolean isOver() {
        return scoreA >= roundsToWin || scoreB >= roundsToWin;
    }

    public boolean teamAWon() {
        return scoreA >= roundsToWin;
    }
}
