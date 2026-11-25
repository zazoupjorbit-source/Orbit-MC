import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

/**
 * Bornes d'arcade 3D de la salle cachée : un modèle de borne (caisse, pupitre, joystick, boutons, écran, fronton)
 * et, pour chaque jeu, deux textures HD 512×512 dessinées ici :
 *   item/borne/<id>_ecran.png : affiche du jeu (écran 512×384) + fronton lumineux (512×128) ;
 *   item/borne/<id>_deco.png  : flancs (affiche en hauteur), pupitre, porte à pièces, couleurs de la caisse.
 * Deux objets par jeu : orbitcasino:borne_<id> (caisse, éclairée par la salle) et borne_<id>_ecran (écran + fronton,
 * pleine lumière). Usage : java BornesPackGen.java <dossier pack> <dossier maquettes>
 * Jeux originaux ORBIT : aucun nom, personnage ou visuel repris d'un jeu existant.
 */
public class BornesPackGen {

    static final String NS = "orbitcasino";
    static File pack;
    static final Color NAVY = new Color(0x070B1A), INK = new Color(0x05060C), WHITE = Color.WHITE;
    static final Color GOLD = new Color(0xE0B864), BLUE = new Color(0x3A7FFF), ICE = new Color(0x62CFF4);
    static final String TITLE_FONT = "Arial Black", TEXT_FONT = "Bahnschrift";

    interface Motif {
        void draw(Graphics2D g, int w, int h, Game gm, Random r);
    }

    record Game(String id, String title, String tagline, Color c1, Color c2, Motif motif) {
    }

    public static void main(String[] args) throws Exception {
        pack = new File(args[0]);
        File previews = new File(args[1]);
        previews.mkdirs();
        List<Game> games = List.of(
                new Game("rush", "ORBIT RUSH", "Trois voies · aucune limite", hex("#FF4FD8"), ICE, BornesPackGen::rush),
                new Game("vol", "VOLE MON ORBITO", "Élève · fusionne · pille", GOLD, ICE, BornesPackGen::vol),
                new Game("casino", "LE CASINO", "Cartes · roulette · machines", hex("#E0484A"), GOLD, BornesPackGen::casino),
                new Game("astroblocs", "ASTROBLOCS", "Empile les blocs de l'espace", ICE, BLUE, BornesPackGen::astroblocs),
                new Game("cristaux", "CRISTAUX D'ORBIT", "Aligne trois cristaux", hex("#FF4FD8"), GOLD, BornesPackGen::cristaux),
                new Game("defense", "DÉFENSE ORBITALE", "Repousse les drones", hex("#7CFF8A"), ICE, BornesPackGen::defense),
                new Game("pulsar", "PULSAR", "Tiens le rythme de l'étoile", hex("#C79BFF"), hex("#FF4FD8"), BornesPackGen::pulsar),
                new Game("nebuleuse", "NÉBULEUSE", "Grossis · avale · survis", ICE, hex("#C79BFF"), BornesPackGen::nebuleuse),
                new Game("nova", "CHARGE NOVA", "Pose · fuis · fais tout sauter", hex("#FF8A3D"), GOLD, BornesPackGen::nova),
                new Game("palet", "PALET ORBITAL", "Le palet le plus rapide du ciel", ICE, hex("#7CFF8A"), BornesPackGen::palet),
                new Game("turbo", "TURBO-SPHÈRE", "Bolides · sphère · buts", BLUE, GOLD, BornesPackGen::turbo),
                new Game("siege", "SIÈGE ORBITAL", "Cartes · plasma · tours", BLUE, GOLD, BornesPackGen::siege),
                new Game("chasseurs", "CHASSEURS D'ORBITE", "Combat de vaisseaux en apesanteur", ICE, hex("#FF8A3D"), BornesPackGen::chasseurs));

        regions();
        BufferedImage sheet = new BufferedImage(4 * 520, ((games.size() + 3) / 4) * 1040, BufferedImage.TYPE_INT_RGB);
        Graphics2D sg = sheet.createGraphics();
        int i = 0;
        for (Game gm : games) {
            BufferedImage art = art(gm, 512, 384);
            BufferedImage ecran = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = gfx(ecran);
            screen(g, art, gm);
            g.translate(0, 384);
            marquee(g, gm);
            g.dispose();
            BufferedImage deco = deco(gm, art);
            png("item/borne/" + gm.id + "_ecran", ecran);
            png("item/borne/" + gm.id + "_deco", deco);
            models(gm.id);
            sg.drawImage(front(ecran, deco), (i % 4) * 520, (i / 4) * 1040, null);
            i++;
        }
        sg.dispose();
        ImageIO.write(sheet, "png", new File(previews, "bornes-arcade.png"));
        System.out.println("Bornes d'arcade : " + games.size() + " jeux (modèle 3D + affiches HD).");
    }

    // ------------------------------------------------------------------ outils de dessin

