package games.orbit.orbitstaff.common.util;

import games.orbit.orbitstaff.common.OrbitStaffConstants;
import games.orbit.orbitstaff.common.error.ValidationException;

/**
 * Validateurs surs, reutilises par l'API, le plugin et le bot pour appliquer les
 * memes regles metier (motifs, notes, champs obligatoires).
 */
public final class Validation {

    private Validation() {
    }

    /**
     * Verifie qu'une chaine n'est ni nulle ni vide (apres trim).
     *
     * @return la valeur nettoyee
     * @throws ValidationException si vide
     */
    public static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException("Le champ '" + fieldName + "' est obligatoire");
        }
        return value.trim();
    }

    /**
     * Valide un motif de sanction / action selon les bornes de longueur communes.
     *
     * @return le motif nettoye
     * @throws ValidationException si vide, trop court ou trop long
     */
    public static String requireReason(String reason) {
        String cleaned = requireNonBlank(reason, "motif");
        if (cleaned.length() < OrbitStaffConstants.MIN_REASON_LENGTH) {
            throw new ValidationException(
                    "Le motif doit contenir au moins " + OrbitStaffConstants.MIN_REASON_LENGTH + " caracteres");
        }
        if (cleaned.length() > OrbitStaffConstants.MAX_REASON_LENGTH) {
            throw new ValidationException(
                    "Le motif ne doit pas depasser " + OrbitStaffConstants.MAX_REASON_LENGTH + " caracteres");
        }
        return cleaned;
    }

    /**
     * Valide le contenu d'une note interne.
     *
     * @return le contenu nettoye
     * @throws ValidationException si vide ou trop long
     */
    public static String requireNoteContent(String content) {
        String cleaned = requireNonBlank(content, "note");
        if (cleaned.length() > OrbitStaffConstants.MAX_NOTE_LENGTH) {
            throw new ValidationException(
                    "La note ne doit pas depasser " + OrbitStaffConstants.MAX_NOTE_LENGTH + " caracteres");
        }
        return cleaned;
    }
}
