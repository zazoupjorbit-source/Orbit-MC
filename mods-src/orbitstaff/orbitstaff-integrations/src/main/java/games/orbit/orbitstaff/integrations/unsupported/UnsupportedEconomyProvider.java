package games.orbit.orbitstaff.integrations.unsupported;

import games.orbit.orbitstaff.common.enums.IntegrationAvailability;
import games.orbit.orbitstaff.common.spi.EconomyProvider;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Repli lorsqu'aucune economie n'est disponible (ex: Vault absent). */
public final class UnsupportedEconomyProvider implements EconomyProvider {

    @Override
    public IntegrationAvailability availability() {
        return IntegrationAvailability.ABSENT;
    }

    @Override
    public Optional<BigDecimal> getBalance(UUID playerUuid) {
        return Optional.empty();
    }
}
