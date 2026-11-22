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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Les capitales du continent, sur le modèle de /station (OrbitDevCore) : les réglages sont dans capitales.yml, le
 * dessin ({@link CapitalDesign}) se calcule hors du fil principal, puis ce service le compare au monde (« ecarts »,
 * qui ne change rien) ou le pose par tranches (« generer »), en ne touchant que les blocs qui diffèrent. Les PNJ de
 * ville sont ceux des étages, marqués pour être retirés et reposés à chaque génération.
 *
 * Une capitale déjà générée n'est jamais réécrite par surprise : s'il y a des écarts (retouches à la main, ou dessin
 * modifié), « generer » s'arrête et demande « generer confirmer ».
 */
public final class CapitalService {

    private enum Mode { PLAN, DIFF, GENERATE }

    private static final String TAG = "orbit_capitale";
    private static final int MAX_LINES = 4000;

    private final OrbitMMO plugin;
    private final ContinentService continent;
    private final File settingsFile;
    private final File stateFile;
    private final Map<String, CapitalDesign.Settings> capitals = new LinkedHashMap<>();
    private int budgetMs = 20;
    private Work work;
    private BukkitTask task;

    CapitalService(OrbitMMO plugin, ContinentService continent) {
        this.plugin = plugin;
        this.continent = continent;
        this.settingsFile = new File(plugin.getDataFolder(), "capitales.yml");
        this.stateFile = new File(plugin.getDataFolder(), "capitales-etat.yml");
    }

    /** Lit capitales.yml (créé depuis le jar s'il manque). */
    public void load() {
        if (!settingsFile.isFile()) {
            plugin.saveResource("capitales.yml", false);
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(settingsFile);
        budgetMs = Math.max(2, Math.min(40, y.getInt("millisecondes-par-tick", 20)));
        capitals.clear();
        ConfigurationSection all = y.getConfigurationSection("capitales");
        if (all != null) {
            for (String key : all.getKeys(false)) {
                ConfigurationSection c = all.getConfigurationSection(key);
                if (c == null) {
                    continue;
                }
                int level = CapitalDesign.AUTO;
                try {
                    level = Integer.parseInt(String.valueOf(c.get("niveau", "auto")).trim());
                } catch (NumberFormatException ignored) {
                    // « auto » : le dessin choisit d'après le fleuve et le sol
                }
                String id = key.toLowerCase(Locale.ROOT);
                capitals.put(id, new CapitalDesign.Settings(id, c.getString("lieu", key), c.getString("style", "lumiere"),
                        c.getInt("rayon-rempart", 104), c.getInt("rayon-raccord", 150), level, c.getLong("graine", 1)));
            }
        }
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
        }
    }

    public List<String> ids() {
        return new ArrayList<>(capitals.keySet());
    }

    /** Nom affiché de la capitale (celui du lieu), ou null. */
    public String displayName(String id) {
        CapitalDesign.Settings s = capitals.get(id);
        if (s == null) {
            return null;
        }
        ContinentData.Place p = continent.data().place(s.place());
        return p == null ? s.place() : p.name();
    }

    public boolean generated(String id) {
        return state().getBoolean(id + ".genere");
    }

    public String generatedAt(String id) {
        return state().getString(id + ".date");
    }

    public boolean busy() {
        return work != null;
    }

    /** Phrase d'état du travail en cours, ou null. */
    public String status() {
        Work w = work;
        if (w == null) {
            return null;
        }
        String name = displayName(w.id);
        return switch (w.phase) {
            case 0 -> name + " : calcul du plan";
            case 1 -> name + " : chargement des chunks, reste " + w.pending;
            case 2 -> name + " : comparaison au monde, " + percent(w) + " %";
            case 3 -> name + " : pose des blocs, " + percent(w) + " % (" + String.format(Locale.FRENCH, "%,d", w.changed) + " posés)";
            default -> name + " : pose des PNJ";
        };
    }

    private static int percent(Work w) {
        return w.plan == null ? 0 : (int) (w.cursor * 100L / Math.max(1, w.plan.cells.length));
    }

    public boolean stop() {
        if (work == null) {
            return false;
        }
        work.cancelled = true;
        return true;
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (work != null) {
            release(work);
            work = null;
        }
    }