    static Color hex(String s) {
        return new Color(Integer.parseInt(s.substring(1), 16));
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    static Graphics2D gfx(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g;
    }

    static void glowDot(Graphics2D g, double cx, double cy, double r, Color c) {
        if (r <= 0) {
            return;
        }
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) r, new float[]{0f, 0.25f, 1f},
                new Color[]{alpha(mix(c, WHITE, 0.5), 230), alpha(c, 140), alpha(c, 0)}));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    static void glow(Graphics2D g, Shape s, Color c, float width) {
        Stroke old = g.getStroke();
        for (float w = width * 5; w > width; w *= 0.72f) {
            g.setColor(alpha(c, 22));
            g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(s);
        }
        g.setColor(c);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(s);
        g.setColor(alpha(mix(c, WHITE, 0.7), 220));
        g.setStroke(new BasicStroke(Math.max(1f, width * 0.35f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(s);
        g.setStroke(old);
    }

    static void neon(Graphics2D g, double x1, double y1, double x2, double y2, Color c, float w) {
        glow(g, new Line2D.Double(x1, y1, x2, y2), c, w);
    }

    static void sphere(Graphics2D g, double cx, double cy, double r, Color c) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx - r * 0.35, cy - r * 0.4), (float) (r * 1.35), new float[]{0f, 0.45f, 1f},
                new Color[]{mix(c, WHITE, 0.6), c, mix(c, INK, 0.75)}));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.setColor(alpha(WHITE, 150));
        g.fill(new Ellipse2D.Double(cx - r * 0.55, cy - r * 0.62, r * 0.42, r * 0.26));
    }

    static void space(Graphics2D g, int w, int h, Color c1, Color c2, Random r) {
        g.setPaint(new GradientPaint(0, 0, new Color(0x0A0F24), 0, h, new Color(0x020309)));
        g.fillRect(0, 0, w, h);
        for (int i = 0; i < 12; i++) {
            double x = r.nextDouble() * w, y = r.nextDouble() * h * 0.85, rad = 70 + r.nextDouble() * 170;
            Color c = i % 3 == 0 ? BLUE : (i % 2 == 0 ? c1 : c2);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), (float) rad, new float[]{0f, 1f},
                    new Color[]{alpha(c, 34 + r.nextInt(26)), alpha(c, 0)}));
            g.fill(new Ellipse2D.Double(x - rad, y - rad, rad * 2, rad * 2));
        }
        for (int i = 0; i < w * h / 700; i++) {
            double x = r.nextDouble() * w, y = r.nextDouble() * h;
            double s = r.nextDouble() < 0.88 ? 1.1 : 2.0;
            g.setColor(alpha(r.nextBoolean() ? WHITE : mix(c1, WHITE, 0.6), 70 + r.nextInt(170)));
            g.fill(new Ellipse2D.Double(x, y, s, s));
        }
        for (int i = 0; i < 7; i++) {
            sparkle(g, r.nextDouble() * w, r.nextDouble() * h * 0.7, 5 + r.nextDouble() * 7, mix(c1, WHITE, 0.5));
        }
    }

    static void sparkle(Graphics2D g, double x, double y, double s, Color c) {
        glowDot(g, x, y, s * 1.4, c);
        g.setColor(alpha(WHITE, 220));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Line2D.Double(x - s * 1.8, y, x + s * 1.8, y));
        g.draw(new Line2D.Double(x, y - s * 1.8, x, y + s * 1.8));
    }

    static Font font(String name, int style, float size) {
        return new Font(name, style, 10).deriveFont(size);
    }

    static Font fit(Graphics2D g, String name, int style, String text, float max, double maxW) {
        Font f = font(name, style, max);
        double w = f.createGlyphVector(g.getFontRenderContext(), text).getVisualBounds().getWidth();
        if (w > maxW) {
            f = f.deriveFont((float) (max * maxW / w));
        }
        return f;
    }

    /** Titre lumineux : halo c1, contour sombre, remplissage dégradé blanc → c1 → c2, reflet en haut. */
    static void title(Graphics2D g, String text, float max, double cx, double baseline, double maxW, Color c1, Color c2, float halo) {
        Font f = fit(g, TITLE_FONT, Font.PLAIN, text, max, maxW);
        GlyphVector gv = f.createGlyphVector(g.getFontRenderContext(), text);
        Rectangle2D b = gv.getVisualBounds();
        Shape s = gv.getOutline((float) (cx - b.getWidth() / 2 - b.getX()), (float) baseline);
        Rectangle2D sb = s.getBounds2D();
        Stroke old = g.getStroke();
        for (float w = halo; w > 2; w *= 0.8f) {
            g.setColor(alpha(c1, 16));
            g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(s);
        }
        g.setColor(INK);
        g.setStroke(new BasicStroke(Math.max(3f, f.getSize2D() / 9f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(s);
        g.setPaint(new LinearGradientPaint((float) sb.getX(), (float) sb.getY(), (float) sb.getX(), (float) sb.getMaxY() + 0.01f,
                new float[]{0f, 0.45f, 1f}, new Color[]{mix(c1, WHITE, 0.75), c1, c2}));
        g.fill(s);
        Shape clip = g.getClip();
        g.clip(s);
        g.setColor(alpha(WHITE, 70));
        g.fill(new Rectangle2D.Double(sb.getX(), sb.getY(), sb.getWidth(), sb.getHeight() * 0.38));
        g.setClip(clip);
        g.setColor(alpha(mix(c1, WHITE, 0.6), 200));
        g.setStroke(new BasicStroke(1.1f));
        g.draw(s);
        g.setStroke(old);
    }

    static void text(Graphics2D g, String text, Font f, Color c, double cx, double baseline) {
        g.setFont(f);
        double w = g.getFontMetrics().stringWidth(text);
        g.setColor(alpha(INK, 200));
        g.drawString(text, (float) (cx - w / 2 + 1.5), (float) (baseline + 1.5));
        g.setColor(c);
        g.drawString(text, (float) (cx - w / 2), (float) baseline);
    }

    static void bevel(Graphics2D g, double x, double y, double s, Color c) {
        g.setColor(mix(c, INK, 0.25));
        g.fill(new Rectangle2D.Double(x, y, s, s));
        Path2D tl = new Path2D.Double();
        tl.moveTo(x, y);
        tl.lineTo(x + s, y);
        tl.lineTo(x + s * 0.82, y + s * 0.18);
        tl.lineTo(x + s * 0.18, y + s * 0.18);
        tl.lineTo(x + s * 0.18, y + s * 0.82);
        tl.lineTo(x, y + s);
        tl.closePath();
        g.setColor(mix(c, WHITE, 0.45));
        g.fill(tl);
        g.setPaint(new GradientPaint((float) x, (float) y, mix(c, WHITE, 0.15), (float) (x + s), (float) (y + s), mix(c, INK, 0.1)));
        g.fill(new Rectangle2D.Double(x + s * 0.18, y + s * 0.18, s * 0.64, s * 0.64));
        g.setColor(alpha(WHITE, 140));
        g.fill(new Rectangle2D.Double(x + s * 0.24, y + s * 0.24, s * 0.2, s * 0.08));
        g.setColor(alpha(INK, 160));
        g.setStroke(new BasicStroke(1f));
        g.draw(new Rectangle2D.Double(x, y, s, s));
    }

    /** Perspective : point de fuite (vx, vy), sol jusqu'en bas. */
    static void gridFloor(Graphics2D g, int w, int h, double vx, double vy, Color c, Color floor) {
        g.setPaint(new GradientPaint(0, (float) vy, mix(floor, INK, 0.6), 0, h, floor));
        g.fill(new Rectangle2D.Double(0, vy, w, h - vy));
        for (int k = -14; k <= 14; k++) {
            double bx = vx + k * w / 7.0;
            g.setColor(alpha(c, 110));
            g.setStroke(new BasicStroke(1.4f));
            g.draw(new Line2D.Double(vx, vy, bx, h));
        }
        for (int k = 1; k < 12; k++) {
            double t = Math.pow(k / 11.0, 2.1);
            double y = vy + (h - vy) * t;
            g.setColor(alpha(c, (int) (40 + 120 * t)));
            g.draw(new Line2D.Double(0, y, w, y));
        }
        g.setPaint(new GradientPaint(0, (float) vy - 6, alpha(c, 200), 0, (float) vy + 10, alpha(c, 0)));
        g.fill(new Rectangle2D.Double(0, vy - 6, w, 16));
    }

    // ------------------------------------------------------------------ composition

    static BufferedImage art(Game gm, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = gfx(img);
        Random r = new Random(gm.id.hashCode());
        space(g, w, h, gm.c1, gm.c2, r);
        gm.motif.draw(g, w, h, gm, r);
        g.dispose();
        return img;
    }

    /** Écran 512×384 : l'affiche du jeu, titre et accroche, balayage d'écran cathodique. */
    static void screen(Graphics2D g, BufferedImage art, Game gm) {
        g.drawImage(art, 0, 0, null);
        g.setPaint(new GradientPaint(0, 210, alpha(INK, 0), 0, 384, alpha(INK, 235)));
        g.fillRect(0, 210, 512, 174);
        title(g, gm.title, 66, 256, 322, 470, gm.c1, gm.c2, 26);
        text(g, gm.tagline.toUpperCase(Locale.ROOT), font(TEXT_FONT, Font.BOLD, 19), alpha(mix(gm.c2, WHITE, 0.55), 240), 256, 356);
        // badge ORBIT ARCADE
        g.setColor(alpha(INK, 170));
        g.fill(new RoundRectangle2D.Double(12, 12, 128, 26, 10, 10));
        glow(g, new RoundRectangle2D.Double(12, 12, 128, 26, 10, 10), gm.c1, 1.6f);
        text(g, "ORBIT ARCADE", font(TEXT_FONT, Font.BOLD, 15), WHITE, 76, 31);
        // écran cathodique : lignes, bords sombres
        for (int y = 0; y < 384; y += 3) {
            g.setColor(alpha(INK, 26));
            g.fillRect(0, y, 512, 1);
        }
        g.setPaint(new RadialGradientPaint(new Point2D.Double(256, 192), 330, new float[]{0.55f, 1f},
                new Color[]{alpha(INK, 0), alpha(INK, 170)}));
        g.fillRect(0, 0, 512, 384);
    }

    /** Fronton 512×128 au-dessus de l'écran. */
    static void marquee(Graphics2D g, Game gm) {
        Random r = new Random(gm.id.hashCode() * 31L);
        g.setPaint(new GradientPaint(0, 0, mix(gm.c1, INK, 0.82), 0, 128, mix(gm.c2, INK, 0.9)));
        g.fillRect(0, 0, 512, 128);
        for (int i = 0; i < 90; i++) {
            g.setColor(alpha(WHITE, 60 + r.nextInt(150)));
            double s = r.nextDouble() < 0.85 ? 1.2 : 2.2;
            g.fill(new Ellipse2D.Double(r.nextDouble() * 512, r.nextDouble() * 128, s, s));
        }
        // rayons derrière le titre
        for (int k = 0; k < 18; k++) {
            double a = Math.PI * k / 17;
            Path2D p = new Path2D.Double();
            p.moveTo(256, 150);
            p.lineTo(256 + Math.cos(a - 0.04) * 420, 150 - Math.sin(a - 0.04) * 420);
            p.lineTo(256 + Math.cos(a + 0.04) * 420, 150 - Math.sin(a + 0.04) * 420);
            p.closePath();
            g.setColor(alpha(k % 2 == 0 ? gm.c1 : gm.c2, 22));
            g.fill(p);
        }
        glow(g, new RoundRectangle2D.Double(8, 8, 496, 112, 22, 22), gm.c1, 2.4f);
        glow(g, new RoundRectangle2D.Double(16, 16, 480, 96, 16, 16), gm.c2, 1.2f);
        title(g, gm.title, 64, 256, 88, 430, gm.c1, gm.c2, 30);
        sparkle(g, 40, 64, 6, gm.c2);
        sparkle(g, 472, 64, 6, gm.c2);
    }

    static final Map<String, double[]> UV = new LinkedHashMap<>();

    /** Zones de la texture deco (pixels) → coordonnées UV (0-16, 32 px par unité). */
    static void regions() {
        put("SIDE", 0, 0, 208, 496);
        put("PANEL", 224, 0, 224, 136);
        put("KICK", 224, 144, 224, 192);
        put("BODY", 464, 0, 48, 48);
        put("TRIM", 464, 64, 48, 48);
        put("TRIM2", 464, 128, 48, 48);
        put("BLACK", 464, 192, 48, 48);
        put("METAL", 464, 256, 48, 48);
        put("BALL", 464, 320, 48, 48);
        put("BTN1", 224, 352, 48, 48);
        put("BTN2", 288, 352, 48, 48);
        put("BTN3", 352, 352, 48, 48);
        put("STRIP", 224, 416, 224, 48);
        double[] p = UV.get("PANEL");
        UV.put("PANEL_F", new double[]{p[2], p[3], p[0], p[1]}); // tourné de 180° : lisible depuis l'avant
        UV.put("SCREEN", new double[]{0, 0, 16, 12});
        UV.put("MARQUEE", new double[]{0, 12, 16, 16});
    }

    static void put(String k, int x, int y, int w, int h) {
        UV.put(k, new double[]{x / 32.0, y / 32.0, (x + w) / 32.0, (y + h) / 32.0});
    }

    static BufferedImage deco(Game gm, BufferedImage art) {
        BufferedImage img = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = gfx(img);
        Random r = new Random(gm.id.hashCode() * 7L);
        Color body = new Color(0x10162B);
        // caisse : bleu nuit à grain fin
        g.setColor(body);
        g.fillRect(0, 0, 512, 512);
        for (int i = 0; i < 9000; i++) {
            g.setColor(alpha(r.nextBoolean() ? WHITE : INK, 10 + r.nextInt(14)));
            g.fillRect(r.nextInt(512), r.nextInt(512), 1, 1);
        }
        // ---------------- flanc : l'affiche en hauteur + titre vertical
        Shape clip = g.getClip();
        g.setClip(0, 0, 208, 496);
        double sc = 496.0 / 384;
        g.drawImage(art, (int) (104 - 256 * sc), 0, (int) (512 * sc), 496, null);
        g.setPaint(new GradientPaint(0, 0, alpha(mix(gm.c1, INK, 0.6), 120), 208, 0, alpha(INK, 30)));
        g.fillRect(0, 0, 208, 496);
        g.setPaint(new GradientPaint(0, 330, alpha(INK, 0), 0, 496, alpha(INK, 230)));
        g.fillRect(0, 330, 208, 166);
        for (int k = 0; k < 3; k++) { // bandes néon en biais
            double y0 = 360 + k * 26;
            Path2D band = new Path2D.Double();
            band.moveTo(0, y0);
            band.lineTo(208, y0 - 90);
            band.lineTo(208, y0 - 80);
            band.lineTo(0, y0 + 10);
            band.closePath();
            g.setColor(alpha(k == 1 ? gm.c2 : gm.c1, 170));
            g.fill(band);
        }
        AffineTransform at = g.getTransform();
        g.translate(150, 248);
        g.rotate(-Math.PI / 2);
        title(g, gm.title, 52, 0, 18, 440, gm.c1, gm.c2, 20);
        g.setTransform(at);
        text(g, "ORBIT", font(TITLE_FONT, Font.PLAIN, 24), WHITE, 104, 480);
        glow(g, new Rectangle2D.Double(3, 3, 202, 490), gm.c1, 2f);
        g.setClip(clip);

        // ---------------- pupitre (vu depuis l'avant après rotation des UV)
        g.setClip(224, 0, 224, 136);
        g.setPaint(new GradientPaint(224, 0, new Color(0x1A2140), 224, 136, new Color(0x0B0F1F)));
        g.fillRect(224, 0, 224, 136);
        for (int k = 0; k < 6; k++) {
            g.setColor(alpha(k % 2 == 0 ? gm.c1 : gm.c2, 40));
            g.fillRect(224, 10 + k * 22, 224, 8);
        }
        glow(g, new Rectangle2D.Double(228, 4, 216, 128), gm.c1, 2f);
        // anneaux sous le joystick et les boutons (positions du modèle : x, z → pixels)
        ring(g, panelX(4.0), panelY(3.5), 20, gm.c1);
        ring(g, panelX(7.7), panelY(3.7), 14, gm.c2);
        ring(g, panelX(9.7), panelY(3.7), 14, gm.c2);
        ring(g, panelX(11.7), panelY(3.7), 14, gm.c2);
        text(g, "1 JOUEUR", font(TEXT_FONT, Font.BOLD, 13), WHITE, panelX(9.7), panelY(5.9) + 4);
        text(g, gm.title, fit(g, TEXT_FONT, Font.BOLD, gm.title, 15, 190), alpha(mix(gm.c1, WHITE, 0.4), 230), 336, panelY(7.8) + 5);
        g.setClip(clip);

        // ---------------- porte à pièces (bas de la façade)
        g.setClip(224, 144, 224, 192);
        g.setPaint(new GradientPaint(224, 144, new Color(0x141A33), 224, 336, new Color(0x080B16)));
        g.fillRect(224, 144, 224, 192);
        RoundRectangle2D door = new RoundRectangle2D.Double(268, 176, 136, 132, 12, 12);
        g.setPaint(new GradientPaint(268, 176, new Color(0x8C93A3), 404, 308, new Color(0x3A4050)));
        g.fill(door);
        g.setColor(INK);
        g.setStroke(new BasicStroke(2f));
        g.draw(door);
        for (int k = 0; k < 2; k++) {
            double x = 296 + k * 58;
            g.setColor(new Color(0x1B1F2A));
            g.fill(new RoundRectangle2D.Double(x, 196, 22, 40, 6, 6));
            glowDot(g, x + 11, 216, 16, k == 0 ? gm.c1 : gm.c2);
            g.setColor(INK);
            g.fill(new Rectangle2D.Double(x + 9, 202, 4, 28));
        }
        text(g, "GRATUIT", font(TEXT_FONT, Font.BOLD, 16), INK, 336, 266);
        text(g, "ORBIT ARCADE", font(TEXT_FONT, Font.BOLD, 12), new Color(0x1B1F2A), 336, 290);
        glow(g, new Rectangle2D.Double(228, 148, 216, 184), gm.c2, 1.6f);
        g.setClip(clip);

        // ---------------- aplats de la caisse
        swatch(g, 464, 64, gm.c1);
        swatch(g, 464, 128, gm.c2);
        g.setColor(new Color(0x05070D));
        g.fillRect(464, 192, 48, 48);
        g.setPaint(new GradientPaint(464, 256, new Color(0xC9CED8), 512, 304, new Color(0x5A6070)));
        g.fillRect(464, 256, 48, 48);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(478, 334), 40, new float[]{0f, 1f}, new Color[]{hex("#FF8C8C"), hex("#B0202A")}));
        g.fillRect(464, 320, 48, 48);
        button(g, 224, 352, gm.c1);
        button(g, 288, 352, gm.c2);
        button(g, 352, 352, GOLD);
        // ---------------- bord avant du pupitre
        g.setColor(INK);
        g.fillRect(224, 416, 224, 48);
        neon(g, 228, 424, 444, 424, gm.c1, 2f);
        neon(g, 228, 456, 444, 456, gm.c2, 2f);
        text(g, "ORBIT ARCADE", font(TEXT_FONT, Font.BOLD, 17), WHITE, 336, 447);
        g.dispose();
        return img;
    }

    static double panelX(double x) { // pupitre x 1..15, UV tournées de 180°
        return 224 + 224 - (x - 1) * 16;
    }

    static double panelY(double z) { // pupitre z 0.5..9
        return 136 - (z - 0.5) * 16;
    }

    static void ring(Graphics2D g, double cx, double cy, double r, Color c) {
        g.setColor(new Color(0x05070D));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        glow(g, new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r), c, 1.6f);
    }

    static void swatch(Graphics2D g, int x, int y, Color c) {
        g.setPaint(new GradientPaint(x, y, mix(c, WHITE, 0.35), x, y + 48, c));
        g.fillRect(x, y, 48, 48);
    }

    static void button(Graphics2D g, int x, int y, Color c) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x + 18, y + 16), 40, new float[]{0f, 0.5f, 1f},
                new Color[]{mix(c, WHITE, 0.7), c, mix(c, INK, 0.5)}));
        g.fillRect(x, y, 48, 48);
    }

    /** Aperçu de façade (fronton, écran, pupitre, porte) pour vérifier les textures sans le jeu. */
    static BufferedImage front(BufferedImage ecran, BufferedImage deco) {
        BufferedImage out = new BufferedImage(512, 1040, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = gfx(out);
        g.setColor(new Color(0x10162B));
        g.fillRect(0, 0, 512, 1040);
        g.drawImage(ecran.getSubimage(0, 384, 512, 128), 0, 0, null);
        g.drawImage(ecran.getSubimage(0, 0, 512, 384), 0, 136, null);
        BufferedImage panel = deco.getSubimage(224, 0, 224, 136);
        g.drawImage(panel, 0, 520, 512, 274, null); // UV retournées + vue depuis l'avant : se lit à l'endroit
        g.drawImage(deco.getSubimage(224, 144, 224, 192), 0, 800, 512, 240, null);
        g.drawImage(deco.getSubimage(0, 0, 208, 496), 0, 520, 104, 248, null);
        g.dispose();
        return out;
    }

    // ------------------------------------------------------------------ pack

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

    static String n(double v) {
        return String.format(Locale.ROOT, "%.4f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    static String uv(String region) {
        double[] u = UV.get(region);
        return n(u[0]) + ", " + n(u[1]) + ", " + n(u[2]) + ", " + n(u[3]);
    }

    /** Pavé du modèle ; faces = "north=REGION" ... ; les faces non citées prennent def (null = pas de face). */
    static String box(double[] from, double[] to, String tex, String def, boolean shade, String... faces) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String d : new String[]{"north", "east", "south", "west", "up", "down"}) {
            if (def != null) {
                m.put(d, def);
            }
        }
        for (String f : faces) {
            String[] kv = f.split("=");
            if (kv[1].equals("-")) {
                m.remove(kv[0]);
            } else {
                m.put(kv[0], kv[1]);
            }
        }
        StringBuilder sb = new StringBuilder("    {\"from\": [" + n(from[0]) + ", " + n(from[1]) + ", " + n(from[2]) + "], \"to\": ["
                + n(to[0]) + ", " + n(to[1]) + ", " + n(to[2]) + "]" + (shade ? "" : ", \"shade\": false") + ", \"faces\": {");
        boolean first = true;
        for (var e : m.entrySet()) {
            sb.append(first ? "" : ", ").append("\"").append(e.getKey()).append("\": {\"uv\": [").append(uv(e.getValue()))
                    .append("], \"texture\": \"#").append(tex).append("\"}");
            first = false;
        }
        return sb.append("}}").toString();
    }

    static double[] v(double x, double y, double z) {
        return new double[]{x, y, z};
    }

    /**
     * Borne : avant du modèle = face nord (z petit), tournée vers l'avant de l'entité par le rendu des ItemDisplay.
     * Unités : 16 = 1 bloc avant l'échelle de l'ItemDisplay (1,25 par défaut → 1,25 × 2,4 blocs).
     */
    static void models(String id) throws IOException {
        String tex = NS + ":item/borne/" + id;
        List<String> body = new ArrayList<>();
        body.add(box(v(0, 0, 1), v(1, 31, 14), "d", "BODY", true, "west=SIDE", "north=TRIM"));          // flanc gauche
        body.add(box(v(15, 0, 1), v(16, 31, 14), "d", "BODY", true, "east=SIDE", "north=TRIM"));        // flanc droit
        body.add(box(v(1, 0, 13), v(15, 31, 14), "d", "BODY", true));                                    // dos
        body.add(box(v(1, 30, 1), v(15, 31, 13), "d", "BODY", true, "north=TRIM2"));                     // toit
        body.add(box(v(1, 0, 3), v(15, 12, 13), "d", "BODY", true, "north=KICK"));                       // porte à pièces
        body.add(box(v(0.6, 0, 2.6), v(15.4, 0.8, 13.4), "d", "BLACK", true));                           // socle
        body.add(box(v(1, 12, 0.5), v(15, 14.5, 9), "d", "BODY", true, "up=PANEL_F", "north=STRIP"));    // pupitre
        body.add(box(v(3.7, 14.5, 3.2), v(4.3, 16.8, 3.8), "d", "METAL", true, "down=-"));               // manche
        body.add(box(v(3.2, 16.6, 2.7), v(4.8, 18.2, 4.3), "d", "BALL", true));                          // boule
        body.add(box(v(7, 14.5, 3), v(8.4, 15.1, 4.4), "d", "BTN1", true, "down=-"));                    // boutons
        body.add(box(v(9, 14.5, 3), v(10.4, 15.1, 4.4), "d", "BTN2", true, "down=-"));
        body.add(box(v(11, 14.5, 3), v(12.4, 15.1, 4.4), "d", "BTN3", true, "down=-"));
        body.add(box(v(1, 14.5, 7), v(15, 26, 13), "d", "BLACK", true));                                 // cadre de l'écran
        body.add(box(v(1, 26, 2), v(15, 30, 13), "d", "BODY", true, "north=BLACK", "down=BLACK"));       // caisson du fronton
        body.add(box(v(1, 25.6, 2.4), v(15, 26, 7), "d", "TRIM", true));                                 // visière néon
        write("assets/" + NS + "/models/item/borne/" + id + ".json", "{\n  \"textures\": {\"d\": \"" + tex + "_deco\", \"particle\": \""
                + tex + "_deco\"},\n  \"elements\": [\n" + String.join(",\n", body) + "\n  ]\n}\n");

        List<String> lit = new ArrayList<>();
        lit.add(box(v(1.6, 14.9, 6.9), v(14.4, 24.5, 6.9), "e", null, false, "north=SCREEN"));
        lit.add(box(v(1, 26.25, 1.95), v(15, 29.75, 1.95), "e", null, false, "north=MARQUEE"));
        write("assets/" + NS + "/models/item/borne/" + id + "_ecran.json", "{\n  \"textures\": {\"e\": \"" + tex + "_ecran\", \"particle\": \""
                + tex + "_ecran\"},\n  \"elements\": [\n" + String.join(",\n", lit) + "\n  ]\n}\n");

        for (String m : new String[]{id, id + "_ecran"}) {
            write("assets/" + NS + "/items/borne_" + m + ".json", "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
                    + NS + ":item/borne/" + m + "\"\n  }\n}\n");
        }
    }

    // ------------------------------------------------------------------ affiches des jeux

    static void rush(Graphics2D g, int w, int h, Game gm, Random r) {
        double vy = 170;
        // soleil à bandes
        Shape sun = new Ellipse2D.Double(256 - 95, vy - 150, 190, 190);
        glowDot(g, 256, vy - 55, 150, gm.c1);
        g.setPaint(new GradientPaint(0, (float) (vy - 150), GOLD, 0, (float) vy, gm.c1));
        g.fill(sun);
        g.setColor(new Color(0x0A0F24));
        for (int k = 0; k < 6; k++) {
            double y = vy - 70 + k * 13;
            g.fill(new Rectangle2D.Double(150, y, 212, 2 + k * 1.4));
        }
        gridFloor(g, w, h, 256, vy, gm.c1, new Color(0x2A0A3A));
        // piste à trois voies
        Path2D track = new Path2D.Double();
        track.moveTo(250, vy);
        track.lineTo(262, vy);
        track.lineTo(470, h);
        track.lineTo(42, h);
        track.closePath();
        g.setColor(alpha(new Color(0x0B0618), 220));
        g.fill(track);
        for (int k = 0; k <= 3; k++) {
            double bx = 42 + k * (428 / 3.0), tx = 250 + k * 4;
            neon(g, tx, vy, bx, h, k == 0 || k == 3 ? gm.c2 : alpha(gm.c2, 160), k == 0 || k == 3 ? 2.6f : 1.4f);
        }
        // barrières et pièces
        double[] depth = {0.22, 0.45};
        int[] lane = {0, 2};
        for (int i = 0; i < 2; i++) {
            double t = depth[i];
            double y = vy + (h - vy) * t;
            double half = 6 + 208 * t;
            double lw = half * 2 / 3;
            double x0 = 256 - half + lane[i] * lw;
            Shape bar = new Rectangle2D.Double(x0 + lw * 0.1, y - 22 * t * 2.4, lw * 0.8, 20 * t * 2.4);
            g.setColor(alpha(new Color(0x3A0820), 230));
            g.fill(bar);
            glow(g, bar, hex("#FF4F6A"), 2f);
        }
        for (int i = 0; i < 6; i++) {
            double t = 0.08 + i * 0.075;
            double y = vy + (h - vy) * t, s = 4 + 40 * t;
            double x = 256 + (i % 2 == 0 ? 0 : 0);
            glowDot(g, x, y - s, s * 1.6, GOLD);
            g.setPaint(new GradientPaint(0, (float) (y - s * 1.6), hex("#FFF0C2"), 0, (float) (y - s * 0.4), GOLD));
            g.fill(new Ellipse2D.Double(x - s * 0.45, y - s * 1.6, s * 0.9, s * 1.2));
        }
        // traits de vitesse
        for (int i = 0; i < 26; i++) {
            double a = r.nextDouble() * Math.PI * 2, d0 = 90 + r.nextDouble() * 60, d1 = d0 + 40 + r.nextDouble() * 90;
            g.setColor(alpha(WHITE, 60 + r.nextInt(80)));
            g.setStroke(new BasicStroke(1.3f));
            g.draw(new Line2D.Double(256 + Math.cos(a) * d0, vy + Math.sin(a) * d0 * 0.7, 256 + Math.cos(a) * d1, vy + Math.sin(a) * d1 * 0.7));
        }
    }

    static void orbito(Graphics2D g, double cx, double cy, double r, Color c, boolean wink) {
        glowDot(g, cx, cy, r * 1.7, c);
        g.setColor(alpha(INK, 90));
        g.fill(new Ellipse2D.Double(cx - r * 0.8, cy + r * 0.85, r * 1.6, r * 0.3));
        // pieds
        g.setColor(mix(c, INK, 0.4));
        g.fill(new Ellipse2D.Double(cx - r * 0.6, cy + r * 0.7, r * 0.45, r * 0.32));
        g.fill(new Ellipse2D.Double(cx + r * 0.15, cy + r * 0.7, r * 0.45, r * 0.32));
        // antenne
        g.setColor(mix(c, INK, 0.3));
        g.setStroke(new BasicStroke((float) (r * 0.08), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Double(cx, cy - r * 0.9, cx + r * 0.1, cy - r * 1.35, cx + r * 0.4, cy - r * 1.45));
        glowDot(g, cx + r * 0.42, cy - r * 1.47, r * 0.35, ICE);
        sphere(g, cx, cy, r, c);
        // yeux
        for (int k = -1; k <= 1; k += 2) {
            double ex = cx + k * r * 0.34, ey = cy - r * 0.08;
            if (wink && k == 1) {
                g.setColor(INK);
                g.setStroke(new BasicStroke((float) (r * 0.07), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Arc2D.Double(ex - r * 0.16, ey - r * 0.1, r * 0.32, r * 0.22, 0, 180, Arc2D.OPEN));
                continue;
            }
            g.setColor(WHITE);
            g.fill(new Ellipse2D.Double(ex - r * 0.2, ey - r * 0.27, r * 0.4, r * 0.5));
            g.setColor(INK);
            g.fill(new Ellipse2D.Double(ex - r * 0.12, ey - r * 0.12, r * 0.26, r * 0.32));
            g.setColor(WHITE);
            g.fill(new Ellipse2D.Double(ex - r * 0.07, ey - r * 0.08, r * 0.09, r * 0.09));
        }
        g.setColor(alpha(hex("#FF7FA0"), 140));
        g.fill(new Ellipse2D.Double(cx - r * 0.66, cy + r * 0.18, r * 0.24, r * 0.13));
        g.fill(new Ellipse2D.Double(cx + r * 0.42, cy + r * 0.18, r * 0.24, r * 0.13));
        g.setColor(INK);
        g.setStroke(new BasicStroke((float) (r * 0.05), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(cx - r * 0.14, cy + r * 0.12, r * 0.28, r * 0.2, 200, 140, Arc2D.OPEN));
    }

    static void vol(Graphics2D g, int w, int h, Game gm, Random r) {
        // planète en bas, île flottante
        g.setPaint(new RadialGradientPaint(new Point2D.Double(256, 560), 380, new float[]{0.6f, 0.68f, 1f},
                new Color[]{new Color(0x1D3A5A), new Color(0x2E5F8A), alpha(ICE, 0)}));
        g.fill(new Ellipse2D.Double(-124, 180, 760, 760));
        Path2D isle = new Path2D.Double();
        isle.moveTo(110, 228);
        isle.curveTo(170, 200, 340, 200, 402, 228);
        isle.curveTo(360, 290, 300, 320, 256, 330);
        isle.curveTo(210, 320, 150, 290, 110, 228);
        g.setPaint(new GradientPaint(0, 210, new Color(0x6B4A2E), 0, 330, new Color(0x2A1A10)));
        g.fill(isle);
        g.setPaint(new GradientPaint(0, 205, new Color(0x7CFF8A), 0, 236, new Color(0x2E8A4A)));
        g.fill(new Ellipse2D.Double(108, 204, 296, 40));
        // œufs
        double[][] eggs = {{150, 214, 16}, {372, 216, 14}, {300, 222, 11}};
        for (double[] e : eggs) {
            Shape egg = new Ellipse2D.Double(e[0] - e[2], e[1] - e[2] * 2.6, e[2] * 2, e[2] * 2.7);
            glowDot(g, e[0], e[1] - e[2] * 1.3, e[2] * 2.2, gm.c2);
            g.setPaint(new GradientPaint((float) e[0], (float) (e[1] - e[2] * 2.6), WHITE, (float) e[0], (float) e[1], hex("#BFD8FF")));
            g.fill(egg);
            g.setColor(alpha(gm.c1, 200));
            g.fill(new Ellipse2D.Double(e[0] - e[2] * 0.5, e[1] - e[2] * 1.9, e[2] * 0.5, e[2] * 0.4));
            g.fill(new Ellipse2D.Double(e[0] + e[2] * 0.2, e[1] - e[2] * 1.2, e[2] * 0.4, e[2] * 0.35));
        }
        orbito(g, 256, 150, 62, gm.c1, false);
        orbito(g, 118, 150, 36, ICE, true);
        orbito(g, 398, 140, 40, hex("#FF4FD8"), false);
        for (int i = 0; i < 9; i++) {
            double x = 40 + r.nextDouble() * 432, y = 30 + r.nextDouble() * 90;
            coin(g, x, y, 7 + r.nextDouble() * 6);
        }
    }

    static void coin(Graphics2D g, double x, double y, double s) {
        glowDot(g, x, y, s * 2, GOLD);
        g.setPaint(new GradientPaint((float) x, (float) (y - s), hex("#FFF0C2"), (float) x, (float) (y + s), hex("#B8862E")));
        g.fill(new Ellipse2D.Double(x - s, y - s, s * 2, s * 2));
        g.setColor(alpha(hex("#8A6A2A"), 220));
        g.setStroke(new BasicStroke((float) Math.max(1, s * 0.15)));
        g.draw(new Ellipse2D.Double(x - s * 0.62, y - s * 0.62, s * 1.24, s * 1.24));
    }

    static void card(Graphics2D g, double cx, double cy, double angle, String rank, String suit, boolean red) {
        AffineTransform at = g.getTransform();
        g.translate(cx, cy);
        g.rotate(angle);
        RoundRectangle2D c = new RoundRectangle2D.Double(-46, -66, 92, 132, 12, 12);
        g.setColor(alpha(INK, 120));
        g.fill(new RoundRectangle2D.Double(-42, -60, 92, 132, 12, 12));
        g.setPaint(new GradientPaint(0, -66, WHITE, 0, 66, hex("#E6E2D6")));
        g.fill(c);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Double(-41, -61, 82, 122, 9, 9));
        Color ink = red ? hex("#D02A2A") : hex("#15161B");
        g.setColor(ink);
        g.setFont(font("Arial", Font.BOLD, 26));
        g.drawString(rank, -38, -34);
        g.setFont(font("Arial", Font.PLAIN, 58));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(suit, -fm.stringWidth(suit) / 2f, 22);
        g.setTransform(at);
    }

    static void chips(Graphics2D g, double x, double y, int n, Color c) {
        for (int i = 0; i < n; i++) {
            double yy = y - i * 7;
            g.setColor(mix(c, INK, 0.45));
            g.fill(new Ellipse2D.Double(x - 30, yy - 8, 60, 22));
            g.setColor(c);
            g.fill(new Ellipse2D.Double(x - 30, yy - 12, 60, 22));
            g.setColor(alpha(WHITE, 200));
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10, new float[]{6, 8}, 0));
            g.draw(new Ellipse2D.Double(x - 24, yy - 9, 48, 16));
        }
    }

    static void casino(Graphics2D g, int w, int h, Game gm, Random r) {
        // roue de roulette derrière
        double cx = 256, cy = 120, R = 150;
        glowDot(g, cx, cy, R * 1.3, gm.c1);
        for (int k = 0; k < 37; k++) {
            double a0 = k * 360.0 / 37;
            g.setColor(k == 0 ? hex("#1E8C45") : (k % 2 == 0 ? hex("#15161B") : hex("#C0242A")));
            g.fill(new Arc2D.Double(cx - R, cy - R, 2 * R, 2 * R, a0, 360.0 / 37 + 0.3, Arc2D.PIE));
        }
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) (R * 0.72), new float[]{0f, 1f},
                new Color[]{hex("#6B4A2E"), hex("#2A1A10")}));
        g.fill(new Ellipse2D.Double(cx - R * 0.72, cy - R * 0.72, R * 1.44, R * 1.44));
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(6f));
        g.draw(new Ellipse2D.Double(cx - R, cy - R, 2 * R, 2 * R));
        g.setStroke(new BasicStroke(3f));
        g.draw(new Ellipse2D.Double(cx - R * 0.72, cy - R * 0.72, R * 1.44, R * 1.44));
        sphere(g, cx + R * 0.82, cy - R * 0.3, 9, WHITE);
        // tapis vert en bas
        g.setPaint(new GradientPaint(0, 200, hex("#17804F"), 0, 384, hex("#08391F")));
        g.fill(new Ellipse2D.Double(-120, 200, 752, 400));
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(3f));
        g.draw(new Ellipse2D.Double(-120, 200, 752, 400));
        card(g, 206, 196, -0.32, "A", "♠", false);
        card(g, 256, 186, 0, "R", "♥", true);
        card(g, 306, 196, 0.32, "D", "♦", true);
        chips(g, 90, 250, 5, hex("#D02A2A"));
        chips(g, 140, 262, 3, hex("#2050C8"));
        chips(g, 420, 255, 6, GOLD);
        chips(g, 372, 266, 2, hex("#1E8C45"));
    }

    static final Color[] BLOCKS = {ICE, BLUE, hex("#FF4FD8"), GOLD, hex("#7CFF8A"), hex("#FF8A3D"), hex("#C79BFF")};

    static void astroblocs(Graphics2D g, int w, int h, Game gm, Random r) {
        double s = 24, x0 = 256 - 5 * s, y0 = 10, wellH = 20 * s * 0.6;
        Shape well = new Rectangle2D.Double(x0 - 4, y0, 10 * s + 8, 280);
        g.setColor(alpha(INK, 170));
        g.fill(well);
        glow(g, well, gm.c1, 2.2f);
        // pile du fond (lignes avec trous)
        int rows = 6;
        for (int row = 0; row < rows; row++) {
            int hole = r.nextInt(10);
            for (int col = 0; col < 10; col++) {
                if (col == hole || (row == rows - 1 && r.nextInt(3) == 0)) {
                    continue;
                }
                bevel(g, x0 + col * s, y0 + 280 - (row + 1) * s - 2, s, BLOCKS[(row * 3 + col) % BLOCKS.length]);
            }
        }
        // pièce qui tombe (en L) avec traînée
        int[][] piece = {{0, 0}, {0, 1}, {0, 2}, {1, 2}};
        double px = x0 + 4 * s, py = y0 + 46;
        for (int t = 4; t >= 1; t--) {
            g.setColor(alpha(gm.c1, 22 * (5 - t)));
            for (int[] c : piece) {
                g.fill(new Rectangle2D.Double(px + c[0] * s, py + c[1] * s - t * 14, s, s));
            }
        }
        for (int[] c : piece) {
            bevel(g, px + c[0] * s, py + c[1] * s, s, hex("#FF4FD8"));
        }
        glowDot(g, px + s, py + 1.5 * s, 70, hex("#FF4FD8"));
        // astéroïdes autour du puits
        asteroid(g, 70, 90, 38, r);
        asteroid(g, 440, 70, 30, r);
        asteroid(g, 450, 200, 22, r);
        asteroid(g, 60, 220, 18, r);
        // pièces flottantes autour
        bevel(g, 90, 160, 20, GOLD);
        bevel(g, 110, 160, 20, GOLD);
        bevel(g, 400, 120, 20, hex("#7CFF8A"));
        bevel(g, 400, 140, 20, hex("#7CFF8A"));
        bevel(g, 420, 140, 20, hex("#7CFF8A"));
    }

    static void asteroid(Graphics2D g, double cx, double cy, double rad, Random r) {
        Path2D p = new Path2D.Double();
        int n = 11;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n, rr = rad * (0.78 + r.nextDouble() * 0.3);
            if (i == 0) {
                p.moveTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
            } else {
                p.lineTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
            }
        }
        p.closePath();
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx - rad * 0.4, cy - rad * 0.4), (float) (rad * 1.6), new float[]{0f, 1f},
                new Color[]{hex("#8A8F9E"), hex("#22252E")}));
        g.fill(p);
        for (int i = 0; i < 3; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * rad * 0.45, cr = rad * (0.12 + r.nextDouble() * 0.12);
            double x = cx + Math.cos(a) * d, y = cy + Math.sin(a) * d;
            g.setColor(alpha(INK, 110));
            g.fill(new Ellipse2D.Double(x - cr, y - cr, cr * 2, cr * 2));
            g.setColor(alpha(WHITE, 40));
            g.draw(new Arc2D.Double(x - cr, y - cr, cr * 2, cr * 2, 200, 140, Arc2D.OPEN));
        }
        g.setColor(alpha(ICE, 90));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(p);
    }

    static void gem(Graphics2D g, double cx, double cy, double s, int kind, Color c) {
        Path2D p = new Path2D.Double();
        int n = switch (kind) {
            case 0 -> 4;
            case 1 -> 6;
            case 2 -> 3;
            case 3 -> 8;
            default -> 5;
        };
        double rot = kind == 2 ? -Math.PI / 2 : (kind == 0 ? 0 : -Math.PI / 2);
        for (int i = 0; i < n; i++) {
            double a = rot + i * Math.PI * 2 / n;
            double x = cx + Math.cos(a) * s, y = cy + Math.sin(a) * s * (kind == 0 ? 1.25 : 1);
            if (i == 0) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        p.closePath();
        glowDot(g, cx, cy, s * 1.7, c);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx - s * 0.3, cy - s * 0.4), (float) (s * 1.5), new float[]{0f, 0.5f, 1f},
                new Color[]{mix(c, WHITE, 0.7), c, mix(c, INK, 0.55)}));
        g.fill(p);
        // facettes
        g.setColor(alpha(WHITE, 70));
        g.setStroke(new BasicStroke(1.2f));
        PathIterator it = p.getPathIterator(null);
        double[] pt = new double[6];
        while (!it.isDone()) {
            if (it.currentSegment(pt) != PathIterator.SEG_CLOSE) {
                g.draw(new Line2D.Double(cx, cy - s * 0.1, pt[0], pt[1]));
            }
            it.next();
        }
        g.setColor(alpha(WHITE, 210));
        g.fill(new Ellipse2D.Double(cx - s * 0.45, cy - s * 0.55, s * 0.32, s * 0.2));
        g.setColor(alpha(mix(c, WHITE, 0.5), 220));
        g.setStroke(new BasicStroke(1.6f));
        g.draw(p);
    }

    static void cristaux(Graphics2D g, int w, int h, Game gm, Random r) {
        Color[] cs = {hex("#FF4FD8"), ICE, GOLD, hex("#7CFF8A"), hex("#C79BFF"), hex("#FF5A5A")};
        double cell = 48, x0 = 256 - 4 * cell, y0 = 22;
        Shape board = new RoundRectangle2D.Double(x0 - 8, y0 - 8, cell * 8 + 16, cell * 5 + 16, 18, 18);
        g.setColor(alpha(new Color(0x140A2A), 200));
        g.fill(board);
        glow(g, board, gm.c1, 2.2f);
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 8; col++) {
                g.setColor(alpha(WHITE, (row + col) % 2 == 0 ? 10 : 22));
                g.fill(new Rectangle2D.Double(x0 + col * cell, y0 + row * cell, cell, cell));
            }
        }
        int[][] grid = new int[5][8];
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 8; col++) {
                grid[row][col] = r.nextInt(cs.length);
            }
        }
        grid[2][2] = grid[2][3] = grid[2][4] = 0; // un alignement en cours
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 8; col++) {
                int k = grid[row][col];
                gem(g, x0 + col * cell + cell / 2, y0 + row * cell + cell / 2, cell * 0.36, k % 5, cs[k]);
            }
        }
        Shape match = new RoundRectangle2D.Double(x0 + 2 * cell + 3, y0 + 2 * cell + 3, 3 * cell - 6, cell - 6, 14, 14);
        glow(g, match, WHITE, 2.2f);
        for (int i = 0; i < 6; i++) {
            sparkle(g, x0 + 2 * cell + r.nextDouble() * 3 * cell, y0 + 2 * cell + r.nextDouble() * cell, 4 + r.nextDouble() * 6, GOLD);
        }
        gem(g, 50, 120, 26, 1, GOLD);
        gem(g, 462, 100, 22, 0, ICE);
        gem(g, 470, 210, 16, 3, hex("#FF4FD8"));
    }

    static void drone(Graphics2D g, double cx, double cy, double s, Color c) {
        Path2D wingL = new Path2D.Double();
        wingL.moveTo(cx - s * 0.5, cy);
        wingL.lineTo(cx - s * 1.25, cy - s * 0.45);
        wingL.lineTo(cx - s * 1.1, cy + s * 0.55);
        wingL.closePath();
        Path2D wingR = new Path2D.Double();
        wingR.moveTo(cx + s * 0.5, cy);
        wingR.lineTo(cx + s * 1.25, cy - s * 0.45);
        wingR.lineTo(cx + s * 1.1, cy + s * 0.55);
        wingR.closePath();
        g.setColor(mix(c, INK, 0.35));
        g.fill(wingL);
        g.fill(wingR);
        Path2D hexa = new Path2D.Double();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 6 + i * Math.PI / 3;
            if (i == 0) {
                hexa.moveTo(cx + Math.cos(a) * s * 0.7, cy + Math.sin(a) * s * 0.7);
            } else {
                hexa.lineTo(cx + Math.cos(a) * s * 0.7, cy + Math.sin(a) * s * 0.7);
            }
        }
        hexa.closePath();
        g.setPaint(new GradientPaint((float) cx, (float) (cy - s * 0.7), mix(c, WHITE, 0.3), (float) cx, (float) (cy + s * 0.7), mix(c, INK, 0.4)));
        g.fill(hexa);
        glowDot(g, cx, cy, s * 0.5, hex("#FF4F6A"));
        g.setColor(WHITE);
        g.fill(new Ellipse2D.Double(cx - s * 0.12, cy - s * 0.12, s * 0.24, s * 0.24));
    }

    static void ship(Graphics2D g, double cx, double cy, double s, Color c, double angle) {
        AffineTransform at = g.getTransform();
        g.translate(cx, cy);
        g.rotate(angle);
        Path2D flame = new Path2D.Double();
        flame.moveTo(-s * 0.25, s * 0.55);
        flame.lineTo(0, s * 1.6);
        flame.lineTo(s * 0.25, s * 0.55);
        flame.closePath();
        glowDot(g, 0, s * 0.9, s * 0.8, hex("#FF8A3D"));
        g.setPaint(new GradientPaint(0, (float) (s * 0.55), WHITE, 0, (float) (s * 1.6), alpha(hex("#FF8A3D"), 0)));
        g.fill(flame);
        Path2D hull = new Path2D.Double();
        hull.moveTo(0, -s);
        hull.lineTo(s * 0.32, -s * 0.1);
        hull.lineTo(s, s * 0.45);
        hull.lineTo(s * 0.95, s * 0.65);
        hull.lineTo(s * 0.3, s * 0.5);
        hull.lineTo(0, s * 0.7);
        hull.lineTo(-s * 0.3, s * 0.5);
        hull.lineTo(-s * 0.95, s * 0.65);
        hull.lineTo(-s, s * 0.45);
        hull.lineTo(-s * 0.32, -s * 0.1);
        hull.closePath();
        g.setPaint(new GradientPaint((float) -s, 0, mix(c, INK, 0.3), (float) s, 0, mix(c, WHITE, 0.35)));
        g.fill(hull);
        g.setColor(alpha(WHITE, 200));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(hull);
        g.setPaint(new GradientPaint(0, (float) (-s * 0.6), WHITE, 0, (float) (s * 0.1), ICE));
        g.fill(new Ellipse2D.Double(-s * 0.13, -s * 0.55, s * 0.26, s * 0.5));
        g.setTransform(at);
    }

    static void defense(Graphics2D g, int w, int h, Game gm, Random r) {
        Color[] rows = {hex("#FF4FD8"), hex("#C79BFF"), gm.c1};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 7; col++) {
                drone(g, 88 + col * 56 + (row % 2) * 10, 40 + row * 40, 15, rows[row]);
            }
        }
        // boucliers en blocs
        for (int k = 0; k < 4; k++) {
            double bx = 62 + k * 112;
            for (int i = 0; i < 6; i++) {
                for (int j = 0; j < 3; j++) {
                    if (j == 2 && (i == 2 || i == 3)) {
                        continue;
                    }
                    if (r.nextInt(9) == 0) {
                        continue;
                    }
                    bevel(g, bx + i * 9, 196 + j * 9, 9, gm.c1);
                }
            }
        }
        // tirs
        neon(g, 256, 228, 256, 150, hex("#7CFF8A"), 2.4f);
        neon(g, 172, 120, 176, 170, hex("#FF4F6A"), 2f);
        neon(g, 368, 110, 362, 160, hex("#FF4F6A"), 2f);
        glowDot(g, 256, 138, 28, WHITE);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            neon(g, 256 + Math.cos(a) * 8, 138 + Math.sin(a) * 8, 256 + Math.cos(a) * 22, 138 + Math.sin(a) * 22, GOLD, 1.4f);
        }
        ship(g, 256, 252, 26, ICE, 0);
        // planète en bas à protéger
        g.setPaint(new RadialGradientPaint(new Point2D.Double(256, 720), 470, new float[]{0.86f, 0.9f, 1f},
                new Color[]{new Color(0x123A2A), gm.c1, alpha(gm.c1, 0)}));
        g.fill(new Ellipse2D.Double(-214, 250, 940, 940));
    }

    static void pulsar(Graphics2D g, int w, int h, Game gm, Random r) {
        double sx = 256, sy = 92;
        // faisceaux de l'étoile
        for (int k = 0; k < 2; k++) {
            double a = -0.55 + k * Math.PI;
            Path2D beam = new Path2D.Double();
            beam.moveTo(sx, sy);
            beam.lineTo(sx + Math.cos(a - 0.12) * 420, sy + Math.sin(a - 0.12) * 420);
            beam.lineTo(sx + Math.cos(a + 0.12) * 420, sy + Math.sin(a + 0.12) * 420);
            beam.closePath();
            g.setPaint(new RadialGradientPaint(new Point2D.Double(sx, sy), 420, new float[]{0f, 1f},
                    new Color[]{alpha(mix(gm.c1, WHITE, 0.5), 200), alpha(gm.c1, 0)}));
            g.fill(beam);
        }
        for (int k = 1; k <= 4; k++) {
            g.setColor(alpha(gm.c2, 120 - k * 22));
            g.setStroke(new BasicStroke(2f));
            g.draw(new Ellipse2D.Double(sx - k * 22, sy - k * 22 * 0.45, k * 44, k * 44 * 0.45));
        }
        glowDot(g, sx, sy, 60, gm.c1);
        glowDot(g, sx, sy, 22, WHITE);
        // égaliseur à l'horizon
        double hy = 168;
        for (int i = 0; i < 40; i++) {
            double bh = 6 + Math.abs(Math.sin(i * 0.7) * 26) + r.nextDouble() * 10;
            g.setColor(alpha(i % 2 == 0 ? gm.c1 : gm.c2, 150));
            g.fill(new Rectangle2D.Double(i * 12.8 + 2, hy - bh, 8.8, bh));
        }
        gridFloor(g, w, h, 256, hy, gm.c2, new Color(0x1A0A2E));
        // quatre couloirs
        double bottom = 384, spread = 300;
        for (int k = 0; k <= 4; k++) {
            double bx = 256 - spread + k * spread / 2, tx = 256 - 24 + k * 12;
            neon(g, tx, hy, bx, bottom, alpha(gm.c1, 200), 1.6f);
        }
        Color[] lanes = {hex("#62CFF4"), hex("#7CFF8A"), hex("#FFD166"), hex("#FF4FD8")};
        double[][] notes = {{0, 0.25}, {2, 0.4}, {1, 0.6}, {3, 0.5}, {0, 0.75}, {2, 0.85}};
        for (double[] n : notes) {
            double t = n[1];
            double y = hy + (bottom - hy) * t * 0.62;
            double half = 24 + (spread - 24) * t * 0.62;
            double lw = half / 2;
            double x = 256 - half + n[0] * lw;
            Shape note = new RoundRectangle2D.Double(x + lw * 0.12, y - 4 - t * 6, lw * 0.76, 8 + t * 12, 6, 6);
            glowDot(g, x + lw / 2, y, lw * 0.6, lanes[(int) n[0]]);
            g.setColor(lanes[(int) n[0]]);
            g.fill(note);
            g.setColor(alpha(WHITE, 200));
            g.draw(note);
        }
    }

    static void nebuleuse(Graphics2D g, int w, int h, Game gm, Random r) {
        // nourriture
        for (int i = 0; i < 70; i++) {
            glowDot(g, r.nextDouble() * w, r.nextDouble() * 280, 3 + r.nextDouble() * 4, BLOCKS[r.nextInt(BLOCKS.length)]);
        }
        cell(g, 330, 130, 84, gm.c1);
        cell(g, 140, 90, 44, hex("#FF4FD8"));
        cell(g, 470, 40, 26, GOLD);
        cell(g, 70, 230, 30, hex("#7CFF8A"));
        cell(g, 210, 200, 20, gm.c2);
        // comète : une traînée qui serpente
        for (int i = 26; i >= 0; i--) {
            double t = i / 26.0;
            double x = 40 + t * 230, y = 270 - Math.sin(t * Math.PI * 2.2) * 34 - t * 60;
            double s = 15 - t * 9;
            Color c = mix(gm.c2, hex("#FF4FD8"), t);
            glowDot(g, x, y, s * 1.8, c);
            sphere(g, x, y, s, c);
        }
        glowDot(g, 40, 270, 36, WHITE);
        sphere(g, 40, 270, 17, mix(gm.c2, WHITE, 0.4));
    }

    static void cell(Graphics2D g, double cx, double cy, double r, Color c) {
        glowDot(g, cx, cy, r * 1.5, c);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx - r * 0.3, cy - r * 0.35), (float) (r * 1.2), new float[]{0f, 0.7f, 1f},
                new Color[]{alpha(mix(c, WHITE, 0.5), 230), alpha(c, 200), alpha(mix(c, INK, 0.3), 240)}));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.setColor(alpha(mix(c, WHITE, 0.6), 230));
        g.setStroke(new BasicStroke((float) Math.max(2, r * 0.07)));
        g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.setColor(alpha(WHITE, 60));
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, new float[]{4, 7}, 0));
        g.draw(new Ellipse2D.Double(cx - r * 0.82, cy - r * 0.82, r * 1.64, r * 1.64));
        g.setColor(alpha(WHITE, 170));
        g.fill(new Ellipse2D.Double(cx - r * 0.55, cy - r * 0.6, r * 0.4, r * 0.22));
    }

    static void nova(Graphics2D g, int w, int h, Game gm, Random r) {
        double s = 34, x0 = 256 - 7 * s, y0 = 10;
        g.setColor(alpha(new Color(0x101828), 220));
        g.fill(new Rectangle2D.Double(x0, y0, 14 * s, 8 * s));
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 14; col++) {
                boolean wall = row == 0 || row == 7 || col == 0 || col == 13 || (row % 2 == 0 && col % 2 == 0);
                boolean crate = !wall && r.nextInt(4) == 0 && !(row >= 3 && row <= 5 && col >= 5 && col <= 9);
                if (wall) {
                    bevel(g, x0 + col * s, y0 + row * s, s, new Color(0x4A5470));
                } else if (crate) {
                    bevel(g, x0 + col * s, y0 + row * s, s, hex("#8A5A2E"));
                } else {
                    g.setColor(alpha(gm.c1, (row + col) % 2 == 0 ? 14 : 26));
                    g.fill(new Rectangle2D.Double(x0 + col * s, y0 + row * s, s, s));
                }
            }
        }
        // souffle en croix
        double bx = x0 + 9.5 * s, by = y0 + 3.5 * s;
        for (int k = 0; k < 2; k++) {
            Shape arm = k == 0 ? new RoundRectangle2D.Double(bx - 2.5 * s, by - s * 0.32, 5 * s, s * 0.64, 20, 20)
                    : new RoundRectangle2D.Double(bx - s * 0.32, by - 2.5 * s, s * 0.64, 5 * s, 20, 20);
            g.setColor(alpha(hex("#FF8A3D"), 120));
            g.fill(arm);
            glow(g, arm, GOLD, 2f);
        }
        glowDot(g, bx, by, 60, hex("#FFD166"));
        glowDot(g, bx, by, 24, WHITE);
        // charge nova avec mèche
        double nx = x0 + 5.5 * s, ny = y0 + 4.5 * s;
        glowDot(g, nx, ny, 50, gm.c1);
        sphere(g, nx, ny, 24, hex("#2A2F45"));
        g.setColor(gm.c1);
        g.setStroke(new BasicStroke(3f));
        g.draw(new Ellipse2D.Double(nx - 16, ny - 16, 32, 32));
        g.setColor(hex("#C9CED8"));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Double(nx + 10, ny - 20, nx + 18, ny - 38, nx + 30, ny - 40));
        sparkle(g, nx + 31, ny - 41, 7, GOLD);
    }

    static void palet(Graphics2D g, int w, int h, Game gm, Random r) {
        Path2D table = new Path2D.Double();
        table.moveTo(150, 40);
        table.lineTo(362, 40);
        table.lineTo(490, 300);
        table.lineTo(22, 300);
        table.closePath();
        g.setPaint(new GradientPaint(0, 40, hex("#1A3A5C"), 0, 300, hex("#4FA8D8")));
        g.fill(table);
        glow(g, table, gm.c1, 3f);
        // grille de trous d'air
        for (int row = 1; row < 12; row++) {
            double t = row / 12.0, y = 40 + 260 * t, half = 106 + 128 * t;
            for (int col = 1; col < 16; col++) {
                double x = 256 - half + col * half * 2 / 16;
                g.setColor(alpha(INK, 60));
                g.fill(new Ellipse2D.Double(x - 1.2 - t, y - 1, 2.4 + t * 2, 2 + t));
            }
        }
        neon(g, 22 + 64, 170, 490 - 64, 170, hex("#FF4F6A"), 2f);
        g.setColor(alpha(WHITE, 160));
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Double(256 - 50, 170 - 22, 100, 44));
        neon(g, 226, 40, 286, 40, gm.c2, 4f);
        neon(g, 206, 300, 306, 300, gm.c2, 4f);
        // palet et traînée
        for (int i = 6; i >= 1; i--) {
            g.setColor(alpha(gm.c2, 30 + i * 10));
            g.fill(new Ellipse2D.Double(300 - i * 14 - 14, 130 + i * 9 - 6, 28, 13));
        }
        glowDot(g, 300, 130, 40, gm.c2);
        g.setColor(INK);
        g.fill(new Ellipse2D.Double(284, 128, 32, 12));
        g.setColor(mix(gm.c2, INK, 0.2));
        g.fill(new Ellipse2D.Double(284, 122, 32, 13));
        // maillets
        mallet(g, 170, 250, 30, hex("#FF4FD8"));
        mallet(g, 330, 66, 16, gm.c1);
    }

    static void mallet(Graphics2D g, double cx, double cy, double r, Color c) {
        glowDot(g, cx, cy, r * 2, c);
        g.setColor(mix(c, INK, 0.5));
        g.fill(new Ellipse2D.Double(cx - r, cy - r * 0.35, r * 2, r * 0.8));
        g.setColor(c);
        g.fill(new Ellipse2D.Double(cx - r, cy - r * 0.6, r * 2, r * 0.8));
        g.setColor(mix(c, WHITE, 0.3));
        g.fill(new Ellipse2D.Double(cx - r * 0.4, cy - r * 1.3, r * 0.8, r * 0.9));
        g.setColor(alpha(WHITE, 180));
        g.fill(new Ellipse2D.Double(cx - r * 0.2, cy - r * 1.2, r * 0.3, r * 0.2));
    }

    static void bolide(Graphics2D g, double cx, double cy, double s, Color c, boolean faceRight) {
        AffineTransform at = g.getTransform();
        g.translate(cx, cy);
        if (!faceRight) {
            g.scale(-1, 1);
        }
        Path2D flame = new Path2D.Double();
        flame.moveTo(-s * 1.1, -s * 0.15);
        flame.lineTo(-s * 2.4, 0);
        flame.lineTo(-s * 1.1, s * 0.2);
        flame.closePath();
        glowDot(g, -s * 1.4, 0, s * 0.9, hex("#FF8A3D"));
        g.setPaint(new GradientPaint((float) (-s * 1.1), 0, WHITE, (float) (-s * 2.4), 0, alpha(hex("#FF8A3D"), 0)));
        g.fill(flame);
        Path2D car = new Path2D.Double();
        car.moveTo(-s * 1.15, s * 0.3);
        car.lineTo(-s * 1.1, -s * 0.25);
        car.lineTo(-s * 0.3, -s * 0.38);
        car.lineTo(s * 0.1, -s * 0.72);
        car.lineTo(s * 0.6, -s * 0.7);
        car.lineTo(s * 1.25, s * 0.05);
        car.lineTo(s * 1.2, s * 0.3);
        car.closePath();
        g.setPaint(new GradientPaint(0, (float) (-s * 0.7), mix(c, WHITE, 0.35), 0, (float) (s * 0.3), mix(c, INK, 0.35)));
        g.fill(car);
        g.setColor(alpha(WHITE, 210));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(car);
        g.setPaint(new GradientPaint(0, (float) (-s * 0.65), WHITE, 0, (float) (-s * 0.3), ICE));
        Path2D win = new Path2D.Double();
        win.moveTo(s * 0.15, -s * 0.62);
        win.lineTo(s * 0.55, -s * 0.6);
        win.lineTo(s * 0.85, -s * 0.3);
        win.lineTo(s * 0.0, -s * 0.33);
        win.closePath();
        g.fill(win);
        for (double wx : new double[]{-s * 0.7, s * 0.75}) {
            glowDot(g, wx, s * 0.38, s * 0.45, c);
            g.setColor(new Color(0x15161B));
            g.fill(new Ellipse2D.Double(wx - s * 0.26, s * 0.12, s * 0.52, s * 0.52));
            g.setColor(c);
            g.setStroke(new BasicStroke(2f));
            g.draw(new Ellipse2D.Double(wx - s * 0.16, s * 0.22, s * 0.32, s * 0.32));
        }
        g.setTransform(at);
    }

    static void turbo(Graphics2D g, int w, int h, Game gm, Random r) {
        gridFloor(g, w, h, 256, 150, gm.c1, new Color(0x0A2440));
        // but à droite
        Shape goal = new Rectangle2D.Double(400, 70, 96, 90);
        g.setColor(alpha(gm.c2, 40));
        g.fill(goal);
        g.setColor(alpha(WHITE, 80));
        for (int i = 0; i < 8; i++) {
            g.draw(new Line2D.Double(400 + i * 12, 70, 400 + i * 12, 160));
        }
        for (int i = 0; i < 7; i++) {
            g.draw(new Line2D.Double(400, 70 + i * 15, 496, 70 + i * 15));
        }
        glow(g, goal, gm.c2, 2.4f);
        // sphère
        double bx = 256, by = 110, br = 62;
        glowDot(g, bx, by, br * 1.6, gm.c1);
        sphere(g, bx, by, br, new Color(0x9AA8C0));
        Shape clip = g.getClip();
        g.clip(new Ellipse2D.Double(bx - br, by - br, br * 2, br * 2));
        g.setColor(alpha(new Color(0x1A2140), 150));
        g.setStroke(new BasicStroke(2.2f));
        for (int i = -3; i <= 3; i++) {
            for (int j = -3; j <= 3; j++) {
                double hx = bx + i * 30 + (j % 2 == 0 ? 0 : 15), hy = by + j * 26;
                Path2D hx6 = new Path2D.Double();
                for (int k = 0; k < 6; k++) {
                    double a = Math.PI / 6 + k * Math.PI / 3;
                    if (k == 0) {
                        hx6.moveTo(hx + Math.cos(a) * 15, hy + Math.sin(a) * 15);
                    } else {
                        hx6.lineTo(hx + Math.cos(a) * 15, hy + Math.sin(a) * 15);
                    }
                }
                hx6.closePath();
                g.draw(hx6);
            }
        }
        g.setClip(clip);
        g.setColor(alpha(WHITE, 120));
        g.fill(new Ellipse2D.Double(bx - br * 0.6, by - br * 0.7, br * 0.5, br * 0.3));
        bolide(g, 120, 220, 40, gm.c1, true);
        bolide(g, 400, 232, 34, hex("#FF8A3D"), false);
    }

    static void tower(Graphics2D g, double cx, double base, double s, Color c) {
        g.setPaint(new GradientPaint((float) (cx - s), 0, mix(c, INK, 0.55), (float) (cx + s), 0, mix(c, INK, 0.2)));
        g.fill(new Rectangle2D.Double(cx - s * 0.7, base - s * 2.4, s * 1.4, s * 2.4));
        for (int k = 0; k < 4; k++) {
            g.fill(new Rectangle2D.Double(cx - s * 0.8 + k * s * 0.46, base - s * 2.7, s * 0.3, s * 0.32));
        }
        g.setColor(mix(c, INK, 0.65));
        g.fill(new Rectangle2D.Double(cx - s * 0.85, base - s * 2.42, s * 1.7, s * 0.14));
        Path2D roof = new Path2D.Double();
        roof.moveTo(cx - s * 0.55, base - s * 2.7);
        roof.lineTo(cx, base - s * 3.8);
        roof.lineTo(cx + s * 0.55, base - s * 2.7);
        roof.closePath();
        g.setPaint(new GradientPaint((float) cx, (float) (base - s * 3.8), mix(c, WHITE, 0.3), (float) cx, (float) (base - s * 2.7), c));
        g.fill(roof);
        glowDot(g, cx, base - s * 4.05, s * 0.8, c);
        gem(g, cx, base - s * 4.05, s * 0.26, 0, mix(c, WHITE, 0.3));
        g.setColor(GOLD);
        g.fill(new RoundRectangle2D.Double(cx - s * 0.22, base - s * 0.9, s * 0.44, s * 0.9, s * 0.4, s * 0.4));
        for (int k = 0; k < 2; k++) {
            glowDot(g, cx - s * 0.35 + k * s * 0.7, base - s * 1.8, s * 0.22, GOLD);
        }
    }

    static void siege(Graphics2D g, int w, int h, Game gm, Random r) {
        // arène vue de face : rivière + pont
        g.setPaint(new GradientPaint(0, 160, hex("#1E4A2E"), 0, 384, hex("#0A1E12")));
        g.fill(new Rectangle2D.Double(0, 160, w, h - 160));
        g.setPaint(new GradientPaint(0, 176, hex("#62CFF4"), 0, 196, hex("#1E5A8A")));
        g.fill(new Rectangle2D.Double(0, 178, w, 18));
        g.setColor(hex("#6B4A2E"));
        g.fill(new Rectangle2D.Double(236, 172, 40, 30));
        tower(g, 110, 200, 36, gm.c1);
        tower(g, 402, 200, 36, hex("#E0484A"));
        // projectile qui traverse
        g.setColor(alpha(gm.c2, 200));
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, new float[]{6, 8}, 0));
        g.draw(new QuadCurve2D.Double(140, 110, 256, 20, 372, 100));
        glowDot(g, 372, 100, 22, gm.c2);
        sphere(g, 372, 100, 7, GOLD);
        // cartes en éventail
        String[] icons = {"⚔", "✦", "⚡", "⛨"};
        for (int i = 0; i < 4; i++) {
            AffineTransform at = g.getTransform();
            g.translate(256 + (i - 1.5) * 50, 262 + Math.abs(i - 1.5) * 8);
            g.rotate((i - 1.5) * 0.14);
            RoundRectangle2D c = new RoundRectangle2D.Double(-22, -32, 44, 62, 8, 8);
            g.setPaint(new GradientPaint(0, -32, mix(gm.c1, WHITE, 0.25), 0, 30, mix(gm.c1, INK, 0.5)));
            g.fill(c);
            g.setColor(GOLD);
            g.setStroke(new BasicStroke(2f));
            g.draw(c);
            g.setFont(font("Segoe UI Symbol", Font.PLAIN, 26));
            g.setColor(WHITE);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(icons[i], -fm.stringWidth(icons[i]) / 2f, 8);
            g.setColor(hex("#C79BFF"));
            g.fill(new Ellipse2D.Double(-20, -30, 13, 13));
            g.setTransform(at);
        }
        // jauge de plasma
        for (int i = 0; i < 10; i++) {
            g.setColor(i < 7 ? hex("#C79BFF") : alpha(hex("#C79BFF"), 50));
            g.fill(new RoundRectangle2D.Double(130 + i * 25.5, 220, 22, 7, 4, 4));
        }
    }

    static void chasseurs(Graphics2D g, int w, int h, Game gm, Random r) {
        // planète à anneau
        double px = 400, py = 90, pr = 70;
        g.setColor(alpha(gm.c2, 120));
        g.setStroke(new BasicStroke(5f));
        g.draw(new Arc2D.Double(px - pr * 1.8, py - pr * 0.4, pr * 3.6, pr * 0.8, 180, 180, Arc2D.OPEN));
        sphere(g, px, py, pr, hex("#C26A3A"));
        g.setColor(alpha(gm.c2, 200));
        g.draw(new Arc2D.Double(px - pr * 1.8, py - pr * 0.4, pr * 3.6, pr * 0.8, 0, 180, Arc2D.OPEN));
        asteroid(g, 70, 70, 34, r);
        asteroid(g, 200, 40, 16, r);
        asteroid(g, 470, 220, 26, r);
        asteroid(g, 40, 200, 20, r);
        // lasers croisés
        neon(g, 150, 210, 330, 120, gm.c1, 2.2f);
        neon(g, 165, 225, 345, 135, gm.c1, 2.2f);
        neon(g, 380, 200, 230, 160, hex("#FF4F6A"), 2.2f);
        ship(g, 130, 230, 34, gm.c1, Math.toRadians(62));
        ship(g, 400, 210, 28, hex("#FF8A3D"), Math.toRadians(-110));
        ship(g, 250, 120, 16, hex("#7CFF8A"), Math.toRadians(80));
        glowDot(g, 330, 130, 30, WHITE);
        sparkle(g, 330, 130, 8, GOLD);
    }
}
