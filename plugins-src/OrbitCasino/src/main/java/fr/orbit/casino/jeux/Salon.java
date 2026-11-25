package fr.orbit.casino.jeux;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * Le Salon des Étoiles : aile ajoutée à l'est du casino (porte dans le mur est, entre les deux tables de poker).
 * <pre>
 *                      mur nord (z = -17) : PLINKO (planche x 41..52, y 65..75)
 *   porte (x = 31,                                                    ROUE DES ÉTOILES
 *   z -2..2)  ──►        BILLARD (tapis x 43..49, z -1..1)            sur le mur est (x = 61)
 *
 *            borne CRASH (38, 16)                     borne MINES (55, 16)
 * </pre>
 * Construite une seule fois (ou par /casino generer) : les retouches faites à la main dans la grande salle restent,
 * seule la porte est percée dans son mur est. Le jukebox est posé sur le comptoir du bar s'il y a la place.
 */
public final class Salon {

    public static final int X1 = 31, X2 = 61, Z1 = -17, Z2 = 17, FLOOR = 63, Y = 64, CEIL = 76;
    public static final double CX = 46.5, CZ = 0.5;

    /** Billard : tapis de 7 x 3 blocs (x 43.0 → 50.0, z -1.0 → 2.0), dessus du tapis en y = 65. */
    public static final int BIL_X1 = 43, BIL_X2 = 49, BIL_Z1 = -1, BIL_Z2 = 1;
    /** Plinko : planche contre le mur nord. */
    public static final int PK_X1 = 41, PK_X2 = 52, PK_Y1 = 65, PK_Y2 = 75, PK_COUNTER_Z = -14;
    /** Bornes de Crash et de Mines contre le mur sud. */
    public static final int CRASH_X = 38, MINES_X = 55, BORNE_Z = 16;
    /** Roue des Étoiles, contre le mur est. */
    public static final double WHEEL_X = 60.8, WHEEL_Y = 70.0, WHEEL_Z = 0.5;
    /** Jukebox sur le comptoir du bar de la grande salle. */
    public static final int JB_X = -25, JB_Y = 65, JB_Z = 16;

    private Salon() {
    }

    public static boolean built(World w) {
        return w.getBlockAt(46, FLOOR, 10).getType() != Material.AIR;
    }

    private static BlockData data(String s) {
        try {
            return Bukkit.createBlockData(s);
        } catch (IllegalArgumentException e) {
            return Bukkit.createBlockData(s.replace("iron_chain", "chain"));
        }
    }

    private static void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    private static void set(World w, int x, int y, int z, BlockData d) {
        w.getBlockAt(x, y, z).setBlockData(d, false);
    }

