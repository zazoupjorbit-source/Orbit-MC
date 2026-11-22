import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aperçu des modèles de véhicules du pack (veh_*.json) : chaque modèle est dessiné en vue de trois quarts avant,
 * puis de profil, par lancer de rayons sur ses cubes. Sert à vérifier les formes sans lancer le jeu.
 * Usage : java VehiclePreview.java <dossier du pack> <dossier des aperçus>
 */
public class VehiclePreview {

    record Box(double x1, double y1, double z1, double x2, double y2, double z2, int rgb) {
    }

    static final int W = 420, H = 300;

    public static void main(String[] a) throws Exception {
        File pack = new File(a[0]);
        File out = new File(a.length > 1 ? a[1] : ".", "vehicules_apercu.png");
        BufferedImage pal = ImageIO.read(new File(pack, "assets/orbitguns/textures/item/palette.png"));
        File dir = new File(pack, "assets/orbitguns/models/item");
        File[] files = dir.listFiles((d, n) -> n.startsWith("veh_") && n.endsWith(".json"));
        if (files == null || files.length == 0) {
            System.out.println("Aucun modèle de véhicule.");
            return;
        }
        java.util.Arrays.sort(files);
        BufferedImage img = new BufferedImage(W * 2, H * files.length, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x20242B));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        Pattern el = Pattern.compile("\"from\": \\[([-0-9.]+), ([-0-9.]+), ([-0-9.]+)\\], \"to\": \\[([-0-9.]+), ([-0-9.]+), ([-0-9.]+)\\].*?\"uv\": \\[([-0-9.]+), ([-0-9.]+),");
        for (int i = 0; i < files.length; i++) {
            List<Box> boxes = new ArrayList<>();
            for (String line : Files.readAllLines(files[i].toPath(), StandardCharsets.UTF_8)) {
                Matcher m = el.matcher(line);
                if (m.find()) {
                    int cu = (int) Math.round((Double.parseDouble(m.group(7)) - 0.5) / 4), cv = (int) Math.round((Double.parseDouble(m.group(8)) - 0.5) / 4);
                    int rgb = pal.getRGB(cu * 8 + 4, cv * 8 + 4) & 0xFFFFFF;
                    boxes.add(new Box(d(m, 1), d(m, 2), d(m, 3), d(m, 4), d(m, 5), d(m, 6), rgb));
                }
            }
            render(img, 0, i * H, boxes, 35, 22);      // trois quarts avant droit
            render(img, W, i * H, boxes, 90, 4);       // profil droit
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.drawString(files[i].getName().replace(".json", "") + "  (" + boxes.size() + " cubes)", 10, i * H + 20);
        }
        g.dispose();
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("OK apercu des vehicules");
        objects(pack, pal, el, new File(out.getParentFile(), "objets_apercu.png"));
    }

    /** Petits objets (munitions, soins, outils) : une vue de trois quarts chacun, en grille. */
    static void objects(File pack, BufferedImage pal, Pattern el, File out) throws Exception {
        File dir = new File(pack, "assets/orbitguns/models/item");
        List<String> small = List.of("bandage", "medkit", "jerrican", "repairkit", "frag");
        File[] files = dir.listFiles((d, n) -> n.startsWith("ammo_") || small.contains(n.replace(".json", "")));
        if (files == null || files.length == 0) {
            return;
        }
        java.util.Arrays.sort(files);
        int cols = 4, cw = 210, ch = 200, rows = (files.length + cols - 1) / cols;
        BufferedImage img = new BufferedImage(cols * cw, rows * ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x20242B));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < files.length; i++) {
            List<Box> boxes = new ArrayList<>();
            for (String line : Files.readAllLines(files[i].toPath(), StandardCharsets.UTF_8)) {
                Matcher m = el.matcher(line);
                if (m.find()) {
                    int cu = (int) Math.round((Double.parseDouble(m.group(7)) - 0.5) / 4), cv = (int) Math.round((Double.parseDouble(m.group(8)) - 0.5) / 4);
                    boxes.add(new Box(d(m, 1), d(m, 2), d(m, 3), d(m, 4), d(m, 5), d(m, 6), pal.getRGB(cu * 8 + 4, cv * 8 + 4) & 0xFFFFFF));
                }
            }
            BufferedImage cell = new BufferedImage(W * 2, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D cg = cell.createGraphics();
            cg.setColor(new Color(0x20242B));
            cg.fillRect(0, 0, W * 2, H);
            cg.dispose();
            SCALE = 15;
            CENTER = new double[]{8, 7.5, 8};
            render(cell, 0, 0, boxes, 35, 25);
            int x = (i % cols) * cw, y = (i / cols) * ch;
            g.drawImage(cell.getSubimage((W - cw) / 2, (H - ch) / 2, cw, ch), x, y, null);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.drawString(files[i].getName().replace(".json", ""), x + 8, y + 18);
        }
        g.dispose();
        ImageIO.write(img, "png", out);
        System.out.println("OK apercu des objets");
    }

    static double SCALE = 7.2;      // pixels par unité de modèle
    static double[] CENTER = {8, 8, 8};

    static double d(Matcher m, int i) {
        return Double.parseDouble(m.group(i));
    }

    /** Vue orthographique : la caméra tourne de {@code yawDeg} autour de Y depuis l'avant (-Z) et regarde de {@code pitchDeg} vers le bas. */
    static void render(BufferedImage img, int ox, int oy, List<Box> boxes, double yawDeg, double pitchDeg) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        // Direction du regard (de la caméra vers le modèle).
        double[] dir = {-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
        double[] right = {-Math.cos(yaw), 0, -Math.sin(yaw)};
        double[] up = {dir[1] * right[2] - dir[2] * right[1], dir[2] * right[0] - dir[0] * right[2], dir[0] * right[1] - dir[1] * right[0]};
        if (up[1] < 0) {
            up = new double[]{-up[0], -up[1], -up[2]};
        }
        double scale = SCALE;
        double[] c = CENTER;
        for (int py = 0; py < H; py++) {
            for (int px = 0; px < W; px++) {
                double sx = (px - W / 2.0) / scale, sy = (H / 2.0 - py) / scale;
                double[] o = new double[3];
                for (int k = 0; k < 3; k++) {
                    o[k] = c[k] + right[k] * sx + up[k] * sy - dir[k] * 200;
                }
                double best = Double.MAX_VALUE;
                int color = -1;
                for (Box b : boxes) {
                    double[] lo = {b.x1, b.y1, b.z1}, hi = {b.x2, b.y2, b.z2};
                    double t0 = 0, t1 = Double.MAX_VALUE;
                    int axis = -1;
                    boolean hit = true;
                    for (int k = 0; k < 3 && hit; k++) {
                        if (Math.abs(dir[k]) < 1e-9) {
                            hit = o[k] >= lo[k] && o[k] <= hi[k];
                            continue;
                        }
                        double ta = (lo[k] - o[k]) / dir[k], tb = (hi[k] - o[k]) / dir[k];
                        double near = Math.min(ta, tb), far = Math.max(ta, tb);
                        if (near > t0) {
                            t0 = near;
                            axis = k;
                        }
                        t1 = Math.min(t1, far);
                        hit = t0 <= t1;
                    }
                    if (hit && t0 < best) {
                        best = t0;
                        double shade = axis == 1 ? 1.0 : axis == 0 ? 0.62 : 0.8;
                        int r = (int) (((b.rgb >> 16) & 255) * shade), gr = (int) (((b.rgb >> 8) & 255) * shade), bl = (int) ((b.rgb & 255) * shade);
                        color = (r << 16) | (gr << 8) | bl;
                    }
                }
                if (color >= 0) {
                    img.setRGB(ox + px, oy + py, color);
                }
            }
        }
    }
}
