package fr.orbit.mmo.player;

import fr.orbit.mmo.OrbitMMO;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Profils en YAML (un fichier par joueur). Lecture à la pré-connexion (thread async),
 * écriture sur un thread dédié à partir d'un instantané pris sur le thread principal.
 */
public final class PlayerStore {

    private final OrbitMMO plugin;
    private final File dir;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OrbitMMO-IO");
        t.setDaemon(true);
        return t;
    });

    public PlayerStore(OrbitMMO plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "players");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            plugin.getLogger().warning("Impossible de créer " + dir);
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveAll, 6000L, 6000L);
    }

    public PlayerData get(Player p) {
        return cache.computeIfAbsent(p.getUniqueId(), id -> read(id, p.getName()));
    }

    public PlayerData loaded(UUID id) {
        return cache.get(id);
    }

    public Collection<PlayerData> all() {
        return cache.values();
    }

    /** Appelé depuis AsyncPlayerPreLoginEvent. */
    public void preload(UUID id, String name) {
        cache.computeIfAbsent(id, k -> read(k, name));
    }

    public void unload(UUID id) {
        PlayerData d = cache.remove(id);
        if (d != null) {
            save(d);
        }
    }

    /** Efface l'avatar (nouvelle partie). */
    public PlayerData reset(Player p) {
        PlayerData fresh = new PlayerData(p.getUniqueId());
        fresh.name = p.getName();
        fresh.introDone = true;
        cache.put(p.getUniqueId(), fresh);
        save(fresh);
        return fresh;
    }

    public void save(PlayerData d) {
        YamlConfiguration y = new YamlConfiguration();
        d.save(y);
        String content = y.saveToString();
        File target = file(d.id);
        io.execute(() -> write(target, content));
    }

    public void saveAll() {
        for (PlayerData d : cache.values()) {
            save(d);
        }
    }

    public void shutdown() {
        saveAll();
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Sauvegarde des profils interrompue (délai dépassé).");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    private PlayerData read(UUID id, String name) {
        PlayerData d = new PlayerData(id);
        d.name = name;
        File f = file(id);
        if (f.isFile()) {
            d.load(YamlConfiguration.loadConfiguration(f));
        }
        return d;
    }

    private void write(File target, String content) {
        try {
            Path tmp = target.toPath().resolveSibling(target.getName() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Échec de la sauvegarde de " + target.getName(), e);
        }
    }
}
