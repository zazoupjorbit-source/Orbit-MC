package fr.orbit.extraction;

import fr.orbit.extraction.command.AdminCommand;
import fr.orbit.extraction.command.PlayerCommands;
import fr.orbit.extraction.gun.Grenades;
import fr.orbit.extraction.gun.GunItems;
import fr.orbit.extraction.gun.GunListener;
import fr.orbit.extraction.gun.GunRegistry;
import fr.orbit.extraction.gun.GunService;
import fr.orbit.extraction.gun.ItemSpecs;
import fr.orbit.extraction.gun.MagStore;
import fr.orbit.extraction.infected.InfectedService;
import fr.orbit.extraction.loot.Crates;
import fr.orbit.extraction.loot.Meds;
import fr.orbit.extraction.map.MiniMap;
import fr.orbit.extraction.rank.Kits;
import fr.orbit.extraction.rank.Ranks;
import fr.orbit.extraction.pack.ResourcePackService;
import fr.orbit.extraction.phone.Parcels;
import fr.orbit.extraction.phone.PhoneMenu;
import fr.orbit.extraction.phone.PhoneService;
import fr.orbit.extraction.raid.AutoSpawns;
import fr.orbit.extraction.raid.ProfileStore;
import fr.orbit.extraction.raid.RaidMap;
import fr.orbit.extraction.raid.RaidService;
import fr.orbit.extraction.raid.StashMenu;
import fr.orbit.extraction.vehicle.Vehicles;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Orbit Extraction : extraction shooter PvPvE. Armes à feu, infestés, raids chronométrés, planque. */
public final class OrbitExtraction extends JavaPlugin {

    private GunRegistry guns;
    private MagStore mags;
    private GunItems items;
    private ItemSpecs specs;
    private GunService gunService;
    private InfectedService infected;
    private RaidMap map;
    private ProfileStore profiles;
    private RaidService raid;
    private StashMenu stash;
    private ResourcePackService pack;
    private AutoSpawns autoSpawns;
    private PhoneService phone;
    private PhoneMenu phoneMenu;
    private Parcels parcels;
    private Meds meds;
    private Crates crates;
    private Ranks ranks;
    private Kits kits;
    private MiniMap miniMap;
    private Vehicles vehicles;
    private fr.orbit.extraction.armor.Armors armors;
    private fr.orbit.extraction.event.RaidEvents events;
    private fr.orbit.extraction.phone.Market market;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Keys.init(this);

        guns = new GunRegistry(this);
        mags = new MagStore(this);
        items = new GunItems(guns, mags);
        specs = new ItemSpecs(guns, items);
        gunService = new GunService(this, items, mags);
        infected = new InfectedService(this);
        map = new RaidMap(this);
        autoSpawns = new AutoSpawns(this);
        profiles = new ProfileStore(this);
        raid = new RaidService(this, profiles, map, autoSpawns);
        stash = new StashMenu(profiles, getLogger());
        pack = new ResourcePackService(this);
        phone = new PhoneService(this);
        phoneMenu = new PhoneMenu(this, phone);
        parcels = new Parcels(this);
        meds = new Meds(this);
        specs.meds(meds);
        crates = new Crates(this);
        ranks = new Ranks(this);
        kits = new Kits(this);
        miniMap = new MiniMap(this);
        vehicles = new Vehicles(this);
        specs.vehicles(vehicles);
        armors = new fr.orbit.extraction.armor.Armors(this);
        specs.armors(armors);
        events = new fr.orbit.extraction.event.RaidEvents(this);
        market = new fr.orbit.extraction.phone.Market(this);
        market.loadData();

