package fr.orbit.tele.jeux;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * CHARGE NOVA : arène à piliers et caisses. On pose des charges qui explosent en croix, on ouvre le passage et on
 * piège l'adversaire. Trois manches gagnantes. Seul : contre ORBI-7.
 */
final class Nova extends Jeu {

    private static final int COLS = 17, RANGS = 11, C = 32, X0 = 48, Y0 = 30, VIDE = 0, PILIER = 1, CAISSE = 2;
    private static final int[][] DIRS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
    private static final int[] TOUCHES = {Manette.HAUT, Manette.BAS, Manette.GAUCHE, Manette.DROITE};
    private static final int MECHE = 66, FEU = 14, MANCHES = 3;

    private static final class Pilote {
        int cx, cy, tx, ty, charges, portee, posees, regard = 1;
        double av, vitesse;
        boolean bouge, mort;

        double x() {
            return bouge ? cx + (tx - cx) * av : cx;
        }

        double y() {
            return bouge ? cy + (ty - cy) * av : cy;
        }

        int caseX() {
            return bouge && av >= 0.5 ? tx : cx;
        }

        int caseY() {
            return bouge && av >= 0.5 ? ty : cy;
        }
    }

    private interface Cible {
        boolean ok(int x, int y);
    }

    private final int[][] carte = new int[RANGS][COLS], flamme = new int[RANGS][COLS], bonus = new int[RANGS][COLS];
    private final Pilote[] pilotes = {new Pilote(), new Pilote()};
    private final List<int[]> charges = new ArrayList<>(); // x, y, mèche, portée, propriétaire
    private final int[] manches = new int[2];
    private final boolean robot;
    private int temps, finManche;

    Nova(Contexte ctx) {
        super(ctx);
        robot = !ctx.deux();
        record = false;
        manche();
    }

    private void manche() {
        charges.clear();
        temps = 90 * 30;
        finManche = 0;
        for (int y = 0; y < RANGS; y++) {
            for (int x = 0; x < COLS; x++) {
                flamme[y][x] = bonus[y][x] = 0;
                boolean depart = (x + y <= 2) || (COLS - 1 - x) + (RANGS - 1 - y) <= 2;
                carte[y][x] = x % 2 == 1 && y % 2 == 1 ? PILIER : !depart && alea.nextInt(100) < 72 ? CAISSE : VIDE;
            }
        }
        for (int i = 0; i < 2; i++) {
            Pilote p = pilotes[i];
            p.cx = i == 0 ? 0 : COLS - 1;
            p.cy = i == 0 ? 0 : RANGS - 1;
            p.bouge = p.mort = false;
            p.charges = 1;
            p.portee = 2;
            p.posees = 0;
            p.vitesse = 0.11;
        }
    }

    private boolean chargeEn(int x, int y) {
        for (int[] c : charges) {
            if (c[0] == x && c[1] == y) {
                return true;
            }
        }
        return false;
    }

    private boolean libre(int x, int y) {
        return x >= 0 && y >= 0 && x < COLS && y < RANGS && carte[y][x] == VIDE && !chargeEn(x, y);
    }

