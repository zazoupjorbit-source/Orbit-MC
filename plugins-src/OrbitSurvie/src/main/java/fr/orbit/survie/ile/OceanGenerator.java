package fr.orbit.survie.ile;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Monde de l'île : un océan tropical plat et sans fin (fond de sable à 42, mer jusqu'à 62), sans relief,
 * sans structures ni créatures. L'île elle-même est posée par {@link IleService}.
 */
public final class OceanGenerator extends ChunkGenerator {

    public static final int SEA = 62;
    public static final int SAND_TOP = 42;
    public static final int SAND_BOTTOM = 40;

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        int min = info.getMinHeight();
        data.setRegion(0, min, 0, 16, min + 1, 16, Material.BEDROCK);
        data.setRegion(0, min + 1, 0, 16, SAND_BOTTOM, 16, Material.STONE);
        data.setRegion(0, SAND_BOTTOM, 0, 16, SAND_TOP + 1, 16, Material.SAND);
        data.setRegion(0, SAND_TOP + 1, 0, 16, SEA + 1, 16, Material.WATER);
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, IleDesign.G + 1, 6.5, 180f, 0f);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                return Biome.WARM_OCEAN;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo info) {
                return List.of(Biome.WARM_OCEAN);
            }
        };
    }
}
