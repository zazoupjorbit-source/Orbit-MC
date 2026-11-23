package games.orbit.orbitstaff.api.infrastructure.persistence;

import games.orbit.orbitstaff.api.application.port.IdempotencyRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Implementation JDBC de {@link IdempotencyRepository}. */
@Repository
public class JdbcIdempotencyRepository implements IdempotencyRepository {

    private final JdbcTemplate jdbc;

    public JdbcIdempotencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> findResultPublicId(long serviceAccountId, String idempotencyKey) {
        List<byte[]> rows = jdbc.query(
                "SELECT result_public_id FROM api_idempotency_keys WHERE service_account_id = ? AND idempotency_key = ?",
                (rs, rowNum) -> rs.getBytes("result_public_id"),
                serviceAccountId, idempotencyKey);
        return rows.stream().findFirst()
                .filter(bytes -> bytes != null)
                .map(Uuids::toUuid);
    }

    @Override
    public void save(long serviceAccountId, String idempotencyKey, String requestHash, UUID resultPublicId, Instant now) {
        jdbc.update(
                """
                INSERT INTO api_idempotency_keys
                    (service_account_id, idempotency_key, request_hash, result_public_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                serviceAccountId, idempotencyKey, requestHash,
                resultPublicId == null ? null : Uuids.toBytes(resultPublicId),
                SqlTimes.toTimestamp(now));
    }
}
