package fr.orbit.devcore.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Position sauvegardée par nom de monde : reste lisible même si le monde
 * n'est pas encore chargé au démarrage.
 */
public record StoredLocation(String world, double x, double y, double z, float yaw, float pitch) {

    public static StoredLocation of(Location location) {
        return new StoredLocation(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    /** @return la position, ou {@code null} si le monde n'est pas chargé. */
    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    public int chunkX() {
        return blockX() >> 4;
    }

    public int chunkZ() {
        return blockZ() >> 4;
    }

    public void write(ConfigurationSection section) {
        section.set("world", world);
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("yaw", (double) yaw);
        section.set("pitch", (double) pitch);
    }

    /** @return la position lue, ou {@code null} si la section est absente ou incomplète. */
    public static StoredLocation read(ConfigurationSection section) {
        if (section == null || !section.isString("world")) {
            return null;
        }
        return new StoredLocation(section.getString("world"),
                section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }
}
