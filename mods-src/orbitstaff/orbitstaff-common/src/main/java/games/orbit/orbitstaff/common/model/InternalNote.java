package games.orbit.orbitstaff.common.model;

import games.orbit.orbitstaff.common.enums.NoteVisibility;

import java.time.Instant;
import java.util.UUID;

/**
 * Note interne rattachee au dossier d'un joueur. Jamais visible du joueur.
 *
 * @param id           identifiant unique
 * @param targetUuid   joueur concerne
 * @param authorUuid   auteur de la note (staff)
 * @param content      contenu factuel
 * @param visibility   niveau de visibilite minimal requis
 * @param createdAt    date de creation (UTC)
 * @param deleted      suppression logique (soft delete)
 */
public record InternalNote(
        UUID id,
        UUID targetUuid,
        UUID authorUuid,
        String content,
        NoteVisibility visibility,
        Instant createdAt,
        boolean deleted) {
}
