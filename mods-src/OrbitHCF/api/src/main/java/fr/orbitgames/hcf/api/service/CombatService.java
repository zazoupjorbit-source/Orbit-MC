package fr.orbitgames.hcf.api.service;

import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.UUID;

/**
 * Acces public aux regles de combat. Implemente par OrbitHCF-Combat.
 * Absent si le module Combat n'est pas installe.
 */
public interface CombatService {

    // ------------------------------------------------------------------
    // Combat tag
    // ------------------------------------------------------------------

    boolean isCombatTagged(UUID playerId);

    Duration getCombatTagRemaining(UUID playerId);

    /** Applique (ou rafraichit) le combat tag des deux joueurs. */
    void tag(Player victim, Player attacker);

    /** Retire le combat tag du joueur. */
    void untag(UUID playerId);

    // ------------------------------------------------------------------
    // PvP timer
    // ------------------------------------------------------------------

    boolean hasPvpTimer(UUID playerId);

    Duration getPvpTimerRemaining(UUID playerId);

    void removePvpTimer(UUID playerId);

    // ------------------------------------------------------------------
    // Deathban et vies
    // ------------------------------------------------------------------

    boolean isDeathbanEnabled();

    boolean isDeathbanned(UUID playerId);

    Duration getDeathbanRemaining(UUID playerId);

    void deathban(UUID playerId, Duration duration);

    /** Leve le deathban d'un joueur (revive). */
    void undeathban(UUID playerId);

    int getLives(UUID playerId);

    void setLives(UUID playerId, int lives);

    default void addLives(UUID playerId, int amount) {
        setLives(playerId, Math.max(0, getLives(playerId) + amount));
    }
}
