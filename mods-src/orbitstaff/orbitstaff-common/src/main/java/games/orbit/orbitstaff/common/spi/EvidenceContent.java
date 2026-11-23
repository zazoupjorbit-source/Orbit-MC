package games.orbit.orbitstaff.common.spi;

/**
 * Contenu lu depuis {@link EvidenceStorage}.
 *
 * @param contentType type MIME
 * @param data        octets du contenu
 */
public record EvidenceContent(
        String contentType,
        byte[] data) {

    public EvidenceContent {
        if (data == null) {
            throw new IllegalArgumentException("data obligatoire");
        }
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}
