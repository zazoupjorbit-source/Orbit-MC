package games.orbit.orbitstaff.core.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Execute les appels API hors du thread principal Paper, puis renvoie le
 * resultat sur le thread principal pour manipuler l'etat Bukkit en securite.
 *
 * <p>Garantit qu'aucune requete reseau ne bloque le serveur (section 24).</p>
 */
public final class ApiExecutor {

    private final Plugin plugin;

    public ApiExecutor(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @param apiCall   appel bloquant (execute en asynchrone)
     * @param onSuccess callback sur le thread principal
     * @param onError   callback d'erreur sur le thread principal
     */
    public <T> void call(Supplier<T> apiCall, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                T result = apiCall.get();
                Bukkit.getScheduler().runTask(plugin, () -> onSuccess.accept(result));
            } catch (Throwable t) {
                Bukkit.getScheduler().runTask(plugin, () -> onError.accept(t));
            }
        });
    }

    /** Variante pour une action sans valeur de retour. */
    public void run(Runnable apiCall, Runnable onSuccess, Consumer<Throwable> onError) {
        call(() -> {
            apiCall.run();
            return null;
        }, ignored -> onSuccess.run(), onError);
    }
}