        mags.load();
        loadSettings();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new GunListener(gunService, items), this);
        pm.registerEvents(new Grenades(this, guns, items, gunService), this);
        pm.registerEvents(infected, this);
        pm.registerEvents(raid, this);
        pm.registerEvents(stash, this);
        pm.registerEvents(pack, this);
        pm.registerEvents(phone, this);
        pm.registerEvents(phoneMenu, this);
        pm.registerEvents(parcels, this);
        pm.registerEvents(meds, this);
        pm.registerEvents(crates, this);
        pm.registerEvents(market, this);
        pm.registerEvents(ranks, this);
        pm.registerEvents(miniMap, this);
        pm.registerEvents(vehicles, this);
        miniMap.enable();
        pack.enable();

        PlayerCommands pc = new PlayerCommands(this);
        bind("raid", pc);
        bind("planque", pc);
        bind("telephone", pc);
        bind("kit", pc);
        bind("carte", pc);
        bind("extraction", new AdminCommand(this));

        gunService.start();
        infected.start();
        infected.startAi();
        raid.start();
        parcels.start();
        meds.start();
        crates.start();
        events.start();
        market.start();
        ranks.start();
        vehicles.start();
        getServer().getScheduler().runTaskTimerAsynchronously(this, mags::save, 1200L, 1200L);
        getLogger().info("Orbit Extraction prêt.");
    }

    @Override
    public void onDisable() {
        if (pack != null) {
            pack.shutdown();
        }
        if (gunService != null) {
            gunService.unaimAll(); // la vitesse de marche (zoom) est sauvegardée avec le joueur
        }
        if (raid != null) {
            raid.shutdown();
        }
        if (parcels != null) {
            parcels.shutdown();
        }
        if (events != null) {
            events.shutdown();
        }
        if (crates != null) {
            crates.shutdown();
        }
        if (vehicles != null) {
            vehicles.shutdown();
        }
        if (infected != null) {
            infected.purge();
        }
        if (mags != null) {
            mags.save();
        }
        if (profiles != null) {
            getServer().getOnlinePlayers().forEach(p -> p.closeInventory());
            profiles.shutdown();
        }
    }

    private void loadSettings() {
        reloadConfig();
        guns.load();
        meds.load(getConfig().getConfigurationSection("soins"));    // avant tout ce qui lit des objets « soin:... »
        map.load();
        autoSpawns.load();
        armors.load(getConfig().getConfigurationSection("armures"));      // avant la boutique, les kits et les caisses
        vehicles.load(getConfig().getConfigurationSection("vehicules"));  // avant la boutique et les caisses (jerricans)
        infected.load(getConfig().getConfigurationSection("infectes"));
        raid.load(getConfig().getConfigurationSection("raid"));
        phone.load(getConfig());
        parcels.load(getConfig().getConfigurationSection("colis"));
        ranks.load(getConfig().getConfigurationSection("rangs"));
        kits.load(getConfig().getConfigurationSection("kits"));
        crates.load(getConfig().getConfigurationSection("caisses"));
        events.load(getConfig().getConfigurationSection("evenements"));     // après les caisses (niveaux epave, largage)
        market.load(getConfig().getConfigurationSection("hdv"));
    }

    public void reloadAll() {
        loadSettings();
        infected.start();
    }

    private void bind(String name, TabExecutor ex) {
        PluginCommand c = getCommand(name);
        if (c == null) {
            getLogger().severe("Commande /" + name + " absente de plugin.yml");
            return;
        }
        c.setExecutor(ex);
        c.setTabCompleter(ex);
    }

    public GunRegistry guns() {
        return guns;
    }

    public ItemSpecs specs() {
        return specs;
    }

    public InfectedService infected() {
        return infected;
    }

    public RaidService raid() {
        return raid;
    }

    public RaidMap map() {
        return map;
    }

    public ProfileStore profiles() {
        return profiles;
    }

    public StashMenu stash() {
        return stash;
    }

    public ResourcePackService pack() {
        return pack;
    }

    public GunItems items() {
        return items;
    }

    public GunService gunService() {
        return gunService;
    }

    public AutoSpawns autoSpawns() {
        return autoSpawns;
    }

    public PhoneService phone() {
        return phone;
    }

    public PhoneMenu phoneMenu() {
        return phoneMenu;
    }

    public Parcels parcels() {
        return parcels;
    }

    public Meds meds() {
        return meds;
    }

    public Crates crates() {
        return crates;
    }

    public Ranks ranks() {
        return ranks;
    }

    public Kits kits() {
        return kits;
    }

    public MiniMap miniMap() {
        return miniMap;
    }

    public Vehicles vehicles() {
        return vehicles;
    }

    public fr.orbit.extraction.armor.Armors armors() {
        return armors;
    }

    public fr.orbit.extraction.event.RaidEvents events() {
        return events;
    }

    public fr.orbit.extraction.phone.Market market() {
        return market;
    }
}
