package fr.orbitgames.hcf.api.service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Moteur centralise de timers et cooldowns. Implemente par OrbitHCF-Core.
 * <p>
 * Les cles standard sont definies dans {@link fr.orbitgames.hcf.api.TimerKeys}.
 * Un timer persistant survit aux deconnexions et aux redemarrages.
 */
public interface TimerService {

    // ------------------------------------------------------------------
    // Timers joueur
    // ------------------------------------------------------------------

    default void setTimer(UUID playerId, String key, Duration duration) {
        setTimer(playerId, key, duration, false);
    }

    void setTimer(UUID playerId, String key, Duration duration, boolean persistent);

    /** Temps restant, {@link Duration#ZERO} si inactif. */
    Duration getRemaining(UUID playerId, String key);

    boolean isActive(UUID playerId, String key);

    void clearTimer(UUID playerId, String key);

    /** Fige le timer (le temps restant est conserve jusqu'a la reprise). */
    void pauseTimer(UUID playerId, String key);

    void resumeTimer(UUID playerId, String key);

    boolean isPaused(UUID playerId, String key);

    /** Timers actifs d'un joueur : cle vers temps restant. */
    Map<String, Duration> getActiveTimers(UUID playerId);

    // ------------------------------------------------------------------
    // Timers globaux (SOTW, EOTW, evenements...)
    // ------------------------------------------------------------------

    void setGlobalTimer(String key, Duration duration);

    Duration getGlobalRemaining(String key);

    boolean isGlobalActive(String key);

    void clearGlobalTimer(String key);
}
