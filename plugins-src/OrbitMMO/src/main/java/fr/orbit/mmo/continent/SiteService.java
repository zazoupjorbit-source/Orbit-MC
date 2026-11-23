package fr.orbit.mmo.continent;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.floor.FloorService;
import fr.orbit.mmo.util.Text;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Les sites du continent autres que les capitales : villages, entrées de donjon, camps, ruines, cratères. Même
 * principe que {@link CapitalService} : le dessin ({@link SiteDesign}) se calcule hors du fil principal, puis ce
 * service le compare au monde (« ecarts ») ou le pose par tranches (« generer »), sans jamais réécrire par surprise un
 * site déjà construit. « tout » enchaîne tous les sites : ceux qui ont été retouchés à la main sont alors laissés.
 */
public final class SiteService {

    private enum Mode { PLAN, DIFF, GENERATE }

    private static final String TAG = "orbit_site";
    private static final int MAX_LINES = 2000;

    /** Un site construit, avec ses points (arrivée, portail, ancrage, quêtes). */
    public record Built(SiteDesign.Site site, Map<String, Location> points) {
    }

    private final OrbitMMO plugin;
    private final ContinentService continent;
    private final File settingsFile, placesFile, stateFile;
    private SiteCatalog catalog;
    private YamlConfiguration state = new YamlConfiguration();
    private final Map<String, Built> built = new LinkedHashMap<>();
    private Work work;
    private BukkitTask task;
    /** Sites qui restent à traiter après celui en cours (« tout »). */
    private final Deque<String> queue = new ArrayDeque<>();
    private Batch batch;

    SiteService(OrbitMMO plugin, ContinentService continent) {
        this.plugin = plugin;
        this.continent = continent;
        this.settingsFile = new File(plugin.getDataFolder(), "sites.yml");
        this.placesFile = new File(plugin.getDataFolder(), "sites-places.yml");
        this.stateFile = new File(plugin.getDataFolder(), "sites-etat.yml");
    }

