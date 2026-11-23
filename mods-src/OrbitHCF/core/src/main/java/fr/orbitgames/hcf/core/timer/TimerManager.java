package fr.orbitgames.hcf.core.timer;

import fr.orbitgames.hcf.api.service.TimerService;
import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import fr.orbitgames.hcf.core.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Moteur centralise de timers : cooldowns joueurs (persistants ou non)
 * et timers globaux. Les timers persistants survivent aux redemarrages.
 */
public class TimerManager implements TimerService {

    private static class TimerEntry {
        long expiry;            // epoch millis, 0 si en pause
        long pausedRemaining;   // millis restants si en pause, 0 sinon
        boolean persistent;

        TimerEntry(long expiry, long pausedRemaining, boolean persistent) {
            this.expiry = expiry;
            this.pausedRemaining = pausedRemaining;
            this.persistent = persistent;
        }

        boolean isPaused() {
            return pausedRemaining > 0L;
        }

        boolean isActive(long now) {
            return isPaused() || expiry > now;
        }
    }

    private final OrbitHCFCorePlugin plugin;
    private final Database database;

    private final Map<UUID, Map<String, TimerEntry>> playerTimers = new ConcurrentHashMap<>();
    private final Map<String, Long> globalTimers = new ConcurrentHashMap<>();

    private BukkitTask cleanupTask;

    public TimerManager(OrbitHCFCorePlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void loadAll() {
        String prefix = database.getTablePrefix();
        long now = System.currentTimeMillis();
        database.query("SELECT uuid, timer_key, expiry, paused_remaining FROM " + prefix + "timers", rs -> {
            while (rs.next()) {
                long expiry = rs.getLong(3);
                long paused = rs.getLong(4);
                if (paused <= 0L && expiry <= now) {
                    continue;
                }
                UUID uuid = UUID.fromString(rs.getString(1));
                playerTimers.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                        .put(rs.getString(2), new TimerEntry(expiry, paused, true));
            }
            return null;
        });
        database.query("SELECT timer_key, expiry FROM " + prefix + "global_timers", rs -> {
            while (rs.next()) {
                long expiry = rs.getLong(2);
                if (expiry > now) {
                    globalTimers.put(rs.getString(1), expiry);
                }
            }
            return null;
        });
    }

    public void start() {
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanup, 20L * 60L, 20L * 60L);
    }

    public void stop() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        saveAll();
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        playerTimers.values().forEach(map -> map.entrySet().removeIf(e -> !e.getValue().isActive(now)));
        playerTimers.entrySet().removeIf(e -> e.getValue().isEmpty());
        globalTimers.entrySet().removeIf(e -> e.getValue() <= now);
    }

    public void saveAll() {
        String prefix = database.getTablePrefix();
        database.update("DELETE FROM " + prefix + "timers");
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<String, TimerEntry>> byPlayer : playerTimers.entrySet()) {
            for (Map.Entry<String, TimerEntry> byKey : byPlayer.getValue().entrySet()) {
                TimerEntry entry = byKey.getValue();
                if (entry.persistent && entry.isActive(now)) {
                    database.update("REPLACE INTO " + prefix
                                    + "timers (uuid, timer_key, expiry, paused_remaining) VALUES (?, ?, ?, ?)",
                            byPlayer.getKey().toString(), byKey.getKey(), entry.expiry, entry.pausedRemaining);
                }
            }
        }
        database.update("DELETE FROM " + prefix + "global_timers");
        for (Map.Entry<String, Long> entry : globalTimers.entrySet()) {
            if (entry.getValue() > now) {
                database.update("REPLACE INTO " + prefix + "global_timers (timer_key, expiry) VALUES (?, ?)",
                        entry.getKey(), entry.getValue());
            }
        }
    }

    // ------------------------------------------------------------------
    // TimerService
    // ------------------------------------------------------------------

    @Override
    public void setTimer(UUID playerId, String key, Duration duration, boolean persistent) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            clearTimer(playerId, key);
            return;
        }
        playerTimers.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(key, new TimerEntry(System.currentTimeMillis() + duration.toMillis(), 0L, persistent));
    }

    @Override
    public Duration getRemaining(UUID playerId, String key) {
        Map<String, TimerEntry> map = playerTimers.get(playerId);
        if (map == null) {
            return Duration.ZERO;
        }
        TimerEntry entry = map.get(key);
        if (entry == null) {
            return Duration.ZERO;
        }
        if (entry.isPaused()) {
            return Duration.ofMillis(entry.pausedRemaining);
        }
        long remaining = entry.expiry - System.currentTimeMillis();
        return remaining > 0L ? Duration.ofMillis(remaining) : Duration.ZERO;
    }

    @Override
    public boolean isActive(UUID playerId, String key) {
        Map<String, TimerEntry> map = playerTimers.get(playerId);
        return map != null && map.containsKey(key) && map.get(key).isActive(System.currentTimeMillis());
    }

    @Override
    public void clearTimer(UUID playerId, String key) {
        Map<String, TimerEntry> map = playerTimers.get(playerId);
        if (map != null) {
            map.remove(key);
        }
    }

    @Override
    public void pauseTimer(UUID playerId, String key) {
        Map<String, TimerEntry> map = playerTimers.get(playerId);
        if (map == null) {
            return;
        }
        TimerEntry entry = map.get(key);
        if (entry != null && !entry.isPaused()) {
            long remaining = entry.expiry - System.currentTimeMillis();
            if (remaining > 0L) {
                entry.pausedRemaining = remaining;
                entry.expiry = 0L;
            } else {
                map.remove(key);
            }
        }
    }

    @Override
    public void resumeTimer(UUID playerId, String key) {
        Map<String, TimerEntry> map = playerTimers.get(playerId);
        if (map == null) {
            return;
        }
        TimerEntry entry = map.get(key);
        if (entry != null && entry.isPaused()) {
            entry.expiry = System.currentTimeMillis() + entry.pausedRemaining;
            entry.pausedRemaining = 0L;
        }
    }

    @Override
    public boolean isPaused(UUID playerId, String key) {
        Map<String, TimerEntry> map = playerTimers.get(playerId);
        return map != null && map.containsKey(key) && map.get(key).isPaused();
    }

    @Override
    public Map<String, Duration> getActiveTimers(UUID playerId) {
        Map<String, TimerEntry> map = playerTimers.get(playerId);
        if (map == null) {
            return Map.of();
        }
        Map<String, Duration> result = new HashMap<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, TimerEntry> entry : map.entrySet()) {
            if (entry.getValue().isActive(now)) {
                result.put(entry.getKey(), getRemaining(playerId, entry.getKey()));
            }
        }
        return result;
    }

    @Override
    public void setGlobalTimer(String key, Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            clearGlobalTimer(key);
            return;
        }
        globalTimers.put(key, System.currentTimeMillis() + duration.toMillis());
    }

    @Override
    public Duration getGlobalRemaining(String key) {
        Long expiry = globalTimers.get(key);
        if (expiry == null) {
            return Duration.ZERO;
        }
        long remaining = expiry - System.currentTimeMillis();
        return remaining > 0L ? Duration.ofMillis(remaining) : Duration.ZERO;
    }

    @Override
    public boolean isGlobalActive(String key) {
        Long expiry = globalTimers.get(key);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    @Override
    public void clearGlobalTimer(String key) {
        globalTimers.remove(key);
    }
}
