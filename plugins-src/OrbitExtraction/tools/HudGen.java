import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * HUD d'Orbit Extraction (pack chargé) : console du bas de l'écran dessinée par la police orbitextraction:hud dans la
 * barre d'action, sur le modèle de la console d'OrbitMMO (mêmes positions, validées en jeu), aux couleurs du mode :
 * acier brossé et rouge « zone infectée ».
 * <p>
 * <b>Haute résolution</b> : tout est dessiné en x{@value #K} (lissé, dégradés, reflets). Le jeu ramène chaque glyphe à
 * la hauteur déclarée dans hud.json (en unités d'interface) : l'encombrement à l'écran ne change pas, le détail si.
 * Un glyphe ne peut pas dépasser 256 px : les grandes pièces (ailes, pont, cadre de progression) sont découpées en
 * tuiles de 64 unités au plus, posées bout à bout par Console.java (mêmes largeurs : {@link #WING_TILES}…).
 * Les textures de la barre d'objets (sprites d'interface) acceptent aussi une résolution supérieure.
 * <p>
 * Points de code et hauteurs : doivent rester ceux de src/.../hud/Console.java. La ligne de texte de la barre
 * d'action commence 72 unités au-dessus du bas de l'écran ; un glyphe dont le haut est à r unités du bas a un ascent
 * de r - 65.
 * <p>
 * Usage : java HudGen.java &lt;dossier_pack&gt; [&lt;dossier_apercus&gt;]
 */
public class HudGen {

    /** Pixels de texture par unité d'interface. */
    static final int K = 4;
    static final int[] WING_TILES = {59, 58}, BRIDGE_TILES = {62, 62, 62}, CAST_TILES = {62, 62};

    static File pack;
    static final String FONT_DIR = "assets/orbitextraction/textures/font/";
    static final String HUD_DIR = "assets/minecraft/textures/gui/sprites/hud/";

    // ------------------------------------------------------------------ Palette
    static final int OUT = 0xFF050608;
    static final int STEEL_T = 0xFF5A5F69, STEEL_M = 0xFF3A3E46, STEEL_B = 0xFF1C1E23;
    static final int RED = 0xFFC9372A, RED_L = 0xFFFF8A78, RED_D = 0xFF6A1710;
    static final int SCREEN_T = 0xFF090B0E, SCREEN_B = 0xFF171A1F;
    static final int HEAD_T = 0xFF0E1013, HEAD_B = 0xFF15181C;

    static Color c(int argb) {
        return new Color(argb, true);
    }

    static Color a(int rgb, double alpha) {
        return new Color((rgb & 0xFFFFFF) | ((int) Math.round(alpha * 255) << 24), true);
    }

    static int mix(int x, int y, double t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (int) Math.round(((x >>> 24) & 255) * (1 - t) + ((y >>> 24) & 255) * t);
        int r = (int) Math.round(((x >> 16) & 255) * (1 - t) + ((y >> 16) & 255) * t);
        int g = (int) Math.round(((x >> 8) & 255) * (1 - t) + ((y >> 8) & 255) * t);
        int b = (int) Math.round((x & 255) * (1 - t) + (y & 255) * t);
        return (aa << 24) | (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ Toile en unités d'interface
    static BufferedImage hi(double wu, double hu) {
        return new BufferedImage((int) Math.round(wu * K), (int) Math.round(hu * K), BufferedImage.TYPE_INT_ARGB);
    }

    static Graphics2D gfx(BufferedImage b) {
        Graphics2D g = b.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(K, K);
        return g;
    }

    static RoundRectangle2D rr(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2);
    }

    static GradientPaint vgrad(double y0, int c0, double y1, int c1) {
        return new GradientPaint(0, (float) y0, c(c0), 0, (float) y1, c(c1));
    }

    static void clear(BufferedImage b, double x, double y, double w, double h) {
        Graphics2D g = b.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect((int) Math.round(x * K), (int) Math.round(y * K), (int) Math.round(w * K), (int) Math.round(h * K));
        g.dispose();
    }

    /** Pixel quasi invisible dans la dernière colonne : la largeur du glyphe (donc son avance) reste exacte. */
    static void forceWidth(BufferedImage b) {
        int x = b.getWidth() - 1;
        for (int y = 0; y < b.getHeight(); y++) if ((b.getRGB(x, y) >>> 24) > 0) return;
        b.setRGB(x, 0, 0x01000000);
    }

    static void save(BufferedImage b, String rel) throws Exception {
        File f = new File(pack, rel);
        f.getParentFile().mkdirs();
        ImageIO.write(b, "png", f);
    }

    /** Découpe horizontale en tuiles (largeurs en unités) : name_0.png, name_1.png… */
    static void saveTiles(BufferedImage b, String name, int[] widths) throws Exception {
        int x = 0;
        for (int i = 0; i < widths.length; i++) {
            BufferedImage t = new BufferedImage(widths[i] * K, b.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = t.createGraphics();
            g.drawImage(b, -x * K, 0, null);
            g.dispose();
            forceWidth(t);
            save(t, FONT_DIR + name + "_" + i + ".png");
            x += widths[i];
        }
        if (x * K != b.getWidth()) throw new IllegalStateException(name + " : tuiles de " + x + " unités pour " + b.getWidth() / K);
    }

    static void saveGlyph(BufferedImage b, String name) throws Exception {
        forceWidth(b);
        save(b, FONT_DIR + name + ".png");
    }

    // ------------------------------------------------------------------ Matériaux
    /** Plaque d'acier brossé : contour, coins arrondis, dégradé, reflet en haut, ombre en bas, stries fines. */
    static void plate(Graphics2D g, double x, double y, double w, double h, double r, long seed) {
        g.setColor(c(OUT));
        g.fill(rr(x, y, w, h, r));
        Shape body = rr(x + 0.5, y + 0.5, w - 1, h - 1, r - 0.4);
        g.setPaint(new LinearGradientPaint(0, (float) y, 0, (float) (y + h), new float[]{0f, 0.45f, 1f},
                new Color[]{c(STEEL_T), c(STEEL_M), c(STEEL_B)}));
        g.fill(body);
        Shape old = g.getClip();
        g.clip(body);
        Random rnd = new Random(seed);
        for (double yy = y + 0.5; yy < y + h; yy += 0.25) {
            double v = rnd.nextDouble();
            if (v < 0.35) {
                g.setColor(a(v < 0.17 ? 0xFFFFFF : 0x000000, 0.035 + rnd.nextDouble() * 0.04));
                double len = w * (0.3 + rnd.nextDouble() * 0.7);
                g.fill(new Rectangle2D.Double(x + rnd.nextDouble() * (w - len), yy, len, 0.25));
            }
        }
        // reflet du haut, ombre du bas
        g.setPaint(new GradientPaint(0, (float) y, a(0xFFFFFF, 0.22), 0, (float) (y + 3), a(0xFFFFFF, 0)));
        g.fill(new Rectangle2D.Double(x, y, w, 3));
        g.setPaint(new GradientPaint(0, (float) (y + h - 2.5), a(0x000000, 0), 0, (float) (y + h), a(0x000000, 0.45)));
        g.fill(new Rectangle2D.Double(x, y + h - 2.5, w, 2.5));
        g.setClip(old);
        g.setStroke(new BasicStroke(0.25f));
        g.setColor(a(0xFFFFFF, 0.28));
        g.draw(rr(x + 0.75, y + 0.75, w - 1.5, h - 1.5, r - 0.6));
    }

    /** Bandeau sombre (titre de l'aile). */
    static void strip(Graphics2D g, double x, double y, double w, double h) {
        g.setPaint(vgrad(y, HEAD_T, y + h, HEAD_B));
        g.fill(rr(x, y, w, h, 1));
        g.setPaint(new GradientPaint(0, (float) y, a(0x000000, 0.6), 0, (float) (y + 1.2), a(0x000000, 0)));
        g.fill(rr(x, y, w, 1.2, 0.6));
        g.setColor(a(0xFFFFFF, 0.07));
        g.fill(new Rectangle2D.Double(x + 1, y + h - 0.25, w - 2, 0.25));
    }

    /** Filet rouge lumineux, plus vif au centre, avec halo. */
    static void redLine(Graphics2D g, double x0, double x1, double y) {
        float[] f = {0f, 0.15f, 0.5f, 0.85f, 1f};
        g.setPaint(new LinearGradientPaint((float) x0, 0, (float) x1, 0, f,
                new Color[]{a(RED_D, 0.2), a(RED, 0.55), a(RED_L, 0.55), a(RED, 0.55), a(RED_D, 0.2)}));
        g.fill(new Rectangle2D.Double(x0, y - 0.5, x1 - x0, 1.5));
        g.setPaint(new LinearGradientPaint((float) x0, 0, (float) x1, 0, f,
                new Color[]{c(RED_D), c(RED), c(0xFFFFB8AC), c(RED), c(RED_D)}));
        g.fill(new Rectangle2D.Double(x0, y, x1 - x0, 0.5));
    }

    /** Vis à tête bombée (rayon en unités). */
    static void screw(Graphics2D g, double cx, double cy, double r) {
        g.setColor(c(OUT));
        g.fill(new Ellipse2D.Double(cx - r - 0.25, cy - r - 0.25, 2 * r + 0.5, 2 * r + 0.5));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx - r * 0.4, cy - r * 0.4), (float) (r * 1.6),
                new float[]{0f, 1f}, new Color[]{c(0xFFD8DCE3), c(0xFF4A4E56)}));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.setStroke(new BasicStroke(0.25f));
        g.setColor(a(0x0A0B0D, 0.85));
        g.draw(new java.awt.geom.Line2D.Double(cx - r * 0.6, cy + r * 0.6, cx + r * 0.6, cy - r * 0.6));
    }

    /** Rainure d'une barre : bord 1 unité (le remplissage couvre l'intérieur exact), ombre intérieure, reflet bas. */
    static void track(Graphics2D g, double x0, double y0, double x1, double y1) {
        double w = x1 - x0 + 1, h = y1 - y0 + 1;
        g.setColor(c(0xFF020304));
        g.fill(rr(x0, y0, w, h, 0.9));
        g.setColor(a(0xFFFFFF, 0.12));
        g.fill(rr(x0 + 0.25, y0 + h - 0.5, w - 0.5, 0.5, 0.25));
        g.setPaint(vgrad(y0 + 1, SCREEN_T, y1, SCREEN_B));
        g.fill(new Rectangle2D.Double(x0 + 1, y0 + 1, w - 2, h - 2));
        g.setPaint(new GradientPaint(0, (float) (y0 + 1), a(0x000000, 0.75), 0, (float) (y0 + 2.5), a(0x000000, 0)));
        g.fill(new Rectangle2D.Double(x0 + 1, y0 + 1, w - 2, 1.5));
        // fines graduations (un quart)
        g.setColor(a(0xFFFFFF, 0.06));
        for (int k = 1; k < 4; k++) g.fill(new Rectangle2D.Double(x0 + 1 + (w - 2) * k / 4.0 - 0.125, y0 + 1, 0.25, h - 2));
    }

    static Path2D octagon(double x, double y, double n, double cut) {
        Path2D p = new Path2D.Double();
        p.moveTo(x + cut, y);
        p.lineTo(x + n - cut, y);
        p.lineTo(x + n, y + cut);
        p.lineTo(x + n, y + n - cut);
        p.lineTo(x + n - cut, y + n);
        p.lineTo(x + cut, y + n);
        p.lineTo(x, y + n - cut);
        p.lineTo(x, y + cut);
        p.closePath();
        return p;
    }

    /** Écrou octogonal : cerclage rouge biseauté, gorge sombre, coeur en dégradé radial, reflet. */
    static void gem(Graphics2D g, double x, double y, double n, int inA, int inB) {
        g.setColor(c(OUT));
        g.fill(octagon(x, y, n, 4.6));
        g.setPaint(new GradientPaint((float) x, (float) y, c(RED_L), (float) (x + n), (float) (y + n), c(RED_D)));
        g.fill(octagon(x + 0.5, y + 0.5, n - 1, 4.4));
        g.setColor(c(0xFF0A0B0D));
        g.fill(octagon(x + 1.5, y + 1.5, n - 3, 3.9));
        double cx = x + n / 2, cy = y + n / 2;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx - 1.5, cy - 2), (float) (n * 0.6), new float[]{0f, 1f},
                new Color[]{c(inA), c(inB)}));
        g.fill(octagon(x + 2.25, y + 2.25, n - 4.5, 3.6));
        g.setPaint(new GradientPaint(0, (float) (y + 2.5), a(0xFFFFFF, 0.28), 0, (float) (y + n / 2), a(0xFFFFFF, 0)));
        g.fill(new Ellipse2D.Double(x + 3.5, y + 2.6, n - 7, n * 0.38));
    }

    /** Logement 20x20 : cerclage rouge, gorge, creux 16x16 (vidé si hole : l'objet de la main gauche s'y affiche). */
    static void socket(BufferedImage b, Graphics2D g, double x, double y, boolean hole) {
        g.setColor(c(OUT));
        g.fill(rr(x, y, 20, 20, 2.6));
        g.setPaint(new GradientPaint((float) x, (float) y, c(RED_L), (float) (x + 20), (float) (y + 20), c(RED_D)));
        g.fill(rr(x + 0.5, y + 0.5, 19, 19, 2.3));
        g.setColor(c(0xFF07080A));
        g.fill(rr(x + 1.25, y + 1.25, 17.5, 17.5, 1.6));
        g.setPaint(vgrad(y + 2, 0xFF0C0E11, y + 18, 0xFF1B1E24));
        g.fill(new Rectangle2D.Double(x + 2, y + 2, 16, 16));
        g.setPaint(new GradientPaint(0, (float) (y + 2), a(0x000000, 0.7), 0, (float) (y + 4), a(0x000000, 0)));
        g.fill(new Rectangle2D.Double(x + 2, y + 2, 16, 2));
        if (hole) clear(b, x + 2, y + 2, 16, 16);
    }

    // ------------------------------------------------------------------ Ailes, pont, cadre de progression
    static void wingL(boolean hole, String name) throws Exception {
        BufferedImage b = hi(117, 31);
        Graphics2D g = gfx(b);
        plate(g, 0, 0, 117, 31, 3, 11);
        strip(g, 2.5, 2, 112, 7);
        redLine(g, 2, 115, 9);
        gem(g, 3, 10, 19, 0xFF8A93A3, 0xFF14161A);
        track(g, 25, 10, 90, 19);
        track(g, 25, 21, 90, 26);
        screw(g, 113, 13, 1.1);
        screw(g, 113, 27, 1.1);
        screw(g, 26.5, 28.6, 0.8);
        screw(g, 89.5, 28.6, 0.8);
        socket(b, g, 91, 10, hole);
        g.dispose();
        saveTiles(b, name, WING_TILES);
    }

    static void wingR() throws Exception {
        BufferedImage b = hi(117, 31);
        Graphics2D g = gfx(b);
        plate(g, 0, 0, 117, 31, 3, 12);
        strip(g, 2.5, 2, 112, 7);
        redLine(g, 2, 115, 9);
        track(g, 26, 10, 91, 19);
        strip(g, 26, 21, 66, 7);
        redLine(g, 27, 91, 28);
        gem(g, 95, 10, 19, 0xFF6B2219, 0xFF120706);
        screw(g, 4, 13, 1.1);
        screw(g, 4, 27, 1.1);
        socket(b, g, 6, 10, false);
        g.dispose();
        saveTiles(b, "hud_wing_r", WING_TILES);
    }

    static void bridge() throws Exception {
        BufferedImage b = hi(186, 7);
        Graphics2D g = gfx(b);
        g.setColor(c(OUT));
        g.fill(rr(0, 0, 186, 7, 1.5));
        g.setPaint(vgrad(0.5, STEEL_T, 6.5, STEEL_B));
        g.fill(rr(0.5, 0.5, 185, 6, 1.2));
        redLine(g, 1, 185, 1.25);
        g.setColor(c(0xFF020304));
        g.fill(new Rectangle2D.Double(2, 2, 182, 3));
        g.setPaint(vgrad(2, SCREEN_T, 5, SCREEN_B));
        g.fill(new Rectangle2D.Double(3, 2, 180, 3));
        g.setPaint(new GradientPaint(0, 2, a(0x000000, 0.7), 0, 3, a(0x000000, 0)));
        g.fill(new Rectangle2D.Double(3, 2, 180, 1));
        g.setColor(a(0xFFFFFF, 0.1));
        for (int k = 1; k < 10; k++) g.fill(new Rectangle2D.Double(3 + 18 * k - 0.125, 2, 0.25, 3));   // un dixième du raid
        g.setColor(a(0xFFFFFF, 0.14));
        g.fill(new Rectangle2D.Double(1, 5.75, 184, 0.25));
        g.dispose();
        saveTiles(b, "hud_bridge", BRIDGE_TILES);
    }

    /** Cadre de la barre de progression (124x9) + 10 unités vides dessous : posé au-dessus de la ligne de texte. */
    static void castFrame() throws Exception {
        BufferedImage b = hi(124, 19);
        Graphics2D g = gfx(b);
        g.setColor(a(0x000000, 0.35));
        g.fill(rr(0.5, 1, 124, 9, 2.5));   // ombre portée
        g.setColor(c(OUT));
        g.fill(rr(0, 0, 124, 9, 2.5));
        g.setPaint(vgrad(0.5, STEEL_T, 8.5, STEEL_B));
        g.fill(rr(0.5, 0.5, 123, 8, 2.2));
        redLine(g, 3, 121, 0.75);
        g.setColor(c(0xFF020304));
        g.fill(new Rectangle2D.Double(1.5, 1.5, 121, 6));
        g.setPaint(vgrad(2, SCREEN_T, 7, SCREEN_B));
        g.fill(new Rectangle2D.Double(2, 2, 120, 5));
        g.setPaint(new GradientPaint(0, 2, a(0x000000, 0.7), 0, 3.2f, a(0x000000, 0)));
        g.fill(new Rectangle2D.Double(2, 2, 120, 1.2));
        g.dispose();
        saveTiles(b, "hud_cast", CAST_TILES);
    }

    /** Remplissage blanc (teinté en jeu par la couleur du texte) : dégradé vertical brillant, pad unités vides dessous. */
    static void fill(int wu, int hu, int pad, String name) throws Exception {
        BufferedImage b = hi(wu, hu + pad);
        int h = hu * K;
        for (int y = 0; y < h; y++) {
            double t = y / (double) (h - 1);
            double v;
            if (y < K / 2) v = 1.0;                                   // liseré lumineux
            else if (t < 0.42) v = 0.97 - t * 0.12;                   // reflet
            else v = 0.86 - (t - 0.42) * 0.42;                        // ombre vers le bas
            if (y >= h - K / 2) v *= 0.82;                            // arête basse
            int g = (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
            for (int x = 0; x < wu * K; x++) b.setRGB(x, y, 0xFF000000 | (g << 16) | (g << 8) | g);
        }
        save(b, FONT_DIR + name + ".png");
    }

    /** Insigne du rang (16x16) : étoile biseautée en niveaux de gris, teintée par la couleur du rang en jeu. */
    static void rankStar() throws Exception {
        BufferedImage b = hi(16, 16);
        Graphics2D g = gfx(b);
        Path2D star = new Path2D.Double();
        for (int k = 0; k < 10; k++) {
            double r = k % 2 == 0 ? 7.6 : 3.2, ang = -Math.PI / 2 + k * Math.PI / 5;
            double px = 8 + Math.cos(ang) * r, py = 8.6 + Math.sin(ang) * r;
            if (k == 0) star.moveTo(px, py); else star.lineTo(px, py);
        }
        star.closePath();
        g.setColor(a(0x000000, 0.55));
        g.fill(AffineTransform.getTranslateInstance(0.4, 0.5).createTransformedShape(star));
        g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c(0xFF1A1A1A));
        g.draw(star);
        g.setPaint(new GradientPaint(0, 1, c(0xFFFFFFFF), 0, 15, c(0xFF9A9A9A)));
        g.fill(star);
        // facettes : moitié gauche claire, moitié droite plus sombre
        Shape old = g.getClip();
        g.clip(star);
        g.setColor(a(0x000000, 0.18));
        g.fill(new Rectangle2D.Double(8, 0, 8, 16));
        g.setClip(old);
        g.setColor(a(0xFFFFFF, 0.9));
        g.fill(new Ellipse2D.Double(7.1, 6.6, 1.8, 1.8));
        g.dispose();
        saveGlyph(b, "hud_rank");
    }

    static void zones() throws Exception {
        // Planque : écusson vert frappé d'une maison.
        BufferedImage b = hi(11, 11);
        Graphics2D g = gfx(b);
        Path2D shield = new Path2D.Double();
        shield.moveTo(1, 1.2);
        shield.lineTo(10, 1.2);
        shield.lineTo(10, 5.2);
        shield.curveTo(10, 8, 7.5, 9.6, 5.5, 10.4);
        shield.curveTo(3.5, 9.6, 1, 8, 1, 5.2);
        shield.closePath();
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c(0xFF06200F));
        g.draw(shield);
        g.setPaint(new GradientPaint(0, 1, c(0xFF9CFFC2), 0, 10, c(0xFF1C8A4C)));
        g.fill(shield);
        Path2D house = new Path2D.Double();
        house.moveTo(5.5, 2.8);
        house.lineTo(8.2, 5.2);
        house.lineTo(7.4, 5.2);
        house.lineTo(7.4, 7.6);
        house.lineTo(3.6, 7.6);
        house.lineTo(3.6, 5.2);
        house.lineTo(2.8, 5.2);
        house.closePath();
        g.setColor(a(0x06200F, 0.6));
        g.fill(AffineTransform.getTranslateInstance(0.25, 0.3).createTransformedShape(house));
        g.setColor(c(0xFFFFFFFF));
        g.fill(house);
        g.dispose();
        saveGlyph(b, "hud_zone_safe");

        // Raid : crâne.
        b = hi(11, 11);
        g = gfx(b);
        Path2D skull = new Path2D.Double();
        skull.append(new Ellipse2D.Double(1.2, 0.6, 8.6, 7.6), false);
        skull.append(rr(3, 6, 5, 3.8, 1), false);
        java.awt.geom.Area area = new java.awt.geom.Area(skull);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c(0xFF1A0505));
        g.draw(area);
        g.setPaint(new GradientPaint(0, 0.6f, c(0xFFFFFCF2), 0, 10, c(0xFFB3A88E)));
        g.fill(area);
        g.setColor(c(0xFF1A0505));
        g.fill(new Ellipse2D.Double(2.6, 3.6, 2.4, 2.4));
        g.fill(new Ellipse2D.Double(6, 3.6, 2.4, 2.4));
        Path2D nose = new Path2D.Double();
        nose.moveTo(5.5, 6.1);
        nose.lineTo(6.1, 7.3);
        nose.lineTo(4.9, 7.3);
        nose.closePath();
        g.fill(nose);
        g.setStroke(new BasicStroke(0.45f));
        for (double tx = 4.4; tx <= 6.7; tx += 1.1) g.draw(new java.awt.geom.Line2D.Double(tx, 8.2, tx, 9.6));
        g.setColor(c(0xFFFF4A3A));
        g.fill(new Ellipse2D.Double(3.35, 4.35, 0.9, 0.9));
        g.fill(new Ellipse2D.Double(6.75, 4.35, 0.9, 0.9));
        g.dispose();
        saveGlyph(b, "hud_zone_raid");

        // Extraction en cours : flèche verte vers le haut.
        b = hi(11, 11);
        g = gfx(b);
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(5.5, 0.8);
        arrow.lineTo(10.2, 5.4);
        arrow.lineTo(7.4, 5.4);
        arrow.lineTo(7.4, 10.2);
        arrow.lineTo(3.6, 10.2);
        arrow.lineTo(3.6, 5.4);
        arrow.lineTo(0.8, 5.4);
        arrow.closePath();
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c(0xFF06200F));
        g.draw(arrow);
        g.setPaint(new GradientPaint(0, 1, c(0xFFB8FFD4), 0, 10, c(0xFF1F9A52)));
        g.fill(arrow);
        g.dispose();
        saveGlyph(b, "hud_zone_extract");
    }

    // ------------------------------------------------------------------ Police (lissée, coeur de 5 unités de haut)
    /** Ordre = CHARSET de Console.java. Largeur du coeur : 1 (étroits), 5 (larges), 3 (les autres). */
    static final String CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ/%.,:-+_'!?◆⇧·<>|";
    static final String NARROW = ".,:'!·|I", WIDE = "MNW⇧";

    static int core(char ch) {
        return NARROW.indexOf(ch) >= 0 ? 1 : WIDE.indexOf(ch) >= 0 ? 5 : 3;
    }

    static Font font;

    static Font pickFont() {
        List<String> have = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String name : new String[]{"Bahnschrift SemiBold Condensed", "Bahnschrift SemiBold SemiConden", "Bahnschrift", "Arial Narrow", "Arial"}) {
            if (have.contains(name)) {
                System.out.println("Police du HUD : " + name);
                return new Font(name, name.equals("Bahnschrift") || name.startsWith("Arial") ? Font.BOLD : Font.PLAIN, 400);
            }
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, 400);
    }

    /** Contour d'un caractère, en unités : chapiteau en y = 0, ligne de base en y = 5, dans une boîte de largeur core. */
    static Shape charShape(char ch) {
        int core = core(ch);
        switch (ch) {
            case '◆': {
                Path2D p = new Path2D.Double();
                p.moveTo(1.5, 0.4); p.lineTo(3, 2.5); p.lineTo(1.5, 4.6); p.lineTo(0, 2.5); p.closePath();
                return p;
            }
            case '⇧': {
                Path2D p = new Path2D.Double();
                p.moveTo(2.5, 0); p.lineTo(5, 2.6); p.lineTo(3.5, 2.6); p.lineTo(3.5, 5); p.lineTo(1.5, 5);
                p.lineTo(1.5, 2.6); p.lineTo(0, 2.6); p.closePath();
                return p;
            }
            case '·':
                return new Ellipse2D.Double(0.05, 2.05, 0.9, 0.9);
            case '|':
                return new Rectangle2D.Double(0.25, 0, 0.6, 5);
            case '-':
                return rr(0.15, 2.1, 2.7, 0.85, 0.2);
            case '_':
                return new Rectangle2D.Double(0, 4.2, 3, 0.8);
            default:
        }
        FontRenderContext frc = new FontRenderContext(null, true, true);
        double cap = -font.createGlyphVector(frc, "H").getVisualBounds().getY();
        GlyphVector gv = font.createGlyphVector(frc, String.valueOf(ch));
        Shape o = gv.getOutline();
        Rectangle2D vb = o.getBounds2D();
        double sy = 5.0 / cap;
        double sx = sy;
        double gw = vb.getWidth() * sx;
        double maxW = core == 1 ? 1.15 : core;
        if (gw > maxW) sx = maxW / vb.getWidth();
        double dx = (core - vb.getWidth() * sx) / 2 - vb.getX() * sx;
        double dy = 5;
        if (ch == ',') dy = 4.4;
        AffineTransform at = new AffineTransform();
        at.translate(dx, dy);
        at.scale(sx, sy);
        return at.createTransformedShape(o);
    }

    static BufferedImage tiny(int cellH) {
        BufferedImage b = hi(16 * 7, 4 * cellH);
        Graphics2D g = gfx(b);
        for (int k = 0; k < CHARSET.length(); k++) {
            char ch = CHARSET.charAt(k);
            double cx = (k % 16) * 7, cy = (k / 16) * cellH;
            Shape s = AffineTransform.getTranslateInstance(cx, cy).createTransformedShape(charShape(ch));
            // ombre portée douce (une demi-unité), puis la lettre blanche
            g.setColor(a(0x07080A, 0.85));
            g.fill(AffineTransform.getTranslateInstance(0.5, 0.5).createTransformedShape(s));
            g.setColor(c(0xFFFFFFFF));
            g.fill(s);
        }
        g.dispose();
        // Largeur du glyphe = coeur + 1 unité : dernière colonne de chaque cellule marquée (pixel quasi invisible).
        for (int k = 0; k < CHARSET.length(); k++) {
            int core = core(CHARSET.charAt(k));
            int x = ((k % 16) * 7 + core + 1) * K - 1, y = (k / 16) * cellH * K;
            for (int xx = x + 1; xx < ((k % 16) + 1) * 7 * K; xx++)
                for (int yy = y; yy < y + cellH * K; yy++) b.setRGB(xx, yy, 0);   // rien au-delà de la largeur
            if ((b.getRGB(x, y) >>> 24) == 0) b.setRGB(x, y, 0x01000000);
        }
        return b;
    }

    // ------------------------------------------------------------------ Barre d'objets, sélection, main secondaire
    static void hotbar() throws Exception {
        BufferedImage b = hi(182, 22);
        Graphics2D g = gfx(b);
        plate(g, 0, 0, 182, 22, 2.5, 21);
        redLine(g, 2, 180, 0.9);
        for (int i = 0; i < 9; i++) {
            double x0 = 20 * i + 2;
            g.setColor(c(0xFF020304));
            g.fill(rr(x0, 2, 18, 18, 1.4));
            g.setPaint(vgrad(3, 0xFF0B0D10, 19, 0xFF1C1F25));
            g.fill(rr(x0 + 0.75, 2.75, 16.5, 16.5, 1));
            g.setPaint(new GradientPaint(0, 2.75f, a(0x000000, 0.65), 0, 4.5f, a(0x000000, 0)));
            g.fill(new Rectangle2D.Double(x0 + 0.75, 2.75, 16.5, 1.75));
            g.setColor(a(0xFFFFFF, 0.1));
            g.fill(new Rectangle2D.Double(x0 + 1.5, 19.1, 15, 0.25));
        }
        for (int i = 0; i < 8; i++) screw(g, 20 * i + 21, 11, 0.55);
        g.dispose();
        // Au-delà de 256 px de large (x4 = 728, x2 = 364), la hotbar s'affiche en texture manquante en jeu :
        // elle est réduite à 256x31 (le jeu l'étire de toute façon sur 182x22 unités).
        BufferedImage half = new BufferedImage(256, 31, BufferedImage.TYPE_INT_ARGB);
        Graphics2D hg = half.createGraphics();
        hg.drawImage(b.getScaledInstance(256, 31, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null);
        hg.dispose();
        save(half, HUD_DIR + "hotbar.png");

        BufferedImage s = hi(24, 23);
        g = gfx(s);
        g.setStroke(new BasicStroke(2.2f));
        g.setColor(a(0xE8695C, 0.35));
        g.draw(rr(1.1, 1.1, 21.8, 20.8, 2.5));
        g.setStroke(new BasicStroke(1.1f));
        g.setPaint(new GradientPaint(0, 0, c(0xFFFFB3A6), 0, 23, c(0xFFC9372A)));
        g.draw(rr(1.1, 1.1, 21.8, 20.8, 2.2));
        g.setColor(c(0xFFFFFFFF));
        double[][] corners = {{0.6, 0.6, 1, 1}, {23.4, 0.6, -1, 1}, {0.6, 22.4, 1, -1}, {23.4, 22.4, -1, -1}};
        for (double[] cc : corners) {
            g.fill(new Rectangle2D.Double(Math.min(cc[0], cc[0] + 4 * cc[2]), Math.min(cc[1], cc[1] + 0.9 * cc[3]), 4, 0.9));
            g.fill(new Rectangle2D.Double(Math.min(cc[0], cc[0] + 0.9 * cc[2]), Math.min(cc[1], cc[1] + 4 * cc[3]), 0.9, 4));
        }
        g.dispose();
        save(s, HUD_DIR + "hotbar_selection.png");

        for (Object[] o : new Object[][]{{3, "hotbar_offhand_left.png"}, {10, "hotbar_offhand_right.png"}}) {
            int ix = (Integer) o[0];
            BufferedImage h = hi(29, 24);
            g = gfx(h);
            plate(g, ix - 2, 2, 20, 20, 2.2, 31);
            g.setColor(c(0xFF020304));
            g.fill(rr(ix - 0.75, 3.25, 17.5, 17.5, 1.2));
            g.setPaint(vgrad(4, 0xFF0B0D10, 20, 0xFF1C1F25));
            g.fill(new Rectangle2D.Double(ix, 4, 16, 16));
            g.dispose();
            save(h, HUD_DIR + o[1]);
        }
    }

    // ------------------------------------------------------------------ HUD vanilla masqué (tailles lues dans le jar client)
    static int hideVanilla(File clientJar) throws Exception {
        String prefix = "assets/minecraft/textures/gui/sprites/hud/";
        String[] hide = {"heart/", "food_", "armor_", "experience_bar_", "jump_bar_", "hotbar_attack_indicator_", "locator_bar_"};
        int n = 0;
        try (ZipFile zip = new ZipFile(clientJar)) {
            for (Enumeration<? extends ZipEntry> en = zip.entries(); en.hasMoreElements(); ) {
                ZipEntry e = en.nextElement();
                String name = e.getName();
                if (!name.startsWith(prefix) || !name.endsWith(".png")) continue;
                String rel = name.substring(prefix.length());
                boolean match = false;
                for (String h : hide) match |= rel.startsWith(h);
                if (!match) continue;
                BufferedImage img;
                try (InputStream in = zip.getInputStream(e)) {
                    img = ImageIO.read(in);
                }
                save(new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB), HUD_DIR + rel);
                n++;
            }
            for (Object[] chk : new Object[][]{{"hotbar", 182, 22}, {"hotbar_selection", 24, 23}, {"hotbar_offhand_left", 29, 24},
                    {"hotbar_offhand_right", 29, 24}}) {
                ZipEntry e = zip.getEntry(prefix + chk[0] + ".png");
                if (e == null) throw new IllegalStateException("Sprite vanilla absent : " + chk[0]);
                try (InputStream in = zip.getInputStream(e)) {
                    BufferedImage img = ImageIO.read(in);
                    if (img.getWidth() != (Integer) chk[1] || img.getHeight() != (Integer) chk[2])
                        throw new IllegalStateException("Taille inattendue pour " + chk[0]);
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ Police orbitextraction:hud
    static String u(int code) {
        return String.format("\\u%04X", code);
    }

    static String glyph(String file, int ascent, int height, String chars) {
        return "    {\"type\": \"bitmap\", \"file\": \"orbitextraction:font/" + file + ".png\", \"ascent\": " + ascent
                + ", \"height\": " + height + ", \"chars\": [" + chars + "]}";
    }

    static String one(int code) {
        return "\"" + u(code) + "\"";
    }

    static String tinyRows(int base) {
        List<String> rows = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            StringBuilder s = new StringBuilder("\"");
            for (int col = 0; col < 16; col++) {
                int k = r * 16 + col;
                s.append(u(k < CHARSET.length() ? base + k : 0));
            }
            rows.add(s.append('"').toString());
        }
        return String.join(", ", rows);
    }

    static void tiles(List<String> p, String name, int ascent, int height, int first, int count) {
        for (int i = 0; i < count; i++) p.add(glyph(name + "_" + i, ascent, height, one(first + i)));
    }

    static void writeFont() throws Exception {
        List<String> spaces = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            spaces.add("\"" + u(0xF801 + i) + "\": " + (-(1 << i)));
            spaces.add("\"" + u(0xF821 + i) + "\": " + (1 << i));
        }
        List<String> p = new ArrayList<>();
        p.add("    {\"type\": \"space\", \"advances\": {" + String.join(", ", spaces) + "}}");
        p.add(glyph("hud_tiny", -37, 7, tinyRows(0xE100)));
        p.add(glyph("hud_tiny", -46, 7, tinyRows(0xE140)));
        p.add(glyph("hud_tiny", -51, 7, tinyRows(0xE180)));
        p.add(glyph("hud_tiny", -56, 7, tinyRows(0xE1C0)));
        p.add(glyph("hud_tiny_cast", 17, 17, tinyRows(0xE200)));
        tiles(p, "hud_wing_l", -34, 31, 0xE300, WING_TILES.length);
        tiles(p, "hud_wing_l_hole", -34, 31, 0xE302, WING_TILES.length);
        tiles(p, "hud_wing_r", -34, 31, 0xE304, WING_TILES.length);
        tiles(p, "hud_bridge", -35, 7, 0xE306, BRIDGE_TILES.length);
        p.add(glyph("hud_fill8_1", -45, 8, one(0xE310)));
        p.add(glyph("hud_fill8_8", -45, 8, one(0xE311)));
        p.add(glyph("hud_fill4_1", -56, 4, one(0xE312)));
        p.add(glyph("hud_fill4_8", -56, 4, one(0xE313)));
        p.add(glyph("hud_fill3_1", -37, 3, one(0xE314)));
        p.add(glyph("hud_fill3_8", -37, 3, one(0xE315)));
        p.add(glyph("hud_fillc_1", 17, 17, one(0xE316)));
        p.add(glyph("hud_fillc_8", 17, 17, one(0xE317)));
        p.add(glyph("hud_rank", -46, 16, one(0xE320)));
        p.add(glyph("hud_zone_safe", -48, 11, one(0xE330)));
        p.add(glyph("hud_zone_raid", -48, 11, one(0xE331)));
        p.add(glyph("hud_zone_extract", -48, 11, one(0xE332)));
        tiles(p, "hud_cast", 19, 19, 0xE340, CAST_TILES.length);
        File f = new File(pack, "assets/orbitextraction/font/hud.json");
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), "{\n  \"providers\": [\n" + String.join(",\n", p) + "\n  ]\n}\n", StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ Aperçu (maquettes/hud_apercu.png)
    /** Reproduction de Hud.java avec des valeurs d'exemple : 480 x 120 unités d'interface, en x K (GUI 4). */
    static final class Preview {
        static final int GW = 480, GH = 120, CX = 240;
        final BufferedImage img = new BufferedImage(GW * K, GH * K, BufferedImage.TYPE_INT_ARGB);
        final Map<String, BufferedImage> tex;

        Preview(Map<String, BufferedImage> tex) {
            this.tex = tex;
            Graphics2D g = img.createGraphics();
            g.setPaint(new GradientPaint(0, 0, c(0xFF7D9A84), 0, GH * K, c(0xFF39463A)));
            g.fillRect(0, 0, GW * K, GH * K);
            g.dispose();
        }

        /** Image posée avec son haut à (ascent + 65) unités du bas, teintée par color. */
        void put(BufferedImage src, int sx, int sy, int w, int h, double x, int ascent, int color) {
            int top = (GH - (ascent + 65)) * K, left = (int) Math.round((CX + x) * K);
            for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) {
                int col = src.getRGB(sx + i, sy + j);
                int al = col >>> 24;
                if (al <= 1) continue;
                int r = ((col >> 16) & 255) * ((color >> 16) & 255) / 255, gg = ((col >> 8) & 255) * ((color >> 8) & 255) / 255,
                        bl = (col & 255) * (color & 255) / 255;
                int X = left + i, Y = top + j;
                if (X < 0 || Y < 0 || X >= img.getWidth() || Y >= img.getHeight()) continue;
                img.setRGB(X, Y, mix(img.getRGB(X, Y), 0xFF000000 | (r << 16) | (gg << 8) | bl, al / 255.0));
            }
        }

        void glyph(String name, double x, int ascent, int color) {
            BufferedImage t = tex.get(name);
            put(t, 0, 0, t.getWidth(), t.getHeight(), x, ascent, color);
        }

        void tiles(String name, double x, int ascent, int[] widths) {
            for (int i = 0; i < widths.length; i++) {
                glyph(name + "_" + i, x, ascent, 0xFFFFFFFF);
                x += widths[i];
            }
        }

        void fill(String name, int x, int w, int ascent, int color) {
            BufferedImage t = tex.get(name);
            for (int i = 0; i < w; i++) put(t, 0, 0, K, t.getHeight(), x + i, ascent, color);
        }

        static int width(String s) {
            int w = 0;
            for (char ch : s.toCharArray()) w += ch == ' ' || CHARSET.indexOf(ch) < 0 ? 3 : core(ch) + 1;
            return w;
        }

        int text(int x, String s, int ascent, int cellH, int color) {
            BufferedImage t = tex.get(cellH == 7 ? "hud_tiny" : "hud_tiny_cast");
            for (char ch : s.toCharArray()) {
                int idx = CHARSET.indexOf(ch);
                if (ch == ' ' || idx < 0) {
                    x += 3;
                    continue;
                }
                put(t, (idx % 16) * 7 * K, (idx / 16) * cellH * K, 7 * K, 7 * K, x, ascent, color);
                x += core(ch) + 1;
            }
            return x;
        }

        void hotbar() {
            BufferedImage h = tex.get("hotbar"), sel = tex.get("hotbar_selection"), off = tex.get("hotbar_offhand_left");
            put(h, 0, 0, h.getWidth(), h.getHeight(), -91, 22 - 65, 0xFFFFFFFF);
            put(off, 0, 0, off.getWidth(), off.getHeight(), -120, 23 - 65, 0xFFFFFFFF);
            put(sel, 0, 0, sel.getWidth(), sel.getHeight(), -92 + 2 * 20, 23 - 65, 0xFFFFFFFF);
        }
    }

    static void preview(File dir, Map<String, BufferedImage> tex) throws Exception {
        Preview v = new Preview(tex);
        v.hotbar();
        v.tiles("hud_wing_l_hole", -210, -34, WING_TILES);
        v.tiles("hud_wing_r", 93, -34, WING_TILES);
        v.tiles("hud_bridge", -93, -35, BRIDGE_TILES);
        v.fill("hud_fill3_8", -90, (int) Math.round(0.58 * 180), -37, 0xFFD7DCE2);
        v.fill("hud_fill8_8", -184, (int) Math.round(0.62 * 64), -45, 0xFF2ECC71);
        v.fill("hud_fill4_8", -184, (int) Math.round(0.8 * 64), -56, 0xFFE6A23C);
        String hp = "62/100";
        v.text(-152 - Preview.width(hp) / 2, hp, -46, 7, 0xFFFFFFFF);
        v.text((int) Math.round(-197.5 - Preview.width("12") / 2.0), "12", -51, 7, 0xFFD7DCE2);
        v.text(-206, "ZAZOU", -37, 7, 0xFFFFFFFF);
        v.text(-97 - Preview.width("TACTIQUE"), "TACTIQUE", -37, 7, 0xFF5CC8FF);
        int x = v.text(97, "F", -37, 7, 0xFFE8695C);
        x = v.text(x + 3, "RECHARGER", -37, 7, 0xFFB8C0CA);
        x = v.text(x + 4, "·", -37, 7, 0xFF5A6270);
        x = v.text(x + 4, "MAJ+F", -37, 7, 0xFFE8695C);
        v.text(x + 3, "FICHE", -37, 7, 0xFFB8C0CA);
        v.glyph("hud_rank", 101, -46, 0xFFF5C542);
        v.fill("hud_fill8_8", 120, (int) Math.round(24 / 30.0 * 64), -45, 0xFFD9C27A);
        v.text(152 - Preview.width("24/30") / 2, "24/30", -46, 7, 0xFFFFFFFF);
        v.text(121, "RESERVE 120", -56, 7, 0xFFD9C27A);
        v.glyph("hud_zone_raid", 192, -48, 0xFFFFFFFF);
        v.tiles("hud_cast", -62, 19, CAST_TILES);
        v.fill("hud_fillc_8", -60, (int) Math.round(0.4 * 120), 17, 0xFF2ECC71);
        v.text(-Preview.width("4,2") / 2, "4,2", 17, 17, 0xFFFFFFFF);
        dir.mkdirs();
        ImageIO.write(v.img, "png", new File(dir, "hud_apercu.png"));
    }

    // ------------------------------------------------------------------ main
    public static void main(String[] args) throws Exception {
        pack = new File(args[0]);
        File previews = args.length > 1 ? new File(args[1]) : null;
        String appdata = System.getenv("APPDATA");
        File clientJar = new File(appdata == null ? "" : appdata, ".minecraft/versions/26.3/26.3.jar");
        if (!clientJar.isFile()) throw new IllegalStateException("Jar client 26.3 introuvable : " + clientJar);
        font = pickFont();

        // anciennes pièces d'un seul tenant (avant le découpage en tuiles)
        for (String old : new String[]{"hud_wing_l", "hud_wing_l_hole", "hud_wing_r", "hud_bridge", "hud_cast"})
            new File(pack, FONT_DIR + old + ".png").delete();

        wingL(false, "hud_wing_l");
        wingL(true, "hud_wing_l_hole");
        wingR();
        bridge();
        castFrame();
        for (int w : new int[]{1, 8}) {
            fill(w, 8, 0, "hud_fill8_" + w);
            fill(w, 4, 0, "hud_fill4_" + w);
            fill(w, 3, 0, "hud_fill3_" + w);
            fill(w, 5, 12, "hud_fillc_" + w);
        }
        rankStar();
        zones();
        save(tiny(7), FONT_DIR + "hud_tiny.png");
        save(tiny(17), FONT_DIR + "hud_tiny_cast.png");
        hotbar();
        int hidden = hideVanilla(clientJar);
        writeFont();

        if (previews != null) {
            Map<String, BufferedImage> tex = new HashMap<>();
            File dir = new File(pack, FONT_DIR);
            for (File f : dir.listFiles()) tex.put(f.getName().replace(".png", ""), ImageIO.read(f));
            for (String n : new String[]{"hotbar", "hotbar_selection", "hotbar_offhand_left"})
                tex.put(n, ImageIO.read(new File(pack, HUD_DIR + n + ".png")));
            preview(previews, tex);
        }
        System.out.println("OK HUD x" + K + " : console, police orbitextraction:hud, " + hidden + " sprites vanilla masques");
    }
}
