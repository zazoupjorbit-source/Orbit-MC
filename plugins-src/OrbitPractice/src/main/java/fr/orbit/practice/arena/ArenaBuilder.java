package fr.orbit.practice.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Construit par code le monde du Practice : un hub (« le Vestiaire ») à l'origine et des arènes rondes
 * fermées, alignées tous les 200 blocs. Quatre thèmes, tous aux mêmes dimensions pour que le combat
 * soit le même partout. Ne construit que dans le monde vide créé ici.
 */
public final class ArenaBuilder {

    /** Palette d'une arène. */
    public record Theme(String id, String label, Material floorA, Material floorB, Material wall,
                        Material accent, Material light, Material glass) {}

    public static final List<Theme> THEMES = List.of(
            new Theme("hangar", "Hangar", Material.SMOOTH_STONE, Material.LIGHT_GRAY_CONCRETE,
                    Material.POLISHED_DEEPSLATE, Material.BLUE_CONCRETE, Material.SEA_LANTERN,
                    Material.LIGHT_BLUE_STAINED_GLASS),
            new Theme("holo", "Arène holo", Material.QUARTZ_BLOCK, Material.WHITE_CONCRETE,
                    Material.SMOOTH_QUARTZ, Material.LIGHT_BLUE_CONCRETE, Material.SEA_LANTERN,
                    Material.CYAN_STAINED_GLASS),
            new Theme("forge", "Forge", Material.BLACKSTONE, Material.POLISHED_BLACKSTONE,
                    Material.POLISHED_BLACKSTONE_BRICKS, Material.GILDED_BLACKSTONE, Material.SHROOMLIGHT,
                    Material.ORANGE_STAINED_GLASS),
            new Theme("serre", "Serre", Material.MOSS_BLOCK, Material.GRASS_BLOCK,
                    Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.OCHRE_FROGLIGHT,
                    Material.LIME_STAINED_GLASS));

    public static final int Y = 100;
    public static final int RADIUS = 22;
    private static final int SPACING = 200;
    private static final int WALL_HEIGHT = 6;
    private static final int CEILING = 24;
    private static final int HUB_RADIUS = 12;

    private final String worldName;

    public ArenaBuilder(String worldName) {
        this.worldName = worldName;
    }

    public String worldName() {
        return worldName;
    }

