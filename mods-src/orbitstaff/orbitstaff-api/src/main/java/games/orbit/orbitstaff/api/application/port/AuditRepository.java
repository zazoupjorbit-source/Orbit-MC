package games.orbit.orbitstaff.api.application.port;

import games.orbit.orbitstaff.common.model.AuditEvent;

/** Ecriture du journal d'audit. Ne doit jamais faire echouer l'action metier. */
public interface AuditRepository {
    void record(AuditEvent event);
}
