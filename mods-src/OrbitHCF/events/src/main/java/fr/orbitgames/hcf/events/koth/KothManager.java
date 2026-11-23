package fr.orbitgames.hcf.events.koth;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.event.KothCaptureEvent;
import fr.orbitgames.hcf.api.event.KothEndEvent;
import fr.orbitgames.hcf.api.event.KothStartEvent;
import fr.orbitgames.hcf.api.model.Faction;
import fr.orbitgames.hcf.api.model.FactionType;
import fr.orbitgames.hcf.api.model.KothInfo;
import fr.orbitgames.hcf.api.model.Relation;
import fr.orbitgames.hcf.api.service.ClaimService;
import fr.orbitgames.hcf.api.service.FactionService;
import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.events.OrbitHCFEventsPlugin;
import fr.orbitgames.hcf.events.zone.CaptureZone;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * KOTH et Citadel : capture de zone, contestation, recompenses,
 * faction systeme d'evenement, restauration apres redemarrage.
 */
public class KothManager {

    /** Etat du KOTH actif. */
    public class ActiveKoth implements KothInfo {
        final CaptureZone zone;
        final boolean citadel;
        int remaining;
        UUID capper;
        boolean contested;
        Faction eventFaction;

        ActiveKoth(CaptureZone zone, boolean citadel, int remaining) {
            this.zone = zone;
            this.citadel = citadel;
            this.remaining = remaining;
        }

        @Override
        public String getName() {
            return zone.getName();
        }

        @Override
        public boolean isCitadel() {
            return citadel;
        }

        @Override
        public Duration getRemainingCapture() {
            return Duration.ofSeconds(remaining);
        }

        @Override
        public Duration getTotalCapture() {
            return Duration.ofSeconds(zone.getCaptureSeconds());
        }

        @Override
        public Optional<UUID> getCapturingPlayer() {
            return Optional.ofNullable(capper);
        }

        @Override
        public boolean isContested() {
            return contested;
        }
    }

    private static final String STATE_KEY = "active_koth";

    private final OrbitHCFEventsPlugin plugin;
    private ActiveKoth active;

    public KothManager(OrbitHCFEventsPlugin plugin) {
        this.plugin = plugin;
    }

    public ActiveKoth getActive() {
        return active;
    }

    // ------------------------------------------------------------------
    // Cycle de vie
    // ------------------------------------------------------------------

    public boolean start(CaptureZone zone) {
        if (active != null || !zone.isComplete()) {
            return false;
        }
        boolean citadel = zone.getType() == CaptureZone.ZoneType.CITADEL;
        active = new ActiveKoth(zone, citadel, zone.getCaptureSeconds());
        claimEventZone(zone);
        saveState();

        Bukkit.getPluginManager().callEvent(new KothStartEvent(zone.getName(), citadel));
        plugin.getMessages().broadcastRaw(citadel ? "citadel.started" : "koth.started",
                "koth", zone.getName(),
                "temps", DurationFormat.compact(Duration.ofSeconds(zone.getCaptureSeconds())));
        return true;
    }

    public void stop(boolean silent) {
        if (active == null) {
            return;
        }
        String name = active.zone.getName();
        boolean citadel = active.citadel;
        cleanup();
        Bukkit.getPluginManager().callEvent(new KothEndEvent(name, citadel, false));
        if (!silent) {
            plugin.getMessages().broadcastRaw("koth.stopped", "koth", name);
        }
    }

    private void cleanup() {
        if (active != null && active.eventFaction != null) {
            FactionService factions = OrbitHCF.factions();
            if (factions != null) {
                factions.removeSystemFaction(active.eventFaction);
            }
        }
        active = null;
        plugin.getEventsDb().setState(STATE_KEY, null);
    }

    private void claimEventZone(CaptureZone zone) {
        FactionService factions = OrbitHCF.factions();
        ClaimService claims = OrbitHCF.claims();
        World world = zone.getWorld();
        if (factions == null || claims == null || world == null) {
            return;
        }
        Faction eventFaction = factions.createSystemFaction("KOTH_" + zone.getName(), FactionType.EVENT);
        for (int[] chunk : zone.getChunks()) {
            claims.claimChunk(eventFaction, world, chunk[0], chunk[1]);
        }
        if (active != null) {
            active.eventFaction = eventFaction;
        }
    }

    // ------------------------------------------------------------------
    // Tick (1 seconde)
    // ------------------------------------------------------------------

