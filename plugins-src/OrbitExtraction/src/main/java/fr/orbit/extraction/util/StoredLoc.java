package fr.orbit.extraction.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/** Position enregistrée par nom de monde : résolue au moment de l'usage (le monde peut être chargé après le plugin). */
public record StoredLoc(String world, double x, double y, double z, float yaw, float pitch) {

    public static StoredLoc of(Location l) {
        return new StoredLoc(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** null si le monde n'est pas chargé. */
    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    public void save(ConfigurationSection s) {
        s.set("monde", world);
        s.set("x", x);
        s.set("y", y);
        s.set("z", z);
        s.set("yaw", (double) yaw);
        s.set("pitch", (double) pitch);
    }

    public static StoredLoc load(ConfigurationSection s) {
        if (s == null || s.getString("monde") == null) {
            return null;
        }
        return new StoredLoc(s.getString("monde"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    public String pretty() {
        return String.format(Locale.ROOT, "%s %.0f %.0f %.0f", world, x, y, z);
    }
}
