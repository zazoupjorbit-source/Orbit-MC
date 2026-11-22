package fr.orbit.mmo.quest;

import fr.orbit.mmo.OrbitMMO;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

/**
 * Points nommés (PNJ, objectifs, zones d'apparition), posés en jeu avec /quetes admin point &lt;nom&gt;.
 * Séparés des quêtes : quand la vraie map sera construite, on replace les points sans toucher aux quêtes.
 * Fichier : plugins/OrbitMMO/quest-points.yml.
 */
public final class QuestPoints {

    public record Point(String world, double x, double y, double z, float yaw, float pitch) {

        /** Nul si le monde n'est pas chargé. */
        public Location location() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, pitch);
        }
    }

    private final OrbitMMO plugin;
    private final File file;
    private final Map<String, Point> points = new TreeMap<>();

    public QuestPoints(OrbitMMO plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "quest-points.yml");
    }

    public void load() {
        points.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(k);
            if (s == null || s.getString("monde") == null) {
                continue;
            }
            points.put(k, new Point(s.getString("monde"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    (float) s.getDouble("yaw"), (float) s.getDouble("pitch")));
        }
    }

    public Point get(String name) {
        return name == null ? null : points.get(name);
    }

    /** Position du point si posé et si son monde est chargé, sinon nul. */
    public Location location(String name) {
        Point p = get(name);
        return p == null ? null : p.location();
    }

    public boolean has(String name) {
        return name != null && points.containsKey(name);
    }

    public Map<String, Point> all() {
        return Collections.unmodifiableMap(points);
    }

    public void set(String name, Location l) {
        points.put(name, new Point(l.getWorld().getName(), round(l.getX()), round(l.getY()), round(l.getZ()),
                (float) round(l.getYaw()), (float) round(l.getPitch())));
        save();
    }

    public boolean remove(String name) {
        boolean had = points.remove(name) != null;
        if (had) {
            save();
        }
        return had;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Point> e : points.entrySet()) {
            Point p = e.getValue();
            String k = e.getKey();
            y.set(k + ".monde", p.world());
            y.set(k + ".x", p.x());
            y.set(k + ".y", p.y());
            y.set(k + ".z", p.z());
            y.set(k + ".yaw", (double) p.yaw());
            y.set(k + ".pitch", (double) p.pitch());
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Échec de la sauvegarde de quest-points.yml", e);
        }
    }
}
