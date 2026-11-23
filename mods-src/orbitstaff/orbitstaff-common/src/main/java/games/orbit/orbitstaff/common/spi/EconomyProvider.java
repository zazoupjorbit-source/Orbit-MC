package games.orbit.orbitstaff.common.spi;

import games.orbit.orbitstaff.common.enums.IntegrationAvailability;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Port d'acces a l'economie d'un joueur (integration Vault).
 */
public interface EconomyProvider {

    IntegrationAvailability availability();

    /**
     * @param playerUuid joueur cible
     * @return le solde, ou vide si indisponible
     */
    Optional<BigDecimal> getBalance(UUID playerUuid);
}
