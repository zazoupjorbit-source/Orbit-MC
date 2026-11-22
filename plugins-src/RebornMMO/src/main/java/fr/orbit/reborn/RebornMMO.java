package fr.orbit.reborn;

import fr.orbit.reborn.boss.BossService;
import fr.orbit.reborn.combat.Combat;
import fr.orbit.reborn.combat.CombatListener;
import fr.orbit.reborn.combat.Cursor;
import fr.orbit.reborn.command.AdminCommand;
import fr.orbit.reborn.command.MmoCommand;
import fr.orbit.reborn.command.PartyCommand;
import fr.orbit.reborn.floor.FloorService;
import fr.orbit.reborn.hud.Hud;
import fr.orbit.reborn.listener.PlayerListener;
import fr.orbit.reborn.listener.WorldListener;
import fr.orbit.reborn.mob.MobService;
import fr.orbit.reborn.pack.ResourcePackService;
import fr.orbit.reborn.party.PartyService;
import fr.orbit.reborn.player.PlayerStore;
import fr.orbit.reborn.player.Progression;
import fr.orbit.reborn.registry.GameRegistry;
import fr.orbit.reborn.skill.SkillEngine;
import fr.orbit.reborn.ui.MenuListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RebornMMO : MMO façon « jeu virtuel » de l'instance Reborn.
 * Étages flottants, classes, compétences d'épée, boss d'étage, groupes, curseurs PvP.
 */
public final class RebornMMO extends JavaPlugin {

    private GameRegistry registry;
    private PlayerStore players;
    private Progression progression;
    private PartyService parties;
    private Cursor cursor;
    private MobService mobs;
    private BossService bosses;
    private FloorService floors;
    private Combat combat;
    private SkillEngine skills;
    private Hud hud;
    private PlayerListener playerListener;
    private ResourcePackService pack;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Ajoute au config.yml existant les nouvelles options (pack de ressources, HUD) sans toucher aux réglages.
        getConfig().options().copyDefaults(true);
        saveConfig();
        Keys.init(this);
        registry = new GameRegistry(this);
        registry.load();
        players = new PlayerStore(this);
        progression = new Progression(this);
        parties = new PartyService(this);
        cursor = new Cursor(this);
        mobs = new MobService(this);
        bosses = new BossService(this);
        floors = new FloorService(this);
        combat = new Combat(this);
        skills = new SkillEngine(this);
        hud = new Hud(this);
        playerListener = new PlayerListener(this);
        pack = new ResourcePackService(this);
        pack.enable();

        floors.enable();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(playerListener, this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new WorldListener(this), this);
        pm.registerEvents(skills, this);
        pm.registerEvents(mobs, this);
        pm.registerEvents(floors, this);
        pm.registerEvents(pack, this);

        register("mmo", new MmoCommand(this));
        register("groupe", new PartyCommand(this));
        register("mmoadmin", new AdminCommand(this));

        mobs.start();
        bosses.start();
        hud.start();
        combat.start();
        floors.start();

        for (Player p : getServer().getOnlinePlayers()) {
            playerListener.join(p);
            if (pack.enabled()) {
                pack.send(p);
            }
        }
        getLogger().info("RebornMMO activé — " + floors.highestCleared() + " étage(s) franchi(s) sur " + floors.maxFloor() + ".");
    }

    @Override
    public void onDisable() {
        if (bosses != null) {
            bosses.shutdown();
        }
        if (mobs != null) {
            mobs.shutdown();
        }
        if (hud != null) {
            hud.shutdown();
        }
        if (floors != null) {
            floors.saveState();
        }
        if (players != null) {
            players.shutdown();
        }
        if (pack != null) {
            pack.shutdown();
        }
    }

    public ResourcePackService pack() {
        return pack;
    }

    private void register(String name, TabExecutor handler) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Commande absente du plugin.yml : " + name);
            return;
        }
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    public GameRegistry registry() {
        return registry;
    }

    public PlayerStore players() {
        return players;
    }

    public Progression progression() {
        return progression;
    }

    public PartyService parties() {
        return parties;
    }

    public Cursor cursor() {
        return cursor;
    }

    public MobService mobs() {
        return mobs;
    }

    public BossService bosses() {
        return bosses;
    }

    public FloorService floors() {
        return floors;
    }

    public Combat combat() {
        return combat;
    }

    public SkillEngine skills() {
        return skills;
    }

    public Hud hud() {
        return hud;
    }

    public PlayerListener playerListener() {
        return playerListener;
    }
}
