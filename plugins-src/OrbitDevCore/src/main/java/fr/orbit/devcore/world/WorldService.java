package fr.orbit.devcore.world;

import fr.orbit.devcore.OrbitDevCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Mondes supplémentaires du serveur de dev (ex. un superflat).
 * Bukkit ne recharge pas tout seul un monde créé par plugin : ils sont listés dans
 * plugins/OrbitDevCore/worlds.yml et rechargés au démarrage.
 */
public final class WorldService {

    public enum Kind { FLAT, NORMAL, VOID }

    public static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");

    private final OrbitDevCore plugin;
    private final File file;
    private final Map<String, Kind> worlds = new LinkedHashMap<>();

    public WorldService(OrbitDevCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "worlds.yml");
    }

    /** Lit worlds.yml et charge chaque monde (le crée s'il n'existe pas encore sur le disque). */
    public void load() {
        worlds.clear();
        if (!file.exists()) {
            return;
        }
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("worlds");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            Kind kind = parseKind(root.getString(name + ".type", "flat"));
            if (kind == null || !NAME_PATTERN.matcher(name).matches()) {
                plugin.getLogger().warning("Monde ignoré (nom ou type invalide) : " + name);
                continue;
            }
            worlds.put(name, kind);
            if (Bukkit.getWorld(name) == null) {
                plugin.getLogger().info("Chargement du monde " + name + " (" + kind.name().toLowerCase(Locale.ROOT) + ")...");
                creator(name, kind).createWorld();
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("worlds");
        for (Map.Entry<String, Kind> entry : worlds.entrySet()) {
            root.set(entry.getKey() + ".type", entry.getValue().name().toLowerCase(Locale.ROOT));
        }
        plugin.fileWriter().writeAsync(file.toPath(), yaml.saveToString());
    }

    /** Crée (ou charge s'il existe déjà) un monde et l'ajoute à worlds.yml. Bloque le tick le temps de la génération. */
    public World create(String name, Kind kind) {
        World world = Bukkit.getWorld(name);
        if (world == null) {
            world = creator(name, kind).createWorld();
        }
        if (world != null) {
            worlds.put(name, kind);
            save();
        }
        return world;
    }

    private static WorldCreator creator(String name, Kind kind) {
        WorldCreator creator = new WorldCreator(name);
        if (kind == Kind.FLAT) {
            creator.type(WorldType.FLAT).generateStructures(false);
        } else if (kind == Kind.VOID) {
            creator.generator(new VoidGenerator()).generateStructures(false);
        }
        return creator;
    }

    /** Point d'arrivée dans un monde : son spawn, posé sur le bloc le plus haut. */
    public Location spawnOf(World world) {
        Location spawn = world.getSpawnLocation();
        int y = world.getHighestBlockYAt(spawn.getBlockX(), spawn.getBlockZ()) + 1;
        return new Location(world, spawn.getBlockX() + 0.5, y, spawn.getBlockZ() + 0.5, spawn.getYaw(), 0f);
    }

    public Map<String, Kind> managed() {
        return worlds;
    }

    public List<String> loadedNames() {
        List<String> names = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            names.add(world.getName());
        }
        return names;
    }

    public static Kind parseKind(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "flat", "superflat", "plat" -> Kind.FLAT;
            case "normal", "default" -> Kind.NORMAL;
            case "void", "vide" -> Kind.VOID;
            default -> null;
        };
    }
}
