package fr.orbit.contrebande;

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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * OrbitContrebande : la borne CONTREBANDE de la salle d'arcade cachée de La Station (lobby seulement).
 * Un empire de contrebande aux Docks d'Orbite : chaque organisation (1 à 4 amis) produit, mélange, livre et sème la
 * douane, nuit après nuit. Tout est gardé en base (OrbitCasino) ; chaque nuit se joue dans sa propre copie de la
 * ville, construite par code dans le monde vide {@code contrebande}. Fortune au tableau des records de la salle,
 * Orbit Coins au portefeuille d'OrbitCasino (plafond par jour).
 */
public final class OrbitContrebande extends JavaPlugin implements TabCompleter {

    private World world;
    private NamespacedKey key;
    private Store store;
    private Sessions sessions;
    private Npc npc;
    private Pack pack;
    private boolean[] pretes;
    private final Deque<Integer> aConstruire = new ArrayDeque<>();
    private Chantier chantier;
    private int chantierSlot = -1;

    World world() {
        return world;
    }

    NamespacedKey key() {
        return key;
    }

    Store store() {
        return store;
    }

    Sessions sessions() {
        return sessions;
    }

    Npc npc() {
        return npc;
    }

    Pack pack() {
        return pack;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        key = new NamespacedKey(this, "contrebande");
        Produit.keys(this);
        String name = getConfig().getString("monde", "contrebande");
        world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).generator(new Void()).generateStructures(false).createWorld();
        }
        if (world == null) {
            getLogger().severe("Monde de CONTREBANDE impossible à créer : plugin désactivé.");
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
        Plugin casino = getServer().getPluginManager().getPlugin("OrbitCasino");
        if (casino != null && casino.isEnabled()) {
            try {
                store = new Store(this, (fr.orbit.casino.OrbitCasino) casino);
            } catch (LinkageError | ClassCastException e) {
                getLogger().warning("OrbitCasino incompatible : organisations en mémoire seulement (" + e + ").");
                store = null;
            }
        }
        if (store == null) {
            store = new Store.Memoire(this);
        }
        store.init();
        pack = new Pack(this);
        pack.activer();
        getServer().getPluginManager().registerEvents(pack, this);
        sessions = new Sessions(this);
        getServer().getPluginManager().registerEvents(sessions, this);
        sessions.start();
        pretes = new boolean[sessions.places()];
        npc = new Npc(this);
        getServer().getPluginManager().registerEvents(npc, this);
        npc.startWhenReady();
        var cmd = getCommand("contrebande");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        // les villes manquantes (ou d'une ancienne version) sont construites une à une, sans figer le serveur
        YamlConfiguration y = YamlConfiguration.loadConfiguration(builtFile());
        for (int i = 0; i < pretes.length; i++) {
            pretes[i] = y.getInt("place-" + i, -1) == Ville.VERSION;
            if (!pretes[i]) {
                aConstruire.add(i);
            }
        }
        Bukkit.getScheduler().runTaskLater(this, this::suivante, 60L);
        getLogger().info("CONTREBANDE prête (monde " + world.getName() + ", " + sessions.places() + " villes, "
                + aConstruire.size() + " à construire).");
    }

    @Override
    public void onDisable() {
        if (sessions != null) {
            sessions.stopAll();
        }
        if (store != null) {
            store.attendre();
        }
        if (pack != null) {
            pack.arreter();
        }
        if (npc != null) {
            npc.stop();
        }
    }

    // ------------------------------------------------------------------ villes

    private File builtFile() {
        return new File(getDataFolder(), "construits.yml");
    }

    boolean villePrete(int slot) {
        return slot >= 0 && slot < pretes.length && pretes[slot];
    }

    int pourcent(int slot) {
        if (slot == chantierSlot && chantier != null) {
            return chantier.pourcent();
        }
        return villePrete(slot) ? 100 : 0;
    }

    /** Lance la construction de la prochaine ville de la file. */
    private void suivante() {
        if (chantier != null || !isEnabled()) {
            return;
        }
        Integer slot = aConstruire.poll();
        if (slot == null) {
            return;
        }
        if (sessions.slotBusy(slot)) {
            aConstruire.add(slot);
            Bukkit.getScheduler().runTaskLater(this, this::suivante, 200L);
            return;
        }
        pretes[slot] = false;
        long t0 = System.currentTimeMillis();
        Chantier ch = new Chantier(world, slot, true);
        Ville.plan(ch);
        chantier = ch;
        chantierSlot = slot;
        getLogger().info("Construction de la ville " + slot + " (" + ch.total() + " blocs)…");
        ch.lancer(this, () -> {
            Ville.postes(world, slot, 2, 0, false);
            Ville.grilles(world, slot, false, false);
            // sauvegardé tout de suite : un arrêt brutal juste après ne doit pas laisser construits.yml mentir
            world.save();
            YamlConfiguration y = YamlConfiguration.loadConfiguration(builtFile());
            y.set("place-" + slot, Ville.VERSION);
            try {
                y.save(builtFile());
            } catch (IOException e) {
                getLogger().warning("construits.yml : " + e);
            }
            pretes[slot] = true;
            chantier = null;
            chantierSlot = -1;
            getLogger().info("Ville " + slot + " construite en " + (System.currentTimeMillis() - t0) / 1000 + " s.");
            Bukkit.getScheduler().runTaskLater(this, this::suivante, 40L);
        });
    }

    // ------------------------------------------------------------------ commande

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        Player p = sender instanceof Player pl ? pl : null;
        switch (sub) {
            case "jouer", "rejouer" -> {
                if (p != null) {
                    sessions.play(p);
                }
            }
            case "top", "classement" -> sessions.showTop(sender);
            case "regles", "règles", "aide" -> {
                if (p != null) {
                    Nuit.regles(p);
                }
            }
            case "orga", "organisation" -> {
                if (p != null) {
                    sessions.commandeOrga(p, args);
                }
            }
            case "quitter", "abandonner", "rentrer" -> {
                if (p == null) {
                    return true;
                }
                if (!sessions.playing(p)) {
                    Txt.send(p, "<#9AA4B2>Tu n'es pas aux Docks.");
                } else {
                    sessions.quitter(p, true);
                }
            }
            case "pnj" -> {
                if (admin(sender)) {
                    npc.spawn();
                    Txt.send(sender, "PNJ de CONTREBANDE reposé.");
                }
            }
            case "reconstruire" -> {
                if (!admin(sender)) {
                    return true;
                }
                int n = 0;
                for (int i = 0; i < pretes.length; i++) {
                    if (!sessions.slotBusy(i) && i != chantierSlot && !aConstruire.contains(i)) {
                        aConstruire.add(i);
                        pretes[i] = false;
                        n++;
                    }
                }
                suivante();
                Txt.send(sender, n + " ville(s) en reconstruction, une à une (les villes en pleine nuit attendent l'aube).");
            }
            case "etat" -> {
                if (!admin(sender)) {
                    return true;
                }
                StringBuilder v = new StringBuilder();
                for (int i = 0; i < pretes.length; i++) {
                    v.append(i > 0 ? ", " : "").append(i).append(pretes[i] ? " prête" : " " + pourcent(i) + " %");
                }
                Txt.send(sender, sessions.all().size() + " nuit(s) en cours · base " + (store.available() ? "ok" : "absente (mémoire)")
                        + " · villes : " + v);
                for (Nuit n : sessions.all()) {
                    List<String> noms = new ArrayList<>();
                    for (Nuit.Joueur j : n.joueurs.values()) {
                        noms.add(j.p.getName());
                    }
                    sender.sendMessage(Txt.mm("  <#E6EDF3>" + n.orga.nom + "</#E6EDF3> <#9AA4B2>(" + String.join(", ", noms) + ") · ville " + n.slot
                            + " · " + Txt.clock(n.tick / 20) + " · caisse " + Txt.n(n.orga.credits) + " cr · +" + Txt.n(n.gains) + " cr · "
                            + n.ventes + " vente(s) · " + n.alertes + " alerte(s) · avis " + n.orga.chaleur + " %"));
                }
            }
            case "tp" -> {
                if (p != null && admin(sender)) {
                    int slot = args.length > 1 ? Math.max(0, Math.min(pretes.length - 1, parse(args[1]))) : 0;
                    p.teleport(new Location(world, Ville.ox(slot) + Ville.W / 2.0, 100, Ville.D + 10, 180f, 35f));
                }
            }
            default -> {
                Txt.send(sender, "Bâtis ton empire aux Docks d'Orbite : parle à la borne de la salle d'arcade.");
                sender.sendMessage(Txt.mm("  <#9AA4B2>/contrebande jouer · top · regles · orga · quitter"));
            }
        }
        return true;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean admin(CommandSender sender) {
        if (!sender.hasPermission("orbitcontrebande.admin")) {
            Txt.send(sender, "<#E0484A>Réservé au staff.");
            return false;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> subs = new ArrayList<>();
        if (args.length == 1) {
            subs.addAll(List.of("jouer", "top", "regles", "orga", "quitter"));
            if (sender.hasPermission("orbitcontrebande.admin")) {
                subs.addAll(List.of("etat", "pnj", "reconstruire", "tp"));
            }
        } else if (args.length == 2 && args[0].toLowerCase(Locale.ROOT).startsWith("orga")) {
            subs.addAll(List.of("info", "inviter", "accepter", "refuser", "renommer", "chef", "exclure", "quitter"));
        } else if (args.length == 3 && args[0].toLowerCase(Locale.ROOT).startsWith("orga") && args[1].equalsIgnoreCase("inviter")) {
            for (Player pl : Bukkit.getOnlinePlayers()) {
                subs.add(pl.getName());
            }
        } else {
            return List.of();
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : subs) {
            if (s.toLowerCase(Locale.ROOT).startsWith(last)) {
                out.add(s);
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
