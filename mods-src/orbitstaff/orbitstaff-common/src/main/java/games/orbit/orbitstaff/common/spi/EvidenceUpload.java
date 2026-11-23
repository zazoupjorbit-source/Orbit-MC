package games.orbit.orbitstaff.common.spi;

/**
 * Contenu a stocker via {@link EvidenceStorage}.
 *
 * @param originalFileName nom de fichier d'origine (sera assaini)
 * @param contentType      type MIME declare
 * @param data             octets du contenu
 */
public record EvidenceUpload(
        String originalFileName,
        String contentType,
        byte[] data) {

    public EvidenceUpload {
        if (data == null) {
            throw new IllegalArgumentException("data obligatoire");
        }
        // Copie defensive : un record ne doit pas exposer le tableau interne mutable.
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    /** @return la taille du contenu en octets. */
    public int size() {
        return data.length;
    }
}
