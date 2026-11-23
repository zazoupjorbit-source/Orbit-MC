package fr.orbitgames.hcf.api.service;

import fr.orbitgames.hcf.api.model.DtrChangeReason;
import fr.orbitgames.hcf.api.model.Faction;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Acces public au DTR (Deaths Til Raidable). Implemente par OrbitHCF-Core.
 */
public interface DtrService {

    double getDtr(Faction faction);

    /** Modifie le DTR (borne entre le minimum configure et le DTR max). */
    void setDtr(Faction faction, double value, DtrChangeReason reason);

    double getMaxDtr(Faction faction);

    /**
     * Applique la perte de DTR liee a la mort du joueur et demarre le gel de DTR.
     * Utilise par Core a la mort et par Combat pour la sanction de combat log.
     */
    void applyDeathLoss(Player victim);

    /** Valeur de DTR perdue par mort de joueur. */
    double getDeathLoss();

    // ------------------------------------------------------------------
    // Gel de DTR
    // ------------------------------------------------------------------

    void freeze(Faction faction, Duration duration);

    void unfreeze(Faction faction);

    boolean isFrozen(Faction faction);

    Duration getFreezeRemaining(Faction faction);

    // ------------------------------------------------------------------
    // Raidable
    // ------------------------------------------------------------------

    boolean isRaidable(Faction faction);
}
