package fr.orbit.devcore.station;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrôle d'étanchéité de la station : l'air où l'on marche ne doit jamais communiquer avec le vide.
 *
 * Principe : une case d'air est « enfermée » si, dans les six directions, on bute sur un bloc plein (mur, sol,
 * verre) avant de sortir de la station. On part des points connus (spawn, portes, PNJ) et on avance de case
 * enfermée en case enfermée ; dès qu'une case voisine voit le vide, c'est une ouverture. Dans une station
 * fermée, tout l'intérieur est enfermé et la liste des ouvertures est vide.
 *
 * Calcul pur sur le plan (aucun accès au monde) : il tourne hors du thread principal.
 */
public final class Airtight {

    /** Ouverture trouvée : case dans le repère de la station, direction du vide, nombre de cases touchées. */
    public record Hole(int x, int y, int z, String toward, int cells) { }

    /** Résultat : cases d'air à l'intérieur, ouvertures, et points repères qui sont eux-mêmes à l'air libre. */
    public record Report(int inside, List<Hole> holes, List<String> exposed) {
        public boolean sealed() {
            return inside > 0 && holes.isEmpty() && exposed.isEmpty();
        }
    }

    private static final int PAD = 2;
    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final String[] NAMES = {"l'est", "l'ouest", "le haut", "le bas", "le sud", "le nord"};
    private static final BlockFace[] FACES = {BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN, BlockFace.SOUTH, BlockFace.NORTH};

    private Airtight() {
    }

    public static Report check(StationPlan plan) {
        Map<BlockData, Boolean> seals = new IdentityHashMap<>();
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (Map.Entry<StationPlan.Pos, BlockData> e : plan.blocks().entrySet()) {
            if (!seals.computeIfAbsent(e.getValue(), Airtight::seals)) {
                continue;
            }
            StationPlan.Pos p = e.getKey();
            x0 = Math.min(x0, p.x());
            x1 = Math.max(x1, p.x());
            y0 = Math.min(y0, p.y());
            y1 = Math.max(y1, p.y());
            z0 = Math.min(z0, p.z());
            z1 = Math.max(z1, p.z());
        }
        if (x0 > x1) {
            return new Report(0, List.of(), List.of());
        }
        x0 -= PAD;
        y0 -= PAD;
        z0 -= PAD;
        int nx = x1 + PAD - x0 + 1, ny = y1 + PAD - y0 + 1, nz = z1 + PAD - z0 + 1;
        int[] step = {ny * nz, -ny * nz, nz, -nz, 1, -1};
        int[] size = {nx, ny, nz};
        boolean[] solid = new boolean[nx * ny * nz];
        for (Map.Entry<StationPlan.Pos, BlockData> e : plan.blocks().entrySet()) {
            if (seals.get(e.getValue())) {
                StationPlan.Pos p = e.getKey();
                solid[((p.x() - x0) * ny + (p.y() - y0)) * nz + (p.z() - z0)] = true;
            }
        }

        // open[i] : directions dans lesquelles la case i voit le bord sans rencontrer de bloc plein
        byte[] open = new byte[solid.length];
        for (int d = 0; d < 6; d++) {
            int axis = d / 2;
            int a = size[axis], b = size[(axis + 1) % 3], c = size[(axis + 2) % 3];
            int[] at = new int[3];
            for (int i = 0; i < b; i++) {
                for (int j = 0; j < c; j++) {
                    at[(axis + 1) % 3] = i;
                    at[(axis + 2) % 3] = j;
                    boolean blocked = false;
                    for (int k = 0; k < a; k++) {
                        // on remonte le rayon depuis le bord vers l'intérieur
                        at[axis] = d % 2 == 0 ? a - 1 - k : k;
                        int idx = (at[0] * ny + at[1]) * nz + at[2];
                        if (solid[idx]) {
                            blocked = true;
                        } else if (!blocked) {
                            open[idx] |= (byte) (1 << d);
                        }
                    }
                }
            }
        }

        // parcours de l'intérieur, de case enfermée en case enfermée, depuis les points repères
        boolean[] seen = new boolean[solid.length];
        int[] queue = new int[solid.length];
        int head = 0, tail = 0;
        List<String> exposed = new ArrayList<>();
        for (Map.Entry<String, StationPlan.Point> e : plan.points().entrySet()) {
            StationPlan.Point p = e.getValue();
            int x = (int) Math.floor(p.x()) - x0, y = (int) Math.floor(p.y()) - y0, z = (int) Math.floor(p.z()) - z0;
            if (x < 0 || y < 0 || z < 0 || x >= nx || y >= ny - 2 || z >= nz) {
                exposed.add(e.getKey() + " (hors de la station)");
                continue;
            }
            int idx = (x * ny + y) * nz + z;
            for (int up = 0; up < 2 && solid[idx]; up++) {
                idx += nz;
            }
            if (solid[idx]) {
                continue; // point posé dans un bloc (écran, décor) : rien à contrôler
            }
            if (open[idx] != 0) {
                exposed.add(e.getKey() + " (ouvert vers " + NAMES[Integer.numberOfTrailingZeros(open[idx])] + ")");
            } else if (!seen[idx]) {
                seen[idx] = true;
                queue[tail++] = idx;
            }
        }
        List<int[]> frontier = new ArrayList<>(); // {x, y, z, directions ouvertes}
        boolean[] flagged = new boolean[solid.length];
        while (head < tail) {
            int idx = queue[head++];
            for (int d = 0; d < 6; d++) {
                int n = idx + step[d];
                if (solid[n] || seen[n]) {
                    continue; // une case enfermée n'est jamais au bord : n reste dans la grille
                }
                if (open[n] == 0) {
                    seen[n] = true;
                    queue[tail++] = n;
                } else if (!flagged[n]) {
                    flagged[n] = true;
                    frontier.add(new int[]{n / (ny * nz) + x0, (n / nz) % ny + y0, n % nz + z0, open[n]});
                }
            }
        }
        return new Report(tail, group(frontier), exposed);
    }

