package games.orbit.orbitstaff.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LinkCode")
class LinkCodeTest {

    @RepeatedTest(50)
    @DisplayName("genere un code valide de la bonne longueur, sans caractere ambigu")
    void generatesValidCode() {
        String code = LinkCode.generate();
        assertEquals(LinkCode.DEFAULT_LENGTH, code.length());
        assertTrue(LinkCode.isValid(code));
        // Aucun caractere ambigu (0, O, 1, I, L).
        assertFalse(code.matches(".*[01OIL].*"));
    }

    @Test
    @DisplayName("respecte la longueur demandee")
    void respectsLength() {
        assertEquals(8, LinkCode.generate(8).length());
        assertEquals(12, LinkCode.generate(12).length());
    }

    @Test
    @DisplayName("refuse les longueurs hors bornes")
    void rejectsOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () -> LinkCode.generate(3));
        assertThrows(IllegalArgumentException.class, () -> LinkCode.generate(13));
    }

    @Test
    @DisplayName("isValid rejette null, trop court, minuscules et caracteres ambigus")
    void isValidRejectsBadInput() {
        assertFalse(LinkCode.isValid(null));
        assertFalse(LinkCode.isValid("ABC"));
        assertFalse(LinkCode.isValid("abcdef"));
        assertFalse(LinkCode.isValid("ABC0EF"));
        assertTrue(LinkCode.isValid("ABCD23"));
    }
}
