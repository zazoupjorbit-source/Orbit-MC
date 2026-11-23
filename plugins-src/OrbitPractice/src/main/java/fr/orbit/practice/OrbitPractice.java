package fr.orbit.practice;

import fr.orbit.practice.arena.ArenaBuilder;
import fr.orbit.practice.arena.ArenaStore;
import fr.orbit.practice.kit.ClassPowers;
import fr.orbit.practice.kit.Kits;
import fr.orbit.practice.match.MatchManager;
import fr.orbit.practice.match.QueueManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

/**
 * Orbit Practice HCF : duels classés 1v1 et 2v2 (NoDebuff, classes HCF), Elo par type de combat dans la
 * base MariaDB du réseau, hub et arènes construits par code dans un monde vide.
 * Reprend les files, les manches et l'Elo d'OrbitVersus, et les classes d'OrbitHCF.
 */
public final class OrbitPractice extends JavaPlugin {

    private Db db;
    private EloStore elo;
    private ArenaStore arenas;
    private ArenaBuilder builder;
    private Lobby lobby;
    private MatchManager matches;
    private QueueManager queue;
    private HcfMode hcf;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        db = new Db(this);
        db.connect(new File(getConfig().getString("base.fichier", "../../setup/db.properties")));
        elo = new EloStore(db, getConfig().getInt("elo.depart", 1000), getConfig().getInt("elo.k", 32));

        builder = new ArenaBuilder(getConfig().getString("arenes.monde", "arenes"));
        arenas = new ArenaStore(this);
        arenas.load();
        // Premier démarrage : les arènes sont construites tout de suite. Ensuite, le monde est
        // seulement chargé ; rien n'est reconstruit sans /practice arene generer.
        boolean first = arenas.all().isEmpty();
        World world = builder.world();
        if (world == null) {
            getLogger().severe("Monde des arènes impossible à créer : aucun combat ne pourra démarrer.");
        } else if (first) {
            List<String> built = builder.generate(arenas, getConfig().getInt("arenes.copies-par-theme", 2), false);
            getLogger().info("Premier démarrage : " + built.size() + " arènes construites dans « " + world.getName() + " ».");
        }
        if (world != null && arenas.all().stream().noneMatch(a -> ArenaBuilder.SUMO_THEME.equals(a.theme()))) {
            // Plateformes de Sumo ajoutées après coup : construites une fois, comme les arènes au premier démarrage.
            List<String> sumo = builder.generateSumo(arenas, getConfig().getInt("arenes.plateformes-sumo", 4), false);
            getLogger().info(sumo.size() + " plateformes de Sumo construites.");
        }

        lobby = new Lobby(this, builder);
        Kits kits = new Kits(this);
        matches = new MatchManager(this, arenas, elo, kits, lobby);
        queue = new QueueManager(matches, elo, lobby);
        ClassPowers powers = new ClassPowers(this, matches);
        QueueMenu menu = new QueueMenu(this, queue, matches, elo);
        Commands commands = new Commands(this);

        // Mode HCF (OrbitHCF dans son propre monde) : avant la protection, qui laisse ce monde libre.
        if (getConfig().getBoolean("hcf.actif", true)
                && Bukkit.getPluginManager().isPluginEnabled("OrbitHCF-API")) {
            hcf = new HcfMode(this);
            if (!hcf.enable()) {
                hcf = null;
            }
        } else {
            getLogger().info("Mode HCF absent (OrbitHCF-API non installé ou hcf.actif: false).");
        }

        Protection protection = new Protection(this);
        Bukkit.getPluginManager().registerEvents(protection, this);
        int purged = protection.purgeHostiles();
        if (purged > 0) {
            getLogger().info(purged + " créature(s) hostile(s) retirée(s).");
        }
        VieuxCombat vieuxCombat = new VieuxCombat(this);
        Bukkit.getPluginManager().registerEvents(vieuxCombat, this);
        Bukkit.getPluginManager().registerEvents(kits, this);
        Bukkit.getPluginManager().registerEvents(powers, this);
        Bukkit.getPluginManager().registerEvents(
                new PracticeListener(this, matches, queue, elo, lobby, menu, powers, commands), this);
        for (String name : List.of("practice", "duel", "party", "spec", "quitter", "hcf", "hub")) {
            PluginCommand command = getCommand(name);
            if (command != null) {
                command.setExecutor(commands);
                command.setTabCompleter(commands);
            }
        }

        // Une fois par seconde : formation des combats, pouvoirs de classe, plein jour dans les arènes.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            queue.tick();
            powers.tick();
            vieuxCombat.tick();
            World w = Bukkit.getWorld(builder.worldName());
            if (w != null && w.getTime() != 6000) {
                w.setTime(6000);
            }
        }, 20L, 20L);

        Bukkit.getOnlinePlayers().forEach(p -> elo.load(p.getUniqueId(), p.getName()));
        getLogger().info("OrbitPractice actif : " + arenas.all().size() + " arènes, Elo "
                + (db.available() ? "en base MariaDB" : "en mémoire") + ".");
    }

    @Override
    public void onDisable() {
        if (hcf != null) {
            hcf.disable();
        }
        if (matches != null) {
            matches.shutdown();
        }
        if (db != null) {
            db.close();
        }
    }

    public EloStore elo() { return elo; }
    public ArenaStore arenas() { return arenas; }
    public ArenaBuilder builder() { return builder; }
    public Lobby lobby() { return lobby; }
    public MatchManager matches() { return matches; }
    public QueueManager queue() { return queue; }
    /** Le mode HCF, ou null s'il n'est pas installé. */
    public HcfMode hcf() { return hcf; }
}
