package fr.orbit.mmo.continent;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static fr.orbit.mmo.continent.ContinentGenerator.NONE;

/**
 * Dessin d'une capitale du continent, calculé à partir du terrain : rempart rond et ses tours, portes là où les
 * routes arrivent, fleuve gardé dans son lit entre deux quais, grand pont, deux places (la Grand-Place au sud avec
 * l'hôtel des ventes, la forge et l'alchimiste ; le parvis au nord avec le donjon, le temple et l'atelier des runes),
 * rues, maisons, arbres et lanternes. Calcul pur, sans serveur : le résultat est un {@link CapitalPlan}.
 *
 * Repère : dx vers l'est, dz vers le sud, origine au centre du lieu. Le pavé de la ville est à Y = g.
 */
class CapitalDesign {

    static final int AUTO = Integer.MIN_VALUE;

    /**
     * @param wall  rayon du rempart
     * @param outer rayon où le terrain retrouve son relief naturel
     * @param level Y du pavé, ou AUTO pour le déduire du fleuve et du sol
     */
    record Settings(String id, String place, String style, int wall, int outer, int level, long seed) {
    }

    // Nature de chaque colonne. De YARD à PLAZA : sol de la ville, du moins au plus « noble » (une rue ne recouvre que moins noble qu'elle).
    static final byte OUT = 0, YARD = 1, QUAY = 2, LANE = 3, STREET = 4, AVENUE = 5, PLAZA = 6, LOT = 7,
            QUAY_EDGE = 10, WATER = 11, WALL = 12, GATE = 13, PASSAGE = 14, TOWER = 15, RING = 16, RING_WATER = 17, BRIDGE = 18;
    /** Demi-côté des deux places. */
    static final int PH = 16;
    /** Hauteur du chemin de ronde au-dessus du pavé. */
    static final int WALL_H = 10;
    /** Hauteur du plan au-dessus du pavé. */
    static final int TOP = 40;

    // ------------------------------------------------------------------ Styles
    static final class Style {
        Object[] wall, street, avenue, lane, quay, frames, bases, infills, roofs, floors, flowers;
        String coping, plazaA, plazaB, accent, walk, cone, fence, post, lantern, banner, leaves, log, planks, door, glass,
                stained, stone, stoneBase, pale, paleFloor, pillar, bigRoof, chimney, parapet, stairs, slab, rim, light;
        boolean dark;

        static Style of(String name) {
            Style s = new Style();
            if (name.equalsIgnoreCase("ombre")) {
                s.dark = true;
                s.wall = new Object[]{"deepslate_bricks", 50, "cracked_deepslate_bricks", 10, "deepslate_tiles", 14, "cobbled_deepslate", 16, "polished_deepslate", 10};
                s.street = new Object[]{"tuff_bricks", 30, "cobblestone", 25, "tuff", 20, "andesite", 15, "gravel", 10};
                s.avenue = new Object[]{"polished_tuff", 40, "tuff_bricks", 35, "andesite", 15, "cobbled_deepslate", 10};
                s.lane = new Object[]{"cobblestone", 35, "tuff", 25, "gravel", 25, "coarse_dirt", 15};
                s.quay = new Object[]{"tuff_bricks", 45, "polished_tuff", 25, "cobbled_deepslate", 30};
                s.frames = new Object[]{"dark_oak_log", 70, "spruce_log", 30};
                s.bases = new Object[]{"cobbled_deepslate", 50, "deepslate_bricks", 50};
                s.infills = new Object[]{"tuff_bricks", 35, "light_gray_terracotta", 25, "polished_tuff", 15, "gray_terracotta", 15, "deepslate_bricks", 10};
                s.roofs = new Object[]{"deepslate_tile", 45, "dark_oak", 20, "blackstone", 15, "mangrove", 10, "crimson", 10};
                s.floors = new Object[]{"dark_oak_planks", 60, "spruce_planks", 40};
                s.flowers = new Object[]{"fern", 70, "allium", 15, "blue_orchid", 15};
                s.coping = "polished_deepslate";
                s.plazaA = "polished_tuff";
                s.plazaB = "polished_deepslate";
                s.accent = "purple_terracotta";
                s.walk = "deepslate_tiles";
                s.cone = "purple_terracotta";
                s.fence = "dark_oak_fence";
                s.post = "cobbled_deepslate_wall";
                s.lantern = "soul_lantern";
                s.banner = "purple";
                s.leaves = "dark_oak_leaves";
                s.log = "dark_oak_log";
                s.planks = "dark_oak_planks";
                s.door = "dark_oak_door";
                s.glass = "glass_pane";
                s.stained = "purple_stained_glass_pane";
                s.stone = "deepslate_bricks";
                s.stoneBase = "polished_deepslate";
                s.pale = "tuff_bricks";
                s.paleFloor = "polished_tuff";
                s.pillar = "polished_basalt";
                s.bigRoof = "deepslate_tile";
                s.chimney = "cobbled_deepslate";
                s.parapet = "deepslate_brick_wall";
                s.stairs = "deepslate_brick_stairs";
                s.slab = "deepslate_brick_slab";
                s.rim = "chiseled_deepslate";
                s.light = "crying_obsidian";
            } else {
                s.wall = new Object[]{"stone_bricks", 58, "cracked_stone_bricks", 8, "mossy_stone_bricks", 8, "andesite", 12, "cobblestone", 14};
                s.street = new Object[]{"cobblestone", 40, "stone_bricks", 25, "andesite", 20, "gravel", 10, "mossy_cobblestone", 5};
                s.avenue = new Object[]{"stone_bricks", 45, "polished_andesite", 30, "andesite", 15, "cobblestone", 10};
                s.lane = new Object[]{"cobblestone", 45, "gravel", 25, "andesite", 15, "coarse_dirt", 15};
                s.quay = new Object[]{"stone_bricks", 50, "smooth_stone", 25, "andesite", 25};
                s.frames = new Object[]{"oak_log", 70, "spruce_log", 30};
                s.bases = new Object[]{"stone_bricks", 60, "cobblestone", 40};
                s.infills = new Object[]{"calcite", 40, "white_terracotta", 30, "smooth_sandstone", 18, "bricks", 12};
                s.roofs = new Object[]{"brick", 50, "spruce", 20, "dark_prismarine", 12, "dark_oak", 10, "stone_brick", 8};
                s.floors = new Object[]{"oak_planks", 60, "spruce_planks", 40};
                s.flowers = new Object[]{"dandelion", 30, "poppy", 25, "oxeye_daisy", 20, "cornflower", 15, "azure_bluet", 10};
                s.coping = "polished_andesite";
                s.plazaA = "smooth_stone";
                s.plazaB = "polished_andesite";
                s.accent = "yellow_terracotta";
                s.walk = "stone_bricks";
                s.cone = "dark_prismarine";
                s.fence = "oak_fence";
                s.post = "cobblestone_wall";
                s.lantern = "lantern";
                s.banner = "yellow";
                s.leaves = "oak_leaves";
                s.log = "oak_log";
                s.planks = "oak_planks";
                s.door = "oak_door";
                s.glass = "glass_pane";
                s.stained = "yellow_stained_glass_pane";
                s.stone = "stone_bricks";
                s.stoneBase = "polished_andesite";
                s.pale = "calcite";
                s.paleFloor = "polished_diorite";
                s.pillar = "quartz_pillar";
                s.bigRoof = "dark_prismarine";
                s.chimney = "bricks";
                s.parapet = "stone_brick_wall";
                s.stairs = "stone_brick_stairs";
                s.slab = "stone_brick_slab";
                s.rim = "chiseled_stone_bricks";
                s.light = "sea_lantern";
            }
            return s;
        }
    }

    /** Un bâtiment à poser : rectangle (bornes comprises), côté de la façade, nature. */
    record Lot(int x0, int z0, int x1, int z1, char front, String kind) {
    }

    static final class Gate {
        double ang, ex, ez, half;
        boolean royal;
        String name;
    }

    record Tower(int cx, int cz, double rad, int h) {
    }

    /** Portion du rempart déjà occupée (porte ou passage du fleuve), tours comprises. */
    record Feature(double a1, double a2) {
    }

    /** Ce qui distingue un bâtiment d'un autre. */
    static final class Spec {
        int storeys = 1, storeyH = 4, winLo = 2, winHi = 3, doorW = 1;
        String frame, base, infill0, infill1, floor, roof, door, glass, bed = "red";
        boolean gableFront, flat, chimney, furnish = true;
    }

    final ContinentData data;
    final ContinentGenerator gen;
    final Settings s;
    final Style st;
    final int ox, oz, R, side, Rw, Ro, salt;
    final String placeName;
    int g, cn, cs;
    final int[] nTop, fTop, low;
    final byte[] bio, zone;
    final float[] roadY, dw;
    final boolean[] royal, parvis, doorFront;
    CapitalPlan plan;
    final List<Gate> gates = new ArrayList<>();
    final List<Tower> towers = new ArrayList<>();
    final List<Lot> lots = new ArrayList<>();
    final List<int[]> lamps = new ArrayList<>();
    int houses, trees;

    CapitalDesign(ContinentData data, ContinentGenerator gen, Settings s, ContinentData.Place place) {
        this.data = data;
        this.gen = gen;
        this.s = s;
        this.st = Style.of(s.style());
        this.ox = place.x();
        this.oz = place.z();
        this.placeName = place.name();
        this.Rw = s.wall();
        this.Ro = s.outer();
        this.R = Ro + 2;
        this.side = 2 * R + 1;
        this.salt = (int) (s.seed() * 7919L);
        int n = side * side;
        nTop = new int[n];
        fTop = new int[n];
        low = new int[n];
        bio = new byte[n];
        zone = new byte[n];
        roadY = new float[n];
        dw = new float[n];
        royal = new boolean[n];
        parvis = new boolean[n];
        doorFront = new boolean[n];
    }

    /** Calcule le plan. Lève IllegalArgumentException si le lieu est inconnu ou les rayons incohérents. */
    static CapitalPlan build(ContinentData data, ContinentGenerator gen, Settings s) {
        ContinentData.Place place = data.place(s.place());
        if (place == null) {
            throw new IllegalArgumentException("lieu inconnu : " + s.place());
        }
        if (s.wall() < 70 || s.wall() > 160 || s.outer() < s.wall() + 20 || s.outer() > 240) {
            throw new IllegalArgumentException("rayons hors limites (rempart 70 à 160, raccord au moins 20 blocs plus loin, 240 au plus)");
        }
        return new CapitalDesign(data, gen, s, place).run();
    }

