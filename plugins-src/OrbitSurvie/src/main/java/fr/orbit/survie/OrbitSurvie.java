package fr.orbit.survie;

import fr.orbit.survie.ile.Dessin;
import fr.orbit.survie.ile.IleService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Orbit Survie : le monde de survie reste vanilla ; le spawn est une île tropicale construite par code dans
 * son propre monde (place, portail aléatoire, marché, tour des enchantements, hôtel des ventes, arène de
 * duel, port). Économie en crédits dans la base MariaDB du réseau. Autour : grades achetés en crédits,
 * contrats du jour, métiers, classements, événements automatiques, duels et tournoi, échoppes de joueurs,
 * points de retour, monde ressources.
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
    private Stats stats;
    private Grades grades;
    private Contrats contrats;
    private Metiers metiers;
    private Classements classements;
    private Evenements evenements;
    private Duels duels;
    private Echoppes echoppes;
    private Tp tp;
    private Ressources ressources;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Msg.monnaie(getConfig().getString("monnaie.nom", "crédits"));

        db = new Db(this);
        db.connect(new File(getConfig().getString("base.fichier", "../../setup/db.properties")));
        eco = new Economie(db, getConfig().getDouble("monnaie.depart", 100));

        String style = getConfig().getString("ile.style", "tropical");
        Dessin dessin = style.equalsIgnoreCase("orbital") ? Dessin.orbital() : Dessin.tropical();
        ile = new IleService(this, getConfig().getString("ile.monde", "ile"), dessin);
        World world = ile.world();
        if (world == null) {
            getLogger().severe("Monde du spawn impossible à créer.");
        }
        pnj = new Pnj(this, ile);
        marche = new Marche(this);
        marche.charger();
        shop = new Shop(this, marche);
        hdv = new Hdv(this);
        bases = new Bases(this);
        bases.lireBase();
        kits = new Kits(this);
        stats = new Stats(this);
        grades = new Grades(this);
        contrats = new Contrats(this);
        metiers = new Metiers(this);
        classements = new Classements(this);
        evenements = new Evenements(this);
        echoppes = new Echoppes(this);
        echoppes.lireBase();
        Commandes commandes = new Commandes(this);
        pnj.action(commandes::pnj);
        ile.apresGeneration(() -> {
            pnj.retirer();
            pnj.tick();
            marquerGeneree();
        });

        Protection protection = new Protection(this);
        Voyages voyages = new Voyages(this);
        duels = new Duels(this, protection);
        tp = new Tp(this, voyages);
        ressources = new Ressources(this, voyages);
        BaseProtection baseProtection = new BaseProtection(bases);
        baseProtection.libre(echoppes::est);
        Bukkit.getPluginManager().registerEvents(protection, this);
        Bukkit.getPluginManager().registerEvents(pnj, this);
        Bukkit.getPluginManager().registerEvents(new Menu.Ecoute(), this);
        Bukkit.getPluginManager().registerEvents(voyages, this);
        Bukkit.getPluginManager().registerEvents(bases, this);
        Bukkit.getPluginManager().registerEvents(baseProtection, this);
        Bukkit.getPluginManager().registerEvents(metiers, this);
        Bukkit.getPluginManager().registerEvents(evenements, this);
        Bukkit.getPluginManager().registerEvents(duels, this);
        Bukkit.getPluginManager().registerEvents(echoppes, this);
        Bukkit.getPluginManager().registerEvents(tp, this);
        Bukkit.getPluginManager().registerEvents(ressources, this);
        commandes(bases, "base");
        Bukkit.getScheduler().runTaskTimer(this, bases::surveiller, 1200L, 12000L);
        marche.demarrer();
        Bukkit.getPluginManager().registerEvents(shop, this);
        commandes(shop, "shop", "vendre", "prix");
        hud = new Hud(this);
        Bukkit.getPluginManager().registerEvents(hud, this);
        hud.demarrer();
        commandes(commandes, "ile", "argent", "payer", "richesses", "hdv", "kit", "grade", "contrats", "metiers", "classement");
        commandes(evenements, "evenement");
        commandes(duels, "duel", "pari", "tournoi");
        commandes(echoppes, "echoppe", "echoppes");
        commandes(tp, "home", "sethome", "delhome", "homes", "tpa", "tpaccept", "tprefuse");
        commandes(ressources, "ressources");

        // PNJ, panneaux et étiquettes des échoppes (toutes les 5 s), plein jour sur l'île.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            pnj.tick();
            echoppes.affiches();
            World w = Bukkit.getWorld(ile.worldName());
            if (w != null) {
                w.setTime(getConfig().getLong("ile.heure", 6000));
            }
        }, 40L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, pnj::ambiance, 60L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            evenements.tic();
            duels.tic();
        }, 100L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, classements::tic, 80L, 300L);
        Bukkit.getScheduler().runTaskTimer(this, stats::vider, 600L, 600L);
        Bukkit.getScheduler().runTaskTimer(this, metiers::tic, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, classements::lotsDuMoisPasse, 200L, 12000L);
        Bukkit.getOnlinePlayers().forEach(metiers::lire);

        // Premier démarrage : l'île est construite tout de suite. Ensuite, rien n'est reposé sans /ile generer.
        if (world != null && !marque().isFile()) {
            getLogger().info("Premier démarrage : construction du spawn…");
            ile.parcourir(Bukkit.getConsoleSender(), true);
        }
        ressources.demarrer();
        getLogger().info("OrbitSurvie actif : spawn « " + ile.worldName() + " », économie "
                + (db.available() ? "en base MariaDB" : "COUPÉE (base injoignable)") + ".");
    }

    private <T extends CommandExecutor & TabCompleter> void commandes(T executeur, String... noms) {
        for (String nom : noms) {
            PluginCommand c = getCommand(nom);
            if (c != null) {
                c.setExecutor(executeur);
                c.setTabCompleter(executeur);
            } else {
                getLogger().warning("Commande absente de plugin.yml : /" + nom);
            }
        }
    }

    /** Marque « déjà construit » propre à chaque monde de spawn (ile-generee pour l'ancienne île). */
    private File marque() {
        String w = ile.worldName();
        return new File(getDataFolder(), w.equals("ile") ? "ile-generee" : "generee-" + w);
    }

    private void marquerGeneree() {
        try {
            getDataFolder().mkdirs();
            marque().createNewFile();
        } catch (IOException e) {
            getLogger().warning("Marque ile-generee non écrite : " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (hud != null) {
            hud.arreter();
        }
        if (evenements != null) {
            evenements.terminer();
        }
        if (echoppes != null) {
            echoppes.retirerAffiches();
        }
        if (metiers != null) {
            metiers.fermer();
        }
        if (stats != null) {
            stats.vider();
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
    public Stats stats() { return stats; }
    public Grades grades() { return grades; }
    public Contrats contrats() { return contrats; }
    public Metiers metiers() { return metiers; }
    public Classements classements() { return classements; }
    public Evenements evenements() { return evenements; }
    public Duels duels() { return duels; }
    public Echoppes echoppes() { return echoppes; }
    public Ressources ressources() { return ressources; }
}
