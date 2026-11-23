package games.orbit.orbitstaff.common.error;

/**
 * Donnee d'entree invalide (motif trop court, champ vide, valeur hors bornes...).
 */
public class ValidationException extends OrbitStaffException {

    public ValidationException(String message) {
        super("VALIDATION", message);
    }
}
