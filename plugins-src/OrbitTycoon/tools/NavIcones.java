import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/**
 * Icônes HD (32x32, pixel art ombré) du Navigateur orbital d'Orbit Tycoon : 10 catégories (médaillons dorés)
 * et toutes les sous-catégories. Formes vectorielles tramées au centre des pixels, relief automatique
 * (lumière en haut à gauche), contour bleu nuit. Couleurs d'Orbit MC : bleu glacé + or.
 *
 * Sorties (pour chaque id) : pack/assets/orbittycoon/textures/item/nav_<id>.png,
 *   models/item/nav_<id>.json, items/nav_<id>.json ; aperçu agrandi en option.
 * Usage : java tools/NavIcones.java <dossier_pack> [<image_apercu.png>]
 */
public class NavIcones {

    static final int N = 32;
    static final int CONTOUR = 0x0A1426, BLANC = 0xF2F6FA, NUIT = 0x16305C;
    static final int BLEU = 0x62CFF4, OR = 0xE0B864, VERT = 0x7CFC9A, VIOLET = 0xC77DFF, ROUGE = 0xFF4D4D,
            ORANGE = 0xFF9F43, ROSE = 0xFF8FC7, BLE = 0xF5D442, ACIER = 0xB0BEC5, ACIER_F = 0x6E7B86,
            BOIS = 0x9A5F34, BOIS_C = 0xC8894B, PEAU = 0xE8B48C, OS = 0xEDE6D3, HERBE = 0x5BC44A, TERRE = 0x8A5A3A,
            PARCHEMIN = 0xF2DDA6, PECHE = 0x3FA9F5, DIAMANT = 0x5FF2E8, PIERRE = 0x8C96A3;

    // ------------------------------------------------------------------ couleurs

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    // ------------------------------------------------------------------ formes

