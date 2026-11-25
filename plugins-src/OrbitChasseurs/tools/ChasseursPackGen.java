import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Pack de CHASSEURS D'ORBITE : modèles 3D des vaisseaux (dessinés ici, en boîtes), du drone, du cargo, du missile,
 * du rayon laser et des bonus, plus leur texture commune. Les zones « ACCENT » sont teintées par la couleur du
 * pilote (composant dyed_color, teinte minecraft:dye).
 * Usage : java ChasseursPackGen.java <dossier pack> <dossier maquettes>
 * Repère d'un modèle : centre (0,0,0), nez vers -z, y vers le haut, 16 unités = 1 bloc.
 */
public final class ChasseursPackGen {

    static final String NS = "orbitchasseurs";
    static final int TILE = 32, TEX = 128;
    // tuiles de la texture (colonne, ligne)
    static final int HULL = 0, HULL2 = 1, ACCENT = 2, GLASS = 3, ENGINE = 4, DARK = 5, GOLD = 6, RED = 7,
            REPAIR = 8, AMMO = 9, BOOST = 10, LASER = 11;
    static final int[] AVG = new int[12];

    record Box(double x1, double y1, double z1, double x2, double y2, double z2, int tile) {
    }

    static final class Model {
        final String id;
        final List<Box> boxes = new ArrayList<>();
        double guiScale = 0.55;

        Model(String id) {
            this.id = id;
        }

