package fr.orbit.extraction.raid;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
public final class ProfileStore {

    private final JavaPlugin plugin;
    private final File dir;
    private final Map<UUID, Profile> cache = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OrbitExtraction-IO");
        t.setDaemon(true);
        return t;
    });

    public ProfileStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "joueurs");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            plugin.getLogger().warning("Impossible de créer " + dir);
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveAll, 6000L, 6000L);
    }

    public Profile get(Player p) {
        Profile pr = cache.computeIfAbsent(p.getUniqueId(), id -> read(id, p.getName()));
        pr.name = p.getName();
        return pr;
    }

    public Profile loaded(UUID id) {
        return cache.get(id);
    }

    /** Appelé depuis AsyncPlayerPreLoginEvent. */
    public void preload(UUID id, String name) {
        cache.computeIfAbsent(id, k -> read(k, name));
    }

    public void unload(UUID id) {
        Profile pr = cache.remove(id);
        if (pr != null) {
            save(pr);
        }
    }

    public void save(Profile pr) {
        YamlConfiguration y = new YamlConfiguration();
        pr.save(y);
        String content = y.saveToString();
        File target = new File(dir, pr.id + ".yml");
        io.execute(() -> write(target, content));
    }

    public void saveAll() {
        for (Profile pr : cache.values()) {
            save(pr);
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

    private Profile read(UUID id, String name) {
        Profile pr = new Profile(id);
        pr.name = name;
        File f = new File(dir, id + ".yml");
        if (f.isFile()) {
            pr.load(YamlConfiguration.loadConfiguration(f));
        }
        return pr;
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
