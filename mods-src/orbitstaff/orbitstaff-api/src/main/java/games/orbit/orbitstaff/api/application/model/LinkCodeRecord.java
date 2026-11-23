package games.orbit.orbitstaff.api.application.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Enregistrement d'un code de liaison Discord (hash stocke, jamais le code en clair).
 *
 * @param id         identifiant interne
 * @param playerUuid joueur demandeur
 * @param codeHash   hash du code
 * @param attempts   nombre de tentatives de consommation deja effectuees
 * @param expiresAt  expiration
 * @param consumed   true si deja consomme
 */
public record LinkCodeRecord(
        long id,
        UUID playerUuid,
        String codeHash,
        int attempts,
        Instant expiresAt,
        boolean consumed) {
}
