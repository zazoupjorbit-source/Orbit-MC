package fr.orbit.bornes.cristaux;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Plateau de CRISTAUX D'ORBIT (logique pure, sans Bukkit) : 7 colonnes × 6 lignes, case i = r × 7 + c (r = 0 en haut).
 * Alignements de 3+ ; 4 en ligne → cristal rayé (efface sa ligne ou sa colonne), alignement en L ou en T → cristal
 * « pulsar » (explose en 3 × 3), 5 en ligne → NOVA (échangée avec un cristal, efface toute sa couleur).
 */
public final class Grid {

    public static final int W = 7, H = 6, N = W * H;
    public static final int NONE = 0, STRIPE_H = 1, STRIPE_V = 2, BOMB = 3, NOVA = 4;

    /** Couleur (0 à colors-1), -1 = vide ; NOVA n'a pas de couleur (-2). */
    public final int[] color = new int[N];
    public final int[] special = new int[N];
    public final int colors;
    private final SplittableRandom rnd;

    /** Résultat d'un alignement : cases effacées et cristaux spéciaux créés. */
    public static final class Resolution {
        public final BitSet cleared = new BitSet(N);
        /** {case, couleur, spécial} */
        public final List<int[]> created = new ArrayList<>();

        public boolean empty() {
            return cleared.isEmpty() && created.isEmpty();
        }
    }

    public Grid(int colors, long seed) {
        this.colors = colors;
        this.rnd = new SplittableRandom(seed);
        fill();
    }

    public static int idx(int r, int c) {
        return r * W + c;
    }

    public boolean adjacent(int a, int b) {
        int ra = a / W, ca = a % W, rb = b / W, cb = b % W;
        return Math.abs(ra - rb) + Math.abs(ca - cb) == 1;
    }

    /** Plateau neuf sans alignement tout fait, avec au moins un coup possible. */
    public void fill() {
        do {
            for (int i = 0; i < N; i++) {
                special[i] = NONE;
                int c;
                do {
                    c = rnd.nextInt(colors);
                    color[i] = c;
                } while (makesRun(i));
            }
        } while (!hasMove());
    }

    private boolean makesRun(int i) {
        int r = i / W, c = i % W, k = color[i];
        return (c >= 2 && color[i - 1] == k && color[i - 2] == k) || (r >= 2 && color[i - W] == k && color[i - 2 * W] == k);
    }

    public void swap(int a, int b) {
        int t = color[a];
        color[a] = color[b];
        color[b] = t;
        t = special[a];
        special[a] = special[b];
        special[b] = t;
    }

    /** Échange avec une NOVA ou entre deux spéciaux : effet direct (null sinon). Les cases a et b sont déjà échangées. */
    public Resolution comboSwap(int a, int b) {
        boolean na = special[a] == NOVA, nb = special[b] == NOVA;
        Resolution res = new Resolution();
        if (na || nb) {
            int nova = na ? a : b, other = na ? b : a;
            res.cleared.set(nova);
            if (special[other] == NOVA) {
                res.cleared.set(0, N);
            } else {
                int k = color[other];
                for (int i = 0; i < N; i++) {
                    if (color[i] == k) {
                        res.cleared.set(i);
                    }
                }
            }
            expand(res.cleared, nova);
            return res;
        }
        if (special[a] != NONE && special[b] != NONE) {
            res.cleared.set(a);
            res.cleared.set(b);
            expand(res.cleared, -1);
            return res;
        }
        return null;
    }

    /** Alignements présents ({@code a}, {@code b} : cases de l'échange, pour y placer le spécial ; -1 sinon). */
    public Resolution matches(int a, int b) {
        int[] runH = new int[N], runV = new int[N];
        BitSet marked = new BitSet(N);
        // runs horizontaux et verticaux
        for (int r = 0; r < H; r++) {
            int c = 0;
            while (c < W) {
                int k = color[idx(r, c)], e = c + 1;
                while (k >= 0 && e < W && color[idx(r, e)] == k) {
                    e++;
                }
                if (k >= 0 && e - c >= 3) {
                    for (int x = c; x < e; x++) {
                        runH[idx(r, x)] = e - c;
                        marked.set(idx(r, x));
                    }
                }
                c = e;
            }
        }
        for (int c = 0; c < W; c++) {
            int r = 0;
            while (r < H) {
                int k = color[idx(r, c)], e = r + 1;
                while (k >= 0 && e < H && color[idx(e, c)] == k) {
                    e++;
                }
                if (k >= 0 && e - r >= 3) {
                    for (int y = r; y < e; y++) {
                        runV[idx(y, c)] = e - r;
                        marked.set(idx(y, c));
                    }
                }
                r = e;
            }
        }
        Resolution res = new Resolution();
        if (marked.isEmpty()) {
            return res;
        }
        // groupes : cases marquées voisines de même couleur
        BitSet seen = new BitSet(N);
        for (int start = marked.nextSetBit(0); start >= 0; start = marked.nextSetBit(start + 1)) {
            if (seen.get(start)) {
                continue;
            }
            List<Integer> group = new ArrayList<>();
            Deque<Integer> q = new ArrayDeque<>();
            q.add(start);
            seen.set(start);
            while (!q.isEmpty()) {
                int i = q.poll();
                group.add(i);
                for (int j : neighbours(i)) {
                    if (marked.get(j) && !seen.get(j) && color[j] == color[i]) {
                        seen.set(j);
                        q.add(j);
                    }
                }
            }
            int maxH = 0, maxV = 0, cross = -1;
            for (int i : group) {
                maxH = Math.max(maxH, runH[i]);
                maxV = Math.max(maxV, runV[i]);
                if (runH[i] >= 3 && runV[i] >= 3) {
                    cross = i;
                }
            }
            int make = NONE;
            if (maxH >= 5 || maxV >= 5) {
                make = NOVA;
            } else if (cross >= 0) {
                make = BOMB;
            } else if (maxH == 4) {
                make = STRIPE_V;
            } else if (maxV == 4) {
                make = STRIPE_H;
            }
            for (int i : group) {
                res.cleared.set(i);
            }
            if (make != NONE) {
                int at = group.contains(a) ? a : group.contains(b) ? b : cross >= 0 ? cross : group.get(group.size() / 2);
                res.created.add(new int[]{at, make == NOVA ? -2 : color[at], make});
            }
        }
        BitSet keep = new BitSet(N);
        for (int[] cr : res.created) {
            keep.set(cr[0]);
        }
        expand(res.cleared, -1);
        // la case du nouveau spécial n'est effacée que si un autre spécial l'a atteinte… on la garde quand même
        for (int[] cr : res.created) {
            res.cleared.clear(cr[0]);
        }
        return res;
    }

