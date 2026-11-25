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
 * Pack de ressources du Siège Orbital : sprites des unités (version bleue et rouge) et icônes des cartes,
 * dessinés ici (aucune image reprise d'un autre jeu). Les unités sont des ItemDisplay tournés vers la caméra.
 *
 * Usage : java RoyalePackGen.java <dossier_pack> <dossier_apercus>
 */
public class RoyalePackGen {

    static final String NS = "orbitroyale";
    static File pack;
    static final int S = 64;

    static final String[] UNITS = {"sentinelle", "canonnier", "essaim", "chasseur", "tourelle", "drones", "colosse", "chevalier", "mage", "portail"};
    static final String[] CARDS = {"sentinelle", "canonnier", "essaim", "chasseur", "frappe", "tourelle", "givre", "drones", "colosse", "chevalier", "mage", "portail"};
    static final Map<String, Color> TYPE = new HashMap<>();

    static final Color GOLD = new Color(0xE0B864), ICE = new Color(0x62CFF4), BLUE = new Color(0x3A7FFF), NAVY = new Color(0x0B1020);
    static final Color STEEL = new Color(0xB8C4D6), STEEL_D = new Color(0x5B6678), VOID = new Color(0x3B1E5E), VOID_L = new Color(0x8A4FD8);

    public static void main(String[] args) throws Exception {
        pack = new File(args[0]);
        File previews = new File(args[1]);
        previews.mkdirs();
        TYPE.put("frappe", new Color(0xFF8A4A));
        TYPE.put("givre", new Color(0xFF8A4A));
        TYPE.put("tourelle", GOLD);
        TYPE.put("portail", GOLD);
        TYPE.put("chasseur", ICE);
        TYPE.put("drones", ICE);
        write("pack.mcmeta", "{\n  \"pack\": {\n    \"description\": \"§6ORBIT §7- Siège Orbital\",\n    \"min_format\": 97,\n    \"max_format\": 97\n  }\n}\n");

        List<BufferedImage> sheet = new ArrayList<>();
        for (String id : UNITS) {
            for (int team = 0; team < 2; team++) {
                BufferedImage img = unit(id, team);
                String name = "unit_" + id + (team == 0 ? "_b" : "_r");
                png(name, img);
                item(name);
                sheet.add(img);
            }
        }
        for (String id : CARDS) {
            BufferedImage card = card(id);
            png("card_" + id, card);
            item("card_" + id);
            png("card_" + id + "_off", dim(card));
            item("card_" + id + "_off");
            sheet.add(card);
        }
        // planche d'aperçu
        int cols = 8, rows = (sheet.size() + cols - 1) / cols, z = 2;
        BufferedImage prev = new BufferedImage(cols * S * z, rows * S * z, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = prev.createGraphics();
        g.setColor(new Color(0x1A2030));
        g.fillRect(0, 0, prev.getWidth(), prev.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int i = 0; i < sheet.size(); i++) {
            g.drawImage(sheet.get(i), (i % cols) * S * z, (i / cols) * S * z, S * z, S * z, null);
        }
        g.dispose();
        ImageIO.write(prev, "png", new File(previews, "siege-sprites.png"));
        System.out.println("Pack du Siege : " + (UNITS.length * 2 + CARDS.length * 2) + " images.");
    }

    static void write(String path, String content) throws IOException {
        File f = new File(pack, path);
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
    }

    static void png(String name, BufferedImage img) throws IOException {
        File f = new File(pack, "assets/" + NS + "/textures/item/" + name + ".png");
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }

    static void item(String id) throws IOException {
        write("assets/" + NS + "/items/" + id + ".json", "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
                + NS + ":item/" + id + "\"\n  }\n}\n");
        write("assets/" + NS + "/models/item/" + id + ".json", "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\"layer0\": \""
                + NS + ":item/" + id + "\"},\n  \"gui_light\": \"front\"\n}\n");
    }

    static Graphics2D g(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    static Color team(int t) {
        return t == 0 ? ICE : new Color(0xFF5A5A);
    }

    static Color teamDark(int t) {
        return t == 0 ? BLUE : new Color(0xB02A3A);
    }

    static void outline(Graphics2D g, Shape s, Color fill, Color line, float w) {
        g.setColor(fill);
        g.fill(s);
        g.setColor(line);
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(s);
    }

    static RoundRectangle2D rr(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r, r);
    }

    static Ellipse2D el(double cx, double cy, double rx, double ry) {
        return new Ellipse2D.Double(cx - rx, cy - ry, rx * 2, ry * 2);
    }

    static void glow(Graphics2D g, double cx, double cy, double r, Color c) {
        RadialGradientPaint p = new RadialGradientPaint(new Point2D.Double(cx, cy), (float) r, new float[]{0f, 1f},
                new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), 200), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)});
        g.setPaint(p);
        g.fill(el(cx, cy, r, r));
    }

    /** Socle d'équipe : anneau coloré sous l'unité (lisible vu d'en haut). */
    static void base(Graphics2D g, int t) {
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(el(32, 57, 20, 5));
        g.setColor(team(t));
        g.setStroke(new BasicStroke(2.2f));
        g.draw(el(32, 57, 18, 4.5));
    }

    static BufferedImage unit(String id, int t) {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        Color tc = team(t), td = teamDark(t);
        Color line = new Color(0x10131C);
        switch (id) {
            case "sentinelle" -> {
                base(g, t);
                outline(g, rr(18, 44, 9, 13, 3), STEEL_D, line, 1.6f);           // jambes
                outline(g, rr(37, 44, 9, 13, 3), STEEL_D, line, 1.6f);
                outline(g, rr(14, 22, 36, 26, 9), STEEL, line, 2f);              // corps
                outline(g, rr(22, 8, 20, 16, 7), STEEL, line, 2f);               // tête
                outline(g, rr(25, 13, 14, 5, 3), tc, line, 1.2f);                // visière
                glow(g, 32, 15, 10, tc);
                outline(g, rr(4, 24, 12, 22, 5), td, line, 1.6f);                // bouclier
                outline(g, el(32, 34, 5, 5), tc, line, 1.4f);                     // cœur
            }
            case "canonnier" -> {
                base(g, t);
                outline(g, rr(22, 42, 8, 14, 3), STEEL_D, line, 1.5f);
                outline(g, rr(34, 42, 8, 14, 3), STEEL_D, line, 1.5f);
                outline(g, rr(18, 24, 28, 22, 8), td, line, 2f);
                outline(g, el(32, 16, 9, 9), new Color(0xE8D9C4), line, 2f);
                outline(g, rr(24, 12, 16, 5, 2), GOLD, line, 1f);                 // lunettes
                outline(g, rr(30, 26, 30, 9, 4), STEEL, line, 1.8f);              // canon
                outline(g, rr(54, 24, 7, 13, 3), GOLD, line, 1.4f);
                glow(g, 60, 30, 8, tc);
            }
            case "essaim" -> {
                base(g, t);
                outline(g, el(32, 38, 19, 17), new Color(0xF2F4FA), line, 2f);   // Orbito tout rond
                outline(g, el(32, 44, 14, 9), tc, line, 0f);
                outline(g, el(32, 33, 9, 9), Color.WHITE, line, 1.8f);            // gros œil
                outline(g, el(33, 34, 5, 5), NAVY, NAVY, 0.5f);
                g.setColor(Color.WHITE);
                g.fill(el(35, 32, 1.6, 1.6));
                g.setColor(line);
                g.setStroke(new BasicStroke(2f));
                g.draw(new Line2D.Double(32, 21, 36, 10));                        // antenne
                outline(g, el(37, 9, 3.5, 3.5), GOLD, line, 1.2f);
                glow(g, 37, 9, 7, GOLD);
            }
            case "chasseur" -> {
                g.setColor(new Color(0, 0, 0, 70));
                g.fill(el(32, 58, 16, 4));
                Path2D ship = new Path2D.Double();
                ship.moveTo(32, 4);
                ship.lineTo(44, 30);
                ship.lineTo(60, 44);
                ship.lineTo(42, 44);
                ship.lineTo(36, 52);
                ship.lineTo(28, 52);
                ship.lineTo(22, 44);
                ship.lineTo(4, 44);
                ship.lineTo(20, 30);
                ship.closePath();
                outline(g, ship, STEEL, line, 2f);
                Path2D stripe = new Path2D.Double();
                stripe.moveTo(4, 44);
                stripe.lineTo(20, 34);
                stripe.lineTo(22, 44);
                stripe.closePath();
                outline(g, stripe, tc, line, 1.2f);
                Path2D stripe2 = new Path2D.Double();
                stripe2.moveTo(60, 44);
                stripe2.lineTo(44, 34);
                stripe2.lineTo(42, 44);
                stripe2.closePath();
                outline(g, stripe2, tc, line, 1.2f);
                outline(g, el(32, 26, 5, 9), new Color(0x9FE8FF), line, 1.4f);   // cockpit
                glow(g, 30, 55, 7, new Color(0xFFB34A));
                glow(g, 34, 55, 7, new Color(0xFFB34A));
            }
            case "tourelle" -> {
                base(g, t);
                outline(g, rr(12, 40, 40, 16, 5), STEEL_D, line, 2f);
                outline(g, el(32, 34, 16, 13), STEEL, line, 2f);
                outline(g, rr(30, 20, 28, 8, 4), STEEL, line, 1.8f);
                outline(g, rr(52, 18, 8, 12, 3), GOLD, line, 1.4f);
                outline(g, el(30, 33, 6, 6), tc, line, 1.4f);
                glow(g, 30, 33, 11, tc);
                glow(g, 59, 24, 8, tc);
                g.setColor(GOLD);
                g.fill(rr(14, 49, 36, 3, 2));
            }
            case "drones" -> {
                g.setColor(new Color(0, 0, 0, 70));
                g.fill(el(32, 58, 14, 3.5));
                g.setColor(line);
                g.setStroke(new BasicStroke(3f));
                g.draw(new Line2D.Double(14, 18, 50, 46));
                g.draw(new Line2D.Double(50, 18, 14, 46));
                for (double[] r : new double[][]{{14, 18}, {50, 18}, {14, 46}, {50, 46}}) {
                    outline(g, el(r[0], r[1], 10, 4), new Color(200, 220, 240, 150), line, 1.2f);
                    outline(g, el(r[0], r[1], 2.5, 2.5), STEEL_D, line, 1f);
                }
                outline(g, el(32, 32, 11, 11), STEEL, line, 2f);
                outline(g, el(32, 32, 5, 5), tc, line, 1.2f);
                glow(g, 32, 32, 10, tc);
            }
            case "colosse" -> {
                base(g, t);
                outline(g, rr(14, 40, 14, 18, 5), VOID, line, 2f);
                outline(g, rr(36, 40, 14, 18, 5), VOID, line, 2f);
                outline(g, rr(6, 14, 52, 34, 14), new Color(0x2A1544), line, 2.4f);
                outline(g, rr(0, 20, 12, 28, 6), VOID, line, 2f);                 // bras
                outline(g, rr(52, 20, 12, 28, 6), VOID, line, 2f);
                outline(g, rr(20, 4, 24, 16, 8), new Color(0x2A1544), line, 2f);  // tête
                outline(g, rr(24, 10, 6, 4, 2), tc, tc, 0.5f);
                outline(g, rr(34, 10, 6, 4, 2), tc, tc, 0.5f);
                glow(g, 27, 12, 6, tc);
                glow(g, 37, 12, 6, tc);
                g.setColor(VOID_L);
                g.setStroke(new BasicStroke(1.6f));
                g.draw(new Line2D.Double(18, 24, 26, 36));
                g.draw(new Line2D.Double(44, 26, 38, 40));
                g.draw(new Line2D.Double(30, 22, 34, 30));
            }
            case "chevalier" -> {
                base(g, t);
                outline(g, rr(22, 42, 8, 14, 3), new Color(0xD8DDE6), line, 1.5f);
                outline(g, rr(34, 42, 8, 14, 3), new Color(0xD8DDE6), line, 1.5f);
                Path2D cape = new Path2D.Double();
                cape.moveTo(18, 22);
                cape.lineTo(46, 22);
                cape.lineTo(50, 52);
                cape.lineTo(14, 52);
                cape.closePath();
                outline(g, cape, td, line, 1.6f);
                outline(g, rr(19, 20, 26, 26, 8), new Color(0xF4F1E8), line, 2f);
                outline(g, rr(23, 6, 18, 16, 7), new Color(0xF4F1E8), line, 2f);
                outline(g, rr(26, 12, 12, 3, 1.5), NAVY, NAVY, 0.5f);
                outline(g, el(32, 33, 4.5, 4.5), GOLD, line, 1.2f);               // soleil de l'Aube
                glow(g, 32, 33, 9, GOLD);
                g.setColor(line);
                g.setStroke(new BasicStroke(1.8f));
                Path2D sword = new Path2D.Double();
                sword.moveTo(52, 6);
                sword.lineTo(56, 6);
                sword.lineTo(56, 40);
                sword.lineTo(52, 40);
                sword.closePath();
                outline(g, sword, new Color(0xEAF6FF), line, 1.4f);
                outline(g, rr(47, 38, 14, 4, 2), GOLD, line, 1.2f);
                glow(g, 54, 16, 7, new Color(0xFFF0C2));
            }
            case "mage" -> {
                base(g, t);
                Path2D robe = new Path2D.Double();
                robe.moveTo(32, 4);
                robe.lineTo(46, 26);
                robe.lineTo(52, 56);
                robe.lineTo(12, 56);
                robe.lineTo(18, 26);
                robe.closePath();
                outline(g, robe, new Color(0x2B1B40), line, 2f);
                outline(g, el(32, 22, 8, 7), NAVY, line, 1.2f);
                outline(g, el(29, 22, 1.6, 1.6), tc, tc, 0.5f);
                outline(g, el(35, 22, 1.6, 1.6), tc, tc, 0.5f);
                g.setColor(VOID_L);
                g.setStroke(new BasicStroke(1.6f));
                g.draw(new Line2D.Double(22, 40, 42, 40));
                glow(g, 52, 30, 12, VOID_L);
                outline(g, el(52, 30, 5, 5), new Color(0xD7B8FF), line, 1.2f);   // orbe d'ombre
                g.setColor(line);
                g.setStroke(new BasicStroke(2f));
                g.draw(new Line2D.Double(52, 36, 50, 58));
            }
            case "portail" -> {
                base(g, t);
                glow(g, 32, 32, 26, VOID_L);
                outline(g, el(32, 32, 22, 24), new Color(0x1A0E2C), line, 2f);
                for (int i = 0; i < 3; i++) {
                    g.setColor(new Color(0x8A4FD8 + i * 0x101010));
                    g.setStroke(new BasicStroke(2f));
                    g.draw(new Arc2D.Double(32 - 16 + i * 4, 32 - 17 + i * 4, 32 - i * 8, 34 - i * 8, i * 70, 230, Arc2D.OPEN));
                }
                g.setColor(tc);
                g.setStroke(new BasicStroke(3.2f));
                g.draw(el(32, 32, 22, 24));
                outline(g, el(32, 32, 4, 4), Color.WHITE, Color.WHITE, 0.5f);
                g.setColor(GOLD);
                g.fill(rr(28, 4, 8, 6, 2));
                g.fill(rr(28, 54, 8, 6, 2));
            }
            default -> {
            }
        }
        g.dispose();
        return img;
    }

    /** Icône de carte : cadre arrondi coloré selon le type, fond étoilé, unité ou effet au centre. */
    static BufferedImage card(String id) {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        Color frame = TYPE.getOrDefault(id, new Color(0x7FA8FF));
        g.setPaint(new GradientPaint(0, 0, new Color(0x1C2640), 0, S, new Color(0x0B1020)));
        g.fill(rr(5, 2, 54, 60, 10));
        Random r = new Random(id.hashCode());
        for (int i = 0; i < 14; i++) {
            g.setColor(new Color(255, 255, 255, 60 + r.nextInt(120)));
            double x = 9 + r.nextDouble() * 46, y = 6 + r.nextDouble() * 52;
            g.fill(el(x, y, 0.7, 0.7));
        }
        if (id.equals("frappe")) {
            glow(g, 32, 46, 20, new Color(0xFF8A4A));
            g.setPaint(new GradientPaint(32, 4, new Color(255, 240, 200, 0), 32, 44, new Color(255, 240, 200, 255)));
            g.fill(new Rectangle2D.Double(28, 4, 8, 42));
            outline(g, el(32, 46, 12, 5), new Color(0xFFD08A), new Color(0xFF6A2A), 1.6f);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                g.setColor(new Color(0xFFB34A));
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(32 + Math.cos(a) * 13, 46 + Math.sin(a) * 6, 32 + Math.cos(a) * 19, 46 + Math.sin(a) * 9));
            }
        } else if (id.equals("givre")) {
            glow(g, 32, 32, 22, ICE);
            g.setColor(new Color(0xEAFBFF));
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6;
                double ex = 32 + Math.cos(a) * 19, ey = 32 + Math.sin(a) * 19;
                g.draw(new Line2D.Double(32, 32, ex, ey));
                double mx = 32 + Math.cos(a) * 12, my = 32 + Math.sin(a) * 12;
                g.draw(new Line2D.Double(mx, my, mx + Math.cos(a + 0.8) * 6, my + Math.sin(a + 0.8) * 6));
                g.draw(new Line2D.Double(mx, my, mx + Math.cos(a - 0.8) * 6, my + Math.sin(a - 0.8) * 6));
            }
        } else {
            BufferedImage u = unit(id, 0);
            g.drawImage(u, 8, 7, 48, 48, null);
        }
        g.setColor(frame);
        g.setStroke(new BasicStroke(3f));
        g.draw(rr(5, 2, 54, 60, 10));
        g.setColor(new Color(255, 255, 255, 70));
        g.setStroke(new BasicStroke(1f));
        g.draw(rr(8, 5, 48, 54, 7));
        g.dispose();
        return img;
    }

    static BufferedImage dim(BufferedImage src) {
        BufferedImage out = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                int argb = src.getRGB(x, y);
                int a = argb >>> 24, rr = (argb >> 16) & 255, gg = (argb >> 8) & 255, bb = argb & 255;
                int l = (int) ((rr * 0.3 + gg * 0.59 + bb * 0.11) * 0.55);
                out.setRGB(x, y, (a << 24) | (l << 16) | (l << 8) | Math.min(255, l + 12));
            }
        }
        return out;
    }
}
