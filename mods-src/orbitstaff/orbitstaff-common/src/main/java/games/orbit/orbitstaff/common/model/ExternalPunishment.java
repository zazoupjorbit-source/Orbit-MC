package games.orbit.orbitstaff.common.model;

import java.time.Instant;
import java.util.Optional;

/**
 * Sanction provenant d'un systeme externe (ex: EssentialsX), agregee au dossier
 * en lecture seule.
 *
 * @param source    plugin d'origine
 * @param type      type textuel (mute, ban...)
 * @param reason    motif
 * @param issuedBy  auteur
 * @param issuedAt  date d'emission
 * @param expiresAt expiration (vide = permanent)
 */
public record ExternalPunishment(
        String source,
        String type,
        String reason,
        String issuedBy,
        Instant issuedAt,
        Optional<Instant> expiresAt) {

    public ExternalPunishment {
        expiresAt = expiresAt == null ? Optional.empty() : expiresAt;
    }
}
