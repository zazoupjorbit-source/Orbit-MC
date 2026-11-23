package games.orbit.orbitstaff.api.application.service;

import games.orbit.orbitstaff.api.application.model.LinkCodeRecord;
import games.orbit.orbitstaff.api.support.Fakes;
import games.orbit.orbitstaff.common.error.ValidationException;
import games.orbit.orbitstaff.common.util.Hashing;
import games.orbit.orbitstaff.common.util.LinkCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DiscordLinkService")
class DiscordLinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");
    private static final int TTL_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;

    private Fakes.FakeDiscordLinkRepository repo;
    private AuditService auditService;
    private DiscordLinkService service;

    private final UUID player = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repo = new Fakes.FakeDiscordLinkRepository();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        auditService = new AuditService(new Fakes.FakeAuditRepository(), new Fakes.SequentialIdSource(), clock);
        service = new DiscordLinkService(repo, auditService, clock, 6, TTL_MINUTES, MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("genere puis consomme un code, et lie le compte")
    void generateThenConsume() {
        String code = service.generateCode(player, "srv-main", "c");
        assertTrue(LinkCode.isValid(code));

        UUID linked = service.consumeCode(code, "discord-123", "srv-main", "c");
        assertEquals(player, linked);
        assertEquals("discord-123", service.status(player).orElseThrow());
    }

    @Test
    @DisplayName("un code expire est refuse")
    void expiredCodeRejected() {
        String code = service.generateCode(player, "srv-main", "c");

        Clock later = Clock.fixed(NOW.plus(Duration.ofMinutes(TTL_MINUTES + 1)), ZoneOffset.UTC);
        DiscordLinkService future = new DiscordLinkService(repo, auditService, later, 6, TTL_MINUTES, MAX_ATTEMPTS);

        assertThrows(ValidationException.class, () -> future.consumeCode(code, "d", "srv-main", "c"));
    }

    @Test
    @DisplayName("un code deja consomme ne peut pas etre reutilise")
    void singleUse() {
        String code = service.generateCode(player, "srv-main", "c");
        service.consumeCode(code, "discord-1", "srv-main", "c");
        assertThrows(ValidationException.class, () -> service.consumeCode(code, "discord-2", "srv-main", "c"));
    }

    @Test
    @DisplayName("un code depassant le nombre de tentatives est refuse")
    void tooManyAttemptsRejected() {
        String code = service.generateCode(player, "srv-main", "c");
        LinkCodeRecord rec = repo.findByCodeHash(Hashing.sha256Hex(code)).orElseThrow();
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            repo.incrementAttempts(rec.id());
        }
        assertThrows(ValidationException.class, () -> service.consumeCode(code, "d", "srv-main", "c"));
    }

    @Test
    @DisplayName("un code inconnu est refuse")
    void invalidCodeRejected() {
        assertThrows(ValidationException.class, () -> service.consumeCode("ZZZZZZ", "d", "srv-main", "c"));
    }

    @Test
    @DisplayName("la deliaison retire le lien")
    void unlinkRemovesLink() {
        String code = service.generateCode(player, "srv-main", "c");
        service.consumeCode(code, "discord-1", "srv-main", "c");
        service.unlink(player, "srv-main", "c");
        assertTrue(service.status(player).isEmpty());
    }
}
