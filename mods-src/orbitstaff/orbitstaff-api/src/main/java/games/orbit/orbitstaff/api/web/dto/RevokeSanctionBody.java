package games.orbit.orbitstaff.api.web.dto;

/**
 * Corps d'annulation d'une sanction.
 *
 * @param actorUuid auteur de l'annulation (staff)
 * @param reason    motif de l'annulation
 */
public record RevokeSanctionBody(String actorUuid, String reason) {
}
