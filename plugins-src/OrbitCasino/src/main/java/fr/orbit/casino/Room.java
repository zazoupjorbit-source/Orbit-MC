package fr.orbit.casino;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;

/**
 * Plan de la salle du casino (monde vide « casino ») et sa construction par le code.
 * <pre>
 *                         mur nord (z = -21)
 *    [13 machines à sous, x = -24 … 24, z = -20]      JACKPOT au-dessus
 *
 *  BLACKJACK 1 (-20,-9)          ROULETTE (0,-1)         POKER Nébuleuse (20,-7)
 *  BLACKJACK 2 (-20, 0)
 *  BLACKJACK 3 (-20, 9)                                  POKER Supernova (20, 6)
 *
 *  BAR (sud-ouest)            ENTRÉE / SORTIE (0,20)     CAISSE · BOUTIQUE (sud-est)
 * </pre>
 * Sol à y = 63 (on marche à y = 64), plafond à y = 76, murs à x = ±31 et z = ±21.
 */
public final class Room {

    public static final int FLOOR = 63, Y = 64, CEIL = 76, HX = 31, HZ = 21;

    public static final int[] SLOT_X = {-24, -20, -16, -12, -8, -4, 0, 4, 8, 12, 16, 20, 24};
    public static final int SLOT_Z = -20;

    public static final int BJ_X = -20;
    public static final int[] BJ_Z = {-9, 0, 9};

    public static final int POKER_X = 20;
    public static final int[] POKER_Z = {-7, 6};

    /** Table de roulette : x -3..3, z -3..1. */
    public static final int RL_X1 = -3, RL_X2 = 3, RL_Z1 = -3, RL_Z2 = 1;

    public static final int EXIT_X = 0, EXIT_Y = 65, EXIT_Z = 20;

    private Room() {
    }

    public static Location spawn(World w) {
        return new Location(w, 0.5, Y, 17.5, 180f, 0f);
    }

    public static Location cashier(World w) {
        return new Location(w, 25.5, Y, 18.5, 180f, 0f);
    }

    /** Hôtesse des règles, juste à droite de l'arrivée, tournée vers le joueur qui entre. */
    public static Location guide(World w) {
        return new Location(w, 3.5, Y, 15.5, 90f, 0f);
    }

    public static Location barman(World w) {
        return new Location(w, -25.5, Y, 18.5, 180f, 0f);
    }

    private static BlockData data(String s) {
        try {
            return Bukkit.createBlockData(s);
        } catch (IllegalArgumentException e) {
            // noms qui ont changé selon les versions (chaîne : iron_chain / chain)
            return Bukkit.createBlockData(s.replace("iron_chain", "chain"));
        }
    }

    private static void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    private static void set(World w, int x, int y, int z, BlockData d) {
        w.getBlockAt(x, y, z).setBlockData(d, false);
    }

