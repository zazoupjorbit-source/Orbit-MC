package games.orbit.orbitstaff.common.util;

import games.orbit.orbitstaff.common.error.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Validation")
class ValidationTest {

    @Test
    @DisplayName("requireNonBlank nettoie et rejette le vide")
    void requireNonBlank() {
        assertEquals("x", Validation.requireNonBlank("  x  ", "f"));
        assertThrows(ValidationException.class, () -> Validation.requireNonBlank("   ", "f"));
        assertThrows(ValidationException.class, () -> Validation.requireNonBlank(null, "f"));
    }

    @Test
    @DisplayName("requireReason applique les bornes de longueur")
    void requireReason() {
        assertEquals("spam", Validation.requireReason("  spam  "));
        assertThrows(ValidationException.class, () -> Validation.requireReason("ab"));
        assertThrows(ValidationException.class, () -> Validation.requireReason("   "));
        String tooLong = "a".repeat(513);
        assertThrows(ValidationException.class, () -> Validation.requireReason(tooLong));
    }

    @Test
    @DisplayName("requireNoteContent rejette le vide et le trop long")
    void requireNoteContent() {
        assertEquals("note ok", Validation.requireNoteContent("note ok"));
        assertThrows(ValidationException.class, () -> Validation.requireNoteContent(""));
        String tooLong = "a".repeat(2001);
        assertThrows(ValidationException.class, () -> Validation.requireNoteContent(tooLong));
    }
}
