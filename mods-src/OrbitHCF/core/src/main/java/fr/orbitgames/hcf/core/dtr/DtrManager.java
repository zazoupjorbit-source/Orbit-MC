package fr.orbitgames.hcf.core.dtr;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.event.DtrFreezeEndEvent;
import fr.orbitgames.hcf.api.event.DtrFreezeStartEvent;
import fr.orbitgames.hcf.api.event.FactionDtrChangeEvent;
import fr.orbitgames.hcf.api.event.FactionRaidableEvent;
import fr.orbitgames.hcf.api.event.FactionUnraidableEvent;
import fr.orbitgames.hcf.api.model.DtrChangeReason;
import fr.orbitgames.hcf.api.model.Faction;
import fr.orbitgames.hcf.api.model.FactionType;
import fr.orbitgames.hcf.api.service.DtrService;
import fr.orbitgames.hcf.api.service.EventsService;
import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import fr.orbitgames.hcf.core.faction.FactionImpl;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * DTR : plafond par membre, perte a la mort, gel, regeneration, raidabilite.
 */
public class DtrManager implements DtrService {

    private final OrbitHCFCorePlugin plugin;

    private double perMember;
    private double maxCap;
    private double minFloor;
    private double lossPerDeath;
    private boolean sotwBlocksLoss;
    private long freezeMinutes;
    private double regenPerHour;
    private boolean regenRequireOnline;
    private int regenIntervalSeconds;

    /** Factions dont le gel est en cours (pour detecter la fin). */
    private final Set<UUID> frozenTracked = new HashSet<>();

    private BukkitTask tickTask;
    private int regenCounter;

