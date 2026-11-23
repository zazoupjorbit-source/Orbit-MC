package games.orbit.orbitstaff.api.infrastructure.persistence;

import games.orbit.orbitstaff.api.application.port.StaffRepository;
import games.orbit.orbitstaff.common.enums.StaffRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Implementation JDBC de {@link StaffRepository}. */
@Repository
public class JdbcStaffRepository implements StaffRepository {

    private final JdbcTemplate jdbc;

    public JdbcStaffRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StaffRole> findActiveRole(UUID staffUuid) {
        List<String> codes = jdbc.query(
                """
                SELECT r.code
                FROM staff_members m
                JOIN staff_roles r ON r.id = m.role_id
                WHERE m.player_uuid = ? AND m.active = TRUE
                """,
                (rs, rowNum) -> rs.getString("code"),
                Uuids.toBytes(staffUuid));
        return codes.stream().findFirst().map(StaffRole::valueOf);
    }
}
