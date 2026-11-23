package games.orbit.orbitstaff.common.spi;

import games.orbit.orbitstaff.common.enums.IntegrationAvailability;
import games.orbit.orbitstaff.common.model.PlayerHome;

import java.util.List;
import java.util.UUID;

/**
 * Port d'acces aux homes d'un joueur.
 *
 * <p>Implementations attendues : {@code EssentialsHomeProvider} (integration
 * reelle) et un provider "Unsupported" renvoyant {@link IntegrationAvailability#ABSENT}
 * et une liste vide, afin que le plugin demarre meme sans EssentialsX.</p>
 */
public interface HomeProvider {

    /** @return l'etat de disponibilite de l'integration. */
    IntegrationAvailability availability();

    /**
     * @param playerUuid joueur cible
     * @return la liste des homes (vide si indisponible)
     */
    List<PlayerHome> getHomes(UUID playerUuid);
}
