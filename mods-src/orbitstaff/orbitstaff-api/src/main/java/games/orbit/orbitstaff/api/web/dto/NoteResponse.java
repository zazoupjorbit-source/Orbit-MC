package games.orbit.orbitstaff.api.web.dto;

import games.orbit.orbitstaff.common.model.InternalNote;

/** Representation JSON d'une note interne. */
public record NoteResponse(
        String id,
        String targetUuid,
        String authorUuid,
        String content,
        String visibility,
        String createdAt) {

    public static NoteResponse from(InternalNote n) {
        return new NoteResponse(
                n.id().toString(),
                n.targetUuid().toString(),
                n.authorUuid().toString(),
                n.content(),
                n.visibility().name(),
                n.createdAt().toString());
    }
}
