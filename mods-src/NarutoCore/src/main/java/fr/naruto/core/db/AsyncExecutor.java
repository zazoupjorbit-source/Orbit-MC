package fr.naruto.core.db;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** I/O base de donnees hors thread principal ; retour sur le thread Bukkit via {@link #sync}. */
public final class AsyncExecutor {

    private final Plugin plugin;
    private final ExecutorService pool;

    public AsyncExecutor(Plugin plugin) {
        this.plugin = plugin;
        this.pool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "NarutoCore-DB");
            t.setDaemon(true);
            return t;
        });
    }

    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, pool);
    }

    public void sync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public void shutdown() {
        pool.shutdown();
    }
}
