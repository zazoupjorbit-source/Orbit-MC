package fr.orbitgames.hcf.core.claim;

import fr.orbitgames.hcf.api.model.Claim;

import java.util.Objects;
import java.util.UUID;

public class ClaimImpl implements Claim {

    private final String world;
    private final int chunkX;
    private final int chunkZ;
    private final UUID factionId;

    public ClaimImpl(String world, int chunkX, int chunkZ, UUID factionId) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.factionId = factionId;
    }

    @Override
    public String getWorldName() {
        return world;
    }

    @Override
    public int getChunkX() {
        return chunkX;
    }

    @Override
    public int getChunkZ() {
        return chunkZ;
    }

    @Override
    public UUID getFactionId() {
        return factionId;
    }

    public static String key(String world, int cx, int cz) {
        return world + ";" + cx + ";" + cz;
    }

    public String key() {
        return key(world, chunkX, chunkZ);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClaimImpl claim)) {
            return false;
        }
        return chunkX == claim.chunkX && chunkZ == claim.chunkZ && world.equals(claim.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, chunkX, chunkZ);
    }
}
