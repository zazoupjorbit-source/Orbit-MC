package games.orbit.orbitstaff.common.model;

import games.orbit.orbitstaff.common.enums.EvidenceType;
import games.orbit.orbitstaff.common.enums.NoteVisibility;

import java.time.Instant;
import java.util.UUID;

/**
 * Preuve rattachee a un dossier.
 *
 * <p>Les gros fichiers ne sont jamais stockes dans MariaDB : seule une reference
 * de stockage ({@code storageReference}) et un {@code hash} d'integrite sont
 * conserves. Le contenu est gere par un {@code EvidenceStorage}.</p>
 *
 * @param id               identifiant unique
 * @param caseId           dossier rattache
 * @param authorUuid       auteur du depot
 * @param type             type de preuve
 * @param description      description courte
 * @param storageReference reference opaque vers le stockage (jamais un chemin brut)
 * @param hash             empreinte d'integrite (ex: sha-256)
 * @param visibility       niveau de sensibilite
 * @param createdAt        date de depot (UTC)
 * @param deleted          suppression logique
 */
public record Evidence(
        UUID id,
        UUID caseId,
        UUID authorUuid,
        EvidenceType type,
        String description,
        String storageReference,
        String hash,
        NoteVisibility visibility,
        Instant createdAt,
        boolean deleted) {
}
