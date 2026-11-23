package games.orbit.orbitstaff.common.error;

/**
 * Racine de la hierarchie d'exceptions metier OrbitStaff.
 *
 * <p>Chaque erreur porte un {@link #errorCode()} stable, exploitable par l'API
 * (mapping HTTP) comme par les interfaces (message localise). On evite ainsi les
 * {@code catch (Exception ignored)} et les erreurs silencieuses.</p>
 */
public abstract class OrbitStaffException extends RuntimeException {

    private final String errorCode;

    protected OrbitStaffException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected OrbitStaffException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** @return code d'erreur stable (ex: {@code VALIDATION}, {@code PERMISSION_DENIED}). */
    public String errorCode() {
        return errorCode;
    }
}
