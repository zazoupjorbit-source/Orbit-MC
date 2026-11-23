package games.orbit.orbitstaff.api.infrastructure.persistence;

import games.orbit.orbitstaff.api.application.port.SanctionRepository;
import games.orbit.orbitstaff.common.enums.AuditSource;
import games.orbit.orbitstaff.common.enums.SanctionStatus;
import games.orbit.orbitstaff.common.enums.SanctionType;
import games.orbit.orbitstaff.common.enums.Severity;
import games.orbit.orbitstaff.common.error.NotFoundException;
import games.orbit.orbitstaff.common.model.Sanction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Implementation JDBC de {@link SanctionRepository}. */
@Repository
public class JdbcSanctionRepository implements SanctionRepository {

    private final JdbcTemplate jdbc;

    public JdbcSanctionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Sanction save(Sanction sanction, long serviceAccountId) {
        long serverId = resolveServerId(sanction.serverId());
        jdbc.update(
                """
                INSERT INTO sanctions
                    (public_id, server_id, target_uuid, actor_uuid, type, status, reason, severity,
                     source, created_at, starts_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Uuids.toBytes(sanction.id()),
                serverId,
                Uuids.toBytes(sanction.targetUuid()),
                sanction.actorUuid().map(Uuids::toBytes).orElse(null),
                sanction.type().name(),
                sanction.status().name(),
                sanction.reason(),
                sanction.severity().name(),
                sanction.source().name(),
                SqlTimes.toTimestamp(sanction.createdAt()),
                SqlTimes.toTimestamp(sanction.startsAt()),
                sanction.expiresAt().map(SqlTimes::toTimestamp).orElse(null));
        return sanction;
    }

    @Override
    public Optional<Sanction> findByPublicId(UUID publicId) {
        List<Sanction> results = jdbc.query(baseSelect() + " WHERE sa.public_id = ?",
                sanctionMapper(), Uuids.toBytes(publicId));
        return results.stream().findFirst();
    }

    @Override
    public List<Sanction> findByTarget(UUID targetUuid) {
        return jdbc.query(baseSelect() + " WHERE sa.target_uuid = ? ORDER BY sa.created_at DESC",
                sanctionMapper(), Uuids.toBytes(targetUuid));
    }

    @Override
    public void markRevoked(UUID publicId, UUID revokedBy, String reason, Instant now) {
        jdbc.update(
                """
                UPDATE sanctions
                SET status = 'REVOKED', revoked_by = ?, revoked_at = ?, revoke_reason = ?
                WHERE public_id = ?
                """,
                Uuids.toBytes(revokedBy), SqlTimes.toTimestamp(now), reason, Uuids.toBytes(publicId));
    }

    private long resolveServerId(String serverKey) {
        List<Long> ids = jdbc.query("SELECT id FROM servers WHERE server_key = ?",
                (rs, rowNum) -> rs.getLong(1), serverKey);
        if (ids.isEmpty()) {
            throw new NotFoundException("Serveur", serverKey);
        }
        return ids.get(0);
    }

    private static String baseSelect() {
        return """
                SELECT sa.public_id, s.server_key, sa.target_uuid, sa.actor_uuid, sa.type, sa.status,
                       sa.reason, sa.severity, sa.source, sa.created_at, sa.starts_at, sa.expires_at
                FROM sanctions sa
                JOIN servers s ON s.id = sa.server_id
                """;
    }

    private static RowMapper<Sanction> sanctionMapper() {
        return (ResultSet rs, int rowNum) -> map(rs);
    }

    private static Sanction map(ResultSet rs) throws SQLException {
        byte[] actor = rs.getBytes("actor_uuid");
        java.sql.Timestamp expires = rs.getTimestamp("expires_at");
        return new Sanction(
                Uuids.toUuid(rs.getBytes("public_id")),
                rs.getString("server_key"),
                Uuids.toUuid(rs.getBytes("target_uuid")),
                actor == null ? Optional.empty() : Optional.of(Uuids.toUuid(actor)),
                SanctionType.valueOf(rs.getString("type")),
                SanctionStatus.valueOf(rs.getString("status")),
                rs.getString("reason"),
                Severity.valueOf(rs.getString("severity")),
                SqlTimes.toInstant(rs.getTimestamp("created_at")),
                SqlTimes.toInstant(rs.getTimestamp("starts_at")),
                expires == null ? Optional.empty() : Optional.of(expires.toInstant()),
                AuditSource.valueOf(rs.getString("source")),
                Optional.empty(),
                Optional.empty());
    }
}
