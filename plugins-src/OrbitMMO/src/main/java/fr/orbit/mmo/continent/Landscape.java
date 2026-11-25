package fr.orbit.mmo.continent;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static fr.orbit.mmo.continent.ContinentData.ALP;
import static fr.orbit.mmo.continent.ContinentData.ASH;
import static fr.orbit.mmo.continent.ContinentData.DARK_FOREST;
import static fr.orbit.mmo.continent.ContinentData.FOREST;
import static fr.orbit.mmo.continent.ContinentData.HILLS;
import static fr.orbit.mmo.continent.ContinentData.MARSH;
import static fr.orbit.mmo.continent.ContinentData.MESA;
import static fr.orbit.mmo.continent.ContinentData.MOOR;
import static fr.orbit.mmo.continent.ContinentData.OASIS;
import static fr.orbit.mmo.continent.ContinentData.PINES;
import static fr.orbit.mmo.continent.ContinentData.PLAIN;
import static fr.orbit.mmo.continent.ContinentData.ROCK;
import static fr.orbit.mmo.continent.ContinentData.SAND;
import static fr.orbit.mmo.continent.ContinentData.SNOW;
import static fr.orbit.mmo.continent.ContinentData.VOLCANIC;
import static fr.orbit.mmo.continent.ContinentGenerator.NONE;
import static fr.orbit.mmo.continent.ContinentGenerator.rnd;

/**
 * Paysage du continent : la vie semée entre les sites, sans nom ni monstre — bosquets, arbres isolés, buissons,
 * rochers, troncs couchés, herbes et taches de fleurs, étangs et lacs, et des « petits lieux » : maisons abandonnées
 * ou en ruine, tours de guet en bois, tours de pierre écroulées, champs avec épouvantail, meules, enclos, feux de camp,
 * tombes, vieux murs, charrettes, cairns, ossements, cabanes sur pilotis, orgues de basalte.
 *
 * Tout est tiré du hasard stable de la position : un chunk traité deux fois reçoit les mêmes blocs, et un objet à
 * cheval sur plusieurs chunks est posé morceau par morceau. Ne pose que dans l'air, les herbes et les feuilles (et
 * creuse le sol naturel pour les étangs) : routes, sites, capitales et points de quête sont tenus à l'écart, et rien
 * de ce qu'un site ou un joueur a construit n'est remplacé.
 */
final class Landscape {

    /** Version du dessin : changer le tirage demande de refaire la passe sur un monde neuf. */
    static final int VERSION = 1;

    private static final int CELL = 56, LAKE_CELL = 320, BUCKET = 256;
    /** Portée maximale d'un petit lieu, d'un lac, autour de son centre. */
    private static final int MAX_R = 12, MAX_LAKE = 30;
    private static final int TREE_CELL = 6, BUSH_CELL = 5, ROCK_CELL = 17, LOG_CELL = 23;

    private static final int SOFT = 0, EARTH = 1, CLEAR = 2, DIG = 3, WET = 4, POST = 5, COVER = 6;

    /** Zone à laisser libre : rayon {@code r} pour les petits lieux, {@code c} pour la végétation (0 = aucune limite). */
    record Avoid(int x, int z, int r, int c) {
    }

    /**
     * Un petit lieu ou un lac.
     *
     * @param r     rayon de dessin
     * @param reach rayon de l'emprise (tout ce qui est posé tient dedans)
     * @param base  sol le plus bas de l'emprise
     * @param level sol le plus haut de l'emprise
     */
    record Feature(String kind, int x, int z, int r, int reach, int rot, String style, int seed, int base, int level) {
    }

    private static final Feature NOTHING = new Feature("", 0, 0, 0, 0, 0, "", 0, 0, 0);

    private record Pal(String post, String postX, String postZ, String planks, String stairs, String slab, String fence,
                       String gate, String roof, String roofSlab, String stone, String stone2, String brick, String crack,
                       String brickSlab, String wall, String trapdoor, String door, String wool) {
    }

    private final ContinentData data;
    private final ContinentGenerator gen;
    private final Map<String, String> regionStyle = new HashMap<>();
    private final Map<Long, List<Avoid>> avoid = new HashMap<>();
    private final Map<Long, Feature> features = lru(60_000);
    private final Map<Long, Feature> lakes = lru(4_000);
    private final Map<String, BlockData> parsed = new HashMap<>();
    private final ContinentGenerator.Column col = new ContinentGenerator.Column();
    private final ContinentData.RoadHit hit = new ContinentData.RoadHit();
    private final int half;
    /** Ce qui a été posé (centres dans les chunks traités) : genre → nombre. */
    final Map<String, Integer> counts = new TreeMap<>();

    private static final Set<Material> NATURAL = EnumSet.noneOf(Material.class);
    private static final Set<Material> PLANTS = EnumSet.noneOf(Material.class);
    private static final Set<Material> GRASSY = EnumSet.of(Material.GRASS_BLOCK, Material.PODZOL, Material.MOSS_BLOCK,
            Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.MUD, Material.PALE_MOSS_BLOCK);
    private static final Set<Material> DOUBLE = EnumSet.of(Material.TALL_GRASS, Material.LARGE_FERN, Material.SUNFLOWER,
            Material.LILAC, Material.ROSE_BUSH, Material.PEONY, Material.PITCHER_PLANT);

    static {
        for (Material m : new Material[]{Material.ANDESITE, Material.BASALT, Material.BLACKSTONE, Material.CLAY, Material.COARSE_DIRT,
                Material.DIRT, Material.GRASS_BLOCK, Material.GRAVEL, Material.GRAY_CONCRETE_POWDER, Material.LIGHT_GRAY_CONCRETE_POWDER,
                Material.MAGMA_BLOCK, Material.MOSS_BLOCK, Material.MUD, Material.PACKED_MUD, Material.PODZOL, Material.RED_SAND,
                Material.SAND, Material.SANDSTONE, Material.SMOOTH_BASALT, Material.SNOW_BLOCK, Material.STONE, Material.TUFF,
                Material.ROOTED_DIRT, Material.DEEPSLATE, Material.CALCITE, Material.DIORITE, Material.GRANITE, Material.ICE,
                Material.PACKED_ICE, Material.POWDER_SNOW, Material.MYCELIUM, Material.PALE_MOSS_BLOCK, Material.RED_SANDSTONE,
                Material.DRIPSTONE_BLOCK}) {
            NATURAL.add(m);
        }
        for (Material m : Material.values()) {
            if (m.isBlock() && m.name().endsWith("TERRACOTTA") && !m.name().contains("GLAZED")) {
                NATURAL.add(m);
            }
        }
        for (Material m : new Material[]{Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN, Material.LARGE_FERN,
                Material.DEAD_BUSH, Material.BUSH, Material.FIREFLY_BUSH, Material.LEAF_LITTER, Material.WILDFLOWERS,
                Material.PINK_PETALS, Material.SHORT_DRY_GRASS, Material.TALL_DRY_GRASS, Material.SWEET_BERRY_BUSH,
                Material.SUNFLOWER, Material.LILAC, Material.ROSE_BUSH, Material.PEONY, Material.PITCHER_PLANT,
                Material.BROWN_MUSHROOM, Material.RED_MUSHROOM}) {
            PLANTS.add(m);
        }
        PLANTS.addAll(Tag.SMALL_FLOWERS.getValues());
    }

