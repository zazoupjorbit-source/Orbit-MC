package fr.orbit.extraction.raid;

import fr.orbit.extraction.util.StoredLoc;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/** Réglage de la map (map.yml) : planque, points d'apparition, points d'extraction. Édité en jeu par /extraction. */
public final class RaidMap {

    public record Extract(String name, StoredLoc loc, double radius, int seconds) {
    }

    private final JavaPlugin plugin;
    private final File file;
    public StoredLoc hideout;
    public final List<StoredLoc> spawns = new ArrayList<>();
    public final Map<String, Extract> extracts = new LinkedHashMap<>();

    public RaidMap(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "map.yml");
    }

    public void load() {
        spawns.clear();
        extracts.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        hideout = StoredLoc.load(y.getConfigurationSection("planque"));
        ConfigurationSection sp = y.getConfigurationSection("apparitions");
        if (sp != null) {
            for (String k : sp.getKeys(false)) {
                StoredLoc l = StoredLoc.load(sp.getConfigurationSection(k));
                if (l != null) {
                    spawns.add(l);
                }
            }
        }
        ConfigurationSection ex = y.getConfigurationSection("extractions");
        if (ex != null) {
            for (String name : ex.getKeys(false)) {
                ConfigurationSection s = ex.getConfigurationSection(name);
                StoredLoc l = StoredLoc.load(s);
                if (l != null) {
                    extracts.put(name, new Extract(name, l, s.getDouble("rayon", 4), s.getInt("secondes", 8)));
                }
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        if (hideout != null) {
            hideout.save(y.createSection("planque"));
        }
        ConfigurationSection sp = y.createSection("apparitions");
        for (int i = 0; i < spawns.size(); i++) {
            spawns.get(i).save(sp.createSection(String.valueOf(i + 1)));
        }
        ConfigurationSection ex = y.createSection("extractions");
        for (Extract e : extracts.values()) {
            ConfigurationSection s = ex.createSection(e.name());
            e.loc().save(s);
            s.set("rayon", e.radius());
            s.set("secondes", e.seconds());
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Échec de la sauvegarde de map.yml", e);
        }
    }
}
