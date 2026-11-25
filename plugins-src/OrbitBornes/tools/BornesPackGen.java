import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

/**
 * Pack de ressources des bornes d'arcade (OrbitBornes) : cristaux de CRISTAUX D'ORBIT (6 couleurs, chacune avec sa forme,
 * + variantes spéciales), icônes de niveaux, boutons, drones et vaisseaux de DÉFENSE ORBITALE, fond de l'écran HD et police.
 * Tout est dessiné ici (aucune image reprise d'un autre jeu). Écrit aussi src/main/resources/hud-font.txt et des aperçus.
 *
 * Géométrie de l'écran (comme le casino) : coffre 6 lignes, la case (c, r) est en x = 8 + 18c, y = 18 + 18r ;
 * le titre s'écrit en (8, 6) et une glyphe d'« ascent » A a son haut en y = 13 - A. Fond : 176 x 140 en 2 x 2 tuiles.
 *
 * Usage : java BornesPackGen.java <dossier_pack> <fichier_largeurs> <dossier_apercus>
 */
public class BornesPackGen {

    static final String NS = "orbitbornes";
    static File pack;
    static final StringBuilder FONT = new StringBuilder();
    static final List<String> ITEMS = new ArrayList<>();

    /** Haut (unités GUI) de chaque ligne de texte : titre, puis valeurs du panneau de droite (niveau, score, objectif, coups, étoiles). */
    static final int[] LINE_TOP = {5, 27, 45, 63, 81, 99};
    static final String CHARSET = " !\"%'()+,-./0123456789:;?ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
            + "àâçéèêëîïôùûÀÉÈÊÇ·×▶»«✦→★☆";
    static final String SYMBOLS = "▶»«✦→·×★☆";

    static final Color GOLD = new Color(0xE0B864), GOLD_HI = new Color(0xFFF0C2), GOLD_LO = new Color(0x8A6A2A);
    static final Color NAVY = new Color(0x0B1020), NAVY2 = new Color(0x141B33), BLUE = new Color(0x3A7FFF), ICE = new Color(0x62CFF4);

    // cristaux : base, clair, sombre ; forme
    static final int[][] GEM = {
            {0xE0484A, 0xFFA3A3, 0x6E1220}, // rubis : rond
            {0xE0B864, 0xFFF0C2, 0x7A5A1E}, // ambre : hexagone
            {0x2EC46A, 0xA6FFC8, 0x0C5028}, // émeraude : carré taillé
            {0x3A7FFF, 0xB0D0FF, 0x0F2A72}, // saphir : losange
            {0xA65BFF, 0xE4CCFF, 0x45168A}, // améthyste : triangle
            {0x62CFF4, 0xF0FCFF, 0x15627E}, // glace : étoile à 4 branches
    };

