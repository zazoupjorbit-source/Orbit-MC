package games.orbit.orbitstaff.api.support;

import games.orbit.orbitstaff.api.application.model.LinkCodeRecord;
import games.orbit.orbitstaff.api.application.model.ServiceAccount;
import games.orbit.orbitstaff.api.application.model.ServiceAccountKind;
import games.orbit.orbitstaff.api.application.port.AuditRepository;
import games.orbit.orbitstaff.api.application.port.DiscordLinkRepository;
import games.orbit.orbitstaff.api.application.port.IdSource;
import games.orbit.orbitstaff.api.application.port.IdempotencyRepository;
import games.orbit.orbitstaff.api.application.port.NoteRepository;
import games.orbit.orbitstaff.api.application.port.PermissionCatalog;
import games.orbit.orbitstaff.api.application.port.PlayerRepository;
import games.orbit.orbitstaff.api.application.port.SanctionRepository;
import games.orbit.orbitstaff.api.application.port.ServiceAccountRepository;
import games.orbit.orbitstaff.api.application.port.StaffRepository;
import games.orbit.orbitstaff.common.enums.SanctionStatus;
import games.orbit.orbitstaff.common.enums.StaffRole;
import games.orbit.orbitstaff.common.model.AuditEvent;
import games.orbit.orbitstaff.common.model.InternalNote;
import games.orbit.orbitstaff.common.model.Sanction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Doublures en memoire des ports, pour tester la logique metier sans base.
 */
public final class Fakes {

    private Fakes() {
    }

    /** Generateur d'UUID deterministe. */
    public static final class SequentialIdSource implements IdSource {
        private final AtomicLong counter = new AtomicLong(1);

        @Override
        public UUID newUuid() {
            return new UUID(0L, counter.getAndIncrement());
        }
    }

    public static final class FakeSanctionRepository implements SanctionRepository {
        private final Map<UUID, Sanction> byId = new LinkedHashMap<>();

        @Override
        public Sanction save(Sanction sanction, long serviceAccountId) {
            byId.put(sanction.id(), sanction);
            return sanction;
        }

        @Override
        public Optional<Sanction> findByPublicId(UUID publicId) {
            return Optional.ofNullable(byId.get(publicId));
        }

        @Override
        public List<Sanction> findByTarget(UUID targetUuid) {
            return byId.values().stream()
                    .filter(s -> s.targetUuid().equals(targetUuid))
                    .sorted(Comparator.comparing(Sanction::createdAt).reversed())
                    .toList();
        }

        @Override
        public void markRevoked(UUID publicId, UUID revokedBy, String reason, Instant now) {
            Sanction s = byId.get(publicId);
            if (s != null) {
                byId.put(publicId, new Sanction(s.id(), s.serverId(), s.targetUuid(), s.actorUuid(),
                        s.type(), SanctionStatus.REVOKED, s.reason(), s.severity(), s.createdAt(),
                        s.startsAt(), s.expiresAt(), s.source(), s.evidenceId(), s.previousLinked()));
            }
        }

        public int count() {
            return byId.size();
        }
    }

    public static final class FakeIdempotencyRepository implements IdempotencyRepository {
        private final Map<String, UUID> store = new HashMap<>();

        private static String key(long said, String k) {
            return said + "|" + k;
        }

        @Override
        public Optional<UUID> findResultPublicId(long serviceAccountId, String idempotencyKey) {
            return Optional.ofNullable(store.get(key(serviceAccountId, idempotencyKey)));
        }

        @Override
        public void save(long serviceAccountId, String idempotencyKey, String requestHash, UUID resultPublicId, Instant now) {
            store.put(key(serviceAccountId, idempotencyKey), resultPublicId);
        }
    }

    public static final class FakePlayerRepository implements PlayerRepository {
        public final List<UUID> ensured = new ArrayList<>();

        @Override
        public void ensurePlayer(UUID uuid, String username, Instant now) {
            ensured.add(uuid);
        }
    }

    public static final class FakeAuditRepository implements AuditRepository {
        public final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void record(AuditEvent event) {
            events.add(event);
        }

        public long countOf(games.orbit.orbitstaff.common.enums.AuditAction action) {
            return events.stream().filter(e -> e.action() == action).count();
        }
    }

    public static final class FakeStaffRepository implements StaffRepository {
        private final Map<UUID, StaffRole> roles = new HashMap<>();

        public void assign(UUID uuid, StaffRole role) {
            roles.put(uuid, role);
        }

        @Override
        public Optional<StaffRole> findActiveRole(UUID staffUuid) {
            return Optional.ofNullable(roles.get(staffUuid));
        }
    }

    /** Reproduit les seuils de poids de la migration V2. */
    public static final class FakePermissionCatalog implements PermissionCatalog {
        @Override
        public boolean hasPermission(StaffRole role, String node) {
            return role.weight() >= minWeight(node);
        }

