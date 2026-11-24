import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Apparence 3D des 6 coffres orbitaux du spawn (affichée par un ItemDisplay posé sur le bloc coffre, Coffres.java).
 * Même forme pour les 6 : socle doré, corps, couvercle, bandeau et 4 colonnes d'angle dorées, serrure avec gemme
 * au nord et au sud, gemme sur le couvercle. Chaque style a sa palette et son motif (mêmes couleurs que les clés).
 *
 * Sorties (pack/assets/orbittycoon/) : textures/item/coffre_<style>_{cote,dessus,gemme}.png, coffre_metal.png,
 *   models/item/coffre_<style>.json, items/coffre_<style>.json ; aperçu (faces avant agrandies) en option.
 * Usage : java tools/CoffresModeles.java <dossier_pack> [<apercu.png>]
 */
public class CoffresModeles {

    record Palette(int a, int b, int c, int d, int e, int o) {}

    static final Map<String, Palette> STYLES = new LinkedHashMap<>();
    static {
        STYLES.put("cryo", new Palette(0x62CFF4, 0xD8F6FF, 0x2F86B8, 0xFFFFFF, 0xA8E9FF, 0x0B2A4A));
        STYLES.put("solaire", new Palette(0xE0B864, 0xFFE9A8, 0xA8792A, 0xFFF6D6, 0xFF9A3B, 0x4A2E08));
        STYLES.put("nebuleuse", new Palette(0xC77DFF, 0xE8C6FF, 0x5A2A8C, 0xFFFFFF, 0xFF7AD9, 0x2A0B48));
        STYLES.put("asteroide", new Palette(0x8E9AA6, 0xC3CCD4, 0x5A646E, 0x7CFC9A, 0x2FCB62, 0x1C2228));
        STYLES.put("reacteur", new Palette(0xFF4D4D, 0xFFB0A0, 0x4A4F5A, 0xFFE066, 0xFF9A3B, 0x24080A));
        STYLES.put("menagerie", new Palette(0xFF8FC7, 0xFFD1E8, 0xC2508A, 0xFFFFFF, 0xE0B864, 0x4A0B2A));
    }

    // Or d'Orbit MC (métal commun : socle, bandeau, colonnes, serrures).
    static final int OR = 0xE0B864, OR_C = 0xFFE9A8, OR_F = 0xA8792A, OR_N = 0x5C3F12;

    static int mix(int x, int y, double t) {
        int r = (int) Math.round(((x >> 16) & 255) * (1 - t) + ((y >> 16) & 255) * t);
        int g = (int) Math.round(((x >> 8) & 255) * (1 - t) + ((y >> 8) & 255) * t);
        int b = (int) Math.round((x & 255) * (1 - t) + (y & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    /** Légère variation de teinte (grain du pixel art). */
    static int grain(int c, Random r) {
        double t = (r.nextDouble() - 0.5) * 0.16;
        return t > 0 ? mix(c, 0xFFFFFF, t) : mix(c, 0x000000, -t);
    }

    static BufferedImage img() { return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB); }

    static void px(BufferedImage im, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < 16 && y < 16) im.setRGB(x, y, 0xFF000000 | c);
    }

    // ------------------------------------------------------------------ textures

    static BufferedImage metal() {
        BufferedImage im = img();
        Random r = new Random(7);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int c = y % 4 == 0 ? OR_C : y % 4 == 3 ? OR_F : OR;
            px(im, x, y, grain(c, r));
        }
        for (int y = 2; y < 16; y += 4) for (int x = 2; x < 16; x += 4) { px(im, x, y, OR_N); px(im, x - 1, y - 1, OR_C); }
        return im;
    }