    /** Lit sites.yml (créé depuis le jar s'il manque), fige les emplacements calculés, relit l'état. */
    public void load() {
        if (!settingsFile.isFile()) {
            plugin.saveResource("sites.yml", false);
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(settingsFile);
        YamlConfiguration frozen = placesFile.isFile() ? YamlConfiguration.loadConfiguration(placesFile) : new YamlConfiguration();
        long t0 = System.currentTimeMillis();
        try {
            catalog = SiteCatalog.load(continent.data(), continent.generator(), y, frozen);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Sites : lecture de sites.yml impossible", e);
            return;
        }
        if (!catalog.computed.isEmpty()) {
            for (Map.Entry<String, String[]> e : catalog.computed.entrySet()) {
                String[] v = e.getValue();
                frozen.set(e.getKey() + ".type", v[0]);
                frozen.set(e.getKey() + ".x", Integer.parseInt(v[1]));
                frozen.set(e.getKey() + ".z", Integer.parseInt(v[2]));
                if (!v[3].isEmpty()) {
                    frozen.set(e.getKey() + ".theme", v[3]);
                }
            }
            frozen.options().setHeader(List.of("Emplacements des camps, des ruines et des cratères, calculés une fois puis figés.",
                    "Supprimer ce fichier les fait recalculer : à ne faire que sur un monde où ils ne sont pas construits."));
            try {
                frozen.save(placesFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Sites : écriture de " + placesFile.getName() + " impossible", e);
            }
        }
        plugin.registry().addMobs(y.getConfigurationSection("monstres"), false);
        state = stateFile.isFile() ? YamlConfiguration.loadConfiguration(stateFile) : new YamlConfiguration();
        index();
        for (String note : catalog.notes) {
            plugin.getLogger().info("Sites : " + note);
        }
        plugin.getLogger().info("Sites : " + catalog.sites.size() + " sites inscrits, " + built.size() + " construits ("
                + (System.currentTimeMillis() - t0) + " ms).");
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
        }
    }

    public SiteCatalog catalog() {
        return catalog;
    }

    public List<String> ids() {
        return catalog == null ? List.of() : new ArrayList<>(catalog.sites.keySet());
    }

    public SiteDesign.Site site(String id) {
        return catalog == null ? null : catalog.site(id);
    }

    public boolean generated(String id) {
        return state.getBoolean(id + ".genere");
    }

    public String generatedAt(String id) {
        return state.getString(id + ".date");
    }

    /** Les sites construits. */
    public Collection<Built> built() {
        return built.values();
    }

    /** Les villages et hameaux construits, pour les PNJ d'ambiance : centre, rayon de la palissade, points. */
    List<ContinentService.Town> towns() {
        List<ContinentService.Town> out = new ArrayList<>();
        for (Built b : built.values()) {
            SiteDesign.Site s = b.site();
            if (s.kind().equals("village")) {
                out.add(new ContinentService.Town(s.id(), s.name(), false, s.faction(), s.style(), s.x(), s.z(), s.radius(), b.points()));
            }
        }
        return out;
    }

    /** Un site construit, pour la carte : genre (village, donjon, camp, ruine…), nom, centre. */
    public record Spot(String kind, String name, int x, int z) {
    }

    /** Les sites construits (carte du joueur). */
    public List<Spot> spots() {
        List<Spot> out = new ArrayList<>();
        for (Built b : built.values()) {
            SiteDesign.Site s = b.site();
            out.add(new Spot(s.kind(), s.name(), s.x(), s.z()));
        }
        return out;
    }

    public Built built(String id) {
        return built.get(id);
    }

    public Location point(String id, String name) {
        Built b = built.get(id);
        return b == null ? null : b.points().get(name);
    }

    public boolean busy() {
        return work != null || !queue.isEmpty();
    }

    /** Phrase d'état du travail en cours, ou null. */
    public String status() {
        Work w = work;
        if (w == null) {
            return queue.isEmpty() ? null : "file d'attente : " + queue.size() + " sites";
        }
        String name = w.site.name() + " (" + w.site.id() + ")";
        String rest = queue.isEmpty() ? "" : ", puis " + queue.size() + " autres sites";
        return switch (w.phase) {
            case 0 -> name + " : calcul du plan" + rest;
            case 1 -> name + " : chargement des chunks, reste " + w.pending + rest;
            case 2 -> name + " : comparaison au monde, " + percent(w) + " %" + rest;
            case 3 -> name + " : pose des blocs, " + percent(w) + " %" + rest;
            default -> name + " : pose des PNJ" + rest;
        };
    }

    private static int percent(Work w) {
        return w.plan == null ? 0 : (int) (w.cursor * 100L / Math.max(1, w.plan.cells.length));
    }

    public boolean stop() {
        boolean any = work != null || !queue.isEmpty();
        queue.clear();
        if (batch != null) {
            batch.stopped = true;
        }
        if (work != null) {
            work.cancelled = true;
        }
        return any;
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        queue.clear();
        if (work != null) {
            release(work);
            work = null;
        }
    }

    // ------------------------------------------------------------------ Sites construits
    private void index() {
        built.clear();
        if (catalog == null || !continent.ready()) {
            return;
        }
        World world = continent.world();
        for (SiteDesign.Site s : catalog.sites.values()) {
            if (!state.getBoolean(s.id() + ".genere")) {
                continue;
            }
            Map<String, Location> points = new LinkedHashMap<>();
            ConfigurationSection sec = state.getConfigurationSection(s.id() + ".points");
            if (sec != null) {
                for (String name : sec.getKeys(false)) {
                    points.put(name, new Location(world, sec.getDouble(name + ".x"), sec.getDouble(name + ".y"), sec.getDouble(name + ".z"),
                            (float) sec.getDouble(name + ".regard"), 0));
                }
            }
            built.put(s.id(), new Built(s, points));
        }
    }

    /** Hameau construit dont la zone sûre contient ce bloc (les villages de la carte sont déjà des lieux sûrs). */
    public SiteDesign.Site safeAt(int x, int z) {
        for (Built b : built.values()) {
            SiteDesign.Site s = b.site();
            if (s.safe() > 0 && s.kind().equals("village")) {
                double dx = x - s.x(), dz = z - s.z();
                if (dx * dx + dz * dz < (double) s.safe() * s.safe()) {
                    return s;
                }
            }
        }
        return null;
    }

    /** Site construit le plus proche d'un bloc, dans la limite donnée, parmi ceux d'un type (null : tous). */
    public Built near(int x, int z, double within, String kind) {
        Built best = null;
        double bestD = within * within;
        for (Built b : built.values()) {
            SiteDesign.Site s = b.site();
            if (kind != null && !s.kind().equals(kind)) {
                continue;
            }
            double dx = x - s.x(), dz = z - s.z(), d = dx * dx + dz * dz;
            if (d <= bestD) {
                bestD = d;
                best = b;
            }
        }
        return best;
    }

    /** Arrivée dans un village ou devant un donjon construit : son point d'arrivée, sinon le dessus de ce qui est bâti. */
    Location arrival(int x, int z) {
        for (Built b : built.values()) {
            SiteDesign.Site s = b.site();
            double d = Math.hypot(x - s.x(), z - s.z());
            if (d > s.radius() + 6) {
                continue;
            }
            Location spawn = d < 3 ? b.points().get("spawn") : null;
            if (spawn != null) {
                return spawn.clone();
            }
            World w = continent.world();
            return new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 1, z + 0.5);
        }
        return null;
    }

