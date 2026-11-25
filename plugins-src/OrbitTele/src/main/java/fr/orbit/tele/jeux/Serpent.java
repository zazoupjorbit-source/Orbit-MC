package fr.orbit.tele.jeux;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * NÉBULEUSE : un serpent d'étoiles qui grandit en mangeant ; une comète d'or passe de temps en temps.
 * À deux : deux serpents sur la même grille, celui qui percute un mur ou un corps a perdu.
 */
final class Serpent extends Jeu {

    private static final int COLS = 30, RANGS = 16, C = 20, X0 = 20, Y0 = 48;

    private static final class Corps {
        final Deque<int[]> anneaux = new ArrayDeque<>();
        int dx, dy, vx, vy, grandir = 3, points;
        boolean mort;

        Corps(int x, int y, int dx) {
            anneaux.addFirst(new int[]{x, y});
            this.dx = vx = dx;
        }

        boolean sur(int x, int y) {
            for (int[] c : anneaux) {
                if (c[0] == x && c[1] == y) {
                    return true;
                }
            }
            return false;
        }

        void diriger(Manette m) {
            if (m.appui(Manette.HAUT) && dy == 0) {
                vx = 0;
                vy = -1;
            } else if (m.appui(Manette.BAS) && dy == 0) {
                vx = 0;
                vy = 1;
            } else if (m.appui(Manette.GAUCHE) && dx == 0) {
                vx = -1;
                vy = 0;
            } else if (m.appui(Manette.DROITE) && dx == 0) {
                vx = 1;
                vy = 0;
            }
        }
    }

    private final Corps[] serpents;
    private int attente, manges;
    private int[] etoile, comete;
    private int cometeReste;

    Serpent(Contexte ctx) {
        super(ctx);
        serpents = ctx.deux()
                ? new Corps[]{new Corps(6, RANGS / 2, 1), new Corps(COLS - 7, RANGS / 2, -1)}
                : new Corps[]{new Corps(COLS / 2, RANGS / 2, 1)};
        record = !ctx.deux();
        etoile = libre();
    }

    private boolean occupe(int x, int y) {
        for (Corps s : serpents) {
            if (s.sur(x, y)) {
                return true;
            }
        }
        return false;
    }

    private int[] libre() {
        int[] p;
        do {
            p = new int[]{alea.nextInt(COLS), alea.nextInt(RANGS)};
        } while (occupe(p[0], p[1]) || (etoile != null && etoile[0] == p[0] && etoile[1] == p[1]));
        return p;
    }

    @Override
    protected void tic(Manette a, Manette b) {
        serpents[0].diriger(a);
        if (serpents.length == 2) {
            serpents[1].diriger(b);
        }
        if (comete != null && --cometeReste <= 0) {
            comete = null;
        }
        if (++attente < Math.max(3, 7 - manges / 6)) {
            return;
        }
        attente = 0;
        int[][] tetes = new int[serpents.length][];
        for (int i = 0; i < serpents.length; i++) {
            Corps s = serpents[i];
            s.dx = s.vx;
            s.dy = s.vy;
            int[] t = s.anneaux.peekFirst();
            tetes[i] = new int[]{t[0] + s.dx, t[1] + s.dy};
            int nx = tetes[i][0], ny = tetes[i][1];
            s.mort = nx < 0 || ny < 0 || nx >= COLS || ny >= RANGS || occupe(nx, ny);
        }
        if (serpents.length == 2 && tetes[0][0] == tetes[1][0] && tetes[0][1] == tetes[1][1]) {
            serpents[0].mort = serpents[1].mort = true;
        }
        boolean unMort = false;
        for (Corps s : serpents) {
            unMort |= s.mort;
        }
        if (unMort) {
            fini = true;
            son("perdu");
            if (serpents.length == 2) {
                gagnant = serpents[0].mort && serpents[1].mort ? 0 : serpents[0].mort ? 2 : 1;
                bilan = serpents[0].points + "  –  " + serpents[1].points;
            }
            return;
        }
        for (int i = 0; i < serpents.length; i++) {
            Corps s = serpents[i];
            int nx = tetes[i][0], ny = tetes[i][1];
            s.anneaux.addFirst(tetes[i]);
            if (nx == etoile[0] && ny == etoile[1]) {
                manges++;
                s.points += 10;
                s.grandir += 1;
                etoile = libre();
                son("piece");
                if (comete == null && alea.nextInt(100) < 18) {
                    comete = libre();
                    cometeReste = 170;
                }
            } else if (comete != null && nx == comete[0] && ny == comete[1]) {
                s.points += 50;
                s.grandir += 2;
                comete = null;
                son("bravo");
            }
            if (s.grandir > 0) {
                s.grandir--;
            } else {
                s.anneaux.removeLast();
            }
        }
        score = serpents[0].points;
    }

