package games.orbit.orbitstaff.common.model;

import java.time.Instant;

/**
 * Entree d'historique de blocs (integration CoreProtect, lecture seule).
 *
 * @param time    horodatage
 * @param actor   auteur de l'action (joueur ou entite)
 * @param action  action (ex: placement, destruction)
 * @param blockId identifiant du bloc concerne
 * @param world   monde
 * @param x       coordonnee X
 * @param y       coordonnee Y
 * @param z       coordonnee Z
 */
public record BlockLogEntry(
        Instant time,
        String actor,
        String action,
        String blockId,
        String world,
        int x,
        int y,
        int z) {
}
