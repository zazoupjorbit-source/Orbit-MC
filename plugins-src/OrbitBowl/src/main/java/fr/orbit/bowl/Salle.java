package fr.orbit.bowl;

import fr.orbit.bowl.fleches.PartieFleches;
import fr.orbit.bowl.golf.Golf;
import fr.orbit.bowl.laser.LaserNova;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Plan d'ORBIT BOWL (monde vide {@code bowl}) et sa construction par le code. Sol à y = 63 (on marche à y = 64),
 * plafond à y = 77, murs à x = ±39, z = -27 / 45.
 * <pre>
 *  z -27 ┌──────────────── machines à quilles · grand logo ORBIT BOWL au-dessus des pistes ───────────────┐
 *        │  COMPTOIR        │ 1 MERCURE │ 2 VÉNUS │ 3 TERRE │ 4 MARS │ 5 JUPITER │ 6 SATURNE │  ACCUEIL     │
 *        │  DES ÉTOILES     │   (6 pistes de 3 blocs, tête de quille à 18 blocs de la ligne)  │  RECORDS     │
 *  z   0 │  (bar)           ═══════════════ ligne de faute ═══ élan ═══ retours de boules ══════  (mur est)   │
 *        │                  consoles + banquettes des pistes                                                  │
 *  z  11 │════════════════════════ ALLÉE CENTRALE (arrivée au milieu, sortie à l'est) ═══════════════════════│
 *        │ FLÉCHETTES (3 cibles,   BABY-ORBIT (2 tables)     salon       TIR AUX COMÈTES (2 stands)           │
 *        │  mur ouest)             PANIER ORBITAL (3 bornes)            LASER NOVA (console)                  │
 *  z  45 └──── porte du golf (x -26) ──────────── porte du laser game (x 9) ───────────────────────────────┘
 *              GOLF DES ASTÉROÏDES : terrasse sous les étoiles      LASER NOVA : arène néon
 * </pre>
 */
public final class Salle {

    public static final int FLOOR = 63, Y = 64, CEIL = 77, X1 = -39, X2 = 39, Z1 = -27, Z2 = 45;
    /** Repère « déjà construite » : bloc de la ligne de faute de la piste 1. */
    private static final int MARK_X = -15, MARK_Z = 0;

    // ------------------------------------------------------------------ bowling
    public static final int PISTES = 6;
    public static final String[] PLANETES = {"MERCURE", "VÉNUS", "TERRE", "MARS", "JUPITER", "SATURNE"};
    public static final double FAUTE_Z = 0.0, TETE_Z = -18.0, DECK_Z = -21.0, FOSSE_Z = -23.0;
    public static final double DEMI_LARGEUR = 1.5, GOUTTIERE = 1.0, PAROI = 2.5;

    public static double pisteX(int i) {
        return -14.5 + 6 * i;
    }

    // ------------------------------------------------------------------ fléchettes (mur ouest)
    public static final double CIBLE_X = -37.94, CIBLE_Y = 65.6, OCHE_X = -33.0;

    public static double cibleZ(int k) {
        return 22.5 + 7 * k;
    }

    // ------------------------------------------------------------------ baby-foot
    public static final double BABY_X0 = -22.0, BABY_X1 = -15.0, BABY_H = 4.0, BABY_Y = 65.0;

    public static double babyZ0(int k) {
        return 20.0 + 9 * k;
    }

    // ------------------------------------------------------------------ basket
    public static final double BASKET_TIR_Z = 37.5, PANIER_Z = 43.35, PANIER_Y = 67.2, PANNEAU_Z = 44.0;

    public static double basketX(int k) {
        return -10.5 + 5 * k;
    }

    // ------------------------------------------------------------------ tir forain
    public static final double TIR_X = 25.6, CIBLES_X = 35.5;

    public static double tirZ(int k) {
        return 24.5 + 13 * k;
    }

    private Salle() {
    }

    public static Location spawn(World w) {
        return new Location(w, 0.5, Y, 13.5, 180f, 0f);
    }

    public static Location sortie(World w) {
        return new Location(w, 38.4, Y + 1.0, 13.5);
    }

    /** Devant une console, tourné vers elle. */
    public static Location devant(Location console) {
        Vector d = console.getDirection().setY(0).normalize();
        Location r = console.clone().add(d.multiply(1.6));
        r.setYaw(console.getYaw() + 180f);
        r.setPitch(0f);
        return r;
    }

    public static boolean construite(World w) {
        return w.getBlockAt(MARK_X, FLOOR, MARK_Z).getType() == Material.SEA_LANTERN;
    }

    // ------------------------------------------------------------------ outils de construction

    private static World w;
    private static int poses;

    private static BlockData data(String s) {
        try {
            return Bukkit.createBlockData(s);
        } catch (IllegalArgumentException e) {
            return Bukkit.createBlockData(s.replace("iron_chain", "chain"));
        }
    }

    static void set(int x, int y, int z, Material m) {
        Block b = w.getBlockAt(x, y, z);
        if (b.getType() != m) {
            b.setType(m, false);
        }
        poses++;
    }

    static void set(int x, int y, int z, String d) {
        w.getBlockAt(x, y, z).setBlockData(data(d), false);
        poses++;
    }

    static void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, m);
                }
            }
        }
    }

    /** Construit (ou reconstruit) toute la salle, le golf et l'arène laser. Retourne le nombre de blocs posés. */
    public static int construire(World monde) {
        w = monde;
        poses = 0;
        // on vide tout le volume (salle + terrasse du golf + arène laser)
        for (int x = X1 - 1; x <= X2 + 1; x++) {
            for (int z = Z1 - 1; z <= 97; z++) {
                for (int y = 58; y <= 80; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (!b.getType().isAir()) {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }
        coque();
        bowling();
        bar();
        accueil();
        fletchettes();
        baby();
        basket();
        tir();
        salon();
        Golf.construire(w);
        LaserNova.construire(w);
        int n = poses;
        w = null;
        return n;
    }

    // ------------------------------------------------------------------ murs, sol, plafond

    private static void coque() {
        for (int x = X1 + 1; x < X2; x++) {
            for (int z = Z1 + 1; z < Z2; z++) {
                set(x, FLOOR - 1, z, Material.BLACKSTONE);
                set(x, FLOOR, z, moquette(x, z));
                boolean allee = z >= 11 && z <= 16;
                boolean lumiere = Math.floorMod(x, 5) == 2 && Math.floorMod(z, 5) == 2;
                set(x, CEIL, z, allee ? (Math.floorMod(x, 4) == 0 ? Material.PEARLESCENT_FROGLIGHT : Material.GLASS)
                        : lumiere ? Material.PEARLESCENT_FROGLIGHT : Material.BLACK_CONCRETE);
            }
        }
        for (int y = FLOOR; y <= CEIL; y++) {
            for (int x = X1; x <= X2; x++) {
                set(x, y, Z1, mur(x, y));
                set(x, y, Z2, mur(x, y));
            }
            for (int z = Z1 + 1; z < Z2; z++) {
                set(X1, y, z, mur(z, y));
                set(X2, y, z, mur(z, y));
            }
        }
        // sortie : arche néon dorée sur le mur est, au bout de l'allée centrale
        for (int z = 11; z <= 16; z++) {
            for (int y = Y; y <= Y + 4; y++) {
                boolean cadre = z == 11 || z == 16 || y == Y + 4;
                set(X2, y, z, cadre ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_STAINED_GLASS);
            }
        }
        set(X2 - 1, Y + 1, 13, "minecraft:polished_blackstone_button[face=wall,facing=west]");
        set(X2 - 1, Y + 1, 14, "minecraft:polished_blackstone_button[face=wall,facing=west]");
        // lustres de l'allée
        for (int x = -32; x <= 32; x += 8) {
            for (int y = CEIL - 1; y >= CEIL - 3; y--) {
                set(x, y, 13, "minecraft:iron_chain[axis=y]");
            }
            set(x, CEIL - 4, 13, "minecraft:soul_lantern[hanging=true]");
        }
    }

    /** Moquette « cosmic bowling » : bleu nuit, étoiles bleu glace et dorées, allée centrale bordée d'or. */
    private static Material moquette(int x, int z) {
        if (z == 11 || z == 16) {
            return Material.GOLD_BLOCK;
        }
        if (z > 11 && z < 16) {
            return Math.floorMod(x, 4) == 0 ? Material.LIGHT_BLUE_WOOL : Material.BLUE_WOOL;
        }
        boolean a = Math.floorMod(x + z, 7) == 0, b = Math.floorMod(x - 2 * z, 9) == 0;
        if (a && b) {
            return Material.YELLOW_WOOL;
        }
        if (a || b) {
            return Math.floorMod(x * 3 + z, 11) == 0 ? Material.MAGENTA_WOOL : Material.LIGHT_BLUE_WOOL;
        }
        return Material.BLUE_WOOL;
    }

    private static Material mur(int le, int y) {
        if (y == FLOOR || y == Y) {
            return Material.POLISHED_BLACKSTONE;
        }
        if (y == CEIL - 4) {
            return Material.LIGHT_BLUE_STAINED_GLASS;
        }
        if (y == CEIL) {
            return Material.POLISHED_BLACKSTONE;
        }
        if (Math.floorMod(le, 8) == 0) {
            return y == Y + 3 || y == CEIL - 2 ? Material.PEARLESCENT_FROGLIGHT : Material.CHISELED_POLISHED_BLACKSTONE;
        }
        return y == Y + 1 ? Material.BLUE_TERRACOTTA : Material.POLISHED_BLACKSTONE_BRICKS;
    }

    // ------------------------------------------------------------------ pistes

    private static void bowling() {
        // machines à quilles au fond : bloc noir sur toute la largeur, rideau noir face aux pistes
        fill(-18, FLOOR, -26, 18, CEIL - 1, -24, Material.BLACKSTONE);
        fill(-17, FLOOR, -24, 17, Y + 2, -24, Material.BLACK_WOOL);
        // panneau de masquage au-dessus des quilles (logo et noms des pistes posés en entités)
        for (int x = -18; x <= 18; x++) {
            for (int y = Y + 3; y < CEIL; y++) {
                Material m = y == Y + 3 || y == Y + 6 ? Material.GOLD_BLOCK : Material.BLACK_CONCRETE;
                set(x, y, -16, m);
            }
            set(x, Y + 3, -15, "minecraft:light_blue_stained_glass_pane");
        }
        for (int i = 0; i < PISTES; i++) {
            int b0 = -16 + 6 * i;                // première colonne du lit de la piste
            int gG = b0 - 1, gD = b0 + 3;         // gouttières
            for (int z = -21; z <= -1; z++) {
                for (int x = b0; x <= b0 + 2; x++) {
                    boolean fleche = (z == -6 && x == b0 + 1) || (z == -5 && x != b0 + 1);
                    boolean point = z == -2 && x == b0 + 1;
                    set(x, FLOOR, z, fleche || point ? Material.OCHRE_FROGLIGHT : Material.BIRCH_PLANKS);
                    set(x, FLOOR - 1, z, Material.BLACKSTONE);
                }
                for (int g : new int[]{gG, gD}) {
                    set(g, FLOOR, z, Material.AIR);
                    set(g, FLOOR - 1, z, Material.BLUE_STAINED_GLASS);
                    set(g, FLOOR - 2, z, Material.SEA_LANTERN);
                    set(g, FLOOR - 3, z, Material.BLACKSTONE);
                }
            }
            // fosse derrière les quilles
            for (int z = -23; z <= -22; z++) {
                for (int x = gG; x <= gD; x++) {
                    set(x, FLOOR, z, Material.AIR);
                    set(x, FLOOR - 1, z, Material.AIR);
                    set(x, FLOOR - 2, z, Material.BLACK_CONCRETE);
                }
            }
            // élan : ligne de faute néon, puis bois clair avec deux rangées de repères
            for (int z = 0; z <= 4; z++) {
                for (int x = gG; x <= gD; x++) {
                    boolean repere = (z == 2 || z == 4) && x == b0 + 1;
                    set(x, FLOOR, z, z == 0 ? Material.SEA_LANTERN : repere ? Material.OCHRE_FROGLIGHT : Material.BIRCH_PLANKS);
                }
            }
            // console de la piste + banquette
            int cx = b0 + 1;
            set(cx, Y, 6, Material.POLISHED_BLACKSTONE);
            set(cx, Y + 1, 6, Material.LIGHT_BLUE_STAINED_GLASS);
            for (int x = b0 - 1; x <= b0 + 3; x++) {
                set(x, FLOOR, 5, Material.GOLD_BLOCK);
                set(x, Y, 9, "minecraft:dark_prismarine_stairs[facing=south]");
                set(x, FLOOR, 10, Material.GOLD_BLOCK);
            }
            set(b0 - 1, Y, 8, "minecraft:dark_prismarine_stairs[facing=east]");
            set(b0 + 3, Y, 8, "minecraft:dark_prismarine_stairs[facing=west]");
        }
        // séparations (chapeaux) entre les pistes, parois des quilles, retours de boules
        for (int k = 0; k <= PISTES; k++) {
            int x = -18 + 6 * k;
            for (int z = -21; z <= 4; z++) {
                set(x, FLOOR, z, Material.POLISHED_BLACKSTONE_BRICKS);
            }
            for (int z = -23; z <= -16; z++) {
                set(x, FLOOR, z, Material.POLISHED_BLACKSTONE);
                set(x, Y, z, Material.POLISHED_BLACKSTONE);
                set(x, Y + 1, z, z % 2 == 0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.POLISHED_BLACKSTONE);
                set(x, Y + 2, z, Material.POLISHED_BLACKSTONE_SLAB);
            }
            if (k == 1 || k == 3 || k == 5) {
                for (int z = 1; z <= 3; z++) {
                    set(x, Y, z, Material.POLISHED_BLACKSTONE_BRICKS);
                }
                set(x, Y + 1, 1, Material.POLISHED_BLACKSTONE_SLAB);
                set(x, Y + 1, 3, Material.SEA_LANTERN);
            }
        }
        // côtés de la zone des pistes : murets néon
        for (int z = -26; z <= 10; z++) {
            for (int x : new int[]{-19, 19}) {
                set(x, Y, z, Material.POLISHED_BLACKSTONE_BRICKS);
                set(x, Y + 1, z, z % 4 == 0 ? Material.SEA_LANTERN : Material.LIGHT_BLUE_STAINED_GLASS);
            }
        }
        for (int x : new int[]{-19, 19}) {
            for (int z = 5; z <= 10; z++) {
                set(x, Y, z, Material.AIR);
                set(x, Y + 1, z, Material.AIR);
            }
        }
    }

    // ------------------------------------------------------------------ bar (nord-ouest) et accueil (nord-est)

    private static void bar() {
        for (int x = -35; x <= -23; x++) {
            set(x, Y, -6, Material.POLISHED_BLACKSTONE_BRICKS);
            set(x, Y + 1, -6, "minecraft:smooth_quartz_slab[type=bottom]");
            set(x, FLOOR, -5, Material.GOLD_BLOCK);
        }
        for (int x = -35; x <= -23; x++) {
            for (int y = Y; y <= Y + 4; y++) {
                set(x, y, -26, y == Y + 2 ? Material.SEA_LANTERN : y % 2 == 0 ? Material.DARK_OAK_PLANKS : Material.BOOKSHELF);
            }
        }
        for (int x = -34; x <= -24; x += 2) {
            set(x, Y, -3, Material.DARK_OAK_FENCE);
            set(x, Y + 1, -3, Material.BLUE_CARPET);
        }
        // tables rondes et banquettes
        for (int[] t : new int[][]{{-33, 3}, {-27, 3}, {-33, 8}, {-27, 8}}) {
            set(t[0], Y, t[1], Material.DARK_OAK_FENCE);
            set(t[0], Y + 1, t[1], "minecraft:smooth_quartz_slab[type=bottom]");
            set(t[0] - 1, Y, t[1], "minecraft:dark_prismarine_stairs[facing=west]");
            set(t[0] + 1, Y, t[1], "minecraft:dark_prismarine_stairs[facing=east]");
        }
        for (int[] p : new int[][]{{-37, -25}, {-21, -25}, {-37, 9}, {-21, 9}}) {
            set(p[0], Y, p[1], "minecraft:potted_bamboo");
        }
    }

    private static void accueil() {
        for (int x = 24; x <= 34; x++) {
            set(x, Y, -6, Material.POLISHED_BLACKSTONE_BRICKS);
            set(x, Y + 1, -6, Material.GOLD_BLOCK);
            set(x, FLOOR, -5, Material.LIGHT_BLUE_WOOL);
        }
        // casiers à chaussures derrière le comptoir
        for (int x = 24; x <= 34; x++) {
            for (int y = Y; y <= Y + 3; y++) {
                set(x, y, -9, (x + y) % 2 == 0 ? Material.BLUE_TERRACOTTA : Material.LIGHT_BLUE_TERRACOTTA);
            }
        }
        // mur des records : cadres dorés sur le mur est
        for (int z = -24; z <= 6; z++) {
            for (int y = Y + 1; y <= Y + 10; y++) {
                boolean cadre = (z + 24) % 10 == 0 || y == Y + 1 || y == Y + 5 || y == Y + 10;
                if (cadre) {
                    set(X2, y, z, Material.GOLD_BLOCK);
                } else {
                    set(X2, y, z, Material.BLACK_CONCRETE);
                }
            }
        }
        for (int[] p : new int[][]{{21, -25}, {37, -25}, {21, 9}, {37, 9}}) {
            set(p[0], Y, p[1], "minecraft:potted_bamboo");
        }
    }

    // ------------------------------------------------------------------ jeux du sud

    private static void fletchettes() {
        for (int k = 0; k < 3; k++) {
            int zc = (int) Math.floor(cibleZ(k));
            // cabinet de la cible sur le mur ouest
            for (int z = zc - 2; z <= zc + 2; z++) {
                for (int y = Y; y <= Y + 4; y++) {
                    boolean bord = Math.abs(z - zc) == 2 || y == Y || y == Y + 4;
                    set(X1, y, z, bord ? Material.GOLD_BLOCK : Material.BLACK_CONCRETE);
                }
            }
            set(X1 + 1, Y + 4, zc, "minecraft:soul_lantern[hanging=false]");
            // ligne de lancer (oche) néon
            for (int z = zc - 1; z <= zc + 1; z++) {
                set((int) OCHE_X, FLOOR, z, Material.SEA_LANTERN);
            }
            // console
            set(-30, Y, zc, Material.POLISHED_BLACKSTONE);
            set(-30, Y + 1, zc, Material.LIGHT_BLUE_STAINED_GLASS);
        }
        // cloisons entre les cibles
        for (int z : new int[]{26, 33}) {
            for (int x = X1 + 1; x <= X1 + 3; x++) {
                for (int y = Y; y <= Y + 3; y++) {
                    set(x, y, z, y == Y + 3 ? Material.POLISHED_BLACKSTONE_SLAB : Material.LIGHT_BLUE_STAINED_GLASS);
                }
            }
        }
    }

    private static void baby() {
        for (int k = 0; k < 2; k++) {
            int z0 = (int) babyZ0(k);
            for (int x = (int) BABY_X0 - 1; x <= (int) BABY_X1; x++) {
                for (int z = z0 - 1; z <= z0 + (int) BABY_H; z++) {
                    boolean bord = x == (int) BABY_X0 - 1 || x == (int) BABY_X1 || z == z0 - 1 || z == z0 + (int) BABY_H;
                    set(x, Y, z, bord ? Material.DARK_OAK_PLANKS : Material.GREEN_CONCRETE);
                }
            }
            // ligne médiane et rond central en bleu glace
            for (int z = z0; z < z0 + (int) BABY_H; z++) {
                set(-19, Y, z, Material.LIME_CONCRETE);
            }
            set(-13, Y, z0 + 2, Material.POLISHED_BLACKSTONE);
            set(-13, Y + 1, z0 + 2, Material.LIGHT_BLUE_STAINED_GLASS);
        }
    }

    private static void basket() {
        for (int k = 0; k < 3; k++) {
            int c = (int) Math.floor(basketX(k));
            for (int z = 38; z <= 44; z++) {
                for (int y = Y; y <= Y + 4; y++) {
                    set(c - 2, y, z, y == Y + 4 ? Material.POLISHED_BLACKSTONE_SLAB : z == 44 ? Material.BLACK_CONCRETE
                            : Material.LIGHT_BLUE_STAINED_GLASS);
                    set(c + 2, y, z, y == Y + 4 ? Material.POLISHED_BLACKSTONE_SLAB : z == 44 ? Material.BLACK_CONCRETE
                            : Material.LIGHT_BLUE_STAINED_GLASS);
                }
                for (int x = c - 1; x <= c + 1; x++) {
                    set(x, FLOOR, z, z >= 41 ? Material.ORANGE_CONCRETE : Material.BLACK_CONCRETE);
                }
            }
            // panneau du fond (lumineux) derrière le panier
            for (int x = c - 1; x <= c + 1; x++) {
                for (int y = Y; y <= Y + 5; y++) {
                    set(x, y, 44, y >= Y + 2 && y <= Y + 4 ? Material.WHITE_CONCRETE : Material.BLACK_CONCRETE);
                }
            }
            for (int x = c - 1; x <= c + 1; x++) {
                set(x, FLOOR, 37, Material.SEA_LANTERN);
            }
            set(c, Y, 35, Material.POLISHED_BLACKSTONE);
            set(c, Y + 1, 35, Material.ORANGE_STAINED_GLASS);
        }
    }

    private static void tir() {
        for (int k = 0; k < 2; k++) {
            int zc = (int) Math.floor(tirZ(k));
            int za = zc - 6, zb = zc + 6;
            // comptoir
            for (int z = za + 1; z <= zb - 1; z++) {
                set(26, Y, z, Material.DARK_OAK_PLANKS);
                set(26, Y + 1, z, "minecraft:dark_oak_slab[type=bottom]");
            }
            // cloisons, auvent rayé bleu et or, décor étoilé du fond
            for (int x = 26; x <= 38; x++) {
                for (int y = Y; y <= Y + 6; y++) {
                    set(x, y, za, y == Y + 6 ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS);
                    set(x, y, zb, y == Y + 6 ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS);
                }
                for (int z = za; z <= zb; z++) {
                    set(x, Y + 7, z, Math.floorMod(z, 2) == 0 ? Material.BLUE_WOOL : Material.YELLOW_WOOL);
                    if (x > 26) {
                        set(x, FLOOR, z, Material.BLACK_CONCRETE);
                    }
                }
            }
            for (int z = za + 1; z <= zb - 1; z++) {
                for (int y = Y; y <= Y + 6; y++) {
                    boolean etoile = Math.floorMod(z * 7 + y * 3, 11) == 0;
                    set(38, y, z, etoile ? Material.PEARLESCENT_FROGLIGHT : Material.BLACK_CONCRETE);
                }
                // rails des trois rangées de cibles
                for (int y : new int[]{Y, Y + 2}) {
                    set(36, y, z, Material.POLISHED_BLACKSTONE_SLAB);
                }
            }
            set(23, Y, zc, Material.POLISHED_BLACKSTONE);
            set(23, Y + 1, zc, Material.ORANGE_STAINED_GLASS);
        }
    }

    private static void salon() {
        // canapés autour d'une table basse, au milieu du sud
        for (int[] c : new int[][]{{6, 22}, {14, 22}, {6, 30}, {14, 30}}) {
            for (int dx = -1; dx <= 1; dx++) {
                set(c[0] + dx, Y, c[1] - 2, "minecraft:dark_prismarine_stairs[facing=north]");
                set(c[0] + dx, Y, c[1] + 2, "minecraft:dark_prismarine_stairs[facing=south]");
            }
            set(c[0], Y, c[1], Material.POLISHED_BLACKSTONE);
            set(c[0], Y + 1, c[1], "minecraft:smooth_quartz_slab[type=bottom]");
        }
        // console du laser game devant sa porte, console du golf devant la sienne
        set(9, Y, 43, Material.POLISHED_BLACKSTONE);
        set(9, Y + 1, 43, Material.MAGENTA_STAINED_GLASS);
        set(-26, Y, 43, Material.POLISHED_BLACKSTONE);
        set(-26, Y + 1, 43, Material.LIME_STAINED_GLASS);
        // grande baie vitrée sur l'arène laser
        for (int x = 13; x <= 24; x++) {
            for (int y = Y + 2; y <= Y + 6; y++) {
                set(x, y, Z2, y == Y + 2 || y == Y + 6 ? Material.GOLD_BLOCK : Material.TINTED_GLASS);
            }
        }
    }

    // ------------------------------------------------------------------ entités de décor (non persistantes)

    public static TextDisplay texte(Location at, String mm, float taille, boolean billboard) {
        return at.getWorld().spawn(at, TextDisplay.class, e -> {
            OrbitBowl.marquer(e);
            e.text(Txt.mm(mm));
            e.setBillboard(billboard ? Display.Billboard.CENTER : Display.Billboard.FIXED);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setShadowed(true);
            e.setBackgroundColor(Color.fromARGB(150, 10, 12, 18));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setLineWidth(260);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(taille, taille, taille), new AxisAngle4f()));
        });
    }

    /** Image plate du pack (logo, enseigne) : {@code largeur} × {@code hauteur} blocs, tournée vers {@code yaw}. */
    public static ItemDisplay image(World w, double x, double y, double z, float yaw, String modele, float largeur, float hauteur) {
        ItemStack it = Activite.objet(Material.PAPER, modele, "");
        return w.spawn(new Location(w, x, y, z, yaw, 0f), ItemDisplay.class, d -> {
            OrbitBowl.marquer(d);
            d.setItemStack(it);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(3f);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(largeur, hauteur, 1f), new AxisAngle4f()));
        });
    }

    /** Logo, noms des pistes, enseignes : posés au démarrage (et après une reconstruction). */
    public static void decorer(World w) {
        image(w, 0.5, Y + 9.6, -14.93, 0f, "logo", 20f, 5f);
        for (int i = 0; i < PISTES; i++) {
            texte(new Location(w, pisteX(i), Y + 4.15, -14.9, 0f, 0f),
                    "<gradient:#62CFF4:#3A7FFF><b>" + (i + 1) + "</b></gradient> <#E0B864>" + PLANETES[i], 1.6f, false)
                    .setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        }
        texte(new Location(w, -29, Y + 4.2, -5.4, 0f, 0f),
                "<gradient:#E0B864:#62CFF4><b>✦ LE COMPTOIR DES ÉTOILES ✦</b></gradient>\n<#9AA4B2>bar de l'ORBIT BOWL", 1.3f, false);
        texte(new Location(w, 29, Y + 4.2, -5.4, 0f, 0f),
                "<gradient:#3A7FFF:#E0B864><b>ACCUEIL · CHAUSSURES</b></gradient>\n<#9AA4B2>parle à l'hôtesse pour les règles", 1.3f, false);
        texte(new Location(w, X2 - 0.1, Y + 11.2, -9, 90f, 0f), "<#E0B864><b>🏆 MUR DES RECORDS</b>", 1.6f, false)
                .setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        for (int k = 0; k < 3; k++) {
            double zc = cibleZ(k);
            image(w, CIBLE_X, CIBLE_Y, zc, -90f, "cible", (float) (2 * PartieFleches.R_AFF), (float) (2 * PartieFleches.R_AFF));
            // numéros posés par le code : toujours en face de la bonne case, quel que soit le sens de l'image
            for (int i = 0; i < 20; i++) {
                double a = Math.toRadians(i * 18), r = 0.81;
                double u = Math.sin(a) * r, v = Math.cos(a) * r;
                texte(new Location(w, CIBLE_X + 0.04, CIBLE_Y + v - 0.06, zc - u, -90f, 0f), "<#E6EDF3><b>" + PartieFleches.SECTEURS[i], 0.32f, false)
                        .setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            }
        }
        texte(new Location(w, -31, Y + 5.4, 29.5, -90f, 0f), Jeu.FLECHETTES.titre() + "\n<#9AA4B2>3 cibles · lance depuis la ligne néon", 1.2f, false);
        texte(new Location(w, -18.5, Y + 3.6, 27.5, 0f, 0f), Jeu.BABY.titre() + "\n<#9AA4B2>2 tables", 1.2f, true);
        texte(new Location(w, -5.5, Y + 6.4, 36.2, 180f, 0f), Jeu.BASKET.titre(), 1.4f, false);
        texte(new Location(w, 23.5, Y + 6.4, 31.0, 90f, 0f), Jeu.TIR.titre() + "\n<#9AA4B2>la fête foraine de l'espace", 1.3f, false);
        texte(new Location(w, 9.5, Y + 5.0, 44.0, 180f, 0f), Jeu.LASER.titre() + "\n<#9AA4B2>l'arène est derrière la vitre", 1.3f, false);
        texte(new Location(w, -26, Y + 5.0, 44.0, 180f, 0f), Jeu.GOLF.titre() + "\n<#9AA4B2>terrasse sous les étoiles", 1.3f, false);
        texte(new Location(w, X2 - 0.6, Y + 5.6, 13.5, 90f, 0f), "<#62CFF4><b>⬅ SORTIE</b>\n<#9AA4B2>retour à l'arcade · appuie sur le bouton", 1.0f, false);
        fr.orbit.bowl.basket.PartieBasket.decorer(w);
        Golf.decorer(w);
        LaserNova.decorer(w);
    }
}
