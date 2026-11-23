package fr.naruto.core.jutsu;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Cooldowns par joueur et par jutsu. */
public final class CooldownManager {

    private final Map<UUID, Map<String, Long>> until = new ConcurrentHashMap<>();

    public boolean isReady(UUID player, String jutsuId) {
        Map<String, Long> m = until.get(player);
        return m == null || System.currentTimeMillis() >= m.getOrDefault(jutsuId, 0L);
    }

    public long remainingMs(UUID player, String jutsuId) {
        Map<String, Long> m = until.get(player);
        if (m == null) return 0;
        return Math.max(0, m.getOrDefault(jutsuId, 0L) - System.currentTimeMillis());
    }

    public void set(UUID player, String jutsuId, long cooldownMs) {
        until.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .put(jutsuId, System.currentTimeMillis() + cooldownMs);
    }

    public void clear(UUID player) { until.remove(player); }
}
