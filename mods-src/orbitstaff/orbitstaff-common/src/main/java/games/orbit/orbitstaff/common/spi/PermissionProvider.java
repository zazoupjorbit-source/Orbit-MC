package games.orbit.orbitstaff.common.spi;

import games.orbit.orbitstaff.common.enums.IntegrationAvailability;
import games.orbit.orbitstaff.common.model.PlayerPermissionProfile;

import java.util.Optional;
import java.util.UUID;

/**
 * Port d'acces au profil de permissions d'un joueur.
 *
 * <p>Implementations attendues : {@code LuckPermsPermissionProvider} et un
 * provider "Unsupported".</p>
 */
public interface PermissionProvider {

    IntegrationAvailability availability();

    /**
     * @param playerUuid joueur cible
     * @return le profil, ou vide si indisponible
     */
    Optional<PlayerPermissionProfile> getProfile(UUID playerUuid);
}
