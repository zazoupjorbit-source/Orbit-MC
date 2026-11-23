package games.orbit.orbitstaff.api.infrastructure.persistence;

import games.orbit.orbitstaff.api.application.port.PlayerRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/** Implementation JDBC de {@link PlayerRepository}. */
@Repository
public class JdbcPlayerRepository implements PlayerRepository {

    private final JdbcTemplate jdbc;

    public JdbcPlayerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ensurePlayer(UUID uuid, String username, Instant now) {
        jdbc.update(
                """
                INSERT INTO players (uuid, current_username, first_seen, last_seen, created_at)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    last_seen = VALUES(last_seen),
                    current_username = COALESCE(VALUES(current_username), current_username)
                """,
                Uuids.toBytes(uuid),
                username,
                SqlTimes.toTimestamp(now),
                SqlTimes.toTimestamp(now),
                SqlTimes.toTimestamp(now));
    }
}
