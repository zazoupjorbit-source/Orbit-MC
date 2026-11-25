package fr.orbit.tele.jeux;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ORBIT RUSH : course sans fin sur trois voies, vue en perspective. À deux, écran scindé : chacun sa piste, avec
 * exactement les mêmes obstacles, et le dernier debout gagne.
 */
final class Rush extends Jeu {

    private static final int BARRIERE = 0, PORTIQUE = 1, NAVETTE = 2, PIECE = 3;
    private static final double F = 9, LOIN = 46;
    private static final int HORIZON = 118, SOL = 338;

    private static double p(double z) {
        return F / (F + z);
    }

    private static int y(double z) {
        return (int) (HORIZON + (SOL - HORIZON) * p(z));
    }

    private static int x(double v, double z) {
        return (int) (W / 2.0 + v * 150 * p(z));
    }

    /** La course d'un joueur. */
    private final class Piste {
        final Random sort;
        final Color tenue;
        final List<double[]> objets = new ArrayList<>(); // type, voie, distance
        double voie, vitesse = 0.30, dist, reste = 20;
        int cible, saut, glisse, pieces, points;
        boolean tombe;

        Piste(long graine, Color tenue) {
            sort = new Random(graine);
            this.tenue = tenue;
        }

        double hauteur() {
            return saut > 0 ? Math.sin(Math.PI * (20 - saut) / 20.0) : 0;
        }

        void tic(Manette m) {
            if (tombe) {
                return;
            }
            if (m.appui(Manette.GAUCHE) && cible > -1) {
                cible--;
            }
            if (m.appui(Manette.DROITE) && cible < 1) {
                cible++;
            }
            voie += (cible - voie) * 0.38;
            if ((m.appui(Manette.A) || m.appui(Manette.HAUT)) && saut == 0) {
                saut = 20;
                glisse = 0;
                son("clic");
            }
            if (m.appui(Manette.BAS) || m.appui(Manette.B)) {
                glisse = 18;
                saut = 0;
            }
            saut = Math.max(0, saut - 1);
            glisse = Math.max(0, glisse - 1);
            vitesse = Math.min(0.85, 0.30 + dist / 4500);
            dist += vitesse;
            points = (int) dist + pieces * 25;
            if ((reste -= vitesse) <= 0) {
                rangee();
                reste = 15 + sort.nextInt(9);
            }
            for (int i = objets.size() - 1; i >= 0; i--) {
                double[] o = objets.get(i);
                double avant = o[2];
                o[2] -= vitesse;
                if (o[2] < -3) {
                    objets.remove(i);
                } else if (avant > 0.5 && o[2] <= 0.5 && (int) o[1] == cible) {
                    int t = (int) o[0];
                    if (t == PIECE) {
                        pieces++;
                        objets.remove(i);
                        son("piece");
                    } else if (t == NAVETTE || (t == BARRIERE && hauteur() < 0.35) || (t == PORTIQUE && glisse == 0)) {
                        tombe = true;
                        son("perdu");
                    }
                }
            }
        }

        void rangee() {
            int d = sort.nextInt(100), libre = sort.nextInt(3) - 1;
            if (d < 30) {
                objets.add(new double[]{sort.nextInt(3), libre, LOIN});
                pieces((libre + 2) % 3 - 1, 3);
            } else if (d < 62) {
                for (int v = -1; v <= 1; v++) {
                    if (v != libre) {
                        objets.add(new double[]{NAVETTE, v, LOIN});
                    }
                }
                pieces(libre, 2);
            } else if (d < 84) {
                int t = sort.nextBoolean() ? BARRIERE : PORTIQUE;
                for (int v = -1; v <= 1; v++) {
                    objets.add(new double[]{t, v, LOIN});
                }
            } else {
                pieces(libre, 5);
            }
        }

        void pieces(int v, int n) {
            for (int i = 0; i < n; i++) {
                objets.add(new double[]{PIECE, v, LOIN + 2 + i * 2.2});
            }
        }

