package fr.orbit.mmo.quest;

import fr.orbit.mmo.OrbitMMO;
import org.bukkit.configuration.file.YamlConfiguration;

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

/** Lecture / écriture de l'avancement des quêtes (même principe que PlayerStore : écriture sur un thread dédié). */
public final class QuestStore {

    private final OrbitMMO plugin;
    private final File dir;
    private final Map<UUID, QuestProgress> cache = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OrbitMMO-Quetes-IO");
        t.setDaemon(true);
        return t;
    });

    public QuestStore(OrbitMMO plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "quests-joueurs");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            plugin.getLogger().warning("Impossible de créer " + dir);
        }
    }

    public QuestProgress get(UUID id) {
        return cache.computeIfAbsent(id, this::read);
    }

    /** Appelé depuis AsyncPlayerPreLoginEvent. */
    public void preload(UUID id) {
        cache.computeIfAbsent(id, this::read);
    }

    public void unload(UUID id) {
        QuestProgress p = cache.remove(id);
        if (p != null) {
            save(p);
        }
    }

    public void save(QuestProgress p) {
        YamlConfiguration y = new YamlConfiguration();
        p.save(y);
        p.dirty = false;
        String content = y.saveToString();
        File target = new File(dir, p.id + ".yml");
        io.execute(() -> write(target, content));
    }

    public void saveDirty() {
        for (QuestProgress p : cache.values()) {
            if (p.dirty) {
                save(p);
            }
        }
    }

    public void shutdown() {
        for (QuestProgress p : cache.values()) {
            save(p);
        }
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Sauvegarde des quêtes interrompue (délai dépassé).");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private QuestProgress read(UUID id) {
        QuestProgress p = new QuestProgress(id);
        File f = new File(dir, id + ".yml");
        if (f.isFile()) {
            p.load(YamlConfiguration.loadConfiguration(f));
        }
        return p;
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
            plugin.getLogger().log(Level.SEVERE, "Échec de la sauvegarde des quêtes " + target.getName(), e);
        }
    }
}
