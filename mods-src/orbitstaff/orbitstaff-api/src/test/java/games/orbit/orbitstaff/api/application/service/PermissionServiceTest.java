package games.orbit.orbitstaff.api.application.service;

import games.orbit.orbitstaff.api.support.Fakes;
import games.orbit.orbitstaff.common.enums.SanctionType;
import games.orbit.orbitstaff.common.enums.StaffRole;
import games.orbit.orbitstaff.common.error.PermissionDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PermissionService")
class PermissionServiceTest {

    private Fakes.FakeStaffRepository staff;
    private PermissionService service;

    private final UUID admin = UUID.randomUUID();
    private final UUID helper = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        staff = new Fakes.FakeStaffRepository();
        staff.assign(admin, StaffRole.ADMINISTRATOR);
        staff.assign(helper, StaffRole.HELPER);
        service = new PermissionService(staff, new Fakes.FakePermissionCatalog());
    }

    @Test
    @DisplayName("require accepte un grade suffisant et refuse un grade insuffisant")
    void requireEnforcesPermission() {
        assertDoesNotThrow(() -> service.require(admin, "orbitstaff.ban"));
        assertThrows(PermissionDeniedException.class, () -> service.require(helper, "orbitstaff.ban"));
    }

    @Test
    @DisplayName("can reflete la permission sans lever d'exception")
    void canReflectsPermission() {
        assertTrue(service.can(admin, "orbitstaff.ban"));
        assertFalse(service.can(helper, "orbitstaff.ban"));
        assertTrue(service.can(helper, "orbitstaff.warn"));
    }

    @Test
    @DisplayName("requireStaff refuse un non-membre")
    void requireStaffRejectsStranger() {
        assertThrows(PermissionDeniedException.class, () -> service.requireStaff(stranger));
    }

    @Test
    @DisplayName("le mapping type de sanction -> permission est correct")
    void sanctionNodeMapping() {
        assertEquals("orbitstaff.ban", PermissionService.nodeForSanction(SanctionType.BAN));
        assertEquals("orbitstaff.mute", PermissionService.nodeForSanction(SanctionType.MUTE));
        assertEquals("orbitstaff.warn", PermissionService.nodeForSanction(SanctionType.WARNING));
    }
}
