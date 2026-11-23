package games.orbit.orbitstaff.common.model;

import games.orbit.orbitstaff.common.enums.AuditAction;
import games.orbit.orbitstaff.common.enums.AuditSource;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Entree du journal d'audit.
 *
 * <p>Ne doit jamais contenir de secret (mot de passe, token, cle, cookie) ni le
 * contenu complet d'un fichier sensible.</p>
 *
 * @param eventId       identifiant unique de l'evenement
 * @param timestamp     horodatage UTC
 * @param serverId      serveur emetteur
 * @param actorUuid     acteur (vide si systeme)
 * @param targetUuid    cible (optionnelle)
 * @param action        action realisee
 * @param source        origine
 * @param oldValue      ancienne valeur (optionnelle, jamais un secret)
 * @param newValue      nouvelle valeur (optionnelle, jamais un secret)
 * @param reason        motif (optionnel)
 * @param success       resultat de l'action
 * @param correlationId identifiant de correlation
 */
public record AuditEvent(
        UUID eventId,
        Instant timestamp,
        String serverId,
        Optional<UUID> actorUuid,
        Optional<UUID> targetUuid,
        AuditAction action,
        AuditSource source,
        Optional<String> oldValue,
        Optional<String> newValue,
        Optional<String> reason,
        boolean success,
        String correlationId) {

    public AuditEvent {
        actorUuid = actorUuid == null ? Optional.empty() : actorUuid;
        targetUuid = targetUuid == null ? Optional.empty() : targetUuid;
        oldValue = oldValue == null ? Optional.empty() : oldValue;
        newValue = newValue == null ? Optional.empty() : newValue;
        reason = reason == null ? Optional.empty() : reason;
    }
}
