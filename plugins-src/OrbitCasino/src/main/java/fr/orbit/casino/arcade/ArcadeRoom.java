package fr.orbit.casino.arcade;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * Plan de la salle d'arcade cachée (même monde vide que le casino, au sud : centre 0 64 400) et sa construction.
 * <pre>
 *                      mur nord (z = 384) : grand écran ORBIT RUSH
 *     bornes déco     [ PILOTE · ORBIT RUSH ]      bornes déco     classement à gauche
 *
 *  VOLE MON ORBITO (ouest)        ⬢ ARCADE ⬢         CASINO (est)
 *
 *     bornes déco          ENTRÉE / SORTIE (0, 416)      bornes déco
 * </pre>
 * Sol à y = 63 (on marche à y = 64), plafond à y = 73, murs à x = ±22 et z = 384 / 416.
 */
public final class ArcadeRoom {

    public static final int CX = 0, CZ = 400, FLOOR = 63, Y = 64, CEIL = 73, HX = 22, HZ = 16;
    public static final int NORTH = CZ - HZ, SOUTH = CZ + HZ;
    public static final int EXIT_X = 0, EXIT_Y = 65, EXIT_Z = SOUTH - 1;

    private static final String[] COLORS = {"magenta", "cyan", "lime", "orange", "purple", "light_blue", "pink", "yellow"};

    private ArcadeRoom() {
    }

    public static Location spawn(World w) {
        return new Location(w, 0.5, Y, SOUTH - 3.5, 180f, 0f);
    }

    public static Location rushNpc(World w) {
        return new Location(w, 0.5, Y, NORTH + 4.5, 0f, 0f);
    }

    /** Où l'on revient après une course : devant le pilote, face à l'écran. */
    public static Location rushReturn(World w) {
        return new Location(w, 0.5, Y, NORTH + 7.5, 180f, 0f);
    }

    public static Location volNpc(World w) {
        return new Location(w, -HX + 3.5, Y, CZ + 0.5, -90f, 0f);
    }

    public static Location casinoNpc(World w) {
        return new Location(w, HX - 2.5, Y, CZ + 0.5, 90f, 0f);
    }

    public static Location board(World w) {
        return new Location(w, -12.5, Y + 4.2, NORTH + 1.4, 0f, 0f);
    }

    private static BlockData data(String s) {
        return Bukkit.createBlockData(s);
    }

    private static void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    private static void set(World w, int x, int y, int z, String d) {
        w.getBlockAt(x, y, z).setBlockData(data(d), false);
    }

    private static Material mat(String color, String suffix) {
        return Material.valueOf((color + "_" + suffix).toUpperCase(java.util.Locale.ROOT));
    }

