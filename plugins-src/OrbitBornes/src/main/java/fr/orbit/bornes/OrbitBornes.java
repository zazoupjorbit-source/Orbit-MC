package fr.orbit.bornes;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * OrbitBornes : les bornes d'arcade solo de la salle cachée de La Station (lobby seulement).
 * ASTROBLOCS (blocs qui tombent, solo + duel), CRISTAUX D'ORBIT (match-3 en écran HD), DÉFENSE ORBITALE (tir spatial)
 * et PULSAR (rythme). Les jeux assis se jouent dans le monde vide {@code bornes} ; les scores vont au tableau des records
 * de la salle et les Orbit Coins au portefeuille d'OrbitCasino (plafond par jour).
 */
public final class OrbitBornes extends JavaPlugin implements TabCompleter {

    private World world;
    private NamespacedKey key;
    private Pack pack;
    private Bank bank;
    private Sessions sessions;
    private Npcs npcs;

    public World world() {
        return world;
    }

    public NamespacedKey key() {
        return key;
    }

    public Pack pack() {
        return pack;
    }

    /** Pont vers OrbitCasino (null s'il n'est pas chargé). */
    Bank bank() {
        return bank;
    }

    public Sessions sessions() {
        return sessions;
    }

    /** Étoiles de CRISTAUX (toutes à 0 sans base). */
    public void loadStars(java.util.UUID id, int levels, java.util.function.Consumer<int[]> done) {
        if (bank == null) {
            done.accept(new int[levels]);
        } else {
            bank.loadStars(id, levels, done);
        }
    }

    public void saveStars(Player p, int[] stars) {
        if (bank != null) {
            bank.saveStars(p.getUniqueId(), p.getName(), stars.clone());
        }
    }

    public Npcs npcs() {
        return npcs;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        key = new NamespacedKey(this, "borne");
        Hud.load(this);
        String name = getConfig().getString("monde", "bornes");
        world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).generator(new Void()).generateStructures(false).createWorld();
        }
        if (world == null) {
            getLogger().severe("Monde des bornes impossible à créer : plugin désactivé.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        world.setTime(18000);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        for (Entity e : world.getEntities()) {
            if (e.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                e.remove();
            }
        }
        pack = new Pack(this);
        pack.enable();
        getServer().getPluginManager().registerEvents(pack, this);
        Plugin casino = getServer().getPluginManager().getPlugin("OrbitCasino");
        if (casino != null && casino.isEnabled()) {
            try {
                bank = new Bank(this, (fr.orbit.casino.OrbitCasino) casino);
                bank.init();
            } catch (LinkageError | ClassCastException e) {
                getLogger().warning("OrbitCasino incompatible : scores et Orbit Coins désactivés (" + e + ").");
                bank = null;
            }
        } else {
            getLogger().warning("OrbitCasino absent : les bornes tournent sans scores ni Orbit Coins.");
        }
        sessions = new Sessions(this);
        getServer().getPluginManager().registerEvents(sessions, this);
        sessions.start();
        getServer().getPluginManager().registerEvents(new fr.orbit.bornes.cristaux.CristauxGame.Clicks(this), this);
        npcs = new Npcs(this);
        getServer().getPluginManager().registerEvents(npcs, this);
        npcs.startWhenReady();
        var cmd = getCommand("bornes");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getLogger().info("Bornes prêtes : ASTROBLOCS, CRISTAUX D'ORBIT, DÉFENSE ORBITALE, PULSAR (monde " + world.getName() + ").");
    }

    @Override
    public void onDisable() {
        if (sessions != null) {
            sessions.stopAll();
        }
        if (npcs != null) {
            npcs.stop();
        }
        if (pack != null) {
            pack.shutdown();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        Player p = sender instanceof Player pl ? pl : null;
        switch (sub) {
            case "top", "classement" -> {
                GameType t = args.length > 1 ? GameType.of(args[1]) : null;
                if (t == null) {
                    Txt.send(sender, "Classement de quel jeu ? <#9AA4B2>astroblocs, cristaux, defense, pulsar");
                    return true;
                }
                sessions.showTop(sender, t);
            }
            case "rejouer" -> {
                if (p != null) {
                    sessions.replay(p);
                }
            }
            case "jouer" -> {
                if (p == null) {
                    return true;
                }
                GameType t = args.length > 1 ? GameType.of(args[1]) : null;
                if (t == null) {
                    Txt.send(p, "Quel jeu ? <#9AA4B2>astroblocs, cristaux, defense, pulsar");
                    return true;
                }
                sessions.play(p, t, java.util.Arrays.copyOfRange(args, 2, args.length));
            }
            case "quitter", "abandonner" -> {
                if (p == null) {
                    return true;
                }
                if (sessions.cancelWait(p)) {
                    Txt.send(p, "Recherche de duel annulée.");
                    return true;
                }
                Game g = sessions.game(p);
                if (g == null) {
                    Txt.send(p, "<#9AA4B2>Tu n'es dans aucune partie.");
                } else {
                    sessions.finish(g, "<#E0484A><b>Partie abandonnée</b>");
                }
            }
            case "reprendre" -> {
                if (p != null && sessions.game(p) instanceof fr.orbit.bornes.cristaux.CristauxGame cg) {
                    cg.reopen();
                }
            }
            case "pnj" -> {
                if (!sender.hasPermission("orbitbornes.admin")) {
                    Txt.send(sender, "<#E0484A>Réservé au staff.");
                    return true;
                }
                npcs.spawnAll();
                Txt.send(sender, "PNJ des bornes reposés.");
            }
            case "etat" -> {
                if (!sender.hasPermission("orbitbornes.admin")) {
                    Txt.send(sender, "<#E0484A>Réservé au staff.");
                    return true;
                }
                Txt.send(sender, sessions.count() + " partie(s) en cours · pack " + (pack.ready() ? "servi" : "absent")
                        + " · base " + (bank != null && bank.available() ? "ok" : "absente"));
                for (Game g : sessions.all()) {
                    sender.sendMessage(Txt.mm("  <#E6EDF3>" + g.player().getName() + "</#E6EDF3> <#9AA4B2>" + g.type().name
                            + " · place " + g.slot + " · " + Math.round(g.score())));
                }
            }
            case "tp" -> {
                if (p != null && p.hasPermission("orbitbornes.admin")) {
                    p.teleport(new Location(world, 4.5, 110, 30, 180f, 0f));
                }
            }
            default -> {
                Txt.send(sender, "Bornes d'arcade de la salle cachée : parle aux PNJ.");
                sender.sendMessage(Txt.mm("  <#9AA4B2>/bornes rejouer · /bornes top <jeu> · /bornes quitter"));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : new String[]{"rejouer", "top", "quitter", "reprendre", "jouer"}) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("top") || args[0].equalsIgnoreCase("jouer"))) {
            for (GameType t : GameType.values()) {
                if (t.id.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(t.id);
                }
            }
        }
        return out;
    }

    /** Monde entièrement vide. */
    static final class Void extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, 100, 0.5);
        }

        @Override
        public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
            return new BiomeProvider() {
                @Override
                public Biome getBiome(WorldInfo info, int x, int y, int z) {
                    return Biome.THE_VOID;
                }

                @Override
                public List<Biome> getBiomes(WorldInfo info) {
                    return List.of(Biome.THE_VOID);
                }
            };
        }
    }
}
