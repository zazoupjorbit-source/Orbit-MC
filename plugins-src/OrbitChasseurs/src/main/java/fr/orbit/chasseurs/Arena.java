package fr.orbit.chasseurs;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Une arène dans le vide : une sphère de rayon {@link #R} autour de (cx, CY, cz). Tout le décor est calculé
 * en mémoire dans une grille de voxels (la même à chaque démarrage : graine fixe), qui sert à la fois à poser
 * les blocs dans le monde et à calculer les collisions et les tirs — ce que l'on voit est ce que l'on touche.
 * La cage de verre qui marque la limite de zone ne bloque rien (indices ≥ 64).
 */
final class Arena {

    static final int R = 76, HALF = 78, N = 2 * HALF + 1, CY = 160, VERSION = 3;

    enum Theme { CEINTURE, EPAVE, ROUTE }

    record Ring(Vector3d c, Vector3d n, double r) {
    }

    private static final int DEEPSLATE = 1, COBBLED = 2, TUFF = 3, ANDESITE = 4, BLACKSTONE = 5, AMETHYST = 6, RAW_GOLD = 7, PACKED_ICE = 8,
            BLUE_ICE = 9, SNOW = 10, IRON = 11, LIGHT_GRAY = 12, WHITE = 13, BLUE = 14, GOLD = 15, WINDOW = 16, LANTERN = 17, LAPIS = 18,
            POLISHED = 20, RAW_IRON = 21, GRAY = 22, CRYING = 23, CALCITE = 24, BASALT = 25, MAGMA = 26, CAGE = 64, CAGE_LIGHT = 65;
    private static final Material[] PAL = new Material[66];

    static {
        PAL[DEEPSLATE] = Material.DEEPSLATE;
        PAL[COBBLED] = Material.COBBLED_DEEPSLATE;
        PAL[TUFF] = Material.TUFF;
        PAL[ANDESITE] = Material.ANDESITE;
        PAL[BLACKSTONE] = Material.BLACKSTONE;
        PAL[AMETHYST] = Material.AMETHYST_BLOCK;
        PAL[RAW_GOLD] = Material.RAW_GOLD_BLOCK;
        PAL[PACKED_ICE] = Material.PACKED_ICE;
        PAL[BLUE_ICE] = Material.BLUE_ICE;
        PAL[SNOW] = Material.SNOW_BLOCK;
        PAL[IRON] = Material.IRON_BLOCK;
        PAL[LIGHT_GRAY] = Material.LIGHT_GRAY_CONCRETE;
        PAL[WHITE] = Material.WHITE_CONCRETE;
        PAL[BLUE] = Material.BLUE_CONCRETE;
        PAL[GOLD] = Material.GOLD_BLOCK;
        PAL[WINDOW] = Material.LIGHT_BLUE_STAINED_GLASS;
        PAL[LANTERN] = Material.SEA_LANTERN;
        PAL[LAPIS] = Material.LAPIS_BLOCK;
        PAL[POLISHED] = Material.POLISHED_DEEPSLATE;
        PAL[RAW_IRON] = Material.RAW_IRON_BLOCK;
        PAL[GRAY] = Material.GRAY_CONCRETE;
        PAL[CRYING] = Material.CRYING_OBSIDIAN;
        PAL[CALCITE] = Material.CALCITE;
        PAL[BASALT] = Material.SMOOTH_BASALT;
        PAL[MAGMA] = Material.MAGMA_BLOCK;
        PAL[CAGE] = Material.LIGHT_BLUE_STAINED_GLASS;
        PAL[CAGE_LIGHT] = Material.SEA_LANTERN;
    }

    final int slot;
    final Theme theme;
    final World world;
    final int cx, cz;
    final List<Ring> rings = new ArrayList<>();
    final List<Vector3d> spawns = new ArrayList<>();
    final List<Vector3d> pickups = new ArrayList<>();
    private final byte[] vox = new byte[N * N * N];
    private final Random rnd;
    private final int seed;
    volatile boolean ready;
    boolean building;
    int blocks;

    Arena(int slot, Theme theme, World world) {
        this.slot = slot;
        this.theme = theme;
        this.world = world;
        this.cx = slot * 1000;
        this.cz = 0;
        this.seed = 7919 * (slot + 1) + VERSION;
        this.rnd = new Random(seed);
        generate();
    }

    Vector3d centre() {
        return new Vector3d(cx + 0.5, CY + 0.5, cz + 0.5);
    }

    // ------------------------------------------------------------------ requêtes (coordonnées du monde)

    private static int idx(int lx, int ly, int lz) {
        return (lx * N + lz) * N + ly;
    }

    private int at(int lx, int ly, int lz) {
        if (lx < 0 || ly < 0 || lz < 0 || lx >= N || ly >= N || lz >= N) {
            return 0;
        }
        return vox[idx(lx, ly, lz)];
    }

    boolean solid(double x, double y, double z) {
        int v = at((int) Math.floor(x) - cx + HALF, (int) Math.floor(y) - CY + HALF, (int) Math.floor(z) - cz + HALF);
        return v != 0 && v < 64;
    }

    /** Un volume de rayon r autour du point touche-t-il le décor ? (centre + 6 points) */
    boolean touches(double x, double y, double z, double r) {
        return solid(x, y, z) || solid(x + r, y, z) || solid(x - r, y, z) || solid(x, y + r, z) || solid(x, y - r, z)
                || solid(x, y, z + r) || solid(x, y, z - r);
    }

    /** Distance jusqu'au premier bloc du décor le long du rayon, ou -1 si rien avant max. */
    double ray(Vector3d o, Vector3d d, double max) {
        for (double t = 0.4; t <= max; t += 0.4) {
            if (solid(o.x + d.x * t, o.y + d.y * t, o.z + d.z * t)) {
                return t;
            }
        }
        return -1;
    }

    double fromCentre(Vector3d p) {
        double dx = p.x - cx - 0.5, dy = p.y - CY - 0.5, dz = p.z - cz - 0.5;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Point libre (rien à moins de {@code clear} blocs) entre deux rayons du centre. */
    Vector3d freePoint(Random r, double min, double max, double clear) {
        for (int i = 0; i < 80; i++) {
            Vector3d dir = new Vector3d(r.nextGaussian(), r.nextGaussian() * 0.55, r.nextGaussian());
            if (dir.lengthSquared() < 0.01) {
                continue;
            }
            dir.normalize().mul(min + r.nextDouble() * (max - min)).add(centre());
            if (!touches(dir.x, dir.y, dir.z, clear) && !touches(dir.x, dir.y, dir.z, clear / 2)) {
                return dir;
            }
        }
        return centre().add(0, max, 0);
    }

    /** Trajet du cargo (arène ROUTE) : boucle fermée, t en radians. */
    Vector3d path(double t) {
        return new Vector3d(cx + 0.5 + 48 * Math.cos(t), CY + 0.5 + 12 * Math.sin(2 * t), cz + 0.5 + 48 * Math.sin(t));
    }

    // ------------------------------------------------------------------ génération

    private static double hash(int x, int y, int z, int seed) {
        int h = x * 374761393 + y * 668265263 + z * 2147483629 + seed * 144665;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    /** Bruit de valeur lissé, entre -1 et 1. */
    static double noise(double x, double y, double z, int seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double fx = smooth(x - x0), fy = smooth(y - y0), fz = smooth(z - z0);
        double v = 0;
        for (int i = 0; i < 8; i++) {
            int dx = i & 1, dy = (i >> 1) & 1, dz = (i >> 2) & 1;
            v += hash(x0 + dx, y0 + dy, z0 + dz, seed) * (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
        }
        return v * 2 - 1;
    }

    private void set(int lx, int ly, int lz, int v) {
        if (lx < 0 || ly < 0 || lz < 0 || lx >= N || ly >= N || lz >= N) {
            return;
        }
        double dx = lx - HALF, dy = ly - HALF, dz = lz - HALF;
        if (v < 64 && v != 0 && dx * dx + dy * dy + dz * dz > (R - 3) * (R - 3)) {
            return; // rien de solide contre la cage
        }
        vox[idx(lx, ly, lz)] = (byte) v;
    }

    private void generate() {
        switch (theme) {
            case CEINTURE -> {
                asteroid(0, 0, 0, 12, 11, 12, 1); // le Noyau, veiné d'or
                for (int i = 0; i < 4; i++) {
                    scatter(30, 58, 9 + rnd.nextInt(3), 0);
                }
                for (int i = 0; i < 30; i++) {
                    scatter(20, 66, 3 + rnd.nextInt(5), rnd.nextInt(6) == 0 ? 2 : 0);
                }
                for (int i = 0; i < 4; i++) {
                    scatter(30, 64, 4 + rnd.nextInt(3), 3);
                }
                tube(34, -20, -26, 16, 6, 4.6, true);
                for (int i = 0; i < 5; i++) {
                    ring(40 + rnd.nextInt(12), i * 1.2566 + 0.4, rnd.nextInt(30) - 15, 6);
                }
            }
            case EPAVE -> {
                station();
                for (int i = 0; i < 20; i++) {
                    scatter(36, 66, 3 + rnd.nextInt(6), rnd.nextInt(5) == 0 ? 2 : 0);
                }
                for (int i = 0; i < 4; i++) {
                    ring(46 + rnd.nextInt(8), i * 1.5708 + 0.8, rnd.nextInt(24) - 12, 6);
                }
            }
            case ROUTE -> {
                for (int i = 0; i < 28; i++) {
                    scatter(8, 66, 3 + rnd.nextInt(6), rnd.nextInt(4) == 0 ? 0 : 3);
                }
                asteroid(0, 0, 0, 14, 12, 13, 3);
                // le couloir du convoi reste libre
                for (double t = 0; t < Math.PI * 2; t += 0.02) {
                    Vector3d p = path(t);
                    carve(p.x - cx, p.y - CY, p.z - cz, 10);
                }
                // anneaux-balises que le cargo traverse
                for (int i = 0; i < 4; i++) {
                    double t = i * Math.PI / 2 + 0.6;
                    Vector3d p = path(t), q = path(t + 0.01);
                    ringAt(new Vector3d(p.x - cx - 0.5, p.y - CY - 0.5, p.z - cz - 0.5), q.sub(p).normalize(), 9, GOLD);
                }
            }
        }
        // points de départ : 8 autour, dégagés
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + 0.39, y = (i % 2 == 0 ? 10 : -10);
            Vector3d p = new Vector3d(Math.cos(a) * 56, y, Math.sin(a) * 56);
            carve(p.x, p.y, p.z, 8);
            spawns.add(p.add(cx + 0.5, CY + 0.5, cz + 0.5));
        }
        cage();
        Random pr = new Random(seed * 31L);
        for (int i = 0; i < 9; i++) {
            pickups.add(freePoint(pr, 14, 60, 4));
        }
        for (byte b : vox) {
            if (b != 0) {
                blocks++;
            }
        }
    }

    /** Astéroïde posé au hasard entre deux rayons du centre. kind : 0 roche, 1 roche à or, 2 roche à améthyste, 3 glace. */
    private void scatter(double min, double max, int r, int kind) {
        Vector3d p = new Vector3d(rnd.nextGaussian(), rnd.nextGaussian() * 0.6, rnd.nextGaussian());
        if (p.lengthSquared() < 0.01) {
            p.set(1, 0, 0);
        }
        p.normalize().mul(min + rnd.nextDouble() * (max - min));
        asteroid(p.x, p.y, p.z, r * (0.75 + rnd.nextDouble() * 0.5), r * (0.7 + rnd.nextDouble() * 0.5), r * (0.75 + rnd.nextDouble() * 0.5), kind);
    }

    private void asteroid(double x, double y, double z, double rx, double ry, double rz, int kind) {
        int s = rnd.nextInt(100000);
        int m = (int) Math.ceil(Math.max(rx, Math.max(ry, rz)) * 1.35) + 1;
        for (int dx = -m; dx <= m; dx++) {
            for (int dy = -m; dy <= m; dy++) {
                for (int dz = -m; dz <= m; dz++) {
                    double d = Math.sqrt(dx * dx / (rx * rx) + dy * dy / (ry * ry) + dz * dz / (rz * rz));
                    double wx = x + dx, wy = y + dy, wz = z + dz;
                    double lump = noise(wx * 0.16, wy * 0.16, wz * 0.16, s) * 0.3 + noise(wx * 0.45, wy * 0.45, wz * 0.45, s + 1) * 0.1;
                    if (d > 1 + lump) {
                        continue;
                    }
                    double vein = noise(wx * 0.3, wy * 0.3, wz * 0.3, s + 2), grain = hash((int) wx, (int) wy, (int) wz, s + 3);
                    int v;
                    if (kind == 3) {
                        v = vein > 0.45 ? BLUE_ICE : grain < 0.2 ? SNOW : grain < 0.3 ? CALCITE : PACKED_ICE;
                    } else {
                        v = grain < 0.45 ? DEEPSLATE : grain < 0.7 ? COBBLED : grain < 0.85 ? TUFF : grain < 0.93 ? BASALT : BLACKSTONE;
                        if (kind == 1 && vein > 0.5) {
                            v = grain < 0.6 ? RAW_GOLD : GOLD;
                        } else if (kind == 2 && vein > 0.45) {
                            v = grain < 0.85 ? AMETHYST : CRYING;
                        } else if (kind == 0 && vein > 0.62) {
                            v = grain < 0.7 ? RAW_IRON : MAGMA;
                        }
                    }
                    set((int) Math.round(wx) + HALF, (int) Math.round(wy) + HALF, (int) Math.round(wz) + HALF, v);
                }
            }
        }
    }

    private void carve(double x, double y, double z, double r) {
        int m = (int) Math.ceil(r);
        for (int dx = -m; dx <= m; dx++) {
            for (int dy = -m; dy <= m; dy++) {
                for (int dz = -m; dz <= m; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= r * r) {
                        int lx = (int) Math.round(x) + dx + HALF, ly = (int) Math.round(y) + dy + HALF, lz = (int) Math.round(z) + dz + HALF;
                        if (lx >= 0 && ly >= 0 && lz >= 0 && lx < N && ly < N && lz < N) {
                            vox[idx(lx, ly, lz)] = 0;
                        }
                    }
                }
            }
        }
    }

    private void box(int x1, int y1, int z1, int x2, int y2, int z2, int v) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x + HALF, y + HALF, z + HALF, v);
                }
            }
        }
    }

    /** Tronçon de coque creux, le long de x. Traversable de bout en bout ; hublots et bandes ORBIT. */
    private void tube(int x, int y, int z, int halfLen, double ro, double ri, boolean torn) {
        int s = rnd.nextInt(100000);
        int m = (int) Math.ceil(ro);
        for (int dx = -halfLen; dx <= halfLen; dx++) {
            for (int dy = -m; dy <= m; dy++) {
                for (int dz = -m; dz <= m; dz++) {
                    double d = Math.sqrt(dy * dy + dz * dz);
                    if (d > ro || d < ri) {
                        continue;
                    }
                    // bords déchirés
                    double edge = halfLen - Math.abs(dx);
                    if (torn && edge < 5 && noise((x + dx) * 0.4, (y + dy) * 0.4, (z + dz) * 0.4, s) > (edge - 1) * 0.3 - 0.4) {
                        continue;
                    }
                    int v = LIGHT_GRAY;
                    if (Math.floorMod(dx, 8) == 0) {
                        v = POLISHED;
                    } else if (Math.abs(dy) <= 1 && Math.floorMod(dx, 8) >= 3 && Math.floorMod(dx, 8) <= 5) {
                        v = WINDOW;
                    } else if (dy >= m - 2) {
                        v = WHITE;
                    } else if (dy <= -m + 2) {
                        v = Math.floorMod(dx, 16) < 8 ? BLUE : GRAY;
                    }
                    if (Math.floorMod(dx, 8) == 4 && dz == 0 && dy < 0) {
                        v = LANTERN;
                    }
                    set(x + dx + HALF, y + dy + HALF, z + dz + HALF, v);
                }
            }
        }
    }

    private void shell(int x, int y, int z, double ro, double ri, int v, int band) {
        int m = (int) Math.ceil(ro);
        for (int dx = -m; dx <= m; dx++) {
            for (int dy = -m; dy <= m; dy++) {
                for (int dz = -m; dz <= m; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d <= ro && d >= ri) {
                        set(x + dx + HALF, y + dy + HALF, z + dz + HALF, dy == 0 ? band : Math.abs(dy) == 3 && (dx + dz) % 4 == 0 ? LANTERN : v);
                    }
                }
            }
        }
    }

    /** L'épave de la station Hélios : deux tronçons séparés, un module sphérique éventré, des panneaux solaires brisés. */
    private void station() {
        tube(-17, 0, 0, 15, 8, 6.4, true);
        tube(20, 3, 2, 12, 8, 6.4, true);
        shell(-40, 0, 0, 9, 7.6, WHITE, GOLD);
        carve(-46, 4, 5, 6);
        carve(-33, 0, 0, 5.5);
        // bras et panneaux solaires (certains arrachés)
        int[][] arms = {{-22, 1}, {-10, -1}, {16, 1}, {26, -1}};
        for (int[] arm : arms) {
            int ax = arm[0], dir = arm[1], y = ax > 0 ? 3 : 0;
            box(ax, y, dir * 9, ax, y, dir * 30, IRON);
            for (int z = 12; z <= 30; z++) {
                for (int x = -5; x <= 5; x++) {
                    if (noise((ax + x) * 0.2, z * 0.2, dir, seed + 5) > 0.42) {
                        continue; // trou
                    }
                    set(ax + x + HALF, y + HALF, dir * z + HALF, x == 0 || z % 6 == 0 ? IRON : (x + z) % 9 == 0 ? LANTERN : LAPIS);
                }
            }
        }
        // mât et antenne
        box(2, 4, 0, 2, 22, 0, IRON);
        box(-1, 22, -3, 5, 22, 3, GRAY);
        box(2, 23, 0, 2, 26, 0, GOLD);
        // débris
        for (int i = 0; i < 16; i++) {
            int x = rnd.nextInt(80) - 40, y = rnd.nextInt(40) - 20, z = rnd.nextInt(60) - 30;
            if (Math.abs(y) < 11 && Math.abs(z) < 11) {
                continue;
            }
            int w = rnd.nextInt(3), h = rnd.nextInt(2), d = rnd.nextInt(3);
            box(x, y, z, x + w, y + h, z + d, rnd.nextBoolean() ? LIGHT_GRAY : rnd.nextBoolean() ? IRON : LAPIS);
        }
    }

    /** Anneau à traverser, à la distance dist du centre dans la direction angle. */
    private void ring(double dist, double angle, double y, double r) {
        Vector3d c = new Vector3d(Math.cos(angle) * dist, y, Math.sin(angle) * dist);
        double a = rnd.nextDouble() * Math.PI * 2;
        ringAt(c, new Vector3d(Math.cos(a), (rnd.nextDouble() - 0.5) * 0.4, Math.sin(a)).normalize(), r, BLUE);
    }

    private void ringAt(Vector3d c, Vector3d n, double r, int accent) {
        carve(c.x, c.y, c.z, r + 3);
        Vector3d u = Math.abs(n.y) > 0.9 ? new Vector3d(1, 0, 0) : new Vector3d(0, 1, 0);
        u = new Vector3d(n).cross(u).normalize();
        Vector3d v = new Vector3d(n).cross(u).normalize();
        int steps = (int) (r * 14);
        for (int i = 0; i < steps; i++) {
            double a = i * Math.PI * 2 / steps;
            for (double w = -0.5; w <= 0.5; w += 0.5) {
                double rr = r + w;
                double x = c.x + (u.x * Math.cos(a) + v.x * Math.sin(a)) * rr, y = c.y + (u.y * Math.cos(a) + v.y * Math.sin(a)) * rr,
                        z = c.z + (u.z * Math.cos(a) + v.z * Math.sin(a)) * rr;
                int seg = (int) (a / (Math.PI * 2) * 16);
                set((int) Math.round(x) + HALF, (int) Math.round(y) + HALF, (int) Math.round(z) + HALF, seg % 2 == 0 ? LANTERN : accent);
            }
        }
        rings.add(new Ring(new Vector3d(c).add(cx + 0.5, CY + 0.5, cz + 0.5), new Vector3d(n), r));
    }

    /** Cage de verre : méridiens et parallèles tous les 15°, balises lumineuses aux croisements. */
    private void cage() {
        for (int k = 0; k < 24; k++) {
            double th = Math.toRadians(k * 15);
            for (double ph = -90; ph <= 90; ph += 0.35) {
                cagePoint(th, Math.toRadians(ph), Math.abs(Math.round(ph / 15.0) * 15 - ph) < 0.4);
            }
        }
        for (int k = -5; k <= 5; k++) {
            double ph = Math.toRadians(k * 15);
            for (double th = 0; th < 360; th += 0.3) {
                cagePoint(Math.toRadians(th), ph, false);
            }
        }
    }

    private void cagePoint(double th, double ph, boolean light) {
        int lx = (int) Math.round(R * Math.cos(ph) * Math.cos(th)) + HALF, ly = (int) Math.round(R * Math.sin(ph)) + HALF,
                lz = (int) Math.round(R * Math.cos(ph) * Math.sin(th)) + HALF;
        if (lx < 0 || ly < 0 || lz < 0 || lx >= N || ly >= N || lz >= N) {
            return;
        }
        int i = idx(lx, ly, lz);
        if (vox[i] == 0 || vox[i] == CAGE) {
            vox[i] = (byte) (light ? CAGE_LIGHT : CAGE);
        }
    }

    // ------------------------------------------------------------------ pose dans le monde

    private NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, "arene_" + slot);
    }

    boolean built(Plugin plugin) {
        Integer v = world.getPersistentDataContainer().get(key(plugin), PersistentDataType.INTEGER);
        return v != null && v == seed;
    }

    /**
     * Pose les blocs par petites tranches (20 ms par tick au plus). {@code clear} : efface aussi ce qui dépasse
     * d'une ancienne version. L'arène est jouable ({@link #ready}) à la fin.
     */
    void build(Plugin plugin, boolean clear, Runnable done) {
        if (building) {
            return;
        }
        building = true;
        ready = false;
        new BukkitRunnable() {
            int i = 0;

            @Override
            public void run() {
                long start = System.nanoTime();
                while (i < vox.length && System.nanoTime() - start < 20_000_000L) {
                    int end = Math.min(vox.length, i + 4096);
                    for (; i < end; i++) {
                        int v = vox[i];
                        if (v == 0 && !clear) {
                            continue;
                        }
                        int ly = i % N, lz = (i / N) % N, lx = i / (N * N);
                        Block b = world.getBlockAt(lx - HALF + cx, ly - HALF + CY, lz - HALF + cz);
                        Material want = v == 0 ? Material.AIR : PAL[v];
                        if (b.getType() != want) {
                            b.setType(want, false);
                        }
                    }
                }
                if (i >= vox.length) {
                    cancel();
                    world.getPersistentDataContainer().set(key(plugin), PersistentDataType.INTEGER, seed);
                    building = false;
                    ready = true;
                    if (done != null) {
                        done.run();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Garde les chunks de l'arène chargés pendant une partie (les bots volent loin des joueurs). */
    void hold(Plugin plugin, boolean on) {
        for (int x = (cx - HALF) >> 4; x <= (cx + HALF) >> 4; x++) {
            for (int z = (cz - HALF) >> 4; z <= (cz + HALF) >> 4; z++) {
                if (on) {
                    world.addPluginChunkTicket(x, z, plugin);
                } else {
                    world.removePluginChunkTicket(x, z, plugin);
                }
            }
        }
    }
}
