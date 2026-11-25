package fr.orbit.tele.jeux;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;

/** PALET ORBITAL : palet sur glace en face à face, chacun sa moitié de table, premier à 7. Seul : contre ORBI-7. */
final class Palet extends Jeu {

    private static final int X0 = 30, X1 = 610, Y0 = 56, Y1 = 356, BUT0 = 151, BUT1 = 261, R = 20, RP = 11, BUTS = 7;

    private final double[] mx = {120, 520}, my = {206, 206}, mvx = new double[2], mvy = new double[2];
    private final int[] buts = new int[2];
    private final boolean robot;
    private double px = 320, py = 206, pvx, pvy;
    private int pause = 40, eclat, engage = 1;
    private final double[][] trace = new double[6][2];

    Palet(Contexte ctx) {
        super(ctx);
        robot = !ctx.deux();
        record = false;
        for (int i = 0; i < trace.length; i++) {
            trace[i] = new double[]{320, 206};
        }
        engager(alea.nextBoolean() ? 0 : 1);
    }

    /** Le palet repart du côté de celui qui vient d'encaisser. */
    private void engager(int cote) {
        px = cote == 0 ? 230 : 410;
        py = 206;
        pvx = pvy = 0;
        pause = 40;
        engage = cote;
    }

    @Override
    protected void tic(Manette a, Manette b) {
        eclat = Math.max(0, eclat - 1);
        for (int i = 0; i < 2; i++) {
            double ax, ay;
            if (i == 1 && robot) {
                // ORBI-7 : se place derrière le palet quand il est chez lui, sinon garde son but
                boolean chezLui = px > 320 && pvx > -6;
                double cx = chezLui ? px + 26 : 540, cy = chezLui ? py + (py - 206) * 0.08 : 206 + (py - 206) * 0.7;
                double dx = cx - mx[1], dy = cy - my[1], d = Math.hypot(dx, dy);
                if (chezLui && Math.abs(dy) < 14 && px < mx[1]) {
                    dx = -1;
                    dy = (py - my[1]) * 0.1;
                    d = Math.hypot(dx, dy);
                }
                double v = Math.min(4.3, d);
                ax = d < 0.5 ? 0 : dx / d * v;
                ay = d < 0.5 ? 0 : dy / d * v;
            } else {
                Manette m = i == 0 ? a : b;
                ax = (m.tenu(Manette.DROITE) ? 1 : 0) - (m.tenu(Manette.GAUCHE) ? 1 : 0);
                ay = (m.tenu(Manette.BAS) ? 1 : 0) - (m.tenu(Manette.HAUT) ? 1 : 0);
                double n = Math.hypot(ax, ay);
                ax = n == 0 ? 0 : ax / n * 6.4;
                ay = n == 0 ? 0 : ay / n * 6.4;
            }
            mvx[i] += (ax - mvx[i]) * 0.45;
            mvy[i] += (ay - mvy[i]) * 0.45;
            mx[i] = Math.max(i == 0 ? X0 + R : 320 + R, Math.min(i == 0 ? 320 - R : X1 - R, mx[i] + mvx[i]));
            my[i] = Math.max(Y0 + R, Math.min(Y1 - R, my[i] + mvy[i]));
        }
        if (pause > 0) {
            pause--;
        }
        System.arraycopy(trace, 0, trace, 1, trace.length - 1);
        trace[0] = new double[]{px, py};
        px += pvx;
        py += pvy;
        pvx *= 0.994;
        pvy *= 0.994;
        for (int i = 0; i < 2; i++) {
            double dx = px - mx[i], dy = py - my[i], d = Math.hypot(dx, dy);
            if (d < R + RP && d > 0.01) {
                double nx = dx / d, ny = dy / d, rel = (pvx - mvx[i]) * nx + (pvy - mvy[i]) * ny;
                px = mx[i] + nx * (R + RP);
                py = my[i] + ny * (R + RP);
                if (rel < 0) {
                    pvx -= 1.9 * rel * nx;
                    pvy -= 1.9 * rel * ny;
                    son("touche");
                }
            }
        }
        double v = Math.hypot(pvx, pvy);
        if (v > 13) {
            pvx *= 13 / v;
            pvy *= 13 / v;
        }
        if (py < Y0 + RP) {
            py = Y0 + RP;
            pvy = Math.abs(pvy) * 0.95;
        } else if (py > Y1 - RP) {
            py = Y1 - RP;
            pvy = -Math.abs(pvy) * 0.95;
        }
        boolean dansLeBut = py > BUT0 && py < BUT1;
        if (px < X0 + RP && !dansLeBut) {
            px = X0 + RP;
            pvx = Math.abs(pvx) * 0.95;
        } else if (px > X1 - RP && !dansLeBut) {
            px = X1 - RP;
            pvx = -Math.abs(pvx) * 0.95;
        } else if (px < X0 - RP || px > X1 + RP) {
            int marque = px < X0 ? 1 : 0;
            buts[marque]++;
            eclat = 24;
            son("bravo");
            if (buts[marque] >= BUTS) {
                fini = true;
                gagnant = marque + 1;
                bilan = buts[0] + "  –  " + buts[1];
            } else {
                engager(1 - marque);
            }
        }
    }

