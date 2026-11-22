package fr.orbit.mmo.continent;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

import static fr.orbit.mmo.continent.ContinentData.ALP;
import static fr.orbit.mmo.continent.ContinentData.ASH;
import static fr.orbit.mmo.continent.ContinentData.BEACH;
import static fr.orbit.mmo.continent.ContinentData.BLACK_BEACH;
import static fr.orbit.mmo.continent.ContinentData.DARK_FOREST;
import static fr.orbit.mmo.continent.ContinentData.FOREST;
import static fr.orbit.mmo.continent.ContinentData.HILLS;
import static fr.orbit.mmo.continent.ContinentData.LAVA;
import static fr.orbit.mmo.continent.ContinentData.MARSH;
import static fr.orbit.mmo.continent.ContinentData.MESA;
import static fr.orbit.mmo.continent.ContinentData.MOOR;
import static fr.orbit.mmo.continent.ContinentData.OASIS;
import static fr.orbit.mmo.continent.ContinentData.OCEAN;
import static fr.orbit.mmo.continent.ContinentData.PINES;
import static fr.orbit.mmo.continent.ContinentData.PLAIN;
import static fr.orbit.mmo.continent.ContinentData.ROCK;
import static fr.orbit.mmo.continent.ContinentData.SAND;
import static fr.orbit.mmo.continent.ContinentData.SNOW;
import static fr.orbit.mmo.continent.ContinentData.VOLCANIC;
import static fr.orbit.mmo.continent.ContinentData.WATER;

/**
 * Terrain du continent : chaque colonne est lue dans {@link ContinentData} (hauteur, fluide, biome, route),
 * puis habillée selon son biome. Les routes sont posées par demi-blocs (dalles) pour se monter sans sauter ;
 * au-dessus de l'eau elles deviennent des ponts. La végétation est ajoutée par {@link Decor}.
 */
public final class ContinentGenerator extends ChunkGenerator {

    public static final int NONE = Integer.MIN_VALUE;

    private static final Material[] MESA_BANDS = {Material.ORANGE_TERRACOTTA, Material.TERRACOTTA, Material.TERRACOTTA,
            Material.YELLOW_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.RED_TERRACOTTA, Material.TERRACOTTA,
            Material.WHITE_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.BROWN_TERRACOTTA, Material.TERRACOTTA,
            Material.LIGHT_GRAY_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.RED_TERRACOTTA};

    private final ContinentData data;

    public ContinentGenerator(ContinentData data) {
        this.data = data;
    }

    /** Ce que le générateur sait d'une colonne de blocs. */
    public static final class Column {
        public int biome;
        /** Y du dernier bloc plein (sol, fond de l'eau ou chaussée). */
        public int top;
        /** Y du dernier bloc de fluide, ou NONE. */
        public int fluid;
        public boolean lava;
        public boolean road;
        public boolean royal;
        public boolean bridge;
        /** Demi-bloc (dalle) posé sur la chaussée. */
        public boolean slab;
        public boolean edge;
        public boolean pillar;
        /** Y du tablier d'un pont. */
        public int deck;

        /** Y où un joueur a les pieds. */
        public int standY() {
            if (bridge) {
                return deck + 1;
            }
            return Math.max(top, fluid == NONE ? top : fluid) + 1;
        }
    }

    public void column(int wx, int wz, boolean roads, ContinentData.RoadHit hit, Column c) {
        double h = data.height(wx, wz);
        int f = data.fluid(wx, wz);
        int level = Math.abs(f);
        c.biome = data.biome(wx, wz);
        c.lava = f < 0;
        c.top = (int) Math.floor(h) - 1;
        c.fluid = level > 0 && c.top < level - 1 ? level - 1 : NONE;
        c.road = false;
        c.bridge = false;
        c.slab = false;
        c.pillar = false;
        if (c.fluid == NONE) {
            // Berge : jamais plus basse que l'eau (ou la lave) de la colonne voisine, sinon le fluide tiendrait en l'air.
            for (int k = 0; k < 4; k++) {
                int nx = wx + (k == 0 ? -1 : (k == 1 ? 1 : 0)), nz = wz + (k == 2 ? -1 : (k == 3 ? 1 : 0));
                int surface = Math.abs(data.fluid(nx, nz)) - 1;
                if (surface > c.top && (int) Math.floor(data.height(nx, nz)) - 1 < surface) {
                    c.top = surface;
                }
            }
        }
        if (roads && data.road(wx, wz, 0, hit)) {
            double q = Math.round(hit.y * 2) / 2.0;
            int full = (int) Math.floor(q) - 1;
            c.road = true;
            c.royal = hit.road.royal;
            c.slab = q - Math.floor(q) >= 0.5;
            c.edge = hit.dist > hit.road.halfWidth() - (hit.road.royal ? 1.0 : 0.7);
            if (c.fluid != NONE && !c.lava) {
                c.bridge = true;
                c.deck = Math.max(full, c.fluid + 1);
                c.pillar = hit.sample % 12 == 0 && hit.dist < 1.3;
            } else {
                c.top = full;
                c.fluid = NONE;
            }
        }
    }