    /** Déclenche en chaîne les spéciaux des cases effacées (sauf {@code skip}). */
    private void expand(BitSet cleared, int skip) {
        Deque<Integer> q = new ArrayDeque<>();
        BitSet done = new BitSet(N);
        for (int i = cleared.nextSetBit(0); i >= 0; i = cleared.nextSetBit(i + 1)) {
            q.add(i);
        }
        while (!q.isEmpty()) {
            int i = q.poll();
            if (done.get(i) || i == skip) {
                continue;
            }
            done.set(i);
            int r = i / W, c = i % W;
            List<Integer> hit = new ArrayList<>();
            switch (special[i]) {
                case STRIPE_H -> {
                    for (int x = 0; x < W; x++) {
                        hit.add(idx(r, x));
                    }
                }
                case STRIPE_V -> {
                    for (int y = 0; y < H; y++) {
                        hit.add(idx(y, c));
                    }
                }
                case BOMB -> {
                    for (int y = r - 1; y <= r + 1; y++) {
                        for (int x = c - 1; x <= c + 1; x++) {
                            if (y >= 0 && y < H && x >= 0 && x < W) {
                                hit.add(idx(y, x));
                            }
                        }
                    }
                }
                case NOVA -> {
                    int k = rnd.nextInt(colors);
                    for (int j = 0; j < N; j++) {
                        if (color[j] == k) {
                            hit.add(j);
                        }
                    }
                }
                default -> {
                }
            }
            for (int j : hit) {
                if (!cleared.get(j)) {
                    cleared.set(j);
                }
                q.add(j);
            }
        }
    }

    private List<Integer> neighbours(int i) {
        List<Integer> out = new ArrayList<>(4);
        int r = i / W, c = i % W;
        if (r > 0) {
            out.add(i - W);
        }
        if (r < H - 1) {
            out.add(i + W);
        }
        if (c > 0) {
            out.add(i - 1);
        }
        if (c < W - 1) {
            out.add(i + 1);
        }
        return out;
    }

    /** Efface et crée ; renvoie les points (20 par cristal × série, bonus des spéciaux créés). */
    public int apply(Resolution res, int cascade) {
        int n = res.cleared.cardinality();
        for (int i = res.cleared.nextSetBit(0); i >= 0; i = res.cleared.nextSetBit(i + 1)) {
            color[i] = -1;
            special[i] = NONE;
        }
        int bonus = 0;
        for (int[] cr : res.created) {
            color[cr[0]] = cr[1];
            special[cr[0]] = cr[2];
            bonus += cr[2] == NOVA ? 200 : cr[2] == BOMB ? 100 : 60;
        }
        return (n * 20 + bonus) * Math.max(1, cascade);
    }

    /** Un pas de chute : chaque cristal tombe d'une case si c'est vide dessous, de nouveaux entrent en haut. */
    public boolean fallStep() {
        boolean moved = false;
        for (int r = H - 1; r >= 1; r--) {
            for (int c = 0; c < W; c++) {
                int i = idx(r, c), up = idx(r - 1, c);
                if (empty(i) && !empty(up)) {
                    color[i] = color[up];
                    special[i] = special[up];
                    color[up] = -1;
                    special[up] = NONE;
                    moved = true;
                }
            }
        }
        for (int c = 0; c < W; c++) {
            if (empty(c)) {
                color[c] = rnd.nextInt(colors);
                special[c] = NONE;
                moved = true;
            }
        }
        return moved;
    }

    public boolean empty(int i) {
        return color[i] == -1 && special[i] == NONE;
    }

    public boolean full() {
        for (int i = 0; i < N; i++) {
            if (empty(i)) {
                return false;
            }
        }
        return true;
    }

    /** Un échange possible fait-il un alignement (ou utilise une NOVA) ? */
    public boolean hasMove() {
        return findMove() != null;
    }

    public int[] findMove() {
        for (int i = 0; i < N; i++) {
            if (special[i] == NOVA) {
                return new int[]{i, i % W < W - 1 ? i + 1 : i - 1};
            }
            for (int j : new int[]{i + 1, i + W}) {
                if (j >= N || (j == i + 1 && i % W == W - 1)) {
                    continue;
                }
                swap(i, j);
                boolean ok = !matches(i, j).empty() || (special[i] != NONE && special[j] != NONE);
                swap(i, j);
                if (ok) {
                    return new int[]{i, j};
                }
            }
        }
        return null;
    }

    /** Mélange jusqu'à avoir un coup et aucun alignement tout fait (les spéciaux restent). */
    public void shuffle() {
        for (int tries = 0; tries < 200; tries++) {
            for (int i = N - 1; i > 0; i--) {
                swap(i, rnd.nextInt(i + 1));
            }
            if (matches(-1, -1).empty() && hasMove()) {
                return;
            }
        }
        fill();
    }
}
