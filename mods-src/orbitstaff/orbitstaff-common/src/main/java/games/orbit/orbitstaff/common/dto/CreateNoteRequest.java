package games.orbit.orbitstaff.common.dto;

import games.orbit.orbitstaff.common.enums.AuditSource;
import games.orbit.orbitstaff.common.enums.NoteVisibility;

import java.util.UUID;

/**
 * Requete de creation d'une note interne.
 *
 * @param targetUuid joueur concerne
 * @param authorUuid auteur
 * @param content    contenu factuel (valide par le service)
 * @param visibility niveau de visibilite
 * @param source     origine de l'action
 */
public record CreateNoteRequest(
        UUID targetUuid,
        UUID authorUuid,
        String content,
        NoteVisibility visibility,
        AuditSource source) {
}
