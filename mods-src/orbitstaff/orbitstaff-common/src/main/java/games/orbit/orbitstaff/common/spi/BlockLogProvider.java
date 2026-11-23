package games.orbit.orbitstaff.common.spi;

import games.orbit.orbitstaff.common.enums.IntegrationAvailability;
import games.orbit.orbitstaff.common.model.BlockLogEntry;
import games.orbit.orbitstaff.common.model.BlockLookupQuery;

import java.util.List;

/**
 * Port d'acces a l'historique de blocs (integration CoreProtect, lecture seule).
 *
 * <p>OrbitStaff ne recree pas CoreProtect : il se contente d'afficher ses
 * resultats dans un dossier.</p>
 */
public interface BlockLogProvider {

    IntegrationAvailability availability();

    /**
     * @param query parametres de recherche
     * @return les entrees correspondantes (vide si indisponible)
     */
    List<BlockLogEntry> lookup(BlockLookupQuery query);
}
