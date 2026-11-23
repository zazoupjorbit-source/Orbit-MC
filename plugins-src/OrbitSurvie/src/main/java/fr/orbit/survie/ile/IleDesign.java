package fr.orbit.survie.ile;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Le dessin de l'île du spawn, centrée sur la fontaine (0, 0). Sol des places à {@link #G}.
 * <pre>
 *        Maison   Portail vers la survie (0, -38)   Ferme (17, -40)   Maison
 *                    Jardinière · Place · Jardinière · Pavillon des kits (19, -19)
 *   Tour des enchantements (-34, 0)  ·  Place + fontaine (0, 0)  ·  Marché (26..44, -8..8)
 *   Maison   Hôtel des ventes (-28, 28)   Étang (-10, 32)   Arène de duel (26, 26)
 *                    Port, bateau et phare (x = 0, au bout du ponton)   Maison
 * </pre>
 * Collines entre les quartiers, plage avec parasols et rochers, fond marin avec coraux.
 * Tout est calculé : la même version du code donne toujours exactement la même île.
 */
public final class IleDesign {

    /** Hauteur du sol (bloc) des places. On marche à G + 1. */
    public static final int G = 66;
    /** Demi-côté du carré couvert par le plan. */
    public static final int REACH = 112;
    public static final int MIN_Y = 40;
    public static final int MAX_Y = 100;

    /** Centre et rayon de l'arène de duel (zone PvP). */
    public static final int ARENE_X = 26, ARENE_Z = 26;
    public static final double ARENE_R = 10.5;

    /** Maisons : x, z, porte au sud (1) ou au nord (-1). */
    private static final int[][] MAISONS = {{-18, -24, 1}, {-40, -20, 1}, {40, -24, 1}, {-46, 14, -1}, {10, 44, -1}};

    /** Quartiers (x, z, rayon) : le sol y reste plat, pas de colline. */
    private static final int[][] ZONES = {
            {0, 0, 16}, {0, -38, 11}, {35, 0, 17}, {-34, 0, 11}, {-28, 28, 14}, {26, 26, 18},
            {19, -19, 9}, {17, -40, 10}, {-10, 32, 8}, {11, -11, 5}, {-11, -11, 5},
            {-18, -24, 8}, {-40, -20, 8}, {40, -24, 8}, {-46, 14, 8}, {10, 44, 8}};

    /** Chemins (x1, z1, x2, z2) : plats eux aussi, avec des lampadaires. */
    private static final int[][] CHEMINS = {
            {0, -11, 0, -31}, {11, 0, 26, 0}, {-11, 0, -27, 0}, {-8, 8, -28, 21}, {8, 8, 12, 26},
            {8, -8, 15, -15}, {3, -33, 16, -34}};

    private IleDesign() {}

    // ------------------------------------------------------------------ Relief

    /** Rayon du rivage selon l'angle : une île ronde mais irrégulière (62 à 82 blocs). */
    static double rivage(double angle) {
        return 72 + 5 * Math.sin(3 * angle + 0.7) + 3 * Math.sin(5 * angle + 2.1) + 2 * Math.sin(9 * angle + 4.0);
    }

    static double rivage(int x, int z) {
        return rivage(Math.atan2(z, x));
    }

    private static double distSegment(double px, double pz, int[] s) {
        double vx = s[2] - s[0], vz = s[3] - s[1];
        double t = ((px - s[0]) * vx + (pz - s[1]) * vz) / (vx * vx + vz * vz);
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(px - (s[0] + t * vx), pz - (s[1] + t * vz));
    }

    /** Hauteur des collines (0 près des quartiers, des chemins et du rivage). */
    static int colline(int x, int z, double d, double r) {
        double libre = Double.MAX_VALUE;
        for (int[] zo : ZONES) {
            libre = Math.min(libre, Math.hypot(x - zo[0], z - zo[1]) - zo[2]);
        }
        for (int[] ch : CHEMINS) {
            libre = Math.min(libre, distSegment(x, z, ch) - 4);
        }
        if (z > 8) {
            libre = Math.min(libre, Math.abs(x) - 4); // chemin du port
        }
        if (libre <= 0) {
            return 0;
        }
        double hf = Math.min(1, libre / 9.0);
        hf = hf * hf * (3 - 2 * hf);
        double sf = Math.max(0, Math.min(1, (r - 13 - d) / 10.0));
        double n = (Math.sin(x * 0.11 + 1.3) + Math.sin(z * 0.093 + 0.4) + Math.sin((x + z) * 0.071 + 2.2)
                + Math.sin((x - z) * 0.13 + 0.9)) / 4.0;
        return (int) Math.round(Math.max(0, n + 0.15) * 8 * hf * sf);
    }

    /** Hauteur du sol : plateau à G (+ collines), plage qui descend à 63, puis fond marin. */
    public static int sol(int x, int z) {
        double d = Math.hypot(x, z);
        double r = rivage(x, z);
        if (d <= r - 12) {
            return G + colline(x, z, d, r);
        }
        if (d <= r) {
            return G - (int) Math.round(3 * (d - (r - 12)) / 12.0);
        }
        return Math.max(OceanGenerator.SAND_TOP, OceanGenerator.SEA - (int) Math.round((d - r) * 1.5));
    }

    static boolean plage(int x, int z) {
        return Math.hypot(x, z) > rivage(x, z) - 6;
    }

    /** Petit hasard fixe par position (0..99). */
    static int hasard(int x, int z, int sel) {
        long h = x * 73428767L ^ z * 91227611L ^ sel * 19349663L;
        h ^= (h >>> 13);
        h *= 0x5DEECE66DL;
        h ^= (h >>> 17);
        return (int) Math.floorMod(h, 100L);
    }

    // ------------------------------------------------------------------ Dessin complet

    public static Canvas dessiner() {
        Canvas c = new Canvas();
        terrain(c);
        place(c);
        chemins(c);
        portail(c);
        marche(c);
        tour(c);
        hdv(c);
        arene(c);
        port(c);
        kits(c);
        for (int[] m : MAISONS) {
            maison(c, m[0], m[1], m[2]);
        }
        ferme(c);
        etang(c);
        plage(c);
        lampadairesChemins(c);
        nature(c);
        return c;
    }

    private static void terrain(Canvas c) {
        for (int x = -REACH; x <= REACH; x++) {
            for (int z = -REACH; z <= REACH; z++) {
                double d = Math.hypot(x, z);
                if (d > rivage(x, z) + 16) {
                    continue;
                }
                int s = sol(x, z);
                boolean sable = s < 63 || plage(x, z);
                boolean rocher = s >= G + 3 && hasard(x, z, 9) < 14;
                for (int y = MIN_Y; y <= s; y++) {
                    int prof = s - y;
                    Material m;
                    if (sable) {
                        m = prof < 2 ? Material.SAND : prof < 4 ? Material.SANDSTONE : Material.STONE;
                    } else if (rocher && prof == 0) {
                        m = hasard(x, z, 10) < 50 ? Material.MOSSY_COBBLESTONE : Material.ANDESITE;
                    } else {
                        m = prof == 0 ? Material.GRASS_BLOCK : prof < 4 ? Material.DIRT : Material.STONE;
                    }
                    c.terrain(x, y, z, m);
                }
            }
        }
    }

    /** Sol d'une construction à G : comble le creux s'il est sur la pente de la plage. */
    private static void fondation(Canvas c, int x, int z, Material m) {
        for (int y = Math.min(sol(x, z) + 1, G); y < G; y++) {
            c.set(x, y, z, Material.DIRT);
        }
        c.set(x, G, z, m);
    }

    // ------------------------------------------------------------------ Place centrale