    // ------------------------------------------------------------------ État (capitales générées, points)
    private YamlConfiguration state() {
        return stateFile.isFile() ? YamlConfiguration.loadConfiguration(stateFile) : new YamlConfiguration();
    }

    public Map<String, Location> points(String id) {
        Map<String, Location> out = new LinkedHashMap<>();
        ConfigurationSection sec = state().getConfigurationSection(id + ".points");
        if (sec == null || !continent.ready()) {
            return out;
        }
        for (String name : sec.getKeys(false)) {
            out.put(name, new Location(continent.world(), sec.getDouble(name + ".x"), sec.getDouble(name + ".y"), sec.getDouble(name + ".z"),
                    (float) sec.getDouble(name + ".regard"), 0));
        }
        return out;
    }

    public Location point(String id, String name) {
        return points(id).get(name);
    }

    /** Arrivée dans une capitale générée : sa place au centre, sinon le dessus de ce qui est construit là. Null ailleurs. */
    Location arrival(int x, int z) {
        for (CapitalDesign.Settings s : capitals.values()) {
            ContinentData.Place p = continent.data().place(s.place());
            if (p == null || !generated(s.id())) {
                continue;
            }
            double d = Math.hypot(x - p.x(), z - p.z());
            if (d > s.wall() + 8) {
                continue;
            }
            Location spawn = d < 3 ? point(s.id(), "spawn") : null;
            if (spawn != null) {
                return spawn;
            }
            World w = continent.world();
            return new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 1, z + 0.5);
        }
        return null;
    }

    // ------------------------------------------------------------------ Travaux
    private final class Work {
        final String id;
        final Mode mode;
        final boolean confirm;
        final CommandSender who;
        final long startedAt = System.currentTimeMillis();
        CapitalPlan plan;
        BlockData[] states;
        boolean[] loose;
        final List<Chunk> held = new ArrayList<>();
        /** 0 calcul, 1 chargement des chunks, 2 comparaison, 3 pose, 4 PNJ. */
        int phase;
        int pending, failed, cursor, waited;
        long differ, toPlace, toRemove, toReplace, changed;
        final List<String> lines = new ArrayList<>();
        final Map<String, Integer> byBlock = new HashMap<>();
        boolean cancelled;

        Work(String id, Mode mode, boolean confirm, CommandSender who) {
            this.id = id;
            this.mode = mode;
            this.confirm = confirm;
            this.who = who;
        }
    }

    public String plan(CommandSender who, String id) {
        return start(who, id, Mode.PLAN, false);
    }

    public String diff(CommandSender who, String id) {
        return start(who, id, Mode.DIFF, false);
    }

    public String generate(CommandSender who, String id, boolean confirm) {
        return start(who, id, Mode.GENERATE, confirm);
    }

    /** @return message d'erreur, ou null si le travail est lancé */
    private String start(CommandSender who, String id, Mode mode, boolean confirm) {
        if (work != null) {
            return "Un travail est déjà en cours : " + status() + ".";
        }
        if (continent.busy()) {
            return "Le continent est occupé : " + continent.jobStatus() + ".";
        }
        CapitalDesign.Settings s = capitals.get(id);
        if (s == null) {
            return "Capitale inconnue : " + id + ". Connues : " + String.join(", ", capitals.keySet()) + ".";
        }
        Work w = new Work(id, mode, confirm, who);
        work = w;
        ContinentData data = continent.data();
        ContinentGenerator gen = continent.generator();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            CapitalPlan plan = null;
            String error = null;
            try {
                plan = CapitalDesign.build(data, gen, s);
            } catch (RuntimeException e) {
                error = String.valueOf(e.getMessage());
                plugin.getLogger().log(Level.WARNING, "Capitale " + id + " : calcul du plan en échec", e);
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
            finish(w, "<#FF5C5C>" + (plan == null ? "Plan impossible : " + Text.esc(error) : "Travail arrêté."));
            return;
        }
        w.plan = plan;
        int n = plan.palette.size();
        w.states = new BlockData[n];
        w.loose = new boolean[n];
        List<String> bad = new ArrayList<>();
        for (int i = 1; i < n; i++) {
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
        String name = plan.name;
        for (String note : plan.notes) {
            plugin.getLogger().info("Capitale " + name + " : " + note);
        }
        if (!bad.isEmpty()) {
            plugin.getLogger().warning("Capitale " + name + " : états de bloc inconnus de cette version : " + bad);
            if (w.mode != Mode.PLAN) {
                finish(w, "<#FF5C5C>" + bad.size() + " états de bloc inconnus de cette version (voir la console) : rien n'a été posé.");
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
        if (w == null || w.phase == 0) {
            return;
        }
        if (w.cancelled) {
            if (w.pending == 0) {
                finish(w, "<#FF5C5C>Travail arrêté" + (w.changed > 0 ? " après " + String.format(Locale.FRENCH, "%,d", w.changed)
                        + " blocs posés : la capitale est incomplète, relance « generer confirmer »." : "."));
            }
            return;
        }
        switch (w.phase) {
            case 1 -> {
                if (w.pending > 0) {
                    return;
                }
                if (w.failed > 0) {
                    finish(w, "<#FF5C5C>" + w.failed + " chunks n'ont pas pu être chargés : rien n'a été posé.");
                    return;
                }
                // Capitale déjà générée : on regarde d'abord si le monde a été retouché.
                w.phase = w.mode == Mode.DIFF || (generated(w.id) && !w.confirm) ? 2 : 3;
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

    /** Parcourt le plan pendant quelques millisecondes. Renvoie true quand tout est vu. */
    private boolean scan(Work w, boolean apply) {
        CapitalPlan p = w.plan;
        World world = continent.world();
        short[] cells = p.cells;
        int side = p.side, layer = side * side, n = cells.length, i = w.cursor;
        long deadline = System.nanoTime() + budgetMs * 1_000_000L;
        while (i < n) {
            short c = cells[i];
            if (c != CapitalPlan.KEEP) {
                int rest = i % layer, x = p.ox - p.r + rest % side, y = p.y0 + i / layer, z = p.oz - p.r + rest / side;
                BlockData cur = world.getBlockData(x, y, z), want = w.states[c];
                if (cur.getMaterial() != want.getMaterial() || !(w.loose[c] || cur.equals(want))) {
                    w.differ++;
                    if (apply) {
                        world.getBlockAt(x, y, z).setBlockData(want, false);
                        w.changed++;
                    } else {
                        record(w, x, y, z, cur, want, c == CapitalPlan.AIR);
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
        String name = w.plan.name;
        String summary = count(w.differ) + (w.differ > 1 ? " blocs diffèrent" : " bloc diffère") + " du plan (" + count(w.toPlace) + " à poser, " + count(w.toRemove) + " à retirer, "
                + count(w.toReplace) + " à remplacer)";
        if (w.mode == Mode.GENERATE) {
            if (w.differ == 0) {
                w.phase = 4;
                return;
            }
            finish(w, "<#FFD66E>" + Text.esc(name) + " est déjà générée et " + summary + " : retouches à la main, ou dessin modifié. "
                    + "<#D7DCE2>« /continent capitale " + w.id + " ecarts » en donne le détail ; « generer confirmer » reconstruit selon le plan "
                    + "(les retouches sont perdues).");
            return;
        }
        if (w.differ == 0) {
            finish(w, "<#7FD4FF>" + Text.esc(name) + " est conforme au plan : aucun écart.");
            return;
        }
        File file = new File(new File(plugin.getDataFolder(), "capitales"), w.id + "-ecarts.txt");
        List<String> out = new ArrayList<>();
        out.add(name + " : " + summary + ".");
        out.add("Par nature (les plus nombreux d'abord) :");
        w.byBlock.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(40)
                .forEach(e -> out.add("  " + count(e.getValue()) + "  " + e.getKey()));
        out.add("");
        out.add("Détail (x y z : monde -> plan)" + (w.differ > w.lines.size() ? ", les " + w.lines.size() + " premiers :" : " :"));
        out.addAll(w.lines);
        try {
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Capitale " + name + " : écriture de " + file.getName() + " impossible", e);
        }
        List<Map.Entry<String, Integer>> top = new ArrayList<>(w.byBlock.entrySet());
        top.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder main = new StringBuilder();
        for (int k = 0; k < Math.min(4, top.size()); k++) {
            main.append(k == 0 ? "" : " ; ").append(count(top.get(k).getValue())).append(' ').append(top.get(k).getKey());
        }
        finish(w, "<#7FD4FF>" + Text.esc(name) + " : " + summary + ". <#D7DCE2>Surtout : " + Text.esc(main.toString())
                + ". Détail dans " + Text.esc(file.getPath()) + (generated(w.id) ? "" : " (capitale pas encore générée)."));
    }

    /** Retire les PNJ d'une génération précédente, pose ceux du plan, puis enregistre l'état et les points. */
    private void entities(Work w) {
        World world = continent.world();
        CapitalPlan plan = w.plan;
        boolean ready = true;
        for (CapitalPlan.Npc n : plan.npcs) {
            if (!world.getChunkAt((int) Math.floor(n.x()) >> 4, (int) Math.floor(n.z()) >> 4).isEntitiesLoaded()) {
                ready = false;
            }
        }
        if (!ready && w.waited++ < 100) {
            return;
        }
        String own = TAG + "_" + w.id;
        for (Entity e : world.getEntities()) {
            if (e.getScoreboardTags().contains(own)) {
                e.remove();
            }
        }
        int posed = 0;
        for (CapitalPlan.Npc n : plan.npcs) {
            List<Entity> made = FloorService.spawnTownNpc(world, n.tag(), new Location(world, n.x(), n.y(), n.z(), n.yaw(), 0));
            for (Entity e : made) {
                e.addScoreboardTag(TAG);
                e.addScoreboardTag(own);
            }
            posed += made.isEmpty() ? 0 : 1;
        }
        YamlConfiguration st = state();
        st.set(w.id + ".genere", true);
        st.set(w.id + ".date", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()));
        st.set(w.id + ".pave", plan.ground);
        st.set(w.id + ".points", null);
        for (Map.Entry<String, double[]> e : plan.points.entrySet()) {
            String path = w.id + ".points." + e.getKey();
            st.set(path + ".x", e.getValue()[0]);
            st.set(path + ".y", e.getValue()[1]);
            st.set(path + ".z", e.getValue()[2]);
            st.set(path + ".regard", e.getValue()[3]);
        }
        try {
            st.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Capitale " + plan.name + " : écriture de " + stateFile.getName() + " impossible", e);
        }
        world.save();
        long seconds = Math.max(1, (System.currentTimeMillis() - w.startedAt) / 1000);
        finish(w, "<#7FD4FF>" + Text.esc(plan.name) + " générée : <#D7DCE2>" + count(w.changed) + (w.changed > 1 ? " blocs posés, " : " bloc posé, ") + posed + " PNJ, en "
                + seconds + " s. « /continent capitale " + w.id + " tp » pour y aller.");
    }

    private void release(Work w) {
        for (Chunk c : w.held) {
            c.removePluginChunkTicket(plugin);
        }
        w.held.clear();
    }

    private void finish(Work w, String mini) {
        release(w);
        if (work == w) {
            work = null;
        }
        tell(w.who, mini);
    }

    private void tell(CommandSender who, String mini) {
        plugin.getLogger().info("Capitale : " + PlainTextComponentSerializer.plainText().serialize(Text.mm(mini)));
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
                rgb = name.contains("GLASS") ? 0xBFE4EE : (name.contains("LANTERN") ? 0xFFD070 : 0x9A9A9A);
            }
            colors[i] = rgb;
        }
        File dir = new File(plugin.getDataFolder(), "apercus");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String result;
            try {
                dir.mkdirs();
                File top = new File(dir, w.id + "_plan.png"), oblique = new File(dir, w.id + "_plan_oblique.png");
                ImageIO.write(plan.top(colors, 3), "png", top);
                ImageIO.write(plan.oblique(colors, 3), "png", oblique);
                result = "<#7FD4FF>Plan de " + Text.esc(plan.name) + " écrit : <#D7DCE2>" + Text.esc(top.getPath()) + " et " + Text.esc(oblique.getName())
                        + " <#5A6270>(" + count(plan.blocks()) + " blocs, pavé à Y " + plan.ground + ")";
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Capitale " + plan.name + " : aperçu du plan impossible", e);
                result = "<#FF5C5C>Aperçu du plan impossible : " + Text.esc(String.valueOf(e.getMessage()));
            }
            String message = result;
            Bukkit.getScheduler().runTask(plugin, () -> finish(w, message));
        });
    }
}
