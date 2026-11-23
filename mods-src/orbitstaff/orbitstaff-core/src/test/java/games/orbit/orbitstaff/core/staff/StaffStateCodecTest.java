package games.orbit.orbitstaff.core.staff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("StaffStateCodec")
class StaffStateCodecTest {

    @Test
    @DisplayName("un etat survit a l'aller-retour Map")
    void roundTrip() {
        StaffModeState original = new StaffModeState(
                true, true, "CREATIVE", "base64data", "world",
                100.5, 64.0, -20.25, 90.0f, -45.0f);

        StaffModeState restored = StaffStateCodec.fromMap(StaffStateCodec.toMap(original));

        assertEquals(original, restored);
    }

    @Test
    @DisplayName("une map vide donne un etat inactif par defaut")
    void emptyMapDefaults() {
        StaffModeState state = StaffStateCodec.fromMap(Map.of());
        assertEquals(false, state.active());
        assertEquals("SURVIVAL", state.previousGameMode());
    }
}
