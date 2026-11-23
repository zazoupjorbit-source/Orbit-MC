package fr.orbitversus.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Une arène = un nom + deux points d'apparition (équipe A / équipe B). */
public record Arena(
        String name, String world,
        double ax, double ay, double az, float ayaw,
        double bx, double by, double bz, float byaw
) {

    public Location spawnA() {
        return location(ax, ay, az, ayaw);
    }

    public Location spawnB() {
        return location(bx, by, bz, byaw);
    }

    /** Centre approximatif de l'arène (pour placer les spectateurs). */
    public Location center() {
        return location((ax + bx) / 2, (ay + by) / 2, (az + bz) / 2, ayaw);
    }

    private Location location(double x, double y, double z, float yaw) {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, 0f);
    }

    public boolean isValid() {
        return Bukkit.getWorld(world) != null;
    }
}
