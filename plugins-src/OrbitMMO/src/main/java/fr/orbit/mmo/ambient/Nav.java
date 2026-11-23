package fr.orbit.mmo.ambient;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Déplacements des PNJ d'ambiance : où l'on peut se tenir, et un chemin court entre deux points (A* sur les blocs,
 * dans les chunks déjà chargés seulement). Un PNJ monte une dalle ou une marche d'escalier, jamais un bloc plein :
 * il ne grimpe donc ni sur les étals, ni sur les tonneaux, ni sur les toits.
 */
final class Nav {

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private Nav() {
    }

    static boolean loaded(World w, int x, int z) {
        return w.isChunkLoaded(x >> 4, z >> 4);
    }

    /** Hauteur de collision du bloc (0 = on passe au travers, 1 = bloc plein, 0,5 = dalle, 1,5 = barrière). */
    private static double top(Block b) {
        Material m = b.getType();
        if (m.isAir()) {
            return 0;
        }
        if (b.isLiquid()) {
            return -1;
        }
        double max = 0;
        for (BoundingBox box : b.getCollisionShape().getBoundingBoxes()) {
            max = Math.max(max, box.getMaxY());
        }
        return max;
    }

    /**
     * Hauteur des pieds dans cette colonne, au plus près de {@code nearY} (à 1,6 bloc près), ou NaN s'il n'y a pas la place
     * de se tenir debout (eau, mur, plafond bas, chunk non chargé).
     */
    static double feet(World w, int x, int z, double nearY) {
        if (!loaded(w, x, z)) {
            return Double.NaN;
        }
        double best = Double.NaN;
        int base = (int) Math.floor(nearY);
        for (int y = base + 1; y >= base - 2; y--) {
            if (y <= w.getMinHeight() || y >= w.getMaxHeight() - 3) {
                continue;
            }
            Block ground = w.getBlockAt(x, y - 1, z);
            double h = top(ground);
            if (h <= 0 || h > 1.01 || Tag.LEAVES.isTagged(ground.getType())) {
                continue;
            }
            double f = y - 1 + h;
            if (Math.abs(f - nearY) > 1.6) {
                continue;
            }
            // Deux blocs libres au-dessus des pieds (un tapis ou une plaque de pression ne gênent pas).
            int fy = (int) Math.floor(f + 0.01);
            double extra = 0;
            if (fy == y - 1) {
                // Dalle : les pieds sont dans la moitié haute du bloc du sol.
                if (top(w.getBlockAt(x, y, z)) != 0 || top(w.getBlockAt(x, y + 1, z)) != 0) {
                    continue;
                }
            } else {
                extra = top(w.getBlockAt(x, fy, z));
                if (extra < 0 || extra > 0.2 || top(w.getBlockAt(x, fy + 1, z)) != 0) {
                    continue;
                }
            }
            if (Double.isNaN(best) || Math.abs(f - nearY) < Math.abs(best - nearY)) {
                best = f + extra;
            }
        }
        return best;
    }

    /** Hauteur des pieds sur le sol naturel de cette colonne (hors des villes), ou NaN (eau, arbre, chunk non chargé). */
    static double ground(World w, int x, int z) {
        if (!loaded(w, x, z)) {
            return Double.NaN;
        }
        int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        return feet(w, x, z, y + 1);
    }

    /** Vrai si rien ne couvre cette colonne au-dessus des pieds : dehors, et pas sous un toit. */
    static boolean open(World w, int x, int z, double feetY) {
        return w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) < feetY + 0.5;
    }

    private static boolean stairs(World w, int x, double feetY, int z) {
        Material m = w.getBlockAt(x, (int) Math.floor(feetY - 0.01), z).getType();
        return Tag.STAIRS.isTagged(m) || Tag.SLABS.isTagged(m);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static final class Node implements Comparable<Node> {
        final int x, z;
        final double y, g, f;
        final Node from;

        Node(int x, int z, double y, double g, double f, Node from) {
            this.x = x;
            this.z = z;
            this.y = y;
            this.g = g;
            this.f = f;
            this.from = from;
        }

        @Override
        public int compareTo(Node o) {
            return Double.compare(f, o.f);
        }
    }

    /**
     * Chemin à pied de (fx, fy, fz) à la colonne (tx, tz), une case par bloc, ou null s'il n'y en a pas en
     * {@code budget} cases explorées. Le premier point est la case de départ, le dernier la case d'arrivée.
     */
    static List<Vector> path(World w, double fx, double fy, double fz, int tx, int tz, int budget) {
        int sx = (int) Math.floor(fx), sz = (int) Math.floor(fz);
        double sy = feet(w, sx, sz, fy);
        if (Double.isNaN(sy)) {
            return null;
        }
        Map<Long, Double> seen = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>();
        open.add(new Node(sx, sz, sy, 0, Math.hypot(tx - sx, tz - sz), null));
        seen.put(key(sx, sz), 0.0);
        int explored = 0;
        while (!open.isEmpty() && explored++ < budget) {
            Node n = open.poll();
            if (n.x == tx && n.z == tz) {
                List<Vector> out = new ArrayList<>();
                for (Node k = n; k != null; k = k.from) {
                    out.add(new Vector(k.x + 0.5, k.y, k.z + 0.5));
                }
                Collections.reverse(out);
                return out;
            }
            for (int[] d : DIRS) {
                int nx = n.x + d[0], nz = n.z + d[1];
                double ny = feet(w, nx, nz, n.y);
                if (Double.isNaN(ny)) {
                    continue;
                }
                double rise = ny - n.y;
                // Un bloc plein ne se monte ni ne se descend que par une dalle ou un escalier.
                if (Math.abs(rise) > 0.6 && !(Math.abs(rise) <= 1.01 && (stairs(w, nx, ny, nz) || stairs(w, n.x, n.y, n.z)))) {
                    continue;
                }
                if (d[0] != 0 && d[1] != 0) {
                    // En diagonale, les deux cases voisines doivent être libres et de plain-pied.
                    double a = feet(w, n.x + d[0], n.z, n.y), b = feet(w, n.x, n.z + d[1], n.y);
                    if (Double.isNaN(a) || Double.isNaN(b) || Math.abs(a - n.y) > 0.6 || Math.abs(b - n.y) > 0.6 || Math.abs(rise) > 0.6) {
                        continue;
                    }
                }
                double g = n.g + (d[0] != 0 && d[1] != 0 ? 1.414 : 1) + Math.abs(rise) * 0.5;
                Double old = seen.get(key(nx, nz));
                if (old != null && old <= g) {
                    continue;
                }
                seen.put(key(nx, nz), g);
                open.add(new Node(nx, nz, ny, g, g + Math.hypot(tx - nx, tz - nz), n));
            }
        }
        return null;
    }
}
