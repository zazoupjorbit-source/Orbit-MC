package fr.orbitgames.hcf.api.service;

import fr.orbitgames.hcf.api.model.ConquestInfo;
import fr.orbitgames.hcf.api.model.KothInfo;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Acces public aux evenements HCF. Implemente par OrbitHCF-Events.
 * Absent si le module Events n'est pas installe.
 */
public interface EventsService {

    // ------------------------------------------------------------------
    // SOTW (Start Of The World)
    // ------------------------------------------------------------------

    boolean isSotwActive();

    Duration getSotwRemaining();

    /**
     * Vrai si le joueur beneficie encore de la protection SOTW
     * (SOTW actif et protection non retiree volontairement).
     */
    boolean isSotwProtected(UUID playerId);

    /** Retire volontairement la protection SOTW du joueur (/sotw off). */
    void removeSotwProtection(UUID playerId);

    // ------------------------------------------------------------------
    // EOTW (End Of The World)
    // ------------------------------------------------------------------

    boolean isEotwActive();

    // ------------------------------------------------------------------
    // KOTH / Citadel / Conquest
    // ------------------------------------------------------------------

    /** KOTH ou Citadel actuellement actif. */
    Optional<KothInfo> getActiveKoth();

    /** Conquest actuellement actif. */
    Optional<ConquestInfo> getActiveConquest();
}
