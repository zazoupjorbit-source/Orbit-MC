package games.orbit.orbitstaff.api.application.port;

import games.orbit.orbitstaff.common.model.Sanction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistance des sanctions. */
public interface SanctionRepository {

    /** Insere une sanction et la retourne. */
    Sanction save(Sanction sanction, long serviceAccountId);

    Optional<Sanction> findByPublicId(UUID publicId);

    /** Historique (dossier disciplinaire) d'un joueur, du plus recent au plus ancien. */
    List<Sanction> findByTarget(UUID targetUuid);

    /** Passe une sanction au statut REVOKED. */
    void markRevoked(UUID publicId, UUID revokedBy, String reason, java.time.Instant now);
}
