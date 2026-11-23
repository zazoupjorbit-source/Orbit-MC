package games.orbit.orbitstaff.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ServiceKeyHasher & ServiceKeyGenerator")
class ServiceKeyHasherTest {

    @Test
    @DisplayName("le hash est deterministe et long de 64 hex")
    void hashIsDeterministic() {
        String key = "oss_exemple";
        String h1 = ServiceKeyHasher.hash(key);
        String h2 = ServiceKeyHasher.hash(key);
        assertEquals(h1, h2);
        assertEquals(64, h1.length());
        assertTrue(h1.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("des cles differentes donnent des hash differents")
    void differentKeysDifferentHashes() {
        assertNotEquals(ServiceKeyHasher.hash("oss_a"), ServiceKeyHasher.hash("oss_b"));
    }

    @Test
    @DisplayName("matches valide la bonne cle et rejette les autres")
    void matchesWorks() {
        String raw = ServiceKeyGenerator.generate();
        String stored = ServiceKeyHasher.hash(raw);
        assertTrue(ServiceKeyHasher.matches(raw, stored));
        assertFalse(ServiceKeyHasher.matches(raw + "x", stored));
        assertFalse(ServiceKeyHasher.matches(null, stored));
        assertFalse(ServiceKeyHasher.matches(raw, null));
    }

    @Test
    @DisplayName("les cles generees sont prefixees, valides et uniques")
    void generatedKeysAreWellFormed() {
        String k1 = ServiceKeyGenerator.generate();
        String k2 = ServiceKeyGenerator.generate();
        assertTrue(k1.startsWith("oss_"));
        assertTrue(ServiceKeyGenerator.hasValidShape(k1));
        assertNotEquals(k1, k2);
        assertFalse(ServiceKeyGenerator.hasValidShape("oss_"));
        assertFalse(ServiceKeyGenerator.hasValidShape("nope"));
        assertFalse(ServiceKeyGenerator.hasValidShape(null));
    }
}
