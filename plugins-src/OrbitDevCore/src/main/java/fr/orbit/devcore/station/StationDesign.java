package fr.orbit.devcore.station;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dessin de « La Station » : calcule tous les blocs à partir des réglages de station.yml.
 * Aucun accès au monde ici, le calcul est pur ; la pose est faite par tranches dans {@link StationService}.
 *
 * Repères : x vers l'est, z vers le sud, y = 0 au niveau du sol. Les ailes sont dessinées dans un repère
 * local (u = dans l'axe de la passerelle en s'éloignant de l'atrium, v = vers la droite) puis tournées.
 */
public final class StationDesign {

    public static final List<String> ZONES = List.of("atrium", "serre", "sas", "arene", "portail", "reliques", "passerelles");

    public static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3;
    private static final int[][] FWD = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final int[][] RIGHT = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    private static final int[][] N4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static final int ICE = 0x62CFF4, GOLD = 0xE0B864, WARM = 0xE6B35C, RED = 0xE0484A, VIOLET = 0xC79BFF, ALERT = 0xF2913D;
    /** Hauteur intérieure des passerelles. */
    private static final int CH = 7;
    /** Hauteur du mur d'enceinte de l'arène : la verrière part de là. */
    private static final int ARENA_WALL = 9;

    private final ConfigurationSection cfg;
    private final StationPlan plan = new StationPlan();
    private final Map<Object, BlockData> dataCache = new HashMap<>();
    private String zone = "atrium";
    private double longestJump;

    private final int a, wallH, domeH, len, hw, wr, pkR, pkSteps, pkRise, obD, obH, centerD;
    private final double glassShare;

    public StationDesign(ConfigurationSection cfg) {
        this.cfg = cfg;
        this.a = clamp(cfg.getInt("atrium.rayon", 24), 16, 48);
        this.wallH = clamp(cfg.getInt("atrium.hauteur-murs", 14), 10, 30);
        this.domeH = clamp(cfg.getInt("atrium.hauteur-dome", 16), 8, 40);
        this.len = clamp(cfg.getInt("passerelle.longueur", 20), 6, 60);
        this.hw = clamp((cfg.getInt("passerelle.largeur", 9) - 1) / 2, 2, 6);
        this.wr = clamp(cfg.getInt("ailes.rayon", 22), 18, 40);
        this.glassShare = Math.max(0, Math.min(1, cfg.getDouble("verre", 0.6)));
        this.pkR = clamp(cfg.getInt("parcours.rayon", 9), 7, a - 8);
        this.pkSteps = clamp(cfg.getInt("parcours.marches", 32), 8, 80);
        this.pkRise = clamp(cfg.getInt("parcours.hauteur", 21), 6, wallH + domeH - 8);
        this.obD = clamp(cfg.getInt("obelisques.distance", 62), 40, 120);
        this.obH = clamp(cfg.getInt("obelisques.hauteur", 30), 10, 60);
        this.centerD = a + len + wr;

        // États de blocs décrits par texte : créés ici, sur le thread du serveur, car draw() tourne ensuite
        // en arrière-plan. Tout nouvel état écrit en texte dans le dessin doit être ajouté à cette liste.
        for (String f : List.of("north", "east", "south", "west")) {
            data("minecraft:barrel[facing=" + f + "]");
            data("minecraft:redstone_wall_torch[facing=" + f + "]");
        }
        data("minecraft:iron_bars[east=true,west=true]");
        data("minecraft:iron_bars[west=true,east=true]");
        data("minecraft:iron_bars[north=true,south=true]");
        data("minecraft:iron_bars[south=true,north=true]");
        data("minecraft:end_rod[facing=down]");
        data("minecraft:azalea_leaves[persistent=true]");
        data("minecraft:flowering_azalea_leaves[persistent=true]");
        data("minecraft:white_concrete_stairs[facing=west,half=top]");
        data("minecraft:white_concrete_stairs[facing=east,half=top]");
        data("minecraft:white_concrete_slab[type=top]");
        data("minecraft:candle");
        data("minecraft:cyan_candle");
        for (int level = 0; level <= 15; level++) {
            data("minecraft:light[level=" + level + "]");
        }
    }

    /** Dessine les zones demandées (toutes si la liste est vide) et renvoie le plan. */
    public StationPlan draw(List<String> zones) {
        boolean all = zones.isEmpty();
        // Une aile redessinée seule reboucherait son entrée : les passerelles, dessinées en dernier,
        // sont alors redessinées aussi pour rouvrir le passage.
        boolean corridors = all || zones.stream().anyMatch(z -> !z.equals("reliques"));
        for (String name : ZONES) {
            if (!all && !zones.contains(name) && !(corridors && name.equals("passerelles"))) {
                continue;
            }
            zone = name;
            switch (name) {
                case "atrium" -> atrium();
                case "serre" -> serre();
                case "sas" -> sas();
                case "arene" -> arene();
                case "portail" -> portail();
                case "reliques" -> reliques();
                case "passerelles" -> passerelles();
                default -> { }
            }
        }
        // Sous un bloc plein, l'herbe meurt toute seule en jeu : le plan y met de la terre,
        // sinon le monde s'écarterait du plan dès qu'un joueur passe.
        List<StationPlan.Pos> shaded = new ArrayList<>();
        for (Map.Entry<StationPlan.Pos, BlockData> e : plan.blocks().entrySet()) {
            if (e.getValue().getMaterial() == Material.GRASS_BLOCK) {
                StationPlan.Pos p = e.getKey();
                BlockData above = plan.blocks().get(new StationPlan.Pos(p.x(), p.y() + 1, p.z()));
                if (above != null && above.getMaterial().isOccluding()) {
                    shaded.add(p);
                }
            }
        }
        for (StationPlan.Pos p : shaded) {
            plan.set(p.x(), p.y(), p.z(), data(Material.DIRT));
        }
        return plan;
    }

    /** Demi-côté de l'emprise de la station (le dôme de l'île du MMO est ce qui dépasse le plus). */
    public int reach() {
        return Math.max(centerD + islandR() + 3, obD + 3);
    }

    public int minY() {
        return -24;
    }

    public int maxY() {
        int domes = Math.max(islandH(), ARENA_WALL + arenaH());
        return Math.max(Math.max(wallH + domeH, domes) + 3, obH + 4);
    }

    /** Rayon et hauteur du dôme de confinement de l'île : il englobe l'île et son portail. */
    private int islandR() {
        return wr + 4;
    }

    private int islandH() {
        return Math.max(22, wr + 4);
    }

    /** Hauteur de la verrière de l'arène, au-dessus du mur d'enceinte. */
    private int arenaH() {
        return Math.max(11, (int) Math.round(wr * 0.64));
    }

    // ------------------------------------------------------------------ atrium

    private void atrium() {
        Material floor = pal("sol", Material.WHITE_CONCRETE);
        Material ring = pal("anneaux", Material.LIGHT_BLUE_CONCRETE);
        Material trim = pal("bordure", Material.LIGHT_GRAY_CONCRETE);
        Material hull = pal("coque", Material.SMOOTH_QUARTZ);
        Material accent = pal("accent", Material.LIGHT_BLUE_CONCRETE);
        Material gold = pal("or", Material.GOLD_BLOCK);
        Material glass = pal("verre", Material.GLASS);
        Material tinted = pal("verre-teinte", Material.LIGHT_BLUE_STAINED_GLASS);
        Material rib = pal("nervures", Material.IRON_BLOCK);
        Material light = pal("lumiere", Material.SEA_LANTERN);
        double rc = a * 1.0824;
        int period = 2 + (int) Math.round(glassShare * 6);

        for (int x = -a; x <= a; x++) {
            for (int z = -a; z <= a; z++) {
                if (!inOct(x, z, a)) {
                    continue;
                }
                boolean edge = !inOct(x, z, a - 1);
                double r = Math.hypot(x, z);
                Material f = floor;
                if (edge || !inOct(x, z, a - 2)) {
                    f = trim;
                } else if (Math.abs(r - (pkR - 2)) < 0.5 || Math.abs(r - (a - 7)) < 0.5) {
                    f = ring;
                }
                set(x, 0, z, f);
                set(x, -1, z, trim);
                if (inOct(x, z, a - 3)) {
                    set(x, -2, z, trim);
                }
                if (inOct(x, z, a - 7)) {
                    set(x, -3, z, hull);
                }
                if (inOct(x, z, a - 12)) {
                    set(x, -4, z, Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0 ? light : trim);
                }
                if (edge) {
                    boolean door = (Math.abs(x) <= hw && Math.abs(z) >= a - 1) || (Math.abs(z) <= hw && Math.abs(x) >= a - 1);
                    int along = Math.abs(x) >= Math.abs(z) ? z : x;
                    for (int y = 1; y <= wallH; y++) {
                        Material m;
                        if (door && y <= CH) {
                            m = Material.AIR;
                        } else if (door && y == CH + 1) {
                            m = gold;
                        } else if (y == 1) {
                            m = trim;
                        } else if (y == 2 || y == wallH - 1) {
                            m = accent;
                        } else if (y == wallH) {
                            m = gold;
                        } else if (y >= 4 && y <= wallH - 2 && glassShare > 0.05 && Math.floorMod(along, period) != 0) {
                            m = glass;
                        } else {
                            m = hull;
                        }
                        set(x, y, z, m);
                    }
                }
                // dôme
                int top = wallH + cap(r, rc, domeH);
                int low = top;
                for (int[] n : N4) {
                    int nx = x + n[0], nz = z + n[1];
                    int nt = inOct(nx, nz, a) ? wallH + cap(Math.hypot(nx, nz), rc, domeH) : wallH;
                    low = Math.min(low, nt + 1);
                }
                low = edge ? wallH + 1 : Math.max(low, wallH + 1);
                for (int y = Math.min(low, top); y <= top; y++) {
                    if (y <= wallH) {
                        continue;
                    }
                    Material m;
                    if (r < 2.5) {
                        m = light;
                    } else if (x == 0 || z == 0 || Math.abs(x) == Math.abs(z)
                            || (glassShare < 0.4 && (Math.floorMod(x, 6) == 0 || Math.floorMod(z, 6) == 0))) {
                        m = rib;
                    } else {
                        m = r > rc * 0.75 ? tinted : glass;
                    }
                    set(x, y, z, m);
                }
            }
        }

        // noyau d'énergie
        int coreTop = wallH + domeH - 1;
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                double r = Math.hypot(x, z);
                if (r > 3.2 && r <= 5.2) {
                    set(x, 1, z, Material.CHISELED_QUARTZ_BLOCK);
                }
                if (r > 3.2) {
                    continue;
                }
                for (int y = 1; y <= coreTop; y++) {
                    set(x, y, z, r <= 1.5 ? (y % 4 == 0 ? gold : light) : (y % 8 == 0 ? gold : tinted));
                }
            }
        }
        holo(0.5, 8.2, 5.5, label("ORBIT MC", ICE, true), 3.2f);
        holo(0.5, 6.6, 5.5, label("LA STATION", GOLD, true), 1.4f);

        // rose des vents sous le spawn
        int sz = spawnZ();
        for (int d = -3; d <= 3; d++) {
            set(d, 0, sz, gold);
            set(0, 0, sz + d, gold);
        }
        set(0, 0, sz, Material.CHISELED_QUARTZ_BLOCK);
        plan.point("spawn", 0.5, 1, sz + 0.5, 180f);
        plan.point("pnj.guide", 4.5, 1, sz + 0.5, 90f);

        // parcours d'attente : spirale autour du noyau
        double th = Math.PI / 2;
        int px = 0, pz = pkR;
        set(px, 1, pz, ring);
        int lastY = 1;
        for (int i = 1; i < pkSteps; i++) {
            int x, z;
            do {
                th += 0.02;
                x = (int) Math.round(pkR * Math.cos(th));
                z = (int) Math.round(pkR * Math.sin(th));
            } while (Math.hypot(x - px, z - pz) < 2.9);
            longestJump = Math.max(longestJump, Math.hypot(x - px, z - pz));
            lastY = 1 + (int) Math.round(i * pkRise / (pkSteps - 1.0));
            set(x, lastY, z, i % 8 == 7 ? gold : ring);
            px = x;
            pz = z;
        }
        // belvédère : anneau de verre juste au-dessus de la dernière marche
        int by = lastY + 1;
        for (int x = -pkR - 6; x <= pkR + 6; x++) {
            for (int z = -pkR - 6; z <= pkR + 6; z++) {
                double r = Math.hypot(x, z);
                if (r < pkR + 1.5 || r > pkR + 5.2) {
                    continue;
                }
                set(x, by, z, r > pkR + 4.4 ? gold : tinted);
                if (r > pkR + 4.4) {
                    set(x, by + 1, z, glass);
                }
            }
        }
        double end = th + Math.PI;
        int ex = (int) Math.round((pkR + 3) * Math.cos(end)), ez = (int) Math.round((pkR + 3) * Math.sin(end));
        set(ex, by, ez, gold);
        set(ex, by + 1, ez, Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        holo(ex + 0.5, by + 3.2, ez + 0.5, label("LE BELVÉDÈRE", GOLD, true), 1.2f);
        plan.point("parcours.depart", 0.5, 1, pkR + 2.5, 180f);
        plan.point("parcours.arrivee", ex + 0.5, by + 1, ez + 0.5, 0f);

        // panneaux des passerelles
        String[] names = {"SURVIE", "EXTRACTION", "PRACTICE", "MMO"};
        int[] colors = {WARM, RED, ICE, VIOLET};
        for (int dir = 0; dir < 4; dir++) {
            holo(wx(dir, a - 3, 0) + 0.5, CH + 2.4, wz(dir, a - 3, 0) + 0.5, label(names[dir], colors[dir], true), 2.0f);
        }

        // comptoir (sud-est) : un bar en escaliers et dalles retournés entre deux blocs d'or, bougies posées dessus.
        // Forme reprise telle quelle des retouches faites à la main dans la station le 22/11.
        int c = a / 2;
        for (int x = c - 2; x <= c + 3; x++) {
            boolean tip = x == c - 2 || x == c + 3;
            if (tip) {
                set(x, 1, c - 1, gold);
            } else {
                plan.set(x, 1, c - 1, data(x == c - 1 ? "minecraft:white_concrete_stairs[facing=west,half=top]"
                        : x == c + 2 ? "minecraft:white_concrete_stairs[facing=east,half=top]" : "minecraft:white_concrete_slab[type=top]"));
            }
            for (int y = 1; y <= 4; y++) {
                set(x, y, c + 3, y == 4 ? gold : hull);
            }
        }
        plan.set(c - 2, 2, c - 1, data("minecraft:cyan_candle"));
        plan.set(c - 1, 2, c - 1, data("minecraft:candle"));
        plan.set(c + 2, 2, c - 1, data("minecraft:cyan_candle"));
        holo(c + 1.0, 5.6, c + 3.5, label("LE COMPTOIR", GOLD, true), 1.3f);
        plan.point("pnj.comptoir", c + 1.0, 1, c + 1.5, 180f);

        // écran du réseau (sud-ouest)
        for (int x = -c - 4; x <= -c + 4; x++) {
            for (int y = 1; y <= 6; y++) {
                boolean frame = x == -c - 4 || x == -c + 4 || y == 1 || y == 6;
                set(x, y, c + 3, frame ? rib : Material.BLACK_CONCRETE);
            }
        }
        plan.text(zone, -c + 0.5, 4.3, c + 2.8, 180f, label("RÉSEAU ORBIT MC", ICE, true), 1.1f);
        plan.text(zone, -c + 0.5, 3.2, c + 2.8, 180f, label("compteurs à venir", GOLD, false), 0.9f);
        plan.point("ecran.reseau", -c + 0.5, 3.2, c + 2.8, 180f);

        // éclairage d'ambiance : lumières invisibles à hauteur de tête
        for (int x = -a; x <= a; x += 4) {
            for (int z = -a; z <= a; z += 4) {
                if (inOct(x, z, a - 2)) {
                    light(x, 2, z, 15);
                    light(x, 9, z, 15);
                }
            }
        }
    }

    /** Plus long saut du parcours (distance entre les centres de deux marches), pour vérifier qu'il reste faisable. */
    public double longestJump() {
        return longestJump;
    }

    private int spawnZ() {
        return pkR + 4;
    }

    // ------------------------------------------------------------------ passerelles

    private void passerelles() {
        Material floor = pal("sol", Material.WHITE_CONCRETE);
        Material trim = pal("bordure", Material.LIGHT_GRAY_CONCRETE);
        Material hull = pal("coque", Material.SMOOTH_QUARTZ);
        Material accent = pal("accent", Material.LIGHT_BLUE_CONCRETE);
        Material glass = pal("verre", Material.GLASS);
        Material rib = pal("nervures", Material.IRON_BLOCK);
        Material light = pal("lumiere", Material.SEA_LANTERN);
        for (int dir = 0; dir < 4; dir++) {
            // chaque passerelle traverse la paroi de son aile et s'arrête trois blocs plus loin
            int end = centerD - (dir == EAST ? wr - 4 : dir == WEST ? islandR() : wr) + 3;
            for (int u = a + 1; u <= end; u++) {
                boolean ribRow = Math.floorMod(u, 4) == 0;
                for (int v = -hw - 1; v <= hw + 1; v++) {
                    int x = wx(dir, u, v), z = wz(dir, u, v);
                    boolean wall = Math.abs(v) == hw + 1;
                    Material f = wall || Math.abs(v) == hw ? trim : v == 0 ? (Math.floorMod(u, 5) == 0 ? light : accent) : floor;
                    set(x, 0, z, f);
                    set(x, -1, z, trim);
                    for (int y = 1; y <= CH; y++) {
                        Material m = Material.AIR;
                        if (wall) {
                            m = y == 1 ? trim : y == CH ? accent : ribRow ? rib : glassShare > 0.05 ? glass : hull;
                        }
                        set(x, y, z, m);
                    }
                    Material roof = ribRow ? rib : Math.abs(v) <= hw - 2 + (glassShare > 0.8 ? 2 : 0) && glassShare > 0.05 ? glass : hull;
                    set(x, CH + 1, z, roof);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Survie : la serre

    private void serre() {
        int dir = NORTH;
        int R = wr, H = wr - 2;
        Material glass = pal("verre", Material.GLASS);
        Material frame = pal("or", Material.GOLD_BLOCK);
        Material base = pal("socle", Material.WAXED_COPPER_BLOCK);
        Material warm = pal("lumiere", Material.OCHRE_FROGLIGHT);
        Material under = pal("dessous", Material.LIGHT_GRAY_CONCRETE);
        int ribs = glassShare >= 0.6 ? 8 : 12;
        double step = 2 * Math.PI / ribs;
        int gateU = R - 5;

        Set<Long> water = new HashSet<>();
        for (int u = -R + 3; u <= R - 3; u++) {
            int v0 = (int) Math.round(7 * Math.sin(u / 4.0));
            for (int v = v0; v <= v0 + 1; v++) {
                if (Math.abs(v) > 1 && Math.hypot(u, v) < R - 3) {
                    water.add(key(u, v));
                }
            }
        }

        for (int u = -R; u <= R; u++) {
            for (int v = -R; v <= R; v++) {
                double r = Math.hypot(u, v);
                if (r > R + 0.5) {
                    continue;
                }
                boolean rim = r > R - 1.0;
                boolean path = Math.abs(v) <= 1;
                Material f = rim ? base : path ? Material.DIRT_PATH : water.contains(key(u, v)) ? Material.WATER
                        : hash(u, v, 5) < 0.08 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK;
                wset(dir, u, 0, v, f);
                wset(dir, u, -1, v, rim ? base : Material.DIRT);
                wset(dir, u, -2, v, under);
                if (r <= R - 5) {
                    wset(dir, u, -3, v, under);
                }
                if (r <= R - 11) {
                    wset(dir, u, -4, v, hash(u, v, 6) < 0.06 ? warm : under);
                }
                // dôme
                int top = cap(r, R, H);
                int low = top;
                for (int[] n : N4) {
                    double nr = Math.hypot(u + n[0], v + n[1]);
                    low = Math.min(low, (nr > R + 0.5 ? 0 : cap(nr, R, H)) + 1);
                }
                double ang = Math.atan2(v, u);
                double off = r * Math.abs(Math.sin(ang - Math.round(ang / step) * step));
                for (int y = Math.max(1, Math.min(low, top)); y <= top; y++) {
                    Material m = r < 1.5 ? warm : (off < 0.6 || y % 6 == 0) ? frame : glass;
                    wset(dir, u, y, v, m);
                }
                // fleurs et herbes
                boolean clear = (Math.abs(v) <= hw + 1 && (u < -R + 6 || u > gateU - 6));
                if (f == Material.GRASS_BLOCK && !clear && r < R - 2) {
                    double h = hash(u, v, 7);
                    if (h < 0.22) {
                        Material[] plants = {Material.SHORT_GRASS, Material.SHORT_GRASS, Material.SHORT_GRASS, Material.POPPY,
                                Material.DANDELION, Material.CORNFLOWER, Material.ALLIUM, Material.OXEYE_DAISY};
                        wset(dir, u, 1, v, plants[(int) (h / 0.22 * plants.length) % plants.length]);
                    }
                }
            }
        }

        // lanternes le long du chemin
        for (int u = -R + 6; u <= gateU - 5; u += 6) {
            for (int v : new int[]{-2, 2}) {
                wset(dir, u, 0, v, Material.GRASS_BLOCK);
                wset(dir, u, 1, v, Material.OAK_FENCE);
                wset(dir, u, 2, v, Material.OAK_FENCE);
                wset(dir, u, 3, v, Material.LANTERN);
            }
        }
        // arbres
        double k = R / 22.0;
        int[][] trees = {{-8, -10}, {3, 11}, {9, -9}, {-12, 8}, {12, 9}, {-2, -14}};
        for (int[] t : trees) {
            tree(dir, (int) Math.round(t[0] * k), (int) Math.round(t[1] * k));
        }
        // coin potager
        int hu = (int) Math.round(-5 * k), hv = (int) Math.round(14 * k);
        wset(dir, hu, 1, hv, Material.HAY_BLOCK);
        wset(dir, hu + 1, 1, hv, Material.HAY_BLOCK);
        wset(dir, hu, 2, hv, Material.HAY_BLOCK);
        wset(dir, hu + 2, 1, hv + 1, Material.COMPOSTER);
        wset(dir, hu - 1, 1, hv + 1, Material.BARREL);

        for (int u = -R; u <= R; u += 4) {
            for (int v = -R; v <= R; v += 4) {
                if (Math.hypot(u, v) < R - 2) {
                    wlight(dir, u, 2, v, 14);
                    wlight(dir, u, 8, v, 14);
                }
            }
        }
        gate(dir, "survie", "SURVIE", WARM, gateU, 10, false, base, Material.OAK_LOG, pal("rideau", Material.ORANGE_STAINED_GLASS), warm);
    }

    private void tree(int dir, int u, int v) {
        wset(dir, u, 0, v, Material.DIRT);
        BlockData leaves = data("minecraft:azalea_leaves[persistent=true]");
        BlockData flowering = data("minecraft:flowering_azalea_leaves[persistent=true]");
        int h = 4 + (int) (hash(u, v, 11) * 3);
        for (int du = -3; du <= 3; du++) {
            for (int dv = -3; dv <= 3; dv++) {
                for (int dy = -2; dy <= 3; dy++) {
                    if (du * du + dv * dv + dy * dy * 1.6 <= 7.5) {
                        plan.set(wx(dir, centerD + u + du, v + dv), h + 1 + dy, wz(dir, centerD + u + du, v + dv),
                                hash(u + du, v + dv, dy) < 0.25 ? flowering : leaves);
                    }
                }
            }
        }
        for (int y = 1; y <= h + 1; y++) {
            wset(dir, u, y, v, Material.OAK_LOG);
        }
    }

    // ------------------------------------------------------------------ Extraction : le sas

    private void sas() {
        int dir = EAST;
        int L = wr - 4, W = wr - 7, top = 11;
        Material wallM = pal("coque", Material.POLISHED_BLACKSTONE);
        Material brick = pal("mur-interieur", Material.POLISHED_BLACKSTONE_BRICKS);
        Material floor = pal("sol", Material.GRAY_CONCRETE);
        Material metal = pal("metal", Material.IRON_BLOCK);
        Material neon = pal("verre", Material.RED_STAINED_GLASS);
        Material light = pal("lumiere", Material.SEA_LANTERN);
        Material yellow = Material.YELLOW_CONCRETE, black = Material.BLACK_CONCRETE;
        int inner = -L + 8;
        int gateU = L - 1;

        for (int u = -L; u <= L; u++) {
            for (int v = -W; v <= W; v++) {
                boolean wall = Math.abs(u) == L || Math.abs(v) == W;
                boolean chamber = u < inner && Math.abs(v) <= hw;
                boolean dock = u >= gateU - 4 && Math.abs(v) <= hw;
                Material f = chamber || dock ? (Math.floorMod(u + v, 4) < 2 ? yellow : black)
                        : (Math.floorMod(u, 6) == 0 || Math.floorMod(v, 6) == 0) ? wallM : floor;
                wset(dir, u, 0, v, f);
                wset(dir, u, -1, v, floor);
                if (Math.abs(u) < L - 2 && Math.abs(v) < W - 2) {
                    wset(dir, u, -2, v, floor);
                }
                boolean strip = !wall && (v == 0 || Math.abs(v) == W / 2);
                wset(dir, u, top, v, strip ? light : wallM);
                if (wall) {
                    int along = Math.abs(v) == W ? u : v;
                    for (int y = 1; y < top; y++) {
                        wset(dir, u, y, v, y == 1 ? brick : (y == 5 || y == 6) && Math.floorMod(along, 6) == 3 ? neon : wallM);
                    }
                } else if (strip) {
                    wset(dir, u, top - 1, v, neon);
                }
            }
        }
        // sas de décontamination
        for (int v = -W + 1; v <= W - 1; v++) {
            for (int y = 1; y < top; y++) {
                boolean open = Math.abs(v) <= 2 && y <= 4;
                boolean frame = (Math.abs(v) == 3 && y <= 5) || (y == 5 && Math.abs(v) <= 3);
                wset(dir, inner, y, v, open ? Material.AIR : frame ? metal : brick);
            }
        }
        BlockData barsData = data("minecraft:iron_bars[" + face(dir, 1, 0) + "=true," + face(dir, -1, 0) + "=true]");
        for (int u = -L + 1; u < inner; u++) {
            for (int side : new int[]{-1, 1}) {
                for (int y = 1; y < top; y++) {
                    if (y >= 2 && y <= 4 && Math.floorMod(u, 2) == 0) {
                        plan.set(wx(dir, centerD + u, side * (hw + 1)), y, wz(dir, centerD + u, side * (hw + 1)), barsData);
                    } else {
                        wset(dir, u, y, side * (hw + 1), metal);
                    }
                }
            }
        }
        BlockData rodDown = data("minecraft:end_rod[facing=down]");
        for (int u = -L + 2; u < inner; u += 3) {
            for (int v : new int[]{-2, 2}) {
                plan.set(wx(dir, centerD + u, v), top - 1, wz(dir, centerD + u, v), rodDown);
            }
        }
        // casiers le long des murs
        for (int side : new int[]{-1, 1}) {
            BlockData barrel = data("minecraft:barrel[facing=" + face(dir, 0, -side) + "]");
            BlockData torch = data("minecraft:redstone_wall_torch[facing=" + face(dir, 0, -side) + "]");
            int v = side * (W - 1);
            for (int u = inner + 2; u <= L - 3; u++) {
                boolean sep = Math.floorMod(u, 4) == 0;
                for (int y = 1; y <= 3; y++) {
                    if (sep) {
                        wset(dir, u, y, v, brick);
                    } else {
                        plan.set(wx(dir, centerD + u, v), y, wz(dir, centerD + u, v), barrel);
                    }
                }
                wset(dir, u, 4, v, metal);
                if (sep) {
                    plan.set(wx(dir, centerD + u, v), 6, wz(dir, centerD + u, v), torch);
                }
            }
            wset(dir, L - 1, top - 1, v, Material.COBWEB);
            wset(dir, inner + 1, top - 1, v, Material.COBWEB);
            wset(dir, L - 1, 1, v, Material.COBWEB);
        }
        // établis
        Material[] tops = {Material.SMITHING_TABLE, Material.FLETCHING_TABLE, Material.CRAFTING_TABLE, Material.CARTOGRAPHY_TABLE};
        int n = 0;
        for (int u0 : new int[]{inner + 5, inner + 13}) {
            for (int side : new int[]{-1, 1}) {
                for (int u = u0; u <= u0 + 3; u++) {
                    int v = side * (W / 2 + 1);
                    wset(dir, u, 1, v, brick);
                    if (u == u0 + 1) {
                        wset(dir, u, 2, v, tops[n++ % tops.length]);
                    }
                }
            }
        }
        // panneaux peints sur les murs : vus en arrivant par le sas, puis dans la salle des casiers
        wsign(dir, inner - 0.7, 7.2, 0, -1, 0, label("⚠ QUARANTAINE ⚠", RED, true), 1.5f);
        wsign(dir, inner - 0.7, 6.3, 0, -1, 0, label("ZONE CONTAMINÉE · ACCÈS RESTREINT", ALERT, false), 0.7f);
        wsign(dir, inner - 2.5, 6.2, -(hw + 0.3), 0, 1, label("DÉCONTAMINATION", ALERT, true), 0.8f);
        wsign(dir, inner - 2.5, 6.2, hw + 0.3, 0, -1, label("DÉCONTAMINATION", ALERT, true), 0.8f);
        wsign(dir, 2, 7.4, -(W - 0.7), 0, 1, label("NE RIEN RAMENER DE LA ZONE", RED, true), 0.9f);
        wsign(dir, 2, 7.4, W - 0.7, 0, -1, label("JOUR 1 · AUCUN SURVIVANT SIGNALÉ", RED, true), 0.9f);

        // lumière basse dans la salle, vive dans le sas de décontamination
        for (int u = -L + 2; u <= L - 2; u += 5) {
            for (int v = -W + 3; v <= W - 3; v += 6) {
                wlight(dir, u, 3, v, 12);
            }
        }
        for (int u = -L + 2; u < inner; u += 3) {
            wlight(dir, u, 3, 0, 15);
        }

        gate(dir, "extraction", "EXTRACTION", RED, gateU, 8, true, metal, brick, pal("rideau", Material.RED_STAINED_GLASS), Material.REDSTONE_BLOCK);
    }

    // ------------------------------------------------------------------ Practice : l'arène

    private void arene() {
        int dir = SOUTH;
        int R = wr;
        double inner = R * 13 / 22.0;
        double standEnd = R - 2;
        Material dark = pal("sol", Material.BLACK_CONCRETE);
        Material grid = pal("verre", Material.CYAN_STAINED_GLASS);
        Material light = pal("lumiere", Material.SEA_LANTERN);
        Material accent = pal("accent", Material.LIGHT_BLUE_CONCRETE);
        Material stand = pal("gradins", Material.POLISHED_DEEPSLATE);
        Material band = pal("verre-teinte", Material.TINTED_GLASS);
        Material under = pal("dessous", Material.LIGHT_GRAY_CONCRETE);
        Material domeGlass = pal("dome", Material.GLASS);
        int gateU = R - 3;
        int DH = arenaH();
        double step = Math.PI / 4;

        for (int u = -R; u <= R; u++) {
            for (int v = -R; v <= R; v++) {
                double r = Math.hypot(u, v);
                if (r > R + 0.5) {
                    continue;
                }
                boolean gang = Math.abs(v) <= hw && r > inner + 0.5;
                boolean line = Math.floorMod(u, 4) == 0 || Math.floorMod(v, 4) == 0;
                Material f;
                if (r <= inner + 0.5) {
                    f = line ? grid : dark;
                    wset(dir, u, -1, v, line ? light : under);
                } else {
                    f = r <= inner + 1.5 ? accent : stand;
                    wset(dir, u, -1, v, under);
                }
                wset(dir, u, 0, v, f);
                if (r <= R - 4) {
                    wset(dir, u, -2, v, under);
                }
                if (r > inner + 1.5 && r <= standEnd + 0.5 && !gang) {
                    int steps = 1 + (int) ((r - (inner + 1.5)) / 1.5);
                    for (int y = 1; y <= steps; y++) {
                        wset(dir, u, y, v, y == steps && steps % 2 == 0 ? dark : stand);
                    }
                }
                if (r > standEnd + 0.5) {
                    for (int y = 1; y <= ARENA_WALL; y++) {
                        wset(dir, u, y, v, y == ARENA_WALL ? accent : y >= 6 && glassShare > 0.05 ? band : dark);
                    }
                    // balises sur le haut du mur, côté intérieur (sous la verrière)
                    if (r <= standEnd + 1.5 && Math.floorMod((int) Math.round(Math.atan2(v, u) * R), 6) == 0) {
                        wset(dir, u, ARENA_WALL + 1, v, Material.END_ROD);
                    }
                }
                // anneau holographique
                if (Math.abs(r - inner * 0.75) < 0.5 && Math.floorMod(u + v, 2) == 0) {
                    wset(dir, u, 12, v, grid);
                }
                // verrière : elle part du haut du mur d'enceinte et ferme l'arène sur l'espace
                int top = ARENA_WALL + cap(r, R + 0.5, DH);
                int low = top;
                for (int[] n : N4) {
                    double nr = Math.hypot(u + n[0], v + n[1]);
                    low = Math.min(low, ARENA_WALL + (nr > R + 0.5 ? 0 : cap(nr, R + 0.5, DH)) + 1);
                }
                double ang = Math.atan2(v, u);
                double off = r * Math.abs(Math.sin(ang - Math.round(ang / step) * step));
                for (int y = Math.max(ARENA_WALL + 1, Math.min(low, top)); y <= top; y++) {
                    wset(dir, u, y, v, r < 1.5 ? light : off < 0.6 || (y - ARENA_WALL) % 5 == 0 ? grid : domeGlass);
                }
            }
        }
        // tableau des meilleurs Elo : un cube suspendu sous la verrière, lisible depuis tous les gradins
        int by = ARENA_WALL + DH - 8;
        for (int u = -3; u <= 3; u++) {
            for (int v = -3; v <= 3; v++) {
                boolean side = Math.abs(u) == 3 || Math.abs(v) == 3;
                boolean corner = Math.abs(u) == 3 && Math.abs(v) == 3;
                for (int y = by; y <= by + 4; y++) {
                    boolean lid = y == by || y == by + 4;
                    if (side || lid) {
                        wset(dir, u, y, v, corner || lid ? accent : dark);
                    }
                }
            }
        }
        for (int y = by + 5; y < ARENA_WALL + DH; y++) {
            wset(dir, 0, y, 0, accent);
        }
        for (int[] f : N4) {
            wsign(dir, f[0] * 3.7, by + 2.4, f[1] * 3.7, f[0], f[1], label("TOP ELO", ICE, true), 2.0f);
            wsign(dir, f[0] * 3.7, by + 1.3, f[1] * 3.7, f[0], f[1], label("classement à venir", GOLD, false), 0.9f);
        }
        wpoint(dir, "ecran.elo", -3.7, by + 2, 0, -1, 0);
        wlight(dir, 0, by - 1, 0, 15);

        for (int u = -R; u <= R; u += 5) {
            for (int v = -R; v <= R; v += 5) {
                double r = Math.hypot(u, v);
                if (r > inner && r < R - 2) {
                    wlight(dir, u, 7, v, 13);
                }
            }
        }
        gate(dir, "practice", "PRACTICE", ICE, gateU, 9, false, accent, stand, pal("rideau", Material.CYAN_STAINED_GLASS), light);
    }

    // ------------------------------------------------------------------ MMO : le portail

    private void portail() {
        int dir = WEST;
        int R = wr;
        Material gold = pal("or", Material.GOLD_BLOCK);
        Material domeGlass = pal("dome", Material.GLASS);
        Material domeRib = pal("nervures", Material.POLISHED_DEEPSLATE);
        Material domeRing = pal("anneaux", Material.PURPLE_STAINED_GLASS);
        Material base = pal("socle", Material.POLISHED_DEEPSLATE);
        int gateU = R - 5;
        // dôme de confinement : il englobe l'île, son portail et une couronne de verre au-dessus du vide
        int Rd = islandR(), Hd = islandH();
        double step = Math.PI / 4;
        for (int u = -Rd - 1; u <= Rd + 1; u++) {
            for (int v = -Rd - 1; v <= Rd + 1; v++) {
                double r = Math.hypot(u, v);
                if (r > Rd + 0.5) {
                    continue;
                }
                double th = Math.atan2(v, u);
                double edge = R - 1.5 + 1.5 * Math.sin(3 * th) + (hash(u, v, 1) - 0.5) * 2.4;
                boolean keep = (u > gateU - 8 && Math.abs(v) <= 10 && r <= R) || (u < -R + 6 && Math.abs(v) <= hw + 1 && r <= R + 1);
                boolean island = keep || !(r > edge || (r > edge - 2 && hash(u, v, 2) < 0.3));
                if (island) {
                    double h = hash(u, v, 3);
                    Material f = h < 0.5 ? Material.STONE_BRICKS : h < 0.75 ? Material.MOSSY_STONE_BRICKS : Material.CRACKED_STONE_BRICKS;
                    if (r < 1.5) {
                        f = Material.GILDED_BLACKSTONE;
                    } else if (r < 6.4) {
                        f = Material.DEEPSLATE_TILES;
                    } else if (Math.abs(r - 7) < 0.6) {
                        f = Math.floorMod((int) Math.round(th * 7), 3) == 0 ? gold : Material.CHISELED_STONE_BRICKS;
                    }
                    wset(dir, u, 0, v, f);
                    int depth = Math.min(15, (int) (Math.max(0, edge - r) * 0.7) + 1);
                    for (int d = 1; d <= depth; d++) {
                        double hd = hash(u + d, v - d, 4);
                        wset(dir, u, -d, v, hd < 0.5 ? Material.STONE : hd < 0.8 ? Material.DEEPSLATE : Material.TUFF);
                    }
                    boolean free = r < 9 || (Math.abs(v) <= hw + 2);
                    if (!free && r < edge - 2 && hash(u, v, 8) < 0.035) {
                        wset(dir, u, 1, v, hash(u, v, 9) < 0.5 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE);
                    }
                } else if (r > Rd - 1.0) {
                    // socle du dôme
                    wset(dir, u, 0, v, hash(u, v, 21) < 0.2 ? Material.GILDED_BLACKSTONE : base);
                    wset(dir, u, -1, v, base);
                } else {
                    // couronne : un plancher de verre entre le bord déchiqueté de l'île et le dôme
                    wset(dir, u, 0, v, domeGlass);
                }
                // dôme
                int top = cap(r, Rd + 0.5, Hd);
                int low = top;
                for (int[] n : N4) {
                    double nr = Math.hypot(u + n[0], v + n[1]);
                    low = Math.min(low, (nr > Rd + 0.5 ? 0 : cap(nr, Rd + 0.5, Hd)) + 1);
                }
                double off = r * Math.abs(Math.sin(th - Math.round(th / step) * step));
                for (int y = Math.max(1, Math.min(low, top)); y <= top; y++) {
                    wset(dir, u, y, v, r < 1.5 ? Material.CRYING_OBSIDIAN : off < 0.6 ? domeRib : y % 8 == 0 ? domeRing : domeGlass);
                }
            }
        }
        // colonnes brisées
        double k = R / 22.0;
        int[][] cols = {{-12, 10}, {-12, -10}, {-2, 13}, {-2, -13}, {8, 12}, {8, -12}};
        for (int[] c : cols) {
            int cu = (int) Math.round(c[0] * k), cv = (int) Math.round(c[1] * k);
            int h = 3 + (int) (hash(cu, cv, 12) * 7);
            for (int du = 0; du <= 1; du++) {
                for (int dv = 0; dv <= 1; dv++) {
                    int hh = h - (int) (hash(cu + du, cv + dv, 13) * 3);
                    wset(dir, cu + du, 0, cv + dv, Material.CHISELED_STONE_BRICKS);
                    for (int y = 1; y <= hh; y++) {
                        double hm = hash(cu + du + y, cv + dv, 14);
                        wset(dir, cu + du, y, cv + dv, y == 1 ? Material.CHISELED_STONE_BRICKS
                                : hm < 0.5 ? Material.STONE_BRICKS : hm < 0.75 ? Material.MOSSY_STONE_BRICKS : Material.CRACKED_STONE_BRICKS);
                    }
                }
            }
        }
        // portail ancien : anneau de pierre et rideau violet
        Material curtain = pal("rideau", Material.PURPLE_STAINED_GLASS);
        int yc = 8;
        for (int v = -8; v <= 8; v++) {
            for (int y = 1; y <= 16; y++) {
                double d = Math.hypot(v, y - yc);
                if (d > 7.2) {
                    continue;
                }
                if (d >= 5.5) {
                    double h = hash(v, y, 15);
                    wset(dir, gateU, y, v, h < 0.15 ? gold : h < 0.55 ? Material.CRYING_OBSIDIAN : Material.CHISELED_DEEPSLATE);
                } else {
                    wset(dir, gateU, y, v, curtain);
                }
            }
        }
        for (int side : new int[]{-1, 1}) {
            for (int y = 1; y <= 12; y++) {
                wset(dir, gateU, y, side * 10, y == 12 ? gold : y % 4 == 0 ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS);
            }
        }
        String runes = "ᚠᚢᚦᚨᚱᚲᚷᚹ";
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4 + Math.PI / 8;
            wtext(dir, 7 * Math.cos(ang), 2.2, 7 * Math.sin(ang), label(String.valueOf(runes.charAt(i)), GOLD, true), 1.8f);
        }
        // pénombre sur les ruines, lueur devant le portail
        for (int u = -R; u <= R; u += 5) {
            for (int v = -R; v <= R; v += 5) {
                if (Math.hypot(u, v) < R - 3) {
                    wlight(dir, u, 3, v, 11);
                }
            }
        }
        for (int v : new int[]{-3, 0, 3}) {
            wlight(dir, gateU - 1, 4, v, 15);
            wlight(dir, gateU - 1, 9, v, 15);
        }
        wtext(dir, gateU - 0.6, 16.6, 0, label("MMO", VIOLET, true), 2.4f);
        wpoint(dir, "porte.mmo", gateU - 1, 1, 0, 1, 0);
        wpoint(dir, "pnj.mmo", gateU - 5, 1, 0, -1, 0);
    }

    // ------------------------------------------------------------------ reliques (clins d'œil ARK)

    private void reliques() {
        int c = a / 2;
        Material bone = Material.BONE_BLOCK;
        // fossile (nord-est), sur un socle
        int fx = c - 5, fz = -c;
        for (int x = fx; x <= fx + 10; x++) {
            for (int z = fz - 2; z <= fz + 2; z++) {
                boolean corner = (x == fx || x == fx + 10) && Math.abs(z - fz) == 2;
                set(x, 1, z, corner ? Material.GOLD_BLOCK : Material.POLISHED_DEEPSLATE);
            }
        }
        for (int x = fx + 2; x <= fx + 8; x++) {
            set(x, 5, fz, bone);
        }
        set(fx + 1, 5, fz, bone);
        set(fx, 4, fz, bone);
        set(fx - 1, 3, fz, bone);
        for (int lx : new int[]{fx + 4, fx + 7}) {
            for (int dz : new int[]{-1, 1}) {
                for (int y = 2; y <= 4; y++) {
                    set(lx, y, fz + dz, bone);
                }
            }
        }
        for (int x = fx + 4; x <= fx + 7; x++) {
            set(x, 4, fz - 1, bone);
            set(x, 4, fz + 1, bone);
        }
        set(fx + 9, 6, fz, bone);
        for (int x = fx + 10; x <= fx + 12; x++) {
            set(x, 7, fz, bone);
            set(x, 8, fz, bone);
        }
        set(fx + 11, 6, fz, bone);
        set(fx + 12, 6, fz, bone);
        set(fx + 9, 4, fz - 1, bone);
        set(fx + 9, 4, fz + 1, bone);
        holo(fx + 5.5, 10.2, fz + 0.5, label("FOSSILE · PRÉDATEUR ALPHA", GOLD, true), 1.0f);

        // caisson (nord-ouest) avec sa créature : capsule octogonale, socle et couvercle au ras de la paroi de verre.
        // Forme reprise telle quelle des retouches faites à la main dans la station le 22/11.
        int tx = -c, tz = -c;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                double r = Math.hypot(dx, dz);
                if (r > 2.5) {
                    continue;
                }
                set(tx + dx, 1, tz + dz, Material.IRON_BLOCK);
                set(tx + dx, 7, tz + dz, r < 1 ? Material.SEA_LANTERN : Material.IRON_BLOCK);
                if (r > 1.9) {
                    for (int y = 2; y <= 6; y++) {
                        set(tx + dx, y, tz + dz, Material.LIGHT_BLUE_STAINED_GLASS);
                    }
                }
            }
        }
        plan.creature(zone, tx + 0.5, 2, tz + 0.5, 135f);
        holo(tx + 0.5, 9.2, tz + 0.5, label("SPÉCIMEN 07 · CONFINEMENT", RED, true), 1.0f);

        // trois obélisques flottants
        obelisk(obD, -obD, Material.BLUE_CONCRETE, Material.BLUE_STAINED_GLASS);
        obelisk(obD, obD, Material.RED_CONCRETE, Material.RED_STAINED_GLASS);
        obelisk(-obD, -obD, Material.LIME_CONCRETE, Material.LIME_STAINED_GLASS);
    }

    private void obelisk(int cx, int cz, Material color, Material beam) {
        for (int y = -20; y <= obH; y++) {
            int half = y < -12 ? 0 : 1;
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    boolean face = half == 1 && (dx == 0) != (dz == 0);
                    Material m = y == obH ? Material.IRON_BLOCK : face && Math.floorMod(y, 3) == 0 ? color : Material.POLISHED_DEEPSLATE;
                    set(cx + dx, y, cz + dz, m);
                }
            }
        }
        set(cx, obH + 1, cz, Material.BEACON);
        set(cx, obH + 2, cz, beam);
    }

    // ------------------------------------------------------------------ porte d'embarquement

    private void gate(int dir, String id, String title, int color, int gateU, int height, boolean indoor,
                      Material frame, Material pillar, Material curtain, Material keystone) {
        for (int v = -hw; v <= hw; v++) {
            for (int y = 1; y <= height; y++) {
                boolean side = Math.abs(v) == hw;
                Material m = v == 0 && y == height ? keystone : y == height ? frame : side ? pillar : curtain;
                wset(dir, gateU, y, v, m);
            }
        }
        for (int v : new int[]{-2, 2}) {
            wlight(dir, gateU - 1, 3, v, 15);
            wlight(dir, gateU - 1, 6, v, 15);
        }
        if (indoor) {
            // sous un plafond : affiche fixe, plus petite, juste au-dessus du linteau
            wsign(dir, gateU - 0.7, height + 1.2, 0, -1, 0, label(title, color, true), 1.6f);
        } else {
            wtext(dir, gateU - 0.6, height + 1.6, 0, label(title, color, true), 2.4f);
        }
        wpoint(dir, "porte." + id, gateU - 1, 1, 0, 1, 0);
        wpoint(dir, "pnj." + id, gateU - 4, 1, 0, -1, 0);
    }

    // ------------------------------------------------------------------ outils

    private void set(int x, int y, int z, Material m) {
        plan.set(x, y, z, data(m));
    }

    /** Pose un bloc dans le repère local d'une aile. */
    private void wset(int dir, int u, int y, int v, Material m) {
        plan.set(wx(dir, centerD + u, v), y, wz(dir, centerD + u, v), data(m));
    }

    /** Texte flottant, toujours tourné vers le joueur. */
    private void holo(double x, double y, double z, Component text, float scale) {
        plan.text(zone, x, y, z, null, text, scale);
    }

    /** Lumière invisible, posée seulement là où il n'y a rien. */
    private void light(int x, int y, int z, int level) {
        if (!plan.has(x, y, z)) {
            plan.set(x, y, z, data("minecraft:light[level=" + level + "]"));
        }
    }

    private void wlight(int dir, int u, int y, int v, int level) {
        light(wx(dir, centerD + u, v), y, wz(dir, centerD + u, v), level);
    }

    private void wtext(int dir, double u, double y, double v, Component text, float scale) {
        double du = centerD + u;
        holo(du * FWD[dir][0] + v * RIGHT[dir][0] + 0.5, y, du * FWD[dir][1] + v * RIGHT[dir][1] + 0.5, text, scale);
    }

    /** Affiche fixe dans une aile : elle regarde vers (lookU, lookV) du repère local. */
    private void wsign(int dir, double u, double y, double v, int lookU, int lookV, Component text, float scale) {
        double du = centerD + u;
        double dx = lookU * FWD[dir][0] + lookV * RIGHT[dir][0];
        double dz = lookU * FWD[dir][1] + lookV * RIGHT[dir][1];
        plan.text(zone, du * FWD[dir][0] + v * RIGHT[dir][0] + 0.5, y, du * FWD[dir][1] + v * RIGHT[dir][1] + 0.5,
                yawOf(dx, dz), text, scale);
    }

    /** Angle de regard Minecraft (0 = sud, 90 = ouest, 180 = nord, 270 = est) pour une direction donnée. */
    private static float yawOf(double dx, double dz) {
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return (yaw < 0 ? yaw + 360f : yaw) + 0f;
    }

    /** Point repère dans une aile, regard tourné vers (lookU, lookV) dans le repère local. */
    private void wpoint(int dir, String name, double u, double y, double v, int lookU, int lookV) {
        double du = centerD + u;
        double dx = lookU * FWD[dir][0] + lookV * RIGHT[dir][0];
        double dz = lookU * FWD[dir][1] + lookV * RIGHT[dir][1];
        plan.point(name, du * FWD[dir][0] + v * RIGHT[dir][0] + 0.5, y, du * FWD[dir][1] + v * RIGHT[dir][1] + 0.5,
                yawOf(dx, dz));
    }

    private static int wx(int dir, int u, int v) {
        return u * FWD[dir][0] + v * RIGHT[dir][0];
    }

    private static int wz(int dir, int u, int v) {
        return u * FWD[dir][1] + v * RIGHT[dir][1];
    }

    private static String face(int dir, int du, int dv) {
        int dx = du * FWD[dir][0] + dv * RIGHT[dir][0];
        int dz = du * FWD[dir][1] + dv * RIGHT[dir][1];
        return dz < 0 ? "north" : dx > 0 ? "east" : dz > 0 ? "south" : "west";
    }

    private static boolean inOct(double x, double z, double apothem) {
        return Math.abs(x) <= apothem && Math.abs(z) <= apothem && Math.abs(x) + Math.abs(z) <= apothem * 1.4142;
    }

    /** Hauteur d'une calotte de rayon R et de hauteur H à la distance r de son axe. */
    private static int cap(double r, double R, int H) {
        return r >= R ? 0 : (int) Math.round(H * Math.sqrt(1 - (r / R) * (r / R)));
    }

    private static long key(int u, int v) {
        return ((long) u << 32) ^ (v & 0xFFFFFFFFL);
    }

    /** Pseudo-hasard stable : la même station sort à chaque génération. */
    private static double hash(int x, int y, int salt) {
        long n = x * 73856093L ^ y * 19349663L ^ salt * 83492791L;
        n = (n ^ (n >>> 13)) * 0x5DEECE66DL;
        n ^= n >>> 17;
        return (n & 0xFFFFFF) / (double) 0x1000000;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Component label(String text, int rgb, boolean bold) {
        Component c = Component.text(text, TextColor.color(rgb));
        return bold ? c.decorate(TextDecoration.BOLD) : c;
    }

    /** Bloc d'une palette : palettes.&lt;zone&gt;.&lt;rôle&gt; dans station.yml, sinon la valeur par défaut. */
    private Material pal(String role, Material def) {
        String z = zone.equals("passerelles") ? "passerelle" : zone;
        String value = cfg.getString("palettes." + z + "." + role);
        Material m = value == null ? null : Material.matchMaterial(value);
        return m != null && m.isBlock() ? m : def;
    }

    private BlockData data(Material m) {
        return dataCache.computeIfAbsent(m, k -> m.createBlockData());
    }

    private BlockData data(String state) {
        return dataCache.computeIfAbsent(state, k -> Bukkit.createBlockData(state));
    }
}
