package fr.orbitgames.hcf.combat;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.service.CombatService;
import fr.orbitgames.hcf.api.service.DatabaseService;
import fr.orbitgames.hcf.api.util.Messages;
import fr.orbitgames.hcf.combat.command.LivesCommand;
import fr.orbitgames.hcf.combat.command.PvpCommand;
import fr.orbitgames.hcf.combat.command.ReviveCommand;
import fr.orbitgames.hcf.combat.listener.CombatTagListener;
import fr.orbitgames.hcf.combat.listener.DeathbanListener;
import fr.orbitgames.hcf.combat.listener.EnchantLimiter;
import fr.orbitgames.hcf.combat.listener.GappleListener;
import fr.orbitgames.hcf.combat.listener.PearlListener;
import fr.orbitgames.hcf.combat.listener.PotionLimiter;
import fr.orbitgames.hcf.combat.listener.PvpTimerListener;
import fr.orbitgames.hcf.combat.listener.RestrictionListener;
import fr.orbitgames.hcf.combat.storage.CombatDb;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * OrbitHCF-Combat : regles de combat HCF.
 * Combat tag, PvP timer, ender pearls, gapples, deathban, vies,
 * limites d'enchantements et de potions, objets illegaux.
 * Communique avec OrbitHCF-Classes via l'API quand il est present (softdepend).
 */
public final class OrbitHCFCombatPlugin extends JavaPlugin {

    private CombatDb combatDb;
    private CombatManager combatManager;
    private fr.orbitgames.hcf.combat.listener.CombatLogger combatLogger;
    private Messages messages;

    private FileConfiguration combatConfig;
    private FileConfiguration deathbanConfig;
    private FileConfiguration enchantmentsConfig;
    private FileConfiguration potionsConfig;

    @Override
    public void onEnable() {
        combatConfig = loadYaml("combat.yml");
        deathbanConfig = loadYaml("deathban.yml");
        enchantmentsConfig = loadYaml("enchantments.yml");
        potionsConfig = loadYaml("potions.yml");
        messages = new Messages(loadYaml("messages.yml"));

        DatabaseService database = OrbitHCF.database();
        if (database == null) {
            getLogger().severe("OrbitHCF-Core n'est pas actif : module Combat desactive.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        combatDb = new CombatDb(this, database);
        combatDb.createTables();

        combatManager = new CombatManager(this, combatDb);
        OrbitHCF.register(CombatService.class, combatManager);

        combatLogger = new fr.orbitgames.hcf.combat.listener.CombatLogger(this);
        fr.orbitgames.hcf.api.HcfWorlds.register(this, combatLogger);
        fr.orbitgames.hcf.api.HcfWorlds.register(this, new CombatTagListener(this));
        fr.orbitgames.hcf.api.HcfWorlds.register(this, new PvpTimerListener(this));
        fr.orbitgames.hcf.api.HcfWorlds.register(this, new PearlListener(this));
        fr.orbitgames.hcf.api.HcfWorlds.register(this, new GappleListener(this));
        fr.orbitgames.hcf.api.HcfWorlds.register(this, new RestrictionListener(this));
        fr.orbitgames.hcf.api.HcfWorlds.register(this, new EnchantLimiter(this));
        fr.orbitgames.hcf.api.HcfWorlds.register(this, new PotionLimiter(this));
        fr.orbitgames.hcf.api.HcfWorlds.register(this, new DeathbanListener(this));

        // Combat facon 1.7/1.8. Si OrbitPractice est sur le serveur, c'est lui qui s'en charge deja.
        if (Bukkit.getPluginManager().getPlugin("OrbitPractice") == null) {
            fr.orbitgames.hcf.combat.listener.OldCombat oldCombat =
                    new fr.orbitgames.hcf.combat.listener.OldCombat(this, loadYaml("vieux-combat.yml"));
            Bukkit.getPluginManager().registerEvents(oldCombat, this);
            Bukkit.getScheduler().runTaskTimer(this, oldCombat::tick, 20L, 20L);
            getLogger().info("Combat 1.8 : " + (oldCombat.actif() ? "actif" : "coupe (vieux-combat.yml)") + ".");
        }

        PluginCommand pvp = getCommand("pvp");
        if (pvp != null) {
            pvp.setExecutor(new PvpCommand(this));
        }
        PluginCommand lives = getCommand("lives");
        if (lives != null) {
            lives.setExecutor(new LivesCommand(this));
        }
        PluginCommand revive = getCommand("revive");
        if (revive != null) {
            revive.setExecutor(new ReviveCommand(this));
        }

        if (Bukkit.getPluginManager().getPlugin("OrbitHCF-Classes") != null) {
            getLogger().info("OrbitHCF-Classes detecte : integration active.");
        }
        getLogger().info("OrbitHCF-Combat " + getDescription().getVersion() + " actif.");
    }

    @Override
    public void onDisable() {
        if (combatLogger != null) {
            combatLogger.shutdown();
        }
        OrbitHCF.unregister(CombatService.class);
        getLogger().info("OrbitHCF-Combat desactive.");
    }

    private FileConfiguration loadYaml(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public CombatDb getCombatDb() {
        return combatDb;
    }

    public fr.orbitgames.hcf.combat.listener.CombatLogger getCombatLogger() {
        return combatLogger;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public FileConfiguration getCombatConfig() {
        return combatConfig;
    }

    public FileConfiguration getDeathbanConfig() {
        return deathbanConfig;
    }

    public FileConfiguration getEnchantmentsConfig() {
        return enchantmentsConfig;
    }

    public FileConfiguration getPotionsConfig() {
        return potionsConfig;
    }
}
