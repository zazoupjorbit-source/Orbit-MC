package fr.orbit.devcore.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Écrit les fichiers de données hors du thread principal, dans l'ordre des demandes
 * (un seul thread), avec écriture atomique (fichier temporaire puis remplacement).
 */
public final class FileWriter {

    private final Logger logger;
    private final ExecutorService executor;

    public FileWriter(Logger logger) {
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "OrbitDevCore-IO");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Planifie l'écriture ; si le plugin s'arrête, écrit tout de suite. */
    public void writeAsync(Path file, String content) {
        try {
            executor.execute(() -> write(file, content));
        } catch (RejectedExecutionException stopped) {
            write(file, content);
        }
    }

    /** Écrit immédiatement sur le thread appelant (utilisé à l'arrêt). */
    public void writeNow(Path file, String content) {
        write(file, content);
    }

    private void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Impossible d'écrire " + file, e);
        }
    }

    /** Termine les écritures en attente (appelé dans onDisable). */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Des écritures de fichiers n'ont pas pu se terminer à l'arrêt.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