        void objet(Graphics2D g, double[] o) {
            double s = p(o[2]);
            if (o[2] > LOIN) {
                return;
            }
            int cx = x(o[1], o[2]), sol = y(o[2]), a = (int) Math.min(255, (LOIN - o[2]) * 30);
            switch ((int) o[0]) {
                case BARRIERE -> {
                    int w = (int) (124 * s), h = (int) (34 * s);
                    g.setColor(Dessin.alpha(Dessin.ROUGE, a));
                    g.fillRoundRect(cx - w / 2, sol - h, w, h, (int) (8 * s), (int) (8 * s));
                    g.setColor(Dessin.alpha(Color.WHITE, a));
                    for (int i = 0; i < 4; i++) {
                        g.fillRect(cx - w / 2 + (int) (w * (0.08 + i * 0.25)), sol - h + (int) (h * 0.3), (int) (w * 0.1), (int) (h * 0.4));
                    }
                }
                case PORTIQUE -> {
                    int w = (int) (130 * s), haut = (int) (100 * s), barre = (int) (38 * s), pied = Math.max(2, (int) (10 * s));
                    g.setColor(Dessin.alpha(Dessin.GRIS, a));
                    g.fillRect(cx - w / 2, sol - haut, pied, haut);
                    g.fillRect(cx + w / 2 - pied, sol - haut, pied, haut);
                    g.setColor(Dessin.alpha(Dessin.VIOLET, a));
                    g.fillRoundRect(cx - w / 2, sol - haut, w, barre, (int) (8 * s), (int) (8 * s));
                    g.setColor(Dessin.alpha(Dessin.OR, a));
                    g.fillRect(cx - w / 2 + pied, sol - haut + barre - Math.max(2, (int) (6 * s)), w - 2 * pied, Math.max(2, (int) (6 * s)));
                }
                case NAVETTE -> {
                    int w = (int) (132 * s), h = (int) (112 * s);
                    g.setColor(Dessin.alpha(new Color(0x24406E), a));
                    g.fillRoundRect(cx - w / 2, sol - h, w, h, (int) (22 * s), (int) (22 * s));
                    g.setColor(Dessin.alpha(Dessin.GLACE, a));
                    g.fillRoundRect(cx - (int) (w * 0.36), sol - (int) (h * 0.84), (int) (w * 0.72), (int) (h * 0.34), (int) (12 * s), (int) (12 * s));
                    g.setColor(Dessin.alpha(Dessin.ROUGE, a));
                    g.fillOval(cx - (int) (w * 0.4), sol - (int) (h * 0.3), (int) (w * 0.16), (int) (h * 0.14));
                    g.fillOval(cx + (int) (w * 0.24), sol - (int) (h * 0.3), (int) (w * 0.16), (int) (h * 0.14));
                }
                default -> {
                    int r = Math.max(2, (int) (15 * s));
                    int large = (int) (r * Math.abs(Math.cos(tics * 0.15 + o[2])));
                    g.setColor(Dessin.alpha(Dessin.OR, a));
                    g.fillOval(cx - Math.max(1, large), sol - (int) (44 * s) - r, Math.max(2, large * 2), r * 2);
                }
            }
        }

