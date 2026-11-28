package fr.orbit.survie.ile;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Monde du port orbital : le vide (aucun bloc, aucune structure, aucune créature). La station et la planète
 * sous elle sont posées par {@link IleService} d'après {@link OrbitalDesign}.
 */
public final class VideGenerator extends ChunkGenerator {

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, OrbitalDesign.G + 1, OrbitalDesign.SPAWN_Z + 0.5, 180f, 0f);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                return Biome.THE_VOID;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo info) {
                return List.of(Biome.THE_VOID);
            }
        };
    }
}