    /** Construit (ou reconstruit) toute la salle. Retourne le nombre de blocs posés. */
    public static int build(World w) {
        int count = 0;
        // vide l'intérieur
        for (int x = -HX - 1; x <= HX + 1; x++) {
            for (int z = -HZ - 1; z <= HZ + 1; z++) {
                for (int y = FLOOR - 1; y <= CEIL + 1; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (!b.getType().isAir()) {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }
        // sol : moquette rouge à losanges noirs, croisements dorés ; sous-couche pleine
        for (int x = -HX; x <= HX; x++) {
            for (int z = -HZ; z <= HZ; z++) {
                set(w, x, FLOOR - 1, z, Material.BLACKSTONE);
                boolean a = Math.floorMod(x + z, 8) == 0, b = Math.floorMod(x - z, 8) == 0;
                Material m = a && b ? Material.YELLOW_WOOL : a || b ? Material.BLACK_WOOL : Material.RED_WOOL;
                if (Math.abs(x) == HX || Math.abs(z) == HZ) {
                    m = Material.POLISHED_BLACKSTONE;
                }
                set(w, x, FLOOR, z, m);
                // plafond noir, plafonniers dorés tous les 6 blocs
                boolean light = Math.floorMod(x, 6) == 3 && Math.floorMod(z, 6) == 3;
                set(w, x, CEIL, z, light ? Material.OCHRE_FROGLIGHT : Material.BLACK_CONCRETE);
                count += 3;
            }
        }
        // murs : briques de blackstone, plinthe, frise dorée, appliques lumineuses
        for (int y = Y; y < CEIL; y++) {
            for (int x = -HX; x <= HX; x++) {
                count += wall(w, x, y, -HZ, x) + wall(w, x, y, HZ, x);
            }
            for (int z = -HZ + 1; z < HZ; z++) {
                count += wall(w, -HX, y, z, z) + wall(w, HX, y, z, z);
            }
        }
        // lustres : chaînes + lanternes
        for (int[] c : new int[][]{{-10, -5}, {10, -5}, {-10, 8}, {10, 8}, {0, 10}, {0, -12}}) {
            for (int y = CEIL - 1; y >= CEIL - 3; y--) {
                set(w, c[0], y, c[1], data("minecraft:iron_chain[axis=y]"));
            }
            set(w, c[0], CEIL - 4, c[1], data("minecraft:lantern[hanging=true]"));
            count += 4;
        }
        count += slots(w) + roulette(w) + bars(w);
        for (int z : BJ_Z) {
            count += blackjack(w, BJ_X, z);
        }
        for (int z : POKER_Z) {
            count += poker(w, POKER_X, z);
        }
        // sortie : arche dorée et bouton
        for (int y = Y; y <= Y + 3; y++) {
            set(w, EXIT_X - 2, y, HZ, Material.GOLD_BLOCK);
            set(w, EXIT_X + 2, y, HZ, Material.GOLD_BLOCK);
        }
        for (int x = EXIT_X - 2; x <= EXIT_X + 2; x++) {
            set(w, x, Y + 4, HZ, Material.GOLD_BLOCK);
        }
        for (int x = EXIT_X - 1; x <= EXIT_X + 1; x++) {
            for (int y = Y; y <= Y + 3; y++) {
                set(w, x, y, HZ, Material.BLACK_STAINED_GLASS);
            }
        }
        set(w, EXIT_X, EXIT_Y, HZ, Material.GOLD_BLOCK);
        set(w, EXIT_X, EXIT_Y, EXIT_Z, data("minecraft:polished_blackstone_button[face=wall,facing=north]"));
        return count + 20;
    }

    private static int wall(World w, int x, int y, int z, int along) {
        Material m;
        boolean pillar = Math.floorMod(along, 8) == 0;
        if (y == Y) {
            m = Material.POLISHED_BLACKSTONE;
        } else if (y == CEIL - 4) {
            m = Material.GOLD_BLOCK;
        } else if (pillar) {
            m = y == Y + 3 ? Material.OCHRE_FROGLIGHT : Material.CHISELED_POLISHED_BLACKSTONE;
        } else {
            m = Material.POLISHED_BLACKSTONE_BRICKS;
        }
        set(w, x, y, z, m);
        return 1;
    }

    private static int slots(World w) {
        int n = 0;
        for (int x : SLOT_X) {
            set(w, x, Y, SLOT_Z, Material.GOLD_BLOCK);
            set(w, x, Y + 1, SLOT_Z, Material.BLACK_CONCRETE);
            set(w, x, Y + 2, SLOT_Z, Material.BLACK_CONCRETE);
            set(w, x, Y + 3, SLOT_Z, Material.SEA_LANTERN);
            set(w, x, Y + 4, SLOT_Z, data("minecraft:red_stained_glass"));
            set(w, x + 1, Y + 1, SLOT_Z, data("minecraft:lever[face=wall,facing=east]"));
            n += 6;
        }
        return n;
    }

    /** Liste des blocs « machine » de la machine i (clic droit = jouer). */
    public static List<Block> slotBlocks(World w, int i) {
        int x = SLOT_X[i];
        List<Block> list = new ArrayList<>();
        for (int y = Y; y <= Y + 4; y++) {
            list.add(w.getBlockAt(x, y, SLOT_Z));
        }
        list.add(w.getBlockAt(x + 1, Y + 1, SLOT_Z));
        return list;
    }

    private static int table(World w, int x1, int z1, int x2, int z2) {
        int n = 0;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean rim = x == x1 || x == x2 || z == z1 || z == z2;
                set(w, x, Y, z, rim ? Material.STRIPPED_DARK_OAK_WOOD : Material.GREEN_WOOL);
                n++;
            }
        }
        return n;
    }

    private static int roulette(World w) {
        int n = table(w, RL_X1, RL_Z1, RL_X2, RL_Z2);
        // la roue au centre
        set(w, 0, Y, -1, Material.CHISELED_QUARTZ_BLOCK);
        set(w, -1, Y, -1, Material.RED_WOOL);
        set(w, 1, Y, -1, Material.BLACK_WOOL);
        set(w, 0, Y, -2, Material.BLACK_WOOL);
        set(w, 0, Y, 0, Material.RED_WOOL);
        return n + 5;
    }

    private static int blackjack(World w, int cx, int cz) {
        // demi-lune : 5 de large, 2 de profondeur ; le croupier au nord, les joueurs au sud
        int n = 0;
        for (int x = cx - 2; x <= cx + 2; x++) {
            set(w, x, Y, cz, Material.STRIPPED_DARK_OAK_WOOD);
            set(w, x, Y, cz + 1, Material.GREEN_WOOL);
            n += 2;
        }
        set(w, cx - 2, Y, cz + 1, Material.STRIPPED_DARK_OAK_WOOD);
        set(w, cx + 2, Y, cz + 1, Material.STRIPPED_DARK_OAK_WOOD);
        return n;
    }

    /** Blocs de la table de blackjack (clic droit = s'asseoir). */
    public static boolean isBlackjack(Block b, int cz) {
        return b.getY() == Y && b.getX() >= BJ_X - 2 && b.getX() <= BJ_X + 2 && (b.getZ() == cz || b.getZ() == cz + 1);
    }

    private static int poker(World w, int cx, int cz) {
        int n = table(w, cx - 3, cz - 1, cx + 3, cz + 1);
        // tabourets
        for (int dx = -2; dx <= 2; dx += 2) {
            set(w, cx + dx, Y - 1, cz - 2, Material.GOLD_BLOCK);
            set(w, cx + dx, Y - 1, cz + 2, Material.GOLD_BLOCK);
        }
        return n + 6;
    }

    public static boolean isPoker(Block b, int cz) {
        return b.getY() == Y && b.getX() >= POKER_X - 3 && b.getX() <= POKER_X + 3 && b.getZ() >= cz - 1 && b.getZ() <= cz + 1;
    }

    public static boolean isRoulette(Block b) {
        return b.getY() == Y && b.getX() >= RL_X1 && b.getX() <= RL_X2 && b.getZ() >= RL_Z1 && b.getZ() <= RL_Z2;
    }

    private static int bars(World w) {
        int n = 0;
        // bar (sud-ouest) et caisse (sud-est) : comptoir, étagères derrière
        for (int side : new int[]{-1, 1}) {
            for (int i = 22; i <= 28; i++) {
                int x = side * i;
                set(w, x, Y, 16, i == 22 || i == 28 ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE);
                set(w, x, Y + 1, HZ - 1, side < 0 ? Material.BARREL : Material.BOOKSHELF);
                set(w, x, Y + 2, HZ - 1, data(side < 0 ? "minecraft:dark_oak_slab[type=bottom]" : "minecraft:chiseled_bookshelf"));
                n += 3;
            }
            set(w, side * 22, Y + 1, 16, data(side < 0 ? "minecraft:cyan_candle[candles=3,lit=true]" : "minecraft:candle[candles=2,lit=true]"));
            set(w, side * 28, Y + 1, 16, data("minecraft:candle[candles=1,lit=true]"));
        }
        // salon : fauteuils autour d'une table basse, côté bar
        set(w, -14, Y, 15, Material.DARK_OAK_PLANKS);
        set(w, -14, Y + 1, 15, data("minecraft:potted_red_tulip"));
        set(w, -15, Y, 15, data("minecraft:red_wool"));
        set(w, -13, Y, 15, data("minecraft:red_wool"));
        // plantes près des piliers
        for (int x : new int[]{-29, 29}) {
            for (int z : new int[]{-14, 14}) {
                set(w, x, Y, z, data("minecraft:potted_bamboo"));
            }
        }
        return n + 10;
    }
}
