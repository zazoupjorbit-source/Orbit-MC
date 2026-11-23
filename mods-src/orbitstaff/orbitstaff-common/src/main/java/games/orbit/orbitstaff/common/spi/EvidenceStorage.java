package games.orbit.orbitstaff.common.spi;

import java.util.Optional;

/**
 * Abstraction de stockage des preuves.
 *
 * <p>Les gros fichiers ne sont jamais stockes dans MariaDB. En developpement on
 * utilise un stockage local abstrait ; en production un backend compatible S3.
 * Le code metier ne manipule que des references opaques.</p>
 */
public interface EvidenceStorage {

    /**
     * Stocke un contenu et renvoie sa reference.
     *
     * @param upload contenu et metadonnees
     * @return reference opaque a persister dans la base
     */
    EvidenceReference store(EvidenceUpload upload);

    /**
     * @param reference reference opaque
     * @return le contenu si present
     */
    Optional<EvidenceContent> read(String reference);

    /**
     * Supprime le contenu associe (la suppression logique en base reste geree
     * separement pour conserver la tracabilite).
     *
     * @param reference reference opaque
     */
    void delete(String reference);
}
