package games.orbit.orbitstaff.api.infrastructure.persistence;

import games.orbit.orbitstaff.api.application.port.AuditRepository;
import games.orbit.orbitstaff.common.model.AuditEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Implementation JDBC de {@link AuditRepository}. */
@Repository
public class JdbcAuditRepository implements AuditRepository {

    private final JdbcTemplate jdbc;

    public JdbcAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(AuditEvent event) {
        jdbc.update(
                """
                INSERT INTO audit_events
                    (public_id, occurred_at, server_id, actor_uuid, target_uuid, action, source,
                     old_value, new_value, reason, success, correlation_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Uuids.toBytes(event.eventId()),
                SqlTimes.toTimestamp(event.timestamp()),
                resolveServerId(event.serverId()),
                event.actorUuid().map(Uuids::toBytes).orElse(null),
                event.targetUuid().map(Uuids::toBytes).orElse(null),
                event.action().name(),
                event.source().name(),
                event.oldValue().orElse(null),
                event.newValue().orElse(null),
                event.reason().orElse(null),
                event.success(),
                event.correlationId());
    }

    private Long resolveServerId(String serverKey) {
        if (serverKey == null) {
            return null;
        }
        List<Long> ids = jdbc.query("SELECT id FROM servers WHERE server_key = ?",
                (rs, rowNum) -> rs.getLong(1), serverKey);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