        Model b(double x1, double y1, double z1, double x2, double y2, double z2, int tile) {
            boxes.add(new Box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2), tile));
            return this;
        }

        /** La boîte et son reflet (x -> -x). */
        Model m(double x1, double y1, double z1, double x2, double y2, double z2, int tile) {
            b(x1, y1, z1, x2, y2, z2, tile);
            return b(-x1, y1, z1, -x2, y2, z2, tile);
        }

        Model gui(double s) {
            guiScale = s;
            return this;
        }
    }

    static Path out;

    public static void main(String[] args) throws Exception {
        out = Path.of(args[0]);
        File previews = new File(args[1]);
        write("pack.mcmeta", "{\n  \"pack\": {\n    \"description\": \"§9ORBIT §7- Chasseurs d'Orbite\",\n    \"min_format\": 97,\n    \"max_format\": 97\n  }\n}\n");
        write("assets/" + NS + "/items/vide.json", "{\n  \"model\": {\n    \"type\": \"minecraft:empty\"\n  }\n}\n");
        BufferedImage atlas = atlas();
        File tex = out.resolve("assets/" + NS + "/textures/item/coque.png").toFile();
        tex.getParentFile().mkdirs();
        ImageIO.write(atlas, "png", tex);

        List<Model> models = new ArrayList<>();

        // ---- Flèche : chasseur de départ, équilibré
        models.add(new Model("fleche")
                .b(-1.5, -1, -6, 1.5, 1, 5, HULL)
                .b(-1, -0.75, -10, 1, 0.5, -6, HULL2)
                .b(-0.5, -0.5, -12, 0.5, 0.25, -10, ACCENT)
                .b(-1, 1, -4, 1, 2, 0, GLASS)
                .m(-8, -0.5, -1, -1.5, 0, 4, HULL2)
                .m(-8.5, -0.75, -3, -7.5, 0.5, 5, ACCENT)
                .m(-3, -1, 3, -1.5, 0.5, 6, DARK)
                .m(-2.75, -0.75, 6, -1.75, 0.25, 6.5, ENGINE)
                .b(-0.25, 1, 2, 0.25, 3.5, 5.5, ACCENT)
                .m(-7.2, -0.2, -6, -6.8, 0.2, -3, DARK)
                .b(-1.6, -0.2, -5, 1.6, 0.2, -4.5, GOLD));

        // ---- Comète : intercepteur, long et fin, un gros réacteur
        models.add(new Model("comete")
                .b(-1, -1, -8, 1, 1, 4, HULL)
                .b(-0.5, -0.5, -13, 0.5, 0.5, -8, ACCENT)
                .b(-0.75, 1, -5, 0.75, 1.75, -1, GLASS)
                .b(-2, -2, 4, 2, 2, 8, DARK)
                .b(-1.5, -1.5, 8, 1.5, 1.5, 8.5, ENGINE)
                .b(-2.25, -2.25, 3, 2.25, 2.25, 4, ACCENT)
                .m(-6, -0.25, 1, -1, 0.25, 4, HULL2)
                .m(-7, -0.25, 3, -6, 0.25, 8, ACCENT)
                .m(-3, -0.2, -6, -1, 0.2, -4.5, HULL2)
                .b(-0.2, 2, 4.5, 0.2, 4, 8, HULL2)
                .b(-1.1, -0.2, -7, 1.1, 0.2, -6.5, GOLD));

        // ---- Bastion : canonnière lourde, large et carrée
        models.add(new Model("bastion")
                .b(-3, -2, -5, 3, 2, 6, HULL)
                .b(-2.5, -1.5, -8, 2.5, 1.5, -5, HULL2)
                .b(-1.5, 2, -4, 1.5, 3, 0, GLASS)
                .m(-7, -1.5, -3, -4, 1.5, 5, HULL2)
                .m(-4, -0.5, -1, -3, 0.5, 3, DARK)
                .m(-7.25, -0.5, -3.5, -3.75, 0.5, -3, ACCENT)
                .m(-6, -0.5, -9, -5, 0.5, -3.5, DARK)
                .m(-6.5, -1, 5, -4.5, 1, 7, DARK)
                .m(-6.25, -0.75, 7, -4.75, 0.75, 7.5, ENGINE)
                .b(-1.5, -1, 6, 1.5, 1, 7.5, DARK)
                .b(-1.25, -0.75, 7.5, 1.25, 0.75, 8, ENGINE)
                .b(-2, 2, 1, 2, 2.5, 5, ACCENT)
                .b(-3.1, -0.3, -4, 3.1, 0.3, -3.5, GOLD));

        // ---- Spectre : aile volante furtive, plate
        models.add(new Model("spectre")
                .b(-2, -0.75, -6, 2, 0.75, 5, HULL)
                .b(-1, -0.5, -9, 1, 0.5, -6, HULL)
                .b(-1, 0.75, -5, 1, 1.25, -1, GLASS)
                .m(-6, -0.4, -2, -2, 0.4, 5, HULL)
                .m(-10, -0.3, 1, -6, 0.3, 6, HULL2)
                .m(-10, -0.35, 5.5, -2, 0.35, 6.5, ACCENT)
                .m(-10.25, -0.3, 2, -9.75, 1.5, 6, ACCENT)
                .b(-1.5, -0.5, 5, 1.5, 0.5, 5.5, ENGINE)
                .m(-4.2, -0.2, -5, -3.8, 0.2, -2, DARK)
                .b(-0.4, -0.3, -10, 0.4, 0.3, -9, GOLD));

        // ---- Éclipse : double coque et anneau d'or
        models.add(new Model("eclipse")
                .m(-4, -1, -9, -2, 1, 6, HULL)
                .m(-3.5, -0.5, -12, -2.5, 0.5, -9, ACCENT)
                .b(-2, -0.5, -2, 2, 0.5, 3, HULL2)
                .b(-1.25, 0.5, -2.5, 1.25, 2, 1, GLASS)
                .b(-5, 4, 3, 5, 5, 5, GOLD)
                .b(-5, -5, 3, 5, -4, 5, GOLD)
                .m(-5, -4, 3, -4, 4, 5, GOLD)
                .m(-3.75, -0.75, 6, -2.25, 0.75, 6.5, ENGINE)
                .m(-6.5, -0.25, -1, -4, 0.25, 4, HULL2)
                .m(-7, -0.4, -2, -6.5, 0.4, 5, ACCENT)
                .gui(0.5));

        // ---- Écumeur : drone pirate (ennemi du mode Convoi)
        models.add(new Model("drone")
                .b(-2, -2, -2, 2, 2, 2, DARK)
                .b(-1, -1, -2.5, 1, 1, -2, RED)
                .m(-5, -0.5, -4, -2, 0.5, 1, HULL)
                .m(-5, -0.5, -6, -4, 0.5, -4, RED)
                .b(-0.5, 2, -3, 0.5, 5, 2, HULL)
                .b(-0.5, -5, -3, 0.5, -2, 2, HULL)
                .b(-1, -1, 2, 1, 1, 2.5, RED));

        // ---- Cargo du convoi (affiché à grande échelle)
        models.add(new Model("cargo")
                .b(-2, -2, -14, 2, 2, 14, HULL)
                .b(-3, 2, -13, 3, 5, -8, HULL2)
                .b(-2.5, 3, -13.5, 2.5, 4, -13, GLASS)
                .m(-5, -3, -6, -2, 3, -1, ACCENT)
                .m(-5, -3, 0, -2, 3, 5, HULL2)
                .m(-5, -3, 6, -2, 3, 11, ACCENT)
                .b(-4, -3, 12, 4, 3, 15, DARK)
                .m(-3.5, -2, 15, -0.5, 2, 15.5, ENGINE)
                .b(-2.25, -0.5, -14.5, 2.25, 0.5, -14, GOLD)
                .m(-5.2, -0.5, -6.5, -4.8, 0.5, 11.5, GOLD)
                .gui(0.4));

        // ---- Missile, rayon laser, bonus
        models.add(new Model("missile")
                .b(-0.5, -0.5, -4, 0.5, 0.5, 3, HULL2)
                .b(-0.4, -0.4, -5, 0.4, 0.4, -4, RED)
                .b(-1.5, -0.1, 1.5, 1.5, 0.1, 3, ACCENT)
                .b(-0.1, -1.5, 1.5, 0.1, 1.5, 3, ACCENT)
                .b(-0.4, -0.4, 3, 0.4, 0.4, 3.5, ENGINE)
                .gui(1.2));
        models.add(new Model("laser").b(-0.5, -0.5, -8, 0.5, 0.5, 8, LASER).gui(0.9));
        models.add(new Model("bonus_reparation").b(-3, -3, -3, 3, 3, 3, REPAIR).m(-3.5, -3.5, -3.5, -3, 3.5, 3.5, HULL2).gui(1.2));
        models.add(new Model("bonus_missiles").b(-3, -3, -3, 3, 3, 3, AMMO).m(-3.5, -3.5, -3.5, -3, 3.5, 3.5, HULL2).gui(1.2));
        models.add(new Model("bonus_survolteur").b(-3, -3, -3, 3, 3, 3, BOOST).m(-3.5, -3.5, -3.5, -3, 3.5, 3.5, HULL2).gui(1.2));

        for (Model m : models) {
            write("assets/" + NS + "/models/item/" + m.id + ".json", json(m));
            write("assets/" + NS + "/items/" + m.id + ".json", "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \"" + NS + ":item/" + m.id
                    + "\",\n    \"tints\": [\n      {\n        \"type\": \"minecraft:dye\",\n        \"default\": 3833855\n      }\n    ]\n  }\n}\n");
        }
        if (previews.isDirectory()) {
            ImageIO.write(sheet(models), "png", new File(previews, "chasseurs-modeles.png"));
            ImageIO.write(atlas, "png", new File(previews, "chasseurs-texture.png"));
        }
        System.out.println("Pack des Chasseurs : " + models.size() + " modeles.");
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
        return "\"" + name + "\": {\"uv\": [" + f(u0) + ", " + f(v0) + ", " + f(u0 + wu) + ", " + f(v0 + hu) + "], \"texture\": \"#0\""
                + (tile == ACCENT || tile == LASER ? ", \"tintindex\": 0" : "") + "}";
    }

    static String json(Model m) {
        StringBuilder sb = new StringBuilder("{\n  \"textures\": {\n    \"0\": \"" + NS + ":item/coque\",\n    \"particle\": \"" + NS + ":item/coque\"\n  },\n  \"elements\": [\n");
        for (int i = 0; i < m.boxes.size(); i++) {
            Box b = m.boxes.get(i);
            double w = b.x2 - b.x1, h = b.y2 - b.y1, d = b.z2 - b.z1;
            sb.append("    {\"from\": [").append(f(b.x1 + 8)).append(", ").append(f(b.y1 + 8)).append(", ").append(f(b.z1 + 8))
                    .append("], \"to\": [").append(f(b.x2 + 8)).append(", ").append(f(b.y2 + 8)).append(", ").append(f(b.z2 + 8))
                    .append("], \"shade\": ").append(b.tile == ENGINE || b.tile == LASER || b.tile == RED ? "false" : "true").append(", \"faces\": {")
                    .append(face("north", w, h, b.tile)).append(", ").append(face("south", w, h, b.tile)).append(", ")
                    .append(face("east", d, h, b.tile)).append(", ").append(face("west", d, h, b.tile)).append(", ")
                    .append(face("up", w, d, b.tile)).append(", ").append(face("down", w, d, b.tile)).append("}}")
                    .append(i + 1 < m.boxes.size() ? ",\n" : "\n");
        }
        sb.append("  ],\n  \"display\": {\n    \"gui\": {\"rotation\": [25, 215, 0], \"translation\": [0, 0, 0], \"scale\": [")
                .append(f(m.guiScale)).append(", ").append(f(m.guiScale)).append(", ").append(f(m.guiScale)).append("]}\n  }\n}\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------ texture

    static BufferedImage atlas() {
        BufferedImage img = new BufferedImage(TEX, TEX, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(26);
        for (int t = 0; t < 12; t++) {
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
        double n = 0.94 + rnd.nextDouble() * 0.12;
        double cx = x - 15.5, cy = y - 15.5, d = Math.sqrt(cx * cx + cy * cy) / 22.0;
        switch (t) {
            case HULL: {
                int c = mul(0x3A4250, n);
                if (x % 8 == 0 || y % 16 == 0) c = mul(c, 0.72);
                if (x % 8 == 1 || y % 16 == 1) c = mul(c, 1.12);
                if (x % 8 == 4 && y % 16 == 8) c = mul(c, 1.5);
                return c;
            }
            case HULL2: {
                int c = mul(0x6C7890, n);
                if (y % 8 == 0 || x % 16 == 0) c = mul(c, 0.78);
                if (y % 8 == 1) c = mul(c, 1.1);
                return c;
            }
            case ACCENT: {
                int c = mul(0xF2F4F8, 0.97 + (n - 1) * 0.3);
                if ((x + y) % 16 < 2) c = mul(c, 0.82);
                if (x == 0 || y == 0 || x == TILE - 1 || y == TILE - 1) c = mul(c, 0.86);
                return c;
            }
            case GLASS: {
                int c = mix(0x8FE4FF, 0x1E5AA0, Math.min(1, (x + y) / 52.0));
                if (x + y > 12 && x + y < 17) c = mix(c, 0xFFFFFF, 0.6);
                if (x % 16 == 0 || y % 16 == 0) c = mul(c, 0.7);
                return c;
            }
            case ENGINE:
                return mix(0xFFFFFF, 0x38B6FF, Math.min(1, d * 1.5));
            case DARK: {
                int c = mul(0x181B22, n);
                if (y % 4 == 0) c = mul(c, 1.9);
                return c;
            }
            case GOLD: {
                int c = mul(0xE0B864, n);
                if (y % 6 == 0) c = mul(c, 0.82);
                if (y % 6 == 1) c = mul(c, 1.1);
                return c;
            }
            case RED:
                return mix(0xFFD0C0, 0xE0282A, Math.min(1, d * 1.7));
            case REPAIR: {
                boolean cross = (Math.abs(cx) < 4 && Math.abs(cy) < 11) || (Math.abs(cy) < 4 && Math.abs(cx) < 11);
                boolean edge = x < 2 || y < 2 || x > TILE - 3 || y > TILE - 3;
                return cross ? 0xFFFFFF : edge ? 0xC8FFD0 : mul(0x2FBF55, n);
            }
            case AMMO: {
                boolean body = Math.abs(cx) < 3.5 && cy > -5 && cy < 10;
                boolean head = cy <= -5 && cy > -12 && Math.abs(cx) < (cy + 12) * 0.5;
                boolean fins = cy > 6 && cy < 11 && Math.abs(cx) < 7;
                boolean edge = x < 2 || y < 2 || x > TILE - 3 || y > TILE - 3;
                return body || head || fins ? 0xFFFFFF : edge ? 0xFFD0F4 : mul(0xD63AB4, n);
            }
            case BOOST: {
                boolean bolt = (cy < 1 && cy > -11 && Math.abs(cx + cy * 0.45 - 1) < 3) || (cy >= -2 && cy < 11 && Math.abs(cx + cy * 0.45 + 2.5) < 3);
                boolean edge = x < 2 || y < 2 || x > TILE - 3 || y > TILE - 3;
                return bolt ? 0xFFFFFF : edge ? 0xFFF0C0 : mul(0xE8A820, n);
            }
            default:
                return 0xFFFFFF;
        }
    }

    // ------------------------------------------------------------------ aperçu (peintre, vue 3/4)

    static BufferedImage sheet(List<Model> models) {
        int cols = 4, cw = 380, ch = 300, rows = (models.size() + cols - 1) / cols;
        BufferedImage img = new BufferedImage(cols * cw, rows * ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(8, 10, 18));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        int[] tints = {0x3A7FFF, 0xFF4FD8, 0xE0B864, 0x7CFF8A, 0xC79BFF, 0xE0484A, 0x62CFF4, 0xFFFFFF};
        for (int i = 0; i < models.size(); i++) {
            int ox = (i % cols) * cw, oy = (i / cols) * ch;
            draw(g, models.get(i), ox + cw / 2, oy + ch / 2 + 10, tints[i % tints.length]);
            g.setColor(Color.WHITE);
            g.drawString(models.get(i).id + " (" + models.get(i).boxes.size() + " boites)", ox + 10, oy + 18);
        }
        g.dispose();
        return img;
    }

    record Quad(double[][] p, double depth, int color) {
    }

    static void draw(Graphics2D g, Model m, int cx, int cy, int tint) {
        double yaw = Math.toRadians(-38), pitch = Math.toRadians(24), sc = 9.5;
        List<Quad> quads = new ArrayList<>();
        for (Box b : m.boxes) {
            double[][] c = new double[8][];
            for (int i = 0; i < 8; i++) {
                double x = (i & 1) == 0 ? b.x1 : b.x2, y = (i & 2) == 0 ? b.y1 : b.y2, z = (i & 4) == 0 ? b.z1 : b.z2;
                double x1 = x * Math.cos(yaw) + z * Math.sin(yaw), z1 = -x * Math.sin(yaw) + z * Math.cos(yaw);
                double y2 = y * Math.cos(pitch) - z1 * Math.sin(pitch), z2 = y * Math.sin(pitch) + z1 * Math.cos(pitch);
                c[i] = new double[]{cx + x1 * sc, cy - y2 * sc, z2};
            }
            int base = b.tile == ACCENT || b.tile == LASER ? mulc(AVG[b.tile], tint) : AVG[b.tile];
            int[][] faces = {{2, 3, 7, 6}, {0, 1, 5, 4}, {0, 1, 3, 2}, {4, 5, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
            double[] shade = {1.0, 0.45, 0.8, 0.6, 0.7, 0.9};
            for (int f = 0; f < 6; f++) {
                double[][] p = new double[4][];
                double depth = 0;
                for (int k = 0; k < 4; k++) {
                    p[k] = c[faces[f][k]];
                    depth += p[k][2];
                }
                quads.add(new Quad(p, depth / 4, mul(base, shade[f])));
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

    static int mulc(int a, int b) {
        return rgb(((a >> 16) & 255) * ((b >> 16) & 255) / 255.0, ((a >> 8) & 255) * ((b >> 8) & 255) / 255.0, (a & 255) * (b & 255) / 255.0);
    }
}