    @Override
    protected void tic(Manette a, Manette b) {
        if (finManche > 0) {
            if (++finManche > 50) {
                int vivants = (pilotes[0].mort ? 0 : 1) + (pilotes[1].mort ? 0 : 1);
                if (vivants == 1) {
                    int v = pilotes[0].mort ? 1 : 0;
                    if (++manches[v] >= MANCHES) {
                        fini = true;
                        gagnant = v + 1;
                        bilan = manches[0] + "  –  " + manches[1];
                        return;
                    }
                }
                manche();
            }
        } else if (--temps <= 0 || pilotes[0].mort || pilotes[1].mort) {
            finManche = 1;
            if (temps <= 0) {
                pilotes[0].mort = pilotes[1].mort = true;
            }
        }
        for (int i = 0; i < 2; i++) {
            Pilote p = pilotes[i];
            if (p.mort) {
                continue;
            }
            int dir = -1;
            boolean poser;
            if (i == 1 && robot) {
                int r = p.bouge ? 0 : penser(p, pilotes[0]);
                poser = (r & 8) != 0;
                dir = (r & 7) - 1;
            } else {
                Manette m = i == 0 ? a : b;
                poser = m.appui(Manette.A);
                for (int d = 0; d < 4 && dir < 0; d++) {
                    if (m.tenu(TOUCHES[d]) && libre(p.cx + DIRS[d][0], p.cy + DIRS[d][1])) {
                        dir = d;
                    }
                }
            }
            if (poser && p.posees < p.charges && !chargeEn(p.caseX(), p.caseY())) {
                charges.add(new int[]{p.caseX(), p.caseY(), MECHE, p.portee, i});
                p.posees++;
                son("pose");
            }
            if (!p.bouge && dir >= 0 && libre(p.cx + DIRS[dir][0], p.cy + DIRS[dir][1])) {
                p.tx = p.cx + DIRS[dir][0];
                p.ty = p.cy + DIRS[dir][1];
                p.av = 0;
                p.bouge = true;
                p.regard = dir;
            }
            if (p.bouge && (p.av += p.vitesse) >= 1) {
                p.cx = p.tx;
                p.cy = p.ty;
                p.bouge = false;
                int bo = bonus[p.cy][p.cx];
                if (bo != 0) {
                    bonus[p.cy][p.cx] = 0;
                    son("piece");
                    if (bo == 1) {
                        p.portee = Math.min(6, p.portee + 1);
                    } else if (bo == 2) {
                        p.charges = Math.min(4, p.charges + 1);
                    } else {
                        p.vitesse = Math.min(0.19, p.vitesse + 0.025);
                    }
                }
            }
        }
        for (int[] row : flamme) {
            for (int x = 0; x < COLS; x++) {
                row[x] = Math.max(0, row[x] - 1);
            }
        }
        charges.forEach(c -> c[2]--);
        for (boolean encore = true; encore; ) {
            encore = false;
            for (int i = 0; i < charges.size(); i++) {
                if (charges.get(i)[2] <= 0) {
                    exploser(charges.remove(i));
                    encore = true;
                    break;
                }
            }
        }
        for (Pilote p : pilotes) {
            if (!p.mort && flamme[p.caseY()][p.caseX()] > 0) {
                p.mort = true;
                son("perdu");
            }
        }
    }

