import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * Écrans du téléphone d'Orbit Extraction (coffre 6 lignes), même technique que les menus « station » du lobby
 * (plugins-src/OrbitDevCore/tools/OrbitGuiGen.java) : image 196x248 unités GUI rendue x2, découpée en 2x4 tuiles,
 * affichée par des glyphes de la police orbitguns:gui dans le titre du coffre. Palette propre au mode :
 * acier sombre et rouge « zone infectée ».
 *
 * Contraintes Minecraft (ne pas casser) : rien à plus de 24 unités au-dessus du coffre ; une glyphe ne dépasse
 * pas 256 px ; plaque #404040 sous le libellé « Inventaire » du jeu.
 *
 * Usage : java PhoneGuiGen.java <dossier_pack> [<dossier_apercus>]
 */
public class PhoneGuiGen {
    static final int S = 2;
    static final int W = 196 * S, H = 248 * S, OX = 10 * S, OY = 24 * S;
    static BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);

    static final int OUT = 0xFF07080A, RED = 0xFFC0392B, RED_HI = 0xFFE8695C, RED_HI2 = 0xFFF7B5AD, RED_LO = 0xFF7A2018, RED_LO2 = 0xFF4A120D;
    static final int[] METAL = {0xFF50545C, 0xFF3F434B, 0xFF30333A, 0xFF24262C};
    static final int METAL_HI = 0xFF7A808C, METAL_LO = 0xFF131417;
    static final int[] SCREEN = {0xFF171A1D, 0xFF121416, 0xFF0D0F11, 0xFF0A0B0D};
    static final int SCREEN_IN_DK = 0xFF050607, SCREEN_IN_HI = 0xFF2C3036;

    // ---------- primitives (sous-pixels, origine = coin du coffre) ----------
    static void px(int x, int y, int c) {
        x += OX; y += OY;
        if (x >= 0 && y >= 0 && x < W && y < H) img.setRGB(x, y, c);
    }
    static int get(int x, int y) {
        x += OX; y += OY;
        return (x >= 0 && y >= 0 && x < W && y < H) ? img.getRGB(x, y) : 0;
    }
    static void rect(int x, int y, int w, int h, int c) {
        for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) px(x + i, y + j, c);
    }
    static final int[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};
    static double bayer(int x, int y) { return (BAYER[(y & 3)][(x & 3)] + 0.5) / 16.0; }
    static int pick(int[] cols, double t, int x, int y) {
        t = Math.max(0, Math.min(1, t));
        double p = t * (cols.length - 1);
        int i = (int) Math.floor(p);
        if (i >= cols.length - 1) return cols[cols.length - 1];
        double f = Math.max(0, Math.min(1, (p - i - 0.3) / 0.4));
        return f > bayer(x, y) ? cols[i + 1] : cols[i];
    }
    static boolean in(int i, int j, int w, int h, int ch) {
        return i >= 0 && j >= 0 && i < w && j < h && i + j >= ch && (w - 1 - i) + j >= ch
                && i + (h - 1 - j) >= ch && (w - 1 - i) + (h - 1 - j) >= ch;
    }
    static void shape(int x, int y, int w, int h, int ch, int[] grad, int outline, int hi, int lo) {
        for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) {
            if (!in(i, j, w, h, ch)) continue;
            int c;
            if (!in(i - 1, j, w, h, ch) || !in(i + 1, j, w, h, ch) || !in(i, j - 1, w, h, ch) || !in(i, j + 1, w, h, ch)) c = outline;
            else if (!in(i, j - 2, w, h, ch) || !in(i - 2, j, w, h, ch)) c = hi;
            else if (!in(i, j + 2, w, h, ch) || !in(i + 2, j, w, h, ch)) c = lo;
            else c = pick(grad, (j - 2) / (double) Math.max(1, h - 5), x + i, y + j);
            px(x + i, y + j, c);
        }
    }
    static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** Panneau d'acier + écran de terminal (trame de points, parasites, lignes de balayage). */
    static void panel(int x, int y, int w, int h, long seed) {
        shape(x, y, w, h, 6, METAL, OUT, METAL_HI, METAL_LO);
        int m = 8, ix = x + m, iy = y + m, iw = w - 2 * m, ih = h - 2 * m;
        shape(ix, iy, iw, ih, 4, SCREEN, OUT, SCREEN_IN_DK, SCREEN_IN_HI);
        for (int i = 4; i < iw - 4; i++) for (int j = 4; j < ih - 4; j++) {
            int gx = ix + i, gy = iy + j;
            if (gx % 12 == 0 && gy % 12 == 0) px(gx, gy, 0xFF22262B);
            else if (gy % 6 == 0 && (gx / 3 + gy) % 9 == 0) px(gx, gy, 0xFF15181B);
        }
        Random r = new Random(seed);
        for (int k = 0; k < (iw * ih) / 420; k++) {
            int sx = ix + 4 + r.nextInt(iw - 8), sy = iy + 4 + r.nextInt(ih - 8);
            px(sx, sy, r.nextInt(6) == 0 ? 0xFF5A2620 : 0xFF262A30);
        }
        for (int bx = x + 24; bx < x + w - 24; bx += 48) { rivet(bx, y + 3); rivet(bx, y + h - 6); }
        for (int by = y + 24; by < y + h - 24; by += 48) { rivet(x + 3, by); rivet(x + w - 6, by); }
        rivet(x + 3, y + 3); rivet(x + w - 6, y + 3); rivet(x + 3, y + h - 6); rivet(x + w - 6, y + h - 6);
    }
    static void rivet(int x, int y) {
        rect(x, y, 3, 3, OUT); px(x, y, 0xFFC2C6CE); px(x + 1, y, 0xFF8E939C); px(x, y + 1, 0xFF8E939C); px(x + 1, y + 1, 0xFF60646C);
    }

    static void slot(int gx, int gy) {
        int x = gx * S - 2, y = gy * S - 2, s = 36;
        rect(x, y, s, s, OUT);
        rect(x + 1, y + 1, s - 2, s - 2, 0xFF3A3E46);
        rect(x + 1, y + 1, s - 3, s - 3, 0xFF030304);
        for (int j = 0; j < 32; j++) for (int i = 0; i < 32; i++)
            px(x + 2 + i, y + 2 + j, pick(new int[]{0xFF0B0C0E, 0xFF0F1113, 0xFF141619}, j / 31.0, x + 2 + i, y + 2 + j));
        rect(x + 2, y + 2, 32, 1, 0xFF070809);
    }
    static int sx(int c) { return 8 + 18 * c; }
    static int sy(int r) { return 18 + 18 * r; }

    // ---------- police bitmap 5x7 ----------
    static final Map<Character, String[]> F = new HashMap<>();
    static void g(char c, String... rows) { F.put(c, rows); }
    static {
        g('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        g('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
        g('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
        g('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
        g('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
        g('É', "..##.", "#####", "#....", "####.", "#....", "#....", "#####");
        g('Ô', "..#..", ".#.#.", ".###.", "#...#", "#...#", "#...#", ".###.");
        g('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....");
        g('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####");
        g('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        g('I', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####");
        g('J', "..###", "...#.", "...#.", "...#.", "#..#.", "#..#.", ".##..");
        g('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#");
        g('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
        g('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#");
        g('N', "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#");
        g('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        g('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
        g('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#");
        g('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#");
        g('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.");
        g('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        g('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        g('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..");
        g('W', "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#");
        g('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#");
        g('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..");
        g('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####");
        g('-', ".....", ".....", ".....", "#####", ".....", ".....", ".....");
        g(' ', ".....", ".....", ".....", ".....", ".....", ".....", ".....");
    }
    static int textW(String s, int sc) { return textW(s, sc, 6 * sc); }
    static int textW(String s, int sc, int adv) { return s.length() * adv - (adv - 5 * sc); }
    static void text(int x, int y, String s, int sc, int adv, int[] grad, int outline, int shadow, int sd) {
        int tw = textW(s, sc, adv), th = 7 * sc;
        boolean[][] m = new boolean[tw][th];
        int cx = 0;
        for (char ch : s.toCharArray()) {
            String[] gl = F.get(ch);
            if (gl == null) throw new IllegalArgumentException("lettre absente de la police : " + ch);
            for (int r = 0; r < 7; r++) for (int k = 0; k < 5; k++)
                if (gl[r].charAt(k) == '#') for (int a = 0; a < sc; a++) for (int b = 0; b < sc; b++) m[cx + k * sc + a][r * sc + b] = true;
            cx += adv;
        }
        boolean[][] d = new boolean[tw + 2][th + 2];
        for (int i = 0; i < tw; i++) for (int j = 0; j < th; j++) if (m[i][j])
            for (int a = 0; a < 3; a++) for (int b = 0; b < 3; b++) d[i + a][j + b] = true;
        if (shadow != 0) for (int i = 0; i < tw + 2; i++) for (int j = 0; j < th + 2; j++) if (d[i][j]) px(x - 1 + i, y - 1 + j + sd, shadow);
        for (int i = 0; i < tw + 2; i++) for (int j = 0; j < th + 2; j++) if (d[i][j]) px(x - 1 + i, y - 1 + j, outline);
        for (int i = 0; i < tw; i++) for (int j = 0; j < th; j++) if (m[i][j]) px(x + i, y + j, pick(grad, j / (double) (th - 1), x + i, y + j));
    }
    static void textC(int cx, int y, String s, int sc, int[] grad, int outline, int shadow, int sd) {
        text(cx - textW(s, sc) / 2, y, s, sc, 6 * sc, grad, outline, shadow, sd);
    }

    // ---------- icônes : # contour, w blanc, h clair, m moyen, d foncé ----------
    static final Map<String, String[]> ICONS = new HashMap<>();
    static {
        ICONS.put("raid", new String[]{          // viseur
                "....####....",
                "..##.hh.##..",
                ".#...hh...#.",
                "#....mm....#",
                "#hhm.ww.mhh#",
                "#....mm....#",
                ".#...hh...#.",
                "..##.hh.##..",
                "....####...."});
        ICONS.put("shop", new String[]{          // caisse de munitions
                "..########..",
                ".#wwhhhhmm#.",
                "############",
                "#hhhh##mmmm#",
                "#hhh#ww#mmd#",
                "#hhhh##mmmd#",
                "#hmmmmmmmdd#",
                "#mmmmmmmddd#",
                "############"});
        ICONS.put("drop", new String[]{          // parachute + caisse
                "...######...",
                ".##wwhhmm##.",
                "#wwhhhhmmmd#",
                "############",
                ".#..#..#..#.",
                "..#.#..#.#..",
                "...######...",
                "...#hhmd#...",
                "...######..."});
        ICONS.put("mail", new String[]{          // enveloppe
                "############",
                "#ww......mm#",
                "#h#w....m#d#",
                "#hh#w..m#dd#",
                "#hhh####ddd#",
                "#hhhhmmmmdd#",
                "#hhhmmmmmdd#",
                "#hmmmmmmddd#",
                "############"});
        ICONS.put("stash", new String[]{         // coffre
                ".##########.",
                "#wwhhhhhmmm#",
                "#whhhhhmmmd#",
                "############",
                "#hhhh#ww#md#",
                "#hhhh#hm#dd#",
                "#hhhmm##mdd#",
                "#hmmmmmmddd#",
                "############"});
        ICONS.put("stats", new String[]{         // barres
                ".........###",
                ".........#w#",
                ".....###.#h#",
                ".....#w#.#h#",
                ".###.#h#.#m#",
                ".#w#.#h#.#m#",
                ".#h#.#m#.#m#",
                ".#m#.#m#.#d#",
                "############"});
        ICONS.put("kit", new String[]{           // gilet tactique
                "##..####..##",
                "#w##hhhh##m#",
                "#hhhhhhhmmm#",
                ".#hh#ww#mm#.",
                ".#hh#hm#md#.",
                ".#hhh##mmd#.",
                ".#hmmmmmdd#.",
                ".#mmmmmddd#.",
                ".##########."});
        ICONS.put("tag", new String[]{           // étiquette de prix
                "......#####.",
                ".....#wwhh##",
                "....#wh##hm#",
                "...#whh##mm#",
                "..#whhhhmmd#",
                ".#whhhhmmdd#",
                "#whhhmmmdd#.",
                ".#hmmmddd#..",
                "..#######..."});
        ICONS.put("back", new String[]{
                "....##......",
                "...#w#......",
                "..#wh#######",
                ".#whhhhhhmm#",
                "#whhhhhmmmd#",
                ".#hhmmmmmdd#",
                "..#hm#######",
                "...#m#......",
                "....##......"});
        ICONS.put("signal", new String[]{
                ".........##.",
                ".........#w#",
                "......##.#h#",
                "......#w##h#",
                "...##.#h##m#",
                "...#w##h##m#",
                "##.#h##m##d#",
                "#w##m##m##d#",
                "############"});
    }
    static void icon(int x, int y, String name, int sc, int outline, int shadow, int[] pal) {
        String[] gl = ICONS.get(name);
        for (int pass = 0; pass < 2; pass++)
            for (int r = 0; r < gl.length; r++) for (int k = 0; k < gl[r].length(); k++) {
                char c = gl[r].charAt(k);
                if (c == '.') continue;
                int col = pass == 0 ? shadow : switch (c) {
                    case '#' -> outline; case 'w' -> pal[0]; case 'h' -> pal[1]; case 'm' -> pal[2]; default -> pal[3];
                };
                rect(x + k * sc, y + r * sc + (pass == 0 ? 1 : 0), sc, sc, col);
            }
    }
    static final int[] ICON_WHITE = {0xFFFFFFFF, 0xFFE9ECF2, 0xFFBDC2CC, 0xFF858A95};

    static void button(int x, int y, int w, int h, int light, int fill, int dark, int darker, String label) {
        int lip = 4;
        shape(x, y, w, h, 3, new int[]{darker}, OUT, darker, darker);
        shape(x, y, w, h - lip, 3, new int[]{light, fill, fill, dark}, OUT, mix(light, 0xFFFFFFFF, 0.5), dark);
        for (int i = 0; i < 3; i++) { px(x + 5 + i, y + 4, 0xFFFFFFFF); px(x + 4, y + 5 + i, 0xFFFFFFFF); }
        int adv = textW(label, 2) > w - 12 ? 11 : 12;
        int tw = textW(label, 2, adv);
        text(x + (w - tw) / 2, y + (h - lip - 14) / 2, label, 2, adv, new int[]{0xFFFFFFFF, 0xFFF1E9E7}, darker, darker, 1);
    }
    static void bracket(int x, int y, int w, int c) {
        rect(x, y, 6, 2, c); rect(x, y, 2, 6, c);
        rect(x + w - 6, y, 6, 2, c); rect(x + w - 2, y, 2, 6, c);
    }
    static void bracket2(int x, int y, int w, int h, int c) {
        rect(x, y + h - 2, 6, 2, c); rect(x, y + h - 6, 2, 6, c);
        rect(x + w - 6, y + h - 2, 6, 2, c); rect(x + w - 2, y + h - 6, 2, 6, c);
    }
    static void accentSlot(int c, int r, int col) {
        slot(sx(c), sy(r));
        int x = sx(c) * S - 4, y = sy(r) * S - 4;
        bracket(x, y, 40, col); bracket2(x, y, 40, 40, col);
    }
    /** Slot sans objet : l'icône est dessinée dans la texture (la zone cliquable est invisible). */
    static void iconSlot(int c, int r, String icon, int col) {
        accentSlot(c, r, col);
        icon(sx(c) * S + 4, sy(r) * S + 7, icon, 2, OUT, 0xFF020203, new int[]{0xFFFFFFFF, mix(col, 0xFFFFFFFF, 0.55), col, mix(col, 0xFF000000, 0.4)});
    }

    static void antenna(int x) {
        rect(x - 1, -30, 6, 32, OUT);
        rect(x, -29, 2, 30, METAL_HI); rect(x + 2, -29, 2, 30, METAL[2]);
        for (int y = -24; y < 0; y += 6) rect(x - 1, y, 6, 1, OUT);
        rect(x - 3, -2, 10, 5, OUT); rect(x - 2, -1, 8, 3, METAL[1]); rect(x - 2, -1, 8, 1, METAL_HI);
        for (int i = -6; i <= 9; i++) for (int j = -6; j <= 6; j++) {
            double d = Math.sqrt((i - 1.5) * (i - 1.5) + j * j);
            if (d < 6 && d > 3 && (6 - d) / 3 * 0.6 > bayer(x + i, j - 34)) px(x + i, j - 34, 0xFF8A251C);
        }
        rect(x - 1, -37, 6, 6, OUT); rect(x, -36, 4, 4, RED_HI); rect(x, -36, 2, 2, 0xFFFFFFFF);
    }
    static void dial(int cx, int cy, int r, String icon) {
        int[] ringPal = {METAL[3], METAL[2], METAL[1], METAL[0], METAL_HI};
        int[] face = {0xFF1C0C0A, 0xFF2A100D, 0xFF3A1511, 0xFF4A1A15};
        for (int x = -r - 1; x <= r + 1; x++) for (int y = -r - 1; y <= r + 1; y++) {
            double d = Math.sqrt(x * x + y * y);
            if (d > r + 0.5) continue;
            int c;
            if (d > r - 0.7) c = OUT;
            else if (d > r - 4.5) c = pick(ringPal, 0.5 - 0.5 * (x + y) / (r * 1.2), cx + x, cy + y);
            else if (d > r - 5.5) c = OUT;
            else c = pick(face, 0.5 - 0.5 * (x + y) / (r * 1.1), cx + x, cy + y);
            px(cx + x, cy + y, c);
        }
        icon(cx - 12, cy - 9, icon, 2, OUT, 0xFF120505, new int[]{0xFFFFFFFF, RED_HI2, RED_HI, RED});
    }

    /** Base commune : inventaire du joueur, panneau principal, plaque « Inventaire », antennes, bannière. */
    static void frame(String title, String leftIcon, String rightIcon) {
        img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        panel(-8, 266, 368, 178, 7);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) slot(sx(c), 140 + 18 * r);
        for (int c = 0; c < 9; c++) slot(sx(c), 198);
        rect(14, 390, 324, 1, 0xFF040405); rect(14, 391, 324, 1, 0xFF2C3036);

        panel(-8, 0, 368, 266, 3);
        for (int i = 4; i < 348; i++) {
            double t = 1 - Math.abs(i - 176) / 172.0;
            px(i, 252, pick(new int[]{0xFF1E0B09, RED_LO, RED, RED_HI}, t, i, 252));
            px(i, 253, pick(new int[]{0xFF160807, RED_LO2, RED_LO, RED}, t, i, 253));
        }
        shape(2, 253, 166, 21, 3, new int[]{0xFF404040}, OUT, 0xFF5A5A5F, 0xFF2B2B2E);
        rivet(6, 262); rivet(161, 262);
        for (int bx : new int[]{196, 300}) { rect(bx, 260, 12, 12, OUT); rect(bx + 1, 261, 10, 10, METAL[1]); rect(bx + 1, 261, 10, 1, METAL_HI); rivet(bx + 4, 264); }

        antenna(0); antenna(348);

        int bw = Math.max(248, textW(title, 4) + 70), bx = 176 - bw / 2, by = -34, bh = 42, cy = by + bh / 2;
        shape(bx - 14, cy - 9, bw + 28, 18, 4, METAL, OUT, METAL_HI, METAL_LO);
        shape(bx, by, bw, bh, 6, new int[]{0xFFE8695C, 0xFFD44A3B, RED, 0xFF9E2D21, RED_LO}, OUT, RED_HI2, RED_LO2);
        rect(bx + 6, by + 4, bw - 12, 1, 0xFFF4A79E);
        // bandes de danger aux deux bouts de la bannière
        for (int k = 0; k < 2; k++) for (int i = 0; i < 18; i++) for (int j = 6; j < bh - 8; j++) {
            int x = k == 0 ? bx + 30 + i : bx + bw - 48 + i;
            if (((i + j) / 4) % 2 == 0) px(x, by + j, mix(get(x, by + j), 0xFF1A0605, 0.55));
        }
        textC(176, cy - 14, title, 4, new int[]{0xFFFFFFFF, 0xFFFFF3F1, 0xFFF7CFC9}, RED_LO2, 0xFF2A0907, 3);
        dial(bx + 12, cy, 19, leftIcon);
        dial(bx + bw - 13, cy, 19, rightIcon);
    }

    static void heading(String label) {
        int[] grad = {0xFFFFFFFF, RED_HI2, RED_HI};
        textC(176, sy(0) * 2 + 9, label, 2, grad, OUT, 0, 0);
        int half = textW(label, 2) / 2 + 10;
        for (int i = 0; i < 4; i++) {
            int col = i == 0 ? RED_HI : i == 1 ? RED : i == 2 ? RED_LO : 0xFF2A0D0A;
            if (176 - half - 5 - i * 8 < 60) break;   // ne touche pas les cases des coins
            rect(176 - half - 5 - i * 8, sy(0) * 2 + 15, 5, 2, col); rect(176 + half + i * 8, sy(0) * 2 + 15, 5, 2, col);
        }
    }

    // =====================================================================================
    // Accueil : six applications (cartes de 3 x 2 cases)
    // =====================================================================================
    record App(String name, String icon, int[] pal) {}
    static final App[] APPS = {
            new App("RAID", "raid", new int[]{0xFF4A0E0E, 0xFF7A1717, 0xFFB32525, 0xFFE23B3B, 0xFFFF6B6B, 0xFFFFC2C2}),
            new App("BOUTIQUE", "shop", new int[]{0xFF5A3A06, 0xFF8A5A0C, 0xFFC48A17, 0xFFF5C542, 0xFFFFDD7A, 0xFFFFF2C4}),
            new App("COLIS", "drop", new int[]{0xFF0D3B1C, 0xFF17602F, 0xFF23944A, 0xFF3FC46A, 0xFF7CFC9A, 0xFFCBFFD8}),
            new App("MESSAGES", "mail", new int[]{0xFF122A6B, 0xFF1C3F99, 0xFF2F6AE0, 0xFF3A7FFF, 0xFF6EA2FF, 0xFFC4DAFF}),
            new App("COFFRE", "stash", new int[]{0xFF26185C, 0xFF3E2A99, 0xFF5B44D0, 0xFF7A5CFF, 0xFFA48CFF, 0xFFD5CBFF}),
            new App("STATS", "stats", new int[]{0xFF0A4750, 0xFF0F6B78, 0xFF169DAD, 0xFF22C7D9, 0xFF7FEBF7, 0xFFCFF8FC})};

    static void appCard(int c, int r, App a, long seed) {
        int[] p = a.pal();
        int x = sx(c) * S - 2, y = sy(r) * S - 2, w = 108, h = 72;
        int[] bg = {mix(p[1], 0xFF0A0A0C, 0.6), mix(p[0], 0xFF0A0A0C, 0.6), mix(p[0], 0xFF0A0A0C, 0.8)};
        shape(x + 1, y + 1, w - 2, h - 2, 5, bg, OUT, mix(p[2], 0xFF0A0A0C, 0.3), mix(p[0], 0xFF000000, 0.5));
        Random rnd = new Random(seed);
        for (int k = 0; k < 10; k++) px(x + 8 + rnd.nextInt(w - 16), y + 6 + rnd.nextInt(30), mix(p[3], 0xFF0A0A0C, 0.55));
        bracket(x + 3, y + 3, w - 6, p[4]);
        icon(x + (w - 36) / 2, y + 8, a.icon(), 3, OUT, mix(p[0], 0xFF000000, 0.6), new int[]{0xFFFFFFFF, p[5], p[4], p[3]});
        button(x + 6, y + 42, w - 12, 26, p[4], p[3], p[2], p[0], a.name());
    }

    static final int[][] HOME_ITEMS = {{0, 0, 0xFFE0B894}, {8, 0, 0xFFF5C542}};

    static void drawHome() {
        frame("TÉLÉPHONE", "signal", "signal");
        accentSlot(0, 0, RED_HI);
        accentSlot(8, 0, 0xFFF5C542);
        heading("APPLICATIONS");
        for (int i = 0; i < 6; i++) appCard((i % 3) * 3, 1 + (i / 3) * 2, APPS[i], 40 + i);
        // dernière ligne : trois boutons larges de 3 cases (kits, hôtel des ventes, carte)
        int y = sy(5) * S - 1;
        button(sx(0) * S - 2, y, 3 * 36, 34, 0xFFFFC27A, 0xFFFF9A3D, 0xFFC46A17, 0xFF5A2E06, "KITS");
        button(sx(3) * S - 2, y, 3 * 36, 34, 0xFFFFB0E0, 0xFFE05CB8, 0xFFA8307F, 0xFF4A0E36, "MARCHÉ");
        button(sx(6) * S - 2, y, 3 * 36, 34, 0xFFA8E2FF, 0xFF5CC8FF, 0xFF2A8FC4, 0xFF0A3D5A, "CARTE");
    }

    // =====================================================================================
    // Écrans de liste : retour, titre, solde, grille 9 x 4, pagination
    // =====================================================================================
    static final int[][] LIST_ITEMS = {{8, 0, 0xFFF5C542}, {0, 1, 0xFF9AA4C0}, {1, 1, 0xFFE2524A}, {2, 1, 0xFF2FBF71}, {3, 1, 0xFFF2A93B},
            {0, 2, 0xFF3A7FFF}, {1, 2, 0xFFB57BFF}, {4, 3, 0xFF22C7D9}};

    static final int[][] HDV_ITEMS = {{8, 0, 0xFFF5C542}, {0, 1, 0xFF9AA4C0}, {1, 1, 0xFFE2524A}, {2, 1, 0xFF2FBF71},
            {2, 5, 0xFFE05CB8}, {4, 5, 0xFFF5C542}, {6, 5, 0xFFE67E22}};

    static final int[][] KIT_ITEMS = {{8, 0, 0xFFF5C542}, {4, 1, 0xFFE6EDF3}, {1, 3, 0xFFD7DCE2}, {3, 3, 0xFFF5C542}, {5, 3, 0xFF5CE0A0}, {7, 3, 0xFFB57BFF}};

    static void drawList(String title, String icon, String label, int accent, boolean split) {
        frame(title, icon, icon);
        iconSlot(0, 0, "back", RED_HI);
        accentSlot(8, 0, 0xFFF5C542);
        heading(label);
        int gx = sx(0) * S - 6, gy = sy(1) * S - 6, gw = 9 * 36 + 8, gh = 4 * 36 + 8;
        if (split) {   // messages : contacts en haut, messages reçus en bas
            int half = gh / 2;
            shape(gx, gy, gw, half, 4, new int[]{mix(0xFF1C3F99, 0xFF0A0A0C, 0.7), mix(0xFF122A6B, 0xFF0A0A0C, 0.8)}, OUT, 0xFF2F4A8A, 0xFF070A14);
            shape(gx, gy + half, gw, half, 4, new int[]{mix(0xFF17602F, 0xFF0A0A0C, 0.7), mix(0xFF0D3B1C, 0xFF0A0A0C, 0.8)}, OUT, 0xFF2A7A45, 0xFF07140B);
        } else {
            shape(gx, gy, gw, gh, 4, new int[]{mix(accent, 0xFF0A0A0C, 0.78), mix(accent, 0xFF0A0A0C, 0.88)}, OUT, mix(accent, 0xFF0A0A0C, 0.4), 0xFF060607);
        }
        for (int r = 1; r <= 4; r++) for (int c = 0; c < 9; c++) slot(sx(c), sy(r));
        bracket(gx - 2, gy - 2, gw + 4, accent); bracket2(gx - 2, gy - 2, gw + 4, gh + 4, accent);
        // rail de pagination
        int ry = sy(5) * S + 15;
        rect(46, ry - 1, 260, 4, OUT); rect(46, ry, 260, 1, METAL_HI); rect(46, ry + 1, 260, 1, METAL[2]);
        for (int i = 0; i < 5; i++) { int dx = 100 + i * 36; rect(dx, ry - 2, 4, 6, OUT); rect(dx + 1, ry - 1, 2, 4, accent); }
        accentSlot(0, 5, accent);
        accentSlot(8, 5, accent);
    }

    // =====================================================================================
    // Fiche de l'opérateur (Maj + F) : quatre bandeaux de 8 cases, icône de section dessinée en tête de chacun
    // =====================================================================================
    static final int[] BAND_COLORS = {0xFFE8695C, 0xFF9AA4C0, 0xFF22C7D9, 0xFFF5C542};
    static final String[] BAND_ICONS = {"kit", "shop", "stats", "stash"};

    static int[][] profileItems() {
        List<int[]> l = new ArrayList<>();
        l.add(new int[]{8, 0, 0xFFF5C542});
        for (int r = 1; r <= 4; r++) for (int c = 1; c <= 8; c++) l.add(new int[]{c, r, BAND_COLORS[r - 1]});
        return l.toArray(new int[0][]);
    }

    static void drawProfile() {
        frame("OPÉRATEUR", "kit", "stats");
        iconSlot(0, 0, "back", RED_HI);
        accentSlot(8, 0, 0xFFF5C542);
        heading("MA FICHE");
        for (int r = 1; r <= 4; r++) {
            int acc = BAND_COLORS[r - 1];
            int x = sx(0) * S - 6, y = sy(r) * S - 3, w = 9 * 36 + 8, h = 38;
            shape(x, y, w, h, 4, new int[]{mix(acc, 0xFF0A0A0C, 0.8), mix(acc, 0xFF0A0A0C, 0.9)}, OUT, mix(acc, 0xFF0A0A0C, 0.45), 0xFF060607);
            // filet de couleur derrière les huit cases du bandeau
            for (int i = sx(1) * S - 4; i < sx(8) * S + 36; i++) px(i, y + h / 2, mix(acc, 0xFF0A0A0C, 0.65));
            for (int c = 1; c <= 8; c++) slot(sx(c), sy(r));
            iconSlot(0, r, BAND_ICONS[r - 1], acc);
        }
        // rail du bas et bouton « fermer » au centre
        int ry = sy(5) * S + 15;
        rect(46, ry - 1, 260, 4, OUT); rect(46, ry, 260, 1, METAL_HI); rect(46, ry + 1, 260, 1, METAL[2]);
        accentSlot(4, 5, RED_HI);
        icon(sx(4) * S + 4, sy(5) * S + 7, "close", 2, OUT, 0xFF020203, new int[]{0xFFFFFFFF, RED_HI2, RED_HI, RED});
    }

    static {
        ICONS.put("close", new String[]{        // croix
                ".##......##.",
                "#ww#....#hm#",
                ".#wh#..#hm#.",
                "..#hh##mm#..",
                "...#hmmd#...",
                "..#hm##dd#..",
                ".#hm#..#dd#.",
                "#hm#....#dd#",
                ".##......##."});
    }

    // ---------- aperçu ----------
    static final String[] ITEM = {
            "................", "......####......", ".....#wwhh#.....", "....#wwhhhm#....", "...#wwhhhmmm#...", "..#whhhhmmmmd#..",
            ".#############..", ".#hhhhmmmmmmdd#.", "..#hhhmmmmmdd#..", "...#hhmmmmdd#...", "....#hmmmdd#....", ".....#hmdd#.....",
            "......#md#......", ".......##.......", "................", "................"};
    static BufferedImage preview(int[][] items) {
        BufferedImage out = new BufferedImage(W + 40, H + 30, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < out.getWidth(); x++) for (int y = 0; y < out.getHeight(); y++) out.setRGB(x, y, 0xFF20262E);
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) {
            int c = img.getRGB(x, y);
            if ((c >>> 24) != 0) out.setRGB(20 + x, 15 + y, c);
        }
        for (int[] it : items) {
            int x0 = 20 + OX + sx(it[0]) * S, y0 = 15 + OY + sy(it[1]) * S;
            for (int r = 0; r < 16; r++) for (int k = 0; k < 16; k++) {
                char ch = ITEM[r].charAt(k);
                if (ch == '.') continue;
                int c = switch (ch) {
                    case '#' -> 0xFF101018; case 'w' -> mix(it[2], 0xFFFFFFFF, 0.6); case 'h' -> mix(it[2], 0xFFFFFFFF, 0.25);
                    case 'm' -> it[2]; default -> mix(it[2], 0xFF000000, 0.35);
                };
                for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) out.setRGB(x0 + k * 2 + i, y0 + r * 2 + j, c);
            }
        }
        return out;
    }
    /** Pixel alpha 1 dans la dernière colonne de chaque tuile : avance fixe de 99 unités par tuile. */
    static BufferedImage forPack() {
        BufferedImage t = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        t.setData(img.getData());
        int tw = W / 2, th = H / 4;
        for (int row = 0; row < 4; row++) for (int col = 0; col < 2; col++) {
            int x = col * tw + tw - 1, y = row * th;
            if ((t.getRGB(x, y) >>> 24) == 0) t.setRGB(x, y, 0x01000000);
        }
        return t;
    }

    static final List<String> SCREENS = new ArrayList<>();

    static void write(String name, int[][] items, File pack, File previews) throws Exception {
        File out = new File(pack, "assets/orbitguns/textures/gui/" + name + ".png");
        out.getParentFile().mkdirs();
        ImageIO.write(forPack(), "png", out);
        SCREENS.add(name);
        if (previews != null) {
            previews.mkdirs();
            ImageIO.write(preview(items), "png", new File(previews, "telephone_" + name + "_apercu.png"));
        }
    }

    /** Police : espaces négatifs + 4 fournisseurs (un par rangée de tuiles) par écran ; écran n = U+E200 + 0x10 * n. */
    static void writeFont(File pack) throws Exception {
        StringBuilder b = new StringBuilder("{\n  \"providers\": [\n    {\"type\": \"space\", \"advances\": {\"\\uF000\": -18, \"\\uF001\": -1, \"\\uF002\": -197}}");
        int[] ascent = {37, -25, -87, -149};
        for (int s = 0; s < SCREENS.size(); s++) {
            int base = 0xE200 + 0x10 * s;
            for (int row = 0; row < 4; row++) {
                b.append(",\n    {\"type\": \"bitmap\", \"file\": \"orbitguns:gui/").append(SCREENS.get(s))
                        .append(".png\", \"height\": 62, \"ascent\": ").append(ascent[row]).append(", \"chars\": [");
                for (int r = 0; r < 4; r++) {
                    b.append(r == 0 ? "" : ", ");
                    b.append(r == row ? String.format("\"\\u%04X\\u%04X\"", base + row * 2, base + row * 2 + 1) : "\"\\u0000\\u0000\"");
                }
                b.append("]}");
            }
        }
        b.append("\n  ]\n}\n");
        File f = new File(pack, "assets/orbitguns/font/gui.json");
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), b.toString(), StandardCharsets.UTF_8);
        File inv = new File(pack, "assets/orbitguns/items/invisible.json");
        inv.getParentFile().mkdirs();
        Files.writeString(inv.toPath(), "{\n  \"model\": {\n    \"type\": \"minecraft:empty\"\n  }\n}\n", StandardCharsets.UTF_8);
    }

    public static void main(String[] a) throws Exception {
        File pack = new File(a[0]);
        File previews = a.length > 1 ? new File(a[1]) : null;
        // L'ordre fixe les points de code : il doit rester celui de PhoneMenu.Screen.
        drawHome();
        write("home", HOME_ITEMS, pack, previews);
        drawList("BOUTIQUE", "shop", "ARMES ET VIVRES", 0xFFF5C542, false);
        write("shop", LIST_ITEMS, pack, previews);
        drawList("COLIS", "drop", "LARGAGES", 0xFF3FC46A, false);
        write("colis", LIST_ITEMS, pack, previews);
        drawList("MESSAGES", "mail", "MESSAGERIE", 0xFF3A7FFF, true);
        write("messages", LIST_ITEMS, pack, previews);
        drawList("KITS", "kit", "RANGS ET KITS", 0xFFFF9A3D, false);
        write("kits", KIT_ITEMS, pack, previews);
        drawList("MARCHÉ", "tag", "HÔTEL DES VENTES", 0xFFE05CB8, false);
        for (int c : new int[]{2, 4, 6}) accentSlot(c, 5, 0xFFE05CB8);    // mes annonces, vendre, récupérer
        write("hdv", HDV_ITEMS, pack, previews);
        drawProfile();
        write("profil", profileItems(), pack, previews);
        writeFont(pack);
        System.out.println("OK " + SCREENS.size() + " ecrans du telephone");
    }
}
