package games.orbit.orbitstaff.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Hachage et verification des cles de service.
 *
 * <p>Les cles sont a haute entropie (256 bits aleatoires), on utilise donc
 * SHA-256 : suffisant et rapide, la ou bcrypt serait reserve aux mots de passe
 * a faible entropie. Seul le hash est stocke cote API ; la comparaison est faite
 * en temps constant pour ne pas fuiter d'information par timing.</p>
 */
public final class ServiceKeyHasher {

    private ServiceKeyHasher() {
    }

    /**
     * @param rawKey cle en clair
     * @return hash hexadecimal minuscule (64 caracteres)
     */
    public static String hash(String rawKey) {
        if (rawKey == null) {
            throw new IllegalArgumentException("rawKey ne doit pas etre null");
        }
        return Hashing.sha256Hex(rawKey);
    }

    /**
     * Compare en temps constant une cle candidate au hash stocke.
     *
     * @param candidateRawKey cle presentee par le client
     * @param storedHash      hash hexadecimal enregistre
     * @return true si la cle correspond
     */
    public static boolean matches(String candidateRawKey, String storedHash) {
        if (candidateRawKey == null || storedHash == null) {
            return false;
        }
        byte[] expected = storedHash.getBytes(StandardCharsets.UTF_8);
        byte[] actual = hash(candidateRawKey).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
