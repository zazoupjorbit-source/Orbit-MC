package games.orbit.orbitstaff.api.web.dto;

/**
 * Corps de creation d'une sanction.
 *
 * @param targetUuid UUID du joueur cible (obligatoire)
 * @param actorUuid  UUID de l'auteur staff (facultatif : vide = systeme)
 * @param type       type de sanction (WARNING, MUTE, KICK, TEMPBAN, BAN, FREEZE, STAFF_NOTE, CHECK)
 * @param reason     motif (obligatoire)
 * @param duration   duree brute (ex: 7d, permanent) ; facultatif selon le type
 */
public record CreateSanctionBody(
        String targetUuid,
        String actorUuid,
        String type,
        String reason,
        String duration) {
}