    public DtrManager(OrbitHCFCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig(FileConfiguration dtrConfig) {
        perMember = dtrConfig.getDouble("dtr.per-member", 1.1D);
        maxCap = dtrConfig.getDouble("dtr.max", 5.5D);
        minFloor = dtrConfig.getDouble("dtr.min", -1.0D);
        lossPerDeath = dtrConfig.getDouble("dtr.loss-per-death", 1.0D);
        sotwBlocksLoss = dtrConfig.getBoolean("dtr.sotw-blocks-loss", true);
        freezeMinutes = dtrConfig.getLong("freeze.duration-minutes", 45L);
        regenPerHour = dtrConfig.getDouble("regen.per-hour", 2.0D);
        regenRequireOnline = dtrConfig.getBoolean("regen.require-online-member", true);
        regenIntervalSeconds = Math.max(1, dtrConfig.getInt("regen.interval-seconds", 30));
    }

    public void start() {
        // Tick chaque seconde : fins de gel + regeneration periodique
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        for (Faction faction : plugin.getFactionManager().getFactions()) {
            recalculateMax((FactionImpl) faction);
            if (faction.isDtrFrozen()) {
                frozenTracked.add(faction.getId());
            }
        }
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        // Fins de gel
        frozenTracked.removeIf(id -> {
            FactionImpl faction = plugin.getFactionManager().getFactionImpl(id);
            if (faction == null) {
                return true;
            }
            if (faction.getDtrFreezeEnd() <= now) {
                faction.setDtrFreezeEnd(0L);
                plugin.getFactionManager().saveFaction(faction);
                Bukkit.getPluginManager().callEvent(new DtrFreezeEndEvent(faction));
                return true;
            }
            return false;
        });

        // Regeneration
        regenCounter++;
        if (regenCounter >= regenIntervalSeconds) {
            regenCounter = 0;
            regenTick();
        }
    }

    private void regenTick() {
        EventsService events = OrbitHCF.events();
        if (events != null && events.isEotwActive()) {
            return; // EOTW : plus de regeneration
        }
        double amount = regenPerHour * regenIntervalSeconds / 3600.0D;
        for (Faction faction : plugin.getFactionManager().getPlayerFactions()) {
            FactionImpl impl = (FactionImpl) faction;
            if (impl.isDtrFrozen() || impl.getDtr() >= impl.getMaxDtr()) {
                continue;
            }
            if (regenRequireOnline && impl.getOnlineCount() == 0) {
                continue;
            }
            setDtr(impl, impl.getDtr() + amount, DtrChangeReason.REGEN);
        }
    }

    /** Initialise le DTR d'une faction nouvellement creee. */
    public void initialize(FactionImpl faction) {
        recalculateMax(faction);
        faction.setDtrRaw(faction.getMaxDtr());
    }

    /** Recalcule le plafond de DTR et borne la valeur courante. */
    public void recalculateMax(FactionImpl faction) {
        if (faction.getType() != FactionType.PLAYER) {
            faction.setMaxDtr(0.0D);
            return;
        }
        double max = Math.min(maxCap, faction.getMemberCount() * perMember);
        faction.setMaxDtr(max);
        if (faction.getDtr() > max) {
            setDtr(faction, max, DtrChangeReason.MEMBER_CHANGE);
        }
    }

    // ------------------------------------------------------------------
    // DtrService
    // ------------------------------------------------------------------

    @Override
    public double getDtr(Faction faction) {
        return faction.getDtr();
    }

    @Override
    public void setDtr(Faction faction, double value, DtrChangeReason reason) {
        FactionImpl impl = plugin.getFactionManager().getFactionImpl(faction.getId());
        if (impl == null || impl.getType() != FactionType.PLAYER) {
            return;
        }
        double old = impl.getDtr();
        double clamped = clamp(impl, value);
        FactionDtrChangeEvent event = new FactionDtrChangeEvent(impl, old, clamped, reason);
        Bukkit.getPluginManager().callEvent(event);
        double applied = clamp(impl, event.getNewDtr());
        if (applied == old) {
            return;
        }
        impl.setDtrRaw(applied);
        plugin.getFactionManager().saveFaction(impl);

        boolean wasRaidable = old <= 0.0D;
        boolean nowRaidable = applied <= 0.0D;
        if (!wasRaidable && nowRaidable) {
            Bukkit.getPluginManager().callEvent(new FactionRaidableEvent(impl));
            if (plugin.getConfig().getBoolean("broadcast-raidable", true)) {
                plugin.getMessages().broadcastRaw("dtr.raidable-broadcast", "faction", impl.getName());
            }
        } else if (wasRaidable && !nowRaidable) {
            Bukkit.getPluginManager().callEvent(new FactionUnraidableEvent(impl));
            if (plugin.getConfig().getBoolean("broadcast-raidable", true)) {
                plugin.getMessages().broadcastRaw("dtr.unraidable-broadcast", "faction", impl.getName());
            }
        }
    }

    private double clamp(FactionImpl faction, double value) {
        return Math.max(minFloor, Math.min(faction.getMaxDtr(), value));
    }

    @Override
    public double getMaxDtr(Faction faction) {
        return faction.getMaxDtr();
    }

    @Override
    public void applyDeathLoss(Player victim) {
        FactionImpl faction = plugin.getFactionManager().getImplByPlayer(victim.getUniqueId());
        if (faction == null) {
            return;
        }
        EventsService events = OrbitHCF.events();
        if (sotwBlocksLoss && events != null && events.isSotwActive()) {
            return;
        }
        setDtr(faction, faction.getDtr() - lossPerDeath, DtrChangeReason.DEATH);
        freeze(faction, Duration.ofMinutes(freezeMinutes));
    }

    @Override
    public double getDeathLoss() {
        return lossPerDeath;
    }

    @Override
    public void freeze(Faction faction, Duration duration) {
        FactionImpl impl = plugin.getFactionManager().getFactionImpl(faction.getId());
        if (impl == null || duration.isZero() || duration.isNegative()) {
            return;
        }
        impl.setDtrFreezeEnd(System.currentTimeMillis() + duration.toMillis());
        frozenTracked.add(impl.getId());
        plugin.getFactionManager().saveFaction(impl);
        Bukkit.getPluginManager().callEvent(new DtrFreezeStartEvent(impl, duration));
    }

    @Override
    public void unfreeze(Faction faction) {
        FactionImpl impl = plugin.getFactionManager().getFactionImpl(faction.getId());
        if (impl == null || !impl.isDtrFrozen()) {
            return;
        }
        impl.setDtrFreezeEnd(0L);
        frozenTracked.remove(impl.getId());
        plugin.getFactionManager().saveFaction(impl);
        Bukkit.getPluginManager().callEvent(new DtrFreezeEndEvent(impl));
    }

    @Override
    public boolean isFrozen(Faction faction) {
        return faction.isDtrFrozen();
    }

    @Override
    public Duration getFreezeRemaining(Faction faction) {
        long remaining = faction.getDtrFreezeEnd() - System.currentTimeMillis();
        return remaining > 0L ? Duration.ofMillis(remaining) : Duration.ZERO;
    }

    @Override
    public boolean isRaidable(Faction faction) {
        return faction.isRaidable();
    }
}
