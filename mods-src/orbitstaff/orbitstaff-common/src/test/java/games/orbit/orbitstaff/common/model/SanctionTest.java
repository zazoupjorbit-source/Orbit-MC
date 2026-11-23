package games.orbit.orbitstaff.common.model;

import games.orbit.orbitstaff.common.enums.AuditSource;
import games.orbit.orbitstaff.common.enums.SanctionStatus;
import games.orbit.orbitstaff.common.enums.SanctionType;
import games.orbit.orbitstaff.common.enums.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Sanction - expiration")
class SanctionTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");

    private Sanction sanction(SanctionType type, Optional<Instant> expiresAt) {
        return new Sanction(
                UUID.randomUUID(),
                "srv-main",
                UUID.randomUUID(),
                Optional.of(UUID.randomUUID()),
                type,
                SanctionStatus.ACTIVE,
                "motif de test",
                Severity.MEDIUM,
                NOW,
                NOW,
                expiresAt,
                AuditSource.MINECRAFT,
                Optional.empty(),
                Optional.empty());
    }

    @Test
    @DisplayName("une sanction sans expiration est permanente et jamais expiree")
    void permanentNeverExpires() {
        Sanction ban = sanction(SanctionType.BAN, Optional.empty());
        assertTrue(ban.isPermanent());
        assertFalse(ban.isExpiredAt(NOW.plus(Duration.ofDays(3650))));
    }

    @Test
    @DisplayName("une sanction temporaire expire a l'echeance")
    void temporaryExpiresAtDeadline() {
        Instant expiry = NOW.plus(Duration.ofHours(1));
        Sanction tempban = sanction(SanctionType.TEMPBAN, Optional.of(expiry));

        assertFalse(tempban.isPermanent());
        assertFalse(tempban.isExpiredAt(NOW));
        assertFalse(tempban.isExpiredAt(expiry.minusSeconds(1)));
        assertTrue(tempban.isExpiredAt(expiry));               // borne incluse
        assertTrue(tempban.isExpiredAt(expiry.plusSeconds(1)));
    }
}
