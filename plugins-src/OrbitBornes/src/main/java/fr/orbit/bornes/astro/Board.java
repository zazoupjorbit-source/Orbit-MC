package fr.orbit.bornes.astro;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Grille d'ASTROBLOCS : 10 colonnes, 20 lignes visibles (+ 4 cachées au-dessus pour l'apparition).
 * y = 0 en bas. La pièce active est décrite par le haut-gauche de sa boîte (px, py) : case (cx, cy) de la boîte
 * → grille (px + cx, py - cy). Tirage par sacs de 7, réserve, lignes reçues en duel.
 */
public final class Board {

    public static final int W = 10, H = 24, VISIBLE = 20;
    static final int[] LINE_SCORE = {0, 100, 300, 500, 800};
    static final int[] LINE_ATTACK = {0, 0, 1, 2, 4};

    public final int[][] cells = new int[H][W];
    private final SplittableRandom rnd;
    private final Deque<Piece> queue = new ArrayDeque<>();

    public Piece cur, hold;
    public int rot, px, py;
    public boolean holdUsed, over;
    public int score, lines, level = 1, combo = -1;
    /** Lignes reçues de l'adversaire, ajoutées en bas à la prochaine pose sans ligne faite. */
    public int pending;
    public int sent;

    public Board(long seed) {
        rnd = new SplittableRandom(seed);
    }

    public Piece next() {
        refill();
        return queue.peekFirst();
    }

    private void refill() {
        while (queue.size() < 7) {
            List<Piece> bag = new ArrayList<>(List.of(Piece.values()));
            for (int i = bag.size() - 1; i > 0; i--) {
                Collections.swap(bag, i, rnd.nextInt(i + 1));
            }
            queue.addAll(bag);
        }
    }

    /** Fait apparaître la pièce suivante ; perdu si elle ne rentre pas. */
    public void spawn() {
        refill();
        spawn(queue.pollFirst());
    }

    private void spawn(Piece p) {
        cur = p;
        rot = 0;
        px = (W - p.n) / 2;
        py = VISIBLE + 1;
        if (!fits(p, rot, px, py)) {
            over = true;
        } else if (fits(p, rot, px, py - 1)) {
            py--; // apparaît sur la première ligne visible
        }
    }

    public boolean fits(Piece p, int r, int x, int y) {
        for (int[] c : p.cells[r]) {
            int gx = x + c[0], gy = y - c[1];
            if (gx < 0 || gx >= W || gy < 0) {
                return false;
            }
            if (gy < H && cells[gy][gx] != 0) {
                return false;
            }
        }
        return true;
    }

    public boolean move(int dx) {
        if (cur != null && fits(cur, rot, px + dx, py)) {
            px += dx;
            return true;
        }
        return false;
    }

    public boolean down() {
        if (cur != null && fits(cur, rot, px, py - 1)) {
            py--;
            return true;
        }
        return false;
    }

    public boolean landed() {
        return cur != null && !fits(cur, rot, px, py - 1);
    }

    private static final int[][] KICKS = {{0, 0}, {-1, 0}, {1, 0}, {0, 1}, {-1, 1}, {1, 1}, {-2, 0}, {2, 0}, {0, -1}};

    /** Rotation (dir = 1 horaire, -1 antihoraire) avec petits décalages si ça coince. */
    public boolean rotate(int dir) {
        if (cur == null || cur == Piece.O) {
            return false;
        }
        int nr = Math.floorMod(rot + dir, 4);
        for (int[] k : KICKS) {
            if (fits(cur, nr, px + k[0], py + k[1])) {
                rot = nr;
                px += k[0];
                py += k[1];
                return true;
            }
        }
        return false;
    }

    public int ghostY() {
        int y = py;
        while (cur != null && fits(cur, rot, px, y - 1)) {
            y--;
        }
        return y;
    }

    /** Pose directe : renvoie le nombre de lignes descendues. */
    public int hardDrop() {
        int d = 0;
        while (down()) {
            d++;
        }
        return d;
    }

    /** Échange avec la réserve (une fois par pièce). */
    public boolean swapHold() {
        if (cur == null || holdUsed) {
            return false;
        }
        Piece h = hold;
        hold = cur;
        if (h == null) {
            spawn();
        } else {
            spawn(h);
        }
        holdUsed = true;
        return true;
    }

    /** Fixe la pièce dans la grille ; renvoie les lignes pleines (à effacer avec {@link #clear}). */
    public int[] lock() {
        boolean allHidden = true;
        for (int[] c : cur.cells[rot]) {
            int gx = px + c[0], gy = py - c[1];
            if (gy < H) {
                cells[gy][gx] = cur.id();
            }
            if (gy < VISIBLE) {
                allHidden = false;
            }
        }
        cur = null;
        holdUsed = false;
        if (allHidden) {
            over = true;
        }
        List<Integer> full = new ArrayList<>();
        for (int y = 0; y < H; y++) {
            boolean f = true;
            for (int x = 0; x < W; x++) {
                if (cells[y][x] == 0) {
                    f = false;
                    break;
                }
            }
            if (f) {
                full.add(y);
            }
        }
        return full.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Efface les lignes pleines, compte les points ; renvoie les lignes envoyées à l'adversaire. */
    public int clear(int[] rows) {
        int n = rows.length;
        for (int i = rows.length - 1; i >= 0; i--) {
            int y = rows[i];
            for (int yy = y; yy < H - 1; yy++) {
                cells[yy] = cells[yy + 1].clone();
            }
            cells[H - 1] = new int[W];
        }
        lines += n;
        combo++;
        boolean perfect = true;
        for (int[] row : cells) {
            for (int c : row) {
                if (c != 0) {
                    perfect = false;
                    break;
                }
            }
        }
        score += LINE_SCORE[Math.min(4, n)] * level + 50 * Math.max(0, combo) * level + (perfect ? 2000 * level : 0);
        level = 1 + lines / 10;
        int attack = LINE_ATTACK[Math.min(4, n)] + (combo >= 2 ? 1 : 0) + (perfect ? 4 : 0);
        int cancel = Math.min(attack, pending);
        pending -= cancel;
        attack -= cancel;
        sent += attack;
        return attack;
    }

    /** Pose sans ligne : fin de la série, les lignes reçues montent. */
    public void noClear() {
        combo = -1;
        if (pending > 0) {
            addGarbage(Math.min(pending, 8));
            pending = Math.max(0, pending - 8);
        }
    }

    private void addGarbage(int n) {
        for (int y = H - 1; y >= H - n; y--) {
            for (int x = 0; x < W; x++) {
                if (cells[y][x] != 0) {
                    over = true;
                }
            }
        }
        for (int y = H - 1; y >= n; y--) {
            cells[y] = cells[y - n].clone();
        }
        int hole = rnd.nextInt(W);
        for (int y = 0; y < n; y++) {
            cells[y] = new int[W];
            for (int x = 0; x < W; x++) {
                cells[y][x] = x == hole ? 0 : 8;
            }
        }
        if (cur != null) {
            while (!fits(cur, rot, px, py) && py < H + 4) {
                py++;
            }
        }
    }

    /** Hauteur de la pile (ligne la plus haute occupée + 1). */
    public int height() {
        for (int y = H - 1; y >= 0; y--) {
            for (int x = 0; x < W; x++) {
                if (cells[y][x] != 0) {
                    return y + 1;
                }
            }
        }
        return 0;
    }
}
