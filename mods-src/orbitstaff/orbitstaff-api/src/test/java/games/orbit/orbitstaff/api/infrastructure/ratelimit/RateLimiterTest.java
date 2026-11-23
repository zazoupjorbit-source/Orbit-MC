package games.orbit.orbitstaff.api.infrastructure.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RateLimiter")
class RateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");

    @Test
    @DisplayName("autorise jusqu'au quota puis bloque dans la fenetre")
    void allowsUpToQuota() {
        RateLimiter limiter = new RateLimiter(3, 60, Clock.fixed(NOW, ZoneOffset.UTC));
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"), "le 4e appel depasse le quota");
    }

    @Test
    @DisplayName("les cles sont independantes")
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter(1, 60, Clock.fixed(NOW, ZoneOffset.UTC));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
    }

    @Test
    @DisplayName("refuse une configuration invalide")
    void rejectsInvalidConfig() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RateLimiter(0, 60, Clock.fixed(NOW, ZoneOffset.UTC)));
    }
}
