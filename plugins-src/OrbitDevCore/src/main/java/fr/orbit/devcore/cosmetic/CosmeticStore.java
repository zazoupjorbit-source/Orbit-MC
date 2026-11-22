package fr.orbit.devcore.cosmetic;

import fr.orbit.devcore.OrbitDevCore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cosmétiques équipés, un petit fichier YAML par joueur.
 * <p>
 * Le dossier est réglable (cosmetics.data-folder) : en le pointant vers un dossier partagé par
 * le lobby et les instances, le joueur garde ses cosmétiques en changeant de serveur. Un joueur
 * n'étant que sur un serveur à la fois, deux serveurs n'écrivent jamais le même fichier en même temps.
 */
public final class CosmeticStore {

    private final OrbitDevCore plugin;
    private volatile File folder;

    public CosmeticStore(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    public void configure() {
        String path = plugin.getConfig().getString("cosmetics.data-folder", "");
        if (path == null || path.isBlank()) {
            folder = new File(plugin.getDataFolder(), "cosmetics");
        } else {
            File custom = new File(path);
            folder = custom.isAbsolute() ? custom : new File(plugin.getDataFolder(), path);
        }
    }

    public File folder() {
        return folder;
    }

    private File file(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }

    /** Lecture bloquante : à appeler hors du thread principal. */
    public Map<CosmeticType, String> load(UUID uuid) {
        Map<CosmeticType, String> selection = new EnumMap<>(CosmeticType.class);
        File file = file(uuid);
        if (!file.exists()) {
            return selection;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (CosmeticType type : CosmeticType.values()) {
            String id = yaml.getString(type.id());
            if (id != null && CosmeticCatalog.ID_PATTERN.matcher(id).matches()) {
                selection.put(type, id);
            }
        }
        return selection;
    }

    /** Écriture asynchrone, dans l'ordre des demandes. */
    public void save(UUID uuid, String playerName, Map<CosmeticType, String> selection) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", playerName);
        for (Map.Entry<CosmeticType, String> entry : selection.entrySet()) {
            yaml.set(entry.getKey().id(), entry.getValue());
        }
        Path path = file(uuid).toPath();
        plugin.fileWriter().writeAsync(path, yaml.saveToString());
    }
}
