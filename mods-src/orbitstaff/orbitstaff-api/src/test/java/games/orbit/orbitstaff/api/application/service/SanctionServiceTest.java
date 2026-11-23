package games.orbit.orbitstaff.api.application.service;

import games.orbit.orbitstaff.api.support.Fakes;
import games.orbit.orbitstaff.common.dto.CreateSanctionRequest;
import games.orbit.orbitstaff.common.enums.AuditAction;
import games.orbit.orbitstaff.common.enums.AuditSource;
import games.orbit.orbitstaff.common.enums.SanctionStatus;
import games.orbit.orbitstaff.common.enums.SanctionType;
import games.orbit.orbitstaff.common.enums.StaffRole;
import games.orbit.orbitstaff.common.error.InvalidDurationException;
import games.orbit.orbitstaff.common.error.PermissionDeniedException;
import games.orbit.orbitstaff.common.error.ValidationException;
import games.orbit.orbitstaff.common.model.Sanction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SanctionService")
class SanctionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");
    private static final long SERVICE_ACCOUNT = 1L;

    private Fakes.FakeSanctionRepository sanctions;
    private Fakes.FakeIdempotencyRepository idempotency;
    private Fakes.FakeAuditRepository audit;
    private Fakes.FakeStaffRepository staff;
    private SanctionService service;

    private final UUID target = UUID.randomUUID();
    private final UUID moderator = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final UUID helper = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sanctions = new Fakes.FakeSanctionRepository();
        idempotency = new Fakes.FakeIdempotencyRepository();
        audit = new Fakes.FakeAuditRepository();
        staff = new Fakes.FakeStaffRepository();
        staff.assign(moderator, StaffRole.MODERATOR);
        staff.assign(admin, StaffRole.ADMINISTRATOR);
        staff.assign(helper, StaffRole.HELPER);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        PermissionService permissions = new PermissionService(staff, new Fakes.FakePermissionCatalog());
        AuditService auditService = new AuditService(audit, new Fakes.SequentialIdSource(), clock);
        service = new SanctionService(sanctions, idempotency, new Fakes.FakePlayerRepository(),
                permissions, auditService, new Fakes.SequentialIdSource(), clock);
    }

    private CreateSanctionRequest request(UUID actor, SanctionType type, String duration, String key) {
        return new CreateSanctionRequest("srv-main", target, Optional.ofNullable(actor), type,
                "Motif de test valide", Optional.ofNullable(duration), AuditSource.MINECRAFT, key);
    }

    @Test
    @DisplayName("cree une sanction temporaire avec expiration calculee et audit")
    void createsTemporarySanction() {
        Sanction s = service.create(request(moderator, SanctionType.MUTE, "30m", "k1"), SERVICE_ACCOUNT, "corr");

        assertEquals(SanctionStatus.ACTIVE, s.status());
        assertEquals(Optional.of(NOW.plus(Duration.ofMinutes(30))), s.expiresAt());
        assertEquals(1, sanctions.count());
        assertEquals(1, audit.countOf(AuditAction.SANCTION_CREATE));
    }

    @Test
    @DisplayName("un retry avec la meme cle d'idempotence ne cree pas de doublon")
    void idempotentRetryDoesNotDuplicate() {
        Sanction first = service.create(request(moderator, SanctionType.MUTE, "1h", "same-key"), SERVICE_ACCOUNT, "c");
        Sanction second = service.create(request(moderator, SanctionType.MUTE, "1h", "same-key"), SERVICE_ACCOUNT, "c");

        assertEquals(first.id(), second.id());
        assertEquals(1, sanctions.count(), "une seule sanction doit exister");
    }

    @Test
    @DisplayName("un ban sans duree est permanent")
    void permanentBanHasNoExpiry() {
        Sanction s = service.create(request(admin, SanctionType.BAN, null, "kban"), SERVICE_ACCOUNT, "c");
        assertTrue(s.isPermanent());
        assertTrue(s.expiresAt().isEmpty());
    }

    @Test
    @DisplayName("un helper ne peut pas bannir")
    void helperCannotBan() {
        assertThrows(PermissionDeniedException.class,
                () -> service.create(request(helper, SanctionType.BAN, null, "kx"), SERVICE_ACCOUNT, "c"));
        assertEquals(0, sanctions.count());
    }

    @Test
    @DisplayName("le systeme ne pose jamais de ban permanent sans acteur humain")
    void systemCannotAutoPermanentBan() {
        assertThrows(PermissionDeniedException.class,
                () -> service.create(request(null, SanctionType.BAN, null, "ksys"), SERVICE_ACCOUNT, "c"));
    }

    @Test
    @DisplayName("une duree invalide est rejetee")
    void invalidDurationRejected() {
        assertThrows(InvalidDurationException.class,
                () -> service.create(request(moderator, SanctionType.MUTE, "5x", "kd"), SERVICE_ACCOUNT, "c"));
    }

    @Test
    @DisplayName("un motif trop court est rejete")
    void shortReasonRejected() {
        CreateSanctionRequest bad = new CreateSanctionRequest("srv-main", target, Optional.of(moderator),
                SanctionType.WARNING, "ab", Optional.empty(), AuditSource.MINECRAFT, "kr");
        assertThrows(ValidationException.class, () -> service.create(bad, SERVICE_ACCOUNT, "c"));
    }

    @Test
    @DisplayName("l'historique renvoie les sanctions de la cible")
    void historyReturnsSanctions() {
        service.create(request(moderator, SanctionType.WARNING, null, "h1"), SERVICE_ACCOUNT, "c");
        service.create(request(moderator, SanctionType.MUTE, "1h", "h2"), SERVICE_ACCOUNT, "c");
        assertEquals(2, service.history(target).size());
    }

    @Test
    @DisplayName("une duree fournie sur un type sans duree est refusee")
    void durationOnNonDurationTypeRejected() {
        assertThrows(ValidationException.class,
                () -> service.create(request(moderator, SanctionType.KICK, "1h", "kk"), SERVICE_ACCOUNT, "c"));
    }

    @Test
    @DisplayName("revoquer une sanction requiert la permission et change le statut")
    void revokeChangesStatus() {
        Sanction s = service.create(request(admin, SanctionType.BAN, null, "krev"), SERVICE_ACCOUNT, "c");
        service.revoke(s.id(), admin, "Erreur de moderation", "srv-main", "c");
        assertEquals(SanctionStatus.REVOKED, sanctions.findByPublicId(s.id()).orElseThrow().status());
        assertEquals(1, audit.countOf(AuditAction.SANCTION_REVOKE));
    }

    @Test
    @DisplayName("un helper ne peut pas revoquer un ban")
    void helperCannotRevokeBan() {
        Sanction s = service.create(request(admin, SanctionType.BAN, null, "krev2"), SERVICE_ACCOUNT, "c");
        assertThrows(PermissionDeniedException.class,
                () -> service.revoke(s.id(), helper, "raison valable", "srv-main", "c"));
    }
}