    /** Où envoyer quelqu'un qui veut voir le site : son point d'arrivée s'il est construit, sinon le sol du générateur. */
    public Location visit(String id) {
        SiteDesign.Site s = site(id);
        if (s == null || !continent.ready()) {
            return null;
        }
        Location spawn = point(id, "spawn");
        return spawn != null ? spawn.clone() : new Location(continent.world(), s.x() + 0.5, continent.generator().standY(s.x(), s.z()), s.z() + 0.5);
    }

    // ------------------------------------------------------------------ Travaux
    private static final class Batch {
        final Mode mode;
        final CommandSender who;
        final long startedAt = System.currentTimeMillis();
        int total, done, changedSites, skipped, failed;
        long blocks;
        final List<String> altered = new ArrayList<>();
        boolean stopped;

        Batch(Mode mode, CommandSender who) {
            this.mode = mode;
            this.who = who;
        }
    }

    private final class Work {
        final SiteDesign.Site site;
        final Mode mode;
        final boolean confirm;
        final CommandSender who;
        final Batch batch;
        final long startedAt = System.currentTimeMillis();
        CapitalPlan plan;
        BlockData[] states;
        boolean[] loose;
        short veg = -1;
        final List<Chunk> held = new ArrayList<>();
        /** 0 calcul, 1 chargement des chunks, 2 comparaison, 3 pose, 4 PNJ. */
        int phase;
        int pending, failed, cursor, waited;
        long differ, toPlace, toRemove, toReplace, changed;
        final List<String> lines = new ArrayList<>();
        final Map<String, Integer> byBlock = new HashMap<>();
        boolean cancelled;

        Work(SiteDesign.Site site, Mode mode, boolean confirm, CommandSender who, Batch batch) {
            this.site = site;
            this.mode = mode;
            this.confirm = confirm;
            this.who = who;
            this.batch = batch;
        }
    }

    public String plan(CommandSender who, String id) {
        return start(who, id, Mode.PLAN, false, null);
    }

    public String diff(CommandSender who, String id) {
        return start(who, id, Mode.DIFF, false, null);
    }

    public String generate(CommandSender who, String id, boolean confirm) {
        return start(who, id, Mode.GENERATE, confirm, null);
    }