    private static void place(Canvas c) {
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                double d = Math.hypot(x, z);
                if (d > 11.5) {
                    continue;
                }
                Material m;
                if (d <= 3.5) {
                    m = Material.PRISMARINE_BRICKS;
                } else if (d > 10.3) {
                    m = Material.POLISHED_DEEPSLATE;
                } else if ((x == 0 || z == 0 || Math.abs(x) == Math.abs(z)) && d > 6 && d < 8) {
                    m = Material.GOLD_BLOCK;
                } else {
                    m = ((int) (d / 2)) % 2 == 0 ? Material.SMOOTH_STONE : Material.POLISHED_ANDESITE;
                }
                c.set(x, G, z, m);
                if (d > 2.5 && d <= 3.5) {
                    c.set(x, G + 1, z, Material.SMOOTH_QUARTZ);
                } else if (d <= 2.5 && !(x == 0 && z == 0)) {
                    c.set(x, G + 1, z, Material.WATER);
                }
            }
        }
        // Fontaine : colonne, globe lumineux, quatre petites vasques en or.
        c.fill(0, G + 1, 0, 0, G + 4, 0, Material.QUARTZ_PILLAR);
        c.set(0, G + 5, 0, Material.SEA_LANTERN);
        c.set(0, G + 6, 0, Material.GOLD_BLOCK);
        c.set(0, G + 7, 0, Material.END_ROD);
        for (int[] p : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            c.set(p[0], G + 3, p[1], Material.SMOOTH_QUARTZ_SLAB);
        }
        for (int[] p : new int[][]{{7, 7}, {-7, 7}, {7, -7}, {-7, -7}}) {
            lampadaire(c, p[0], p[1]);
        }
        // Bancs face à la fontaine, entre les chemins.
        for (int k : new int[]{3, 4, -3, -4}) {
            c.set(9, G + 1, k, "minecraft:spruce_stairs[facing=east]");
            c.set(-9, G + 1, k, "minecraft:spruce_stairs[facing=west]");
            c.set(k, G + 1, -9, "minecraft:spruce_stairs[facing=north]");
            c.set(k, G + 1, 9, "minecraft:spruce_stairs[facing=south]");
        }
        // Jardinières au nord-est et au nord-ouest.
        jardiniere(c, 11, -11);
        jardiniere(c, -11, -11);
        c.point("spawn", 0.5, G + 1, 6.5, 180f);
        c.pnj("guide", "<#4DA3FF><bold>Guide</bold>", "cartographer", -2.5, G + 1, 7.5, 180f);
        c.panneau("bienvenue", "<#F5C542><bold>ORBIT SURVIE</bold><newline><gray>Bienvenue sur l'île !", 0.5, G + 9, 0.5);
        c.panneau("regles", "<#F5C542><bold>RÈGLES</bold><newline><gray>Pas de triche ni de grief<newline>"
                + "<gray>PvP seulement dans l'arène<newline><gray>Respect de tous les joueurs", -6.5, G + 3.5, 3.5);
        c.panneau("commandes", "<#4DA3FF><bold>COMMANDES</bold><newline><white>/spawn <gray>retour sur l'île<newline>"
                + "<white>/kit <gray>tes kits · <white>/hdv <gray>ventes<newline><white>/argent /payer /richesses", 7.5, G + 3.5, 3.5);
        c.particule("SPLASH", 0.5, G + 2.2, 0.5, 12, 1.6, 0.1, 1.6, 0.05);
        c.particule("END_ROD", 0.5, G + 8.5, 0.5, 2, 0.3, 0.6, 0.3, 0.01);
    }

    private static void lampadaire(Canvas c, int x, int z) {
        lampadaire(c, x, G, z);
    }

    private static void lampadaire(Canvas c, int x, int sol, int z) {
        c.fill(x, sol + 1, z, x, sol + 3, z, Material.SPRUCE_FENCE);
        c.set(x, sol + 4, z, Material.LANTERN);
    }

    private static void jardiniere(Canvas c, int ox, int oz) {
        for (int x = ox - 3; x <= ox + 3; x++) {
            for (int z = oz - 3; z <= oz + 3; z++) {
                double d = Math.hypot(x - ox, z - oz);
                if (d > 3.2) {
                    continue;
                }
                if (d > 2.2) {
                    c.set(x, G, z, Material.POLISHED_ANDESITE);
                    c.set(x, G + 1, z, "minecraft:azalea_leaves[persistent=true]");
                } else {
                    c.set(x, G, z, Material.GRASS_BLOCK);
                    String[] f = {"minecraft:cornflower", "minecraft:blue_orchid", "minecraft:allium", "minecraft:lily_of_the_valley", "minecraft:dandelion"};
                    c.set(x, G + 1, z, f[hasard(x, z, 21) % f.length]);
                }
            }
        }
        c.set(ox, G + 1, oz, "minecraft:flowering_azalea");
    }

    // ------------------------------------------------------------------ Chemins

    private static void chemins(Canvas c) {
        for (int[] ch : CHEMINS) {
            chemin(c, ch[0], ch[1], ch[2], ch[3]);
        }
        // Port : le chemin suit la pente de la plage jusqu'au ponton, bordé de lanternes.
        for (int z = 11; sol(0, z) >= 63; z++) {
            for (int x = -1; x <= 1; x++) {
                int s = sol(x, z);
                c.set(x, s, z, s == G ? sentier(x, z) : Material.SMOOTH_SANDSTONE);
            }
        }
    }

    private static Material sentier(int x, int z) {
        int h = hasard(x, z, 3);
        return h < 70 ? Material.DIRT_PATH : h < 88 ? Material.COARSE_DIRT : Material.GRAVEL;
    }

    private static void chemin(Canvas c, int x1, int z1, int x2, int z2) {
        double len = Math.hypot(x2 - x1, z2 - z1);
        for (double t = 0; t <= len; t += 0.5) {
            int cx = (int) Math.round(x1 + (x2 - x1) * t / len);
            int cz = (int) Math.round(z1 + (z2 - z1) * t / len);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = cx + dx, z = cz + dz;
                    if (Math.hypot(x, z) <= 11.5) {
                        continue;
                    }
                    c.set(x, G, z, sentier(x, z));
                }
            }
        }
    }

    /** Un lampadaire tous les 9 blocs le long des chemins, d'un côté puis de l'autre. */
    private static void lampadairesChemins(Canvas c) {
        List<int[]> tous = new ArrayList<>(List.of(CHEMINS));
        int finPort = 11;
        while (sol(0, finPort) >= 63) {
            finPort++;
        }
        tous.add(new int[]{0, 12, 0, finPort - 4});
        for (int[] ch : tous) {
            double len = Math.hypot(ch[2] - ch[0], ch[3] - ch[1]);
            double nx = -(ch[3] - ch[1]) / len, nz = (ch[2] - ch[0]) / len;
            int k = 0;
            for (double t = 4; t <= len - 2; t += 9, k++) {
                double side = (k % 2 == 0) ? 2.6 : -2.6;
                int x = (int) Math.round(ch[0] + (ch[2] - ch[0]) * t / len + nx * side);
                int z = (int) Math.round(ch[1] + (ch[3] - ch[1]) * t / len + nz * side);
                int s = sol(x, z);
                Material dessous = c.type(x, s, z);
                if (c.reserved(x, z) || (dessous != Material.GRASS_BLOCK && dessous != Material.SAND)
                        || c.type(x, s + 1, z) != Material.AIR || Math.hypot(x, z) < 13) {
                    continue;
                }
                lampadaire(c, x, s, z);
            }
        }
    }

    // ------------------------------------------------------------------ Portail vers la survie (nord)

    private static void portail(Canvas c) {
        int oz = -38;
        for (int x = -7; x <= 7; x++) {
            for (int z = oz - 7; z <= oz + 7; z++) {
                double d = Math.hypot(x, z - oz);
                if (d > 7.2) {
                    continue;
                }
                c.set(x, G, z, d > 6 ? Material.LIGHT_BLUE_CONCRETE : (d < 1.5 ? Material.GOLD_BLOCK
                        : ((int) d) % 2 == 0 ? Material.SMOOTH_QUARTZ : Material.QUARTZ_BRICKS));
            }
        }
        int pz = oz - 3;
        for (int x = -3; x <= 3; x++) {
            c.set(x, G, pz, Material.OBSIDIAN);
            c.set(x, G + 6, pz, Material.OBSIDIAN);
        }
        for (int y = G; y <= G + 6; y++) {
            c.set(-3, y, pz, Material.OBSIDIAN);
            c.set(3, y, pz, Material.OBSIDIAN);
        }
        for (int x = -2; x <= 2; x++) {
            for (int y = G + 1; y <= G + 5; y++) {
                c.set(x, y, pz, "minecraft:nether_portal[axis=x]");
            }
        }
        for (int y = G + 1; y <= G + 7; y++) {
            c.set(-4, y, pz, Material.QUARTZ_PILLAR);
            c.set(4, y, pz, Material.QUARTZ_PILLAR);
        }
        for (int x = -4; x <= 4; x++) {
            c.set(x, G + 7, pz, Material.SMOOTH_QUARTZ);
        }
        c.set(-4, G + 8, pz, Material.GOLD_BLOCK);
        c.set(4, G + 8, pz, Material.GOLD_BLOCK);
        c.set(0, G + 8, pz, Material.SEA_LANTERN);
        c.set(-4, G + 9, pz, Material.SEA_LANTERN);
        c.set(4, G + 9, pz, Material.SEA_LANTERN);
        c.set(0, G + 9, pz, Material.END_ROD);
        // Quatre obélisques autour de l'esplanade.
        for (int[] p : new int[][]{{-6, oz - 3}, {6, oz - 3}, {-5, oz + 4}, {5, oz + 4}}) {
            c.set(p[0], G + 1, p[1], Material.CHISELED_QUARTZ_BLOCK);
            c.set(p[0], G + 2, p[1], Material.QUARTZ_PILLAR);
            c.set(p[0], G + 3, p[1], Material.QUARTZ_PILLAR);
            c.set(p[0], G + 4, p[1], Material.SEA_LANTERN);
            c.set(p[0], G + 5, p[1], "minecraft:smooth_quartz_slab");
        }
        c.point("portail", 0.5, G + 1, oz + 4.5, 180f);
        c.panneau("portail", "<#F5C542><bold>✦ SURVIE ✦</bold><newline><gray>Traverse le portail :<newline><gray>téléportation aléatoire", 0.5, G + 11, pz + 0.5);
        c.particule("PORTAL", 0.5, G + 3.5, pz + 0.5, 30, 2.2, 2.2, 0.4, 0.4);
        c.particule("END_ROD", 0.5, G + 4, pz + 1.5, 3, 3.5, 3, 1.5, 0.01);
    }

    // ------------------------------------------------------------------ Marché (est)

    private static void marche(Canvas c) {
        for (int x = 26; x <= 44; x++) {
            for (int z = -8; z <= 8; z++) {
                int h = hasard(x, z, 7);
                fondation(c, x, z, h < 45 ? Material.STONE_BRICKS : h < 75 ? Material.COBBLESTONE
                        : h < 90 ? Material.ANDESITE : Material.MOSSY_STONE_BRICKS);
            }
        }
        for (int y = G + 1; y <= G + 5; y++) {
            c.set(26, y, -4, Material.SPRUCE_LOG);
            c.set(26, y, 4, Material.SPRUCE_LOG);
        }
        for (int z = -4; z <= 4; z++) {
            c.set(26, G + 6, z, "minecraft:spruce_log[axis=z]");
        }
        c.set(26, G + 5, -3, "minecraft:lantern[hanging=true]");
        c.set(26, G + 5, 3, "minecraft:lantern[hanging=true]");
        // Guirlande de fanions sur l'arche.
        for (int z = -3; z <= 3; z++) {
            String[] laine = {"minecraft:blue_carpet", "minecraft:yellow_carpet", "minecraft:white_carpet"};
            c.set(26, G + 7, z, laine[Math.floorMod(z, 3)]);
        }
        stand(c, 30, -5, 1, Material.BLUE_WOOL, Material.WHITE_WOOL, Material.COAL_BLOCK, Material.IRON_ORE);
        stand(c, 38, -5, 1, Material.YELLOW_WOOL, Material.WHITE_WOOL, Material.OAK_LOG, Material.BIRCH_LOG);
        stand(c, 30, 5, -1, Material.LIME_WOOL, Material.WHITE_WOOL, Material.HAY_BLOCK, Material.PUMPKIN);
        stand(c, 38, 5, -1, Material.RED_WOOL, Material.WHITE_WOOL, Material.BONE_BLOCK, Material.SKELETON_SKULL);
        // Puits au bout de l'allée.
        for (int x = 41; x <= 43; x++) {
            for (int z = -1; z <= 1; z++) {
                boolean centre = x == 42 && z == 0;
                c.set(x, G, z, centre ? Material.WATER : Material.STONE_BRICKS);
                c.set(x, G - 1, z, centre ? Material.WATER : Material.STONE_BRICKS);
                if (!centre) {
                    c.set(x, G + 1, z, Material.STONE_BRICK_WALL);
                }
            }
        }
        for (int[] p : new int[][]{{41, -1}, {43, -1}, {41, 1}, {43, 1}}) {
            c.set(p[0], G + 2, p[1], Material.SPRUCE_FENCE);
            c.set(p[0], G + 3, p[1], Material.SPRUCE_FENCE);
        }
        for (int x = 41; x <= 43; x++) {
            for (int z = -1; z <= 1; z++) {
                c.set(x, G + 4, z, "minecraft:spruce_slab");
            }
        }
        c.set(42, G + 3, 0, "minecraft:lantern[hanging=true]");
        lampadaire(c, 44, -7);
        lampadaire(c, 44, 7);
        // Caisses et tonneaux.
        c.set(44, G + 1, -3, Material.BARREL);
        c.set(44, G + 1, -4, Material.HAY_BLOCK);
        c.set(44, G + 2, -4, Material.BARREL);
        c.set(44, G + 1, 3, Material.MELON);
        c.set(44, G + 1, 4, Material.PUMPKIN);
        c.set(27, G + 1, -7, Material.COMPOSTER);
        c.set(27, G + 1, 7, Material.BARREL);
        c.pnj("mineur", "<#4DA3FF><bold>Mineur</bold>", "toolsmith", 30.5, G + 1, -4.5, 0f);
        c.pnj("bucheron", "<#4DA3FF><bold>Bûcheron</bold>", "mason", 38.5, G + 1, -4.5, 0f);
        c.pnj("fermier", "<#4DA3FF><bold>Fermier</bold>", "farmer", 30.5, G + 1, 5.5, 180f);
        c.pnj("chasseur", "<#4DA3FF><bold>Chasseur</bold>", "leatherworker", 38.5, G + 1, 5.5, 180f);
        c.point("marche", 27.5, G + 1, 0.5, -90f);
        c.panneau("marche", "<#F5C542><bold>MARCHÉ</bold><newline><gray>Achète et vends aux marchands", 26.5, G + 9, 0.5);
    }

    private static void stand(Canvas c, int cx, int cz, int f, Material toitA, Material toitB, Material marchA, Material marchB) {
        int zf = cz + f, zb = cz - f;
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                c.set(x, G, z, Material.SPRUCE_PLANKS);
            }
            c.set(x, G + 1, zf, "minecraft:stripped_spruce_log[axis=x]");
        }
        c.set(cx - 1, G + 2, zf, marchA);
        c.set(cx + 1, G + 2, zf, marchB);
        for (int y = G + 1; y <= G + 3; y++) {
            c.set(cx - 2, y, zb, Material.SPRUCE_FENCE);
            c.set(cx + 2, y, zb, Material.SPRUCE_FENCE);
        }
        for (int y = G + 2; y <= G + 3; y++) {
            c.set(cx - 2, y, zf, Material.SPRUCE_FENCE);
            c.set(cx + 2, y, zf, Material.SPRUCE_FENCE);
        }
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                c.set(x, G + 4, z, Math.floorMod(x - cx, 2) == 0 ? toitA : toitB);
            }
        }
        c.set(cx, G + 3, zf, "minecraft:lantern[hanging=true]");
        c.set(cx - 1, G + 1, zb, Material.BARREL);
        c.set(cx + 1, G + 1, zb, Material.BARREL);
    }

    // ------------------------------------------------------------------ Tour des enchantements (ouest)

    private static void tour(Canvas c) {
        int ox = -34, oz = 0;
        for (int x = ox - 8; x <= ox + 8; x++) {
            for (int z = oz - 8; z <= oz + 8; z++) {
                int dx = x - ox, dz = z - oz;
                double d = Math.hypot(dx, dz);
                if (d > 7.6) {
                    continue;
                }
                if (d <= 6.5) {
                    c.set(x, G, z, d <= 5.5 ? Material.DARK_OAK_PLANKS : Material.DEEPSLATE_BRICKS);
                } else {
                    // Parterre d'améthyste autour de la tour.
                    c.set(x, G, z, hasard(x, z, 13) < 30 ? Material.AMETHYST_BLOCK : Material.MOSS_BLOCK);
                }
                if (d > 5.5 && d <= 6.5) {
                    boolean porte = dx > 0 && Math.abs(dz) <= 1;
                    boolean fenetre = (dx == 0 || (dz == 0 && dx < 0) || Math.abs(dx) == Math.abs(dz)) && !porte;
                    for (int y = G + 1; y <= G + 7; y++) {
                        if (porte && y <= G + 3) {
                            c.set(x, y, z, Material.AIR);
                        } else if (y == G + 7) {
                            c.set(x, y, z, Material.CHISELED_DEEPSLATE);
                        } else if (fenetre && (y == G + 3 || y == G + 4)) {
                            c.set(x, y, z, Material.LIGHT_BLUE_STAINED_GLASS);
                        } else {
                            c.set(x, y, z, Material.DEEPSLATE_BRICKS);
                        }
                    }
                }
                if (d <= 6.5) {
                    c.set(x, G + 8, z, Material.DEEPSLATE_TILES);
                }
                for (int k = 1; k <= 6; k++) {
                    double rk = 7.4 - k * 1.15;
                    if (d <= rk && d > rk - 1.3) {
                        c.set(x, G + 8 + k, z, Material.DARK_PRISMARINE);
                    } else if (d <= rk - 1.3) {
                        c.set(x, G + 8 + k, z, Material.AIR);
                    }
                }
            }
        }
        c.set(ox, G + 14, oz, Material.DARK_PRISMARINE);
        c.set(ox, G + 15, oz, Material.GOLD_BLOCK);
        c.set(ox, G + 16, oz, Material.LIGHTNING_ROD);
        c.set(ox, G + 1, oz, Material.ENCHANTING_TABLE);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 2 || (dx == 2 && Math.abs(dz) <= 1)) {
                    continue;
                }
                c.set(ox + dx, G + 1, oz + dz, Material.BOOKSHELF);
                c.set(ox + dx, G + 2, oz + dz, Material.BOOKSHELF);
                if (hasard(ox + dx, oz + dz, 14) < 40) {
                    c.set(ox + dx, G + 3, oz + dz, "minecraft:amethyst_cluster[facing=up]");
                } else if (hasard(ox + dx, oz + dz, 15) < 40) {
                    c.set(ox + dx, G + 3, oz + dz, "minecraft:candle[candles=3,lit=true]");
                }
            }
        }
        c.set(ox, G + 1, oz - 4, "minecraft:anvil[facing=east]");
        c.set(ox, G + 1, oz + 4, "minecraft:anvil[facing=east]");
        c.set(ox - 3, G + 1, oz - 3, "minecraft:grindstone[face=floor,facing=east]");
        c.set(ox - 3, G + 1, oz + 3, Material.SMITHING_TABLE);
        c.set(ox + 3, G + 1, oz - 4, Material.CRAFTING_TABLE);
        c.set(ox + 3, G + 1, oz + 4, "minecraft:stonecutter[facing=west]");
        c.set(ox - 5, G + 1, oz - 2, "minecraft:lectern[facing=east]");
        // Tapis bleu, sauf autour de la table (les bibliothèques doivent « voir » la table).
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (Math.hypot(dx, dz) > 5.4 || Math.max(Math.abs(dx), Math.abs(dz)) <= 1) {
                    continue;
                }
                if (c.type(ox + dx, G + 1, oz + dz) == Material.AIR) {
                    c.set(ox + dx, G + 1, oz + dz, Math.max(Math.abs(dx), Math.abs(dz)) == 3
                            ? "minecraft:light_blue_carpet" : "minecraft:blue_carpet");
                }
            }
        }
        for (int[] p : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
            c.set(ox + p[0], G + 7, oz + p[1], "minecraft:soul_lantern[hanging=true]");
        }
        c.pnj("enchanteur", "<#4DA3FF><bold>Enchanteur</bold>", "librarian", ox - 4.5, G + 1, oz + 0.5, -90f);
        c.point("enchantement", ox + 8.5, G + 1, oz + 0.5, 90f);
        c.panneau("enchantement", "<#F5C542><bold>ENCHANTEMENTS</bold><newline><gray>Table niveau 30 · enclumes · meule", ox + 7.5, G + 6, oz + 0.5);
        c.particule("ENCHANT", ox + 0.5, G + 2.5, oz + 0.5, 25, 1.8, 0.8, 1.8, 0.6);
        c.particule("END_ROD", ox + 0.5, G + 17, oz + 0.5, 1, 0.2, 0.3, 0.2, 0.01);
    }

    // ------------------------------------------------------------------ Hôtel des ventes (sud-ouest)

    private static void hdv(Canvas c) {
        int x1 = -35, x2 = -21, z1 = 23, z2 = 33;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean bord = x == x1 || x == x2 || z == z1 || z == z2;
                boolean coin = (x == x1 || x == x2) && (z == z1 || z == z2);
                fondation(c, x, z, bord ? Material.SMOOTH_STONE
                        : Math.floorMod(x, 4) == 0 ? Material.LIGHT_BLUE_TERRACOTTA : Material.SMOOTH_QUARTZ);
                for (int y = G + 1; y <= G + 6; y++) {
                    if (!bord) {
                        c.set(x, y, z, Material.AIR);
                        continue;
                    }
                    boolean porte = z == z1 && x >= -29 && x <= -27 && y <= G + 3;
                    int le = (x == x1 || x == x2) ? z : x;
                    boolean fenetre = !coin && Math.floorMod(le, 3) == 0 && (y == G + 2 || y == G + 3)
                            && !(z == z1 && x >= -30 && x <= -26);
                    if (porte) {
                        c.set(x, y, z, Material.AIR);
                    } else if (coin) {
                        c.set(x, y, z, Material.QUARTZ_PILLAR);
                    } else if (fenetre) {
                        c.set(x, y, z, Material.LIGHT_BLUE_STAINED_GLASS);
                    } else if (y == G + 6) {
                        c.set(x, y, z, Material.CUT_SANDSTONE);
                    } else {
                        c.set(x, y, z, Material.QUARTZ_BRICKS);
                    }
                }
                boolean verriere = x >= -31 && x <= -25 && z >= 26 && z <= 30;
                c.set(x, G + 7, z, coin ? Material.GOLD_BLOCK : verriere ? Material.LIGHT_BLUE_STAINED_GLASS : Material.DEEPSLATE_TILES);
            }
        }
        // Fronton doré au-dessus de la porte, marches et bacs à fleurs devant.
        for (int x = -31; x <= -25; x++) {
            c.set(x, G + 8, z1, Math.abs(x + 28) <= 1 ? Material.GOLD_BLOCK : Material.SMOOTH_QUARTZ);
        }
        c.set(-28, G + 9, z1, Material.GOLD_BLOCK);
        for (int x = -29; x <= -27; x++) {
            c.set(x, G + 1, z1 - 1, "minecraft:quartz_stairs[facing=south]");
        }
        for (int x : new int[]{-31, -30, -26, -25}) {
            c.set(x, G + 1, z1 - 1, Material.POLISHED_ANDESITE);
            c.set(x, G + 2, z1 - 1, hasard(x, z1, 17) < 50 ? "minecraft:peony[half=lower]" : "minecraft:rose_bush[half=lower]");
            c.set(x, G + 3, z1 - 1, hasard(x, z1, 17) < 50 ? "minecraft:peony[half=upper]" : "minecraft:rose_bush[half=upper]");
        }
        // Guichet, coffre-fort, coffres de l'Ender, tapis rouge, plantes.
        for (int x = -33; x <= -23; x++) {
            c.set(x, G + 1, 31, x == -33 || x == -23 ? Material.GOLD_BLOCK : Material.DARK_OAK_PLANKS);
        }
        for (int x = -32; x <= -24; x += 2) {
            c.set(x, G + 2, 31, "minecraft:candle[candles=1,lit=true]");
        }
        c.fill(-22, G + 1, 32, -22, G + 2, 32, Material.GOLD_BLOCK);
        c.set(-34, G + 1, 24, "minecraft:ender_chest[facing=east]");
        c.set(-34, G + 1, 25, "minecraft:ender_chest[facing=east]");
        c.set(-22, G + 1, 24, "minecraft:ender_chest[facing=west]");
        for (int z = 24; z <= 30; z++) {
            for (int x = -29; x <= -27; x++) {
                c.set(x, G + 1, z, x == -28 ? "minecraft:red_carpet" : "minecraft:yellow_carpet");
            }
        }
        for (int[] p : new int[][]{{-34, 28}, {-34, 30}, {-22, 28}, {-22, 30}}) {
            c.set(p[0], G + 1, p[1], "minecraft:potted_fern");
        }
        for (int[] p : new int[][]{{-34, 32}, {-33, 32}}) {
            c.set(p[0], G + 1, p[1], Material.BOOKSHELF);
            c.set(p[0], G + 2, p[1], Material.BOOKSHELF);
        }
        for (int[] p : new int[][]{{-32, 25}, {-24, 25}, {-32, 29}, {-24, 29}}) {
            c.set(p[0], G + 6, p[1], "minecraft:lantern[hanging=true]");
        }
        c.pnj("commissaire", "<#F5C542><bold>Hôtel des ventes</bold>", "cartographer", -30.5, G + 1, 32.5, 180f);
        c.pnj("banquier", "<#F5C542><bold>Banquier</bold>", "cleric", -24.5, G + 1, 32.5, 180f);
        c.point("hdv", -27.5, G + 1, 20.5, 0f);
        c.panneau("hdv", "<#F5C542><bold>HÔTEL DES VENTES</bold><newline><gray>Banque · ventes entre joueurs", -27.5, G + 11, 22.5);
    }

    // ------------------------------------------------------------------ Arène de duel (sud-est)

    private static void arene(Canvas c) {
        int ox = ARENE_X, oz = ARENE_Z;
        for (int x = ox - 14; x <= ox + 14; x++) {
            for (int z = oz - 14; z <= oz + 14; z++) {
                int dx = x - ox, dz = z - oz;
                double d = Math.hypot(dx, dz);
                if (d <= ARENE_R) {
                    c.set(x, G - 4, z, Material.STONE);
                    c.set(x, G - 3, z, hasard(x, z, 11) < 80 ? Material.SAND : Material.SMOOTH_SANDSTONE);
                    for (int y = G - 2; y <= G + 1; y++) {
                        c.set(x, y, z, Material.AIR);
                    }
                    // Cercle central et marques de départ.
                    if (Math.abs(d - 3) < 0.5 || (Math.abs(dz) <= 0 && Math.abs(Math.abs(dx) - 8) <= 0)) {
                        c.set(x, G - 3, z, Material.RED_SANDSTONE);
                    }
                } else if (d <= 11.6) {
                    boolean entree = dx < 0 && Math.abs(dz) <= 1;
                    for (int y = G - 3; y <= G + 1; y++) {
                        if (entree && y >= G - 1) {
                            c.set(x, y, z, y == G - 1 ? "minecraft:stone_brick_stairs[facing=west]" : "AIR");
                        } else {
                            c.set(x, y, z, y == G + 1 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
                        }
                    }
                    if (!entree && (dx == 0 || dz == 0)) {
                        c.set(x, G + 1, z, Material.GOLD_BLOCK);
                    }
                    if (!entree && Math.abs(dx) == Math.abs(dz) && Math.abs(dx) == 8) {
                        c.set(x, G + 2, z, Material.LANTERN);
                    }
                } else if (d <= 13.5) {
                    boolean entree = dx < 0 && Math.abs(dz) <= 1;
                    fondation(c, x, z, Material.POLISHED_ANDESITE);
                    // Gradins : un rang de bancs autour de la fosse.
                    if (d <= 12.6 && !entree) {
                        c.set(x, G + 1, z, "minecraft:smooth_stone_slab");
                    }
                }
            }
        }
        for (int dz = -1; dz <= 1; dz++) {
            c.set(ox - 10, G - 2, oz + dz, "minecraft:stone_brick_stairs[facing=west]");
            c.set(ox - 12, G, oz + dz, Material.POLISHED_ANDESITE);
        }
        for (int[] p : new int[][]{{5, 5}, {-5, 5}, {5, -5}, {-5, -5}}) {
            for (int y = G - 2; y <= G; y++) {
                c.set(ox + p[0], y, oz + p[1], Material.STONE_BRICKS);
            }
            c.set(ox + p[0], G + 1, oz + p[1], Material.CHISELED_STONE_BRICKS);
        }
        // Mâts à fanions rouges aux quatre coins.
        for (int[] p : new int[][]{{10, 10}, {-10, 10}, {10, -10}, {-10, -10}}) {
            int x = ox + p[0], z = oz + p[1];
            for (int y = G + 1; y <= G + 6; y++) {
                c.set(x, y, z, Material.DARK_OAK_FENCE);
            }
            c.set(x, G + 7, z, Material.GOLD_BLOCK);
            c.set(x + Integer.signum(p[0]), G + 6, z, Material.RED_WOOL);
            c.set(x + Integer.signum(p[0]), G + 5, z, Material.RED_WOOL);
            c.set(x + 2 * Integer.signum(p[0]), G + 6, z, Material.WHITE_WOOL);
        }
        c.pnj("arene", "<red><bold>Maître d'arène</bold>", "weaponsmith", ox - 13.5, G + 1, oz + 3.5, -90f);
        c.point("arene", ox - 13.5, G + 1, oz + 0.5, -90f);
        c.panneau("arene", "<red><bold>⚔ ZONE DE DUEL ⚔</bold><newline><gray>PvP activé dans la fosse<newline><gray>Inventaire gardé à la mort", ox + 0.5, G + 7, oz + 0.5);    }

    // ------------------------------------------------------------------ Port, bateau et phare (sud)

    private static void port(Canvas c) {
        int debut = 11;
        while (sol(0, debut) >= 63) {
            debut++;
        }
        int fin = debut + 22;
        for (int z = debut - 1; z < fin; z++) {
            for (int x = -2; x <= 2; x++) {
                c.set(x, 63, z, Material.SPRUCE_PLANKS);
            }
            if (Math.floorMod(z - debut, 4) == 0) {
                for (int x : new int[]{-2, 2}) {
                    for (int y = sol(x, z) + 1; y <= 62; y++) {
                        c.set(x, y, z, Material.SPRUCE_LOG);
                    }
                    c.set(x, 64, z, Material.SPRUCE_FENCE);
                    if (Math.floorMod(z - debut, 8) == 0) {
                        c.set(x, 65, z, Material.LANTERN);
                    }
                }
            }
        }
        // Caisses sur le ponton.
        c.set(-2, 64, debut + 6, Material.BARREL);
        c.set(-2, 64, debut + 7, Material.BARREL);
        c.set(-2, 65, debut + 6, "minecraft:spruce_trapdoor[half=bottom]");
        c.set(-2, 64, debut + 14, Material.HAY_BLOCK);
        c.set(2, 64, debut + 18, "minecraft:white_carpet");
        bateau(c, 6, debut + 5);
        for (int x = -4; x <= 4; x++) {
            for (int z = fin; z <= fin + 8; z++) {
                c.set(x, 63, z, Material.SPRUCE_PLANKS);
                if (Math.abs(x) == 4 || z == fin + 8) {
                    for (int y = sol(x, z) + 1; y <= 62; y++) {
                        c.set(x, y, z, Material.SPRUCE_LOG);
                    }
                    if ((Math.abs(x) == 4 && Math.floorMod(z - fin, 2) == 0) || (z == fin + 8 && x % 2 == 0)) {
                        c.set(x, 64, z, Material.SPRUCE_FENCE);
                    }
                }
            }
        }
        int lz = fin + 5;
        for (int x = -3; x <= 3; x++) {
            for (int z = lz - 3; z <= lz + 3; z++) {
                double d = Math.hypot(x, z - lz);
                if (d > 2.6) {
                    continue;
                }
                for (int y = 64; y <= 79; y++) {
                    if (d > 1.5) {
                        c.set(x, y, z, ((y - 64) / 3) % 2 == 0 ? Material.WHITE_CONCRETE : Material.BLUE_CONCRETE);
                    } else {
                        c.set(x, y, z, Material.AIR);
                    }
                }
                c.set(x, 80, z, Material.SMOOTH_QUARTZ);
                for (int y = 81; y <= 83; y++) {
                    c.set(x, y, z, d > 1.5 ? (Math.abs(x) == 2 && Math.abs(z - lz) == 2 ? Material.QUARTZ_PILLAR : Material.LIGHT_BLUE_STAINED_GLASS)
                            : (x == 0 && z == lz ? Material.SEA_LANTERN : Material.AIR));
                }
                c.set(x, 84, z, d > 1.5 ? Material.SMOOTH_QUARTZ : Material.GOLD_BLOCK);
            }
        }
        c.set(0, 85, lz, Material.GOLD_BLOCK);
        c.set(0, 86, lz, Material.LIGHTNING_ROD);
        c.set(-3, 64, fin + 1, Material.BARREL);
        c.set(-4, 64, fin + 1, Material.BARREL);
        c.set(-4, 65, fin + 1, Material.BARREL);
        c.set(-4, 64, fin + 2, "minecraft:cauldron");
        c.pnj("pecheur", "<#4DA3FF><bold>Pêcheur</bold>", "fisherman", 2.5, 64, fin + 1.5, 180f);
        c.point("port", 0.5, 64, debut + 1.5, 0f);
        c.panneau("port", "<#F5C542><bold>LE PORT</bold><newline><gray>Pêcheur au bout du ponton", 0.5, 67.5, debut - 1.5);
        c.particule("END_ROD", 0.5, 82, lz + 0.5, 2, 1.5, 0.6, 1.5, 0.02);
    }

    /** Petit voilier amarré le long du ponton (coque en épicéa, voile blanche et bleue). */
    private static void bateau(Canvas c, int cx, int z0) {
        for (int k = 0; k <= 11; k++) {
            int z = z0 + k;
            int w = (k == 0 || k == 11) ? 0 : (k == 1 || k == 10) ? 1 : 2;
            for (int dx = -w; dx <= w; dx++) {
                int x = cx + dx;
                c.set(x, 61, z, Material.SPRUCE_PLANKS);
                boolean bord = Math.abs(dx) == w;
                c.set(x, 62, z, bord ? Material.SPRUCE_PLANKS : Material.AIR);
                c.set(x, 63, z, bord ? "minecraft:spruce_slab" : "AIR");
            }
        }
        c.set(cx, 63, z0, Material.SPRUCE_FENCE);
        c.set(cx, 64, z0 - 1, Material.SPRUCE_FENCE);
        for (int y = 62; y <= 71; y++) {
            c.set(cx, y, z0 + 5, Material.SPRUCE_FENCE);
        }
        for (int y = 65; y <= 70; y++) {
            int w = y <= 67 ? 2 : y <= 69 ? 1 : 0;
            for (int dx = -w; dx <= w; dx++) {
                if (dx != 0) {
                    c.set(cx + dx, y, z0 + 5, (y + dx) % 3 == 0 ? Material.LIGHT_BLUE_WOOL : Material.WHITE_WOOL);
                }
            }
        }
        c.set(cx, 72, z0 + 5, "minecraft:blue_banner");
        c.set(cx, 62, z0 + 8, Material.BARREL);
        c.set(cx + 1, 62, z0 + 9, Material.BARREL);
        // Amarre jusqu'au ponton.
        c.set(cx - 3, 63, z0 + 3, "minecraft:spruce_slab");
    }

    // ------------------------------------------------------------------ Pavillon des kits (nord-est de la place)

    private static void kits(Canvas c) {
        int ox = 19, oz = -19;
        for (int x = ox - 6; x <= ox + 6; x++) {
            for (int z = oz - 6; z <= oz + 6; z++) {
                double d = Math.hypot(x - ox, z - oz);
                if (d > 5.5) {
                    continue;
                }
                fondation(c, x, z, d > 4.5 ? Material.POLISHED_DEEPSLATE
                        :((int) d) % 2 == 0 ? Material.SMOOTH_QUARTZ : Material.LIGHT_BLUE_TERRACOTTA);
                if (d <= 5.5) {
                    c.set(x, G + 6, z, d > 4.5 ? "minecraft:smooth_quartz_slab" : "SMOOTH_QUARTZ");
                }
                if (d <= 3.6) {
                    c.set(x, G + 7, z, Material.LIGHT_BLUE_CONCRETE);
                }
                if (d <= 2.1) {
                    c.set(x, G + 8, z, Material.BLUE_CONCRETE);
                }
            }
        }
        c.set(ox, G + 9, oz, Material.GOLD_BLOCK);
        c.set(ox, G + 10, oz, Material.END_ROD);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + Math.PI / 6;
            int x = ox + (int) Math.round(Math.cos(a) * 4), z = oz + (int) Math.round(Math.sin(a) * 4);
            for (int y = G + 1; y <= G + 5; y++) {
                c.set(x, y, z, Material.QUARTZ_PILLAR);
            }
            c.set(x, G + 1, z, Material.CHISELED_QUARTZ_BLOCK);
        }
        c.set(ox, G + 5, oz, "minecraft:lantern[hanging=true]");
        // Coffres de récompense en décor autour de l'intendant.
        c.set(ox - 2, G + 1, oz - 2, "minecraft:chest[facing=south]");
        c.set(ox + 2, G + 1, oz - 2, "minecraft:chest[facing=south]");
        c.set(ox - 2, G + 1, oz + 2, Material.BARREL);
        c.set(ox + 2, G + 1, oz + 2, Material.GOLD_BLOCK);
        c.pnj("intendant", "<#F5C542><bold>Kits</bold>", "nitwit", ox + 0.5, G + 1, oz + 0.5, 135f);
        c.point("kits", ox - 4.5, G + 1, oz + 5.5, -135f);
        c.panneau("kits", "<#F5C542><bold>KITS</bold><newline><gray>Joueur · VIP · VIP+ · ORBIT", ox + 0.5, G + 12, oz + 0.5);
        c.particule("HAPPY_VILLAGER", ox + 0.5, G + 3, oz + 0.5, 3, 2, 1, 2, 0);
    }

    // ------------------------------------------------------------------ Maisons

    /** Chaumière 7 x 7 à toit à deux pentes et cheminée qui fume. porte = 1 : porte au sud. */
    private static void maison(Canvas c, int ox, int oz, int porte) {
        String bois = hasard(ox, oz, 31) < 50 ? "oak" : "spruce";
        String toit = hasard(ox, oz, 32) < 50 ? "dark_oak" : "spruce";
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = ox + dx, z = oz + dz;
                boolean mur = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                boolean coin = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                fondation(c, x, z, mur ? Material.COBBLESTONE : Material.valueOf(bois.toUpperCase() + "_PLANKS"));
                for (int y = G + 1; y <= G + 3; y++) {
                    if (!mur) {
                        c.set(x, y, z, Material.AIR);
                    } else if (coin) {
                        c.set(x, y, z, "minecraft:stripped_" + bois + "_log");
                    } else if (y == G + 2 && (dx == 0 || dz == 0)) {
                        c.set(x, y, z, Material.GLASS);
                    } else {
                        c.set(x, y, z, "minecraft:" + (y == G + 1 ? "cobblestone" : bois + "_planks"));
                    }
                }
            }
        }
        // Porte (au milieu du mur sud ou nord).
        int pz = oz + 3 * porte;
        String sens = porte > 0 ? "south" : "north";
        c.set(ox, G + 1, pz, "minecraft:" + bois + "_door[half=lower,facing=" + sens + ",hinge=left]");
        c.set(ox, G + 2, pz, "minecraft:" + bois + "_door[half=upper,facing=" + sens + ",hinge=left]");
        c.set(ox, G, pz + porte, "minecraft:cobblestone_slab");
        // Toit à deux pentes (faîte selon x), pignons en bois.
        for (int k = 0; k <= 4; k++) {
            int y = G + 4 + k;
            for (int dx = -4; dx <= 4; dx++) {
                if (k < 4) {
                    c.set(ox + dx, y, oz - 4 + k, "minecraft:" + toit + "_stairs[facing=south]");
                    c.set(ox + dx, y, oz + 4 - k, "minecraft:" + toit + "_stairs[facing=north]");
                } else {
                    c.set(ox + dx, y, oz, "minecraft:" + toit + "_slab");
                }
            }
            for (int dz = -(3 - k); dz <= 3 - k; dz++) {
                if (k < 4 && Math.abs(dz) < 4 - k) {
                    c.set(ox - 3, y, oz + dz, "minecraft:" + bois + "_planks");
                    c.set(ox + 3, y, oz + dz, "minecraft:" + bois + "_planks");
                }
            }
        }
        // Cheminée en pierre avec un feu de camp qui fume.
        int chx = ox + 2, chz = oz - porte * 2;
        for (int y = G + 1; y <= G + 9; y++) {
            c.set(chx, y, chz, Material.BRICKS);
        }
        c.set(chx, G + 10, chz, "minecraft:campfire[lit=true,signal_fire=false]");
        // Intérieur : lit, table, tonneau, lanterne, bacs à fleurs aux fenêtres.
        int lz = oz - porte * 2;
        c.set(ox - 2, G + 1, lz, "minecraft:red_bed[part=head,facing=" + (porte > 0 ? "north" : "south") + "]");
        c.set(ox - 2, G + 1, lz + porte, "minecraft:red_bed[part=foot,facing=" + (porte > 0 ? "north" : "south") + "]");
        c.set(ox - 2, G + 1, oz + porte * 2, Material.CRAFTING_TABLE);
        c.set(ox + 2, G + 1, oz + porte * 2, Material.BARREL);
        c.set(ox + 1, G + 1, oz - porte * 2, "minecraft:" + bois + "_fence");
        c.set(ox + 1, G + 2, oz - porte * 2, "minecraft:" + bois + "_pressure_plate");
        c.set(ox, G + 7, oz, "minecraft:lantern[hanging=true]");
        for (int dx : new int[]{-2, 2}) {
            int z = oz + 4 * porte;
            if (c.type(ox + dx, G + 1, z) == Material.AIR) {
                c.set(ox + dx, G + 1, z, hasard(ox + dx, z, 33) < 50 ? "minecraft:rose_bush[half=lower]" : "minecraft:lilac[half=lower]");
                c.set(ox + dx, G + 2, z, hasard(ox + dx, z, 33) < 50 ? "minecraft:rose_bush[half=upper]" : "minecraft:lilac[half=upper]");
            }
        }
        c.particule("CAMPFIRE_COSY_SMOKE", chx + 0.5, G + 11.5, chz + 0.5, 1, 0.1, 0.2, 0.1, 0.01);
    }

    // ------------------------------------------------------------------ Ferme (nord-est du portail)

    private static void ferme(Canvas c) {
        int x1 = 12, x2 = 22, z1 = -45, z2 = -35;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean bord = x == x1 || x == x2 || z == z1 || z == z2;
                if (bord) {
                    fondation(c, x, z, Material.COARSE_DIRT);
                    c.set(x, G + 1, z, (z == z2 && x == 17) ? "minecraft:oak_fence_gate[facing=south]" : "OAK_FENCE");
                    continue;
                }
                if (x == 17) {
                    fondation(c, x, z, Material.WATER);
                    c.set(x, G - 1, z, Material.CLAY);
                    if (z == z1 + 1 || z == z2 - 1) {
                        c.set(x, G + 1, z, "minecraft:lily_pad");
                    }
                    continue;
                }
                fondation(c, x, z, Material.DIRT);
                c.set(x, G, z, "minecraft:farmland[moisture=7]");
                String culture = x <= 14 ? "minecraft:wheat[age=7]" : x <= 16 ? "minecraft:carrots[age=7]"
                        : x <= 19 ? "minecraft:potatoes[age=7]" : "minecraft:beetroots[age=3]";
                c.set(x, G + 1, z, culture);
            }
        }
        for (int[] p : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
            c.set(p[0], G + 2, p[1], Material.LANTERN);
        }
        // Épouvantail, botte de foin, composteur.
        c.set(24, G + 1, -40, Material.OAK_FENCE);
        c.set(24, G + 2, -40, Material.HAY_BLOCK);
        c.set(24, G + 3, -40, "minecraft:carved_pumpkin[facing=west]");
        c.set(23, G + 2, -40, "minecraft:oak_fence");
        c.set(25, G + 2, -40, "minecraft:oak_fence");
        c.set(11, G + 1, -44, Material.HAY_BLOCK);
        c.set(11, G + 1, -43, Material.HAY_BLOCK);
        c.set(11, G + 2, -44, Material.HAY_BLOCK);
        c.set(11, G + 1, -41, Material.COMPOSTER);
        for (int x = 11; x <= 24; x++) {
            for (int z = -46; z <= -34; z++) {
                c.reserve(x, z);
            }
        }
    }

    // ------------------------------------------------------------------ Étang (entre l'hôtel des ventes et le port)

    private static void etang(Canvas c) {
        int ox = -10, oz = 32;
        for (int x = ox - 7; x <= ox + 7; x++) {
            for (int z = oz - 7; z <= oz + 7; z++) {
                double a = Math.atan2(z - oz, x - ox);
                double r = 4.5 + 0.9 * Math.sin(3 * a + 1) + 0.5 * Math.sin(5 * a);
                double d = Math.hypot(x - ox, z - oz);
                if (d <= r) {
                    c.set(x, G - 2, z, Material.CLAY);
                    c.set(x, G - 1, z, d < r - 1.5 ? Material.WATER : Material.SAND);
                    c.set(x, G, z, Material.WATER);
                    if (hasard(x, z, 41) < 14) {
                        c.set(x, G + 1, z, "minecraft:lily_pad");
                    } else if (d < r - 1.5 && hasard(x, z, 42) < 10) {
                        c.set(x, G - 1, z, "minecraft:seagrass");
                    }
                } else if (d <= r + 1.2) {
                    c.set(x, G, z, hasard(x, z, 43) < 30 ? Material.MUD : Material.GRASS_BLOCK);
                    int h = hasard(x, z, 44);
                    if (h < 22) {
                        c.set(x, G + 1, z, Material.SUGAR_CANE);
                        c.set(x, G + 2, z, Material.SUGAR_CANE);
                    } else if (h < 40) {
                        c.set(x, G + 1, z, "minecraft:tall_grass[half=lower]");
                        c.set(x, G + 2, z, "minecraft:tall_grass[half=upper]");
                    } else if (h < 48) {
                        c.set(x, G + 1, z, "minecraft:blue_orchid");
                    }
                }
            }
        }
        // Petit ponton de pêche en bois.
        for (int z = oz + 3; z <= oz + 6; z++) {
            c.set(ox, G + 1, z, "minecraft:spruce_slab");
        }
        c.set(ox + 1, G + 1, oz + 6, "minecraft:spruce_fence");
        c.set(ox + 1, G + 2, oz + 6, "minecraft:lantern[hanging=false]");
    }

    // ------------------------------------------------------------------ Plage : parasols, feu de camp, rochers

    private static void plage(Canvas c) {
        String[] toiles = {"WHITE_WOOL", "LIGHT_BLUE_WOOL", "YELLOW_WOOL", "BLUE_WOOL", "ORANGE_WOOL", "LIME_WOOL"};
        double[] angles = {1.25, 1.95, 2.35, 0.85, 0.15, -0.35, 2.85, 3.5};
        for (int i = 0; i < angles.length; i++) {
            double r = rivage(angles[i]) - 4.5;
            int x = (int) Math.round(Math.cos(angles[i]) * r), z = (int) Math.round(Math.sin(angles[i]) * r);
            int s = sol(x, z);
            if (s < 63 || !c.free(x, z, 3)) {
                continue;
            }
            for (int y = s + 1; y <= s + 3; y++) {
                c.set(x, y, z, Material.BIRCH_FENCE);
            }
            String toile = toiles[i % toiles.length];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    c.set(x + dx, s + 4, z + dz, (dx == 0 && dz == 0) ? "WHITE_WOOL" : ((dx + dz) % 2 == 0 ? toile : "WHITE_WOOL"));
                }
            }
            for (int[] d : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
                c.set(x + d[0], s + 3, z + d[1], "minecraft:" + toile.toLowerCase().replace("_wool", "_carpet"));
                c.reserve(x + d[0], z + d[1]);
            }
            // Serviette et chaise longue.
            String serviette = "minecraft:" + toiles[(i + 2) % toiles.length].toLowerCase().replace("_wool", "_carpet");
            if (sol(x + 2, z + 1) == s) {
                c.set(x + 2, s + 1, z + 1, serviette);
                c.set(x + 2, s + 1, z + 2, serviette);
            }
            if (sol(x - 2, z + 1) == s) {
                c.set(x - 2, s + 1, z + 1, "minecraft:birch_stairs[facing=north]");
                c.set(x - 2, s + 1, z + 2, "minecraft:birch_slab");
            }
        }
        // Feu de camp sur la plage ouest, entouré de bûches pour s'asseoir.
        double a = Math.PI + 0.25;
        double r = rivage(a) - 5;
        int fx = (int) Math.round(Math.cos(a) * r), fz = (int) Math.round(Math.sin(a) * r);
        int fs = sol(fx, fz);
        if (fs >= 63 && c.free(fx, fz, 3)) {
            c.set(fx, fs + 1, fz, "minecraft:campfire[lit=true,signal_fire=false]");
            for (int[] d : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
                c.set(fx + d[0], sol(fx + d[0], fz + d[1]) + 1, fz + d[1],
                        "minecraft:stripped_oak_log[axis=" + (d[0] != 0 ? "z" : "x") + "]");
            }
            c.particule("CAMPFIRE_COSY_SMOKE", fx + 0.5, fs + 2.5, fz + 0.5, 1, 0.1, 0.1, 0.1, 0.01);
        }
        // Rochers à moitié dans l'eau.
        for (int i = 0; i < 12; i++) {
            double ra = i * (2 * Math.PI / 12) + 0.3;
            double rr = rivage(ra) + 1.5;
            int x = (int) Math.round(Math.cos(ra) * rr), z = (int) Math.round(Math.sin(ra) * rr);
            if (Math.abs(x) <= 8 && z > 0) {
                continue; // pas devant le port
            }
            int taille = 1 + hasard(x, z, 51) % 2;
            int base = sol(x, z);
            for (int dx = -taille - 1; dx <= taille + 1; dx++) {
                for (int dz = -taille - 1; dz <= taille + 1; dz++) {
                    for (int dy = -1; dy <= taille + 1; dy++) {
                        double dd = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.6);
                        if (dd > taille + 0.6 + (hasard(x + dx, z + dz, dy + 60) % 3) * 0.2) {
                            continue;
                        }
                        int y = Math.max(base, 62) + dy;
                        int h = hasard(x + dx, z + dz, dy + 70);
                        c.set(x + dx, y, z + dz, h < 40 ? Material.STONE : h < 70 ? Material.ANDESITE
                                : h < 85 ? Material.COBBLESTONE : Material.MOSSY_COBBLESTONE);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ Nature : palmiers, arbres, herbes, fond marin

    private static void nature(Canvas c) {
        for (int i = 0; i < 26; i++) {
            double a = i * (2 * Math.PI / 26) + 0.13 * (i % 3);
            double r = rivage(a) - 3 - (i % 4);
            int x = (int) Math.round(Math.cos(a) * r);
            int z = (int) Math.round(Math.sin(a) * r);
            if (c.free(x, z, 3) && sol(x, z) >= 63) {
                palmier(c, x, z, sol(x, z), a);
            }
        }
        int[][] arbres = {{-14, -22}, {16, -26}, {-50, -16}, {50, -22}, {-12, 44}, {14, 50}, {-52, 6},
                {-44, -34}, {36, -40}, {-18, -46}, {28, -50}, {52, 16}, {-54, 34}, {44, 40}, {-38, 48},
                {-26, -36}, {-30, -48}, {6, -52}, {-6, -50}, {48, -8}, {54, -36}, {-56, -8}, {-22, 52},
                {30, 50}, {48, 30}, {-60, 22}, {-8, -22}, {24, -8}, {-22, 12}, {-48, 40}};
        for (int i = 0; i < arbres.length; i++) {
            int x = arbres[i][0], z = arbres[i][1];
            if (!plage(x, z) && sol(x, z) >= G && c.free(x, z, 3)) {
                String bois = i % 5 == 0 ? "cherry" : i % 3 == 0 ? "birch" : "oak";
                arbre(c, x, z, bois, 5 + i % 3);
            }
        }
        String[] fleurs = {"minecraft:dandelion", "minecraft:poppy", "minecraft:cornflower",
                "minecraft:oxeye_daisy", "minecraft:azure_bluet", "minecraft:allium", "minecraft:pink_tulip"};
        for (int x = -REACH; x <= REACH; x++) {
            for (int z = -REACH; z <= REACH; z++) {
                double d = Math.hypot(x, z);
                if (d > rivage(x, z) + 15 || c.reserved(x, z)) {
                    continue;
                }
                int s = sol(x, z);
                int h = hasard(x, z, 1);
                Material dessus = c.type(x, s, z);
                if (dessus == Material.GRASS_BLOCK && c.type(x, s + 1, z) == Material.AIR) {
                    // Les fleurs poussent en massifs (bruit lent), l'herbe partout.
                    double massif = Math.sin(x * 0.21 + 0.5) * Math.sin(z * 0.19 + 1.7);
                    if (massif > 0.55 && h < 45) {
                        c.decor(x, s + 1, z, fleurs[(int) ((massif * 100 + hasard(x, z, 2)) % fleurs.length)]);
                    } else if (h < 20) {
                        c.decor(x, s + 1, z, "minecraft:short_grass");
                    } else if (h < 23 && c.type(x, s + 2, z) == Material.AIR) {
                        c.decor(x, s + 1, z, "minecraft:tall_grass[half=lower]");
                        c.decor(x, s + 2, z, "minecraft:tall_grass[half=upper]");
                    } else if (h < 25) {
                        c.decor(x, s + 1, z, fleurs[hasard(x, z, 2) % fleurs.length]);
                    } else if (h == 25 && c.free(x, z, 1)) {
                        c.decor(x, s + 1, z, "minecraft:azalea_leaves[persistent=true]");
                    } else if (h == 26 && c.free(x, z, 1)) {
                        c.decor(x, s + 1, z, "minecraft:sweet_berry_bush[age=3]");
                    }
                } else if (dessus == Material.SAND && s >= 63 && h < 2) {
                    c.decor(x, s + 1, z, "minecraft:dead_bush");
                } else if (s <= 60 && c.type(x, s + 1, z) == Material.WATER) {
                    if (h < 14) {
                        c.decor(x, s + 1, z, "minecraft:seagrass");
                    } else if (h < 16 && s <= 56) {
                        String[] coraux = {"minecraft:brain_coral_block", "minecraft:tube_coral_block",
                                "minecraft:fire_coral_block", "minecraft:horn_coral_block", "minecraft:bubble_coral_block"};
                        c.decor(x, s + 1, z, coraux[hasard(x, z, 4) % coraux.length]);
                    } else if (h < 18 && s <= 57) {
                        String[] coraux = {"minecraft:brain_coral", "minecraft:tube_coral", "minecraft:fire_coral",
                                "minecraft:horn_coral", "minecraft:bubble_coral", "minecraft:sea_pickle[pickles=3]"};
                        c.decor(x, s + 1, z, coraux[hasard(x, z, 5) % coraux.length]);
                    } else if (h == 18 && s <= 54) {
                        int haut = 3 + hasard(x, z, 6) % 5;
                        for (int k = 1; k < haut && s + k < 61; k++) {
                            c.decor(x, s + k, z, "minecraft:kelp_plant");
                        }
                        c.decor(x, Math.min(s + haut, 61), z, "minecraft:kelp[age=20]");
                    }
                }
            }
        }
    }

    private static void palmier(Canvas c, int x, int z, int s, double angle) {
        int lx = (int) Math.signum(Math.round(Math.cos(angle) * 2)), lz = (int) Math.signum(Math.round(Math.sin(angle) * 2));
        int tx = x, tz = z, haut = 6 + hasard(x, z, 5) % 3;
        for (int k = 1; k <= haut; k++) {
            if (k == 4 || k == 7) {
                tx += lx;
                tz += lz;
            }
            c.decor(tx, s + k, tz, "minecraft:jungle_log");
            c.reserve(tx, tz);
        }
        int ty = s + haut + 1;
        String f = "minecraft:jungle_leaves[persistent=true]";
        c.decor(tx, ty, tz, f);
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int k = 1; k <= 3; k++) {
                c.decor(tx + dir[0] * k, k == 3 ? ty - 1 : ty, tz + dir[1] * k, f);
            }
            c.decor(tx + dir[0] * 3, ty - 2, tz + dir[1] * 3, f);
        }
        for (int[] dir : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
            c.decor(tx + dir[0], ty, tz + dir[1], f);
            c.decor(tx + dir[0] * 2, ty - 1, tz + dir[1] * 2, f);
        }
        c.decor(tx, ty + 1, tz, f);
        c.decor(tx + 1, ty - 1, tz, "minecraft:cocoa[age=2,facing=west]");
    }

    private static void arbre(Canvas c, int x, int z, String bois, int haut) {
        int s = sol(x, z);
        String feuille = "minecraft:" + bois + "_leaves[persistent=true]";
        for (int dy = haut - 2; dy <= haut + 1; dy++) {
            int r = dy >= haut ? 1 : 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (r == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2 && hasard(x + dx, z + dz, dy) < 60) {
                        continue;
                    }
                    if (dy == haut + 1 && Math.abs(dx) + Math.abs(dz) > 1) {
                        continue;
                    }
                    c.decor(x + dx, s + dy, z + dz, feuille);
                }
            }
        }
        for (int dy = 1; dy <= haut; dy++) {
            c.decor(x, s + dy, z, "minecraft:" + bois + "_log");
        }
        c.reserve(x, z);
    }
}