    @Override
    public void dessiner(Graphics2D g) {
        Dessin.fond(g, tics, 0.05);
        g.setColor(new Color(0x101C36));
        g.fillRoundRect(X0, Y0, X1 - X0, Y1 - Y0, 26, 26);
        g.setStroke(new BasicStroke(2f));
        g.setColor(Dessin.alpha(Dessin.GLACE, 70));
        g.drawLine(320, Y0, 320, Y1);
        g.drawOval(320 - 46, 206 - 46, 92, 92);
        g.setStroke(new BasicStroke(3f));
        g.setColor(Dessin.GLACE);
        g.drawRoundRect(X0, Y0, X1 - X0, Y1 - Y0, 26, 26);
        // buts
        g.setColor(Dessin.FOND);
        g.fillRect(X0 - 3, BUT0, 6, BUT1 - BUT0);
        g.fillRect(X1 - 3, BUT0, 6, BUT1 - BUT0);
        g.setColor(Dessin.BLEU);
        g.fillRoundRect(X0 - 12, BUT0, 8, BUT1 - BUT0, 6, 6);
        g.setColor(Dessin.OR);
        g.fillRoundRect(X1 + 4, BUT0, 8, BUT1 - BUT0, 6, 6);
        g.setStroke(new BasicStroke(1f));
        if (eclat > 0) {
            g.setColor(new Color(255, 255, 255, eclat * 6));
            g.fillRect(0, 0, W, H);
        }
        for (int i = trace.length - 1; i >= 0; i--) {
            g.setColor(new Color(255, 255, 255, 60 - i * 10));
            g.fillOval((int) trace[i][0] - RP + i, (int) trace[i][1] - RP + i, (RP - i) * 2, (RP - i) * 2);
        }
        g.setColor(Dessin.BLANC);
        g.fillOval((int) px - RP, (int) py - RP, RP * 2, RP * 2);
        g.setColor(Dessin.GLACE);
        g.drawOval((int) px - RP + 3, (int) py - RP + 3, RP * 2 - 6, RP * 2 - 6);
        for (int i = 0; i < 2; i++) {
            Color c = i == 0 ? Dessin.BLEU : Dessin.OR;
            g.setColor(Dessin.alpha(c, 70));
            g.fillOval((int) mx[i] - R - 4, (int) my[i] - R - 4, R * 2 + 8, R * 2 + 8);
            g.setColor(c);
            g.fillOval((int) mx[i] - R, (int) my[i] - R, R * 2, R * 2);
            g.setColor(Dessin.BLANC);
            g.fillOval((int) mx[i] - 8, (int) my[i] - 8, 16, 16);
        }
        g.setFont(Dessin.titre(30));
        g.setColor(Dessin.BLEU);
        Dessin.droite(g, String.valueOf(buts[0]), 300, 40);
        g.setColor(Dessin.OR);
        g.drawString(String.valueOf(buts[1]), 340, 40);
        g.setColor(Dessin.GRIS);
        Dessin.centre(g, "–", W / 2, 38);
        g.setFont(Dessin.texte(13));
        g.setColor(Dessin.BLEU);
        g.drawString(noms[0], X0, 36);
        g.setColor(Dessin.OR);
        Dessin.droite(g, noms[1], X1, 36);
        g.setFont(Dessin.texte(11));
        g.setColor(Dessin.GRIS);
        Dessin.centre(g, pause > 0 ? "Engagement pour " + noms[engage] : "Premier à " + BUTS, W / 2, 374);
    }
}
