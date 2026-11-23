package games.orbit.orbitstaff.common.model;

/**
 * Home d'un joueur, expose via une integration (ex: EssentialsX).
 */
public record PlayerHome(
        String name,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch) {
}
