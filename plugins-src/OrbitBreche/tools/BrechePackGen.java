import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Pack de BRÈCHE : modèles 3D (boîtes) des six armes, de la lame, du Noyau et de la tourelle, plus les icônes des
 * vingt pouvoirs (32 × 32). Tout est dessiné ici, aucun visuel repris d'un jeu existant.
 * Usage : java BrechePackGen.java <dossier pack> <dossier maquettes>
 * Repère des armes (comme les armes de l'extraction) : canon le long de -Z, haut = +Y, arme centrée sur x = 8,
 * axe du canon vers y = 10 ; 16 unités = 1 bloc. Tourelle : canon vers +Z (avant de l'entité).
 */
public final class BrechePackGen {

    static final String NS = "orbitbreche";
    static final int TILE = 32, TEX = 128;
    static final int METAL = 0, METAL2 = 1, NOIR = 2, BLEU = 3, OR = 4, LUEUR = 5, COEUR = 6, BLANC = 7;
    static final int[] AVG = new int[8];

    record Box(double x1, double y1, double z1, double x2, double y2, double z2, int tile) {
    }

    static final class Model {
        final String id;
        final String kind;  // arme, objet, pose
        final List<Box> boxes = new ArrayList<>();
        double[] grip;

        Model(String id, String kind) {
            this.id = id;
            this.kind = kind;
        }

        Model b(double x1, double y1, double z1, double x2, double y2, double z2, int tile) {
            boxes.add(new Box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2), tile));
            return this;
        }

        Model grip(double y, double z) {
            grip = new double[]{y, z};
            return this;
        }
    }

    static Path out;

    public static void main(String[] args) throws Exception {
        out = Path.of(args[0]);
        File previews = new File(args[1]);
        write("pack.mcmeta", "{\n  \"pack\": {\n    \"description\": \"§9ORBIT §7- BRÈCHE\",\n    \"min_format\": 97,\n    \"max_format\": 97\n  }\n}\n");
        BufferedImage atlas = atlas();
        File tex = out.resolve("assets/" + NS + "/textures/item/breche.png").toFile();
        tex.getParentFile().mkdirs();
        ImageIO.write(atlas, "png", tex);

        List<Model> models = new ArrayList<>();
        models.add(new Model("arme_etincelle", "arme")
                .b(7, 9.5, 0, 9, 11.5, 9, METAL2)
                .b(7.1, 8.5, 1, 8.9, 9.5, 9, METAL)
                .b(7.2, 4, 6, 8.8, 8.5, 9, NOIR)
                .b(7.6, 7.5, 4, 8.4, 8.5, 6, METAL)
                .b(6.9, 10.5, 1, 9.1, 10.9, 8, BLEU)
                .b(7.5, 10, -0.2, 8.5, 11, 0, LUEUR)
                .grip(8.5, 7.5));
        models.add(new Model("arme_faucon", "arme")
                .b(7.4, 10, -2, 8.6, 11.2, 5, METAL2)
                .b(6.8, 9, 5, 9.2, 11.6, 8, METAL)
                .b(7.2, 8.6, 8, 8.8, 11.4, 10, METAL)
                .b(7.2, 4, 9, 8.8, 8.8, 12, OR)
                .b(7.6, 11.4, 9.5, 8.4, 12, 10.5, NOIR)
                .b(7.6, 11.2, -1.5, 8.4, 11.6, 5, BLEU)
                .b(7.6, 10.2, -2.2, 8.4, 11, -2, LUEUR)
                .grip(8.5, 10.5));
        models.add(new Model("arme_essaim", "arme")
                .b(7, 9, -2, 9, 12, 10, METAL)
                .b(7.5, 10, -6, 8.5, 11, -2, METAL2)
                .b(7.2, 9.6, -4, 8.8, 11.4, -2, METAL2)
                .b(7.3, 4, 1, 8.7, 9, 3, NOIR)
                .b(7.3, 5, 6, 8.7, 9, 8.5, NOIR)
                .b(7.5, 9.5, 10, 8.5, 10.5, 15, METAL2)
                .b(7.2, 8, 14, 8.8, 11, 15, NOIR)
                .b(6.9, 11, -1, 9.1, 11.5, 8, BLEU)
                .b(7.6, 10.1, -6.2, 8.4, 10.9, -6, LUEUR)
                .grip(8.5, 7.2));
        models.add(new Model("arme_briseroc", "arme")
                .b(7.4, 10.6, -12, 8.6, 11.8, 4, METAL2)
                .b(7.5, 9.4, -10, 8.5, 10.6, 2, METAL)
                .b(7.1, 9.1, -8, 8.9, 10.9, -3, OR)
                .b(7, 9, 2, 9, 12, 8, METAL)
                .b(7.3, 5, 7, 8.7, 9, 9.5, NOIR)
                .b(7.2, 8, 8, 8.8, 11.5, 16, METAL)
                .b(7.1, 7.5, 15.5, 8.9, 11.8, 16.5, NOIR)
                .b(6.9, 11.2, 3, 9.1, 11.7, 7, BLEU)
                .b(7.6, 10.8, -12.2, 8.4, 11.6, -12, LUEUR)
                .grip(8.5, 8.2));
        models.add(new Model("arme_comete", "arme")
                .b(7, 9, -2, 9, 12, 8, METAL)
                .b(7.2, 9.3, -9, 8.8, 11.7, -2, METAL2)
                .b(7.6, 10.1, -14, 8.4, 10.9, -9, METAL)
                .b(7.4, 9.9, -15, 8.6, 11.1, -14, NOIR)
                .b(7.3, 4.5, 0, 8.7, 9, 2.5, NOIR)
                .b(7.3, 5, 5, 8.7, 9, 7, NOIR)
                .b(7.4, 8.5, 8, 8.6, 11.5, 14, METAL2)
                .b(7.2, 7.5, 13.5, 8.8, 11.8, 14.5, NOIR)
                .b(7.5, 12, 0, 8.5, 13.2, 5, NOIR)
                .b(7.6, 12.3, -0.1, 8.4, 13, 0, LUEUR)
                .b(6.9, 10.4, -1.5, 9.1, 10.8, 6, OR)
                .b(6.95, 9.8, -8.5, 9.05, 10.2, -3, LUEUR)
                .grip(8.5, 6));
        models.add(new Model("arme_zenith", "arme")
                .b(7, 9, 0, 9, 12, 9, METAL)
                .b(7.6, 10.2, -20, 8.4, 11, 0, METAL2)
                .b(7.2, 9.8, -21, 8.8, 11.4, -19, NOIR)
                .b(7.3, 12.5, -3, 8.7, 14.1, 6, NOIR)
                .b(7.5, 12.7, -3.1, 8.5, 13.9, -3, LUEUR)
                .b(7.6, 12, -1, 8.4, 12.5, 0, METAL2)
                .b(7.6, 12, 4, 8.4, 12.5, 5, METAL2)
                .b(7.3, 6, 3, 8.7, 9, 5, NOIR)
                .b(7.3, 5, 7, 8.7, 9, 9, NOIR)
                .b(7.3, 8, 9, 8.7, 11.6, 15, METAL2)
                .b(7.4, 11.6, 10, 8.6, 12.3, 14, OR)
                .b(7.2, 7.5, 14.5, 8.8, 11.9, 15.5, NOIR)
                .b(7.2, 6, -14, 7.6, 9.8, -13, METAL)
                .b(8.4, 6, -14, 8.8, 9.8, -13, METAL)
                .b(6.9, 10.6, -6, 9.1, 11, 0, BLEU)
                .grip(8.5, 8));
        models.add(new Model("lame", "arme")
                .b(7.4, 9.3, 4, 8.6, 10.9, 10, NOIR)
                .b(7, 9, 3.4, 9, 11.6, 4, OR)
                .b(7.8, 9.8, -5, 8.2, 11.2, 3.4, METAL2)
                .b(7.8, 10, -6, 8.2, 10.8, -5, METAL2)
                .b(7.75, 9.6, -5, 8.25, 9.8, 3.4, LUEUR)
                .b(7.3, 9.2, 9.6, 8.7, 11, 10.4, BLEU)
                .grip(10, 7));
        Model noyau = new Model("noyau", "objet")
                .b(3, 0, 3, 13, 2, 13, METAL)
                .b(5, 2, 5, 11, 10, 11, COEUR)
                .b(3.5, 2, 3.5, 5, 11, 5, METAL2)
                .b(11, 2, 3.5, 12.5, 11, 5, METAL2)
                .b(3.5, 2, 11, 5, 11, 12.5, METAL2)
                .b(11, 2, 11, 12.5, 11, 12.5, METAL2)
                .b(4, 11, 4, 12, 12.5, 12, METAL)
                .b(4.4, 5.5, 4.4, 11.6, 6.5, 11.6, BLEU)
                .b(6.5, 12.5, 6.5, 9.5, 13.5, 9.5, OR)
                .b(2.8, 1.6, 2.8, 13.2, 2.1, 13.2, OR);
        models.add(noyau);
        models.add(new Model("tourelle", "pose")
                .b(7.3, 0, 7.3, 8.7, 6, 8.7, METAL)
                .b(3, 0, 7.3, 13, 0.8, 8.7, METAL2)
                .b(7.3, 0, 3, 8.7, 0.8, 13, METAL2)
                .b(5, 6, 5, 11, 10, 11, METAL)
                .b(5.5, 10, 5.5, 10.5, 10.6, 10.5, OR)
                .b(7.2, 7.4, 11, 8.8, 8.6, 16, METAL2)
                .b(7.4, 7.6, 16, 8.6, 8.4, 16.3, LUEUR)
                .b(6, 8.5, 10.9, 10, 9.5, 11.1, BLEU)
                .b(4.8, 6.6, 5.5, 5, 9.4, 10.5, BLEU)
                .b(11, 6.6, 5.5, 11.2, 9.4, 10.5, BLEU));

        for (Model m : models) {
            write("assets/" + NS + "/models/item/" + m.id + ".json", json(m));
            write("assets/" + NS + "/items/" + m.id + ".json", "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \"" + NS + ":item/" + m.id + "\"\n  }\n}\n");
        }

        // icônes des pouvoirs
        Map<String, Integer> pouvoirs = new LinkedHashMap<>();
        int sol = 0xFFB547, ory = 0xC79BFF, kae = 0x62CFF4, ech = 0x3A7FFF, nai = 0x7CFF8A;
        for (String id : new String[]{"brasier", "eclat", "envol", "supernova"}) pouvoirs.put(id, sol);
        for (String id : new String[]{"puits", "pas", "nebuleuse", "eclipse"}) pouvoirs.put(id, ory);
        for (String id : new String[]{"fil", "mine", "tourelle", "glaciation"}) pouvoirs.put(id, kae);
        for (String id : new String[]{"onde", "commotion", "sonar", "traque"}) pouvoirs.put(id, ech);
        for (String id : new String[]{"cristal", "lenteur", "soin", "renaissance"}) pouvoirs.put(id, nai);
        List<BufferedImage> icones = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Integer> e : pouvoirs.entrySet()) {
            boolean ult = i % 4 == 3;
            BufferedImage img = icone(e.getKey(), e.getValue(), ult);
            icones.add(img);
            File f = out.resolve("assets/" + NS + "/textures/item/pouvoir_" + e.getKey() + ".png").toFile();
            ImageIO.write(img, "png", f);
            write("assets/" + NS + "/models/item/pouvoir_" + e.getKey() + ".json", "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \""
                    + NS + ":item/pouvoir_" + e.getKey() + "\"\n  }\n}\n");
            write("assets/" + NS + "/items/pouvoir_" + e.getKey() + ".json", "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \"" + NS
                    + ":item/pouvoir_" + e.getKey() + "\"\n  }\n}\n");
            i++;
        }
        if (previews.isDirectory()) {
            ImageIO.write(sheet(models, icones), "png", new File(previews, "breche-armes.png"));
        }
        System.out.println("Pack de BRECHE : " + models.size() + " modeles, " + icones.size() + " icones de pouvoir.");
    }

    static void write(String path, String content) throws Exception {
        Path p = out.resolve(path);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ modèle JSON

    static String f(double v) {
        String s = String.format(Locale.ROOT, "%.3f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }

    static String face(String name, double w, double h, int tile) {
        double tu = (tile % 4) * 4.0, tv = (tile / 4) * 4.0;
        double wu = Math.max(0.5, Math.min(4, w / 4.0)), hu = Math.max(0.5, Math.min(4, h / 4.0));
        double u0 = tu + (4 - wu) / 2, v0 = tv + (4 - hu) / 2;
        return "\"" + name + "\": {\"uv\": [" + f(u0) + ", " + f(v0) + ", " + f(u0 + wu) + ", " + f(v0 + hu) + "], \"texture\": \"#0\"}";
    }

    static String json(Model m) {
        StringBuilder sb = new StringBuilder("{\n  \"textures\": {\n    \"0\": \"" + NS + ":item/breche\",\n    \"particle\": \"" + NS + ":item/breche\"\n  },\n  \"elements\": [\n");
        double[] min = {99, 99, 99}, max = {-99, -99, -99};
        for (int i = 0; i < m.boxes.size(); i++) {
            Box b = m.boxes.get(i);
            min[0] = Math.min(min[0], b.x1);
            min[1] = Math.min(min[1], b.y1);
            min[2] = Math.min(min[2], b.z1);
            max[0] = Math.max(max[0], b.x2);
            max[1] = Math.max(max[1], b.y2);
            max[2] = Math.max(max[2], b.z2);
            double w = b.x2 - b.x1, h = b.y2 - b.y1, d = b.z2 - b.z1;
            sb.append("    {\"from\": [").append(f(b.x1)).append(", ").append(f(b.y1)).append(", ").append(f(b.z1))
                    .append("], \"to\": [").append(f(b.x2)).append(", ").append(f(b.y2)).append(", ").append(f(b.z2))
                    .append("], \"shade\": ").append(b.tile == LUEUR || b.tile == COEUR ? "false" : "true")
                    .append(b.tile == LUEUR || b.tile == COEUR ? ", \"light_emission\": 15" : "").append(", \"faces\": {")
                    .append(face("north", w, h, b.tile)).append(", ").append(face("south", w, h, b.tile)).append(", ")
                    .append(face("east", d, h, b.tile)).append(", ").append(face("west", d, h, b.tile)).append(", ")
                    .append(face("up", w, d, b.tile)).append(", ").append(face("down", w, d, b.tile)).append("}}")
                    .append(i + 1 < m.boxes.size() ? ",\n" : "\n");
        }
        sb.append("  ],\n").append(display(m, min, max)).append("}\n");
        return sb.toString();
    }

    /** Tenue en main : même calcul que les armes de l'extraction (réglages calés dans Blockbench). */
    static String display(Model m, double[] min, double[] max) {
        if (!m.kind.equals("arme")) {
            String s = m.kind.equals("pose") ? "0.5" : "0.45";
            return "  \"display\": {\n"
                    + "    \"thirdperson_righthand\": {\"rotation\": [0, 0, 0], \"translation\": [0, 2.5, 2], \"scale\": [0.375, 0.375, 0.375]},\n"
                    + "    \"thirdperson_lefthand\": {\"rotation\": [0, 0, 0], \"translation\": [0, 2.5, 2], \"scale\": [0.375, 0.375, 0.375]},\n"
                    + "    \"firstperson_righthand\": {\"rotation\": [0, 35, 0], \"translation\": [1, 3, 0], \"scale\": [" + s + ", " + s + ", " + s + "]},\n"
                    + "    \"firstperson_lefthand\": {\"rotation\": [0, 35, 0], \"translation\": [1, 3, 0], \"scale\": [" + s + ", " + s + ", " + s + "]},\n"
                    + "    \"ground\": {\"rotation\": [0, 0, 0], \"translation\": [0, 2, 0], \"scale\": [0.3, 0.3, 0.3]},\n"
                    + "    \"gui\": {\"rotation\": [30, 225, 0], \"translation\": [0, 0, 0], \"scale\": [0.62, 0.62, 0.62]},\n"
                    + "    \"fixed\": {\"rotation\": [0, 0, 0], \"translation\": [0, 0, 0], \"scale\": [0.6, 0.6, 0.6]},\n"
                    + "    \"head\": {\"rotation\": [0, 0, 0], \"translation\": [0, 4, 0], \"scale\": [0.6, 0.6, 0.6]}\n  }\n";
        }
        double len = max[2] - min[2], cz = (min[2] + max[2]) / 2, cy = (min[1] + max[1]) / 2;
        double hand = Math.round(Math.max(0.6, Math.min(0.75, 24.0 / len)) * 1000) / 1000.0;
        double fp = Math.round(hand * 0.85 * 1000) / 1000.0;
        double gui = Math.round(Math.min(1.0, 21.0 / len) * 1000) / 1000.0;
        double gx = -(cz - 8) * gui * 0.7, gy = -(cy - 8) * gui;
        double[] g = m.grip != null ? m.grip : new double[]{cy - 1, cz + 3};
        double ty = (g[1] - 8) * hand, tz = -(g[0] - 8) * hand, fz = -(cz - 8) * fp;
        return "  \"display\": {\n"
                + "    \"thirdperson_righthand\": {\"rotation\": [90, 0, 0], \"translation\": [0, " + f(ty) + ", " + f(tz) + "], \"scale\": [" + f(hand) + ", " + f(hand) + ", " + f(hand) + "]},\n"
                + "    \"thirdperson_lefthand\": {\"rotation\": [90, 0, 0], \"translation\": [0, " + f(ty) + ", " + f(tz) + "], \"scale\": [" + f(hand) + ", " + f(hand) + ", " + f(hand) + "]},\n"
                + "    \"firstperson_righthand\": {\"rotation\": [0, 0, 0], \"translation\": [-1.5, 2.6, " + f(fz - 3) + "], \"scale\": [" + f(fp) + ", " + f(fp) + ", " + f(fp) + "]},\n"
                + "    \"firstperson_lefthand\": {\"rotation\": [0, 0, 0], \"translation\": [-1.5, 2.6, " + f(fz - 3) + "], \"scale\": [" + f(fp) + ", " + f(fp) + ", " + f(fp) + "]},\n"
                + "    \"ground\": {\"rotation\": [0, 0, 0], \"translation\": [0, 2, 0], \"scale\": [" + f(hand * 0.6) + ", " + f(hand * 0.6) + ", " + f(hand * 0.6) + "]},\n"
                + "    \"gui\": {\"rotation\": [0, 90, 0], \"translation\": [" + f(gx) + ", " + f(gy) + ", 0], \"scale\": [" + f(gui) + ", " + f(gui) + ", " + f(gui) + "]},\n"
                + "    \"fixed\": {\"rotation\": [0, 90, 0], \"translation\": [0, 0, 0], \"scale\": [" + f(gui) + ", " + f(gui) + ", " + f(gui) + "]},\n"
                + "    \"head\": {\"rotation\": [0, 90, 0], \"translation\": [0, 4, 0], \"scale\": [" + f(hand) + ", " + f(hand) + ", " + f(hand) + "]}\n  }\n";
    }

    // ------------------------------------------------------------------ texture

    static BufferedImage atlas() {
        BufferedImage img = new BufferedImage(TEX, TEX, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(33);
        for (int t = 0; t < 8; t++) {
            int ox = (t % 4) * TILE, oy = (t / 4) * TILE;
            long r = 0, g = 0, b = 0;
            for (int y = 0; y < TILE; y++) {
                for (int x = 0; x < TILE; x++) {
                    int c = pixel(t, x, y, rnd);
                    img.setRGB(ox + x, oy + y, 0xFF000000 | c);
                    r += (c >> 16) & 255;
                    g += (c >> 8) & 255;
                    b += c & 255;
                }
            }
            int n = TILE * TILE;
            AVG[t] = (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
        }
        return img;
    }

    static int rgb(double r, double g, double b) {
        return clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    static int mul(int c, double k) {
        return rgb(((c >> 16) & 255) * k, ((c >> 8) & 255) * k, (c & 255) * k);
    }

    static int mix(int a, int b, double t) {
        return rgb(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t, ((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t, (a & 255) * (1 - t) + (b & 255) * t);
    }

    static int pixel(int t, int x, int y, Random rnd) {
        double n = 0.95 + rnd.nextDouble() * 0.1;
        double cx = x - 15.5, cy = y - 15.5, d = Math.sqrt(cx * cx + cy * cy) / 22.0;
        switch (t) {
            case METAL: {
                int c = mul(0x2E333C, n);
                if (x % 8 == 0 || y % 16 == 0) c = mul(c, 0.75);
                if (y % 16 == 1) c = mul(c, 1.25);
                return c;
            }
            case METAL2: {
                int c = mul(0x6C7580, n);
                if (y % 8 == 0) c = mul(c, 0.8);
                if (y % 8 == 1) c = mul(c, 1.15);
                return c;
            }
            case NOIR: {
                int c = mul(0x16181D, n);
                if ((x + y) % 4 == 0) c = mul(c, 1.6);
                return c;
            }
            case BLEU: {
                int c = mix(0x5A97FF, 0x2A5FCF, y / 31.0);
                return mul(c, n);
            }
            case OR: {
                int c = mul(0xE0B864, n);
                if (y % 6 == 0) c = mul(c, 0.82);
                if (y % 6 == 1) c = mul(c, 1.12);
                return c;
            }
            case LUEUR:
                return mix(0xFFFFFF, 0x62CFF4, Math.min(1, d * 1.4));
            case COEUR:
                return mix(0xFFF3C0, 0xFF6A2A, Math.min(1, d * 1.5));
            default:
                return mul(0xE6EDF3, n);
        }
    }

    // ------------------------------------------------------------------ icônes des pouvoirs

    static BufferedImage icone(String id, int col, boolean ult) {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color c = new Color(col), fonce = new Color(mul(col, 0.22)), clair = new Color(mix(col, 0xFFFFFF, 0.55));
        g.setColor(fonce);
        g.fill(new Ellipse2D.Double(1, 1, 30, 30));
        g.setStroke(new BasicStroke(2f));
        g.setColor(ult ? new Color(0xE0B864) : c);
        g.draw(new Ellipse2D.Double(1.5, 1.5, 29, 29));
        g.setColor(clair);
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (id) {
            case "brasier" -> {
                g.fillPolygon(new int[]{16, 22, 21, 24, 16, 8, 11, 10}, new int[]{5, 13, 17, 21, 27, 21, 17, 12}, 8);
                g.setColor(new Color(0xFFF0C0));
                g.fillPolygon(new int[]{16, 19, 16, 13}, new int[]{14, 22, 26, 22}, 4);
            }
            case "eclat" -> {
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    g.draw(new Line2D.Double(16 + Math.cos(a) * 4, 16 + Math.sin(a) * 4, 16 + Math.cos(a) * 11, 16 + Math.sin(a) * 11));
                }
                g.fill(new Ellipse2D.Double(12.5, 12.5, 7, 7));
            }
            case "envol" -> {
                g.drawPolyline(new int[]{9, 16, 23}, new int[]{17, 10, 17}, 3);
                g.drawPolyline(new int[]{9, 16, 23}, new int[]{24, 17, 24}, 3);
            }
            case "supernova" -> {
                Polygon p = new Polygon();
                for (int k = 0; k < 16; k++) {
                    double a = k * Math.PI / 8, r = k % 2 == 0 ? 12 : 5;
                    p.addPoint((int) Math.round(16 + Math.cos(a) * r), (int) Math.round(16 + Math.sin(a) * r));
                }
                g.fillPolygon(p);
            }
            case "puits" -> {
                for (int k = 0; k < 3; k++) {
                    g.draw(new Arc2D.Double(16 - 4 - k * 3.5, 16 - 4 - k * 3.5, 8 + k * 7, 8 + k * 7, k * 60, 250, Arc2D.OPEN));
                }
            }
            case "pas" -> {
                g.fill(new Ellipse2D.Double(8, 15, 6, 9));
                g.fill(new Ellipse2D.Double(18, 7, 6, 9));
            }
            case "nebuleuse" -> {
                g.fill(new Ellipse2D.Double(6, 13, 11, 11));
                g.fill(new Ellipse2D.Double(12, 8, 13, 13));
                g.fill(new Ellipse2D.Double(16, 14, 10, 10));
            }
            case "eclipse" -> {
                g.fill(new Ellipse2D.Double(7, 7, 18, 18));
                g.setColor(fonce);
                g.fill(new Ellipse2D.Double(11, 5, 17, 17));
            }
            case "fil" -> {
                g.drawLine(6, 22, 6, 12);
                g.drawLine(26, 22, 26, 12);
                g.drawLine(6, 17, 26, 17);
            }
            case "mine" -> g.fillPolygon(new int[]{16, 25, 16, 7}, new int[]{7, 16, 25, 16}, 4);
            case "tourelle" -> {
                g.fillRect(10, 11, 11, 8);
                g.fillRect(20, 13, 7, 3);
                g.drawLine(15, 19, 9, 26);
                g.drawLine(15, 19, 21, 26);
            }
            case "glaciation" -> {
                for (int k = 0; k < 3; k++) {
                    double a = k * Math.PI / 3;
                    g.draw(new Line2D.Double(16 - Math.cos(a) * 11, 16 - Math.sin(a) * 11, 16 + Math.cos(a) * 11, 16 + Math.sin(a) * 11));
                }
            }
            case "onde" -> {
                g.fill(new Ellipse2D.Double(13, 13, 6, 6));
                g.draw(new Ellipse2D.Double(8, 8, 16, 16));
            }
            case "commotion" -> g.drawPolyline(new int[]{6, 11, 15, 19, 23, 27}, new int[]{16, 9, 23, 9, 23, 16}, 6);
            case "sonar" -> {
                g.drawLine(8, 24, 22, 10);
                g.fillPolygon(new int[]{24, 24, 17}, new int[]{8, 15, 8}, 3);
                g.draw(new Arc2D.Double(4, 4, 24, 24, 200, 70, Arc2D.OPEN));
            }
            case "traque" -> {
                g.draw(new Arc2D.Double(5, 9, 22, 18, 20, 140, Arc2D.OPEN));
                g.draw(new Arc2D.Double(5, 5, 22, 18, 200, 140, Arc2D.OPEN));
                g.fill(new Ellipse2D.Double(12.5, 12.5, 7, 7));
            }
            case "cristal" -> {
                g.fillPolygon(new int[]{8, 11, 14, 11}, new int[]{24, 9, 24, 26}, 4);
                g.fillPolygon(new int[]{14, 17, 20, 17}, new int[]{24, 6, 24, 26}, 4);
                g.fillPolygon(new int[]{20, 23, 26, 23}, new int[]{24, 10, 24, 26}, 4);
            }
            case "lenteur" -> {
                g.fillPolygon(new int[]{9, 23, 16}, new int[]{7, 7, 16}, 3);
                g.fillPolygon(new int[]{9, 23, 16}, new int[]{25, 25, 16}, 3);
            }
            case "soin" -> {
                g.fillRect(13, 7, 6, 18);
                g.fillRect(7, 13, 18, 6);
            }
            case "renaissance" -> {
                g.draw(new Ellipse2D.Double(10, 4, 12, 5));
                g.drawLine(16, 26, 16, 12);
                g.drawPolyline(new int[]{10, 16, 22}, new int[]{18, 12, 18}, 3);
            }
            default -> g.drawString("?", 13, 21);
        }
        g.dispose();
        return img;
    }

    // ------------------------------------------------------------------ aperçu (peintre, vue 3/4)

    static BufferedImage sheet(List<Model> models, List<BufferedImage> icones) {
        int cols = 3, cw = 420, ch = 260, rows = (models.size() + cols - 1) / cols;
        BufferedImage img = new BufferedImage(cols * cw, rows * ch + 110, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(10, 12, 20));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < models.size(); i++) {
            int ox = (i % cols) * cw, oy = (i / cols) * ch;
            draw(g, models.get(i), ox + cw / 2, oy + ch / 2 + 10);
            g.setColor(Color.WHITE);
            g.drawString(models.get(i).id + " (" + models.get(i).boxes.size() + " boites)", ox + 10, oy + 18);
        }
        for (int i = 0; i < icones.size(); i++) {
            g.drawImage(icones.get(i), 14 + i * 62, rows * ch + 30, 56, 56, null);
        }
        g.dispose();
        return img;
    }

    record Quad(double[][] p, double depth, int color) {
    }

    static void draw(Graphics2D g, Model m, int cx, int cy) {
        double yaw = Math.toRadians(-60), pitch = Math.toRadians(22), sc = 7.5;
        List<Quad> quads = new ArrayList<>();
        for (Box b : m.boxes) {
            double[][] c = new double[8][];
            for (int i = 0; i < 8; i++) {
                double x = ((i & 1) == 0 ? b.x1 : b.x2) - 8, y = ((i & 2) == 0 ? b.y1 : b.y2) - 8, z = ((i & 4) == 0 ? b.z1 : b.z2) - 8;
                double x1 = x * Math.cos(yaw) + z * Math.sin(yaw), z1 = -x * Math.sin(yaw) + z * Math.cos(yaw);
                double y2 = y * Math.cos(pitch) - z1 * Math.sin(pitch), z2 = y * Math.sin(pitch) + z1 * Math.cos(pitch);
                c[i] = new double[]{cx + x1 * sc, cy - y2 * sc, z2};
            }
            int base = AVG[b.tile];
            int[][] faces = {{2, 3, 7, 6}, {0, 1, 5, 4}, {0, 1, 3, 2}, {4, 5, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
            double[] shade = {1.0, 0.45, 0.8, 0.6, 0.7, 0.9};
            for (int f = 0; f < 6; f++) {
                double[][] p = new double[4][];
                double depth = 0;
                for (int k = 0; k < 4; k++) {
                    p[k] = c[faces[f][k]];
                    depth += p[k][2];
                }
                quads.add(new Quad(p, depth / 4, b.tile == LUEUR || b.tile == COEUR ? base : mul(base, shade[f])));
            }
        }
        quads.sort((a, b) -> Double.compare(a.depth, b.depth));
        for (Quad q : quads) {
            Polygon poly = new Polygon();
            for (double[] p : q.p) {
                poly.addPoint((int) Math.round(p[0]), (int) Math.round(p[1]));
            }
            g.setColor(new Color(q.color));
            g.fillPolygon(poly);
        }
    }
}
