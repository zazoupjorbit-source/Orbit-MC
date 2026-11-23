package games.orbit.orbitstaff.core.staff;

/**
 * Etat sauvegarde d'un membre du staff, pour restauration apres desactivation,
 * reconnexion, crash ou redemarrage (section 8). Donnee pure, serialisable.
 *
 * @param active           mode staff actif
 * @param vanished         invisible
 * @param previousGameMode mode de jeu a restaurer (ex: SURVIVAL)
 * @param inventoryData    inventaire du joueur avant le mode staff (base64, peut etre null)
 * @param world            monde de la position a restaurer
 * @param x                position X
 * @param y                position Y
 * @param z                position Z
 * @param yaw              orientation horizontale
 * @param pitch            orientation verticale
 */
public record StaffModeState(
        boolean active,
        boolean vanished,
        String previousGameMode,
        String inventoryData,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch) {
}
