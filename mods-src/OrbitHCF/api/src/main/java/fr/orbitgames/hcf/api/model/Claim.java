package fr.orbitgames.hcf.api.model;

import java.util.UUID;

/**
 * Un chunk revendique par une faction.
 */
public interface Claim {

    String getWorldName();

    int getChunkX();

    int getChunkZ();

    /** Faction proprietaire du claim. */
    UUID getFactionId();
}