    /** Regroupe les cases ouvertes voisines en ouvertures : une par trou, les plus grandes d'abord. */
    private static List<Hole> group(List<int[]> cells) {
        List<int[]> boxes = new ArrayList<>(); // {minX, maxX, minY, maxY, minZ, maxZ}
        List<List<int[]>> members = new ArrayList<>();
        for (int[] c : cells) {
            int found = -1;
            for (int i = 0; i < boxes.size() && found < 0; i++) {
                int[] b = boxes.get(i);
                if (c[0] >= b[0] - 3 && c[0] <= b[1] + 3 && c[1] >= b[2] - 3 && c[1] <= b[3] + 3 && c[2] >= b[4] - 3 && c[2] <= b[5] + 3) {
                    found = i;
                }
            }
            if (found < 0) {
                boxes.add(new int[]{c[0], c[0], c[1], c[1], c[2], c[2]});
                members.add(new ArrayList<>(List.of(c)));
            } else {
                int[] b = boxes.get(found);
                b[0] = Math.min(b[0], c[0]);
                b[1] = Math.max(b[1], c[0]);
                b[2] = Math.min(b[2], c[1]);
                b[3] = Math.max(b[3], c[1]);
                b[4] = Math.min(b[4], c[2]);
                b[5] = Math.max(b[5], c[2]);
                members.get(found).add(c);
            }
        }
        List<Hole> holes = new ArrayList<>();
        for (List<int[]> group : members) {
            double sx = 0, sy = 0, sz = 0;
            int[] votes = new int[6];
            for (int[] c : group) {
                sx += c[0];
                sy += c[1];
                sz += c[2];
                for (int d = 0; d < 6; d++) {
                    votes[d] += (c[3] >> d) & 1;
                }
            }
            double mx = sx / group.size(), my = sy / group.size(), mz = sz / group.size();
            int[] best = group.get(0);
            double bestDist = Double.MAX_VALUE;
            for (int[] c : group) {
                double dist = Math.abs(c[0] - mx) + Math.abs(c[1] - my) + Math.abs(c[2] - mz);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = c;
                }
            }
            int toward = 0;
            for (int d = 1; d < 6; d++) {
                if (votes[d] > votes[toward]) {
                    toward = d;
                }
            }
            holes.add(new Hole(best[0], best[1], best[2], NAMES[toward], group.size()));
        }
        holes.sort(Comparator.comparingInt(Hole::cells).reversed());
        return holes;
    }

    /** Un bloc ferme la station s'il est plein sur ses six faces : mur, sol, verre... mais pas une barrière, une dalle ou une plante. */
    private static boolean seals(BlockData data) {
        Material m = data.getMaterial();
        if (m.isAir() || !m.isSolid()) {
            return false;
        }
        for (BlockFace face : FACES) {
            if (!data.isFaceSturdy(face, BlockSupport.FULL)) {
                return false;
            }
        }
        return true;
    }
}
