package games.orbit.orbitstaff.common.model;

import java.time.Duration;

/**
 * Requete de recherche d'historique de blocs (integration CoreProtect).
 *
 * @param world  monde cible
 * @param x      coordonnee X
 * @param y      coordonnee Y
 * @param z      coordonnee Z
 * @param radius rayon en blocs
 * @param window fenetre temporelle a remonter
 */
public record BlockLookupQuery(
        String world,
        int x,
        int y,
        int z,
        int radius,
        Duration window) {
}