    /** Construit (ou reconstruit) toute la salle. Retourne le nombre de blocs posés. */
    public static int build(World w) {
        int n = 0;
        for (int x = -HX - 1; x <= HX + 1; x++) {
            for (int z = NORTH - 1; z <= SOUTH + 1; z++) {
                for (int y = FLOOR - 1; y <= CEIL + 1; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (!b.getType().isAir()) {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }
        // sol : moquette noire à damier néon (violet / cyan), croisements roses ; plafond noir à néons
        for (int x = -HX; x <= HX; x++) {
            for (int z = NORTH; z <= SOUTH; z++) {
                set(w, x, FLOOR - 1, z, Material.BLACKSTONE);
                boolean a = Math.floorMod(x + z, 6) == 0, b = Math.floorMod(x - z, 6) == 0;
                Material m = a && b ? Material.PINK_WOOL : a ? Material.PURPLE_WOOL : b ? Material.CYAN_WOOL : Material.BLACK_WOOL;
                if (Math.abs(x) == HX || z == NORTH || z == SOUTH) {
                    m = Material.POLISHED_BLACKSTONE;
                }
                set(w, x, FLOOR, z, m);
                boolean light = Math.floorMod(x, 5) == 2 && Math.floorMod(z - NORTH, 5) == 2;
                set(w, x, CEIL, z, light ? (Math.floorMod(x + z, 2) == 0 ? Material.PEARLESCENT_FROGLIGHT : Material.VERDANT_FROGLIGHT)
                        : Material.BLACK_CONCRETE);
                n += 3;
            }
        }
        // murs : béton noir, plinthe, bandeau néon rose/vert, piliers lumineux
        for (int y = Y; y < CEIL; y++) {
            for (int x = -HX; x <= HX; x++) {
                n += wall(w, x, y, NORTH, x) + wall(w, x, y, SOUTH, x);
            }
            for (int z = NORTH + 1; z < SOUTH; z++) {
                n += wall(w, -HX, y, z, z) + wall(w, HX, y, z, z);
            }
        }
        n += rushScreen(w) + sideScreen(w, -1, "orange", Material.YELLOW_STAINED_GLASS) + sideScreen(w, 1, "red", Material.RED_STAINED_GLASS);
        // bornes d'arcade décoratives le long des murs nord et sud
        int i = 0;
        for (int x : new int[]{-19, -16, -9, -6, 6, 9, 16, 19}) {
            n += cabinet(w, x, NORTH + 1, 1, COLORS[i++ % COLORS.length]);
        }
        for (int x : new int[]{-19, -16, -13, -9, 9, 13, 16, 19}) {
            n += cabinet(w, x, SOUTH - 1, -1, COLORS[i++ % COLORS.length]);
        }
        // îlot central : socle néon et poufs
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(w, dx, Y, CZ + dz, dx == 0 && dz == 0 ? Material.SEA_LANTERN : Material.POLISHED_BLACKSTONE);
            }
        }
        set(w, 0, Y + 1, CZ, "minecraft:end_rod[facing=up]");
        for (int[] p : new int[][]{{-4, 0}, {4, 0}, {0, -4}, {0, 4}}) {
            set(w, p[0], Y, CZ + p[1], Material.MAGENTA_WOOL);
        }
        // sortie : arche néon et bouton
        for (int y = Y; y <= Y + 3; y++) {
            set(w, EXIT_X - 2, y, SOUTH, Material.PEARLESCENT_FROGLIGHT);
            set(w, EXIT_X + 2, y, SOUTH, Material.PEARLESCENT_FROGLIGHT);
        }
        for (int x = EXIT_X - 2; x <= EXIT_X + 2; x++) {
            set(w, x, Y + 4, SOUTH, Material.PEARLESCENT_FROGLIGHT);
        }
        for (int x = EXIT_X - 1; x <= EXIT_X + 1; x++) {
            for (int y = Y; y <= Y + 3; y++) {
                set(w, x, y, SOUTH, Material.PURPLE_STAINED_GLASS);
            }
        }
        set(w, EXIT_X, EXIT_Y, SOUTH, Material.POLISHED_BLACKSTONE);
        set(w, EXIT_X, EXIT_Y, EXIT_Z, "minecraft:polished_blackstone_button[face=wall,facing=north]");
        return n + 40;
    }

    private static int wall(World w, int x, int y, int z, int along) {
        Material m;
        if (y == Y) {
            m = Material.POLISHED_BLACKSTONE;
        } else if (y == CEIL - 3) {
            m = Math.floorMod(along, 2) == 0 ? Material.PEARLESCENT_FROGLIGHT : Material.MAGENTA_CONCRETE;
        } else if (Math.floorMod(along, 6) == 0) {
            m = Material.CYAN_CONCRETE;
        } else {
            m = Material.BLACK_CONCRETE;
        }
        set(w, x, y, z, m);
        return 1;
    }

    /** Grand écran d'ORBIT RUSH au mur nord, bureau de commande devant. */
    private static int rushScreen(World w) {
        int n = 0;
        for (int x = -4; x <= 4; x++) {
            for (int y = Y + 1; y <= Y + 6; y++) {
                boolean frame = x == -4 || x == 4 || y == Y + 1 || y == Y + 6;
                set(w, x, y, NORTH, frame ? Material.PEARLESCENT_FROGLIGHT : Material.SEA_LANTERN);
                if (!frame) {
                    // piste stylisée : 3 voies sur l'écran
                    Material glass = x == -2 || x == 2 ? Material.MAGENTA_STAINED_GLASS
                            : (y == Y + 3 && x == 0) ? Material.YELLOW_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS;
                    set(w, x, y, NORTH + 1, glass);
                }
                n += 2;
            }
        }
        for (int x = -3; x <= 3; x++) {
            set(w, x, Y, NORTH + 2, Material.POLISHED_BLACKSTONE);
            n++;
        }
        set(w, -2, Y + 1, NORTH + 2, "minecraft:red_carpet");
        set(w, 0, Y + 1, NORTH + 2, "minecraft:lime_carpet");
        set(w, 2, Y + 1, NORTH + 2, "minecraft:yellow_carpet");
        return n + 3;
    }

    /** Écran mural d'un côté (side -1 = ouest Vole mon Orbito, 1 = est Casino). */
    private static int sideScreen(World w, int side, String color, Material glass) {
        int n = 0;
        int wx = side * HX, fx = side * (HX - 1);
        for (int z = CZ - 3; z <= CZ + 3; z++) {
            for (int y = Y + 1; y <= Y + 5; y++) {
                boolean frame = z == CZ - 3 || z == CZ + 3 || y == Y + 1 || y == Y + 5;
                set(w, wx, y, z, frame ? (side < 0 ? Material.OCHRE_FROGLIGHT : Material.GOLD_BLOCK) : Material.SEA_LANTERN);
                if (!frame) {
                    set(w, fx, y, z, glass);
                }
                n += 2;
            }
        }
        for (int z = CZ - 2; z <= CZ + 2; z++) {
            set(w, fx, Y, z, mat(color, "concrete"));
            n++;
        }
        return n;
    }

    /** Borne d'arcade : arrière lumineux + écran coloré, fronton, petit pupitre. dir = +1 face au sud, -1 face au nord. */
    private static int cabinet(World w, int x, int zBack, int dir, String color) {
        int zFront = zBack + dir;
        set(w, x, Y, zBack, Material.BLACK_CONCRETE);
        set(w, x, Y + 1, zBack, Material.SEA_LANTERN);
        set(w, x, Y + 2, zBack, Material.BLACK_CONCRETE);
        set(w, x, Y + 3, zBack, mat(color, "concrete"));
        set(w, x, Y, zFront, mat(color, "concrete"));
        set(w, x, Y + 1, zFront, mat(color, "stained_glass"));
        set(w, x, Y + 2, zFront, "minecraft:polished_blackstone_slab[type=top]");
        set(w, x, Y, zFront + dir, "minecraft:polished_blackstone_button[face=wall,facing=" + (dir > 0 ? "south" : "north") + "]");
        return 8;
    }
}
