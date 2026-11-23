package games.orbit.orbitstaff.core.net;

/**
 * L'API est injoignable (timeout, coupure reseau). Declenche le comportement
 * degrade cote plugin (lecture locale possible, actions critiques refusees
 * proprement), sans jamais bloquer le serveur.
 */
public class ApiUnavailableException extends RuntimeException {

    public ApiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
