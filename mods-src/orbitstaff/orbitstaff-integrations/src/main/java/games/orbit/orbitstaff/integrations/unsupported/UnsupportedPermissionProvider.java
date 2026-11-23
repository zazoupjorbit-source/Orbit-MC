package games.orbit.orbitstaff.integrations.unsupported;

import games.orbit.orbitstaff.common.enums.IntegrationAvailability;
import games.orbit.orbitstaff.common.model.PlayerPermissionProfile;
import games.orbit.orbitstaff.common.spi.PermissionProvider;

import java.util.Optional;
import java.util.UUID;

/** Repli lorsqu'aucun fournisseur de permissions n'est disponible (ex: LuckPerms absent). */
public final class UnsupportedPermissionProvider implements PermissionProvider {

    @Override
    public IntegrationAvailability availability() {
        return IntegrationAvailability.ABSENT;
    }

    @Override
    public Optional<PlayerPermissionProfile> getProfile(UUID playerUuid) {
        return Optional.empty();
    }
}
