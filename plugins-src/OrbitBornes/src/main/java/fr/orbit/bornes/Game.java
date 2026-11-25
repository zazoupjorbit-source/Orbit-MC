package fr.orbit.bornes;

import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Une partie d'une borne pour un joueur. Le gestionnaire ({@link Sessions}) la fait tourner à chaque tick. */
public abstract class Game {

    protected final OrbitBornes plugin;
    protected final Player player;
    protected final GameType type;
    /** Fin de partie déjà traitée. */
    boolean ended;

    public boolean isEnded() {
        return ended;
    }
    /** Numéro de place (zone du monde des bornes), -1 si la partie n'en utilise pas. */
    int slot = -1;

    protected Game(OrbitBornes plugin, Player player, GameType type) {
        this.plugin = plugin;
        this.player = player;
        this.type = type;
    }

    public Player player() {
        return player;
    }

    public GameType type() {
        return type;
    }

    /** Démarre la partie (place déjà attribuée). */
    protected abstract void start();

    protected abstract void tick();

    protected void input(Input in) {
    }

    /** Retire tout ce que la partie a posé et rend au joueur son état (ne le téléporte pas). */
    protected abstract void cleanup();

    /** Score enregistré au tableau des records. */
    protected abstract double score();

    /** Orbit Coins demandés pour cette partie (avant plafond du jour). */
    protected abstract int ocWanted();

    /** Ligne de bilan dans le chat (sans le préfixe). */
    protected abstract String summary();

    /** Le joueur est assis sur un support : on l'empêche de descendre. */
    protected boolean seated() {
        return false;
    }

    /** Une téléportation vers {@code to} fait-elle encore partie du jeu ? */
    protected boolean allows(Location to) {
        return false;
    }

    /** La partie a-t-elle vraiment commencé (sinon : pas de score ni d'OC) ? */
    protected abstract boolean played();
}