    CapitalPlan run() {
        survey();
        distances();
        zones();
        features();
        plazas();
        streets();
        landmarks();
        lanes();
        houses();
        ground();
        wall();
        towers();
        gatehouses();
        bridge(0, 4, true);
        for (int x : new int[]{-64, -32, 32, 64}) {
            bridge(x, 2, false);
        }
        for (Lot lot : lots) {
            build(lot);
        }
        plazaDecor();
        yards();
        lamps();
        plan.points.put("spawn", new double[]{ox + 0.5, g + 1, oz + cs - 9 + 0.5, 0});
        plan.points.put("grand-place", new double[]{ox + 0.5, g + 1, oz + cs + 8 + 0.5, 180});
        plan.points.put("parvis", new double[]{ox + 0.5, g + 1, oz + cn + 9 + 0.5, 180});
        gatekeeper();
        plan.notes.add(houses + " maisons, " + trees + " arbres, " + lamps.size() + " lanternes, " + towers.size() + " tours, "
                + gates.size() + " portes");
        return plan;
    }

    /**
     * Gardien du Pilier des Astres : le PNJ qui ouvre le donjon des cent étages, debout à côté de la porte du donjon de la capitale.
     * Un PNJ et un point (« tour »), aucun bloc : le plan des blocs ne change pas.
     */
    void gatekeeper() {
        Lot keep = null;
        for (Lot lot : lots) {
            if (lot.kind.equals("donjon")) {
                keep = lot;
            }
        }
        if (keep == null) {
            plan.notes.add("pas de donjon : pas de gardien du Pilier");
            return;
        }
        Frame f = new Frame(keep.x0, keep.z0, keep.x1, keep.z1, keep.front);
        int pc = f.w / 2;
        for (int[] c : new int[][]{{pc + 3, -2}, {pc - 4, -2}, {pc + 3, -3}, {pc - 4, -3}, {pc + 4, -3}, {pc - 5, -3}, {pc + 3, -4}, {pc - 4, -4}}) {
            if (!standable(f, c[0], c[1], 1)) {
                continue;
            }
            int x = f.x(c[0], c[1]), z = f.z(c[0], c[1]);
            plan.npcs.add(new CapitalPlan.Npc(s.style().equals("ombre") ? "tower_ombre" : "tower", ox + x + 0.5, g + 1, oz + z + 0.5,
                    yaw(-f.bx, -f.bz)));
            int b = -6;
            while (b < -3 && !standable(f, pc, b, 0)) {
                b++;
            }
            plan.points.put("tour", new double[]{ox + f.x(pc, b) + 0.5, g + 1, oz + f.z(pc, b) + 0.5, yaw(f.bx, f.bz)});
            plan.notes.add("gardien du Pilier en " + (ox + x) + " " + (g + 1) + " " + (oz + z));
            return;
        }
        plan.notes.add("pas de place libre devant le donjon pour le gardien du Pilier");
    }

