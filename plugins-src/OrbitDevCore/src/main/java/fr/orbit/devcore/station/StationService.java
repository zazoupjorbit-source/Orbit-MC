package fr.orbit.devcore.station;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.world.WorldService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sniffer;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * « La Station » : lit station.yml, fait dessiner le plan par {@link StationDesign} (hors du thread principal)
 * puis le pose dans le monde par tranches (un budget de blocs par tick) pour ne jamais bloquer le serveur.
 */
public final class StationService implements Listener {

    public static final String TAG = "orbit_station";
    private static final TextColor ICE = TextColor.color(0x62CFF4);
    private static final TextColor GOLD = TextColor.color(0xE0B864);
    private static final TextColor RED = TextColor.color(0xE0484A);

    private final OrbitDevCore plugin;
    private final File file;
    private final File pointsFile;
    private YamlConfiguration cfg = new YamlConfiguration();
    private Map<String, Location> pointCache;
    private boolean computing;
    private BukkitTask job;
    private BukkitTask guard;

    public StationService(OrbitDevCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "station.yml");
        this.pointsFile = new File(plugin.getDataFolder(), "station-points.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("station.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(file);
        pointCache = null;
        if (guard == null) {
            guard = Bukkit.getScheduler().runTaskTimer(plugin, this::rescueFallen, 40L, 10L);
        }
    }

    public YamlConfiguration config() {
        return cfg;
    }

    public boolean busy() {
        return computing || job != null;
    }

    public String worldName() {
        return cfg.getString("monde", "station");
    }

