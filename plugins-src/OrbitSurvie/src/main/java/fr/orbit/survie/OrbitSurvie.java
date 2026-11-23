package fr.orbit.survie;

import fr.orbit.survie.ile.IleService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Orbit Survie : le monde de survie reste vanilla ; le spawn est une île tropicale construite par code dans
 * son propre monde (place, portail aléatoire, marché, tour des enchantements, hôtel des ventes, arène de
 * duel, port). Économie en crédits dans la base MariaDB du réseau.
 */
public final class OrbitSurvie extends JavaPlugin {

    private Db db;
    private Economie eco;
    private IleService ile;
    private Pnj pnj;
    private Marche marche;
    private Shop shop;
    private Hdv hdv;
    private Kits kits;
    private Bases bases;
    private Hud hud;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Msg.monnaie(getConfig().getString("monnaie.nom", "crédits"));

        db = new Db(this);
        db.connect(new File(getConfig().getString("base.fichier", "../../setup/db.properties")));
        eco = new Economie(db, getConfig().getDouble("monnaie.depart", 100));

        ile = new IleService(this, getConfig().getString("ile.monde", "ile"));
        World world = ile.world();
        if (world == null) {
            getLogger().severe("Monde de l'île impossible à créer.");
        }
        pnj = new Pnj(this, ile);
        marche = new Marche(this);
        marche.charger();
        shop = new Shop(this, marche);
        hdv = new Hdv(this);
        bases = new Bases(this);
        bases.lireBase();
        kits = new Kits(this);
        Commandes commandes = new Commandes(this);
        pnj.action(commandes::pnj);
        ile.apresGeneration(() -> {
            pnj.retirer();
            pnj.tick();
            marquerGeneree();
        });

        Protection protection = new Protection(this);
        Bukkit.getPluginManager().registerEvents(protection, this);
        Bukkit.getPluginManager().registerEvents(pnj, this);
        Bukkit.getPluginManager().registerEvents(new Menu.Ecoute(), this);
        Bukkit.getPluginManager().registerEvents(new Voyages(this), this);
        Bukkit.getPluginManager().registerEvents(bases, this);
        Bukkit.getPluginManager().registerEvents(new BaseProtection(bases), this);
        PluginCommand base = getCommand("base");
        if (base != null) {
            base.setExecutor(bases);
            base.setTabCompleter(bases);
        }
        Bukkit.getScheduler().runTaskTimer(this, bases::surveiller, 1200L, 12000L);
        marche.demarrer();
        Bukkit.getPluginManager().registerEvents(shop, this);
        for (String name : List.of("shop", "vendre", "prix")) {
            PluginCommand c = getCommand(name);
            if (c != null) {
                c.setExecutor(shop);
                c.setTabCompleter(shop);
            }
        }
        hud = new Hud(this);
        Bukkit.getPluginManager().registerEvents(hud, this);
        hud.demarrer();
        for (String name : List.of("ile", "argent", "payer", "richesses", "hdv", "kit")) {
            PluginCommand c = getCommand(name);
            if (c != null) {
                c.setExecutor(commandes);
                c.setTabCompleter(commandes);
            }
        }

        // PNJ et panneaux (toutes les 5 s), plein jour sur l'île.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            pnj.tick();
            World w = Bukkit.getWorld(ile.worldName());
            if (w != null) {
                w.setTime(6000);
            }
        }, 40L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, pnj::ambiance, 60L, 10L);

        // Premier démarrage : l'île est construite tout de suite. Ensuite, rien n'est reposé sans /ile generer.
        if (world != null && !new File(getDataFolder(), "ile-generee").isFile()) {
            getLogger().info("Premier démarrage : construction de l'île…");
            ile.parcourir(Bukkit.getConsoleSender(), true);
        }
        getLogger().info("OrbitSurvie actif : île « " + ile.worldName() + " », économie "
                + (db.available() ? "en base MariaDB" : "COUPÉE (base injoignable)") + ".");
    }

    private void marquerGeneree() {
        try {
            getDataFolder().mkdirs();
            new File(getDataFolder(), "ile-generee").createNewFile();
        } catch (IOException e) {
            getLogger().warning("Marque ile-generee non écrite : " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (hud != null) {
            hud.arreter();
        }
        if (marche != null) {
            marche.sauver(true);
        }
        if (pnj != null) {
            pnj.retirer();
        }
        if (db != null) {
            db.close();
        }
    }

    public Db db() { return db; }
    public Economie eco() { return eco; }
    public IleService ile() { return ile; }
    public Pnj pnj() { return pnj; }
    public Marche marche() { return marche; }
    public Shop shop() { return shop; }
    public Hdv hdv() { return hdv; }
    public Kits kits() { return kits; }
    public Bases bases() { return bases; }
}
