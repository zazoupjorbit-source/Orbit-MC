package fr.orbit.devcore;

import fr.orbit.devcore.chat.ChatListener;
import fr.orbit.devcore.chat.LuckPermsPrefixProvider;
import fr.orbit.devcore.chat.PrefixProvider;
import fr.orbit.devcore.command.AdminCommand;
import fr.orbit.devcore.command.CompassCommand;
import fr.orbit.devcore.command.CosmeticCommand;
import fr.orbit.devcore.command.MountCommand;
import fr.orbit.devcore.cosmetic.CosmeticListener;
import fr.orbit.devcore.cosmetic.CosmeticMenu;
import fr.orbit.devcore.cosmetic.CosmeticService;
import fr.orbit.devcore.command.NpcCommand;
import fr.orbit.devcore.command.SpawnCommand;
import fr.orbit.devcore.command.WarpCommand;
import fr.orbit.devcore.command.WorldCommand;
import fr.orbit.devcore.world.WorldService;
import fr.orbit.devcore.station.StationService;
import fr.orbit.devcore.command.StationCommand;
import fr.orbit.devcore.compass.CompassListener;
import fr.orbit.devcore.compass.CompassService;
import fr.orbit.devcore.compass.WarpMenu;
import fr.orbit.devcore.firework.FireworkGame;
import fr.orbit.devcore.listener.PlayerListener;
import fr.orbit.devcore.message.Messages;
import fr.orbit.devcore.npc.NpcListener;
import fr.orbit.devcore.npc.NpcService;
import fr.orbit.devcore.npc.ServerConnector;
import fr.orbit.devcore.command.ShopCommand;
import fr.orbit.devcore.pack.LobbyPack;
import fr.orbit.devcore.shop.ShopMenu;
import fr.orbit.devcore.profile.LuckPermsRankProvider;
import fr.orbit.devcore.profile.RankProvider;
import fr.orbit.devcore.storage.FileWriter;
import fr.orbit.devcore.storage.LocationStore;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin core du serveur de dev ORBIT : /spawn, warps, boussole de téléportation
 * PNJ qui envoient les joueurs vers d'autres serveurs du proxy, et cosmétiques.
 */
public final class OrbitDevCore extends JavaPlugin {

    private FileWriter fileWriter;
    private Messages messages;
    private LocationStore locations;
    private CompassService compass;
    private WarpMenu warpMenu;
    private ServerConnector connector;
    private NpcService npcs;
    private WorldService worlds;
    private StationService station;
    private ChatListener chat;
    private RankProvider rankProvider;
    private CosmeticService cosmetics;
    private CosmeticMenu cosmeticMenu;
    private LobbyPack lobbyPack;
    private ShopMenu shopMenu;
    private FireworkGame fireworkGame;
    private fr.orbit.devcore.anneau.Anneaux anneaux;
    private fr.orbit.devcore.chat.Annonces annonces;
    private fr.orbit.devcore.chat.ClearLag clearLag;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        fileWriter = new FileWriter(getLogger());
        messages = new Messages(this);
        messages.load();
        worlds = new WorldService(this);
        worlds.load();
        station = new StationService(this);
        station.load();
        locations = new LocationStore(this);
        locations.load();
        compass = new CompassService(this);
        warpMenu = new WarpMenu(this, this::rankProvider);
        connector = new ServerConnector(this);
        npcs = new NpcService(this);
        npcs.load();
        cosmetics = new CosmeticService(this);
        cosmeticMenu = new CosmeticMenu(this);
        shopMenu = new ShopMenu(this);
        lobbyPack = new LobbyPack(this);
        lobbyPack.enable();

