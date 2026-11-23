package games.orbit.orbitstaff.api.application.service;

import games.orbit.orbitstaff.api.application.model.LinkCodeRecord;
import games.orbit.orbitstaff.api.application.port.DiscordLinkRepository;
import games.orbit.orbitstaff.common.enums.AuditAction;
import games.orbit.orbitstaff.common.enums.AuditSource;
import games.orbit.orbitstaff.common.error.ValidationException;
import games.orbit.orbitstaff.common.util.Hashing;
import games.orbit.orbitstaff.common.util.LinkCode;
import games.orbit.orbitstaff.common.util.Validation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service de liaison Minecraft &lt;-&gt; Discord.
 *
 * <p>Le code de liaison est aleatoire, a usage unique, expirant, limite en
 * tentatives. Seul son hash est stocke (jamais le code en clair).</p>
 */
@Service
public class DiscordLinkService {

    private final DiscordLinkRepository repository;
    private final AuditService auditService;
    private final Clock clock;
    private final int codeLength;
    private final int ttlMinutes;
    private final int maxAttempts;

    public DiscordLinkService(DiscordLinkRepository repository,
                              AuditService auditService,
                              Clock clock,
                              @Value("${orbitstaff.discord-link.code-length:6}") int codeLength,
                              @Value("${orbitstaff.discord-link.code-ttl-minutes:10}") int ttlMinutes,
                              @Value("${orbitstaff.discord-link.max-attempts:5}") int maxAttempts) {
        this.repository = repository;
        this.auditService = auditService;
        this.clock = clock;
        this.codeLength = codeLength;
        this.ttlMinutes = ttlMinutes;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Genere un code de liaison pour un joueur (appele depuis Minecraft).
     *
     * @return le code en clair, a montrer une seule fois au joueur
     */
    @Transactional
    public String generateCode(UUID playerUuid, String serverId, String correlationId) {
        String code = LinkCode.generate(codeLength);
        Instant now = Instant.now(clock);
        repository.saveCode(playerUuid, Hashing.sha256Hex(code), now, now.plus(Duration.ofMinutes(ttlMinutes)));

        auditService.record(AuditAction.DISCORD_LINK, AuditSource.MINECRAFT, serverId,
                Optional.of(playerUuid), Optional.empty(),
                Optional.empty(), Optional.of("code-generated"), Optional.empty(), true, correlationId);
        return code;
    }

    /**
     * Consomme un code depuis Discord et lie le compte.
     *
     * @param code          code saisi cote Discord
     * @param discordUserId identifiant Discord a lier
     * @return l'UUID du joueur lie
     * @throws ValidationException si le code est invalide, expire, deja consomme
     *                             ou si le nombre de tentatives est depasse
     */
    @Transactional
    public UUID consumeCode(String code, String discordUserId, String serverId, String correlationId) {
        String cleanCode = Validation.requireNonBlank(code, "code");
        String discord = Validation.requireNonBlank(discordUserId, "discordUserId");
        Instant now = Instant.now(clock);

        LinkCodeRecord record = repository.findByCodeHash(Hashing.sha256Hex(cleanCode))
                .orElseThrow(() -> fail(serverId, correlationId, "code-invalid"));

        if (record.consumed()) {
            throw fail(serverId, correlationId, "code-already-used");
        }
        if (record.attempts() >= maxAttempts) {
            throw fail(serverId, correlationId, "too-many-attempts");
        }
        if (!now.isBefore(record.expiresAt())) {
            repository.incrementAttempts(record.id());
            throw fail(serverId, correlationId, "code-expired");
        }

        repository.markConsumed(record.id(), now);
        repository.link(record.playerUuid(), discord, now);

        auditService.record(AuditAction.DISCORD_LINK, AuditSource.DISCORD, serverId,
                Optional.of(record.playerUuid()), Optional.empty(),
                Optional.empty(), Optional.of("linked"), Optional.empty(), true, correlationId);
        return record.playerUuid();
    }

    /** @return l'identifiant Discord lie a un joueur, s'il existe. */
    public Optional<String> status(UUID playerUuid) {
        return repository.findDiscordId(playerUuid);
    }

    /** Supprime la liaison d'un joueur. */
    @Transactional
    public void unlink(UUID playerUuid, String serverId, String correlationId) {
        repository.unlink(playerUuid);
        auditService.record(AuditAction.DISCORD_UNLINK, AuditSource.API, serverId,
                Optional.of(playerUuid), Optional.empty(),
                Optional.of("linked"), Optional.of("unlinked"), Optional.empty(), true, correlationId);
    }

    private ValidationException fail(String serverId, String correlationId, String detail) {
        auditService.record(AuditAction.DISCORD_LINK, AuditSource.DISCORD, serverId,
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(detail), Optional.empty(), false, correlationId);
        return new ValidationException("Liaison Discord refusee : " + detail);
    }
}
