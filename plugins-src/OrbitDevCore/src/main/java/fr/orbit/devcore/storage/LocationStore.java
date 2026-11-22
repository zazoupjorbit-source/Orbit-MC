package fr.orbit.devcore.storage;

import fr.orbit.devcore.OrbitDevCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Spawn et warps, stockés dans plugins/OrbitDevCore/locations.yml. */
public final class LocationStore {

    public static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private final OrbitDevCore plugin;
    private final File file;
    private final Map<String, Warp> warps = new TreeMap<>();
    private StoredLocation spawn;

    public LocationStore(OrbitDevCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "locations.yml");
    }

    public void load() {
        warps.clear();
        spawn = null;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        spawn = StoredLocation.read(yaml.getConfigurationSection("spawn"));
        ConfigurationSection section = yaml.getConfigurationSection("warps");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection warpSection = section.getConfigurationSection(key);
            StoredLocation location = warpSection == null ? null
                    : StoredLocation.read(warpSection.getConfigurationSection("location"));
            if (location == null) {
                plugin.getLogger().warning("Warp ignoré (position invalide) : " + key);
                continue;
            }
            Material icon = parseIcon(warpSection.getString("icon"));
            warps.put(key.toLowerCase(Locale.ROOT), new Warp(key, location, icon));
        }
    }

    /** Sauvegarde : sérialise sur le thread appelant, écrit le fichier en arrière-plan. */
    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        if (spawn != null) {
            spawn.write(yaml.createSection("spawn"));
        }
        ConfigurationSection section = yaml.createSection("warps");
        for (Warp warp : warps.values()) {
            ConfigurationSection warpSection = section.createSection(warp.name());
            warpSection.set("icon", warp.icon().name());
            warp.location().write(warpSection.createSection("location"));
        }
        plugin.fileWriter().writeAsync(file.toPath(), yaml.saveToString());
    }

    /** @return le spawn défini, sinon le spawn du monde principal, sinon {@code null}. */
    public Location spawn() {
        if (spawn != null) {
            Location location = spawn.toLocation();
            if (location != null) {
                return location;
            }
        }
        if (Bukkit.getWorlds().isEmpty()) {
            return null;
        }
        World main = Bukkit.getWorlds().get(0);
        return main.getSpawnLocation().add(0.5, 0, 0.5);
    }

    public void setSpawn(Location location) {
        spawn = StoredLocation.of(location);
        save();
    }

    public Collection<Warp> warps() {
        return Collections.unmodifiableCollection(warps.values());
    }

    public Warp warp(String name) {
        return warps.get(name.toLowerCase(Locale.ROOT));
    }

    public void setWarp(String name, Location location, Material icon) {
        Warp previous = warp(name);
        String displayName = previous != null ? previous.name() : name;
        Material finalIcon = icon != null ? icon : previous != null ? previous.icon() : defaultIcon();
        warps.put(name.toLowerCase(Locale.ROOT), new Warp(displayName, StoredLocation.of(location), finalIcon));
        save();
    }

    public boolean deleteWarp(String name) {
        boolean removed = warps.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    private Material parseIcon(String value) {
        Material material = value == null ? null : Material.matchMaterial(value);
        return material != null && material.isItem() && !material.isAir() ? material : defaultIcon();
    }

    private Material defaultIcon() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("menu.default-warp-icon", "ENDER_PEARL"));
        return material != null && material.isItem() && !material.isAir() ? material : Material.ENDER_PEARL;
    }
}
