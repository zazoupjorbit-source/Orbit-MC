package fr.orbitgames.hcf.classes;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.model.HcfClass;
import fr.orbitgames.hcf.api.service.ClassesService;
import fr.orbitgames.hcf.api.util.Messages;
import fr.orbitgames.hcf.classes.listener.ArcherListener;
import fr.orbitgames.hcf.classes.listener.ArmorListener;
import fr.orbitgames.hcf.classes.listener.BardListener;
import fr.orbitgames.hcf.classes.listener.ClassRestrictionListener;
import fr.orbitgames.hcf.classes.listener.RogueListener;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Optional;

/**
 * OrbitHCF-Classes : classes HCF detectees par l'armure.
 * Diamond, Bard, Archer, Rogue, Miner.
 * Utilise les factions et relations du Core via OrbitHCF-API.
 */
public final class OrbitHCFClassesPlugin extends JavaPlugin {

    private ClassManager classManager;
    private BardManager bardManager;
    private Messages messages;
    private FileConfiguration classesConfig;

    @Override
    public void onEnable() {
        classesConfig = loadYaml("classes.yml");
        messages = new Messages(loadYaml("messages.yml"));

        if (OrbitHCF.factions() == null) {
            getLogger().severe("OrbitHCF-Core n'est pas actif : module Classes desactive.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        bardManager = new BardManager(this);
        classManager = new ClassManager(this);
        classManager.loadConfig();
        bardManager.loadConfig();
        classManager.start();
        bardManager.start();

        OrbitHCF.register(ClassesService.class, classManager);

        fr.orbitgames.hcf.api.HcfWorlds.register(this, new ArmorListener(this));
        fr.orbitgames.hcf.api.HcfWorlds.register(this, new BardListener(this));
        fr.orbitgames.hcf.api.HcfWorlds.register(this, new ArcherListener(this));
        fr.orbitgames.hcf.api.HcfWorlds.register(this, new RogueListener(this));
        fr.orbitgames.hcf.api.HcfWorlds.register(this, new ClassRestrictionListener(this));

        PluginCommand classCommand = getCommand("class");
        if (classCommand != null) {
            classCommand.setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "general.player-only");
                    return true;
                }
                Optional<HcfClass> active = classManager.getActiveClass(player.getUniqueId());
                if (active.isPresent()) {
                    messages.send(player, "class.current", "classe", active.get().getDisplayName());
                } else {
                    messages.send(player, "class.none");
                }
                return true;
            });
        }

        // Reevalue les joueurs deja connectes (rechargement a chaud)
        for (Player player : fr.orbitgames.hcf.api.HcfWorlds.players()) {
            classManager.recheck(player);
        }

        getLogger().info("OrbitHCF-Classes " + getDescription().getVersion() + " actif.");
    }

    @Override
    public void onDisable() {
        if (classManager != null) {
            classManager.stop();
        }
        if (bardManager != null) {
            bardManager.stop();
        }
        OrbitHCF.unregister(ClassesService.class);
        getLogger().info("OrbitHCF-Classes desactive.");
    }

    private FileConfiguration loadYaml(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public ClassManager getClassManager() {
        return classManager;
    }

    public BardManager getBardManager() {
        return bardManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public FileConfiguration getClassesConfig() {
        return classesConfig;
    }
}
