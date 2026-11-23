package fr.orbitgames.hcf.api.model;

import java.util.Map;
import java.util.UUID;

/**
 * Etat public d'un Conquest en cours.
 */
public interface ConquestInfo {

    /** Scores actuels par faction. */
    Map<UUID, Integer> getScores();

    /** Score a atteindre pour gagner. */
    int getTargetScore();

    /** Noms des points de capture actifs. */
    java.util.List<String> getZoneNames();
}
