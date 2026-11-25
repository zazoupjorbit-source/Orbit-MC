package fr.orbit.tele.jeux;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.List;

/** DÉFENSE ORBITALE : un ou deux chasseurs en bas, des vagues de drones qui descendent. À deux, on joue en équipe. */
final class Defense extends Jeu {

    private static final Color[] RANGS = {Dessin.ROUGE, Dessin.VIOLET, Dessin.GLACE, Dessin.VERT};
    private static final Color[] COQUES = {Dessin.BLEU, Dessin.OR};

    private final int n;
    private final double[] vx;
    private final int[] vies, recharge, bouclier;
    private double sens = 1, mere = Double.NaN;
    private int vague, flash;
    /** tirs : x, y, tireur. */
    private final List<double[]> drones = new ArrayList<>(), tirs = new ArrayList<>(), bombes = new ArrayList<>(), eclats = new ArrayList<>();

    Defense(Contexte ctx) {
        super(ctx);
        n = ctx.joueurs();
        vx = n == 2 ? new double[]{W / 3.0, W * 2 / 3.0} : new double[]{W / 2.0};
        vies = n == 2 ? new int[]{3, 3} : new int[]{3};
        recharge = new int[n];
        bouclier = new int[n];
        vagueSuivante();
    }

    private void vagueSuivante() {
        vague++;
        drones.clear();
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 9; c++) {
                drones.add(new double[]{110 + c * 46, 54 + r * 30, r});
            }
        }
        sens = 1;
    }

    @Override
    protected void tic(Manette a, Manette b) {
        for (int i = 0; i < n; i++) {
            Manette m = i == 0 ? a : b;
            recharge[i] = Math.max(0, recharge[i] - 1);
            bouclier[i] = Math.max(0, bouclier[i] - 1);
            if (vies[i] <= 0) {
                continue;
            }
            vx[i] += ((m.tenu(Manette.DROITE) ? 1 : 0) - (m.tenu(Manette.GAUCHE) ? 1 : 0)) * 6.5;
            vx[i] = Math.max(24, Math.min(W - 24, vx[i]));
            int moi = i;
            if ((m.tenu(Manette.A) || m.tenu(Manette.HAUT)) && recharge[i] == 0 && tirs.stream().filter(t -> t[2] == moi).count() < 4) {
                tirs.add(new double[]{vx[i], 322, i});
                recharge[i] = 8;
                son("tir");
            }
        }
        flash = Math.max(0, flash - 1);
        // drones : glissent, descendent d'un cran au bord
        double v = (0.55 + vague * 0.14 + (36 - drones.size()) * 0.035) * sens;
        boolean bord = false;
        for (double[] d : drones) {
            d[0] += v;
            bord |= d[0] < 26 || d[0] > W - 26;
            if (d[1] > 316) {
                fini = true;
            }
        }
        if (bord) {
            sens = -sens;
            drones.forEach(d -> d[1] += 12);
        }
        if (!drones.isEmpty() && alea.nextDouble() < (0.018 + vague * 0.004) * (n == 2 ? 1.4 : 1)) {
            double[] d = drones.get(alea.nextInt(drones.size()));
            bombes.add(new double[]{d[0], d[1] + 10, 3 + vague * 0.25});
        }
        // vaisseau-mère de passage
        if (Double.isNaN(mere)) {
            if (tics % 600 == 599) {
                mere = -30;
            }
        } else if ((mere += 2.6) > W + 30) {
            mere = Double.NaN;
        }
        tirs.removeIf(t -> (t[1] -= 9.5) < -10 || touche(t));
        bombes.removeIf(bo -> {
            bo[1] += bo[2];
            for (int i = 0; i < n; i++) {
                if (vies[i] > 0 && bouclier[i] == 0 && Math.abs(bo[0] - vx[i]) < 15 && Math.abs(bo[1] - 338) < 13) {
                    vies[i]--;
                    bouclier[i] = 60;
                    flash = 10;
                    son("boum");
                    eclats(vx[i], 338, COQUES[i]);
                    return true;
                }
            }
            return bo[1] > H + 8;
        });
        eclats.removeIf(e -> {
            e[0] += e[2];
            e[1] += e[3];
            return --e[4] <= 0;
        });
        if (drones.isEmpty()) {
            score += 100 * vague;
            son("bravo");
            vagueSuivante();
        }
        boolean vivant = false;
        for (int x : vies) {
            vivant |= x > 0;
        }
        if (!vivant) {
            fini = true;
        }
        if (fini) {
            son("perdu");
        }
    }

    private boolean touche(double[] t) {
        if (!Double.isNaN(mere) && Math.abs(t[0] - mere) < 24 && Math.abs(t[1] - 26) < 12) {
            score += 150;
            eclats(mere, 26, Dessin.OR);
            mere = Double.NaN;
            son("bravo");
            return true;
        }
        for (int i = 0; i < drones.size(); i++) {
            double[] d = drones.get(i);
            if (Math.abs(t[0] - d[0]) < 15 && Math.abs(t[1] - d[1]) < 11) {
                score += (4 - (int) d[2]) * 10;
                eclats(d[0], d[1], RANGS[(int) d[2]]);
                drones.remove(i);
                son("touche");
                return true;
            }
        }
        return false;
    }

    private void eclats(double x, double y, Color c) {
        for (int i = 0; i < 9; i++) {
            double a = alea.nextDouble() * Math.PI * 2, v = 1 + alea.nextDouble() * 3;
            eclats.add(new double[]{x, y, Math.cos(a) * v, Math.sin(a) * v, 14 + alea.nextInt(8), c.getRGB()});
        }
    }

    @Override
    public void dessiner(Graphics2D g) {
        Dessin.fond(g, tics, 0.5);
        if (flash > 0) {
            g.setColor(new Color(255, 80, 90, flash * 12));
            g.fillRect(0, 0, W, H);
        }
        g.setColor(Dessin.alpha(Dessin.BLEU, 90));
        g.fillRect(0, 360, W, 2);
        for (double[] d : drones) {
            int x = (int) d[0], y = (int) d[1];
            Color c = RANGS[(int) d[2]];
            int aile = (tics / 10 + x / 46) % 2 == 0 ? 3 : 0;
            g.setColor(Dessin.alpha(c, 150));
            g.fillPolygon(new int[]{x - 15, x - 7, x - 7}, new int[]{y + 2 + aile, y - 5, y + 6}, 3);
            g.fillPolygon(new int[]{x + 15, x + 7, x + 7}, new int[]{y + 2 + aile, y - 5, y + 6}, 3);
            g.setColor(c);
            g.fillRoundRect(x - 9, y - 8, 18, 15, 8, 8);
            g.setColor(Dessin.FOND);
            g.fillOval(x - 5, y - 4, 10, 7);
            g.setColor(Color.WHITE);
            g.fillOval(x - 2, y - 3, 4, 4);
        }
        if (!Double.isNaN(mere)) {
            int x = (int) mere;
            g.setColor(Dessin.OR);
            g.fillOval(x - 24, 20, 48, 14);
            g.setColor(Dessin.GLACE);
            g.fillOval(x - 10, 13, 20, 14);
            g.setColor(Dessin.ROUGE);
            for (int i = -2; i <= 2; i++) {
                g.fillOval(x + i * 9 - 2, 25, 4, 4);
            }
        }
        for (double[] t : tirs) {
            g.setColor(t[2] == 0 ? Dessin.GLACE : Dessin.OR);
            g.fillRoundRect((int) t[0] - 2, (int) t[1] - 8, 4, 14, 3, 3);
        }
        g.setColor(Dessin.ORANGE);
        for (double[] b : bombes) {
            g.fillOval((int) b[0] - 3, (int) b[1] - 5, 6, 10);
        }
        for (double[] e : eclats) {
            g.setColor(Dessin.alpha(new Color((int) e[5]), (int) e[4] * 12));
            g.fillRect((int) e[0] - 1, (int) e[1] - 1, 3, 3);
        }
        for (int i = 0; i < n; i++) {
            if (vies[i] > 0 && (bouclier[i] == 0 || tics % 6 < 3)) {
                int x = (int) vx[i], y = 338;
                g.setColor(Dessin.ORANGE);
                g.fillPolygon(new int[]{x - 5, x, x + 5}, new int[]{y + 10, y + 16 + (int) (tics % 4), y + 10}, 3);
                g.setColor(COQUES[i]);
                g.fillPolygon(new Polygon(new int[]{x, x + 16, x + 9, x - 9, x - 16}, new int[]{y - 16, y + 10, y + 6, y + 6, y + 10}, 5));
                g.setColor(i == 0 ? Dessin.GLACE : Dessin.BLANC);
                g.fillPolygon(new int[]{x, x + 5, x - 5}, new int[]{y - 8, y + 3, y + 3}, 3);
            }
        }
        g.setFont(Dessin.titre(15));
        g.setColor(Dessin.BLANC);
        Dessin.centre(g, "SCORE " + score + "   ·   VAGUE " + vague, W / 2, 378);
        for (int i = 0; i < n; i++) {
            g.setColor(COQUES[i]);
            for (int k = 0; k < vies[i]; k++) {
                int x = i == 0 ? 20 + k * 22 : W - 20 - k * 22;
                g.fillPolygon(new int[]{x, x + 8, x - 8}, new int[]{366, 380, 380}, 3);
            }
        }
    }
}
