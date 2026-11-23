package fr.orbitgames.hcf.events.conquest;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.model.ConquestInfo;
import fr.orbitgames.hcf.api.model.Faction;
import fr.orbitgames.hcf.api.model.FactionType;
import fr.orbitgames.hcf.api.service.ClaimService;
import fr.orbitgames.hcf.api.service.FactionService;
import fr.orbitgames.hcf.events.OrbitHCFEventsPlugin;
import fr.orbitgames.hcf.events.zone.CaptureZone;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conquest : plusieurs points de capture, attribution periodique de points,
 * victoire au score configure.
 */
public class ConquestManager implements ConquestInfo {

    private static final String STATE_KEY = "conquest_state";

    private final OrbitHCFEventsPlugin plugin;
    private final Map<UUID, Integer> scores = new ConcurrentHashMap<>();
    private boolean running;
    private Faction eventFaction;
    private int tickCounter;

    public ConquestManager(OrbitHCFEventsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return running;
    }

    // ------------------------------------------------------------------
    // Cycle de vie
    // ------------------------------------------------------------------

    public boolean start() {
        if (running) {
            return false;
        }
        List<CaptureZone> zones = plugin.getZoneRegistry().byType(CaptureZone.ZoneType.CONQUEST);
        zones.removeIf(zone -> !zone.isComplete());
        if (zones.isEmpty()) {
            return false;
        }
        running = true;
        scores.clear();
        tickCounter = 0;
        claimZones(zones);
        saveState();
        plugin.getMessages().broadcastRaw("conquest.started", "score", getTargetScore());
        return true;
    }

    public void stop(boolean broadcast) {
        if (!running) {
            return;
        }
        running = false;
        scores.clear();
        unclaimZones();
        plugin.getEventsDb().setState(STATE_KEY, null);
        if (broadcast) {
            plugin.getMessages().broadcastRaw("conquest.stopped");
        }
    }

    private void claimZones(List<CaptureZone> zones) {
        FactionService factions = OrbitHCF.factions();
        ClaimService claims = OrbitHCF.claims();
        if (factions == null || claims == null) {
            return;
        }
        eventFaction = factions.createSystemFaction("Conquest", FactionType.EVENT);
        for (CaptureZone zone : zones) {
            World world = zone.getWorld();
            if (world == null) {
                continue;
            }
            for (int[] chunk : zone.getChunks()) {
                claims.claimChunk(eventFaction, world, chunk[0], chunk[1]);
            }
        }
    }

    private void unclaimZones() {
        if (eventFaction != null) {
            FactionService factions = OrbitHCF.factions();
            if (factions != null) {
                factions.removeSystemFaction(eventFaction);
            }
            eventFaction = null;
        }
    }

    // ------------------------------------------------------------------
    // Tick (1 seconde)
    // ------------------------------------------------------------------

    public void tick() {
        if (!running) {
            return;
        }
        int interval = Math.max(5, plugin.getEventsConfig().getInt("conquest.interval-seconds", 30));
        tickCounter++;
        if (tickCounter < interval) {
            return;
        }
        tickCounter = 0;
        awardPoints();
    }

    private void awardPoints() {
        FactionService factions = OrbitHCF.factions();
        if (factions == null) {
            return;
        }
        int pointsPerInterval = plugin.getEventsConfig().getInt("conquest.points-per-interval", 1);
        for (CaptureZone zone : plugin.getZoneRegistry().byType(CaptureZone.ZoneType.CONQUEST)) {
            UUID holder = resolveHolder(zone, factions);
            if (holder == null) {
                continue;
            }
            int newScore = scores.merge(holder, pointsPerInterval, Integer::sum);
            if (newScore >= getTargetScore()) {
                win(holder, newScore, factions);
                return;
            }
        }
        saveState();
    }

    /** Faction seule presente dans la zone (contestation = personne ne marque). */
    private UUID resolveHolder(CaptureZone zone, FactionService factions) {
        World world = zone.getWorld();
        if (world == null) {
            return null;
        }
        Set<UUID> present = new HashSet<>();
        boolean factionlessPresent = false;
        for (Player player : world.getPlayers()) {
            if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR
                    || !zone.contains(player.getLocation())) {
                continue;
            }
            Faction faction = factions.getFactionByPlayer(player.getUniqueId());
            if (faction == null) {
                factionlessPresent = true;
            } else {
                present.add(faction.getId());
            }
        }
        if (present.size() != 1 || factionlessPresent) {
            return null;
        }
        return present.iterator().next();
    }

    private void win(UUID factionId, int score, FactionService factions) {
        Faction faction = factions.getFaction(factionId);
        String name = faction != null ? faction.getName() : "?";
        plugin.getMessages().broadcastRaw("conquest.won", "faction", name, "score", score);
        if (faction != null) {
            factions.addPoints(faction, plugin.getRewardsConfig().getInt("conquest.points", 50));
            for (Player member : faction.getOnlinePlayers()) {
                for (String command : plugin.getRewardsConfig().getStringList("conquest.commands")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                            .replace("{joueur}", member.getName())
                            .replace("{faction}", name));
                }
            }
        }
        stop(false);
    }

    // ------------------------------------------------------------------
    // Restauration
    // ------------------------------------------------------------------

    private void saveState() {
        if (!running) {
            return;
        }
        StringBuilder sb = new StringBuilder("1");
        for (Map.Entry<UUID, Integer> entry : scores.entrySet()) {
            sb.append(";").append(entry.getKey()).append("=").append(entry.getValue());
        }
        plugin.getEventsDb().setState(STATE_KEY, sb.toString());
    }

    public void restore() {
        String state = plugin.getEventsDb().getState(STATE_KEY);
        if (state == null || !state.startsWith("1")) {
            return;
        }
        List<CaptureZone> zones = plugin.getZoneRegistry().byType(CaptureZone.ZoneType.CONQUEST);
        zones.removeIf(zone -> !zone.isComplete());
        if (zones.isEmpty()) {
            plugin.getEventsDb().setState(STATE_KEY, null);
            return;
        }
        running = true;
        for (String part : state.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                try {
                    scores.put(UUID.fromString(part.substring(0, eq)),
                            Integer.parseInt(part.substring(eq + 1)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        claimZones(zones);
        plugin.getLogger().info("Conquest restaure apres redemarrage.");
    }

    // ------------------------------------------------------------------
    // ConquestInfo
    // ------------------------------------------------------------------

    @Override
    public Map<UUID, Integer> getScores() {
        return new HashMap<>(scores);
    }

    @Override
    public int getTargetScore() {
        return plugin.getEventsConfig().getInt("conquest.target-score", 300);
    }

    @Override
    public List<String> getZoneNames() {
        List<String> names = new ArrayList<>();
        for (CaptureZone zone : plugin.getZoneRegistry().byType(CaptureZone.ZoneType.CONQUEST)) {
            names.add(zone.getName());
        }
        return names;
    }
}