    public static void main(String[] args) throws Exception {
        pack = new File(args[0]);
        File widths = new File(args[1]);
        File previews = new File(args[2]);
        previews.mkdirs();
        write("pack.mcmeta", "{\n  \"pack\": {\n    \"description\": \"§bORBIT §7- bornes d'arcade\",\n    \"min_format\": 97,\n    \"max_format\": 97\n  }\n}\n");

        // ---------------- cristaux
        for (int i = 0; i < GEM.length; i++) {
            for (String v : new String[]{"", "_sel", "_h", "_v", "_b", "_b_sel", "_h_sel", "_v_sel"}) {
                String id = "gem_" + i + v;
                png("item/" + id, gem(i, v.contains("sel"), v.startsWith("_h") ? 1 : v.startsWith("_v") ? 2 : v.startsWith("_b") ? 3 : 0));
                item(id, id, 1.0);
            }
        }
        png("item/nova", nova(false));
        item("nova", "nova", 1.0);
        png("item/nova_sel", nova(true));
        item("nova_sel", "nova_sel", 1.0);
        png("item/burst", burst());
        item("burst", "burst", 1.05);
        // niveaux
        png("item/lvl_lock", levelIcon(-1));
        item("lvl_lock", "lvl_lock", 1.0);
        for (int s = 0; s <= 3; s++) {
            png("item/lvl_" + s, levelIcon(s));
            item("lvl_" + s, "lvl_" + s, 1.0);
        }
        // boutons
        png("item/btn_quitter", square("✕", new Color(0x5A6270)));
        item("btn_quitter", "btn_quitter", 1.0);
        png("item/btn_rejouer", square("↻", new Color(0x3A7FFF)));
        item("btn_rejouer", "btn_rejouer", 1.0);
        png("item/btn_suivant", square("▶", new Color(0x2E9E55)));
        item("btn_suivant", "btn_suivant", 1.0);
        png("item/btn_niveaux", square("☰", new Color(0xA65BFF)));
        item("btn_niveaux", "btn_niveaux", 1.0);

        // ---------------- DÉFENSE ORBITALE (objets affichés dans le monde)
        for (int f = 1; f <= 2; f++) {
            png("item/drone_a_" + f, droneA(f == 2));
            item("drone_a_" + f, "drone_a_" + f, 1.0);
            png("item/drone_b_" + f, droneB(f == 2));
            item("drone_b_" + f, "drone_b_" + f, 1.0);
            png("item/drone_c_" + f, droneC(f == 2));
            item("drone_c_" + f, "drone_c_" + f, 1.0);
        }
        png("item/ship", ship());
        item("ship", "ship", 1.0);
        png("item/mother", mother());
        item("mother", "mother", 1.0);
        png("item/shot", shot());
        item("shot", "shot", 1.0);
        png("item/bolt", bolt());
        item("bolt", "bolt", 1.0);
        png("item/boom", burstColor(new Color(0xFFB000), new Color(0xFF4FD8)));
        item("boom", "boom", 1.0);

        // ---------------- police : espaces, fond, texte
        FONT.append("{\n  \"providers\": [\n");
        FONT.append("    {\"type\": \"space\", \"advances\": {");
        int[] pow = {1, 2, 4, 8, 16, 32, 64, 128, 256};
        List<String> sp = new ArrayList<>();
        for (int i = 0; i < pow.length; i++) {
            sp.add("\"" + u(0xF801 + i) + "\": -" + pow[i]);
            sp.add("\"" + u(0xF811 + i) + "\": " + pow[i]);
        }
        sp.add("\"" + u(0xF820) + "\": 3");
        FONT.append(String.join(", ", sp)).append("}},\n");
        BufferedImage bg = background();
        png("font/bg_cristaux", bg);
        bgProviders("bg_cristaux", 0xE600);
        int[] widthTable = textFont();
        FONT.setLength(FONT.length() - 2);
        FONT.append("\n  ]\n}\n");
        write("assets/" + NS + "/font/hud.json", FONT.toString());

        StringBuilder w = new StringBuilder("# Généré par tools/BornesPackGen.java : caractère <tab> avance (unités GUI)\n");
        for (int i = 0; i < CHARSET.length(); i++) {
            w.append(CHARSET.codePointAt(i)).append('\t').append(widthTable[i]).append('\n');
        }
        Files.writeString(widths.toPath(), w.toString(), StandardCharsets.UTF_8);

        preview(bg, new File(previews, "bornes-cristaux.png"));
        sheet(new File(previews, "bornes-sprites.png"));
        System.out.println("OK pack bornes : " + ITEMS.size() + " objets");
    }

    // ================================================================== utilitaires

    static String u(int cp) {
        return String.format("\\u%04x", cp);
    }

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

    static BufferedImage read(String path) throws IOException {
        return ImageIO.read(new File(pack, "assets/" + NS + "/textures/" + path + ".png"));
    }

    /** Objet : modèle plat ; dans les menus, agrandi de {@code scale}. */
    static void item(String id, String texture, double scale) throws IOException {
        write("assets/" + NS + "/items/" + id + ".json", "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
                + NS + ":item/" + id + "\"\n  }\n}\n");
        String s = String.format(Locale.ROOT, "%.3f", scale);
        write("assets/" + NS + "/models/item/" + id + ".json", "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\"layer0\": \""
                + NS + ":item/" + texture + "\"},\n  \"gui_light\": \"front\",\n  \"display\": {\n    \"gui\": {\"rotation\": [0, 0, 0], \"translation\": [0, 0, 0], \"scale\": ["
                + s + ", " + s + ", 1]},\n    \"fixed\": {\"rotation\": [0, 0, 0], \"translation\": [0, 0, 0], \"scale\": [1, 1, 1]}\n  }\n}\n");
        ITEMS.add(id);
    }

