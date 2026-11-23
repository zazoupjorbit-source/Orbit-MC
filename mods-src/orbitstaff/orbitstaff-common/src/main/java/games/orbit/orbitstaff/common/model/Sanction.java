package games.orbit.orbitstaff.common.model;

import games.orbit.orbitstaff.common.enums.AuditSource;
import games.orbit.orbitstaff.common.enums.SanctionStatus;
import games.orbit.orbitstaff.common.enums.SanctionType;
import games.orbit.orbitstaff.common.enums.Severity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Sanction persistee (representation de lecture cote domaine).
 *
 * @param id             identifiant unique
 * @param serverId       serveur emetteur
 * @param targetUuid     joueur cible
 * @param actorUuid      auteur (staff), vide si systeme
 * @param type           type de sanction
 * @param status         statut courant
 * @param reason         motif
 * @param severity       gravite
 * @param createdAt      date de creation (UTC)
 * @param startsAt       date de debut d'effet
 * @param expiresAt      date d'expiration (vide = permanent)
 * @param source         origine de l'action
 * @param evidenceId     preuve associee (optionnelle)
 * @param previousLinked sanction precedente liee (recidive)
 */
public record Sanction(
        UUID id,
        String serverId,
        UUID targetUuid,
        Optional<UUID> actorUuid,
        SanctionType type,
        SanctionStatus status,
        String reason,
        Severity severity,
        Instant createdAt,
        Instant startsAt,
        Optional<Instant> expiresAt,
        AuditSource source,
        Optional<UUID> evidenceId,
        Optional<UUID> previousLinked) {

    public Sanction {
        actorUuid = actorUuid == null ? Optional.empty() : actorUuid;
        expiresAt = expiresAt == null ? Optional.empty() : expiresAt;
        evidenceId = evidenceId == null ? Optional.empty() : evidenceId;
        previousLinked = previousLinked == null ? Optional.empty() : previousLinked;
    }

    /** @return true si la sanction est permanente (pas de date d'expiration). */
    public boolean isPermanent() {
        return expiresAt.isEmpty();
    }

    /**
     * @param now instant de reference
     * @return true si la sanction est expiree a {@code now}
     */
    public boolean isExpiredAt(Instant now) {
        return expiresAt.map(exp -> !now.isBefore(exp)).orElse(false);
    }
}
