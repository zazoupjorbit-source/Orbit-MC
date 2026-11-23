package games.orbit.orbitstaff.api.web.dto;

/**
 * Corps de creation d'une note interne.
 *
 * @param targetUuid joueur concerne
 * @param authorUuid auteur (staff)
 * @param content    contenu factuel
 * @param visibility visibilite (HELPER, MODERATOR, SENIOR, ADMIN, OWNER)
 */
public record CreateNoteBody(
        String targetUuid,
        String authorUuid,
        String content,
        String visibility) {
}
