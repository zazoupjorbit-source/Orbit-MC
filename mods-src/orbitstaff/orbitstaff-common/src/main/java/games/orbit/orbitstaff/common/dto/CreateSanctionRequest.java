package games.orbit.orbitstaff.common.dto;

import games.orbit.orbitstaff.common.enums.AuditSource;
import games.orbit.orbitstaff.common.enums.SanctionType;

import java.util.Optional;
import java.util.UUID;

/**
 * Requete de creation d'une sanction, emise depuis Minecraft ou Discord.
 *
 * <p>La {@code idempotencyKey} garantit qu'un retry reseau ne cree pas de
 * doublon (section 6). La duree est fournie sous forme brute (ex: "7d",
 * "permanent") et validee par le service via {@code DurationParser}.</p>
 *
 * @param serverId       serveur emetteur
 * @param targetUuid     joueur cible
 * @param actorUuid      auteur (vide si systeme)
 * @param type           type de sanction
 * @param reason         motif brut (valide par le service)
 * @param rawDuration    duree brute si applicable (vide sinon)
 * @param source         origine de l'action
 * @param idempotencyKey cle d'idempotence unique de l'action
 */
public record CreateSanctionRequest(
        String serverId,
        UUID targetUuid,
        Optional<UUID> actorUuid,
        SanctionType type,
        String reason,
        Optional<String> rawDuration,
        AuditSource source,
        String idempotencyKey) {

    public CreateSanctionRequest {
        actorUuid = actorUuid == null ? Optional.empty() : actorUuid;
        rawDuration = rawDuration == null ? Optional.empty() : rawDuration;
    }
}
