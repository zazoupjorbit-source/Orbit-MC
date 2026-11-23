package games.orbit.orbitstaff.api.infrastructure.persistence;

import games.orbit.orbitstaff.api.application.model.ServiceAccount;
import games.orbit.orbitstaff.api.application.model.ServiceAccountKind;
import games.orbit.orbitstaff.api.application.port.ServiceAccountRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Implementation JDBC de {@link ServiceAccountRepository}. */
@Repository
public class JdbcServiceAccountRepository implements ServiceAccountRepository {

    private final JdbcTemplate jdbc;

    public JdbcServiceAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ServiceAccount> findActiveByKeyHash(String keyHash) {
        List<ServiceAccount> results = jdbc.query(
                """
                SELECT sa.id, sa.server_id, s.server_key, sa.kind
                FROM service_accounts sa
                JOIN servers s ON s.id = sa.server_id
                WHERE sa.key_hash = ? AND sa.revoked = FALSE
                """,
                (rs, rowNum) -> new ServiceAccount(
                        rs.getLong("id"),
                        rs.getLong("server_id"),
                        rs.getString("server_key"),
                        ServiceAccountKind.valueOf(rs.getString("kind"))),
                keyHash);
        return results.stream().findFirst();
    }

    @Override
    public void touch(long serviceAccountId, Instant now) {
        jdbc.update("UPDATE service_accounts SET last_used_at = ? WHERE id = ?",
                SqlTimes.toTimestamp(now), serviceAccountId);
    }
}
