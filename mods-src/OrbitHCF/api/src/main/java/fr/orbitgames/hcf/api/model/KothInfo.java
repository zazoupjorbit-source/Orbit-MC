package fr.orbitgames.hcf.api.model;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Etat public d'un KOTH (ou d'une Citadel) en cours.
 */
public interface KothInfo {

    String getName();

    /** Vrai si cet evenement est la Citadel (KOTH majeur). */
    boolean isCitadel();

    /** Temps de capture restant pour le joueur en train de capturer. */
    Duration getRemainingCapture();

    /** Duree totale de capture configuree. */
    Duration getTotalCapture();

    /** Joueur en train de capturer, si present. */
    Optional<UUID> getCapturingPlayer();

    /** Vrai si plusieurs factions se disputent la zone (timer en pause). */
    boolean isContested();
}
