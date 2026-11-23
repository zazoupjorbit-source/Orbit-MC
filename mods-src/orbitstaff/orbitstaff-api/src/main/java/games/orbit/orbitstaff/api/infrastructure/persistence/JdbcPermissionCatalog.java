package games.orbit.orbitstaff.api.infrastructure.persistence;

import games.orbit.orbitstaff.api.application.port.PermissionCatalog;
import games.orbit.orbitstaff.common.enums.StaffRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Implementation JDBC de {@link PermissionCatalog} (miroir de staff_role_permissions). */
@Repository
public class JdbcPermissionCatalog implements PermissionCatalog {

    private final JdbcTemplate jdbc;

    public JdbcPermissionCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hasPermission(StaffRole role, String node) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM staff_role_permissions rp
                JOIN staff_roles r ON r.id = rp.role_id
                JOIN staff_permissions p ON p.id = rp.permission_id
                WHERE r.code = ? AND p.node = ?
                """,
                Integer.class,
                role.name(), node);
        return count != null && count > 0;
    }
}
