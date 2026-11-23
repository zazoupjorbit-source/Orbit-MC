package fr.orbitgames.hcf.core.timer;

import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Actions differees annulables (home, stuck, logout) :
 * le timer d'affichage vit dans le TimerService, l'action ici.
 */
public class WarmupManager {

    private record Warmup(String key, BukkitTask task, String cancelMessageKey) {
    }

    private final OrbitHCFCorePlugin plugin;
    private final Map<UUID, Map<String, Warmup>> warmups = new ConcurrentHashMap<>();

    public WarmupManager(OrbitHCFCorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Demarre un warmup. Remplace un warmup existant de meme cle.
     *
     * @param cancelMessageKey cle du message envoye si le warmup est annule (peut etre null)
     */
    public void start(Player player, String key, Duration duration, Runnable onComplete, String cancelMessageKey) {
        cancel(player.getUniqueId(), key, false);
        UUID uuid = player.getUniqueId();
        plugin.getTimerManager().setTimer(uuid, key, duration);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Map<String, Warmup> map = warmups.get(uuid);
            if (map != null) {
                map.remove(key);
            }
            plugin.getTimerManager().clearTimer(uuid, key);
            onComplete.run();
        }, Math.max(1L, duration.toMillis() / 50L));
        warmups.computeIfAbsent(uuid, k -> new HashMap<>()).put(key, new Warmup(key, task, cancelMessageKey));
    }

    public boolean has(UUID playerId, String key) {
        Map<String, Warmup> map = warmups.get(playerId);
        return map != null && map.containsKey(key);
    }

    public void cancel(UUID playerId, String key, boolean notify) {
        Map<String, Warmup> map = warmups.get(playerId);
        if (map == null) {
            return;
        }
        Warmup warmup = map.remove(key);
        if (warmup == null) {
            return;
        }
        warmup.task().cancel();
        plugin.getTimerManager().clearTimer(playerId, key);
        if (notify && warmup.cancelMessageKey() != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                plugin.getMessages().send(player, warmup.cancelMessageKey());
            }
        }
    }

    /** Annule tous les warmups du joueur (deconnexion, degats, mouvement). */
    public void cancelAll(UUID playerId, boolean notify) {
        Map<String, Warmup> map = warmups.get(playerId);
        if (map == null) {
            return;
        }
        for (String key : new HashMap<>(map).keySet()) {
            cancel(playerId, key, notify);
        }
        warmups.remove(playerId);
    }

    public boolean hasAny(UUID playerId) {
        Map<String, Warmup> map = warmups.get(playerId);
        return map != null && !map.isEmpty();
    }
}
