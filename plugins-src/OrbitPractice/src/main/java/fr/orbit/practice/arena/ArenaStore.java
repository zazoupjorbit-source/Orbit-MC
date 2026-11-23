package fr.orbit.practice.arena;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Liste des arènes, gardée dans plugins/OrbitPractice/arenes.yml. */
public final class ArenaStore {

    private final Plugin plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenes.yml");
    }

    public void load() {
        arenas.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("arenes");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) {
                continue;
            }
            arenas.put(name.toLowerCase(), new Arena(name, s.getString("theme", "?"), s.getString("monde", "arenes"),
                    s.getDouble("a.x"), s.getDouble("a.y"), s.getDouble("a.z"), (float) s.getDouble("a.yaw"),
                    s.getDouble("b.x"), s.getDouble("b.y"), s.getDouble("b.z"), (float) s.getDouble("b.yaw"),
                    s.getInt("rayon", 24)));
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Arena a : arenas.values()) {
            String p = "arenes." + a.name() + ".";
            yaml.set(p + "theme", a.theme());
            yaml.set(p + "monde", a.world());
            yaml.set(p + "rayon", a.radius());
            yaml.set(p + "a.x", a.ax());
            yaml.set(p + "a.y", a.ay());
            yaml.set(p + "a.z", a.az());
            yaml.set(p + "a.yaw", a.ayaw());
            yaml.set(p + "b.x", a.bx());
            yaml.set(p + "b.y", a.by());
            yaml.set(p + "b.z", a.bz());
            yaml.set(p + "b.yaw", a.byaw());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("arenes.yml : écriture impossible : " + e.getMessage());
        }
    }

    public void put(Arena arena) {
        arenas.put(arena.name().toLowerCase(), arena);
    }

    public Arena get(String name) {
        return arenas.get(name.toLowerCase());
    }

    public Collection<Arena> all() {
        return arenas.values();
    }

    /** Une arène libre tirée au hasard, ou null si toutes sont prises. */
    public Arena randomFree(Set<String> inUse) {
        return randomFree(inUse, false);
    }

    /** Arène libre au hasard : une plateforme de Sumo si {@code sumo}, sinon une arène fermée. */
    public Arena randomFree(Set<String> inUse, boolean sumo) {
        List<Arena> free = new ArrayList<>();
        for (Arena a : arenas.values()) {
            if (a.isValid() && !inUse.contains(a.name().toLowerCase())
                    && ArenaBuilder.SUMO_THEME.equals(a.theme()) == sumo) {
                free.add(a);
            }
        }
        return free.isEmpty() ? null : free.get(ThreadLocalRandom.current().nextInt(free.size()));
    }
}
