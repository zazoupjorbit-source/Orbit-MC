package games.orbit.orbitstaff.api.application.port;

import games.orbit.orbitstaff.common.enums.StaffRole;

/**
 * Catalogue des permissions par grade (miroir de staff_role_permissions).
 */
public interface PermissionCatalog {

    /**
     * @param role grade du membre
     * @param node noeud de permission (ex: orbitstaff.ban)
     * @return true si le grade possede la permission
     */
    boolean hasPermission(StaffRole role, String node);
}
