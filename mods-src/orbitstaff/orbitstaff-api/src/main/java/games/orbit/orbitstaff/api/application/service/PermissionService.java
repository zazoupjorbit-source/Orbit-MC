package games.orbit.orbitstaff.api.application.service;

import games.orbit.orbitstaff.api.application.port.PermissionCatalog;
import games.orbit.orbitstaff.api.application.port.StaffRepository;
import games.orbit.orbitstaff.common.enums.SanctionType;
import games.orbit.orbitstaff.common.enums.StaffRole;
import games.orbit.orbitstaff.common.error.PermissionDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Verifie les permissions metier cote serveur (jamais uniquement en masquant un
 * bouton). Combine le grade du membre (StaffRepository) et le catalogue des
 * permissions (PermissionCatalog, miroir de staff_role_permissions).
 */
@Service
public class PermissionService {

    private final StaffRepository staffRepository;
    private final PermissionCatalog permissionCatalog;

    public PermissionService(StaffRepository staffRepository, PermissionCatalog permissionCatalog) {
        this.staffRepository = staffRepository;
        this.permissionCatalog = permissionCatalog;
    }

    /**
     * @return le grade actif du membre
     * @throws PermissionDeniedException si le joueur n'est pas un membre du staff actif
     */
    public StaffRole requireStaff(UUID staffUuid) {
        return staffRepository.findActiveRole(staffUuid)
                .orElseThrow(() -> new PermissionDeniedException("staff-member"));
    }

    /**
     * Exige que le membre possede une permission, sinon leve une exception.
     */
    public void require(UUID staffUuid, String node) {
        StaffRole role = requireStaff(staffUuid);
        if (!permissionCatalog.hasPermission(role, node)) {
            throw new PermissionDeniedException(node);
        }
    }

    /** @return true si le membre possede la permission (sans lever d'exception). */
    public boolean can(UUID staffUuid, String node) {
        return staffRepository.findActiveRole(staffUuid)
                .map(role -> permissionCatalog.hasPermission(role, node))
                .orElse(false);
    }

    /** Associe un type de sanction au noeud de permission requis pour l'appliquer. */
    public static String nodeForSanction(SanctionType type) {
        return switch (type) {
            case WARNING -> "orbitstaff.warn";
            case MUTE -> "orbitstaff.mute";
            case KICK -> "orbitstaff.kick";
            case TEMPBAN -> "orbitstaff.tempban";
            case BAN -> "orbitstaff.ban";
            case FREEZE -> "orbitstaff.freeze";
            case STAFF_NOTE -> "orbitstaff.notes.create";
            case CHECK -> "orbitstaff.inspect";
        };
    }
}
