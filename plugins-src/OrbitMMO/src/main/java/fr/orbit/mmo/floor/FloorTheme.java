package fr.orbit.mmo.floor;

import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.Biome;

import java.util.Random;

/** Apparence d'un étage flottant. */
public enum FloorTheme {
    PLAINS("Plaines", Material.GRASS_BLOCK, Material.DIRT, Material.STONE, Material.DIRT_PATH, Material.GRASS_BLOCK,
            Biome.PLAINS, 0.2, 10, new Material[]{Material.SHORT_GRASS, Material.SHORT_GRASS, Material.DANDELION, Material.POPPY},
            TreeType.TREE),
    FOREST("Forêt", Material.GRASS_BLOCK, Material.DIRT, Material.STONE, Material.DIRT_PATH, Material.OAK_SAPLING,
            Biome.FOREST, 2.2, 8, new Material[]{Material.SHORT_GRASS, Material.FERN, Material.LILY_OF_THE_VALLEY},
            TreeType.TREE, TreeType.BIRCH),
    DARK_FOREST("Forêt sombre", Material.GRASS_BLOCK, Material.DIRT, Material.DEEPSLATE, Material.DIRT_PATH, Material.DARK_OAK_SAPLING,
            Biome.DARK_FOREST, 1.4, 6, new Material[]{Material.FERN, Material.SHORT_GRASS, Material.BROWN_MUSHROOM},
            TreeType.DARK_OAK, TreeType.TREE),
    DESERT("Désert", Material.SAND, Material.SANDSTONE, Material.SANDSTONE, Material.SMOOTH_SANDSTONE, Material.SAND,
            Biome.DESERT, 0, 3, new Material[]{Material.DEAD_BUSH, Material.CACTUS}),
    SNOW("Toundra", Material.SNOW_BLOCK, Material.DIRT, Material.STONE, Material.PACKED_ICE, Material.SNOW_BLOCK,
            Biome.SNOWY_TAIGA, 0.9, 0, new Material[0], TreeType.REDWOOD);

    public final String label;
    public final Material top;
    public final Material filler;
    public final Material stone;
    public final Material path;
    public final Material icon;
    public final Biome biome;
    public final double trees;
    public final int plants;
    private final Material[] plantTypes;
    private final TreeType[] treeTypes;

    FloorTheme(String label, Material top, Material filler, Material stone, Material path, Material icon, Biome biome,
               double trees, int plants, Material[] plantTypes, TreeType... treeTypes) {
        this.label = label;
        this.top = top;
        this.filler = filler;
        this.stone = stone;
        this.path = path;
        this.icon = icon;
        this.biome = biome;
        this.trees = trees;
        this.plants = plants;
        this.plantTypes = plantTypes;
        this.treeTypes = treeTypes;
    }

    public TreeType tree(Random random) {
        return treeTypes.length == 0 ? null : treeTypes[random.nextInt(treeTypes.length)];
    }

    public Material plant(Random random) {
        return plantTypes.length == 0 ? null : plantTypes[random.nextInt(plantTypes.length)];
    }
}