    // ------------------------------------------------------------------ Hasard stable (même résultat à chaque génération)
    static double rnd(int x, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Bruit lisse entre 0 et 1, pour des taches de sol de quelques blocs. */
    private static double patch(int x, int z, double scale, int salt) {
        double u = x / scale, v = z / scale;
        int x0 = (int) Math.floor(u), z0 = (int) Math.floor(v);
        double fx = u - x0, fz = v - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = rnd(x0, z0, salt), b = rnd(x0 + 1, z0, salt), c = rnd(x0, z0 + 1, salt), d = rnd(x0 + 1, z0 + 1, salt);
        return a + (b - a) * fx + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
    }

    static Material pick(double r, Object... table) {
        // table : matériau, poids, matériau, poids…
        double total = 0;
        for (int i = 1; i < table.length; i += 2) {
            total += ((Number) table[i]).doubleValue();
        }
        double t = r * total;
        for (int i = 0; i < table.length; i += 2) {
            t -= ((Number) table[i + 1]).doubleValue();
            if (t < 0) {
                return (Material) table[i];
            }
        }
        return (Material) table[table.length - 2];
    }

    // ------------------------------------------------------------------ Matériaux
    private static boolean grassy(int b) {
        return b == PLAIN || b == MOOR || b == FOREST || b == DARK_FOREST || b == HILLS || b == PINES || b == ALP
                || b == OASIS || b == MARSH;
    }

    private static boolean dark(int b) {
        return b == ASH || b == VOLCANIC || b == LAVA || b == BLACK_BEACH;
    }

    /** Bloc du dessus d'une colonne à l'air libre. */
    static Material surface(int b, int x, int z, int y, int steep) {
        double r = rnd(x, z, 1);
        if (steep >= 3 && grassy(b)) {
            return pick(r, Material.STONE, 60, Material.ANDESITE, 25, Material.COBBLESTONE, 15);
        }
        switch (b) {
            case BEACH:
                return Material.SAND;
            case BLACK_BEACH:
                return pick(r, Material.GRAVEL, 45, Material.BLACKSTONE, 35, Material.TUFF, 20);
            case PLAIN, ALP, OASIS:
                return Material.GRASS_BLOCK;
            case MOOR: {
                double p = patch(x, z, 9, 11);
                return p > 0.74 ? Material.COARSE_DIRT : (p < 0.22 ? Material.PODZOL : Material.GRASS_BLOCK);
            }
            case FOREST:
                return patch(x, z, 7, 12) > 0.8 ? Material.PODZOL : Material.GRASS_BLOCK;
            case DARK_FOREST: {
                double p = patch(x, z, 8, 13);
                return p > 0.62 ? Material.PODZOL : (p < 0.12 ? Material.COARSE_DIRT : Material.GRASS_BLOCK);
            }
            case HILLS:
                return patch(x, z, 6, 14) > 0.8 ? pick(r, Material.STONE, 60, Material.ANDESITE, 40) : Material.GRASS_BLOCK;
            case PINES: {
                double p = patch(x, z, 7, 15);
                return p > 0.55 ? Material.PODZOL : (p < 0.1 ? Material.COARSE_DIRT : Material.GRASS_BLOCK);
            }
            case ROCK: {
                // Vers le haut, des plaques de neige de plus en plus larges ; vers le bas, des restes d'alpage.
                double p = patch(x, z, 11, 21);
                if (steep < 3 && y >= 148 && p > 1.0 - (y - 146) * 0.022) {
                    return Material.SNOW_BLOCK;
                }
                if (steep < 2 && y < 146 && p > Math.max(0.45, 0.45 + (y - 132) * 0.035)) {
                    return Material.GRASS_BLOCK;
                }
                if (steep <= 1 && patch(x, z, 6, 22) > 0.7) {
                    return pick(r, Material.GRAVEL, 70, Material.COBBLESTONE, 30);
                }
                return pick(r, Material.STONE, 62, Material.ANDESITE, 22, Material.COBBLESTONE, 10, Material.TUFF, 6);
            }
            case SNOW:
                return steep >= 3 ? pick(r, Material.STONE, 70, Material.ANDESITE, 30) : Material.SNOW_BLOCK;
            case SAND:
                return steep >= 3 ? Material.SANDSTONE : Material.SAND;
            case MESA:
                return steep < 2 && patch(x, z, 6, 16) > 0.45 ? Material.RED_SAND : band(x, z, y);
            case MARSH: {
                double p = patch(x, z, 6, 17);
                return p > 0.7 ? Material.MUD : (p < 0.12 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK);
            }
            case ASH: {
                double p = patch(x, z, 8, 18);
                if (p > 0.66) {
                    return pick(r, Material.GRAY_CONCRETE_POWDER, 60, Material.LIGHT_GRAY_CONCRETE_POWDER, 40);
                }
                return pick(r, Material.TUFF, 50, Material.BLACKSTONE, 20, Material.SMOOTH_BASALT, 20, Material.GRAVEL, 10);
            }
            case VOLCANIC, LAVA:
                return pick(r, Material.BLACKSTONE, 45, Material.SMOOTH_BASALT, 30, Material.BASALT, 17, Material.TUFF, 8);
            default:
                return Material.GRASS_BLOCK;
        }
    }

    private static Material band(int x, int z, int y) {
        int shift = (int) (patch(x, z, 40, 19) * 3);
        return MESA_BANDS[Math.floorMod(y + shift, MESA_BANDS.length)];
    }

    /** Les trois blocs sous la surface. */
    static Material filler(int b, int steep) {
        if (steep >= 3 && grassy(b)) {
            return Material.STONE;
        }
        return switch (b) {
            case BEACH -> Material.SAND;
            case SAND -> Material.SANDSTONE;
            case ROCK, SNOW -> Material.STONE;
            case ASH -> Material.TUFF;
            case VOLCANIC, LAVA, BLACK_BEACH -> Material.BLACKSTONE;
            case MARSH -> Material.MUD;
            default -> Material.DIRT;
        };
    }

    /** Fond d'une colonne noyée. */
    private static Material bed(Column c, int x, int z) {
        double r = rnd(x, z, 2);
        if (c.lava) {
            return pick(r, Material.MAGMA_BLOCK, 35, Material.BLACKSTONE, 45, Material.BASALT, 20);
        }
        int depth = c.fluid - c.top;
        if (c.biome == MARSH) {
            return pick(r, Material.MUD, 75, Material.CLAY, 25);
        }
        if (c.biome == OCEAN || c.biome == BEACH) {
            return depth <= 4 ? Material.SAND : (patch(x, z, 10, 20) > 0.6 ? Material.GRAVEL : (r < 0.08 ? Material.CLAY : Material.SAND));
        }
        if (dark(c.biome)) {
            return pick(r, Material.GRAVEL, 55, Material.BLACKSTONE, 45);
        }
        return pick(r, Material.GRAVEL, 45, Material.SAND, 25, Material.DIRT, 20, Material.CLAY, 10);
    }

    static Material roadBlock(Column c, int x, int z) {
        double r = rnd(x, z, 3);
        int b = c.biome;
        if (b == SAND || b == MESA || b == OASIS || b == BEACH) {
            return pick(r, Material.SMOOTH_SANDSTONE, 60, Material.SANDSTONE, 25, Material.CUT_SANDSTONE, 15);
        }
        if (dark(b)) {
            // Pavés clairs : ils se détachent de la cendre et du basalte.
            return pick(r, Material.SMOOTH_STONE, 40, Material.STONE_BRICKS, 25, Material.CRACKED_STONE_BRICKS, 20, Material.COBBLESTONE, 15);
        }
        if (c.royal) {
            return pick(r, Material.STONE_BRICKS, 55, Material.CRACKED_STONE_BRICKS, 12, Material.MOSSY_STONE_BRICKS, 10,
                    Material.COBBLESTONE, 13, Material.ANDESITE, 10);
        }
        if (b == MARSH) {
            return pick(r, Material.PACKED_MUD, 40, Material.MUD_BRICKS, 30, Material.SPRUCE_PLANKS, 30);
        }
        // Terre battue partout ailleurs, y compris sur la roche et la neige où elle trace une piste brune bien lisible.
        return pick(r, Material.DIRT_PATH, 62, Material.COARSE_DIRT, 20, Material.GRAVEL, 10, Material.COBBLESTONE, 8);
    }

    static Material roadSlab(Column c) {
        int b = c.biome;
        if (b == SAND || b == MESA || b == OASIS || b == BEACH) {
            return Material.SMOOTH_SANDSTONE_SLAB;
        }
        if (dark(b)) {
            return Material.SMOOTH_STONE_SLAB;
        }
        if (c.royal) {
            return Material.STONE_BRICK_SLAB;
        }
        return b == MARSH ? Material.MUD_BRICK_SLAB : Material.COBBLESTONE_SLAB;
    }

    // ------------------------------------------------------------------ Génération
    @Override
    public void generateNoise(WorldInfo info, Random random, int cx, int cz, ChunkData chunk) {
        int minY = chunk.getMinHeight();
        int x0 = cx << 4, z0 = cz << 4;
        boolean roads = data.chunkHasRoad(cx, cz);
        ContinentData.RoadHit hit = new ContinentData.RoadHit();
        Column[] cols = new Column[256];
        // Hauteurs sur 18 x 18 : il faut les voisines du bord pour repérer les pentes raides.
        int[] tops = new int[18 * 18];
        int low = Integer.MAX_VALUE;
        Column edge = new Column();
        for (int z = -1; z <= 16; z++) {
            for (int x = -1; x <= 16; x++) {
                boolean in = x >= 0 && x < 16 && z >= 0 && z < 16;
                Column c = in ? (cols[z * 16 + x] = new Column()) : edge;
                if (in) {
                    column(x0 + x, z0 + z, roads, hit, c);
                    low = Math.min(low, c.top);
                } else {
                    c.top = (int) Math.floor(data.height(x0 + x, z0 + z)) - 1;
                }
                tops[(z + 1) * 18 + x + 1] = c.top;
            }
        }
        // Socle commun à tout le chunk, d'un bloc.
        int bulk = Math.max(minY + 1, low - 4);
        chunk.setRegion(0, minY, 0, 16, minY + 1, 16, Material.BEDROCK);
        if (bulk > minY + 1) {
            chunk.setRegion(0, minY + 1, 0, 16, Math.min(bulk, 0), 16, Material.DEEPSLATE);
            if (bulk > 0) {
                chunk.setRegion(0, 0, 0, 16, bulk, 16, Material.STONE);
            }
        }
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                Column c = cols[z * 16 + x];
                int wx = x0 + x, wz = z0 + z, top = c.top, i = (z + 1) * 18 + x + 1;
                int steep = Math.max(Math.max(Math.abs(top - tops[i - 1]), Math.abs(top - tops[i + 1])),
                        Math.max(Math.abs(top - tops[i - 18]), Math.abs(top - tops[i + 18])));
                boolean wet = c.fluid != NONE;
                boolean mesa = c.biome == MESA && !wet;
                Material rock = dark(c.biome) ? Material.BLACKSTONE : (c.biome == SAND ? Material.SANDSTONE : Material.STONE);
                Material fill = wet ? bed(c, wx, wz) : filler(c.biome, steep);
                for (int y = bulk; y <= top; y++) {
                    Material m;
                    if (mesa && y > top - 26) {
                        m = y == top ? surface(MESA, wx, wz, y, steep) : band(wx, wz, y);
                    } else if (y == top) {
                        m = wet ? fill : (c.road ? roadBlock(c, wx, wz) : surface(c.biome, wx, wz, y, steep));
                        if (c.slab && m == Material.DIRT_PATH) {
                            // Un chemin de terre redevient de la terre sous une dalle.
                            m = Material.COARSE_DIRT;
                        }
                    } else if (y >= top - 3) {
                        m = c.biome == SNOW && !wet && y == top - 1 ? Material.SNOW_BLOCK : fill;
                    } else {
                        m = y < 0 ? Material.DEEPSLATE : rock;
                    }
                    chunk.setBlock(x, y, z, m);
                }
                if (wet) {
                    chunk.setRegion(x, top + 1, z, x + 1, c.fluid + 1, z + 1, c.lava ? Material.LAVA : Material.WATER);
                }
                if (c.road && !c.bridge && c.slab) {
                    chunk.setBlock(x, top + 1, z, roadSlab(c));
                }
                if (c.bridge) {
                    Material deck = c.royal ? Material.STONE_BRICKS : Material.SPRUCE_PLANKS;
                    Material slab = c.royal ? Material.STONE_BRICK_SLAB : Material.SPRUCE_SLAB;
                    if (c.pillar) {
                        chunk.setRegion(x, top + 1, z, x + 1, c.deck, z + 1, c.royal ? Material.STONE_BRICKS : Material.SPRUCE_LOG);
                    }
                    chunk.setBlock(x, c.deck, z, c.edge && !c.royal ? Material.STRIPPED_SPRUCE_LOG : deck);
                    if (c.edge) {
                        // Parapet : bloc plein ou dalle (les murets ne se raccordent pas pendant la génération).
                        chunk.setBlock(x, c.deck + 1, z, c.royal ? Material.STONE_BRICKS : Material.SPRUCE_SLAB);
                    } else if (c.slab) {
                        chunk.setBlock(x, c.deck + 1, z, slab);
                    }
                }
            }
        }
    }

    /** Biome du jeu (couleurs de l'herbe et de l'eau, brouillard, particules) pour chaque biome du dessin. */
    private Biome gameBiome(int x, int z) {
        int b = data.biome(x, z);
        switch (b) {
            case OCEAN:
                return Biome.OCEAN;
            case WATER: {
                ContinentData.Region r = data.region(x, z);
                return r != null && r.terrain().equals("marais") ? Biome.SWAMP : Biome.RIVER;
            }
            case BEACH:
                return Biome.BEACH;
            case PLAIN:
                return Biome.PLAINS;
            case MOOR:
                return Biome.WINDSWEPT_HILLS;
            case FOREST:
                return Biome.FOREST;
            case DARK_FOREST:
                return Biome.DARK_FOREST;
            case HILLS, ALP:
                return Biome.MEADOW;
            case PINES:
                return Biome.TAIGA;
            case ROCK:
                return Biome.STONY_PEAKS;
            case SNOW:
                return Biome.FROZEN_PEAKS;
            case SAND:
                return Biome.DESERT;
            case MESA:
                return Biome.BADLANDS;
            case OASIS:
                return Biome.SPARSE_JUNGLE;
            case MARSH:
                return Biome.SWAMP;
            default:
                return Biome.BASALT_DELTAS;
        }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                return gameBiome(x, z);
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(Biome.OCEAN, Biome.RIVER, Biome.SWAMP, Biome.BEACH, Biome.PLAINS, Biome.WINDSWEPT_HILLS,
                        Biome.FOREST, Biome.DARK_FOREST, Biome.MEADOW, Biome.TAIGA, Biome.STONY_PEAKS, Biome.FROZEN_PEAKS,
                        Biome.DESERT, Biome.BADLANDS, Biome.SPARSE_JUNGLE, Biome.BASALT_DELTAS);
            }
        };
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of(new Decor());
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        ContinentData.Place p = data.place("les-quatre-vents");
        int x = p == null ? 0 : p.x(), z = p == null ? 0 : p.z();
        return new Location(world, x + 0.5, standY(x, z), z + 0.5);
    }

    /** Y où poser les pieds d'un joueur à cet endroit. */
    public int standY(int x, int z) {
        Column c = new Column();
        column(x, z, true, new ContinentData.RoadHit(), c);
        return c.standY() + (c.slab ? 1 : 0);
    }

    @Override
    public int getBaseHeight(WorldInfo info, Random random, int x, int z, org.bukkit.HeightMap heightMap) {
        Column c = new Column();
        column(x, z, true, new ContinentData.RoadHit(), c);
        return c.standY();
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    // ------------------------------------------------------------------ Végétation
    private static double treeDensity(int b) {
        return switch (b) {
            case PLAIN -> 0.35;
            case MOOR -> 0.25;
            case FOREST -> 9;
            case DARK_FOREST -> 12;
            case HILLS -> 0.8;
            case PINES -> 8;
            case ALP -> 0.15;
            case OASIS -> 3;
            case MARSH -> 2.2;
            default -> 0;
        };
    }

    private static TreeType tree(int b, Random r) {
        int n = r.nextInt(100);
        return switch (b) {
            case PLAIN -> n < 80 ? TreeType.TREE : (n < 95 ? TreeType.BIRCH : TreeType.BIG_TREE);
            case MOOR -> n < 60 ? TreeType.REDWOOD : TreeType.TREE;
            case FOREST -> n < 55 ? TreeType.TREE : (n < 85 ? TreeType.BIRCH : TreeType.BIG_TREE);
            case DARK_FOREST -> n < 70 ? TreeType.DARK_OAK : (n < 90 ? TreeType.TREE : TreeType.BIG_TREE);
            case HILLS -> n < 50 ? TreeType.TREE : (n < 80 ? TreeType.BIRCH : TreeType.REDWOOD);
            case PINES -> n < 60 ? TreeType.REDWOOD : TreeType.TALL_REDWOOD;
            case ALP -> TreeType.REDWOOD;
            case OASIS -> n < 50 ? TreeType.ACACIA : (n < 80 ? TreeType.SMALL_JUNGLE : TreeType.TREE);
            case MARSH -> n < 75 ? TreeType.SWAMP : TreeType.MANGROVE;
            default -> null;
        };
    }

    /** Chance qu'un essai de plante réussisse, sur 48 essais par chunk. */
    private static double plantChance(int b) {
        return switch (b) {
            case PLAIN -> 0.55;
            case MOOR -> 0.45;
            case FOREST -> 0.45;
            case DARK_FOREST -> 0.35;
            case HILLS -> 0.4;
            case PINES -> 0.35;
            case ALP -> 0.6;
            case ROCK -> 0.25;
            case SAND -> 0.035;
            case MESA -> 0.04;
            case OASIS -> 0.7;
            case MARSH -> 0.45;
            default -> 0;
        };
    }

    private static Material plant(int b, Random r) {
        double n = r.nextDouble();
        return switch (b) {
            case PLAIN -> pick(n, Material.SHORT_GRASS, 76, Material.DANDELION, 6, Material.POPPY, 5, Material.OXEYE_DAISY, 4,
                    Material.CORNFLOWER, 3, Material.AZURE_BLUET, 3, Material.BUSH, 3);
            case MOOR -> pick(n, Material.SHORT_GRASS, 58, Material.FERN, 30, Material.BUSH, 8, Material.ALLIUM, 4);
            case FOREST -> pick(n, Material.SHORT_GRASS, 50, Material.FERN, 16, Material.LEAF_LITTER, 10, Material.BUSH, 8,
                    Material.FIREFLY_BUSH, 6, Material.LILY_OF_THE_VALLEY, 4, Material.POPPY, 3, Material.WILDFLOWERS, 3);
            case DARK_FOREST -> pick(n, Material.FERN, 42, Material.SHORT_GRASS, 30, Material.LEAF_LITTER, 16, Material.BUSH, 12);
            case HILLS -> pick(n, Material.SHORT_GRASS, 80, Material.FERN, 8, Material.ALLIUM, 4, Material.AZURE_BLUET, 4, Material.OXEYE_DAISY, 4);
            case PINES -> pick(n, Material.FERN, 55, Material.SHORT_GRASS, 33, Material.LEAF_LITTER, 12);
            case ALP -> pick(n, Material.SHORT_GRASS, 52, Material.ALLIUM, 8, Material.CORNFLOWER, 8, Material.OXEYE_DAISY, 8,
                    Material.AZURE_BLUET, 8, Material.POPPY, 8, Material.DANDELION, 8);
            case ROCK -> n < 0.85 ? Material.SHORT_GRASS : Material.FERN;
            case SAND -> n < 0.55 ? Material.DEAD_BUSH : Material.CACTUS;
            case MESA -> Material.DEAD_BUSH;
            case OASIS -> pick(n, Material.SHORT_GRASS, 68, Material.FERN, 16, Material.POPPY, 8, Material.BLUE_ORCHID, 8);
            case MARSH -> pick(n, Material.SHORT_GRASS, 62, Material.FERN, 20, Material.BLUE_ORCHID, 12, Material.BUSH, 6);
            default -> null;
        };
    }

    private static boolean soil(Material m) {
        return m == Material.GRASS_BLOCK || m == Material.PODZOL || m == Material.MOSS_BLOCK || m == Material.COARSE_DIRT
                || m == Material.DIRT || m == Material.ROOTED_DIRT || m == Material.MUD;
    }

    private static boolean sandy(Material m) {
        return m == Material.SAND || m == Material.RED_SAND || m.name().endsWith("TERRACOTTA");
    }

    /** Arbres, herbes, fleurs, cactus et nénuphars. */
    private final class Decor extends BlockPopulator {

        @Override
        public void populate(WorldInfo info, Random random, int cx, int cz, LimitedRegion region) {
            int x0 = cx << 4, z0 = cz << 4;
            boolean roads = data.chunkHasRoad(cx, cz);
            ContinentData.RoadHit hit = new ContinentData.RoadHit();
            Column c = new Column();
            for (int i = 0; i < 16; i++) {
                int x = x0 + random.nextInt(16), z = z0 + random.nextInt(16);
                double roll = random.nextDouble() * 16;
                column(x, z, roads, hit, c);
                if (c.road || c.fluid != NONE || roll >= treeDensity(c.biome)) {
                    continue;
                }
                if ((roads && data.road(x, z, ContinentData.ROAD_MARGIN, hit)) || data.reserved(x, z)) {
                    continue;
                }
                TreeType type = tree(c.biome, random);
                if (type == null || !region.isInRegion(x, c.top + 1, z) || !soil(region.getType(x, c.top, z))) {
                    continue;
                }
                region.generateTree(new Location(null, x, c.top + 1, z), random, type);
            }
            for (int i = 0; i < 48; i++) {
                int x = x0 + random.nextInt(16), z = z0 + random.nextInt(16);
                double roll = random.nextDouble();
                column(x, z, roads, hit, c);
                if (c.road) {
                    continue;
                }
                if (c.fluid != NONE) {
                    if (c.biome == MARSH && !c.lava && roll < 0.1 && region.isInRegion(x, c.fluid + 1, z)
                            && region.getType(x, c.fluid, z) == Material.WATER && region.getType(x, c.fluid + 1, z).isAir()) {
                        region.setType(x, c.fluid + 1, z, Material.LILY_PAD);
                    }
                    continue;
                }
                if (roll >= plantChance(c.biome)) {
                    continue;
                }
                Material plant = plant(c.biome, random);
                int y = c.top;
                if (plant == null || !region.isInRegion(x, y + 1, z) || !region.getType(x, y + 1, z).isAir()) {
                    continue;
                }
                Material ground = region.getType(x, y, z);
                if (plant == Material.CACTUS || plant == Material.DEAD_BUSH) {
                    if (!sandy(ground)) {
                        continue;
                    }
                    if (plant == Material.CACTUS) {
                        if (ground != Material.SAND || !lonely(region, x, y + 1, z)) {
                            continue;
                        }
                        int h = 1 + random.nextInt(3);
                        for (int k = 1; k <= h && region.isInRegion(x, y + k, z); k++) {
                            region.setType(x, y + k, z, Material.CACTUS);
                        }
                        continue;
                    }
                } else if (ground != Material.GRASS_BLOCK && ground != Material.PODZOL && ground != Material.MOSS_BLOCK) {
                    continue;
                }
                region.setType(x, y + 1, z, plant);
            }
        }

        /** Un cactus casse s'il touche un bloc sur le côté. */
        private boolean lonely(LimitedRegion region, int x, int y, int z) {
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                for (int k = 0; k < 3; k++) {
                    if (!region.isInRegion(x + d[0], y + k, z + d[1]) || !region.getType(x + d[0], y + k, z + d[1]).isAir()) {
                        return false;
                    }
                }
            }
            return true;
        }
    }
}
