package games.orbit.orbitstaff.api.application.service;

import games.orbit.orbitstaff.api.application.port.AuditRepository;
import games.orbit.orbitstaff.api.application.port.IdSource;
import games.orbit.orbitstaff.common.enums.AuditAction;
import games.orbit.orbitstaff.common.enums.AuditSource;
import games.orbit.orbitstaff.common.model.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Ecrit les evenements du journal d'audit.
 *
 * <p>Une defaillance d'ecriture d'audit ne doit jamais faire echouer l'action
 * metier : l'erreur est journalisee (WARN) mais non propagee. On ne masque pas
 * l'erreur pour autant (elle est visible dans les logs applicatifs).</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditRepository auditRepository;
    private final IdSource idSource;
    private final Clock clock;

    public AuditService(AuditRepository auditRepository, IdSource idSource, Clock clock) {
        this.auditRepository = auditRepository;
        this.idSource = idSource;
        this.clock = clock;
    }

    /**
     * Enregistre un evenement d'audit.
     */
    public void record(AuditAction action,
                       AuditSource source,
                       String serverId,
                       Optional<UUID> actor,
                       Optional<UUID> target,
                       Optional<String> oldValue,
                       Optional<String> newValue,
                       Optional<String> reason,
                       boolean success,
                       String correlationId) {
        AuditEvent event = new AuditEvent(
                idSource.newUuid(),
                Instant.now(clock),
                serverId,
                actor,
                target,
                action,
                source,
                oldValue,
                newValue,
                reason,
                success,
                correlationId);
        try {
            auditRepository.record(event);
        } catch (RuntimeException e) {
            log.warn("Echec d'ecriture d'un evenement d'audit ({}), action non interrompue", action, e);
        }
    }
}
