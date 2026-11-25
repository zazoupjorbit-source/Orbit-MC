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
 * Pack de ressources du casino (écran de table « HD ») : cartes, jetons, boutons, sablier, fonds de table et
 * police du texte, en résolution x2 ou plus. Écrit aussi src/main/resources/hud-font.txt (largeurs du texte)
 * et des aperçus des écrans dans le dossier d'aperçus.
 *
 * Géométrie (identique aux menus du lobby) : coffre 6 lignes, la case (c, r) est en x = 8 + 18c, y = 18 + 18r ;
 * le titre s'écrit en (8, 6) et une glyphe d'« ascent » A a son haut en y = 13 - A.
 * Le fond couvre le haut du coffre (176 x 140 unités GUI) en 2 x 2 tuiles de 88 x 70.
 *
 * Usage : java CasinoPackGen.java <dossier_pack> <fichier_largeurs> <dossier_apercus>
 */
public class CasinoPackGen {

    static final String NS = "orbitcasino";
    static File pack;
    static final StringBuilder FONT = new StringBuilder();
    static final List<String> ITEMS = new ArrayList<>();

    // lignes de texte : haut (unités GUI) de chaque ligne
    static final int[] LINE_TOP = {5, 20, 62, 97};
    static final String CHARSET = " !\"%'()+,-./0123456789:;?ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
            + "àâçéèêëîïôùûÀÉÈÊÇ·×♠♥♦♣▶»«✦→";

    static final Color GOLD = new Color(0xE0B864), GOLD_HI = new Color(0xFFF0C2), GOLD_LO = new Color(0x8A6A2A);
    static final Color NAVY = new Color(0x0B1020), NAVY2 = new Color(0x141B33), BLUE = new Color(0x3A7FFF);
    static final Color FELT_HI = new Color(0x17804F), FELT = new Color(0x0F5E3A), FELT_LO = new Color(0x08391F);