    static Shape circ(double cx, double cy, double r) { return new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r); }
    static Shape ell(double cx, double cy, double rx, double ry) { return new Ellipse2D.Double(cx - rx, cy - ry, 2 * rx, 2 * ry); }
    static Shape rect(double x, double y, double w, double h) { return new Rectangle2D.Double(x, y, w, h); }
    static Shape rr(double x, double y, double w, double h, double a) { return new RoundRectangle2D.Double(x, y, w, h, a, a); }
    static Shape poly(double... p) {
        Path2D.Double s = new Path2D.Double();
        s.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) s.lineTo(p[i], p[i + 1]);
        s.closePath();
        return s;
    }
    static Shape ligne(double... p) {
        Path2D.Double s = new Path2D.Double();
        s.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) s.lineTo(p[i], p[i + 1]);
        return s;
    }
    static Shape trait(double x1, double y1, double x2, double y2, double w) {
        return new BasicStroke((float) w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(new Line2D.Double(x1, y1, x2, y2));
    }
    static Shape trace(Shape s, double w) {
        return new BasicStroke((float) w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(s);
    }
    static Shape moins(Shape a, Shape... b) { Area r = new Area(a); for (Shape s : b) r.subtract(new Area(s)); return r; }
    static Shape plus(Shape... s) { Area r = new Area(); for (Shape x : s) r.add(new Area(x)); return r; }
    static Shape inter(Shape a, Shape b) { Area r = new Area(a); r.intersect(new Area(b)); return r; }
    static Shape rot(Shape s, double deg, double cx, double cy) {
        return AffineTransform.getRotateInstance(Math.toRadians(deg), cx, cy).createTransformedShape(s);
    }
    static Shape dep(Shape s, double dx, double dy) { return AffineTransform.getTranslateInstance(dx, dy).createTransformedShape(s); }
    static Shape etoile(double cx, double cy, double R, double r, int n) {
        double[] p = new double[4 * n];
        for (int i = 0; i < 2 * n; i++) {
            double a = -Math.PI / 2 + i * Math.PI / n, d = i % 2 == 0 ? R : r;
            p[2 * i] = cx + Math.cos(a) * d;
            p[2 * i + 1] = cy + Math.sin(a) * d;
        }
        return poly(p);
    }

    // ------------------------------------------------------------------ toile

    static final class Ic {
        final int[] px = new int[N * N];
        boolean[] groupe;
        AffineTransform t = new AffineTransform();

        boolean[] masque(Shape s) {
            Shape u = t.createTransformedShape(s);
            boolean[] m = new boolean[N * N];
            Rectangle b = u.getBounds();
            for (int y = Math.max(0, b.y); y <= Math.min(N - 1, b.y + b.height); y++)
                for (int x = Math.max(0, b.x); x <= Math.min(N - 1, b.x + b.width); x++)
                    if (u.contains(x + 0.5, y + 0.5)) m[y * N + x] = true;
            return m;
        }

        void ecrire(int i, int c) { px[i] = 0xFF000000 | c; if (groupe != null) groupe[i] = true; }

        static boolean dans(boolean[] m, int x, int y) { return x >= 0 && y >= 0 && x < N && y < N && m[y * N + x]; }

        /** Aplat d'une couleur. */
        void plat(Shape s, int c) {
            boolean[] m = masque(s);
            for (int i = 0; i < m.length; i++) if (m[i]) ecrire(i, c);
        }

        /** Forme en relief : dégradé lumière haut-gauche → ombre bas-droite, arêtes éclairées / assombries. */
        void relief(Shape s, int base) {
            boolean[] m = masque(s);
            int x0 = N, y0 = N, x1 = -1, y1 = -1;
            for (int i = 0; i < m.length; i++) if (m[i]) {
                int x = i % N, y = i / N;
                x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
            }
            if (x1 < 0) return;
            int w = x1 - x0, h = y1 - y0;
            int clair = mix(base, 0xFFFFFF, 0.30), tresClair = mix(base, 0xFFFFFF, 0.62),
                    ombre = mix(base, CONTOUR, 0.22), sombre = mix(base, CONTOUR, 0.48);
            boolean grand = w >= 4 && h >= 4;
            for (int i = 0; i < m.length; i++) {
                if (!m[i]) continue;
                int x = i % N, y = i / N;
                double tt = (w == 0 ? 0.5 : (x - x0) / (double) w) * 0.5 + (h == 0 ? 0.5 : (y - y0) / (double) h) * 0.5;
                int c = base;
                if (grand) { if (tt < 0.30) c = clair; else if (tt > 0.70) c = ombre; }
                boolean haut = !dans(m, x, y - 1), gauche = !dans(m, x - 1, y), bas = !dans(m, x, y + 1), droite = !dans(m, x + 1, y);
                if ((bas || droite) && !(haut || gauche)) c = sombre;
                else if ((haut || gauche) && !(bas || droite)) c = tt < 0.40 ? tresClair : clair;
                ecrire(i, c);
            }
        }

        void debut() { groupe = new boolean[N * N]; }

        /** Contour autour du groupe en cours (par-dessus ce qui est dessous). */
        void fin() {
            boolean[] g = groupe;
            groupe = null;
            for (int i = 0; i < g.length; i++) {
                if (g[i]) continue;
                int x = i % N, y = i / N;
                if (dans(g, x + 1, y) || dans(g, x - 1, y) || dans(g, x, y + 1) || dans(g, x, y - 1)) px[i] = 0xFF000000 | CONTOUR;
            }
        }

        /** Contour bleu nuit autour de tout le dessin (pixels vides seulement). */
        void contourGlobal() {
            boolean[] g = new boolean[N * N];
            for (int i = 0; i < g.length; i++) g[i] = px[i] != 0;
            for (int i = 0; i < g.length; i++) {
                if (g[i]) continue;
                int x = i % N, y = i / N;
                if (dans(g, x + 1, y) || dans(g, x - 1, y) || dans(g, x, y + 1) || dans(g, x, y - 1)) px[i] = 0xFF000000 | CONTOUR;
            }
        }

        /** Petite étincelle (croix de 5 pixels, cœur blanc). */
        void etincelle(int x, int y, int c) {
            int[][] d = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] v : d) {
                int xx = x + v[0], yy = y + v[1];
                if (xx >= 0 && yy >= 0 && xx < N && yy < N) px[yy * N + xx] = 0xFF000000 | (v[0] == 0 && v[1] == 0 ? 0xFFFFFF : c);
            }
        }

        void point(int x, int y, int c) { if (x >= 0 && y >= 0 && x < N && y < N) px[y * N + x] = 0xFF000000 | c; }

        BufferedImage image() {
            BufferedImage b = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
            for (int i = 0; i < px.length; i++) b.setRGB(i % N, i / N, px[i]);
            return b;
        }
    }

    // ------------------------------------------------------------------ motifs réutilisables

    static void pioche(Ic g, int tete) {
        g.relief(trait(7, 26, 21.5, 11.5, 2.8), BOIS_C);
        AffineTransform a = new AffineTransform();
        a.translate(20.5, 11.5);
        a.rotate(Math.toRadians(45));
        Shape croissant = a.createTransformedShape(moins(ell(0, 0, 12.5, 5.5), ell(0, 3.2, 12.5, 5.2)));
        g.relief(croissant, tete);
        g.plat(rect(18.5, 10, 3, 3), mix(BOIS_C, CONTOUR, 0.3));
    }

    static void epee(Ic g) {
        g.relief(poly(3.5, 3.5, 9, 5.2, 20.6, 16.8, 16.8, 20.6, 5.2, 9), 0xD8E6F0);
        g.plat(trait(6.5, 6.5, 16.5, 16.5, 1.2), BLEU);
        g.relief(trait(14.5, 23.5, 23.5, 14.5, 2.8), OR);
        g.relief(trait(20.5, 20.5, 25, 25, 2.8), NUIT);
        g.relief(circ(26.6, 26.6, 2.4), OR);
    }

    static void sac(Ic g) {
        g.relief(trace(moins(ell(16, 11, 5.5, 5.5), rect(0, 11, 32, 32)), 2.2), OR);
        g.relief(rr(5, 10, 22, 19, 6), BLEU);
        g.plat(rect(5, 13, 22, 1.6), mix(BLEU, CONTOUR, 0.35));
        g.relief(etoile(16, 20.5, 5.6, 2.4, 5), OR);
        g.etincelle(25, 7, OR);
    }

    static void casque(Ic g) {
        g.relief(rr(7, 23, 18, 7, 3), ACIER);
        g.plat(rect(9, 25, 14, 1.2), OR);
        g.relief(circ(16, 14.5, 11), BLANC);
        g.relief(ell(16, 15.5, 7.8, 5.8), NUIT);
        g.plat(ell(13.5, 13.5, 2.6, 1.6), BLEU);
        g.plat(circ(19.5, 17.5, 1), mix(BLEU, NUIT, 0.3));
        g.relief(trait(23.5, 6.5, 26.5, 2.5, 1.6), ACIER);
        g.relief(circ(26.8, 2.6, 1.8), OR);
    }

    static void parchemin(Ic g) {
        g.relief(rect(8, 6, 16, 20), PARCHEMIN);
        g.relief(rr(5.5, 3.5, 21, 5, 5), BOIS_C);
        g.relief(rr(5.5, 24, 21, 5, 5), BOIS_C);
        for (int i = 0; i < 3; i++) g.plat(rect(10.5, 10 + i * 3, 11, 1.1), mix(PARCHEMIN, BOIS, 0.55));
        g.relief(trace(ligne(11, 18.5, 14.5, 22, 22.5, 13), 2.6), VERT);
    }

    static void boussole(Ic g) {
        g.relief(circ(16, 16, 13), OR);
        g.relief(circ(16, 16, 10.3), BLANC);
        for (int i = 0; i < 4; i++) g.plat(rot(rect(15.4, 6.6, 1.2, 2), i * 90, 16, 16), ACIER_F);
        g.relief(poly(16, 6.5, 19, 16, 13, 16), ROUGE);
        g.relief(poly(16, 25.5, 19, 16, 13, 16), BLEU);
        g.relief(circ(16, 16, 1.8), OR);
    }

    static void ampoule(Ic g) {
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(-160 + i * 35);
            g.plat(trait(16 + Math.cos(a) * 11.5, 12 + Math.sin(a) * 11.5, 16 + Math.cos(a) * 14, 12 + Math.sin(a) * 14, 1.6), OR);
        }
        g.relief(circ(16, 12, 8.6), BLE);
        g.relief(poly(11.5, 17.5, 20.5, 17.5, 19, 22, 13, 22), BLE);
        g.plat(trace(ligne(13.5, 18, 15, 13.5, 17, 13.5, 18.5, 18), 1.1), ORANGE);
        g.plat(ell(12.6, 9, 2, 2.8), 0xFFFBE0);
        g.relief(rr(12, 21.5, 8, 6.5, 2), ACIER);
        g.plat(rect(12, 23.6, 8, 1), ACIER_F);
        g.plat(rect(12, 25.6, 8, 1), ACIER_F);
        g.relief(rect(14, 28, 4, 2), ACIER_F);
    }

    static void recommencer(Ic g) {
        Shape arc = new Arc2D.Double(5, 5, 22, 22, 60, 280, Arc2D.OPEN);
        g.relief(trace(arc, 4), BLEU);
        double a = Math.toRadians(-60);
        double ex = 16 + Math.cos(a) * 11, ey = 16 + Math.sin(a) * 11;
        g.relief(poly(ex - 6.5, ey - 1.5, ex + 3.5, ey - 3.5, ex + 1.5, ey + 6.5), BLEU);
        g.relief(circ(16, 16, 3.2), OR);
    }

    static void passer(Ic g) {
        g.relief(poly(4, 7, 15.5, 16, 4, 25), VIOLET);
        g.relief(poly(14, 7, 25.5, 16, 14, 25), VIOLET);
        g.relief(rr(25, 7, 3.6, 18, 1.5), OR);
    }

    static void livreOuvert(Ic g) {
        g.relief(poly(1.5, 9, 16, 11.5, 30.5, 9, 30.5, 26, 16, 28.5, 1.5, 26), NUIT);
        g.relief(poly(3, 7, 15, 9.5, 15, 25.5, 3, 23), BLANC);
        g.relief(poly(17, 9.5, 29, 7, 29, 23, 17, 25.5), mix(BLANC, PARCHEMIN, 0.4));
        for (int i = 0; i < 4; i++) {
            g.plat(poly(5, 11 + i * 3.2, 13, 12.6 + i * 3.2, 13, 13.6 + i * 3.2, 5, 12 + i * 3.2), ACIER);
            g.plat(poly(19, 12.6 + i * 3.2, 27, 11 + i * 3.2, 27, 12 + i * 3.2, 19, 13.6 + i * 3.2), ACIER);
        }
        g.relief(poly(21, 8.3, 24.5, 7.6, 24.5, 30, 22.8, 28, 21, 30), OR);
    }

    static void livre(Ic g) {
        g.relief(rr(21, 5.5, 5, 23, 1.5), BLANC);
        g.relief(rr(5, 3.5, 18, 26, 3), NUIT);
        g.relief(rect(5, 3.5, 3, 26), mix(NUIT, CONTOUR, 0.25));
        g.plat(trace(rr(10, 7, 10.5, 19, 2), 1.1), OR);
        g.relief(etoile(15.3, 16.5, 4.8, 2, 4), OR);
        g.etincelle(25, 4, BLEU);
    }

    static void ile(Ic g, boolean maison) {
        g.relief(poly(4, 14, 28, 14, 24, 21, 18.5, 29.5, 13, 23, 7.5, 19.5), TERRE);
        g.plat(poly(16, 22, 18.5, 29.5, 20, 22), mix(TERRE, CONTOUR, 0.3));
        g.relief(ell(16, 14, 13, 4.3), HERBE);
        if (maison) {
            g.relief(rect(7, 8, 8, 6), BLANC);
            g.relief(poly(5.5, 8.5, 11, 3.5, 16.5, 8.5), BLEU);
            g.plat(rect(10, 10.5, 2.4, 3.5), BOIS);
        }
        g.relief(rect(20, 4, 2.6, 10), BOIS);
        g.relief(circ(21.3, 5.5, 5), mix(HERBE, CONTOUR, 0.15));
        g.plat(circ(19.6, 3.8, 1.4), mix(HERBE, 0xFFFFFF, 0.35));
    }

    static void plusBadge(Ic g, double cx, double cy, int c) {
        g.relief(circ(cx, cy, 5.6), c);
        g.plat(rect(cx - 3, cy - 0.9, 6, 1.8), BLANC);
        g.plat(rect(cx - 0.9, cy - 3, 1.8, 6), BLANC);
    }

    static void niveau(Ic g) {
        g.relief(poly(16, 3, 27.5, 14.5, 20.5, 14.5, 20.5, 28.5, 11.5, 28.5, 11.5, 14.5, 4.5, 14.5), VERT);
        g.plat(rect(14, 17, 4, 1.4), mix(VERT, 0xFFFFFF, 0.5));
        g.plat(rect(14, 21, 4, 1.4), mix(VERT, 0xFFFFFF, 0.5));
        g.etincelle(5, 6, OR);
        g.etincelle(27, 23, OR);
    }

    static void agrandir(Ic g) {
        g.relief(rr(10, 10, 12, 12, 2), HERBE);
        g.plat(rect(10, 16, 12, 6), TERRE);
        g.plat(rect(10, 15, 12, 1.4), mix(HERBE, CONTOUR, 0.2));
        for (int i = 0; i < 4; i++) {
            Shape fl = poly(2, 2, 10.5, 2, 7.6, 4.9, 10.5, 7.8, 7.8, 10.5, 4.9, 7.6, 2, 10.5);
            g.relief(rot(fl, i * 90, 16, 16), OR);
        }
    }

    static void personne(Ic g, int c) {
        g.relief(rr(4, 17, 18, 13, 9), c);
        g.relief(circ(13, 10, 5.6), PEAU);
        g.plat(moins(circ(13, 9, 6), rect(0, 9.5, 32, 32)), BOIS);
    }

    static void etiquette(Ic g, int c, boolean piece) {
        Shape tag = moins(poly(3, 16, 10, 7, 28, 7, 28, 25, 10, 25), circ(9.5, 16, 1.9));
        g.relief(trace(new Arc2D.Double(4, 6, 10, 10, 90, 160, Arc2D.OPEN), 1.2), ACIER_F);
        g.relief(tag, c);
        if (piece) {
            g.relief(circ(19.5, 16, 5.2), OR);
            g.plat(trace(circ(19.5, 16, 3), 1), mix(OR, BOIS, 0.5));
        } else {
            g.plat(rect(14, 12, 11, 1.6), BLANC);
            g.plat(rect(14, 15.5, 8, 1.6), BLANC);
            g.plat(rect(14, 19, 10, 1.6), BLANC);
        }
    }

    static void porte(Ic g) {
        g.relief(rr(6, 2.5, 20, 28, 9), PIERRE);
        g.relief(moins(rr(9, 5.5, 14, 25, 7), rect(0, 30.5, 32, 5)), BOIS_C);
        g.plat(rect(15.4, 6, 1.2, 24), BOIS);
        g.plat(rect(9, 14, 14, 1.2), BOIS);
        g.plat(rect(9, 22, 14, 1.2), BOIS);
        g.relief(circ(19.5, 18, 1.6), OR);
        g.relief(poly(23, 13, 30, 18, 23, 23), VERT);
    }

    static void longueVue(Ic g) {
        Shape tube = plus(rr(3, 15.5, 10, 7, 2), rr(11, 13.5, 9, 9, 2), rr(18, 11.5, 11, 11, 2));
        Shape r = rot(tube, -28, 16, 16);
        g.relief(r, NUIT);
        g.relief(rot(rect(12, 13.5, 1.6, 9), -28, 16, 16), OR);
        g.relief(rot(rect(19, 11.5, 1.6, 11), -28, 16, 16), OR);
        g.relief(rot(ell(28.6, 17, 1.6, 5), -28, 16, 16), BLEU);
        g.relief(trait(12, 22, 9, 30, 1.8), ACIER_F);
        g.relief(trait(14, 21, 18, 30, 1.8), ACIER_F);
    }

    static void urne(Ic g) {
        g.relief(rect(11.5, 3, 9, 13), BLANC);
        g.relief(trace(ligne(13.5, 9, 15.5, 11.5, 19, 6.5), 1.6), VERT);
        g.relief(rr(4.5, 13, 23, 16, 3), BLEU);
        g.plat(rect(9.5, 13, 13, 2), CONTOUR);
        g.relief(rect(4.5, 16.5, 23, 2.4), OR);
        g.relief(etoile(16, 23.5, 4, 1.8, 5), OR);
    }

    static void feuArtifice(Ic g) {
        int[] cs = {OR, BLEU, ROSE, VERT, VIOLET, ORANGE, BLEU, OR};
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 - 90);
            g.relief(trait(16 + Math.cos(a) * 5.5, 13 + Math.sin(a) * 5.5, 16 + Math.cos(a) * 11, 13 + Math.sin(a) * 11, 2.4), cs[i]);
        }
        g.relief(etoile(16, 13, 5, 2.2, 5), OR);
        g.relief(trait(16, 20, 16, 30, 1.6), ACIER_F);
        g.etincelle(4, 27, OR);
        g.etincelle(28, 4, BLEU);
    }

    static void coffre(Ic g, boolean portail) {
        if (portail) g.relief(trace(ell(16, 16, 14, 13.5), 2.6), BLEU);
        g.relief(rr(4.5, 14, 23, 13.5, 2), BOIS);
        g.relief(rr(4.5, 6.5, 23, 9, 7), BOIS_C);
        g.relief(rect(4.5, 14, 23, 2.4), OR);
        g.relief(rect(6.5, 7.5, 2.4, 20), mix(OR, BOIS, 0.15));
        g.relief(rect(23.1, 7.5, 2.4, 20), mix(OR, BOIS, 0.15));
        g.relief(rr(13.5, 12.5, 5, 6.5, 1.5), OR);
        g.plat(rect(15.4, 15, 1.2, 2.4), CONTOUR);
    }

    static void cle(Ic g) {
        g.relief(trait(14, 14, 26.5, 26.5, 3.4), OR);
        g.relief(rot(rect(21, 26.5, 3, 4), 0, 0, 0), OR);
        g.relief(poly(22.8, 22.8, 27.5, 18.1, 29.2, 19.8, 24.5, 24.5), OR);
        g.relief(poly(18.8, 18.8, 22.3, 15.3, 24, 17, 20.5, 20.5), OR);
        g.relief(moins(circ(10, 10, 7.2), circ(10, 10, 3.4)), OR);
        g.relief(circ(10, 10, 2.6), BLEU);
    }

    static void generateur(Ic g) {
        g.relief(rr(9, 2.5, 4, 6, 1), ACIER_F);
        g.relief(rr(19, 2.5, 4, 6, 1), ACIER_F);
        g.relief(rr(4.5, 7, 23, 22, 4), ACIER);
        g.relief(circ(16, 17.5, 7.2), NUIT);
        g.relief(circ(16, 17.5, 5), BLEU);
        g.plat(circ(16, 17.5, 2.2), 0xE8FBFF);
        g.relief(rect(6.5, 9, 2, 3), OR);
        g.relief(rect(23.5, 9, 2, 3), OR);
        g.plat(rect(7, 26, 18, 1.1), ACIER_F);
    }

    static void cube(Ic g, double cx, double cy, double s, int c) {
        g.plat(poly(cx, cy - s * 0.5, cx + s, cy, cx, cy + s * 0.5, cx - s, cy), mix(c, 0xFFFFFF, 0.28));
        g.plat(poly(cx - s, cy, cx, cy + s * 0.5, cx, cy + s * 1.5, cx - s, cy + s), c);
        g.plat(poly(cx + s, cy, cx, cy + s * 0.5, cx, cy + s * 1.5, cx + s, cy + s), mix(c, CONTOUR, 0.32));
        g.plat(trait(cx - s + 0.6, cy + 0.2, cx - 0.2, cy + s * 0.5 - 0.1, 0.9), mix(c, 0xFFFFFF, 0.55));
    }

    static void blocs(Ic g) {
        cube(g, 16, 4.5, 7.5, OR);
        cube(g, 9, 13.5, 7.5, 0xC0583E);
        cube(g, 23, 13.5, 7.5, BLEU);
    }

    static void vendre(Ic g) {
        for (int i = 0; i < 4; i++) {
            double y = 26 - i * 4;
            g.relief(rect(3.5, y - 1, 15, 3.5), mix(OR, BOIS, 0.25));
            g.relief(ell(11, y - 1, 7.5, 2.6), OR);
        }
        g.relief(poly(18, 13, 25, 6, 21.5, 6, 21.5, 2.5, 30, 2.5, 30, 11, 26.5, 11, 26.5, 7.5, 19.5, 14.5), VERT);
        g.etincelle(26, 20, OR);
    }

    static void marche(Ic g) {
        g.relief(rect(6, 11, 2.4, 10), BOIS);
        g.relief(rect(23.6, 11, 2.4, 10), BOIS);
        for (int i = 0; i < 6; i++) g.relief(rect(3 + i * 4.333, 4, 4.333, 7.5), i % 2 == 0 ? BLEU : BLANC);
        for (int i = 0; i < 6; i++) g.relief(circ(5.17 + i * 4.333, 11.4, 2.17), i % 2 == 0 ? BLEU : BLANC);
        g.relief(rr(3.5, 19, 25, 10, 2), BOIS_C);
        g.plat(rect(3.5, 22.5, 25, 1.1), BOIS);
        g.relief(circ(9, 17.5, 2.6), ROUGE);
        g.relief(circ(15, 17, 2.8), OR);
        g.relief(circ(21.5, 17.5, 2.6), VERT);
    }

    static void marteau(Ic g) {
        g.relief(rr(15.5, 25, 14, 5, 2), NUIT);
        g.plat(rect(15.5, 26.5, 14, 1.2), OR);
        g.relief(trait(17, 15, 5.5, 26.5, 3), BOIS_C);
        Shape tete = rot(rr(12, 4, 15, 8.5, 3), 45, 19.5, 8.25);
        g.relief(tete, BOIS);
        g.relief(rot(rect(14.5, 4, 2.2, 8.5), 45, 19.5, 8.25), OR);
        g.relief(rot(rect(22.3, 4, 2.2, 8.5), 45, 19.5, 8.25), OR);
    }

    static void ventes(Ic g) {
        g.relief(rr(5.5, 4.5, 21, 25.5, 3), BOIS_C);
        g.relief(rect(8, 8, 16, 19.5), BLANC);
        g.relief(rr(11.5, 2.5, 9, 4.5, 1.5), ACIER);
        for (int i = 0; i < 4; i++) {
            g.relief(circ(11, 11.5 + i * 4.3, 1.5), OR);
            g.plat(rect(14, 10.9 + i * 4.3, 8, 1.3), ACIER_F);
        }
    }

    static void info(Ic g) {
        g.relief(circ(16, 16, 13), BLEU);
        g.plat(trace(circ(16, 16, 10.8), 0.9), mix(BLEU, 0xFFFFFF, 0.45));
        g.relief(circ(16, 9.5, 2.3), BLANC);
        g.relief(rr(13.8, 13.5, 4.4, 11, 1.5), BLANC);
        g.relief(rect(12, 22.5, 8, 2.2), BLANC);
    }

    static void retour(Ic g) {
        g.relief(poly(3, 16, 14, 5, 14, 11.5, 28.5, 11.5, 28.5, 20.5, 14, 20.5, 14, 27), OR);
    }

    static void fermer(Ic g) {
        g.relief(circ(16, 16, 13), ROUGE);
        g.relief(rot(rr(8, 14, 16, 4, 1.5), 45, 16, 16), BLANC);
        g.relief(rot(rr(8, 14, 16, 4, 1.5), -45, 16, 16), BLANC);
    }

    static void poisson(Ic g) {
        g.relief(poly(22, 16, 30, 8.5, 28, 16, 30, 23.5), mix(PECHE, NUIT, 0.2));
        g.relief(poly(10, 10, 16, 5.5, 19, 10.5), mix(PECHE, NUIT, 0.2));
        g.relief(ell(14, 16, 11, 7.2), PECHE);
        g.plat(moins(ell(14, 17.5, 9, 4.5), rect(0, 0, 32, 18.5)), mix(PECHE, 0xFFFFFF, 0.55));
        g.plat(trace(new Arc2D.Double(13, 10, 6, 12, 100, 160, Arc2D.OPEN), 1), mix(PECHE, NUIT, 0.35));
        g.relief(circ(8.5, 14.5, 2.4), BLANC);
        g.plat(circ(8, 14.5, 1.1), CONTOUR);
        g.etincelle(5, 5, BLEU);
    }

    static void bucheron(Ic g) {
        g.relief(rect(3, 19, 18, 9.5), BOIS);
        g.plat(rect(3, 22, 18, 1), mix(BOIS, CONTOUR, 0.3));
        g.plat(rect(3, 25.5, 18, 1), mix(BOIS, CONTOUR, 0.3));
        g.relief(ell(21, 23.75, 4, 4.75), BOIS_C);
        g.plat(trace(ell(21, 23.75, 2, 2.6), 0.9), BOIS);
        g.relief(trait(9, 18, 23, 4, 2.8), BOIS_C);
        Shape lame = poly(19, 4, 23.5, 0.5, 30, 6, 29, 12.5, 24.5, 9);
        g.relief(lame, ACIER);
        g.plat(trait(29.4, 6.5, 28.6, 12, 1), 0xFFFFFF);
    }

    static void ble(Ic g) {
        double[][] tiges = {{9, 29, 7, 7}, {16, 29, 16, 4}, {23, 29, 25, 7}};
        for (double[] t : tiges) g.relief(trait(t[0], t[1], t[2], t[3] + 4, 1.6), HERBE);
        for (double[] t : tiges) {
            for (int k = 0; k < 4; k++) {
                double f = 0.08 + k * 0.13, x = t[2] + (t[0] - t[2]) * f, y = t[3] + 1 + (t[1] - t[3]) * f;
                g.relief(rot(ell(x - 2, y, 1.8, 2.8), -30, x - 2, y), BLE);
                g.relief(rot(ell(x + 2, y, 1.8, 2.8), 30, x + 2, y), BLE);
            }
            g.relief(ell(t[2], t[3], 1.6, 2.6), BLE);
        }
    }

    static void crane(Ic g) {
        g.relief(rr(10, 19, 12, 9, 4), OS);
        g.relief(circ(16, 13.5, 10.5), OS);
        g.relief(ell(11.8, 14.5, 3.2, 3.6), CONTOUR);
        g.relief(ell(20.2, 14.5, 3.2, 3.6), CONTOUR);
        g.plat(circ(12.3, 14.8, 1.3), ROUGE);
        g.plat(circ(20.7, 14.8, 1.3), ROUGE);
        g.plat(poly(16, 18, 17.6, 21, 14.4, 21), CONTOUR);
        for (int i = 0; i < 3; i++) g.plat(rect(12.5 + i * 3, 24, 1, 4), mix(OS, CONTOUR, 0.4));
    }

    static void mine(Ic g) {
        g.relief(rr(3, 13, 18, 16, 2), PIERRE);
        int[][] p = {{6, 16}, {12, 19}, {7, 24}, {15, 25}, {16, 15}};
        for (int[] q : p) { g.relief(rect(q[0], q[1], 2.6, 2.6), DIAMANT); }
        pioche(g, BLEU);
    }

    /** Empreinte de patte (catégorie Pets). */
    static void patte(Ic g) {
        g.relief(plus(ell(16, 21.5, 7.5, 6.2), ell(11, 24.5, 4, 3.4), ell(21, 24.5, 4, 3.4)), ROSE);
        g.relief(ell(6.2, 13.5, 3.2, 4), ROSE);
        g.relief(ell(12, 7.8, 3.3, 4.3), ROSE);
        g.relief(ell(20, 7.8, 3.3, 4.3), ROSE);
        g.relief(ell(25.8, 13.5, 3.2, 4), ROSE);
        g.etincelle(27, 4, OR);
    }

    static void station(Ic g) {
        g.relief(rect(1.5, 12.5, 8, 7), BLEU);
        g.relief(rect(22.5, 12.5, 8, 7), BLEU);
        g.plat(rect(5.2, 12.5, 0.9, 7), NUIT);
        g.plat(rect(26.2, 12.5, 0.9, 7), NUIT);
        g.relief(rect(9, 15, 14, 2.2), ACIER_F);
        g.relief(trait(16, 3, 16, 8, 1.4), ACIER);
        g.relief(circ(16, 3, 1.6), OR);
        g.relief(moins(trace(ell(16, 16, 10, 4), 2.4), rect(5, 9, 22, 7.5)), OR);
        g.relief(circ(16, 16, 6.8), ACIER);
        g.relief(circ(16, 16, 3), BLEU);
        g.relief(moins(trace(ell(16, 16, 10, 4), 2.4), rect(0, 0, 32, 16.5)), OR);
    }

    static void planete(Ic g) {
        Shape anneau = rot(trace(ell(16, 16, 14, 4.2), 2.2), -20, 16, 16);
        g.relief(inter(anneau, rect(0, 0, 32, 15)), OR);
        g.relief(circ(16, 16, 9), VIOLET);
        g.plat(inter(circ(16, 16, 9), rect(0, 12, 32, 1.6)), mix(VIOLET, 0xFFFFFF, 0.35));
        g.plat(inter(circ(16, 16, 9), rect(0, 19, 32, 1.6)), mix(VIOLET, CONTOUR, 0.3));
        g.relief(inter(anneau, rect(0, 15, 32, 32)), OR);
        g.etincelle(4, 5, BLEU);
        g.etincelle(27, 26, OR);
        g.point(26, 6, 0xFFFFFF);
    }

    static void cristaux(Ic g) {
        g.relief(ell(16, 27, 12.5, 3.5), PIERRE);
        g.relief(rot(poly(9, 10, 12, 13.5, 12, 26, 9, 28, 6, 26, 6, 13.5), -18, 9, 27), VERT);
        g.relief(rot(poly(23, 10, 26, 13.5, 26, 26, 23, 28, 20, 26, 20, 13.5), 18, 23, 27), BLEU);
        g.relief(poly(16, 2.5, 20, 8, 20, 25, 16, 28.5, 12, 25, 12, 8), VIOLET);
        g.plat(poly(16, 2.5, 16, 28.5, 12, 25, 12, 8), mix(VIOLET, 0xFFFFFF, 0.3));
        g.etincelle(25, 5, BLANC);
    }

    static void enclume(Ic g) {
        g.relief(poly(2.5, 11, 10, 10, 10, 15.5, 6.5, 14.5), ACIER_F);
        g.relief(rr(9, 9, 20, 6, 2), ACIER_F);
        g.relief(rect(13.5, 15, 10, 6), mix(ACIER_F, CONTOUR, 0.2));
        g.relief(rr(9, 21, 19, 4, 1.5), ACIER_F);
        g.relief(rr(7, 25, 23, 4, 1.5), ACIER_F);
        g.plat(rect(10, 9.6, 18, 1), ACIER);
        g.relief(rot(rr(16, 1.5, 10, 4.5, 1.5), -20, 21, 4), OR);
        g.etincelle(8, 5, ORANGE);
        g.etincelle(29, 4, OR);
    }

    static void trophee(Ic g) {
        g.relief(trace(circ(8.5, 10.5, 3.4), 2), OR);
        g.relief(trace(circ(23.5, 10.5, 3.4), 2), OR);
        g.relief(plus(rect(8.5, 4, 15, 8), ell(16, 12, 7.5, 6.5)), OR);
        g.relief(rect(14.4, 17.5, 3.2, 4.5), mix(OR, BOIS, 0.2));
        g.relief(rr(10.5, 21.5, 11, 3, 1), OR);
        g.relief(rr(8, 24, 16, 5.5, 1.5), NUIT);
        g.plat(rect(12, 25.8, 8, 1.6), OR);
        g.relief(etoile(16, 10.5, 3.8, 1.6, 5), 0xFFF6D6);
        g.etincelle(27, 3, BLEU);
    }

    static void outils(Ic g) { pioche(g, ACIER); epee(g); }

    // ------------------------------------------------------------------ insignes des grades

    /** Écusson arrondi en haut, pointe en bas (centré, largeur 2·l, du haut h0 à la pointe h1). */
    static Shape ecusson(double l, double h0, double h1) {
        return plus(rr(16 - l, h0, 2 * l, (h1 - h0) * 0.55, 5), poly(16 - l, h0 + (h1 - h0) * 0.4, 16 + l, h0 + (h1 - h0) * 0.4, 16, h1));
    }

    static void aile(Ic g, boolean gauche, int c) {
        double s = gauche ? -1 : 1;
        for (int k = 0; k < 3; k++) {
            double y = 12 + k * 3.2, l = 12 - k * 2.6;
            g.relief(poly(16 + s * 4, y, 16 + s * (4 + l), y - 2.2 + k * 0.4, 16 + s * (4 + l - 1.5), y + 1.6, 16 + s * 4, y + 2.4), c);
        }
    }

    /** Pilote : ailes d'argent autour d'un petit écusson bleu glacé. */
    static void gradePilote(Ic g) {
        aile(g, true, ACIER);
        aile(g, false, ACIER);
        g.relief(ecusson(5.5, 8, 25), NUIT);
        g.plat(trace(ecusson(4.6, 8.9, 23.8), 0.9), BLEU);
        g.relief(etoile(16, 14.5, 4, 1.7, 5), BLANC);
        g.etincelle(26, 4, BLEU);
    }

    /** Commandant : écusson bleu, liseré or, deux chevrons dorés et une étoile. */
    static void gradeCommandant(Ic g) {
        g.relief(ecusson(11, 3, 30), mix(BLEU, NUIT, 0.45));
        g.plat(trace(ecusson(9.6, 4.4, 28.2), 1.1), OR);
        g.relief(etoile(16, 10.5, 4.2, 1.8, 5), BLANC);
        for (int k = 0; k < 2; k++) {
            double y = 16 + k * 5;
            g.relief(trace(ligne(9.5, y, 16, y + 4, 22.5, y), 2.4), OR);
        }
        g.etincelle(28, 4, BLEU);
    }

    /** Amiral : écusson or massif, bordure bleu nuit, trois étoiles en arc et une ancre stellaire. */
    static void gradeAmiral(Ic g) {
        g.relief(ecusson(12, 2.5, 30.5), OR);
        g.relief(ecusson(9.4, 5, 27.6), NUIT);
        for (int k = -1; k <= 1; k++) g.relief(etoile(16 + k * 5.2, 9.6 + Math.abs(k) * 1.3, 2.7, 1.15, 5), OR);
        g.relief(trait(16, 13.5, 16, 24, 1.8), BLEU);
        g.relief(trait(12.5, 16, 19.5, 16, 1.6), BLEU);
        g.relief(trace(moins(circ(16, 20.5, 4.6), rect(10, 14, 12, 6.5)), 1.6), BLEU);
        g.etincelle(4, 4, OR);
        g.etincelle(28, 26, BLEU);
    }

    // ------------------------------------------------------------------ titres (images spatiales)

    static void titreFusee(Ic g) {
        g.relief(poly(9, 22, 5, 28, 12, 26), ROUGE);
        g.relief(poly(23, 22, 27, 28, 20, 26), ROUGE);
        g.relief(plus(rr(11, 9, 10, 18, 4), poly(11, 11, 16, 2, 21, 11)), BLANC);
        g.relief(circ(16, 14, 3), NUIT);
        g.plat(circ(15.2, 13.2, 1.1), BLEU);
        g.plat(rect(11, 20, 10, 1.4), OR);
        g.relief(poly(12.5, 27, 19.5, 27, 16, 31.5), ORANGE);
        g.plat(poly(14.3, 27, 17.7, 27, 16, 30), BLE);
        g.etincelle(5, 6, BLEU);
        g.etincelle(27, 9, OR);
    }

    static void titreSatellite(Ic g) {
        g.relief(rot(rect(2, 12, 9, 8), 0, 16, 16), BLEU);
        g.relief(rect(21, 12, 9, 8), BLEU);
        g.plat(rect(6.2, 12, 0.9, 8), NUIT);
        g.plat(rect(25.2, 12, 0.9, 8), NUIT);
        g.relief(trait(11, 16, 21, 16, 1.6), ACIER);
        g.relief(rr(12, 11, 8, 10, 2), OR);
        g.relief(trace(moins(circ(16, 8, 5), rect(10, 8, 12, 8)), 1.4), ACIER);
        g.relief(trait(16, 8, 16, 11, 1.2), ACIER);
        g.point(16, 4, BLEU);
        g.etincelle(5, 26, BLEU);
        g.etincelle(27, 5, OR);
    }

    static void titreComete(Ic g) {
        g.relief(poly(4, 28, 13, 15, 19, 21), mix(BLEU, NUIT, 0.35));
        g.relief(poly(6, 29, 14, 18, 17, 21), BLEU);
        g.relief(circ(19, 13, 7), mix(BLEU, BLANC, 0.5));
        g.plat(circ(17, 11, 2.4), BLANC);
        g.etincelle(27, 4, BLANC);
        g.etincelle(5, 7, VIOLET);
        g.point(26, 26, BLEU);
    }

    static void titreGalaxie(Ic g) {
        g.relief(ell(16, 16, 14, 7), mix(VIOLET, NUIT, 0.55));
        g.plat(trace(ell(16, 16, 10.5, 4.8), 1.3), mix(VIOLET, ROSE, 0.4));
        g.plat(trace(ell(16, 16, 6.5, 2.8), 1.2), VIOLET);
        g.relief(circ(16, 16, 3), mix(BLANC, VIOLET, 0.2));
        g.point(8, 13, BLANC); g.point(24, 19, BLANC); g.point(11, 20, BLEU); g.point(22, 12, ROSE);
        g.etincelle(5, 4, BLANC);
        g.etincelle(27, 27, BLEU);
    }

    static void titreEtoile(Ic g) {
        g.plat(circ(16, 16, 13), mix(OR, NUIT, 0.75));
        g.relief(etoile(16, 16, 13, 5, 4), OR);
        g.relief(etoile(16, 16, 8, 3.5, 4), mix(OR, BLANC, 0.6));
        g.relief(circ(16, 16, 2.4), BLANC);
        g.etincelle(5, 5, BLEU);
        g.etincelle(27, 26, OR);
    }

    static void titreTrouNoir(Ic g) {
        g.relief(ell(16, 17, 14.5, 5.5), ORANGE);
        g.plat(trace(ell(16, 17, 12, 4.2), 1.1), BLE);
        g.relief(circ(16, 16, 7.5), 0x05070F);
        g.plat(trace(circ(16, 16, 7.5), 0.9), mix(VIOLET, ORANGE, 0.4));
        g.relief(inter(ell(16, 17, 14.5, 5.5), rect(0, 17, 32, 15)), ORANGE);
        g.plat(inter(trace(ell(16, 17, 12, 4.2), 1.1), rect(0, 17, 32, 15)), BLE);
        g.etincelle(4, 5, VIOLET);
        g.etincelle(28, 6, BLANC);
    }

    /** Baguette de vente : manche bleu nuit cerclé d'or, gemme de la couleur du palier, étincelles. */
    static void baguette(Ic g, int gemme) {
        g.relief(trait(7, 27, 19.5, 12.5, 3.2), NUIT);
        for (int k = 0; k < 3; k++) {
            double t = 0.2 + k * 0.28;
            double x = 7 + (19.5 - 7) * t, y = 27 + (12.5 - 27) * t;
            g.relief(trait(x - 1.4, y - 1.2, x + 1.4, y + 1.2, 1.4), OR);
        }
        g.relief(etoile(22, 10, 7.5, 3.2, 4), OR);
        g.relief(circ(22, 10, 3.6), gemme);
        g.plat(circ(21, 9, 1.2), mix(gemme, BLANC, 0.7));
        g.etincelle(28, 3, gemme);
        g.etincelle(13, 6, BLANC);
        g.etincelle(28, 20, gemme);
    }

    /** Astral : couronne d'or sur un astre violet cerclé d'un anneau bleu glacé, éclats d'étoiles. */
    static void gradeAstral(Ic g) {
        g.relief(trace(ell(16, 19, 14.5, 5), 1.6), BLEU);
        g.relief(circ(16, 19, 8.5), mix(VIOLET, NUIT, 0.25));
        g.plat(ell(13.3, 16.2, 3, 2), mix(VIOLET, BLANC, 0.45));
        g.relief(inter(trace(ell(16, 19, 14.5, 5), 1.6), rect(0, 19, 32, 13)), BLEU);
        g.relief(poly(8.5, 9.5, 11.5, 3.5, 14, 8, 16, 2, 18, 8, 20.5, 3.5, 23.5, 9.5, 22, 11.5, 10, 11.5), OR);
        g.plat(rect(10.5, 9.6, 11, 1.2), mix(OR, BOIS, 0.35));
        g.point(16, 5, BLANC);
        g.etincelle(4, 6, BLANC);
        g.etincelle(28, 5, VIOLET);
        g.etincelle(27, 28, BLEU);
    }

    // ------------------------------------------------------------------ médaillons des catégories

    static Consumer<Ic> medaillon(int accent, Consumer<Ic> symbole) {
        return g -> {
            g.relief(circ(16, 16, 15), OR);
            g.relief(circ(16, 16, 12.6), mix(accent, 0x0B1A33, 0.68));
            g.plat(trace(circ(16, 16, 11.9), 0.8), mix(accent, 0x0B1A33, 0.3));
            for (int i = 0; i < 4; i++) {
                double a = Math.toRadians(45 + i * 90);
                g.point((int) Math.floor(16 + Math.cos(a) * 13.8), (int) Math.floor(16 + Math.sin(a) * 13.8), 0xFFF6D6);
            }
            g.contourGlobal();
            g.t = new AffineTransform();
            g.t.translate(16, 16.3);
            g.t.scale(0.66, 0.66);
            g.t.translate(-16, -16);
            g.debut();
            symbole.accept(g);
            g.fin();
            g.t = new AffineTransform();
        };
    }

    // ------------------------------------------------------------------ catalogue

    static Map<String, Consumer<Ic>> catalogue() {
        Map<String, Consumer<Ic>> m = new LinkedHashMap<>();
        // Catégories
        m.put("cat_outils", medaillon(BLEU, NavIcones::outils));
        m.put("cat_boutique", medaillon(OR, NavIcones::sac));
        m.put("cat_quetes", medaillon(VERT, NavIcones::parchemin));
        m.put("cat_tuto", medaillon(VIOLET, NavIcones::ampoule));
        m.put("cat_ile", medaillon(VERT, g -> ile(g, false)));
        m.put("cat_vote", medaillon(OR, NavIcones::urne));
        m.put("cat_coffres", medaillon(VIOLET, g -> coffre(g, false)));
        m.put("cat_shop", medaillon(VERT, NavIcones::marche));
        m.put("cat_hdv", medaillon(OR, NavIcones::marteau));
        m.put("cat_zones", medaillon(BLEU, NavIcones::station));
        m.put("cat_pets", medaillon(ROSE, NavIcones::patte));
        // Communs
        m.put("info", NavIcones::info);
        m.put("retour", NavIcones::retour);
        m.put("fermer", NavIcones::fermer);
        // Outils
        m.put("outil_interface", NavIcones::outils);
        m.put("outil_ressources", NavIcones::cristaux);
        m.put("outil_atelier", NavIcones::enclume);
        m.put("outil_classement", NavIcones::trophee);
        // Boutique
        m.put("boutique_ouvrir", NavIcones::sac);
        m.put("boutique_pnj", NavIcones::casque);
        m.put("boutique_grades", g -> {
            g.relief(etoile(16, 16, 13, 5.5, 5), OR);
            g.relief(etoile(16, 16, 6, 2.6, 5), VIOLET);
            g.etincelle(26, 5, BLEU);
        });
        m.put("boutique_cles", g -> { cle(g); g.etincelle(26, 6, VIOLET); });
        m.put("boutique_items", g -> { coffre(g, false); g.etincelle(26, 5, VIOLET); });
        // Insignes des 4 grades (écussons, du plus simple au plus prestigieux).
        m.put("grade_pilote", NavIcones::gradePilote);
        m.put("grade_commandant", NavIcones::gradeCommandant);
        m.put("grade_amiral", NavIcones::gradeAmiral);
        m.put("grade_astral", NavIcones::gradeAstral);
        // Titres de la boutique : une image spatiale par thème.
        m.put("titre_fusee", NavIcones::titreFusee);
        m.put("titre_satellite", NavIcones::titreSatellite);
        m.put("titre_comete", NavIcones::titreComete);
        m.put("titre_galaxie", NavIcones::titreGalaxie);
        m.put("titre_etoile", NavIcones::titreEtoile);
        m.put("titre_trou_noir", NavIcones::titreTrouNoir);
        // Baguettes de vente (sell wands) : la gemme change de couleur avec le multiplicateur.
        m.put("baguette_1", g -> baguette(g, BLEU));
        m.put("baguette_2", g -> baguette(g, VIOLET));
        m.put("baguette_3", g -> baguette(g, OR));
        // Quêtes
        m.put("quetes_mes", NavIcones::parchemin);
        m.put("quetes_pavillon", NavIcones::boussole);
        // Tuto
        m.put("tuto_consigne", NavIcones::ampoule);
        m.put("tuto_recommencer", NavIcones::recommencer);
        m.put("tuto_passer", NavIcones::passer);
        m.put("tuto_lire", NavIcones::livreOuvert);
        m.put("tuto_livre", NavIcones::livre);
        // Île
        m.put("ile_creer", g -> { ile(g, false); plusBadge(g, 25, 25, VERT); });
        m.put("ile_mon", g -> ile(g, true));
        m.put("ile_niveau", NavIcones::niveau);
        m.put("ile_agrandir", NavIcones::agrandir);
        m.put("ile_inviter", g -> { personne(g, BLEU); plusBadge(g, 24.5, 24, VERT); });
        m.put("ile_renommer", g -> etiquette(g, OR, false));
        m.put("ile_rejoindre", NavIcones::porte);
        m.put("ile_visiter", NavIcones::longueVue);
        // Vote
        m.put("vote_voter", NavIcones::urne);
        m.put("vote_party", NavIcones::feuArtifice);
        // Coffres
        m.put("coffres_aller", g -> coffre(g, true));
        m.put("coffres_liste", NavIcones::cle);
        // Shop
        m.put("shop_generateurs", NavIcones::generateur);
        m.put("shop_blocs", NavIcones::blocs);
        m.put("shop_vendre", NavIcones::vendre);
        m.put("shop_marche", NavIcones::marche);
        // HDV
        m.put("hdv_ouvrir", NavIcones::marteau);
        m.put("hdv_ventes", NavIcones::ventes);
        m.put("hdv_vendre", g -> etiquette(g, VERT, true));
        // Zones
        m.put("zone_peche", NavIcones::poisson);
        m.put("zone_bucheron", NavIcones::bucheron);
        m.put("zone_agriculture", NavIcones::ble);
        m.put("zone_mobzone", NavIcones::crane);
        m.put("zone_mine", NavIcones::mine);
        m.put("zone_base", NavIcones::station);
        m.put("zone_observatoire", NavIcones::planete);
        // Équipements de farm (un set par outil)
        m.put("cat_equipements", medaillon(BLEU, NavIcones::casque));
        m.put("equip_cratere", NavIcones::mine);
        m.put("equip_sylvanova", NavIcones::bucheron);
        m.put("equip_moisson", NavIcones::ble);
        m.put("equip_maree", NavIcones::poisson);
        m.put("equip_supernova", NavIcones::crane);
        return m;
    }

    // ------------------------------------------------------------------ sorties

    public static void main(String[] args) throws Exception {
        File pack = new File(args[0]);
        File tex = new File(pack, "assets/orbittycoon/textures/item"), mod = new File(pack, "assets/orbittycoon/models/item"),
                items = new File(pack, "assets/orbittycoon/items");
        tex.mkdirs(); mod.mkdirs(); items.mkdirs();
        Map<String, Consumer<Ic>> cat = catalogue();
        Map<String, BufferedImage> images = new LinkedHashMap<>();
        for (var e : cat.entrySet()) {
            Ic g = new Ic();
            e.getValue().accept(g);
            g.contourGlobal();
            BufferedImage img = g.image();
            images.put(e.getKey(), img);
            String id = "nav_" + e.getKey();
            ImageIO.write(img, "png", new File(tex, id + ".png"));
            Files.writeString(new File(mod, id + ".json").toPath(),
                    "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\"layer0\": \"orbittycoon:item/" + id + "\"}\n}\n",
                    StandardCharsets.UTF_8);
            Files.writeString(new File(items, id + ".json").toPath(),
                    "{\n  \"model\": {\"type\": \"minecraft:model\", \"model\": \"orbittycoon:item/" + id + "\"}\n}\n",
                    StandardCharsets.UTF_8);
        }
        System.out.println(images.size() + " icônes écrites dans " + tex);
        // Objet invisible (cases cliquables posées sous les grandes cartes du menu).
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "png", new File(tex, "nav_vide.png"));
        Files.writeString(new File(mod, "nav_vide.json").toPath(),
                "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\"layer0\": \"orbittycoon:item/nav_vide\"}\n}\n", StandardCharsets.UTF_8);
        Files.writeString(new File(items, "nav_vide.json").toPath(),
                "{\n  \"model\": {\"type\": \"minecraft:model\", \"model\": \"orbittycoon:item/nav_vide\"}\n}\n", StandardCharsets.UTF_8);
        cartes(pack, new java.util.ArrayList<>(images.keySet()));
        if (args.length > 1) apercu(images, new File(args[1]));
    }

    // ------------------------------------------------------------------ menu à grandes cartes (polices du titre)

    /**
     * Géométrie, en pixels de la fenêtre du coffre de 6 rangées (le titre commence en x = 8, ligne de base y = 13 :
     * un glyphe d'ascent a a son haut en y = 13 - a). Trois bandes de cartes, une tous les PAS pixels à partir de Y0.
     * Doit rester identique aux constantes de Navigateur.java.
     */
    static final int Y0 = 5, PAS = 40, CADRE_L = 38, CADRE_H = 40, ICONE_Y = 8, MARGE = 10;

    static void cartes(File pack, java.util.List<String> ids) throws Exception {
        File police = new File(pack, "assets/orbittycoon/font"), fontTex = new File(pack, "assets/orbittycoon/textures/font");
        police.mkdirs();
        // Fond : la grille de 6 rangées du pack, cases du coffre et bandeau du titre effacés.
        BufferedImage g6 = ImageIO.read(new File(fontTex, "menu_grille6.png"));
        BufferedImage fond = new BufferedImage(g6.getWidth(), g6.getHeight(), BufferedImage.TYPE_INT_ARGB);
        fond.getGraphics().drawImage(g6, 0, 0, null);
        int y0 = -4, y1 = 114 + 18 * 6 + 3;
        for (int y = 0; y <= 126; y++) for (int x = 0; x <= 175; x++)
            fond.setRGB(x + MARGE, y + MARGE, 0xFF000000 | mix(0x17284A, 0x0A1424, (y - y0) / (double) (y1 - y0)));
        long s = 20261124L;
        for (int i = 0; i < 45; i++) {
            s = (s * 1103515245L + 12345L) % 2147483648L; int sx = 2 + (int) (s % 172);
            s = (s * 1103515245L + 12345L) % 2147483648L; int sy = 2 + (int) (s % 122);
            fond.setRGB(sx + MARGE, sy + MARGE, i % 5 == 0 ? 0xFFFFFFFF : (i % 2 == 0 ? 0xFF62CFF4 : 0xFF6F86A8));
        }
        ImageIO.write(fond, "png", new File(fontTex, "nav_fond.png"));
        // Cadre d'une carte : plaque du nom (rangées 0 à 9) + puits de l'icône.
        BufferedImage c = new BufferedImage(CADRE_L, CADRE_H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < CADRE_H; y++) for (int x = 0; x < CADRE_L; x++) {
            int col = 0;
            boolean coin = (x == 0 || x == CADRE_L - 1) && (y == 0 || y == 9);
            if (y <= 9) {
                if (coin) continue;
                if (x == 0 || x == CADRE_L - 1 || y == 0 || y == 9) col = y == 0 ? 0xF2D48A : OR;
                else if (y == 1) col = 0x2E5C9E;
                else col = mix(0x1E3F75, 0x12284D, (y - 2) / 6.0);
            } else {
                if (x == 0 || x == CADRE_L - 1) continue;
                if (x == 1 || x == CADRE_L - 2 || y == CADRE_H - 1) col = CONTOUR;
                else if (x == 2 || y == 10) col = 0x23406E;
                else if (x == CADRE_L - 3 || y == CADRE_H - 2) col = 0x08101F;
                else {
                    double d = Math.hypot((x - CADRE_L / 2.0) / 17.0, (y - 25) / 15.0);
                    col = mix(0x18336A, 0x0A1530, Math.min(1, d));
                }
                if ((x == 3 || x == CADRE_L - 4) && (y == 11 || y == CADRE_H - 3)) col = OR;
            }
            c.setRGB(x, y, 0xFF000000 | col);
        }
        ImageIO.write(c, "png", new File(fontTex, "nav_cadre.png"));

        // Police orbittycoon:nav : espaces (-1..-128 en U+F810.., +1..+128 en U+F820..), fond U+E000,
        // cadres U+E010 + bande, icônes U+E100 + 256 * bande + index.
        StringBuilder j = new StringBuilder("{\n  \"providers\": [\n");
        StringBuilder adv = new StringBuilder();
        for (int k = 0; k < 8; k++) {
            if (k > 0) adv.append(", ");
            adv.append("\"").append(u(0xF810 + k)).append("\": ").append(-(1 << k))
               .append(", \"").append(u(0xF820 + k)).append("\": ").append(1 << k);
        }
        j.append("    {\"type\": \"space\", \"advances\": {").append(adv).append("}},\n");
        j.append(bitmap("orbittycoon:font/nav_fond.png", fond.getHeight(), 13 + MARGE, 0xE000)).append(",\n");
        for (int b = 0; b < 3; b++) j.append(bitmap("orbittycoon:font/nav_cadre.png", CADRE_H, 13 - (Y0 + PAS * b), 0xE010 + b)).append(",\n");
        for (int b = 0; b < 3; b++) for (int k = 0; k < ids.size(); k++) {
            boolean dernier = b == 2 && k == ids.size() - 1;
            j.append(bitmap("orbittycoon:item/nav_" + ids.get(k) + ".png", 32, 13 - (Y0 + PAS * b + ICONE_Y), 0xE100 + 256 * b + k))
             .append(dernier ? "\n" : ",\n");
        }
        j.append("  ]\n}\n");
        Files.writeString(new File(police, "nav.json").toPath(), j.toString(), StandardCharsets.UTF_8);

        // Polices des noms : la police du jeu (ascii + accents) descendue au niveau de la plaque de chaque bande.
        String mc = Files.readString(new File(pack, "../tools/police-mc.txt").toPath(), StandardCharsets.UTF_8);
        for (int b = 0; b < 3; b++) {
            int a = 13 - (Y0 + 1 + PAS * b);
            String prov = mc.replace("__A7__", String.valueOf(a)).replace("__A10__", String.valueOf(a + 3)).trim().replace("\n", ",\n    ");
            Files.writeString(new File(police, "nav_t" + b + ".json").toPath(),
                    "{\n  \"providers\": [\n    {\"type\": \"reference\", \"id\": \"minecraft:include/space\"},\n    " + prov + "\n  ]\n}\n",
                    StandardCharsets.UTF_8);
        }
        // Ordre des icônes, lu par le plugin (index des glyphes).
        Files.writeString(new File(pack, "../src/main/resources/nav-icones.txt").toPath(), String.join("\n", ids) + "\n", StandardCharsets.UTF_8);
        System.out.println("Menu à cartes : fond, cadre et polices écrits.");
    }

    static String u(int cp) { return String.format("\\u%04X", cp); }

    static String bitmap(String file, int h, int ascent, int cp) {
        return "    {\"type\": \"bitmap\", \"file\": \"" + file + "\", \"height\": " + h + ", \"ascent\": " + ascent + ", \"chars\": [\"" + u(cp) + "\"]}";
    }

    /** Planche d'aperçu : chaque icône agrandie x6 sur une case de menu, avec son id. */
    static void apercu(Map<String, BufferedImage> images, File sortie) throws Exception {
        int z = 6, cols = 8, cw = 32 * z + 40, ch = 32 * z + 64;
        int rows = (images.size() + cols - 1) / cols;
        BufferedImage b = new BufferedImage(cols * cw, rows * ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = b.createGraphics();
        g.setColor(new Color(0x0B1A33));
        g.fillRect(0, 0, b.getWidth(), b.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        int k = 0;
        for (var e : images.entrySet()) {
            int x = (k % cols) * cw + 20, y = (k / cols) * ch + 14;
            g.setColor(new Color(0x14284A));
            g.fillRect(x - 6, y - 6, 32 * z + 12, 32 * z + 12);
            g.drawImage(e.getValue(), x, y, 32 * z, 32 * z, null);
            g.setColor(new Color(0xE0B864));
            g.drawString(e.getKey(), x, y + 32 * z + 30);
            k++;
        }
        g.dispose();
        sortie.getParentFile().mkdirs();
        ImageIO.write(b, "png", sortie);
        System.out.println("Aperçu : " + sortie);
    }
}
