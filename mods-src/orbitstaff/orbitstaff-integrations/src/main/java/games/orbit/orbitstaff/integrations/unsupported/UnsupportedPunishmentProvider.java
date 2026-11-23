package games.orbit.orbitstaff.integrations.unsupported;

import games.orbit.orbitstaff.common.enums.IntegrationAvailability;
import games.orbit.orbitstaff.common.model.ExternalPunishment;
import games.orbit.orbitstaff.common.spi.PunishmentProvider;

import java.util.List;
import java.util.UUID;

/** Repli lorsqu'aucune source de sanctions externes n'est disponible. */
public final class UnsupportedPunishmentProvider implements PunishmentProvider {

    @Override
    public IntegrationAvailability availability() {
        return IntegrationAvailability.ABSENT;
    }

    @Override
    public List<ExternalPunishment> getExternalPunishments(UUID playerUuid) {
        return List.of();
    }
}
