package fr.orbit.devcore.station;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import fr.orbit.devcore.anneau.RingShape;
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

    public static final List<String> ZONES = List.of("atrium", "serre", "sas", "arene", "portail", "reliques", "tycoon", "passerelles");

    public static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3;
    private static final int[][] FWD = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final int[][] RIGHT = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    private static final int[][] N4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static final int ICE = 0x62CFF4, GOLD = 0xE0B864, WARM = 0xE6B35C, RED = 0xE0484A, VIOLET = 0xC79BFF, ALERT = 0xF2913D;
    /** Hauteur intérieure des passerelles. */
    private static final int CH = 7;
    /** Hauteur du mur d'enceinte de l'arène : la verrière part de là. */
    private static final int ARENA_WALL = 9;

    // Aile Tycoon, sur la diagonale sud-ouest. Repère : u le long de la diagonale en s'éloignant de l'atrium,
    // v vers la droite. L'axe passe 3,5 blocs à l'ouest du centre pour laisser libre l'écran du réseau.
    private static final double S2 = Math.sqrt(2);
    private static final double TY_OX = -3.5, TY_OZ = -3.5;
    /** Distance du centre du dôme sur l'axe : le dôme est centré sur le bloc (-57, 49). */
    private static final double TY_U = 53 * S2;
    private static final int TY_R = 20, TY_WALL = 9, TY_DOME = 10;
    /** Demi-largeur intérieure de la passerelle diagonale, et jusqu'où va sa paroi. */
    private static final double TY_IN = 4.3, TY_OUT = 5.9;

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
        // déco des halls et de l'atrium (23/11)
        for (String f : List.of("north", "east", "south", "west")) {
            data("minecraft:smooth_quartz_stairs[facing=" + f + "]");
            data("minecraft:blue_wall_banner[facing=" + f + "]");
            data("minecraft:yellow_wall_banner[facing=" + f + "]");
        }
        data("minecraft:lantern[hanging=true]");
        data("minecraft:soul_lantern[hanging=true]");
        data("minecraft:spruce_fence[north=true,south=true]");
        data("minecraft:spruce_fence[east=true,west=true]");
        data("minecraft:iron_trapdoor[half=top]");
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
                case "tycoon" -> tycoon();
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
        return Math.max(Math.max(centerD + islandR() + 3, obD + 3), (int) Math.ceil(TY_U / Math.sqrt(2) + 3.5 + TY_R + 3));
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
        // socle à gradins du noyau, quatre conduits de cuivre et deux anneaux flottants reliés au noyau
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                double r = Math.hypot(x, z);
                if (r > 3.2 && r <= 4.3) {
                    set(x, 2, z, x == 0 || z == 0 ? gold : hull);
                }
                for (int ry : new int[]{12, 19}) {
                    if (r > 3.2 && r < 5.5 && (x == 0 || z == 0)) {
                        set(x, ry, z, gold);
                    } else if (r >= 5.5 && r <= 6.4) {
                        set(x, ry, z, Math.floorMod((int) Math.round(Math.atan2(z, x) * 4 / Math.PI), 2) == 0 ? gold : accent);
                    }
                }
            }
        }
        for (int cx : new int[]{-3, 3}) {
            for (int cz : new int[]{-3, 3}) {
                set(cx, 2, cz, Material.WAXED_CUT_COPPER);
                set(cx, 3, cz, Material.WAXED_CUT_COPPER);
                set(cx, 4, cz, Material.LIGHTNING_ROD);
            }
        }
        holo(0.5, 8.2, 5.5, label("ORBIT MC", ICE, true), 3.2f);
        holo(0.5, 6.6, 5.5, label("LA STATION", GOLD, true), 1.4f);

        // allées lumineuses : du noyau vers chaque porte, à la couleur du mode, rails gris pointillés d'or
        Material[] lanes = {Material.ORANGE_STAINED_GLASS, Material.RED_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS,
                Material.PURPLE_STAINED_GLASS};
        for (int dir = 0; dir < 4; dir++) {
            for (int u = 6; u <= a - 2; u++) {
                set(wx(dir, u, 0), 0, wz(dir, u, 0), lanes[dir]);
                set(wx(dir, u, 0), -1, wz(dir, u, 0), light);
                for (int v : new int[]{-1, 1}) {
                    set(wx(dir, u, v), 0, wz(dir, u, v), Math.floorMod(u, 4) == 0 ? gold : trim);
                }
            }
        }

        // estrade du spawn : disque de quartz, rose des vents à 8 branches, cerclage bleu et 4 bornes lumineuses
        int sz = spawnZ();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double r = Math.hypot(dx, dz);
                if (r > 5.4) {
                    continue;
                }
                Material m;
                if (r < 0.5) {
                    m = Material.CHISELED_QUARTZ_BLOCK;
                } else if (r > 4.6) {
                    m = accent;
                } else if ((dx == 0 || dz == 0) && r <= 4.4) {
                    m = gold;
                } else if (Math.abs(dx) == Math.abs(dz) && r <= 3.2) {
                    m = ring;
                } else {
                    m = hull;
                }
                set(dx, 0, sz + dz, m);
            }
        }
        for (int[] p : new int[][]{{-5, -2}, {5, -2}, {-5, 2}, {5, 2}}) {
            set(p[0], 1, sz + p[1], Material.CHISELED_QUARTZ_BLOCK);
            set(p[0], 2, sz + p[1], Material.END_ROD);
        }
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

        // de chaque côté des portes : pilastre de quartz cerclé d'or, jardinière d'azalées et banc dos au mur.
        // Rien n'est posé sur le chemin de l'aile Tycoon (pan sud-ouest).
        for (int dir = 0; dir < 4; dir++) {
            BlockData bench = data("minecraft:smooth_quartz_stairs[facing=" + face(dir, 1, 0) + "]");
            for (int side : new int[]{-1, 1}) {
                int pv = side * (hw + 2);
                int ix = wx(dir, a - 1, pv), iz = wz(dir, a - 1, pv);
                if (!nearTycoon(ix, iz)) {
                    for (int y = 1; y <= wallH - 1; y++) {
                        set(ix, y, iz, y == 1 || y == wallH - 1 ? gold : Material.QUARTZ_PILLAR);
                    }
                }
                for (int k = hw + 3; k <= hw + 4; k++) {
                    int qx = wx(dir, a - 1, side * k), qz = wz(dir, a - 1, side * k);
                    if (!nearTycoon(qx, qz)) {
                        set(qx, 1, qz, Material.MOSS_BLOCK);
                        set(qx, 2, qz, k == hw + 4 ? Material.FLOWERING_AZALEA : Material.AZALEA);
                    }
                    int bx = wx(dir, a - 3, side * k), bz = wz(dir, a - 3, side * k);
                    if (!nearTycoon(bx, bz)) {
                        plan.set(bx, 1, bz, bench);
                    }
                }
            }
        }

        // lustres : 8 lanternes en croix pendues au dôme par une chaîne, entre le belvédère et les murs
        BlockData chain = data(Material.IRON_CHAIN);
        BlockData hanging = data("minecraft:lantern[hanging=true]");
        BlockData rodDown = data("minecraft:end_rod[facing=down]");
        for (int i = 0; i < 8; i++) {
            double ang = Math.PI / 8 + i * Math.PI / 4;
            int lx = (int) Math.round(18 * Math.cos(ang)), lz = (int) Math.round(18 * Math.sin(ang));
            int bottom = domeBottom(lx, lz, r -> wallH + cap(r, rc, domeH));
            int hy = 18;
            for (int y = hy + 1; y < bottom; y++) {
                plan.set(lx, y, lz, chain);
            }
            set(lx, hy, lz, rib);
            plan.set(lx, hy - 1, lz, rodDown);
            for (int[] n : N4) {
                set(lx + n[0], hy, lz + n[1], gold);
                plan.set(lx + n[0], hy - 1, lz + n[1], hanging);
            }
        }

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
        // passerelle de l'aile Tycoon, en diagonale : elle perce le pan sud-ouest de l'atrium et la paroi du dôme.
        // Paroi épaisse de 1,6 en travers de la diagonale : pas de fuite par les coins des marches d'escalier.
        Material gold = pal("or", Material.GOLD_BLOCK);
        double uEnd = TY_U - TY_R + 2.5;
        for (int x = -90; x <= 0; x++) {
            for (int z = 0; z <= 90; z++) {
                double u = tyU(x, z), v = Math.abs(tyV(x, z));
                if (u < 22.6 || u > uEnd || v > TY_OUT) {
                    continue;
                }
                boolean ribRow = Math.floorMod((int) Math.floor(u), 4) == 0;
                boolean door = u < 24.6;
                set(x, -1, z, trim);
                if (v > TY_IN) {
                    set(x, 0, z, trim);
                    for (int y = 1; y <= CH; y++) {
                        set(x, y, z, y == 1 ? trim : y == CH ? accent : ribRow ? rib : glassShare > 0.05 ? glass : hull);
                    }
                    set(x, CH + 1, z, door ? gold : hull);
                } else {
                    set(x, 0, z, v > TY_IN - 0.8 ? trim : v < 0.4 ? (Math.floorMod((int) Math.floor(u), 5) == 0 ? light : accent) : floor);
                    for (int y = 1; y <= CH; y++) {
                        set(x, y, z, Material.AIR);
                    }
                    set(x, CH + 1, z, door ? gold : ribRow ? rib : v <= TY_IN - 2 && glassShare > 0.05 ? glass : hull);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Tycoon : l'aile de la base (sud-ouest)

    private void tycoon() {
        Material floor = pal("sol", Material.WHITE_CONCRETE);
        Material trim = pal("bordure", Material.SMOOTH_QUARTZ);
        Material accent = pal("accent", Material.LIGHT_BLUE_CONCRETE);
        Material gold = pal("or", Material.GOLD_BLOCK);
        Material glass = pal("verre", Material.GLASS);
        Material tinted = pal("verre-teinte", Material.LIGHT_BLUE_STAINED_GLASS);
        Material light = pal("lumiere", Material.SEA_LANTERN);
        Material under = pal("dessous", Material.LIGHT_GRAY_CONCRETE);
        double cx = tyX(TY_U, 0), cz = tyZ(TY_U, 0);
        int bx0 = (int) Math.floor(cx), bz0 = (int) Math.floor(cz);
        double R = TY_R + 0.3;

        // dôme : sol, paroi de verre à piliers, calotte à 8 nervures dorées
        for (int dx = -TY_R - 2; dx <= TY_R + 2; dx++) {
            for (int dz = -TY_R - 2; dz <= TY_R + 2; dz++) {
                int x = bx0 + dx, z = bz0 + dz;
                double r = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                if (r > R) {
                    continue;
                }
                boolean edge = r > TY_R - 1.2;
                double th = Math.atan2(z + 0.5 - cz, x + 0.5 - cx);
                Material f = r > TY_R - 2.2 ? trim : Math.abs(r - 7.5) < 0.5 ? gold : Math.abs(r - (TY_R - 4)) < 0.5 ? accent : floor;
                set(x, 0, z, f);
                set(x, -1, z, under);
                if (r < TY_R - 2) {
                    set(x, -2, z, under);
                }
                if (r < TY_R - 8) {
                    set(x, -3, z, Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0 ? light : under);
                }
                if (edge) {
                    boolean pillar = Math.floorMod((int) Math.round(th * TY_R), 6) == 0;
                    for (int y = 1; y <= TY_WALL; y++) {
                        Material m = y == 1 ? trim : y == 2 || y == TY_WALL - 1 ? accent : y == TY_WALL ? gold
                                : pillar || glassShare <= 0.05 ? trim : glass;
                        set(x, y, z, m);
                    }
                }
                int top = TY_WALL + cap(r, R, TY_DOME);
                int low = top;
                for (int[] n : N4) {
                    double nr = Math.hypot(x + n[0] + 0.5 - cx, z + n[1] + 0.5 - cz);
                    int nt = nr <= R ? TY_WALL + cap(nr, R, TY_DOME) : TY_WALL;
                    low = Math.min(low, nt + 1);
                }
                low = edge ? TY_WALL + 1 : Math.max(low, TY_WALL + 1);
                double off = r * Math.abs(Math.sin(th - Math.round(th / (Math.PI / 4)) * (Math.PI / 4)));
                for (int y = Math.min(low, top); y <= top; y++) {
                    if (y > TY_WALL) {
                        set(x, y, z, r < 2.2 ? light : off < 0.6 ? gold : r > R * 0.75 ? tinted : glass);
                    }
                }
            }
        }

        // maquette des 5 îles : une table noire (l'espace) cerclée d'or, la base au centre, les îles autour
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double r = Math.hypot(bx0 + dx + 0.5 - cx, bz0 + dz + 0.5 - cz);
                if (r <= 6.5) {
                    set(bx0 + dx, 1, bz0 + dz, r > 5.6 ? gold : Material.BLACK_CONCRETE);
                }
            }
        }
        set(bx0, 2, bz0, gold);
        set(bx0, 3, bz0, light);
        String[] islands = {"Pêche", "Bûcheron", "Agriculture", "Mobzone", "Mine"};
        int[] islandColors = {0x3FA9F5, 0xC8894B, 0xF5D442, 0xFF4D4D, 0xB0BEC5};
        Material[] arms = {Material.PRISMARINE_BRICKS, Material.MOSS_BLOCK, Material.GRASS_BLOCK,
                Material.POLISHED_BLACKSTONE_BRICKS, Material.COBBLED_DEEPSLATE};
        Material[] cores = {Material.WATER, Material.OAK_LOG, Material.HAY_BLOCK, Material.CRYING_OBSIDIAN, Material.DEEPSLATE_DIAMOND_ORE};
        for (int i = 0; i < islands.length; i++) {
            double ang = Math.toRadians(i * 72);
            int ix = (int) Math.floor(tyX(TY_U + 3.6 * Math.cos(ang), 3.6 * Math.sin(ang)));
            int iz = (int) Math.floor(tyZ(TY_U + 3.6 * Math.cos(ang), 3.6 * Math.sin(ang)));
            for (int[] n : N4) {
                set(ix + n[0], 2, iz + n[1], arms[i]);
            }
            set(ix, 2, iz, cores[i]);
            double h = 4.6;
            switch (i) {
                case 1 -> {
                    set(ix, 3, iz, Material.OAK_LOG);
                    plan.set(ix, 4, iz, data("minecraft:azalea_leaves[persistent=true]"));
                    h = 5.6;
                }
                case 2 -> set(ix, 3, iz, Material.HAY_BLOCK);
                case 3 -> set(ix, 3, iz, Material.REDSTONE_BLOCK);
                case 4 -> set(ix, 3, iz, Material.RAW_GOLD_BLOCK);
                default -> { }
            }
            holo(ix + 0.5, h, iz + 0.5, label(islands[i], islandColors[i], true), 0.8f);
        }
        holo(cx, 7.6, cz, label("ORBIT TYCOON", ICE, true), 2.2f);
        holo(cx, 6.5, cz, label("la base et ses 5 îles", GOLD, false), 0.9f);

        // pets spatiaux : modèles du pack sur des socles, ils tournent lentement
        String[] pets = {"pet_robot", "pet_drone", "pet_chat", "pet_meduse", "pet_satellite"};
        String[] petNames = {"MINI-ROBOT", "DRONE ÉCLAIREUR", "ASTRO-CHAT", "MÉDUSE COSMIQUE", "PETIT SATELLITE"};
        double[] petAngles = {-135, -90, -45, 45, 90};
        for (int i = 0; i <= pets.length; i++) {
            double ang = Math.toRadians(i < pets.length ? petAngles[i] : 135);
            int px = (int) Math.floor(tyX(TY_U + 12 * Math.cos(ang), 12 * Math.sin(ang)));
            int pz = (int) Math.floor(tyZ(TY_U + 12 * Math.cos(ang), 12 * Math.sin(ang)));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set(px + dx, 1, pz + dz, dx != 0 && dz != 0 ? gold : dx == 0 && dz == 0 ? accent : trim);
                }
            }
            set(px, 2, pz, light);
            if (i < pets.length) {
                plan.model(zone, px + 0.5, 3.75, pz + 0.5, pets[i], 1.2f);
                holo(px + 0.5, 5.0, pz + 0.5, label(petNames[i], ICE, true), 0.8f);
            } else {
                holo(px + 0.5, 4.3, pz + 0.5, label("COMPAGNONS", GOLD, true), 1.1f);
                holo(px + 0.5, 3.6, pz + 0.5, label("les pets d'Orbit Tycoon", 0xD7DCE2, false), 0.7f);
            }
        }

        // lampadaires dorés
        for (double deg : new double[]{-157.5, -112.5, -67.5, 67.5, 112.5, 157.5}) {
            double ang = Math.toRadians(deg);
            int lx = (int) Math.floor(tyX(TY_U + 17 * Math.cos(ang), 17 * Math.sin(ang)));
            int lz = (int) Math.floor(tyZ(TY_U + 17 * Math.cos(ang), 17 * Math.sin(ang)));
            set(lx, 1, lz, gold);
            for (int y = 2; y <= 4; y++) {
                set(lx, y, lz, Material.END_ROD);
            }
            set(lx, 5, lz, light);
            set(lx, 6, lz, Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        }

        // anneau-portail vers le serveur tycoon, au fond du dôme, et son PNJ devant
        double fx = -1 / S2, fz = 1 / S2;
        double rr = 3.6;
        ringAt(tyX(TY_U + 14, 0), tyZ(TY_U + 14, 0), fx, fz, rr, "tycoon", gold, light);
        double ringTop = 1 + 2 * rr + 1.4;
        holo(tyX(TY_U + 13.4, 0), ringTop + 1.6, tyZ(TY_U + 13.4, 0), label("TYCOON", GOLD, true), 2.4f);
        holo(tyX(TY_U + 13.4, 0), ringTop + 0.7, tyZ(TY_U + 13.4, 0), label("Traverse l'anneau", ICE, false), 0.9f);
        plan.point("porte.tycoon", tyX(TY_U + 13, 0), 1, tyZ(TY_U + 13, 0), yawOf(fx, fz));
        plan.point("pnj.tycoon", tyX(TY_U + 10, 0), 1, tyZ(TY_U + 10, 0), yawOf(-fx, -fz));

        // panneau au-dessus de l'entrée, côté atrium
        holo(tyX(21.5, 0), CH + 2.4, tyZ(21.5, 0), label("TYCOON", GOLD, true), 2.0f);

        for (int dx = -TY_R; dx <= TY_R; dx += 4) {
            for (int dz = -TY_R; dz <= TY_R; dz += 4) {
                if (Math.hypot(dx, dz) < TY_R - 2) {
                    light(bx0 + dx, 2, bz0 + dz, 15);
                    light(bx0 + dx, 8, bz0 + dz, 14);
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
                        Material[] plants = {Material.SHORT_GRASS, Material.SHORT_GRASS, Material.SHORT_GRASS, Material.FERN,
                                Material.FERN, Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.ALLIUM,
                                Material.OXEYE_DAISY, Material.AZALEA};
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
        // ponts de bois là où la rivière coupe le chemin, garde-corps de chaque côté (sauf sous une lanterne)
        BlockData rail = data("minecraft:spruce_fence[" + (FWD[dir][0] == 0 ? "north=true,south=true" : "east=true,west=true") + "]");
        for (int u = -R + 3; u <= R - 3; u++) {
            int v0 = (int) Math.round(7 * Math.sin(u / 4.0));
            if (v0 < -3 || v0 > 2) {
                continue;
            }
            for (int v = -1; v <= 1; v++) {
                wset(dir, u, 0, v, Material.SPRUCE_PLANKS);
            }
            boolean lanternRow = u >= -R + 6 && u <= gateU - 5 && Math.floorMod(u + R - 6, 6) == 0;
            if (!lanternRow) {
                for (int v : new int[]{-2, 2}) {
                    plan.set(wx(dir, centerD + u, v), 1, wz(dir, centerD + u, v), rail);
                }
            }
        }
        // mare aux nénuphars, bordée de pierres moussues et de fougères
        int pu = (int) Math.round(4 * k), pv = (int) Math.round(-14 * k);
        for (int du = -5; du <= 5; du++) {
            for (int dv = -4; dv <= 4; dv++) {
                double e = (du / 3.2) * (du / 3.2) + (dv / 2.4) * (dv / 2.4);
                if (e <= 1) {
                    wset(dir, pu + du, 0, pv + dv, Material.WATER);
                    wset(dir, pu + du, 1, pv + dv, hash(du, dv, 15) < 0.22 ? Material.LILY_PAD : Material.AIR);
                } else if (e <= 1.6) {
                    double h = hash(du, dv, 19);
                    if (h < 0.35) {
                        wset(dir, pu + du, 0, pv + dv, Material.MOSSY_COBBLESTONE);
                        wset(dir, pu + du, 1, pv + dv, Material.AIR);
                    } else if (h < 0.7) {
                        wset(dir, pu + du, 1, pv + dv, Material.FERN);
                    }
                }
            }
        }
        // rochers moussus
        for (int[] p : new int[][]{{-15, -5}, {0, -8}, {-8, 5}, {14, -12}}) {
            rock(dir, (int) Math.round(p[0] * k), (int) Math.round(p[1] * k));
        }
        // camp du survivant : feu de camp, souches pour s'asseoir, tente ouverte vers le feu
        int cu = (int) Math.round(-3 * k), cv = (int) Math.round(11 * k);
        for (int du = -3; du <= 3; du++) {
            for (int dv = -3; dv <= 3; dv++) {
                if (Math.hypot(du, dv) <= 2.6) {
                    wset(dir, cu + du, 0, cv + dv, Material.COARSE_DIRT);
                    wset(dir, cu + du, 1, cv + dv, Material.AIR);
                }
            }
        }
        wset(dir, cu, 1, cv, Material.CAMPFIRE);
        for (int[] s : new int[][]{{-2, 0}, {2, 0}, {0, -2}}) {
            wset(dir, cu + s[0], 1, cv + s[1], Material.STRIPPED_OAK_LOG);
        }
        wset(dir, cu + 2, 2, cv, Material.LANTERN);
        for (int dv = 3; dv <= 5; dv++) {
            for (int du = -2; du <= 2; du++) {
                int y = 3 - Math.abs(du);
                wset(dir, cu + du, y, cv + dv, Material.ORANGE_WOOL);
                for (int yy = 1; yy < y; yy++) {
                    wset(dir, cu + du, yy, cv + dv, dv == 5 ? Material.WHITE_WOOL : Material.AIR);
                }
            }
        }
        wset(dir, cu - 1, 1, cv + 4, Material.BARREL);
        // potager : bac bordé de dalles, citrouilles et pastèques, pots de fleurs, composteur et foin
        int gu = (int) Math.round(6 * k), gv = (int) Math.round(12 * k);
        Material[] crops = {Material.PUMPKIN, Material.MELON, Material.PUMPKIN};
        Material[] pots = {Material.POTTED_RED_TULIP, Material.POTTED_CORNFLOWER, Material.POTTED_OXEYE_DAISY};
        for (int du = 0; du <= 4; du++) {
            for (int dv = 0; dv <= 3; dv++) {
                boolean border = du == 0 || du == 4 || dv == 0 || dv == 3;
                // sous une dalle, l'herbe meurt en jeu : terre partout dans le bac
                wset(dir, gu + du, 0, gv + dv, Material.COARSE_DIRT);
                wset(dir, gu + du, 1, gv + dv, border ? Material.SPRUCE_SLAB : dv == 1 ? crops[du - 1] : pots[du - 1]);
            }
        }
        wset(dir, gu + 5, 1, gv + 1, Material.COMPOSTER);
        wset(dir, gu + 5, 1, gv + 2, Material.HAY_BLOCK);
        wset(dir, gu + 5, 2, gv + 2, Material.HAY_BLOCK);

        for (int u = -R; u <= R; u += 4) {
            for (int v = -R; v <= R; v += 4) {
                if (Math.hypot(u, v) < R - 2) {
                    wlight(dir, u, 2, v, 14);
                    wlight(dir, u, 8, v, 14);
                }
            }
        }
        gate(dir, "survie", "survie", "SURVIE", WARM, gateU, 10, false, 4.0, base, warm);
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

    /** Rocher moussu de 3 x 3 (plus haut au centre), coiffé de tapis de mousse. */
    private void rock(int dir, int u, int v) {
        Material[] stones = {Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.ANDESITE, Material.TUFF};
        for (int du = -1; du <= 1; du++) {
            for (int dv = -1; dv <= 1; dv++) {
                int d = Math.abs(du) + Math.abs(dv);
                int h = d == 0 ? 3 : d == 1 ? (hash(u + du, v + dv, 17) < 0.3 ? 1 : 2) : (hash(u + du, v + dv, 16) < 0.5 ? 1 : 0);
                for (int y = 1; y <= h; y++) {
                    wset(dir, u + du, y, v + dv, stones[(int) (hash(u + du + y, v + dv, 18) * stones.length)]);
                }
                if (h > 0) {
                    wset(dir, u + du, h + 1, v + dv, h >= 2 || hash(u + du, v + dv, 20) < 0.5 ? Material.MOSS_CARPET : Material.AIR);
                }
            }
        }
    }

    /** Sacs de sable : un bloc de boue tassée surmonté d'une dalle. */
    private void sandbag(int dir, int u, int v) {
        wset(dir, u, 1, v, Material.PACKED_MUD);
        wset(dir, u, 2, v, Material.MUD_BRICK_SLAB);
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
        // poste de contrôle : murets de sacs de sable devant le quai, allée centrale libre
        for (int side : new int[]{-1, 1}) {
            for (int k = hw + 1; k <= hw + 4; k++) {
                sandbag(dir, gateU - 7, side * k);
            }
            for (int u = gateU - 6; u <= gateU - 5; u++) {
                sandbag(dir, u, side * (hw + 4));
            }
        }
        // piles de caisses militaires dans les coins
        for (int side : new int[]{-1, 1}) {
            for (int[] p : new int[][]{{-8, 10}, {-7, 10}, {-8, 11}, {-8, 12}, {-7, 12}, {14, 10}, {15, 10}, {15, 11}, {14, 12}, {15, 12}, {16, 11}}) {
                int h = 1 + (int) (hash(p[0], p[1] * side, 32) * 3);
                for (int y = 1; y <= h; y++) {
                    wset(dir, p[0], y, side * p[1], Math.floorMod(p[0] + p[1] + y, 2) == 0 ? Material.GREEN_TERRACOTTA : Material.STRIPPED_SPRUCE_WOOD);
                }
            }
        }
        // table de la carte devant un écran rétroéclairé
        for (int u = -2; u <= 2; u++) {
            for (int y = 1; y <= 5; y++) {
                boolean fr = Math.abs(u) == 2 || y == 1 || y == 5;
                wset(dir, u, y, 11, fr ? metal : Material.CYAN_STAINED_GLASS);
                if (!fr) {
                    wset(dir, u, y, 12, light);
                }
            }
        }
        for (int u = -1; u <= 1; u++) {
            wset(dir, u, 1, 9, u == 0 ? Material.CARTOGRAPHY_TABLE : metal);
        }
        wsign(dir, 0, 3.6, 10.4, 0, -1, label("CARTE DE LA ZONE", ICE, true), 0.9f);
        wsign(dir, 0, 2.8, 10.4, 0, -1, label("◉ secteur 7 · contaminé", RED, false), 0.6f);
        // groupe électrogène, câble jusqu'au plafond
        for (int u = -1; u <= 1; u++) {
            for (int v = -12; v <= -11; v++) {
                wset(dir, u, 1, v, u == 0 && v == -11 ? Material.BLAST_FURNACE : Material.IRON_BLOCK);
                wset(dir, u, 2, v, Material.WAXED_WEATHERED_CUT_COPPER);
            }
        }
        wset(dir, -1, 3, -12, Material.LIGHTNING_ROD);
        wset(dir, 1, 3, -12, Material.LIGHTNING_ROD);
        for (int y = 3; y < top; y++) {
            wset(dir, 0, y, -12, Material.IRON_CHAIN);
        }
        wsign(dir, 0, 3.4, -10.4, 0, 1, label("GROUPE ÉLECTROGÈNE · 12 %", ALERT, true), 0.6f);
        // tuyaux et grilles d'aération sous le plafond, néons grillés par endroits
        BlockData vent = data("minecraft:iron_trapdoor[half=top]");
        for (int u = inner + 1; u <= L - 1; u++) {
            for (int side : new int[]{-1, 1}) {
                wset(dir, u, top - 1, side * hw, Math.floorMod(u, 6) == 0 ? metal : Material.WAXED_WEATHERED_CUT_COPPER);
                if (Math.floorMod(u, 6) == 3) {
                    for (int v = W - 5; v <= W - 4; v++) {
                        plan.set(wx(dir, centerD + u, side * v), top - 1, wz(dir, centerD + u, side * v), vent);
                    }
                }
            }
            for (int v : new int[]{-W / 2, 0, W / 2}) {
                if (hash(u, v, 31) < 0.15) {
                    wset(dir, u, top, v, Material.REDSTONE_LAMP);
                    wset(dir, u, top - 1, v, Material.AIR);
                }
            }
        }
        // bandes de danger : en haut des murs latéraux et en bas du mur du quai
        for (int u = -L + 1; u <= L - 1; u++) {
            for (int side : new int[]{-1, 1}) {
                wset(dir, u, 9, side * W, Math.floorMod(u, 2) == 0 ? yellow : black);
            }
        }
        for (int v = -W + 1; v <= W - 1; v++) {
            wset(dir, L, 2, v, Math.floorMod(v, 2) == 0 ? yellow : black);
        }
        // traces de lutte : crânes et taches au sol
        wset(dir, -4, 1, -13, Material.SKELETON_SKULL);
        wset(dir, 9, 1, 12, Material.SKELETON_SKULL);
        for (int[] p : new int[][]{{2, -5}, {3, -5}, {2, -6}, {3, -4}, {8, 6}, {9, 6}, {8, 7}}) {
            wset(dir, p[0], 0, p[1], hash(p[0], p[1], 33) < 0.6 ? Material.RED_TERRACOTTA : Material.BROWN_TERRACOTTA);
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

        gate(dir, "extraction", "extraction", "EXTRACTION", RED, gateU, 8, true, RingShape.fit(1, 8), metal, Material.REDSTONE_BLOCK);
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
        // logo au centre de la piste : cercle d'or et deux lames croisées
        for (int u = -4; u <= 4; u++) {
            for (int v = -4; v <= 4; v++) {
                double r = Math.hypot(u, v);
                if (r < 0.5) {
                    wset(dir, u, 0, v, light);
                } else if (r > 2.5 && r <= 3.4) {
                    wset(dir, u, 0, v, Material.GOLD_BLOCK);
                } else if (Math.abs(u) == Math.abs(v) && r <= 2.4) {
                    wset(dir, u, 0, v, accent);
                }
            }
        }
        // plots de départ face à face : équipe bleue contre équipe or
        for (int side : new int[]{-1, 1}) {
            Material rim = side < 0 ? Material.BLUE_CONCRETE : Material.GOLD_BLOCK;
            for (int du = -1; du <= 1; du++) {
                for (int dv = -1; dv <= 1; dv++) {
                    wset(dir, du, 0, side * 9 + dv, du == 0 && dv == 0 ? light : rim);
                }
            }
            wtext(dir, 0, 3.2, side * 9, side < 0 ? label("ÉQUIPE BLEUE", 0x4A7BFF, true) : label("ÉQUIPE OR", GOLD, true), 1.0f);
        }
        // podium de la saison, en haut des gradins (1 or, 2 fer, 3 cuivre)
        for (int u = -3; u <= 3; u++) {
            for (int v = -20; v <= -16; v++) {
                double r = Math.hypot(u, v);
                if (r <= inner + 1.5 || r > standEnd + 0.5) {
                    continue;
                }
                for (int y = 1; y <= 4; y++) {
                    wset(dir, u, y, v, y == 4 ? accent : stand);
                }
            }
        }
        wset(dir, 0, 5, -18, stand);
        wset(dir, 0, 6, -18, stand);
        wset(dir, 0, 7, -18, Material.GOLD_BLOCK);
        wset(dir, -2, 5, -18, stand);
        wset(dir, -2, 6, -18, Material.IRON_BLOCK);
        wset(dir, 2, 5, -18, Material.WAXED_COPPER_BLOCK);
        wsign(dir, 0, 9.6, -17.4, 0, 1, label("PODIUM DE LA SAISON", GOLD, true), 1.0f);
        // fanions d'équipe sur le mur d'enceinte (bleus d'un côté, or de l'autre) et projecteurs aux diagonales
        for (int i = 0; i < 24; i++) {
            double ang = i * Math.PI / 12;
            int u = (int) Math.round(20 * Math.cos(ang)), v = (int) Math.round(20 * Math.sin(ang));
            if (Math.abs(v) <= hw + 1 || Math.hypot(u, v) > standEnd + 0.5) {
                continue;
            }
            int fu = Math.abs(u) >= Math.abs(v) ? -Integer.signum(u) : 0;
            int fv = Math.abs(u) >= Math.abs(v) ? 0 : -Integer.signum(v);
            String color = v < 0 ? "blue" : "yellow";
            plan.set(wx(dir, centerD + u, v), 8, wz(dir, centerD + u, v),
                    data("minecraft:" + color + "_wall_banner[facing=" + face(dir, fu, fv) + "]"));
            if (i % 6 == 3) {
                wset(dir, u, 9, v, light);
                wset(dir, u, 10, v, dark);
            }
        }
        // présentoirs des kits, de chaque côté de l'entrée
        Material[] kits = {Material.ANVIL, Material.ENCHANTING_TABLE, Material.BREWING_STAND, Material.TARGET};
        int n = 0;
        for (int side : new int[]{-1, 1}) {
            for (int u : new int[]{-17, -15}) {
                wset(dir, u, 1, side * hw, accent);
                wset(dir, u, 2, side * hw, kits[n++]);
            }
        }
        wtext(dir, -16, 3.8, 0, label("KITS", ICE, true), 1.2f);

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
        gate(dir, "practice", "practice", "PRACTICE", ICE, gateU, 9, false, 3.2, accent, light);
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
        // allée dorée : de l'entrée au cercle central, puis du cercle au portail
        for (int u = -R + 2; u <= gateU - 1; u++) {
            if (Math.abs(u) < 8) {
                continue;
            }
            wset(dir, u, 0, 0, Material.GILDED_BLACKSTONE);
            wset(dir, u, 0, -1, Material.POLISHED_BLACKSTONE_BRICKS);
            wset(dir, u, 0, 1, Material.POLISHED_BLACKSTONE_BRICKS);
        }
        // arche en ruine au-dessus de l'allée, linteau effondré au milieu
        int au = (int) Math.round(-12 * k);
        for (int side : new int[]{-1, 1}) {
            for (int y = 1; y <= 7; y++) {
                double hm = hash(au, side * 5 + y, 34);
                wset(dir, au, y, side * 5, y == 1 || y == 7 ? Material.CHISELED_STONE_BRICKS
                        : hm < 0.5 ? Material.STONE_BRICKS : hm < 0.75 ? Material.MOSSY_STONE_BRICKS : Material.CRACKED_STONE_BRICKS);
            }
        }
        for (int v = -5; v <= 5; v++) {
            if (v >= -1 && v <= 2) {
                continue;
            }
            wset(dir, au, 8, v, hash(au, v, 35) < 0.4 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
        }
        // autels des deux factions : Lumière (Auréval) d'un côté de l'allée, Ombre (Morneval) de l'autre
        for (int side : new int[]{-1, 1}) {
            boolean lumiere = side < 0;
            int tu = (int) Math.round(3 * k), tv = side * (int) Math.round(10 * k);
            for (int du = -2; du <= 2; du++) {
                for (int dv = -2; dv <= 2; dv++) {
                    double r = Math.hypot(du, dv);
                    if (r > 2.5) {
                        continue;
                    }
                    boolean mark = (du == 0) != (dv == 0) && r == 2;
                    wset(dir, tu + du, 1, tv + dv, mark ? (lumiere ? gold : Material.CRYING_OBSIDIAN)
                            : lumiere ? Material.QUARTZ_BRICKS : Material.POLISHED_BLACKSTONE);
                    if (r <= 1.5) {
                        wset(dir, tu + du, 2, tv + dv, lumiere ? Material.CHISELED_QUARTZ_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS);
                        if (Math.abs(du) == 1 && Math.abs(dv) == 1) {
                            wset(dir, tu + du, 3, tv + dv, lumiere ? Material.END_ROD : Material.AMETHYST_CLUSTER);
                        }
                    }
                }
            }
            for (int y = 3; y <= 5; y++) {
                wset(dir, tu, y, tv, lumiere ? Material.QUARTZ_PILLAR : y == 3 ? Material.CHISELED_POLISHED_BLACKSTONE : Material.OBSIDIAN);
            }
            wset(dir, tu, 6, tv, lumiere ? gold : Material.CRYING_OBSIDIAN);
            wset(dir, tu, 7, tv, lumiere ? Material.OCHRE_FROGLIGHT : Material.SOUL_LANTERN);
            wtext(dir, tu, 9.8, tv, label(lumiere ? "LUMIÈRE" : "OMBRE", lumiere ? GOLD : VIOLET, true), 1.6f);
            wtext(dir, tu, 9.0, tv, label(lumiere ? "Auréval" : "Morneval", lumiere ? WARM : 0x8F6BD8, false), 1.0f);
        }
        // cristaux d'améthyste : quatre amas et quelques éclats dans les ruines
        for (int[] p : new int[][]{{-7, 8}, {-7, -8}, {12, 7}, {12, -7}}) {
            int gu = (int) Math.round(p[0] * k), gv = (int) Math.round(p[1] * k);
            wset(dir, gu, 1, gv, Material.AMETHYST_BLOCK);
            wset(dir, gu, 2, gv, Material.AMETHYST_CLUSTER);
            wset(dir, gu + 1, 1, gv, Material.AMETHYST_CLUSTER);
            wset(dir, gu, 1, gv + Integer.signum(gv), Material.AMETHYST_CLUSTER);
        }
        // lanternes d'âme pendues au dôme
        BlockData chain = data(Material.IRON_CHAIN);
        BlockData soul = data("minecraft:soul_lantern[hanging=true]");
        for (int i = 0; i < 6; i++) {
            double ang = Math.PI / 6 + i * Math.PI / 3;
            int lu = (int) Math.round(12 * Math.cos(ang)), lv = (int) Math.round(12 * Math.sin(ang));
            int bottom = domeBottom(lu, lv, r -> cap(r, Rd + 0.5, Hd));
            for (int y = 12; y < bottom; y++) {
                plan.set(wx(dir, centerD + lu, lv), y, wz(dir, centerD + lu, lv), chain);
            }
            plan.set(wx(dir, centerD + lu, lv), 11, wz(dir, centerD + lu, lv), soul);
        }

        // portail : anneau-portail Orbit (l'ancien anneau de pierre et son rideau violet sont vidés)
        for (int v = -8; v <= 8; v++) {
            for (int y = 1; y <= 16; y++) {
                if (Math.hypot(v, y - 8) <= 7.2) {
                    wset(dir, gateU, y, v, Material.AIR);
                }
            }
        }
        ring(dir, gateU, 5.0, "reborn", Material.CHISELED_DEEPSLATE, gold);
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

    /**
     * Porte d'embarquement d'une aile : un anneau-portail de rayon r (traverser le disque = envoi vers {@code server}).
     * L'ancienne porte vitrée (rideau entre deux piliers) est vidée pour qu'une régénération partielle la retire.
     */
    private void gate(int dir, String id, String server, String title, int color, int gateU, int height, boolean indoor,
                      double r, Material frame, Material keystone) {
        for (int v = -hw; v <= hw; v++) {
            for (int y = 1; y <= height; y++) {
                wset(dir, gateU, y, v, Material.AIR);
            }
        }
        ring(dir, gateU, r, server, frame, keystone);
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

    /** Anneau-portail dans le plan de la porte d'une aile (centré sur l'axe de la passerelle). */
    private void ring(int dir, int gateU, double r, String server, Material frame, Material keystone) {
        double du = centerD + gateU;
        ringAt(du * FWD[dir][0] + 0.5, du * FWD[dir][1] + 0.5, FWD[dir][0], FWD[dir][1], r, server, frame, keystone);
    }

    /**
     * Anneau-portail posé sur le sol (dessus du sol à y = 1) : couronne de blocs, ligne au sol sous l'anneau
     * (pointe au centre), et disque animé ajouté au plan. (cx, cz) centre, (nx, nz) sens de passage.
     */
    private void ringAt(double cx, double cz, double nx, double nz, double r, String server, Material frame, Material keystone) {
        double cy = RingShape.centerY(1, r);
        RingShape.draw(cx, cy, cz, r, nx, nz, 1, this::set);
        double len = Math.hypot(nx, nz);
        double tx = -nz / len, tz = nx / len;
        int span = (int) Math.ceil(RingShape.outer(r));
        for (int s = -span; s <= span; s++) {
            set((int) Math.floor(cx + tx * s), 0, (int) Math.floor(cz + tz * s), s == 0 ? keystone : frame);
        }
        plan.ring(zone, cx, cy, cz, nx / len, nz / len, r, server);
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

    /** Repère de l'aile Tycoon (diagonale sud-ouest) : u et v du centre d'un bloc, et retour vers x / z. */
    private static double tyU(int x, int z) {
        return ((z + 0.5 - TY_OZ) - (x + 0.5 - TY_OX)) / S2;
    }

    private static double tyV(int x, int z) {
        return -((x + 0.5 - TY_OX) + (z + 0.5 - TY_OZ)) / S2;
    }

    private static double tyX(double u, double v) {
        return TY_OX + (-u - v) / S2;
    }

    private static double tyZ(double u, double v) {
        return TY_OZ + (u - v) / S2;
    }

    /** Vrai si (x, z) de l'atrium est sur le chemin qui mène à l'aile Tycoon : rien n'y est posé. */
    private static boolean nearTycoon(int x, int z) {
        return x < 0 && z > 0 && Math.abs(tyV(x, z)) < 6;
    }

    /**
     * Plus bas bloc d'un dôme dans une colonne, avec la même règle que son dessin : top(r) donne le haut du dôme
     * à la distance r de l'axe. Ce qui pend du dôme s'arrête juste en dessous, sans percer la coque.
     */
    private static int domeBottom(int x, int z, java.util.function.DoubleToIntFunction top) {
        int t = top.applyAsInt(Math.hypot(x, z));
        int low = t;
        for (int[] n : N4) {
            low = Math.min(low, top.applyAsInt(Math.hypot(x + n[0], z + n[1])) + 1);
        }
        return Math.min(low, t);
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
