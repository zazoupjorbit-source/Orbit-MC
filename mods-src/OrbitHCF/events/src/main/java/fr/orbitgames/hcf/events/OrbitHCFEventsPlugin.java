package fr.orbitgames.hcf.events;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.service.DatabaseService;
import fr.orbitgames.hcf.api.service.EventsService;
import fr.orbitgames.hcf.api.util.Messages;
import fr.orbitgames.hcf.events.command.CitadelCommand;
import fr.orbitgames.hcf.events.command.ConquestCommand;
import fr.orbitgames.hcf.events.command.EotwCommand;
import fr.orbitgames.hcf.events.command.KothCommand;
import fr.orbitgames.hcf.events.command.SotwCommand;
import fr.orbitgames.hcf.events.conquest.ConquestManager;
import fr.orbitgames.hcf.events.eotw.EotwManager;
import fr.orbitgames.hcf.events.koth.KothManager;
import fr.orbitgames.hcf.events.sotw.SotwListener;
import fr.orbitgames.hcf.events.sotw.SotwManager;
import fr.orbitgames.hcf.events.storage.EventsDb;
import fr.orbitgames.hcf.events.zone.ZoneRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;

/**
 * OrbitHCF-Events : evenements traditionnels du HCF.
 * KOTH, Citadel, Conquest, SOTW, EOTW, calendrier, recompenses,
 * points de faction, classement, restauration apres redemarrage.
 */
public final class OrbitHCFEventsPlugin extends JavaPlugin {

    private ZoneRegistry zoneRegistry;
    private EventsDb eventsDb;
    private KothManager kothManager;
    private ConquestManager conquestManager;
    private SotwManager sotwManager;
    private EotwManager eotwManager;
    private Messages messages;

    private FileConfiguration eventsConfig;
    private FileConfiguration rewardsConfig;
    private File eventsFile;

    private BukkitTask tickTask;
    private BukkitTask scheduleTask;

    @Override
    public void onEnable() {
        eventsFile = new File(getDataFolder(), "events.yml");
        eventsConfig = loadYaml("events.yml");
        rewardsConfig = loadYaml("rewards.yml");
        messages = new Messages(loadYaml("messages.yml"));

        DatabaseService database = OrbitHCF.database();
        if (database == null || OrbitHCF.factions() == null) {
            getLogger().severe("OrbitHCF-Core n'est pas actif : module Events desactive.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        eventsDb = new EventsDb(this, database);
        eventsDb.createTables();

        zoneRegistry = new ZoneRegistry(this);
        zoneRegistry.load();

        kothManager = new KothManager(this);
        conquestManager = new ConquestManager(this);
        sotwManager = new SotwManager(this);
        eotwManager = new EotwManager(this);

        OrbitHCF.register(EventsService.class, new EventsServiceImpl(this));

        fr.orbitgames.hcf.api.HcfWorlds.register(this, new SotwListener(this));
        fr.orbitgames.hcf.api.HcfWorlds.register(this, eotwManager);

        // Restauration apres redemarrage
        sotwManager.restore();
        eotwManager.restore();
        kothManager.restore();
        conquestManager.restore();

        // Tick des evenements (1 seconde) et calendrier (20 secondes)
        tickTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            kothManager.tick();
            conquestManager.tick();
            sotwManager.tick();
        }, 20L, 20L);
        EventScheduler scheduler = new EventScheduler(this);
        scheduleTask = Bukkit.getScheduler().runTaskTimer(this, scheduler::tick, 20L * 10L, 20L * 20L);

        registerCommand("koth", new KothCommand(this));
        registerCommand("citadel", new CitadelCommand(this));
        registerCommand("conquest", new ConquestCommand(this));
        registerCommand("sotw", new SotwCommand(this));
        registerCommand("eotw", new EotwCommand(this));

        getLogger().info("OrbitHCF-Events " + getDescription().getVersion() + " actif.");
    }

    private void registerCommand(String name, Object executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor((org.bukkit.command.CommandExecutor) executor);
            if (executor instanceof org.bukkit.command.TabCompleter completer) {
                command.setTabCompleter(completer);
            }
        }
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (scheduleTask != null) {
            scheduleTask.cancel();
        }
        if (kothManager != null) {
            kothManager.saveState();
        }
        if (eotwManager != null) {
            eotwManager.shutdown();
        }
        OrbitHCF.unregister(EventsService.class);
        getLogger().info("OrbitHCF-Events desactive, etat sauvegarde.");
    }

    private FileConfiguration loadYaml(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public void saveEventsConfig() {
        try {
            eventsConfig.save(eventsFile);
        } catch (IOException e) {
            getLogger().severe("Sauvegarde de events.yml impossible : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Accesseurs
    // ------------------------------------------------------------------

    public ZoneRegistry getZoneRegistry() {
        return zoneRegistry;
    }

    public EventsDb getEventsDb() {
        return eventsDb;
    }

    public KothManager getKothManager() {
        return kothManager;
    }

    public ConquestManager getConquestManager() {
        return conquestManager;
    }

    public SotwManager getSotwManager() {
        return sotwManager;
    }

    public EotwManager getEotwManager() {
        return eotwManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public FileConfiguration getEventsConfig() {
        return eventsConfig;
    }

    public FileConfiguration getRewardsConfig() {
        return rewardsConfig;
    }
}
