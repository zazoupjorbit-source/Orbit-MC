package games.orbit.orbitstaff.common.spi;

import games.orbit.orbitstaff.common.enums.IntegrationAvailability;
import games.orbit.orbitstaff.common.model.ExternalPunishment;

import java.util.List;
import java.util.UUID;

/**
 * Port d'acces aux sanctions provenant de systemes externes (lecture seule).
 */
public interface PunishmentProvider {

    IntegrationAvailability availability();

    /**
     * @param playerUuid joueur cible
     * @return les sanctions externes connues (vide si indisponible)
     */
    List<ExternalPunishment> getExternalPunishments(UUID playerUuid);
}