    public void tick() {
        if (active == null) {
            return;
        }
        World world = active.zone.getWorld();
        if (world == null) {
            return;
        }
        FactionService factions = OrbitHCF.factions();
        boolean contestEnabled = plugin.getEventsConfig().getBoolean("koth.contest-enabled", true);

        Player capper = active.capper != null ? Bukkit.getPlayer(active.capper) : null;
        if (capper == null || !capper.isOnline() || capper.isDead()
                || !active.zone.contains(capper.getLocation())) {
            capper = null;
        }

        boolean contested = false;
        for (Player player : world.getPlayers()) {
            if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR
                    || !active.zone.contains(player.getLocation())) {
                continue;
            }
            if (capper == null) {
                capper = player;
                plugin.getMessages().broadcastRaw("koth.capturing",
                        "joueur", player.getName(), "koth", active.zone.getName());
                continue;
            }
            if (player.getUniqueId().equals(capper.getUniqueId())) {
                continue;
            }
            if (contestEnabled && factions != null
                    && !factions.getRelation(capper, player).isFriendly()) {
                contested = true;
            }
        }

        if (capper == null) {
            // Zone vide : reinitialisation du timer
            if (active.capper != null) {
                plugin.getMessages().broadcastRaw("koth.reset", "koth", active.zone.getName());
            }
            active.capper = null;
            active.contested = false;
            active.remaining = active.zone.getCaptureSeconds();
            return;
        }

        boolean wasContested = active.contested;
        active.capper = capper.getUniqueId();
        active.contested = contested;
        if (contested) {
            if (!wasContested) {
                plugin.getMessages().broadcastRaw("koth.contested", "koth", active.zone.getName());
            }
            return; // timer en pause
        }

        active.remaining--;
        if (active.remaining % 15 == 0) {
            saveState();
        }
        if (active.remaining <= 0) {
            capture(capper);
        }
    }

    private void capture(Player capper) {
        FactionService factions = OrbitHCF.factions();
        Faction faction = factions != null ? factions.getFactionByPlayer(capper.getUniqueId()) : null;
        String kothName = active.zone.getName();
        boolean citadel = active.citadel;
        String rewardSection = citadel ? "citadel" : "koth";

        // Recompenses : commandes console + points de faction + classement
        for (String command : plugin.getRewardsConfig().getStringList(rewardSection + ".commands")) {
            String parsed = command.replace("{joueur}", capper.getName())
                    .replace("{faction}", faction != null ? faction.getName() : "Aucune");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
        if (faction != null && factions != null) {
            factions.addPoints(faction, plugin.getRewardsConfig().getInt(rewardSection + ".points", 10));
            plugin.getEventsDb().incrementCaptures(faction.getId(), faction.getName());
        }
        if (citadel) {
            plugin.getEventsDb().addCitadelWin(faction != null ? faction.getName() : capper.getName());
        }

        plugin.getMessages().broadcastRaw(citadel ? "citadel.captured" : "koth.captured",
                "joueur", capper.getName(),
                "faction", faction != null ? faction.getName() : "Aucune",
                "koth", kothName);

        Bukkit.getPluginManager().callEvent(new KothCaptureEvent(kothName, citadel, capper, faction));
        cleanup();
        Bukkit.getPluginManager().callEvent(new KothEndEvent(kothName, citadel, true));
    }

    // ------------------------------------------------------------------
    // Restauration apres redemarrage
    // ------------------------------------------------------------------

    public void saveState() {
        if (active != null) {
            plugin.getEventsDb().setState(STATE_KEY, active.zone.getName() + ";" + active.remaining);
        }
    }

    public void restore() {
        String state = plugin.getEventsDb().getState(STATE_KEY);
        if (state == null) {
            return;
        }
        String[] parts = state.split(";");
        CaptureZone zone = plugin.getZoneRegistry().get(parts[0]);
        if (zone == null || !zone.isComplete()) {
            plugin.getEventsDb().setState(STATE_KEY, null);
            return;
        }
        boolean citadel = zone.getType() == CaptureZone.ZoneType.CITADEL;
        active = new ActiveKoth(zone, citadel, zone.getCaptureSeconds());
        if (parts.length > 1) {
            try {
                active.remaining = Math.max(1, Integer.parseInt(parts[1]));
            } catch (NumberFormatException ignored) {
            }
        }
        claimEventZone(zone);
        plugin.getLogger().info("KOTH " + zone.getName() + " restaure apres redemarrage ("
                + active.remaining + "s restants).");
    }
}
