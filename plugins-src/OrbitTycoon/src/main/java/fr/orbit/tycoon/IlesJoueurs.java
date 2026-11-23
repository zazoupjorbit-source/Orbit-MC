package fr.orbit.tycoon;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Îles des joueurs (/is) : un monde vide à part, une île par équipe rangée sur une grille (une case = 1000 blocs).
 * Chaque île est délimitée (100 × 100 au départ) par un mur invisible ; les améliorations (/is upgrade) l'agrandissent.
 * Données : plugins/OrbitTycoon/iles.yml.
 */
final class IlesJoueurs implements Listener {

    static final String NOM_MONDE = "iles_joueurs";
    /** Écart entre deux îles et nombre d'îles par rangée de la grille. */
    static final int CASE = 1000, PAR_RANGEE = 100;
    static final int Y = 100;
    private static final long INVITATION_MS = 5 * 60_000L;

    enum Rang {
        MEMBRE("Membre", "#B0BEC5"), OFFICIER("Officier", "#62CFF4"), CHEF("Chef", "#E0B864");
        final String nom, couleur;
        Rang(String nom, String couleur) { this.nom = nom; this.couleur = couleur; }
        String joli() { return "<" + couleur + ">" + nom + "</" + couleur + ">"; }
    }

    static final class Ile {
        final int index;
        String nom;
        int palier;   // 0 = taille de départ
        final Map<UUID, Rang> membres = new LinkedHashMap<>();
        final Set<UUID> bannis = new HashSet<>();
        Ile(int index) { this.index = index; }
        int cx() { return (index % PAR_RANGEE) * CASE; }
        int cz() { return (index / PAR_RANGEE) * CASE; }
        UUID chef() {
            for (var e : membres.entrySet()) if (e.getValue() == Rang.CHEF) return e.getKey();
            return null;
        }
    }

    private final OrbitTycoon plugin;
    private final Map<Integer, Ile> parIndex = new HashMap<>();
    private final Map<UUID, Ile> parJoueur = new HashMap<>();
    /** Invité -> (île -> fin de validité). */
    private final Map<UUID, Map<Integer, Long>> invitations = new HashMap<>();
    /** Confirmations en attente (joueur -> action), valables 30 s. */
    private final Map<UUID, Long> confirmations = new HashMap<>();
    private final Map<UUID, Long> dernierAvertissement = new HashMap<>();
    private int prochainIndex = 1;
    private World monde;