    /** Monde vide du Practice : chargé s'il existe déjà sur le disque, créé sinon. */
    public World world() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = new WorldCreator(worldName).generator(new VoidGenerator()).generateStructures(false).createWorld();
            if (world != null) {
                world.setSpawnLocation(0, Y + 1, 0);
                world.setTime(6000);
            }
        }
        return world;
    }

    public Location hubSpawn() {
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, 0.5, Y + 1, 0.5, 0f, 0f);
    }

    // ------------------------------------------------------------------ Hub

    /** Le Vestiaire : plateau rond sous une verrière, à l'origine du monde. */
    public void buildHub() {
        World w = world();
        int r = HUB_RADIUS;
        for (int dx = -r - 2; dx <= r + 2; dx++) {
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 1.5) {
                    continue;
                }
                Material floor;
                if (d <= 1.5) {
                    floor = Material.SEA_LANTERN;
                } else if (d <= 3.5) {
                    floor = Material.GOLD_BLOCK;
                } else if (d > r - 1.5) {
                    floor = Material.BLUE_CONCRETE;
                } else if (Math.floorMod(dx, 5) == 0 && Math.floorMod(dz, 5) == 0) {
                    floor = Material.SEA_LANTERN;
                } else {
                    floor = ((int) (d / 3)) % 2 == 0 ? Material.SMOOTH_QUARTZ : Material.LIGHT_GRAY_CONCRETE;
                }
                set(w, dx, Y, dz, floor);
                set(w, dx, Y - 1, dz, Material.POLISHED_DEEPSLATE);
                // Verrière : demi-sphère de verre posée sur un muret.
                for (int h = 1; h <= r + 2; h++) {
                    double d3 = Math.sqrt(dx * dx + dz * dz + h * h);
                    Material m = Material.AIR;
                    if (d3 > r + 0.5 && d3 <= r + 1.5) {
                        m = h <= 1 ? Material.POLISHED_DEEPSLATE
                                : (Math.floorMod(dx, 6) == 0 || Math.floorMod(dz, 6) == 0)
                                        ? Material.BLUE_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS;
                    } else if (d3 > r + 1.5) {
                        continue;
                    }
                    set(w, dx, Y + h, dz, m);
                }
            }
        }
        w.setSpawnLocation(0, Y + 1, 0);
    }

    // ------------------------------------------------------------------ Arènes

    /**
     * Construit les arènes manquantes : {@code copies} par thème. Une arène déjà enregistrée n'est
     * reconstruite que si {@code force} est vrai (les retouches faites à la main en jeu seraient perdues).
     * Renvoie les noms des arènes construites.
     */
    public List<String> generate(ArenaStore store, int copies, boolean force) {
        World w = world();
        List<String> built = new ArrayList<>();
        int index = 0;
        for (int copy = 1; copy <= copies; copy++) {
            for (Theme theme : THEMES) {
                index++;
                String name = theme.id() + "-" + copy;
                if (store.get(name) != null && !force) {
                    continue;
                }
                int cx = index * SPACING;
                build(w, theme, cx, 0);
                int reach = RADIUS - 6;
                store.put(new Arena(name, theme.id(), worldName,
                        cx - reach + 0.5, Y + 1, 0.5, -90f,
                        cx + reach + 0.5, Y + 1, 0.5, 90f, RADIUS));
                built.add(name);
            }
        }
        if (!built.isEmpty()) {
            store.save();
        }
        return built;
    }

    // ------------------------------------------------------------------ Sumo

    public static final String SUMO_THEME = "sumo";
    public static final int SUMO_RADIUS = 7;
    private static final int SUMO_ROW_Z = 400;

    /**
     * Arènes de Sumo : une plateforme ronde de 15 blocs au-dessus d'un bassin (tomber à l'eau = éliminé).
     * Alignées sur une deuxième rangée (z = 400). Même règle que les autres : seulement les manquantes.
     */
    public List<String> generateSumo(ArenaStore store, int count, boolean force) {
        World w = world();
        List<String> built = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String name = SUMO_THEME + "-" + i;
            if (store.get(name) != null && !force) {
                continue;
            }
            Theme t = THEMES.get((i - 1) % THEMES.size());
            int cx = i * SPACING;
            buildSumo(w, t, cx, SUMO_ROW_Z);
            store.put(new Arena(name, SUMO_THEME, worldName,
                    cx - 3 + 0.5, Y + 1, SUMO_ROW_Z + 0.5, -90f,
                    cx + 3 + 0.5, Y + 1, SUMO_ROW_Z + 0.5, 90f, SUMO_RADIUS));
            built.add(name);
        }
        if (!built.isEmpty()) {
            store.save();
        }
        return built;
    }

    private void buildSumo(World w, Theme t, int cx, int cz) {
        int pool = SUMO_RADIUS + 9;
        for (int dx = -pool - 2; dx <= pool + 2; dx++) {
            for (int dz = -pool - 2; dz <= pool + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx, z = cz + dz;
                if (d > pool + 1.5) {
                    continue;
                }
                boolean rim = d > pool + 0.5;
                // Bassin : fond, deux blocs d'eau, rebord ; paroi invisible tout autour.
                set(w, x, Y - 7, z, t.wall());
                set(w, x, Y - 6, z, rim ? t.wall() : Material.WATER);
                set(w, x, Y - 5, z, rim ? t.accent() : Material.WATER);
                for (int h = -4; h <= 12; h++) {
                    set(w, x, Y + h, z, rim ? Material.BARRIER : Material.AIR);
                }
                if (d <= SUMO_RADIUS + 0.5) {
                    Material top;
                    if (d > SUMO_RADIUS - 0.5) {
                        top = t.accent();
                    } else if (dz == 0 && Math.abs(dx) == 3) {
                        top = t.light(); // plots de départ
                    } else {
                        top = ((int) d) % 2 == 0 ? t.floorA() : t.floorB();
                    }
                    set(w, x, Y, z, top);
                    set(w, x, Y - 1, z, t.wall());
                }
            }
        }
        // Pilier sous la plateforme, jusqu'au fond du bassin.
        for (int y = Y - 6; y < Y - 1; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set(w, cx + dx, y, cz + dz, t.wall());
                }
            }
        }
    }

    private void build(World w, Theme t, int cx, int cz) {
        int r = RADIUS;
        for (int dx = -r - 3; dx <= r + 3; dx++) {
            for (int dz = -r - 3; dz <= r + 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx, z = cz + dz;
                if (d <= r + 0.5) {
                    set(w, x, Y, z, floor(t, dx, dz, d, r));
                    set(w, x, Y - 1, z, t.wall());
                    for (int h = 1; h < CEILING; h++) {
                        set(w, x, Y + h, z, Material.AIR);
                    }
                    set(w, x, Y + CEILING, z, Material.BARRIER);
                } else if (d <= r + 2.5) {
                    // Mur d'enceinte : 6 blocs visibles, puis une paroi invisible jusqu'au plafond.
                    boolean window = ((int) Math.floor((Math.atan2(dz, dx) + Math.PI) / (2 * Math.PI) * 24)) % 2 == 0;
                    set(w, x, Y, z, t.wall());
                    set(w, x, Y - 1, z, t.wall());
                    for (int h = 1; h <= CEILING; h++) {
                        Material m;
                        if (h > WALL_HEIGHT) {
                            m = Material.BARRIER;
                        } else if (h == WALL_HEIGHT) {
                            m = t.accent();
                        } else if (window && h >= 2 && h <= 4) {
                            m = t.glass();
                        } else {
                            m = t.wall();
                        }
                        set(w, x, Y + h, z, m);
                    }
                }
            }
        }
        // Quatre piliers lumineux contre le mur, en diagonale : repères visuels, hors de la zone de combat.
        int p = (int) Math.round((r - 2) * Math.sqrt(0.5));
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int h = 1; h <= 4; h++) {
                    set(w, cx + sx * p, Y + h, cz + sz * p, t.wall());
                }
                set(w, cx + sx * p, Y + 5, cz + sz * p, t.light());
            }
        }
    }

    private static Material floor(Theme t, int dx, int dz, double d, int r) {
        int reach = r - 6;
        // Plots de départ : un carré 3x3 marqué de chaque côté.
        if (Math.abs(dz) <= 1 && (Math.abs(dx - reach) <= 1 || Math.abs(dx + reach) <= 1)) {
            return dz == 0 && Math.abs(Math.abs(dx) - reach) == 0 ? t.light() : t.accent();
        }
        if (d <= 1.5 || d > r - 1.5) {
            return t.accent();
        }
        if (Math.floorMod(dx, 7) == 0 && Math.floorMod(dz, 7) == 0) {
            return t.light();
        }
        return ((int) (d / 4)) % 2 == 0 ? t.floorA() : t.floorB();
    }

    private static void set(World w, int x, int y, int z, Material m) {
        var block = w.getBlockAt(x, y, z);
        if (block.getType() != m) {
            block.setType(m, false);
        }
    }

    /** Monde entièrement vide, un seul biome : ni relief, ni créatures, ni pluie. */
    public static final class VoidGenerator extends ChunkGenerator {

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, Y + 1, 0.5);
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
}