    static Graphics2D g(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g;
    }

    static Font font(int style, float size) {
        return new Font("Segoe UI", style, 1).deriveFont(style, size);
    }

    static Font symbols(float size) {
        return new Font("Segoe UI Symbol", Font.PLAIN, 1).deriveFont(size);
    }

    static void center(Graphics2D g, String s, float cx, float baseline) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2f, baseline);
    }

    static Color c(int rgb) {
        return new Color(rgb);
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    static Path2D polygon(int n, double cx, double cy, double r, double rot) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < n; i++) {
            double a = rot + i * 2 * Math.PI / n;
            double x = cx + r * Math.cos(a), y = cy + r * Math.sin(a);
            if (i == 0) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        p.closePath();
        return p;
    }

    static Path2D star(int points, double cx, double cy, double r1, double r2, double rot) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < points * 2; i++) {
            double r = i % 2 == 0 ? r1 : r2;
            double a = rot + i * Math.PI / points;
            double x = cx + r * Math.cos(a), y = cy + r * Math.sin(a);
            if (i == 0) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        p.closePath();
        return p;
    }

    // ================================================================== cristaux (64 x 64)

    static Shape gemShape(int kind) {
        double c = 32;
        return switch (kind) {
            case 0 -> new Ellipse2D.Double(7, 7, 50, 50);
            case 1 -> polygon(6, c, c, 27, Math.PI / 6);
            case 2 -> polygon(8, c, c, 27, Math.PI / 8);
            case 3 -> polygon(4, c, c + 1, 29, -Math.PI / 2);
            case 4 -> polygon(3, c, c + 5, 31, -Math.PI / 2);
            default -> star(4, c, c, 29, 13, -Math.PI / 2);
        };
    }

    /** kind : 0 normal, 1 rayé horizontal, 2 rayé vertical, 3 « pulsar » (explose en 3 x 3). */
    static BufferedImage gem(int i, boolean selected, int special) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        if (selected) {
            g.setPaint(new RadialGradientPaint(new Point2D.Float(32, 32), 32, new float[]{0.55f, 1f},
                    new Color[]{alpha(GOLD_HI, 200), alpha(GOLD, 0)}));
            g.fill(new Ellipse2D.Float(0, 0, 64, 64));
            g.setStroke(new BasicStroke(2.5f));
            g.setColor(GOLD);
            g.draw(new RoundRectangle2D.Float(2, 2, 60, 60, 14, 14));
        }
        if (special == 3) {
            // halo d'énergie autour
            g.setPaint(new RadialGradientPaint(new Point2D.Float(32, 32), 31, new float[]{0.6f, 0.85f, 1f},
                    new Color[]{alpha(Color.WHITE, 0), alpha(c(GEM[i][1]), 220), alpha(c(GEM[i][0]), 0)}));
            g.fill(new Ellipse2D.Float(1, 1, 62, 62));
        }
        Shape s = gemShape(i);
        Color base = c(GEM[i][0]), hi = c(GEM[i][1]), lo = c(GEM[i][2]);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(26, 22), 38, new float[]{0f, 0.55f, 1f}, new Color[]{hi, base, lo}));
        g.fill(s);
        g.setClip(s);
        // facettes : rayons du centre vers les bords
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(alpha(Color.WHITE, 70));
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8;
            g.draw(new Line2D.Double(32, 32, 32 + 40 * Math.cos(a), 32 + 40 * Math.sin(a)));
        }
        g.setColor(alpha(lo, 120));
        g.fill(polygon(Math.max(3, i == 0 ? 8 : i == 4 ? 3 : i == 3 ? 4 : i == 1 ? 6 : 8), 32, 33, 11, i == 4 ? -Math.PI / 2 : 0));
        g.setColor(alpha(Color.WHITE, 60));
        g.fill(polygon(i == 4 ? 3 : 6, 32, 33, 8, -Math.PI / 2));
        if (special == 1 || special == 2) {
            for (int k = -1; k <= 1; k++) {
                g.setColor(alpha(Color.WHITE, 170));
                if (special == 1) {
                    g.fill(new Rectangle2D.Double(0, 32 + k * 12 - 2.5, 64, 5));
                } else {
                    g.fill(new Rectangle2D.Double(32 + k * 12 - 2.5, 0, 5, 64));
                }
            }
        }
        // reflet
        g.setColor(alpha(Color.WHITE, 150));
        g.fill(new Ellipse2D.Double(18, 13, 14, 8));
        g.setClip(null);
        g.setStroke(new BasicStroke(2.4f));
        g.setColor(lo.darker());
        g.draw(s);
        g.setStroke(new BasicStroke(1f));
        g.setColor(alpha(hi, 180));
        g.draw(AffineTransform.getTranslateInstance(-0.6, -0.6).createTransformedShape(s));
        if (special == 3) {
            g.setColor(Color.WHITE);
            g.fill(star(4, 32, 32, 9, 3, 0));
        }
        if (special == 1 || special == 2) {
            // flèches
            g.setColor(Color.WHITE);
            for (int d : new int[]{-1, 1}) {
                Path2D ar = new Path2D.Double();
                if (special == 1) {
                    ar.moveTo(32 + d * 30, 32);
                    ar.lineTo(32 + d * 23, 26);
                    ar.lineTo(32 + d * 23, 38);
                } else {
                    ar.moveTo(32, 32 + d * 30);
                    ar.lineTo(26, 32 + d * 23);
                    ar.lineTo(38, 32 + d * 23);
                }
                ar.closePath();
                g.fill(ar);
            }
        }
        g.dispose();
        return img;
    }

    /** NOVA : noyau d'étoile multicolore, efface toute une couleur. */
    static BufferedImage nova(boolean selected) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        if (selected) {
            g.setStroke(new BasicStroke(2.5f));
            g.setColor(GOLD);
            g.draw(new RoundRectangle2D.Float(2, 2, 60, 60, 14, 14));
        }
        g.setPaint(new RadialGradientPaint(new Point2D.Float(32, 32), 30, new float[]{0f, 0.5f, 1f},
                new Color[]{Color.WHITE, alpha(c(0xC79BFF), 200), alpha(c(0x3A7FFF), 0)}));
        g.fill(new Ellipse2D.Float(2, 2, 60, 60));
        for (int k = 0; k < 6; k++) {
            g.setColor(c(GEM[k][0]));
            g.fill(new Arc2D.Double(12, 12, 40, 40, k * 60, 60, Arc2D.PIE));
        }
        g.setColor(Color.WHITE);
        g.fill(star(8, 32, 32, 13, 5, 0));
        g.setStroke(new BasicStroke(2f));
        g.setColor(c(0x1A1030));
        g.draw(new Ellipse2D.Double(12, 12, 40, 40));
        g.dispose();
        return img;
    }

    static BufferedImage burst() {
        return burstColor(GOLD_HI, ICE);
    }

    static BufferedImage burstColor(Color in, Color out) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(32, 32), 30, new float[]{0f, 0.4f, 1f},
                new Color[]{Color.WHITE, alpha(in, 220), alpha(out, 0)}));
        g.fill(star(8, 32, 32, 30, 12, 0.2));
        g.setColor(Color.WHITE);
        g.fill(star(4, 32, 32, 16, 4, 0));
        Random r = new Random(3);
        for (int k = 0; k < 10; k++) {
            double a = r.nextDouble() * Math.PI * 2, d = 18 + r.nextDouble() * 10;
            g.setColor(alpha(out, 230));
            g.fill(new Ellipse2D.Double(32 + d * Math.cos(a) - 2, 32 + d * Math.sin(a) - 2, 4, 4));
        }
        g.dispose();
        return img;
    }

    /** Planète du niveau ; s = étoiles obtenues (-1 : verrouillé). */
    static BufferedImage levelIcon(int s) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        boolean locked = s < 0;
        Color a = locked ? c(0x3A4050) : c(0x3A7FFF), b = locked ? c(0x8A92A0) : c(0x62CFF4);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(25, 20), 34, new float[]{0f, 1f}, new Color[]{b, a.darker()}));
        g.fill(new Ellipse2D.Double(12, 6, 40, 40));
        g.setStroke(new BasicStroke(3f));
        g.setColor(locked ? c(0x5A6270) : GOLD);
        g.draw(new Ellipse2D.Double(4, 20, 56, 12));
        if (locked) {
            g.setColor(c(0xD7DCE2));
            g.fill(new RoundRectangle2D.Double(24, 22, 16, 13, 3, 3));
            g.setStroke(new BasicStroke(3f));
            g.draw(new Arc2D.Double(26, 13, 12, 16, 0, 180, Arc2D.OPEN));
        } else {
            for (int k = 0; k < 3; k++) {
                g.setColor(k < s ? GOLD_HI : c(0x2A3040));
                Shape st = star(5, 14 + k * 18, 54, 8, 3.4, -Math.PI / 2);
                g.fill(st);
                g.setStroke(new BasicStroke(1.2f));
                g.setColor(k < s ? GOLD_LO : c(0x5A6270));
                g.draw(st);
            }
        }
        g.dispose();
        return img;
    }

    static BufferedImage square(String icon, Color col) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setPaint(new GradientPaint(0, 4, col.brighter(), 0, 60, col.darker()));
        g.fill(new RoundRectangle2D.Float(4, 4, 56, 56, 16, 16));
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(GOLD);
        g.draw(new RoundRectangle2D.Float(4, 4, 56, 56, 16, 16));
        g.setColor(Color.WHITE);
        g.setFont(symbols(34f));
        center(g, icon, 32, 44);
        g.dispose();
        return img;
    }

    // ================================================================== DÉFENSE ORBITALE (64 x 64, dessins originaux)

    /** Sentinelle : œil unique dans une coque ronde, antennes qui battent. */
    static BufferedImage droneA(boolean alt) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(ICE);
        int dy = alt ? 6 : 0;
        g.draw(new Line2D.Double(20, 22, 10, 8 + dy));
        g.draw(new Line2D.Double(44, 22, 54, 8 + dy));
        g.draw(new Line2D.Double(18, 44, 10 + (alt ? 4 : 0), 58));
        g.draw(new Line2D.Double(46, 44, 54 - (alt ? 4 : 0), 58));
        g.setPaint(new RadialGradientPaint(new Point2D.Float(28, 26), 26, new float[]{0f, 1f}, new Color[]{c(0xB0F0FF), c(0x1A6A8A)}));
        g.fill(new Ellipse2D.Double(12, 16, 40, 34));
        g.setColor(c(0x0B1020));
        g.fill(new Ellipse2D.Double(22, 24, 20, 18));
        g.setColor(c(0xFF4FD8));
        g.fill(new Ellipse2D.Double(alt ? 30 : 26, 28, 9, 9));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(alt ? 31 : 27, 29, 3, 3));
        g.dispose();
        return img;
    }

    /** Méduse orbitale : dôme rose et filaments qui ondulent. */
    static BufferedImage droneB(boolean alt) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c(0xC79BFF));
        for (int k = 0; k < 4; k++) {
            double x = 16 + k * 10.5;
            Path2D t = new Path2D.Double();
            t.moveTo(x, 36);
            double w = (alt ? -1 : 1) * (k % 2 == 0 ? 4 : -4);
            t.curveTo(x + w, 44, x - w, 50, x + w * 0.5, 60);
            g.draw(t);
        }
        g.setPaint(new GradientPaint(0, 8, c(0xFFB3F0), 0, 40, c(0xB02A8A)));
        g.fill(new Arc2D.Double(8, 8, 48, 56, 0, 180, Arc2D.CHORD));
        g.setColor(c(0x5A1050));
        g.fill(new Rectangle2D.Double(8, 34, 48, 4));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(20, 20, 7, 7));
        g.fill(new Ellipse2D.Double(37, 20, 7, 7));
        g.setColor(alpha(Color.WHITE, 120));
        g.fill(new Ellipse2D.Double(16, 12, 16, 6));
        g.dispose();
        return img;
    }

    /** Croiseur : coque anguleuse verte, ailes qui montent et descendent. */
    static BufferedImage droneC(boolean alt) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        double wy = alt ? -8 : 0;
        Path2D wing = new Path2D.Double();
        wing.moveTo(32, 30);
        wing.lineTo(4, 22 + wy);
        wing.lineTo(8, 40 + wy);
        wing.lineTo(32, 40);
        wing.lineTo(56, 40 + wy);
        wing.lineTo(60, 22 + wy);
        wing.closePath();
        g.setColor(c(0x1F7A44));
        g.fill(wing);
        Path2D body = new Path2D.Double();
        body.moveTo(32, 8);
        body.lineTo(46, 30);
        body.lineTo(40, 54);
        body.lineTo(24, 54);
        body.lineTo(18, 30);
        body.closePath();
        g.setPaint(new GradientPaint(0, 8, c(0xA6FFC8), 0, 54, c(0x2EC46A)));
        g.fill(body);
        g.setStroke(new BasicStroke(2f));
        g.setColor(c(0x0C5028));
        g.draw(body);
        g.setColor(c(0xFFB000));
        g.fill(new Rectangle2D.Double(27, 30, 10, 5));
        g.dispose();
        return img;
    }

    /** Vaisseau du joueur : flèche bleue ORBIT, cockpit doré, réacteur. */
    static BufferedImage ship() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(32, 58), 10, new float[]{0f, 1f}, new Color[]{Color.WHITE, alpha(ICE, 0)}));
        g.fill(new Ellipse2D.Double(22, 48, 20, 16));
        Path2D hull = new Path2D.Double();
        hull.moveTo(32, 4);
        hull.lineTo(42, 28);
        hull.lineTo(60, 46);
        hull.lineTo(60, 54);
        hull.lineTo(40, 50);
        hull.lineTo(32, 56);
        hull.lineTo(24, 50);
        hull.lineTo(4, 54);
        hull.lineTo(4, 46);
        hull.lineTo(22, 28);
        hull.closePath();
        g.setPaint(new GradientPaint(0, 4, c(0xB0D0FF), 0, 56, BLUE.darker()));
        g.fill(hull);
        g.setStroke(new BasicStroke(2f));
        g.setColor(c(0x0B1020));
        g.draw(hull);
        g.setColor(GOLD);
        g.fill(new Ellipse2D.Double(27, 20, 10, 16));
        g.setColor(GOLD_HI);
        g.fill(new Ellipse2D.Double(29, 22, 4, 6));
        g.dispose();
        return img;
    }

    /** Vaisseau-mère : soucoupe annelée or et violet. */
    static BufferedImage mother() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setPaint(new GradientPaint(0, 14, c(0xE4CCFF), 0, 30, c(0x6A2AC0)));
        g.fill(new Ellipse2D.Double(18, 14, 28, 22));
        g.setPaint(new GradientPaint(0, 26, GOLD_HI, 0, 44, GOLD_LO));
        g.fill(new Ellipse2D.Double(2, 26, 60, 16));
        g.setColor(c(0x0B1020));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Ellipse2D.Double(2, 26, 60, 16));
        for (int k = 0; k < 5; k++) {
            g.setColor(k % 2 == 0 ? c(0xFF4FD8) : ICE);
            g.fill(new Ellipse2D.Double(9 + k * 10.5, 31, 6, 6));
        }
        g.dispose();
        return img;
    }

    static BufferedImage shot() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setPaint(new GradientPaint(0, 4, Color.WHITE, 0, 60, alpha(ICE, 0)));
        g.fill(new RoundRectangle2D.Double(27, 4, 10, 56, 10, 10));
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(30, 4, 4, 30, 4, 4));
        g.dispose();
        return img;
    }

    static BufferedImage bolt() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        Path2D z = new Path2D.Double();
        z.moveTo(36, 2);
        z.lineTo(22, 26);
        z.lineTo(34, 28);
        z.lineTo(24, 62);
        z.lineTo(44, 24);
        z.lineTo(32, 22);
        z.lineTo(42, 2);
        z.closePath();
        g.setColor(c(0xFF6A33));
        g.fill(z);
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(c(0xFFE08A));
        g.draw(z);
        g.dispose();
        return img;
    }

    // ================================================================== écran HD de CRISTAUX

    static BufferedImage background() {
        int S = 2;
        BufferedImage img = new BufferedImage(176 * S, 140 * S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.scale(S, S);
        g.setColor(NAVY);
        g.fill(new RoundRectangle2D.Float(0, 0, 176, 140, 8, 8));
        g.setColor(NAVY2);
        g.fill(new Rectangle2D.Float(3, 3, 170, 13));
        g.setColor(GOLD_LO);
        g.fill(new Rectangle2D.Float(3, 16, 170, 0.8f));
        // plateau 7 x 6 : fond de nébuleuse, cases alternées
        Shape board = new RoundRectangle2D.Float(6, 17, 128, 109, 6, 6);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(70, 70), 90, new float[]{0f, 1f},
                new Color[]{c(0x2A1850), c(0x0E0A24)}));
        g.fill(board);
        g.setClip(board);
        Random rnd = new Random(11);
        for (int i = 0; i < 160; i++) { // étoiles
            g.setColor(new Color(255, 255, 255, 30 + rnd.nextInt(90)));
            float s = rnd.nextFloat() * 0.8f + 0.3f;
            g.fill(new Ellipse2D.Float(6 + rnd.nextFloat() * 128, 17 + rnd.nextFloat() * 109, s, s));
        }
        for (int r = 0; r < 6; r++) {
            for (int col = 0; col < 7; col++) {
                float x = 8 + 18 * col - 0.5f, y = 18 + 18 * r - 0.5f;
                g.setColor((r + col) % 2 == 0 ? new Color(255, 255, 255, 22) : new Color(120, 90, 255, 26));
                g.fill(new RoundRectangle2D.Float(x, y, 17, 17, 4, 4));
            }
        }
        g.setClip(null);
        g.setStroke(new BasicStroke(1f));
        g.setColor(GOLD);
        g.draw(new RoundRectangle2D.Float(6.5f, 17.5f, 127, 108, 6, 6));
        // panneau de droite : 5 étiquettes, valeurs écrites par le plugin, 2 boutons en bas
        Shape side = new RoundRectangle2D.Float(135, 17, 36, 109, 6, 6);
        g.setPaint(new GradientPaint(0, 17, NAVY2, 0, 126, c(0x0A0E1C)));
        g.fill(side);
        g.setColor(GOLD_LO);
        g.draw(new RoundRectangle2D.Float(135.5f, 17.5f, 35, 108, 6, 6));
        String[] labels = {"NIVEAU", "SCORE", "OBJECTIF", "COUPS", "ÉTOILES"};
        g.setFont(font(Font.BOLD, 5.2f));
        for (int r = 0; r < labels.length; r++) {
            g.setColor(GOLD);
            center(g, labels[r], 153, 18 + 18 * r + 6.2f);
            if (r > 0) {
                g.setColor(alpha(GOLD_LO, 160));
                g.fill(new Rectangle2D.Float(139, 18 + 18 * r - 0.6f, 28, 0.5f));
            }
        }
        // plaque « Inventaire » (#404040 rend le libellé du jeu invisible)
        g.setColor(new Color(0x404040));
        g.fill(new Rectangle2D.Float(3, 126, 170, 14));
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, 175, 139, 8, 8));
        g.dispose();
        return img;
    }

    static void bgProviders(String file, int base) {
        int[] ascents = {13, 13 - 70};
        for (int row = 0; row < 2; row++) {
            String[] chars = new String[2];
            for (int r = 0; r < 2; r++) {
                chars[r] = r == row ? u(base + row * 2) + u(base + row * 2 + 1) : u(0) + u(0);
            }
            FONT.append("    {\"type\": \"bitmap\", \"file\": \"" + NS + ":font/" + file + ".png\", \"height\": 70, \"ascent\": ")
                    .append(ascents[row]).append(", \"chars\": [\"").append(chars[0]).append("\", \"").append(chars[1]).append("\"]},\n");
        }
    }

    static int[] textFont() throws IOException {
        int cols = 16, rows = (CHARSET.length() + cols - 1) / cols, cell = 16;
        BufferedImage atlas = new BufferedImage(cols * cell, rows * cell, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(atlas);
        Font f = font(Font.BOLD, 13.5f);
        Font sym = symbols(12.5f);
        int[] adv = new int[CHARSET.length()];
        for (int i = 0; i < CHARSET.length(); i++) {
            String ch = CHARSET.substring(i, i + 1);
            int cx = (i % cols) * cell, cy = (i / cols) * cell;
            boolean isSym = SYMBOLS.contains(ch);
            BufferedImage one = new BufferedImage(cell, cell, BufferedImage.TYPE_INT_ARGB);
            Graphics2D o = g(one);
            o.setFont(isSym ? sym : f);
            o.setColor(Color.WHITE);
            o.drawString(ch, 0, 12);
            o.dispose();
            int maxX = -1;
            for (int x = 0; x < cell; x++) {
                for (int y = 0; y < cell; y++) {
                    if ((one.getRGB(x, y) >>> 24) > 40) {
                        maxX = Math.max(maxX, x);
                    }
                }
            }
            g.drawImage(one, cx, cy, null);
            adv[i] = ch.equals(" ") ? 3 : (int) (0.5 + (maxX + 1) * 0.5) + 1;
        }
        g.dispose();
        png("font/text", atlas);
        for (int line = 0; line < LINE_TOP.length; line++) {
            int base = 0xE700 + line * 0x100;
            StringBuilder rowsJson = new StringBuilder();
            for (int r = 0; r < rows; r++) {
                StringBuilder row = new StringBuilder();
                for (int c = 0; c < cols; c++) {
                    int i = r * cols + c;
                    row.append(i < CHARSET.length() && !CHARSET.substring(i, i + 1).equals(" ") ? u(base + i) : u(0));
                }
                rowsJson.append(r == 0 ? "" : ", ").append('"').append(row).append('"');
            }
            int ascent = 13 - LINE_TOP[line];
            FONT.append("    {\"type\": \"bitmap\", \"file\": \"" + NS + ":font/text.png\", \"height\": 8, \"ascent\": ")
                    .append(ascent).append(", \"chars\": [").append(rowsJson).append("]},\n");
        }
        return adv;
    }

    // ================================================================== aperçus

    static void put(Graphics2D g, int S, String texture, int col, int row) throws IOException {
        BufferedImage t = read("item/" + texture);
        g.drawImage(t, (8 + 18 * col) * S, (18 + 18 * row) * S, 16 * S, 16 * S, null);
    }

    static void preview(BufferedImage bg, File out) throws IOException {
        int S = 3;
        BufferedImage img = new BufferedImage(176 * S, 222 * S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setColor(new Color(0xC6C6C6));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(bg, 0, 0, 176 * S, 140 * S, null);
        Random r = new Random(5);
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 7; col++) {
                int k = r.nextInt(6);
                String v = r.nextInt(14) == 0 ? "_h" : r.nextInt(18) == 0 ? "_b" : "";
                put(g, S, (row == 2 && col == 3) ? "gem_" + k + "_sel" : (row == 4 && col == 1) ? "nova" : "gem_" + k + v, col, row);
            }
        }
        put(g, S, "btn_rejouer", 7, 5);
        put(g, S, "btn_quitter", 8, 5);
        g.setFont(new Font("Segoe UI", Font.BOLD, 8 * S - 2));
        g.setColor(GOLD);
        g.drawString("CRISTAUX D'ORBIT · NIVEAU 4", 8 * S, (LINE_TOP[0] + 7) * S);
        String[] vals = {"4", "1 840", "2 500", "12", "★☆☆"};
        g.setColor(Color.WHITE);
        for (int l = 0; l < vals.length; l++) {
            center(g, vals[l], 153 * S, (LINE_TOP[l + 1] + 7) * S);
        }
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    static void sheet(File out) throws IOException {
        String[] ids = {"drone_a_1", "drone_a_2", "drone_b_1", "drone_b_2", "drone_c_1", "drone_c_2", "ship", "mother", "shot", "bolt", "boom",
                "lvl_lock", "lvl_0", "lvl_1", "lvl_2", "lvl_3", "btn_suivant", "btn_niveaux", "burst", "nova_sel"};
        BufferedImage img = new BufferedImage(10 * 72, 2 * 72, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setColor(NAVY);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < ids.length; i++) {
            g.drawImage(read("item/" + ids[i]), (i % 10) * 72 + 4, (i / 10) * 72 + 4, null);
        }
        g.dispose();
        ImageIO.write(img, "png", out);
    }
}
