package fr.orbitgames.hcf.events.eotw;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.event.EotwEndEvent;
import fr.orbitgames.hcf.api.event.EotwStartEvent;
import fr.orbitgames.hcf.api.event.KothCaptureEvent;
import fr.orbitgames.hcf.api.model.DtrChangeReason;
import fr.orbitgames.hcf.api.model.Faction;
import fr.orbitgames.hcf.api.service.DtrService;
import fr.orbitgames.hcf.api.service.FactionService;
import fr.orbitgames.hcf.events.OrbitHCFEventsPlugin;
import fr.orbitgames.hcf.events.zone.CaptureZone;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

/**
 * EOTW : fin de saison. Arret de la regeneration du DTR (via EventsService),
 * passage progressif des factions en raidable, protections desactivees,
 * PvP global, evenement final.
 */
public class EotwManager implements Listener {

    private static final String STATE_KEY = "eotw_active";

    private final OrbitHCFEventsPlugin plugin;
    private boolean active;
    private BukkitTask reductionTask;

    public EotwManager(OrbitHCFEventsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() {
        return active;
    }

    public boolean start() {
        if (active) {
            return false;
        }
        active = true;
        plugin.getEventsDb().setState(STATE_KEY, "1");
        Bukkit.getPluginManager().callEvent(new EotwStartEvent());
        plugin.getMessages().broadcastRaw("eotw.started");
        startReductionTask();

        // Evenement final configurable
        String finalKoth = plugin.getEventsConfig().getString("eotw.final-koth", "");
        if (finalKoth != null && !finalKoth.isBlank()) {
            CaptureZone zone = plugin.getZoneRegistry().get(finalKoth);
            if (zone != null && zone.isComplete() && plugin.getKothManager().getActive() == null) {
                plugin.getKothManager().start(zone);
            }
        }
        return true;
    }

    public void stop() {
        if (!active) {
            return;
        }
        active = false;
        plugin.getEventsDb().setState(STATE_KEY, null);
        if (reductionTask != null) {
            reductionTask.cancel();
            reductionTask = null;
        }
        Bukkit.getPluginManager().callEvent(new EotwEndEvent());
        plugin.getMessages().broadcastRaw("eotw.stopped");
    }

    private void startReductionTask() {
        long intervalTicks = 20L * 60L * Math.max(1L,
                plugin.getEventsConfig().getLong("eotw.reduction-interval-minutes", 10L));
        reductionTask = Bukkit.getScheduler().runTaskTimer(plugin, this::reduceAll, intervalTicks, intervalTicks);
    }

    /** Reduction progressive du DTR : toutes les factions finissent raidables. */
    private void reduceAll() {
        if (!active) {
            return;
        }
        FactionService factions = OrbitHCF.factions();
        DtrService dtr = OrbitHCF.dtr();
        if (factions == null || dtr == null) {
            return;
        }
        double step = plugin.getEventsConfig().getDouble("eotw.dtr-reduction-per-interval", 0.5D);
        for (Faction faction : factions.getPlayerFactions()) {
            dtr.setDtr(faction, faction.getDtr() - step, DtrChangeReason.EVENT);
        }
    }

    /** Vainqueur de l'evenement final de la saison. */
    @EventHandler
    public void onKothCapture(KothCaptureEvent event) {
        if (!active) {
            return;
        }
        String finalKoth = plugin.getEventsConfig().getString("eotw.final-koth", "");
        if (finalKoth == null || finalKoth.isBlank()
                || !finalKoth.equalsIgnoreCase(event.getKothName())) {
            return;
        }
        String faction = event.getFaction() != null ? event.getFaction().getName() : event.getCapturer().getName();
        plugin.getMessages().broadcastRaw("eotw.final-winner", "faction", faction);
    }

    public void restore() {
        if ("1".equals(plugin.getEventsDb().getState(STATE_KEY))) {
            active = true;
            startReductionTask();
            plugin.getLogger().info("EOTW restaure apres redemarrage.");
        }
    }

    public void shutdown() {
        if (reductionTask != null) {
            reductionTask.cancel();
        }
    }
}
