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

    /** Zone ronde du monde (centre et rayon, en blocs) : là où /station remettre repose le plan. */
    public record Area(int x, int y, int z, int radius) {
        boolean has(int wx, int wy, int wz) {
            long dx = wx - x, dy = wy - y, dz = wz - z;
            return dx * dx + dy * dy + dz * dz <= (long) radius * radius;
        }
    }

    /** Une case où le monde n'est pas comme sur le plan. {@code world} ou {@code plan} nul = de l'air. */
    private record Gap(StationPlan.Pos pos, BlockData world, BlockData plan) { }

    /** Ce qu'un chantier fait : poser toute la station, quelques zones, remettre comme sur le plan, ou tout vider. */
    private enum Work { BUILD, ZONES, RESTORE, CLEAR }

    private static boolean air(BlockData data) {
        return data == null || data.getMaterial().isAir();
    }

    /** Deux blocs identiques, réglages compris ; « rien » et de l'air sont la même chose. */
    private static boolean same(BlockData a, BlockData b) {
        return air(a) ? air(b) : !air(b) && a.equals(b);
    }

    /** Monde de la station s'il a déjà été créé par le plugin ; sinon nul, avec un message. */
    private World built(CommandSender sender) {
        World world = Bukkit.getWorld(worldName());
        if (world == null || plugin.worlds().managed().get(world.getName()) != WorldService.Kind.VOID) {
            say(sender, "La station n'est pas encore générée (/station generer).", RED);
            return null;
        }
        return world;
    }

    /**
     * Génère toute la station (liste vide) ou seulement les zones données. Seuls les blocs que le plan change
     * sont touchés : ce qui a été retouché à la main depuis la dernière pose est gardé tel quel.
     */
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
                    + (full ? " (seuls ceux qui changent sont touchés, les retouches faites à la main sont gardées)."
                    : " (zones : " + String.join(", ", zones) + ")."), ICE);
            long solid = plan.blocks().values().stream().filter(d -> !d.getMaterial().isAir()).count();
            plugin.getLogger().info("Plan : " + solid + " blocs pleins, " + (plan.blocks().size() - solid) + " cases d'air.");
            if (drawn.design().longestJump() > 0) {
                say(sender, String.format(Locale.ROOT, "Parcours : saut le plus long %.1f blocs entre deux marches.", drawn.design().longestJump()), ICE);
            }
            run(sender, world, drawn, full ? Work.BUILD : Work.ZONES, zones, null);
        });
    }

    /**
     * Remet le monde comme sur le plan, retouches faites à la main comprises : dans une zone ronde,
     * ou partout si {@code area} est nul.
     */
    public void restore(CommandSender sender, Area area) {
        if (busy()) {
            say(sender, "Une génération est déjà en cours.", RED);
            return;
        }
        World world = built(sender);
        if (world == null) {
            return;
        }
        draw(sender, List.of(), area == null, drawn -> run(sender, world, drawn, Work.RESTORE, List.of(), area));
    }

    /**
     * Contrôle l'étanchéité de la station telle qu'elle est dans le monde, retouches faites à la main comprises,
     * sans rien poser. Tant que la station n'est pas posée, c'est le plan seul qui est contrôlé.
     */
    public void seal(CommandSender sender) {
        if (busy()) {
            say(sender, "Une génération est déjà en cours.", RED);
            return;
        }
        World found = Bukkit.getWorld(worldName());
        World world = found != null && plugin.worlds().managed().get(found.getName()) == WorldService.Kind.VOID ? found : null;
        String planOnly = " (plan seul : la station n'est pas encore posée)";
        draw(sender, List.of(), true, drawn -> {
            if (world == null) {
                announceSeal(sender, drawn.seal(), planOnly);
                return;
            }
            survey(sender, world, drawn, null, scan -> {
                if (scan.blocks == 0 || scan.gaps.isEmpty()) {
                    announceSeal(sender, drawn.seal(), scan.blocks == 0 ? planOnly : "");
                    return;
                }
                // le monde n'est pas tout à fait le plan : on contrôle ce qui est vraiment posé
                StationPlan real = overlay(drawn.plan(), scan.gaps);
                String note = " (" + scan.gaps.size() + " blocs du monde ne sont pas comme sur le plan : c'est le monde qui est contrôlé)";
                computing = true;
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    Airtight.Report report = null;
                    String error = null;
                    try {
                        report = Airtight.check(real);
                    } catch (RuntimeException | LinkageError e) {
                        error = e.toString();
                    }
                    Airtight.Report result = report;
                    String failure = error;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        computing = false;
                        if (result == null) {
                            say(sender, "Contrôle d'étanchéité impossible : " + failure, RED);
                            return;
                        }
                        announceSeal(sender, result, note);
                        if (!result.sealed() && drawn.seal().sealed()) {
                            say(sender, "Le plan seul est étanche : l'ouverture vient d'un bloc retiré ou changé à la main, ou d'une partie"
                                    + " du plan pas encore posée. /station ecarts donne la liste.", RED);
                        }
                    });
                });
            });
        });
    }

    private void announceSeal(CommandSender sender, Airtight.Report seal, String note) {
        if (seal.sealed()) {
            say(sender, "Étanchéité : aucune ouverture sur le vide, " + seal.inside() + " cases d'air à l'intérieur" + note + ".", GOLD);
            return;
        }
        int ox = cfg.getInt("origine.x", 0), oy = cfg.getInt("origine.y", 100), oz = cfg.getInt("origine.z", 0);
        say(sender, "Étanchéité : " + seal.holes().size() + " ouverture(s) sur le vide, " + seal.exposed().size()
                + " point(s) repère(s) à l'air libre" + note + ".", RED);
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
     * Compare le monde au plan sans rien changer et écrit station-ecarts.txt : les retouches faites à la main
     * (que /station generer garde) et ce que le plan a changé depuis la dernière pose (qu'il posera).
     */
    public void diff(CommandSender sender) {
        if (busy()) {
            say(sender, "Une génération est déjà en cours.", RED);
            return;
        }
        World world = built(sender);
        if (world == null) {
            return;
        }
        draw(sender, List.of(), false, drawn -> {
            int ox = cfg.getInt("origine.x", 0), oy = cfg.getInt("origine.y", 100), oz = cfg.getInt("origine.z", 0);
            PlanMemory stored = PlanMemory.load(world, ox, oy, oz, plugin.getLogger());
            survey(sender, world, drawn, stored, scan -> {
                if (stored == null && scan.blocks == 0) {
                    say(sender, "La station n'est pas encore générée (/station generer).", RED);
                    return;
                }
                // sans mémoire, tout écart est tenu pour une retouche : c'est ce que fera /station generer
                PlanMemory memory = stored != null ? stored : PlanMemory.of(drawn.plan());
                List<Gap> kept = new ArrayList<>(), todo = new ArrayList<>();
                int moved = sort(scan.gaps, memory, kept, todo);
                File out = writeGaps(sender, world, kept, todo, stored == null);
                if (scan.gaps.isEmpty()) {
                    say(sender, "Écarts : aucun, le monde est identique au plan.", GOLD);
                    return;
                }
                say(sender, "Écarts : " + scan.gaps.size() + " blocs diffèrent du plan. Liste : " + out.getPath(), GOLD);
                if (stored == null) {
                    say(sender, NO_MEMORY + " /station generer n'y touchera pas ; /station remettre <rayon> sur place,"
                            + " ou /station remettre tout, y pose le plan.", GOLD);
                    announceChanges(sender, "Écarts par sorte (monde → plan) :", kinds(kept, ox, oy, oz));
                    return;
                }
                if (!kept.isEmpty()) {
                    say(sender, "Retouches faites à la main : " + kept.size() + ". /station generer les garde"
                            + (moved > 0 ? " (" + moved + " sont à un endroit où le plan a changé : le plan n'y sera pas posé)." : "."), GOLD);
                    announceChanges(sender, "Retouches par sorte (monde → plan) :", kinds(kept, ox, oy, oz));
                }
                if (!todo.isEmpty()) {
                    say(sender, "Plan changé depuis la dernière pose : " + todo.size() + " blocs. /station generer les posera.", GOLD);
                    announceChanges(sender, "À poser par sorte (monde → plan) :", kinds(todo, ox, oy, oz));
                }
            });
        });
    }

    private static final String NO_MEMORY = "Ce monde n'a pas encore de mémoire du dernier plan posé : impossible de savoir"
            + " si ces blocs sont des retouches faites à la main ou du plan pas encore posé.";

    /**
     * Trie les différences : {@code kept} = retouches faites à la main (le monde n'est plus ce que le générateur
     * avait posé), {@code todo} = cases intactes que le plan a changées. Renvoie le nombre de retouches situées
     * à un endroit où le plan a changé lui aussi.
     */
    private static int sort(List<Gap> gaps, PlanMemory memory, List<Gap> kept, List<Gap> todo) {
        int moved = 0;
        for (Gap g : gaps) {
            BlockData remembered = memory.get(g.pos());
            if (same(g.world(), remembered)) {
                todo.add(g);
            } else {
                kept.add(g);
                if (!same(g.plan(), remembered)) {
                    moved++;
                }
            }
        }
        return moved;
    }

    /** Regroupe des différences par sorte : « bloc du monde → bloc du plan » -> {nombre, x, y, z du premier}. */
    private static Map<String, int[]> kinds(List<Gap> gaps, int ox, int oy, int oz) {
        Map<String, int[]> kinds = new LinkedHashMap<>();
        for (Gap g : gaps) {
            note(kinds, g.world() == null ? Material.AIR : g.world().getMaterial(), g.plan() == null ? Material.AIR : g.plan().getMaterial(),
                    ox + g.pos().x(), oy + g.pos().y(), oz + g.pos().z());
        }
        return kinds;
    }

    /**
     * Écrit station-ecarts.txt : une ligne par bloc qui n'est pas comme sur le plan, les retouches d'abord.
     * Le fichier est toujours réécrit, même sans écart, pour ne jamais laisser traîner une vieille liste.
     */
    private File writeGaps(CommandSender sender, World world, List<Gap> kept, List<Gap> todo, boolean unknown) {
        int ox = cfg.getInt("origine.x", 0), oy = cfg.getInt("origine.y", 100), oz = cfg.getInt("origine.z", 0);
        File out = new File(plugin.getDataFolder(), "station-ecarts.txt");
        List<String> text = new ArrayList<>();
        text.add("# Écarts entre le monde « " + world.getName() + " » et le plan de la station : " + (kept.size() + todo.size()) + " blocs.");
        text.add("# Une ligne par bloc : x y z | monde : ce qui est posé | plan : ce que prévoit le plan | ce qu'en fait /station generer.");
        text.add(unknown ? "# Pas de mémoire du dernier plan posé pour ce monde : tout écart est gardé tel quel."
                : "# « retouche » = bloc changé à la main, gardé tel quel ; « à poser » = le plan a changé depuis la dernière pose.");
        for (int pass = 0; pass < 2; pass++) {
            String label = pass == 1 ? "à poser" : unknown ? "gardé" : "retouche";
            for (Gap g : pass == 0 ? kept : todo) {
                if (text.size() >= 50003) {
                    break;
                }
                text.add((ox + g.pos().x()) + " " + (oy + g.pos().y()) + " " + (oz + g.pos().z())
                        + " | monde : " + (g.world() == null ? "minecraft:air" : g.world().getAsString())
                        + " | plan : " + (g.plan() == null ? "minecraft:air" : g.plan().getAsString()) + " | " + label);
            }
        }
        try {
            java.nio.file.Files.write(out.toPath(), text, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            say(sender, "Liste des écarts non écrite : " + e.getMessage(), RED);
        }
        return out;
    }

    /** Plan réel : le plan, avec à la place de ses blocs ceux que le monde a vraiment aux endroits donnés. */
    private static StationPlan overlay(StationPlan plan, List<Gap> gaps) {
        StationPlan real = new StationPlan();
        real.blocks().putAll(plan.blocks());
        real.points().putAll(plan.points());
        for (Gap g : gaps) {
            if (g.world() == null) {
                real.blocks().remove(g.pos());
            } else {
                real.blocks().put(g.pos(), g.world());
            }
        }
        return real;
    }

    /**
     * Boîte du monde à balayer, {x0, x1, y0, y1, z0, z1} : l'emprise de la station, élargie à tout ce que
     * contiennent le plan et la mémoire du dernier plan posé (une station rétrécie laisse des restes à retirer).
     */
    private int[] box(World world, StationDesign design, StationPlan plan, PlanMemory memory) {
        int ox = cfg.getInt("origine.x", 0), oy = cfg.getInt("origine.y", 100), oz = cfg.getInt("origine.z", 0);
        int reach = design.reach();
        int[] b = {-reach, reach, design.minY(), design.maxY(), -reach, reach};
        widen(b, plan.blocks().keySet());
        if (memory != null) {
            widen(b, memory.cells().keySet());
        }
        return new int[]{ox + b[0], ox + b[1], Math.max(world.getMinHeight(), oy + b[2]),
                Math.min(world.getMaxHeight() - 1, oy + b[3]), oz + b[4], oz + b[5]};
    }

    private static void widen(int[] b, Iterable<StationPlan.Pos> cells) {
        for (StationPlan.Pos p : cells) {
            b[0] = Math.min(b[0], p.x());
            b[1] = Math.max(b[1], p.x());
            b[2] = Math.min(b[2], p.y());
            b[3] = Math.max(b[3], p.y());
            b[4] = Math.min(b[4], p.z());
            b[5] = Math.max(b[5], p.z());
        }
    }

    /** Charge en arrière-plan les chunks de la boîte et les garde en mémoire le temps du chantier. */
    private List<int[]> hold(World world, int[] box, int[] loaded) {
        List<int[]> chunks = new ArrayList<>();
        for (int cx = box[0] >> 4; cx <= box[1] >> 4; cx++) {
            for (int cz = box[4] >> 4; cz <= box[5] >> 4; cz++) {
                chunks.add(new int[]{cx, cz});
                world.getChunkAtAsync(cx, cz).thenAccept(chunk -> {
                    chunk.addPluginChunkTicket(plugin);
                    loaded[0]++;
                });
            }
        }
        return chunks;
    }

    /** Colonnes du monde relues par tick pendant un balayage. */
    private int columnsPerTick() {
        return Math.max(300, Math.max(500, cfg.getInt("blocs-par-tick", 12000)) / 8);
    }

    /** Balayage du monde par tranches : relève chaque case qui n'est pas comme sur le plan, sans rien changer. */
    private static final class Scan {
        final List<Gap> gaps = new ArrayList<>();
        /** Blocs rencontrés (0 = rien n'est posé ici), et cases pleines du plan déjà en place dans le monde. */
        int blocks, matched;
        private final World world;
        private final StationPlan plan;
        private final int ox, oy, oz;
        private final int[] box;
        private final Area area;
        private final Iterator<Map.Entry<StationPlan.Pos, BlockData>> cells;
        private int x, z;

        /** {@code planOnly} : ne regarde que les cases du plan (pose de quelques zones) au lieu de toute la boîte. */
        Scan(World world, StationPlan plan, int ox, int oy, int oz, int[] box, Area area, boolean planOnly) {
            this.world = world;
            this.plan = plan;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.box = box;
            this.area = area;
            this.cells = planOnly ? plan.blocks().entrySet().iterator() : null;
            this.x = box[0];
            this.z = box[4];
        }

        /** Avance d'un nombre de colonnes ; vrai quand tout est balayé. */
        boolean advance(int columns) {
            if (cells != null) {
                int n = columns * 40;
                while (cells.hasNext() && n-- > 0) {
                    Map.Entry<StationPlan.Pos, BlockData> e = cells.next();
                    int y = oy + e.getKey().y();
                    if (y >= box[2] && y <= box[3]) {
                        look(e.getKey(), e.getValue());
                    }
                }
                return !cells.hasNext();
            }
            while (x <= box[1] && columns-- > 0) {
                for (int y = box[2]; y <= box[3]; y++) {
                    if (area == null || area.has(x, y, z)) {
                        StationPlan.Pos p = new StationPlan.Pos(x - ox, y - oy, z - oz);
                        look(p, plan.blocks().get(p));
                    }
                }
                if (++z > box[5]) {
                    z = box[4];
                    x++;
                }
            }
            return x > box[1];
        }

        private void look(StationPlan.Pos p, BlockData wanted) {
            Block block = world.getBlockAt(ox + p.x(), oy + p.y(), oz + p.z());
            if (block.getType().isAir()) {
                if (!air(wanted)) {
                    gaps.add(new Gap(p, null, wanted));
                }
                return;
            }
            blocks++;
            BlockData current = block.getBlockData();
            if (air(wanted)) {
                gaps.add(new Gap(p, current, null));
            } else if (current.equals(wanted)) {
                matched++;
            } else {
                gaps.add(new Gap(p, current, wanted));
            }
        }
    }

    /** Balaye toute la station sans rien changer, puis donne à {@code then} ce qui diffère du plan. */
    private void survey(CommandSender sender, World world, Drawn drawn, PlanMemory memory, Consumer<Scan> then) {
        int ox = cfg.getInt("origine.x", 0), oy = cfg.getInt("origine.y", 100), oz = cfg.getInt("origine.z", 0);
        int[] box = box(world, drawn.design(), drawn.plan(), memory);
        int[] loaded = {0};
        List<int[]> chunks = hold(world, box, loaded);
        Scan scan = new Scan(world, drawn.plan(), ox, oy, oz, box, null, false);
        int columns = columnsPerTick();
        job = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (loaded[0] < chunks.size() || !scan.advance(columns)) {
                    return;
                }
                finish(world, chunks);
                then.accept(scan);
            } catch (RuntimeException e) {
                finish(world, chunks);
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Comparaison de la station interrompue", e);
                say(sender, "Comparaison interrompue : " + e, RED);
            }
        }, 1L, 1L);
    }

    /** Vide toute l'emprise de la station, retouches faites à la main comprises, et oublie le dernier plan posé. */
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
        run(sender, world, new Drawn(new StationDesign(cfg), new StationPlan(), null), Work.CLEAR, List.of(), null);
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

    /**
     * Le chantier : relit le monde, trie ce qui diffère du plan, puis ne pose que ce qui doit l'être.
     *
     * Une case du monde qui n'est plus ce que le générateur y avait mis (mémoire du dernier plan posé) est une
     * retouche faite à la main : elle est gardée, sauf si on demande de remettre comme sur le plan. Une case
     * intacte que le plan a changée est mise à jour. La mémoire n'est réécrite qu'à la fin : une pose
     * interrompue reprend où elle en était, sans prendre ses propres blocs pour des retouches.
     */
    private void run(CommandSender sender, World world, Drawn drawn, Work work, List<String> zones, Area area) {
        StationDesign design = drawn.design();
        StationPlan plan = drawn.plan();
        int ox = cfg.getInt("origine.x", 0), oy = cfg.getInt("origine.y", 100), oz = cfg.getInt("origine.z", 0);
        int budget = Math.max(500, cfg.getInt("blocs-par-tick", 12000));
        PlanMemory stored = PlanMemory.load(world, ox, oy, oz, plugin.getLogger());
        if (work == Work.ZONES && stored == null) {
            say(sender, "Ce monde n'a pas encore de mémoire du dernier plan posé : lance une fois /station generer"
                    + " (toute la station), puis recommence avec les zones.", RED);
            return;
        }
        int[] whole = box(world, design, plan, stored);
        int[] box = area == null ? whole : new int[]{
                Math.max(whole[0], area.x() - area.radius()), Math.min(whole[1], area.x() + area.radius()),
                Math.max(whole[2], area.y() - area.radius()), Math.min(whole[3], area.y() + area.radius()),
                Math.max(whole[4], area.z() - area.radius()), Math.min(whole[5], area.z() + area.radius())};
        String where = area == null ? "" : "à moins de " + area.radius() + " blocs de " + area.x() + " " + area.y() + " " + area.z();
        if (box[0] > box[1] || box[2] > box[3] || box[4] > box[5]) {
            say(sender, "Rien de la station " + where + ".", RED);
            return;
        }
        // station entière (et pas seulement des zones ou un endroit) : hologrammes, points, spawn et étanchéité suivent
        boolean everywhere = work == Work.BUILD || (work == Work.RESTORE && area == null);
        int solid = (int) plan.blocks().values().stream().filter(d -> !d.getMaterial().isAir()).count();

        int[] loaded = {0};
        List<int[]> chunks = hold(world, box, loaded);
        Scan scan = new Scan(world, plan, ox, oy, oz, box, area, work == Work.ZONES);
        int columns = columnsPerTick();
        long started = System.currentTimeMillis();
        int[] state = {0, 0, 0, 0, 0}; // phase, blocs posés, blocs retirés, dernier palier annoncé, avancement dans la liste
        int[] facts = {0, 0}; // retouches à un endroit où le plan a changé aussi ; retouches remplacées par le plan
        boolean[] flags = {false, false}; // monde sans mémoire (tout écart gardé) ; contrôle d'étanchéité en cours
        List<Gap> todo = new ArrayList<>(), kept = new ArrayList<>();
        PlanMemory[] memory = {stored};
        Airtight.Report[] seal = {null};
        // ce que le chantier change, par sorte : « avant → après » -> {nombre, x, y, z du premier}
        Map<String, int[]> changes = new LinkedHashMap<>();

        job = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (loaded[0] < chunks.size()) {
                    return;
                }
                if (state[0] == 0) {
                    if (!scan.advance(columns)) {
                        return;
                    }
                    if (work == Work.CLEAR) {
                        todo.addAll(scan.gaps);
                    } else {
                        if (memory[0] == null) {
                            if (work == Work.BUILD && scan.matched * 20L < solid) {
                                // presque rien du plan n'est en place : la station n'a jamais été posée ici.
                                // Ce repère écrit tout de suite fait qu'une pose interrompue reprendra où elle en était.
                                memory[0] = PlanMemory.empty();
                                saveMemory(sender, world, memory[0], ox, oy, oz);
                            } else {
                                // station posée avant que la mémoire existe : on suppose qu'elle l'avait été avec ce plan,
                                // donc tout écart est gardé comme une retouche (rien n'est jamais écrasé par erreur)
                                memory[0] = PlanMemory.of(plan);
                                flags[0] = true;
                            }
                        }
                        for (Gap g : scan.gaps) {
                            BlockData remembered = memory[0].get(g.pos());
                            boolean retouched = !same(g.world(), remembered);
                            if (work == Work.RESTORE || !retouched) {
                                todo.add(g);
                                if (retouched) {
                                    facts[1]++;
                                }
                            } else {
                                kept.add(g);
                                if (!same(g.plan(), remembered)) {
                                    facts[0]++;
                                }
                            }
                        }
                        if (everywhere) {
                            if (kept.isEmpty()) {
                                seal[0] = drawn.seal(); // le monde sera exactement le plan
                            } else {
                                // étanchéité du résultat réel : le plan, avec les retouches gardées
                                StationPlan real = overlay(plan, kept);
                                flags[1] = true;
                                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                                    Airtight.Report report = null;
                                    try {
                                        report = Airtight.check(real);
                                    } catch (RuntimeException | LinkageError e) {
                                        plugin.getLogger().warning("Contrôle d'étanchéité impossible : " + e);
                                    }
                                    Airtight.Report result = report;
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        seal[0] = result;
                                        flags[1] = false;
                                    });
                                });
                            }
                        }
                    }
                    state[0] = 1;
                    return;
                }
                if (state[0] == 1) {
                    int n = 0;
                    while (state[4] < todo.size() && n++ < budget) {
                        Gap g = todo.get(state[4]++);
                        int x = ox + g.pos().x(), y = oy + g.pos().y(), z = oz + g.pos().z();
                        Block block = world.getBlockAt(x, y, z);
                        if (g.plan() == null) {
                            note(changes, block.getType(), Material.AIR, x, y, z);
                            block.setType(Material.AIR, false);
                            state[2]++;
                        } else {
                            note(changes, block.getType(), g.plan().getMaterial(), x, y, z);
                            block.setBlockData(g.plan(), false);
                            state[1]++;
                        }
                    }
                    int step = todo.isEmpty() ? 4 : (int) ((long) state[4] * 4 / todo.size());
                    if (todo.size() > 2 * budget && step > state[3] && step < 4) {
                        state[3] = step;
                        say(sender, "La Station : " + (step * 25) + " %", ICE);
                    }
                    if (state[4] >= todo.size()) {
                        state[0] = 2;
                    }
                    return;
                }
                if (flags[1]) {
                    return; // le contrôle d'étanchéité tourne encore en arrière-plan
                }
                // dernière étape : hologrammes, créature, points, mémoire du plan posé
                int spawned = 0;
                if (work == Work.CLEAR) {
                    removeEntities(world, List.of());
                    try {
                        PlanMemory.delete(world);
                    } catch (IOException e) {
                        say(sender, "Mémoire du dernier plan posé non effacée : " + e.getMessage(), RED);
                    }
                } else {
                    if (everywhere) {
                        removeEntities(world, List.of());
                        spawned = spawnEntities(world, plan, ox, oy, oz);
                        savePoints(world, plan, ox, oy, oz, true);
                        applySpawnAndWarps(world, plan, ox, oy, oz);
                        memory[0] = PlanMemory.of(plan);
                    } else if (work == Work.ZONES) {
                        removeEntities(world, zones);
                        spawned = spawnEntities(world, plan, ox, oy, oz);
                        savePoints(world, plan, ox, oy, oz, false);
                        for (Map.Entry<StationPlan.Pos, BlockData> e : plan.blocks().entrySet()) {
                            memory[0].put(e.getKey(), e.getValue());
                        }
                    } else {
                        // un endroit remis comme sur le plan : la mémoire y suit le plan
                        memory[0].follow(plan, p -> area.has(ox + p.x(), oy + p.y(), oz + p.z()));
                    }
                    // un monde sans mémoire dont on n'a remis qu'un endroit reste sans mémoire : rien n'est inventé
                    if (everywhere || !flags[0]) {
                        saveMemory(sender, world, memory[0], ox, oy, oz);
                    }
                }
                finish(world, chunks);
                long seconds = Math.max(1, (System.currentTimeMillis() - started) / 1000);
                if (work == Work.CLEAR) {
                    say(sender, "Emprise vidée : " + state[2] + " blocs retirés.", GOLD);
                    return;
                }
                if (area != null) {
                    say(sender, state[1] + state[2] == 0 ? "Rien à remettre : " + where + ", le monde est déjà comme sur le plan."
                            : "Remis comme sur le plan " + where + " : " + state[1] + " blocs posés, " + state[2] + " retirés"
                            + (flags[0] ? "." : ", dont " + facts[1] + " retouches faites à la main."), GOLD);
                } else {
                    say(sender, "La Station est prête : " + state[1] + " blocs posés, " + state[2] + " retirés, "
                            + spawned + " hologrammes, créatures, anneaux et pets sur " + (plan.texts().size() + plan.creatures().size() + plan.rings().size() + plan.models().size())
                            + ", en " + seconds + " s. /station tp pour la visiter.", GOLD);
                }
                if (work == Work.RESTORE && area == null && facts[1] > 0 && !flags[0]) {
                    say(sender, "Tout est remis comme sur le plan : " + facts[1] + " retouches faites à la main ont été remplacées.", GOLD);
                }
                if (!kept.isEmpty()) {
                    if (flags[0]) {
                        say(sender, "Ce monde n'avait pas de mémoire du dernier plan posé : impossible de savoir si les " + kept.size()
                                + " blocs qui diffèrent du plan sont des retouches faites à la main ou du plan pas encore posé."
                                + " Ils sont gardés tels quels. Pour y poser le plan : /station remettre <rayon> sur place,"
                                + " ou /station remettre tout.", GOLD);
                    } else {
                        say(sender, "Retouches faites à la main gardées telles quelles : " + kept.size()
                                + (facts[0] > 0 ? ", dont " + facts[0] + " à un endroit où le plan a changé (le plan n'y est pas posé)" : "")
                                + ". /station remettre <rayon> remet un endroit comme sur le plan.", GOLD);
                    }
                }
                if (everywhere) {
                    File list = writeGaps(sender, world, kept, List.of(), flags[0]);
                    if (!kept.isEmpty()) {
                        say(sender, "Liste : " + list.getPath(), ICE);
                    }
                    if (seal[0] != null) {
                        announceSeal(sender, seal[0], kept.isEmpty() ? "" : " (retouches comprises)");
                        if (!seal[0].sealed() && !kept.isEmpty()) {
                            say(sender, "Si l'ouverture vient d'un bloc retiré ou changé à la main, /station remettre <rayon> sur place la referme.", RED);
                        }
                    }
                }
                // une retouche de la station en place : on dit ce qui a bougé (inutile pour une première pose)
                if (state[1] + state[2] > 0 && state[1] < solid / 2) {
                    announceChanges(sender, "Ce qui a changé :", changes);
                }
            } catch (RuntimeException e) {
                finish(world, chunks);
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Génération de la station interrompue", e);
                say(sender, "Génération interrompue : " + e, RED);
            }
        }, 1L, 1L);
    }

    private void saveMemory(CommandSender sender, World world, PlanMemory memory, int ox, int oy, int oz) {
        try {
            memory.save(world, ox, oy, oz);
        } catch (IOException e) {
            plugin.getLogger().warning("Mémoire de la station non enregistrée : " + e);
            say(sender, "Mémoire du dernier plan posé non enregistrée (" + e.getMessage()
                    + ") : la prochaine génération risque de mal reconnaître les retouches.", RED);
        }
    }

    private static void note(Map<String, int[]> changes, Material before, Material after, int x, int y, int z) {
        String a = before.name().toLowerCase(Locale.ROOT), b = after.name().toLowerCase(Locale.ROOT);
        changes.computeIfAbsent(before == after ? b + " (réglage du bloc)" : a + " → " + b, k -> new int[]{0, x, y, z})[0]++;
    }

    /** Liste des changements par sorte, les plus nombreux d'abord : dix lignes en jeu, quarante dans la console. */
    private void announceChanges(CommandSender sender, String title, Map<String, int[]> changes) {
        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(changes.entrySet());
        sorted.sort((x, y) -> Integer.compare(y.getValue()[0], x.getValue()[0]));
        say(sender, title, ICE);
        int shown = 0, rest = 0, hidden = 0;
        for (Map.Entry<String, int[]> e : sorted) {
            int[] c = e.getValue();
            String line = "  " + c[0] + " × " + e.getKey() + ", vers " + c[1] + " " + c[2] + " " + c[3];
            if (shown < 10) {
                say(sender, line, ICE);
            } else {
                rest += c[0];
                if (shown < 40) {
                    plugin.getLogger().info(line);
                } else {
                    hidden++;
                }
            }
            shown++;
        }
        if (hidden > 0) {
            plugin.getLogger().info("  ... et " + hidden + " autres sortes.");
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
        for (StationPlan.Ring r : plan.rings()) {
            if (plugin.anneaux().spawnDisc(world, ox + r.x(), oy + r.y(), oz + r.z(), r.nx(), r.nz(), r.r(), r.server(),
                    List.of(TAG, TAG + "_" + r.zone())).isValid()) {
                count++;
            }
        }
        for (StationPlan.Model m : plan.models()) {
            if (plugin.anneaux().spawnModel(world, ox + m.x(), oy + m.y(), oz + m.z(),
                    new org.bukkit.NamespacedKey("orbitdev", m.model()), m.scale(), List.of(TAG, TAG + "_" + m.zone())).isValid()) {
                count++;
            }
        }
        plugin.anneaux().rescan();
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
            icons.put("tycoon", Material.GOLD_INGOT);
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
