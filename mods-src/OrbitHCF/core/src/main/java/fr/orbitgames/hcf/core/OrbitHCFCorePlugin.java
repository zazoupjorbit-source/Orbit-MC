package fr.orbitgames.hcf.core;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.service.ClaimService;
import fr.orbitgames.hcf.api.service.DatabaseService;
import fr.orbitgames.hcf.api.service.DtrService;
import fr.orbitgames.hcf.api.service.FactionService;
import fr.orbitgames.hcf.api.service.ProfileService;
import fr.orbitgames.hcf.api.service.TimerService;
import fr.orbitgames.hcf.api.util.Messages;
import fr.orbitgames.hcf.core.claim.ClaimManager;
import fr.orbitgames.hcf.core.command.FactionCommand;
import fr.orbitgames.hcf.core.command.LogoutCommand;
import fr.orbitgames.hcf.core.dtr.DtrManager;
import fr.orbitgames.hcf.core.faction.FactionManager;
import fr.orbitgames.hcf.core.listener.ConnectionListener;
import fr.orbitgames.hcf.core.listener.DeathListener;
import fr.orbitgames.hcf.core.listener.ProtectionListener;
import fr.orbitgames.hcf.core.listener.WarmupListener;
import fr.orbitgames.hcf.core.profile.ProfileManager;
import fr.orbitgames.hcf.core.storage.Database;
import fr.orbitgames.hcf.core.timer.TimerManager;
import fr.orbitgames.hcf.core.timer.WarmupManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OrbitHCF-Core : coeur du serveur HCF.
 * Profils, factions, claims, zones systeme, DTR, timers, stockage.
 * Demarre sans les modules optionnels (Classes, Combat, Events, Display).
 */
public final class OrbitHCFCorePlugin extends JavaPlugin {

    private Database database;
    private ProfileManager profileManager;
    private FactionManager factionManager;
    private ClaimManager claimManager;
    private DtrManager dtrManager;
    private TimerManager timerManager;
    private WarmupManager warmupManager;
    private Messages messages;

    private FileConfiguration factionsConfig;
    private FileConfiguration claimsConfig;
    private FileConfiguration dtrConfig;
    private FileConfiguration databaseConfig;

    private final Set<UUID> bypassing = ConcurrentHashMap.newKeySet();
    private BukkitTask autosaveTask;

    @Override
    public void onEnable() {
        // Configurations
        saveDefaultConfig();
        factionsConfig = loadYaml("factions.yml");
        claimsConfig = loadYaml("claims.yml");
        dtrConfig = loadYaml("dtr.yml");
        databaseConfig = loadYaml("database.yml");
        messages = new Messages(loadYaml("messages.yml"));

        // Stockage
        database = new Database(this);
        try {
            database.connect(databaseConfig);
        } catch (SQLException e) {
            getLogger().severe("Connexion a la base impossible : " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Gestionnaires
        profileManager = new ProfileManager(this, database);
        factionManager = new FactionManager(this, database);
        claimManager = new ClaimManager(this, database);
        dtrManager = new DtrManager(this);
        timerManager = new TimerManager(this, database);
        warmupManager = new WarmupManager(this);

        profileManager.preloadNames();
        factionManager.loadAll();
        factionManager.ensureSystemFactions();
        claimManager.loadConfig(claimsConfig, getConfig());
        claimManager.loadAll();
        dtrManager.loadConfig(dtrConfig);
        dtrManager.start();
        timerManager.loadAll();
        timerManager.start();

        // Enregistrement des services API
        OrbitHCF.register(DatabaseService.class, database);
        OrbitHCF.register(ProfileService.class, profileManager);
        OrbitHCF.register(FactionService.class, factionManager);
        OrbitHCF.register(ClaimService.class, claimManager);
        OrbitHCF.register(DtrService.class, dtrManager);
        OrbitHCF.register(TimerService.class, timerManager);

        // Listeners
        fr.orbitgames.hcf.api.HcfWorlds.register(this, new ConnectionListener(this));
        fr.orbitgames.hcf.api.HcfWorlds.register(this, new DeathListener(this));
        fr.orbitgames.hcf.api.HcfWorlds.register(this, new ProtectionListener(this));
        fr.orbitgames.hcf.api.HcfWorlds.register(this, new WarmupListener(this));

        // Commandes
        FactionCommand factionCommand = new FactionCommand(this);
        PluginCommand f = getCommand("f");
        if (f != null) {
            f.setExecutor(factionCommand);
            f.setTabCompleter(factionCommand);
        }
        PluginCommand logout = getCommand("logout");
        if (logout != null) {
            logout.setExecutor(new LogoutCommand(this));
        }

        // Sauvegarde automatique
        long interval = 20L * Math.max(60L, getConfig().getLong("autosave-seconds", 300L));
        autosaveTask = Bukkit.getScheduler().runTaskTimer(this, this::saveEverything, interval, interval);

        getLogger().info("OrbitHCF-Core " + getDescription().getVersion() + " active.");
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (dtrManager != null) {
            dtrManager.stop();
        }
        if (timerManager != null) {
            timerManager.stop();
        }
        if (factionManager != null) {
            factionManager.saveAll();
        }
        if (profileManager != null) {
            profileManager.saveAll();
        }

        OrbitHCF.unregister(DatabaseService.class);
        OrbitHCF.unregister(ProfileService.class);
        OrbitHCF.unregister(FactionService.class);
        OrbitHCF.unregister(ClaimService.class);
        OrbitHCF.unregister(DtrService.class);
        OrbitHCF.unregister(TimerService.class);

        if (database != null) {
            database.close();
        }
        getLogger().info("OrbitHCF-Core desactive, donnees sauvegardees.");
    }

    private void saveEverything() {
        factionManager.saveAll();
        profileManager.saveAll();
        Bukkit.getScheduler().runTaskAsynchronously(this, timerManager::saveAll);
    }

    private FileConfiguration loadYaml(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    // ------------------------------------------------------------------
    // Bypass des protections
    // ------------------------------------------------------------------

    public boolean isBypassing(UUID playerId) {
        return bypassing.contains(playerId);
    }

    public boolean toggleBypass(UUID playerId) {
        if (bypassing.remove(playerId)) {
            return false;
        }
        bypassing.add(playerId);
        return true;
    }

    // ------------------------------------------------------------------
    // Accesseurs
    // ------------------------------------------------------------------

    public Database getDatabase() {
        return database;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }

    public FactionManager getFactionManager() {
        return factionManager;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public DtrManager getDtrManager() {
        return dtrManager;
    }

    public TimerManager getTimerManager() {
        return timerManager;
    }

    public WarmupManager getWarmupManager() {
        return warmupManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public FileConfiguration getFactionsConfig() {
        return factionsConfig;
    }

    public FileConfiguration getClaimsConfig() {
        return claimsConfig;
    }

    public FileConfiguration getDtrConfig() {
        return dtrConfig;
    }
}