    IlesJoueurs(OrbitTycoon plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ monde et données

    /** Générateur vide : rien n'est généré, seules les îles construites existent. */
    private static final class Vide extends ChunkGenerator { }

    void demarrer() {
        monde = new WorldCreator(NOM_MONDE).generator(new Vide()).generateStructures(false).createWorld();
        if (monde == null) { plugin.getLogger().severe("Impossible de créer le monde des îles."); return; }
        monde.setGameRule(GameRules.ADVANCE_TIME, false);
        monde.setGameRule(GameRules.ADVANCE_WEATHER, false);
        monde.setGameRule(GameRules.SPAWN_MOBS, false);
        monde.setGameRule(GameRules.SPAWN_PATROLS, false);
        monde.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        monde.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        monde.setGameRule(GameRules.FALL_DAMAGE, false);
        monde.setGameRule(GameRules.KEEP_INVENTORY, true);
        monde.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        monde.setGameRule(GameRules.PVP, false);
        monde.setTime(18000);   // nuit permanente (minuit)
        monde.setStorm(false);
        monde.setSpawnLocation(0, Y + 1, 0);
        charger();
    }

    static boolean estMonde(World w) { return w != null && w.getName().equals(NOM_MONDE); }

    private File fichier() { return new File(plugin.getDataFolder(), "iles.yml"); }

    private void charger() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier());
        prochainIndex = Math.max(1, y.getInt("prochain-index", 1));
        ConfigurationSection s = y.getConfigurationSection("iles");
        if (s == null) return;
        for (String cle : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(cle);
            if (c == null) continue;
            Ile ile = new Ile(Integer.parseInt(cle));
            ile.nom = c.getString("nom", "Île");
            ile.palier = c.getInt("palier", 0);
            ConfigurationSection m = c.getConfigurationSection("membres");
            if (m != null) for (String id : m.getKeys(false)) {
                try { ile.membres.put(UUID.fromString(id), Rang.valueOf(m.getString(id, "MEMBRE"))); }
                catch (IllegalArgumentException ignore) { }
            }
            for (String id : c.getStringList("bannis")) {
                try { ile.bannis.add(UUID.fromString(id)); } catch (IllegalArgumentException ignore) { }
            }
            parIndex.put(ile.index, ile);
            for (UUID id : ile.membres.keySet()) parJoueur.put(id, ile);
            prochainIndex = Math.max(prochainIndex, ile.index + 1);
        }
        plugin.getLogger().info(parIndex.size() + " île(s) de joueurs chargée(s).");
    }

    /** Construit le YAML sur le thread principal, l'écrit sur le disque en asynchrone. */
    private void sauver() {
        YamlConfiguration y = new YamlConfiguration();
        sauverDans(y);
        String texte = y.saveToString();
        File f = fichier();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> ecrire(f, texte));
    }

    /** Sauvegarde immédiate (à l'arrêt du serveur). */
    void arreter() {
        if (monde == null) return;
        YamlConfiguration y = new YamlConfiguration();
        sauverDans(y);
        ecrire(fichier(), y.saveToString());
    }

    private void sauverDans(YamlConfiguration y) {
        y.set("prochain-index", prochainIndex);
        for (Ile ile : parIndex.values()) {
            String b = "iles." + ile.index + ".";
            y.set(b + "nom", ile.nom);
            y.set(b + "palier", ile.palier);
            for (var e : ile.membres.entrySet()) y.set(b + "membres." + e.getKey(), e.getValue().name());
            y.set(b + "bannis", ile.bannis.stream().map(UUID::toString).toList());
        }
    }

    private synchronized void ecrire(File f, String texte) {
        try {
            f.getParentFile().mkdirs();
            File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
            Files.writeString(tmp.toPath(), texte, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible d'écrire iles.yml : " + e);
        }
    }

    // ------------------------------------------------------------------ tailles (paliers)

    private List<Integer> tailles() {
        List<Integer> l = plugin.getConfig().getIntegerList("iles.tailles");
        return l.isEmpty() ? List.of(100) : l;
    }

    private List<Long> couts() { return plugin.getConfig().getLongList("iles.couts"); }

    int taille(Ile ile) {
        List<Integer> t = tailles();
        return Math.min(CASE - 100, t.get(Math.min(ile.palier, t.size() - 1)));
    }

    /** Vrai si (x, z) est dans la zone autorisée de l'île. Taille paire : de cx - t/2 à cx + t/2 - 1. */
    boolean dedans(Ile ile, double x, double z) {
        int t = taille(ile), min = -t / 2, max = min + t;
        double dx = x - ile.cx(), dz = z - ile.cz();
        return dx >= min && dx < max && dz >= min && dz < max;
    }

    /** Île de la case de la grille où se trouve (x, z), ou null. */
    Ile ileA(double x, double z) {
        int gx = (int) Math.round(x / CASE), gz = (int) Math.round(z / CASE);
        if (gx < 0 || gx >= PAR_RANGEE || gz < 0) return null;
        return parIndex.get(gz * PAR_RANGEE + gx);
    }

    Ile ileDe(UUID id) { return parJoueur.get(id); }

    Location arrivee(Ile ile) {
        return new Location(monde, ile.cx() + 0.5, Y + 1, ile.cz() + 4.5, 180f, 0f);
    }

    // ------------------------------------------------------------------ construction de l'île de départ

    /** Rayon de l'astéroïde de départ (15 = environ 31 × 31 blocs). */
    private static final int RAYON_ILE = 15;

    /** Astéroïde de départ (≈ 31 × 31) au centre de la zone : herbe, roche en cône dessous, un arbre et un pad. */
    private void construire(Ile ile) {
        int cx = ile.cx(), cz = ile.cz();
        monde.getChunkAt(cx >> 4, cz >> 4).load(true);
        plateforme(ile);
        // Pad d'arrivée (bleu et or), au sud.
        for (int dx = -1; dx <= 1; dx++) for (int dz = 3; dz <= 5; dz++)
            monde.getBlockAt(cx + dx, Y, cz + dz).setType(dx == 0 && dz == 4 ? Material.SEA_LANTERN
                    : (dx + dz) % 2 == 0 ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_CONCRETE, false);
        // Balise lumineuse au bord.
        monde.getBlockAt(cx - 4, Y + 1, cz - 3).setType(Material.END_ROD, false);
        monde.getBlockAt(cx + 4, Y + 1, cz - 3).setType(Material.END_ROD, false);
        // Un arbre pour démarrer.
        Block sol = monde.getBlockAt(cx - 2, Y, cz - 2);
        if (!monde.generateTree(sol.getLocation().add(0, 1, 0), TreeType.TREE))
            sol.getRelative(0, 1, 0).setType(Material.OAK_SAPLING, false);
        decorer(ile);
    }

    // ------------------------------------------------------------------ décor spatial autour de l'île

    /** Pose le décor spatial de toutes les îles existantes (ne remplace que de l'air). Renvoie le nombre de blocs posés. */
    int decorerTout() {
        int n = 0;
        for (Ile ile : parIndex.values()) n += plateforme(ile) + decorer(ile);
        return n;
    }

    /**
     * Plateforme de l'astéroïde (disque d'herbe de rayon RAYON_ILE, roche en cône dessous).
     * Ne pose que dans l'air : sur une île existante, elle agrandit le bord sans toucher aux constructions.
     */
    int plateforme(Ile ile) {
        int cx = ile.cx(), cz = ile.cz(), r = RAYON_ILE, n = 0;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > r + 0.4) continue;
            int x = cx + dx, z = cz + dz;
            Block haut = monde.getBlockAt(x, Y, z);
            if (haut.getType().isAir()) { haut.setType(Material.GRASS_BLOCK, false); n++; }
            int prof = (int) Math.round((r + 1 - d) * 0.9) + 1;
            for (int k = 1; k <= prof; k++) {
                Block b = monde.getBlockAt(x, Y - k, z);
                if (!b.getType().isAir()) continue;
                Material m = k <= 2 ? Material.DIRT : (Math.floorMod(x * 31 + z * 17 + k, 5) == 0 ? Material.DEEPSLATE : Material.STONE);
                if (k == prof && Math.floorMod(x + z, 3) == 0) m = Material.LIGHT_BLUE_STAINED_GLASS;
                b.setType(m, false);
                n++;
            }
        }
        return n;
    }

    /**
     * Décor spatial dans la zone de départ (100 × 100) : antenne sur l'astéroïde, anneau orbital incliné au-dessus,
     * satellite à panneaux solaires, petite planète à anneau, trois mini-astéroïdes à cristaux.
     * Ne pose que dans l'air : sans risque sur une île déjà construite par les joueurs.
     */
    int decorer(Ile ile) { return decorerPlateforme(ile) + decorerCiel(ile); }

    /**
     * Décor posé sur la plateforme centrale : allée dorée depuis le pad, cercle bleu et or, 4 lampadaires stellaires,
     * mini-fusée sur son pas de tir, antenne radar, jardin de cristaux et de fleurs bleues.
     * Au sol, ne remplace que l'herbe ; au-dessus, que l'air.
     */
    private int decorerPlateforme(Ile ile) {
        int cx = ile.cx(), cz = ile.cz();
        int[] n = {0};
        // Pose (dx, dy, dz) : au niveau du sol (dy = 0) seulement sur l'herbe, au-dessus seulement dans l'air.
        java.util.function.BiConsumer<int[], Material> poser = (p, m) -> {
            Block b = monde.getBlockAt(cx + p[0], Y + p[1], cz + p[2]);
            boolean libre = p[1] == 0 ? b.getType() == Material.GRASS_BLOCK : b.getType().isAir();
            if (libre) { b.setType(m, false); n[0]++; }
        };
        // Allée du pad d'arrivée jusqu'au bord sud : bord doré, centre bleu, lanterne tous les 4 blocs.
        for (int dz = 6; dz <= 14; dz++)
            for (int dx = -1; dx <= 1; dx++)
                poser.accept(new int[]{dx, 0, dz}, dx != 0 ? Material.GOLD_BLOCK
                        : dz % 4 == 0 ? Material.SEA_LANTERN : Material.LIGHT_BLUE_CONCRETE);
        // Cercle bleu et or (rayon 11).
        for (int t = 0; t < 360; t += 2) {
            double a = Math.toRadians(t);
            int x = (int) Math.round(11 * Math.cos(a)), z = (int) Math.round(11 * Math.sin(a));
            poser.accept(new int[]{x, 0, z}, t % 30 == 0 ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_CONCRETE);
        }
        // 4 lampadaires stellaires sur les diagonales.
        for (int[] l : new int[][]{{9, 9}, {-9, 9}, {9, -9}, {-9, -9}}) {
            poser.accept(new int[]{l[0], 0, l[1]}, Material.POLISHED_DEEPSLATE);
            for (int y = 1; y <= 3; y++) poser.accept(new int[]{l[0], y, l[1]}, Material.POLISHED_DEEPSLATE_WALL);
            poser.accept(new int[]{l[0], 4, l[1]}, Material.SEA_LANTERN);
            poser.accept(new int[]{l[0], 5, l[1]}, Material.END_ROD);
        }
        // Mini-fusée sur son pas de tir (à l'ouest du pad).
        int fx = -8, fz = 2;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            poser.accept(new int[]{fx + dx, 0, fz + dz}, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? Material.GOLD_BLOCK : Material.POLISHED_DEEPSLATE);
        for (int y = 1; y <= 7; y++)
            poser.accept(new int[]{fx, y, fz}, y == 4 ? Material.LIGHT_BLUE_STAINED_GLASS
                    : y == 7 ? Material.GOLD_BLOCK : Material.WHITE_CONCRETE);
        poser.accept(new int[]{fx, 8, fz}, Material.LIGHTNING_ROD);
        for (int[] a : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
            for (int y = 1; y <= 2; y++) poser.accept(new int[]{fx + a[0], y, fz + a[1]}, Material.LIGHT_BLUE_CONCRETE);   // ailerons
        // Antenne radar (à l'est) : mât, parabole de capteurs, émetteur au centre.
        int rx = 8, rz = 3;
        poser.accept(new int[]{rx, 0, rz}, Material.POLISHED_DEEPSLATE);
        for (int y = 1; y <= 2; y++) poser.accept(new int[]{rx, y, rz}, Material.IRON_CHAIN);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            poser.accept(new int[]{rx + dx, 3, rz + dz}, dx == 0 && dz == 0 ? Material.GOLD_BLOCK : Material.DAYLIGHT_DETECTOR);
        poser.accept(new int[]{rx, 4, rz}, Material.END_ROD);
        // Jardin de cristaux et de fleurs bleues sur l'herbe (hors allée et cercle).
        Material[] fleurs = {Material.CORNFLOWER, Material.BLUE_ORCHID, Material.AZURE_BLUET, Material.SMALL_AMETHYST_BUD,
                Material.AMETHYST_CLUSTER, Material.CORNFLOWER};
        for (int dx = -14; dx <= 14; dx++) for (int dz = -14; dz <= 14; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < 6 || d > 14.5) continue;
            int h = Math.floorMod((cx + dx) * 73856093 ^ (cz + dz) * 19349663, 100);
            if (h >= 9) continue;
            if (monde.getBlockAt(cx + dx, Y, cz + dz).getType() != Material.GRASS_BLOCK) continue;
            poser.accept(new int[]{dx, 1, dz}, fleurs[h % fleurs.length]);
        }
        return n[0];
    }

    /** Décor dans le ciel de la zone (anneau, satellite, planète, mini-astéroïdes) et antenne sur l'astéroïde. */
    private int decorerCiel(Ile ile) {
        int cx = ile.cx(), cz = ile.cz();
        int[] n = {0};
        java.util.function.BiConsumer<int[], Material> poser = (p, m) -> {
            Block b = monde.getBlockAt(cx + p[0], Y + p[1], cz + p[2]);
            if (b.getType().isAir()) { b.setType(m, false); n[0]++; }
        };
        // Antenne de communication sur l'astéroïde.
        for (int y = 1; y <= 4; y++) poser.accept(new int[]{3, y, -1}, Material.IRON_CHAIN);
        poser.accept(new int[]{3, 5, -1}, Material.LIGHTNING_ROD);
        poser.accept(new int[]{2, 3, -1}, Material.END_ROD);
        poser.accept(new int[]{4, 3, -1}, Material.END_ROD);

        // Anneau orbital incliné (or + lanternes de mer) au-dessus de l'île.
        double inc = Math.toRadians(22);
        for (int t = 0; t < 360; t += 2) {
            double a = Math.toRadians(t);
            int x = (int) Math.round(14 * Math.cos(a)), y = 18 + (int) Math.round(14 * Math.sin(a) * Math.sin(inc));
            int z = (int) Math.round(14 * Math.sin(a) * Math.cos(inc));
            poser.accept(new int[]{x, y, z}, t % 30 == 0 ? Material.SEA_LANTERN : Material.GOLD_BLOCK);
        }

        // Satellite : corps bleu, panneaux solaires (verre bleu sur longeron doré), antenne.
        int sx = 18, sy = 9, sz = -15;
        for (int dx = 0; dx <= 2; dx++) for (int dy = 0; dy <= 2; dy++) for (int dz = 0; dz <= 2; dz++)
            poser.accept(new int[]{sx + dx, sy + dy, sz + dz}, dx == 1 && dy == 1 && dz == 0 ? Material.SEA_LANTERN
                    : (dx + dy + dz) % 2 == 0 ? Material.LIGHT_BLUE_CONCRETE : Material.WHITE_CONCRETE);
        for (int dx = -7; dx <= 9; dx++) {
            if (dx >= 0 && dx <= 2) continue;
            for (int dz = 0; dz <= 2; dz++)
                poser.accept(new int[]{sx + dx, sy + 1, sz + dz}, dz == 1 || dx == -7 || dx == 9 ? Material.GOLD_BLOCK : Material.BLUE_STAINED_GLASS);
        }
        poser.accept(new int[]{sx + 1, sy + 3, sz + 1}, Material.LIGHTNING_ROD);
        poser.accept(new int[]{sx + 1, sy + 4, sz + 1}, Material.END_ROD);

        // Petite planète bleue à anneau doré, au loin.
        int px = -30, py = 24, pz = 28;
        for (int dx = -4; dx <= 4; dx++) for (int dy = -4; dy <= 4; dy++) for (int dz = -4; dz <= 4; dz++) {
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > 4.3) continue;
            int h = Math.floorMod(dx * 73 + dy * 31 + dz * 17, 7);
            poser.accept(new int[]{px + dx, py + dy, pz + dz}, h == 0 ? Material.CYAN_TERRACOTTA
                    : h <= 2 ? Material.LIGHT_BLUE_TERRACOTTA : Material.BLUE_TERRACOTTA);
        }
        for (int t = 0; t < 360; t += 4) {
            double a = Math.toRadians(t);
            int x = (int) Math.round(7.5 * Math.cos(a)), z = (int) Math.round(7.5 * Math.sin(a));
            int y = (int) Math.round(x * 0.35);
            poser.accept(new int[]{px + x, py + y, pz + z}, t % 24 == 0 ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_STAINED_GLASS);
        }

        // Trois mini-astéroïdes flottants, cristaux d'améthyste et glace cosmique.
        int[][] rochers = {{-22, 4, 12, 3}, {24, -3, 18, 3}, {-15, 7, -24, 2}};
        Material[] roches = {Material.TUFF, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.BLACKSTONE};
        for (int[] r : rochers) {
            int ry = r[3];
            for (int dx = -ry; dx <= ry; dx++) for (int dy = -ry; dy <= ry; dy++) for (int dz = -ry; dz <= ry; dz++) {
                double d = Math.sqrt(dx * dx + dy * dy * 1.8 + dz * dz);
                if (d > ry + 0.3) continue;
                int h = Math.floorMod(dx * 13 + dy * 7 + dz * 29 + r[0], 11);
                Material m = d < 1 ? Material.BLUE_ICE : h == 0 ? Material.AMETHYST_BLOCK : roches[h % roches.length];
                poser.accept(new int[]{r[0] + dx, r[1] + dy, r[2] + dz}, m);
            }
            // Cristaux sur le dessus.
            for (int[] c : new int[][]{{0, 0}, {1, -1}, {-1, 1}}) {
                int top = r[1] + (int) Math.floor(ry / Math.sqrt(1.8)) + 1;
                poser.accept(new int[]{r[0] + c[0], top, r[2] + c[1]}, c[0] == 0 ? Material.AMETHYST_CLUSTER : Material.END_ROD);
            }
        }
        return n[0];
    }

    // ------------------------------------------------------------------ commande /is

    private static void msg(CommandSender s, String mm) { OrbitTycoon.msg(s, mm); }

    private static String nom(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? id.toString().substring(0, 8) : n;
    }

    private static String echapper(String s) { return MiniMessage.miniMessage().escapeTags(s); }

    boolean commande(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { msg(s, "Commande réservée aux joueurs."); return true; }
        if (monde == null) { msg(s, "<#FF4D4D>Le monde des îles n'est pas disponible."); return true; }
        String sous = args.length > 0 ? args[0].toLowerCase() : "";
        String cible = args.length > 1 ? args[1] : null;
        boolean confirmer = args.length > 1 && args[args.length - 1].equalsIgnoreCase("confirmer");
        switch (sous) {
            case "" -> { if (ileDe(p.getUniqueId()) != null) home(p); else aide(p); }
            case "create", "creer" -> creer(p, args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null);
            case "home", "go", "maison" -> home(p);
            case "invite", "inviter" -> inviter(p, cible);
            case "join", "rejoindre" -> rejoindre(p, cible);
            case "leave", "quitter" -> quitter(p, confirmer);
            case "level", "niveau", "info" -> niveau(p);
            case "upgrade", "ameliorer" -> ameliorer(p);
            case "promote", "promouvoir" -> promouvoir(p, cible, confirmer);
            case "demote", "retrograder" -> retrograder(p, cible);
            case "kick", "expulser" -> expulser(p, cible);
            case "ban", "bannir" -> bannir(p, cible);
            case "unban", "debannir" -> debannir(p, cible);
            case "visit", "visiter" -> visiter(p, cible);
            case "nom", "rename" -> renommer(p, args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null);
            default -> aide(p);
        }
        return true;
    }

    private void aide(Player p) {
        msg(p, "<#E0B864><bold>Îles</bold> <gray>— commandes :");
        msg(p, "<#E0B864>/is create [nom]<gray> crée ton île, <#E0B864>/is home<gray> t'y téléporte");
        msg(p, "<#E0B864>/is invite <joueur><gray>, <#E0B864>/is join <joueur><gray>, <#E0B864>/is leave");
        msg(p, "<#E0B864>/is level<gray>, <#E0B864>/is upgrade<gray> (agrandir), <#E0B864>/is nom <nom>");
        msg(p, "<#E0B864>/is promote|demote|kick|ban|unban <joueur><gray>, <#E0B864>/is visit <joueur>");
    }

    void home(Player p) {
        Ile ile = ileDe(p.getUniqueId());
        if (ile == null) { msg(p, "Tu n'as pas d'île. <#E0B864>/is create"); return; }
        teleporter(p, ile);
    }

    private void teleporter(Player p, Ile ile) {
        p.teleportAsync(arrivee(ile), PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
            if (!ok) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f);
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.05);
                p.showTitle(net.kyori.adventure.title.Title.title(
                        MiniMessage.miniMessage().deserialize("<gradient:#62CFF4:#E0B864><bold>" + echapper(ile.nom) + "</bold></gradient>"),
                        MiniMessage.miniMessage().deserialize("<gray>Zone " + taille(ile) + " × " + taille(ile))));
            });
        });
    }

    private void creer(Player p, String nomIle) {
        if (ileDe(p.getUniqueId()) != null) { msg(p, "Tu as déjà une île. <#E0B864>/is home"); return; }
        if (nomIle == null || nomIle.isBlank()) nomIle = "Île de " + p.getName();
        if (nomIle.length() > 20) { msg(p, "Nom trop long (20 caractères max)."); return; }
        if (prochainIndex >= PAR_RANGEE * PAR_RANGEE) { msg(p, "<#FF4D4D>Plus de place pour de nouvelles îles."); return; }
        Ile ile = new Ile(prochainIndex++);
        ile.nom = nomIle;
        ile.membres.put(p.getUniqueId(), Rang.CHEF);
        parIndex.put(ile.index, ile);
        parJoueur.put(p.getUniqueId(), ile);
        construire(ile);
        sauver();
        plugin.monnaies().definirIle(p.getUniqueId(), ile.nom);
        msg(p, "<#7CFC9A>Île créée !</#7CFC9A> Elle fait <#E0B864>" + taille(ile) + " × " + taille(ile)
                + "</#E0B864><gray> : améliore-la avec <#E0B864>/is upgrade</#E0B864><gray> pour l'agrandir.");
        teleporter(p, ile);
    }

    private void renommer(Player p, String nouveau) {
        Ile ile = ileDe(p.getUniqueId());
        if (ile == null) { msg(p, "Tu n'as pas d'île."); return; }
        if (ile.membres.get(p.getUniqueId()) != Rang.CHEF) { msg(p, "Seul le chef peut renommer l'île."); return; }
        if (nouveau == null || nouveau.isBlank()) { msg(p, "Usage : /is nom <nom>"); return; }
        if (nouveau.length() > 20) { msg(p, "Nom trop long (20 caractères max)."); return; }
        ile.nom = nouveau;
        for (UUID id : ile.membres.keySet()) plugin.monnaies().definirIle(id, nouveau);
        sauver();
        msg(p, "Île renommée : <white>" + echapper(nouveau));
    }

    /** Joueur cible déjà connu du serveur (connecté ou non), sinon null + message. */
    private OfflinePlayer joueur(Player p, String n) {
        if (n == null) { msg(p, "Précise un joueur."); return null; }
        Player en = Bukkit.getPlayerExact(n);
        if (en != null) return en;
        OfflinePlayer o = Bukkit.getOfflinePlayerIfCached(n);
        if (o == null) msg(p, "Joueur inconnu : " + echapper(n));
        return o;
    }

    private Ile ileAvecRang(Player p, Rang minimum) {
        Ile ile = ileDe(p.getUniqueId());
        if (ile == null) { msg(p, "Tu n'as pas d'île."); return null; }
        if (ile.membres.get(p.getUniqueId()).ordinal() < minimum.ordinal()) {
            msg(p, "Il faut être au moins " + minimum.joli() + "<gray> pour ça.");
            return null;
        }
        return ile;
    }

    private void inviter(Player p, String n) {
        Ile ile = ileAvecRang(p, Rang.OFFICIER);
        if (ile == null) return;
        Player c = n == null ? null : Bukkit.getPlayerExact(n);
        if (c == null) { msg(p, "Ce joueur n'est pas connecté."); return; }
        if (ileDe(c.getUniqueId()) != null) { msg(p, c.getName() + " a déjà une île."); return; }
        if (ile.bannis.contains(c.getUniqueId())) { msg(p, c.getName() + " est banni de l'île : <#E0B864>/is unban</#E0B864><gray> d'abord."); return; }
        invitations.computeIfAbsent(c.getUniqueId(), k -> new HashMap<>()).put(ile.index, System.currentTimeMillis() + INVITATION_MS);
        msg(p, "Invitation envoyée à <white>" + c.getName() + "</white><gray> (valable 5 minutes).");
        msg(c, "<white>" + p.getName() + "</white><gray> t'invite sur l'île <white>" + echapper(ile.nom)
                + "</white><gray>. Tape <#E0B864>/is join " + p.getName());
        c.playSound(c.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f);
    }

    private void rejoindre(Player p, String n) {
        if (ileDe(p.getUniqueId()) != null) { msg(p, "Tu as déjà une île : <#E0B864>/is leave</#E0B864><gray> d'abord."); return; }
        OfflinePlayer hote = joueur(p, n);
        if (hote == null) return;
        Ile ile = ileDe(hote.getUniqueId());
        Map<Integer, Long> mes = invitations.get(p.getUniqueId());
        Long fin = ile == null || mes == null ? null : mes.get(ile.index);
        if (fin == null || fin < System.currentTimeMillis()) { msg(p, "Tu n'as pas d'invitation valable pour cette île."); return; }
        invitations.remove(p.getUniqueId());
        ile.membres.put(p.getUniqueId(), Rang.MEMBRE);
        parJoueur.put(p.getUniqueId(), ile);
        plugin.monnaies().definirIle(p.getUniqueId(), ile.nom);
        sauver();
        annoncer(ile, "<white>" + p.getName() + "</white><gray> a rejoint l'île !");
        teleporter(p, ile);
    }

    private void quitter(Player p, boolean confirmer) {
        Ile ile = ileDe(p.getUniqueId());
        if (ile == null) { msg(p, "Tu n'as pas d'île."); return; }
        if (ile.membres.get(p.getUniqueId()) == Rang.CHEF) {
            if (ile.membres.size() > 1) {
                msg(p, "Tu es le chef : nomme un autre chef avec <#E0B864>/is promote <officier> confirmer</#E0B864><gray> avant de partir.");
                return;
            }
            if (!confirmer || !confirme(p)) {
                confirmations.put(p.getUniqueId(), System.currentTimeMillis() + 30_000);
                msg(p, "<#FF4D4D>Tu es seul : partir supprime ton île pour toujours.</#FF4D4D><gray> Tape <#E0B864>/is leave confirmer</#E0B864><gray> dans les 30 s.");
                return;
            }
            parIndex.remove(ile.index);   // l'index n'est jamais réutilisé : les blocs restent, personne n'y est envoyé
            parJoueur.remove(p.getUniqueId());
            plugin.monnaies().definirIle(p.getUniqueId(), null);
            sauver();
            msg(p, "Ton île a été supprimée.");
            if (estMonde(p.getWorld())) plugin.versBase(p);
            return;
        }
        ile.membres.remove(p.getUniqueId());
        parJoueur.remove(p.getUniqueId());
        plugin.monnaies().definirIle(p.getUniqueId(), null);
        sauver();
        msg(p, "Tu as quitté l'île <white>" + echapper(ile.nom) + "</white><gray>.");
        annoncer(ile, "<white>" + p.getName() + "</white><gray> a quitté l'île.");
        if (estMonde(p.getWorld()) && ileA(p.getX(), p.getZ()) == ile) plugin.versBase(p);
    }

    private boolean confirme(Player p) {
        Long fin = confirmations.remove(p.getUniqueId());
        return fin != null && fin >= System.currentTimeMillis();
    }

    private void niveau(Player p) {
        Ile ile = ileDe(p.getUniqueId());
        if (ile == null) { msg(p, "Tu n'as pas d'île. <#E0B864>/is create"); return; }
        int t = taille(ile);
        msg(p, "<gradient:#62CFF4:#E0B864><bold>" + echapper(ile.nom) + "</bold></gradient>");
        msg(p, "Niveau <#E0B864>" + (ile.palier + 1) + "</#E0B864><gray> / " + tailles().size()
                + " — zone <#E0B864>" + t + " × " + t);
        if (ile.palier + 1 < tailles().size()) {
            int suivante = tailles().get(ile.palier + 1);
            msg(p, "Prochain niveau : <#E0B864>" + suivante + " × " + suivante + "</#E0B864><gray> pour "
                    + Monnaies.Monnaie.CREDIT.format(cout(ile.palier + 1)) + "<gray> (<#E0B864>/is upgrade</#E0B864><gray>).");
        } else msg(p, "<#7CFC9A>Taille maximale atteinte.");
        StringBuilder sb = new StringBuilder();
        for (var e : ile.membres.entrySet()) {
            if (!sb.isEmpty()) sb.append("<gray>, ");
            sb.append("<white>").append(nom(e.getKey())).append(" <dark_gray>(").append(e.getValue().joli()).append("<dark_gray>)");
        }
        msg(p, "Membres : " + sb);
    }

    private long cout(int palier) {
        List<Long> c = couts();
        return palier < c.size() ? c.get(palier) : Long.MAX_VALUE;
    }

    private void ameliorer(Player p) {
        Ile ile = ileAvecRang(p, Rang.OFFICIER);
        if (ile == null) return;
        if (ile.palier + 1 >= tailles().size()) { msg(p, "<#7CFC9A>Ton île est déjà à la taille maximale."); return; }
        long prix = cout(ile.palier + 1);
        if (prix == Long.MAX_VALUE) { msg(p, "<#FF4D4D>Prix de ce niveau absent de la config (iles.couts)."); return; }
        if (!plugin.monnaies().retirer(p.getUniqueId(), Monnaies.Monnaie.CREDIT, prix)) {
            msg(p, "<#FF4D4D>Il te faut " + Monnaies.Monnaie.CREDIT.format(prix) + "<#FF4D4D>.");
            return;
        }
        ile.palier++;
        sauver();
        int t = taille(ile);
        annoncer(ile, "<#7CFC9A>L'île passe au niveau " + (ile.palier + 1) + " !</#7CFC9A><gray> Nouvelle zone : <#E0B864>" + t + " × " + t);
        for (UUID id : ile.membres.keySet()) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) m.playSound(m.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
        }
    }

    private void promouvoir(Player p, String n, boolean confirmer) {
        Ile ile = ileAvecRang(p, Rang.CHEF);
        if (ile == null) return;
        OfflinePlayer c = joueur(p, n);
        if (c == null) return;
        Rang r = ile.membres.get(c.getUniqueId());
        if (r == null) { msg(p, nom(c.getUniqueId()) + " n'est pas membre de ton île."); return; }
        if (r == Rang.CHEF) { msg(p, "Tu es déjà le chef."); return; }
        if (r == Rang.MEMBRE) {
            ile.membres.put(c.getUniqueId(), Rang.OFFICIER);
            sauver();
            annoncer(ile, "<white>" + nom(c.getUniqueId()) + "</white><gray> est maintenant " + Rang.OFFICIER.joli() + "<gray>.");
            return;
        }
        // Officier -> chef : transfert de l'île, avec confirmation.
        if (!confirmer || !confirme(p)) {
            confirmations.put(p.getUniqueId(), System.currentTimeMillis() + 30_000);
            msg(p, "Donner l'île à <white>" + nom(c.getUniqueId()) + "</white><gray> ? Tu deviendras officier. Tape <#E0B864>/is promote "
                    + nom(c.getUniqueId()) + " confirmer</#E0B864><gray> dans les 30 s.");
            return;
        }
        ile.membres.put(p.getUniqueId(), Rang.OFFICIER);
        ile.membres.put(c.getUniqueId(), Rang.CHEF);
        sauver();
        annoncer(ile, "<white>" + nom(c.getUniqueId()) + "</white><gray> est le nouveau " + Rang.CHEF.joli() + "<gray> de l'île.");
    }

    private void retrograder(Player p, String n) {
        Ile ile = ileAvecRang(p, Rang.CHEF);
        if (ile == null) return;
        OfflinePlayer c = joueur(p, n);
        if (c == null) return;
        if (ile.membres.get(c.getUniqueId()) != Rang.OFFICIER) { msg(p, nom(c.getUniqueId()) + " n'est pas officier."); return; }
        ile.membres.put(c.getUniqueId(), Rang.MEMBRE);
        sauver();
        annoncer(ile, "<white>" + nom(c.getUniqueId()) + "</white><gray> redevient " + Rang.MEMBRE.joli() + "<gray>.");
    }

    /** Retire un membre de l'île (vrai si fait). L'officier ne peut toucher qu'aux simples membres. */
    private boolean retirerMembre(Player p, Ile ile, UUID cible) {
        Rang r = ile.membres.get(cible);
        if (r == null) return true;
        if (r.ordinal() >= ile.membres.get(p.getUniqueId()).ordinal()) {
            msg(p, "Tu ne peux pas faire ça à un " + r.joli() + "<gray>.");
            return false;
        }
        ile.membres.remove(cible);
        parJoueur.remove(cible);
        plugin.monnaies().definirIle(cible, null);
        return true;
    }

    private void expulser(Player p, String n) {
        Ile ile = ileAvecRang(p, Rang.OFFICIER);
        if (ile == null) return;
        OfflinePlayer c = joueur(p, n);
        if (c == null) return;
        if (!ile.membres.containsKey(c.getUniqueId())) { msg(p, nom(c.getUniqueId()) + " n'est pas membre de ton île."); return; }
        if (!retirerMembre(p, ile, c.getUniqueId())) return;
        sauver();
        annoncer(ile, "<white>" + nom(c.getUniqueId()) + "</white><gray> a été expulsé de l'île.");
        renvoyer(c, ile, "Tu as été expulsé de l'île <white>" + echapper(ile.nom) + "</white><gray>.");
    }

    private void bannir(Player p, String n) {
        Ile ile = ileAvecRang(p, Rang.OFFICIER);
        if (ile == null) return;
        OfflinePlayer c = joueur(p, n);
        if (c == null) return;
        if (c.getUniqueId().equals(p.getUniqueId())) { msg(p, "Tu ne peux pas te bannir toi-même."); return; }
        if (!retirerMembre(p, ile, c.getUniqueId())) return;
        ile.bannis.add(c.getUniqueId());
        Map<Integer, Long> inv = invitations.get(c.getUniqueId());
        if (inv != null) inv.remove(ile.index);
        sauver();
        annoncer(ile, "<white>" + nom(c.getUniqueId()) + "</white><gray> est banni de l'île.");
        renvoyer(c, ile, "Tu as été banni de l'île <white>" + echapper(ile.nom) + "</white><gray>.");
    }

    private void debannir(Player p, String n) {
        Ile ile = ileAvecRang(p, Rang.OFFICIER);
        if (ile == null) return;
        OfflinePlayer c = joueur(p, n);
        if (c == null) return;
        if (!ile.bannis.remove(c.getUniqueId())) { msg(p, nom(c.getUniqueId()) + " n'est pas banni."); return; }
        sauver();
        msg(p, nom(c.getUniqueId()) + " n'est plus banni de l'île.");
    }

    /** Prévient le joueur et le renvoie au spawn s'il se trouve sur l'île. */
    private void renvoyer(OfflinePlayer c, Ile ile, String message) {
        Player en = c.getPlayer();
        if (en == null) return;
        msg(en, message);
        if (estMonde(en.getWorld()) && ileA(en.getX(), en.getZ()) == ile) plugin.versBase(en);
    }

    private void visiter(Player p, String n) {
        OfflinePlayer c = joueur(p, n);
        if (c == null) return;
        Ile ile = ileDe(c.getUniqueId());
        if (ile == null) { msg(p, nom(c.getUniqueId()) + " n'a pas d'île."); return; }
        if (ile.bannis.contains(p.getUniqueId())) { msg(p, "<#FF4D4D>Tu es banni de cette île."); return; }
        teleporter(p, ile);
    }

    private void annoncer(Ile ile, String mm) {
        for (UUID id : ile.membres.keySet()) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) msg(m, mm);
        }
    }

    List<String> completer(String[] args) {
        List<String> res = new ArrayList<>();
        if (args.length == 1) res.addAll(List.of("create", "home", "invite", "join", "leave", "level", "upgrade",
                "promote", "demote", "kick", "ban", "unban", "visit", "nom"));
        else if (args.length == 2 && !List.of("create", "nom", "leave", "level", "upgrade", "home").contains(args[0].toLowerCase()))
            for (Player p : Bukkit.getOnlinePlayers()) res.add(p.getName());
        else if (args.length == 2 && args[0].equalsIgnoreCase("leave")) res.add("confirmer");
        String debut = args[args.length - 1].toLowerCase();
        res.removeIf(x -> !x.toLowerCase().startsWith(debut));
        return res;
    }

    // ------------------------------------------------------------------ mur invisible et protections

    /** Vrai si le joueur peut modifier ce bloc : membre de l'île et dans sa zone (ou admin en créatif). */
    private boolean peutModifier(Player p, Block b) {
        if (Zones.constructeur(p)) return true;
        Ile ile = ileA(b.getX(), b.getZ());
        return ile != null && ile == ileDe(p.getUniqueId()) && dedans(ile, b.getX(), b.getZ());
    }

    private void refuser(Player p) {
        long now = System.currentTimeMillis();
        Long der = dernierAvertissement.get(p.getUniqueId());
        if (der != null && now - der < 1500) return;
        dernierAvertissement.put(p.getUniqueId(), now);
        p.sendActionBar(MiniMessage.miniMessage().deserialize("<#FF4D4D>Tu ne peux pas construire ici."));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void bouger(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (!estMonde(to.getWorld())) return;
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockZ() == e.getFrom().getBlockZ()) return;
        Player p = e.getPlayer();
        Ile ile = ileA(to.getX(), to.getZ());
        if (ile == null) {   // case vide de la grille : retour sur son île (ou au spawn)
            Ile sienne = ileDe(p.getUniqueId());
            if (sienne != null) p.teleportAsync(arrivee(sienne)); else plugin.versBase(p);
            return;
        }
        if (dedans(ile, to.getX(), to.getZ())) return;
        if (Zones.constructeur(p)) return;
        // Mur invisible : on bloque le déplacement et on montre un petit éclat là où le joueur a touché.
        Location retour = e.getFrom().clone();
        retour.setYaw(to.getYaw());
        retour.setPitch(to.getPitch());
        e.setTo(retour);
        p.setVelocity(p.getVelocity().setX(0).setZ(0));
        long now = System.currentTimeMillis();
        Long der = dernierAvertissement.get(p.getUniqueId());
        if (der == null || now - der > 1500) {
            dernierAvertissement.put(p.getUniqueId(), now);
            p.sendActionBar(MiniMessage.miniMessage().deserialize(
                    "<#62CFF4>Limite de l'île</#62CFF4> <dark_gray>— <gray>améliore-la avec <#E0B864>/is upgrade"));
            p.spawnParticle(Particle.DUST, to.clone().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(0x62, 0xCF, 0xF4), 1.2f));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent e) {
        if (!estMonde(e.getTo().getWorld()) || e.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
                || e.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND || e.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) return;
        // Perle, fruit de chorus… : pas question de sortir de la zone.
        Ile ile = ileA(e.getTo().getX(), e.getTo().getZ());
        if (ile == null || !dedans(ile, e.getTo().getX(), e.getTo().getZ())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void casser(BlockBreakEvent e) {
        if (!estMonde(e.getBlock().getWorld())) return;
        if (!peutModifier(e.getPlayer(), e.getBlock())) { e.setCancelled(true); refuser(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void poser(BlockPlaceEvent e) {
        if (!estMonde(e.getBlock().getWorld())) return;
        if (!peutModifier(e.getPlayer(), e.getBlock())) { e.setCancelled(true); refuser(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void seauVider(PlayerBucketEmptyEvent e) {
        if (!estMonde(e.getBlock().getWorld())) return;
        if (!peutModifier(e.getPlayer(), e.getBlock())) { e.setCancelled(true); refuser(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void seauRemplir(PlayerBucketFillEvent e) {
        if (!estMonde(e.getBlock().getWorld())) return;
        if (!peutModifier(e.getPlayer(), e.getBlock())) { e.setCancelled(true); refuser(e.getPlayer()); }
    }

    /** Visiteurs : pas d'ouverture de coffres, de portes, de leviers… */
    @EventHandler(priority = EventPriority.HIGH)
    public void interagir(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || !estMonde(b.getWorld())) return;
        if (!peutModifier(e.getPlayer(), b)) e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }

    @EventHandler(ignoreCancelled = true)
    public void ecoulement(BlockFromToEvent e) {
        Block b = e.getToBlock();
        if (!estMonde(b.getWorld())) return;
        Ile ile = ileA(b.getX(), b.getZ());
        if (ile == null || !dedans(ile, b.getX(), b.getZ())) e.setCancelled(true);
    }

    private boolean horsZone(List<Block> blocs, org.bukkit.block.BlockFace sens) {
        for (Block b : blocs) {
            Block c = b.getRelative(sens);
            Ile ile = ileA(c.getX(), c.getZ());
            if (ile == null || !dedans(ile, c.getX(), c.getZ())) return true;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent e) {
        if (estMonde(e.getBlock().getWorld()) && horsZone(e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void pistonRetour(BlockPistonRetractEvent e) {
        if (estMonde(e.getBlock().getWorld()) && horsZone(e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void explosion(EntityExplodeEvent e) {
        if (!estMonde(e.getLocation().getWorld())) return;
        e.blockList().removeIf(b -> { Ile ile = ileA(b.getX(), b.getZ()); return ile == null || !dedans(ile, b.getX(), b.getZ()); });
    }

    @EventHandler(ignoreCancelled = true)
    public void pousse(StructureGrowEvent e) {
        if (!estMonde(e.getWorld())) return;
        e.getBlocks().removeIf(s -> { Ile ile = ileA(s.getX(), s.getZ()); return ile == null || !dedans(ile, s.getX(), s.getZ()); });
    }

    /** Chute dans le vide sur une île : retour sur le pad de l'île. */
    void vide(Player p) {
        Ile ile = ileA(p.getX(), p.getZ());
        if (ile == null) ile = ileDe(p.getUniqueId());
        p.setFallDistance(0);
        if (ile != null) p.teleportAsync(arrivee(ile)); else plugin.versBase(p);
    }

    Location reapparition(Player p) {
        Ile ile = ileA(p.getX(), p.getZ());
        if (ile == null) ile = ileDe(p.getUniqueId());
        return ile == null ? null : arrivee(ile);
    }
}