    public static void main(String[] args) throws Exception {
        pack = new File(args[0]);
        File widths = new File(args[1]);
        File previews = new File(args[2]);
        previews.mkdirs();
        write("pack.mcmeta", "{\n  \"pack\": {\n    \"description\": \"§6ORBIT §7- casino\",\n    \"min_format\": 97,\n    \"max_format\": 97\n  }\n}\n");
        write("assets/" + NS + "/items/invisible.json", "{\n  \"model\": {\n    \"type\": \"minecraft:empty\"\n  }\n}\n");

        // ---------------- cartes
        String[] suits = {"s", "h", "d", "c"};
        for (int r = 2; r <= 14; r++) {
            for (int s = 0; s < 4; s++) {
                String id = "card_" + r + suits[s];
                png("item/" + id, card(r, s));
                item(id, id, 2.05, 0);
            }
        }
        png("item/card_back", cardBack(1.0));
        item("card_back", "card_back", 2.05, 0);
        item("card_back_g1", "card_back", 0.7, 0);
        item("card_back_g2", "card_back", 1.45, 0);
        png("item/card_back_half", squeeze(cardBack(1.0), 0.45));
        item("card_back_half", "card_back_half", 2.05, 0);
        png("item/card_edge", squeeze(cardBack(1.0), 0.08));
        item("card_edge", "card_edge", 2.05, 0);
        png("item/card_face_half", squeeze(card(0, 0), 0.45));
        item("card_face_half", "card_face_half", 2.05, 0);
        png("item/card_slot", cardSlot());
        item("card_slot", "card_slot", 2.05, 0);

        // ---------------- jetons
        int[][] chips = {{10, 0xE8E8E8, 0x1A1A1A}, {50, 0xD63A3A, 0xFFFFFF}, {100, 0x2F6BE0, 0xFFFFFF},
                {250, 0x2E9E55, 0xFFFFFF}, {500, 0x7B4AD6, 0xFFFFFF}, {1000, 0x1A1A1A, 0xE0B864}};
        for (int[] c : chips) {
            String id = "chip_" + c[0];
            png("item/" + id, chip(c[0], new Color(c[1]), new Color(c[2])));
            item(id, id, 1.25, 0);
            item(id + "_g", id, 0.6, 0);
        }

        // ---------------- boutons (2 cases de large : décalés d'une demi-case)
        button("btn_tirer", "TIRER", "+", new Color(0x2E9E55));
        button("btn_rester", "RESTER", "■", new Color(0xC23B3B));
        button("btn_doubler", "DOUBLER", "×2", new Color(0xC79A3A));
        button("btn_separer", "SÉPARER", "⇆", new Color(0x2F6BE0));
        button("btn_coucher", "SE COUCHER", "✕", new Color(0xC23B3B));
        button("btn_parole", "PAROLE", "✓", new Color(0x5A6880));
        button("btn_suivre", "SUIVRE", "=", new Color(0x2E9E55));
        button("btn_relancer", "RELANCER", "▲", new Color(0xC79A3A));
        button("btn_tapis", "TAPIS", "★", new Color(0x7B4AD6));
        for (String off : new String[]{"btn_doubler", "btn_separer", "btn_relancer", "btn_tirer", "btn_rester",
                "btn_coucher", "btn_parole", "btn_suivre", "btn_tapis"}) {
            png("item/" + off + "_off", grey(read("item/" + off)));
            item(off + "_off", off + "_off", 2.15, 9);
        }
        png("item/btn_quitter", square("✕", new Color(0x5A6270)));
        item("btn_quitter", "btn_quitter", 1.15, 0);
        png("item/btn_plus", square("+", new Color(0x3A7FFF)));
        item("btn_plus", "btn_plus", 1.15, 0);
        png("item/btn_moins", square("−", new Color(0x3A7FFF)));
        item("btn_moins", "btn_moins", 1.15, 0);
        png("item/timer", hourglass());
        item("timer", "timer", 1.1, 0);

        // ---------------- roulette : bille, et petits jetons posés dans le coin bas-droit d'une case du tapis
        png("item/rl_ball", ball());
        itemAt("rl_ball", "rl_ball", 0.55, -3.5, 3.5);
        for (int[] c : chips) {
            itemAt("rl_chip_" + c[0], "chip_" + c[0], 0.62, 3.5, -3.5);
        }

        // ---------------- police : espaces, fonds, texte
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
        BufferedImage bj = background(false);
        BufferedImage poker = background(true);
        png("font/bg_blackjack", bj);
        png("font/bg_poker", poker);
        bgProviders("bg_blackjack", 0xE600);
        bgProviders("bg_poker", 0xE610);
        BufferedImage roulette = rouletteBackground();
        png("font/bg_roulette", roulette);
        bgProviders("bg_roulette", 0xE620);
        // jeux du Salon des Étoiles (Crash, Mines, Plinko, roue, billard)
        BufferedImage crash = crashBackground();
        png("font/bg_crash", crash);
        bgProviders("bg_crash", 0xE630);
        BufferedImage mines = minesBackground();
        png("font/bg_mines", mines);
        bgProviders("bg_mines", 0xE640);
        salonItems();
        int[] widthTable = textFont();
        FONT.setLength(FONT.length() - 2);
        FONT.append("\n  ]\n}\n");
        write("assets/" + NS + "/font/hud.json", FONT.toString());

        // largeurs pour le plugin : index du caractère dans CHARSET → avance en unités GUI
        StringBuilder w = new StringBuilder("# Généré par tools/CasinoPackGen.java : caractère <tab> avance (unités GUI)\n");
        for (int i = 0; i < CHARSET.length(); i++) {
            w.append(CHARSET.codePointAt(i)).append('\t').append(widthTable[i]).append('\n');
        }
        Files.writeString(widths.toPath(), w.toString(), StandardCharsets.UTF_8);

        preview(bj, false, new File(previews, "casino-blackjack.png"));
        preview(poker, true, new File(previews, "casino-poker.png"));
        previewRoulette(roulette, new File(previews, "casino-roulette.png"));
        previewSalon(crash, mines, previews);
        System.out.println("OK pack casino : " + ITEMS.size() + " objets");
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

    /** Objet : modèle plat agrandi dans les menus (scale), décalé de tx unités GUI vers la droite. */
    static void item(String id, String texture, double scale, double tx) throws IOException {
        write("assets/" + NS + "/items/" + id + ".json", "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
                + NS + ":item/" + id + "\"\n  }\n}\n");
        String s = String.format(Locale.ROOT, "%.3f", scale);
        write("assets/" + NS + "/models/item/" + id + ".json", "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\"layer0\": \""
                + NS + ":item/" + texture + "\"},\n  \"gui_light\": \"front\",\n  \"display\": {\n    \"gui\": {\"rotation\": [0, 0, 0], \"translation\": ["
                + String.format(Locale.ROOT, "%.2f", tx) + ", 0, 0], \"scale\": [" + s + ", " + s + ", 1]}\n  }\n}\n");
        ITEMS.add(id);
    }

    /** Comme item(), avec un décalage vertical ty (unités GUI, vers le haut) en plus. */
    static void itemAt(String id, String texture, double scale, double tx, double ty) throws IOException {
        write("assets/" + NS + "/items/" + id + ".json", "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
                + NS + ":item/" + id + "\"\n  }\n}\n");
        String s = String.format(Locale.ROOT, "%.3f", scale);
        write("assets/" + NS + "/models/item/" + id + ".json", "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\"layer0\": \""
                + NS + ":item/" + texture + "\"},\n  \"gui_light\": \"front\",\n  \"display\": {\n    \"gui\": {\"rotation\": [0, 0, 0], \"translation\": ["
                + String.format(Locale.ROOT, "%.2f, %.2f", tx, ty) + ", 0], \"scale\": [" + s + ", " + s + ", 1]}\n  }\n}\n");
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

    // ================================================================== cartes (128 x 128, carte 84 x 118 au centre)

    static final int CW = 84, CH = 118, CX = (128 - CW) / 2, CY = (128 - CH) / 2;

    static void cardBody(Graphics2D g, Color fill) {
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new RoundRectangle2D.Float(CX + 2, CY + 3, CW, CH, 14, 14));
        g.setColor(fill);
        g.fill(new RoundRectangle2D.Float(CX, CY, CW, CH, 14, 14));
        g.setStroke(new BasicStroke(1.6f));
        g.setColor(new Color(0xB9B9B0));
        g.draw(new RoundRectangle2D.Float(CX + 0.8f, CY + 0.8f, CW - 1.6f, CH - 1.6f, 13, 13));
    }

    static String rank(int r) {
        return switch (r) {
            case 14 -> "A";
            case 13 -> "R";
            case 12 -> "D";
            case 11 -> "V";
            default -> String.valueOf(r);
        };
    }

    static final String[] SUIT = {"♠", "♥", "♦", "♣"};

    /** Face d'une carte ; rank 0 = carte blanche (image de retournement). */
    static BufferedImage card(int r, int s) {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        cardBody(g, new Color(0xFBFAF4));
        if (r == 0) {
            g.dispose();
            return img;
        }
        Color ink = s == 1 || s == 2 ? new Color(0xD42A2A) : new Color(0x16181F);
        g.setColor(ink);
        String rk = rank(r);
        // coins : rang + petite couleur
        g.setFont(font(Font.BOLD, rk.length() > 1 ? 25f : 28f));
        center(g, rk, CX + 15, CY + 27);
        g.setFont(symbols(20f));
        center(g, SUIT[s], CX + 15, CY + 46);
        AffineTransform old = g.getTransform();
        g.rotate(Math.PI, 64, 64);
        g.setFont(font(Font.BOLD, rk.length() > 1 ? 25f : 28f));
        center(g, rk, CX + 15, CY + 27);
        g.setFont(symbols(20f));
        center(g, SUIT[s], CX + 15, CY + 46);
        g.setTransform(old);
        if (r >= 11 && r <= 13) {
            // figure : cadre doré et grande lettre
            g.setColor(new Color(0xF3E7C4));
            g.fill(new RoundRectangle2D.Float(CX + 21, CY + 26, CW - 42, CH - 52, 8, 8));
            g.setStroke(new BasicStroke(2f));
            g.setColor(GOLD_LO);
            g.draw(new RoundRectangle2D.Float(CX + 21, CY + 26, CW - 42, CH - 52, 8, 8));
            g.setColor(ink);
            g.setFont(font(Font.BOLD, 40f));
            center(g, rk, 64, 64 + 8);
            g.setFont(symbols(22f));
            center(g, SUIT[s], 64, 64 + 30);
            g.setColor(GOLD_LO);
            g.setFont(symbols(16f));
            center(g, "♛", 64, CY + 42);
        } else {
            g.setFont(symbols(r == 14 ? 64f : 52f));
            center(g, SUIT[s], 64, 64 + (r == 14 ? 22 : 18));
        }
        g.dispose();
        return img;
    }

    static BufferedImage cardBack(double unused) {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        cardBody(g, new Color(0xFBFAF4));
        Shape inner = new RoundRectangle2D.Float(CX + 5, CY + 5, CW - 10, CH - 10, 9, 9);
        g.setPaint(new GradientPaint(CX, CY, new Color(0x1C3F99), CX + CW, CY + CH, new Color(0x0B1A3A)));
        g.fill(inner);
        g.setClip(inner);
        g.setStroke(new BasicStroke(1.3f));
        g.setColor(new Color(0x3A7FFF & 0xFFFFFF | 0x55000000, true));
        for (int i = -140; i < 140; i += 9) {
            g.draw(new Line2D.Float(CX + i, CY, CX + i + 140, CY + 140));
            g.draw(new Line2D.Float(CX + i + 140, CY, CX + i, CY + 140));
        }
        g.setClip(null);
        g.setStroke(new BasicStroke(2f));
        g.setColor(GOLD);
        g.draw(inner);
        // emblème ORBIT : anneau doré et étoile
        g.setColor(NAVY);
        g.fill(new Ellipse2D.Float(64 - 20, 64 - 20, 40, 40));
        g.setStroke(new BasicStroke(3f));
        g.setColor(GOLD);
        g.draw(new Ellipse2D.Float(64 - 17, 64 - 17, 34, 34));
        g.setStroke(new BasicStroke(1.6f));
        g.draw(new Ellipse2D.Float(64 - 28, 64 - 8, 56, 16));
        g.setFont(symbols(18f));
        center(g, "✦", 64, 64 + 7);
        g.dispose();
        return img;
    }

    /** Carte vue de biais pendant qu'elle se retourne : écrasée horizontalement autour du centre. */
    static BufferedImage squeeze(BufferedImage src, double fx) {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        int w = Math.max(2, (int) Math.round(128 * fx));
        g.drawImage(src, 64 - w / 2, 0, w, 128, null);
        g.dispose();
        return img;
    }

    static BufferedImage cardSlot() {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{6, 5}, 0));
        g.setColor(new Color(255, 255, 255, 70));
        g.draw(new RoundRectangle2D.Float(CX + 2, CY + 2, CW - 4, CH - 4, 12, 12));
        g.dispose();
        return img;
    }

    // ================================================================== jetons, boutons, sablier

    static BufferedImage chip(int value, Color c, Color ink) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setColor(new Color(0, 0, 0, 80));
        g.fill(new Ellipse2D.Float(4, 6, 56, 56));
        g.setColor(c);
        g.fill(new Ellipse2D.Float(3, 3, 56, 56));
        // créneaux du bord
        g.setColor(ink.equals(GOLD) ? GOLD : Color.WHITE);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            AffineTransform old = g.getTransform();
            g.rotate(a, 31, 31);
            g.fill(new RoundRectangle2D.Float(27, 3, 8, 9, 3, 3));
            g.setTransform(old);
        }
        g.setColor(c.darker());
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Float(13, 13, 36, 36));
        g.setColor(ink);
        String v = value >= 1000 ? "1K" : String.valueOf(value);
        g.setFont(font(Font.BOLD, v.length() >= 3 ? 16f : 19f));
        center(g, v, 31, 38);
        g.dispose();
        return img;
    }

    /** Bouton large (sur 2 cases) : bande de 120 x 56 au milieu d'une image 128 x 128. */
    static void button(String id, String label, String icon, Color c) throws IOException {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        RoundRectangle2D body = new RoundRectangle2D.Float(4, 36, 120, 56, 16, 16);
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Float(4, 40, 120, 56, 16, 16));
        g.setPaint(new GradientPaint(0, 36, c.brighter(), 0, 92, c.darker()));
        g.fill(body);
        g.setStroke(new BasicStroke(2.4f));
        g.setColor(new Color(255, 255, 255, 120));
        g.draw(new RoundRectangle2D.Float(5.5f, 37.5f, 117, 53, 14, 14));
        g.setColor(Color.WHITE);
        g.setFont(symbols(icon.length() > 1 ? 18f : 24f));
        g.drawString(icon, 12, 72);
        g.setFont(font(Font.BOLD, label.length() > 8 ? 14f : label.length() > 6 ? 16f : 19f));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, 84 - fm.stringWidth(label) / 2f, 71);
        g.dispose();
        png("item/" + id, img);
        item(id, id, 2.15, 9);
    }

    static BufferedImage square(String icon, Color c) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setPaint(new GradientPaint(0, 4, c.brighter(), 0, 60, c.darker()));
        g.fill(new RoundRectangle2D.Float(4, 4, 56, 56, 14, 14));
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(255, 255, 255, 110));
        g.draw(new RoundRectangle2D.Float(5, 5, 54, 54, 12, 12));
        g.setColor(Color.WHITE);
        g.setFont(symbols(30f));
        center(g, icon, 32, 43);
        g.dispose();
        return img;
    }

    static BufferedImage hourglass() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setColor(GOLD);
        g.fill(new RoundRectangle2D.Float(14, 6, 36, 6, 3, 3));
        g.fill(new RoundRectangle2D.Float(14, 52, 36, 6, 3, 3));
        Path2D glass = new Path2D.Float();
        glass.moveTo(18, 12);
        glass.lineTo(46, 12);
        glass.lineTo(34, 32);
        glass.lineTo(46, 52);
        glass.lineTo(18, 52);
        glass.lineTo(30, 32);
        glass.closePath();
        g.setColor(new Color(0xC4DAFF));
        g.fill(glass);
        g.setColor(new Color(0xE0B864));
        Path2D sand = new Path2D.Float();
        sand.moveTo(22, 52);
        sand.lineTo(42, 52);
        sand.lineTo(32, 40);
        sand.closePath();
        g.fill(sand);
        g.setStroke(new BasicStroke(2f));
        g.setColor(NAVY);
        g.draw(glass);
        g.dispose();
        return img;
    }

    static BufferedImage grey(BufferedImage src) {
        BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < src.getWidth(); x++) {
            for (int y = 0; y < src.getHeight(); y++) {
                int p = src.getRGB(x, y);
                int a = p >>> 24, r = (p >> 16) & 255, gg = (p >> 8) & 255, b = p & 255;
                int l = (r * 3 + gg * 6 + b) / 10;
                l = 40 + l / 3;
                img.setRGB(x, y, (Math.min(255, (int) (a * 0.75)) << 24) | (l << 16) | (l << 8) | (l + 6));
            }
        }
        return img;
    }

    // ================================================================== fonds de table (352 x 280 px = 176 x 140 GUI)

    static BufferedImage background(boolean poker) {
        int S = 2;
        BufferedImage img = new BufferedImage(176 * S, 140 * S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.scale(S, S);
        // cadre du coffre
        g.setColor(NAVY);
        g.fill(new RoundRectangle2D.Float(0, 0, 176, 140, 8, 8));
        g.setColor(NAVY2);
        g.fill(new Rectangle2D.Float(3, 3, 170, 13));
        g.setColor(GOLD_LO);
        g.fill(new Rectangle2D.Float(3, 16, 170, 0.8f));
        // tapis
        Shape felt = new RoundRectangle2D.Float(4, 18, 168, 88, 14, 14);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(88, 70), 100, new float[]{0f, 0.7f, 1f},
                new Color[]{FELT_HI, FELT, FELT_LO}));
        g.fill(felt);
        g.setClip(felt);
        Random rnd = new Random(7);
        for (int i = 0; i < 2600; i++) { // grain du feutre
            g.setColor(new Color(0, 0, 0, rnd.nextInt(18)));
            g.fill(new Rectangle2D.Float(4 + rnd.nextFloat() * 168, 18 + rnd.nextFloat() * 88, 0.5f, 0.5f));
        }
        g.setClip(null);
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(GOLD);
        g.draw(new RoundRectangle2D.Float(4.6f, 18.6f, 166.8f, 86.8f, 13, 13));
        g.setStroke(new BasicStroke(0.6f));
        g.setColor(new Color(0xE0B864 | 0x66000000, true));
        if (!poker) {
            // arc du croupier et règle imprimée sur le tapis
            g.draw(new Arc2D.Float(14, -30, 148, 120, 200, 140, Arc2D.OPEN));

        } else {
            // ovale de la table et places
            g.draw(new Ellipse2D.Float(26, 24, 124, 76));
            for (int r = 1; r <= 3; r++) {
                for (int c : new int[]{0, 8}) {
                    float x = 8 + 18 * c, y = 18 + 18 * r;
                    g.setColor(new Color(0, 0, 0, 70));
                    g.fill(new Ellipse2D.Float(x - 2, y - 2, 20, 20));
                    g.setColor(new Color(0xE0B864 | 0x77000000, true));
                    g.draw(new Ellipse2D.Float(x - 2, y - 2, 20, 20));
                }
            }

        }
        // barre des boutons
        g.setPaint(new GradientPaint(0, 106, NAVY2, 0, 126, NAVY));
        g.fill(new Rectangle2D.Float(3, 106.5f, 170, 19));
        g.setColor(GOLD_LO);
        g.fill(new Rectangle2D.Float(3, 106, 170, 0.6f));
        // plaque « Inventaire » (#404040 rend le libellé du jeu invisible) et transition vers l'inventaire
        g.setColor(new Color(0x404040));
        g.fill(new Rectangle2D.Float(3, 126, 170, 14));
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, 175, 139, 8, 8));
        g.dispose();
        return img;
    }

    /** 4 fournisseurs : 2 rangées de 2 tuiles 88 x 70 (176 x 140 px), ascent 13 puis 13 - 70. */
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

    // ================================================================== roulette

    static final Set<Integer> RL_RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    /**
     * Tapis de roulette dont chaque case tombe sur une case du coffre (Roulette.java a la même carte) :
     * cases 0-35 = numéros 1 à 36, 36 = zéro, 37-39 = douzaines, 40-42 = colonnes, 43 = manque, 44 = passe,
     * 45 = rouge, 46 = noir, 47 = pair, 48 = impair, 49-53 = plateau des jetons.
     */
    static BufferedImage rouletteBackground() {
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
        // tapis sous les 5 premières rangées
        Shape felt = new RoundRectangle2D.Float(4, 17, 168, 91, 10, 10);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(88, 62), 110, new float[]{0f, 0.7f, 1f},
                new Color[]{FELT_HI, FELT, FELT_LO}));
        g.fill(felt);
        g.setClip(felt);
        Random rnd = new Random(11);
        for (int i = 0; i < 2600; i++) {
            g.setColor(new Color(0, 0, 0, rnd.nextInt(18)));
            g.fill(new Rectangle2D.Float(4 + rnd.nextFloat() * 168, 17 + rnd.nextFloat() * 91, 0.5f, 0.5f));
        }
        g.setClip(null);
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(GOLD);
        g.draw(new RoundRectangle2D.Float(4.6f, 17.6f, 166.8f, 89.8f, 9, 9));
        // rangée du bas : barre sombre (chances simples + jetons)
        g.setPaint(new GradientPaint(0, 108, NAVY2, 0, 126, NAVY));
        g.fill(new Rectangle2D.Float(3, 108.5f, 170, 17.5f));
        g.setColor(GOLD_LO);
        g.fill(new Rectangle2D.Float(3, 108, 170, 0.6f));
        // plateau des jetons (cases 49 à 53)
        g.setColor(new Color(0x05070F));
        g.fill(new RoundRectangle2D.Float(8 + 18 * 4 - 1.5f, 108.8f + 0.7f, 18 * 5 - 0.5f, 16.4f, 6, 6));
        g.setColor(new Color(0xE0B864 | 0x66000000, true));
        g.setStroke(new BasicStroke(0.6f));
        g.draw(new RoundRectangle2D.Float(8 + 18 * 4 - 1.5f, 108.8f + 0.7f, 18 * 5 - 0.5f, 16.4f, 6, 6));

        Color red = new Color(0xC3302E), black = new Color(0x1C1D24), green = new Color(0x1E8E4E), dark = new Color(0x0A4A2C);
        for (int n = 1; n <= 36; n++) {
            cell(g, n - 1, RL_RED.contains(n) ? red : black, String.valueOf(n), Color.WHITE, 8.5f, null);
        }
        cell(g, 36, green, "0", Color.WHITE, 9.5f, null);
        String[] labels = {"1-12", "13-24", "25-36", "COL 1", "COL 2", "COL 3", "1-18", "19-36"};
        String[] subs = {"×3", "×3", "×3", "×3", "×3", "×3", "×2", "×2"};
        for (int k = 0; k < labels.length; k++) {
            cell(g, 37 + k, dark, labels[k], GOLD, labels[k].length() > 4 ? 4.6f : 5.2f, subs[k]);
        }
        cell(g, 45, dark, null, null, 0, "×2");
        cell(g, 46, dark, null, null, 0, "×2");
        diamond(g, 45, red);
        diamond(g, 46, new Color(0x0B0B10));
        cell(g, 47, dark, "PAIR", GOLD, 5f, "×2");
        cell(g, 48, dark, "IMPAIR", GOLD, 4.1f, "×2");

        // plaque « Inventaire » (#404040 rend le libellé du jeu invisible)
        g.setColor(new Color(0x404040));
        g.fill(new Rectangle2D.Float(3, 126, 170, 14));
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, 175, 139, 8, 8));
        g.dispose();
        return img;
    }

    /** Case du tapis (unités GUI) : fond arrondi, liseré doré, texte au centre, petite cote en bas. */
    static void cell(Graphics2D g, int slot, Color fill, String text, Color ink, float size, String sub) {
        float x = 8 + 18 * (slot % 9), y = 18 + 18 * (slot / 9);
        RoundRectangle2D box = new RoundRectangle2D.Float(x - 0.6f, y - 0.6f, 17.2f, 17.2f, 4, 4);
        g.setPaint(new GradientPaint(0, y, fill.brighter(), 0, y + 17, fill.darker()));
        g.fill(box);
        g.setStroke(new BasicStroke(0.5f));
        g.setColor(new Color(0xE0B864 | 0x99000000, true));
        g.draw(box);
        if (text != null) {
            g.setColor(ink);
            g.setFont(font(Font.BOLD, size));
            center(g, text, x + 8, y + (sub == null ? 8 + size * 0.36f : 7.2f + size * 0.3f));
        }
        if (sub != null) {
            g.setColor(new Color(0xB8C2D0));
            g.setFont(font(Font.BOLD, 3.8f));
            center(g, sub, x + 8, y + 14.6f);
        }
    }

    static void diamond(Graphics2D g, int slot, Color c) {
        float cx = 8 + 18 * (slot % 9) + 8, cy = 18 + 18 * (slot / 9) + 6.6f;
        Path2D d = new Path2D.Float();
        d.moveTo(cx, cy - 4.6f);
        d.lineTo(cx + 5.4f, cy);
        d.lineTo(cx, cy + 4.6f);
        d.lineTo(cx - 5.4f, cy);
        d.closePath();
        g.setColor(c);
        g.fill(d);
        g.setStroke(new BasicStroke(0.5f));
        g.setColor(GOLD);
        g.draw(d);
    }

    static BufferedImage ball() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new Ellipse2D.Float(14, 18, 40, 40));
        g.setPaint(new RadialGradientPaint(new Point2D.Float(26, 24), 28, new float[]{0f, 0.55f, 1f},
                new Color[]{Color.WHITE, new Color(0xE4E8EE), new Color(0x8C96A6)}));
        g.fill(new Ellipse2D.Float(12, 12, 40, 40));
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(2.4f));
        g.draw(new Ellipse2D.Float(12, 12, 40, 40));
        g.dispose();
        return img;
    }

    static void previewRoulette(BufferedImage bg, File out) throws IOException {
        int S = 3;
        BufferedImage img = new BufferedImage(176 * S, 222 * S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setColor(new Color(0xC6C6C6));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(bg, 0, 0, 176 * S, 140 * S, null);
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 9; c++) {
                g.setColor(new Color(0x8B8B8B));
                g.fillRect((7 + 18 * c) * S, (139 + 18 * r + (r == 3 ? 4 : 0)) * S, 18 * S, 18 * S);
            }
        }
        putAt(g, S, "chip_100", 0.62, 3.5, -3.5, 16);   // mise sur 17
        putAt(g, S, "chip_500", 0.62, 3.5, -3.5, 45);   // mise sur rouge
        putAt(g, S, "rl_ball", 0.55, -3.5, 3.5, 22);    // bille sur 23
        putAt(g, S, "chip_100", 1.25, 0, 0, 51);        // jeton choisi
        for (int k : new int[]{49, 50, 52, 53}) {
            putAt(g, S, new String[]{"chip_10", "chip_50", "", "chip_500", "chip_1000"}[k - 49], 0.6, 0, 0, k);
        }
        g.setFont(new Font("Segoe UI", Font.BOLD, 8 * S - 2));
        g.setColor(GOLD);
        g.drawString("ROULETTE · 24 S", 8 * S, 12 * S);
        g.setColor(new Color(0xE6EDF3));
        g.drawString("MISE 600", 128 * S, 12 * S);
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    static void putAt(Graphics2D g, int S, String texture, double scale, double tx, double ty, int slot) throws IOException {
        BufferedImage t = read("item/" + texture);
        double size = 16 * scale;
        double cx = 8 + 18 * (slot % 9) + 8 + tx, cy = 18 + 18 * (slot / 9) + 8 - ty;
        g.drawImage(t, (int) ((cx - size / 2) * S), (int) ((cy - size / 2) * S), (int) (size * S), (int) (size * S), null);
    }

    // ================================================================== police du texte

    /** Atlas 16 x N cases de 16 px (hauteur 8 GUI, x2), une copie par ligne de texte. Retourne les avances. */
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
            boolean isSym = "♠♥♦♣▶»«✦→·×".contains(ch);
            g.setFont(isSym ? sym : f);
            g.setColor(Color.WHITE);
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
            // avance Minecraft : (int)(0.5 + largeur * échelle) + 1, échelle = 8 / 16
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

    // ================================================================== aperçus (fond + objets aux cases)

    static void preview(BufferedImage bg, boolean poker, File out) throws IOException {
        int S = 3;
        BufferedImage img = new BufferedImage(176 * S, 222 * S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setColor(new Color(0xC6C6C6));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(bg, 0, 0, 176 * S, 140 * S, null);
        for (int r = 0; r < 4; r++) { // inventaire du joueur
            for (int c = 0; c < 9; c++) {
                g.setColor(new Color(0x8B8B8B));
                g.fillRect((7 + 18 * c) * S, (139 + 18 * r + (r == 3 ? 4 : 0)) * S, 18 * S, 18 * S);
            }
        }
        if (!poker) {
            put(g, S, "card_back", 2.05, 0, 3, 1);
            put(g, S, "card_13h", 2.05, 0, 4, 1);
            put(g, S, "card_14s", 2.05, 0, 3, 3);
            put(g, S, "card_10d", 2.05, 0, 4, 3);
            put(g, S, "card_slot", 2.05, 0, 5, 3);
            put(g, S, "btn_tirer", 2.15, 9, 0, 5);
            put(g, S, "btn_rester", 2.15, 9, 2, 5);
            put(g, S, "btn_doubler", 2.15, 9, 4, 5);
            put(g, S, "btn_separer_off", 2.15, 9, 6, 5);
            put(g, S, "btn_quitter", 1.15, 0, 8, 5);
            put(g, S, "timer", 1.1, 0, 8, 0);
        } else {
            for (int k = 0; k < 5; k++) {
                put(g, S, k < 3 ? "card_" + (10 + k) + "c" : "card_slot", 2.05, 0, 2 + k, 1);
            }
            put(g, S, "card_14h", 2.05, 0, 3, 3);
            put(g, S, "card_14d", 2.05, 0, 5, 3);
            put(g, S, "btn_coucher", 2.15, 9, 0, 5);
            put(g, S, "btn_suivre", 2.15, 9, 2, 5);
            put(g, S, "btn_relancer", 2.15, 9, 4, 5);
            put(g, S, "btn_tapis", 2.15, 9, 6, 5);
            put(g, S, "btn_quitter", 1.15, 0, 8, 5);
            put(g, S, "btn_moins", 1.15, 0, 7, 4);
            put(g, S, "btn_plus", 1.15, 0, 8, 4);
            put(g, S, "chip_500", 1.25, 0, 4, 2);
        }
        // repères des lignes de texte
        g.setFont(new Font("Segoe UI", Font.BOLD, 8 * S - 2));
        g.setColor(GOLD);
        String[] demo = poker ? new String[]{"POKER · NÉBULEUSE            1 250 OC", "POT  340 OC", "À TOI DE JOUER · 24 S",
                "PAIRE D'AS · TAPIS 1 200"} : new String[]{"BLACKJACK 1                 1 250 OC", "CROUPIER  10 + ?", "À TOI DE JOUER · 24 S",
                "TA MAIN  21 · MISE 100 OC"};
        for (int l = 0; l < LINE_TOP.length; l++) {
            g.drawString(demo[l], 8 * S, (LINE_TOP[l] + 7) * S);
        }
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    // ================================================================== Salon des Étoiles (Crash, Mines, Plinko, roue, billard)

    /**
     * Modèle plat à une seule face pour les objets posés dans le monde (ItemDisplay) : la face nord du modèle,
     * retournée par le rendu des ItemDisplay, regarde vers l'avant de l'entité (comme un TextDisplay).
     */
    static void flat(String id, String texture) throws IOException {
        write("assets/" + NS + "/items/" + id + ".json", "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
                + NS + ":item/" + id + "\"\n  }\n}\n");
        write("assets/" + NS + "/models/item/" + id + ".json", "{\n  \"textures\": {\"t\": \"" + NS + ":item/" + texture
                + "\", \"particle\": \"" + NS + ":item/" + texture + "\"},\n"
                + "  \"elements\": [{\"from\": [0, 0, 8], \"to\": [16, 16, 8], \"shade\": false,\n"
                + "    \"faces\": {\"north\": {\"uv\": [0, 0, 16, 16], \"texture\": \"#t\"}}}]\n}\n");
        ITEMS.add(id);
    }

    static final int[] POOL = {0xF4F1E6, 0xF2C230, 0x2050C8, 0xD02A2A, 0x6A2FA0, 0xF07A20, 0x1E8C45, 0x7A1F1F, 0x15161B};
    static final String[] WHEEL = {"x2", "x4", "x2", "x8", "x2", "x4", "x2", "x12", "x2", "x4", "x2", "orbit", "x2", "x8", "x4",
            "x2", "x12", "x2", "x4", "x2", "x8", "x4", "x2", "comete", "x2"};

    static void salonItems() throws IOException {
        // Crash
        png("item/crash_rocket", rocket());
        item("crash_rocket", "crash_rocket", 1.2, 0);
        png("item/crash_trail", dot(new Color(0x62CFF4)));
        item("crash_trail", "crash_trail", 0.55, 0);
        png("item/crash_boom", boom(new Color(0xFF7A2E), new Color(0xFFE27A)));
        item("crash_boom", "crash_boom", 1.35, 0);
        button("btn_miser", "MISER", "▶", new Color(0x2E9E55));
        button("btn_encaisser", "ENCAISSER", "✦", new Color(0xC79A3A));
        button("btn_auto", "AUTO", "↻", new Color(0x2F6BE0));
        for (String off : new String[]{"btn_miser", "btn_encaisser"}) {
            png("item/" + off + "_off", grey(read("item/" + off)));
            item(off + "_off", off + "_off", 2.15, 9);
        }
        // Mines
        png("item/mn_cell", tile(new Color(0x1B2A55), new Color(0x3A7FFF)));
        item("mn_cell", "mn_cell", 1.12, 0);
        png("item/mn_gem", gem());
        item("mn_gem", "mn_gem", 1.12, 0);
        png("item/mn_mine", mine(false));
        item("mn_mine", "mn_mine", 1.12, 0);
        png("item/mn_boom", mine(true));
        item("mn_boom", "mn_boom", 1.12, 0);
        // Billard, Plinko, roue : objets posés dans la salle
        for (int n = 0; n <= 15; n++) {
            png("item/pool_" + n, poolBall(n));
            flat("pool_" + n, "pool_" + n);
        }
        png("item/pk_ball", plinkoBall());
        flat("pk_ball", "pk_ball");
        png("item/wheel", wheel());
        flat("wheel", "wheel");
    }

    static BufferedImage rocket() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.rotate(Math.toRadians(45), 32, 32);
        // flamme
        Path2D flame = new Path2D.Float();
        flame.moveTo(25, 46);
        flame.quadTo(32, 66, 39, 46);
        flame.closePath();
        g.setPaint(new GradientPaint(32, 46, new Color(0xFFE27A), 32, 62, new Color(0xFF5A1E, false)));
        g.fill(flame);
        // ailerons
        g.setColor(GOLD);
        Path2D fins = new Path2D.Float();
        fins.moveTo(22, 34);
        fins.lineTo(14, 48);
        fins.lineTo(24, 46);
        fins.closePath();
        fins.moveTo(42, 34);
        fins.lineTo(50, 48);
        fins.lineTo(40, 46);
        fins.closePath();
        g.fill(fins);
        // corps
        Path2D body = new Path2D.Float();
        body.moveTo(32, 4);
        body.curveTo(44, 14, 43, 34, 40, 48);
        body.lineTo(24, 48);
        body.curveTo(21, 34, 20, 14, 32, 4);
        body.closePath();
        g.setPaint(new GradientPaint(22, 0, Color.WHITE, 44, 0, new Color(0xB9C3D6)));
        g.fill(body);
        g.setColor(new Color(0xD63A3A));
        Path2D nose = new Path2D.Float();
        nose.moveTo(32, 4);
        nose.curveTo(38, 9, 40, 13, 40.6f, 16);
        nose.lineTo(23.4f, 16);
        nose.curveTo(24, 13, 26, 9, 32, 4);
        g.fill(nose);
        g.setColor(NAVY);
        g.fill(new Ellipse2D.Float(26, 21, 12, 12));
        g.setColor(BLUE);
        g.fill(new Ellipse2D.Float(28, 23, 8, 8));
        g.setColor(new Color(255, 255, 255, 160));
        g.fill(new Ellipse2D.Float(29, 24, 3, 3));
        g.setStroke(new BasicStroke(1.4f));
        g.setColor(NAVY);
        g.draw(body);
        g.dispose();
        return img;
    }

    static BufferedImage dot(Color c) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(32, 32), 22, new float[]{0f, 0.45f, 1f},
                new Color[]{Color.WHITE, c, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new Ellipse2D.Float(10, 10, 44, 44));
        g.dispose();
        return img;
    }

    static Path2D star(float cx, float cy, float r1, float r2, int points, double rot) {
        Path2D s = new Path2D.Float();
        for (int i = 0; i < points * 2; i++) {
            double a = rot + i * Math.PI / points;
            float r = i % 2 == 0 ? r1 : r2;
            float x = cx + (float) (Math.cos(a) * r), y = cy + (float) (Math.sin(a) * r);
            if (i == 0) {
                s.moveTo(x, y);
            } else {
                s.lineTo(x, y);
            }
        }
        s.closePath();
        return s;
    }

    static BufferedImage boom(Color outer, Color inner) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setColor(new Color(0xD63A3A));
        g.fill(star(32, 32, 30, 16, 12, 0.2));
        g.setColor(outer);
        g.fill(star(32, 32, 24, 13, 10, 0.5));
        g.setColor(inner);
        g.fill(star(32, 32, 14, 8, 8, 0.1));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Float(27, 27, 10, 10));
        g.dispose();
        return img;
    }

    static BufferedImage tile(Color fill, Color edge) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Float(4, 6, 57, 57, 12, 12));
        g.setPaint(new GradientPaint(0, 3, fill.brighter(), 0, 60, fill.darker()));
        g.fill(new RoundRectangle2D.Float(3, 3, 57, 57, 12, 12));
        g.setStroke(new BasicStroke(2.2f));
        g.setColor(edge);
        g.draw(new RoundRectangle2D.Float(4, 4, 55, 55, 11, 11));
        g.setColor(new Color(255, 255, 255, 40));
        g.fill(new RoundRectangle2D.Float(7, 7, 49, 18, 8, 8));
        g.dispose();
        return img;
    }

    static BufferedImage gem() {
        BufferedImage img = tile(new Color(0x10324A), new Color(0x62CFF4));
        Graphics2D g = g(img);
        Path2D d = new Path2D.Float();
        d.moveTo(32, 12);
        d.lineTo(48, 26);
        d.lineTo(32, 52);
        d.lineTo(16, 26);
        d.closePath();
        g.setPaint(new GradientPaint(16, 12, new Color(0xE8FBFF), 48, 52, new Color(0x2FA7D9)));
        g.fill(d);
        g.setStroke(new BasicStroke(1.4f));
        g.setColor(new Color(0x0E5A80));
        g.draw(d);
        g.draw(new Line2D.Float(16, 26, 48, 26));
        g.draw(new Line2D.Float(26, 26, 32, 52));
        g.draw(new Line2D.Float(38, 26, 32, 52));
        g.setColor(Color.WHITE);
        g.fill(star(25, 19, 4, 1.4f, 4, 0));
        g.dispose();
        return img;
    }

    static BufferedImage mine(boolean exploded) {
        BufferedImage img = exploded ? tile(new Color(0x7A1A1A), new Color(0xFF5A4A)) : tile(new Color(0x23262E), new Color(0x5A6270));
        Graphics2D g = g(img);
        if (exploded) {
            g.setColor(new Color(0xFF7A2E));
            g.fill(star(32, 32, 26, 14, 10, 0.3));
            g.setColor(new Color(0xFFE27A));
            g.fill(star(32, 32, 14, 8, 8, 0.1));
        }
        g.setColor(new Color(0x15161B));
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            g.draw(new Line2D.Double(32, 32, 32 + Math.cos(a) * 19, 32 + Math.sin(a) * 19));
        }
        g.setPaint(new RadialGradientPaint(new Point2D.Float(28, 28), 16, new float[]{0f, 1f},
                new Color[]{new Color(0x5A6270), new Color(0x0B0C10)}));
        g.fill(new Ellipse2D.Float(19, 19, 26, 26));
        g.setColor(new Color(0xE0484A));
        g.fill(new Ellipse2D.Float(29, 29, 6, 6));
        g.dispose();
        return img;
    }

    static BufferedImage poolBall(int n) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        Color c = new Color(POOL[n <= 8 ? n : n - 8]);
        Ellipse2D ball = new Ellipse2D.Float(2, 2, 60, 60);
        g.setColor(n >= 9 ? new Color(POOL[0]) : c);
        g.fill(ball);
        if (n >= 9) {
            g.setClip(ball);
            g.setColor(c);
            g.fill(new Rectangle2D.Float(0, 17, 64, 30));
            g.setClip(null);
        }
        if (n > 0) {
            g.setColor(new Color(0xF8F6EE));
            g.fill(new Ellipse2D.Float(20, 20, 24, 24));
            g.setColor(new Color(0x15161B));
            String s = String.valueOf(n);
            g.setFont(font(Font.BOLD, s.length() > 1 ? 14f : 17f));
            center(g, s, 32, s.length() > 1 ? 37 : 38.5f);
        }
        // volume : reflet en haut à gauche, ombre en bas à droite
        g.setPaint(new RadialGradientPaint(new Point2D.Float(22, 18), 46, new float[]{0f, 0.35f, 1f},
                new Color[]{new Color(255, 255, 255, 150), new Color(255, 255, 255, 0), new Color(0, 0, 0, 110)}));
        g.fill(ball);
        g.dispose();
        return img;
    }

    static BufferedImage plinkoBall() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(24, 22), 40, new float[]{0f, 0.4f, 1f},
                new Color[]{Color.WHITE, new Color(0x62CFF4), new Color(0x1C3F99)}));
        g.fill(new Ellipse2D.Float(2, 2, 60, 60));
        g.setStroke(new BasicStroke(3f));
        g.setColor(GOLD);
        g.draw(new Ellipse2D.Float(3.5f, 3.5f, 57, 57));
        g.dispose();
        return img;
    }

    /** Roue des Étoiles : 25 cases, la case 0 en haut puis dans le sens des aiguilles d'une montre (Roue.java a la même liste). */
    static BufferedImage wheel() {
        int N = 512, C = 256;
        BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setPaint(new GradientPaint(0, 0, GOLD_HI, N, N, GOLD_LO));
        g.fill(new Ellipse2D.Float(2, 2, N - 4, N - 4));
        g.setColor(NAVY);
        g.fill(new Ellipse2D.Float(16, 16, N - 32, N - 32));
        double step = 360.0 / WHEEL.length;
        for (int k = 0; k < WHEEL.length; k++) {
            String s = WHEEL[k];
            Color c = new Color(switch (s) {
                case "x2" -> 0x2F6BE0;
                case "x4" -> 0x1C9CC4;
                case "x8" -> 0x7B4AD6;
                case "x12" -> 0xC79A3A;
                case "orbit" -> 0xD63A3A;
                default -> 0x2E9E55;
            });
            double start = 90 - k * step - step / 2;
            Arc2D wedge = new Arc2D.Double(22, 22, N - 44, N - 44, start, step, Arc2D.PIE);
            g.setPaint(new RadialGradientPaint(new Point2D.Float(C, C), C - 22, new float[]{0.2f, 1f}, new Color[]{c.darker(), c.brighter()}));
            g.fill(wedge);
            g.setStroke(new BasicStroke(3f));
            g.setColor(GOLD);
            g.draw(wedge);
            String label = switch (s) {
                case "orbit" -> "ORBIT";
                case "comete" -> "COMÈTE";
                default -> "×" + s.substring(1);
            };
            AffineTransform old = g.getTransform();
            g.rotate(Math.toRadians(k * step), C, C);
            g.translate(C, C - 160);
            g.rotate(-Math.PI / 2);
            g.setColor(Color.WHITE);
            g.setFont(font(Font.BOLD, label.length() > 3 ? 19f : 27f));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(label, -fm.stringWidth(label) / 2f, fm.getAscent() / 2f - 3);
            g.setTransform(old);
        }
        // clous dorés au bord, entre les cases
        for (int k = 0; k < WHEEL.length; k++) {
            double a = Math.toRadians(-90 + (k + 0.5) * step);
            float x = C + (float) Math.cos(a) * (C - 19), y = C + (float) Math.sin(a) * (C - 19);
            g.setColor(GOLD_HI);
            g.fill(new Ellipse2D.Float(x - 6, y - 6, 12, 12));
        }
        // moyeu
        g.setColor(NAVY);
        g.fill(new Ellipse2D.Float(C - 62, C - 62, 124, 124));
        g.setStroke(new BasicStroke(5f));
        g.setColor(GOLD);
        g.draw(new Ellipse2D.Float(C - 58, C - 58, 116, 116));
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new Ellipse2D.Float(C - 50, C - 16, 100, 32));
        g.setFont(font(Font.BOLD, 24f));
        center(g, "ORBIT", C, C + 9);
        g.dispose();
        return img;
    }

    static BufferedImage frame(int S) {
        BufferedImage img = new BufferedImage(176 * S, 140 * S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.scale(S, S);
        g.setColor(NAVY);
        g.fill(new RoundRectangle2D.Float(0, 0, 176, 140, 8, 8));
        g.setColor(NAVY2);
        g.fill(new Rectangle2D.Float(3, 3, 170, 13));
        g.setColor(GOLD_LO);
        g.fill(new Rectangle2D.Float(3, 16, 170, 0.8f));
        g.setPaint(new GradientPaint(0, 106, NAVY2, 0, 126, NAVY));
        g.fill(new Rectangle2D.Float(3, 108.5f, 170, 17.5f));
        g.setColor(GOLD_LO);
        g.fill(new Rectangle2D.Float(3, 108, 170, 0.6f));
        g.setColor(new Color(0x404040));
        g.fill(new Rectangle2D.Float(3, 126, 170, 14));
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, 175, 139, 8, 8));
        g.dispose();
        return img;
    }

    /** Ciel du Crash : cases 0 à 44 = trajectoire de la fusée, paliers de multiplicateur à droite de chaque rangée. */
    static BufferedImage crashBackground() {
        int S = 2;
        BufferedImage img = frame(S);
        Graphics2D g = g(img);
        g.scale(S, S);
        Shape sky = new RoundRectangle2D.Float(4, 17, 168, 91, 10, 10);
        g.setPaint(new GradientPaint(0, 17, new Color(0x05081A), 0, 108, new Color(0x162A5E)));
        g.fill(sky);
        g.setClip(sky);
        Random rnd = new Random(23);
        for (int i = 0; i < 140; i++) {
            float x = 4 + rnd.nextFloat() * 168, y = 17 + rnd.nextFloat() * 91, r = 0.3f + rnd.nextFloat() * 0.6f;
            g.setColor(new Color(255, 255, 255, 60 + rnd.nextInt(170)));
            g.fill(new Ellipse2D.Float(x, y, r, r));
        }
        // planète au loin et horizon
        g.setPaint(new RadialGradientPaint(new Point2D.Float(150, 40), 22, new float[]{0f, 1f},
                new Color[]{new Color(0x3A7FFF), new Color(0x0B1A3A)}));
        g.fill(new Ellipse2D.Float(132, 24, 34, 34));
        g.setColor(new Color(0xE0B864 | 0x88000000, true));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new Ellipse2D.Float(126, 37, 46, 8));
        g.setPaint(new GradientPaint(0, 100, new Color(0x3A7FFF & 0xFFFFFF | 0x00000000, true), 0, 108, new Color(0x62CFF4 | 0x66000000, true)));
        g.fill(new Rectangle2D.Float(4, 100, 168, 8));
        // repères des rangées
        g.setStroke(new BasicStroke(0.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{2, 2}, 0));
        String[] marks = {"×6.5", "×4", "×2.6", "×1.6", "×1"};
        for (int r = 0; r < 5; r++) {
            float y = 18 + 18 * r + 17.5f;
            g.setColor(new Color(0x62CFF4 | 0x33000000, true));
            if (r < 4) {
                g.draw(new Line2D.Float(6, y, 170, y));
            }
            g.setColor(new Color(0x9AA4B2));
            g.setFont(font(Font.BOLD, 3.6f));
            g.drawString(marks[r], 162, 18 + 18 * r + 16);
        }
        g.setClip(null);
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(GOLD);
        g.draw(new RoundRectangle2D.Float(4.6f, 17.6f, 166.8f, 89.8f, 9, 9));
        g.dispose();
        return img;
    }

    /** Mines : grille 5 x 5 en colonnes 2 à 6, panneaux de gain à gauche et à droite. */
    static BufferedImage minesBackground() {
        int S = 2;
        BufferedImage img = frame(S);
        Graphics2D g = g(img);
        g.scale(S, S);
        Shape felt = new RoundRectangle2D.Float(4, 17, 168, 91, 10, 10);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(88, 62), 110, new float[]{0f, 1f},
                new Color[]{new Color(0x16224A), new Color(0x070B18)}));
        g.fill(felt);
        // plateau de la grille
        g.setColor(new Color(0x03050C));
        g.fill(new RoundRectangle2D.Float(8 + 36 - 2.5f, 18 - 1.5f, 18 * 5 + 5, 91, 8, 8));
        g.setColor(new Color(0x3A7FFF | 0x77000000, true));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new RoundRectangle2D.Float(8 + 36 - 2.5f, 18 - 1.5f, 18 * 5 + 5, 91, 8, 8));
        // panneaux
        String[][] labels = {{"MULTIPLICATEUR", "À ENCAISSER"}, {"CASE SUIVANTE", "MINES"}};
        for (int side = 0; side < 2; side++) {
            float x = side == 0 ? 6 : 138;
            g.setColor(new Color(0x0B1020));
            g.fill(new RoundRectangle2D.Float(x, 19, 33, 87, 6, 6));
            g.setColor(new Color(0xE0B864 | 0x55000000, true));
            g.draw(new RoundRectangle2D.Float(x, 19, 33, 87, 6, 6));
            g.setColor(new Color(0x9AA4B2));
            g.setFont(font(Font.BOLD, 3.3f));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(labels[side][0], x + 16.5f - fm.stringWidth(labels[side][0]) / 2f, 33);
            g.drawString(labels[side][1], x + 16.5f - fm.stringWidth(labels[side][1]) / 2f, 75);
        }
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(GOLD);
        g.draw(new RoundRectangle2D.Float(4.6f, 17.6f, 166.8f, 89.8f, 9, 9));
        g.dispose();
        return img;
    }

    static void previewSalon(BufferedImage crash, BufferedImage mines, File dir) throws IOException {
        int S = 3;
        for (int which = 0; which < 2; which++) {
            BufferedImage img = new BufferedImage(176 * S, 222 * S, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = g(img);
            g.setColor(new Color(0xC6C6C6));
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.drawImage(which == 0 ? crash : mines, 0, 0, 176 * S, 140 * S, null);
            g.setFont(new Font("Segoe UI", Font.BOLD, 8 * S - 2));
            if (which == 0) {
                int[][] path = {{0, 4}, {1, 4}, {2, 4}, {3, 3}, {4, 3}, {5, 2}};
                for (int[] p : path) {
                    put(g, S, "crash_trail", 0.55, 0, p[0], p[1]);
                }
                put(g, S, "crash_rocket", 1.2, 0, 6, 2);
                put(g, S, "btn_moins", 1.15, 0, 0, 5);
                put(g, S, "chip_100", 1.25, 0, 1, 5);
                put(g, S, "btn_plus", 1.15, 0, 2, 5);
                put(g, S, "btn_encaisser", 2.15, 9, 3, 5);
                put(g, S, "btn_auto", 2.15, 9, 6, 5);
                put(g, S, "btn_quitter", 1.15, 0, 8, 5);
                g.setColor(GOLD);
                g.drawString("CRASH · EN VOL", 8 * S, 12 * S);
                g.drawString("×2.34", 80 * S, 27 * S);
            } else {
                for (int r = 0; r < 5; r++) {
                    for (int c = 0; c < 5; c++) {
                        String m = r == 1 && c == 2 || r == 3 && c == 0 || r == 2 && c == 4 ? "mn_gem" : r == 4 && c == 3 ? "mn_mine" : "mn_cell";
                        put(g, S, m, 1.12, 0, 2 + c, r);
                    }
                }
                put(g, S, "btn_moins", 1.15, 0, 0, 5);
                put(g, S, "chip_100", 1.25, 0, 1, 5);
                put(g, S, "btn_plus", 1.15, 0, 2, 5);
                put(g, S, "btn_encaisser", 2.15, 9, 3, 5);
                put(g, S, "mn_mine", 1.12, 0, 6, 5);
                put(g, S, "btn_quitter", 1.15, 0, 8, 5);
                g.setColor(GOLD);
                g.drawString("MINES · 3 MINES", 8 * S, 12 * S);
                g.drawString("×1.38", 10 * S, 27 * S);
            }
            g.dispose();
            ImageIO.write(img, "png", new File(dir, which == 0 ? "casino-crash.png" : "casino-mines.png"));
        }
        // roue et billes de billard : planche d'aperçu
        BufferedImage sheet = new BufferedImage(1100, 520, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(sheet);
        g.setColor(new Color(0x0B1020));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.drawImage(read("item/wheel"), 4, 4, 512, 512, null);
        for (int n = 0; n <= 15; n++) {
            g.drawImage(read("item/pool_" + n), 540 + (n % 8) * 68, 40 + (n / 8) * 70, 64, 64, null);
        }
        g.drawImage(read("item/pk_ball"), 540, 200, 64, 64, null);
        g.dispose();
        ImageIO.write(sheet, "png", new File(dir, "casino-roue-billard.png"));
    }

    static void put(Graphics2D g, int S, String texture, double scale, double tx, int c, int r) throws IOException {
        String file = texture.startsWith("chip") || texture.equals("timer") || texture.startsWith("btn_q") || texture.startsWith("btn_p")
                && texture.length() <= 8 || texture.startsWith("btn_m") ? texture : texture;
        BufferedImage t = read("item/" + file);
        double size = 16 * scale;
        double cx = 8 + 18 * c + 8 + tx, cy = 18 + 18 * r + 8;
        g.drawImage(t, (int) ((cx - size / 2) * S), (int) ((cy - size / 2) * S), (int) (size * S), (int) (size * S), null);
    }
}