        getServer().getMessenger().registerOutgoingPluginChannel(this, ServerConnector.CHANNEL);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new CompassListener(this), this);
        pm.registerEvents(warpMenu, this);
        pm.registerEvents(new NpcListener(this), this);
        chat = new ChatListener(createPrefixProvider());
        chat.load(getConfig().getConfigurationSection("chat"));
        pm.registerEvents(chat, this);
        pm.registerEvents(new CosmeticListener(this, cosmetics), this);
        pm.registerEvents(cosmeticMenu, this);
        pm.registerEvents(shopMenu, this);
        pm.registerEvents(lobbyPack, this);
        pm.registerEvents(station, this);
        anneaux = new fr.orbit.devcore.anneau.Anneaux(this);
        pm.registerEvents(anneaux, this);
        anneaux.start();
        fireworkGame = new FireworkGame(this);
        pm.registerEvents(fireworkGame, this); // après PlayerListener : la boussole prend sa case d'abord
        fireworkGame.start();
        annonces = new fr.orbit.devcore.chat.Annonces(this);
        annonces.start();
        clearLag = new fr.orbit.devcore.chat.ClearLag(this);
        clearLag.start();

        SpawnCommand spawn = new SpawnCommand(this);
        register("spawn", spawn);
        register("setspawn", spawn);
        WarpCommand warp = new WarpCommand(this);
        register("warp", warp);
        register("setwarp", warp);
        register("delwarp", warp);
        register("boussole", new CompassCommand(this));
        register("lobby", new fr.orbit.devcore.command.LobbyCommand(this));
        register("servernpc", new NpcCommand(this));
        register("devworld", new WorldCommand(this));
        register("cosmetiques", new CosmeticCommand(this));
        register("monture", new MountCommand(this));
        register("orbitdev", new AdminCommand(this));
        register("station", new StationCommand(this));
        register("boutique", new ShopCommand(this));
        register("vote", new fr.orbit.devcore.command.VoteCommand(this));
        register("anneau", new fr.orbit.devcore.command.AnneauCommand(this));

        npcs.spawnAllLoaded();
        cosmetics.start();
        getLogger().info("OrbitDevCore activé — " + locations.warps().size() + " warp(s), "
                + npcs.all().size() + " PNJ, proxy " + (connector.proxyEnabled() ? "activé" : "désactivé") + ".");
    }

    @Override
    public void onDisable() {
        if (annonces != null) {
            annonces.stop();
        }
        if (clearLag != null) {
            clearLag.stop();
        }
        if (anneaux != null) {
            anneaux.stop();
        }
        if (fireworkGame != null) {
            fireworkGame.stop();
        }
        if (lobbyPack != null) {
            lobbyPack.shutdown();
        }
        if (cosmetics != null) {
            cosmetics.stop();
        }
        if (npcs != null) {
            npcs.despawnAll();
        }
        if (locations != null) {
            locations.save();
        }
        if (npcs != null) {
            npcs.save();
        }
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        if (fileWriter != null) {
            fileWriter.shutdown();
        }
    }

    /** Recharge config, messages, warps et PNJ sans redémarrer le serveur. */
    public void reloadAll() {
        reloadConfig();
        messages.load();
        chat.load(getConfig().getConfigurationSection("chat"));
        annonces.start();
        clearLag.start();
        worlds.load();
        station.load();
        locations.load();
        npcs.despawnAll();
        npcs.load();
        npcs.spawnAllLoaded();
        cosmetics.stop();
        cosmetics.start();
    }

    /** Préfixes de rôle via LuckPerms s'il est installé, sinon pseudo sans rôle. */
    private PrefixProvider createPrefixProvider() {
        if (getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            PrefixProvider luckPerms = LuckPermsPrefixProvider.create(getServer());
            if (luckPerms != null) {
                return luckPerms;
            }
        }
        getLogger().warning("LuckPerms absent : chat sans rôles.");
        return player -> PrefixProvider.FALLBACK;
    }

    /** Grade (menu de la boussole) : LuckPerms s'il est installé, sinon « Joueur ». Résolu à la demande. */
    private RankProvider rankProvider() {
        if (rankProvider == null) {
            if (getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
                rankProvider = LuckPermsRankProvider.create(getServer());
            }
            if (rankProvider == null) {
                rankProvider = player -> RankProvider.NONE;
            }
        }
        return rankProvider;
    }

    private <T extends CommandExecutor & TabCompleter> void register(String name, T handler) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Commande absente du plugin.yml : " + name);
            return;
        }
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    public FileWriter fileWriter() {
        return fileWriter;
    }

    public Messages messages() {
        return messages;
    }

    public LocationStore locations() {
        return locations;
    }

    public CompassService compass() {
        return compass;
    }

    public WarpMenu warpMenu() {
        return warpMenu;
    }

    public ServerConnector connector() {
        return connector;
    }

    public NpcService npcs() {
        return npcs;
    }

    public WorldService worlds() {
        return worlds;
    }

    public StationService station() {
        return station;
    }

    public CosmeticService cosmetics() {
        return cosmetics;
    }

    public CosmeticMenu cosmeticMenu() {
        return cosmeticMenu;
    }

    public LobbyPack lobbyPack() {
        return lobbyPack;
    }

    public ShopMenu shopMenu() {
        return shopMenu;
    }

    public fr.orbit.devcore.anneau.Anneaux anneaux() {
        return anneaux;
    }

    public fr.orbit.devcore.chat.ClearLag clearLag() {
        return clearLag;
    }

    public FireworkGame fireworkGame() {
        return fireworkGame;
    }
}
