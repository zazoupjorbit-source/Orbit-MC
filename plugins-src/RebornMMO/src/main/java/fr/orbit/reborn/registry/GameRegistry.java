package fr.orbit.reborn.registry;

import fr.orbit.reborn.RebornMMO;
import fr.orbit.reborn.floor.FloorTheme;
import fr.orbit.reborn.registry.Defs.BossDef;
import fr.orbit.reborn.registry.Defs.DropDef;
import fr.orbit.reborn.registry.Defs.FloorDef;
import fr.orbit.reborn.registry.Defs.MobDef;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Monstres, boss et étages définis dans config.yml (rechargeables). */
public final class GameRegistry {

    private final RebornMMO plugin;
    private final Map<String, MobDef> mobs = new HashMap<>();
    private final Map<String, BossDef> bosses = new HashMap<>();
    private final TreeMap<Integer, FloorDef> floors = new TreeMap<>();
    private int maxFloor = 100;

    public GameRegistry(RebornMMO plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration c = plugin.getConfig();
        mobs.clear();
        bosses.clear();
        floors.clear();

        ConfigurationSection ms = c.getConfigurationSection("mobs");
        if (ms != null) {
            for (String id : ms.getKeys(false)) {
                ConfigurationSection s = ms.getConfigurationSection(id);
                EntityType type = entity(s == null ? null : s.getString("type"));
                if (s == null || type == null || !type.isAlive()) {
                    plugin.getLogger().warning("Monstre « " + id + " » ignoré : type d'entité invalide.");
                    continue;
                }
                DropDef drop = null;
                ConfigurationSection dr = s.getConfigurationSection("drop");
                if (dr != null) {
                    drop = new DropDef(dr.getString("name", "Matériau"), material(dr.getString("item"), Material.PAPER),
                            dr.getInt("sell", 5), dr.getDouble("chance", 0.3));
                }
                mobs.put(id, new MobDef(id, s.getString("name", id), type, s.getDouble("hp", 1), s.getDouble("damage", 1),
                        s.getDouble("speed", 1), s.getDouble("scale", 1), s.getDouble("xp", 1), s.getDouble("col", 1),
                        material(s.getString("weapon"), null), drop));
            }
        }

        ConfigurationSection bs = c.getConfigurationSection("bosses");
        if (bs != null) {
            for (String id : bs.getKeys(false)) {
                ConfigurationSection s = bs.getConfigurationSection(id);
                EntityType type = entity(s == null ? null : s.getString("type"));
                if (s == null || type == null || !type.isAlive()) {
                    plugin.getLogger().warning("Boss « " + id + " » ignoré : type d'entité invalide.");
                    continue;
                }
                bosses.put(id, new BossDef(id, s.getString("name", id), type, s.getDouble("scale", 2), s.getDouble("hp", 14),
                        s.getDouble("damage", 1.8), s.getDouble("speed", 1), Math.max(1, s.getInt("bars", 4)),
                        material(s.getString("weapon"), null), material(s.getString("final-weapon"), null),
                        s.getString("adds"), s.getInt("adds-per-bar", 2), s.getDouble("xp", 25), s.getDouble("col", 20),
                        s.getString("lab.kind", "weapon"), s.getString("lab.name", "Trophée de " + id),
                        material(s.getString("lab.item"), Material.LEATHER_CHESTPLATE), s.getInt("lab.value", 6)));
            }
        }

        maxFloor = Math.max(1, c.getInt("floors.max", 100));
        ConfigurationSection fs = c.getConfigurationSection("floors.list");
        if (fs != null) {
            for (String key : fs.getKeys(false)) {
                ConfigurationSection s = fs.getConfigurationSection(key);
                int n;
                try {
                    n = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (s == null) {
                    continue;
                }
                FloorTheme theme;
                try {
                    theme = FloorTheme.valueOf(s.getString("theme", "PLAINS").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    theme = FloorTheme.PLAINS;
                }
                List<Integer> lv = s.getIntegerList("levels");
                int min = lv.isEmpty() ? 1 : lv.get(0);
                int max = lv.size() < 2 ? min + 5 : lv.get(1);
                floors.put(n, new FloorDef(n, s.getString("name", "Étage " + n), s.getString("town", "Ville de l'étage " + n),
                        theme, min, max, s.getStringList("mobs"), s.getString("boss")));
            }
        }
        if (floors.isEmpty()) {
            floors.put(1, new FloorDef(1, "Étage 1", "Ville des Débuts", FloorTheme.PLAINS, 1, 5, List.copyOf(mobs.keySet()), null));
        }
        plugin.getLogger().info(mobs.size() + " monstres, " + bosses.size() + " boss, " + floors.size() + " étages définis (" + maxFloor + " au total).");
    }

    public MobDef mob(String id) {
        return id == null ? null : mobs.get(id);
    }

    public BossDef boss(String id) {
        return id == null ? null : bosses.get(id);
    }

    public Map<String, MobDef> mobs() {
        return mobs;
    }

    public int maxFloor() {
        return maxFloor;
    }

    /** Étage défini dans la config, sinon généré à partir du modèle des étages définis (thèmes et niveaux croissants). */
    public FloorDef floor(int n) {
        FloorDef def = floors.get(n);
        if (def != null) {
            return def;
        }
        int defined = floors.lastKey();
        FloorDef last = floors.get(defined);
        FloorDef template = floors.get(((n - 1) % defined) + 1);
        if (template == null) {
            template = last;
        }
        int span = 6;
        int min = last.maxLevel() + (n - defined - 1) * span;
        FloorTheme theme = FloorTheme.values()[(n - 1) % FloorTheme.values().length];
        return new FloorDef(n, theme.label + " de l'étage " + n, "Ville de l'étage " + n, theme, min, min + span,
                template.mobs(), template.boss());
    }

    private static EntityType entity(String name) {
        if (name == null) {
            return null;
        }
        return Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
    }

    private static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material m = Material.matchMaterial(name);
        return m == null ? fallback : m;
    }
}