    @Override
    public void dessiner(Graphics2D g) {
        Dessin.fond(g, tics, 0.1);
        Dessin.panneau(g, X0 - 4, Y0 - 4, COLS * C + 8, RANGS * C + 8, Dessin.VIOLET);
        g.setColor(new Color(255, 255, 255, 10));
        for (int x = 1; x < COLS; x++) {
            g.drawLine(X0 + x * C, Y0, X0 + x * C, Y0 + RANGS * C);
        }
        for (int y = 1; y < RANGS; y++) {
            g.drawLine(X0, Y0 + y * C, X0 + COLS * C, Y0 + y * C);
        }
        int battement = (int) (Math.sin(tics * 0.25) * 2);
        g.setColor(Dessin.BLANC);
        g.fillOval(X0 + etoile[0] * C + 5 - battement, Y0 + etoile[1] * C + 5 - battement, 10 + battement * 2, 10 + battement * 2);
        g.setColor(Dessin.alpha(Dessin.GLACE, 90));
        g.drawOval(X0 + etoile[0] * C + 1, Y0 + etoile[1] * C + 1, 18, 18);
        if (comete != null && (cometeReste > 45 || tics % 6 < 3)) {
            int x = X0 + comete[0] * C, y = Y0 + comete[1] * C;
            g.setColor(Dessin.alpha(Dessin.OR, 90));
            g.fillPolygon(new int[]{x + 10, x + 30, x + 12}, new int[]{y + 4, y - 8, y + 14}, 3);
            g.setColor(Dessin.OR);
            g.fillOval(x + 2, y + 2, 16, 16);
        }
        for (int k = 0; k < serpents.length; k++) {
            Corps s = serpents[k];
            Color tete = k == 0 ? Dessin.GLACE : Dessin.OR, queue = k == 0 ? new Color(0x7B4BF4) : Dessin.ROUGE;
            int i = 0, n = s.anneaux.size();
            for (int[] c : s.anneaux) {
                float f = n <= 1 ? 0 : i / (float) (n - 1);
                g.setColor(s.mort ? Dessin.GRIS : new Color((int) (tete.getRed() + (queue.getRed() - tete.getRed()) * f),
                        (int) (tete.getGreen() + (queue.getGreen() - tete.getGreen()) * f), (int) (tete.getBlue() + (queue.getBlue() - tete.getBlue()) * f)));
                int marge = i == 0 ? 1 : 2;
                g.fillRoundRect(X0 + c[0] * C + marge, Y0 + c[1] * C + marge, C - marge * 2, C - marge * 2, 9, 9);
                if (i == 0) {
                    g.setColor(Dessin.FOND);
                    g.fillOval(X0 + c[0] * C + 7 + s.dx * 4 - s.dy * 4, Y0 + c[1] * C + 7 + s.dy * 4 - s.dx * 4, 5, 5);
                    g.fillOval(X0 + c[0] * C + 7 + s.dx * 4 + s.dy * 4, Y0 + c[1] * C + 7 + s.dy * 4 + s.dx * 4, 5, 5);
                }
                i++;
            }
        }
        g.setFont(Dessin.titre(22));
        g.setColor(Dessin.VIOLET);
        if (serpents.length == 2) {
            Dessin.centre(g, "NÉBULEUSE", W / 2, 32);
            g.setFont(Dessin.titre(16));
            g.setColor(Dessin.GLACE);
            g.drawString(noms[0] + "  " + serpents[0].points, 20, 32);
            g.setColor(Dessin.OR);
            Dessin.droite(g, serpents[1].points + "  " + noms[1], W - 20, 32);
        } else {
            g.drawString("NÉBULEUSE", 20, 32);
            g.setFont(Dessin.titre(18));
            g.setColor(Dessin.OR);
            Dessin.droite(g, "SCORE " + score, W - 20, 32);
            g.setFont(Dessin.texte(12));
            g.setColor(Dessin.GRIS);
            Dessin.centre(g, "Longueur " + serpents[0].anneaux.size(), W / 2, 30);
        }
    }
}
