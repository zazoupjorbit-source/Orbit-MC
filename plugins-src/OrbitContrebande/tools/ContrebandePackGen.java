import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Générateur du pack de CONTREBANDE (Java pur, relancé par build.ps1) :
 *  1. icônes HD 64 px des objets (germe, minerai, marchandise teintée, 8 additifs) ;
 *  2. modèles 3D (communicateur, 3 drones de la douane, caisse de cargaison) avec leurs atlas ;
 *  3. fonds de menus au style « station » ORBIT (mêmes règles que plugins-src/OrbitDevCore/tools/OrbitGuiGen.java :
 *     image 2x découpée en 2 x 4 glyphes de la police contrebande:gui, dessinée dans le titre du coffre).
 * Les cases dessinées ici (slots) doivent rester les mêmes que dans Menus.java.
 *
 * Usage : java ContrebandePackGen.java <dossier_pack> <dossier_apercus>
 */
public class ContrebandePackGen {

    static final String NS = "contrebande";
    static File pack, apercus;
    static final Map<String, BufferedImage> ICONES = new LinkedHashMap<>();

    public static void main(String[] a) throws Exception {
        pack = new File(a[0]);
        apercus = new File(a.length > 1 ? a[1] : ".");
        apercus.mkdirs();
        write("pack.mcmeta", "{\n  \"pack\": {\n    \"description\": \"§6ORBIT §7- CONTREBANDE\",\n    \"min_format\": 97,\n    \"max_format\": 97\n  }\n}\n");
        write("assets/" + NS + "/items/invisible.json", "{\n  \"model\": {\n    \"type\": \"minecraft:empty\"\n  }\n}\n");
        objets();
        modeles();
        menus();
        planche();
        System.out.println("Pack de CONTREBANDE : " + ICONES.size() + " icônes, " + MODELES.size() + " modèles 3D, " + ECRITS.size() + " menus.");
    }

    // =====================================================================================================
    // Fichiers
    // =====================================================================================================

    static void write(String path, String content) throws IOException {
        File f = new File(pack, path);
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
    }

    static void png(String path, BufferedImage img) throws IOException {
        File f = new File(pack, "assets/" + NS + "/textures/" + path + ".png");
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }

    static Graphics2D g(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    static Color c(int rgb) {
        return new Color(rgb);
    }

    static Color a(int rgb, int alpha) {
        return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha);
    }

    static BufferedImage toile() {
        return new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    }

