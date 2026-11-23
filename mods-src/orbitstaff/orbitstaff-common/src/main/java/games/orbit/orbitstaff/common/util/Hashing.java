package games.orbit.orbitstaff.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Fonctions de hachage generiques (integrite, empreintes de requetes, codes).
 *
 * <p>SHA-256 convient pour les entrees a haute entropie (cles, codes aleatoires,
 * empreintes). Pour des mots de passe a faible entropie, utiliser bcrypt/argon2.</p>
 */
public final class Hashing {

    private static final String ALGORITHM = "SHA-256";

    private Hashing() {
    }

    /**
     * @param input chaine a hacher (UTF-8)
     * @return empreinte SHA-256 en hexadecimal minuscule (64 caracteres)
     */
    public static String sha256Hex(String input) {
        if (input == null) {
            throw new IllegalArgumentException("input ne doit pas etre null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] out = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algorithme " + ALGORITHM + " indisponible", e);
        }
    }
}
