package fr.orbit.practice.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Une arène : un nom, son thème et ses deux points d'apparition (équipe A à l'ouest, équipe B à l'est).
 * Le centre et le rayon servent aux spectateurs et au nettoyage entre deux manches.
 */
public record Arena(String name, String theme, String world,
                    double ax, double ay, double az, float ayaw,
                    double bx, double by, double bz, float byaw, int radius) {

    public Location spawnA(int index) {
        return location(ax, ay, az + offset(index), ayaw);
    }

    public Location spawnB(int index) {
        return location(bx, by, bz + offset(index), byaw);
    }

    /** Les coéquipiers apparaissent côte à côte, pas l'un dans l'autre. */
    private static double offset(int index) {
        return index == 0 ? 0 : (index % 2 == 1 ? 2.0 : -2.0) * ((index + 1) / 2);
    }

    public Location center() {
        return location((ax + bx) / 2, Math.max(ay, by) + 6, (az + bz) / 2, 0f);
    }

    private Location location(double x, double y, double z, float yaw) {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, 0f);
    }

    public boolean isValid() {
        return Bukkit.getWorld(world) != null;
    }
}
