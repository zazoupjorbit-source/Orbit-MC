package games.orbit.orbitstaff.common.enums;

/**
 * Cycle de vie d'un appel de sanction.
 *
 * <p>Le modele de donnees est prevu des la v1 meme si l'interface complete
 * arrive plus tard (voir section 17 du cahier des charges).</p>
 */
public enum AppealStatus {
    NEW,
    ASSIGNED,
    WAITING_PLAYER,
    UNDER_REVIEW,
    ACCEPTED,
    PARTIALLY_ACCEPTED,
    REJECTED,
    CLOSED
}