    private static <K, V> Map<K, V> lru(int max) {
        return new LinkedHashMap<>(1024, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> e) {
                return size() > max;
            }
        };
    }

    Landscape(ContinentData data, ContinentGenerator gen, SiteCatalog catalog, List<int[]> questPoints) {
        this.data = data;
        this.gen = gen;
        this.half = data.size / 2;
        if (catalog != null) {
            for (SiteCatalog.RegionRule r : catalog.regions.values()) {
                regionStyle.put(r.id(), r.style());
            }
            // La végétation s'arrête juste au bord de l'emprise d'un site (au-delà, « ecarts » verrait des écarts) ;
            // les petits lieux restent plus loin.
            for (SiteDesign.Site s : catalog.sites.values()) {
                addAvoid(s.x(), s.z(), s.outer() + (s.kind().equals("village") ? 26 : 14), s.outer() + 3);
            }
        }
        for (ContinentData.Place p : data.places) {
            if (p.kind().equals("capitale")) {
                addAvoid(p.x(), p.z(), 168, 155);
            } else if (p.safe() > 0) {
                addAvoid(p.x(), p.z(), p.safe() + 24, 0);
            }
        }
        for (int[] q : questPoints) {
            addAvoid(q[0], q[1], 9, 3);
        }
    }

    // ================================================================== Où poser
    private static long key(int a, int b) {
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }

    private void addAvoid(int x, int z, int r, int c) {
        Avoid a = new Avoid(x, z, r, c);
        int reach = r + MAX_LAKE + 8;
        for (int bx = Math.floorDiv(x - reach, BUCKET); bx <= Math.floorDiv(x + reach, BUCKET); bx++) {
            for (int bz = Math.floorDiv(z - reach, BUCKET); bz <= Math.floorDiv(z + reach, BUCKET); bz++) {
                avoid.computeIfAbsent(key(bx, bz), k -> new ArrayList<>()).add(a);
            }
        }
    }

    /** Vrai si un disque de rayon {@code rad} touche un site, une capitale, un point de quête ou le bord de la carte. */
    private boolean blocked(int x, int z, int rad) {
        if (Math.abs(x) > half - 48 - rad || Math.abs(z) > half - 48 - rad) {
            return true;
        }
        List<Avoid> list = avoid.get(key(Math.floorDiv(x, BUCKET), Math.floorDiv(z, BUCKET)));
        if (list != null) {
            for (Avoid a : list) {
                double dx = x - a.x(), dz = z - a.z(), lim = a.r() + rad;
                if (dx * dx + dz * dz < lim * lim) {
                    return true;
                }
            }
        }
        return data.reserved(x, z);
    }

    /** Comme {@link #blocked}, avec la marge serrée de la végétation (arbres, buissons, rochers, troncs). */
    private boolean crowded(int x, int z, int rad) {
        if (Math.abs(x) > half - 40 - rad || Math.abs(z) > half - 40 - rad) {
            return true;
        }
        List<Avoid> list = avoid.get(key(Math.floorDiv(x, BUCKET), Math.floorDiv(z, BUCKET)));
        if (list != null) {
            for (Avoid a : list) {
                double dx = x - a.x(), dz = z - a.z(), lim = a.c() + rad;
                if (a.c() > 0 && dx * dx + dz * dz < lim * lim) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean nearRoad(int x, int z, int rad) {
        if (data.road(x, z, 5, hit)) {
            return true;
        }
        int n = Math.max(8, rad + 2);
        for (double f : new double[]{0.4, 0.75, 1.0}) {
            for (int k = 0; k < n; k++) {
                double a = k * 2 * Math.PI / n;
                int px = x + (int) Math.round(Math.cos(a) * rad * f), pz = z + (int) Math.round(Math.sin(a) * rad * f);
                if (data.road(px, pz, 4, hit)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int ground(int x, int z) {
        gen.column(x, z, false, hit, col);
        return col.top;
    }

    /** Sol le plus bas, le plus haut, colonnes mouillées (+1000 par colonne de lave), eau la plus profonde. */
    private int[] survey(int x, int z, int r) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, wet = 0, deep = 0;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dz * dz > r * r + r) {
                    continue;
                }
                gen.column(x + dx, z + dz, false, hit, col);
                lo = Math.min(lo, col.top);
                hi = Math.max(hi, col.top);
                if (col.fluid != NONE) {
                    wet += col.lava ? 1000 : 1;
                    deep = Math.max(deep, col.fluid - col.top);
                }
            }
        }
        return new int[]{lo, hi, wet, deep};
    }

    String style(int x, int z) {
        ContinentData.Region r = data.region(x, z);
        if (r == null) {
            return "neutre";
        }
        String s = regionStyle.get(r.id());
        if (s != null) {
            return s;
        }
        return switch (r.faction()) {
            case "lumiere" -> "lumiere";
            case "ombre" -> "ombre";
            default -> "neutre";
        };
    }

    /** Le biome l'emporte sur le style de la région : on bâtit en grès au désert, en pierre noire aux cendres… */
    private static String look(String style, int biome) {
        return switch (biome) {
            case SAND, MESA -> "desert";
            case SNOW -> "givre";
            case ASH, VOLCANIC -> "cendre";
            case MARSH -> "marais";
            default -> style;
        };
    }

    // ================================================================== Tirage des petits lieux et des lacs
    private static double structureChance(int b) {
        return switch (b) {
            case PLAIN -> 0.8;
            case MOOR -> 0.65;
            case HILLS -> 0.6;
            case FOREST -> 0.45;
            case DARK_FOREST, PINES -> 0.4;
            case ALP -> 0.45;
            case ROCK, SNOW -> 0.3;
            case SAND -> 0.5;
            case MESA -> 0.45;
            case OASIS -> 0.65;
            case MARSH -> 0.7;
            case ASH -> 0.55;
            case VOLCANIC -> 0.4;
            default -> 0;
        };
    }

    private static Object[] pool(String look, int b) {
        boolean farm = b == PLAIN || b == MOOR || b == HILLS || b == ALP || b == OASIS;
        int f = farm ? 1 : 0;
        return switch (look) {
            case "desert" -> new Object[]{"ruine", 10, "tour_ruine", 8, "mur", 8, "os", 8, "feu", 5, "cairn", 6, "charrette", 4,
                    "tombes", 3, "etang", b == OASIS ? 8 : 0};
            case "givre" -> new Object[]{"maison", 8, "ruine", 6, "tour_guet", 6, "tour_ruine", 5, "feu", 5, "cairn", 8, "os", 3,
                    "etang", 6, "tombes", 3, "charrette", 2};
            case "cendre" -> new Object[]{"ruine", 10, "tour_ruine", 8, "mur", 8, "os", 6, "basalte", 10, "cairn", 4, "tombes", 4,
                    "charrette", 3};
            case "marais" -> new Object[]{"ruine", 6, "pilotis", 10, "etang", 14, "tombes", 4, "feu", 4, "cairn", 2, "charrette", 2,
                    "tour_guet", 3, "maison", 3};
            case "ombre" -> new Object[]{"ruine", 12, "maison", 6, "tour_guet", 5, "tour_ruine", 7, "champ", 6 * f, "etang", 7,
                    "meules", 3 * f, "enclos", 3 * f, "feu", 4, "tombes", 7, "mur", 5, "charrette", 4, "cairn", 3};
            default -> new Object[]{"ruine", 10, "maison", 8, "tour_guet", 6, "tour_ruine", 5, "champ", 12 * f, "etang", 9,
                    "meules", 6 * f, "enclos", 6 * f, "feu", 4, "tombes", 3, "mur", 4, "charrette", 4, "cairn", 3};
        };
    }

    private static String pickKind(Object[] pool, double r) {
        double total = 0;
        for (int i = 1; i < pool.length; i += 2) {
            total += ((Number) pool[i]).doubleValue();
        }
        if (total <= 0) {
            return null;
        }
        double t = r * total;
        for (int i = 0; i < pool.length; i += 2) {
            t -= ((Number) pool[i + 1]).doubleValue();
            if (t < 0) {
                return (String) pool[i];
            }
        }
        return (String) pool[pool.length - 2];
    }

    private static String pickS(double r, Object... table) {
        return pickKind(table, r);
    }

    private static int radius(String kind, double r) {
        return switch (kind) {
            case "maison", "ruine", "tour_ruine", "enclos" -> 7;
            case "tour_guet", "charrette", "basalte" -> 4;
            case "champ" -> 9;
            case "etang" -> 4 + (int) (r * 4);
            case "meules", "os" -> 6;
            case "feu", "tombes" -> 5;
            case "mur", "pilotis" -> 8;
            case "cairn" -> 2;
            default -> 6;
        };
    }

    private static int reach(String kind, int r) {
        return kind.equals("etang") || kind.equals("lac") ? (int) Math.ceil(r * 1.3) + 2 : r;
    }

    private static int tolerance(String kind) {
        return switch (kind) {
            case "champ" -> 1;
            case "etang", "enclos", "meules", "feu", "tombes" -> 2;
            case "maison", "ruine", "pilotis", "tour_guet", "charrette" -> 3;
            default -> 5;
        };
    }

    Feature feature(int i, int j) {
        long k = key(i, j);
        Feature f = features.get(k);
        if (f == null) {
            f = pickFeature(i, j);
            if (f == null) {
                f = NOTHING;
            }
            features.put(k, f);
        }
        return f == NOTHING ? null : f;
    }

    private Feature pickFeature(int i, int j) {
        int x = i * CELL + CELL / 2 + (int) ((rnd(i, j, 7101) - 0.5) * CELL * 0.6);
        int z = j * CELL + CELL / 2 + (int) ((rnd(i, j, 7102) - 0.5) * CELL * 0.6);
        if (Math.abs(x) > half - 60 || Math.abs(z) > half - 60) {
            return null;
        }
        gen.column(x, z, false, hit, col);
        int biome = col.biome;
        if ((col.fluid != NONE && biome != MARSH) || rnd(i, j, 7103) >= structureChance(biome)) {
            return null;
        }
        String look = look(style(x, z), biome);
        Object[] pool = pool(look, biome);
        if (lakeNear(x, z, MAX_R + 6)) {
            return null;
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            String kind = pickKind(pool, rnd(i, j, 7110 + attempt));
            if (kind == null) {
                return null;
            }
            int r = radius(kind, rnd(i, j, 7130 + attempt));
            int reach = reach(kind, r);
            if (blocked(x, z, reach + 3) || nearRoad(x, z, reach + 1)) {
                continue;
            }
            int[] s = survey(x, z, reach);
            boolean wetOk = kind.equals("pilotis") ? s[2] < 1000 && s[3] <= 3 : s[2] == 0;
            if (!wetOk || s[1] - s[0] > tolerance(kind)) {
                continue;
            }
            int seed = (int) (rnd(i, j, 7120 + attempt) * 1_000_000);
            int rot = (int) (rnd(i, j, 7140) * 4);
            return new Feature(kind, x, z, r, reach, rot, look, seed, s[0], s[1]);
        }
        return null;
    }

    private static double lakeChance(int b) {
        return switch (b) {
            case PLAIN, OASIS -> 0.45;
            case MOOR -> 0.4;
            case FOREST -> 0.35;
            case HILLS, DARK_FOREST, PINES, ALP -> 0.3;
            case MARSH -> 0.6;
            case SNOW -> 0.2;
            default -> 0;
        };
    }

    Feature lake(int i, int j) {
        long k = key(i, j);
        Feature f = lakes.get(k);
        if (f == null) {
            f = pickLake(i, j);
            if (f == null) {
                f = NOTHING;
            }
            lakes.put(k, f);
        }
        return f == NOTHING ? null : f;
    }

    private Feature pickLake(int i, int j) {
        int x = i * LAKE_CELL + LAKE_CELL / 2 + (int) ((rnd(i, j, 7201) - 0.5) * LAKE_CELL * 0.6);
        int z = j * LAKE_CELL + LAKE_CELL / 2 + (int) ((rnd(i, j, 7202) - 0.5) * LAKE_CELL * 0.6);
        if (Math.abs(x) > half - 90 || Math.abs(z) > half - 90) {
            return null;
        }
        gen.column(x, z, false, hit, col);
        int biome = col.biome;
        if (col.fluid != NONE || rnd(i, j, 7203) >= lakeChance(biome)) {
            return null;
        }
        int r = 11 + (int) (rnd(i, j, 7204) * 10);
        int reach = reach("lac", r);
        if (reach > MAX_LAKE || blocked(x, z, reach + 4) || nearRoad(x, z, reach + 2)) {
            return null;
        }
        int[] s = survey(x, z, reach);
        if (s[2] > 0 || s[1] - s[0] > 3) {
            return null;
        }
        return new Feature("lac", x, z, r, reach, (int) (rnd(i, j, 7205) * 4), look(style(x, z), biome),
                (int) (rnd(i, j, 7206) * 1_000_000), s[0], s[1]);
    }

    private boolean lakeNear(int x, int z, int pad) {
        int reach = MAX_LAKE + pad;
        for (int i = Math.floorDiv(x - reach, LAKE_CELL); i <= Math.floorDiv(x + reach, LAKE_CELL); i++) {
            for (int j = Math.floorDiv(z - reach, LAKE_CELL); j <= Math.floorDiv(z + reach, LAKE_CELL); j++) {
                Feature l = lake(i, j);
                if (l != null && Math.hypot(x - l.x(), z - l.z()) < l.reach() + pad) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Vrai si le point est sur l'emprise (plus une marge) d'un petit lieu ou d'un lac. */
    private boolean nearFeature(int x, int z, int pad) {
        int i = Math.floorDiv(x, CELL), j = Math.floorDiv(z, CELL);
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                Feature f = feature(i + di, j + dj);
                if (f != null && Math.hypot(x - f.x(), z - f.z()) < f.reach() + pad + 1) {
                    return true;
                }
            }
        }
        return lakeNear(x, z, pad);
    }

    /** Petits lieux et lacs dont le centre tombe dans le carré donné (sans rien poser). */
    Map<String, Integer> census(int x0, int z0, int x1, int z1) {
        Map<String, Integer> out = new TreeMap<>();
        for (int i = Math.floorDiv(x0, CELL); i <= Math.floorDiv(x1, CELL); i++) {
            for (int j = Math.floorDiv(z0, CELL); j <= Math.floorDiv(z1, CELL); j++) {
                Feature f = feature(i, j);
                if (f != null && f.x() >= x0 && f.x() <= x1 && f.z() >= z0 && f.z() <= z1) {
                    out.merge(f.kind(), 1, Integer::sum);
                }
            }
        }
        for (int i = Math.floorDiv(x0, LAKE_CELL); i <= Math.floorDiv(x1, LAKE_CELL); i++) {
            for (int j = Math.floorDiv(z0, LAKE_CELL); j <= Math.floorDiv(z1, LAKE_CELL); j++) {
                Feature f = lake(i, j);
                if (f != null && f.x() >= x0 && f.x() <= x1 && f.z() >= z0 && f.z() <= z1) {
                    out.merge("lac", 1, Integer::sum);
                }
            }
        }
        return out;
    }

    // ================================================================== Pose d'un chunk
    /** Habille un chunk ; renvoie le nombre de blocs changés. */
    int apply(Chunk chunk) {
        Paint pt = new Paint(chunk);
        if (!data.inside(pt.x0, pt.z0) || !data.inside(pt.x0 + 15, pt.z0 + 15)) {
            return 0;
        }
        for (int i = Math.floorDiv(pt.x0 - MAX_LAKE, LAKE_CELL); i <= Math.floorDiv(pt.x0 + 15 + MAX_LAKE, LAKE_CELL); i++) {
            for (int j = Math.floorDiv(pt.z0 - MAX_LAKE, LAKE_CELL); j <= Math.floorDiv(pt.z0 + 15 + MAX_LAKE, LAKE_CELL); j++) {
                Feature l = lake(i, j);
                if (l != null && pt.touches(l.x(), l.z(), l.reach())) {
                    draw(l, pt);
                }
            }
        }
        for (int i = Math.floorDiv(pt.x0 - CELL, CELL); i <= Math.floorDiv(pt.x0 + 15 + CELL, CELL); i++) {
            for (int j = Math.floorDiv(pt.z0 - CELL, CELL); j <= Math.floorDiv(pt.z0 + 15 + CELL, CELL); j++) {
                Feature f = feature(i, j);
                if (f != null && pt.touches(f.x(), f.z(), f.reach() + 2)) {
                    draw(f, pt);
                }
            }
        }
        String style = style(pt.x0 + 8, pt.z0 + 8);
        trees(pt, style);
        bushes(pt, style);
        rocks(pt);
        logs(pt);
        cover(pt, style);
        return pt.placed;
    }

    private void draw(Feature f, Paint pt) {
        Draw d = new Draw(pt, f.x(), f.z(), f.rot(), f.style(), f.seed());
        switch (f.kind()) {
            case "maison" -> house(d, false);
            case "ruine" -> house(d, true);
            case "tour_guet" -> watchtower(d);
            case "tour_ruine" -> ruinedTower(d);
            case "champ" -> field(d);
            case "etang" -> pond(d, f, false);
            case "lac" -> pond(d, f, true);
            case "meules" -> haystacks(d);
            case "enclos" -> pen(d);
            case "feu" -> campfire(d);
            case "tombes" -> graves(d);
            case "mur" -> oldWall(d);
            case "charrette" -> cart(d, 0, 0);
            case "cairn" -> cairn(d);
            case "os" -> bones(d);
            case "pilotis" -> stiltHut(d, f);
            case "basalte" -> basalt(d);
            default -> {
            }
        }
        d.flush();
        if (pt.in(f.x(), f.z())) {
            counts.merge(f.kind(), 1, Integer::sum);
        }
    }

    // ================================================================== Blocs
    private BlockData data(String state, int rot) {
        String k = rot == 0 ? state : state + "#" + rot;
        BlockData bd = parsed.get(k);
        if (bd == null) {
            BlockData base = parsed.get(state);
            if (base == null) {
                try {
                    base = Bukkit.createBlockData(state);
                } catch (IllegalArgumentException e) {
                    Bukkit.getLogger().warning("[OrbitMMO] Paysage : état de bloc refusé, ignoré : " + state);
                    base = BAD;
                }
                parsed.put(state, base);
            }
            if (base == BAD) {
                return BAD;
            }
            if (rot == 0) {
                bd = base;
            } else {
                bd = base.clone();
                bd.rotate(rot == 1 ? StructureRotation.CLOCKWISE_90 : (rot == 2 ? StructureRotation.CLOCKWISE_180
                        : StructureRotation.COUNTERCLOCKWISE_90));
                parsed.put(k, bd);
            }
        }
        return bd;
    }

    private static final BlockData AIR = Material.AIR.createBlockData();
    /** Marque d'un état de bloc refusé par le jeu : rien n'est posé. */
    private static final BlockData BAD = Material.STRUCTURE_VOID.createBlockData();

    private static boolean plant(Material m) {
        return PLANTS.contains(m);
    }

    private static boolean soft(Material m) {
        return m.isAir() || PLANTS.contains(m) || Tag.LEAVES.isTagged(m) || m == Material.VINE || m == Material.COBWEB
                || m == Material.SNOW || m == Material.GLOW_LICHEN || m == Material.MOSS_CARPET || m == Material.PALE_MOSS_CARPET
                || m == Material.PALE_HANGING_MOSS;
    }

    /** Les blocs d'un chunk, posés sans mise à jour des voisins. */
    private final class Paint {
        final Chunk chunk;
        final int cx, cz, x0, z0, minY, maxY;
        int placed;

        Paint(Chunk chunk) {
            this.chunk = chunk;
            cx = chunk.getX();
            cz = chunk.getZ();
            x0 = cx << 4;
            z0 = cz << 4;
            World w = chunk.getWorld();
            minY = w.getMinHeight();
            maxY = w.getMaxHeight() - 1;
        }

        boolean in(int x, int z) {
            return (x >> 4) == cx && (z >> 4) == cz;
        }

        boolean touches(int x, int z, int r) {
            return x + r >= x0 && x - r <= x0 + 15 && z + r >= z0 && z - r <= z0 + 15;
        }

        Block block(int x, int y, int z) {
            return chunk.getBlock(x & 15, y, z & 15);
        }

        void set(int x, int y, int z, BlockData bd, int mode) {
            if (y <= minY || y > maxY || bd == BAD) {
                return;
            }
            Block bl = block(x, y, z);
            Material m = bl.getType();
            boolean ok = switch (mode) {
                case SOFT -> soft(m);
                case EARTH -> soft(m) || NATURAL.contains(m);
                case CLEAR -> plant(m) || m == Material.SNOW;
                case DIG -> soft(m) || NATURAL.contains(m);
                case WET -> soft(m) || NATURAL.contains(m) || m == Material.WATER || m == Material.SEAGRASS;
                case POST -> soft(m) || m == Material.WATER || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS;
                case COVER -> m.isAir() && GRASSY.contains(block(x, y - 1, z).getType());
                default -> false;
            };
            if (!ok) {
                return;
            }
            BlockData target = bd == null ? AIR : bd;
            if (m == target.getMaterial() && bl.getBlockData().matches(target)) {
                return;
            }
            if (DOUBLE.contains(m)) {
                for (int dy : new int[]{-1, 1}) {
                    if (y + dy > minY && y + dy <= maxY) {
                        Block o = block(x, y + dy, z);
                        if (o.getType() == m) {
                            o.setBlockData(AIR, false);
                        }
                    }
                }
            }
            bl.setBlockData(target, false);
            placed++;
        }
    }

    /**
     * Dessin d'un objet en coordonnées locales (dx, y absolu, dz), tourné d'un quart de tour par {@code rot}.
     * Les blocs hors du chunk sont ignorés ; un bloc posé deux fois garde le dernier.
     */
    private final class Draw {
        final Paint pt;
        final int ox, oz, rot, seed;
        final String style;
        final Pal pal;
        private final Map<Long, Object[]> plan = new LinkedHashMap<>();
        private final Map<Integer, Integer> grounds = new HashMap<>();

        Draw(Paint pt, int ox, int oz, int rot, String style, int seed) {
            this.pt = pt;
            this.ox = ox;
            this.oz = oz;
            this.rot = rot;
            this.style = style;
            this.seed = seed;
            this.pal = pal(style);
        }

        int wx(int dx, int dz) {
            return ox + switch (rot) {
                case 1 -> -dz;
                case 2 -> -dx;
                case 3 -> dz;
                default -> dx;
            };
        }

        int wz(int dx, int dz) {
            return oz + switch (rot) {
                case 1 -> dx;
                case 2 -> -dz;
                case 3 -> -dx;
                default -> dz;
            };
        }

        boolean in(int dx, int dz) {
            return pt.in(wx(dx, dz), wz(dx, dz));
        }

        /** Sol naturel (Y du dernier bloc plein) sous un point local. */
        int g(int dx, int dz) {
            int k = (dx + 512) * 1024 + dz + 512;
            Integer v = grounds.get(k);
            if (v == null) {
                v = ground(wx(dx, dz), wz(dx, dz));
                grounds.put(k, v);
            }
            return v;
        }

        int maxG(int x0, int z0, int x1, int z1) {
            int m = Integer.MIN_VALUE;
            for (int dz = z0; dz <= z1; dz++) {
                for (int dx = x0; dx <= x1; dx++) {
                    m = Math.max(m, g(dx, dz));
                }
            }
            return m;
        }

        int minG(int x0, int z0, int x1, int z1) {
            int m = Integer.MAX_VALUE;
            for (int dz = z0; dz <= z1; dz++) {
                for (int dx = x0; dx <= x1; dx++) {
                    m = Math.min(m, g(dx, dz));
                }
            }
            return m;
        }

        /** Surface de l'eau la plus haute sur un rectangle, ou NONE. */
        int maxFluid(int x0, int z0, int x1, int z1) {
            int m = NONE;
            for (int dz = z0; dz <= z1; dz++) {
                for (int dx = x0; dx <= x1; dx++) {
                    gen.column(wx(dx, dz), wz(dx, dz), false, hit, col);
                    if (col.fluid != NONE && !col.lava) {
                        m = Math.max(m, col.fluid);
                    }
                }
            }
            return m;
        }

        double r(int a, int b, int salt) {
            return rnd(ox * 31 + a, oz * 17 + b, seed + salt);
        }

        Material actual(int dx, int y, int dz) {
            int x = wx(dx, dz), z = wz(dx, dz);
            return pt.in(x, z) ? pt.block(x, y, z).getType() : null;
        }

        private long pkey(int x, int y, int z) {
            return ((long) (y + 4096) << 8) | ((x & 15) << 4) | (z & 15);
        }

        void put(int dx, int y, int dz, String state, int mode) {
            int x = wx(dx, dz), z = wz(dx, dz);
            if (!pt.in(x, z)) {
                return;
            }
            plan.put(pkey(x, y, z), new Object[]{state == null ? null : data(state, rot), mode});
        }

        void put(int dx, int y, int dz, String state) {
            put(dx, y, dz, state, SOFT);
        }

        void earth(int dx, int y, int dz, String state) {
            put(dx, y, dz, state, EARTH);
        }

        void wet(int dx, int y, int dz, String state) {
            put(dx, y, dz, state, WET);
        }

        void post(int dx, int y, int dz, String state) {
            put(dx, y, dz, state, POST);
        }

        /** Retire herbes et fleurs (ou annule un bloc prévu à cet endroit). */
        void air(int dx, int y, int dz) {
            put(dx, y, dz, null, CLEAR);
        }

        void dig(int dx, int y, int dz) {
            put(dx, y, dz, null, DIG);
        }

        void cover(int dx, int y, int dz, String state) {
            put(dx, y, dz, state, COVER);
        }

        /** Le bloc prévu à un point local, ou null. */
        BlockData planned(int dx, int y, int dz) {
            int x = wx(dx, dz), z = wz(dx, dz);
            if (!pt.in(x, z)) {
                return null;
            }
            Object[] e = plan.get(pkey(x, y, z));
            return e == null ? null : (BlockData) e[0];
        }

        void flush() {
            for (Map.Entry<Long, Object[]> e : plan.entrySet()) {
                long k = e.getKey();
                int y = (int) (k >> 8) - 4096, lx = (int) ((k >> 4) & 15), lz = (int) (k & 15);
                pt.set(pt.x0 + lx, y, pt.z0 + lz, (BlockData) e.getValue()[0], (Integer) e.getValue()[1]);
            }
            plan.clear();
        }

        /** Ellipsoïde de blocs (feuillage, rocher), au bord effiloché. */
        void blob(double cx, double cy, double cz, double rx, double ry, double rz, String[] states, int salt, int mode) {
            for (int y = (int) Math.floor(cy - ry); y <= (int) Math.ceil(cy + ry); y++) {
                for (int dz = (int) Math.floor(cz - rz); dz <= (int) Math.ceil(cz + rz); dz++) {
                    for (int dx = (int) Math.floor(cx - rx); dx <= (int) Math.ceil(cx + rx); dx++) {
                        double ex = (dx - cx) / rx, ey = (y - cy) / ry, ez = (dz - cz) / rz;
                        double e = ex * ex + ey * ey + ez * ez;
                        if (e > 1 || (e > 0.6 && r(dx, y * 31 + dz, salt) < (e - 0.6) * 1.6)) {
                            continue;
                        }
                        String s = states.length == 1 ? states[0] : states[(int) (r(dx * 7, y * 13 + dz, salt + 1) * states.length)];
                        put(dx, y, dz, s, mode);
                    }
                }
            }
        }

        void leaves(double cx, double cy, double cz, double rx, double ry, double rz, String leaves, int salt) {
            blob(cx, cy, cz, rx, ry, rz, new String[]{leaves + "[persistent=true]"}, salt, SOFT);
        }

        String mix(int dx, int y, int dz) {
            double n = r(dx * 3, y * 11 + dz, 300);
            return n < 0.4 ? pal.stone : (n < 0.7 ? pal.brick : (n < 0.88 ? pal.stone2 : pal.crack));
        }

        String fence(boolean n, boolean e, boolean s, boolean w) {
            return pal.fence + "[north=" + n + ",east=" + e + ",south=" + s + ",west=" + w + ",waterlogged=false]";
        }
    }

    private static String stair(String block, String facing) {
        return block + "[facing=" + facing + ",half=bottom,shape=straight,waterlogged=false]";
    }

    private static Pal pal(String style) {
        return switch (style) {
            case "ombre" -> new Pal("dark_oak_log[axis=y]", "dark_oak_log[axis=x]", "dark_oak_log[axis=z]", "dark_oak_planks",
                    "dark_oak_stairs", "dark_oak_slab", "dark_oak_fence", "dark_oak_fence_gate", "deepslate_tile_stairs",
                    "deepslate_tile_slab", "cobbled_deepslate", "mossy_cobblestone", "deepslate_bricks", "cracked_deepslate_bricks",
                    "deepslate_brick_slab", "cobbled_deepslate_wall", "dark_oak_trapdoor", "dark_oak_door", "gray_wool");
            case "marais" -> new Pal("mangrove_log[axis=y]", "mangrove_log[axis=x]", "mangrove_log[axis=z]", "mangrove_planks",
                    "mangrove_stairs", "mangrove_slab", "mangrove_fence", "mangrove_fence_gate", "dark_oak_stairs", "dark_oak_slab",
                    "mossy_cobblestone", "mud_bricks", "mud_bricks", "mossy_stone_bricks", "mud_brick_slab", "mud_brick_wall",
                    "mangrove_trapdoor", "mangrove_door", "green_wool");
            case "desert" -> new Pal("cut_sandstone", "cut_sandstone", "cut_sandstone", "smooth_sandstone", "sandstone_stairs",
                    "sandstone_slab", "acacia_fence", "acacia_fence_gate", "smooth_sandstone_stairs", "smooth_sandstone_slab",
                    "sandstone", "cut_sandstone", "smooth_sandstone", "chiseled_sandstone", "sandstone_slab", "sandstone_wall",
                    "acacia_trapdoor", "acacia_door", "orange_wool");
            case "givre" -> new Pal("spruce_log[axis=y]", "spruce_log[axis=x]", "spruce_log[axis=z]", "spruce_planks", "spruce_stairs",
                    "spruce_slab", "spruce_fence", "spruce_fence_gate", "spruce_stairs", "spruce_slab", "cobblestone", "stone",
                    "stone_bricks", "cracked_stone_bricks", "stone_brick_slab", "cobblestone_wall", "spruce_trapdoor", "spruce_door",
                    "white_wool");
            case "cendre" -> new Pal("basalt[axis=y]", "basalt[axis=x]", "basalt[axis=z]", "dark_oak_planks", "dark_oak_stairs",
                    "dark_oak_slab", "dark_oak_fence", "dark_oak_fence_gate", "blackstone_stairs", "blackstone_slab", "blackstone",
                    "cracked_polished_blackstone_bricks", "polished_blackstone_bricks", "gilded_blackstone",
                    "polished_blackstone_brick_slab", "blackstone_wall", "dark_oak_trapdoor", "dark_oak_door", "black_wool");
            case "neutre" -> new Pal("spruce_log[axis=y]", "spruce_log[axis=x]", "spruce_log[axis=z]", "spruce_planks", "spruce_stairs",
                    "spruce_slab", "spruce_fence", "spruce_fence_gate", "dark_oak_stairs", "dark_oak_slab", "cobblestone", "andesite",
                    "stone_bricks", "mossy_stone_bricks", "stone_brick_slab", "mossy_cobblestone_wall", "spruce_trapdoor", "spruce_door",
                    "brown_wool");
            default -> new Pal("oak_log[axis=y]", "oak_log[axis=x]", "oak_log[axis=z]", "oak_planks", "oak_stairs", "oak_slab",
                    "oak_fence", "oak_fence_gate", "brick_stairs", "brick_slab", "cobblestone", "mossy_cobblestone", "stone_bricks",
                    "cracked_stone_bricks", "stone_brick_slab", "cobblestone_wall", "oak_trapdoor", "oak_door", "white_wool");
        };
    }

    // ================================================================== Petits lieux
    /** Toit à deux pans (faîte selon z), premier rang à Y {@code ry}, pignons pleins ; {@code holes} = part de tuiles manquantes. */
    private void gable(Draw d, int ry, int hx, int hz, double holes) {
        Pal p = d.pal;
        for (int k = 0; k <= hx + 1; k++) {
            int y = ry + k, xl = -hx - 1 + k, xr = hx + 1 - k;
            for (int dz = -hz - 1; dz <= hz + 1; dz++) {
                if (holes > 0 && d.r(k * 5, dz, 9) < holes) {
                    continue;
                }
                if (xl < xr) {
                    d.put(xl, y, dz, stair(p.roof(), "east"));
                    d.put(xr, y, dz, stair(p.roof(), "west"));
                } else {
                    d.put(0, y, dz, p.roofSlab() + "[type=bottom,waterlogged=false]");
                }
            }
            for (int dx = xl + 1; dx <= xr - 1; dx++) {
                d.put(dx, y, -hz, p.planks());
                d.put(dx, y, hz, p.planks());
            }
        }
    }

    /** Lierre qui pend le long d'un mur extérieur (côté indiqué : +x, -x, +z ou -z). */
    private void vines(Draw d, int dx, int dz, int yTop, int yLow, String face) {
        for (int y = yTop; y >= yLow; y--) {
            d.put(dx, y, dz, "vine[" + face + "=true]");
        }
    }

    private void rubble(Draw d, double rIn, double rOut, double density, int salt) {
        int R = (int) Math.ceil(rOut);
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                double dist = Math.hypot(dx, dz);
                if (dist < rIn || dist > rOut) {
                    continue;
                }
                double fade = 1 - (dist - rIn) / Math.max(1, rOut - rIn);
                double n = d.r(dx, dz, salt);
                if (n < density * fade) {
                    int g = d.g(dx, dz);
                    d.put(dx, g + 1, dz, n < density * fade * 0.3 ? d.pal.brickSlab() + "[type=bottom,waterlogged=false]" : d.mix(dx, 1, dz));
                }
            }
        }
    }

    private void house(Draw d, boolean ruined) {
        Pal p = d.pal;
        int hx = d.r(0, 0, 1) < 0.5 ? 2 : 3, hz = d.r(0, 0, 2) < 0.5 ? 3 : 4, H = 4;
        int b = d.maxG(-hx, -hz, hx, hz);
        for (int dz = -hz; dz <= hz; dz++) {
            for (int dx = -hx; dx <= hx; dx++) {
                int g = d.g(dx, dz);
                for (int y = g + 1; y < b; y++) {
                    d.earth(dx, y, dz, p.stone());
                }
                boolean edge = Math.abs(dx) == hx || Math.abs(dz) == hz;
                String floor = edge ? p.stone() : (!ruined ? p.planks()
                        : (d.r(dx, dz, 3) < 0.35 ? "coarse_dirt" : (d.r(dx, dz, 4) < 0.5 ? p.planks() : "moss_block")));
                d.earth(dx, b, dz, floor);
                for (int y = b + 1; y <= b + H + hx + 3; y++) {
                    d.air(dx, y, dz);
                }
            }
        }
        double ph = d.r(0, 0, 6) * 6.28;
        int[][] tops = new int[2 * hx + 1][2 * hz + 1];
        for (int dz = -hz; dz <= hz; dz++) {
            for (int dx = -hx; dx <= hx; dx++) {
                if (Math.abs(dx) != hx && Math.abs(dz) != hz) {
                    continue;
                }
                boolean corner = Math.abs(dx) == hx && Math.abs(dz) == hz;
                int top = H;
                if (ruined) {
                    double n = 0.5 * d.r(dx, dz, 5) + 0.5 * (0.5 + 0.5 * Math.sin(dx * 0.9 + dz * 0.7 + ph));
                    top = corner ? 2 + (int) (n * 3) : (int) Math.floor(n * (H + 0.99));
                }
                tops[dx + hx][dz + hz] = top;
                for (int y = 1; y <= top; y++) {
                    String s;
                    if (ruined) {
                        s = d.r(dx, y * 7 + dz, 8) < 0.2 && !corner && y > 1 ? p.planks() : d.mix(dx, y, dz);
                    } else {
                        s = corner ? p.post() : (y == 1 ? p.stone() : p.planks());
                    }
                    d.put(dx, b + y, dz, s);
                }
            }
        }
        // Porte au sud, fenêtres sur les côtés, au fond.
        d.air(0, b + 1, hz);
        d.air(0, b + 2, hz);
        for (int sx : new int[]{-hx, hx}) {
            for (int wz : hz == 4 ? new int[]{-2, 2} : new int[]{0}) {
                for (int y = b + 2; y <= b + 3; y++) {
                    if (!ruined && d.r(sx, wz + y, 12) < 0.25) {
                        d.put(sx, y, wz, "cobweb");
                    } else {
                        d.air(sx, y, wz);
                    }
                }
            }
        }
        if (hx == 3) {
            d.air(0, b + 2, -hz);
            d.air(0, b + 3, -hz);
        }
        if (ruined) {
            // Débris de toit et de murs, herbes et mousse à l'intérieur.
            for (int dz = -hz + 1; dz <= hz - 1; dz++) {
                for (int dx = -hx + 1; dx <= hx - 1; dx++) {
                    double n = d.r(dx, dz, 13);
                    if (n < 0.12) {
                        d.put(dx, b + 1, dz, stair(p.roof(), new String[]{"north", "east", "south", "west"}[(int) (n * 33) % 4]));
                    } else if (n < 0.2) {
                        d.put(dx, b + 1, dz, d.mix(dx, 1, dz));
                    } else if (n < 0.32) {
                        d.put(dx, b + 1, dz, "moss_carpet");
                    } else if (n < 0.45) {
                        d.put(dx, b + 1, dz, "short_grass");
                    }
                }
            }
            if (d.r(0, 0, 14) < 0.5) {
                d.put(-hx + 1, b + 1, -hz + 1, "cobweb");
            }
            rubble(d, Math.max(hx, hz) + 1, Math.max(hx, hz) + 3.5, 0.22, 15);
        } else {
            gable(d, b + H + 1, hx, hz, 0.06);
            boolean noDoor = d.r(0, 0, 16) < 0.35;
            if (!noDoor) {
                d.put(0, b + 1, hz, p.door() + "[half=lower,facing=south,hinge=left,open=true,powered=false]");
                d.put(0, b + 2, hz, p.door() + "[half=upper,facing=south,hinge=left,open=true,powered=false]");
            }
            // Cheminée à l'angle nord-ouest, feu parfois allumé : quelqu'un squatte.
            int chTop = b + H + hx + 3;
            for (int y = b + 1; y <= chTop; y++) {
                d.put(-hx + 1, y, -hz, p.brick());
            }
            d.put(-hx + 1, chTop + 1, -hz, "campfire[lit=" + (d.r(0, 0, 17) < 0.3) + ",signal_fire=false,facing=north,waterlogged=false]");
            // Mobilier oublié.
            d.put(-hx + 1, b + 1, -hz + 1, "barrel[facing=up,open=false]");
            d.put(hx - 1, b + 1, -hz + 1, "white_bed[facing=north,part=head,occupied=false]");
            d.put(hx - 1, b + 1, -hz + 2, "white_bed[facing=north,part=foot,occupied=false]");
            d.put(-hx + 1, b + 1, 1, p.fence());
            d.put(-hx + 1, b + 2, 1, d.r(0, 0, 18) < 0.5 ? "potted_dead_bush" : "candle[candles=2,lit=false,waterlogged=false]");
            d.put(-hx + 1, b + 1, 2, stair(p.stairs(), "south"));
            for (int[] c : new int[][]{{-hx + 1, -hz + 1}, {hx - 1, hz - 1}, {hx - 1, -hz + 1}}) {
                if (d.r(c[0], c[1], 19) < 0.5) {
                    d.put(c[0], b + H, c[1], "cobweb");
                }
            }
            for (int dz = -hz + 1; dz <= hz - 1; dz++) {
                for (int dx = -hx + 1; dx <= hx - 1; dx++) {
                    if (d.r(dx, dz, 20) < 0.15) {
                        d.put(dx, b + 1, dz, "moss_carpet");
                    }
                }
            }
        }
        // Lierre sur les murs.
        for (int dz = -hz + 1; dz <= hz - 1; dz++) {
            for (int side : new int[]{-1, 1}) {
                int top = tops[side < 0 ? 0 : 2 * hx][dz + hz];
                if (top >= 3 && d.r(side, dz, 21) < 0.3) {
                    vines(d, side * (hx + 1), dz, b + top, b + 2 + (int) (d.r(side, dz, 22) * 2), side > 0 ? "west" : "east");
                }
            }
        }
    }

    private void watchtower(Draw d) {
        Pal p = d.pal;
        boolean broken = d.r(0, 0, 1) < 0.3;
        int H = 7 + (int) (d.r(0, 0, 2) * 3);
        int b = d.maxG(-2, -2, 2, 2);
        int top = b + H;
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int y = d.g(dx, dz) + 1; y <= top + 7; y++) {
                    d.air(dx, y, dz);
                }
            }
        }
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            int g = d.g(c[0], c[1]);
            d.earth(c[0], g, c[1], p.stone());
            int end = top + (broken && c[0] > 0 && c[1] < 0 ? 1 : 4);
            for (int y = g + 1; y <= end; y++) {
                d.put(c[0], y, c[1], p.post());
            }
        }
        int mid = b + H / 2 + 1;
        for (int t = -1; t <= 1; t++) {
            d.put(t, mid, -2, p.postX());
            d.put(t, mid, 2, p.postX());
            d.put(-2, mid, t, p.postZ());
            d.put(2, mid, t, p.postZ());
        }
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                    continue;
                }
                if (broken && d.r(dx, dz, 3) < 0.25) {
                    continue;
                }
                d.put(dx, top + 1, dz, p.planks());
            }
        }
        for (int y = d.g(2, 1) + 1; y <= top + 1; y++) {
            d.put(2, y, 1, "ladder[facing=north,waterlogged=false]");
        }
        for (int t = -1; t <= 1; t++) {
            if (!broken || d.r(t, 9, 4) > 0.3) {
                d.put(t, top + 2, -2, d.fence(false, true, false, true));
                d.put(t, top + 2, 2, d.fence(false, true, false, true));
                d.put(-2, top + 2, t, d.fence(true, false, true, false));
            }
            if (t != 1 && (!broken || d.r(t, 8, 4) > 0.3)) {
                d.put(2, top + 2, t, d.fence(true, false, t < 0, false));
            }
        }
        if (!broken) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dx = -3; dx <= 3; dx++) {
                    boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                    d.put(dx, top + 5, dz, edge ? p.roofSlab() + "[type=bottom,waterlogged=false]" : p.planks());
                    if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                        d.put(dx, top + 6, dz, p.roofSlab() + "[type=bottom,waterlogged=false]");
                    }
                }
            }
            d.put(0, top + 4, 0, "lantern[hanging=true,waterlogged=false]");
            d.put(-1, top + 2, -1, "barrel[facing=up,open=false]");
        }
    }

    private void ruinedTower(Draw d) {
        Pal p = d.pal;
        double R = 3.2;
        int H = 8 + (int) (d.r(0, 0, 1) * 7);
        int b = d.minG(-3, -3, 3, 3);
        double ph = d.r(0, 0, 2) * 6.28;
        for (int dz = -4; dz <= 4; dz++) {
            for (int dx = -4; dx <= 4; dx++) {
                double dist = Math.hypot(dx, dz);
                if (dist > R + 0.5) {
                    continue;
                }
                int g = d.g(dx, dz);
                if (dist >= R - 1.0) {
                    double a = Math.atan2(dz, dx);
                    double n = 0.5 + 0.3 * Math.sin(a + ph) + 0.2 * Math.sin(2 * a + ph * 1.7);
                    int hh = Math.max(1, (int) Math.round(H * (0.15 + 0.85 * n) + (d.r(dx, dz, 3) - 0.5) * 2));
                    boolean door = dz >= 2 && dx == 0;
                    for (int y = 1; y <= Math.max(hh, g - b); y++) {
                        if (door && y <= 3) {
                            d.dig(dx, b + y, dz);
                            continue;
                        }
                        if (y > hh) {
                            break;
                        }
                        if (y % 4 == 3 && (dx == 0 || dz == 0)) {
                            d.dig(dx, b + y, dz);
                            continue;
                        }
                        d.earth(dx, b + y, dz, d.mix(dx, y, dz));
                    }
                    if (hh >= 4 && d.r(dx, dz, 7) < 0.15) {
                        int vx = (int) Math.signum(dx), vz = (int) Math.signum(dz);
                        if (Math.abs(dx) >= Math.abs(dz)) {
                            vines(d, dx + vx, dz, b + hh, b + 2, vx > 0 ? "west" : "east");
                        } else {
                            vines(d, dx, dz + vz, b + hh, b + 2, vz > 0 ? "north" : "south");
                        }
                    }
                } else {
                    for (int y = b + 1; y <= g + 1; y++) {
                        d.dig(dx, y, dz);
                    }
                    double n = d.r(dx, dz, 4);
                    d.earth(dx, b, dz, n < 0.35 ? "gravel" : (n < 0.6 ? "mossy_cobblestone" : (n < 0.8 ? "coarse_dirt" : "moss_block")));
                    if (d.r(dx, dz, 5) < 0.3) {
                        d.put(dx, b + 1, dz, d.r(dx, dz, 6) < 0.5 ? "short_grass" : d.mix(dx, 1, dz));
                    }
                }
            }
        }
        for (int t = 0; t < H / 2; t++) {
            double a = t * 0.9 + ph;
            d.put((int) Math.round(Math.cos(a) * 1.6), b + 1 + t, (int) Math.round(Math.sin(a) * 1.6),
                    p.brickSlab() + "[type=top,waterlogged=false]");
        }
        rubble(d, R + 0.8, 6.5, 0.25, 8);
    }

    private void fenceRing(Draw d, int hx, int hz, double missing, int salt) {
        for (int dz = -hz; dz <= hz; dz++) {
            for (int dx = -hx; dx <= hx; dx++) {
                boolean ex = Math.abs(dx) == hx, ez = Math.abs(dz) == hz;
                if (!ex && !ez) {
                    continue;
                }
                if (dz == hz && dx == 0) {
                    d.put(0, d.g(0, hz) + 1, hz, d.pal.gate() + "[facing=south,open=false,in_wall=false,powered=false]");
                    continue;
                }
                if (d.r(dx, dz, salt) < missing) {
                    continue;
                }
                boolean n = ex && dz > -hz, s = ex && dz < hz, e = ez && dx < hx, w = ez && dx > -hx;
                d.put(dx, d.g(dx, dz) + 1, dz, d.fence(n, e, s, w));
            }
        }
    }

    private void field(Draw d) {
        int hx = 4 + (int) (d.r(0, 0, 1) * 3), hz = 3 + (int) (d.r(0, 0, 2) * 2);
        boolean flat = d.maxG(-hx - 1, -hz - 1, hx + 1, hz + 1) == d.minG(-hx - 1, -hz - 1, hx + 1, hz + 1);
        String[] crops = d.style.equals("ombre") ? new String[]{"beetroots", "potatoes", "wheat", "beetroots"}
                : new String[]{"wheat", "carrots", "wheat", "potatoes", "beetroots"};
        int off = (int) (d.r(0, 0, 3) * crops.length);
        for (int dz = -hz; dz <= hz; dz++) {
            String crop = crops[((dz + hz) / 2 + off) % crops.length];
            int max = crop.equals("beetroots") ? 3 : 7;
            for (int dx = -hx; dx <= hx; dx++) {
                int g = d.g(dx, dz);
                for (int y = g + 1; y <= g + 3; y++) {
                    d.air(dx, y, dz);
                }
                if (dz == 0 && flat) {
                    d.wet(dx, g, 0, "water");
                    continue;
                }
                if (d.r(dx, dz, 4) < 0.05) {
                    d.earth(dx, g, dz, "coarse_dirt");
                    continue;
                }
                d.earth(dx, g, dz, "farmland[moisture=7]");
                int age = d.r(dx, dz, 5) < 0.7 ? max : (int) (d.r(dx, dz, 6) * (max + 1));
                d.put(dx, g + 1, dz, crop + "[age=" + age + "]");
            }
        }
        fenceRing(d, hx + 1, hz + 1, 0.08, 7);
        int sx = (d.r(0, 0, 8) < 0.5 ? -1 : 1) * (hx - 2), sz = -2;
        int g = d.g(sx, sz);
        d.put(sx, g + 1, sz, d.fence(false, false, false, false));
        d.put(sx, g + 2, sz, "hay_block[axis=y]");
        d.put(sx, g + 3, sz, "carved_pumpkin[facing=south]");
        int gz = d.g(0, hz + 2);
        d.earth(0, gz, hz + 2, "dirt_path");
        d.earth(0, d.g(0, hz + 3), hz + 3, "dirt_path");
        d.put(2, d.g(2, hz + 2) + 1, hz + 2, "composter[level=5]");
        d.put(-2, d.g(-2, hz + 2) + 1, hz + 2, "hay_block[axis=y]");
        if (d.r(0, 0, 9) < 0.5) {
            d.put(-3, d.g(-3, hz + 2) + 1, hz + 2, "barrel[facing=up,open=false]");
        }
    }

    private void pond(Draw d, Feature f, boolean lake) {
        boolean frozen = d.style.equals("givre");
        boolean marsh = d.style.equals("marais");
        double R = f.r();
        double ph1 = d.r(0, 0, 1) * 6.28, ph2 = d.r(0, 0, 2) * 6.28;
        int W = f.base();
        int maxDepth = lake ? 3 + (int) (d.r(0, 0, 3) * 3) : 2 + (int) (d.r(0, 0, 3) * 2);
        int ext = f.reach();
        for (int dz = -ext; dz <= ext; dz++) {
            for (int dx = -ext; dx <= ext; dx++) {
                if (!d.in(dx, dz)) {
                    continue;
                }
                double dist = Math.hypot(dx, dz), a = Math.atan2(dz, dx);
                double rr = R * (1 + 0.18 * Math.sin(3 * a + ph1) + 0.1 * Math.sin(5 * a + ph2));
                double q = dist / rr;
                int g = d.g(dx, dz);
                if (q <= 1) {
                    Material above = d.actual(dx, g + 1, dz);
                    if (above != null && Tag.LOGS.isTagged(above)) {
                        continue;
                    }
                    int depth = Math.max(1, (int) Math.round(maxDepth * Math.min(1, (1 - q) * 1.6)));
                    for (int y = W + 1; y <= g + 2; y++) {
                        d.dig(dx, y, dz);
                    }
                    for (int y = W - depth + 1; y <= W; y++) {
                        d.wet(dx, y, dz, frozen && y == W ? "ice" : "water");
                    }
                    double n = d.r(dx, dz, 4);
                    d.wet(dx, W - depth, dz, marsh ? "mud" : (n < 0.5 ? "sand" : (n < 0.75 ? "clay" : "gravel")));
                    if (!frozen && depth >= 2 && d.r(dx, dz, 6) < 0.3) {
                        d.wet(dx, W - depth + 1, dz, "seagrass");
                    }
                    if (!frozen && q > 0.5 && d.r(dx, dz, 7) < (marsh ? 0.25 : 0.1)) {
                        d.put(dx, W + 1, dz, "lily_pad");
                    }
                } else if (q <= 1.35) {
                    Material top = d.actual(dx, g, dz);
                    if (top == Material.GRASS_BLOCK && g <= W + 1 && d.r(dx, dz, 8) < 0.45) {
                        d.earth(dx, g, dz, marsh ? "mud" : (frozen ? "snow_block" : (d.r(dx, dz, 9) < 0.6 ? "sand" : "gravel")));
                    }
                    if (!frozen && g == W && d.r(dx, dz, 10) < 0.22) {
                        int h = 1 + (int) (d.r(dx, dz, 11) * 3);
                        for (int k = 1; k <= h; k++) {
                            d.put(dx, g + k, dz, "sugar_cane[age=0]");
                        }
                    } else if (g == W && d.r(dx, dz, 12) < 0.2) {
                        d.put(dx, g + 1, dz, marsh ? "blue_orchid" : "tall_grass[half=lower]");
                        if (!marsh) {
                            d.put(dx, g + 2, dz, "tall_grass[half=upper]");
                        }
                    }
                }
            }
        }
        // Ponton sur les grands lacs.
        if (lake && !frozen && d.r(0, 0, 13) < 0.5) {
            Pal p = d.pal;
            double a = Math.PI / 2;
            int shore = (int) Math.floor(R * (1 + 0.18 * Math.sin(3 * a + ph1) + 0.1 * Math.sin(5 * a + ph2)));
            for (int dz = shore + 1; dz >= shore - 6; dz--) {
                d.put(0, W + 1, dz, p.planks());
                d.put(1, W + 1, dz, p.slab() + "[type=top,waterlogged=false]");
                if ((shore - dz) % 3 == 0) {
                    for (int y = W - 6; y <= W; y++) {
                        d.post(-1, y, dz, p.post());
                    }
                    d.put(-1, W + 1, dz, p.post());
                }
            }
            d.put(-1, W + 2, shore - 6, d.fence(false, false, false, false));
            d.put(-1, W + 3, shore - 6, "lantern[hanging=false,waterlogged=false]");
            d.put(1, W + 2, shore - 5, "barrel[facing=up,open=false]");
        }
    }

    private void haystacks(Draw d) {
        int n = 2 + (int) (d.r(0, 0, 1) * 2);
        for (int k = 0; k < n; k++) {
            int px = (int) Math.round((d.r(k, 0, 2) - 0.5) * 6), pz = (int) Math.round((d.r(k, 0, 3) - 0.5) * 6);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (Math.abs(dx) == 1 && Math.abs(dz) == 1 && d.r(px + dx, pz + dz, 4) < 0.6) {
                        continue;
                    }
                    d.put(px + dx, d.g(px + dx, pz + dz) + 1, pz + dz, "hay_block[axis=y]");
                }
            }
            int g0 = d.g(px, pz);
            d.put(px, g0 + 2, pz, "hay_block[axis=y]");
            if (d.r(k, 1, 5) < 0.5) {
                d.put(px + 1, g0 + 2, pz, "hay_block[axis=x]");
            }
        }
        cart(d, 3, 3);
        d.put(-4, d.g(-4, -3) + 1, -3, d.fence(false, false, false, false));
        d.put(-4, d.g(-4, -3) + 2, -3, "lantern[hanging=false,waterlogged=false]");
    }

    private void cart(Draw d, int cx, int cz) {
        Pal p = d.pal;
        boolean broken = d.r(cx, cz, 1) < 0.5;
        int g = d.g(cx, cz);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                d.air(cx + dx, g + 1, cz + dz);
                d.put(cx + dx, g + 1, cz + dz, p.slab() + (broken && dx == 1 ? "[type=bottom,waterlogged=false]" : "[type=top,waterlogged=false]"));
            }
        }
        d.put(cx, g + 1, cz - 1, p.trapdoor() + "[facing=north,half=bottom,open=true,powered=false,waterlogged=false]");
        if (!broken) {
            d.put(cx, g + 1, cz + 2, p.trapdoor() + "[facing=south,half=bottom,open=true,powered=false,waterlogged=false]");
        } else {
            d.put(cx + 2, d.g(cx + 2, cz + 2) + 1, cz + 2, p.trapdoor() + "[facing=north,half=bottom,open=false,powered=false,waterlogged=false]");
        }
        d.put(cx - 2, g + 1, cz, d.fence(false, true, false, false));
        d.put(cx - 2, g + 1, cz + 1, d.fence(false, true, false, false));
        d.put(cx, g + 2, cz, "barrel[facing=up,open=false]");
        if (d.r(cx, cz, 2) < 0.6) {
            d.put(cx - 1, g + 2, cz + 1, "hay_block[axis=x]");
        }
        if (broken) {
            d.put(cx + 2, d.g(cx + 2, cz) + 1, cz, "barrel[facing=east,open=false]");
        }
    }

    private void pen(Draw d) {
        int hx = 4 + (int) (d.r(0, 0, 1) * 2), hz = 3 + (int) (d.r(0, 0, 2) * 2);
        for (int dz = -hz + 1; dz <= hz - 1; dz++) {
            for (int dx = -hx + 1; dx <= hx - 1; dx++) {
                int g = d.g(dx, dz);
                double n = d.r(dx, dz, 3);
                if (n < 0.3) {
                    d.earth(dx, g, dz, n < 0.08 ? "packed_mud" : "coarse_dirt");
                    d.air(dx, g + 1, dz);
                }
            }
        }
        fenceRing(d, hx, hz, 0.12, 4);
        for (int dx = -1; dx <= 0; dx++) {
            d.put(dx, d.g(dx, -hz + 1) + 1, -hz + 1, "water_cauldron[level=3]");
        }
        d.put(hx - 1, d.g(hx - 1, -hz + 1) + 1, -hz + 1, "hay_block[axis=y]");
        // Abri : deux poteaux et un toit de dalles dans un coin.
        int ax = -hx + 1, az = -hz + 1, g = d.maxG(ax, az, ax + 1, az + 1);
        d.put(ax, d.g(ax, az + 1) + 1, az + 1, d.fence(false, false, false, false));
        d.put(ax + 1, d.g(ax + 1, az + 1) + 1, az + 1, d.fence(false, false, false, false));
        for (int y = d.g(ax, az + 1) + 2; y <= g + 2; y++) {
            d.put(ax, y, az + 1, d.fence(false, false, false, false));
        }
        for (int y = d.g(ax + 1, az + 1) + 2; y <= g + 2; y++) {
            d.put(ax + 1, y, az + 1, d.fence(false, false, false, false));
        }
        for (int dx = ax - 1; dx <= ax + 2; dx++) {
            for (int dz = az - 1; dz <= az + 1; dz++) {
                d.put(dx, g + 3, dz, d.pal.slab() + "[type=bottom,waterlogged=false]");
            }
        }
    }

    private void campfire(Draw d) {
        Pal p = d.pal;
        int g = d.g(0, 0);
        boolean lit = d.r(0, 0, 1) < 0.35;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                d.air(dx, d.g(dx, dz) + 1, dz);
            }
        }
        d.put(0, g + 1, 0, "campfire[lit=" + lit + ",signal_fire=false,facing=north,waterlogged=false]");
        d.put(-1, d.g(-1, -2) + 1, -2, p.postX());
        d.put(0, d.g(0, -2) + 1, -2, p.postX());
        d.put(2, d.g(2, 0) + 1, 0, p.postZ());
        d.put(2, d.g(2, 1) + 1, 1, p.postZ());
        String carpet = d.style.equals("ombre") ? "gray_carpet" : (d.style.equals("desert") ? "orange_carpet" : "brown_carpet");
        d.put(-2, d.g(-2, 1) + 1, 1, carpet);
        d.put(-2, d.g(-2, 2) + 1, 2, carpet);
        d.put(1, d.g(1, 2) + 1, 2, "barrel[facing=up,open=false]");
        if (lit) {
            d.put(1, d.g(1, -1) + 1, -1, "lantern[hanging=false,waterlogged=false]");
        }
        if (d.r(0, 0, 2) < 0.6) {
            // Tente en A, ouverte aux deux bouts.
            for (int dz = -1; dz <= 1; dz++) {
                d.put(-5, d.g(-5, dz) + 1, dz, p.wool());
                d.put(-3, d.g(-3, dz) + 1, dz, p.wool());
                int gm = d.g(-4, dz);
                d.air(-4, gm + 1, dz);
                d.put(-4, gm + 2, dz, p.wool());
            }
        }
    }

    private void graves(Draw d) {
        Pal p = d.pal;
        int n = 3 + (int) (d.r(0, 0, 1) * 4);
        String[] flowers = d.style.equals("ombre") ? new String[]{"lily_of_the_valley", "closed_eyeblossom", "allium"}
                : new String[]{"poppy", "lily_of_the_valley", "oxeye_daisy"};
        for (int k = 0; k < n; k++) {
            int gx = (k % 3 - 1) * 2, gz = k < 3 ? -2 : 1;
            int g = d.g(gx, gz);
            d.put(gx, g + 1, gz, k % 2 == 0 ? p.wall() : "stone_brick_wall");
            int top = g + 1;
            if (d.r(k, 0, 2) < 0.4) {
                d.put(gx, g + 2, gz, p.wall());
                top = g + 2;
            }
            if (d.r(k, 0, 3) < 0.25) {
                d.put(gx, top + 1, gz, "candle[candles=1,lit=false,waterlogged=false]");
            }
            d.earth(gx, d.g(gx, gz + 1), gz + 1, "coarse_dirt");
            d.earth(gx, d.g(gx, gz + 2), gz + 2, "podzol[snowy=false]");
            if (d.r(k, 0, 4) < 0.35) {
                d.put(gx, d.g(gx, gz + 1) + 1, gz + 1, flowers[(int) (d.r(k, 0, 5) * flowers.length)]);
            }
        }
        d.put(4, d.g(4, -1) + 1, -1, d.fence(false, false, false, false));
        d.put(4, d.g(4, -1) + 2, -1, d.fence(false, false, false, false));
        d.put(4, d.g(4, -1) + 3, -1, "lantern[hanging=false,waterlogged=false]");
    }

    private void oldWall(Draw d) {
        int L = 4 + (int) (d.r(0, 0, 1) * 4);
        double ph = d.r(0, 0, 2) * 6.28;
        boolean arch = d.r(0, 0, 3) < 0.5;
        for (int dx = -L; dx <= L; dx++) {
            int g = d.g(dx, 0);
            double n = 0.5 + 0.35 * Math.sin(dx * 0.45 + ph) + 0.3 * (d.r(dx, 0, 4) - 0.5);
            int h = Math.max(0, (int) Math.round(n * 5.5));
            if (Math.abs(dx) == L) {
                h = Math.min(h, 1);
            }
            if (arch && Math.abs(dx) <= 2) {
                h = Math.max(h, 5);
            }
            d.earth(dx, g, 0, d.pal.stone());
            for (int y = 1; y <= h; y++) {
                if (arch && Math.abs(dx) <= 1 && y <= 3) {
                    d.air(dx, g + y, 0);
                    continue;
                }
                d.put(dx, g + y, 0, d.mix(dx, y, 0));
            }
            if (h > 1 && d.r(dx, 1, 5) < 0.4) {
                int g1 = d.g(dx, 1);
                for (int y = 1; y < h - (g1 - g); y++) {
                    d.put(dx, g1 + y, 1, d.mix(dx, y, 1));
                }
            }
            if (h >= 3 && d.r(dx, 2, 6) < 0.2) {
                vines(d, dx, -1, g + h, g + 2, "south");
            }
        }
        rubble(d, 1.5, L + 1.5, 0.12, 7);
    }

    private void cairn(Draw d) {
        Pal p = d.pal;
        int g = d.g(0, 0);
        d.put(0, g + 1, 0, d.mix(0, 1, 0));
        d.put(0, g + 2, 0, d.mix(0, 2, 0));
        for (int[] c : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (d.r(c[0], c[1], 1) < 0.7) {
                d.put(c[0], d.g(c[0], c[1]) + 1, c[1], d.mix(c[0], 1, c[1]));
            }
        }
        if (d.r(0, 0, 2) < 0.5) {
            d.put(0, g + 3, 0, p.wall());
            if (d.r(0, 0, 3) < 0.5) {
                d.put(0, g + 4, 0, "lantern[hanging=false,waterlogged=false]");
            }
        } else {
            d.put(0, g + 3, 0, p.brickSlab() + "[type=bottom,waterlogged=false]");
        }
    }

    private void bones(Draw d) {
        int L = 4;
        int g0 = d.g(0, 0);
        for (int dx = -L; dx <= L; dx++) {
            int g = d.g(dx, 0);
            if (Math.floorMod(dx, 3) == 0) {
                d.earth(dx, g, 0, "bone_block[axis=x]");
            } else {
                d.put(dx, g + 1, 0, "bone_block[axis=x]");
            }
        }
        for (int dx : new int[]{-3, -1, 1, 3}) {
            for (int side : new int[]{-1, 1}) {
                if (d.r(dx, side, 1) < 0.2) {
                    continue;
                }
                int g = d.g(dx, side);
                d.put(dx, g + 2, side, "bone_block[axis=z]");
                d.put(dx, g + 2, 2 * side, "bone_block[axis=z]");
                int g3 = d.g(dx, 3 * side);
                d.put(dx, g3 + 1, 3 * side, "bone_block[axis=y]");
                d.earth(dx, g3, 3 * side, "bone_block[axis=y]");
            }
        }
        d.put(L + 1, d.g(L + 1, 0) + 1, 0, "bone_block[axis=y]");
        d.put(L + 2, d.g(L + 2, 0) + 1, 0, "bone_block[axis=y]");
        d.put(L + 1, Math.max(g0, d.g(L + 1, 0)) + 2, 0, "bone_block[axis=y]");
        for (int dz = -4; dz <= 4; dz++) {
            for (int dx = -5; dx <= 5; dx++) {
                if (d.r(dx, dz, 2) < 0.15) {
                    Material top = d.actual(dx, d.g(dx, dz), dz);
                    if (top == Material.GRASS_BLOCK || top == Material.SAND || top == Material.RED_SAND) {
                        d.earth(dx, d.g(dx, dz), dz, top == Material.GRASS_BLOCK ? "coarse_dirt" : "gravel");
                    }
                }
            }
        }
    }

    private void stiltHut(Draw d, Feature f) {
        Pal p = d.pal;
        int water = d.maxFluid(-3, -3, 3, 3);
        int P = Math.max(f.level(), water == NONE ? f.level() : water) + 3;
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int y = d.g(dx, dz) + 1; y <= P + 8; y++) {
                    d.air(dx, y, dz);
                }
            }
        }
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}, {-2, 0}, {2, 0}}) {
            for (int y = d.g(c[0], c[1]) + 1; y < P; y++) {
                d.post(c[0], y, c[1], p.post());
            }
        }
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                d.put(dx, P, dz, p.planks());
                boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2, corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                if (wall) {
                    for (int y = P + 1; y <= P + 3; y++) {
                        d.put(dx, y, dz, corner ? p.post() : p.planks());
                    }
                }
            }
        }
        d.air(0, P + 1, 2);
        d.air(0, P + 2, 2);
        d.air(-2, P + 2, 0);
        d.air(2, P + 2, 0);
        gable(d, P + 4, 2, 2, 0.04);
        d.put(-1, P + 1, -1, "barrel[facing=up,open=false]");
        d.put(1, P + 1, -1, "cobweb");
        for (int dz = 3; dz <= 7; dz++) {
            d.put(0, P, dz, p.planks());
            if (dz % 2 == 1) {
                for (int y = d.g(0, dz) + 1; y < P; y++) {
                    d.post(0, y, dz, p.post());
                }
            }
        }
        d.put(1, P + 1, 4, d.fence(false, false, false, false));
        d.put(1, P + 2, 4, "lantern[hanging=false,waterlogged=false]");
    }

    private void basalt(Draw d) {
        int n = 5 + (int) (d.r(0, 0, 1) * 5);
        for (int k = 0; k < n; k++) {
            int px = (int) Math.round((d.r(k, 0, 2) - 0.5) * 6), pz = (int) Math.round((d.r(k, 0, 3) - 0.5) * 6);
            int g = d.g(px, pz), h = 2 + (int) (d.r(k, 0, 4) * 6);
            for (int y = 1; y <= h; y++) {
                d.put(px, g + y, pz, y == h && d.r(k, 0, 5) < 0.4 ? "polished_basalt[axis=y]" : "basalt[axis=y]");
            }
        }
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                if (Math.hypot(dx, dz) <= 3 && d.r(dx, dz, 6) < 0.4) {
                    d.earth(dx, d.g(dx, dz), dz, d.r(dx, dz, 7) < 0.2 ? "magma_block" : "blackstone");
                }
            }
        }
    }

    // ================================================================== Arbres, buissons, rochers, troncs
    private static double noise(int x, int z, double scale, int salt) {
        double u = x / scale, v = z / scale;
        int x0 = (int) Math.floor(u), z0 = (int) Math.floor(v);
        double fx = u - x0, fz = v - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = rnd(x0, z0, salt), b = rnd(x0 + 1, z0, salt), c = rnd(x0, z0 + 1, salt), d = rnd(x0 + 1, z0 + 1, salt);
        double top = a + (b - a) * fx, bottom = c + (d - c) * fx;
        return top + (bottom - top) * fz;
    }

    /** 0 hors des bosquets, 1 au cœur. */
    private static double grove(int x, int z) {
        double n = 0.65 * noise(x, z, 64, 7401) + 0.35 * noise(x, z, 23, 7402);
        double t = Math.max(0, Math.min(1, (n - 0.52) / 0.16));
        return t * t * (3 - 2 * t);
    }

    private static double treeBase(int b) {
        return switch (b) {
            case PLAIN -> 0.025;
            case MOOR -> 0.03;
            case HILLS -> 0.04;
            case ALP -> 0.015;
            case OASIS -> 0.06;
            case MARSH -> 0.04;
            case SAND, MESA -> 0.006;
            case ROCK -> 0.01;
            case SNOW, VOLCANIC -> 0.008;
            case ASH -> 0.02;
            default -> 0;
        };
    }

    private static double treeGrove(int b) {
        return switch (b) {
            case PLAIN -> 0.5;
            case MOOR -> 0.35;
            case HILLS -> 0.4;
            case ALP -> 0.25;
            case FOREST -> 0.15;
            case DARK_FOREST, PINES -> 0.1;
            case OASIS -> 0.35;
            case MARSH -> 0.3;
            case SAND, MESA, VOLCANIC -> 0.02;
            case ROCK -> 0.06;
            case SNOW -> 0.05;
            case ASH -> 0.08;
            default -> 0;
        };
    }

    private static String treeType(int b, String style, double r) {
        boolean dark = style.equals("ombre"), light = style.equals("lumiere");
        return switch (b) {
            case PLAIN -> dark ? pickS(r, "chene_noir", 35, "chene", 25, "pale", 20, "mort", 10, "grand_chene", 10)
                    : pickS(r, "chene", 45, "bouleau", 20, "grand_chene", 15, light ? "cerisier" : "chene", 10, "azalee", 10);
            case MOOR -> pickS(r, "sapin", 45, "chene", 25, "mort", 20, "azalee", 10);
            case HILLS -> pickS(r, "chene", 35, "bouleau", 30, "sapin", 25, "grand_chene", 10);
            case ALP -> pickS(r, "sapin", 70, "bouleau", 30);
            case FOREST -> dark ? pickS(r, "chene_noir", 50, "pale", 50)
                    : pickS(r, "bouleau", 40, "grand_chene", 30, light ? "cerisier" : "chene", 30);
            case DARK_FOREST -> pickS(r, "pale", 40, "chene_noir", 60);
            case PINES -> pickS(r, "sapin", 70, "mort", 30);
            case OASIS -> pickS(r, "palmier", 70, "azalee", 30);
            case MARSH -> pickS(r, "marais", 60, "mort", 40);
            case SAND, MESA, VOLCANIC -> "mort";
            case ASH -> "calcine";
            case ROCK, SNOW -> "sapin";
            default -> null;
        };
    }

    private boolean roadAt(int x, int z, double margin) {
        return data.chunkHasRoad(x >> 4, z >> 4) && data.road(x, z, margin, hit);
    }

    private void trees(Paint pt, String chunkStyle) {
        int reach = 6;
        for (int i = Math.floorDiv(pt.x0 - reach, TREE_CELL); i <= Math.floorDiv(pt.x0 + 15 + reach, TREE_CELL); i++) {
            for (int j = Math.floorDiv(pt.z0 - reach, TREE_CELL); j <= Math.floorDiv(pt.z0 + 15 + reach, TREE_CELL); j++) {
                int x = i * TREE_CELL + (int) (rnd(i, j, 7301) * TREE_CELL), z = j * TREE_CELL + (int) (rnd(i, j, 7302) * TREE_CELL);
                if (!pt.touches(x, z, reach)) {
                    continue;
                }
                gen.column(x, z, false, hit, col);
                if (col.fluid != NONE) {
                    continue;
                }
                int b = col.biome, g = col.top;
                double p = treeBase(b) + treeGrove(b) * grove(x, z);
                if (p <= 0 || rnd(i, j, 7303) >= p) {
                    continue;
                }
                if (crowded(x, z, 5) || roadAt(x, z, 3) || nearFeature(x, z, 3)) {
                    continue;
                }
                String type = treeType(b, style(x, z), rnd(i, j, 7304));
                if (type == null) {
                    continue;
                }
                Draw d = new Draw(pt, x, z, 0, chunkStyle, (int) (rnd(i, j, 7305) * 1_000_000));
                drawTree(d, type, g);
                d.flush();
                if (pt.in(x, z)) {
                    counts.merge("arbres", 1, Integer::sum);
                }
            }
        }
    }

    private void drawTree(Draw d, String type, int g) {
        int h;
        switch (type) {
            case "chene" -> roundTree(d, g, "oak", 4 + (int) (d.r(0, 0, 1) * 3), 2.6, 2.1);
            case "bouleau" -> roundTree(d, g, "birch", 5 + (int) (d.r(0, 0, 1) * 3), 2.2, 2.3);
            case "grand_chene" -> bigTree(d, g);
            case "sapin" -> spruce(d, g);
            case "mort" -> deadTree(d, g, d.r(0, 0, 2) < 0.5 ? "oak_log" : "spruce_log");
            case "calcine" -> deadTree(d, g, "stripped_dark_oak_log");
            case "cerisier" -> {
                h = 4 + (int) (d.r(0, 0, 1) * 2);
                d.leaves(0, g + h + 0.5, 0, 3.4, 1.6, 3.4, "cherry_leaves", 31);
                for (int y = 1; y <= h; y++) {
                    d.put(0, g + y, 0, "cherry_log[axis=y]");
                }
                String[] dirs = {"north", "east", "south", "west"};
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dx = -3; dx <= 3; dx++) {
                        if (Math.hypot(dx, dz) <= 3.2 && (dx != 0 || dz != 0) && d.r(dx, dz, 32) < 0.45) {
                            d.cover(dx, d.g(dx, dz) + 1, dz, "pink_petals[flower_amount=" + (1 + (int) (d.r(dx, dz, 33) * 4))
                                    + ",facing=" + dirs[(int) (d.r(dx, dz, 34) * 4)] + "]");
                        }
                    }
                }
            }
            case "chene_noir" -> wideTree(d, g, "dark_oak", false);
            case "pale" -> wideTree(d, g, "pale_oak", true);
            case "palmier" -> palm(d, g);
            case "marais" -> swampTree(d, g);
            case "azalee" -> {
                d.blob(0, g + 2.6, 0, 1.9, 1.4, 1.9, new String[]{"azalea_leaves[persistent=true]", "flowering_azalea_leaves[persistent=true]"}, 41, SOFT);
                d.put(0, g + 1, 0, "oak_log[axis=y]");
                d.put(0, g + 2, 0, "oak_log[axis=y]");
            }
            default -> {
            }
        }
    }

    private void roundTree(Draw d, int g, String wood, int h, double rad, double ry) {
        d.leaves(0, g + h - 0.5, 0, rad, ry, rad, wood + "_leaves", 11);
        d.put(0, g + h + 1, 0, wood + "_leaves[persistent=true]");
        for (int y = 1; y <= h; y++) {
            d.put(0, g + y, 0, wood + "_log[axis=y]");
        }
    }

    private void bigTree(Draw d, int g) {
        int h = 6 + (int) (d.r(0, 0, 1) * 3);
        d.leaves(0, g + h, 0, 3.0, 2.3, 3.0, "oak_leaves", 12);
        int n = 2 + (int) (d.r(0, 0, 2) * 2);
        List<int[]> branch = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            double a = d.r(k, 0, 3) * 6.28;
            int y0 = g + h - 2 - k;
            int bx = 0, bz = 0, by = y0;
            for (int t = 1; t <= 3; t++) {
                bx = (int) Math.round(Math.cos(a) * t);
                bz = (int) Math.round(Math.sin(a) * t);
                by = y0 + t / 2;
                branch.add(new int[]{bx, by, bz, Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? 0 : 2});
            }
            d.leaves(bx, by + 0.5, bz, 2.2, 1.7, 2.2, "oak_leaves", 13 + k);
        }
        for (int y = 1; y <= h; y++) {
            d.put(0, g + y, 0, "oak_log[axis=y]");
        }
        for (int[] b : branch) {
            d.put(b[0], b[1], b[2], "oak_log[axis=" + (b[3] == 0 ? "x" : "z") + "]");
        }
    }

    private void spruce(Draw d, int g) {
        int h = 7 + (int) (d.r(0, 0, 1) * 4);
        for (int y = g + 3; y <= g + h + 1; y++) {
            int fromTop = g + h + 1 - y;
            double rad = fromTop <= 1 ? 0.6 : Math.min(3.2, 0.8 + fromTop * 0.45) * (fromTop % 2 == 0 ? 1.0 : 0.65);
            int R = (int) Math.ceil(rad);
            for (int dz = -R; dz <= R; dz++) {
                for (int dx = -R; dx <= R; dx++) {
                    double e = dx * dx + dz * dz;
                    if (e <= rad * rad + 0.3 && !(e > rad * rad - 1 && d.r(dx, y * 7 + dz, 2) < 0.3)) {
                        d.put(dx, y, dz, "spruce_leaves[persistent=true]");
                    }
                }
            }
        }
        d.put(0, g + h + 2, 0, "spruce_leaves[persistent=true]");
        for (int y = 1; y <= h; y++) {
            d.put(0, g + y, 0, "spruce_log[axis=y]");
        }
    }

    private void deadTree(Draw d, int g, String log) {
        int h = 3 + (int) (d.r(0, 0, 1) * 4);
        for (int y = 1; y <= h; y++) {
            d.put(0, g + y, 0, log + "[axis=y]");
        }
        int n = 1 + (int) (d.r(0, 0, 2) * 3);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int k = 0; k < n; k++) {
            int[] dir = dirs[(int) (d.r(k, 0, 3) * 4)];
            int y = g + h - k;
            int len = 1 + (int) (d.r(k, 0, 4) * 2);
            for (int t = 1; t <= len; t++) {
                d.put(dir[0] * t, y, dir[1] * t, log + "[axis=" + (dir[0] != 0 ? "x" : "z") + "]");
            }
            if (d.r(k, 0, 5) < 0.5) {
                d.put(dir[0] * len, y + 1, dir[1] * len, log + "[axis=y]");
            }
        }
    }

    private void wideTree(Draw d, int g, String wood, boolean moss) {
        int h = (moss ? 6 : 5) + (int) (d.r(0, 0, 1) * 3);
        d.leaves(0.5, g + h, 0.5, 3.6, 2.0, 3.6, wood + "_leaves", 14);
        d.leaves(0.5, g + h + 1.5, 0.5, 2.2, 1.2, 2.2, wood + "_leaves", 15);
        for (int[] c : new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}) {
            for (int y = d.g(c[0], c[1]) + 1; y <= g + h; y++) {
                d.put(c[0], y, c[1], wood + "_log[axis=y]");
            }
        }
        if (moss) {
            String leaf = wood + "_leaves";
            for (int dz = -3; dz <= 4; dz++) {
                for (int dx = -3; dx <= 4; dx++) {
                    if (!d.in(dx, dz) || d.r(dx, dz, 16) >= 0.18) {
                        continue;
                    }
                    for (int y = g + h - 3; y <= g + h + 2; y++) {
                        BlockData bd = d.planned(dx, y, dz);
                        if (bd != null && bd.getMaterial().name().equalsIgnoreCase(leaf)) {
                            boolean two = d.r(dx, dz, 17) < 0.5;
                            d.put(dx, y - 1, dz, "pale_hanging_moss[tip=" + !two + "]");
                            if (two) {
                                d.put(dx, y - 2, dz, "pale_hanging_moss[tip=true]");
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private void palm(Draw d, int g) {
        int h = 5 + (int) (d.r(0, 0, 1) * 3);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] dir = dirs[(int) (d.r(0, 0, 2) * 4)];
        int tx = 0, tz = 0;
        for (int t = 0; t < h; t++) {
            int off = (int) Math.round(t * t * 0.045);
            tx = dir[0] * off;
            tz = dir[1] * off;
            d.put(tx, g + 1 + t, tz, "jungle_log[axis=y]");
        }
        int top = g + h;
        String L = "jungle_leaves[persistent=true]";
        d.put(tx, top + 1, tz, L);
        for (int ax = -1; ax <= 1; ax++) {
            for (int az = -1; az <= 1; az++) {
                if (ax == 0 && az == 0) {
                    continue;
                }
                int len = ax != 0 && az != 0 ? 2 : 3;
                for (int k = 1; k <= len; k++) {
                    d.put(tx + ax * k, top + 1 - (k == len ? 1 : 0), tz + az * k, L);
                }
            }
        }
    }

    private void swampTree(Draw d, int g) {
        int h = 4 + (int) (d.r(0, 0, 1) * 3);
        d.leaves(0, g + h, 0, 3.4, 1.3, 3.4, "oak_leaves", 18);
        d.leaves(0, g + h + 1, 0, 2.0, 1.0, 2.0, "oak_leaves", 19);
        for (int y = 1; y <= h; y++) {
            d.put(0, g + y, 0, "oak_log[axis=y]");
        }
        String[] faces = {"north", "east", "south", "west"};
        for (int dz = -4; dz <= 4; dz++) {
            for (int dx = -4; dx <= 4; dx++) {
                if (!d.in(dx, dz) || (dx == 0 && dz == 0) || d.r(dx, dz, 20) >= 0.2) {
                    continue;
                }
                for (int y = g + h - 2; y <= g + h + 1; y++) {
                    BlockData bd = d.planned(dx, y, dz);
                    if (bd != null && bd.getMaterial() == Material.OAK_LEAVES) {
                        String face = faces[(int) (d.r(dx, dz, 21) * 4)];
                        int len = 1 + (int) (d.r(dx, dz, 22) * 4);
                        for (int k = 1; k <= len && y - k > d.g(dx, dz); k++) {
                            d.put(dx, y - k, dz, "vine[" + face + "=true]");
                        }
                        break;
                    }
                }
            }
        }
    }

    private static double bushChance(int b) {
        return switch (b) {
            case PLAIN, HILLS -> 0.06;
            case MOOR, PINES, MARSH -> 0.08;
            case FOREST -> 0.12;
            case DARK_FOREST, OASIS -> 0.1;
            case ALP -> 0.03;
            default -> 0;
        };
    }

    private void bushes(Paint pt, String style) {
        for (int i = Math.floorDiv(pt.x0 - 2, BUSH_CELL); i <= Math.floorDiv(pt.x0 + 17, BUSH_CELL); i++) {
            for (int j = Math.floorDiv(pt.z0 - 2, BUSH_CELL); j <= Math.floorDiv(pt.z0 + 17, BUSH_CELL); j++) {
                int x = i * BUSH_CELL + (int) (rnd(i, j, 7311) * BUSH_CELL), z = j * BUSH_CELL + (int) (rnd(i, j, 7312) * BUSH_CELL);
                if (!pt.touches(x, z, 2)) {
                    continue;
                }
                gen.column(x, z, false, hit, col);
                int b = col.biome, g = col.top;
                double p = bushChance(b) * (0.5 + grove(x, z));
                if (col.fluid != NONE || p <= 0 || rnd(i, j, 7313) >= p) {
                    continue;
                }
                if (crowded(x, z, 3) || roadAt(x, z, 2) || nearFeature(x, z, 2)) {
                    continue;
                }
                String leaves = switch (b) {
                    case PINES, MOOR -> "spruce_leaves";
                    case DARK_FOREST -> "dark_oak_leaves";
                    case OASIS -> "jungle_leaves";
                    case MARSH -> "mangrove_leaves";
                    default -> style.equals("ombre") ? "dark_oak_leaves" : (rnd(i, j, 7314) < 0.3
                            ? (rnd(i, j, 7315) < 0.5 ? "flowering_azalea_leaves" : "azalea_leaves") : "oak_leaves");
                };
                Draw d = new Draw(pt, x, z, 0, style, (int) (rnd(i, j, 7316) * 1_000_000));
                double rx = 1.0 + rnd(i, j, 7317) * 0.7, rz = 1.0 + rnd(i, j, 7318) * 0.7;
                d.leaves(0, g + 1.0, 0, rx, 0.9 + rnd(i, j, 7319) * 0.4, rz, leaves, 51);
                d.flush();
            }
        }
    }

    private static double rockChance(int b) {
        return switch (b) {
            case HILLS -> 0.16;
            case ALP, VOLCANIC -> 0.12;
            case ROCK -> 0.25;
            case MOOR -> 0.12;
            case PLAIN -> 0.035;
            case SNOW, PINES -> 0.08;
            case SAND -> 0.03;
            case MESA, FOREST, DARK_FOREST -> 0.05;
            case ASH -> 0.1;
            case OASIS, MARSH -> 0.02;
            default -> 0;
        };
    }

    private void rocks(Paint pt) {
        for (int i = Math.floorDiv(pt.x0 - 3, ROCK_CELL); i <= Math.floorDiv(pt.x0 + 18, ROCK_CELL); i++) {
            for (int j = Math.floorDiv(pt.z0 - 3, ROCK_CELL); j <= Math.floorDiv(pt.z0 + 18, ROCK_CELL); j++) {
                int x = i * ROCK_CELL + (int) (rnd(i, j, 7321) * ROCK_CELL), z = j * ROCK_CELL + (int) (rnd(i, j, 7322) * ROCK_CELL);
                if (!pt.touches(x, z, 3)) {
                    continue;
                }
                gen.column(x, z, false, hit, col);
                int b = col.biome, g = col.top;
                if (col.fluid != NONE || rnd(i, j, 7323) >= rockChance(b)) {
                    continue;
                }
                if (crowded(x, z, 5) || roadAt(x, z, 3) || nearFeature(x, z, 3)) {
                    continue;
                }
                String[] mix = switch (b) {
                    case SAND -> new String[]{"sandstone", "sandstone", "smooth_sandstone", "cut_sandstone"};
                    case MESA -> new String[]{"terracotta", "orange_terracotta", "red_terracotta", "brown_terracotta"};
                    case ASH, VOLCANIC -> new String[]{"blackstone", "basalt[axis=y]", "blackstone", "tuff"};
                    case FOREST, DARK_FOREST, MARSH -> new String[]{"mossy_cobblestone", "mossy_cobblestone", "stone", "andesite"};
                    default -> new String[]{"stone", "stone", "andesite", "andesite", "cobblestone", "mossy_cobblestone", "tuff"};
                };
                Draw d = new Draw(pt, x, z, 0, "neutre", (int) (rnd(i, j, 7324) * 1_000_000));
                double rx = 1.0 + rnd(i, j, 7325) * 1.4, rz = 1.0 + rnd(i, j, 7326) * 1.4, ry = 0.9 + rnd(i, j, 7327) * 1.0;
                d.blob(0, g + 0.3, 0, rx, ry, rz, mix, 61, EARTH);
                int crest = g + (int) Math.floor(0.3 + ry);
                if ((b == FOREST || b == DARK_FOREST || b == MARSH) && d.planned(0, crest, 0) != null) {
                    d.put(0, crest + 1, 0, "moss_carpet");
                }
                d.flush();
                if (pt.in(x, z)) {
                    counts.merge("rochers", 1, Integer::sum);
                }
            }
        }
    }

    private static double logChance(int b) {
        return switch (b) {
            case FOREST, DARK_FOREST -> 0.2;
            case PINES -> 0.15;
            case MARSH -> 0.08;
            case MOOR -> 0.06;
            case PLAIN, HILLS -> 0.05;
            default -> 0;
        };
    }

    private void logs(Paint pt) {
        for (int i = Math.floorDiv(pt.x0 - 6, LOG_CELL); i <= Math.floorDiv(pt.x0 + 21, LOG_CELL); i++) {
            for (int j = Math.floorDiv(pt.z0 - 6, LOG_CELL); j <= Math.floorDiv(pt.z0 + 21, LOG_CELL); j++) {
                int x = i * LOG_CELL + (int) (rnd(i, j, 7331) * LOG_CELL), z = j * LOG_CELL + (int) (rnd(i, j, 7332) * LOG_CELL);
                if (!pt.touches(x, z, 6)) {
                    continue;
                }
                gen.column(x, z, false, hit, col);
                int b = col.biome;
                if (col.fluid != NONE || rnd(i, j, 7333) >= logChance(b) * (b == PLAIN || b == HILLS ? 0.4 + grove(x, z) * 2 : 1)) {
                    continue;
                }
                if (crowded(x, z, 7) || roadAt(x, z, 4) || nearFeature(x, z, 6)) {
                    continue;
                }
                String wood = switch (b) {
                    case PINES, MOOR -> "spruce";
                    case DARK_FOREST -> "dark_oak";
                    case MARSH -> "mangrove";
                    default -> rnd(i, j, 7334) < 0.3 ? "birch" : "oak";
                };
                Draw d = new Draw(pt, x, z, (int) (rnd(i, j, 7335) * 4), "neutre", (int) (rnd(i, j, 7336) * 1_000_000));
                int len = 3 + (int) (rnd(i, j, 7337) * 4);
                int g0 = d.g(0, 0);
                for (int t = 0; t < len; t++) {
                    int g = d.g(t, 0);
                    if (Math.abs(g - g0) > 1) {
                        break;
                    }
                    d.put(t, g + 1, 0, wood + "_log[axis=x]");
                    double n = d.r(t, 0, 1);
                    if (n < 0.35) {
                        d.put(t, g + 2, 0, "moss_carpet");
                    } else if (n < 0.45) {
                        d.put(t, g + 2, 0, n < 0.4 ? "brown_mushroom" : "red_mushroom");
                    }
                }
                d.flush();
            }
        }
    }

    // ================================================================== Herbes et fleurs
    private static double coverChance(int b) {
        return switch (b) {
            case PLAIN -> 0.42;
            case MOOR -> 0.45;
            case HILLS -> 0.35;
            case ALP -> 0.4;
            case FOREST -> 0.3;
            case DARK_FOREST -> 0.25;
            case PINES -> 0.28;
            case OASIS -> 0.45;
            case MARSH -> 0.4;
            case ROCK -> 0.1;
            case SAND -> 0.03;
            case MESA -> 0.025;
            case ASH -> 0.01;
            default -> 0;
        };
    }

    private void cover(Paint pt, String style) {
        boolean roads = data.chunkHasRoad(pt.cx, pt.cz);
        List<Avoid> near = new ArrayList<>();
        List<Avoid> bucket = avoid.get(key(Math.floorDiv(pt.x0 + 8, BUCKET), Math.floorDiv(pt.z0 + 8, BUCKET)));
        if (bucket != null) {
            for (Avoid a : bucket) {
                if (Math.hypot(pt.x0 + 8 - a.x(), pt.z0 + 8 - a.z()) < a.r() + 16) {
                    near.add(a);
                }
            }
        }
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = pt.x0 + lx, z = pt.z0 + lz;
                boolean skip = false;
                for (Avoid a : near) {
                    double dx = x - a.x(), dz = z - a.z();
                    if (a.c() > 0 && dx * dx + dz * dz < (double) a.c() * a.c()) {
                        skip = true;
                        break;
                    }
                }
                if (skip) {
                    continue;
                }
                gen.column(x, z, false, hit, col);
                if (col.fluid != NONE) {
                    continue;
                }
                int b = col.biome, g = col.top;
                double base = coverChance(b);
                if (base <= 0 || rnd(x, z, 7502) >= base * (0.25 + 1.5 * noise(x, z, 11, 7501))) {
                    continue;
                }
                if (g + 2 > pt.maxY || !pt.block(x, g + 1, z).getType().isAir()) {
                    continue;
                }
                if (roads && data.road(x, z, 1.5, hit)) {
                    continue;
                }
                Material ground = pt.block(x, g, z).getType();
                String plant = plantFor(x, z, b, style, ground);
                if (plant == null) {
                    continue;
                }
                if (plant.endsWith("|")) {
                    String base2 = plant.substring(0, plant.length() - 1);
                    if (!pt.block(x, g + 2, z).getType().isAir()) {
                        continue;
                    }
                    pt.set(x, g + 1, z, data(base2 + "[half=lower]", 0), SOFT);
                    pt.set(x, g + 2, z, data(base2 + "[half=upper]", 0), SOFT);
                } else {
                    pt.set(x, g + 1, z, data(plant, 0), SOFT);
                }
            }
        }
    }

    private static final String[] FLOWERS_LIGHT = {"poppy", "dandelion", "oxeye_daisy", "cornflower", "azure_bluet", "white_tulip",
            "pink_tulip", "lily_of_the_valley", "wildflowers", "lilac|", "peony|", "rose_bush|", "sunflower|"};
    private static final String[] FLOWERS_DARK = {"allium", "lily_of_the_valley", "closed_eyeblossom", "open_eyeblossom",
            "azure_bluet", "red_tulip", "white_tulip", "allium"};
    private static final String[] FLOWERS = {"poppy", "dandelion", "cornflower", "oxeye_daisy", "red_tulip", "orange_tulip",
            "azure_bluet", "allium", "wildflowers", "rose_bush|"};

    /** Plante d'une colonne : nom d'état, ou nom suivi de « | » pour une plante de deux blocs. */
    private static String plantFor(int x, int z, int b, String style, Material ground) {
        boolean grassy = GRASSY.contains(ground);
        boolean sandy = ground == Material.SAND || ground == Material.RED_SAND || ground.name().endsWith("TERRACOTTA");
        double r = rnd(x, z, 7503);
        switch (b) {
            case SAND, MESA -> {
                return sandy ? pickS(r, "dead_bush", 40, "short_dry_grass", 45, "tall_dry_grass", 15) : null;
            }
            case ASH, VOLCANIC -> {
                return grassy || sandy ? "short_dry_grass" : null;
            }
            case SNOW -> {
                return null;
            }
            default -> {
            }
        }
        if (!grassy) {
            return null;
        }
        if (noise(x, z, 26, 7511) > 0.66 && b != PINES && b != DARK_FOREST && b != ROCK && r < 0.6) {
            String[] set = style.equals("ombre") ? FLOWERS_DARK : (style.equals("lumiere") ? FLOWERS_LIGHT : FLOWERS);
            int main = (int) (noise(x, z, 70, 7521) * set.length * 1.999) % set.length;
            String f = rnd(x, z, 7522) < 0.72 ? set[main] : set[(int) (rnd(x, z, 7523) * set.length)];
            if (f.equals("wildflowers")) {
                return "wildflowers[flower_amount=" + (1 + (int) (rnd(x, z, 7524) * 4)) + ",facing="
                        + new String[]{"north", "east", "south", "west"}[(int) (rnd(x, z, 7525) * 4)] + "]";
            }
            return f;
        }
        boolean dark = style.equals("ombre");
        String flower = dark ? FLOWERS_DARK[(int) (rnd(x, z, 7526) * FLOWERS_DARK.length)]
                : FLOWERS[(int) (rnd(x, z, 7526) * 8)];
        String litter = "leaf_litter[segment_amount=" + (1 + (int) (rnd(x, z, 7527) * 4)) + ",facing="
                + new String[]{"north", "east", "south", "west"}[(int) (rnd(x, z, 7528) * 4)] + "]";
        return switch (b) {
            case PLAIN -> pickS(r, "short_grass", 72, "tall_grass|", 12, "fern", 3, "bush", 4, flower, 6, dark ? "fern" : "short_grass", 3);
            case MOOR -> pickS(r, "short_grass", 50, "fern", 25, "large_fern|", 8, "bush", 8, dark ? "closed_eyeblossom" : "allium", 3,
                    "short_grass", 6);
            case FOREST -> pickS(r, "short_grass", 40, "fern", 22, "large_fern|", 6, litter, 12, "bush", 10, "firefly_bush", 4, flower, 6);
            case DARK_FOREST -> pickS(r, "fern", 40, "short_grass", 25, litter, 20, "bush", 10, dark ? "closed_eyeblossom" : "fern", 5);
            case PINES -> pickS(r, "fern", 45, "short_grass", 25, "large_fern|", 10, "sweet_berry_bush[age=3]", 5, litter, 15);
            case HILLS -> pickS(r, "short_grass", 70, "tall_grass|", 8, "fern", 8, flower, 8, "bush", 6);
            case ALP -> pickS(r, "short_grass", 50, flower, 35, "tall_grass|", 10, "fern", 5);
            case OASIS -> pickS(r, "short_grass", 50, "tall_grass|", 20, "fern", 15, flower, 15);
            case MARSH -> pickS(r, "short_grass", 45, "tall_grass|", 15, "fern", 20, "blue_orchid", 8, "bush", 8, "firefly_bush", 4);
            case ROCK -> pickS(r, "short_grass", 80, "fern", 20);
            default -> null;
        };
    }
}
