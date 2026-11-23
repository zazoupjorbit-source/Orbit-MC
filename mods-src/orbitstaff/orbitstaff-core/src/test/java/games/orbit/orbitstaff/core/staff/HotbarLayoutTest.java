package games.orbit.orbitstaff.core.staff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HotbarLayout")
class HotbarLayoutTest {

    @Test
    @DisplayName("la disposition par defaut couvre les 9 slots")
    void defaultLayoutHasNineItems() {
        HotbarLayout layout = HotbarLayout.defaultLayout();
        assertEquals(9, layout.items().size());
    }

    @Test
    @DisplayName("chaque slot mappe l'action attendue")
    void slotsMapToActions() {
        HotbarLayout layout = HotbarLayout.defaultLayout();
        assertEquals(StaffAction.TELEPORT_PLAYER, layout.actionForSlot(0).orElseThrow());
        assertEquals(StaffAction.FREEZE, layout.actionForSlot(2).orElseThrow());
        assertEquals(StaffAction.EXIT_STAFF_MODE, layout.actionForSlot(8).orElseThrow());
        assertTrue(layout.actionForSlot(20).isEmpty());
    }

    @Test
    @DisplayName("un slot hors bornes est refuse")
    void slotOutOfRangeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new HotbarItem(9, "STONE", StaffAction.SANCTION, "x"));
    }
}
