package games.orbit.orbitstaff.integrations.unsupported;

import games.orbit.orbitstaff.common.enums.IntegrationAvailability;
import games.orbit.orbitstaff.common.model.PlayerHome;
import games.orbit.orbitstaff.common.spi.HomeProvider;

import java.util.List;
import java.util.UUID;

/** Repli lorsqu'aucun fournisseur de homes n'est disponible (ex: EssentialsX absent). */
public final class UnsupportedHomeProvider implements HomeProvider {

    @Override
    public IntegrationAvailability availability() {
        return IntegrationAvailability.ABSENT;
    }

    @Override
    public List<PlayerHome> getHomes(UUID playerUuid) {
        return List.of();
    }
}
