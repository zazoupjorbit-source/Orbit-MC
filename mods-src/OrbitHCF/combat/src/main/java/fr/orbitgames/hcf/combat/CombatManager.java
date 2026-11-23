package fr.orbitgames.hcf.combat;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.TimerKeys;
import fr.orbitgames.hcf.api.service.CombatService;
import fr.orbitgames.hcf.api.service.EventsService;
import fr.orbitgames.hcf.api.service.TimerService;
import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.combat.storage.CombatDb;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Etat de combat : combat tag, PvP timer, deathban, vies.
 */
public class CombatManager implements CombatService {

    private final OrbitHCFCombatPlugin plugin;
    private final CombatDb db;
    /** Dernier attaquant connu (attribution du combat log). */
    private final Map<UUID, UUID> lastAttacker = new ConcurrentHashMap<>();

    public CombatManager(OrbitHCFCombatPlugin plugin, CombatDb db) {
        this.plugin = plugin;
        this.db = db;
    }

    private TimerService timers() {
        return OrbitHCF.timers();
    }

    // ------------------------------------------------------------------
    // Combat tag
    // ------------------------------------------------------------------

    @Override
    public boolean isCombatTagged(UUID playerId) {
        return timers().isActive(playerId, TimerKeys.COMBAT_TAG);
    }

    @Override
    public Duration getCombatTagRemaining(UUID playerId) {
        return timers().getRemaining(playerId, TimerKeys.COMBAT_TAG);
    }

    @Override
    public void tag(Player victim, Player attacker) {
        Duration duration = Duration.ofSeconds(
                plugin.getCombatConfig().getLong("combat-tag.duration-seconds", 30L));
        applyTag(victim, duration);
        applyTag(attacker, duration);
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        lastAttacker.put(attacker.getUniqueId(), victim.getUniqueId());
    }

    private void applyTag(Player player, Duration duration) {
        boolean wasTagged = isCombatTagged(player.getUniqueId());
        timers().setTimer(player.getUniqueId(), TimerKeys.COMBAT_TAG, duration);
        if (!wasTagged) {
            plugin.getMessages().send(player, "tag.tagged", "temps", DurationFormat.compact(duration));
        }
    }

    @Override
    public void untag(UUID playerId) {
        timers().clearTimer(playerId, TimerKeys.COMBAT_TAG);
        lastAttacker.remove(playerId);
    }

    public UUID getLastAttacker(UUID playerId) {
        return lastAttacker.get(playerId);
    }

    // ------------------------------------------------------------------
    // PvP timer
    // ------------------------------------------------------------------

    @Override
    public boolean hasPvpTimer(UUID playerId) {
        return timers().isActive(playerId, TimerKeys.PVP_TIMER);
    }

    @Override
    public Duration getPvpTimerRemaining(UUID playerId) {
        return timers().getRemaining(playerId, TimerKeys.PVP_TIMER);
    }

    @Override
    public void removePvpTimer(UUID playerId) {
        timers().clearTimer(playerId, TimerKeys.PVP_TIMER);
    }

    // ------------------------------------------------------------------
    // Deathban / vies
    // ------------------------------------------------------------------

    @Override
    public boolean isDeathbanEnabled() {
        if (!plugin.getDeathbanConfig().getBoolean("enabled", true)) {
            return false;
        }
        if (plugin.getDeathbanConfig().getBoolean("sotw-disabled", true)) {
            EventsService events = OrbitHCF.events();
            if (events != null && events.isSotwActive()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isDeathbanned(UUID playerId) {
        return db.getDeathbanUntil(playerId) > System.currentTimeMillis();
    }

    @Override
    public Duration getDeathbanRemaining(UUID playerId) {
        long remaining = db.getDeathbanUntil(playerId) - System.currentTimeMillis();
        return remaining > 0L ? Duration.ofMillis(remaining) : Duration.ZERO;
    }

    @Override
    public void deathban(UUID playerId, Duration duration) {
        db.setDeathban(playerId, System.currentTimeMillis() + duration.toMillis());
    }

    @Override
    public void undeathban(UUID playerId) {
        db.removeDeathban(playerId);
    }

    @Override
    public int getLives(UUID playerId) {
        return db.getLives(playerId);
    }

    @Override
    public void setLives(UUID playerId, int lives) {
        db.setLives(playerId, lives);
    }
}
