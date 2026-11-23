package games.orbit.orbitstaff.api.infrastructure.persistence;

import games.orbit.orbitstaff.api.application.model.LinkCodeRecord;
import games.orbit.orbitstaff.api.application.port.DiscordLinkRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Implementation JDBC de {@link DiscordLinkRepository}. */
@Repository
public class JdbcDiscordLinkRepository implements DiscordLinkRepository {

    private final JdbcTemplate jdbc;

    public JdbcDiscordLinkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveCode(UUID playerUuid, String codeHash, Instant createdAt, Instant expiresAt) {
        jdbc.update(
                """
                INSERT INTO discord_link_codes (player_uuid, code_hash, attempts, created_at, expires_at)
                VALUES (?, ?, 0, ?, ?)
                """,
                Uuids.toBytes(playerUuid), codeHash,
                SqlTimes.toTimestamp(createdAt), SqlTimes.toTimestamp(expiresAt));
    }

    @Override
    public Optional<LinkCodeRecord> findByCodeHash(String codeHash) {
        List<LinkCodeRecord> results = jdbc.query(
                """
                SELECT id, player_uuid, code_hash, attempts, expires_at, consumed_at
                FROM discord_link_codes WHERE code_hash = ?
                """,
                (rs, rowNum) -> new LinkCodeRecord(
                        rs.getLong("id"),
                        Uuids.toUuid(rs.getBytes("player_uuid")),
                        rs.getString("code_hash"),
                        rs.getInt("attempts"),
                        SqlTimes.toInstant(rs.getTimestamp("expires_at")),
                        rs.getTimestamp("consumed_at") != null),
                codeHash);
        return results.stream().findFirst();
    }

    @Override
    public void incrementAttempts(long codeId) {
        jdbc.update("UPDATE discord_link_codes SET attempts = attempts + 1 WHERE id = ?", codeId);
    }

    @Override
    public void markConsumed(long codeId, Instant now) {
        jdbc.update("UPDATE discord_link_codes SET consumed_at = ? WHERE id = ?",
                SqlTimes.toTimestamp(now), codeId);
    }

    @Override
    public void link(UUID playerUuid, String discordUserId, Instant now) {
        jdbc.update(
                """
                INSERT INTO discord_links (player_uuid, discord_user_id, linked_at)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE discord_user_id = VALUES(discord_user_id), linked_at = VALUES(linked_at)
                """,
                Uuids.toBytes(playerUuid), discordUserId, SqlTimes.toTimestamp(now));
    }

    @Override
    public Optional<String> findDiscordId(UUID playerUuid) {
        List<String> ids = jdbc.query(
                "SELECT discord_user_id FROM discord_links WHERE player_uuid = ?",
                (rs, rowNum) -> rs.getString("discord_user_id"),
                Uuids.toBytes(playerUuid));
        return ids.stream().findFirst();
    }

    @Override
    public void unlink(UUID playerUuid) {
        jdbc.update("DELETE FROM discord_links WHERE player_uuid = ?", Uuids.toBytes(playerUuid));
    }
}
