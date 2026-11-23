package games.orbit.orbitstaff.common.util;

import games.orbit.orbitstaff.common.OrbitStaffConstants;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Genere les cles de compte de service ({@code oss_...}) remises au plugin et au bot.
 *
 * <p>La cle en clair n'est montree qu'une seule fois a la creation ; l'API n'en
 * conserve que le hash (voir {@link ServiceKeyHasher}).</p>
 */
public final class ServiceKeyGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ENTROPY_BYTES = 32; // 256 bits
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private ServiceKeyGenerator() {
    }

    /**
     * @return une nouvelle cle de service prefixee, ex: {@code oss_Xk3...}
     */
    public static String generate() {
        byte[] buffer = new byte[ENTROPY_BYTES];
        RANDOM.nextBytes(buffer);
        return OrbitStaffConstants.SERVICE_KEY_PREFIX + ENCODER.encodeToString(buffer);
    }

    /**
     * @return true si la chaine a la forme d'une cle de service (prefixe + corps non vide).
     */
    public static boolean hasValidShape(String key) {
        return key != null
                && key.startsWith(OrbitStaffConstants.SERVICE_KEY_PREFIX)
                && key.length() > OrbitStaffConstants.SERVICE_KEY_PREFIX.length();
    }
}
