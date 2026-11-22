package fr.orbit.reborn.floor;

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
import org.bukkit.util.noise.SimplexNoiseGenerator;

import java.util.List;
import java.util.Random;

/**
 * Un étage = un disque de terre flottant dans le vide (dessous en forme de lentille).
 * Le centre (la ville) et la salle du boss sont aplatis ; le reste est vallonné.
 */
public final class FloorGenerator extends ChunkGenerator {

    public static final int SURFACE = 70;

    private final FloorTheme theme;
    private final int radius;
    private final int arenaX;
    private final SimplexNoiseGenerator noise;

    public FloorGenerator(FloorTheme theme, int radius, int arenaX, long seed) {
        this.theme = theme;
        this.radius = radius;
        this.arenaX = arenaX;
        this.noise = new SimplexNoiseGenerator(seed);
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int cx, int cz, ChunkData data) {
        int minY = data.getMinHeight();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = (cx << 4) + x;
                int wz = (cz << 4) + z;
                double d = Math.sqrt((double) wx * wx + (double) wz * wz);
                if (d > radius) {
                    continue;
                }
                int top = height(wx, wz, d);
                int bottom = Math.max(minY + 1, bottom(wx, wz, d));
                for (int y = bottom; y <= top; y++) {
                    Material m;
                    if (y == top) {
                        m = theme.top;
                    } else if (y >= top - 3) {
                        m = theme.filler;
                    } else {
                        m = theme.stone;
                    }
                    data.setBlock(x, y, z, m);
                }
            }
        }
    }

    private int height(int wx, int wz, double d) {
        double town = smooth((d - 30) / 18.0);
        double da = Math.sqrt(Math.pow(wx - arenaX, 2) + (double) wz * wz);
        double arena = smooth((da - 24) / 14.0);
        double path = (wx > 0 && wx < arenaX) ? smooth((Math.abs(wz) - 3) / 6.0) : 1;
        double v = noise.noise(wx * 0.011, wz * 0.011) * 7 + noise.noise(wx * 0.045, wz * 0.045) * 2.2;
        if (v < 0) {
            v *= 0.5;
        }
        double rim = d > radius - 10 ? (d - (radius - 10)) * 0.4 : 0;
        return SURFACE + (int) Math.round(v * town * arena * path - rim);
    }

    private int bottom(int wx, int wz, double d) {
        double k = Math.sqrt(Math.max(0, 1 - (d / radius) * (d / radius)));
        return SURFACE - 4 - (int) (34 * k) + (int) Math.round(noise.noise(wx * 0.07, wz * 0.07, 3.3) * 3);
    }

    private static double smooth(double t) {
        if (t <= 0) {
            return 0;
        }
        if (t >= 1) {
            return 1;
        }
        return t * t * (3 - 2 * t);
    }

    /** Zones sans arbres : ville, salle du boss, chemin, bord. */
    private boolean clear(int x, int z) {
        double d = Math.sqrt((double) x * x + (double) z * z);
        if (d < 36 || d > radius - 6) {
            return false;
        }
        if (Math.sqrt(Math.pow(x - arenaX, 2) + (double) z * z) < 27) {
            return false;
        }
        return !(x > 0 && x < arenaX && Math.abs(z) < 5);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                return theme.biome;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(theme.biome);
            }
        };
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of(new Decorator());
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, SURFACE + 1, 5.5);
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

    /** Arbres et végétation. */
    private final class Decorator extends BlockPopulator {

        @Override
        public void populate(WorldInfo info, Random random, int cx, int cz, LimitedRegion region) {
            int trees = (int) theme.trees + (random.nextDouble() < theme.trees % 1 ? 1 : 0);
            for (int i = 0; i < trees; i++) {
                int x = (cx << 4) + random.nextInt(16);
                int z = (cz << 4) + random.nextInt(16);
                if (!clear(x, z)) {
                    continue;
                }
                int y = surface(region, x, z);
                TreeType type = theme.tree(random);
                if (y < 0 || type == null || region.getType(x, y, z) != theme.top) {
                    continue;
                }
                region.generateTree(new Location(null, x, y + 1, z), random, type);
            }
            for (int i = 0; i < theme.plants; i++) {
                int x = (cx << 4) + random.nextInt(16);
                int z = (cz << 4) + random.nextInt(16);
                double d = Math.sqrt((double) x * x + (double) z * z);
                if (d < 31 || d > radius - 2 || Math.sqrt(Math.pow(x - arenaX, 2) + (double) z * z) < 23
                        || (x > 0 && x < arenaX && Math.abs(z) < 3)) {
                    continue;
                }
                int y = surface(region, x, z);
                Material plant = theme.plant(random);
                if (y < 0 || plant == null || region.getType(x, y, z) != theme.top || !region.getType(x, y + 1, z).isAir()) {
                    continue;
                }
                if (plant == Material.CACTUS) {
                    int h = 1 + random.nextInt(3);
                    for (int k = 1; k <= h; k++) {
                        region.setType(x, y + k, z, Material.CACTUS);
                    }
                } else {
                    region.setType(x, y + 1, z, plant);
                }
            }
        }

        private int surface(LimitedRegion region, int x, int z) {
            if (!region.isInRegion(x, SURFACE, z)) {
                return -1;
            }
            for (int y = SURFACE + 16; y > SURFACE - 16; y--) {
                if (!region.getType(x, y, z).isAir()) {
                    return y;
                }
            }
            return -1;
        }
    }
}