    /** Change un réglage de station.yml (nombre, vrai/faux ou texte) et l'enregistre. */
    public boolean set(String key, String raw) {
        if (busy() || (!cfg.contains(key) && !key.startsWith("palettes."))) {
            return false;
        }
        Object value = raw;
        if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false")) {
            value = Boolean.parseBoolean(raw);
        } else {
            try {
                value = raw.contains(".") ? (Object) Double.parseDouble(raw) : (Object) Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {
                // texte : gardé tel quel
            }
        }
        cfg.set(key, value);
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("station.yml non enregistré : " + e.getMessage());
        }
        return true;
    }

    /**
     * Monde de la station : créé vide au besoin, nuit figée, sans météo, sans mobs ni dégâts de chute.
     * Par sécurité, refuse tout monde qui n'a pas été créé vide par le plugin (le générateur vide son emprise).
     */
    private World world(CommandSender sender) {
        String name = worldName();
        World world = Bukkit.getWorld(name);
        if (world != null && plugin.worlds().managed().get(name) != WorldService.Kind.VOID) {
            say(sender, "Par sécurité, la station ne se construit que dans un monde vide créé pour elle. « " + name
                    + " » existe déjà : change « monde » dans station.yml.", RED);
            return null;
        }
        if (world == null) {
            if (!WorldService.NAME_PATTERN.matcher(name).matches()) {
                say(sender, "Nom de monde invalide dans station.yml : " + name, RED);
                return null;
            }
            say(sender, "Création du monde vide « " + name + " »...", ICE);
            world = plugin.worlds().create(name, WorldService.Kind.VOID);
        }
        if (world == null) {
            say(sender, "Impossible de créer le monde " + name + ".", RED);
            return null;
        }
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setTime(18000);
        world.setStorm(false);
        return world;
    }

    /** Points enregistrés par la dernière génération (portes, PNJ, écrans, spawn). */
    public Map<String, Location> points() {
        if (pointCache != null) {
            return pointCache;
        }
        Map<String, Location> result = new LinkedHashMap<>();
        if (pointsFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(pointsFile);
            World world = Bukkit.getWorld(yaml.getString("monde", worldName()));
            ConfigurationSection root = yaml.getConfigurationSection("points");
            if (world == null || root == null) {
                return result; // monde pas encore chargé : on relira au prochain appel
            }
            for (String key : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s != null) {
                    result.put(key.replace('_', '.'), new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                            (float) s.getDouble("yaw"), 0f));
                }
            }
        }
        pointCache = result;
        return result;
    }

    /** Génère toute la station (liste vide) ou seulement les zones données. */
    public void generate(CommandSender sender, List<String> zones) {
        if (busy()) {
            say(sender, "Une génération est déjà en cours.", RED);
            return;
        }
        World world = world(sender);
        if (world == null) {
            return;
        }
        boolean full = zones.isEmpty();
        draw(sender, zones, full, drawn -> {
            StationPlan plan = drawn.plan();
            say(sender, "La Station : " + plan.blocks().size() + " blocs dans le plan pour « " + world.getName() + " »"
                    + (full ? " (seuls ceux qui changent sont touchés)." : " (zones : " + String.join(", ", zones) + ")."), ICE);
            long solid = plan.blocks().values().stream().filter(d -> !d.getMaterial().isAir()).count();
            plugin.getLogger().info("Plan : " + solid + " blocs pleins, " + (plan.blocks().size() - solid) + " cases d'air.");
            if (drawn.design().longestJump() > 0) {
                say(sender, String.format(Locale.ROOT, "Parcours : saut le plus long %.1f blocs entre deux marches.", drawn.design().longestJump()), ICE);
            }
            if (drawn.seal() != null) {
                announceSeal(sender, drawn.seal());
            }
            run(sender, world, drawn.design(), plan, full, true, zones);
        });
    }

    /** Contrôle l'étanchéité du plan (réglages actuels de station.yml), sans rien poser dans le monde. */
    public void seal(CommandSender sender) {
        if (busy()) {
            say(sender, "Une génération est déjà en cours.", RED);
            return;
        }
        draw(sender, List.of(), true, drawn -> announceSeal(sender, drawn.seal()));
    }

    private void announceSeal(CommandSender sender, Airtight.Report seal) {
        if (seal.sealed()) {
            say(sender, "Étanchéité : aucune ouverture sur le vide, " + seal.inside() + " cases d'air à l'intérieur.", GOLD);
            return;
        }
        int ox = cfg.getInt("origine.x", 0), oy = cfg.getInt("origine.y", 100), oz = cfg.getInt("origine.z", 0);
        say(sender, "Étanchéité : " + seal.holes().size() + " ouverture(s) sur le vide, " + seal.exposed().size()
                + " point(s) repère(s) à l'air libre.", RED);
        int shown = 0;
        for (Airtight.Hole h : seal.holes()) {
            if (shown++ == 12) {
                say(sender, "  ... et " + (seal.holes().size() - 12) + " autre(s).", RED);
                break;
            }
            say(sender, "  vers " + (ox + h.x()) + " " + (oy + h.y()) + " " + (oz + h.z()) + " : ouvert vers " + h.toward()
                    + " (" + h.cells() + " cases)", RED);
        }
        for (String name : seal.exposed()) {
            say(sender, "  point " + name, RED);
        }
    }

    /**
     * Compare le monde au plan sans rien changer et écrit station-ecarts.txt : tout ce qu'une génération
     * complète poserait ou retirerait. Les retouches faites à la main dans la station en font partie.
     */
    public void diff(CommandSender sender) {
        if (busy()) {
            say(sender, "Une génération est déjà en cours.", RED);
            return;
        }
        World world = Bukkit.getWorld(worldName());
        if (world == null || plugin.worlds().managed().get(world.getName()) != WorldService.Kind.VOID) {
            say(sender, "La station n'est pas encore générée (/station generer).", RED);
            return;
        }
        draw(sender, List.of(), false, drawn -> compare(sender, world, drawn.design(), drawn.plan()));
    }

    private void compare(CommandSender sender, World world, StationDesign design, StationPlan plan) {
        int ox = cfg.getInt("origine.x", 0), oy = cfg.getInt("origine.y", 100), oz = cfg.getInt("origine.z", 0);
        int reach = design.reach();
        int minY = Math.max(world.getMinHeight(), oy + design.minY());
        int maxY = Math.min(world.getMaxHeight() - 1, oy + design.maxY());
        List<int[]> chunks = new ArrayList<>();
        int[] loaded = {0};
        for (int cx = (ox - reach) >> 4; cx <= (ox + reach) >> 4; cx++) {
            for (int cz = (oz - reach) >> 4; cz <= (oz + reach) >> 4; cz++) {
                chunks.add(new int[]{cx, cz});
                world.getChunkAtAsync(cx, cz).thenAccept(chunk -> {
                    chunk.addPluginChunkTicket(plugin);
                    loaded[0]++;
                });
            }
        }
        int[] cursor = {ox - reach, oz - reach};
        int[] count = {0, 0}; // blocs différents de ceux du plan, blocs posés hors du plan
        List<String> lines = new ArrayList<>();
        Map<String, int[]> kinds = new LinkedHashMap<>();

        job = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (loaded[0] < chunks.size()) {
                    return;
                }
                int columns = 0;
                while (cursor[0] <= ox + reach && columns++ < 1500) {
                    for (int y = minY; y <= maxY; y++) {
                        Block block = world.getBlockAt(cursor[0], y, cursor[1]);
                        BlockData wanted = plan.blocks().get(new StationPlan.Pos(cursor[0] - ox, y - oy, cursor[1] - oz));
                        if (wanted == null ? block.getType() == Material.AIR : block.getBlockData().equals(wanted)) {
                            continue;
                        }
                        count[wanted == null ? 1 : 0]++;
                        note(kinds, block.getType(), wanted == null ? Material.AIR : wanted.getMaterial(), cursor[0], y, cursor[1]);
                        if (lines.size() < 50000) {
                            lines.add(cursor[0] + " " + y + " " + cursor[1] + " | monde : " + block.getBlockData().getAsString()
                                    + " | plan : " + (wanted == null ? "rien" : wanted.getAsString()));
                        }
                    }
                    if (++cursor[1] > oz + reach) {
                        cursor[1] = oz - reach;
                        cursor[0]++;
                    }
                }
                if (cursor[0] <= ox + reach) {
                    return;
                }
                finish(world, chunks);
                int all = count[0] + count[1];
                if (all == 0) {
                    say(sender, "Écarts : aucun, le monde est identique au plan.", GOLD);
                    return;
                }
                File out = new File(plugin.getDataFolder(), "station-ecarts.txt");
                List<String> text = new ArrayList<>();
                text.add("# Écarts entre le monde « " + world.getName() + " » et le plan de la station : " + all + " blocs.");
                text.add("# Une ligne par bloc : x y z | monde : ce qui est posé | plan : ce que /station generer mettrait.");
                text.addAll(lines);
                try {
                    java.nio.file.Files.write(out.toPath(), text, java.nio.charset.StandardCharsets.UTF_8);
                } catch (IOException e) {
                    say(sender, "Liste des écarts non écrite : " + e.getMessage(), RED);
                }
                say(sender, "Écarts : " + all + " blocs diffèrent du plan, dont " + count[1] + " posés hors du plan."
                        + " Une génération complète les remettrait comme sur le plan. Liste : " + out.getPath(), GOLD);
                announceChanges(sender, "Écarts par sorte (monde → plan) :", kinds);
            } catch (RuntimeException e) {
                finish(world, chunks);
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Comparaison de la station interrompue", e);
                say(sender, "Comparaison interrompue : " + e, RED);
            }
        }, 1L, 1L);
    }

    /** Vide toute l'emprise de la station. */
    public void clear(CommandSender sender) {
        if (busy()) {
            say(sender, "Une génération est déjà en cours.", RED);
            return;
        }
        World world = world(sender);
        if (world == null) {
            return;
        }
        say(sender, "La Station : emprise en cours de vidage...", ICE);
        run(sender, world, new StationDesign(cfg), new StationPlan(), true, false, List.of());
    }

    /**
     * Écrit station-apercu.png : la station vue du dessus, sans rien poser dans le monde.
     * {@code cut} = hauteur de coupe au-dessus du sol (les blocs plus hauts sont ignorés), ou nul pour tout voir.
     */
    public void preview(CommandSender sender, Integer cut) {
        if (busy()) {
            say(sender, "Une génération est déjà en cours.", RED);
            return;
        }
        draw(sender, List.of(), false, drawn -> Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                StationDesign design = drawn.design();
                StationPlan plan = drawn.plan();
                List<String> names = new ArrayList<>();
                String suffix = cut == null ? "" : "-coupe" + cut;
                names.add(paint(design, plan, cut, 0, "station-apercu" + suffix + ".png").getName());
                if (cut == null) {
                    names.add(paint(design, plan, null, 1, "station-apercu-sud.png").getName());
                    names.add(paint(design, plan, null, 2, "station-apercu-est.png").getName());
                }
                Bukkit.getScheduler().runTask(plugin, () -> say(sender, "Aperçu écrit dans " + plugin.getDataFolder().getPath()
                        + " : " + String.join(", ", names), GOLD));
            } catch (IOException | RuntimeException e) {
                Bukkit.getScheduler().runTask(plugin, () -> say(sender, "Aperçu impossible : " + e, RED));
            }
        }));
    }

    /** Plan dessiné, avec son contrôle d'étanchéité s'il a été demandé. */
    private record Drawn(StationDesign design, StationPlan plan, Airtight.Report seal) { }

    /**
     * Calcule le plan hors du thread principal, puis rend la main sur le thread du serveur.
     * {@code checkSeal} : contrôle aussi l'étanchéité (n'a de sens que sur la station entière).
     */
    private void draw(CommandSender sender, List<String> zones, boolean checkSeal, Consumer<Drawn> then) {
        computing = true;
        StationDesign design = new StationDesign(cfg);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                StationPlan plan = design.draw(zones);
                Airtight.Report seal = checkSeal && zones.isEmpty() ? Airtight.check(plan) : null;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    computing = false;
                    then.accept(new Drawn(design, plan, seal));
                });
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().severe("Dessin de la station impossible : " + e);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    computing = false;
                    say(sender, "Dessin de la station impossible : " + e, RED);
                });
            }
        });
    }

    /**
     * Dessine une vue du plan : 0 = du dessus, 1 = de côté depuis le sud, 2 = de côté depuis l'est.
     * Le verre est rendu en transparence : on voit ce qu'il y a derrière, légèrement teinté.
     */
    private File paint(StationDesign design, StationPlan plan, Integer cut, int view, String name) throws IOException {
        int reach = design.reach(), side = 2 * reach + 1, px = 4;
        int minY = design.minY(), maxY = design.maxY();
        int width = side, height = view == 0 ? side : maxY - minY + 1;
        int[] solidDepth = new int[width * height], solidRgb = new int[width * height];
        int[] glassDepth = new int[width * height], glassRgb = new int[width * height];
        Arrays.fill(solidDepth, Integer.MIN_VALUE);
        Arrays.fill(glassDepth, Integer.MIN_VALUE);
        for (Map.Entry<StationPlan.Pos, BlockData> e : plan.blocks().entrySet()) {
            StationPlan.Pos p = e.getKey();
            Material m = e.getValue().getMaterial();
            if (m.isAir() || m == Material.LIGHT || (cut != null && p.y() > cut) || p.y() < minY || p.y() > maxY) {
                continue;
            }
            int col = view == 2 ? reach - p.z() : p.x() + reach;
            int row = view == 0 ? p.z() + reach : maxY - p.y();
            int depth = view == 0 ? p.y() : view == 1 ? p.z() : p.x();
            if (col < 0 || row < 0 || col >= width || row >= height) {
                continue;
            }
            int i = row * width + col;
            if (m.name().contains("GLASS")) {
                if (depth > glassDepth[i]) {
                    glassDepth[i] = depth;
                    glassRgb[i] = m == Material.GLASS ? 0xCFE8F5 : e.getValue().getMapColor().asRGB();
                }
            } else if (depth > solidDepth[i]) {
                solidDepth[i] = depth;
                solidRgb[i] = e.getValue().getMapColor().asRGB();
            }
        }
        BufferedImage image = new BufferedImage(width * px, height * px, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int i = row * width + col;
                int rgb = 0x070B14;
                boolean solid = solidDepth[i] != Integer.MIN_VALUE, glass = glassDepth[i] != Integer.MIN_VALUE;
                if (solid) {
                    double shade = view == 0 ? Math.max(0.55, Math.min(1.0, 0.72 + solidDepth[i] / 50.0))
                            : 0.5 + 0.5 * (solidDepth[i] + reach) / (2.0 * reach);
                    rgb = mix(0, solidRgb[i], shade);
                    if (glass && glassDepth[i] > solidDepth[i]) {
                        rgb = mix(rgb, glassRgb[i], 0.3);
                    }
                } else if (glass) {
                    rgb = mix(0x070B14, glassRgb[i], 0.45);
                }
                for (int dx = 0; dx < px; dx++) {
                    for (int dy = 0; dy < px; dy++) {
                        image.setRGB(col * px + dx, row * px + dy, rgb);
                    }
                }
            }
        }
        File out = new File(plugin.getDataFolder(), name);
        ImageIO.write(image, "png", out);
        return out;
    }

    /** Mélange deux couleurs : {@code t} = part de la seconde. */
    private static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private void run(CommandSender sender, World world, StationDesign design, StationPlan plan,
                     boolean clear, boolean build, List<String> zones) {
        int ox = cfg.getInt("origine.x", 0), oy = cfg.getInt("origine.y", 100), oz = cfg.getInt("origine.z", 0);
        int budget = Math.max(500, cfg.getInt("blocs-par-tick", 12000));
        int reach = design.reach();
        int minY = Math.max(world.getMinHeight(), oy + design.minY());
        int maxY = Math.min(world.getMaxHeight() - 1, oy + design.maxY());

        // les chunks de l'emprise sont chargés en arrière-plan et gardés en mémoire le temps du chantier
        List<int[]> chunks = new ArrayList<>();
        int[] loaded = {0};
        for (int cx = (ox - reach) >> 4; cx <= (ox + reach) >> 4; cx++) {
            for (int cz = (oz - reach) >> 4; cz <= (oz + reach) >> 4; cz++) {
                chunks.add(new int[]{cx, cz});
                world.getChunkAtAsync(cx, cz).thenAccept(chunk -> {
                    chunk.addPluginChunkTicket(plugin);
                    loaded[0]++;
                });
            }
        }

        Iterator<Map.Entry<StationPlan.Pos, BlockData>> blocks = plan.blocks().entrySet().iterator();
        int total = plan.blocks().size();
        long started = System.currentTimeMillis();
        int[] cursor = {ox - reach, oz - reach};
        int[] state = {clear ? 0 : 1, 0, 0, 0, 0}; // phase, blocs posés, blocs retirés, dernier palier annoncé, blocs contrôlés
        // ce que le chantier change, par sorte : « avant → après » -> {nombre, x, y, z du premier}
        Map<String, int[]> changes = new LinkedHashMap<>();

        job = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (loaded[0] < chunks.size()) {
                    return;
                }
                if (state[0] == 0) {
                    int changed = 0, columns = 0, maxColumns = Math.max(300, budget / 6);
                    while (cursor[0] <= ox + reach && changed < budget && columns < maxColumns) {
                        for (int y = minY; y <= maxY; y++) {
                            Block block = world.getBlockAt(cursor[0], y, cursor[1]);
                            // en reconstruction, on ne retire que ce qui n'est plus dans le plan :
                            // le sol reste sous les pieds des joueurs présents
                            if (block.getType() != Material.AIR
                                    && !(build && plan.has(cursor[0] - ox, y - oy, cursor[1] - oz))) {
                                note(changes, block.getType(), Material.AIR, cursor[0], y, cursor[1]);
                                block.setType(Material.AIR, false);
                                changed++;
                            }
                        }
                        columns++;
                        if (++cursor[1] > oz + reach) {
                            cursor[1] = oz - reach;
                            cursor[0]++;
                        }
                    }
                    state[2] += changed;
                    if (cursor[0] > ox + reach) {
                        state[0] = 1;
                    }
                    return;
                }
                if (state[0] == 1) {
                    int n = 0;
                    while (blocks.hasNext() && n < budget) {
                        Map.Entry<StationPlan.Pos, BlockData> e = blocks.next();
                        StationPlan.Pos p = e.getKey();
                        int y = oy + p.y();
                        if (y >= minY && y <= maxY) {
                            // un bloc déjà en place n'est pas reposé : pas de clignotement, pas de trou sous les pieds
                            Block block = world.getBlockAt(ox + p.x(), y, oz + p.z());
                            if (!block.getBlockData().equals(e.getValue())) {
                                note(changes, block.getType(), e.getValue().getMaterial(), ox + p.x(), y, oz + p.z());
                                block.setBlockData(e.getValue(), false);
                                state[1]++;
                            }
                        }
                        n++;
                    }
                    state[4] += n;
                    int step = total == 0 ? 4 : (int) ((long) state[4] * 4 / total);
                    if (step > state[3] && step < 4) {
                        state[3] = step;
                        say(sender, "La Station : " + (step * 25) + " %", ICE);
                    }
                    if (!blocks.hasNext()) {
                        state[0] = 2;
                    }
                    return;
                }
                // dernière étape : hologrammes, créature, points
                removeEntities(world, clear ? List.of() : zones);
                int spawned = 0;
                if (build) {
                    spawned = spawnEntities(world, plan, ox, oy, oz);
                    savePoints(world, plan, ox, oy, oz, clear);
                    if (clear) {
                        applySpawnAndWarps(world, plan, ox, oy, oz);
                    }
                }
                finish(world, chunks);
                long seconds = Math.max(1, (System.currentTimeMillis() - started) / 1000);
                say(sender, build ? "La Station est prête : " + state[1] + " blocs posés, " + state[2] + " retirés, "
                        + spawned + " hologrammes et créatures sur " + (plan.texts().size() + plan.creatures().size())
                        + ", en " + seconds + " s. /station tp pour la visiter."
                        : "Emprise vidée : " + state[2] + " blocs retirés.", GOLD);
                // une retouche de la station en place : on dit ce qui a bougé (inutile pour une première pose)
                if (build && state[1] + state[2] > 0 && state[1] < total / 2) {
                    announceChanges(sender, "Ce qui a changé :", changes);
                }
            } catch (RuntimeException e) {
                finish(world, chunks);
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Génération de la station interrompue", e);
                say(sender, "Génération interrompue : " + e, RED);
            }
        }, 1L, 1L);
    }

    private static void note(Map<String, int[]> changes, Material before, Material after, int x, int y, int z) {
        String a = before.name().toLowerCase(Locale.ROOT), b = after.name().toLowerCase(Locale.ROOT);
        changes.computeIfAbsent(before == after ? b + " (réglage du bloc)" : a + " → " + b, k -> new int[]{0, x, y, z})[0]++;
    }

    /** Liste les changements d'une retouche, les plus nombreux d'abord : tout dans la console, dix lignes en jeu. */
    private void announceChanges(CommandSender sender, String title, Map<String, int[]> changes) {
        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(changes.entrySet());
        sorted.sort((x, y) -> Integer.compare(y.getValue()[0], x.getValue()[0]));
        say(sender, title, ICE);
        int shown = 0, rest = 0;
        for (Map.Entry<String, int[]> e : sorted) {
            int[] c = e.getValue();
            String line = "  " + c[0] + " × " + e.getKey() + ", vers " + c[1] + " " + c[2] + " " + c[3];
            if (shown++ < 10) {
                say(sender, line, ICE);
            } else {
                plugin.getLogger().info(line);
                rest += c[0];
            }
        }
        if (rest > 0 && sender != null && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(Component.text("  et " + rest + " autres blocs (détail dans la console).", ICE));
        }
    }

    private void finish(World world, List<int[]> chunks) {
        if (job != null) {
            job.cancel();
            job = null;
        }
        for (int[] c : chunks) {
            world.removePluginChunkTicket(c[0], c[1], plugin);
        }
    }

    /** Retire les hologrammes et créatures de la station (toutes les zones si la liste est vide). */
    private void removeEntities(World world, List<String> zones) {
        for (Entity entity : world.getEntities()) {
            if (!entity.getScoreboardTags().contains(TAG)) {
                continue;
            }
            boolean match = zones.isEmpty();
            for (String z : zones) {
                match |= entity.getScoreboardTags().contains(TAG + "_" + z);
            }
            if (match) {
                entity.remove();
            }
        }
    }

    /** @return le nombre d'entités réellement apparues (un autre plugin peut en refuser). */
    private int spawnEntities(World world, StationPlan plan, int ox, int oy, int oz) {
        int count = 0;
        for (StationPlan.Text t : plan.texts()) {
            boolean fixed = t.yaw() != null;
            Location at = new Location(world, ox + t.x(), oy + t.y(), oz + t.z(), fixed ? t.yaw() : 0f, 0f);
            Consumer<TextDisplay> setup = d -> {
                d.text(t.text());
                d.setBillboard(fixed ? Display.Billboard.FIXED : Display.Billboard.CENTER);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setShadowed(true);
                d.setLineWidth(400);
                d.setViewRange(t.scale() >= 2f ? 3.0f : 1.0f);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(t.scale(), t.scale(), t.scale()), new AxisAngle4f()));
                d.setPersistent(true);
                d.addScoreboardTag(TAG);
                d.addScoreboardTag(TAG + "_" + t.zone());
            };
            if (world.spawn(at, TextDisplay.class, setup).isValid()) {
                count++;
            }
        }
        for (StationPlan.Creature c : plan.creatures()) {
            Location at = new Location(world, ox + c.x(), oy + c.y(), oz + c.z(), c.yaw(), 0f);
            Consumer<Sniffer> setup = s -> {
                s.setAI(false);
                s.setGravity(false);
                s.setSilent(true);
                s.setInvulnerable(true);
                s.setCollidable(false);
                s.setPersistent(true);
                s.setRemoveWhenFarAway(false);
                s.addScoreboardTag(TAG);
                s.addScoreboardTag(TAG + "_" + c.zone());
            };
            if (world.spawn(at, Sniffer.class, setup).isValid()) {
                count++;
            }
        }
        return count;
    }

    /** Écrit station-points.yml en coordonnées du monde ; une génération partielle complète le fichier existant. */
    private void savePoints(World world, StationPlan plan, int ox, int oy, int oz, boolean replace) {
        YamlConfiguration yaml = replace || !pointsFile.exists() ? new YamlConfiguration() : YamlConfiguration.loadConfiguration(pointsFile);
        yaml.set("monde", world.getName());
        for (Map.Entry<String, StationPlan.Point> e : plan.points().entrySet()) {
            StationPlan.Point p = e.getValue();
            String path = "points." + e.getKey().replace('.', '_');
            yaml.set(path + ".x", ox + p.x());
            yaml.set(path + ".y", oy + p.y());
            yaml.set(path + ".z", oz + p.z());
            yaml.set(path + ".yaw", (double) p.yaw());
        }
        Map<String, Location> cache = new LinkedHashMap<>();
        ConfigurationSection root = yaml.getConfigurationSection("points");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                cache.put(key.replace('_', '.'), new Location(world, root.getDouble(key + ".x"), root.getDouble(key + ".y"),
                        root.getDouble(key + ".z"), (float) root.getDouble(key + ".yaw"), 0f));
            }
        }
        pointCache = cache;
        plugin.fileWriter().writeAsync(pointsFile.toPath(), yaml.saveToString());
    }

    private void applySpawnAndWarps(World world, StationPlan plan, int ox, int oy, int oz) {
        StationPlan.Point spawn = plan.points().get("spawn");
        if (spawn != null && cfg.getBoolean("definir-spawn", true)) {
            Location at = new Location(world, ox + spawn.x(), oy + spawn.y(), oz + spawn.z(), spawn.yaw(), 0f);
            world.setSpawnLocation(at);
            plugin.locations().setSpawn(at);
        }
        if (cfg.getBoolean("creer-warps", true)) {
            Map<String, Material> icons = new LinkedHashMap<>();
            icons.put("survie", Material.OAK_SAPLING);
            icons.put("extraction", Material.CROSSBOW);
            icons.put("practice", Material.DIAMOND_SWORD);
            icons.put("mmo", Material.ENDER_EYE);
            for (Map.Entry<String, Material> e : icons.entrySet()) {
                StationPlan.Point p = plan.points().get("pnj." + e.getKey());
                if (p != null) {
                    // le warp regarde la porte, le PNJ regardera l'atrium
                    plugin.locations().setWarp(e.getKey(), new Location(world, ox + p.x(), oy + p.y(), oz + p.z(),
                            (p.yaw() + 180f) % 360f, 0f), e.getValue());
                }
            }
        }
    }

    /** Un joueur tombé dans le vide sous la station est ramené au spawn de la station. */
    private void rescueFallen() {
        World world = Bukkit.getWorld(worldName());
        if (world == null || world.getPlayers().isEmpty()) {
            return;
        }
        Location spawn = points().get("spawn");
        if (spawn == null) {
            return;
        }
        double limit = cfg.getInt("origine.y", 100) - 40;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().getY() < limit) {
                player.setFallDistance(0f);
                player.teleportAsync(spawn);
            }
        }
    }

    /** Plaque dorée du belvédère : petit message de fin de parcours. */
    @EventHandler(ignoreCancelled = true)
    public void onPlate(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.PHYSICAL || block == null
                || block.getType() != Material.LIGHT_WEIGHTED_PRESSURE_PLATE
                || !block.getWorld().getName().equals(worldName())) {
            return;
        }
        event.getPlayer().sendActionBar(Component.text("Belvédère atteint. Belle vue sur l'orbite.", GOLD));
    }

    private void say(CommandSender sender, String message, TextColor color) {
        plugin.getLogger().info(message);
        if (sender != null && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(Component.text(message, color));
        }
    }
}
