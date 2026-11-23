package games.orbit.orbitstaff.common.error;

/**
 * Action refusee faute de permission suffisante.
 *
 * <p>Doit toujours etre verifiee cote serveur, jamais uniquement en masquant un
 * element d'interface.</p>
 */
public class PermissionDeniedException extends OrbitStaffException {

    public PermissionDeniedException(String permission) {
        super("PERMISSION_DENIED", "Permission requise : " + permission);
    }
}
