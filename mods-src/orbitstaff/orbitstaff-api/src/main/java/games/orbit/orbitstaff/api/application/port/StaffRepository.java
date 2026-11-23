package games.orbit.orbitstaff.api.application.port;

import games.orbit.orbitstaff.common.enums.StaffRole;

import java.util.Optional;
import java.util.UUID;

/** Acces aux membres du staff et a leur grade. */
public interface StaffRepository {

    /**
     * @param staffUuid joueur staff
     * @return son grade s'il est un membre actif du staff
     */
    Optional<StaffRole> findActiveRole(UUID staffUuid);
}
