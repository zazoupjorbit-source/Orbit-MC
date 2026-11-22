package fr.orbit.mmo.continent;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Le continent : le monde ouvert d'Orbit MMO. Charge les données du dessin, crée le monde avec
 * {@link ContinentGenerator}, le protège (rien ne se casse, rien ne coule, rien ne pousse), annonce les régions
 * traversées, pré-génère les chunks et écrit des aperçus PNG pour contrôler le terrain sans entrer en jeu.
 */
public final class ContinentService implements Listener {

    private final OrbitMMO plugin;
    private final File stateFile;
    private ContinentData data;
    private ContinentGenerator generator;
    private World world;
    private int parallel = 64;
    private Job job;
    private CapitalService capitals;
    /** Dernière zone annoncée à chaque joueur. */
    private final Map<UUID, String> zones = new HashMap<>();
    private final Map<Material, Integer> colors = new EnumMap<>(Material.class);

    public ContinentService(OrbitMMO plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "continent-etat.yml");
    }

    public boolean ready() {
        return world != null;
    }

    public ContinentData data() {
        return data;
    }

    public ContinentGenerator generator() {
        return generator;
    }

    public World world() {
        return world;
    }

    public boolean isContinent(World w) {
        return world != null && world.equals(w);
    }

    /** Les capitales (plan, écarts, génération), ou null si le continent n'est pas chargé. */
    public CapitalService capitals() {
        return capitals;
    }

    /** Zone sûre du continent : l'emprise d'une capitale, de la ville ou d'un village. */
    public boolean isSafe(Location l) {
        return l != null && isContinent(l.getWorld()) && data.placeAt(l.getBlockX(), l.getBlockZ()) != null;
    }

    /** Charge les données et le monde. Sans effet si « continent.enabled » est à false. */
    public void enable() {
        if (!plugin.getConfig().getBoolean("continent.enabled", true)) {
            return;
        }
        long t0 = System.currentTimeMillis();
        try (InputStream in = open()) {
            if (in == null) {
                plugin.getLogger().warning("Continent : données absentes du jar (lance tools\\gen-continent.ps1 -Donnees puis build.ps1).");
                return;
            }
            data = ContinentData.load(in);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Continent : lecture des données impossible", e);
            return;
        }
        generator = new ContinentGenerator(data);
        parallel = Math.max(8, Math.min(256, plugin.getConfig().getInt("continent.parallele", 64)));
        String name = plugin.getConfig().getString("continent.world", "orbit_continent");
        world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name)
                    .environment(World.Environment.NORMAL)
                    .generator(generator)
                    .generateStructures(false)
                    .seed(20261122L)
                    .createWorld();
        }
        if (world == null) {
            plugin.getLogger().severe("Continent : impossible de créer le monde " + name);
            return;
        }
        setup(world);
        YamlConfiguration state = state();
        String sig = Long.toHexString(data.signature);
        String old = state.getString("signature");
        if (old == null) {
            state.set("signature", sig);
            save(state);
        } else if (!old.equals(sig)) {
            plugin.getLogger().warning("Continent : le dessin a changé depuis la création du monde « " + name + " ». Les chunks déjà "
                    + "générés gardent l'ancien relief (marches aux raccords). Pour repartir propre : arrêter le serveur, supprimer le "
                    + "dossier du monde et plugins\\OrbitMMO\\continent-etat.yml.");
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::pump, 20L, 1L);
        capitals = new CapitalService(plugin, this);
        capitals.load();
        plugin.getLogger().info("Continent « " + name + " » chargé en " + (System.currentTimeMillis() - t0) + " ms : "
                + data.size + " x " + data.size + " blocs, " + data.regions.size() + " régions, " + data.places.size()
                + " lieux, " + data.roads.size() + " routes.");
    }

    private InputStream open() throws IOException {
        // Un continent.dat posé dans le dossier du plugin remplace celui du jar (essais sans recompiler).
        File override = new File(plugin.getDataFolder(), "continent.dat");
        return override.isFile() ? new FileInputStream(override) : plugin.getResource("continent/continent.dat");
    }

    private void setup(World w) {
        w.setDifficulty(Difficulty.NORMAL);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        w.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        w.setGameRule(GameRule.DO_WARDEN_SPAWNING, false);
        w.setGameRule(GameRule.DO_INSOMNIA, false);
        w.setGameRule(GameRule.NATURAL_REGENERATION, false);
        w.setGameRule(GameRule.KEEP_INVENTORY, true);
        w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        w.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
        w.setGameRule(GameRule.MOB_GRIEFING, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.SPAWN_RADIUS, 0);
        // Rien ne pousse, ne gèle ni ne fane : le décor reste celui du générateur.
        w.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        w.setStorm(false);
        w.setThundering(false);
        WorldBorder border = w.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(data.size);
        border.setWarningDistance(16);
        Location spawn = generator.getFixedSpawnLocation(w, null);
        w.setSpawnLocation(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
    }

    public void shutdown() {
        if (capitals != null) {
            capitals.shutdown();
        }
        if (job != null) {
            job.cancelled = true;
            job.end(false);
            job = null;
        }
    }

    /** Point d'arrivée sûr à cet endroit du continent. */
    public Location arrival(int x, int z) {
        // Dans une capitale construite, le relief du générateur ne dit plus où poser les pieds.
        Location built = capitals == null ? null : capitals.arrival(x, z);
        if (built != null) {
            return built;
        }
        return new Location(world, x + 0.5, generator.standY(x, z), z + 0.5);
    }

    // ------------------------------------------------------------------ État (empreinte du dessin, avancée de la pré-génération)
    private YamlConfiguration state() {
        return stateFile.isFile() ? YamlConfiguration.loadConfiguration(stateFile) : new YamlConfiguration();
    }

    private void save(YamlConfiguration state) {
        try {
            state.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Continent : écriture de " + stateFile.getName() + " impossible", e);
        }
    }

    // ------------------------------------------------------------------ Protections
    private boolean here(World w) {
        return world != null && world.equals(w);
    }

    private static boolean builder(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("orbitmmo.admin");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (here(e.getBlock().getWorld()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (here(e.getBlock().getWorld()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (here(e.getBlock().getWorld()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (here(e.getBlock().getWorld()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    /** L'eau et la lave restent où le générateur les a posées (fleuves en paliers, coulées, cratère). */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (here(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        if (here(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        if (here(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        if (here(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (here(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (here(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (here(e.getBlock().getWorld()) && (e.getPlayer() == null || !builder(e.getPlayer()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        if (here(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (here(e.getLocation().getWorld())) {
            e.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (here(e.getBlock().getWorld())) {
            e.blockList().clear();
        }
    }

    // ------------------------------------------------------------------ Annonce des régions et des lieux
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (!here(to.getWorld())) {
            return;
        }
        Location from = e.getFrom();
        Player p = e.getPlayer();
        if ((to.getBlockX() >> 2) == (from.getBlockX() >> 2) && (to.getBlockZ() >> 2) == (from.getBlockZ() >> 2)
                && zones.containsKey(p.getUniqueId())) {
            return;
        }
        int x = to.getBlockX(), z = to.getBlockZ();
        ContinentData.Place place = data.placeAt(x, z);
        ContinentData.Region region = data.region(x, z);
        String key = place != null ? "lieu:" + place.name() : (region != null ? "region:" + region.id() : "mer");
        if (key.equals(zones.put(p.getUniqueId(), key))) {
            return;
        }
        if (place != null) {
            Text.title(p, factionColor(place.faction()) + "<b>" + Text.esc(place.name()) + "</b>",
                    "<#D7DCE2>" + placeKind(place) + " <#5A6270>·</#5A6270> zone sûre", 8, 50, 16);
        } else if (region != null && data.biome(x, z) != ContinentData.OCEAN) {
            Text.title(p, factionColor(region.faction()) + "<b>" + Text.esc(region.name()) + "</b>",
                    "<#D7DCE2>" + factionName(region.faction()) + " <#5A6270>·</#5A6270> niveaux " + region.levelMin() + " à "
                            + region.levelMax(), 8, 50, 16);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        zones.remove(e.getPlayer().getUniqueId());
    }

    public static String factionColor(String faction) {
        return switch (faction) {
            case "lumiere" -> "<#FFD66E>";
            case "ombre" -> "<#9B59FF>";
            default -> "<#FF8A3B>";
        };
    }

    public static String factionName(String faction) {
        return switch (faction) {
            case "lumiere" -> "Territoire de la Lumière";
            case "ombre" -> "Territoire de l'Ombre";
            default -> "Terre contestée";
        };
    }

    public static String placeKind(ContinentData.Place p) {
        return switch (p.kind()) {
            case "capitale" -> p.faction().equals("ombre") ? "Capitale de l'Ombre" : "Capitale de la Lumière";
            case "ville" -> "Ville neutre";
            case "village" -> "Village";
            case "donjon" -> "Donjon";
            default -> p.kind();
        };
    }

    // ------------------------------------------------------------------ Travaux sur les chunks (pré-génération, aperçus)
    private abstract class Job {
        int inFlight;
        long done;
        long failed;
        boolean cancelled;
        final long startedAt = System.currentTimeMillis();

        abstract boolean hasNext();

        /** Chunk suivant : (x << 32) | z. */
        abstract long next();

        void chunk(Chunk c) {
        }

        void tick() {
        }

        abstract void end(boolean complete);

        abstract String status();
    }

    private static boolean lowMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory() > rt.maxMemory() * 0.85;
    }

    private void pump() {
        Job j = job;
        if (j == null || world == null) {
            return;
        }
        if (!j.cancelled) {
            int room = parallel - j.inFlight;
            if (j.inFlight > 0 && lowMemory()) {
                room = 0;
            }
            while (room-- > 0 && j.hasNext()) {
                long k = j.next();
                request(j, (int) (k >> 32), (int) k);
            }
            j.tick();
        }
        if (j.inFlight == 0 && (j.cancelled || !j.hasNext())) {
            job = null;
            j.end(!j.cancelled);
        }
    }

    private void request(Job j, int cx, int cz) {
        j.inFlight++;
        world.getChunkAtAsync(cx, cz, true).whenComplete((chunk, error) -> {
            Runnable r = () -> {
                j.inFlight--;
                j.done++;
                if (error != null || chunk == null) {
                    if (j.failed++ < 5) {
                        plugin.getLogger().log(Level.WARNING, "Continent : chunk " + cx + ", " + cz + " en échec", error);
                    }
                    return;
                }
                try {
                    j.chunk(chunk);
                } catch (RuntimeException ex) {
                    if (j.failed++ < 5) {
                        plugin.getLogger().log(Level.WARNING, "Continent : traitement du chunk " + cx + ", " + cz + " en échec", ex);
                    }
                }
            };
            if (Bukkit.isPrimaryThread()) {
                r.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, r);
            }
        });
    }

    /** Phrase d'état du travail en cours, ou null. */
    public String jobStatus() {
        return job == null ? null : job.status();
    }

    public boolean busy() {
        return job != null;
    }

    /** Arrête le travail en cours (les chunks déjà demandés se terminent). */
    public boolean stopJob() {
        if (job == null) {
            return false;
        }
        job.cancelled = true;
        return true;
    }

    private static String duration(long seconds) {
        if (seconds < 90) {
            return seconds + " s";
        }
        long minutes = Math.round(seconds / 60.0);
        return minutes < 90 ? minutes + " min" : String.format(Locale.FRENCH, "%d h %02d", minutes / 60, minutes % 60);
    }

    // ------------------------------------------------------------------ Pré-génération
    /** Tous les chunks de la carte, fichier de région après fichier de région (32 x 32 chunks). */
    private final class Pregen extends Job {
        final int cMin, cMax, rMin, side, end;
        final long total;
        final long before;
        int index;
        long lastLog = System.currentTimeMillis(), lastSave = lastLog, lastDone;
        final CommandSender who;

        Pregen(CommandSender who, int from) {
            this.who = who;
            int half = data.size / 2;
            cMin = -half >> 4;
            cMax = (half - 1) >> 4;
            rMin = cMin >> 5;
            side = (cMax >> 5) - rMin + 1;
            end = side * side * 1024;
            total = (long) (cMax - cMin + 1) * (cMax - cMin + 1);
            long n = 0;
            for (int i = 0; i < from && i < end; i++) {
                if (valid(i)) {
                    n++;
                }
            }
            before = n;
            index = Math.max(0, Math.min(end, from));
        }

        int cx(int i) {
            return (rMin + (i >> 10) % side) * 32 + (i & 31);
        }

        int cz(int i) {
            return (rMin + (i >> 10) / side) * 32 + ((i >> 5) & 31);
        }

        boolean valid(int i) {
            int x = cx(i), z = cz(i);
            return x >= cMin && x <= cMax && z >= cMin && z <= cMax;
        }

        @Override
        boolean hasNext() {
            while (index < end && !valid(index)) {
                index++;
            }
            return index < end;
        }

        @Override
        long next() {
            int i = index++;
            return ((long) cx(i) << 32) | (cz(i) & 0xFFFFFFFFL);
        }

        @Override
        void tick() {
            long now = System.currentTimeMillis();
            if (now - lastSave > 30_000) {
                lastSave = now;
                store(false);
            }
            if (now - lastLog > 60_000) {
                plugin.getLogger().info("Continent : " + status());
                lastLog = now;
                lastDone = done;
            }
        }

        void store(boolean finished) {
            YamlConfiguration state = state();
            // On reprend un peu avant : les derniers chunks demandés n'étaient peut-être pas écrits.
            state.set("pregen.index", finished ? end : Math.max(0, index - parallel * 4));
            state.set("pregen.fini", finished);
            save(state);
        }

        @Override
        String status() {
            long made = before + done;
            double seconds = Math.max(1, (System.currentTimeMillis() - startedAt) / 1000.0);
            double rate = done / seconds;
            String rest = rate > 0.5 ? ", reste environ " + duration((long) ((total - made) / rate)) : "";
            return String.format(Locale.FRENCH, "pré-génération %,d / %,d chunks (%.1f %%), %.0f chunks/s%s", made, total,
                    made * 100.0 / total, rate, rest);
        }

        @Override
        void end(boolean complete) {
            store(complete);
            if (world != null) {
                world.save();
            }
            String msg = complete
                    ? "Pré-génération du continent terminée : " + String.format(Locale.FRENCH, "%,d", before + done) + " chunks en "
                    + duration((System.currentTimeMillis() - startedAt) / 1000) + (failed > 0 ? " (" + failed + " en échec)" : "") + "."
                    : "Pré-génération du continent arrêtée (" + status() + "). « /continent pregen » la reprend.";
            plugin.getLogger().info(msg);
            if (who instanceof Player p && p.isOnline()) {
                Text.msg(p, "<#7FD4FF>" + Text.esc(msg));
            }
        }
    }

    /**
     * Lance ou reprend la pré-génération.
     *
     * @param restart true pour repartir du début
     * @return message d'erreur, ou null si elle est lancée
     */
    public String pregen(CommandSender who, boolean restart) {
        if (job != null) {
            return "Un travail est déjà en cours : " + job.status() + ".";
        }
        if (capitals != null && capitals.busy()) {
            return "Une capitale est en travaux : " + capitals.status() + ".";
        }
        YamlConfiguration state = state();
        if (!restart && state.getBoolean("pregen.fini")) {
            return "La pré-génération est déjà faite. « /continent pregen refaire » la relance depuis le début.";
        }
        Pregen p = new Pregen(who, restart ? 0 : state.getInt("pregen.index"));
        job = p;
        plugin.getLogger().info("Continent : pré-génération lancée, " + p.status() + ".");
        return null;
    }

    // ------------------------------------------------------------------ Aperçus PNG
    private int color(Material m) {
        Integer c = colors.get(m);
        if (c == null) {
            int rgb;
            try {
                rgb = m.createBlockData().getMapColor().asRGB();
            } catch (RuntimeException e) {
                rgb = 0x808080;
            }
            if (m == Material.WATER) {
                rgb = 0x3F76E4;
            } else if (m == Material.LAVA) {
                rgb = 0xFF6A00;
            } else if (rgb == 0) {
                rgb = 0x808080;
            }
            c = rgb;
            colors.put(m, c);
        }
        return c;
    }

    private static int shade(int rgb, double f) {
        int r = (int) Math.max(0, Math.min(255, ((rgb >> 16) & 0xFF) * f));
        int g = (int) Math.max(0, Math.min(255, ((rgb >> 8) & 0xFF) * f));
        int b = (int) Math.max(0, Math.min(255, (rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }

    /** Vue de dessus (un pixel tous les « step » blocs) ou vue oblique depuis le sud (un pixel par bloc). */
    private final class Preview extends Job {
        static final int Y_TOP = 250, Y_BOTTOM = 50;
        final CommandSender who;
        final File file;
        final int x0, z0, size, step, w, h;
        final boolean oblique;
        final int[] rgb;
        final short[] tops;
        final int[] depth;
        final int c0x, c0z, cw, cn;
        int index;

        Preview(CommandSender who, File file, int centerX, int centerZ, int size, int step, boolean oblique) {
            this.who = who;
            this.file = file;
            this.size = size;
            this.oblique = oblique;
            this.step = oblique ? 1 : step;
            this.x0 = centerX - size / 2;
            this.z0 = centerZ - size / 2;
            this.w = (size + this.step - 1) / this.step;
            this.h = oblique ? size + (Y_TOP - Y_BOTTOM) + 2 : w;
            this.rgb = new int[w * h];
            Arrays.fill(rgb, 0x12161C);
            this.tops = oblique ? null : new short[w * h];
            this.depth = oblique ? new int[w * h] : null;
            if (depth != null) {
                Arrays.fill(depth, Integer.MIN_VALUE);
            }
            c0x = x0 >> 4;
            c0z = z0 >> 4;
            cw = ((x0 + size - 1) >> 4) - c0x + 1;
            cn = cw * (((z0 + size - 1) >> 4) - c0z + 1);
        }

        @Override
        boolean hasNext() {
            return index < cn;
        }

        @Override
        long next() {
            int i = index++;
            return ((long) (c0x + i % cw) << 32) | ((c0z + i / cw) & 0xFFFFFFFFL);
        }

        @Override
        void chunk(Chunk chunk) {
            ChunkSnapshot snap = chunk.getChunkSnapshot(true, false, false);
            int bx = chunk.getX() << 4, bz = chunk.getZ() << 4;
            int minY = world.getMinHeight(), maxY = world.getMaxHeight() - 1;
            for (int lz = 0; lz < 16; lz++) {
                int pz = bz + lz - z0;
                if (pz < 0 || pz >= size || pz % step != 0) {
                    continue;
                }
                for (int lx = 0; lx < 16; lx++) {
                    int px = bx + lx - x0;
                    if (px < 0 || px >= size || px % step != 0) {
                        continue;
                    }
                    int y = Math.min(maxY, snap.getHighestBlockYAt(lx, lz) + 1);
                    Material m = snap.getBlockType(lx, y, lz);
                    while (m.isAir() && y > minY) {
                        m = snap.getBlockType(lx, --y, lz);
                    }
                    if (oblique) {
                        column(snap, lx, lz, px, pz, y);
                        continue;
                    }
                    int c = color(m);
                    if (m == Material.WATER) {
                        int d = 0;
                        while (d < 24 && y - d - 1 > minY && snap.getBlockType(lx, y - d - 1, lz) == Material.WATER) {
                            d++;
                        }
                        c = shade(c, 1.0 - d * 0.022);
                    }
                    int i = (pz / step) * w + px / step;
                    rgb[i] = c;
                    tops[i] = (short) y;
                }
            }
        }

        private void column(ChunkSnapshot snap, int lx, int lz, int px, int pz, int top) {
            int z = z0 + pz;
            for (int y = Math.max(Y_BOTTOM, top - 64); y <= Math.min(top, Y_TOP); y++) {
                Material m = snap.getBlockType(lx, y, lz);
                boolean fluid = m == Material.WATER || m == Material.LAVA;
                if (m.isAir() || (!fluid && !m.isSolid())) {
                    continue;
                }
                int c = color(m);
                int row = pz + (Y_TOP - y);
                if (fluid) {
                    // Seule la surface du fluide se voit.
                    if (y == top || snap.getBlockType(lx, y + 1, lz) != m) {
                        plot(px, row, c, z * 2);
                    }
                    continue;
                }
                plot(px, row, c, z * 2);
                plot(px, row + 1, shade(c, 0.68), z * 2 + 1);
            }
        }

        private void plot(int px, int row, int c, int d) {
            if (row < 0 || row >= h) {
                return;
            }
            int i = row * w + px;
            if (d >= depth[i]) {
                depth[i] = d;
                rgb[i] = c;
            }
        }

        @Override
        String status() {
            return "aperçu " + file.getName() + " : " + done + " / " + cn + " chunks";
        }

        @Override
        void end(boolean complete) {
            if (!complete) {
                tell("<#FF5C5C>Aperçu abandonné.");
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String result;
                try {
                    int first = 0;
                    if (oblique) {
                        // On coupe le ciel vide au-dessus du relief.
                        while (first < h - 1 && empty(first)) {
                            first++;
                        }
                        first = Math.max(0, first - 6);
                    } else {
                        relief();
                    }
                    int rows = h - first;
                    int zoom = Math.max(1, Math.min(8, 640 / Math.max(w, 1)));
                    BufferedImage img = new BufferedImage(w * zoom, rows * zoom, BufferedImage.TYPE_INT_RGB);
                    int[] line = new int[w * zoom];
                    for (int y = 0; y < rows; y++) {
                        for (int x = 0; x < w * zoom; x++) {
                            line[x] = rgb[(first + y) * w + x / zoom];
                        }
                        for (int k = 0; k < zoom; k++) {
                            img.setRGB(0, y * zoom + k, w * zoom, 1, line, 0, w * zoom);
                        }
                    }
                    file.getParentFile().mkdirs();
                    ImageIO.write(img, "png", file);
                    result = "<#7FD4FF>Aperçu écrit : <#D7DCE2>" + Text.esc(file.getPath()) + " <#5A6270>(" + img.getWidth() + " x "
                            + img.getHeight() + " px, "
                            + duration((System.currentTimeMillis() - startedAt) / 1000) + ")";
                } catch (IOException | RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "Continent : aperçu impossible", e);
                    result = "<#FF5C5C>Aperçu impossible : " + Text.esc(String.valueOf(e.getMessage()));
                }
                String message = result;
                Bukkit.getScheduler().runTask(plugin, () -> tell(message));
            });
        }

        private boolean empty(int row) {
            for (int x = 0; x < w; x++) {
                if (depth[row * w + x] != Integer.MIN_VALUE) {
                    return false;
                }
            }
            return true;
        }

        /** Ombrage du relief : lumière venue du nord-ouest. */
        private void relief() {
            int[] out = new int[rgb.length];
            for (int z = 0; z < h; z++) {
                for (int x = 0; x < w; x++) {
                    int i = z * w + x;
                    int west = tops[z * w + Math.max(0, x - 1)], east = tops[z * w + Math.min(w - 1, x + 1)];
                    int north = tops[Math.max(0, z - 1) * w + x], south = tops[Math.min(h - 1, z + 1) * w + x];
                    double slope = ((east - west) + (south - north)) / (double) step;
                    double f = 1.0 - Math.max(-0.38, Math.min(0.38, slope * 0.11));
                    out[i] = shade(rgb[i], f * (0.86 + Math.min(0.24, Math.max(0, tops[i] - 62) * 0.0017)));
                }
            }
            System.arraycopy(out, 0, rgb, 0, rgb.length);
        }

        private void tell(String mini) {
            plugin.getLogger().info("Continent : " + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(Text.mm(mini)));
            if (who instanceof Player p && p.isOnline()) {
                Text.msg(p, mini);
            }
        }
    }

    /**
     * Demande un aperçu PNG centré sur (x, z), écrit dans plugins/OrbitMMO/apercus.
     *
     * @return message d'erreur, ou null si l'aperçu est lancé
     */
    public String preview(CommandSender who, String label, int x, int z, int size, int step, boolean oblique) {
        if (job != null) {
            return "Un travail est déjà en cours : " + job.status() + ".";
        }
        if (capitals != null && capitals.busy()) {
            return "Une capitale est en travaux : " + capitals.status() + ".";
        }
        step = Math.max(1, Math.min(16, step));
        size = Math.max(64, Math.min(oblique ? 1024 : 2048 * step, size));
        String name = (label == null ? x + "_" + z : ContinentData.plain(label)) + "_" + size + (oblique ? "_oblique" : (step > 1 ? "_pas" + step : ""));
        File file = new File(new File(plugin.getDataFolder(), "apercus"), name + ".png");
        job = new Preview(who, file, x, z, size, step, oblique);
        return null;
    }
}