        /** Le décor, la piste et le coureur, sur 640 × 384. */
        void monde(Graphics2D g) {
            Dessin.fond(g, tics, 0.05);
            g.setPaint(new GradientPaint(0, HORIZON - 60, Dessin.alpha(Dessin.VIOLET, 0), 0, HORIZON, Dessin.alpha(Dessin.VIOLET, 110)));
            g.fillRect(0, HORIZON - 60, W, 60);
            g.setColor(Dessin.OR);
            g.fillArc(W / 2 - 70, HORIZON - 34, 140, 68, 0, 180);
            g.setColor(new Color(0x0C1326));
            g.fillRect(0, HORIZON, W, H - HORIZON);
            g.setColor(new Color(0x18233F));
            g.fillPolygon(new int[]{x(-1.5, LOIN), x(1.5, LOIN), x(1.5, -1.5), x(-1.5, -1.5)},
                    new int[]{y(LOIN), y(LOIN), y(-1.5), y(-1.5)}, 4);
            g.setColor(Dessin.alpha(Dessin.BLEU, 70));
            for (double z = 5 - dist % 5; z < LOIN; z += 5) {
                g.drawLine(x(-1.5, z), y(z), x(1.5, z), y(z));
            }
            for (double b : new double[]{-1.5, -0.5, 0.5, 1.5}) {
                g.setColor(Math.abs(b) > 1 ? Dessin.GLACE : Dessin.alpha(Dessin.GLACE, 90));
                g.drawLine(x(b, LOIN), y(LOIN), x(b, -1.5), y(-1.5));
            }
            objets.stream().filter(o -> o[2] >= 0).sorted((a, b) -> Double.compare(b[2], a[2])).forEach(o -> objet(g, o));
            int cx = (int) (W / 2.0 + voie * 150), haut = (int) (hauteur() * 86);
            g.setColor(new Color(0, 0, 0, 110));
            g.fillOval(cx - 24 + haut / 6, SOL - 6, 48 - haut / 3, 12);
            if (glisse > 0 || tombe) {
                g.setColor(tombe ? Dessin.GRIS : tenue);
                g.fillRoundRect(cx - 30, SOL - 24, 60, 22, 12, 12);
                g.setColor(Dessin.GLACE);
                g.fillOval(cx + 12, SOL - 34, 20, 20);
            } else {
                int pas = saut > 0 ? 0 : (int) (Math.sin(tics * 0.6) * 6);
                g.setColor(new Color(0x24406E));
                g.fillRoundRect(cx - 13, SOL - 26 - haut, 10, 26 + pas, 6, 6);
                g.fillRoundRect(cx + 3, SOL - 26 - haut, 10, 26 - pas, 6, 6);
                g.setColor(tenue);
                g.fillRoundRect(cx - 17, SOL - 62 - haut, 34, 40, 12, 12);
                g.setColor(Dessin.BLANC);
                g.fillRect(cx - 17, SOL - 46 - haut, 34, 4);
                g.setColor(Dessin.GLACE);
                g.fillOval(cx - 12, SOL - 86 - haut, 24, 24);
            }
            objets.stream().filter(o -> o[2] < 0).forEach(o -> objet(g, o));
        }
    }

    private final Piste[] pistes;

    Rush(Contexte ctx) {
        super(ctx);
        long graine = alea.nextLong();
        pistes = ctx.deux() ? new Piste[]{new Piste(graine, Dessin.BLEU), new Piste(graine, Dessin.OR)} : new Piste[]{new Piste(graine, Dessin.BLEU)};
        record = !ctx.deux();
    }

    @Override
    protected void tic(Manette a, Manette b) {
        pistes[0].tic(a);
        if (pistes.length == 1) {
            score = pistes[0].points;
            fini = pistes[0].tombe;
            return;
        }
        pistes[1].tic(b);
        if (pistes[0].tombe || pistes[1].tombe) {
            fini = true;
            gagnant = pistes[0].tombe && pistes[1].tombe ? 0 : pistes[0].tombe ? 2 : 1;
            bilan = pistes[0].points + "  –  " + pistes[1].points;
        }
    }

    @Override
    public void dessiner(Graphics2D g) {
        if (pistes.length == 1) {
            Piste p = pistes[0];
            p.monde(g);
            Dessin.compteur(g, 14, 12, 130, "SCORE", String.valueOf(p.points), Dessin.OR);
            Dessin.compteur(g, W - 104, 12, 90, "PIÈCES", String.valueOf(p.pieces), Dessin.OR);
            g.setFont(Dessin.titre(13));
            g.setColor(Dessin.GLACE);
            Dessin.centre(g, (int) (p.vitesse * 260) + " km/h", W / 2, 30);
            return;
        }
        g.setColor(Dessin.FOND);
        g.fillRect(0, 0, W, H);
        for (int i = 0; i < 2; i++) {
            Piste p = pistes[i];
            Graphics2D v = (Graphics2D) g.create();
            v.clipRect(i * 320, 40, 320, 308);
            v.translate(i * 320 + 160 - 320 * 0.8, 40);
            v.scale(0.8, 0.8);
            p.monde(v);
            v.dispose();
            Color c = i == 0 ? Dessin.BLEU : Dessin.OR;
            g.setFont(Dessin.titre(15));
            g.setColor(c);
            g.drawString("J" + (i + 1) + "  " + noms[i], i * 320 + 14, 26);
            g.setColor(Dessin.BLANC);
            Dessin.droite(g, String.valueOf(p.points), i * 320 + 306, 26);
            g.setFont(Dessin.texte(11));
            g.setColor(Dessin.GRIS);
            g.drawString(p.pieces + " pièces   ·   " + (int) (p.vitesse * 260) + " km/h", i * 320 + 14, 368);
        }
        g.setColor(Dessin.GLACE);
        g.fillRect(319, 0, 2, H);
    }
}
