package fr.orbitgames.hcf.display;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.util.Messages;
import fr.orbitgames.hcf.display.bar.ActionBarManager;
import fr.orbitgames.hcf.display.bar.BossBarManager;
import fr.orbitgames.hcf.display.board.BoardManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * OrbitHCF-Display : elements visuels du HCF.
 * Scoreboard, nametags par relation, tablist, action bar, bossbars.
 * Lecture seule : toutes les donnees proviennent d'OrbitHCF-API.
 * Les lignes des modules absents ne sont pas affichees.
 */
public final class OrbitHCFDisplayPlugin extends JavaPlugin implements Listener {

    private BoardManager boardManager;
    private BossBarManager bossBarManager;
    private ActionBarManager actionBarManager;
    private TablistManager tablistManager;
    private Messages messages;

    private FileConfiguration scoreboardConfig;
    private FileConfiguration nametagsConfig;
    private FileConfiguration tablistConfig;

    private final List<BukkitTask> tasks = new ArrayList<>();

    @Override
    public void onEnable() {
        scoreboardConfig = loadYaml("scoreboard.yml");
        nametagsConfig = loadYaml("nametags.yml");
        tablistConfig = loadYaml("tablist.yml");
        messages = new Messages(loadYaml("messages.yml"));

        if (OrbitHCF.factions() == null) {
            getLogger().severe("OrbitHCF-Core n'est pas actif : module Display desactive.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        boardManager = new BoardManager(this);
        bossBarManager = new BossBarManager(this);
        actionBarManager = new ActionBarManager(this);
        tablistManager = new TablistManager(this);

        Bukkit.getPluginManager().registerEvents(this, this);

        long sidebarInterval = Math.max(5L, scoreboardConfig.getLong("update-interval-ticks", 10L));
        long nametagInterval = Math.max(20L, nametagsConfig.getLong("update-interval-ticks", 40L));
        long tablistInterval = 20L * Math.max(1L, tablistConfig.getLong("update-interval-seconds", 5L));

        tasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> {
            boardManager.updateAllSidebars();
            actionBarManager.tick();
        }, sidebarInterval, sidebarInterval));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this,
                boardManager::updateAllNametags, nametagInterval, nametagInterval));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, bossBarManager::tick, 20L, 20L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, tablistManager::tick, tablistInterval, tablistInterval));

        // Joueurs deja connectes (rechargement a chaud)
        Bukkit.getOnlinePlayers().forEach(boardManager::setup);

        logModule("OrbitHCF-Classes");
        logModule("OrbitHCF-Combat");
        logModule("OrbitHCF-Events");
        getLogger().info("OrbitHCF-Display " + getDescription().getVersion() + " actif.");
    }

    private void logModule(String name) {
        boolean present = Bukkit.getPluginManager().getPlugin(name) != null;
        getLogger().info(name + " : " + (present ? "detecte, lignes activees" : "absent, lignes masquees"));
    }

    @Override
    public void onDisable() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        if (bossBarManager != null) {
            bossBarManager.shutdown();
        }
        if (boardManager != null) {
            boardManager.shutdown();
        }
        getLogger().info("OrbitHCF-Display desactive.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        boardManager.setup(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        boardManager.remove(event.getPlayer());
    }

    private FileConfiguration loadYaml(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public BoardManager getBoardManager() {
        return boardManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public FileConfiguration getScoreboardConfig() {
        return scoreboardConfig;
    }

    public FileConfiguration getNametagsConfig() {
        return nametagsConfig;
    }

    public FileConfiguration getTablistConfig() {
        return tablistConfig;
    }
}