    /** Construit (ou reconstruit) l'aile. Retourne le nombre de blocs posés. */
    public static int build(World w) {
        int n = 0;
        for (int x = X1 + 1; x <= X2 + 1; x++) {
            for (int z = Z1 - 1; z <= Z2 + 1; z++) {
                for (int y = FLOOR - 1; y <= CEIL + 1; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (!b.getType().isAir()) {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }
        // sol : moquette bleu nuit à étoiles dorées ; plafond noir, plafonniers bleutés
        for (int x = X1 + 1; x <= X2; x++) {
            for (int z = Z1; z <= Z2; z++) {
                set(w, x, FLOOR - 1, z, Material.BLACKSTONE);
                boolean a = Math.floorMod(x + z, 6) == 0, b = Math.floorMod(x - z, 6) == 0;
                Material m = a && b ? Material.YELLOW_WOOL : a || b ? Material.LIGHT_BLUE_WOOL : Material.BLUE_WOOL;
                if (x == X2 || Math.abs(z) == Z2) {
                    m = Material.POLISHED_BLACKSTONE;
                }
                set(w, x, FLOOR, z, m);
                boolean light = Math.floorMod(x, 6) == 2 && Math.floorMod(z, 6) == 3;
                set(w, x, CEIL, z, light ? Material.PEARLESCENT_FROGLIGHT : Material.BLACK_CONCRETE);
                n += 3;
            }
        }
        // murs nord, sud et est (le mur ouest est celui de la grande salle)
        for (int y = Y; y < CEIL; y++) {
            for (int x = X1 + 1; x <= X2; x++) {
                n += wall(w, x, y, Z1, x) + wall(w, x, y, Z2, x);
            }
            for (int z = Z1 + 1; z < Z2; z++) {
                n += wall(w, X2, y, z, z);
            }
        }
        // porte dans le mur est de la grande salle : arche dorée
        for (int z = -3; z <= 3; z++) {
            for (int y = Y; y <= Y + 4; y++) {
                boolean frame = Math.abs(z) == 3 || y == Y + 4;
                set(w, X1, y, z, frame ? Material.GOLD_BLOCK : Material.AIR);
            }
        }
        // lustres
        for (int[] c : new int[][]{{36, -10}, {36, 10}, {56, -10}, {56, 10}, {44, 0}, {49, 0}}) {
            for (int y = CEIL - 1; y >= CEIL - 3; y--) {
                set(w, c[0], y, c[1], data("minecraft:iron_chain[axis=y]"));
            }
            set(w, c[0], CEIL - 4, c[1], data("minecraft:soul_lantern[hanging=true]"));
            n += 4;
        }
        n += plinko(w) + billard(w) + bornes(w) + roue(w);
        for (int x : new int[]{33, 59}) {
            for (int z : new int[]{-15, 15}) {
                set(w, x, Y, z, data("minecraft:potted_bamboo"));
            }
        }
        n += jukebox(w);
        return n;
    }

    private static int wall(World w, int x, int y, int z, int along) {
        Material m;
        boolean pillar = Math.floorMod(along, 8) == 0;
        if (y == Y) {
            m = Material.POLISHED_BLACKSTONE;
        } else if (y == CEIL - 4) {
            m = Material.LIGHT_BLUE_STAINED_GLASS;
        } else if (pillar) {
            m = y == Y + 3 ? Material.PEARLESCENT_FROGLIGHT : Material.CHISELED_POLISHED_BLACKSTONE;
        } else {
            m = Material.POLISHED_BLACKSTONE_BRICKS;
        }
        set(w, x, y, z, m);
        return 1;
    }

    private static int plinko(World w) {
        int n = 0;
        for (int x = PK_X1; x <= PK_X2; x++) {
            for (int y = PK_Y1; y <= PK_Y2; y++) {
                boolean frame = x == PK_X1 || x == PK_X2 || y == PK_Y1 || y == PK_Y2;
                set(w, x, y, Z1, frame ? Material.GOLD_BLOCK : Material.BLACK_CONCRETE);
                n++;
            }
        }
        // comptoir de lancement
        for (int x = 44; x <= 49; x++) {
            set(w, x, Y, PK_COUNTER_Z, x == 44 || x == 49 ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE);
            n++;
        }
        return n;
    }

    /** Remet la table de billard d'aplomb (bloc cassé ou remplacé) ; retourne le nombre de blocs réparés. */
    public static int repairBillard(World w) {
        int fixed = 0;
        for (int x = BIL_X1 - 1; x <= BIL_X2 + 1; x++) {
            for (int z = BIL_Z1 - 1; z <= BIL_Z2 + 1; z++) {
                Material want = billardBlock(x, z);
                Block b = w.getBlockAt(x, Y, z);
                if (b.getType() != want) {
                    b.setType(want, false);
                    fixed++;
                }
                if (!w.getBlockAt(x, Y + 1, z).getType().isAir()) {
                    w.getBlockAt(x, Y + 1, z).setType(Material.AIR, false); // rien posé sur le tapis
                    fixed++;
                }
                if (w.getBlockAt(x, FLOOR, z).getType().isAir()) {
                    w.getBlockAt(x, FLOOR, z).setType(Material.BLACKSTONE, false);
                    fixed++;
                }
            }
        }
        return fixed;
    }

    private static Material billardBlock(int x, int z) {
        boolean rim = x == BIL_X1 - 1 || x == BIL_X2 + 1 || z == BIL_Z1 - 1 || z == BIL_Z2 + 1;
        boolean pocket = rim && (x == BIL_X1 - 1 || x == BIL_X2 + 1 || x == 46) && (z == BIL_Z1 - 1 || z == BIL_Z2 + 1);
        return pocket ? Material.BLACK_CONCRETE : rim ? Material.STRIPPED_DARK_OAK_WOOD : Material.GREEN_WOOL;
    }

    /** Bloc d'un jeu du salon (table, planche, bornes, roue, jukebox) : jamais cassé, même en créatif. */
    public static boolean isGameBlock(Block b) {
        return isBillard(b) || isPlinko(b) || isBorne(b, CRASH_X) || isBorne(b, MINES_X) || isRoue(b) || isJukebox(b);
    }

    private static int billard(World w) {
        int n = 0;
        for (int x = BIL_X1 - 1; x <= BIL_X2 + 1; x++) {
            for (int z = BIL_Z1 - 1; z <= BIL_Z2 + 1; z++) {
                boolean rim = x == BIL_X1 - 1 || x == BIL_X2 + 1 || z == BIL_Z1 - 1 || z == BIL_Z2 + 1;
                boolean pocket = rim && (x == BIL_X1 - 1 || x == BIL_X2 + 1 || x == 46) && (z == BIL_Z1 - 1 || z == BIL_Z2 + 1);
                set(w, x, Y, z, pocket ? Material.BLACK_CONCRETE : rim ? Material.STRIPPED_DARK_OAK_WOOD : Material.GREEN_WOOL);
                n++;
            }
        }
        return n;
    }

    private static int bornes(World w) {
        int n = 0;
        for (int x : new int[]{CRASH_X, MINES_X}) {
            set(w, x, Y, BORNE_Z, Material.POLISHED_BLACKSTONE);
            set(w, x, Y + 1, BORNE_Z, x == CRASH_X ? Material.SEA_LANTERN : Material.VERDANT_FROGLIGHT);
            set(w, x, Y + 2, BORNE_Z, Material.GOLD_BLOCK);
            n += 3;
        }
        return n;
    }

    private static int roue(World w) {
        int n = 0;
        // fond sombre derrière la roue et socle
        for (int z = -3; z <= 4; z++) {
            for (int y = Y + 2; y <= Y + 10; y++) {
                set(w, X2, y, z, Material.BLACK_CONCRETE);
                n++;
            }
        }
        for (int z = -1; z <= 2; z++) {
            set(w, X2 - 1, Y, z, Material.GOLD_BLOCK);
            n++;
        }
        return n;
    }

    /** Pose le jukebox sur le comptoir du bar s'il y a de la place (ne touche pas aux retouches). */
    public static int jukebox(World w) {
        Block b = w.getBlockAt(JB_X, JB_Y, JB_Z);
        if (b.getType().isAir()) {
            b.setType(Material.JUKEBOX, false);
            return 1;
        }
        return 0;
    }

    // ------------------------------------------------------------------ repérage des clics

    public static boolean isPlinko(Block b) {
        return (b.getZ() == Z1 && b.getX() >= PK_X1 && b.getX() <= PK_X2 && b.getY() >= PK_Y1 && b.getY() <= PK_Y2)
                || (b.getZ() == PK_COUNTER_Z && b.getY() == Y && b.getX() >= 44 && b.getX() <= 49);
    }

    public static boolean isBillard(Block b) {
        return b.getY() == Y && b.getX() >= BIL_X1 - 1 && b.getX() <= BIL_X2 + 1 && b.getZ() >= BIL_Z1 - 1 && b.getZ() <= BIL_Z2 + 1;
    }

    public static boolean isBorne(Block b, int x) {
        return b.getX() == x && b.getZ() == BORNE_Z && b.getY() >= Y && b.getY() <= Y + 2;
    }

    public static boolean isRoue(Block b) {
        return (b.getX() == X2 && b.getZ() >= -3 && b.getZ() <= 4 && b.getY() >= Y + 2 && b.getY() <= Y + 10)
                || (b.getX() == X2 - 1 && b.getY() == Y && b.getZ() >= -1 && b.getZ() <= 2);
    }

    public static boolean isJukebox(Block b) {
        return b.getX() == JB_X && b.getY() == JB_Y && b.getZ() == JB_Z;
    }
}
