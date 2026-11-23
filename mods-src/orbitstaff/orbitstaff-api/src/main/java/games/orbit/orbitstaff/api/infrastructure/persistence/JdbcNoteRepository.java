package games.orbit.orbitstaff.api.infrastructure.persistence;

import games.orbit.orbitstaff.api.application.port.NoteRepository;
import games.orbit.orbitstaff.common.enums.NoteVisibility;
import games.orbit.orbitstaff.common.model.InternalNote;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Implementation JDBC de {@link NoteRepository}. */
@Repository
public class JdbcNoteRepository implements NoteRepository {

    private final JdbcTemplate jdbc;

    public JdbcNoteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public InternalNote save(InternalNote note) {
        jdbc.update(
                """
                INSERT INTO internal_notes
                    (public_id, target_uuid, author_uuid, content, visibility, created_at, deleted)
                VALUES (?, ?, ?, ?, ?, ?, FALSE)
                """,
                Uuids.toBytes(note.id()),
                Uuids.toBytes(note.targetUuid()),
                Uuids.toBytes(note.authorUuid()),
                note.content(),
                note.visibility().name(),
                SqlTimes.toTimestamp(note.createdAt()));
        return note;
    }

    @Override
    public Optional<InternalNote> findByPublicId(UUID publicId) {
        List<InternalNote> results = jdbc.query(
                "SELECT public_id, target_uuid, author_uuid, content, visibility, created_at, deleted "
                        + "FROM internal_notes WHERE public_id = ?",
                noteMapper(), Uuids.toBytes(publicId));
        return results.stream().findFirst();
    }

    @Override
    public List<InternalNote> findActiveByTarget(UUID targetUuid) {
        return jdbc.query(
                "SELECT public_id, target_uuid, author_uuid, content, visibility, created_at, deleted "
                        + "FROM internal_notes WHERE target_uuid = ? AND deleted = FALSE ORDER BY created_at DESC",
                noteMapper(), Uuids.toBytes(targetUuid));
    }

    @Override
    public void softDelete(UUID publicId, Instant now) {
        jdbc.update("UPDATE internal_notes SET deleted = TRUE, deleted_at = ? WHERE public_id = ?",
                SqlTimes.toTimestamp(now), Uuids.toBytes(publicId));
    }

    private static RowMapper<InternalNote> noteMapper() {
        return (rs, rowNum) -> new InternalNote(
                Uuids.toUuid(rs.getBytes("public_id")),
                Uuids.toUuid(rs.getBytes("target_uuid")),
                Uuids.toUuid(rs.getBytes("author_uuid")),
                rs.getString("content"),
                NoteVisibility.valueOf(rs.getString("visibility")),
                SqlTimes.toInstant(rs.getTimestamp("created_at")),
                rs.getBoolean("deleted"));
    }
}
