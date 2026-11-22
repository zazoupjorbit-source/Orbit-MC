package fr.orbit.mmo.continent;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan d'une capitale : un cube de cases centré sur le lieu, chaque case valant « ne pas toucher », « air » ou un bloc
 * de la palette (états de bloc écrits en texte, sans « minecraft: »). Le plan ne dépend pas du serveur : il se calcule
 * hors du fil principal et se contrôle sans monde. C'est {@link CapitalService} qui le compare au monde et le pose.
 */
final class CapitalPlan {

    /** Case laissée telle qu'elle est dans le monde. */
    static final short KEEP = 0;
    static final short AIR = 1;

    record Npc(String tag, double x, double y, double z, float yaw) {
    }

    final String id;
    final String name;
    /** Centre, en coordonnées du monde. */
    final int ox, oz;
    /** Demi-côté : le plan couvre ox - r .. ox + r. */
    final int r;
    final int side;
    final int y0;
    final int height;
    /** Y du pavé de la ville (on marche à ground + 1). */
    final int ground;
    final short[] cells;
    final List<String> palette = new ArrayList<>();
    private final Map<String, Short> ids = new HashMap<>();
    final List<Npc> npcs = new ArrayList<>();
    /** Points nommés : x, y, z, regard (coordonnées du monde). */
    final Map<String, double[]> points = new LinkedHashMap<>();
    /** Ce que le dessin a décidé (niveau, portes, bâtiments posés) et ses avertissements. */
    final List<String> notes = new ArrayList<>();

    CapitalPlan(String id, String name, int ox, int oz, int r, int y0, int height, int ground) {
        this.id = id;
        this.name = name;
        this.ox = ox;
        this.oz = oz;
        this.r = r;
        this.side = 2 * r + 1;
        this.y0 = y0;
        this.height = height;
        this.ground = ground;
        this.cells = new short[side * side * height];
        palette.add("");
        palette.add("air");
        ids.put("air", AIR);
    }

    short id(String state) {
        Short known = ids.get(state);
        if (known != null) {
            return known;
        }
        if (palette.size() >= Short.MAX_VALUE) {
            throw new IllegalStateException("palette pleine");
        }
        short id = (short) palette.size();
        palette.add(state);
        ids.put(state, id);
        return id;
    }

    boolean in(int dx, int y, int dz) {
        return dx >= -r && dx <= r && dz >= -r && dz <= r && y >= y0 && y < y0 + height;
    }

    int index(int dx, int y, int dz) {
        return ((y - y0) * side + (dz + r)) * side + (dx + r);
    }

    short get(int dx, int y, int dz) {
        return in(dx, y, dz) ? cells[index(dx, y, dz)] : KEEP;
    }

    void set(int dx, int y, int dz, short id) {
        if (in(dx, y, dz)) {
            cells[index(dx, y, dz)] = id;
        }
    }

    /** Nombre de cases que le plan impose (blocs et air). */
    long written() {
        long n = 0;
        for (short c : cells) {
            if (c != KEEP) {
                n++;
            }
        }
        return n;
    }

    long blocks() {
        long n = 0;
        for (short c : cells) {
            if (c > AIR) {
                n++;
            }
        }
        return n;
    }

    private static int shade(int rgb, double f) {
        int r = (int) Math.max(0, Math.min(255, ((rgb >> 16) & 0xFF) * f));
        int g = (int) Math.max(0, Math.min(255, ((rgb >> 8) & 0xFF) * f));
        int b = (int) Math.max(0, Math.min(255, (rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Vue de dessus du plan. {@code colors} donne la couleur de chaque entrée de la palette (négatif : invisible).
     * Les colonnes que le plan ne touche pas restent sombres.
     */
    BufferedImage top(int[] colors, int zoom) {
        int[] rgb = new int[side * side];
        int[] tops = new int[side * side];
        Arrays.fill(rgb, 0x12161C);
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                for (int y = height - 1; y >= 0; y--) {
                    short c = cells[(y * side + z) * side + x];
                    if (c > AIR && colors[c] >= 0) {
                        rgb[z * side + x] = colors[c];
                        tops[z * side + x] = y;
                        break;
                    }
                }
            }
        }
        int[] out = new int[rgb.length];
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                int i = z * side + x;
                int west = tops[z * side + Math.max(0, x - 1)], east = tops[z * side + Math.min(side - 1, x + 1)];
                int north = tops[Math.max(0, z - 1) * side + x], south = tops[Math.min(side - 1, z + 1) * side + x];
                double slope = (east - west) + (south - north);
                double f = 1.0 - Math.max(-0.34, Math.min(0.34, slope * 0.07));
                out[i] = shade(rgb[i], f * (0.9 + Math.min(0.25, tops[i] * 0.006)));
            }
        }
        return image(out, side, side, zoom);
    }

    /** Vue oblique depuis le sud, un pixel par bloc : les façades tournées vers le sud se voient. */
    BufferedImage oblique(int[] colors, int zoom) {
        int w = side, h = side + height + 2;
        int[] rgb = new int[w * h];
        int[] depth = new int[w * h];
        Arrays.fill(rgb, 0x12161C);
        Arrays.fill(depth, Integer.MIN_VALUE);
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                for (int y = 0; y < height; y++) {
                    short c = cells[(y * side + z) * side + x];
                    if (c <= AIR || colors[c] < 0) {
                        continue;
                    }
                    int row = z + (height - 1 - y);
                    plot(rgb, depth, w, h, x, row, colors[c], z * 2);
                    plot(rgb, depth, w, h, x, row + 1, shade(colors[c], 0.68), z * 2 + 1);
                }
            }
        }
        return image(rgb, w, h, zoom);
    }

    private static void plot(int[] rgb, int[] depth, int w, int h, int x, int row, int c, int d) {
        if (row < 0 || row >= h) {
            return;
        }
        int i = row * w + x;
        if (d >= depth[i]) {
            depth[i] = d;
            rgb[i] = c;
        }
    }

    private static BufferedImage image(int[] rgb, int w, int h, int zoom) {
        BufferedImage img = new BufferedImage(w * zoom, h * zoom, BufferedImage.TYPE_INT_RGB);
        int[] line = new int[w * zoom];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w * zoom; x++) {
                line[x] = rgb[y * w + x / zoom];
            }
            for (int k = 0; k < zoom; k++) {
                img.setRGB(0, y * zoom + k, w * zoom, 1, line, 0, w * zoom);
            }
        }
        return img;
    }
}
