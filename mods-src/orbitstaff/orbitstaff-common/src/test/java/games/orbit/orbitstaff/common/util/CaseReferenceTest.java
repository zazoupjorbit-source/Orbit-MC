package games.orbit.orbitstaff.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("CaseReference")
class CaseReferenceTest {

    @Test
    @DisplayName("formate avec zero-padding sur 5 chiffres")
    void formatsWithPadding() {
        assertEquals("OS-2026-00482", new CaseReference(2026, 482).format());
        assertEquals("OS-2026-00001", new CaseReference(2026, 1).toString());
    }

    @Test
    @DisplayName("garde les sequences a plus de 5 chiffres")
    void keepsLongSequences() {
        assertEquals("OS-2026-123456", new CaseReference(2026, 123456).format());
    }

    @Test
    @DisplayName("analyse et reforme la meme reference")
    void parseRoundTrip() {
        CaseReference ref = CaseReference.parse("OS-2026-00482");
        assertEquals(2026, ref.year());
        assertEquals(482, ref.sequence());
        assertEquals("OS-2026-00482", ref.format());
    }

    @Test
    @DisplayName("rejette les formats invalides")
    void rejectsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> CaseReference.parse(null));
        assertThrows(IllegalArgumentException.class, () -> CaseReference.parse("OS-26-1"));
        assertThrows(IllegalArgumentException.class, () -> CaseReference.parse("XX-2026-00001"));
        assertThrows(IllegalArgumentException.class, () -> CaseReference.parse("OS-2026-1"));
        assertThrows(IllegalArgumentException.class, () -> new CaseReference(1999, 1));
        assertThrows(IllegalArgumentException.class, () -> new CaseReference(2026, 0));
    }
}
