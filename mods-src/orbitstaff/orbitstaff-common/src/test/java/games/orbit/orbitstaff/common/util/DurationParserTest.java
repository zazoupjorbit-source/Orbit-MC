package games.orbit.orbitstaff.common.util;

import games.orbit.orbitstaff.common.error.InvalidDurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DurationParser")
class DurationParserTest {

    @Test
    @DisplayName("analyse chaque unite supportee")
    void parsesEachUnit() {
        assertEquals(Duration.ofSeconds(45), DurationParser.parse("45s").duration());
        assertEquals(Duration.ofMinutes(30), DurationParser.parse("30m").duration());
        assertEquals(Duration.ofHours(2), DurationParser.parse("2h").duration());
        assertEquals(Duration.ofDays(3), DurationParser.parse("3d").duration());
        assertEquals(Duration.ofDays(14), DurationParser.parse("2w").duration());
        assertEquals(Duration.ofDays(30), DurationParser.parse("1mo").duration());
        assertEquals(Duration.ofDays(365), DurationParser.parse("1y").duration());
    }

    @Test
    @DisplayName("'mo' n'est pas confondu avec 'm'")
    void distinguishesMonthFromMinute() {
        assertEquals(Duration.ofMinutes(1), DurationParser.parse("1m").duration());
        assertEquals(Duration.ofDays(30), DurationParser.parse("1mo").duration());
    }

    @Test
    @DisplayName("reconnait permanent et son alias")
    void parsesPermanent() {
        assertTrue(DurationParser.parse("permanent").permanent());
        assertTrue(DurationParser.parse("perm").permanent());
        assertTrue(DurationParser.parse("permanent").asDuration().isEmpty());
    }

    @Test
    @DisplayName("tolere espaces et majuscules")
    void trimsAndLowercases() {
        assertEquals(Duration.ofDays(7), DurationParser.parse("  7D ").duration());
        assertTrue(DurationParser.parse("  PERMANENT ").permanent());
    }

    @Test
    @DisplayName("les durees temporaires ne sont pas permanentes")
    void temporaryIsNotPermanent() {
        ParsedDuration parsed = DurationParser.parse("1h");
        assertFalse(parsed.permanent());
        assertTrue(parsed.asDuration().isPresent());
    }

    @ParameterizedTest
    @DisplayName("rejette les entrees invalides")
    @ValueSource(strings = {"", "   ", "abc", "0m", "5x", "5", "mo", "10.5h", "-5m", "99999999999d", "m30"})
    void rejectsInvalid(String input) {
        assertThrows(InvalidDurationException.class, () -> DurationParser.parse(input));
    }

    @Test
    @DisplayName("rejette null")
    void rejectsNull() {
        assertThrows(InvalidDurationException.class, () -> DurationParser.parse(null));
    }
}
