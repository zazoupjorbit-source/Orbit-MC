package games.orbit.orbitstaff.common.util;

import java.security.SecureRandom;

/**
 * Codes de liaison Minecraft &lt;-&gt; Discord (commande {@code /discord link}).
 *
 * <p>Le code est aleatoire, a usage unique, de courte duree de vie (gere cote
 * API), et tire d'un alphabet sans caracteres ambigus (pas de 0/O, 1/I/L) pour
 * eviter les erreurs de saisie. La generation utilise {@link SecureRandom}.</p>
 */
public final class LinkCode {

    /** Alphabet sans caracteres ambigus. */
    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    /** Longueur par defaut d'un code de liaison. */
    public static final int DEFAULT_LENGTH = 6;

    private static final SecureRandom RANDOM = new SecureRandom();

    private LinkCode() {
    }

    /** @return un code de liaison de longueur par defaut. */
    public static String generate() {
        return generate(DEFAULT_LENGTH);
    }

    /**
     * @param length longueur souhaitee (entre 4 et 12)
     * @return un code aleatoire en majuscules
     */
    public static String generate(int length) {
        if (length < 4 || length > 12) {
            throw new IllegalArgumentException("Longueur de code invalide : " + length);
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * @return true si {@code code} respecte le format (longueur et alphabet).
     */
    public static boolean isValid(String code) {
        if (code == null || code.length() < 4 || code.length() > 12) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            if (ALPHABET.indexOf(code.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