    /**
     * Enchaîne tous les sites, ou ceux d'un type.
     *
     * @param generate true pour construire, false pour seulement comparer
     * @return message d'erreur, ou null si la file est lancée
     */
    public String all(CommandSender who, boolean generate, String kind) {
        String blocked = blocked();
        if (blocked != null) {
            return blocked;
        }
        Batch b = new Batch(generate ? Mode.GENERATE : Mode.DIFF, who);
        for (SiteDesign.Site s : catalog.sites.values()) {
            if (kind == null || s.kind().equals(kind)) {
                queue.add(s.id());
            }
        }
        if (queue.isEmpty()) {
            return "Aucun site de ce type. Types : village, donjon, camp, ruine, crateres.";
        }
        b.total = queue.size();
        batch = b;
        return null;
    }

    private String blocked() {
        if (catalog == null) {
            return "Les sites ne sont pas chargés (voir la console).";
        }
        if (busy()) {
            return "Un travail est déjà en cours : " + status() + ".";
        }
        if (continent.busy()) {
            return "Le continent est occupé : " + continent.jobStatus() + ".";
        }
        if (continent.capitals() != null && continent.capitals().busy()) {
            return "Une capitale est en travaux : " + continent.capitals().status() + ".";
        }
        return null;
    }

    /** @return message d'erreur, ou null si le travail est lancé */
    private String start(CommandSender who, String id, Mode mode, boolean confirm, Batch b) {
        if (b == null) {
            String blocked = blocked();
            if (blocked != null) {
                return blocked;
            }
        }
        SiteDesign.Site s = catalog.site(id);
        if (s == null) {
            return "Site inconnu : " + id + ". « /continent site » en donne la liste.";
        }
        Work w = new Work(s, mode, confirm, who, b);
        work = w;
        ContinentData data = continent.data();
        ContinentGenerator gen = continent.generator();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            CapitalPlan plan = null;
            String error = null;
            try {
                plan = SiteDesign.build(data, gen, s);
            } catch (RuntimeException e) {
                error = String.valueOf(e.getMessage());
                plugin.getLogger().log(Level.WARNING, "Site " + id + " : calcul du plan en échec", e);
            }
            CapitalPlan result = plan;
            String failure = error;
            Bukkit.getScheduler().runTask(plugin, () -> computed(w, result, failure));
        });
        return null;
    }

    private void computed(Work w, CapitalPlan plan, String error) {
        if (work != w) {
            return;
        }
        if (plan == null || w.cancelled) {
            if (plan == null) {
                fail(w, "Plan de " + Text.esc(w.site.id()) + " impossible : " + Text.esc(error));
            } else {
                finish(w, "<#FF5C5C>Travail arrêté.", false);
            }
            return;
        }
        w.plan = plan;
        int n = plan.palette.size();
        w.states = new BlockData[n];
        w.loose = new boolean[n];
        List<String> bad = new ArrayList<>();
        for (int i = 1; i < n; i++) {
            if (plan.palette.get(i).equals(SiteDesign.VEG)) {
                w.veg = (short) i;
                w.states[i] = Material.AIR.createBlockData();
                continue;
            }
            try {
                w.states[i] = Bukkit.createBlockData("minecraft:" + plan.palette.get(i));
            } catch (IllegalArgumentException e) {
                bad.add(plan.palette.get(i));
                w.states[i] = Material.STONE.createBlockData();
            }
            Material m = w.states[i].getMaterial();
            // Blocs dont l'état change tout seul ou sous la main des joueurs : on ne compare que leur nature.
            w.loose[i] = Tag.LEAVES.isTagged(m) || Tag.DOORS.isTagged(m) || Tag.TRAPDOORS.isTagged(m) || Tag.CANDLES.isTagged(m)
                    || Tag.CAMPFIRES.isTagged(m);
        }
        if (w.batch == null) {
            for (String note : plan.notes) {
                plugin.getLogger().info("Site " + plan.name + " : " + note);
            }
        }
        if (!bad.isEmpty()) {
            plugin.getLogger().warning("Site " + w.site.id() + " : états de bloc inconnus de cette version : " + bad);
            if (w.mode != Mode.PLAN) {
                fail(w, Text.esc(w.site.id()) + " : " + bad.size() + " états de bloc inconnus de cette version (voir la console), rien n'a été posé.");
                return;
            }
        }
        if (w.mode == Mode.PLAN) {
            render(w);
            return;
        }
        World world = continent.world();
        int c0x = (plan.ox - plan.r) >> 4, c1x = (plan.ox + plan.r) >> 4, c0z = (plan.oz - plan.r) >> 4, c1z = (plan.oz + plan.r) >> 4;
        w.phase = 1;
        for (int cz = c0z; cz <= c1z; cz++) {
            for (int cx = c0x; cx <= c1x; cx++) {
                w.pending++;
                world.getChunkAtAsync(cx, cz, true).whenComplete((chunk, failure) -> {
                    Runnable r = () -> {
                        w.pending--;
                        if (chunk == null || failure != null) {
                            w.failed++;
                        } else if (work == w) {
                            chunk.addPluginChunkTicket(plugin);
                            w.held.add(chunk);
                        }
                    };
                    if (Bukkit.isPrimaryThread()) {
                        r.run();
                    } else {
                        Bukkit.getScheduler().runTask(plugin, r);
                    }
                });
            }
        }
    }

    private void tick() {
        Work w = work;
        if (w == null) {
            next();
            return;
        }
        if (w.phase == 0) {
            return;
        }
        if (w.cancelled) {
            if (w.pending == 0) {
                finish(w, "<#FF5C5C>Travail arrêté" + (w.changed > 0 ? " après " + count(w.changed) + " blocs posés : " + Text.esc(w.site.name())
                        + " est incomplet, relance « generer confirmer »." : "."), false);
            }
            return;
        }
        switch (w.phase) {
            case 1 -> {
                if (w.pending > 0) {
                    return;
                }
                if (w.failed > 0) {
                    fail(w, Text.esc(w.site.id()) + " : " + w.failed + " chunks n'ont pas pu être chargés, rien n'a été posé.");
                    return;
                }
                // Site déjà construit : on regarde d'abord si le monde a été retouché.
                w.phase = w.mode == Mode.DIFF || (generated(w.site.id()) && !w.confirm) ? 2 : 3;
            }
            case 2 -> {
                if (scan(w, false)) {
                    compared(w);
                }
            }
            case 3 -> {
                if (scan(w, true)) {
                    w.phase = 4;
                }
            }
            default -> entities(w);
        }
    }

    /** Lance le site suivant de la file, ou clôt la série. */
    private void next() {
        Batch b = batch;
        if (b == null) {
            return;
        }
        String id = queue.poll();
        if (id == null || b.stopped) {
            batch = null;
            queue.clear();
            long seconds = Math.max(1, (System.currentTimeMillis() - b.startedAt) / 1000);
            String head = b.stopped ? "Série arrêtée" : "Série terminée";
            String msg;
            if (b.mode == Mode.GENERATE) {
                msg = head + " : " + b.done + " sites sur " + b.total + " traités en " + seconds + " s, " + b.changedSites + " construits ou complétés ("
                        + count(b.blocks) + " blocs posés)" + (b.skipped > 0 ? ", " + b.skipped + " laissés tels quels car retouchés : "
                        + String.join(", ", b.altered) : "") + (b.failed > 0 ? ", " + b.failed + " en échec (voir la console)" : "") + ".";
            } else {
                msg = head + " : " + b.done + " sites sur " + b.total + " comparés en " + seconds + " s, "
                        + (b.altered.isEmpty() ? "aucun écart sur les sites construits" : b.altered.size() + " avec des écarts : " + String.join(", ", b.altered))
                        + (b.failed > 0 ? ", " + b.failed + " en échec (voir la console)" : "") + ".";
            }
            if (continent.ready()) {
                continent.world().save();
            }
            tell(b.who, "<#7FD4FF>" + Text.esc(msg));
            return;
        }
        String error = start(b.who, id, b.mode, false, b);
        if (error != null) {
            b.failed++;
            b.done++;
            plugin.getLogger().warning("Site " + id + " : " + error);
        }
    }

    private static boolean vegetation(Material m) {
        if (m.isAir() || m == Material.WATER || m == Material.LAVA) {
            return false;
        }
        return !m.isSolid() || Tag.LEAVES.isTagged(m) || Tag.LOGS.isTagged(m) || m == Material.CACTUS || m == Material.MANGROVE_ROOTS
                || m == Material.BAMBOO || m == Material.MUSHROOM_STEM || m == Material.BROWN_MUSHROOM_BLOCK || m == Material.RED_MUSHROOM_BLOCK;
    }

    /** Parcourt le plan pendant quelques millisecondes. Renvoie true quand tout est vu. */
    private boolean scan(Work w, boolean apply) {
        CapitalPlan p = w.plan;
        World world = continent.world();
        short[] cells = p.cells;
        int side = p.side, layer = side * side, n = cells.length, i = w.cursor;
        long deadline = System.nanoTime() + catalog.budgetMs * 1_000_000L;
        int minY = world.getMinHeight(), maxY = world.getMaxHeight() - 1;
        while (i < n) {
            short c = cells[i];
            if (c != CapitalPlan.KEEP) {
                int rest = i % layer, x = p.ox - p.r + rest % side, y = p.y0 + i / layer, z = p.oz - p.r + rest / side;
                if (y >= minY && y <= maxY) {
                    BlockData cur = world.getBlockData(x, y, z), want = w.states[c];
                    boolean differs = c == w.veg ? vegetation(cur.getMaterial())
                            : cur.getMaterial() != want.getMaterial() || !(w.loose[c] || cur.equals(want));
                    if (differs) {
                        w.differ++;
                        if (apply) {
                            world.getBlockAt(x, y, z).setBlockData(want, false);
                            w.changed++;
                        } else {
                            record(w, x, y, z, cur, want, c == CapitalPlan.AIR || c == w.veg);
                        }
                    }
                }
            }
            i++;
            if ((i & 255) == 0 && System.nanoTime() > deadline) {
                break;
            }
        }
        w.cursor = i;
        return i >= n;
    }

    private void record(Work w, int x, int y, int z, BlockData cur, BlockData want, boolean wantAir) {
        String key;
        if (wantAir) {
            w.toRemove++;
            key = "à retirer : " + cur.getMaterial().name().toLowerCase(Locale.ROOT);
        } else if (cur.getMaterial().isAir()) {
            w.toPlace++;
            key = "à poser : " + want.getMaterial().name().toLowerCase(Locale.ROOT);
        } else {
            w.toReplace++;
            key = "à remplacer : " + cur.getMaterial().name().toLowerCase(Locale.ROOT) + " par " + want.getMaterial().name().toLowerCase(Locale.ROOT);
        }
        w.byBlock.merge(key, 1, Integer::sum);
        if (w.mode == Mode.DIFF && w.lines.size() < MAX_LINES) {
            w.lines.add(x + " " + y + " " + z + " : " + cur.getAsString(true) + " -> " + want.getAsString(true));
        }
    }

    private static String count(long n) {
        return String.format(Locale.FRENCH, "%,d", n);
    }

    private void compared(Work w) {
        String name = w.site.name(), id = w.site.id();
        String summary = count(w.differ) + (w.differ > 1 ? " blocs diffèrent" : " bloc diffère") + " du plan (" + count(w.toPlace) + " à poser, "
                + count(w.toRemove) + " à retirer, " + count(w.toReplace) + " à remplacer)";
        if (w.mode == Mode.GENERATE) {
            if (w.differ == 0) {
                w.phase = 4;
                return;
            }
            if (w.batch != null) {
                w.batch.skipped++;
                w.batch.altered.add(id);
            }
            finish(w, "<#FFD66E>" + Text.esc(name) + " est déjà construit et " + summary + " : retouches à la main, ou dessin modifié. "
                    + "<#D7DCE2>« /continent site " + id + " ecarts » en donne le détail ; « generer confirmer » reconstruit selon le plan "
                    + "(les retouches sont perdues).", w.batch != null);
            return;
        }
        if (w.differ == 0) {
            finish(w, "<#7FD4FF>" + Text.esc(name) + " est conforme au plan : aucun écart.", w.batch != null);
            return;
        }
        if (w.batch != null && generated(id)) {
            w.batch.altered.add(id);
        }
        File file = new File(new File(plugin.getDataFolder(), "sites"), id + "-ecarts.txt");
        List<String> out = new ArrayList<>();
        out.add(name + " : " + summary + ".");
        out.add("Par nature (les plus nombreux d'abord) :");
        w.byBlock.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(40)
                .forEach(e -> out.add("  " + count(e.getValue()) + "  " + e.getKey()));
        out.add("");
        out.add("Détail (x y z : monde -> plan)" + (w.differ > w.lines.size() ? ", les " + w.lines.size() + " premiers :" : " :"));
        out.addAll(w.lines);
        if (w.batch == null || generated(id)) {
            try {
                file.getParentFile().mkdirs();
                Files.write(file.toPath(), out, StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Site " + id + " : écriture de " + file.getName() + " impossible", e);
            }
        }
        List<Map.Entry<String, Integer>> top = new ArrayList<>(w.byBlock.entrySet());
        top.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder main = new StringBuilder();
        for (int k = 0; k < Math.min(4, top.size()); k++) {
            main.append(k == 0 ? "" : " ; ").append(count(top.get(k).getValue())).append(' ').append(top.get(k).getKey());
        }
        finish(w, "<#7FD4FF>" + Text.esc(name) + " : " + summary + ". <#D7DCE2>Surtout : " + Text.esc(main.toString())
                + ". Détail dans " + Text.esc(file.getPath()) + (generated(id) ? "" : " (site pas encore construit)."), w.batch != null && !generated(id));
    }

    /** Retire les PNJ d'une génération précédente, pose ceux du plan, puis enregistre l'état et les points. */
    private void entities(Work w) {
        World world = continent.world();
        CapitalPlan plan = w.plan;
        String id = w.site.id();
        boolean ready = true;
        for (CapitalPlan.Npc n : plan.npcs) {
            if (!world.getChunkAt((int) Math.floor(n.x()) >> 4, (int) Math.floor(n.z()) >> 4).isEntitiesLoaded()) {
                ready = false;
            }
        }
        if (!ready && w.waited++ < 100) {
            return;
        }
        String own = TAG + "_" + id;
        int posed = 0;
        if (!plan.npcs.isEmpty() || generated(id)) {
            for (Chunk c : w.held) {
                for (Entity e : c.getEntities()) {
                    if (e.getScoreboardTags().contains(own)) {
                        e.remove();
                    }
                }
            }
            for (CapitalPlan.Npc n : plan.npcs) {
                List<Entity> made = FloorService.spawnTownNpc(world, n.tag(), new Location(world, n.x(), n.y(), n.z(), n.yaw(), 0));
                for (Entity e : made) {
                    e.addScoreboardTag(TAG);
                    e.addScoreboardTag(own);
                }
                posed += made.isEmpty() ? 0 : 1;
            }
        }
        state.set(id + ".genere", true);
        state.set(id + ".date", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()));
        state.set(id + ".type", w.site.kind());
        state.set(id + ".x", w.site.x());
        state.set(id + ".z", w.site.z());
        state.set(id + ".sol", plan.ground);
        state.set(id + ".points", null);
        for (Map.Entry<String, double[]> e : plan.points.entrySet()) {
            String path = id + ".points." + e.getKey();
            state.set(path + ".x", e.getValue()[0]);
            state.set(path + ".y", e.getValue()[1]);
            state.set(path + ".z", e.getValue()[2]);
            state.set(path + ".regard", e.getValue()[3]);
        }
        try {
            state.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Site " + id + " : écriture de " + stateFile.getName() + " impossible", e);
        }
        index();
        if (w.batch != null) {
            if (w.changed > 0) {
                w.batch.changedSites++;
                w.batch.blocks += w.changed;
            }
        } else {
            world.save();
        }
        long seconds = Math.max(1, (System.currentTimeMillis() - w.startedAt) / 1000);
        finish(w, "<#7FD4FF>" + Text.esc(plan.name) + " construit : <#D7DCE2>" + count(w.changed) + (w.changed > 1 ? " blocs posés, " : " bloc posé, ")
                + posed + " PNJ, en " + seconds + " s. « /continent site " + id + " tp » pour y aller.", w.batch != null);
    }

    private void release(Work w) {
        for (Chunk c : w.held) {
            c.removePluginChunkTicket(plugin);
        }
        w.held.clear();
    }

    /** Échec franc d'un site : toujours dit, et compté dans le bilan d'une série. */
    private void fail(Work w, String mini) {
        if (w.batch != null) {
            w.batch.failed++;
        }
        finish(w, "<#FF5C5C>" + mini, false);
    }

    private void finish(Work w, String mini, boolean quiet) {
        release(w);
        if (work == w) {
            work = null;
        }
        if (w.batch != null) {
            w.batch.done++;
            if (w.cancelled) {
                w.batch.stopped = true;
            }
            if (w.batch.done % 20 == 0) {
                plugin.getLogger().info("Sites : " + w.batch.done + " / " + w.batch.total + " traités.");
            }
        }
        if (quiet) {
            return;
        }
        tell(w.who, mini);
    }

    private void tell(CommandSender who, String mini) {
        plugin.getLogger().info("Site : " + PlainTextComponentSerializer.plainText().serialize(Text.mm(mini)));
        if (who instanceof Player p && p.isOnline()) {
            Text.msg(p, mini);
        }
    }

    // ------------------------------------------------------------------ Aperçu du plan (sans toucher au monde)
    private void render(Work w) {
        CapitalPlan plan = w.plan;
        int[] colors = new int[plan.palette.size()];
        colors[0] = -1;
        colors[1] = -1;
        for (int i = 2; i < colors.length; i++) {
            if (i == w.veg) {
                colors[i] = -1;
                continue;
            }
            Material m = w.states[i].getMaterial();
            int rgb;
            try {
                rgb = w.states[i].getMapColor().asRGB();
            } catch (RuntimeException e) {
                rgb = 0;
            }
            if (m == Material.WATER) {
                rgb = 0x3F76E4;
            } else if (rgb == 0) {
                // Blocs sans couleur de carte : vitres, lanternes, bannières…
                String name = m.name();
                rgb = name.contains("GLASS") ? 0xBFE4EE : (name.contains("LANTERN") ? 0xFFD070 : (m == Material.END_GATEWAY ? 0x2A1B4D : 0x9A9A9A));
            }
            colors[i] = rgb;
        }
        File dir = new File(plugin.getDataFolder(), "apercus");
        String id = w.site.id();
        int zoom = plan.side > 120 ? 4 : 8;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String result;
            try {
                dir.mkdirs();
                File top = new File(dir, "site_" + id + "_plan.png"), oblique = new File(dir, "site_" + id + "_plan_oblique.png");
                ImageIO.write(plan.top(colors, zoom), "png", top);
                ImageIO.write(plan.oblique(colors, zoom), "png", oblique);
                result = "<#7FD4FF>Plan de " + Text.esc(plan.name) + " écrit : <#D7DCE2>" + Text.esc(top.getPath()) + " et " + Text.esc(oblique.getName());
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Site " + id + " : aperçu du plan impossible", e);
                result = "<#FF5C5C>Aperçu du plan impossible : " + Text.esc(String.valueOf(e.getMessage()));
            }
            String message = result;
            Bukkit.getScheduler().runTask(plugin, () -> finish(w, message, false));
        });
    }
}
