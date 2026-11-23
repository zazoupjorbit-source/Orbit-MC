package fr.orbitgames.hcf.events.sotw;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.TimerKeys;
import fr.orbitgames.hcf.api.event.SotwEndEvent;
import fr.orbitgames.hcf.api.event.SotwStartEvent;
import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.events.OrbitHCFEventsPlugin;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SOTW : PvP desactive, pas de deathban ni de perte de DTR.
 * Chaque joueur peut retirer volontairement sa protection (/sotw off).
 * Le timer global est persistant (restaure apres redemarrage par le Core).
 */
public class SotwManager {

    private static final String OPTED_OUT_KEY = "sotw_opted_out";

    private final OrbitHCFEventsPlugin plugin;
    private final Set<UUID> optedOut = ConcurrentHashMap.newKeySet();
    private boolean wasActive;

    public SotwManager(OrbitHCFEventsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(Duration duration) {
        OrbitHCF.timers().setGlobalTimer(TimerKeys.GLOBAL_SOTW, duration);
        optedOut.clear();
        saveOptedOut();
        wasActive = true;
        Bukkit.getPluginManager().callEvent(new SotwStartEvent(duration));
        plugin.getMessages().broadcastRaw("sotw.started", "temps", DurationFormat.compact(duration));
    }

    public void stop() {
        OrbitHCF.timers().clearGlobalTimer(TimerKeys.GLOBAL_SOTW);
        end();
    }

    private void end() {
        if (!wasActive) {
            return;
        }
        wasActive = false;
        optedOut.clear();
        saveOptedOut();
        Bukkit.getPluginManager().callEvent(new SotwEndEvent());
        plugin.getMessages().broadcastRaw("sotw.ended");
    }

    /** Detecte l'expiration naturelle du timer global. */
    public void tick() {
        boolean active = isActive();
        if (wasActive && !active) {
            end();
        } else if (active) {
            wasActive = true;
        }
    }

    public boolean isActive() {
        return OrbitHCF.timers() != null && OrbitHCF.timers().isGlobalActive(TimerKeys.GLOBAL_SOTW);
    }

    public Duration getRemaining() {
        return OrbitHCF.timers() != null
                ? OrbitHCF.timers().getGlobalRemaining(TimerKeys.GLOBAL_SOTW)
                : Duration.ZERO;
    }

    public boolean isProtected(UUID playerId) {
        return isActive() && !optedOut.contains(playerId);
    }

    public boolean removeProtection(UUID playerId) {
        if (optedOut.add(playerId)) {
            saveOptedOut();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Persistance des joueurs ayant retire leur protection
    // ------------------------------------------------------------------

    private void saveOptedOut() {
        if (optedOut.isEmpty()) {
            plugin.getEventsDb().setState(OPTED_OUT_KEY, null);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (UUID uuid : optedOut) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(uuid);
        }
        plugin.getEventsDb().setState(OPTED_OUT_KEY, sb.toString());
    }

    public void restore() {
        wasActive = isActive();
        String state = plugin.getEventsDb().getState(OPTED_OUT_KEY);
        if (state == null || state.isBlank()) {
            return;
        }
        Set<UUID> loaded = new HashSet<>();
        for (String part : state.split(",")) {
            try {
                loaded.add(UUID.fromString(part.trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        optedOut.addAll(loaded);
    }
}
