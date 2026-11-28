import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Random;

/**
 * Génère le pack d'ORBIT BOWL : tout est dessiné ici par code (aucune image reprise d'ailleurs).
 * - icônes de la barre et sprites des jeux : modèles « item/generated » ;
 * - grandes images (logo, cible de fléchettes, drapeau, panier, filet, trou) : plan simple à deux faces ;
 * - quille : vrai modèle 3D (pile de pavés, col bleu et or).
 * Usage : java BowlPackGen.java <dossier du pack>
 */
public final class BowlPackGen {

    static File root;

    static final Color BLEU = new Color(0x3A7FFF), OR = new Color(0xE0B864), GLACE = new Color(0x62CFF4), NUIT = new Color(0x0C0E14),
            ARDOISE = new Color(0x1A1E28), GRIS = new Color(0x9AA4B2), ROUGE = new Color(0xE0484A), ROSE = new Color(0xFF4FD8),
            VERT = new Color(0x7CFF8A), ORANGE = new Color(0xFF8A3D), BLANC = new Color(0xF2F4F8);

    interface Peintre {
        void peindre(Graphics2D g, int w, int h);
    }

    public static void main(String[] args) throws IOException {
        root = new File(args.length > 0 ? args[0] : "pack");
        ecrire("pack.mcmeta", "{\n  \"pack\": {\n    \"description\": \"§9ORBIT §6BOWL\",\n    \"min_format\": 97,\n    \"max_format\": 97\n  }\n}\n");

        // grandes images (plans à deux faces)
        plan("logo", 512, 128, BowlPackGen::logo);
        plan("cible", 256, 256, BowlPackGen::cible);
        plan("drapeau", 32, 64, BowlPackGen::drapeau);
        plan("panier", 64, 64, BowlPackGen::panier);
        plan("filet", 64, 64, BowlPackGen::filet);
        plan("trou", 32, 32, BowlPackGen::trou);

        // bowling
        quille();
        boule("boule_bleue", BLEU, new Color(0x0E2A6B), GLACE, 1);
        boule("boule_or", OR, new Color(0x6B4A12), new Color(0xFFF1C9), 2);
        boule("boule_nebuleuse", new Color(0x8A4DFF), new Color(0x2A0D5E), ROSE, 3);
        boule("boule_glace", GLACE, new Color(0x1F6F8F), BLANC, 4);
        boule("boule_rubis", ROUGE, new Color(0x5E0D14), new Color(0xFFB0B4), 5);
        icone("regles", 32, BowlPackGen::regles);
        icone("quitter", 32, BowlPackGen::quitter);

        // fléchettes
        icone("flechette", 32, (g, w, h) -> flechette(g, 32, false));
        icone("flechette_vol", 32, (g, w, h) -> flechette(g, 32, true));

        // tir aux comètes
        icone("carabine", 32, BowlPackGen::carabine);
        icone("tir_meteore", 64, BowlPackGen::meteore);
        icone("tir_soucoupe", 64, BowlPackGen::soucoupe);
        icone("tir_comete", 64, BowlPackGen::comete);
        icone("tir_satellite", 64, BowlPackGen::satellite);

        // basket
        icone("ballon", 64, BowlPackGen::ballon);

        // baby-foot
        icone("fig_bleu", 64, (g, w, h) -> figurine(g, BLEU, new Color(0x1C3F8C)));
        icone("fig_or", 64, (g, w, h) -> figurine(g, OR, new Color(0x8C6A1C)));
        icone("balle_baby", 32, (g, w, h) -> balle(g, 32, BLANC, new Color(0xB8C0CC)));
        icone("barre", 32, BowlPackGen::barre);

        // golf
        icone("putter", 32, BowlPackGen::putter);
        icone("golf_bleu", 32, (g, w, h) -> balle(g, 32, new Color(0x8FB6FF), BLEU));
        icone("golf_or", 32, (g, w, h) -> balle(g, 32, new Color(0xFFE6A6), OR));
        icone("golf_rose", 32, (g, w, h) -> balle(g, 32, new Color(0xFFB3EE), ROSE));
        icone("golf_vert", 32, (g, w, h) -> balle(g, 32, new Color(0xC2FFCA), new Color(0x3DBF4E)));

        // laser
        icone("pistolet_laser", 32, BowlPackGen::pistolet);

        System.out.println("Pack d'ORBIT BOWL généré dans " + root.getPath());
    }

