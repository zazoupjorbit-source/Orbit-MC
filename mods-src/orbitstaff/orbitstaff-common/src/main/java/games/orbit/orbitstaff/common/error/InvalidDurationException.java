package games.orbit.orbitstaff.common.error;

/**
 * Duree de sanction invalide ou non reconnue (ex: "abc", "0m", "5x").
 */
public class InvalidDurationException extends OrbitStaffException {

    public InvalidDurationException(String rawInput) {
        super("INVALID_DURATION", "Duree invalide : '" + rawInput + "'");
    }
}
