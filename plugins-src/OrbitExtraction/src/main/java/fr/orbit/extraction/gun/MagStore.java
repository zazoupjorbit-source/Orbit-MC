package fr.orbit.extraction.gun;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Munitions dans le chargeur de chaque exemplaire d'arme, par identifiant unique.
 * Gardé hors de l'item : modifier l'item tenu à chaque tir relancerait l'animation de prise en main.
 * L'arme peut changer de main, tomber à la mort, partir à la planque : son chargeur la suit.
 */
public final class MagStore {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Integer> mags = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public MagStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "chargeurs.yml");
    }

    public void load() {
        mags.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) {
            mags.put(k, y.getInt(k));
        }
    }

    public int get(String uid) {
        return uid == null ? 0 : mags.getOrDefault(uid, 0);
    }

    public void set(String uid, int n) {
        if (uid != null) {
            mags.put(uid, Math.max(0, n));
            dirty = true;
        }
    }

    /** Écrit le fichier (appelé en asynchrone par la tâche périodique, en synchrone à l'arrêt). */
    public void save() {
        if (!dirty) {
            return;
        }
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        mags.forEach(y::set);
        try {
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.writeString(tmp.toPath(), y.saveToString(), StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            dirty = true;
            plugin.getLogger().log(Level.SEVERE, "Échec de la sauvegarde des chargeurs", e);
        }
    }
}
