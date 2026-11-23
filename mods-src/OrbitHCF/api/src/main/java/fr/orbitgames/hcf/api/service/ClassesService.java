package fr.orbitgames.hcf.api.service;

import fr.orbitgames.hcf.api.model.HcfClass;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Acces public au systeme de classes. Implemente par OrbitHCF-Classes.
 * Absent si le module Classes n'est pas installe.
 */
public interface ClassesService {

    /** Classe active du joueur, si une armure complete est portee. */
    Optional<HcfClass> getActiveClass(UUID playerId);

    default boolean isActive(UUID playerId, HcfClass hcfClass) {
        return getActiveClass(playerId).filter(c -> c == hcfClass).isPresent();
    }

    // ------------------------------------------------------------------
    // Bard
    // ------------------------------------------------------------------

    /** Energie actuelle du Bard (0 si le joueur n'est pas Bard). */
    double getBardEnergy(UUID playerId);

    double getMaxBardEnergy();

    // ------------------------------------------------------------------
    // Archer
    // ------------------------------------------------------------------

    boolean isArcherTagged(UUID playerId);

    /** Temps restant de l'Archer Tag, {@link Duration#ZERO} si absent. */
    Duration getArcherTagRemaining(UUID playerId);

    /** Multiplicateur de degats applique aux joueurs marques par un Archer. */
    double getArcherTagDamageMultiplier();
}
