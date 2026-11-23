package games.orbit.orbitstaff.api.web.dto;

import games.orbit.orbitstaff.common.model.Sanction;

import java.time.Instant;

/** Representation JSON d'une sanction. */
public record SanctionResponse(
        String id,
        String serverId,
        String targetUuid,
        String actorUuid,
        String type,
        String status,
        String reason,
        String severity,
        String createdAt,
        String startsAt,
        String expiresAt,
        boolean permanent) {

    public static SanctionResponse from(Sanction s) {
        return new SanctionResponse(
                s.id().toString(),
                s.serverId(),
                s.targetUuid().toString(),
                s.actorUuid().map(java.util.UUID::toString).orElse(null),
                s.type().name(),
                s.status().name(),
                s.reason(),
                s.severity().name(),
                s.createdAt().toString(),
                s.startsAt().toString(),
                s.expiresAt().map(Instant::toString).orElse(null),
                s.isPermanent());
    }
}
