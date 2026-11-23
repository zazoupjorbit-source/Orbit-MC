package games.orbit.orbitstaff.api.application.port;

import games.orbit.orbitstaff.common.model.InternalNote;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistance des notes internes. */
public interface NoteRepository {

    InternalNote save(InternalNote note);

    Optional<InternalNote> findByPublicId(UUID publicId);

    /** Notes non supprimees d'un joueur (le filtrage par visibilite est fait par le service). */
    List<InternalNote> findActiveByTarget(UUID targetUuid);

    /** Suppression logique (soft delete). */
    void softDelete(UUID publicId, Instant now);
}
