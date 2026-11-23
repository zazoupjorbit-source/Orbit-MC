package games.orbit.orbitstaff.api.application.port;

import games.orbit.orbitstaff.api.application.model.LinkCodeRecord;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistance de la liaison Minecraft &lt;-&gt; Discord et des codes temporaires. */
public interface DiscordLinkRepository {

    void saveCode(UUID playerUuid, String codeHash, Instant createdAt, Instant expiresAt);

    Optional<LinkCodeRecord> findByCodeHash(String codeHash);

    void incrementAttempts(long codeId);

    void markConsumed(long codeId, Instant now);

    void link(UUID playerUuid, String discordUserId, Instant now);

    Optional<String> findDiscordId(UUID playerUuid);

    void unlink(UUID playerUuid);
}