    // ------------------------------------------------------------------ fichiers

    static BufferedImage dessiner(int w, int h, Peintre p) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        p.peindre(g, w, h);
        g.dispose();
        return img;
    }

    static void texture(String nom, BufferedImage img) throws IOException {
        File f = new File(root, "assets/orbitbowl/textures/item/" + nom + ".png");
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }

    static void definition(String nom) throws IOException {
        ecrire("assets/orbitbowl/items/" + nom + ".json",
                "{\n  \"model\": { \"type\": \"minecraft:model\", \"model\": \"orbitbowl:item/" + nom + "\" }\n}\n");
    }

    /** Icône / sprite : texture carrée, modèle item/generated (visible des deux côtés). */
    static void icone(String nom, int taille, Peintre p) throws IOException {
        texture(nom, dessiner(taille, taille, p));
        ecrire("assets/orbitbowl/models/item/" + nom + ".json",
                "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": { \"layer0\": \"orbitbowl:item/" + nom + "\" }\n}\n");
        definition(nom);
    }

    /** Grande image : un plan de 16 × 16 au milieu du bloc, une face de chaque côté (lisible des deux côtés). */
    static void plan(String nom, int w, int h, Peintre p) throws IOException {
        texture(nom, dessiner(w, h, p));
        String face = "{ \"uv\": [0, 0, 16, 16], \"texture\": \"#t\" }";
        ecrire("assets/orbitbowl/models/item/" + nom + ".json", "{\n  \"textures\": { \"t\": \"orbitbowl:item/" + nom + "\", \"particle\": \"orbitbowl:item/"
                + nom + "\" },\n  \"elements\": [\n    { \"from\": [0, 0, 8], \"to\": [16, 16, 8], \"shade\": false,\n      \"faces\": { \"north\": " + face
                + ", \"south\": " + face + " } }\n  ]\n}\n");
        definition(nom);
    }

    static void ecrire(String chemin, String contenu) throws IOException {
        File f = new File(root, chemin);
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), contenu, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ logo

    static void logo(Graphics2D g, int w, int h) {
        // halo bleu derrière le texte
        g.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, h / 2f), w / 2f, new float[]{0f, 1f},
                new Color[]{new Color(58, 127, 255, 70), new Color(58, 127, 255, 0)}));
        g.fillRect(0, 0, w, h);
        // étoiles
        Random r = new Random(7);
        for (int i = 0; i < 40; i++) {
            int x = r.nextInt(w), y = r.nextInt(h), s = r.nextInt(3) == 0 ? 2 : 1;
            g.setColor(new Color(255, 255, 255, 90 + r.nextInt(120)));
            g.fillRect(x, y, s, s);
        }
        Font f = new Font("SansSerif", Font.BOLD, 84);
        double large = f.createGlyphVector(g.getFontRenderContext(), "ORBITBOWL").getVisualBounds().getWidth() + 74 + 28 + 10;
        if (large > w - 30) {
            f = f.deriveFont((float) (84 * (w - 30) / large));
        }
        // « ORBIT » en dégradé bleu → glace, « BOWL » en or ; une planète à anneau entre les deux
        GlyphVector orbit = f.createGlyphVector(g.getFontRenderContext(), "ORBIT");
        GlyphVector bowl = f.createGlyphVector(g.getFontRenderContext(), "BOWL");
        double wo = orbit.getVisualBounds().getWidth(), wb = bowl.getVisualBounds().getWidth();
        double planete = 74, gap = 14;
        double total = wo + gap + planete + gap + wb;
        double x0 = (w - total) / 2, base = h / 2.0 + orbit.getVisualBounds().getHeight() / 2;
        java.awt.Shape so = orbit.getOutline((float) (x0 - orbit.getVisualBounds().getX()), (float) base);
        java.awt.Shape sb = bowl.getOutline((float) (x0 + wo + gap + planete + gap - bowl.getVisualBounds().getX()), (float) base);
        // ombre portée
        g.setColor(new Color(0, 0, 0, 150));
        g.fill(AffineTransform.getTranslateInstance(3, 4).createTransformedShape(so));
        g.fill(AffineTransform.getTranslateInstance(3, 4).createTransformedShape(sb));
        g.setPaint(new GradientPaint((float) x0, 20, BLEU, (float) (x0 + wo), h - 20, GLACE));
        g.fill(so);
        g.setPaint(new GradientPaint(0, 20, new Color(0xFFE7A8), 0, h - 20, OR));
        g.fill(sb);
        g.setStroke(new BasicStroke(2.2f));
        g.setColor(new Color(0x0C1A3A));
        g.draw(so);
        g.setColor(new Color(0x4A3410));
        g.draw(sb);
        // planète-boule avec anneau d'or
        double cx = x0 + wo + gap + planete / 2, cy = h / 2.0 + 2, rr = 26;
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(OR);
        g.draw(new Arc2D.Double(cx - 46, cy - 12, 92, 24, 0, 180, Arc2D.OPEN));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx - 9, cy - 10), (float) (rr * 1.4), new float[]{0f, 0.6f, 1f},
                new Color[]{GLACE, BLEU, new Color(0x0E2A6B)}));
        g.fill(new Ellipse2D.Double(cx - rr, cy - rr, rr * 2, rr * 2));
        g.setColor(new Color(0x0C1A3A));
        for (double[] t : new double[][]{{-6, -12}, {4, -14}, {-1, -3}}) {
            g.fill(new Ellipse2D.Double(cx + t[0] - 3.5, cy + t[1] - 3.5, 7, 7));
        }
        g.setColor(OR);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(cx - 46, cy - 12, 92, 24, 180, 180, Arc2D.OPEN));
        // filet doré sous le logo
        g.setPaint(new GradientPaint((float) x0, 0, new Color(224, 184, 100, 0), w / 2f, 0, OR, true));
        g.fillRect((int) x0, h - 14, (int) total, 3);
    }

    // ------------------------------------------------------------------ fléchettes

    static final int[] SECTEURS = {20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5};

    /** Cible : rayons en fraction du rayon affiché (0.9 bloc), identiques à ceux du calcul des points. */
    static void cible(Graphics2D g, int w, int h) {
        double c = w / 2.0;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        double rAff = 0.9;
        double bull = 0.045 / rAff, r25 = 0.10 / rAff, t1 = 0.38 / rAff, t2 = 0.45 / rAff, d1 = 0.65 / rAff, d2 = 0.72 / rAff;
        Color sombre = new Color(0x141826), clair = new Color(0xEDE3CC);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = (x + 0.5 - c) / c, dy = (y + 0.5 - c) / c, r = Math.hypot(dx, dy);
                if (r > 1) {
                    continue;
                }
                double ang = Math.toDegrees(Math.atan2(dx, -dy));
                if (ang < 0) {
                    ang += 360;
                }
                int s = (int) Math.floor((ang + 9) / 18) % 20;
                boolean pair = s % 2 == 0;
                Color col;
                if (r < bull) {
                    col = OR;
                } else if (r < r25) {
                    col = BLEU;
                } else if (r < t1 || (r >= t2 && r < d1)) {
                    col = pair ? sombre : clair;
                } else if (r < t2 || r < d2) {
                    col = pair ? new Color(0x2F6BE0) : new Color(0xD9A944);
                } else if (r < 0.985) {
                    col = new Color(0x0B0D12);
                } else {
                    col = OR;
                }
                // fils métalliques entre les secteurs et autour des anneaux
                double frac = ((ang + 9) % 18) / 18.0;
                boolean fil = r > r25 && r < d2 && (frac < 0.012 || frac > 0.988);
                for (double ring : new double[]{r25, t1, t2, d1, d2}) {
                    fil |= Math.abs(r - ring) < 0.006;
                }
                img.setRGB(x, y, (fil ? new Color(0xC9CED8) : col).getRGB());
            }
        }
        g.drawImage(img, 0, 0, null);
    }

    static void flechette(Graphics2D g, int s, boolean vol) {
        AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(vol ? -30 : -45), s / 2.0, s / 2.0);
        g.setColor(new Color(0xC9CED8));
        g.fillRect(s / 2 - 1, 2, 2, 8); // pointe
        g.setPaint(new GradientPaint(0, 10, OR, 0, 20, new Color(0x8C6A1C)));
        g.fillRoundRect(s / 2 - 2, 9, 4, 11, 2, 2); // fût
        g.setColor(BLEU);
        Path2D ail = new Path2D.Double();
        ail.moveTo(s / 2.0, 19);
        ail.lineTo(s / 2.0 - 7, 29);
        ail.lineTo(s / 2.0, 26);
        ail.lineTo(s / 2.0 + 7, 29);
        ail.closePath();
        g.fill(ail);
        g.setColor(GLACE);
        g.drawLine(s / 2, 19, s / 2, 29);
        g.setTransform(old);
    }

    // ------------------------------------------------------------------ bowling

    static void boule(String nom, Color base, Color ombre, Color reflet, int graine) throws IOException {
        icone(nom, 64, (g, w, h) -> {
            double c = 32, r = 30;
            g.setPaint(new RadialGradientPaint(new Point2D.Double(22, 20), 44f, new float[]{0f, 0.55f, 1f}, new Color[]{reflet, base, ombre}));
            g.fill(new Ellipse2D.Double(c - r, c - r, r * 2, r * 2));
            // veines de la boule (nébuleuse)
            Random rnd = new Random(graine);
            g.setClip(new Ellipse2D.Double(c - r, c - r, r * 2, r * 2));
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 4; i++) {
                Path2D p = new Path2D.Double();
                double x = rnd.nextInt(64), y = rnd.nextInt(64);
                p.moveTo(x, y);
                for (int k = 0; k < 3; k++) {
                    p.curveTo(rnd.nextInt(64), rnd.nextInt(64), rnd.nextInt(64), rnd.nextInt(64), rnd.nextInt(64), rnd.nextInt(64));
                }
                g.setColor(new Color(reflet.getRed(), reflet.getGreen(), reflet.getBlue(), 70));
                g.draw(p);
            }
            g.setClip(null);
            // trois trous de doigts
            g.setColor(new Color(0x07080C));
            g.fill(new Ellipse2D.Double(24, 17, 7, 7));
            g.fill(new Ellipse2D.Double(34, 17, 7, 7));
            g.fill(new Ellipse2D.Double(28, 29, 8, 8));
            g.setColor(new Color(255, 255, 255, 120));
            g.fill(new Ellipse2D.Double(13, 11, 10, 6));
        });
    }

    /** Quille 3D : pile de pavés blancs, col bleu et or. Base en y 0, sommet en y 16. */
    static void quille() throws IOException {
        texture("quille", dessiner(16, 16, (g, w, h) -> {
            g.setColor(BLANC);
            g.fillRect(0, 0, 8, 8);
            g.setColor(BLEU);
            g.fillRect(8, 0, 8, 8);
            g.setColor(OR);
            g.fillRect(0, 8, 8, 8);
            g.setColor(new Color(0xDCE1EA));
            g.fillRect(8, 8, 8, 8);
        }));
        double[][] etages = { // y0, y1, largeur, zone (0 blanc, 1 bleu, 2 or, 3 blanc ombré)
                {0, 1, 2.6, 3}, {1, 3, 4.0, 0}, {3, 6, 4.7, 0}, {6, 8, 4.0, 0}, {8, 9, 2.8, 1}, {9, 10, 2.5, 2},
                {10, 11.5, 2.1, 0}, {11.5, 14.5, 3.1, 0}, {14.5, 16, 2.3, 0}};
        String[] uv = {"[2, 2, 6, 6]", "[10, 2, 14, 6]", "[2, 10, 6, 14]", "[10, 10, 14, 14]"};
        StringBuilder sb = new StringBuilder("{\n  \"textures\": { \"q\": \"orbitbowl:item/quille\", \"particle\": \"orbitbowl:item/quille\" },\n  \"elements\": [\n");
        for (int i = 0; i < etages.length; i++) {
            double[] e = etages[i];
            double a = 8 - e[2] / 2, b = 8 + e[2] / 2;
            double a2 = 8 - e[2] * 0.46, b2 = 8 + e[2] * 0.46;
            String f = "{ \"uv\": " + uv[(int) e[3]] + ", \"texture\": \"#q\" }";
            sb.append(String.format(Locale.ROOT, "    { \"from\": [%.2f, %.2f, %.2f], \"to\": [%.2f, %.2f, %.2f],\n      \"faces\": { \"north\": %s, "
                    + "\"south\": %s, \"east\": %s, \"west\": %s, \"up\": %s, \"down\": %s } }", a, e[0], a, b, e[1], b, f, f, f, f, f, f));
            // pavé tourné de 45° pour arrondir le profil
            sb.append(",\n");
            sb.append(String.format(Locale.ROOT, "    { \"from\": [%.2f, %.2f, %.2f], \"to\": [%.2f, %.2f, %.2f], \"rotation\": { \"angle\": 45, \"axis\": \"y\", "
                    + "\"origin\": [8, 8, 8] },\n      \"faces\": { \"north\": %s, \"south\": %s, \"east\": %s, \"west\": %s, \"up\": %s, \"down\": %s } }",
                    a2, e[0] + 0.02, a2, b2, e[1] - 0.02, b2, f, f, f, f, f, f));
            sb.append(i + 1 < etages.length ? ",\n" : "\n");
        }
        sb.append("  ]\n}\n");
        ecrire("assets/orbitbowl/models/item/quille.json", sb.toString());
        definition("quille");
    }

    static void regles(Graphics2D g, int w, int h) {
        g.setColor(new Color(0x0E2A6B));
        g.fillRoundRect(5, 3, 22, 26, 4, 4);
        g.setColor(BLEU);
        g.fillRoundRect(7, 3, 20, 24, 3, 3);
        g.setColor(OR);
        g.fillRect(7, 3, 3, 24);
        g.setColor(BLANC);
        for (int y = 9; y <= 21; y += 4) {
            g.fillRect(13, y, 10, 2);
        }
        g.setColor(OR);
        g.drawRoundRect(7, 3, 20, 24, 3, 3);
    }

    static void quitter(Graphics2D g, int w, int h) {
        g.setColor(ARDOISE);
        g.fillRoundRect(4, 3, 16, 26, 4, 4);
        g.setStroke(new BasicStroke(2f));
        g.setColor(GRIS);
        g.drawRoundRect(4, 3, 16, 26, 4, 4);
        g.setColor(ROUGE);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(13, 16, 28, 16);
        g.drawLine(23, 11, 28, 16);
        g.drawLine(23, 21, 28, 16);
    }

    // ------------------------------------------------------------------ tir aux comètes

    static void carabine(Graphics2D g, int w, int h) {
        AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(-35), 16, 16);
        g.setColor(new Color(0x5A3A1C));
        g.fillRoundRect(2, 15, 10, 7, 3, 3); // crosse
        g.setPaint(new GradientPaint(0, 12, ORANGE, 0, 20, new Color(0x9C4A14)));
        g.fillRoundRect(9, 12, 14, 7, 3, 3); // corps
        g.setColor(new Color(0xC9CED8));
        g.fillRect(22, 14, 8, 3); // canon
        g.setColor(GLACE);
        g.fillOval(13, 13, 4, 4);
        g.setColor(OR);
        g.fillRect(11, 19, 3, 4); // détente
        g.setTransform(old);
    }

    static void meteore(Graphics2D g, int w, int h) {
        // traînée de feu
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(255, 120 + i * 20, 40, 60 + i * 25));
            g.fill(new Ellipse2D.Double(4 + i * 4, 4 + i * 4, 22 - i * 1.5, 22 - i * 1.5));
        }
        g.setPaint(new RadialGradientPaint(new Point2D.Double(34, 34), 22f, new float[]{0f, 1f}, new Color[]{new Color(0x8A7A6A), new Color(0x3A2E26)}));
        Path2D roc = new Path2D.Double();
        double[][] pts = {{40, 18}, {52, 26}, {56, 40}, {48, 54}, {32, 56}, {20, 46}, {20, 30}, {28, 20}};
        roc.moveTo(pts[0][0], pts[0][1]);
        for (double[] p : pts) {
            roc.lineTo(p[0], p[1]);
        }
        roc.closePath();
        g.fill(roc);
        g.setColor(new Color(0x2A201A));
        g.fill(new Ellipse2D.Double(30, 32, 8, 6));
        g.fill(new Ellipse2D.Double(42, 42, 6, 5));
        g.setColor(new Color(255, 170, 80, 200));
        g.setStroke(new BasicStroke(2f));
        g.draw(roc);
        chiffre(g, "10", OR);
    }

    static void soucoupe(Graphics2D g, int w, int h) {
        g.setPaint(new GradientPaint(0, 14, new Color(0xBFF3FF), 0, 32, GLACE));
        g.fill(new Ellipse2D.Double(20, 12, 24, 22)); // dôme
        g.setPaint(new GradientPaint(0, 26, new Color(0xD6DCE6), 0, 42, new Color(0x5A6270)));
        g.fill(new Ellipse2D.Double(4, 26, 56, 16)); // disque
        g.setColor(VERT);
        for (int i = 0; i < 5; i++) {
            g.fill(new Ellipse2D.Double(10 + i * 10, 32, 5, 4));
        }
        g.setColor(new Color(124, 255, 138, 90));
        Path2D rayon = new Path2D.Double();
        rayon.moveTo(24, 42);
        rayon.lineTo(40, 42);
        rayon.lineTo(48, 62);
        rayon.lineTo(16, 62);
        rayon.closePath();
        g.fill(rayon);
        chiffre(g, "25", OR);
    }

    static void comete(Graphics2D g, int w, int h) {
        for (int i = 0; i < 14; i++) {
            g.setColor(new Color(255, 230, 160, 18 + i * 10));
            double s = 10 + i * 1.4;
            g.fill(new Ellipse2D.Double(4 + i * 2.6 - s / 2 + 6, 4 + i * 2.6 - s / 2 + 6, s, s));
        }
        etoile(g, 42, 42, 18, 8, new Color(0xFFE7A8), OR);
        g.setColor(BLANC);
        g.fill(new Ellipse2D.Double(38, 38, 8, 8));
        chiffre(g, "60", VERT);
    }

    static void satellite(Graphics2D g, int w, int h) {
        g.setColor(new Color(0xC9CED8));
        g.fillRect(8, 30, 48, 4);
        for (int s : new int[]{2, 42}) {
            g.setPaint(new GradientPaint(s, 20, BLEU, s + 20, 44, new Color(0x0E2A6B)));
            g.fillRect(s, 20, 20, 24);
            g.setColor(GLACE);
            for (int k = 1; k < 4; k++) {
                g.drawLine(s + k * 5, 20, s + k * 5, 44);
            }
            g.drawLine(s, 32, s + 20, 32);
        }
        g.setPaint(new GradientPaint(0, 18, new Color(0xFFE7A8), 0, 46, OR));
        g.fillRoundRect(23, 18, 18, 28, 5, 5);
        g.setColor(ARDOISE);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.drawString("O", 27, 37);
        // interdit : cercle barré rouge
        g.setStroke(new BasicStroke(4f));
        g.setColor(new Color(224, 72, 74, 230));
        g.draw(new Ellipse2D.Double(5, 5, 54, 54));
        g.drawLine(14, 50, 50, 14);
    }

    static void chiffre(Graphics2D g, String s, Color c) {
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.setColor(new Color(0, 0, 0, 170));
        g.drawString(s, 4, 62);
        g.setColor(c);
        g.drawString(s, 3, 61);
    }

    static void etoile(Graphics2D g, double cx, double cy, double r1, double r2, Color c1, Color c2) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double a = Math.PI / 5 * i - Math.PI / 2, r = i % 2 == 0 ? r1 : r2;
            if (i == 0) {
                p.moveTo(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
            } else {
                p.lineTo(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
            }
        }
        p.closePath();
        g.setPaint(new GradientPaint((float) cx, (float) (cy - r1), c1, (float) cx, (float) (cy + r1), c2));
        g.fill(p);
    }

    // ------------------------------------------------------------------ basket

    static void ballon(Graphics2D g, int w, int h) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(24, 22), 40f, new float[]{0f, 1f}, new Color[]{new Color(0xFFB070), new Color(0xB04A10)}));
        g.fill(new Ellipse2D.Double(2, 2, 60, 60));
        g.setClip(new Ellipse2D.Double(2, 2, 60, 60));
        g.setColor(new Color(0x2A1408));
        g.setStroke(new BasicStroke(2.4f));
        g.drawLine(32, 0, 32, 64);
        g.drawLine(0, 32, 64, 32);
        g.draw(new Arc2D.Double(-22, 6, 44, 52, -60, 120, Arc2D.OPEN));
        g.draw(new Arc2D.Double(42, 6, 44, 52, 120, 120, Arc2D.OPEN));
        g.setClip(null);
        g.setColor(new Color(0x2A1408));
        g.draw(new Ellipse2D.Double(2, 2, 60, 60));
    }

    /** Arceau vu de dessus : anneau orange au rayon du cercle de calcul (0.45 / 0.525). */
    static void panier(Graphics2D g, int w, int h) {
        double r = 32 * 0.857;
        g.setStroke(new BasicStroke(3.4f));
        g.setColor(new Color(0xFF5A1F));
        g.draw(new Ellipse2D.Double(32 - r, 32 - r, r * 2, r * 2));
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(0xFFB070));
        g.draw(new Ellipse2D.Double(32 - r + 1.2, 32 - r + 1.2, r * 2 - 2.4, r * 2 - 2.4));
    }

    static void filet(Graphics2D g, int w, int h) {
        g.setStroke(new BasicStroke(1.4f));
        g.setColor(new Color(240, 244, 248, 220));
        double haut = 58, bas = 38;
        for (int i = 0; i <= 8; i++) {
            double xa = 32 - haut / 2 + haut * i / 8, xb = 32 - bas / 2 + bas * i / 8;
            g.draw(new java.awt.geom.Line2D.Double(xa, 4, xb, 58));
        }
        for (int j = 0; j <= 4; j++) {
            double y = 4 + j * 13.5, l = haut + (bas - haut) * j / 4.0;
            g.draw(new java.awt.geom.Line2D.Double(32 - l / 2, y, 32 + l / 2, y));
        }
    }

    // ------------------------------------------------------------------ baby-foot

    static void figurine(Graphics2D g, Color maillot, Color sombre) {
        // tige qui traverse la figurine, tête, maillot, short, jambes jointes et pied
        g.setColor(new Color(0xF0C8A0));
        g.fill(new Ellipse2D.Double(24, 4, 16, 16));
        g.setColor(new Color(0x3A2A1A));
        g.fill(new Arc2D.Double(24, 3, 16, 12, 0, 180, Arc2D.PIE));
        g.setPaint(new GradientPaint(0, 20, maillot, 0, 40, sombre));
        g.fill(new RoundRectangle2D.Double(21, 19, 22, 20, 6, 6));
        g.setColor(BLANC);
        g.fillRect(30, 22, 4, 12);
        g.setColor(new Color(0x14161E));
        g.fillRect(23, 38, 18, 7);
        g.setColor(maillot);
        g.fillRect(25, 45, 14, 11);
        g.setColor(new Color(0x14161E));
        g.fillRoundRect(22, 55, 20, 7, 4, 4);
    }

    static void balle(Graphics2D g, int s, Color clair, Color sombre) {
        double r = s / 2.0 - 2;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(s * 0.38, s * 0.34), (float) (r * 1.6), new float[]{0f, 1f}, new Color[]{clair, sombre}));
        g.fill(new Ellipse2D.Double(s / 2.0 - r, s / 2.0 - r, r * 2, r * 2));
        g.setColor(new Color(255, 255, 255, 150));
        g.fill(new Ellipse2D.Double(s * 0.3, s * 0.24, s * 0.18, s * 0.12));
    }

    static void barre(Graphics2D g, int w, int h) {
        g.setColor(new Color(0xC9CED8));
        g.fillRect(2, 14, 28, 4);
        g.setColor(BLEU);
        g.fillRoundRect(8, 8, 6, 16, 3, 3);
        g.fillRoundRect(19, 8, 6, 16, 3, 3);
        g.setColor(ARDOISE);
        g.fillRoundRect(0, 12, 5, 8, 2, 2);
    }

    // ------------------------------------------------------------------ golf

    static void putter(Graphics2D g, int w, int h) {
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xC9CED8));
        g.drawLine(22, 3, 10, 25);
        g.setColor(ARDOISE);
        g.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(22, 3, 19, 9);
        g.setPaint(new GradientPaint(0, 24, OR, 0, 29, new Color(0x8C6A1C)));
        g.fillRoundRect(4, 24, 14, 5, 3, 3);
        g.setColor(VERT);
        g.fillOval(22, 23, 6, 6);
    }

    static void drapeau(Graphics2D g, int w, int h) {
        g.setColor(new Color(0xE6EDF3));
        g.fillRect(3, 2, 2, 62);
        Path2D f = new Path2D.Double();
        f.moveTo(5, 3);
        f.curveTo(14, 1, 20, 8, 30, 5);
        f.lineTo(30, 21);
        f.curveTo(20, 24, 14, 17, 5, 20);
        f.closePath();
        g.setPaint(new GradientPaint(5, 3, BLEU, 30, 20, GLACE));
        g.fill(f);
        etoile(g, 17, 12, 5, 2.2, new Color(0xFFE7A8), OR);
    }

    static void trou(Graphics2D g, int w, int h) {
        g.setColor(OR);
        g.fill(new Ellipse2D.Double(1, 1, 30, 30));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(16, 16), 14f, new float[]{0f, 1f}, new Color[]{new Color(0x000000), new Color(0x1A1E28)}));
        g.fill(new Ellipse2D.Double(3, 3, 26, 26));
    }

    // ------------------------------------------------------------------ laser

    static void pistolet(Graphics2D g, int w, int h) {
        AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(-30), 16, 16);
        g.setPaint(new GradientPaint(0, 10, new Color(0x3A3F4E), 0, 20, ARDOISE));
        g.fillRoundRect(6, 10, 20, 8, 4, 4);
        g.fillRoundRect(8, 16, 6, 11, 3, 3);
        g.setColor(ROSE);
        g.fillRect(9, 12, 15, 2);
        g.setColor(GLACE);
        g.fillOval(24, 11, 6, 6);
        g.setColor(BLANC);
        g.fillOval(26, 13, 2, 2);
        g.setTransform(old);
    }
}