    /** Sol plein et trois blocs d'air au-dessus, sur un carré de côté 2 × marge + 1 autour de (a, b). */
    private boolean standable(Frame f, int a, int b, int margin) {
        for (int da = -margin; da <= margin; da++) {
            for (int db = -margin; db <= margin; db++) {
                int x = f.x(a + da, b + db), z = f.z(a + da, b + db);
                if (!plan.in(x, g, z) || plan.get(x, g, z) <= CapitalPlan.AIR) {
                    return false;
                }
                for (int y = g + 1; y <= g + 3; y++) {
                    if (!free(x, y, z)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ Outils
    boolean inGrid(int dx, int dz) {
        return dx >= -R && dx <= R && dz >= -R && dz <= R;
    }

    int ci(int dx, int dz) {
        return (dz + R) * side + dx + R;
    }

    byte zoneAt(int dx, int dz) {
        return inGrid(dx, dz) ? zone[ci(dx, dz)] : OUT;
    }

    boolean wetAt(int dx, int dz) {
        return inGrid(dx, dz) && fTop[ci(dx, dz)] != NONE;
    }

    static boolean walk(byte z) {
        return (z >= QUAY && z <= PLAZA) || z == PASSAGE;
    }

    double rnd(int dx, int dz, int k) {
        return ContinentGenerator.rnd(ox + dx, oz + dz, k * 131 + salt);
    }

    static String pick(double r, Object[] table) {
        double total = 0;
        for (int i = 1; i < table.length; i += 2) {
            total += ((Number) table[i]).doubleValue();
        }
        double t = r * total;
        for (int i = 0; i < table.length; i += 2) {
            t -= ((Number) table[i + 1]).doubleValue();
            if (t < 0) {
                return (String) table[i];
            }
        }
        return (String) table[table.length - 2];
    }

    String mix(Object[] table, int dx, int dz, int k) {
        return pick(rnd(dx, dz, k), table);
    }

    static String name(Material m) {
        return m.name().toLowerCase(Locale.ROOT);
    }

    static String dirName(int dx, int dz) {
        return dx > 0 ? "east" : (dx < 0 ? "west" : (dz > 0 ? "south" : "north"));
    }

    /** Regard (en degrés, 0 = sud, 90 = ouest) de quelqu'un tourné vers (fx, fz). */
    static float yaw(double fx, double fz) {
        return (float) Math.toDegrees(Math.atan2(-fx, fz));
    }

    /** Bûche, pilier : blocs qui ont un axe. Les autres restent tels quels. */
    static String log(String block, String axis) {
        boolean axial = block.endsWith("_log") || block.endsWith("_wood") || block.contains("pillar") || block.endsWith("basalt")
                || block.endsWith("_stem");
        return axial ? block + "[axis=" + axis + "]" : block;
    }

    void put(int dx, int y, int dz, String state) {
        plan.set(dx, y, dz, plan.id(state));
    }

    void air(int dx, int y, int dz) {
        plan.set(dx, y, dz, CapitalPlan.AIR);
    }

    boolean free(int dx, int y, int dz) {
        return plan.in(dx, y, dz) && plan.get(dx, y, dz) <= CapitalPlan.AIR;
    }

    void putIfFree(int dx, int y, int dz, String state) {
        if (free(dx, y, dz)) {
            put(dx, y, dz, state);
        }
    }

    /** Premier Y libre au-dessus de ce qui est posé dans la colonne, à partir de fromY. */
    int topOf(int dx, int dz, int fromY) {
        int y = fromY;
        while (plan.in(dx, y, dz) && plan.get(dx, y, dz) > CapitalPlan.AIR) {
            y++;
        }
        return y;
    }

    void paint(int dx, int dz, byte code) {
        if (!inGrid(dx, dz)) {
            return;
        }
        int i = ci(dx, dz);
        byte z = zone[i];
        if (z >= YARD && z <= PLAZA && z < code) {
            zone[i] = code;
        }
    }

    void paintRect(int x0, int z0, int x1, int z1, byte code) {
        for (int dz = Math.max(-R, z0); dz <= Math.min(R, z1); dz++) {
            for (int dx = Math.max(-R, x0); dx <= Math.min(R, x1); dx++) {
                paint(dx, dz, code);
            }
        }
    }

    void paintSegment(double x0, double z0, double x1, double z1, double hw, byte code) {
        int ax = (int) Math.floor(Math.min(x0, x1) - hw - 1), bx = (int) Math.ceil(Math.max(x0, x1) + hw + 1);
        int az = (int) Math.floor(Math.min(z0, z1) - hw - 1), bz = (int) Math.ceil(Math.max(z0, z1) + hw + 1);
        double vx = x1 - x0, vz = z1 - z0, len2 = vx * vx + vz * vz;
        for (int dz = az; dz <= bz; dz++) {
            for (int dx = ax; dx <= bx; dx++) {
                double t = len2 < 1e-9 ? 0 : ((dx - x0) * vx + (dz - z0) * vz) / len2;
                t = Math.max(0, Math.min(1, t));
                if (Math.hypot(x0 + vx * t - dx, z0 + vz * t - dz) <= hw) {
                    paint(dx, dz, code);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Relevé du terrain
    void survey() {
        ContinentGenerator.Column c = new ContinentGenerator.Column();
        ContinentData.RoadHit hit = new ContinentData.RoadHit();
        long sum = 0;
        int n = 0, wMax = Integer.MIN_VALUE, bedMin = Integer.MAX_VALUE;
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                int i = ci(dx, dz), wx = ox + dx, wz = oz + dz;
                gen.column(wx, wz, false, hit, c);
                nTop[i] = c.top;
                fTop[i] = c.fluid;
                bio[i] = (byte) c.biome;
                low[i] = c.top;
                roadY[i] = Float.NaN;
                if (data.road(wx, wz, 0, hit)) {
                    roadY[i] = (float) hit.y;
                    royal[i] = hit.road.royal;
                    low[i] = Math.min(low[i], (int) Math.floor(Math.round(hit.y * 2) / 2.0) - 1);
                }
                double r = Math.hypot(dx, dz);
                if (c.fluid != NONE) {
                    if (r < Rw) {
                        wMax = Math.max(wMax, c.fluid);
                    }
                    if (r < Rw + 10) {
                        bedMin = Math.min(bedMin, c.top);
                    }
                } else if (r < Rw) {
                    sum += c.top;
                    n++;
                }
            }
        }
        int mean = n == 0 ? 64 : (int) Math.round(sum / (double) n);
        g = s.level() != AUTO ? s.level() : Math.max(mean, wMax == Integer.MIN_VALUE ? mean : wMax + 1);
        int y0 = Math.min(g - 8, bedMin == Integer.MAX_VALUE ? g - 8 : bedMin - 1);
        plan = new CapitalPlan(s.id(), placeName, ox, oz, R, y0, g + TOP - y0 + 1, g);
        plan.notes.add("pavé à Y " + g + " (sol moyen " + mean + (wMax == Integer.MIN_VALUE ? "" : ", eau jusqu'à " + wMax) + ")");
    }

    /** Distance de chaque colonne sèche à l'eau la plus proche. */
    void distances() {
        final float big = 1e6f, d1 = 1f, d2 = 1.4142f;
        for (int i = 0; i < dw.length; i++) {
            dw[i] = fTop[i] != NONE ? 0 : big;
        }
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                int i = ci(dx, dz);
                float v = dw[i];
                if (dx > -R) {
                    v = Math.min(v, dw[i - 1] + d1);
                }
                if (dz > -R) {
                    v = Math.min(v, dw[i - side] + d1);
                    if (dx > -R) {
                        v = Math.min(v, dw[i - side - 1] + d2);
                    }
                    if (dx < R) {
                        v = Math.min(v, dw[i - side + 1] + d2);
                    }
                }
                dw[i] = v;
            }
        }
        for (int dz = R; dz >= -R; dz--) {
            for (int dx = R; dx >= -R; dx--) {
                int i = ci(dx, dz);
                float v = dw[i];
                if (dx < R) {
                    v = Math.min(v, dw[i + 1] + d1);
                }
                if (dz < R) {
                    v = Math.min(v, dw[i + side] + d1);
                    if (dx < R) {
                        v = Math.min(v, dw[i + side + 1] + d2);
                    }
                    if (dx > -R) {
                        v = Math.min(v, dw[i + side - 1] + d2);
                    }
                }
                dw[i] = v;
            }
        }
    }

    void zones() {
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                int i = ci(dx, dz);
                double r = Math.hypot(dx, dz);
                boolean wet = fTop[i] != NONE;
                byte z;
                if (r > Ro) {
                    z = OUT;
                } else if (r > Rw + 2) {
                    z = wet ? RING_WATER : RING;
                } else if (r >= Rw - 2) {
                    z = WALL;
                } else if (wet) {
                    z = WATER;
                } else if (dw[i] <= 1.5f) {
                    z = QUAY_EDGE;
                } else if (dw[i] <= 6.5f) {
                    z = QUAY;
                } else if (r >= Rw - 7) {
                    z = STREET;
                } else {
                    z = YARD;
                }
                zone[i] = z;
            }
        }
    }

    // ------------------------------------------------------------------ Portes, passages du fleuve, tours
    static double angDiff(double a, double b) {
        return Math.abs(Math.atan2(Math.sin(a - b), Math.cos(a - b)));
    }

    void addGate(double ang, boolean isRoyal, String roadName) {
        for (Gate o : gates) {
            if (angDiff(o.ang, ang) < 0.3) {
                if (isRoyal && !o.royal) {
                    o.royal = true;
                    o.name = roadName;
                    o.half = 3.0;
                }
                return;
            }
        }
        Gate gt = new Gate();
        gt.ang = ang;
        gt.ex = Math.cos(ang);
        gt.ez = Math.sin(ang);
        gt.royal = isRoyal;
        gt.half = isRoyal ? 3.0 : 2.5;
        gt.name = roadName;
        gates.add(gt);
    }

    boolean towerWet(int cx, int cz, double rad) {
        int k = (int) Math.ceil(rad) + 1;
        for (int dz = -k; dz <= k; dz++) {
            for (int dx = -k; dx <= k; dx++) {
                if (Math.hypot(dx, dz) <= rad + 0.6 && wetAt(cx + dx, cz + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Ajoute une tour à cet angle ; « step » la décale (en radians) tant qu'elle aurait les pieds dans l'eau. */
    void addTower(double ang, double radius, double rad, int h, double step) {
        for (int tries = 0; tries < 10; tries++) {
            int cx = (int) Math.round(radius * Math.cos(ang)), cz = (int) Math.round(radius * Math.sin(ang));
            if (!towerWet(cx, cz, rad)) {
                for (Tower t : towers) {
                    if (Math.hypot(t.cx - cx, t.cz - cz) < 9) {
                        return;
                    }
                }
                towers.add(new Tower(cx, cz, rad, h));
                return;
            }
            if (step == 0) {
                return;
            }
            ang += step;
        }
    }

    void features() {
        // Portes : là où une route du dessin franchit le rempart.
        for (ContinentData.Road road : data.roads) {
            for (int k = 1; k < road.x.length; k++) {
                double x0 = road.x[k - 1] - ox - 0.5, z0 = road.z[k - 1] - oz - 0.5, x1 = road.x[k] - ox - 0.5, z1 = road.z[k] - oz - 0.5;
                double r0 = Math.hypot(x0, z0), r1 = Math.hypot(x1, z1);
                if ((r0 - Rw) * (r1 - Rw) > 0 || r0 == r1) {
                    continue;
                }
                double t = (Rw - r0) / (r1 - r0);
                addGate(Math.atan2(z0 + (z1 - z0) * t, x0 + (x1 - x0) * t), road.royal, road.name);
            }
        }
        List<Feature> features = new ArrayList<>();
        for (Gate gt : gates) {
            double pad = (gt.half + 7.0) / Rw;
            features.add(new Feature(gt.ang - pad - 5.0 / Rw, gt.ang + pad + 5.0 / Rw));
            addTower(gt.ang - pad, Rw + 1, 4.6, WALL_H + 7, 0);
            addTower(gt.ang + pad, Rw + 1, 4.6, WALL_H + 7, 0);
            plan.notes.add("porte « " + gt.name + " » à " + Math.round(Math.toDegrees(gt.ang)) + "°");
        }
        // Passages du fleuve sous le rempart.
        int n = (int) Math.ceil(2 * Math.PI * Rw * 2);
        boolean[] wet = new boolean[n];
        int wetCount = 0;
        for (int k = 0; k < n; k++) {
            double a = k * 2 * Math.PI / n;
            for (int rr = Rw - 2; rr <= Rw + 2 && !wet[k]; rr++) {
                wet[k] = wetAt((int) Math.round(rr * Math.cos(a)), (int) Math.round(rr * Math.sin(a)));
            }
            wetCount += wet[k] ? 1 : 0;
        }
        if (wetCount > 0 && wetCount < n) {
            int start = 0;
            while (wet[start]) {
                start++;
            }
            int k = start;
            while (k < start + n) {
                if (!wet[k % n]) {
                    k++;
                    continue;
                }
                int k1 = k, k2 = k;
                // Les arcs séparés par moins de six blocs de berge n'en font qu'un.
                int gap = 0;
                while (k < start + n && gap < 12) {
                    if (wet[k % n]) {
                        k2 = k;
                        gap = 0;
                    } else {
                        gap++;
                    }
                    k++;
                }
                double a1 = k1 * 2 * Math.PI / n - 9.0 / Rw, a2 = k2 * 2 * Math.PI / n + 9.0 / Rw;
                features.add(new Feature(a1 - 6.0 / Rw, a2 + 6.0 / Rw));
                addTower(a1, Rw + 1.5, 5.2, WALL_H + 5, -2.0 / Rw);
                addTower(a2, Rw + 1.5, 5.2, WALL_H + 5, 2.0 / Rw);
            }
        }
        // Tours régulières dans les intervalles.
        if (features.isEmpty()) {
            for (int k = 0; k < 12; k++) {
                addTower(k * Math.PI / 6, Rw + 1.5, 5.2, WALL_H + 5, 0);
            }
        } else {
            List<Feature> sorted = new ArrayList<>();
            for (Feature f : features) {
                double a1 = f.a1 % (2 * Math.PI);
                if (a1 < 0) {
                    a1 += 2 * Math.PI;
                }
                sorted.add(new Feature(a1, a1 + (f.a2 - f.a1)));
            }
            sorted.sort(Comparator.comparingDouble(Feature::a1));
            for (int i = 0; i < sorted.size(); i++) {
                double from = sorted.get(i).a2, to = sorted.get((i + 1) % sorted.size()).a1 + (i == sorted.size() - 1 ? 2 * Math.PI : 0);
                double arc = (to - from) * Rw;
                if (arc <= 20) {
                    continue;
                }
                int extra = (int) Math.round(arc / 44.0) - 1;
                for (int j = 1; j <= extra; j++) {
                    addTower(from + (to - from) * j / (extra + 1), Rw + 1.5, 5.2, WALL_H + 5, 0);
                }
            }
        }
        for (Tower t : towers) {
            int k = (int) Math.ceil(t.rad) + 1;
            for (int dz = -k; dz <= k; dz++) {
                for (int dx = -k; dx <= k; dx++) {
                    if (Math.hypot(dx, dz) <= t.rad + 0.3 && inGrid(t.cx + dx, t.cz + dz) && !wetAt(t.cx + dx, t.cz + dz)) {
                        zone[ci(t.cx + dx, t.cz + dz)] = TOWER;
                    }
                }
            }
        }
        for (Gate gt : gates) {
            int cx = (int) Math.round(Rw * gt.ex), cz = (int) Math.round(Rw * gt.ez);
            for (int dz = cz - 22; dz <= cz + 22; dz++) {
                for (int dx = cx - 22; dx <= cx + 22; dx++) {
                    if (!inGrid(dx, dz) || wetAt(dx, dz)) {
                        continue;
                    }
                    double a = dx * gt.ex + dz * gt.ez - Rw, b = -dx * gt.ez + dz * gt.ex;
                    if (Math.abs(a) <= 5.5 && Math.abs(b) <= gt.half + 4.5) {
                        zone[ci(dx, dz)] = Math.abs(b) < gt.half ? PASSAGE : GATE;
                    } else if (a > 5.5 && a <= 15 && Math.abs(b) <= gt.half + 2 && zone[ci(dx, dz)] == RING) {
                        parvis[ci(dx, dz)] = true;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ Places, rues, bâtiments
    boolean plazaFits(int cz) {
        for (int dz = cz - PH; dz <= cz + PH; dz++) {
            for (int dx = -PH; dx <= PH; dx++) {
                if (zoneAt(dx, dz) != YARD || dw[ci(dx, dz)] < 7.5f) {
                    return false;
                }
            }
        }
        return true;
    }

    int plazaCentre(int sign) {
        for (int c = PH + 8; c <= Rw - PH - 30; c++) {
            if (plazaFits(sign * c)) {
                return sign * c;
            }
        }
        plan.notes.add("avertissement : pas de terrain sec pour la place " + (sign < 0 ? "nord" : "sud") + ", posée d'office");
        return sign * 48;
    }

    void plazas() {
        cn = plazaCentre(-1);
        cs = plazaCentre(1);
        paintRect(-PH, cn - PH, PH, cn + PH, PLAZA);
        paintRect(-PH, cs - PH, PH, cs + PH, PLAZA);
        plan.notes.add("parvis (nord) centré en dz " + cn + ", Grand-Place (sud) en dz " + cs);
    }

    boolean crossesWater(double x0, double z0, double x1, double z1) {
        double len = Math.hypot(x1 - x0, z1 - z0);
        for (double t = 0; t <= len; t += 0.5) {
            byte z = zoneAt((int) Math.round(x0 + (x1 - x0) * t / len), (int) Math.round(z0 + (z1 - z0) * t / len));
            if (z == WATER) {
                return true;
            }
        }
        return false;
    }

    void streets() {
        // Grand-Rue : d'une place à l'autre, par le grand pont.
        paintRect(-3, cn + PH, 3, cs - PH, AVENUE);
        paintRect(-Rw, cn - 2, Rw, cn + 2, STREET);
        paintRect(-Rw, cs - 2, Rw, cs + 2, STREET);
        // Une avenue de chaque porte au coin de place le plus proche, sans franchir le fleuve.
        for (Gate gt : gates) {
            double gx = (Rw - 2) * gt.ex, gz = (Rw - 2) * gt.ez, best = Double.MAX_VALUE;
            int bx = 0, bz = cs;
            for (int c : new int[]{cn, cs}) {
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        int px = sx * PH, pz = c + sz * PH;
                        double d = Math.hypot(px - gx, pz - gz) + (crossesWater(gx, gz, px, pz) ? 1000 : 0);
                        if (d < best) {
                            best = d;
                            bx = px;
                            bz = pz;
                        }
                    }
                }
            }
            if (best >= 1000) {
                plan.notes.add("avertissement : la porte « " + gt.name + " » n'atteint une place qu'en traversant l'eau");
            }
            paintSegment(gx, gz, bx, bz, gt.royal ? 3.4 : 2.6, AVENUE);
            plan.points.put("porte-" + ContinentData.plain(gt.name), new double[]{ox + Math.round((Rw - 10) * gt.ex) + 0.5, g + 1,
                    oz + Math.round((Rw - 10) * gt.ez) + 0.5, yaw(-gt.ex, -gt.ez)});
        }
    }

    /** Rectangle entièrement libre (cour), sans bâtiment collé. */
    boolean freeRect(int x0, int z0, int x1, int z1) {
        for (int dz = z0 - 1; dz <= z1 + 1; dz++) {
            for (int dx = x0 - 1; dx <= x1 + 1; dx++) {
                byte z = zoneAt(dx, dz);
                boolean inside = dx >= x0 && dx <= x1 && dz >= z0 && dz <= z1;
                if (inside ? z != YARD : z == LOT) {
                    return false;
                }
            }
        }
        return true;
    }

    Lot claim(int x0, int z0, int x1, int z1, char front, String kind) {
        for (int dz = z0; dz <= z1; dz++) {
            for (int dx = x0; dx <= x1; dx++) {
                zone[ci(dx, dz)] = LOT;
            }
        }
        Lot lot = new Lot(x0, z0, x1, z1, front, kind);
        lots.add(lot);
        return lot;
    }

    /** Cherche une place pour un bâtiment de façade w et de profondeur d, adossé à un côté d'une place. */
    Lot placeFront(int pcz, int w, int d, String sides, String kind) {
        for (char sideOf : sides.toCharArray()) {
            for (int k = 0; k <= 2 * PH; k++) {
                int off = (k + 1) / 2 * ((k & 1) == 1 ? 1 : -1);
                int lo = -(w / 2) + off, hi = lo + w - 1;
                if (lo < -PH - 2 || hi > PH + 2) {
                    continue;
                }
                int x0, z0, x1, z1;
                char front;
                switch (sideOf) {
                    case 'N' -> {
                        x0 = lo;
                        x1 = hi;
                        z1 = pcz - PH - 1;
                        z0 = z1 - d + 1;
                        front = 'S';
                    }
                    case 'S' -> {
                        x0 = lo;
                        x1 = hi;
                        z0 = pcz + PH + 1;
                        z1 = z0 + d - 1;
                        front = 'N';
                    }
                    case 'W' -> {
                        z0 = pcz + lo;
                        z1 = pcz + hi;
                        x1 = -PH - 1;
                        x0 = x1 - d + 1;
                        front = 'E';
                    }
                    default -> {
                        z0 = pcz + lo;
                        z1 = pcz + hi;
                        x0 = PH + 1;
                        x1 = x0 + d - 1;
                        front = 'W';
                    }
                }
                if (freeRect(x0, z0, x1, z1)) {
                    return claim(x0, z0, x1, z1, front, kind);
                }
            }
        }
        return null;
    }

    /** Rectangle d'un bâtiment dont un coin de façade est en (px, pz) et qui s'étend vers les dx/dz croissants le long de la rue. */
    static int[] rect(int px, int pz, int w, int d, char front) {
        return switch (front) {
            case 'N' -> new int[]{px, pz, px + w - 1, pz + d - 1};
            case 'S' -> new int[]{px, pz - d + 1, px + w - 1, pz};
            case 'W' -> new int[]{px, pz, px + d - 1, pz + w - 1};
            default -> new int[]{px - d + 1, pz, px, pz + w - 1};
        };
    }

    /** La porte donne-t-elle sur une rue (directement, ou à un pas) ? */
    boolean doorway(int[] q, char front, boolean commit) {
        Frame f = new Frame(q[0], q[1], q[2], q[3], front);
        int a = f.w / 2;
        byte z1 = zoneAt(f.x(a, -1), f.z(a, -1));
        if (walk(z1)) {
            return true;
        }
        if (z1 == YARD && walk(zoneAt(f.x(a, -2), f.z(a, -2)))) {
            if (commit) {
                zone[ci(f.x(a, -1), f.z(a, -1))] = LANE;
            }
            return true;
        }
        return false;
    }

    /** À défaut de place libre au bord d'une place : le terrain libre sur rue le plus proche. */
    Lot placeAnywhere(int nearX, int nearZ, int w, int d, String kind) {
        double best = Double.MAX_VALUE;
        int[] bq = null;
        char bf = 'N';
        for (int dz = -Rw; dz <= Rw; dz++) {
            for (int dx = -Rw; dx <= Rw; dx++) {
                if (zoneAt(dx, dz) != YARD) {
                    continue;
                }
                double dist = Math.hypot(dx - nearX, dz - nearZ);
                if (dist >= best) {
                    continue;
                }
                for (char front : new char[]{'N', 'S', 'W', 'E'}) {
                    int[] q = rect(dx, dz, w, d, front);
                    if (freeRect(q[0], q[1], q[2], q[3]) && doorway(q, front, false)) {
                        best = dist;
                        bq = q;
                        bf = front;
                        break;
                    }
                }
            }
        }
        if (bq == null) {
            return null;
        }
        doorway(bq, bf, true);
        return claim(bq[0], bq[1], bq[2], bq[3], bf, kind);
    }

    void special(int pcz, int w, int d, String sides, String kind) {
        Lot lot = placeFront(pcz, w, d, sides, kind);
        String where = "au bord de la place";
        if (lot == null) {
            lot = placeAnywhere(0, pcz, w, d, kind);
            where = "hors de la place, faute de terrain libre à son bord";
        }
        if (lot == null) {
            plan.notes.add("avertissement : aucun terrain pour « " + kind + " »");
        } else {
            plan.notes.add(kind + " : " + (lot.x0 + ox) + ", " + (lot.z0 + oz) + " à " + (lot.x1 + ox) + ", " + (lot.z1 + oz)
                    + ", façade " + lot.front + ", " + where);
        }
    }

    void landmarks() {
        special(cn, 19, 19, "N", "donjon");
        special(cs, 15, 11, "S", "hotel-des-ventes");
        special(cn, 11, 21, "WE", "temple");
        special(cn, 9, 9, "EWS", "runes");
        special(cs, 11, 9, "WE", "forge");
        special(cs, 9, 9, "EWN", "alchimie");
    }

    void lanes() {
        for (int x : new int[]{-64, -32, 32, 64}) {
            paintRect(x - 1, -Rw, x + 1, Rw, LANE);
        }
        for (int c : new int[]{cn, cs}) {
            for (int o : new int[]{-30, 30}) {
                paintRect(-Rw, c + o - 1, Rw, c + o + 1, LANE);
            }
        }
    }

    void houses() {
        char[] fronts = {'N', 'S', 'W', 'E'};
        int[][] out = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int dz = -Rw; dz <= Rw; dz++) {
            for (int dx = -Rw; dx <= Rw; dx++) {
                if (zoneAt(dx, dz) != YARD || rnd(dx, dz, 43) < 0.08) {
                    continue;
                }
                for (int k = 0; k < 4; k++) {
                    if (walk(zoneAt(dx + out[k][0], dz + out[k][1])) && tryHouse(dx, dz, fronts[k])) {
                        break;
                    }
                }
            }
        }
    }

    boolean tryHouse(int px, int pz, char front) {
        int w = 6 + (int) (rnd(px, pz, 41) * 5), d = 6 + (int) (rnd(px, pz, 42) * 4);
        for (int attempt = 0; attempt < 6; attempt++) {
            int[] q = rect(px, pz, w, d, front);
            if (freeRect(q[0], q[1], q[2], q[3]) && doorway(q, front, false)) {
                doorway(q, front, true);
                claim(q[0], q[1], q[2], q[3], front, "maison");
                houses++;
                return true;
            }
            if ((attempt & 1) == 0 && w > 6) {
                w--;
            } else if (d > 6) {
                d--;
            } else if (w > 6) {
                w--;
            } else {
                break;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ Sol
    String yardTop(int dx, int dz) {
        if (st.dark) {
            double p = rnd(dx >> 2, dz >> 2, 12);
            return p > 0.8 ? "podzol" : (p < 0.1 ? "coarse_dirt" : "grass_block");
        }
        return "grass_block";
    }

    String plazaBlock(int dx, int dz) {
        int c = dz < 0 ? cn : cs, m = Math.max(Math.abs(dx), Math.abs(dz - c));
        if (m == 7 || m == 13) {
            return st.accent;
        }
        return m == PH || Math.abs(dx) == Math.abs(dz - c) ? st.plazaB : st.plazaA;
    }

    void ground() {
        int yTop = plan.y0 + plan.height - 1;
        short water = plan.id("water[level=0]"), stone = plan.id("stone"), dirt = plan.id("dirt");
        ContinentGenerator.Column col = new ContinentGenerator.Column();
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                int i = ci(dx, dz);
                byte z = zone[i];
                if (z == OUT || z == RING_WATER) {
                    continue;
                }
                if (z == RING) {
                    ring(dx, dz, i, col, yTop);
                    continue;
                }
                if (fTop[i] != NONE) {
                    // Le fleuve reste dans son lit ; on lui rend l'eau prise par les piles de l'ancien pont de la route.
                    for (int y = nTop[i] + 1; y <= fTop[i]; y++) {
                        plan.set(dx, y, dz, water);
                    }
                    for (int y = fTop[i] + 1; y <= yTop; y++) {
                        plan.set(dx, y, dz, CapitalPlan.AIR);
                    }
                    continue;
                }
                int base = Math.min(low[i], g) - 3;
                short under = stone;
                String top;
                switch (z) {
                    case YARD -> {
                        top = yardTop(dx, dz);
                        under = dirt;
                    }
                    case QUAY -> top = mix(st.quay, dx, dz, 5);
                    case LANE -> top = mix(st.lane, dx, dz, 5);
                    case STREET -> top = mix(st.street, dx, dz, 5);
                    case AVENUE, PASSAGE -> top = mix(st.avenue, dx, dz, 5);
                    case PLAZA -> top = plazaBlock(dx, dz);
                    case QUAY_EDGE -> {
                        top = st.coping;
                        base = Math.min(base, g - 6);
                    }
                    default -> top = st.stone;
                }
                for (int y = base; y < g; y++) {
                    plan.set(dx, y, dz, z == QUAY_EDGE ? plan.id(mix(st.wall, dx, dz, 100 + y)) : under);
                }
                put(dx, g, dz, top);
                for (int y = g + 1; y <= yTop; y++) {
                    plan.set(dx, y, dz, CapitalPlan.AIR);
                }
            }
        }
    }

    /** Hors les murs : le terrain rejoint en pente douce son relief naturel, les routes avec lui. */
    void ring(int dx, int dz, int i, ContinentGenerator.Column col, int yTop) {
        double r = Math.hypot(dx, dz);
        double t = Math.max(0, Math.min(1, (r - (Rw + 9)) / (double) (Ro - (Rw + 9))));
        double sm = t * t * (3 - 2 * t);
        int wx = ox + dx, wz = oz + dz, top;
        boolean road = !Float.isNaN(roadY[i]), slab = false;
        String surface;
        col.biome = bio[i];
        col.royal = royal[i];
        if (parvis[i]) {
            top = g;
            surface = mix(st.avenue, dx, dz, 5);
            road = true;
        } else if (road) {
            double q = Math.round(((g + 1) + (roadY[i] - (g + 1)) * sm) * 2) / 2.0;
            top = (int) Math.floor(q) - 1;
            slab = q - Math.floor(q) >= 0.5;
            Material m = ContinentGenerator.roadBlock(col, wx, wz);
            surface = name(slab && m == Material.DIRT_PATH ? Material.COARSE_DIRT : m);
        } else {
            top = (int) Math.floor((g + 1.5) + (nTop[i] - g) * sm) - 1;
            // Berge : jamais sous l'eau de la colonne voisine.
            for (int k = 0; k < 4; k++) {
                int nx = dx + (k == 0 ? -1 : (k == 1 ? 1 : 0)), nz = dz + (k == 2 ? -1 : (k == 3 ? 1 : 0));
                if (inGrid(nx, nz) && fTop[ci(nx, nz)] != NONE && fTop[ci(nx, nz)] > top) {
                    top = fTop[ci(nx, nz)];
                }
            }
            surface = name(ContinentGenerator.surface(bio[i], wx, wz, top, 0));
        }
        short fill = plan.id(name(ContinentGenerator.filler(bio[i], 0)));
        for (int y = Math.min(low[i], top) - 3; y < top; y++) {
            plan.set(dx, y, dz, fill);
        }
        put(dx, top, dz, surface);
        int y = top + 1;
        if (slab) {
            put(dx, y++, dz, name(ContinentGenerator.roadSlab(col)));
        } else if (!road && surface.equals("grass_block")) {
            double p = rnd(dx, dz, 31);
            if (p < 0.2) {
                put(dx, y++, dz, p < 0.192 ? "short_grass" : mix(st.flowers, dx, dz, 32));
            }
        }
        int clearTo = r <= Rw + 16 ? yTop : Math.max(top, Math.max(nTop[i], low[i])) + 8;
        for (; y <= clearTo; y++) {
            plan.set(dx, y, dz, CapitalPlan.AIR);
        }
    }

    // ------------------------------------------------------------------ Rempart
    void wall() {
        int top = g + WALL_H;
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                int i = ci(dx, dz);
                if (zone[i] != WALL) {
                    continue;
                }
                double r = Math.hypot(dx, dz);
                int arc = (int) Math.floor(Math.atan2(dz, dx) * Rw);
                int from;
                if (fTop[i] != NONE) {
                    // Au-dessus du fleuve : des piles, et entre elles des arches où l'eau passe.
                    int m = Math.floorMod(arc, 8);
                    from = m < 2 ? nTop[i] + 1 : Math.max(fTop[i] + 2, g + 1 + Math.min(m - 2, 7 - m));
                } else {
                    from = Math.min(low[i], g) - 2;
                }
                for (int y = from; y < top; y++) {
                    put(dx, y, dz, mix(st.wall, dx, dz, 100 + y));
                }
                put(dx, top, dz, st.walk);
                if (r > Rw + 1.0) {
                    put(dx, top + 1, dz, mix(st.wall, dx, dz, 101 + top));
                    if (Math.floorMod(arc, 4) < 2) {
                        put(dx, top + 2, dz, mix(st.wall, dx, dz, 102 + top));
                    }
                }
            }
        }
    }

    void towers() {
        for (Tower t : towers) {
            int top = g + t.h, span = (int) Math.ceil(t.rad) + 2, floor = g + WALL_H;
            // Direction cardinale qui regarde vers la ville : la porte est de ce côté, l'échelle en face.
            int ddx = 0, ddz = 0;
            if (Math.abs(t.cx) > Math.abs(t.cz)) {
                ddx = t.cx > 0 ? -1 : 1;
            } else {
                ddz = t.cz > 0 ? -1 : 1;
            }
            for (int oz2 = -span; oz2 <= span; oz2++) {
                for (int ox2 = -span; ox2 <= span; ox2++) {
                    double d = Math.hypot(ox2, oz2);
                    int x = t.cx + ox2, z = t.cz + oz2;
                    if (d > t.rad || !inGrid(x, z)) {
                        continue;
                    }
                    int i = ci(x, z), base = fTop[i] != NONE ? nTop[i] + 1 : Math.min(low[i], g) - 2;
                    if (d > t.rad - 1.35) {
                        for (int y = base; y <= top; y++) {
                            put(x, y, z, mix(st.wall, x, z, 100 + y));
                        }
                        // Le chemin de ronde traverse la tour.
                        if (Math.abs(Math.hypot(x, z) - (Rw - 0.4)) <= 1.3) {
                            air(x, floor + 1, z);
                            air(x, floor + 2, z);
                        }
                    } else {
                        for (int y = base; y < g; y++) {
                            put(x, y, z, "stone");
                        }
                        put(x, g, z, mix(st.quay, x, z, 5));
                        for (int y = g + 1; y < top; y++) {
                            air(x, y, z);
                        }
                        put(x, floor, z, st.planks);
                        put(x, top, z, st.planks);
                    }
                }
            }
            int inner = (int) Math.floor(t.rad - 1.35);
            for (int k = inner + 1; k <= (int) Math.ceil(t.rad); k++) {
                air(t.cx + k * ddx, g + 1, t.cz + k * ddz);
                air(t.cx + k * ddx, g + 2, t.cz + k * ddz);
            }
            String facing = dirName(ddx, ddz);
            for (int y = g + 1; y <= floor + 1; y++) {
                put(t.cx - inner * ddx, y, t.cz - inner * ddz, "ladder[facing=" + facing + "]");
            }
            put(t.cx, floor - 1, t.cz, st.lantern + "[hanging=true]");
            put(t.cx, top - 1, t.cz, st.lantern + "[hanging=true]");
            // Toit en poivrière.
            int y = top + 1;
            for (double rk = t.rad + 1.2; rk >= 0.5; rk -= 0.8, y++) {
                int k = (int) Math.ceil(rk);
                for (int oz2 = -k; oz2 <= k; oz2++) {
                    for (int ox2 = -k; ox2 <= k; ox2++) {
                        if (Math.hypot(ox2, oz2) <= rk) {
                            put(t.cx + ox2, y, t.cz + oz2, st.cone);
                        }
                    }
                }
            }
            put(t.cx, y, t.cz, "lightning_rod[facing=up]");
        }
    }

    void gatehouses() {
        for (Gate gt : gates) {
            int cx = (int) Math.round(Rw * gt.ex), cz = (int) Math.round(Rw * gt.ez), roof = g + WALL_H + 3;
            for (int dz = cz - 14; dz <= cz + 14; dz++) {
                for (int dx = cx - 14; dx <= cx + 14; dx++) {
                    if (!inGrid(dx, dz)) {
                        continue;
                    }
                    int i = ci(dx, dz);
                    double a = dx * gt.ex + dz * gt.ez - Rw, b = -dx * gt.ez + dz * gt.ex;
                    if ((zone[i] != GATE && zone[i] != PASSAGE) || Math.abs(a) > 5.5 || Math.abs(b) > gt.half + 4.5) {
                        continue;
                    }
                    int from = Math.min(low[i], g) - 2;
                    if (zone[i] == PASSAGE) {
                        from = Math.abs(b) > gt.half - 1.1 ? g + 5 : g + 6;
                    }
                    for (int y = from; y <= roof; y++) {
                        put(dx, y, dz, mix(st.wall, dx, dz, 100 + y));
                    }
                    if (Math.abs(a) > 4.4 || Math.abs(b) > gt.half + 3.4) {
                        put(dx, roof + 1, dz, mix(st.wall, dx, dz, 101 + roof));
                        if ((((int) Math.floor(a) + (int) Math.floor(b)) & 1) == 0) {
                            put(dx, roof + 2, dz, mix(st.wall, dx, dz, 102 + roof));
                        }
                    }
                }
            }
            // Lanterne sous la voûte, bannières au-dessus de l'entrée.
            int lx = (int) Math.round(Rw * gt.ex), lz = (int) Math.round(Rw * gt.ez);
            if (zoneAt(lx, lz) == PASSAGE && plan.get(lx, g + 6, lz) > CapitalPlan.AIR) {
                put(lx, g + 5, lz, st.lantern + "[hanging=true]");
            }
            int rot = Math.floorMod(Math.round(yaw(gt.ex, gt.ez) / 22.5f), 16);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                double b = sgn * (gt.half + 1.5);
                int bx = (int) Math.round((Rw + 5) * gt.ex - b * gt.ez), bz = (int) Math.round((Rw + 5) * gt.ez + b * gt.ex);
                if (zoneAt(bx, bz) == GATE) {
                    put(bx, topOf(bx, bz, roof), bz, st.banner + "_banner[rotation=" + rot + "]");
                }
            }
        }
    }

    // ------------------------------------------------------------------ Ponts (du nord au sud)
    boolean land(int dx, int dz) {
        byte z = zoneAt(dx, dz);
        return (z >= YARD && z <= PLAZA) || z == QUAY_EDGE;
    }

    void bridge(int bx, int hw, boolean stone) {
        int lim = Rw - 9, zMin = Integer.MAX_VALUE, zMax = Integer.MIN_VALUE;
        for (int dz = -lim; dz <= lim; dz++) {
            for (int dx = bx - hw; dx <= bx + hw; dx++) {
                if (Math.hypot(dx, dz) < lim && zoneAt(dx, dz) == WATER) {
                    zMin = Math.min(zMin, dz);
                    zMax = Math.max(zMax, dz);
                }
            }
        }
        if (zMin > zMax) {
            return;
        }
        int zA = zMin - 2, zB = zMax + 2, len = zMax - zMin + 1;
        if (len > 48 || !land(bx, zA - 1) || !land(bx, zB + 1)) {
            if (stone) {
                plan.notes.add("avertissement : pas de grand pont possible en dx " + bx + " (traversée de " + len + " blocs)");
            }
            return;
        }
        String deckB = "spruce_planks", slabB = "spruce_slab", edgeB = "stripped_spruce_log[axis=z]",
                parapet = "spruce_fence[north=true,south=true]", pierB = "spruce_log[axis=y]", lamp = "spruce_fence";
        if (stone) {
            slabB = st.slab;
            edgeB = st.stone;
            parapet = st.parapet + "[north=low,south=low,up=false]";
            pierB = st.stone;
            lamp = st.post;
        }
        int arches = Math.max(2, Math.round(len / 9f));
        boolean[] pier = new boolean[zB - zA + 1];
        for (int k = 1; k < arches; k++) {
            int pz = zMin + len * k / arches;
            pier[pz - zA] = true;
            if (stone) {
                pier[pz + 1 - zA] = true;
            }
        }
        int maxRise = stone ? 4 : 2;
        for (int z = zA; z <= zB; z++) {
            int t = Math.min(z - zA, zB - z), rise = Math.min(t, maxRise), full = g + rise / 2;
            boolean half = (rise & 1) == 1, isPier = pier[z - zA];
            boolean lit = z == zA || z == zB || (isPier && !pier[z - zA - 1]);
            for (int dx = bx - hw; dx <= bx + hw; dx++) {
                int i = ci(dx, z);
                boolean edge = Math.abs(dx - bx) == hw, wet = fTop[i] != NONE;
                zone[i] = BRIDGE;
                int deck = full + (edge && half ? 1 : 0);
                if (!wet) {
                    for (int y = g + 1; y < deck; y++) {
                        put(dx, y, z, pierB);
                    }
                } else if (isPier && (stone || edge)) {
                    for (int y = nTop[i] + 1; y < deck; y++) {
                        put(dx, y, z, pierB);
                    }
                } else if (stone && deck - 1 > fTop[i]) {
                    // Amorce de voûte contre une pile ou contre la berge.
                    boolean south = z < zB && (pier[z + 1 - zA] || !wetAt(dx, z + 1));
                    boolean north = z > zA && (pier[z - 1 - zA] || !wetAt(dx, z - 1));
                    if (south || north) {
                        put(dx, deck - 1, z, st.stairs + "[facing=" + (south ? "south" : "north") + ",half=top]");
                    }
                }
                put(dx, deck, z, edge ? edgeB : (stone ? mix(st.avenue, dx, z, 5) : deckB));
                if (!edge) {
                    if (half) {
                        put(dx, deck + 1, z, slabB);
                    }
                } else if (lit) {
                    put(dx, deck + 1, z, lamp);
                    put(dx, deck + 2, z, st.lantern);
                } else {
                    put(dx, deck + 1, z, parapet);
                }
            }
        }
        if (bx == 0) {
            plan.points.put("pont", new double[]{ox + 0.5, g + maxRise / 2 + 1, oz + (zA + zB) / 2 + 0.5, 0});
            plan.notes.add("grand pont de dz " + zA + " à " + zB);
        }
    }

    // ------------------------------------------------------------------ Bâtiments
    /** Repère d'un bâtiment : a le long de la façade, b vers le fond. b négatif : devant la façade. */
    final class Frame {
        final int x0, z0, ax, az, bx, bz, w, d;
        final String in, out, dirA, dirAm, axisA, axisB, paneA, paneB;

        Frame(int rx0, int rz0, int rx1, int rz1, char front) {
            int sx, sz, fax, faz, fbx, fbz;
            switch (front) {
                case 'N' -> {
                    sx = rx0;
                    sz = rz0;
                    fax = 1;
                    faz = 0;
                    fbx = 0;
                    fbz = 1;
                }
                case 'S' -> {
                    sx = rx1;
                    sz = rz1;
                    fax = -1;
                    faz = 0;
                    fbx = 0;
                    fbz = -1;
                }
                case 'W' -> {
                    sx = rx0;
                    sz = rz1;
                    fax = 0;
                    faz = -1;
                    fbx = 1;
                    fbz = 0;
                }
                default -> {
                    sx = rx1;
                    sz = rz0;
                    fax = 0;
                    faz = 1;
                    fbx = -1;
                    fbz = 0;
                }
            }
            x0 = sx;
            z0 = sz;
            ax = fax;
            az = faz;
            bx = fbx;
            bz = fbz;
            boolean alongX = fax != 0;
            w = alongX ? rx1 - rx0 + 1 : rz1 - rz0 + 1;
            d = alongX ? rz1 - rz0 + 1 : rx1 - rx0 + 1;
            in = dirName(bx, bz);
            out = dirName(-bx, -bz);
            dirA = dirName(ax, az);
            dirAm = dirName(-ax, -az);
            axisA = alongX ? "x" : "z";
            axisB = alongX ? "z" : "x";
            paneA = alongX ? "[east=true,west=true]" : "[north=true,south=true]";
            paneB = alongX ? "[north=true,south=true]" : "[east=true,west=true]";
        }

        int x(int a, int b) {
            return x0 + a * ax + b * bx;
        }

        int z(int a, int b) {
            return z0 + a * az + b * bz;
        }

        void put(int a, int y, int b, String state) {
            CapitalDesign.this.put(x(a, b), y, z(a, b), state);
        }

        void air(int a, int y, int b) {
            CapitalDesign.this.air(x(a, b), y, z(a, b));
        }

        void putIfFree(int a, int y, int b, String state) {
            CapitalDesign.this.putIfFree(x(a, b), y, z(a, b), state);
        }

        /** Bannière sur pied tournée vers la rue. */
        int rotation() {
            return bz < 0 ? 0 : (bz > 0 ? 8 : (bx > 0 ? 4 : 12));
        }
    }

    static boolean window(int p, int n, int door, int doorW) {
        if (p < 2 || p > n - 3) {
            return false;
        }
        if (door >= 0) {
            int lo = door - (doorW - 1);
            if (p >= lo - 1 && p <= door + 1) {
                return false;
            }
            return (p > door ? p - door : lo - p) % 3 == 0;
        }
        return (Math.min(p, n - 1 - p) - 2) % 3 == 0;
    }

    /** Murs, planchers, porte, fenêtres et toit d'un bâtiment de w sur d, posé sur le pavé. */
    void building(Frame f, Spec sp) {
        int w = f.w, d = f.d, top = g + sp.storeys * sp.storeyH, pc = w / 2;
        for (int a = 0; a < w; a++) {
            for (int b = 0; b < d; b++) {
                boolean wa = a == 0 || a == w - 1, wb = b == 0 || b == d - 1;
                if (!wa && !wb) {
                    f.put(a, g, b, sp.floor);
                    for (int y = g + 1; y <= top; y++) {
                        if ((y - g) % sp.storeyH == 0) {
                            f.put(a, y, b, sp.floor);
                        } else {
                            f.air(a, y, b);
                        }
                    }
                    continue;
                }
                f.put(a, g, b, sp.base);
                boolean corner = wa && wb;
                int p = wb ? a : b, n = wb ? w : d;
                for (int y = g + 1; y <= top; y++) {
                    int k = (y - g - 1) / sp.storeyH, ly = (y - g) - k * sp.storeyH;
                    String state;
                    if (corner) {
                        state = log(sp.frame, "y");
                    } else if (ly == sp.storeyH) {
                        state = log(sp.frame, wb ? f.axisA : f.axisB);
                    } else if (k == 0 && ly == 1) {
                        state = sp.base;
                    } else {
                        state = k == 0 ? sp.infill0 : sp.infill1;
                        if (ly >= sp.winLo && ly <= sp.winHi && window(p, n, b == 0 ? pc : -1, sp.doorW)) {
                            state = sp.glass + (wb ? f.paneA : f.paneB);
                        }
                    }
                    f.put(a, y, b, state);
                }
            }
        }
        for (int k = 0; k < sp.doorW; k++) {
            String hinge = k == 0 ? "left" : "right";
            f.put(pc - k, g + 1, 0, sp.door + "[facing=" + f.in + ",half=lower,hinge=" + hinge + "]");
            f.put(pc - k, g + 2, 0, sp.door + "[facing=" + f.in + ",half=upper,hinge=" + hinge + "]");
            if (sp.storeyH > 4) {
                f.put(pc - k, g + 3, 0, sp.infill0);
            }
            int fx = f.x(pc - k, -1), fz = f.z(pc - k, -1);
            if (inGrid(fx, fz)) {
                doorFront[ci(fx, fz)] = true;
            }
        }
        if (sp.flat) {
            for (int a = 0; a < w; a++) {
                for (int b = 0; b < d; b++) {
                    if (a == 0 || a == w - 1 || b == 0 || b == d - 1) {
                        f.put(a, top + 1, b, sp.infill1);
                        if (((a + b) & 1) == 0) {
                            f.put(a, top + 2, b, sp.infill1);
                        }
                    }
                }
            }
        } else {
            // Toit à deux pans ; le faîte suit la façade, ou lui est perpendiculaire (pignon sur rue).
            int across = sp.gableFront ? w : d, along = sp.gableFront ? d : w;
            String up = sp.gableFront ? f.dirA : f.in, down = sp.gableFront ? f.dirAm : f.out;
            for (int i = -1; i <= across; i++) {
                int k = Math.min(i + 1, across - i);
                boolean ridge = across % 2 == 1 && i == across / 2;
                String state = ridge ? sp.roof + "_slab[type=bottom]"
                        : sp.roof + "_stairs[facing=" + (i < across / 2.0 ? up : down) + ",half=bottom]";
                for (int j = -1; j <= along; j++) {
                    int a = sp.gableFront ? i : j, b = sp.gableFront ? j : i;
                    if (i < 0 || i >= across || j < 0 || j >= along) {
                        f.putIfFree(a, top + k, b, state);
                        continue;
                    }
                    f.put(a, top + k, b, state);
                    if (j == 0 || j == along - 1) {
                        for (int y = top + 1; y < top + k; y++) {
                            f.put(a, y, b, sp.infill1);
                        }
                    }
                }
            }
        }
        // Lanternes sous chaque plafond.
        for (int k = 1; k <= sp.storeys; k++) {
            for (int a = Math.max(2, w / 2 - 6 * ((w - 4) / 12)); a <= w - 3; a += 6) {
                for (int b = Math.max(2, d / 2 - 6 * ((d - 4) / 12)); b <= d - 3; b += 6) {
                    f.put(a, g + k * sp.storeyH - 1, b, st.lantern + "[hanging=true]");
                }
            }
        }
        if (sp.furnish) {
            f.put(2, g + 1, d - 2, sp.bed + "_bed[facing=" + f.dirAm + ",part=foot]");
            f.put(1, g + 1, d - 2, sp.bed + "_bed[facing=" + f.dirAm + ",part=head]");
            f.put(1, g + 1, 1, "bookshelf");
            f.put(1, g + 2, 1, "bookshelf");
            f.put(w - 2, g + 1, 1, st.fence);
            f.put(w - 2, g + 2, 1, "brown_carpet");
        }
        if (sp.chimney) {
            int a = w - 2, b = d - 2, k = sp.flat ? 1 : Math.min(2, sp.gableFront ? Math.min(a + 1, w - a) : Math.min(b + 1, d - b));
            for (int y = g + 1; y <= top + k + 1; y++) {
                f.put(a, y, b, st.chimney);
            }
            f.put(a, top + k + 2, b, "campfire[lit=true]");
        }
    }

    /** Tour carrée creuse, de yFrom à yTop (plancher), avec ou sans créneaux. */
    void squareTower(Frame f, int a0, int b0, int size, int yFrom, int yTop, String block, boolean crenel) {
        for (int a = a0; a < a0 + size; a++) {
            for (int b = b0; b < b0 + size; b++) {
                boolean edge = a == a0 || a == a0 + size - 1 || b == b0 || b == b0 + size - 1;
                for (int y = yFrom; y <= yTop; y++) {
                    if (edge || y == yTop) {
                        f.put(a, y, b, block);
                    } else {
                        f.air(a, y, b);
                    }
                }
                if (crenel && edge) {
                    f.put(a, yTop + 1, b, block);
                    if (((a + b) & 1) == 0) {
                        f.put(a, yTop + 2, b, block);
                    }
                }
            }
        }
    }

    /** Toit en pyramide : un anneau d'escaliers par niveau, centré sur (cx, cz). */
    void pyramid(int cx, int cz, int half, int y, String roof) {
        for (int k = 0; k <= half; k++) {
            int m = half - k;
            for (int dz = -m; dz <= m; dz++) {
                for (int dx = -m; dx <= m; dx++) {
                    boolean ex = Math.abs(dx) == m, ez = Math.abs(dz) == m;
                    if (!ex && !ez) {
                        continue;
                    }
                    String state = (ex && ez) ? roof + "_slab[type=bottom]"
                            : roof + "_stairs[facing=" + (ex ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south")) + ",half=bottom]";
                    put(cx + dx, y + k, cz + dz, state);
                }
            }
        }
        put(cx, y + half + 1, cz, "lightning_rod[facing=up]");
    }

    void build(Lot lot) {
        Frame f = new Frame(lot.x0, lot.z0, lot.x1, lot.z1, lot.front);
        switch (lot.kind) {
            case "donjon" -> keep(f);
            case "temple" -> temple(f);
            case "hotel-des-ventes" -> hall(f);
            case "forge" -> forge(f);
            case "alchimie" -> alchemy(f);
            case "runes" -> runes(f);
            default -> house(f, lot);
        }
    }

    Spec common(int x, int z) {
        Spec sp = new Spec();
        sp.frame = mix(st.frames, x, z, 51);
        sp.base = mix(st.bases, x, z, 52);
        sp.infill1 = mix(st.infills, x, z, 53);
        sp.infill0 = sp.infill1;
        sp.floor = mix(st.floors, x, z, 54);
        sp.roof = mix(st.roofs, x, z, 55);
        sp.door = st.door;
        sp.glass = st.glass;
        return sp;
    }

    void house(Frame f, Lot lot) {
        Spec sp = common(lot.x0, lot.z0);
        if (rnd(lot.x0, lot.z0, 56) < 0.3) {
            sp.infill0 = sp.base;
        }
        sp.storeys = f.w >= 7 && f.d >= 7 && rnd(lot.x0, lot.z0, 57) < 0.6 ? 2 : 1;
        sp.gableFront = f.d > f.w || (f.d == f.w && rnd(lot.x0, lot.z0, 58) < 0.5);
        sp.chimney = rnd(lot.x0, lot.z0, 59) < 0.45;
        sp.bed = pick(rnd(lot.x0, lot.z0, 60), new Object[]{"red", 30, "white", 25, "brown", 20, st.banner, 25});
        building(f, sp);
    }

    void keep(Frame f) {
        Spec sp = new Spec();
        sp.storeys = 2;
        sp.storeyH = 5;
        sp.winLo = 3;
        sp.winHi = 4;
        sp.frame = st.stone;
        sp.base = st.stoneBase;
        sp.infill0 = st.stone;
        sp.infill1 = st.stone;
        sp.floor = st.planks;
        sp.flat = true;
        sp.door = st.door;
        sp.doorW = 2;
        sp.glass = st.glass;
        sp.furnish = false;
        building(f, sp);
        int top = g + 10, w = f.w, d = f.d;
        for (int[] c : new int[][]{{0, 0}, {w - 4, 0}, {0, d - 4}, {w - 4, d - 4}}) {
            squareTower(f, c[0], c[1], 4, top + 1, top + 4, st.stone, true);
        }
        // Le donjon, au fond, domine la ville.
        int a0 = (w - 9) / 2, b0 = d - 11, high = g + 23;
        squareTower(f, a0, b0, 9, top + 1, high, st.stone, true);
        for (int[] c : new int[][]{{a0, b0}, {a0 + 8, b0}, {a0, b0 + 8}, {a0 + 8, b0 + 8}}) {
            int x = f.x(c[0], c[1]), z = f.z(c[0], c[1]);
            put(x, topOf(x, z, high), z, st.banner + "_banner[rotation=" + f.rotation() + "]");
        }
        for (int a : new int[]{w / 2 - 5, w / 2 + 4}) {
            f.put(a, g + 8, -1, st.banner + "_wall_banner[facing=" + f.out + "]");
        }
    }

    void temple(Frame f) {
        Spec sp = new Spec();
        sp.storeyH = 9;
        sp.winLo = 3;
        sp.winHi = 6;
        sp.frame = st.pillar;
        sp.base = st.stoneBase;
        sp.infill0 = st.pale;
        sp.infill1 = st.pale;
        sp.floor = st.paleFloor;
        sp.roof = st.bigRoof;
        sp.gableFront = true;
        sp.door = st.door;
        sp.doorW = 2;
        sp.glass = st.stained;
        sp.furnish = false;
        building(f, sp);
        int w = f.w, d = f.d, pc = w / 2;
        // Clocher au chevet.
        int a0 = (w - 5) / 2, b0 = d - 5, top = g + 22;
        squareTower(f, a0, b0, 5, g + 10, top, st.pale, false);
        for (int y = top - 4; y <= top - 2; y++) {
            for (int k = 1; k <= 3; k++) {
                f.air(a0 + k, y, b0);
                f.air(a0 + k, y, b0 + 4);
                f.air(a0, y, b0 + k);
                f.air(a0 + 4, y, b0 + k);
            }
        }
        f.put(a0 + 2, top - 1, b0 + 2, "bell[attachment=ceiling,facing=" + f.out + "]");
        pyramid(f.x(a0 + 2, b0 + 2), f.z(a0 + 2, b0 + 2), 3, top + 1, sp.roof);
        // Autel et bancs.
        f.put(pc, g + 1, d - 3, "chiseled_quartz_block");
        f.put(pc, g + 2, d - 3, "candle[candles=4,lit=true]");
        for (int b = 4; b <= d - 7; b += 2) {
            for (int a = 2; a <= w - 3; a++) {
                if (a < pc - 1 || a > pc + 1) {
                    f.put(a, g + 1, b, (st.dark ? "dark_oak" : "oak") + "_stairs[facing=" + f.out + ",half=bottom]");
                }
            }
        }
        stall(f, "priest", "autel", "white_wool", "yellow_wool");
    }

    void hall(Frame f) {
        Spec sp = common(0, cs);
        sp.storeys = 2;
        sp.storeyH = 5;
        sp.winLo = 2;
        sp.winHi = 4;
        sp.infill0 = st.stone;
        sp.roof = (String) st.roofs[0];
        sp.doorW = 2;
        sp.furnish = false;
        building(f, sp);
        for (int a : new int[]{f.w / 2 - 5, f.w / 2 + 4}) {
            f.put(a, g + 8, -1, st.banner + "_wall_banner[facing=" + f.out + "]");
        }
        // Comptoirs le long du fond.
        for (int a = 2; a <= f.w - 3; a++) {
            if (a % 4 != 0) {
                f.put(a, g + 1, f.d - 3, a % 4 == 2 ? "barrel[facing=up]" : "bookshelf");
            }
        }
        stall(f, "merchant", "hotel-des-ventes", "red_wool", "yellow_wool");
    }

    void forge(Frame f) {
        Spec sp = common(1, cs);
        sp.storeyH = 5;
        sp.infill0 = st.dark ? "cobbled_deepslate" : "cobblestone";
        sp.infill1 = sp.infill0;
        sp.roof = st.dark ? "blackstone" : "stone_brick";
        sp.chimney = true;
        sp.furnish = false;
        building(f, sp);
        stall(f, "blacksmith", "forge", "orange_wool", "gray_wool");
    }

    void alchemy(Frame f) {
        Spec sp = common(2, cs);
        sp.storeys = 2;
        sp.roof = st.dark ? "crimson" : "dark_prismarine";
        sp.furnish = false;
        building(f, sp);
        stall(f, "alchemist", "alchimie", "magenta_wool", "white_wool");
    }

    void runes(Frame f) {
        Spec sp = common(3, cn);
        sp.storeys = 2;
        sp.infill0 = st.dark ? "polished_tuff" : "smooth_sandstone";
        sp.infill1 = sp.infill0;
        sp.roof = st.dark ? "mangrove" : "dark_oak";
        sp.furnish = false;
        building(f, sp);
        stall(f, "runesmith", "runes", "purple_wool", "black_wool");
    }

    /** Étal devant la porte : auvent, accessoires du métier, et le PNJ au milieu, tourné vers la place. */
    void stall(Frame f, String tag, String point, String woolA, String woolB) {
        int pc = f.w / 2;
        for (int a = pc - 3; a <= pc + 3; a++) {
            for (int b = -3; b <= -1; b++) {
                for (int y = g + 1; y <= g + 3; y++) {
                    f.air(a, y, b);
                }
                f.put(a, g + 4, b, ((a - pc) & 1) == 0 ? woolA : woolB);
            }
        }
        for (int a : new int[]{pc - 3, pc + 3}) {
            for (int y = g + 1; y <= g + 3; y++) {
                f.put(a, y, -3, st.fence);
            }
        }
        String lantern = (tag.equals("runesmith") ? "soul_lantern" : st.lantern) + "[hanging=true]";
        f.put(pc - 2, g + 3, -2, lantern);
        f.put(pc + 2, g + 3, -2, lantern);
        switch (tag) {
            case "blacksmith" -> {
                f.put(pc - 3, g + 1, -1, "blast_furnace[facing=" + f.out + "]");
                f.put(pc - 2, g + 1, -1, "anvil[facing=" + f.dirA + "]");
                f.put(pc + 2, g + 1, -1, "smithing_table");
                f.put(pc + 3, g + 1, -1, "lava_cauldron");
            }
            case "alchemist" -> {
                f.put(pc - 3, g + 1, -1, "barrel[facing=up]");
                f.put(pc - 3, g + 2, -1, "brewing_stand");
                f.put(pc - 2, g + 1, -1, "water_cauldron[level=3]");
                f.put(pc + 2, g + 1, -1, "barrel[facing=up]");
                f.put(pc + 2, g + 2, -1, "potted_allium");
                f.put(pc + 3, g + 1, -1, "barrel[facing=up]");
                f.put(pc + 3, g + 2, -1, "barrel[facing=up]");
            }
            case "priest" -> {
                f.put(pc - 2, g + 1, -1, "lectern[facing=" + f.out + "]");
                for (int a : new int[]{pc - 3, pc + 2, pc + 3}) {
                    f.put(a, g + 1, -1, "chiseled_quartz_block");
                    f.put(a, g + 2, -1, "candle[candles=3,lit=true]");
                }
            }
            case "runesmith" -> {
                f.put(pc - 3, g + 1, -1, "chiseled_bookshelf[facing=" + f.out + "]");
                f.put(pc - 3, g + 2, -1, "chiseled_bookshelf[facing=" + f.out + "]");
                f.put(pc - 2, g + 1, -1, "enchanting_table");
                f.put(pc + 2, g + 1, -1, "lodestone");
                f.put(pc + 3, g + 1, -1, "amethyst_block");
                f.put(pc + 3, g + 2, -1, "amethyst_cluster[facing=up]");
            }
            default -> {
                f.put(pc - 3, g + 1, -1, "bookshelf");
                f.put(pc - 3, g + 2, -1, "bookshelf");
                f.put(pc - 2, g + 1, -1, "cartography_table");
                f.put(pc + 2, g + 1, -1, "fletching_table");
                f.put(pc + 3, g + 1, -1, "bell[attachment=floor,facing=" + f.out + "]");
            }
        }
        float look = yaw(-f.bx, -f.bz);
        plan.npcs.add(new CapitalPlan.Npc(tag, ox + f.x(pc, -2) + 0.5, g + 1, oz + f.z(pc, -2) + 0.5, look));
        plan.points.put(point, new double[]{ox + f.x(pc, -6) + 0.5, g + 1, oz + f.z(pc, -6) + 0.5, yaw(f.bx, f.bz)});
    }

    // ------------------------------------------------------------------ Décor
    void plazaDecor() {
        // Fontaine de la Grand-Place.
        for (int dz = -5; dz <= 5; dz++) {
            for (int dx = -5; dx <= 5; dx++) {
                double d = Math.hypot(dx, dz);
                if (d <= 3.5) {
                    put(dx, g, cs + dz, st.stone);
                    put(dx, g + 1, cs + dz, "water[level=0]");
                } else if (d <= 4.6) {
                    put(dx, g + 1, cs + dz, st.rim);
                }
            }
        }
        for (int y = g + 1; y <= g + 3; y++) {
            put(0, y, cs, log(st.pillar, "y"));
        }
        put(0, g + 4, cs, st.light);
        // Monument du parvis.
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                put(dx, g + 1, cn + dz, st.rim);
            }
        }
        for (int y = g + 2; y <= g + 6; y++) {
            put(0, y, cn, log(st.pillar, "y"));
        }
        put(0, g + 7, cn, st.light);
        if (st.dark) {
            put(0, g + 8, cn, "amethyst_cluster[facing=up]");
        }
        for (int c : new int[]{cn, cs}) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    int x = sx * 7, z = c + sz * 7;
                    put(x, g + 1, z, st.post);
                    put(x, g + 2, z, st.fence);
                    put(x, g + 3, z, st.fence);
                    put(x, g + 4, z, st.banner + "_banner[rotation=" + (sz > 0 ? 0 : 8) + "]");
                }
            }
        }
        // Étals du marché, aux quatre coins de la Grand-Place.
        String[] wools = {"red_wool", "white_wool", "lime_wool", "white_wool", "light_blue_wool", "white_wool", st.banner + "_wool", "white_wool"};
        String[] goods = {"hay_block[axis=y]", "barrel[facing=up]", "melon", "pumpkin", "barrel[facing=up]", "hay_block[axis=y]", "melon", "barrel[facing=up]"};
        int n = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2, n++) {
                int x = sx * 11, z = cs + sz * 11;
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        put(x + dx, g + 3, z + dz, wools[n * 2 + ((dx + dz) & 1)]);
                        if (dx != 0 && dz != 0) {
                            put(x + dx, g + 1, z + dz, st.fence);
                            put(x + dx, g + 2, z + dz, st.fence);
                        }
                    }
                }
                put(x, g + 1, z, goods[n * 2]);
                put(x - sx, g + 1, z, goods[n * 2 + 1]);
            }
        }
    }

    boolean near(int dx, int dz, int reach, byte code) {
        for (int oz2 = -reach; oz2 <= reach; oz2++) {
            for (int ox2 = -reach; ox2 <= reach; ox2++) {
                if (zoneAt(dx + ox2, dz + oz2) == code) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Cours et jardins : herbes, fleurs et quelques arbres à l'écart des toits. */
    void yards() {
        List<int[]> placed = new ArrayList<>();
        for (int dz = -Rw; dz <= Rw; dz++) {
            for (int dx = -Rw; dx <= Rw; dx++) {
                if (zoneAt(dx, dz) != YARD || !free(dx, g + 1, dz)) {
                    continue;
                }
                if (rnd(dx, dz, 61) < 0.07 && !near(dx, dz, 3, LOT)) {
                    boolean alone = true;
                    for (int[] t : placed) {
                        if (Math.abs(t[0] - dx) < 5 && Math.abs(t[1] - dz) < 5) {
                            alone = false;
                            break;
                        }
                    }
                    if (alone) {
                        placed.add(new int[]{dx, dz});
                        tree(dx, dz);
                        continue;
                    }
                }
                double p = rnd(dx, dz, 62);
                if (p < 0.3 && plan.get(dx, g, dz) == plan.id("grass_block")) {
                    put(dx, g + 1, dz, p < 0.24 ? "short_grass" : mix(st.flowers, dx, dz, 63));
                }
            }
        }
        trees = placed.size();
    }

    void tree(int dx, int dz) {
        int h = 4 + (rnd(dx, dz, 64) < 0.5 ? 1 : 0);
        put(dx, g, dz, "dirt");
        for (int y = g + 1; y <= g + h; y++) {
            put(dx, y, dz, st.log + "[axis=y]");
        }
        String leaves = st.leaves + "[persistent=true]";
        for (int y = g + h - 1; y <= g + h + 1; y++) {
            int rad = y <= g + h ? 2 : 1;
            for (int oz2 = -rad; oz2 <= rad; oz2++) {
                for (int ox2 = -rad; ox2 <= rad; ox2++) {
                    boolean corner = Math.abs(ox2) == rad && Math.abs(oz2) == rad;
                    if (corner && (rad == 1 || rnd(dx + ox2, dz + oz2, 65 + y) < 0.7)) {
                        continue;
                    }
                    putIfFree(dx + ox2, y, dz + oz2, leaves);
                }
            }
        }
    }

    void lamps() {
        for (int dz = -Rw; dz <= Rw; dz++) {
            for (int dx = -Rw; dx <= Rw; dx++) {
                byte z = zoneAt(dx, dz);
                if (!((z >= QUAY && z <= AVENUE) || z == QUAY_EDGE) || doorFront[ci(dx, dz)] || rnd(dx, dz, 71) > 0.1) {
                    continue;
                }
                // Au bord du pavé seulement : contre une cour, une maison ou l'eau.
                boolean border = false;
                for (int k = 0; k < 4 && !border; k++) {
                    byte o = zoneAt(dx + (k == 0 ? -1 : (k == 1 ? 1 : 0)), dz + (k == 2 ? -1 : (k == 3 ? 1 : 0)));
                    border = z == QUAY_EDGE ? o == WATER : (o == YARD || o == LOT);
                }
                if (!border) {
                    continue;
                }
                boolean clear = true;
                for (int y = g + 1; y <= g + 4 && clear; y++) {
                    clear = free(dx, y, dz);
                }
                for (int[] l : lamps) {
                    if (Math.abs(l[0] - dx) < 9 && Math.abs(l[1] - dz) < 9) {
                        clear = false;
                        break;
                    }
                }
                if (!clear) {
                    continue;
                }
                lamps.add(new int[]{dx, dz});
                put(dx, g + 1, dz, st.post);
                put(dx, g + 2, dz, st.fence);
                put(dx, g + 3, dz, st.fence);
                put(dx, g + 4, dz, st.lantern);
            }
        }
    }
}
