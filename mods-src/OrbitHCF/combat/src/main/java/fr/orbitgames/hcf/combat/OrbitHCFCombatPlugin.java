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

        Bukkit.getPluginManager().registerEvents(new CombatTagListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PvpTimerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PearlListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GappleListener(this), this);
        Bukkit.getPluginManager().registerEvents(new RestrictionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new EnchantLimiter(this), this);
        Bukkit.getPluginManager().registerEvents(new PotionLimiter(this), this);
        Bukkit.getPluginManager().registerEvents(new DeathbanListener(this), this);

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