    private void exploser(int[] c) {
        pilotes[c[4]].posees--;
        flamme[c[1]][c[0]] = FEU;
        son("boum");
        for (int[] d : DIRS) {
            for (int k = 1; k <= c[3]; k++) {
                int x = c[0] + d[0] * k, y = c[1] + d[1] * k;
                if (x < 0 || y < 0 || x >= COLS || y >= RANGS || carte[y][x] == PILIER) {
                    break;
                }
                flamme[y][x] = FEU;
                if (carte[y][x] == CAISSE) {
                    carte[y][x] = VIDE;
                    bonus[y][x] = alea.nextInt(100) < 34 ? 1 + alea.nextInt(3) : 0;
                    break;
                }
                for (int[] autre : charges) {
                    if (autre[0] == x && autre[1] == y) {
                        autre[2] = 0;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ ORBI-7

    /** Cases qui vont brûler (charges posées, plus une charge imaginaire en (ax, ay) si ax >= 0). */
    private boolean[][] danger(int ax, int ay, int portee) {
        boolean[][] d = new boolean[RANGS][COLS];
        List<int[]> toutes = new ArrayList<>(charges);
        if (ax >= 0) {
            toutes.add(new int[]{ax, ay, 0, portee, 0});
        }
        for (int[] c : toutes) {
            d[c[1]][c[0]] = true;
            for (int[] dir : DIRS) {
                for (int k = 1; k <= c[3]; k++) {
                    int x = c[0] + dir[0] * k, y = c[1] + dir[1] * k;
                    if (x < 0 || y < 0 || x >= COLS || y >= RANGS || carte[y][x] != VIDE) {
                        break;
                    }
                    d[y][x] = true;
                }
            }
        }
        for (int y = 0; y < RANGS; y++) {
            for (int x = 0; x < COLS; x++) {
                d[y][x] |= flamme[y][x] > 0;
            }
        }
        return d;
    }

    /** Premier pas (1 à 4, 0 = aucun) du plus court chemin vers une case qui convient. */
    private int chemin(Pilote p, boolean[][] interdit, Cible cible) {
        int[] premier = new int[COLS * RANGS], file = new int[COLS * RANGS];
        boolean[] vu = new boolean[COLS * RANGS];
        int tete = 0, queue = 0;
        file[queue++] = p.cy * COLS + p.cx;
        vu[p.cy * COLS + p.cx] = true;
        while (tete < queue) {
            int k = file[tete++], x = k % COLS, y = k / COLS;
            for (int d = 0; d < 4; d++) {
                int nx = x + DIRS[d][0], ny = y + DIRS[d][1];
                if (!libre(nx, ny) || vu[ny * COLS + nx] || (interdit != null && interdit[ny][nx])) {
                    continue;
                }
                vu[ny * COLS + nx] = true;
                premier[ny * COLS + nx] = k == p.cy * COLS + p.cx ? d + 1 : premier[k];
                if (cible.ok(nx, ny)) {
                    return premier[ny * COLS + nx];
                }
                file[queue++] = ny * COLS + nx;
            }
        }
        return 0;
    }

    private boolean pres(int x, int y, int quoi) {
        for (int[] d : DIRS) {
            int nx = x + d[0], ny = y + d[1];
            if (nx >= 0 && ny >= 0 && nx < COLS && ny < RANGS && carte[ny][nx] == quoi) {
                return true;
            }
        }
        return false;
    }

    /** @return pas (1 à 4) + 8 s'il faut poser une charge */
    private int penser(Pilote p, Pilote autre) {
        boolean[][] d = danger(-1, 0, 0);
        boolean[][] feu = new boolean[RANGS][COLS];
        for (int y = 0; y < RANGS; y++) {
            for (int x = 0; x < COLS; x++) {
                feu[y][x] = flamme[y][x] > 0;
            }
        }
        if (d[p.cy][p.cx]) {
            return chemin(p, feu, (x, y) -> !d[y][x]);
        }
        int ecart = Math.abs(autre.caseX() - p.cx) + Math.abs(autre.caseY() - p.cy);
        boolean utile = pres(p.cx, p.cy, CAISSE) || (!autre.mort && ecart <= 2);
        if (utile && p.posees < p.charges && !chargeEn(p.cx, p.cy) && alea.nextInt(100) < 80) {
            boolean[][] apres = danger(p.cx, p.cy, p.portee);
            if (chemin(p, feu, (x, y) -> !apres[y][x]) != 0) {
                return 8;
            }
        }
        if (bonusProche(p) != 0) {
            return bonusProche(p);
        }
        return chemin(p, d, (x, y) -> pres(x, y, CAISSE) || (!autre.mort && Math.abs(autre.caseX() - x) + Math.abs(autre.caseY() - y) <= 2));
    }

    private int bonusProche(Pilote p) {
        boolean[][] d = danger(-1, 0, 0);
        return chemin(p, d, (x, y) -> bonus[y][x] != 0);
    }

    // ------------------------------------------------------------------ dessin

    @Override
    public void dessiner(Graphics2D g) {
        Dessin.fond(g, tics, 0.05);
        g.setColor(new Color(0x0E1730));
        g.fillRect(X0 - 4, Y0 - 4, COLS * C + 8, RANGS * C + 8);
        for (int y = 0; y < RANGS; y++) {
            for (int x = 0; x < COLS; x++) {
                int px = X0 + x * C, py = Y0 + y * C;
                g.setColor((x + y) % 2 == 0 ? new Color(0x152243) : new Color(0x121D3A));
                g.fillRect(px, py, C, C);
                if (carte[y][x] == PILIER) {
                    g.setColor(new Color(0x3C4A6B));
                    g.fillRoundRect(px + 1, py + 1, C - 2, C - 2, 8, 8);
                    g.setColor(new Color(0x56658C));
                    g.fillRoundRect(px + 5, py + 5, C - 10, C - 14, 6, 6);
                } else if (carte[y][x] == CAISSE) {
                    g.setColor(new Color(0x9A6A3A));
                    g.fillRoundRect(px + 2, py + 2, C - 4, C - 4, 6, 6);
                    g.setColor(new Color(0xC08A4E));
                    g.fillRect(px + 5, py + 5, C - 10, 4);
                    g.fillRect(px + 5, py + C - 9, C - 10, 4);
                    g.setColor(new Color(0x6E4A26));
                    g.drawLine(px + 6, py + 10, px + C - 7, py + C - 11);
                } else if (bonus[y][x] != 0) {
                    Color c = bonus[y][x] == 1 ? Dessin.ORANGE : bonus[y][x] == 2 ? Dessin.VIOLET : Dessin.VERT;
                    g.setColor(Dessin.alpha(c, 70));
                    g.fillOval(px + 4, py + 4, C - 8, C - 8);
                    g.setColor(c);
                    g.fillOval(px + 8, py + 8, C - 16, C - 16);
                    g.setColor(Dessin.BLANC);
                    g.setFont(Dessin.titre(11));
                    Dessin.centre(g, bonus[y][x] == 1 ? "+" : bonus[y][x] == 2 ? "2" : "»", px + C / 2, py + C / 2 + 4);
                }
            }
        }
        for (int[] c : charges) {
            int px = X0 + c[0] * C + C / 2, py = Y0 + c[1] * C + C / 2, r = 9 + (int) (Math.sin(c[2] * 0.4) * 2);
            g.setColor(Dessin.alpha(c[4] == 0 ? Dessin.BLEU : Dessin.OR, 90));
            g.fillOval(px - r - 3, py - r - 3, r * 2 + 6, r * 2 + 6);
            g.setColor(new Color(0x1B2233));
            g.fillOval(px - r, py - r, r * 2, r * 2);
            g.setColor(c[2] < 20 && c[2] % 6 < 3 ? Dessin.ROUGE : Dessin.GLACE);
            g.fillOval(px - 3, py - 3, 6, 6);
        }
        for (int y = 0; y < RANGS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (flamme[y][x] > 0) {
                    int px = X0 + x * C, py = Y0 + y * C, f = flamme[y][x];
                    g.setColor(Dessin.alpha(Dessin.ORANGE, 90 + f * 11));
                    g.fillRoundRect(px + 1, py + 1, C - 2, C - 2, 10, 10);
                    g.setColor(Dessin.alpha(new Color(0xFFF2B0), 60 + f * 13));
                    g.fillRoundRect(px + 7, py + 7, C - 14, C - 14, 8, 8);
                }
            }
        }
        for (int i = 0; i < 2; i++) {
            Pilote p = pilotes[i];
            int px = X0 + (int) (p.x() * C) + C / 2, py = Y0 + (int) (p.y() * C) + C / 2;
            Color c = p.mort ? Dessin.GRIS : i == 0 ? Dessin.BLEU : Dessin.OR;
            g.setColor(new Color(0, 0, 0, 90));
            g.fillOval(px - 11, py + 6, 22, 8);
            g.setColor(c);
            g.fillRoundRect(px - 11, py - 12, 22, 24, 12, 12);
            g.setColor(p.mort ? Dessin.FOND : Dessin.BLANC);
            g.fillRoundRect(px - 7 + DIRS[p.regard][0] * 3, py - 8 + DIRS[p.regard][1] * 2, 14, 8, 6, 6);
        }
        for (int i = 0; i < 2; i++) {
            Color c = i == 0 ? Dessin.BLEU : Dessin.OR;
            g.setFont(Dessin.titre(14));
            g.setColor(c);
            if (i == 0) {
                g.drawString(noms[0], X0, 20);
            } else {
                Dessin.droite(g, noms[1], X0 + COLS * C, 20);
            }
            for (int k = 0; k < MANCHES; k++) {
                g.setColor(k < manches[i] ? c : new Color(255, 255, 255, 40));
                g.fillOval(i == 0 ? 250 - k * 16 : 380 + k * 16, 9, 10, 10);
            }
        }
        g.setFont(Dessin.titre(16));
        g.setColor(temps < 15 * 30 ? Dessin.ROUGE : Dessin.BLANC);
        Dessin.centre(g, String.valueOf(Math.max(0, temps / 30)), W / 2, 21);
        if (finManche > 0 && !fini) {
            int vivants = (pilotes[0].mort ? 0 : 1) + (pilotes[1].mort ? 0 : 1);
            Dessin.panneau(g, 200, 160, 240, 60, Dessin.OR);
            g.setFont(Dessin.titre(20));
            g.setColor(Dessin.BLANC);
            Dessin.centre(g, vivants == 1 ? "Manche pour " + noms[pilotes[0].mort ? 1 : 0] : "Manche nulle", W / 2, 197);
        }
    }
}
