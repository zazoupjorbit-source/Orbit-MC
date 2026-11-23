package games.orbit.orbitstaff.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("StaffRole - hierarchie et visibilite des notes")
class StaffRoleTest {

    @Test
    @DisplayName("un grade voit les notes de son niveau et en dessous")
    void canViewOwnAndLower() {
        assertTrue(StaffRole.HELPER.canView(NoteVisibility.HELPER));
        assertTrue(StaffRole.MODERATOR.canView(NoteVisibility.HELPER));
        assertTrue(StaffRole.MANAGER.canView(NoteVisibility.OWNER));
        assertTrue(StaffRole.ADMINISTRATOR.canView(NoteVisibility.ADMIN));
    }

    @Test
    @DisplayName("un grade ne voit pas les notes plus sensibles")
    void cannotViewHigher() {
        assertFalse(StaffRole.HELPER.canView(NoteVisibility.MODERATOR));
        assertFalse(StaffRole.MODERATOR.canView(NoteVisibility.SENIOR));
        assertFalse(StaffRole.ADMINISTRATOR.canView(NoteVisibility.OWNER));
    }

    @Test
    @DisplayName("isAtLeast respecte la hierarchie")
    void isAtLeastRespectsHierarchy() {
        assertTrue(StaffRole.ADMINISTRATOR.isAtLeast(StaffRole.MODERATOR));
        assertTrue(StaffRole.MODERATOR.isAtLeast(StaffRole.MODERATOR));
        assertFalse(StaffRole.HELPER.isAtLeast(StaffRole.SENIOR_MODERATOR));
    }
}
