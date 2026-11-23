package fr.orbit.practice;

import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Le monde du mode HCF, sans dépendance à OrbitHCF : le reste du Practice s'en sert pour laisser
 * ce monde tranquille (dégâts, morts, objets, menus). Inactif tant que le mode HCF n'est pas branché.
 */
public final class HcfZone {

    private static volatile String world;

    private HcfZone() {
    }

    static void set(String name) {
        world = name;
    }

    public static boolean active() {
        return world != null;
    }

    public static String worldName() {
        return world;
    }

    public static boolean is(World w) {
        String name = world;
        return name != null && w != null && w.getName().equals(name);
    }

    public static boolean is(Player p) {
        return p != null && is(p.getWorld());
    }
}
