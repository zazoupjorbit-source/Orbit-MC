package fr.orbitversus;

import fr.orbitversus.arena.ArenaManager;
import fr.orbitversus.command.CombatsCommand;
import fr.orbitversus.command.OneVsOneCommand;
import fr.orbitversus.command.SpecCommand;
import fr.orbitversus.command.TwoVsTwoCommand;
import fr.orbitversus.command.VersusAdminCommand;
import fr.orbitversus.db.VersusDb;
import fr.orbitversus.elo.EloStore;
import fr.orbitversus.history.HistoryStore;
import fr.orbitversus.kit.KitManager;
import fr.orbitversus.match.MatchManager;
import fr.orbitversus.match.QueueManager;
import fr.orbitversus.npc.NpcManager;
import fr.orbitversus.npc.QueueMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * OrbitVersus — Mode Versus PvP compétitif.
 * Matchmaking 1v1/2v2 (PNJ + commandes), arènes aléatoires, Best-of-3, Elo, paris, spectate, historique.
 */
public final class OrbitVersus extends JavaPlugin {

    private VersusDb db;
    private MatchManager matches;

    @Override
    public void onEnable() {
        try {
            this.db = new VersusDb(this);
        } catch (Exception e) {
            getLogger().severe("Base de données impossible à ouvrir : " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        EconomyHook economy = new EconomyHook(getLogger());

        ArenaManager arenas = new ArenaManager(db);
        arenas.loadAll();
        EloStore elo = new EloStore(db);
        HistoryStore history = new HistoryStore(db);
        NpcManager npc = new NpcManager(this);
        QueueMenu menu = new QueueMenu();
        KitManager kits = new KitManager(this);

        this.matches = new MatchManager(this, arenas, elo, history, economy, kits);
        QueueManager queue = new QueueManager(matches);

        getServer().getPluginManager().registerEvents(
                new VersusListener(matches, queue, elo, npc, menu), this);
        getServer().getPluginManager().registerEvents(kits, this); // menu de selection de kit

        // Elo des joueurs déjà connectés (cas d'un /reload).
        Bukkit.getOnlinePlayers().forEach(p -> elo.load(p.getUniqueId(), p.getName()));

        // Tick de matchmaking toutes les 2 secondes.
        Bukkit.getScheduler().runTaskTimer(this, queue::tick, 40L, 40L);

        bind("versus", new VersusAdminCommand(arenas, npc, elo, queue, economy));
        bind("1v1", new OneVsOneCommand(queue));
        bind("2v2", new TwoVsTwoCommand(queue));
        bind("spec", new SpecCommand(matches));
        bind("combats", new CombatsCommand(history));

        getLogger().info("OrbitVersus actif — " + arenas.count() + " arène(s). Que le meilleur gagne.");
    }

    @Override
    public void onDisable() {
        if (matches != null) {
            matches.shutdown();
        }
        if (db != null) {
            db.close();
        }
    }

    private void bind(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Commande absente de plugin.yml : " + name);
            return;
        }
        command.setExecutor(executor);
    }
}