        private static int minWeight(String node) {
            return switch (node) {
                case "orbitstaff.staffmode", "orbitstaff.inspect", "orbitstaff.warn",
                     "orbitstaff.notes.view", "orbitstaff.notes.create" -> 1;
                case "orbitstaff.mute", "orbitstaff.kick", "orbitstaff.freeze", "orbitstaff.tempban",
                     "orbitstaff.inventory.view", "orbitstaff.enderchest.view", "orbitstaff.homes.view",
                     "orbitstaff.evidence.view", "orbitstaff.evidence.create" -> 2;
                case "orbitstaff.inventory.modify", "orbitstaff.enderchest.modify", "orbitstaff.homes.delete",
                     "orbitstaff.permissions.view", "orbitstaff.audit.view" -> 3;
                case "orbitstaff.ban", "orbitstaff.unban", "orbitstaff.notes.delete",
                     "orbitstaff.permissions.modify", "orbitstaff.evidence.delete" -> 4;
                case "orbitstaff.admin" -> 5;
                default -> Integer.MAX_VALUE;
            };
        }
    }

    public static final class FakeNoteRepository implements NoteRepository {
        private final Map<UUID, InternalNote> byId = new LinkedHashMap<>();

        @Override
        public InternalNote save(InternalNote note) {
            byId.put(note.id(), note);
            return note;
        }

        @Override
        public Optional<InternalNote> findByPublicId(UUID publicId) {
            return Optional.ofNullable(byId.get(publicId));
        }

        @Override
        public List<InternalNote> findActiveByTarget(UUID targetUuid) {
            return byId.values().stream()
                    .filter(n -> n.targetUuid().equals(targetUuid) && !n.deleted())
                    .sorted(Comparator.comparing(InternalNote::createdAt).reversed())
                    .toList();
        }

        @Override
        public void softDelete(UUID publicId, Instant now) {
            InternalNote n = byId.get(publicId);
            if (n != null) {
                byId.put(publicId, new InternalNote(n.id(), n.targetUuid(), n.authorUuid(),
                        n.content(), n.visibility(), n.createdAt(), true));
            }
        }
    }

    public static final class FakeDiscordLinkRepository implements DiscordLinkRepository {
        private static final class Code {
            final long id;
            final UUID player;
            final String hash;
            int attempts;
            final Instant expires;
            Instant consumed;

            Code(long id, UUID player, String hash, Instant expires) {
                this.id = id;
                this.player = player;
                this.hash = hash;
                this.expires = expires;
            }
        }

        private final AtomicLong seq = new AtomicLong(1);
        private final List<Code> codes = new ArrayList<>();
        private final Map<UUID, String> links = new HashMap<>();

        @Override
        public void saveCode(UUID playerUuid, String codeHash, Instant createdAt, Instant expiresAt) {
            codes.add(new Code(seq.getAndIncrement(), playerUuid, codeHash, expiresAt));
        }

        @Override
        public Optional<LinkCodeRecord> findByCodeHash(String codeHash) {
            return codes.stream()
                    .filter(c -> c.hash.equals(codeHash))
                    .findFirst()
                    .map(c -> new LinkCodeRecord(c.id, c.player, c.hash, c.attempts, c.expires, c.consumed != null));
        }

        @Override
        public void incrementAttempts(long codeId) {
            codes.stream().filter(c -> c.id == codeId).forEach(c -> c.attempts++);
        }

        @Override
        public void markConsumed(long codeId, Instant now) {
            codes.stream().filter(c -> c.id == codeId).forEach(c -> c.consumed = now);
        }

        @Override
        public void link(UUID playerUuid, String discordUserId, Instant now) {
            links.put(playerUuid, discordUserId);
        }

        @Override
        public Optional<String> findDiscordId(UUID playerUuid) {
            return Optional.ofNullable(links.get(playerUuid));
        }

        @Override
        public void unlink(UUID playerUuid) {
            links.remove(playerUuid);
        }
    }

    /** Depot de comptes de service pour tester le filtre d'authentification. */
    public static final class FakeServiceAccountRepository implements ServiceAccountRepository {
        private final Map<String, ServiceAccount> byHash = new HashMap<>();
        public final List<Long> touched = new ArrayList<>();

        public void register(String keyHash, long id, String serverKey, ServiceAccountKind kind) {
            byHash.put(keyHash, new ServiceAccount(id, 1L, serverKey, kind));
        }

        @Override
        public Optional<ServiceAccount> findActiveByKeyHash(String keyHash) {
            return Optional.ofNullable(byHash.get(keyHash));
        }

        @Override
        public void touch(long serviceAccountId, Instant now) {
            touched.add(serviceAccountId);
        }
    }
}