    static Path2D poly(double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }
        p.closePath();
        return p;
    }

    /** Contour sombre épais puis liseré : lisible sur n'importe quel fond de menu. */
    static void contour(Graphics2D g, Shape s, int sombre) {
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c(sombre));
        g.draw(s);
    }

    static void halo(Graphics2D g, double cx, double cy, double r, int rgb, int alpha) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) r, new float[]{0f, 1f}, new Color[]{a(rgb, alpha), a(rgb, 0)}));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    static void etincelle(Graphics2D g, double x, double y, double r, int rgb) {
        g.setColor(c(rgb));
        g.fill(poly(x, y - r, x + r * 0.28, y - r * 0.28, x + r, y, x + r * 0.28, y + r * 0.28, x, y + r, x - r * 0.28, y + r * 0.28, x - r, y, x - r * 0.28, y - r * 0.28));
    }

    /** Objet plat à une texture. */
    static void objet(String id, BufferedImage img) throws IOException {
        png("item/" + id, img);
        write("assets/" + NS + "/items/" + id + ".json", "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \"" + NS + ":item/" + id + "\"\n  }\n}\n");
        write("assets/" + NS + "/models/item/" + id + ".json", "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\"layer0\": \"" + NS + ":item/" + id + "\"}\n}\n");
        ICONES.put(id, img);
    }

    /** Marchandise : socle (0), matière teintée par la propriété (1, couleur 0 des custom_model_data), détails (2). */
    static void marchandise(String id, BufferedImage fond, BufferedImage teinte, BufferedImage dessus, int defaut) throws IOException {
        png("item/" + id + "_0", fond);
        png("item/" + id + "_1", teinte);
        png("item/" + id + "_2", dessus);
        write("assets/" + NS + "/items/" + id + ".json", "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \"" + NS + ":item/" + id + "\",\n"
                + "    \"tints\": [\n      {\"type\": \"minecraft:constant\", \"value\": -1},\n"
                + "      {\"type\": \"minecraft:custom_model_data\", \"index\": 0, \"default\": " + (0xFF000000 | defaut) + "},\n"
                + "      {\"type\": \"minecraft:constant\", \"value\": -1}\n    ]\n  }\n}\n");
        write("assets/" + NS + "/models/item/" + id + ".json", "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\"layer0\": \"" + NS + ":item/" + id
                + "_0\", \"layer1\": \"" + NS + ":item/" + id + "_1\", \"layer2\": \"" + NS + ":item/" + id + "_2\"}\n}\n");
        // aperçu : teinte par défaut appliquée
        BufferedImage ap = toile();
        Graphics2D g = ap.createGraphics();
        g.drawImage(fond, 0, 0, null);
        g.drawImage(teinter(teinte, defaut), 0, 0, null);
        g.drawImage(dessus, 0, 0, null);
        g.dispose();
        ICONES.put(id, ap);
    }

    static BufferedImage teinter(BufferedImage gris, int rgb) {
        BufferedImage out = new BufferedImage(gris.getWidth(), gris.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int tr = (rgb >> 16) & 255, tg = (rgb >> 8) & 255, tb = rgb & 255;
        for (int x = 0; x < gris.getWidth(); x++) {
            for (int y = 0; y < gris.getHeight(); y++) {
                int p = gris.getRGB(x, y);
                int al = p >>> 24;
                out.setRGB(x, y, (al << 24) | ((((p >> 16) & 255) * tr / 255) << 16) | ((((p >> 8) & 255) * tg / 255) << 8) | ((p & 255) * tb / 255));
            }
        }
        return out;
    }

    // =====================================================================================================
    // 1. Icônes des objets
    // =====================================================================================================

    static final int NOIR = 0x0B0D16;

    static void objets() throws IOException {
        objet("germe", germe());
        objet("minerai", minerai());
        quartz();
        plasma();
        cristal();
        objet("add_lune", lune());
        objet("add_comete", comete());
        objet("add_fer", fer());
        objet("add_soufre", soufre());
        objet("add_echo", echo());
        objet("add_algue", algue());
        objet("add_or", or());
        objet("add_plume", plume());
    }

    static BufferedImage germe() {
        BufferedImage img = toile();
        Graphics2D g = g(img);
        halo(g, 32, 38, 28, 0xC79BFF, 90);
        // cosse
        Path2D cosse = new Path2D.Double();
        cosse.moveTo(32, 20);
        cosse.curveTo(50, 26, 52, 50, 32, 58);
        cosse.curveTo(12, 50, 14, 26, 32, 20);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(26, 32), 30, new float[]{0f, 0.6f, 1f}, new Color[]{c(0xE9D6FF), c(0x9A63E8), c(0x3F1F7A)}));
        g.fill(cosse);
        g.setClip(cosse);
        g.setColor(a(0xFFFFFF, 70));
        g.setStroke(new BasicStroke(1.6f));
        for (int k = -2; k <= 2; k++) {
            g.draw(new java.awt.geom.QuadCurve2D.Double(32, 20, 32 + k * 9, 40, 32, 58));
        }
        // cœur lumineux
        halo(g, 32, 42, 12, 0xFFFFFF, 200);
        g.setClip(null);
        contour(g, cosse, 0x1D0F3D);
        // pousse
        g.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c(0x12402F));
        Path2D tige = new Path2D.Double();
        tige.moveTo(32, 21);
        tige.curveTo(31, 14, 34, 10, 38, 6);
        g.draw(tige);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c(0x62F4B4));
        g.draw(tige);
        Shape f1 = poly(33, 14, 22, 9, 18, 15, 27, 18);
        Shape f2 = poly(36, 9, 46, 4, 49, 11, 40, 13);
        for (Shape f : new Shape[]{f1, f2}) {
            g.setPaint(new GradientPaint(20, 6, c(0x9CFFD6), 48, 18, c(0x1FA36E)));
            g.fill(f);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(c(0x12402F));
            g.draw(f);
        }
        etincelle(g, 49, 26, 5, 0xFFFFFF);
        etincelle(g, 13, 44, 3.5, 0xE9D6FF);
        g.dispose();
        return img;
    }

    static BufferedImage minerai() {
        BufferedImage img = toile();
        Graphics2D g = g(img);
        halo(g, 32, 34, 30, 0xFF4FD8, 70);
        Shape roche = poly(14, 22, 28, 9, 46, 12, 56, 28, 52, 46, 36, 57, 17, 52, 8, 37);
        g.setPaint(new GradientPaint(14, 10, c(0x4A4658), 50, 56, c(0x14121C)));
        g.fill(roche);
        g.setClip(roche);
        // facettes
        g.setColor(a(0xFFFFFF, 28));
        g.fill(poly(14, 22, 28, 9, 30, 28, 18, 36));
        g.setColor(a(0x000000, 70));
        g.fill(poly(30, 28, 56, 28, 52, 46, 36, 57, 34, 40));
        g.setColor(a(0xFFFFFF, 16));
        g.fill(poly(28, 9, 46, 12, 56, 28, 30, 28));
        // veines de plasma
        double[][] veines = {{12, 40, 24, 34, 30, 28, 40, 20, 48, 13}, {30, 28, 34, 40, 44, 46, 51, 44}, {34, 40, 30, 50, 24, 54}};
        for (double[] v : veines) {
            Path2D p = new Path2D.Double();
            p.moveTo(v[0], v[1]);
            for (int i = 2; i < v.length; i += 2) {
                p.lineTo(v[i], v[i + 1]);
            }
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(a(0xFF4FD8, 90));
            g.draw(p);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(c(0xFF7BE6));
            g.draw(p);
            g.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(c(0xFFFFFF));
            g.draw(p);
        }
        g.setClip(null);
        contour(g, roche, NOIR);
        etincelle(g, 30, 28, 5, 0xFFFFFF);
        g.dispose();
        return img;
    }

    /** Un éclat de cristal (prisme à pointe) : renvoie [corps, face claire, face sombre]. */
    static Shape[] eclat(double bx, double by, double h, double w, double angle) {
        AffineTransform t = AffineTransform.getRotateInstance(Math.toRadians(angle), bx, by);
        Shape corps = t.createTransformedShape(poly(bx - w / 2, by, bx - w / 2, by - h * 0.72, bx, by - h, bx + w / 2, by - h * 0.72, bx + w / 2, by));
        Shape clair = t.createTransformedShape(poly(bx - w / 2, by, bx - w / 2, by - h * 0.72, bx, by - h, bx - w * 0.08, by - h * 0.68, bx - w * 0.08, by));
        Shape sombre = t.createTransformedShape(poly(bx + w * 0.2, by, bx + w * 0.2, by - h * 0.7, bx, by - h, bx + w / 2, by - h * 0.72, bx + w / 2, by));
        return new Shape[]{corps, clair, sombre};
    }

    static void quartz() throws IOException {
        double[][] e = {{20, 50, 26, 12, -24}, {44, 50, 30, 13, 20}, {32, 52, 44, 16, 0}};
        BufferedImage fond = toile(), teinte = toile(), dessus = toile();
        Graphics2D g = g(fond);
        halo(g, 32, 34, 30, 0xFFFFFF, 60);
        Shape socle = poly(8, 50, 16, 44, 48, 44, 56, 50, 52, 58, 12, 58);
        g.setPaint(new GradientPaint(0, 44, c(0x4A4F66), 0, 58, c(0x171927)));
        g.fill(socle);
        contour(g, socle, NOIR);
        g.dispose();
        g = g(teinte);
        for (double[] k : e) {
            Shape[] s = eclat(k[0], k[1], k[2], k[3], k[4]);
            g.setColor(c(0xC4C4C4));
            g.fill(s[0]);
            g.setColor(c(0xFFFFFF));
            g.fill(s[1]);
            g.setColor(c(0x7E7E7E));
            g.fill(s[2]);
        }
        g.dispose();
        g = g(dessus);
        for (double[] k : e) {
            Shape[] s = eclat(k[0], k[1], k[2], k[3], k[4]);
            contour(g, s[0], NOIR);
        }
        g.setColor(a(0xFFFFFF, 215));
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(28, 44, 28, 20));
        g.draw(new Line2D.Double(15, 44, 9.5, 31));
        g.draw(new Line2D.Double(42, 44, 47, 28));
        etincelle(g, 32, 10, 6, 0xFFFFFF);
        etincelle(g, 53, 24, 3.5, 0xFFFFFF);
        // le socle repasse devant le pied des éclats
        g.setPaint(new GradientPaint(0, 50, c(0x3B3F54), 0, 58, c(0x171927)));
        Shape devant = poly(8, 50, 56, 50, 52, 58, 12, 58);
        g.fill(devant);
        contour(g, devant, NOIR);
        g.setColor(a(0xFFFFFF, 60));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Line2D.Double(11, 51.5, 53, 51.5));
        g.dispose();
        marchandise("produit_quartz", fond, teinte, dessus, 0xC79BFF);
    }

    static void plasma() throws IOException {
        BufferedImage fond = toile(), teinte = toile(), dessus = toile();
        Shape verre = new RoundRectangle2D.Double(19, 12, 26, 42, 12, 12);
        Graphics2D g = g(fond);
        halo(g, 32, 34, 30, 0xFFFFFF, 55);
        g.setColor(c(0x12131E));
        g.fill(verre);
        g.dispose();
        g = g(teinte);
        g.setClip(verre);
        // liquide : surface ondulée, plus clair au centre
        Path2D liq = new Path2D.Double();
        liq.moveTo(19, 27);
        liq.curveTo(25, 23, 30, 31, 36, 26);
        liq.curveTo(40, 23, 43, 27, 45, 26);
        liq.lineTo(45, 54);
        liq.lineTo(19, 54);
        liq.closePath();
        g.setPaint(new GradientPaint(19, 0, c(0x8A8A8A), 32, 0, c(0xFFFFFF), true));
        g.fill(liq);
        g.setColor(a(0x000000, 60));
        g.fill(new Rectangle2D.Double(19, 44, 26, 10));
        g.setColor(c(0xFFFFFF));
        for (double[] b : new double[][]{{26, 40, 2.6}, {37, 34, 1.8}, {31, 47, 2}, {39, 45, 1.4}, {25, 31, 1.4}}) {
            g.fill(new Ellipse2D.Double(b[0] - b[2], b[1] - b[2], b[2] * 2, b[2] * 2));
        }
        g.dispose();
        g = g(dessus);
        // reflets du verre
        g.setClip(verre);
        g.setColor(a(0xFFFFFF, 110));
        g.fill(new RoundRectangle2D.Double(22, 15, 4.5, 34, 4, 4));
        g.setColor(a(0xFFFFFF, 45));
        g.fill(new RoundRectangle2D.Double(38, 17, 3, 30, 3, 3));
        g.setClip(null);
        contour(g, verre, NOIR);
        // embouts de métal doré
        for (double y : new double[]{6, 50}) {
            Shape cap = new RoundRectangle2D.Double(16, y, 32, 9, 5, 5);
            g.setPaint(new GradientPaint(16, 0, c(0xFFF0C2), 48, 0, c(0x8A6A2A)));
            g.fill(cap);
            g.setColor(a(0xFFFFFF, 120));
            g.fill(new Rectangle2D.Double(19, y + 1.6, 26, 1.6));
            contour(g, cap, NOIR);
        }
        Shape bouchon = new RoundRectangle2D.Double(26, 2, 12, 6, 3, 3);
        g.setColor(c(0x3A4250));
        g.fill(bouchon);
        contour(g, bouchon, NOIR);
        etincelle(g, 49, 20, 4.5, 0xFFFFFF);
        g.dispose();
        marchandise("produit_plasma", fond, teinte, dessus, 0xFF4FD8);
    }

    static void cristal() throws IOException {
        BufferedImage fond = toile(), teinte = toile(), dessus = toile();
        Shape gemme = poly(32, 5, 50, 20, 44, 46, 32, 58, 20, 46, 14, 20);
        Graphics2D g = g(fond);
        halo(g, 32, 32, 31, 0xFFFFFF, 95);
        g.dispose();
        g = g(teinte);
        g.setColor(c(0xBDBDBD));
        g.fill(gemme);
        g.setColor(c(0xFFFFFF));
        g.fill(poly(32, 5, 14, 20, 24, 24));
        g.fill(poly(32, 5, 40, 24, 24, 24));
        g.setColor(c(0xE2E2E2));
        g.fill(poly(24, 24, 40, 24, 32, 58));
        g.setColor(c(0x8C8C8C));
        g.fill(poly(40, 24, 50, 20, 44, 46, 32, 58));
        g.setColor(c(0xA8A8A8));
        g.fill(poly(32, 5, 50, 20, 40, 24));
        g.setColor(c(0xD0D0D0));
        g.fill(poly(14, 20, 24, 24, 32, 58, 20, 46));
        g.dispose();
        g = g(dessus);
        g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(a(0xFFFFFF, 150));
        for (double[] l : new double[][]{{32, 5, 24, 24}, {32, 5, 40, 24}, {14, 20, 24, 24}, {50, 20, 40, 24}, {24, 24, 40, 24}, {24, 24, 32, 58}, {40, 24, 32, 58}}) {
            g.draw(new Line2D.Double(l[0], l[1], l[2], l[3]));
        }
        contour(g, gemme, NOIR);
        // anneau d'orbite doré autour de la gemme
        g.setStroke(new BasicStroke(3.6f));
        g.setColor(c(NOIR));
        Shape anneau = AffineTransform.getRotateInstance(Math.toRadians(-18), 32, 34).createTransformedShape(new Ellipse2D.Double(5, 27, 54, 14));
        Area devant = new Area(new BasicStroke(3.6f).createStrokedShape(anneau));
        devant.intersect(new Area(new Rectangle2D.Double(0, 34, 64, 30)));
        g.fill(devant);
        Area or = new Area(new BasicStroke(1.9f).createStrokedShape(anneau));
        or.intersect(new Area(new Rectangle2D.Double(0, 34, 64, 30)));
        g.setPaint(new GradientPaint(5, 0, c(0xFFF0C2), 59, 0, c(0xB8923F)));
        g.fill(or);
        etincelle(g, 23, 15, 6.5, 0xFFFFFF);
        etincelle(g, 47, 50, 4, 0xFFFFFF);
        g.dispose();
        marchandise("produit_cristal", fond, teinte, dessus, 0x62CFF4);
    }

    /** Bocal de verre bouché (pour les poudres) : renvoie la forme du verre. */
    static Shape bocal(Graphics2D g, int liege) {
        Shape verre = new RoundRectangle2D.Double(13, 20, 38, 38, 16, 16);
        Shape col = new Rectangle2D.Double(23, 13, 18, 10);
        Shape bouchon = new RoundRectangle2D.Double(21, 5, 22, 11, 5, 5);
        g.setColor(a(0x1A2338, 215));
        g.fill(verre);
        g.fill(col);
        g.setPaint(new GradientPaint(21, 0, c(liege), 43, 0, c(0x5A3A1A)));
        g.fill(bouchon);
        contour(g, bouchon, NOIR);
        return verre;
    }

    static void verre(Graphics2D g, Shape verre) {
        g.setClip(verre);
        g.setColor(a(0xFFFFFF, 105));
        g.fill(new RoundRectangle2D.Double(17, 25, 5, 24, 5, 5));
        g.setColor(a(0xFFFFFF, 40));
        g.fill(new RoundRectangle2D.Double(43, 28, 3.5, 20, 3, 3));
        g.setClip(null);
        contour(g, verre, NOIR);
        g.setStroke(new BasicStroke(2.6f));
        g.setColor(c(NOIR));
        g.draw(new Line2D.Double(23, 14, 23, 22));
        g.draw(new Line2D.Double(41, 14, 41, 22));
    }

    static BufferedImage lune() {
        BufferedImage img = toile();
        Graphics2D g = g(img);
        halo(g, 32, 40, 28, 0xFFE680, 110);
        Shape v = bocal(g, 0xD9B27A);
        g.setClip(v);
        Path2D tas = new Path2D.Double();
        tas.moveTo(13, 44);
        tas.curveTo(22, 34, 30, 40, 38, 36);
        tas.curveTo(44, 33, 48, 40, 51, 38);
        tas.lineTo(51, 58);
        tas.lineTo(13, 58);
        tas.closePath();
        g.setPaint(new GradientPaint(0, 34, c(0xFFFBE0), 0, 58, c(0xE0A63A)));
        g.fill(tas);
        Random r = new Random(4);
        for (int i = 0; i < 26; i++) {
            double x = 15 + r.nextDouble() * 34, y = 40 + r.nextDouble() * 16;
            g.setColor(r.nextBoolean() ? c(0xFFFFFF) : c(0xFFE680));
            g.fill(new Ellipse2D.Double(x, y, 1.6, 1.6));
        }
        g.setClip(null);
        verre(g, v);
        // croissant
        Area lune = new Area(new Ellipse2D.Double(25, 24, 13, 13));
        lune.subtract(new Area(new Ellipse2D.Double(29, 22, 12, 12)));
        g.setColor(c(0xFFFBE0));
        g.fill(lune);
        etincelle(g, 49, 14, 5.5, 0xFFFFFF);
        etincelle(g, 12, 16, 3.5, 0xFFE680);
        g.dispose();
        return img;
    }

    static BufferedImage comete() {
        BufferedImage img = toile();
        Graphics2D g = g(img);
        // traîne
        g.setPaint(new GradientPaint(52, 8, a(0x9FE7FF, 0), 26, 36, a(0x9FE7FF, 170)));
        g.fill(poly(58, 4, 40, 20, 26, 44, 20, 30));
        g.fill(poly(60, 16, 44, 28, 34, 48, 30, 34));
        halo(g, 26, 40, 24, 0x9FE7FF, 120);
        Shape glace = poly(10, 46, 16, 28, 30, 22, 42, 30, 44, 46, 32, 58, 18, 57);
        g.setPaint(new GradientPaint(12, 24, c(0xF2FCFF), 42, 58, c(0x2F7FC4)));
        g.fill(glace);
        g.setClip(glace);
        g.setColor(a(0xFFFFFF, 190));
        g.fill(poly(16, 28, 30, 22, 28, 38, 18, 42));
        g.setColor(a(0x0B3A78, 120));
        g.fill(poly(28, 38, 44, 46, 32, 58));
        g.setColor(a(0xFFFFFF, 90));
        g.fill(poly(30, 22, 42, 30, 28, 38));
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(a(0xFFFFFF, 210));
        g.draw(new Line2D.Double(18, 42, 28, 38));
        g.draw(new Line2D.Double(28, 38, 32, 58));
        g.draw(new Line2D.Double(28, 38, 44, 46));
        g.setClip(null);
        contour(g, glace, 0x0A2244);
        etincelle(g, 20, 30, 6, 0xFFFFFF);
        etincelle(g, 50, 12, 3.5, 0xFFFFFF);
        g.dispose();
        return img;
    }

    static BufferedImage fer() {
        BufferedImage img = toile();
        Graphics2D g = g(img);
        halo(g, 32, 34, 29, 0xB0B8C8, 70);
        Shape bloc = poly(12, 26, 24, 12, 42, 10, 54, 22, 56, 40, 44, 54, 24, 56, 10, 44);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(24, 22), 40, new float[]{0f, 0.5f, 1f}, new Color[]{c(0xE8EEF8), c(0x7C8698), c(0x252A36)}));
        g.fill(bloc);
        g.setClip(bloc);
        // cratères
        for (double[] k : new double[][]{{24, 26, 6}, {40, 22, 4.5}, {42, 40, 7}, {24, 44, 4.5}, {33, 33, 3}}) {
            g.setColor(a(0x10131C, 150));
            g.fill(new Ellipse2D.Double(k[0] - k[2], k[1] - k[2], k[2] * 2, k[2] * 2));
            g.setColor(a(0xFFFFFF, 120));
            g.setStroke(new BasicStroke(1.3f));
            g.draw(new java.awt.geom.Arc2D.Double(k[0] - k[2], k[1] - k[2], k[2] * 2, k[2] * 2, 200, 140, java.awt.geom.Arc2D.OPEN));
        }
        // stries de Widmanstätten
        g.setStroke(new BasicStroke(1f));
        g.setColor(a(0xFFFFFF, 70));
        for (int i = 0; i < 7; i++) {
            g.draw(new Line2D.Double(8 + i * 8, 60, 28 + i * 8, 6));
        }
        g.setClip(null);
        contour(g, bloc, NOIR);
        etincelle(g, 20, 18, 5.5, 0xFFFFFF);
        // aimantation : deux petits arcs
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c(0x62CFF4));
        g.draw(new java.awt.geom.Arc2D.Double(46, 2, 14, 14, 300, 120, java.awt.geom.Arc2D.OPEN));
        g.draw(new java.awt.geom.Arc2D.Double(50, 5, 8, 8, 300, 120, java.awt.geom.Arc2D.OPEN));
        g.dispose();
        return img;
    }

    static BufferedImage soufre() {
        BufferedImage img = toile();
        Graphics2D g = g(img);
        halo(g, 32, 38, 29, 0xFF8A3D, 120);
        Shape v = bocal(g, 0x8A8FA3);
        g.setClip(v);
        Path2D tas = new Path2D.Double();
        tas.moveTo(13, 42);
        tas.curveTo(24, 36, 30, 44, 40, 38);
        tas.curveTo(45, 35, 48, 40, 51, 39);
        tas.lineTo(51, 58);
        tas.lineTo(13, 58);
        tas.closePath();
        g.setPaint(new GradientPaint(0, 36, c(0xFFD86B), 0, 58, c(0xD9431C)));
        g.fill(tas);
        g.setClip(null);
        verre(g, v);
        // flamme
        Path2D fl = new Path2D.Double();
        fl.moveTo(32, 22);
        fl.curveTo(42, 32, 40, 42, 32, 44);
        fl.curveTo(24, 42, 23, 34, 28, 30);
        fl.curveTo(29, 33, 31, 33, 31, 30);
        fl.curveTo(31, 27, 30, 25, 32, 22);
        g.setPaint(new GradientPaint(0, 22, c(0xFFF3A8), 0, 44, c(0xFF5A1F)));
        g.fill(fl);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c(0x7A1E08));
        g.draw(fl);
        g.setColor(c(0xFFFFFF));
        g.fill(new Ellipse2D.Double(29.5, 35, 5, 6));
        etincelle(g, 50, 13, 5, 0xFFF3A8);
        g.dispose();
        return img;
    }

    static BufferedImage echo() {
        BufferedImage img = toile();
        Graphics2D g = g(img);
        halo(g, 32, 32, 31, 0xC79BFF, 100);
        // ondes
        for (int i = 3; i >= 1; i--) {
            double r = 12 + i * 6;
            g.setStroke(new BasicStroke(2.2f));
            g.setColor(a(0xC79BFF, 60 + (3 - i) * 55));
            g.draw(new Ellipse2D.Double(32 - r, 32 - r, 2 * r, 2 * r));
        }
        Shape orbe = new Ellipse2D.Double(18, 18, 28, 28);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(27, 26), 22, new float[]{0f, 0.5f, 1f}, new Color[]{c(0x7A5CC8), c(0x1B1038), c(0x05030C)}));
        g.fill(orbe);
        contour(g, orbe, 0x05030C);
        g.setStroke(new BasicStroke(1.6f));
        g.setColor(c(0xE9D6FF));
        g.draw(new java.awt.geom.Arc2D.Double(22, 22, 20, 20, 110, 80, java.awt.geom.Arc2D.OPEN));
        g.setColor(c(0xFFFFFF));
        g.fill(new Ellipse2D.Double(25, 24, 4, 4));
        etincelle(g, 52, 12, 4.5, 0xFFFFFF);
        etincelle(g, 11, 52, 3.5, 0xE9D6FF);
        g.dispose();
        return img;
    }

    static BufferedImage algue() {
        BufferedImage img = toile();
        Graphics2D g = g(img);
        halo(g, 32, 36, 29, 0x7CFF8A, 80);
        double[][] brins = {{20, 58, 10, 40, 26, 30, 16, 8}, {32, 58, 42, 42, 24, 26, 34, 5}, {44, 58, 54, 44, 38, 34, 48, 14}};
        int[] clair = {0x9CFFB0, 0xC6FFD0, 0x7CFF8A}, sombre = {0x1E8A4A, 0x2FB868, 0x17703A};
        for (int i = 0; i < brins.length; i++) {
            double[] b = brins[i];
            Path2D p = new Path2D.Double();
            p.moveTo(b[0], b[1]);
            p.curveTo(b[2], b[3], b[4], b[5], b[6], b[7]);
            g.setStroke(new BasicStroke(10.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(c(0x06281A));
            g.draw(p);
            g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setPaint(new GradientPaint(0, 6, c(clair[i]), 0, 58, c(sombre[i])));
            g.draw(p);
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(a(0xFFFFFF, 150));
            g.draw(p);
        }
        // bulles
        for (double[] b : new double[][]{{9, 20, 3.5}, {54, 26, 2.6}, {26, 14, 2.2}, {44, 6, 3}}) {
            g.setColor(a(0xC6FFD0, 120));
            g.fill(new Ellipse2D.Double(b[0] - b[2], b[1] - b[2], b[2] * 2, b[2] * 2));
            g.setStroke(new BasicStroke(1.2f));
            g.setColor(c(0xFFFFFF));
            g.draw(new Ellipse2D.Double(b[0] - b[2], b[1] - b[2], b[2] * 2, b[2] * 2));
        }
        g.dispose();
        return img;
    }

    static BufferedImage or() {
        BufferedImage img = toile();
        Graphics2D g = g(img);
        halo(g, 32, 36, 30, 0xE0B864, 130);
        Shape pepite = poly(10, 40, 16, 24, 30, 14, 46, 18, 56, 32, 52, 48, 38, 57, 20, 54);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(26, 24), 40, new float[]{0f, 0.45f, 1f}, new Color[]{c(0xFFFBE0), c(0xE0B864), c(0x6B4A12)}));
        g.fill(pepite);
        g.setClip(pepite);
        g.setColor(a(0xFFFFFF, 150));
        g.fill(poly(16, 24, 30, 14, 34, 28, 22, 34));
        g.setColor(a(0x4A2F06, 120));
        g.fill(poly(34, 28, 56, 32, 52, 48, 38, 57, 36, 42));
        g.setColor(a(0xFFFFFF, 70));
        g.fill(poly(30, 14, 46, 18, 56, 32, 34, 28));
        g.setColor(a(0x4A2F06, 60));
        g.fill(poly(10, 40, 22, 34, 36, 42, 38, 57, 20, 54));
        g.setClip(null);
        contour(g, pepite, 0x2E1D05);
        etincelle(g, 24, 22, 8, 0xFFFFFF);
        etincelle(g, 50, 14, 4.5, 0xFFF0C2);
        etincelle(g, 46, 46, 3.5, 0xFFFFFF);
        g.dispose();
        return img;
    }

    static BufferedImage plume() {
        BufferedImage img = toile();
        Graphics2D g = g(img);
        halo(g, 34, 30, 30, 0x62CFF4, 80);
        Path2D p = new Path2D.Double();
        p.moveTo(12, 56);
        p.curveTo(8, 34, 24, 10, 54, 5);
        p.curveTo(58, 26, 44, 48, 12, 56);
        g.setPaint(new GradientPaint(12, 56, c(0xFF9BE8), 54, 5, c(0x62CFF4)));
        g.fill(p);
        g.setClip(p);
        // barbes
        g.setStroke(new BasicStroke(1.2f));
        for (int i = 0; i < 12; i++) {
            double t = i / 11.0, x = 14 + t * 36, y = 54 - t * 44;
            g.setColor(a(0xFFFFFF, 95));
            g.draw(new Line2D.Double(x, y, x - 12 + t * 4, y - 9));
            g.setColor(a(0x1B2A6B, 90));
            g.draw(new Line2D.Double(x, y, x + 13, y + 6 - t * 5));
        }
        g.setClip(null);
        contour(g, p, 0x1A1440);
        // rachis
        g.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c(0x1A1440));
        g.draw(new Line2D.Double(6, 61, 51, 8));
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c(0xFFFFFF));
        g.draw(new Line2D.Double(6, 61, 51, 8));
        etincelle(g, 16, 18, 5, 0xFFFFFF);
        etincelle(g, 52, 44, 4, 0xFF9BE8);
        g.dispose();
        return img;
    }

    // =====================================================================================================
    // 2. Modèles 3D (boîtes Minecraft) : un atlas par modèle, zones nommées en unités de 0 à 16
    // =====================================================================================================

    record Boite(double[] de, double[] a, double rotY, Map<String, double[]> faces) {
    }

    static final Map<String, List<Boite>> MODELES = new LinkedHashMap<>();
    static final Map<String, BufferedImage> ATLAS = new HashMap<>();

    static Boite boite(double x1, double y1, double z1, double x2, double y2, double z2, double rotY, double[] cotes, double[] haut, double[] bas) {
        Map<String, double[]> f = new LinkedHashMap<>();
        for (String s : new String[]{"north", "south", "east", "west"}) {
            f.put(s, cotes);
        }
        f.put("up", haut);
        f.put("down", bas);
        return new Boite(new double[]{x1, y1, z1}, new double[]{x2, y2, z2}, rotY, f);
    }

    static String n(double v) {
        String s = String.format(Locale.ROOT, "%.3f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }

    static void modele(String id, List<Boite> boites, BufferedImage atlas, String display) throws IOException {
        png("item/" + id, atlas);
        StringBuilder b = new StringBuilder("{\n  \"textures\": {\"t\": \"" + NS + ":item/" + id + "\", \"particle\": \"" + NS + ":item/" + id + "\"},\n  \"elements\": [\n");
        for (int i = 0; i < boites.size(); i++) {
            Boite e = boites.get(i);
            b.append("    {\"from\": [").append(n(e.de[0])).append(", ").append(n(e.de[1])).append(", ").append(n(e.de[2])).append("], \"to\": [")
                    .append(n(e.a[0])).append(", ").append(n(e.a[1])).append(", ").append(n(e.a[2])).append("]");
            if (e.rotY != 0) {
                b.append(", \"rotation\": {\"angle\": ").append(n(e.rotY)).append(", \"axis\": \"y\", \"origin\": [8, 8, 8]}");
            }
            b.append(", \"shade\": true, \"faces\": {");
            int k = 0;
            for (Map.Entry<String, double[]> f : e.faces.entrySet()) {
                double[] uv = f.getValue();
                b.append(k++ > 0 ? ", " : "").append('"').append(f.getKey()).append("\": {\"uv\": [").append(n(uv[0])).append(", ").append(n(uv[1]))
                        .append(", ").append(n(uv[2])).append(", ").append(n(uv[3])).append("], \"texture\": \"#t\"}");
            }
            b.append("}}").append(i < boites.size() - 1 ? "," : "").append('\n');
        }
        b.append("  ],\n  \"gui_light\": \"side\",\n  \"display\": ").append(display).append("\n}\n");
        write("assets/" + NS + "/models/item/" + id + ".json", b.toString());
        write("assets/" + NS + "/items/" + id + ".json", "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \"" + NS + ":item/" + id + "\"\n  }\n}\n");
        MODELES.put(id, boites);
        ATLAS.put(id, atlas);
    }

    static void modeles() throws IOException {
        communicateur();
        drone("drone_douane", 0x62CFF4, 0);
        drone("drone_sentinelle", 0xFF9BE8, 1);
        drone("drone_chasseur", 0xE0484A, 2);
        caisse();
        postes();
        voiture();
        skate();
        batte("batte", false);
        batte("batte_impulsion", true);
        agent();
    }

    /** Zone d'atlas (unités 0-16) -> rectangle en pixels pour dessiner dedans. */
    static Graphics2D zone(BufferedImage atlas, double[] uv) {
        double k = atlas.getWidth() / 16.0;
        Graphics2D g = g(atlas);
        g.translate(uv[0] * k, uv[1] * k);
        g.setClip(new Rectangle2D.Double(0, 0, (uv[2] - uv[0]) * k, (uv[3] - uv[1]) * k));
        return g;
    }

    static void metal(Graphics2D g, double w, double h, int clair, int sombre) {
        g.setPaint(new GradientPaint(0, 0, c(clair), 0, (float) h, c(sombre)));
        g.fill(new Rectangle2D.Double(0, 0, w, h));
        g.setColor(a(0xFFFFFF, 50));
        g.fill(new Rectangle2D.Double(0, 0, w, 1));
        g.setColor(a(0x000000, 90));
        g.fill(new Rectangle2D.Double(0, h - 1, w, 1));
    }

    static void communicateur() throws IOException {
        BufferedImage at = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        double[] face = {0, 0, 6, 11}, cote = {6, 0, 8, 11}, haut = {8, 0, 14, 2}, ant = {8, 2, 9, 6}, bout = {10, 2, 12, 4}, bouton = {12, 2, 14, 4};
        Graphics2D g = zone(at, face);   // 24 x 44 px
        metal(g, 24, 44, 0x3A4250, 0x171B26);
        g.setColor(c(0xE0B864));
        g.fill(new Rectangle2D.Double(0, 0, 24, 1.5));
        g.fill(new Rectangle2D.Double(0, 42.5, 24, 1.5));
        // écran
        g.setColor(c(0x05070D));
        g.fill(new Rectangle2D.Double(2, 4, 20, 26));
        g.setPaint(new GradientPaint(0, 5, c(0x0E3A52), 0, 29, c(0x071824)));
        g.fill(new Rectangle2D.Double(3, 5, 18, 24));
        g.setColor(c(0x62CFF4));
        g.fill(new Rectangle2D.Double(5, 7, 10, 2));
        g.setColor(c(0xE0B864));
        g.fill(new Rectangle2D.Double(5, 11, 14, 1.5));
        g.setColor(c(0x7CFF8A));
        g.fill(new Rectangle2D.Double(5, 14.5, 9, 1.5));
        g.setColor(c(0x62CFF4));
        g.fill(new Rectangle2D.Double(5, 18, 12, 1.5));
        // radar
        g.setStroke(new BasicStroke(1f));
        g.setColor(c(0x2D8AB0));
        g.draw(new Ellipse2D.Double(11, 20.5, 8, 8));
        g.setColor(c(0xE0484A));
        g.fill(new Rectangle2D.Double(16, 22, 2, 2));
        g.setColor(c(0xFFFFFF));
        g.fill(new Rectangle2D.Double(14.5, 24, 1.5, 1.5));
        // touches
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 2; j++) {
                g.setColor(c(j == 0 && i == 1 ? 0xE0B864 : 0x5A6270));
                g.fill(new Rectangle2D.Double(3.5 + i * 6.2, 33 + j * 4.5, 4.6, 3));
                g.setColor(a(0xFFFFFF, 90));
                g.fill(new Rectangle2D.Double(3.5 + i * 6.2, 33 + j * 4.5, 4.6, 0.8));
            }
        }
        g.dispose();
        g = zone(at, cote);
        metal(g, 8, 44, 0x2A303D, 0x10131B);
        g.setColor(c(0xE0B864));
        g.fill(new Rectangle2D.Double(3, 8, 2, 8));
        g.dispose();
        g = zone(at, haut);
        metal(g, 24, 8, 0x4A5263, 0x232836);
        g.dispose();
        g = zone(at, ant);
        metal(g, 4, 16, 0x9AA4B2, 0x3A4250);
        g.dispose();
        g = zone(at, bout);
        g.setColor(c(0xE0484A));
        g.fill(new Rectangle2D.Double(0, 0, 8, 8));
        g.setColor(c(0xFFB0B0));
        g.fill(new Rectangle2D.Double(1, 1, 3, 3));
        g.dispose();
        g = zone(at, bouton);
        g.setColor(c(0xE0B864));
        g.fill(new Rectangle2D.Double(0, 0, 8, 8));
        g.dispose();
        List<Boite> l = new ArrayList<>();
        l.add(boite(5, 0.5, 7, 11, 11.5, 9, 0, cote, haut, haut));
        l.get(0).faces.put("north", face);
        l.get(0).faces.put("south", face);
        l.add(boite(9.25, 11.5, 7.5, 10.25, 14.5, 8.5, 0, ant, ant, ant));
        l.add(boite(9, 14.5, 7.25, 10.5, 15.75, 8.75, 0, bout, bout, bout));
        l.add(boite(4.6, 6, 7.5, 5, 9, 8.5, 0, bouton, bouton, bouton));
        modele("communicateur", l, at, "{\n"
                + "    \"gui\": {\"rotation\": [12, 28, 0], \"translation\": [0, 0, 0], \"scale\": [1.25, 1.25, 1.25]},\n"
                + "    \"ground\": {\"rotation\": [0, 0, 0], \"translation\": [0, 3, 0], \"scale\": [0.6, 0.6, 0.6]},\n"
                + "    \"fixed\": {\"rotation\": [0, 180, 0], \"translation\": [0, 0, 0], \"scale\": [1.2, 1.2, 1.2]},\n"
                + "    \"thirdperson_righthand\": {\"rotation\": [0, 0, 0], \"translation\": [0, 3, 1], \"scale\": [0.6, 0.6, 0.6]},\n"
                + "    \"thirdperson_lefthand\": {\"rotation\": [0, 0, 0], \"translation\": [0, 3, 1], \"scale\": [0.6, 0.6, 0.6]},\n"
                + "    \"firstperson_righthand\": {\"rotation\": [0, -18, 4], \"translation\": [1.5, 3.4, 1], \"scale\": [0.8, 0.8, 0.8]},\n"
                + "    \"firstperson_lefthand\": {\"rotation\": [0, 18, -4], \"translation\": [1.5, 3.4, 1], \"scale\": [0.8, 0.8, 0.8]}\n  }");
    }

    /** Drone de la douane : coque, dôme, œil lumineux, deux bras en croix, quatre rotors. type 0 patrouille, 1 sentinelle, 2 chasseur. */
    static void drone(String id, int rgb, int type) throws IOException {
        BufferedImage at = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        double[] flanc = {0, 0, 6, 3}, dessus = {6, 0, 12, 6}, dessous = {0, 3, 6, 9}, dome = {12, 0, 16, 4}, oeil = {12, 4, 15, 7},
                bras = {0, 9, 14, 10}, moyeu = {0, 10, 1, 11}, pale = {1, 10, 6, 11}, aile = {6, 10, 9, 13}, lueur = {9, 10, 11, 12};
        int coqueC = type == 2 ? 0x6A4A52 : type == 1 ? 0x7A7690 : 0x66728A, coqueS = type == 2 ? 0x2A161C : 0x262E42;
        Graphics2D g = zone(at, flanc);    // 24 x 12
        metal(g, 24, 12, coqueC, coqueS);
        g.setColor(c(rgb));
        g.fill(new Rectangle2D.Double(0, 5, 24, 2.5));
        g.setColor(a(0xFFFFFF, 150));
        g.fill(new Rectangle2D.Double(0, 5, 24, 0.8));
        g.setColor(c(0x0B0D16));
        for (int i = 0; i < 4; i++) {
            g.fill(new Rectangle2D.Double(2 + i * 6, 9, 3, 1.5));
        }
        g.setColor(c(0xE0B864));
        g.fill(new Rectangle2D.Double(10.5, 1.5, 3, 2));
        g.dispose();
        g = zone(at, dessus);   // 24 x 24
        metal(g, 24, 24, coqueC, coqueS);
        g.setStroke(new BasicStroke(1f));
        g.setColor(a(0x000000, 130));
        g.draw(new Rectangle2D.Double(2.5, 2.5, 19, 19));
        g.setColor(c(rgb));
        for (double[] p : new double[][]{{1, 1}, {20, 1}, {1, 20}, {20, 20}}) {
            g.fill(new Rectangle2D.Double(p[0], p[1], 3, 3));
        }
        g.dispose();
        g = zone(at, dessous);
        metal(g, 24, 24, 0x232836, 0x0B0D16);
        g.setColor(c(rgb));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Ellipse2D.Double(4, 4, 16, 16));
        g.dispose();
        g = zone(at, dome);     // 16 x 16
        g.setPaint(new GradientPaint(0, 0, c(0x2B3049), 16, 16, c(0x05070D)));
        g.fill(new Rectangle2D.Double(0, 0, 16, 16));
        g.setColor(a(0xFFFFFF, 130));
        g.fill(new Rectangle2D.Double(2, 2, 5, 2));
        g.setColor(c(rgb));
        g.fill(new Rectangle2D.Double(11, 11, 3, 3));
        g.dispose();
        g = zone(at, oeil);     // 12 x 12
        g.setColor(c(0x05070D));
        g.fill(new Rectangle2D.Double(0, 0, 12, 12));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(6, 6), 6, new float[]{0f, 0.45f, 1f}, new Color[]{c(0xFFFFFF), c(rgb), a(rgb, 255).darker().darker()}));
        g.fill(new Ellipse2D.Double(1, 1, 10, 10));
        g.dispose();
        g = zone(at, bras);     // 56 x 4
        metal(g, 56, 4, 0x5A6270, 0x1B1F2B);
        g.setColor(c(rgb));
        g.fill(new Rectangle2D.Double(0, 1.2, 6, 1.6));
        g.fill(new Rectangle2D.Double(50, 1.2, 6, 1.6));
        g.dispose();
        g = zone(at, moyeu);
        g.setColor(c(0xE0B864));
        g.fill(new Rectangle2D.Double(0, 0, 4, 4));
        g.dispose();
        g = zone(at, pale);     // 20 x 4
        g.setPaint(new GradientPaint(0, 0, c(0xD7DCE2), 20, 0, c(0x9AA4B2)));
        g.fill(new Rectangle2D.Double(0, 0, 20, 4));
        g.setColor(c(rgb));
        g.fill(new Rectangle2D.Double(0, 0, 3, 4));
        g.fill(new Rectangle2D.Double(17, 0, 3, 4));
        g.dispose();
        g = zone(at, aile);     // 12 x 12
        metal(g, 12, 12, coqueC, coqueS);
        g.setColor(c(rgb));
        g.fill(poly(0, 12, 12, 0, 12, 4, 4, 12));
        g.dispose();
        g = zone(at, lueur);
        g.setColor(c(rgb));
        g.fill(new Rectangle2D.Double(0, 0, 8, 8));
        g.setColor(c(0xFFFFFF));
        g.fill(new Rectangle2D.Double(2, 2, 4, 4));
        g.dispose();

        List<Boite> l = new ArrayList<>();
        double d = type == 1 ? 1 : 0;   // la sentinelle est plus massive
        l.add(boite(5 - d, 6, 5 - d, 11 + d, 9, 11 + d, 0, flanc, dessus, dessous));
        l.add(boite(6 - d, 9, 6 - d, 10 + d, 10 + d, 10 + d, 0, dome, dome, dome));
        l.add(boite(6.5, 4.8, 6.5, 9.5, 6, 9.5, 0, oeil, oeil, oeil));
        l.add(boite(1, 7, 7.4, 15, 8, 8.6, 45, bras, bras, bras));
        l.add(boite(1, 7, 7.4, 15, 8, 8.6, -45, bras, bras, bras));
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                double x = 8 + sx * 4.6, z = 8 + sz * 4.6;
                l.add(boite(x - 0.5, 8, z - 0.5, x + 0.5, 9.2, z + 0.5, 0, moyeu, moyeu, moyeu));
                l.add(boite(x - 2.5, 9.2, z - 0.4, x + 2.5, 9.5, z + 0.4, 0, pale, pale, pale));
                Boite p2 = boite(x - 0.4, 9.2, z - 2.5, x + 0.4, 9.5, z + 2.5, 0, pale, pale, pale);
                l.add(p2);
            }
        }
        if (type == 2) {    // chasseur : deux ailerons et un dard
            l.add(boite(3.8, 6.5, 7.6, 5, 10.5, 8.4, 0, aile, aile, aile));
            l.add(boite(11, 6.5, 7.6, 12.2, 10.5, 8.4, 0, aile, aile, aile));
            l.add(boite(7.5, 6.6, 2.6, 8.5, 7.6, 5, 0, lueur, lueur, lueur));
        }
        if (type == 1) {    // sentinelle : antenne-phare
            l.add(boite(7.6, 11, 7.6, 8.4, 13, 8.4, 0, moyeu, moyeu, moyeu));
            l.add(boite(7.3, 13, 7.3, 8.7, 14.2, 8.7, 0, lueur, lueur, lueur));
        }
        modele(id, l, at, "{\n"
                + "    \"gui\": {\"rotation\": [28, 40, 0], \"translation\": [0, 0, 0], \"scale\": [1.05, 1.05, 1.05]},\n"
                + "    \"fixed\": {\"rotation\": [0, 0, 0], \"translation\": [0, 0, 0], \"scale\": [1, 1, 1]},\n"
                + "    \"head\": {\"rotation\": [0, 0, 0], \"translation\": [0, 0, 0], \"scale\": [1, 1, 1]}\n  }");
    }

    static void caisse() throws IOException {
        BufferedImage at = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        double[] flanc = {0, 0, 12, 10}, dessus = {0, 10, 6, 16}, montant = {12, 0, 13, 10}, bande = {6, 10, 16, 11}, fond = {6, 11, 10, 15};
        Graphics2D g = zone(at, flanc);    // 96 x 80
        g.setPaint(new GradientPaint(0, 0, c(0x2F3B57), 0, 80, c(0x141B2D)));
        g.fill(new Rectangle2D.Double(0, 0, 96, 80));
        g.setColor(a(0x000000, 90));
        for (int y = 16; y < 80; y += 16) {
            g.fill(new Rectangle2D.Double(0, y, 96, 2));
        }
        g.setColor(a(0xFFFFFF, 26));
        for (int y = 2; y < 80; y += 16) {
            g.fill(new Rectangle2D.Double(0, y, 96, 1.5));
        }
        // bandes de danger
        g.setClip(new Rectangle2D.Double(0, 60, 96, 12));
        g.setColor(c(0xE0B864));
        g.fill(new Rectangle2D.Double(0, 60, 96, 12));
        g.setColor(c(0x0B0D16));
        for (int x = -12; x < 100; x += 16) {
            g.fill(poly(x, 72, x + 8, 72, x + 20, 60, x + 12, 60));
        }
        g.setClip(new Rectangle2D.Double(0, 0, 96, 80));
        // emblème : orbite dorée
        g.setColor(c(0x0B0D16));
        g.fill(new RoundRectangle2D.Double(28, 10, 40, 40, 8, 8));
        g.setStroke(new BasicStroke(3f));
        g.setColor(c(0xE0B864));
        g.draw(new RoundRectangle2D.Double(28, 10, 40, 40, 8, 8));
        g.setColor(c(0x62CFF4));
        g.fill(new Ellipse2D.Double(41, 23, 14, 14));
        g.setStroke(new BasicStroke(2.6f));
        g.setColor(c(0xE0B864));
        g.draw(AffineTransform.getRotateInstance(Math.toRadians(-22), 48, 30).createTransformedShape(new Ellipse2D.Double(32, 24, 32, 12)));
        g.dispose();
        g = zone(at, dessus);   // 48 x 48
        g.setPaint(new GradientPaint(0, 0, c(0x3A4868), 48, 48, c(0x18203A)));
        g.fill(new Rectangle2D.Double(0, 0, 48, 48));
        g.setStroke(new BasicStroke(3f));
        g.setColor(c(0xE0B864));
        g.draw(new Rectangle2D.Double(6, 6, 36, 36));
        g.setColor(c(0x0B0D16));
        g.fill(new Rectangle2D.Double(18, 20, 12, 8));
        g.setColor(c(0x62CFF4));
        g.fill(new Rectangle2D.Double(20, 22, 8, 4));
        g.dispose();
        g = zone(at, montant);  // 8 x 80
        metal(g, 8, 80, 0x8A93A6, 0x2B3140);
        g.setColor(c(0x0B0D16));
        for (int y = 6; y < 80; y += 14) {
            g.fill(new Ellipse2D.Double(2.5, y, 3, 3));
        }
        g.dispose();
        g = zone(at, bande);    // 80 x 8
        g.setPaint(new GradientPaint(0, 0, c(0xFFF0C2), 0, 8, c(0x8A6A2A)));
        g.fill(new Rectangle2D.Double(0, 0, 80, 8));
        g.dispose();
        g = zone(at, fond);
        g.setColor(c(0x0E1320));
        g.fill(new Rectangle2D.Double(0, 0, 32, 32));
        g.dispose();
        List<Boite> l = new ArrayList<>();
        l.add(boite(2, 0, 2, 14, 10, 14, 0, flanc, dessus, fond));
        l.add(boite(1.5, 7.5, 1.5, 14.5, 8.5, 14.5, 0, bande, bande, bande));
        for (double[] p : new double[][]{{1.5, 1.5}, {13.5, 1.5}, {1.5, 13.5}, {13.5, 13.5}}) {
            l.add(boite(p[0], 0, p[1], p[0] + 1, 10.4, p[1] + 1, 0, montant, bande, bande));
        }
        l.add(boite(6.5, 10, 6.5, 9.5, 10.8, 9.5, 0, bande, bande, bande));
        modele("caisse", l, at, "{\n"
                + "    \"gui\": {\"rotation\": [30, 225, 0], \"translation\": [0, 1.5, 0], \"scale\": [0.9, 0.9, 0.9]},\n"
                + "    \"fixed\": {\"rotation\": [0, 0, 0], \"translation\": [0, 0, 0], \"scale\": [1, 1, 1]}\n  }");
    }

    /** Aperçu d'un modèle : projection isométrique avec tampon de profondeur (pour vérifier les formes sans lancer le jeu). */
    static BufferedImage rendu(String id, int taille) {
        BufferedImage out = new BufferedImage(taille, taille, BufferedImage.TYPE_INT_ARGB);
        double[][] zb = new double[taille][taille];
        for (double[] r : zb) {
            java.util.Arrays.fill(r, -1e9);
        }
        BufferedImage at = ATLAS.get(id);
        double[] mn = {1e9, 1e9, 1e9}, mx = {-1e9, -1e9, -1e9};
        for (Boite e : MODELES.get(id)) {
            for (int k = 0; k < 3; k++) {
                mn[k] = Math.min(mn[k], e.de[k]);
                mx[k] = Math.max(mx[k], e.a[k]);
            }
        }
        double[] mi = {(mn[0] + mx[0]) / 2 - 8, (mn[1] + mx[1]) / 2 - 8, (mn[2] + mx[2]) / 2 - 8};
        double portee = Math.max(22, 1.15 * (mx[1] - mn[1]) + 0.55 * Math.hypot(mx[0] - mn[0], mx[2] - mn[2]));
        double ech = taille / portee, ya = Math.toRadians(35), xa = Math.toRadians(28);
        String[] noms = {"north", "south", "east", "west", "up", "down"};
        double[] ombre = {0.72, 0.72, 0.86, 0.6, 1.0, 0.45};
        for (Boite e : MODELES.get(id)) {
            for (int f = 0; f < 6; f++) {
                double[] uv = e.faces.get(noms[f]);
                int pas = 150;
                for (int i = 0; i <= pas; i++) {
                    for (int j = 0; j <= pas; j++) {
                        double s = i / (double) pas, t = j / (double) pas, x, y, z;
                        switch (f) {
                            case 0 -> { x = e.a[0] + (e.de[0] - e.a[0]) * s; y = e.a[1] + (e.de[1] - e.a[1]) * t; z = e.de[2]; }
                            case 1 -> { x = e.de[0] + (e.a[0] - e.de[0]) * s; y = e.a[1] + (e.de[1] - e.a[1]) * t; z = e.a[2]; }
                            case 2 -> { z = e.a[2] + (e.de[2] - e.a[2]) * s; y = e.a[1] + (e.de[1] - e.a[1]) * t; x = e.a[0]; }
                            case 3 -> { z = e.de[2] + (e.a[2] - e.de[2]) * s; y = e.a[1] + (e.de[1] - e.a[1]) * t; x = e.de[0]; }
                            case 4 -> { x = e.de[0] + (e.a[0] - e.de[0]) * s; z = e.de[2] + (e.a[2] - e.de[2]) * t; y = e.a[1]; }
                            default -> { x = e.de[0] + (e.a[0] - e.de[0]) * s; z = e.a[2] + (e.de[2] - e.a[2]) * t; y = e.de[1]; }
                        }
                        double px = x - 8, py = y - 8, pz = z - 8;
                        if (e.rotY != 0) {
                            double r = Math.toRadians(e.rotY), cx = px * Math.cos(r) + pz * Math.sin(r), cz = -px * Math.sin(r) + pz * Math.cos(r);
                            px = cx;
                            pz = cz;
                        }
                        px -= mi[0];
                        py -= mi[1];
                        pz -= mi[2];
                        double rx = px * Math.cos(ya) + pz * Math.sin(ya), rz = -px * Math.sin(ya) + pz * Math.cos(ya);
                        double ry = py * Math.cos(xa) - rz * Math.sin(xa), depth = py * Math.sin(xa) + rz * Math.cos(xa);
                        int sx = (int) Math.round(taille / 2.0 + rx * ech), sy = (int) Math.round(taille / 2.0 - ry * ech);
                        if (sx < 0 || sy < 0 || sx >= taille || sy >= taille || depth < zb[sx][sy]) {
                            continue;
                        }
                        int tx = (int) Math.min(at.getWidth() - 1, (uv[0] + (uv[2] - uv[0]) * s) * at.getWidth() / 16.0);
                        int ty = (int) Math.min(at.getHeight() - 1, (uv[1] + (uv[3] - uv[1]) * t) * at.getHeight() / 16.0);
                        int col = at.getRGB(tx, ty);
                        if ((col >>> 24) < 20) {
                            continue;
                        }
                        zb[sx][sy] = depth;
                        double o = ombre[f];
                        out.setRGB(sx, sy, 0xFF000000 | ((int) (((col >> 16) & 255) * o) << 16) | ((int) (((col >> 8) & 255) * o) << 8) | (int) ((col & 255) * o));
                    }
                }
            }
        }
        return out;
    }

    // =====================================================================================================
    // 3. Menus au style « station » (primitives reprises d'OrbitGuiGen : sous-pixels, origine = coin du coffre)
    // =====================================================================================================

    static final int S = 2;
    static final int W = 196 * S, H = 248 * S, OX = 10 * S, OY = 24 * S;
    static BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);

    static final int OUT = 0xFF05050A, BLUE = 0xFF3A7FFF, BLUE_HI = 0xFF8DB8FF, BLUE_HI2 = 0xFFC4DAFF, BLUE_LO = 0xFF1C3F99, BLUE_LO2 = 0xFF122A6B;
    static final int GOLD = 0xFFE0B864, GOLD_HI = 0xFFFFF0C2, GOLD_LO = 0xFF8A6A2A, GOLD_LO2 = 0xFF4A3410, ICE = 0xFF62CFF4, VIOLET = 0xFFC79BFF;
    static final int[] METAL = {0xFF454D6E, 0xFF363D58, 0xFF2A2F45, 0xFF1F2333};
    static final int METAL_HI = 0xFF6B76A3, METAL_LO = 0xFF121420;
    static final int[] SCREEN = {0xFF181A27, 0xFF13141E, 0xFF0E0F17, 0xFF0B0B12};
    static final int SCREEN_IN_DK = 0xFF06070B, SCREEN_IN_HI = 0xFF2B3049;
    static final int[] T_OR = {0xFFFFFFFF, GOLD_HI, GOLD}, T_BLANC = {0xFFFFFFFF, 0xFFF0F5FF, 0xFFC9DBFF}, T_GLACE = {0xFFFFFFFF, 0xFFC8F1FF, ICE};

    static void px(int x, int y, int c) {
        x += OX;
        y += OY;
        if (x >= 0 && y >= 0 && x < W && y < H) {
            img.setRGB(x, y, c);
        }
    }

    static void rect(int x, int y, int w, int h, int c) {
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                px(x + i, y + j, c);
            }
        }
    }

    static final int[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};

    static double bayer(int x, int y) {
        return (BAYER[(y & 3)][(x & 3)] + 0.5) / 16.0;
    }

    static int pick(int[] cols, double t, int x, int y) {
        t = Math.max(0, Math.min(1, t));
        double p = t * (cols.length - 1);
        int i = (int) Math.floor(p);
        if (i >= cols.length - 1) {
            return cols[cols.length - 1];
        }
        double f = Math.max(0, Math.min(1, (p - i - 0.3) / 0.4));
        return f > bayer(x, y) ? cols[i + 1] : cols[i];
    }

    static boolean in(int i, int j, int w, int h, int ch) {
        return i >= 0 && j >= 0 && i < w && j < h && i + j >= ch && (w - 1 - i) + j >= ch && i + (h - 1 - j) >= ch && (w - 1 - i) + (h - 1 - j) >= ch;
    }

    static void shape(int x, int y, int w, int h, int ch, int[] grad, int outline, int hi, int lo) {
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                if (!in(i, j, w, h, ch)) {
                    continue;
                }
                int c;
                if (!in(i - 1, j, w, h, ch) || !in(i + 1, j, w, h, ch) || !in(i, j - 1, w, h, ch) || !in(i, j + 1, w, h, ch)) {
                    c = outline;
                } else if (!in(i, j - 2, w, h, ch) || !in(i - 2, j, w, h, ch)) {
                    c = hi;
                } else if (!in(i, j + 2, w, h, ch) || !in(i + 2, j, w, h, ch)) {
                    c = lo;
                } else {
                    c = pick(grad, (j - 2) / (double) Math.max(1, h - 5), x + i, y + j);
                }
                px(x + i, y + j, c);
            }
        }
    }

    static void rivet(int x, int y) {
        rect(x, y, 3, 3, OUT);
        px(x, y, 0xFFB7C1E6);
        px(x + 1, y, 0xFF8792BE);
        px(x, y + 1, 0xFF8792BE);
        px(x + 1, y + 1, 0xFF5D678F);
    }

    static void twinkle(int x, int y, int core, int mid, int far) {
        px(x, y, core);
        px(x - 1, y, mid);
        px(x + 1, y, mid);
        px(x, y - 1, mid);
        px(x, y + 1, mid);
        px(x - 2, y, far);
        px(x + 2, y, far);
        px(x, y - 2, far);
        px(x, y + 2, far);
    }

    static void panel(int x, int y, int w, int h, long seed) {
        shape(x, y, w, h, 6, METAL, OUT, METAL_HI, METAL_LO);
        int m = 8, ix = x + m, iy = y + m, iw = w - 2 * m, ih = h - 2 * m;
        shape(ix, iy, iw, ih, 4, SCREEN, OUT, SCREEN_IN_DK, SCREEN_IN_HI);
        for (int i = 3; i < iw - 3; i++) {
            for (int j = 3; j < ih - 3; j++) {
                int gx = ix + i, gy = iy + j;
                double nn = Math.sin(gx * 0.045 + seed) + Math.sin(gy * 0.06 + gx * 0.018) + Math.sin((gx + gy) * 0.027 + seed * 2);
                if (nn > 1.1 && (nn - 1.1) / 1.0 > bayer(gx, gy)) {
                    px(gx, gy, (gx / 40 + seed) % 2 == 0 ? 0xFF1B1836 : 0xFF141C38);
                }
            }
        }
        Random r = new Random(seed);
        for (int k = 0; k < (iw * ih) / 260; k++) {
            int sx = ix + 4 + r.nextInt(iw - 8), sy = iy + 4 + r.nextInt(ih - 8);
            int t = r.nextInt(20);
            if (t < 13) {
                px(sx, sy, 0xFF2E3454);
            } else if (t < 18) {
                px(sx, sy, 0xFF56628C);
            } else if (t < 19) {
                rect(sx, sy, 2, 2, 0xFF8E9CC8);
            } else {
                twinkle(sx, sy, 0xFFFFFFFF, 0xFF7FA6FF, 0xFF2C3A70);
            }
        }
        for (int bx = x + 24; bx < x + w - 24; bx += 48) {
            rivet(bx, y + 3);
            rivet(bx, y + h - 6);
        }
        for (int by = y + 24; by < y + h - 24; by += 48) {
            rivet(x + 3, by);
            rivet(x + w - 6, by);
        }
        rivet(x + 3, y + 3);
        rivet(x + w - 6, y + 3);
        rivet(x + 3, y + h - 6);
        rivet(x + w - 6, y + h - 6);
    }

    static void slot(int gx, int gy) {
        int x = gx * S - 2, y = gy * S - 2, s = 36;
        rect(x, y, s, s, OUT);
        rect(x + 1, y + 1, s - 2, s - 2, 0xFF33395A);
        rect(x + 1, y + 1, s - 3, s - 3, 0xFF030306);
        for (int j = 0; j < 32; j++) {
            for (int i = 0; i < 32; i++) {
                px(x + 2 + i, y + 2 + j, pick(new int[]{0xFF0A0B12, 0xFF0E1019, 0xFF121521}, j / 31.0, x + 2 + i, y + 2 + j));
            }
        }
        rect(x + 2, y + 2, 32, 1, 0xFF07080D);
        px(x + 2, y + 2, 0xFF050509);
    }

    static int sx(int c) {
        return 8 + 18 * c;
    }

    static int sy(int r) {
        return 18 + 18 * r;
    }

    /** Case du coffre (0 à 53). */
    static void puits(int... cases) {
        for (int k : cases) {
            slot(sx(k % 9), sy(k / 9));
        }
    }

    /** Case mise en valeur : coins lumineux. */
    static void puitsAccent(int k, int col) {
        puits(k);
        int x = sx(k % 9) * S - 4, y = sy(k / 9) * S - 4, w = 40;
        for (int[] p : new int[][]{{x, y, 1, 1}, {x + w, y, -1, 1}, {x, y + w, 1, -1}, {x + w, y + w, -1, -1}}) {
            rect(Math.min(p[0], p[0] + p[2] * 6), Math.min(p[1], p[1] + p[3] * 2), 6, 2, col);
            rect(Math.min(p[0], p[0] + p[2] * 2), Math.min(p[1], p[1] + p[3] * 6), 2, 6, col);
        }
    }

    // ---------- Police bitmap 5x7 ----------
    static final Map<Character, String[]> F = new HashMap<>();

    static void gl(char c, String... rows) {
        F.put(c, rows);
    }

    static {
        gl('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        gl('À', ".#...", "..#..", ".###.", "#...#", "#####", "#...#", "#...#");
        gl('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
        gl('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
        gl('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
        gl('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
        gl('É', "..##.", "#####", "#....", "####.", "#....", "#....", "#####");
        gl('È', ".##..", "#####", "#....", "####.", "#....", "#....", "#####");
        gl('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....");
        gl('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####");
        gl('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        gl('I', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####");
        gl('J', "..###", "...#.", "...#.", "...#.", "#..#.", "#..#.", ".##..");
        gl('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#");
        gl('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
        gl('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#");
        gl('N', "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#");
        gl('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        gl('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
        gl('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#");
        gl('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#");
        gl('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.");
        gl('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        gl('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        gl('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..");
        gl('W', "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#");
        gl('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#");
        gl('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..");
        gl('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####");
        gl('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.");
        gl('1', "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###.");
        gl('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####");
        gl('3', "####.", "....#", "....#", ".###.", "....#", "....#", "####.");
        gl('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.");
        gl('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.");
        gl('6', ".###.", "#....", "#....", "####.", "#...#", "#...#", ".###.");
        gl('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...");
        gl('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.");
        gl('9', ".###.", "#...#", "#...#", ".####", "....#", "....#", ".###.");
        gl('+', ".....", "..#..", "..#..", "#####", "..#..", "..#..", ".....");
        gl('-', ".....", ".....", ".....", "#####", ".....", ".....", ".....");
        gl(':', ".....", "..#..", "..#..", ".....", "..#..", "..#..", ".....");
        gl('.', ".....", ".....", ".....", ".....", ".....", "..#..", "..#..");
        gl('!', "..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#..");
        gl('\'', "..#..", "..#..", ".#...", ".....", ".....", ".....", ".....");
        gl('>', "#....", ".#...", "..#..", "...#.", "..#..", ".#...", "#....");
        gl(' ', ".....", ".....", ".....", ".....", ".....", ".....", ".....");
    }

    static int textW(String s, int sc) {
        return s.length() * 6 * sc - sc;
    }

    static void text(int x, int y, String s, int sc, int[] grad, int outline, int shadow, int sd) {
        int adv = 6 * sc, tw = textW(s, sc), th = 7 * sc;
        boolean[][] m = new boolean[tw][th];
        int cx = 0;
        for (char ch : s.toCharArray()) {
            String[] g = F.get(ch);
            if (g == null) {
                throw new IllegalArgumentException("lettre absente de la police : " + ch);
            }
            for (int r = 0; r < 7; r++) {
                for (int k = 0; k < 5; k++) {
                    if (g[r].charAt(k) == '#') {
                        for (int aa = 0; aa < sc; aa++) {
                            for (int b = 0; b < sc; b++) {
                                m[cx + k * sc + aa][r * sc + b] = true;
                            }
                        }
                    }
                }
            }
            cx += adv;
        }
        boolean[][] d = new boolean[tw + 2][th + 2];
        for (int i = 0; i < tw; i++) {
            for (int j = 0; j < th; j++) {
                if (m[i][j]) {
                    for (int aa = 0; aa < 3; aa++) {
                        for (int b = 0; b < 3; b++) {
                            d[i + aa][j + b] = true;
                        }
                    }
                }
            }
        }
        for (int i = 0; i < tw + 2; i++) {
            for (int j = 0; j < th + 2; j++) {
                if (d[i][j] && shadow != 0) {
                    px(x - 1 + i, y - 1 + j + sd, shadow);
                }
            }
        }
        for (int i = 0; i < tw + 2; i++) {
            for (int j = 0; j < th + 2; j++) {
                if (d[i][j]) {
                    px(x - 1 + i, y - 1 + j, outline);
                }
            }
        }
        for (int i = 0; i < tw; i++) {
            for (int j = 0; j < th; j++) {
                if (m[i][j]) {
                    px(x + i, y + j, pick(grad, j / (double) (th - 1), x + i, y + j));
                }
            }
        }
    }

    static void textC(int cx, int y, String s, int sc, int[] grad, int outline, int shadow, int sd) {
        text(cx - textW(s, sc) / 2, y, s, sc, grad, outline, shadow, sd);
    }

    static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static void antenna(int x) {
        rect(x - 1, -30, 6, 32, OUT);
        rect(x, -29, 2, 30, METAL_HI);
        rect(x + 2, -29, 2, 30, METAL[2]);
        for (int y = -24; y < 0; y += 6) {
            rect(x - 1, y, 6, 1, OUT);
        }
        rect(x - 3, -2, 10, 5, OUT);
        rect(x - 2, -1, 8, 3, METAL[1]);
        rect(x - 2, -1, 8, 1, METAL_HI);
        for (int i = -6; i <= 9; i++) {
            for (int j = -6; j <= 6; j++) {
                double d = Math.sqrt((i - 1.5) * (i - 1.5) + j * j);
                if (d < 6 && d > 3 && (6 - d) / 3 * 0.6 > bayer(x + i, j - 34)) {
                    px(x + i, j - 34, 0xFF8A6A2A);
                }
            }
        }
        rect(x - 1, -37, 6, 6, OUT);
        rect(x, -36, 4, 4, GOLD);
        rect(x, -36, 2, 2, 0xFFFFFFFF);
    }

    /** Cadran rond (anneau métal + fond nuit) avec une étoile d'or : emblème de la contrebande. */
    static void embleme(int cx, int cy, int r) {
        int[] ringPal = {METAL[3], METAL[2], METAL[1], METAL[0], METAL_HI};
        int[] dial = {0xFF0B142D, 0xFF0F1C3D, 0xFF15264F, 0xFF1C3166};
        for (int x = -r - 1; x <= r + 1; x++) {
            for (int y = -r - 1; y <= r + 1; y++) {
                double d = Math.sqrt(x * x + y * y);
                if (d > r + 0.5) {
                    continue;
                }
                int col;
                if (d > r - 0.7) {
                    col = OUT;
                } else if (d > r - 4.5) {
                    col = pick(ringPal, 0.5 - 0.5 * (x + y) / (r * 1.2), cx + x, cy + y);
                } else if (d > r - 5.5) {
                    col = OUT;
                } else {
                    col = pick(dial, 0.5 - 0.5 * (x + y) / (r * 1.1), cx + x, cy + y);
                }
                px(cx + x, cy + y, col);
            }
        }
        // losange d'or (éclat de quartz)
        for (int x = -8; x <= 8; x++) {
            for (int y = -10; y <= 10; y++) {
                double v = Math.abs(x) / 7.0 + Math.abs(y) / 10.0;
                if (v <= 1.0) {
                    px(cx + x, cy + y, v > 0.82 ? OUT : x + y < -3 ? GOLD_HI : x - y > 4 ? GOLD_LO : GOLD);
                }
            }
        }
    }

    /** Base commune : inventaire du joueur, panneau principal, plaque « Inventaire », antennes, bannière dorée du titre. */
    static void frame(String title) {
        img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        panel(-8, 266, 368, 178, 7);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                slot(sx(c), 140 + 18 * r);
            }
        }
        for (int c = 0; c < 9; c++) {
            slot(sx(c), 198);
        }
        rect(14, 390, 324, 1, 0xFF04050A);
        rect(14, 391, 324, 1, 0xFF2B3049);
        panel(-8, 0, 368, 266, 3);
        for (int i = 4; i < 348; i++) {
            double t = 1 - Math.abs(i - 176) / 172.0;
            px(i, 252, pick(new int[]{0xFF1A1408, GOLD_LO2, GOLD_LO, GOLD}, t, i, 252));
            px(i, 253, pick(new int[]{0xFF120E06, 0xFF2E210A, GOLD_LO2, GOLD_LO}, t, i, 253));
        }
        // plaque d'acier sous le libellé « Inventaire » du jeu : même gris (#404040) que le texte, qui disparaît
        shape(2, 253, 166, 21, 3, new int[]{0xFF404040}, OUT, 0xFF5A5A63, 0xFF2B2B31);
        rivet(6, 262);
        rivet(161, 262);
        for (int bx : new int[]{196, 300}) {
            rect(bx, 260, 12, 12, OUT);
            rect(bx + 1, 261, 10, 10, METAL[1]);
            rect(bx + 1, 261, 10, 1, METAL_HI);
            rivet(bx + 4, 264);
        }
        antenna(0);
        antenna(348);
        int bw = Math.max(248, textW(title, 4) + 70), bx = 176 - bw / 2, by = -34, bh = 42, cy = by + bh / 2;
        shape(bx - 14, cy - 9, bw + 28, 18, 4, METAL, OUT, METAL_HI, METAL_LO);
        shape(bx, by, bw, bh, 6, new int[]{0xFFFFE7A6, 0xFFF0C96E, GOLD, 0xFFC09A48, GOLD_LO}, OUT, GOLD_HI, GOLD_LO2);
        rect(bx + 6, by + 4, bw - 12, 1, 0xFFFFF8DD);
        textC(176, cy - 14, title, 4, new int[]{0xFFFFFFFF, 0xFFFFFBEA, GOLD_HI}, GOLD_LO2, 0xFF2E210A, 3);
        embleme(bx + 12, cy, 19);
        embleme(bx + bw - 13, cy, 19);
    }

    /** Plaque d'information sur une rangée, de la colonne c0 à c1 : écran creusé et texte centré. */
    static void plaque(int r, int c0, int c1, String s, int[] grad) {
        int x = sx(c0) * S - 2, y = sy(r) * S + 3, w = (c1 - c0) * 36 + 36, h = 26;
        shape(x, y, w, h, 3, METAL, OUT, METAL_HI, METAL_LO);
        shape(x + 4, y + 4, w - 8, h - 8, 2, new int[]{0xFF0B142D, 0xFF0F1C3D}, OUT, 0xFF060A18, 0xFF2A4A90);
        if (s != null && !s.isEmpty()) {
            int sc = textW(s, 2) <= w - 12 ? 2 : 1;
            textC(x + w / 2, y + (h - 7 * sc) / 2, s, sc, grad, OUT, 0, 0);
        }
        rivet(x + 1, y + 1);
        rivet(x + w - 4, y + 1);
    }

    /** Bouton peint qui couvre des cases (la zone cliquable est posée par le plugin avec l'objet invisible). */
    static void bouton(int r0, int c0, int r1, int c1, int light, int fill, int dark, int darker, String label, String sous) {
        int x = sx(c0) * S - 2, y = sy(r0) * S - 2, w = (c1 - c0) * 36 + 36, h = (r1 - r0) * 36 + 36, lip = 4;
        shape(x, y, w, h, 4, new int[]{darker}, OUT, darker, darker);
        shape(x, y, w, h - lip, 4, new int[]{light, fill, fill, dark}, OUT, mix(light, 0xFFFFFFFF, 0.5), dark);
        for (int i = 0; i < 4; i++) {
            px(x + 6 + i, y + 5, 0xFFFFFFFF);
            px(x + 5, y + 6 + i, 0xFFFFFFFF);
        }
        int sc = h > 60 ? 4 : 2;
        while (sc > 1 && textW(label, sc) > w - 12) {
            sc--;
        }
        int ty = y + (h - lip - 7 * sc) / 2 - (sous == null ? 0 : 9);
        textC(x + w / 2, ty, label, sc, new int[]{0xFFFFFFFF, mix(light, 0xFFFFFFFF, 0.7)}, darker, darker, sc > 2 ? 2 : 1);
        if (sous != null) {
            textC(x + w / 2, ty + 7 * sc + 8, sous, 1, new int[]{0xFFFFFFFF}, darker, 0, 0);
        }
    }

    /** Horizon de la ville la nuit : immeubles, fenêtres allumées, néons, drone. Sur une rangée entière. */
    static void horizon(int r) {
        int x = sx(0) * S - 2, y = sy(r) * S - 2, w = 9 * 36, h = 36;
        shape(x, y, w, h, 3, new int[]{0xFF0A1024, 0xFF101A3A, 0xFF1A2550, 0xFF2A2F6A}, OUT, 0xFF060A18, 0xFF2A4A90);
        Random rnd = new Random(26);
        for (int k = 0; k < 40; k++) {
            px(x + 4 + rnd.nextInt(w - 8), y + 3 + rnd.nextInt(14), rnd.nextInt(4) == 0 ? 0xFFFFFFFF : 0xFF56628C);
        }
        // lune
        for (int i = -6; i <= 6; i++) {
            for (int j = -6; j <= 6; j++) {
                if (i * i + j * j <= 36 && (i - 3) * (i - 3) + (j + 2) * (j + 2) > 30) {
                    px(x + w - 30 + i, y + 11 + j, GOLD_HI);
                }
            }
        }
        int bx = x + 3;
        int[] neons = {ICE, GOLD, 0xFFFF9BE8, VIOLET, 0xFF7CFF8A};
        while (bx < x + w - 6) {
            int bw = 10 + rnd.nextInt(14), bh = 10 + rnd.nextInt(20);
            bw = Math.min(bw, x + w - 3 - bx);
            int col = rnd.nextBoolean() ? 0xFF0B0E1C : 0xFF10152A;
            rect(bx, y + h - 2 - bh, bw, bh, col);
            rect(bx, y + h - 2 - bh, bw, 1, neons[rnd.nextInt(neons.length)]);
            for (int wx = bx + 2; wx < bx + bw - 2; wx += 3) {
                for (int wy = y + h - bh + 1; wy < y + h - 4; wy += 4) {
                    if (rnd.nextInt(3) == 0) {
                        rect(wx, wy, 2, 2, rnd.nextInt(5) == 0 ? ICE : 0xFFFFD86B);
                    }
                }
            }
            bx += bw + 1;
        }
        // drone et son faisceau
        int dx = x + 96, dy = y + 7;
        rect(dx - 5, dy, 10, 3, 0xFF9AA4B2);
        rect(dx - 8, dy - 1, 5, 1, 0xFFD7DCE2);
        rect(dx + 3, dy - 1, 5, 1, 0xFFD7DCE2);
        rect(dx - 1, dy + 3, 2, 2, 0xFFE0484A);
        for (int j = 0; j < 14; j++) {
            for (int i = -j / 2; i <= j / 2; i++) {
                if (bayer(dx + i, dy + 5 + j) > 0.55) {
                    px(dx + i, dy + 5 + j, 0xFF8A3A46);
                }
            }
        }
    }

    /** Baie de serveurs décorative sur des cases vides. */
    static void baie(int r0, int c0, int r1, int c1, long seed) {
        int x = sx(c0) * S - 2, y = sy(r0) * S - 2, w = (c1 - c0) * 36 + 36, h = (r1 - r0) * 36 + 36;
        shape(x + 2, y + 2, w - 4, h - 4, 3, METAL, OUT, METAL_HI, METAL_LO);
        Random rnd = new Random(seed);
        for (int j = y + 8; j < y + h - 10; j += 8) {
            rect(x + 7, j, w - 14, 5, 0xFF0A0B12);
            for (int i = x + 9; i < x + w - 10; i += 4) {
                int t = rnd.nextInt(10);
                rect(i, j + 1, 2, 2, t < 3 ? 0xFF7CFF8A : t < 5 ? GOLD : t < 6 ? 0xFFE0484A : 0xFF1F2333);
            }
        }
    }

    /** Flèches vers le bas (le résultat est en dessous). */
    static void fleches(int r, int c0, int c1) {
        for (int c = c0; c <= c1; c++) {
            int cx = sx(c) * S + 16, y = sy(r) * S + 8;
            for (int j = 0; j < 8; j++) {
                rect(cx - (7 - j), y + j * 2, (7 - j) * 2, 2, pick(new int[]{GOLD_LO, GOLD, GOLD_HI}, j / 7.0, cx, y + j));
            }
        }
    }

    // ---------- Les six menus (mêmes cases que Menus.java) ----------

    static final Map<String, Integer> BASES = new LinkedHashMap<>();
    static final Map<String, Object[][]> APERCU = new HashMap<>();
    static final List<String> ECRITS = new ArrayList<>();

    static void menus() throws IOException {
        // accueil de la borne
        frame("CONTREBANDE");
        puitsAccent(4, GOLD);
        horizon(1);
        bouton(2, 0, 4, 3, 0xFFFFE7A6, GOLD, 0xFFC09A48, GOLD_LO2, "JOUER", "NUIT DE 10 MIN");
        bouton(2, 5, 2, 8, 0xFF8FE3FF, 0xFF2FA9D6, 0xFF1C7FA6, 0xFF0A3A50, "CLASSEMENT", null);
        bouton(3, 5, 3, 8, 0xFFA9B4D6, 0xFF6B76A3, 0xFF454D6E, 0xFF1F2333, "RÈGLES", null);
        bouton(4, 5, 4, 8, 0xFFE0C8FF, 0xFF9A63E8, 0xFF6B3FC0, 0xFF2A1560, "MON ORGA", null);
        plaque(5, 0, 8, "TOUT EST SAUVEGARDÉ", T_OR);
        plaque(0, 0, 3, "TON ORGA", T_GLACE);
        plaque(0, 5, 8, "EMPIRE", T_GLACE);
        menu("accueil", 0xE300, new Object[][]{{4, "caisse"}});

        // Rix, le fournisseur
        frame("RIX");
        plaque(0, 0, 7, "FOURNITURES", T_OR);
        puitsAccent(8, GOLD);
        puits(10, 12, 14, 16);
        plaque(2, 0, 8, "ADDITIFS", T_GLACE);
        puits(28, 30, 32, 34, 37, 39, 41, 43);
        plaque(5, 0, 8, "CLIC: 1   DROIT: 8   MAJ: 32", T_BLANC);
        menu("rix", 0xE310, new Object[][]{{10, "germe"}, {12, "minerai"}, {28, "add_lune"}, {30, "add_comete"}, {32, "add_fer"}, {34, "add_soufre"},
                {37, "add_echo"}, {39, "add_algue"}, {41, "add_or"}, {43, "add_plume"}});

        // terminal des améliorations
        frame("TERMINAL");
        plaque(0, 0, 7, "AMÉLIORATIONS", T_OR);
        puitsAccent(8, GOLD);
        baie(2, 0, 2, 1, 11);
        baie(2, 7, 2, 8, 12);
        puits(10, 11, 12, 13, 14, 15, 16, 20, 21, 22, 23, 24);
        plaque(3, 0, 8, "ACQUIS POUR TOUJOURS", T_GLACE);
        plaque(4, 0, 3, "REGISTRE >", T_BLANC);
        puitsAccent(40, ICE);
        baie(4, 5, 4, 8, 13);
        plaque(5, 0, 8, "PARTAGÉ AVEC TON ORGA", T_OR);
        menu("terminal", 0xE320, new Object[][]{{10, "produit_quartz"}, {12, "produit_plasma"}, {14, "produit_cristal"}, {16, "drone_douane"}, {21, "batte"}});

        // coffre de l'organisation
        frame("COFFRE");
        for (int k = 0; k < 45; k++) {
            puits(k);
        }
        puits(45, 53);
        puitsAccent(47, 0xFF7CFF8A);
        puitsAccent(49, VIOLET);
        puitsAccent(51, GOLD);
        menu("coffre", 0xE330, new Object[][]{{0, "germe"}, {1, "minerai"}, {2, "add_lune"}, {3, "add_or"}, {4, "produit_cristal"}, {5, "produit_plasma"},
                {6, "produit_quartz"}, {49, "caisse"}});

        // table de mélange
        frame("MÉLANGE");
        plaque(0, 0, 3, "MARCHANDISE", T_OR);
        puits(4);
        plaque(0, 5, 8, "DE TON SAC", T_OR);
        for (int k = 9; k < 27; k++) {
            puits(k);
        }
        plaque(3, 0, 0, "+", T_GLACE);
        for (int k = 28; k < 36; k++) {
            puits(k);
        }
        fleches(4, 3, 5);
        plaque(4, 0, 2, "ADDITIF", T_GLACE);
        plaque(4, 6, 8, "RÉSULTAT", T_OR);
        puits(46, 50, 52);
        puitsAccent(48, GOLD);
        menu("melange", 0xE340, new Object[][]{{9, "produit_quartz"}, {10, "produit_plasma"}, {28, "add_lune"}, {29, "add_comete"}, {30, "add_fer"},
                {31, "add_soufre"}, {32, "add_echo"}, {33, "add_algue"}, {34, "add_or"}, {35, "add_plume"}, {48, "produit_cristal"}});

        // carnet de recettes
        frame("CARNET");
        for (int k = 0; k < 45; k++) {
            puits(k);
        }
        puits(45, 53);
        puitsAccent(49, ICE);
        plaque(5, 1, 3, "RECETTES", T_OR);
        plaque(5, 5, 7, "CONNUES", T_OR);
        menu("carnet", 0xE350, new Object[][]{{0, "produit_cristal"}, {1, "produit_plasma"}, {2, "produit_quartz"}});

        police();
    }

    static void menu(String nom, int base, Object[][] apercu) throws IOException {
        BASES.put(nom, base);
        ECRITS.add(nom);
        // version du pack : un pixel presque transparent au bord droit de chaque tuile, pour une avance fixe de 99
        BufferedImage t = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        t.setData(img.getData());
        int tw = W / 2, th = H / 4;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 2; col++) {
                int x = col * tw + tw - 1, y = row * th;
                if ((t.getRGB(x, y) >>> 24) == 0) {
                    t.setRGB(x, y, 0x01000000);
                }
            }
        }
        png("gui/" + nom, t);
        // aperçu : fond sombre, menu x2, icônes réelles dans quelques cases, libellé « Inventaire » simulé
        int P = 2;
        BufferedImage out = new BufferedImage(W * P + 80, H * P + 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setColor(c(0x141A26));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(img, 40, 30, W * P, H * P, null);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (Object[] it : apercu) {
            int k = (Integer) it[0];
            String id = (String) it[1];
            BufferedImage ic = ICONES.containsKey(id) ? ICONES.get(id) : rendu(id.equals("caisse") ? "caisse" : id, 64);
            g.drawImage(ic, 40 + (OX + sx(k % 9) * S) * P, 30 + (OY + sy(k / 9) * S) * P, 64, 64, null);
        }
        g.dispose();
        ImageIO.write(out, "png", new File(apercus, "contrebande_" + nom + "_apercu.png"));
    }

    static String u(int cp) {
        return String.format("\\u%04x", cp);
    }

    /** assets/contrebande/font/gui.json : espaces négatifs + 4 fournisseurs bitmap (un par rangée de tuiles) par menu. */
    static void police() throws IOException {
        StringBuilder b = new StringBuilder("{\n  \"providers\": [\n");
        b.append("    {\"type\": \"space\", \"advances\": {\"").append(u(0xF000)).append("\": -18, \"").append(u(0xF001)).append("\": -1, \"")
                .append(u(0xF002)).append("\": -197}}");
        for (String nom : ECRITS) {
            int base = BASES.get(nom);
            for (int row = 0; row < 4; row++) {
                b.append(",\n    {\"type\": \"bitmap\", \"file\": \"").append(NS).append(":gui/").append(nom).append(".png\", \"height\": 62, \"ascent\": ")
                        .append(37 - 62 * row).append(", \"chars\": [");
                for (int k = 0; k < 4; k++) {
                    b.append(k == 0 ? "\"" : ", \"");
                    b.append(k == row ? u(base + row * 2) + u(base + row * 2 + 1) : u(0) + u(0)).append('"');
                }
                b.append("]}");
            }
        }
        b.append("\n  ]\n}\n");
        write("assets/" + NS + "/font/gui.json", b.toString());
    }

    // =====================================================================================================
    // Planche d'aperçu : toutes les icônes et les modèles 3D
    // =====================================================================================================

    static void planche() throws IOException {
        int cols = 7, cell = 150, lignes = (ICONES.size() + cols - 1) / cols;
        BufferedImage out = new BufferedImage(cols * cell + 40, lignes * cell + 30 + 300 * ((MODELES.size() + 1 + 4) / 5), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(out);
        g.setColor(c(0x141A26));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 13));
        int i = 0;
        for (Map.Entry<String, BufferedImage> e : ICONES.entrySet()) {
            int x = 20 + (i % cols) * cell, y = 20 + (i / cols) * cell;
            g.setColor(c(0x0E1019));
            g.fillRoundRect(x + 6, y, 132, 132, 10, 10);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(e.getValue(), x + 24, y + 4, 96, 96, null);
            g.drawImage(e.getValue(), x + 104, y + 96, 32, 32, null);   // taille réelle dans un menu (échelle 2)
            g.setColor(c(0x9AA4B2));
            g.drawString(e.getKey(), x + 12, y + 122);
            i++;
        }
        int y = 20 + lignes * cell, x = 20;
        for (String id : MODELES.keySet()) {
            g.setColor(c(0x0E1019));
            g.fillRoundRect(x, y, 200, 290, 10, 10);
            g.drawImage(rendu(id, 200), x, y, null);
            g.drawImage(ATLAS.get(id), x + 68, y + 205, 64, 64, null);
            g.setColor(c(0x9AA4B2));
            g.drawString(id, x + 10, y + 284);
            x += 210;
            if (x + 200 > out.getWidth()) {
                x = 20;
                y += 300;
            }
        }
        g.setColor(c(0x0E1019));
        g.fillRoundRect(x, y, 200, 290, 10, 10);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(AGENT, x + 40, y + 16, 120, 240, null);
        g.setColor(c(0x9AA4B2));
        g.drawString("agent de la douane", x + 10, y + 284);
        g.dispose();
        ImageIO.write(out, "png", new File(apercus, "contrebande_objets_apercu.png"));
        // teintes de la marchandise : une ligne par base, une colonne par propriété
        int[] teintes = {0xFFE680, 0x9FE7FF, 0xB0B8C8, 0xFF8A3D, 0xC79BFF, 0x7CFF8A, 0xE0B864, 0x62CFF4, 0xE0484A, 0xFF9BE8, 0x5AE0FF, 0xFFF35A,
                0xC8D8FF, 0xB98CFF, 0xFFB347, 0xFFFFFF};
        BufferedImage t = new BufferedImage(teintes.length * 70 + 20, 3 * 70 + 20, BufferedImage.TYPE_INT_ARGB);
        g = t.createGraphics();
        g.setColor(c(0x0E1019));
        g.fillRect(0, 0, t.getWidth(), t.getHeight());
        String[] bases = {"produit_quartz", "produit_plasma", "produit_cristal"};
        for (int b = 0; b < 3; b++) {
            for (int k = 0; k < teintes.length; k++) {
                try {
                    File dir = new File(pack, "assets/" + NS + "/textures/item");
                    g.drawImage(ImageIO.read(new File(dir, bases[b] + "_0.png")), 10 + k * 70, 10 + b * 70, null);
                    g.drawImage(teinter(ImageIO.read(new File(dir, bases[b] + "_1.png")), teintes[k]), 10 + k * 70, 10 + b * 70, null);
                    g.drawImage(ImageIO.read(new File(dir, bases[b] + "_2.png")), 10 + k * 70, 10 + b * 70, null);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        g.dispose();
        ImageIO.write(t, "png", new File(apercus, "contrebande_teintes_apercu.png"));
    }

    // =====================================================================================================
    // 2 bis. Postes de la planque, voiture de la douane, skate, uniforme des agents
    // =====================================================================================================

    /** Les postes enveloppent le bloc vanilla qu'ils habillent (un peu plus larges que 16) : il garde le clic et sert sans le pack. */
    static final String POSE_POSTE = "{\n"
            + "    \"gui\": {\"rotation\": [28, 40, 0], \"translation\": [0, -1.5, 0], \"scale\": [0.55, 0.55, 0.55]},\n"
            + "    \"fixed\": {\"rotation\": [0, 0, 0], \"translation\": [0, 0, 0], \"scale\": [1, 1, 1]}\n  }";

    /** Boîte dont les faces nord/sud (les deux « devants ») et est/ouest (les flancs) n'ont pas la même texture. */
    static Boite boite2(double x1, double y1, double z1, double x2, double y2, double z2, double[] ns, double[] eo, double[] haut, double[] bas) {
        Boite b = boite(x1, y1, z1, x2, y2, z2, 0, eo, haut, bas);
        b.faces().put("north", ns);
        b.faces().put("south", ns);
        return b;
    }

    static Boite boite6(double x1, double y1, double z1, double x2, double y2, double z2, double[] nord, double[] sud, double[] est,
                        double[] ouest, double[] haut, double[] bas) {
        Map<String, double[]> f = new LinkedHashMap<>();
        f.put("north", nord);
        f.put("south", sud);
        f.put("east", est);
        f.put("west", ouest);
        f.put("up", haut);
        f.put("down", bas);
        return new Boite(new double[]{x1, y1, z1}, new double[]{x2, y2, z2}, 0, f);
    }

    /** Tôle rivetée : le fond commun des machines. */
    static void tole(Graphics2D g, double w, double h, int clair, int sombre) {
        metal(g, w, h, clair, sombre);
        g.setColor(a(0x000000, 110));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Rectangle2D.Double(2, 2, w - 4, h - 4));
        g.setColor(c(0x9AA4B2));
        for (double[] p : new double[][]{{4, 4}, {w - 6, 4}, {4, h - 6}, {w - 6, h - 6}}) {
            g.fill(new Ellipse2D.Double(p[0], p[1], 2, 2));
        }
    }

    static Path2D trait(double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }
        return p;
    }

    static void uni(BufferedImage at, double[] uv, int rgb) {
        Graphics2D g = zone(at, uv);
        g.setColor(c(rgb));
        g.fill(new Rectangle2D.Double(0, 0, 200, 200));
        g.dispose();
    }

    static void degrade(BufferedImage at, double[] uv, int haut, int bas, boolean vertical) {
        Graphics2D g = zone(at, uv);
        double k = at.getWidth() / 16.0, w = (uv[2] - uv[0]) * k, h = (uv[3] - uv[1]) * k;
        g.setPaint(vertical ? new GradientPaint(0, 0, c(haut), 0, (float) h, c(bas)) : new GradientPaint(0, 0, c(haut), (float) w, 0, c(bas)));
        g.fill(new Rectangle2D.Double(0, 0, w, h));
        g.dispose();
    }

    static void postes() throws IOException {
        condenseur();
        synthetiseur();
        tableMelange();
        terminal();
        coffreOrga();
    }

    /** Condenseur à plasma : cuve rose sur un socle à hublot. */
    static void condenseur() throws IOException {
        BufferedImage at = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        double[] facade = {0, 0, 8, 8}, flanc = {8, 0, 16, 8}, dessus = {0, 8, 6, 14}, cuve = {6, 8, 11, 14}, bande = {11, 8, 16, 9},
                tuyau = {11, 9, 12, 14}, chapeau = {12, 9, 16, 13}, fond = {12, 13, 14, 15};
        Graphics2D g = zone(at, facade);    // 64 x 64
        tole(g, 64, 64, 0x3A4868, 0x141B2D);
        g.setColor(c(0x0B0D16));
        g.fill(new Ellipse2D.Double(14, 7, 36, 36));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(32, 25), 15, new float[]{0f, 0.5f, 1f}, new Color[]{c(0xFFFFFF), c(0xFF4FD8), c(0x5A1250)}));
        g.fill(new Ellipse2D.Double(17, 10, 30, 30));
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(c(0xE0B864));
        g.draw(new Ellipse2D.Double(14, 7, 36, 36));
        g.setColor(c(0x0B0D16));
        g.fill(new RoundRectangle2D.Double(8, 47, 48, 10, 3, 3));
        for (int i = 0; i < 6; i++) {
            g.setColor(c(i < 4 ? 0x62CFF4 : 0x2B3140));
            g.fill(new Rectangle2D.Double(11 + i * 7.3, 49.5, 5, 5));
        }
        g.dispose();
        g = zone(at, flanc);
        tole(g, 64, 64, 0x34405E, 0x121829);
        g.setColor(c(0x0B0D16));
        for (int i = 0; i < 5; i++) {
            g.fill(new RoundRectangle2D.Double(12, 11 + i * 7, 40, 3.2, 2, 2));
        }
        g.setColor(c(0xE0B864));
        g.fill(new Rectangle2D.Double(0, 51, 64, 3));
        g.setColor(c(0xFF4FD8));
        g.fill(new Rectangle2D.Double(0, 54, 64, 1.5));
        g.dispose();
        g = zone(at, dessus);   // 48 x 48
        tole(g, 48, 48, 0x3A4868, 0x1A2238);
        g.setColor(c(0x0B0D16));
        g.fill(new Ellipse2D.Double(8, 8, 32, 32));
        g.dispose();
        g = zone(at, cuve);     // 40 x 48
        g.setPaint(new GradientPaint(0, 0, c(0xFF9BE8), 0, 48, c(0x6A1A5C)));
        g.fill(new Rectangle2D.Double(0, 0, 40, 48));
        g.setColor(c(0xFFD6F4));
        g.fill(new Rectangle2D.Double(5, 3, 4, 42));
        g.setColor(c(0xF08BD8));
        g.fill(new Rectangle2D.Double(12, 3, 2, 42));
        g.setColor(c(0xFFFFFF));
        for (double[] b : new double[][]{{24, 34, 4}, {30, 22, 3}, {20, 14, 2.5}, {31, 40, 2}}) {
            g.fill(new Ellipse2D.Double(b[0], b[1], b[2], b[2]));
        }
        g.dispose();
        degrade(at, bande, 0xFFF0C2, 0x8A6A2A, true);
        g = zone(at, tuyau);    // 8 x 40
        g.setPaint(new GradientPaint(0, 0, c(0x9AA4B2), 8, 0, c(0x2B3140)));
        g.fill(new Rectangle2D.Double(0, 0, 8, 40));
        g.setColor(c(0x0B0D16));
        for (int y = 6; y < 40; y += 12) {
            g.fill(new Rectangle2D.Double(0, y, 8, 1.5));
        }
        g.dispose();
        g = zone(at, chapeau);  // 32 x 32
        metal(g, 32, 32, 0x4A5878, 0x1A2238);
        g.setColor(c(0xE0B864));
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Double(8, 8, 16, 16));
        g.dispose();
        uni(at, fond, 0x0E1320);
        List<Boite> l = new ArrayList<>();
        l.add(boite2(-0.25, 0, -0.25, 16.25, 16.25, 16.25, facade, flanc, dessus, fond));
        l.add(boite(2.8, 16.25, 2.8, 13.2, 17.6, 13.2, 0, bande, bande, bande));
        l.add(boite(3.5, 17.6, 3.5, 12.5, 26.4, 12.5, 0, cuve, chapeau, fond));
        l.add(boite(2.8, 26.4, 2.8, 13.2, 28, 13.2, 0, bande, chapeau, bande));
        l.add(boite(6.3, 28, 6.3, 9.7, 31.8, 9.7, 0, tuyau, bande, bande));
        for (double[] p : new double[][]{{0.6, 0.6}, {13.8, 0.6}, {0.6, 13.8}, {13.8, 13.8}}) {
            l.add(boite(p[0], 16.25, p[1], p[0] + 1.6, 23, p[1] + 1.6, 0, tuyau, bande, bande));
        }
        modele("condenseur", l, at, POSE_POSTE);
    }

    /** Synthétiseur : deux entrées (quartz, plasma), une colonne à bobines, une tête qui cristallise. */
    static void synthetiseur() throws IOException {
        BufferedImage at = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        double[] facade = {0, 0, 8, 8}, flanc = {8, 0, 16, 8}, dessus = {0, 8, 6, 14}, colonne = {6, 8, 8, 14}, tete = {8, 8, 12, 12},
                bande = {12, 8, 16, 9}, tuyau = {12, 9, 13, 14}, lueur = {13, 9, 15, 11}, fond = {13, 11, 15, 13};
        Graphics2D g = zone(at, facade);    // 64 x 64
        tole(g, 64, 64, 0x2F3B57, 0x141B2D);
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c(0xE0B864));
        g.draw(new Line2D.Double(17, 22, 32, 36));
        g.draw(new Line2D.Double(47, 22, 32, 36));
        for (int[] e : new int[][]{{17, 0xC79BFF}, {47, 0xFF4FD8}}) {
            g.setColor(c(0x0B0D16));
            g.fill(new Ellipse2D.Double(e[0] - 8, 8, 16, 16));
            g.setPaint(new RadialGradientPaint(new Point2D.Double(e[0], 16), 6, new float[]{0f, 1f}, new Color[]{c(0xFFFFFF), c(e[1])}));
            g.fill(new Ellipse2D.Double(e[0] - 6, 10, 12, 12));
        }
        Shape losange = poly(32, 32, 44, 45, 32, 58, 20, 45);
        g.setPaint(new GradientPaint(20, 32, c(0xFFFFFF), 44, 58, c(0x62CFF4)));
        g.fill(losange);
        g.setStroke(new BasicStroke(1.6f));
        g.setColor(c(0x0B0D16));
        g.draw(losange);
        g.draw(new Line2D.Double(32, 32, 32, 58));
        g.dispose();
        g = zone(at, flanc);
        tole(g, 64, 64, 0x2A3550, 0x121829);
        g.setStroke(new BasicStroke(1.6f));
        g.setColor(c(0x62CFF4));
        g.draw(trait(10, 14, 30, 14, 30, 30, 54, 30));
        g.draw(trait(10, 46, 22, 46, 22, 38, 54, 38));
        for (double[] p : new double[][]{{10, 14}, {54, 30}, {10, 46}, {54, 38}}) {
            g.fill(new Ellipse2D.Double(p[0] - 2.5, p[1] - 2.5, 5, 5));
        }
        g.setColor(c(0xE0B864));
        g.fill(new Rectangle2D.Double(0, 53, 64, 3));
        g.dispose();
        g = zone(at, dessus);
        tole(g, 48, 48, 0x34405E, 0x1A2238);
        g.setColor(c(0xE0B864));
        g.setStroke(new BasicStroke(2f));
        g.draw(new Rectangle2D.Double(10, 10, 28, 28));
        g.dispose();
        g = zone(at, colonne);  // 16 x 48
        g.setPaint(new GradientPaint(0, 0, c(0x2B3049), 16, 0, c(0x0B0D16)));
        g.fill(new Rectangle2D.Double(0, 0, 16, 48));
        g.setPaint(new GradientPaint(0, 0, c(0xFFFFFF), 0, 48, c(0x62CFF4)));
        g.fill(new Rectangle2D.Double(6, 2, 4, 44));
        g.dispose();
        g = zone(at, tete);     // 32 x 32
        g.setPaint(new RadialGradientPaint(new Point2D.Double(16, 16), 20, new float[]{0f, 0.6f, 1f}, new Color[]{c(0xFFFFFF), c(0x9FE7FF), c(0x2A7FA8)}));
        g.fill(new Rectangle2D.Double(0, 0, 32, 32));
        g.setColor(c(0x0B0D16));
        g.setStroke(new BasicStroke(2f));
        g.draw(new Rectangle2D.Double(1, 1, 30, 30));
        g.dispose();
        degrade(at, bande, 0xFFF0C2, 0x8A6A2A, true);
        degrade(at, tuyau, 0x9AA4B2, 0x2B3140, false);
        uni(at, lueur, 0x9FE7FF);
        uni(at, fond, 0x0E1320);
        List<Boite> l = new ArrayList<>();
        l.add(boite2(-0.25, 0, -0.25, 16.25, 16.25, 16.25, facade, flanc, dessus, fond));
        l.add(boite(1.5, 16.25, 1.5, 14.5, 17.4, 14.5, 0, bande, dessus, bande));
        l.add(boite(6, 17.4, 6, 10, 29, 10, 0, colonne, tete, fond));
        l.add(boite(4.6, 20.5, 4.6, 11.4, 21.6, 11.4, 0, bande, bande, bande));
        l.add(boite(4.6, 24.5, 4.6, 11.4, 25.6, 11.4, 0, bande, bande, bande));
        l.add(boite(4.4, 29, 4.4, 11.6, 31.6, 11.6, 0, tete, tete, tete));
        for (double x : new double[]{1.8, 12.6}) {
            l.add(boite(x, 17.4, 7.2, x + 1.6, 25, 8.8, 0, tuyau, lueur, bande));
            l.add(boite(x - 0.2, 25, 7, x + 1.8, 26.6, 9, 0, lueur, lueur, lueur));
        }
        modele("synthetiseur", l, at, POSE_POSTE);
    }

    /** Table de mélange : paillasse de labo, mélangeur central, fioles, tubes, petit écran. */
    static void tableMelange() throws IOException {
        BufferedImage at = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        double[] plateau = {0, 0, 8, 8}, chant = {8, 0, 16, 1.25}, plinthe = {8, 1.25, 16, 2.5}, pied = {8, 2.5, 10.5, 6.5},
                mixeur = {10.5, 2.5, 13, 4.5}, fioleA = {13, 2.5, 14.5, 5}, fioleB = {14.5, 2.5, 16, 4.5}, fioleC = {13, 5, 13.5, 6.5},
                verre = {13.5, 5, 14, 6}, ecran = {8, 6.5, 10, 8}, dessous = {10.5, 4.5, 13, 6.5}, capot = {10, 6.5, 12, 8};
        Graphics2D g = zone(at, plateau);   // 64 x 64
        g.setPaint(new GradientPaint(0, 0, c(0x35405E), 64, 64, c(0x1A2238)));
        g.fill(new Rectangle2D.Double(0, 0, 64, 64));
        g.setColor(a(0x62CFF4, 46));
        for (int i = 8; i < 64; i += 8) {
            g.fill(new Rectangle2D.Double(i, 0, 0.8, 64));
            g.fill(new Rectangle2D.Double(0, i, 64, 0.8));
        }
        g.setStroke(new BasicStroke(2.4f));
        g.setColor(c(0xE0B864));
        g.draw(new Rectangle2D.Double(1.2, 1.2, 61.6, 61.6));
        g.setColor(c(0x0B0D16));
        g.fill(new Ellipse2D.Double(18, 18, 28, 28));
        g.setColor(c(0x62CFF4));
        g.setStroke(new BasicStroke(1.6f));
        g.draw(new Ellipse2D.Double(18, 18, 28, 28));
        g.setColor(c(0xC79BFF));
        g.fill(new Ellipse2D.Double(8, 44, 7, 5));
        g.setColor(c(0xFF4FD8));
        g.fill(new Ellipse2D.Double(48, 12, 5, 4));
        g.dispose();
        g = zone(at, chant);    // 64 x 10
        metal(g, 64, 10, 0x5A6270, 0x1B1F2B);
        g.setColor(c(0xE0B864));
        g.fill(new Rectangle2D.Double(0, 0, 64, 2));
        g.dispose();
        g = zone(at, plinthe);  // 64 x 10
        metal(g, 64, 10, 0x2B3140, 0x0E1320);
        g.setColor(c(0xE0B864));
        for (int x = 0; x < 64; x += 12) {
            g.fill(poly(x, 10, x + 4, 10, x + 10, 4, x + 6, 4));
        }
        g.dispose();
        g = zone(at, pied);     // 20 x 32
        g.setPaint(new GradientPaint(0, 0, c(0x66728A), 20, 0, c(0x1B1F2B)));
        g.fill(new Rectangle2D.Double(0, 0, 20, 32));
        g.setColor(c(0x62CFF4));
        g.fill(new Rectangle2D.Double(8.5, 4, 3, 24));
        g.dispose();
        g = zone(at, mixeur);   // 20 x 16
        metal(g, 20, 16, 0x4A5878, 0x141B2D);
        g.setColor(c(0x05070D));
        g.fill(new RoundRectangle2D.Double(3, 3, 14, 8, 2, 2));
        g.setPaint(new GradientPaint(3, 3, c(0xFFFFFF), 17, 11, c(0x62CFF4)));
        g.fill(new RoundRectangle2D.Double(4.5, 4.5, 11, 5, 2, 2));
        g.setColor(c(0xE0B864));
        g.fill(new Rectangle2D.Double(0, 13, 20, 1.5));
        g.dispose();
        for (Object[] f : new Object[][]{{fioleA, 0xE4CCFF, 0x8B55D8}, {fioleB, 0xD6F6FF, 0x2F9CCB}, {fioleC, 0xFFB8EE, 0xC2289E}}) {
            double[] uv = (double[]) f[0];
            double w = (uv[2] - uv[0]) * 8, h = (uv[3] - uv[1]) * 8;
            g = zone(at, uv);
            g.setPaint(new GradientPaint(0, 0, c((Integer) f[1]), 0, (float) h, c((Integer) f[2])));
            g.fill(new Rectangle2D.Double(0, 0, w, h));
            g.setColor(c(0xFFFFFF));
            g.fill(new Rectangle2D.Double(w * 0.18, h * 0.12, Math.max(1, w * 0.16), h * 0.6));
            g.dispose();
        }
        uni(at, verre, 0xD7E4F2);
        g = zone(at, ecran);    // 16 x 12
        g.setColor(c(0x05070D));
        g.fill(new Rectangle2D.Double(0, 0, 16, 12));
        g.setColor(c(0x62CFF4));
        for (int i = 0; i < 4; i++) {
            g.fill(new Rectangle2D.Double(2 + i * 3.2, 9 - i * 2, 2, 1 + i * 2));
        }
        g.dispose();
        uni(at, dessous, 0x10151F);
        g = zone(at, capot);    // 16 x 12
        metal(g, 16, 12, 0x66728A, 0x2B3140);
        g.setColor(c(0xE0B864));
        g.fill(new Ellipse2D.Double(5, 3, 6, 6));
        g.dispose();
        List<Boite> l = new ArrayList<>();
        l.add(boite(-0.25, 0, -0.25, 16.25, 2.6, 16.25, 0, plinthe, dessous, dessous));
        l.add(boite(5.5, 2.6, 5.5, 10.5, 10, 10.5, 0, pied, dessous, dessous));
        l.add(boite(-0.5, 10, -0.5, 16.5, 12.5, 16.5, 0, chant, plateau, dessous));
        l.add(boite(5.4, 12.5, 5.4, 10.6, 16.8, 10.6, 0, mixeur, capot, dessous));
        l.add(boite(1.6, 12.5, 1.6, 4.6, 17, 4.6, 0, fioleA, fioleA, fioleA));
        l.add(boite(2.6, 17, 2.6, 3.6, 18.6, 3.6, 0, verre, verre, verre));
        l.add(boite(11.4, 12.5, 11.2, 14.4, 16, 14.2, 0, fioleB, fioleB, fioleB));
        l.add(boite(12.4, 16, 12.2, 13.4, 17.4, 13.2, 0, verre, verre, verre));
        l.add(boite(10.8, 12.5, 1.6, 15.2, 13.3, 3.2, 0, chant, chant, chant));
        for (double x : new double[]{11.3, 12.6, 13.9}) {
            l.add(boite(x, 13.3, 2, x + 0.8, 16.3, 2.8, 0, fioleC, verre, fioleC));
        }
        l.add(boite(1.6, 12.5, 11.6, 5.6, 15.6, 12.6, 0, ecran, chant, chant));
        modele("table_melange", l, at, POSE_POSTE);
    }

    /** Terminal des améliorations : borne à deux écrans (un devant, un derrière), clavier, fronton doré, antenne. */
    static void terminal() throws IOException {
        BufferedImage at = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        double[] ecran = {0, 0, 8, 10}, flanc = {8, 0, 16, 10}, dessus = {0, 10, 6, 16}, fronton = {6, 10, 12, 12}, tuyau = {12, 10, 13, 14},
                lueur = {13, 10, 15, 12}, fond = {13, 12, 15, 14};
        Graphics2D g = zone(at, ecran);     // 64 x 80
        tole(g, 64, 80, 0x2F3B57, 0x141B2D);
        g.setColor(c(0x05070D));
        g.fill(new RoundRectangle2D.Double(7, 7, 50, 38, 4, 4));
        g.setStroke(new BasicStroke(2f));
        g.setColor(c(0xE0B864));
        g.draw(new RoundRectangle2D.Double(7, 7, 50, 38, 4, 4));
        for (int i = 0; i < 5; i++) {
            g.setPaint(new GradientPaint(0, 40 - (6 + i * 5), c(0xC8F1FF), 0, 40, c(0x2F9CCB)));
            g.fill(new Rectangle2D.Double(12 + i * 8, 40 - (6 + i * 5), 5.5, 6 + i * 5));
        }
        g.setColor(c(0xE0B864));
        g.fill(poly(47, 12, 53, 20, 49.5, 20, 49.5, 28, 44.5, 28, 44.5, 20, 41, 20));
        for (int r = 0; r < 3; r++) {
            for (int k = 0; k < 8; k++) {
                g.setColor(c(0x0B0D16));
                g.fill(new Rectangle2D.Double(9 + k * 5.9, 51 + r * 6, 4.6, 4.4));
                g.setColor(c(r == 2 && k > 5 ? 0xE0B864 : 0x66728A));
                g.fill(new Rectangle2D.Double(9.6 + k * 5.9, 51.4 + r * 6, 3.4, 2.4));
            }
        }
        g.setColor(c(0x62CFF4));
        g.fill(new Rectangle2D.Double(9, 72, 46, 2));
        g.dispose();
        g = zone(at, flanc);    // 64 x 80
        tole(g, 64, 80, 0x2A3550, 0x121829);
        g.setColor(c(0x0B0D16));
        for (int i = 0; i < 6; i++) {
            g.fill(new RoundRectangle2D.Double(14, 12 + i * 6.5, 36, 3, 2, 2));
        }
        g.setColor(c(0xE0B864));
        g.fill(new Rectangle2D.Double(0, 60, 64, 3));
        g.setColor(c(0x62CFF4));
        g.fill(new Rectangle2D.Double(0, 63, 64, 1.5));
        g.dispose();
        g = zone(at, dessus);
        tole(g, 48, 48, 0x34405E, 0x1A2238);
        g.dispose();
        g = zone(at, fronton);  // 48 x 16
        g.setPaint(new GradientPaint(0, 0, c(0xFFF0C2), 0, 16, c(0x8A6A2A)));
        g.fill(new Rectangle2D.Double(0, 0, 48, 16));
        g.setColor(c(0x0F1C3D));
        g.fill(new Ellipse2D.Double(18, 2, 12, 12));
        g.setColor(c(0x62CFF4));
        g.fill(new Ellipse2D.Double(21.5, 5.5, 5, 5));
        g.setColor(c(0x4A3410));
        g.fill(new Rectangle2D.Double(4, 7, 10, 2));
        g.fill(new Rectangle2D.Double(34, 7, 10, 2));
        g.dispose();
        degrade(at, tuyau, 0x9AA4B2, 0x2B3140, false);
        uni(at, lueur, 0x9FE7FF);
        uni(at, fond, 0x0E1320);
        List<Boite> l = new ArrayList<>();
        l.add(boite2(-0.25, 0, -1.5, 16.25, 20.5, 17.5, ecran, flanc, dessus, fond));
        l.add(boite(1, 20.5, 4, 15, 23.5, 12, 0, fronton, fronton, fronton));
        l.add(boite(7.4, 23.5, 7.4, 8.6, 27, 8.6, 0, tuyau, lueur, lueur));
        l.add(boite(6.8, 27, 6.8, 9.2, 28.6, 9.2, 0, lueur, lueur, lueur));
        modele("terminal", l, at, POSE_POSTE);
    }

    /** Coffre de l'organisation : coffre-fort violet, volant doré. */
    static void coffreOrga() throws IOException {
        BufferedImage at = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        double[] porte = {0, 0, 8, 8}, flanc = {8, 0, 16, 8}, dessus = {0, 8, 6, 14}, volant = {6, 8, 8, 10}, fond = {8, 8, 10, 10};
        Graphics2D g = zone(at, porte);     // 64 x 64
        tole(g, 64, 64, 0x55488A, 0x1B1630);
        g.setStroke(new BasicStroke(2f));
        g.setColor(c(0x0B0D16));
        g.draw(new RoundRectangle2D.Double(7, 7, 50, 50, 5, 5));
        g.setColor(c(0xE0B864));
        g.fill(new Rectangle2D.Double(4, 14, 4, 9));
        g.fill(new Rectangle2D.Double(4, 41, 4, 9));
        g.setStroke(new BasicStroke(3f));
        g.draw(new Ellipse2D.Double(20, 20, 24, 24));
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 * i;
            g.draw(new Line2D.Double(32 - Math.cos(a) * 12, 32 - Math.sin(a) * 12, 32 + Math.cos(a) * 12, 32 + Math.sin(a) * 12));
        }
        g.setColor(c(0x0B0D16));
        g.fill(new RoundRectangle2D.Double(44, 10, 10, 8, 2, 2));
        g.setColor(c(0xC79BFF));
        g.fill(new Rectangle2D.Double(46, 12, 6, 4));
        g.dispose();
        g = zone(at, flanc);
        tole(g, 64, 64, 0x4A3F78, 0x181430);
        g.setColor(c(0x9AA4B2));
        for (int i = 0; i < 6; i++) {
            g.fill(new Ellipse2D.Double(9 + i * 9, 14, 2.4, 2.4));
            g.fill(new Ellipse2D.Double(9 + i * 9, 46, 2.4, 2.4));
        }
        g.setColor(c(0xE0B864));
        g.fill(new Rectangle2D.Double(0, 29, 64, 3));
        g.setColor(c(0xC79BFF));
        g.fill(new Rectangle2D.Double(0, 32, 64, 1.5));
        g.dispose();
        g = zone(at, dessus);
        tole(g, 48, 48, 0x55488A, 0x241D40);
        g.dispose();
        g = zone(at, volant);   // 16 x 16
        g.setPaint(new GradientPaint(0, 0, c(0xFFF0C2), 16, 16, c(0x8A6A2A)));
        g.fill(new Rectangle2D.Double(0, 0, 16, 16));
        g.setColor(c(0x4A3410));
        g.fill(new Ellipse2D.Double(5, 5, 6, 6));
        g.dispose();
        uni(at, fond, 0x0E1320);
        List<Boite> l = new ArrayList<>();
        l.add(boite2(-0.25, 0, -0.25, 16.25, 16.25, 16.25, porte, flanc, dessus, fond));
        l.add(boite(6.2, 6.4, -1.0, 9.8, 10, -0.25, 0, volant, volant, volant));
        l.add(boite(6.2, 6.4, 16.25, 9.8, 10, 17.0, 0, volant, volant, volant));
        modele("coffre_orga", l, at, POSE_POSTE);
    }

    /** Voiture de patrouille de la douane : l'avant est au nord du modèle (z = 0). */
    static void voiture() throws IOException {
        BufferedImage at = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        double[] flanc = {0, 0, 15, 2.7}, vitres = {0, 2.7, 7.4, 5.1}, avant = {7.4, 2.7, 14.6, 5.4}, arriere = {0, 5.4, 7.2, 8.1},
                pareBrise = {7.4, 5.4, 13.6, 7.8}, lunette = {0, 8.1, 6.2, 10.5}, capot = {9, 8.1, 16, 16}, toit = {0, 10.5, 6, 16},
                gyro = {6.2, 8.1, 8.8, 9.1}, roue = {6.2, 9.1, 8.8, 11.7}, pneu = {6.2, 11.7, 7.4, 14.3}, chrome = {7.4, 11.7, 8.8, 12.4},
                fond = {7.4, 12.4, 8.8, 13.8};
        java.awt.Font gras = new java.awt.Font("SansSerif", java.awt.Font.BOLD, 14);
        Graphics2D g = zone(at, flanc);     // 240 x 43
        g.setPaint(new GradientPaint(0, 0, c(0x2A4C92), 0, 43, c(0x14254A)));
        g.fill(new Rectangle2D.Double(0, 0, 240, 43.2));
        g.setColor(c(0x0B0D16));
        g.fill(new Rectangle2D.Double(0, 36, 240, 8));
        g.fill(new Ellipse2D.Double(24, 20, 48, 46));
        g.fill(new Ellipse2D.Double(168, 20, 48, 46));
        g.setColor(c(0xE0B864));
        g.fill(new Rectangle2D.Double(0, 15, 240, 4));
        g.setColor(c(0xFFFFFF));
        g.fill(new Rectangle2D.Double(0, 20, 240, 1.5));
        g.setFont(gras);
        g.drawString("DOUANE", 120 - g.getFontMetrics().stringWidth("DOUANE") / 2f, 13);
        g.setColor(a(0xFFFFFF, 60));
        g.fill(new Rectangle2D.Double(0, 0, 240, 1.5));
        g.setColor(c(0x0B0D16));
        g.fill(new Rectangle2D.Double(78, 2, 1.2, 33));
        g.fill(new Rectangle2D.Double(160, 2, 1.2, 33));
        g.dispose();
        g = zone(at, vitres);   // 118 x 38
        g.setColor(c(0x1B3266));
        g.fill(new Rectangle2D.Double(0, 0, 119, 39));
        for (double[] v : new double[][]{{5, 52}, {62, 51}}) {
            g.setPaint(new GradientPaint(0, 4, c(0x3D5F8F), 0, 34, c(0x070B14)));
            g.fill(new RoundRectangle2D.Double(v[0], 5, v[1], 28, 6, 6));
            g.setColor(a(0xFFFFFF, 90));
            g.fill(poly(v[0] + 8, 33, v[0] + 22, 5, v[0] + 30, 5, v[0] + 16, 33));
        }
        g.dispose();
        g = zone(at, avant);    // 115 x 43
        g.setPaint(new GradientPaint(0, 0, c(0x2A4C92), 0, 43, c(0x14254A)));
        g.fill(new Rectangle2D.Double(0, 0, 116, 44));
        g.setColor(c(0x05070D));
        g.fill(new RoundRectangle2D.Double(32, 14, 51, 18, 4, 4));
        g.setColor(c(0x5A6270));
        for (int y = 17; y < 30; y += 4) {
            g.fill(new Rectangle2D.Double(35, y, 45, 1.6));
        }
        for (double x : new double[]{6, 87}) {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(x + 11, 22), 14, new float[]{0f, 1f}, new Color[]{c(0xFFFFFF), c(0xFFE680)}));
            g.fill(new RoundRectangle2D.Double(x, 14, 22, 15, 6, 6));
            g.setColor(c(0x0B0D16));
            g.setStroke(new BasicStroke(1.4f));
            g.draw(new RoundRectangle2D.Double(x, 14, 22, 15, 6, 6));
        }
        g.setColor(c(0xE0B864));
        g.fill(new Ellipse2D.Double(53, 3, 10, 8));
        g.setColor(c(0x0B0D16));
        g.fill(new Rectangle2D.Double(0, 36, 116, 8));
        g.dispose();
        g = zone(at, arriere);  // 115 x 43
        g.setPaint(new GradientPaint(0, 0, c(0x2A4C92), 0, 43, c(0x14254A)));
        g.fill(new Rectangle2D.Double(0, 0, 116, 44));
        for (double x : new double[]{5, 88}) {
            g.setColor(c(0xE0484A));
            g.fill(new RoundRectangle2D.Double(x, 12, 22, 11, 4, 4));
            g.setColor(c(0xFFB0B0));
            g.fill(new Rectangle2D.Double(x + 3, 14, 16, 2.5));
        }
        g.setColor(c(0xE6EDF3));
        g.fill(new RoundRectangle2D.Double(41, 20, 34, 12, 3, 3));
        g.setColor(c(0x14254A));
        g.setFont(gras.deriveFont(10f));
        g.drawString("ORB 07", 58 - g.getFontMetrics().stringWidth("ORB 07") / 2f, 30);
        g.setColor(c(0xE0B864));
        g.fill(new Rectangle2D.Double(0, 6, 116, 2.5));
        g.setColor(c(0x0B0D16));
        g.fill(new Rectangle2D.Double(0, 36, 116, 8));
        g.dispose();
        for (double[] uv : new double[][]{pareBrise, lunette}) {    // 99 x 38
            g = zone(at, uv);
            g.setColor(c(0x1B3266));
            g.fill(new Rectangle2D.Double(0, 0, 100, 39));
            g.setPaint(new GradientPaint(0, 4, c(uv == pareBrise ? 0x4A6FA3 : 0x2A3F60), 0, 34, c(0x070B14)));
            g.fill(new RoundRectangle2D.Double(5, 4, 89, 30, 8, 8));
            g.setColor(a(0xFFFFFF, 80));
            g.fill(poly(14, 34, 34, 4, 46, 4, 26, 34));
            g.dispose();
        }
        g = zone(at, capot);    // 112 x 126 (vu de dessus : le nord, donc l'avant, est en haut)
        g.setPaint(new GradientPaint(0, 0, c(0x2F55A2), 112, 0, c(0x1B3266)));
        g.fill(new Rectangle2D.Double(0, 0, 112, 127));
        g.setColor(c(0xE0B864));
        g.fill(new Rectangle2D.Double(52, 0, 8, 127));
        g.setColor(c(0xFFFFFF));
        g.fill(new Rectangle2D.Double(49, 0, 1.6, 127));
        g.fill(new Rectangle2D.Double(61.4, 0, 1.6, 127));
        g.setColor(a(0x000000, 90));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Rectangle2D.Double(8, 4, 96, 30));
        g.draw(new Rectangle2D.Double(8, 102, 96, 21));
        g.dispose();
        g = zone(at, toit);     // 96 x 88
        g.setColor(c(0xE6EDF3));
        g.fill(new Rectangle2D.Double(0, 0, 96, 88));
        g.setStroke(new BasicStroke(4f));
        g.setColor(c(0x1B3266));
        g.draw(new Rectangle2D.Double(2, 2, 92, 84));
        g.setFont(gras.deriveFont(22f));
        g.drawString("07", 48 - g.getFontMetrics().stringWidth("07") / 2f, 80);
        g.dispose();
        g = zone(at, gyro);     // 42 x 16
        g.setPaint(new GradientPaint(0, 0, c(0xFF8A8A), 0, 16, c(0xC62828)));
        g.fill(new Rectangle2D.Double(0, 0, 20, 16));
        g.setPaint(new GradientPaint(0, 0, c(0x9FC2FF), 0, 16, c(0x2A5FD8)));
        g.fill(new Rectangle2D.Double(22, 0, 20, 16));
        g.setColor(c(0xE6EDF3));
        g.fill(new Rectangle2D.Double(19, 0, 4, 16));
        g.dispose();
        g = zone(at, roue);     // 42 x 42 (coins transparents : la roue est ronde)
        g.setColor(c(0x0B0D16));
        g.fill(new Rectangle2D.Double(0, 0, 42, 42));
        g.setColor(c(0x9AA4B2));
        g.fill(new Ellipse2D.Double(11, 11, 20, 20));
        g.setColor(c(0x2B3140));
        g.fill(new Ellipse2D.Double(17, 17, 8, 8));
        g.dispose();
        g = zone(at, pneu);     // 19 x 42
        g.setColor(c(0x0B0D16));
        g.fill(new Rectangle2D.Double(0, 0, 20, 42));
        g.setColor(c(0x2B3140));
        for (int y = 2; y < 42; y += 6) {
            g.fill(new Rectangle2D.Double(2, y, 15, 2));
        }
        g.dispose();
        degrade(at, chrome, 0xE6EDF3, 0x5A6270, true);
        uni(at, fond, 0x0B0D16);
        List<Boite> l = new ArrayList<>();
        l.add(boite6(4.4, 1.2, 0.5, 11.6, 3.9, 15.5, avant, arriere, flanc, flanc, capot, fond));
        l.add(boite6(4.9, 3.9, 4.8, 11.1, 6.3, 12.2, pareBrise, lunette, vitres, vitres, toit, fond));
        l.add(boite(6.2, 6.3, 7.9, 9.8, 7.1, 9.1, 0, gyro, gyro, gyro));
        for (double x : new double[]{3.9, 10.9}) {
            for (double z : new double[]{2.2, 11.2}) {
                l.add(boite6(x, 0, z, x + 1.2, 2.6, z + 2.6, pneu, pneu, roue, roue, pneu, pneu));
            }
            l.add(boite(x + (x < 8 ? 0.3 : 0.2), 4.2, 5.2, x + (x < 8 ? 1.0 : 0.9), 4.8, 5.9, 0, chrome, chrome, chrome));
        }
        l.add(boite(4.2, 1.0, 0.1, 11.8, 2.1, 0.6, 0, chrome, chrome, chrome));
        l.add(boite(4.2, 1.0, 15.4, 11.8, 2.1, 15.9, 0, chrome, chrome, chrome));
        modele("voiture_douane", l, at, "{\n"
                + "    \"gui\": {\"rotation\": [28, 130, 0], \"translation\": [0, 3, 0], \"scale\": [1.05, 1.05, 1.05]},\n"
                + "    \"fixed\": {\"rotation\": [0, 0, 0], \"translation\": [0, 0, 0], \"scale\": [1, 1, 1]}\n  }");
    }

    /** Skate à sustentation : pas de roues, deux répulseurs bleus. */
    static void skate() throws IOException {
        BufferedImage at = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        double[] pont = {0, 0, 5.6, 14}, chant = {6, 0, 16, 1}, ventre = {6, 1, 9, 4}, repulseur = {9, 1, 13, 2}, lueur = {13, 1, 16, 4};
        Graphics2D g = zone(at, pont);      // 45 x 112 (l'avant en haut)
        g.setPaint(new GradientPaint(0, 0, c(0x2B3550), 45, 112, c(0x121829)));
        g.fill(new Rectangle2D.Double(0, 0, 45, 112));
        g.setStroke(new BasicStroke(2.2f));
        g.setColor(c(0xE0B864));
        g.draw(new RoundRectangle2D.Double(1.5, 1.5, 42, 109, 10, 10));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c(0x62CFF4));
        for (int y = 18; y <= 38; y += 10) {
            g.draw(trait(11, y + 8, 22.5, y, 34, y + 8));
        }
        g.setColor(c(0x0B0D16));
        g.fill(new Ellipse2D.Double(11.5, 60, 22, 22));
        g.setStroke(new BasicStroke(2.4f));
        g.setColor(c(0xE0B864));
        g.draw(AffineTransform.getRotateInstance(Math.toRadians(-24), 22.5, 71).createTransformedShape(new Ellipse2D.Double(9, 65, 27, 12)));
        g.setColor(c(0x62CFF4));
        g.fill(new Ellipse2D.Double(18, 66.5, 9, 9));
        g.setColor(c(0x62CFF4));
        g.fill(new Rectangle2D.Double(10, 96, 25, 2.5));
        g.dispose();
        g = zone(at, chant);    // 80 x 8
        g.setPaint(new GradientPaint(0, 0, c(0xFFF0C2), 0, 8, c(0x8A6A2A)));
        g.fill(new Rectangle2D.Double(0, 0, 80, 8));
        g.setColor(c(0x121829));
        g.fill(new Rectangle2D.Double(0, 3, 80, 5));
        g.dispose();
        uni(at, ventre, 0x0E1320);
        g = zone(at, repulseur);    // 32 x 8
        metal(g, 32, 8, 0x66728A, 0x1B1F2B);
        g.setColor(c(0x62CFF4));
        g.fill(new Rectangle2D.Double(0, 5.5, 32, 2.5));
        g.dispose();
        g = zone(at, lueur);    // 24 x 24
        g.setPaint(new RadialGradientPaint(new Point2D.Double(12, 12), 14, new float[]{0f, 0.55f, 1f}, new Color[]{c(0xFFFFFF), c(0x62CFF4), c(0x1A4C74)}));
        g.fill(new Rectangle2D.Double(0, 0, 24, 24));
        g.dispose();
        List<Boite> l = new ArrayList<>();
        l.add(boite(5.2, 7.6, 1.2, 10.8, 8.4, 14.8, 0, chant, pont, ventre));
        l.add(boite(5.7, 7.9, 0.1, 10.3, 8.8, 1.4, 0, chant, chant, ventre));
        l.add(boite(5.7, 7.9, 14.6, 10.3, 8.8, 15.9, 0, chant, chant, ventre));
        l.add(boite(6, 6.7, 3, 10, 7.6, 6, 0, repulseur, ventre, lueur));
        l.add(boite(6, 6.7, 10, 10, 7.6, 13, 0, repulseur, ventre, lueur));
        modele("skate", l, at, "{\n"
                + "    \"gui\": {\"rotation\": [40, 40, 0], \"translation\": [0, 0, 0], \"scale\": [1.1, 1.1, 1.1]},\n"
                + "    \"ground\": {\"rotation\": [0, 0, 0], \"translation\": [0, 2, 0], \"scale\": [0.6, 0.6, 0.6]},\n"
                + "    \"thirdperson_righthand\": {\"rotation\": [70, 0, 0], \"translation\": [0, 1, -1], \"scale\": [0.75, 0.75, 0.75]},\n"
                + "    \"thirdperson_lefthand\": {\"rotation\": [70, 0, 0], \"translation\": [0, 1, -1], \"scale\": [0.75, 0.75, 0.75]},\n"
                + "    \"firstperson_righthand\": {\"rotation\": [20, -25, 0], \"translation\": [2, 2, -2], \"scale\": [0.75, 0.75, 0.75]},\n"
                + "    \"firstperson_lefthand\": {\"rotation\": [20, 25, 0], \"translation\": [2, 2, -2], \"scale\": [0.75, 0.75, 0.75]},\n"
                + "    \"fixed\": {\"rotation\": [0, 0, 0], \"translation\": [0, 0, 0], \"scale\": [1, 1, 1]}\n  }");
    }

    // ---------- uniforme des agents : un skin de joueur 64 x 64 (bras larges)

    static BufferedImage peau;

    /** Rectangle du skin, avec un léger grain pour ne pas faire aplat. */
    static void tissu(int x, int y, int w, int h, int rgb) {
        for (int i = x; i < x + w; i++) {
            for (int j = y; j < y + h; j++) {
                int n = (int) (((i * 73856093L) ^ (j * 19349663L)) & 7) - 3;
                int r = Math.max(0, Math.min(255, ((rgb >> 16) & 255) + n * 2)), v = Math.max(0, Math.min(255, ((rgb >> 8) & 255) + n * 2)),
                        b = Math.max(0, Math.min(255, (rgb & 255) + n * 2));
                peau.setRGB(i, j, 0xFF000000 | (r << 16) | (v << 8) | b);
            }
        }
    }

    static void agent() throws IOException {
        peau = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int chair = 0xD9A77F, marine = 0x1D3566, nuit = 0x131F3D, noir = 0x0B0D16, or = 0xE0B864, glace = 0x62CFF4, kepi = 0x16264A;
        // tête : visage, képi marine à visière noire et insigne doré
        tissu(0, 8, 32, 8, chair);
        tissu(8, 0, 8, 8, kepi);
        tissu(16, 0, 8, 8, chair);
        tissu(0, 8, 32, 3, kepi);
        tissu(8, 11, 8, 1, noir);
        tissu(11, 8, 2, 2, or);
        tissu(0, 11, 8, 2, 0x3A2A1E);
        tissu(16, 11, 16, 2, 0x3A2A1E);
        tissu(9, 12, 2, 1, 0xFFFFFF);
        tissu(13, 12, 2, 1, 0xFFFFFF);
        tissu(10, 12, 1, 1, 0x1B2A4A);
        tissu(13, 12, 1, 1, 0x1B2A4A);
        tissu(10, 14, 4, 1, 0xA9744F);
        // buste : veste marine, col, boutons et insigne dorés, ceinturon
        tissu(16, 16, 24, 16, marine);
        tissu(20, 20, 8, 1, 0x2A4C92);
        tissu(23, 21, 2, 2, 0xE6EDF3);
        for (int y = 23; y <= 27; y += 2) {
            tissu(23, y, 1, 1, or);
        }
        tissu(25, 22, 2, 2, or);
        tissu(16, 30, 24, 2, noir);
        tissu(23, 30, 2, 2, or);
        // dos : bandeau doré et orbite glace
        tissu(32, 22, 8, 1, or);
        tissu(34, 24, 4, 1, glace);
        tissu(33, 25, 1, 2, glace);
        tissu(38, 25, 1, 2, glace);
        tissu(34, 27, 4, 1, glace);
        tissu(35, 25, 2, 2, or);
        // bras (droit puis gauche) : manche marine, écusson glace à l'épaule, galon doré, main
        for (int[] o : new int[][]{{40, 16}, {32, 48}}) {
            tissu(o[0], o[1], 16, 16, marine);
            tissu(o[0], o[1] + 4, 16, 2, 0x2A4C92);
            tissu(o[0] + (o[0] == 40 ? 0 : 8), o[1] + 6, 4, 2, glace);
            tissu(o[0], o[1] + 12, 16, 1, or);
            tissu(o[0], o[1] + 13, 16, 3, chair);
            tissu(o[0] + 8, o[1], 4, 4, chair);
        }
        // jambes (droite puis gauche) : pantalon nuit, liseré doré sur le côté, bottes noires
        for (int[] o : new int[][]{{0, 16}, {16, 48}}) {
            tissu(o[0], o[1], 16, 16, nuit);
            tissu(o[0] + (o[0] == 0 ? 1 : 9), o[1] + 4, 1, 9, or);
            tissu(o[0], o[1] + 13, 16, 3, noir);
            tissu(o[0] + 8, o[1], 4, 4, noir);
        }
        png("entity/douane/agent", peau);
        // aperçu : face avant à plat (tête, buste, bras, jambes), agrandie
        BufferedImage vue = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = vue.createGraphics();
        g.drawImage(peau.getSubimage(8, 8, 8, 8), 4, 0, null);
        g.drawImage(peau.getSubimage(20, 20, 8, 12), 4, 8, null);
        g.drawImage(peau.getSubimage(44, 20, 4, 12), 0, 8, null);
        g.drawImage(peau.getSubimage(36, 52, 4, 12), 12, 8, null);
        g.drawImage(peau.getSubimage(4, 20, 4, 12), 4, 20, null);
        g.drawImage(peau.getSubimage(20, 52, 4, 12), 8, 20, null);
        g.dispose();
        AGENT = vue;
    }

    static BufferedImage AGENT;

    // ---------- battes : l'arme du joueur contre les agents de la douane

    /** Batte du contrebandier (bois, manche bleu nuit) ou batte à impulsion (métal, anneaux bleu glace). Tenue comme une épée. */
    static void batte(String id, boolean impulsion) throws IOException {
        BufferedImage at = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        double[] haut = {0, 0, 4, 7}, bas = {0, 7, 4, 11}, manche = {4, 0, 6, 8}, bague = {6, 0, 10, 1}, bout = {6, 1, 9, 4}, pommeau = {6, 4, 10, 5.5};
        Graphics2D g = zone(at, haut);      // 16 x 28 : le gros du fût
        if (impulsion) {
            g.setPaint(new GradientPaint(0, 0, c(0x3A4868), 16, 0, c(0x121829)));
            g.fill(new Rectangle2D.Double(0, 0, 16, 28));
            for (int y = 3; y < 28; y += 7) {
                g.setColor(c(0x1A4C74));
                g.fill(new Rectangle2D.Double(0, y - 1, 16, 4));
                g.setPaint(new GradientPaint(0, 0, c(0xFFFFFF), 16, 0, c(0x62CFF4)));
                g.fill(new Rectangle2D.Double(0, y, 16, 2));
            }
        } else {
            g.setPaint(new GradientPaint(0, 0, c(0xE7C590), 16, 0, c(0xA8763E)));
            g.fill(new Rectangle2D.Double(0, 0, 16, 28));
            g.setColor(a(0x6B4520, 110));
            for (double[] v : new double[][]{{3, 0, 26}, {7.5, 4, 20}, {11.5, 1, 27}}) {
                g.fill(new Rectangle2D.Double(v[0], v[1], 0.8, v[2]));
            }
            // bandeau peint : bleu nuit, liseré or, une orbite
            g.setColor(c(0x1B3266));
            g.fill(new Rectangle2D.Double(0, 10, 16, 9));
            g.setColor(c(0xE0B864));
            g.fill(new Rectangle2D.Double(0, 10, 16, 1.2));
            g.fill(new Rectangle2D.Double(0, 17.8, 16, 1.2));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new Ellipse2D.Double(4.5, 12.2, 7, 4.6));
            g.setColor(c(0x62CFF4));
            g.fill(new Ellipse2D.Double(6.8, 13.3, 2.4, 2.4));
            // éraflures
            g.setColor(a(0x4A2E12, 150));
            g.fill(new Rectangle2D.Double(2, 3, 4, 0.8));
            g.fill(new Rectangle2D.Double(9, 23, 5, 0.8));
        }
        g.dispose();
        g = zone(at, bas);      // 16 x 16 : le bas du fût, plus fin
        if (impulsion) {
            g.setPaint(new GradientPaint(0, 0, c(0x34405E), 16, 0, c(0x0E1320)));
            g.fill(new Rectangle2D.Double(0, 0, 16, 16));
            g.setColor(c(0x62CFF4));
            g.fill(new Rectangle2D.Double(7, 0, 2, 16));
        } else {
            g.setPaint(new GradientPaint(0, 0, c(0xDDB983), 16, 0, c(0x9C6C38)));
            g.fill(new Rectangle2D.Double(0, 0, 16, 16));
            g.setColor(a(0x6B4520, 110));
            g.fill(new Rectangle2D.Double(5, 0, 0.8, 16));
            g.fill(new Rectangle2D.Double(11, 2, 0.8, 14));
        }
        g.dispose();
        g = zone(at, manche);   // 8 x 32 : ruban de prise en biais
        g.setColor(c(impulsion ? 0x0B0D16 : 0x16264A));
        g.fill(new Rectangle2D.Double(0, 0, 8, 32));
        g.setColor(c(impulsion ? 0x2B3550 : 0x2A4C92));
        for (int y = -8; y < 32; y += 5) {
            g.fill(poly(0, y + 6, 8, y, 8, y + 1.6, 0, y + 7.6));
        }
        g.dispose();
        degrade(at, bague, 0xFFF0C2, 0x8A6A2A, true);
        g = zone(at, bout);     // 12 x 12
        g.setColor(c(impulsion ? 0x1A2238 : 0xC79C5E));
        g.fill(new Rectangle2D.Double(0, 0, 12, 12));
        g.setColor(c(impulsion ? 0x9FE7FF : 0xA8763E));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Double(2.5, 2.5, 7, 7));
        if (impulsion) {
            g.fill(new Ellipse2D.Double(4.5, 4.5, 3, 3));
        }
        g.dispose();
        g = zone(at, pommeau);  // 16 x 6
        g.setPaint(new GradientPaint(0, 0, c(impulsion ? 0x66728A : 0xE0B864), 0, 6, c(impulsion ? 0x1B1F2B : 0x8A6A2A)));
        g.fill(new Rectangle2D.Double(0, 0, 16, 6));
        g.dispose();
        List<Boite> l = new ArrayList<>();
        l.add(boite(7.0, -1.2, 7.0, 9.0, -0.2, 9.0, 0, pommeau, bout, bout));
        l.add(boite(7.35, -0.2, 7.35, 8.65, 6, 8.65, 0, manche, bout, bout));
        l.add(boite(7.1, 6, 7.1, 8.9, 6.7, 8.9, 0, bague, bague, bague));
        l.add(boite(7.1, 6.7, 7.1, 8.9, 10.5, 8.9, 0, bas, bout, bout));
        l.add(boite(6.7, 10.5, 6.7, 9.3, 17, 9.3, 0, haut, bout, bout));
        l.add(boite(7.0, 17, 7.0, 9.0, 17.6, 9.0, 0, impulsion ? bague : bas, bout, bout));
        // tenue comme une épée : mêmes poses que « handheld », recalées pour un modèle droit (et non en diagonale)
        modele(id, l, at, "{\n"
                + "    \"gui\": {\"rotation\": [0, 0, -40], \"translation\": [0, -0.5, 0], \"scale\": [0.82, 0.82, 0.82]},\n"
                + "    \"ground\": {\"rotation\": [0, 0, 90], \"translation\": [0, 2, 0], \"scale\": [0.6, 0.6, 0.6]},\n"
                + "    \"thirdperson_righthand\": {\"rotation\": [0, -90, 10], \"translation\": [0, 4, 0.5], \"scale\": [0.9, 0.9, 0.9]},\n"
                + "    \"thirdperson_lefthand\": {\"rotation\": [0, 90, -10], \"translation\": [0, 4, 0.5], \"scale\": [0.9, 0.9, 0.9]},\n"
                + "    \"firstperson_righthand\": {\"rotation\": [0, -90, -20], \"translation\": [1.13, 3.2, 1.13], \"scale\": [0.75, 0.75, 0.75]},\n"
                + "    \"firstperson_lefthand\": {\"rotation\": [0, 90, 20], \"translation\": [1.13, 3.2, 1.13], \"scale\": [0.75, 0.75, 0.75]},\n"
                + "    \"fixed\": {\"rotation\": [0, 0, -45], \"translation\": [0, 0, 0], \"scale\": [0.9, 0.9, 0.9]}\n  }");
    }
}
