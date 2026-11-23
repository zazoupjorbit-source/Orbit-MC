package fr.orbitgames.hcf.api.service;

import fr.orbitgames.hcf.api.model.Claim;
import fr.orbitgames.hcf.api.model.Faction;
import fr.orbitgames.hcf.api.model.FactionType;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;

/**
 * Acces public aux claims et aux zones systeme. Implemente par OrbitHCF-Core.
 */
public interface ClaimService {

    /** Claim de joueur au point donne, s'il existe. */
    Optional<Claim> getClaimAt(Location location);

    /**
     * Faction proprietaire du terrain au point donne.
     * Tient compte des zones systeme (Safezone, Warzone, Road) puis des claims
     * de factions, et retourne Wilderness par defaut. Ne retourne jamais null.
     */
    Faction getFactionAt(Location location);

    /** Type de zone au point donne. */
    default FactionType getZoneType(Location location) {
        return getFactionAt(location).getType();
    }

    default boolean isSafezone(Location location) {
        return getZoneType(location) == FactionType.SAFEZONE;
    }

    default boolean isWarzone(Location location) {
        return getZoneType(location) == FactionType.WARZONE;
    }

    /** Claims d'une faction. */
    Collection<Claim> getClaims(Faction faction);

    /**
     * Revendique directement un chunk pour une faction (sans verifications de
     * gameplay : usage systeme/admin, par exemple pour une zone d'evenement).
     */
    boolean claimChunk(Faction faction, World world, int chunkX, int chunkZ);

    /** Retire un claim. */
    boolean unclaimChunk(World world, int chunkX, int chunkZ);

    /** Retire tous les claims d'une faction. */
    void unclaimAll(Faction faction);

    /**
     * Vrai si le joueur peut construire/casser au point donne
     * (protections de claims comprises).
     */
    boolean canBuild(Player player, Location location);
}
