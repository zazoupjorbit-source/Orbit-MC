package fr.orbit.mmo;

import fr.orbit.mmo.boss.BossService;
import fr.orbit.mmo.classes.ClassRegistry;
import fr.orbit.mmo.combat.Combat;
import fr.orbit.mmo.combat.CombatListener;
import fr.orbit.mmo.command.AdminCommand;
import fr.orbit.mmo.command.MmoCommand;
import fr.orbit.mmo.command.PartyCommand;
import fr.orbit.mmo.floor.FloorService;
import fr.orbit.mmo.hud.Hud;
import fr.orbit.mmo.listener.PlayerListener;
import fr.orbit.mmo.listener.WorldListener;
import fr.orbit.mmo.mob.MobService;
import fr.orbit.mmo.pack.ResourcePackService;
import fr.orbit.mmo.party.PartyService;
import fr.orbit.mmo.player.PlayerStore;
import fr.orbit.mmo.player.Progression;
import fr.orbit.mmo.registry.GameRegistry;
import fr.orbit.mmo.spell.Auras;
import fr.orbit.mmo.spell.SpellBar;
import fr.orbit.mmo.spell.SpellEngine;
import fr.orbit.mmo.ui.MenuListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Orbit MMO : deux factions (Lumière et Ombre), classes à deux spécialisations,
 * sorts sur la barre d'action, arbres de talents, boss, groupes.
 */
public final class OrbitMMO extends JavaPlugin {

    private GameRegistry registry;
    private ClassRegistry classes;
    private PlayerStore players;
    private Progression progression;
    private PartyService parties;
    private MobService mobs;
    private BossService bosses;
    private FloorService floors;
    private Combat combat;
    private Auras auras;
    private SpellEngine spells;
    private SpellBar bar;
    private Hud hud;
    private PlayerListener playerListener;
    private ResourcePackService pack;
    private fr.orbit.mmo.item.Gear gear;
    private fr.orbit.mmo.market.Market market;
    private fr.orbit.mmo.quest.QuestService quests;
    private fr.orbit.mmo.continent.ContinentService continent;
    private fr.orbit.mmo.guild.GuildService guilds;
    private fr.orbit.mmo.hud.TabList tab;
    private fr.orbit.mmo.mount.MountService mounts;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Ajoute au config.yml existant les nouvelles options sans toucher aux réglages.
        getConfig().options().copyDefaults(true);
        saveConfig();
        Keys.init(this);
        registry = new GameRegistry(this);
        registry.load();
        classes = new ClassRegistry(this);
        classes.load();
        players = new PlayerStore(this);
        progression = new Progression(this);
        parties = new PartyService(this);
        guilds = new fr.orbit.mmo.guild.GuildService(this); // guildes.yml ; lu avant les étages, qui en dépendent
        mobs = new MobService(this);
        bosses = new BossService(this);
        floors = new FloorService(this);
        combat = new Combat(this);
        auras = new Auras(this);
        spells = new SpellEngine(this);
        bar = new SpellBar(this);
        hud = new Hud(this);
        playerListener = new PlayerListener(this);
        gear = new fr.orbit.mmo.item.Gear(this);
        market = new fr.orbit.mmo.market.Market(this);
        pack = new ResourcePackService(this);
        pack.enable();

        floors.enable();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(playerListener, this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new fr.orbit.mmo.combat.CasterAttack(this), this); // trait magique des baguettes, bâtons, grimoires
        pm.registerEvents(new WorldListener(this), this);
        pm.registerEvents(bar, this);
        pm.registerEvents(mobs, this);
        pm.registerEvents(floors, this);
        pm.registerEvents(pack, this);
        pm.registerEvents(gear, this);
        pm.registerEvents(market, this);
        pm.registerEvents(guilds, this);

        MmoCommand mmo = new MmoCommand(this);
        register("mmo", mmo);
        register("perso", mmo);
        register("lobby", mmo);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        register("groupe", new PartyCommand(this));
        fr.orbit.mmo.command.GuildCommand guildCommand = new fr.orbit.mmo.command.GuildCommand(this);
        register("guilde", guildCommand);
        register("gc", guildCommand);
        register("mmoadmin", new AdminCommand(this));

