package games.orbit.orbitstaff.core.net;

/**
 * L'API a renvoye une reponse d'erreur (statut non 2xx) avec un code metier.
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String errorCode;

    public ApiException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public int status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }
}
