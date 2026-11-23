package games.orbit.orbitstaff.api.application.service;

import games.orbit.orbitstaff.api.application.port.IdSource;
import games.orbit.orbitstaff.api.application.port.IdempotencyRepository;
import games.orbit.orbitstaff.api.application.port.PlayerRepository;
import games.orbit.orbitstaff.api.application.port.SanctionRepository;
import games.orbit.orbitstaff.common.dto.CreateSanctionRequest;
import games.orbit.orbitstaff.common.enums.AuditAction;
import games.orbit.orbitstaff.common.enums.SanctionStatus;
import games.orbit.orbitstaff.common.enums.SanctionType;
import games.orbit.orbitstaff.common.enums.Severity;
import games.orbit.orbitstaff.common.error.NotFoundException;
import games.orbit.orbitstaff.common.error.PermissionDeniedException;
import games.orbit.orbitstaff.common.error.ValidationException;
import games.orbit.orbitstaff.common.model.Sanction;
import games.orbit.orbitstaff.common.util.DurationParser;
import games.orbit.orbitstaff.common.util.Hashing;
import games.orbit.orbitstaff.common.util.ParsedDuration;
import games.orbit.orbitstaff.common.util.Validation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service metier des sanctions.
 *
 * <p>Garanties :</p>
 * <ul>
 *   <li><strong>Idempotence</strong> : une meme cle d'idempotence renvoie la
 *       sanction deja creee, jamais un doublon (retry reseau sur).</li>
 *   <li><strong>Validation stricte</strong> du motif et de la duree.</li>
 *   <li><strong>Permissions</strong> verifiees cote serveur selon le type.</li>
 *   <li>Aucun <strong>ban permanent automatique</strong> sans acteur humain.</li>
 * </ul>
 */
@Service
public class SanctionService {

    private final SanctionRepository sanctionRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final PlayerRepository playerRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final IdSource idSource;
    private final Clock clock;

    public SanctionService(SanctionRepository sanctionRepository,
                           IdempotencyRepository idempotencyRepository,
                           PlayerRepository playerRepository,
                           PermissionService permissionService,
                           AuditService auditService,
                           IdSource idSource,
                           Clock clock) {
        this.sanctionRepository = sanctionRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.playerRepository = playerRepository;
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.idSource = idSource;
        this.clock = clock;
    }

    /**
     * Cree une sanction (ou renvoie celle deja creee pour cette cle d'idempotence).
     *
     * @param request          requete validee en entree
     * @param serviceAccountId compte de service emetteur (portee de l'idempotence)
     * @param correlationId    identifiant de correlation pour l'audit
     * @return la sanction creee ou preexistante
     */
    @Transactional
    public Sanction create(CreateSanctionRequest request, long serviceAccountId, String correlationId) {
        String reason = Validation.requireReason(request.reason());
        SanctionType type = request.type();

        // --- Idempotence : si la cle a deja produit un resultat, on le renvoie ---
        String idempotencyKey = Validation.requireNonBlank(request.idempotencyKey(), "idempotencyKey");
        Optional<UUID> existing = idempotencyRepository.findResultPublicId(serviceAccountId, idempotencyKey);
        if (existing.isPresent()) {
            return sanctionRepository.findByPublicId(existing.get())
                    .orElseThrow(() -> new NotFoundException("Sanction", existing.get().toString()));
        }

        // --- Permissions ---
        String node = PermissionService.nodeForSanction(type);
        ParsedDuration duration = resolveDuration(type, request.rawDuration());
        if (request.actorUuid().isPresent()) {
            permissionService.require(request.actorUuid().get(), node);
        } else {
            // Action sans acteur humain : le systeme ne pose jamais de ban permanent seul.
            if (type == SanctionType.BAN && duration.permanent()) {
                throw new PermissionDeniedException("human-validation-required");
            }
        }

        Instant now = Instant.now(clock);
        Optional<Instant> expiresAt = duration.asDuration().map(now::plus);

        playerRepository.ensurePlayer(request.targetUuid(), null, now);

        Sanction sanction = new Sanction(
                idSource.newUuid(),
                request.serverId(),
                request.targetUuid(),
                request.actorUuid(),
                type,
                SanctionStatus.ACTIVE,
                reason,
                Severity.MEDIUM,
                now,
                now,
                expiresAt,
                request.source(),
                Optional.empty(),
                Optional.empty());

        Sanction saved = sanctionRepository.save(sanction, serviceAccountId);

        String requestHash = Hashing.sha256Hex(
                serviceAccountId + "|" + type + "|" + request.targetUuid() + "|" + reason);
        idempotencyRepository.save(serviceAccountId, idempotencyKey, requestHash, saved.id(), now);

        auditService.record(AuditAction.SANCTION_CREATE, request.source(), request.serverId(),
                request.actorUuid(), Optional.of(request.targetUuid()),
                Optional.empty(), Optional.of(type.name()), Optional.of(reason), true, correlationId);

        return saved;
    }

    /** Historique disciplinaire d'un joueur. */
    public List<Sanction> history(UUID targetUuid) {
        return sanctionRepository.findByTarget(targetUuid);
    }

    /**
     * Annule une sanction (statut REVOKED). Requiert la permission adaptee et un motif.
     */
    @Transactional
    public void revoke(UUID publicId, UUID actorUuid, String rawReason, String serverId, String correlationId) {
        String reason = Validation.requireReason(rawReason);
        Sanction sanction = sanctionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new NotFoundException("Sanction", publicId.toString()));

        // Ban/tempban -> permission ban ; sinon la permission du type suffit.
        String node = switch (sanction.type()) {
            case BAN, TEMPBAN -> "orbitstaff.unban";
            default -> PermissionService.nodeForSanction(sanction.type());
        };
        permissionService.require(actorUuid, node);

        sanctionRepository.markRevoked(publicId, actorUuid, reason, Instant.now(clock));

        auditService.record(AuditAction.SANCTION_REVOKE, games.orbit.orbitstaff.common.enums.AuditSource.API,
                serverId, Optional.of(actorUuid), Optional.of(sanction.targetUuid()),
                Optional.of(sanction.status().name()), Optional.of(SanctionStatus.REVOKED.name()),
                Optional.of(reason), true, correlationId);
    }

    /**
     * Determine la duree effective selon le type et l'entree brute, avec validation stricte.
     */
    private ParsedDuration resolveDuration(SanctionType type, Optional<String> rawDuration) {
        if (rawDuration.isPresent() && !rawDuration.get().isBlank()) {
            if (!type.supportsDuration()) {
                throw new ValidationException("Le type " + type + " n'accepte pas de duree");
            }
            return DurationParser.parse(rawDuration.get());
        }
        // Pas de duree fournie : sanction permanente (valide pour BAN ; mute/tempban restent possibles).
        return ParsedDuration.PERMANENT;
    }
}