        mobs.start();
        bosses.start();
        hud.start();
        tab = new fr.orbit.mmo.hud.TabList(this);
        tab.start();
        combat.start();
        auras.start();
        spells.start();
        floors.start();
        market.start();

        // Quêtes (paquet fr.orbit.mmo.quest) : écoute ses propres événements, commande /quetes.
        quests = new fr.orbit.mmo.quest.QuestService(this);
        quests.enable();
        register("quetes", new fr.orbit.mmo.quest.QuestCommand(this, quests));
        register("langue", new fr.orbit.mmo.quest.LangCommand(quests));

        // Le continent (paquet fr.orbit.mmo.continent) : monde ouvert, à côté des étages ; commande /continent.
        continent = new fr.orbit.mmo.continent.ContinentService(this);
        continent.enable();
        pm.registerEvents(continent, this);
        register("continent", new fr.orbit.mmo.continent.ContinentCommand(continent));

        // Montures (paquet fr.orbit.mmo.mount) : mounts.yml, écurie, butin du continent ; commande /monture.
        mounts = new fr.orbit.mmo.mount.MountService(this);
        mounts.enable();
        pm.registerEvents(mounts, this);
        register("monture", new fr.orbit.mmo.mount.MountCommand(this, mounts));

        for (Player p : getServer().getOnlinePlayers()) {
            playerListener.join(p);
            if (pack.enabled()) {
                pack.send(p);
            }
        }
        getLogger().info("OrbitMMO activé — " + floors.highestCleared() + " étage(s) franchi(s) sur " + floors.maxFloor()
                + " (ouverts à tous jusqu'au " + (floors.base() + 1) + "), " + guilds.all().size() + " guilde(s).");
    }

    @Override
    public void onDisable() {
        if (spells != null) {
            spells.stop();
        }
        if (mounts != null) {
            mounts.shutdown();
        }
        if (continent != null) {
            continent.shutdown();
        }
        if (quests != null) {
            quests.shutdown();
        }
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
        if (market != null) {
            market.save();
        }
        if (guilds != null) {
            guilds.save();
        }
        if (players != null) {
            players.shutdown();
        }
        if (pack != null) {
            pack.shutdown();
        }
    }

    /** Renvoie le joueur au lobby du réseau (canal « BungeeCord », compris par Velocity). */
    public void toLobby(Player p) {
        String server = getConfig().getString("lobby.server", "lobby");
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(server);
            p.sendPluginMessage(this, "BungeeCord", bytes.toByteArray());
            fr.orbit.mmo.util.Text.msg(p, "<#7FD4FF>Direction le lobby…");
        } catch (java.io.IOException e) {
            getLogger().warning("Envoi vers le lobby impossible : " + e.getMessage());
        }
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

    public ResourcePackService pack() {
        return pack;
    }

    public GameRegistry registry() {
        return registry;
    }

    public ClassRegistry classes() {
        return classes;
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

    /** Les guildes (une faction, trois grades, banque, progression dans le Pilier des Astres). */
    public fr.orbit.mmo.guild.GuildService guilds() {
        return guilds;
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

    public Auras auras() {
        return auras;
    }

    public SpellEngine spells() {
        return spells;
    }

    public SpellBar bar() {
        return bar;
    }

    /** Liste des joueurs (touche Tab). */
    public fr.orbit.mmo.hud.TabList tab() {
        return tab;
    }

    /** Montures : écurie, appel, monte au sol et en vol. */
    public fr.orbit.mmo.mount.MountService mounts() {
        return mounts;
    }

    public Hud hud() {
        return hud;
    }

    public PlayerListener playerListener() {
        return playerListener;
    }

    /** Usure et améliorations de l'équipement (forgeron). */
    public fr.orbit.mmo.item.Gear gear() {
        return gear;
    }

    /** Le continent (monde ouvert). */
    public fr.orbit.mmo.quest.QuestService quests() {
        return quests;
    }

    /** Monde de jeu : un étage ou le continent (mêmes règles de faim, d'usure, d'apparition et de PvP). */
    public boolean inGame(org.bukkit.World w) {
        return floors.floorOf(w) > 0 || (continent != null && continent.isContinent(w));
    }

    public fr.orbit.mmo.continent.ContinentService continent() {
        return continent;
    }

    /** Hôtel des ventes entre joueurs. */
    public fr.orbit.mmo.market.Market market() {
        return market;
    }
}