    /**
     * Côté (comme une face de bloc entier ; l'UV automatique prend la bonne bande) :
     * lignes 2-5 = couvercle, 6-13 = corps (cadre + motif du style au centre).
     */
    static BufferedImage cote(String style, Palette p) {
        BufferedImage im = img();
        Random r = new Random(style.hashCode());
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) px(im, x, y, grain(p.c, r));
        // Couvercle : arête claire, bande principale avec liseré, ombre en bas.
        for (int x = 0; x < 16; x++) {
            px(im, x, 2, grain(p.b, r));
            px(im, x, 3, grain(p.a, r));
            px(im, x, 4, grain(x % 3 == 0 ? p.e : p.a, r));
            px(im, x, 5, grain(mix(p.c, p.o, 0.4), r));
        }
        // Corps : fond sombre, cadre, panneau à motif.
        for (int y = 6; y <= 13; y++) for (int x = 0; x < 16; x++) px(im, x, y, grain(mix(p.c, p.o, 0.25), r));
        for (int x = 2; x <= 13; x++) { px(im, x, 7, p.b); px(im, x, 13, mix(p.c, p.o, 0.6)); }
        for (int y = 7; y <= 13; y++) { px(im, 2, y, p.b); px(im, 13, y, mix(p.c, p.o, 0.6)); }
        for (int y = 8; y <= 12; y++) for (int x = 3; x <= 12; x++) px(im, x, y, motif(style, p, x, y, r));
        return im;
    }

    /** Motif du panneau (x 3..12, y 8..12). */
    static int motif(String style, Palette p, int x, int y, Random r) {
        return switch (style) {
            case "cryo" -> (x + y) % 4 == 0 ? p.b : (x * 7 + y * 3) % 23 == 0 ? p.d : grain(p.a, r);
            case "solaire" -> {
                double ang = Math.atan2(13 - y, x - 7.5);
                yield y == 12 && Math.abs(x - 7.5) < 2 ? p.d : ((int) Math.floor(ang / (Math.PI / 8))) % 2 == 0 ? p.e : grain(mix(p.c, p.o, 0.35), r);
            }
            case "nebuleuse" -> {
                double s = Math.sin(x * 0.9) * 1.6 + 10;
                yield Math.abs(y - s) < 0.7 ? p.e : r.nextDouble() < 0.12 ? p.d : grain(mix(p.c, p.a, 0.35), r);
            }
            case "asteroide" -> {
                double k = r.nextDouble();
                yield k < 0.08 ? p.d : k < 0.14 ? p.e : k < 0.35 ? p.b : k < 0.55 ? p.c : grain(p.a, r);
            }
            case "reacteur" -> y >= 9 && y <= 11 ? (((x + y) / 2) % 2 == 0 ? p.d : p.o) : grain(p.a, r);
            default -> {   // ménagerie : deux pattes et des cœurs
                boolean patte = (x == 5 || x == 10) && y == 11 || (x == 4 || x == 6 || x == 9 || x == 11) && y == 9;
                boolean pad = (x == 5 || x == 10) && y == 10;
                yield patte || pad ? p.d : (x + y * 3) % 11 == 0 ? p.e : grain(p.a, r);
            }
        };
    }

    /** Dessus du couvercle : bord sombre, liseré clair, diagonales vers le centre (couvert par la gemme). */
    static BufferedImage dessus(String style, Palette p) {
        BufferedImage im = img();
        Random r = new Random(style.hashCode() * 31L);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int bord = Math.min(Math.min(x, y), Math.min(15 - x, 15 - y));
            int c = bord == 0 ? p.o : bord == 1 ? p.c : bord == 2 ? p.b : (x == y || x == 15 - y) ? p.e : p.a;
            px(im, x, y, grain(c, r));
        }
        return im;
    }

    /** Gemme : dégradé radial (clair au centre), reflet en haut à gauche. */
    static BufferedImage gemme(Palette p) {
        BufferedImage im = img();
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double d = Math.hypot(x - 7.5, y - 7.5) / 10.6;
            int c = d < 0.25 ? p.d : d < 0.55 ? mix(p.d, p.a, (d - 0.25) / 0.3) : mix(p.a, p.c, Math.min(1, (d - 0.55) / 0.45));
            px(im, x, y, c);
        }
        for (int k = 0; k < 3; k++) px(im, 4 + k, 4, 0xFFFFFF);
        px(im, 4, 5, 0xFFFFFF);
        return im;
    }

    // ------------------------------------------------------------------ modèle

    static String face(String tex) { return "{\"texture\": \"#" + tex + "\"}"; }

    static String element(double[] de, double[] a, String cote, String haut) {
        StringBuilder s = new StringBuilder("    {\"from\": [").append(f(de[0])).append(", ").append(f(de[1])).append(", ").append(f(de[2]))
                .append("], \"to\": [").append(f(a[0])).append(", ").append(f(a[1])).append(", ").append(f(a[2])).append("], \"faces\": {");
        String[] cotes = {"north", "south", "east", "west"};
        for (String c : cotes) s.append("\"").append(c).append("\": ").append(face(cote)).append(", ");
        s.append("\"up\": ").append(face(haut)).append(", \"down\": ").append(face(cote)).append("}}");
        return s.toString();
    }

    static String f(double v) { return v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v); }

    static String modele(String style) {
        String[] e = {
                element(new double[]{0, 0, 0}, new double[]{16, 2, 16}, "metal", "metal"),                       // socle
                element(new double[]{0.75, 2, 0.75}, new double[]{15.25, 10, 15.25}, "cote", "dessus"),           // corps
                element(new double[]{0.4, 9.6, 0.4}, new double[]{15.6, 10.4, 15.6}, "metal", "metal"),           // bandeau
                element(new double[]{0.5, 10.4, 0.5}, new double[]{15.5, 14, 15.5}, "cote", "dessus"),            // couvercle
                element(new double[]{0, 2, 0}, new double[]{2, 14.5, 2}, "metal", "metal"),                        // colonnes
                element(new double[]{14, 2, 0}, new double[]{16, 14.5, 2}, "metal", "metal"),
                element(new double[]{0, 2, 14}, new double[]{2, 14.5, 16}, "metal", "metal"),
                element(new double[]{14, 2, 14}, new double[]{16, 14.5, 16}, "metal", "metal"),
                element(new double[]{6, 5, 0.25}, new double[]{10, 11, 0.75}, "metal", "metal"),                 // serrure nord
                element(new double[]{7, 7, 0}, new double[]{9, 9.5, 0.25}, "gemme", "gemme"),
                element(new double[]{6, 5, 15.25}, new double[]{10, 11, 15.75}, "metal", "metal"),               // serrure sud
                element(new double[]{7, 7, 15.75}, new double[]{9, 9.5, 16}, "gemme", "gemme"),
                element(new double[]{5.5, 14, 5.5}, new double[]{10.5, 14.75, 10.5}, "metal", "metal"),          // anneau du dessus
                element(new double[]{6.5, 14.75, 6.5}, new double[]{9.5, 16, 9.5}, "gemme", "gemme"),            // gemme du dessus
        };
        String t = "orbittycoon:item/coffre_" + style;
        return "{\n  \"textures\": {\n    \"particle\": \"" + t + "_cote\",\n    \"cote\": \"" + t + "_cote\",\n"
                + "    \"dessus\": \"" + t + "_dessus\",\n    \"gemme\": \"" + t + "_gemme\",\n"
                + "    \"metal\": \"orbittycoon:item/coffre_metal\"\n  },\n  \"elements\": [\n"
                + String.join(",\n", e) + "\n  ]\n}\n";
    }

    // ------------------------------------------------------------------ aperçu (face avant composée)

    static BufferedImage avant(BufferedImage cote, BufferedImage metal, BufferedImage gemme) {
        BufferedImage im = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int h = 16 - y;   // hauteur du pixel dans le modèle
            int c = 0;
            if (h <= 2) c = metal.getRGB(x, y);
            else if ((x < 2 || x >= 14) && h <= 14) c = metal.getRGB(x, y);
            else if (h > 9 && h <= 10) c = metal.getRGB(x, y);
            else if (h <= 14) c = cote.getRGB(x, y);
            else if (h <= 16 && x >= 6 && x < 10) c = gemme.getRGB(x, y);
            if (x >= 6 && x < 10 && h > 5 && h <= 11) c = metal.getRGB(x, y);
            if (x >= 7 && x < 9 && h > 7 && h <= 9) c = gemme.getRGB(x + 1, y + 1);
            im.setRGB(x, y, c);
        }
        return im;
    }

    public static void main(String[] args) throws Exception {
        File pack = new File(args[0]);
        File tex = new File(pack, "assets/orbittycoon/textures/item"), mod = new File(pack, "assets/orbittycoon/models/item"),
                items = new File(pack, "assets/orbittycoon/items");
        tex.mkdirs(); mod.mkdirs(); items.mkdirs();
        BufferedImage metal = metal();
        ImageIO.write(metal, "png", new File(tex, "coffre_metal.png"));
        int k = 0, Z = 14;
        BufferedImage ap = new BufferedImage(STYLES.size() * (16 * Z + 20) + 20, 3 * 16 * Z + 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = ap.createGraphics();
        g.setColor(new Color(0x0B1A33));
        g.fillRect(0, 0, ap.getWidth(), ap.getHeight());
        for (var en : STYLES.entrySet()) {
            String s = en.getKey();
            Palette p = en.getValue();
            BufferedImage c = cote(s, p), d = dessus(s, p), gm = gemme(p);
            ImageIO.write(c, "png", new File(tex, "coffre_" + s + "_cote.png"));
            ImageIO.write(d, "png", new File(tex, "coffre_" + s + "_dessus.png"));
            ImageIO.write(gm, "png", new File(tex, "coffre_" + s + "_gemme.png"));
            Files.writeString(new File(mod, "coffre_" + s + ".json").toPath(), modele(s), StandardCharsets.UTF_8);
            Files.writeString(new File(items, "coffre_" + s + ".json").toPath(),
                    "{\n  \"model\": {\"type\": \"minecraft:model\", \"model\": \"orbittycoon:item/coffre_" + s + "\"}\n}\n", StandardCharsets.UTF_8);
            int x0 = 20 + k * (16 * Z + 20);
            g.drawImage(avant(c, metal, gm), x0, 20, 16 * Z, 16 * Z, null);
            g.drawImage(d, x0, 40 + 16 * Z, 16 * Z, 16 * Z, null);
            g.drawImage(c, x0, 60 + 32 * Z, 16 * Z, 16 * Z, null);
            k++;
        }
        g.dispose();
        System.out.println(STYLES.size() + " coffres écrits dans " + mod);
        if (args.length > 1) { ImageIO.write(ap, "png", new File(args[1])); System.out.println("Aperçu : " + args[1]); }
    }
}
