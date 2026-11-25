package fr.orbit.bornes.astro;

import java.util.SplittableRandom;

/**
 * ORBI-7, le bot des duels d'ASTROBLOCS. Pour chaque pièce, il essaie toutes les rotations et colonnes, note la grille
 * obtenue (hauteur, trous, relief, lignes faites) et joue la meilleure — parfois la deuxième, pour rester battable.
 * Il fait un geste tous les {@code speed} ticks, un peu plus vite au fil du duel.
 */
public final class Bot {

    public static final String NAME = "ORBI-7";

    public final Board board = new Board(System.nanoTime() ^ 0x5DEECE66DL);
    private final SplittableRandom rnd = new SplittableRandom();
    private final int baseSpeed;
    private int timer = 30, targetRot, targetX, age;
    private boolean planned;

    public Bot(int speed) {
        this.baseSpeed = Math.max(1, speed);
    }

    /** Un tick du bot ; les lignes qu'il fait montent chez {@code opponent}. */
    public void tick(Board opponent) {
        if (board.over) {
            return;
        }
        age++;
        if (board.cur == null) {
            board.spawn();
            planned = false;
            if (board.over) {
                return;
            }
        }
        if (!planned) {
            plan();
            planned = true;
        }
        if (--timer > 0) {
            return;
        }
        int speed = Math.max(2, baseSpeed - age / 1200);
        timer = speed;
        if (board.rot != targetRot) {
            if (!board.rotate(1)) {
                targetRot = board.rot;
            }
            return;
        }
        if (board.px != targetX) {
            if (!board.move(Integer.signum(targetX - board.px))) {
                targetX = board.px;
            }
            return;
        }
        board.hardDrop();
        int[] rows = board.lock();
        if (rows.length > 0) {
            opponent.pending += board.clear(rows);
        } else {
            board.noClear();
        }
        planned = false;
        timer = speed * 2;
    }

    private void plan() {
        Piece p = board.cur;
        double best = Double.NEGATIVE_INFINITY, second = Double.NEGATIVE_INFINITY;
        int bestR = 0, bestX = board.px, secR = 0, secX = board.px;
        for (int r = 0; r < (p == Piece.O ? 1 : 4); r++) {
            for (int x = -3; x < Board.W; x++) {
                int y = Board.VISIBLE + 2;
                if (!board.fits(p, r, x, y)) {
                    continue;
                }
                while (board.fits(p, r, x, y - 1)) {
                    y--;
                }
                double s = evaluate(p, r, x, y);
                if (s > best) {
                    second = best;
                    secR = bestR;
                    secX = bestX;
                    best = s;
                    bestR = r;
                    bestX = x;
                } else if (s > second) {
                    second = s;
                    secR = r;
                    secX = x;
                }
            }
        }
        boolean sloppy = second > Double.NEGATIVE_INFINITY && rnd.nextInt(100) < 14;
        targetRot = sloppy ? secR : bestR;
        targetX = sloppy ? secX : bestX;
    }

    private double evaluate(Piece p, int r, int x, int y) {
        int[][] g = new int[Board.H][];
        for (int i = 0; i < Board.H; i++) {
            g[i] = board.cells[i].clone();
        }
        for (int[] c : p.cells[r]) {
            int gy = y - c[1];
            if (gy < Board.H) {
                g[gy][x + c[0]] = 1;
            }
        }
        int lines = 0;
        for (int row = Board.H - 1; row >= 0; row--) {
            boolean full = true;
            for (int col = 0; col < Board.W; col++) {
                if (g[row][col] == 0) {
                    full = false;
                    break;
                }
            }
            if (full) {
                lines++;
                for (int yy = row; yy < Board.H - 1; yy++) {
                    g[yy] = g[yy + 1];
                }
                g[Board.H - 1] = new int[Board.W];
            }
        }
        int[] h = new int[Board.W];
        int holes = 0;
        for (int col = 0; col < Board.W; col++) {
            boolean roof = false;
            for (int row = Board.H - 1; row >= 0; row--) {
                if (g[row][col] != 0) {
                    if (!roof) {
                        h[col] = row + 1;
                        roof = true;
                    }
                } else if (roof) {
                    holes++;
                }
            }
        }
        int agg = 0, bump = 0, max = 0;
        for (int col = 0; col < Board.W; col++) {
            agg += h[col];
            max = Math.max(max, h[col]);
            if (col > 0) {
                bump += Math.abs(h[col] - h[col - 1]);
            }
        }
        return -0.51 * agg + 0.76 * lines - 0.36 * holes - 0.18 * bump - (max > 15 ? 2.0 * (max - 15) : 0);
    }
}
