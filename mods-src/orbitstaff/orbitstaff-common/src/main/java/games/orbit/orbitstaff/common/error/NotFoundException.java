package games.orbit.orbitstaff.common.error;

/**
 * Ressource introuvable (dossier, sanction, note, joueur...).
 */
public class NotFoundException extends OrbitStaffException {

    public NotFoundException(String resource, String id) {
        super("NOT_FOUND", resource + " introuvable : " + id);
    }
}
