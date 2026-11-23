package games.orbit.orbitstaff.common.spi;

/**
 * Reference opaque renvoyee par {@link EvidenceStorage#store} et persistee en base.
 *
 * @param reference identifiant opaque de stockage
 * @param hash      empreinte d'integrite du contenu
 * @param size      taille en octets
 */
public record EvidenceReference(
        String reference,
        String hash,
        long size) {
}
