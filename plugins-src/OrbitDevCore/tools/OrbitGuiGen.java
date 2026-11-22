import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * Générateur des textures de menus ORBIT (DA « station spatiale »), coffre 6 lignes (176x222 unités GUI, slots 18).
 * Rendu en résolution x2 (1 unité GUI = 2x2 sous-pixels) : pixel art Minecraft, mais deux fois plus fin.
 * Dans le resource pack, la glyphe est déclarée avec height = hauteur GUI (l'image est 2x plus grande).
 * Tous les menus partagent frame() (panneaux, antennes, bannière, badges) : un nouveau menu = une méthode drawXxx().
 *
 * Contraintes Minecraft (ne pas casser) :
 *  - rien au-dessus de 24 unités GUI au-dessus du coffre : à l'échelle 4 en 1080p, l'écran ne montre pas plus ;
 *  - une glyphe de police ne dépasse pas 256x256 px : l'image est découpée en 2 x 4 tuiles (196 x 124 px) ;
 *  - le jeu écrit « Inventaire » en #404040 en (8,128) : une plaque de cette couleur exacte le rend invisible.
 *
 * Usage : java OrbitGuiGen.java <dossier_apercus> [<dossier_pack>]
 *  (avec le dossier du pack : écrit aussi assets/orbitdev/textures/gui/<menu>.png et la police assets/orbitdev/font/gui.json)
 */
public class OrbitGuiGen {
    static final int S = 2;                                 // sous-pixels par unité GUI
    static final int W = 196 * S, H = 248 * S, OX = 10 * S, OY = 24 * S;
    static BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);

    // ---------- Palette ORBIT ----------
    static final int OUT = 0xFF05050A, WHITE = 0xFFF4F7FF,
            BLUE = 0xFF3A7FFF, BLUE_HI = 0xFF8DB8FF, BLUE_HI2 = 0xFFC4DAFF, BLUE_LO = 0xFF1C3F99, BLUE_LO2 = 0xFF122A6B;
    static final int[] METAL = {0xFF454D6E, 0xFF363D58, 0xFF2A2F45, 0xFF1F2333};
    static final int METAL_HI = 0xFF6B76A3, METAL_LO = 0xFF121420;
    static final int[] SCREEN = {0xFF181A27, 0xFF13141E, 0xFF0E0F17, 0xFF0B0B12};
    static final int SCREEN_IN_DK = 0xFF06070B, SCREEN_IN_HI = 0xFF2B3049;

    // ---------- Primitives (coordonnées en sous-pixels, origine = coin du coffre) ----------
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
    /** Dégradé par paliers, tramé (Bayer) uniquement à la jonction des paliers : rendu pixel art. */
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
    /** Forme chanfreinée : contour, biseau 1 sous-pixel (haut/gauche = hi, bas/droite = lo), dégradé vertical. */
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

    // ---------- Panneau métal + écran étoilé ----------
    static void panel(int x, int y, int w, int h, long seed) {
        shape(x, y, w, h, 6, METAL, OUT, METAL_HI, METAL_LO);
        int m = 8, ix = x + m, iy = y + m, iw = w - 2 * m, ih = h - 2 * m;
        shape(ix, iy, iw, ih, 4, SCREEN, OUT, SCREEN_IN_DK, SCREEN_IN_HI); // écran creusé (biseau inversé)
        // nébuleuse tramée
        for (int i = 3; i < iw - 3; i++) for (int j = 3; j < ih - 3; j++) {
            int gx = ix + i, gy = iy + j;
            double n = Math.sin(gx * 0.045 + seed) + Math.sin(gy * 0.06 + gx * 0.018) + Math.sin((gx + gy) * 0.027 + seed * 2);
            if (n > 1.1 && (n - 1.1) / 1.0 > bayer(gx, gy)) px(gx, gy, (gx / 40 + seed) % 2 == 0 ? 0xFF1B1836 : 0xFF141C38);
        }
        // étoiles
        Random r = new Random(seed);
        for (int k = 0; k < (iw * ih) / 260; k++) {
            int sx = ix + 4 + r.nextInt(iw - 8), sy = iy + 4 + r.nextInt(ih - 8);
            int t = r.nextInt(20);
            if (t < 13) px(sx, sy, 0xFF2E3454);
            else if (t < 18) px(sx, sy, 0xFF56628C);
            else if (t < 19) { rect(sx, sy, 2, 2, 0xFF8E9CC8); }
            else twinkle(sx, sy, 0xFFFFFFFF, 0xFF7FA6FF, 0xFF2C3A70);
        }
        // trame de vis + jointures sur le cadre
        for (int bx = x + 24; bx < x + w - 24; bx += 48) { rivet(bx, y + 3); rivet(bx, y + h - 6); }
        for (int by = y + 24; by < y + h - 24; by += 48) { rivet(x + 3, by); rivet(x + w - 6, by); }
        rivet(x + 3, y + 3); rivet(x + w - 6, y + 3); rivet(x + 3, y + h - 6); rivet(x + w - 6, y + h - 6);
    }
    static void rivet(int x, int y) {
        rect(x, y, 3, 3, OUT); px(x, y, 0xFFB7C1E6); px(x + 1, y, 0xFF8792BE); px(x, y + 1, 0xFF8792BE); px(x + 1, y + 1, 0xFF5D678F);
    }
    static void twinkle(int x, int y, int core, int mid, int far) {
        px(x, y, core);
        px(x - 1, y, mid); px(x + 1, y, mid); px(x, y - 1, mid); px(x, y + 1, mid);
        px(x - 2, y, far); px(x + 2, y, far); px(x, y - 2, far); px(x, y + 2, far);
    }

    // ---------- Slot (gx, gy = coin intérieur 16x16 en unités GUI, comme Minecraft) ----------
    static void slot(int gx, int gy) {
        int x = gx * S - 2, y = gy * S - 2, s = 36;
        rect(x, y, s, s, OUT);
        rect(x + 1, y + 1, s - 2, s - 2, 0xFF33395A);       // lumière bas/droite
        rect(x + 1, y + 1, s - 3, s - 3, 0xFF030306);       // ombre haut/gauche
        for (int j = 0; j < 32; j++) for (int i = 0; i < 32; i++)
            px(x + 2 + i, y + 2 + j, pick(new int[]{0xFF0A0B12, 0xFF0E1019, 0xFF121521}, j / 31.0, x + 2 + i, y + 2 + j));
        rect(x + 2, y + 2, 32, 1, 0xFF07080D);
        px(x + 2, y + 2, 0xFF050509);
    }
    static int sx(int c) { return 8 + 18 * c; }
    static int sy(int r) { return 18 + 18 * r; }

    // ---------- Police bitmap 5x7 ----------
    static final Map<Character, String[]> F = new HashMap<>();
    static void g(char c, String... rows) { F.put(c, rows); }
    static {
        g('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        g('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
        g('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
        g('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
        g('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
        g('É', "..##.", "#####", "#....", "####.", "#....", "#....", "#####");
        g('Î', "..#..", ".#.#.", ".....", "#####", "..#..", "..#..", "#####");
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
        g('+', ".....", "..#..", "..#..", "#####", "..#..", "..#..", ".....");
        g('-', ".....", ".....", ".....", "#####", ".....", ".....", ".....");
        g(' ', ".....", ".....", ".....", ".....", ".....", ".....", ".....");
    }
    static int textW(String s, int sc) { return textW(s, sc, 6 * sc); }
    /** adv = avance par lettre (6 x sc normalement ; 6 x sc - 1 pour serrer un libellé trop long). */
    static int textW(String s, int sc, int adv) { return s.length() * adv - (adv - 5 * sc); }
    /** Texte : ombre portée, contour 1 sous-pixel, remplissage en dégradé vertical. */
    static void text(int x, int y, String s, int sc, int[] grad, int outline, int shadow, int sd) {
        text(x, y, s, sc, 6 * sc, grad, outline, shadow, sd);
    }
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
        boolean[][] d = new boolean[tw + 2][th + 2]; // masque dilaté (contour)
        for (int i = 0; i < tw; i++) for (int j = 0; j < th; j++) if (m[i][j])
            for (int a = 0; a < 3; a++) for (int b = 0; b < 3; b++) d[i + a][j + b] = true;
        if (shadow != 0) for (int i = 0; i < tw + 2; i++) for (int j = 0; j < th + 2; j++) if (d[i][j]) px(x - 1 + i, y - 1 + j + sd, shadow);
        for (int i = 0; i < tw + 2; i++) for (int j = 0; j < th + 2; j++) if (d[i][j]) px(x - 1 + i, y - 1 + j, outline);
        for (int i = 0; i < tw; i++) for (int j = 0; j < th; j++) if (m[i][j]) px(x + i, y + j, pick(grad, j / (double) (th - 1), x + i, y + j));
    }
    static void textC(int cx, int y, String s, int sc, int[] grad, int outline, int shadow, int sd) {
        text(cx - textW(s, sc) / 2, y, s, sc, grad, outline, shadow, sd);
    }

    // ---------- Icônes (sous-pixels) : # contour, w blanc, h clair, m moyen, d foncé ----------
    static final Map<String, String[]> ICONS = new HashMap<>();
    static {
        ICONS.put("gem", new String[]{
                "...######...",
                "..#wwhhmm#..",
                ".#wwhhhmmmd#",
                "############",
                ".#hhhmmmdd#.",
                "..#hhmmmd#..",
                "...#hmmd#...",
                "....#md#....",
                ".....##....."});
        ICONS.put("crown", new String[]{
                "##...##...##",
                "#w#.#ww#.#w#",
                "#hw#whhm#wm#",
                "#hhwhhmmmmm#",
                "#hhhhmmmmmd#",
                "############",
                "#wwhhmmmmdd#",
                "############"});
        ICONS.put("key", new String[]{
                ".####........",
                "#wwhm#.......",
                "#w..m########",
                "#h..m#whhmmd#",
                "#hmmd########",
                ".####..#m#m#.",
                ".......#####."});
        ICONS.put("box", new String[]{
                "############",
                "#wwwhh#hhhm#",
                "#whhhh#hmmm#",
                "############",
                "#hhhh##mmmd#",
                "#hhh#ww#mmd#",
                "#hhhh##mmmd#",
                "#hmmmmmmmdd#",
                "############"});
        ICONS.put("orbit", new String[]{
                "....####.....",
                "...#wwhm#..##",
                "..#whhhmm##w#",
                "..#hhhm##mm#.",
                ".##hm##mmmd#.",
                "#w##dmmmmd#..",
                "##..#dddd#...",
                ".....####...."});
        String[] chev = {
                "....##",
                "...#w#",
                "..#wh#",
                ".#wh#.",
                "#wh#..",
                ".#hm#.",
                "..#hm#",
                "...#m#",
                "....##"};
        ICONS.put("chevl", chev);
        String[] rev = new String[chev.length];
        for (int i = 0; i < chev.length; i++) rev[i] = new StringBuilder(chev[i]).reverse().toString();
        ICONS.put("chevr", rev);
    }
    static final int IS = 2; // échelle des icônes (= celle du texte des boutons)
    static final int[] ICON_WHITE = {0xFFFFFFFF, 0xFFE3EBFF, 0xFFB3C3EC, 0xFF7D8CB9};
    static void icon(int x, int y, String name, int outline, int shadow) { icon(x, y, name, outline, shadow, ICON_WHITE); }
    /** pal = couleurs de w, h, m, d. */
    static void icon(int x, int y, String name, int outline, int shadow, int[] pal) {
        String[] gl = ICONS.get(name);
        for (int pass = 0; pass < 2; pass++)
            for (int r = 0; r < gl.length; r++) for (int k = 0; k < gl[r].length(); k++) {
                char c = gl[r].charAt(k);
                if (c == '.') continue;
                int col = pass == 0 ? shadow : switch (c) {
                    case '#' -> outline; case 'w' -> pal[0]; case 'h' -> pal[1]; case 'm' -> pal[2]; default -> pal[3];
                };
                rect(x + k * IS, y + r * IS + (pass == 0 ? 1 : 0), IS, IS, col);
            }
    }
    static int iconW(String n) { return ICONS.get(n)[0].length() * IS; }
    static int iconH(String n) { return ICONS.get(n).length * IS; }

    // ---------- Bouton ----------
    static void button(int x, int y, int w, int h, int light, int fill, int dark, int darker, String label, String icon) {
        int lip = 4;
        shape(x, y, w, h, 3, new int[]{darker}, OUT, darker, darker);                       // socle (lèvre 3D)
        shape(x, y, w, h - lip, 3, new int[]{light, fill, fill, dark}, OUT, mix(light, 0xFFFFFFFF, 0.5), dark);
        for (int i = 0; i < 3; i++) { px(x + 5 + i, y + 4, 0xFFFFFFFF); px(x + 4, y + 5 + i, 0xFFFFFFFF); } // reflet
        px(x + 9, y + 4, mix(light, 0xFFFFFFFF, 0.6));
        int extra = icon != null ? iconW(icon) + 6 : 0;
        int adv = textW(label, 2) + extra > w - 8 ? 11 : 12;    // libellé trop long : lettres serrées
        int tw = textW(label, 2, adv) + extra;
        int tx = x + (w - tw) / 2, ty = y + (h - lip - 14) / 2;
        if (icon != null) {
            icon(tx, ty + (14 - iconH(icon)) / 2, icon, darker, darker);
            tx += extra;
        }
        text(tx, ty, label, 2, adv, new int[]{0xFFFFFFFF, 0xFFE4ECFF}, darker, darker, 1);
    }

    // ---------- Astres ----------
    static void disc(int cx, int cy, double r, int col) {
        for (int x = (int) -r - 1; x <= r + 1; x++) for (int y = (int) -r - 1; y <= r + 1; y++)
            if (x * x + y * y <= r * r) px(cx + x, cy + y, col);
    }
    static void ring(int cx, int cy, double rx, double ry, double rot, int[] cols, boolean front) {
        double cs = Math.cos(rot), sn = Math.sin(rot);
        for (int x = (int) -rx - 3; x <= rx + 3; x++) for (int y = (int) -rx - 3; y <= rx + 3; y++) {
            double u = x * cs + y * sn, v = -x * sn + y * cs;
            if ((v >= 0) != front) continue;
            double e = Math.sqrt((u * u) / (rx * rx) + (v * v) / (ry * ry));
            if (e > 1.0 && e <= 1.0 + 1.4 / ry) px(cx + x, cy + y, OUT);
            else if (e <= 1.0 && e > 0.72) px(cx + x, cy + y, pick(cols, (e - 0.72) / 0.28, cx + x, cy + y));
            else if (e <= 0.72 && e > 0.72 - 1.2 / ry) px(cx + x, cy + y, OUT);
        }
    }
    /** Planète ombrée (lumière haut-gauche), bandes, cratères, liseré lumineux, anneau devant/derrière. */
    static void planet(int cx, int cy, int r, int[] pal, int[] ringCols, double rot, long seed) {
        double rx = r * 1.65, ry = r * 0.42;
        if (ringCols != null) ring(cx, cy, rx, ry, rot, ringCols, false);
        for (int x = -r - 1; x <= r + 1; x++) for (int y = -r - 1; y <= r + 1; y++) {
            double d = Math.sqrt(x * x + y * y);
            if (d > r + 0.5) continue;
            if (d > r - 0.7) { px(cx + x, cy + y, OUT); continue; }
            double nx = x / (double) r, ny = y / (double) r, nz = Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny));
            double lam = -0.55 * nx - 0.6 * ny + 0.58 * nz;
            double band = 0.07 * Math.sin((y + x * 0.25) * 0.55 + seed);
            double t = 0.15 + 0.85 * Math.max(0, lam) + band;
            int c = pick(pal, t, cx + x, cy + y);
            if (d > r - 2.2 && lam > 0.25 && nx + ny < -0.6) c = pal[pal.length - 1]; // liseré
            px(cx + x, cy + y, c);
        }
        Random rnd = new Random(seed);
        for (int k = 0; k < 3; k++) {
            int ox = rnd.nextInt(r) - r / 2, oy = rnd.nextInt(r) - r / 3, cr = 2 + rnd.nextInt(Math.max(1, r / 7));
            for (int x = -cr; x <= cr; x++) for (int y = -cr; y <= cr; y++)
                if (x * x + y * y <= cr * cr) {
                    int px0 = cx + ox + x, py0 = cy + oy + y;
                    if ((px0 - cx) * (px0 - cx) + (py0 - cy) * (py0 - cy) < (r - 3) * (r - 3))
                        px(px0, py0, (x + y < -cr / 2) ? pal[0] : mix(get(px0, py0), pal[0], 0.45));
                }
        }
        px(cx - r / 2, cy - r / 2, 0xFFFFFFFF); px(cx - r / 2 + 1, cy - r / 2, pal[pal.length - 1]);
        if (ringCols != null) ring(cx, cy, rx, ry, rot, ringCols, true);
    }
    static void moon(int cx, int cy, int r) {
        int[] pal = {0xFF5B6488, 0xFF8E98BA, 0xFFC9D2EE, 0xFFF1F4FF};
        planet(cx, cy, r, pal, null, 0, 9);
        // croissant : on « mange » la partie droite avec le fond
        for (int x = -r - 1; x <= r + 1; x++) for (int y = -r - 1; y <= r + 1; y++) {
            double dx = x - r * 0.55, dy = y + r * 0.35;
            double d = Math.sqrt(dx * dx + dy * dy);
            if (x * x + y * y <= (r + 1) * (r + 1) && d < r * 0.95) px(cx + x, cy + y, d > r * 0.95 - 1.2 ? OUT : 0xFF10111A);
        }
    }

    static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ---------- Composition ----------
    static void antenna(int x) {
        rect(x - 1, -30, 6, 32, OUT);
        rect(x, -29, 2, 30, METAL_HI); rect(x + 2, -29, 2, 30, METAL[2]);
        for (int y = -24; y < 0; y += 6) rect(x - 1, y, 6, 1, OUT);
        rect(x - 3, -2, 10, 5, OUT); rect(x - 2, -1, 8, 3, METAL[1]); rect(x - 2, -1, 8, 1, METAL_HI);
        // balise lumineuse avec halo tramé
        for (int i = -6; i <= 9; i++) for (int j = -6; j <= 6; j++) {
            double d = Math.sqrt((i - 1.5) * (i - 1.5) + j * j);
            if (d < 6 && d > 3 && (6 - d) / 3 * 0.6 > bayer(x + i, j - 34)) px(x + i, j - 34, 0xFF2D5BC4);
        }
        rect(x - 1, -37, 6, 6, OUT); rect(x, -36, 4, 4, BLUE_HI); rect(x, -36, 2, 2, 0xFFFFFFFF);
    }

    /**
     * Base commune : inventaire joueur, panneau principal, plaque « Inventaire », antennes,
     * bannière titre avec un badge à chaque bout (compass | planet | gem).
     */
    interface Badge { void draw(int cx, int cy, int r); }

    static void frame(String title, String leftBadge, String rightBadge) {
        frame(title, (x, y, r) -> badge(leftBadge, x, y, r), (x, y, r) -> badge(rightBadge, x, y, r));
    }
    static void frame(String title, Badge leftBadge, Badge rightBadge) {
        img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        // inventaire joueur
        panel(-8, 266, 368, 178, 7);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) slot(sx(c), 140 + 18 * r);
        for (int c = 0; c < 9; c++) slot(sx(c), 198);
        rect(14, 390, 324, 1, 0xFF04050A); rect(14, 391, 324, 1, 0xFF2B3049);

        // panneau principal
        panel(-8, 0, 368, 266, 3);

        // bande lumineuse basse (dégradé tramé du centre vers les bords)
        for (int i = 4; i < 348; i++) {
            double t = 1 - Math.abs(i - 176) / 172.0;
            px(i, 252, pick(new int[]{0xFF0F1530, BLUE_LO, BLUE, BLUE_HI}, t, i, 252));
            px(i, 253, pick(new int[]{0xFF0C1022, BLUE_LO2, BLUE_LO, BLUE}, t, i, 253));
        }

        // plaque d'acier sous le libellé « Inventaire » du jeu : même gris (#404040) que le texte, qui disparaît.
        shape(2, 253, 166, 21, 3, new int[]{0xFF404040}, OUT, 0xFF5A5A63, 0xFF2B2B31);
        rivet(6, 262); rivet(161, 262);
        // liaisons entre panneaux
        for (int bx : new int[]{196, 300}) { rect(bx, 260, 12, 12, OUT); rect(bx + 1, 261, 10, 10, METAL[1]); rect(bx + 1, 261, 10, 1, METAL_HI); rivet(bx + 4, 264); }

        antenna(0); antenna(348);

        // bannière : ailes métal + plaque bleue (largeur adaptée au titre), badges aux deux bouts
        int bw = Math.max(248, textW(title, 4) + 70), bx = 176 - bw / 2, by = -34, bh = 42, cy = by + bh / 2;
        shape(bx - 14, cy - 9, bw + 28, 18, 4, METAL, OUT, METAL_HI, METAL_LO);
        shape(bx, by, bw, bh, 6, new int[]{0xFF6EA2FF, 0xFF4A8BFF, BLUE, 0xFF2F6AE0, BLUE_LO}, OUT, BLUE_HI2, BLUE_LO2);
        rect(bx + 6, by + 4, bw - 12, 1, 0xFFA9C9FF);             // reflet
        textC(176, cy - 14, title, 4, new int[]{0xFFFFFFFF, 0xFFF0F5FF, 0xFFC9DBFF}, BLUE_LO2, 0xFF0B1A45, 3);
        leftBadge.draw(bx + 12, cy, 19);
        rightBadge.draw(bx + bw - 13, cy, 19);
    }

    /** Cadran rond (anneau métal + fond bleu nuit) qui sert de fond aux badges. */
    static void dial(int cx, int cy, int r) {
        int[] ringPal = {METAL[3], METAL[2], METAL[1], METAL[0], METAL_HI};
        int[] dial = {0xFF0B142D, 0xFF0F1C3D, 0xFF15264F, 0xFF1C3166};
        for (int x = -r - 1; x <= r + 1; x++) for (int y = -r - 1; y <= r + 1; y++) {
            double d = Math.sqrt(x * x + y * y);
            if (d > r + 0.5) continue;
            int c;
            if (d > r - 0.7) c = OUT;
            else if (d > r - 4.5) c = pick(ringPal, 0.5 - 0.5 * (x + y) / (r * 1.2), cx + x, cy + y);
            else if (d > r - 5.5) c = OUT;
            else c = pick(dial, 0.5 - 0.5 * (x + y) / (r * 1.1), cx + x, cy + y);
            px(cx + x, cy + y, c);
        }
    }
    static void badge(String type, int cx, int cy, int r) {
        switch (type) {
            case "compass" -> compass(cx, cy, r);
            case "planet" -> {
                dial(cx, cy, r);
                planet(cx, cy, r - 9, new int[]{0xFF122A6B, BLUE_LO, 0xFF2F6AE0, BLUE, 0xFF6EA2FF, BLUE_HI2},
                        new int[]{0xFF5D7FC8, 0xFFB9CFFF, 0xFFFFFFFF}, -0.32, 4);
            }
            case "gem" -> {
                dial(cx, cy, r);
                icon(cx - iconW("gem") / 2, cy - iconH("gem") / 2 - 1, "gem", OUT, 0xFF060A18,
                        new int[]{0xFFFFFFFF, BLUE_HI2, BLUE_HI, BLUE});
            }
            default -> throw new IllegalArgumentException("badge inconnu : " + type);
        }
    }

    // =====================================================================================
    // Boutique
    // =====================================================================================
    static final int[][] SHOP_ITEMS = {{0, 0, 0xFFE0B894}, {8, 0, 0xFF3A7FFF}, {2, 1, 0xFF9AA4C0}, {3, 1, 0xFFB57BFF}, {4, 1, 0xFFF2A93B},
            {5, 1, 0xFF2FBF71}, {6, 1, 0xFFE2524A}, {2, 2, 0xFF22C7D9}, {3, 2, 0xFFF2A93B}, {4, 2, 0xFF7A5CFF}, {5, 2, 0xFF3A7FFF}, {6, 2, 0xFFE0E6F5}};

    static void drawShop() {
        frame("BOUTIQUE", "planet", "gem");

        // ligne 0 : profil / titre / solde
        slot(sx(0), sy(0)); slot(sx(8), sy(0));
        for (int[] b : new int[][]{{sx(0) * 2 - 3, sy(0) * 2 - 3}, {sx(8) * 2 - 3, sy(0) * 2 - 3}}) bracket(b[0], b[1], 38, BLUE);
        textC(176, sy(0) * 2 + 9, "EN VEDETTE", 2, new int[]{0xFFFFFFFF, BLUE_HI2, BLUE_HI}, OUT, 0, 0);
        for (int i = 0; i < 4; i++) {
            int c = i == 0 ? BLUE_HI : i == 1 ? BLUE : i == 2 ? BLUE_LO : 0xFF16244F;
            rect(104 - i * 8, sy(0) * 2 + 15, 5, 2, c); rect(243 + i * 8, sy(0) * 2 + 15, 5, 2, c);
        }

        // vitrine
        int vx = 80, vy = 64, vw = 192, vh = 84;
        shape(vx - 2, vy - 2, vw + 4, vh + 4, 5, new int[]{BLUE_HI, BLUE, BLUE_LO}, OUT, BLUE_HI2, BLUE_LO2);
        shape(vx + 3, vy + 3, vw - 6, vh - 6, 3, new int[]{0xFF15264F, 0xFF0F1C3D, 0xFF0B142D}, OUT, 0xFF070C1C, 0xFF2A4A90);
        for (int k = 0; k < 2; k++) for (int i = 0; i < 26; i++) {  // reflets diagonaux du verre
            int bx = vx + 14 + k * 104 + i, by = vy + 6 + i / 2;
            px(bx, by, 0xFF1C3166); px(bx + 3, by, 0xFF17295A);
        }
        for (int r = 1; r <= 2; r++) for (int c = 2; c <= 6; c++) slot(sx(c), sy(r));
        bracket(vx - 6, vy - 6, vw + 12, BLUE_HI); bracket2(vx - 6, vy - 6, vw + 12, vh + 12, BLUE_HI);

        // décors latéraux
        planet(46, 110, 20, new int[]{0xFF26185C, 0xFF3E2A99, 0xFF5B44D0, 0xFF7A5CFF, 0xFFA48CFF, 0xFFD5CBFF},
                new int[]{0xFF4E5FA8, BLUE_HI, 0xFFE4EEFF}, -0.38, 11);
        moon(310, 100, 15);
        twinkle(296, 132, 0xFFFFFFFF, BLUE_HI, 0xFF2C3A70);
        twinkle(334, 128, 0xFFFFFFFF, BLUE_HI, 0xFF2C3A70);
        twinkle(286, 76, 0xFFFFFFFF, BLUE_HI, 0xFF2C3A70);
        twinkle(22, 78, 0xFFFFFFFF, BLUE_HI, 0xFF2C3A70);

        // ligne 3 : gros bouton
        button(50, 146, 252, 34, 0xFF6EA2FF, BLUE, 0xFF2F6AE0, BLUE_LO2, "BOUTIQUE EN LIGNE", "gem");

        // lignes 4-5 : catégories
        button(14, 182, 144, 32, 0xFFA48CFF, 0xFF7A5CFF, 0xFF5B44D0, 0xFF2A1A70, "GRADES", "crown");
        button(194, 182, 144, 32, 0xFFFFCB6B, 0xFFF2A93B, 0xFFD3871E, 0xFF7A420A, "CLÉS", "key");
        button(14, 218, 144, 32, 0xFF6FDFA0, 0xFF2FBF71, 0xFF22985A, 0xFF0D4F2C, "PACKS", "box");
        button(194, 218, 144, 32, 0xFF7FEBF7, 0xFF22C7D9, 0xFF169DAD, 0xFF0A5560, "ORBIT+", "orbit");

        // conduit central
        shape(166, 182, 20, 68, 3, METAL, OUT, METAL_HI, METAL_LO);
        rect(172, 188, 8, 56, OUT);
        for (int j = 0; j < 54; j++) for (int i = 0; i < 6; i++) {
            double t = (Math.sin(j * 0.35) + 1) / 2;
            px(173 + i, 189 + j, pick(new int[]{0xFF0C1430, BLUE_LO, BLUE, BLUE_HI}, t * (i == 0 || i == 5 ? 0.6 : 1), 173 + i, 189 + j));
        }
    }
    /** Coins lumineux (haut) d'un cadre. */
    static void bracket(int x, int y, int w, int c) {
        rect(x, y, 6, 2, c); rect(x, y, 2, 6, c);
        rect(x + w - 6, y, 6, 2, c); rect(x + w - 2, y, 2, 6, c);
    }
    static void bracket2(int x, int y, int w, int h, int c) {
        rect(x, y + h - 2, 6, 2, c); rect(x, y + h - 6, 2, 6, c);
        rect(x + w - 6, y + h - 2, 6, 2, c); rect(x + w - 2, y + h - 6, 2, 6, c);
    }
    /** Slot mis en valeur : coins lumineux aux 4 angles. */
    static void accentSlot(int c, int r, int col) {
        slot(sx(c), sy(r));
        int x = sx(c) * S - 4, y = sy(r) * S - 4;
        bracket(x, y, 40, col); bracket2(x, y, 40, 40, col);
    }

    /** Emblème boussole : cadran métal, graduations, aiguille ORBIT inclinée. */
    static void compass(int cx, int cy, int r) {
        dial(cx, cy, r);
        for (int k = 0; k < 8; k++) {   // graduations
            double a = k * Math.PI / 4;
            int len = k % 2 == 0 ? 3 : 1;
            for (int i = 0; i <= len; i++) {
                double rr = r - 7 - i;
                px(cx + (int) Math.round(Math.sin(a) * rr), cy - (int) Math.round(Math.cos(a) * rr), k == 0 ? 0xFFFFFFFF : BLUE_HI);
            }
        }
        double rot = 0.42, L = r - 7.5, cs = Math.cos(rot), sn = Math.sin(rot);
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) {   // aiguille
            double v = x * sn - y * cs, u = x * cs + y * sn;   // v > 0 : vers le nord
            double hw = (L - Math.abs(v)) * 0.32;
            if (hw < 0 || Math.abs(u) > hw + 0.6) continue;
            int c;
            if (Math.abs(u) > hw - 0.6) c = OUT;
            else if (v > 0) c = u < 0 ? BLUE_HI2 : BLUE;
            else c = u < 0 ? METAL_HI : METAL[2];
            px(cx + x, cy + y, c);
        }
        rect(cx - 1, cy - 1, 3, 3, OUT); px(cx, cy, 0xFFB7C1E6);
    }

    // =====================================================================================
    // Navigation (boussole)
    // =====================================================================================
    record Mode(String name, int[] pal, boolean ring) {}
    // Couleurs reprises de config.yml « modes » ; palette du plus foncé au plus clair.
    static final Mode SURVIE = new Mode("SURVIE", new int[]{0xFF0D3B1C, 0xFF17602F, 0xFF23944A, 0xFF3FC46A, 0xFF7CFC9A, 0xFFCBFFD8}, false);
    static final Mode EXTRACTION = new Mode("EXTRACTION", new int[]{0xFF4A0E0E, 0xFF7A1717, 0xFFB32525, 0xFFE23B3B, 0xFFFF6B6B, 0xFFFFC2C2}, true);
    static final Mode PRACTICE = new Mode("PRACTICE", new int[]{0xFF122A6B, 0xFF1C3F99, 0xFF2F6AE0, 0xFF3A7FFF, 0xFF6EA2FF, 0xFFC4DAFF}, true);
    static final Mode MMO = new Mode("MMO", new int[]{0xFF5A3A06, 0xFF8A5A0C, 0xFFC48A17, 0xFFF5C542, 0xFFFFDD7A, 0xFFFFF2C4}, false);

    static final int[][] NAV_ITEMS = {{0, 0, 0xFFE0B894}, {8, 0, 0xFFF5C542},
            {0, 1, 0xFF3FC46A}, {5, 1, 0xFFE23B3B}, {0, 3, 0xFF3A7FFF}, {5, 3, 0xFFF5C542},
            {0, 5, 0xFFB57BFF}, {2, 5, 0xFF5865F2}, {4, 5, 0xFFFF4FD8}, {6, 5, 0xFF22C7D9}, {8, 5, 0xFFE2524A}};

    /** Carte de mode : 4 colonnes x 2 lignes ; slot de l'icône en haut à gauche, planète du mode, plaque du nom. */
    static void modeCard(int c, int r, Mode m, long seed) {
        int[] p = m.pal();
        int x = sx(c) * S - 2, y = sy(r) * S - 2, w = 144, h = 72;
        int[] bg = {mix(p[1], 0xFF0A0A0F, 0.62), mix(p[0], 0xFF0A0A0F, 0.62), mix(p[0], 0xFF0A0A0F, 0.8)};
        shape(x + 1, y + 1, w - 2, h - 2, 5, bg, OUT, mix(p[2], 0xFF0A0A0F, 0.3), mix(p[0], 0xFF000000, 0.5));
        Random rnd = new Random(seed);
        for (int k = 0; k < 14; k++) px(x + 40 + rnd.nextInt(w - 48), y + 5 + rnd.nextInt(32), rnd.nextBoolean() ? mix(p[3], 0xFF0A0A0F, 0.5) : mix(p[5], 0xFF0A0A0F, 0.3));
        twinkle(x + 52, y + 10, 0xFFFFFFFF, p[4], mix(p[2], 0xFF0A0A0F, 0.4));
        planet(x + 104, y + 21, 13, p, m.ring() ? new int[]{p[2], p[4], p[5]} : null, -0.3, seed);
        // slot de l'icône
        slot(sx(c), sy(r));
        bracket(x + 1, y + 1, 36, p[4]); bracket2(x + 1, y + 1, 36, 36, p[4]);
        // plaque du nom (même construction qu'un bouton)
        button(x + 6, y + 40, w - 12, 28, p[4], p[3], p[2], p[0], m.name(), null);
    }

    static void drawNav() {
        frame("NAVIGATION", "compass", "compass");

        // ligne 0 : profil / titre / boutique
        accentSlot(0, 0, BLUE);
        accentSlot(8, 0, 0xFFF5C542);
        textC(176, sy(0) * 2 + 9, "MODES DE JEU", 2, new int[]{0xFFFFFFFF, BLUE_HI2, BLUE_HI}, OUT, 0, 0);
        for (int i = 0; i < 4; i++) {
            int col = i == 0 ? BLUE_HI : i == 1 ? BLUE : i == 2 ? BLUE_LO : 0xFF16244F;
            rect(94 - i * 8, sy(0) * 2 + 15, 5, 2, col); rect(253 + i * 8, sy(0) * 2 + 15, 5, 2, col);
        }

        // lignes 1-4 : les 4 modes en cartes 4x2
        modeCard(0, 1, SURVIE, 21);
        modeCard(5, 1, EXTRACTION, 22);
        modeCard(0, 3, PRACTICE, 23);
        modeCard(5, 3, MMO, 24);

        // colonne centrale : conduit + moyeu lumineux
        shape(166, 70, 20, 144, 3, METAL, OUT, METAL_HI, METAL_LO);
        rect(172, 76, 8, 132, OUT);
        for (int j = 0; j < 130; j++) for (int i = 0; i < 6; i++) {
            double t = (Math.sin(j * 0.3) + 1) / 2;
            px(173 + i, 77 + j, pick(new int[]{0xFF0C1430, BLUE_LO, BLUE, BLUE_HI}, t * (i == 0 || i == 5 ? 0.6 : 1), 173 + i, 77 + j));
        }
        int hy = sy(3) * S - 2;
        disc(176, hy, 11, OUT);
        for (int x = -10; x <= 10; x++) for (int y = -10; y <= 10; y++) {
            double d = Math.sqrt(x * x + y * y);
            if (d <= 10) px(176 + x, hy + y, d > 6.5 ? pick(new int[]{METAL[3], METAL[1], METAL_HI}, 0.5 - 0.5 * (x + y) / 12.0, 176 + x, hy + y)
                    : d > 5.5 ? OUT : pick(new int[]{BLUE_LO, BLUE, BLUE_HI, BLUE_HI2}, 1 - d / 5.5, 176 + x, hy + y));
        }
        px(174, hy - 2, 0xFFFFFFFF);

        // ligne 5 : Station / Discord / Cosmétiques / Site / Fermer, reliés par un rail
        int ry = sy(5) * S + 15;
        rect(10, ry - 1, 332, 4, OUT); rect(10, ry, 332, 1, METAL_HI); rect(10, ry + 1, 332, 1, METAL[2]);
        for (int i = 0; i < 4; i++) { int dx = sx(2 * i + 1) * S + 14; rect(dx, ry - 2, 4, 6, OUT); rect(dx + 1, ry - 1, 2, 4, BLUE); px(dx + 1, ry - 1, BLUE_HI2); }
        accentSlot(0, 5, 0xFFB57BFF);
        accentSlot(2, 5, 0xFF5865F2);
        accentSlot(4, 5, 0xFFFF4FD8);
        accentSlot(6, 5, 0xFF22C7D9);
        accentSlot(8, 5, 0xFFE2524A);
    }

    // =====================================================================================
    // Cosmétiques : accueil (catégories) + une page par catégorie
    // =====================================================================================
    /** Catégorie : id (= CosmeticType.id()), nom affiché en majuscules, palette du plus foncé au plus clair. */
    record Cat(String id, String name, int[] pal) {}
    // Même ordre que l'enum CosmeticType : CosmeticMenu place les catégories dans cet ordre sur les cartes.
    static final Cat[] CATS = {
            new Cat("trail", "TRAÎNÉES", new int[]{0xFF5A2A06, 0xFF8A4A0C, 0xFFC8741A, 0xFFF2A93B, 0xFFFFCB6B, 0xFFFFE9C4}),
            new Cat("aura", "AURAS", new int[]{0xFF4F0F45, 0xFF7E1B6F, 0xFFB52BA0, 0xFFE646C9, 0xFFFF86E6, 0xFFFFD1F5}),
            new Cat("hat", "CHAPEAUX", new int[]{0xFF0A3F47, 0xFF0F6470, 0xFF169DAD, 0xFF22C7D9, 0xFF7FEBF7, 0xFFD2FAFF}),
            new Cat("back", "DOS", new int[]{0xFF26185C, 0xFF3E2A99, 0xFF5B44D0, 0xFF7A5CFF, 0xFFA48CFF, 0xFFD5CBFF}),
            new Cat("balloon", "BALLONS", new int[]{0xFF4A0E0E, 0xFF7A1717, 0xFFB32525, 0xFFE23B3B, 0xFFFF6B6B, 0xFFFFC2C2}),
            new Cat("pet", "FAMILIERS", new int[]{0xFF0D3B1C, 0xFF17602F, 0xFF23944A, 0xFF3FC46A, 0xFF7CFC9A, 0xFFCBFFD8}),
            new Cat("mount", "MONTURES", new int[]{0xFF5A3A06, 0xFF8A5A0C, 0xFFC48A17, 0xFFF5C542, 0xFFFFDD7A, 0xFFFFF2C4})};
    static final int[] STEEL = {0xFF1F2333, 0xFF2A2F45, 0xFF363D58, 0xFF5A647F, 0xFF8792BE, 0xFFC9D2EE};
    static final int[] BLUE_FLOW = {0xFF0C1430, BLUE_LO, BLUE, BLUE_HI};
    static final int PINK = 0xFFFF4FD8, RED = 0xFFE2524A;

    /** Sous-titre de la ligne 0 (entre les deux slots d'angle), tirets dégradés de chaque côté. */
    static void subtitle(String s) {
        int tw = textW(s, 2), y = sy(0) * S;
        textC(176, y + 9, s, 2, new int[]{0xFFFFFFFF, BLUE_HI2, BLUE_HI}, OUT, 0, 0);
        for (int i = 0; i < 4; i++) {
            int col = i == 0 ? BLUE_HI : i == 1 ? BLUE : i == 2 ? BLUE_LO : 0xFF16244F;
            rect(176 - tw / 2 - 11 - i * 8, y + 15, 5, 2, col); rect(176 + tw / 2 + 6 + i * 8, y + 15, 5, 2, col);
        }
    }

    /** Conduit vertical : colonne métal (20 de large) et flux lumineux tramé (couleurs du plus foncé au plus clair). */
    static void conduit(int x, int y, int h, int[] flow) {
        shape(x, y, 20, h, 3, METAL, OUT, METAL_HI, METAL_LO);
        rect(x + 6, y + 6, 8, h - 12, OUT);
        for (int j = 0; j < h - 14; j++) for (int i = 0; i < 6; i++) {
            double t = (Math.sin(j * 0.3) + 1) / 2;
            px(x + 7 + i, y + 7 + j, pick(flow, t * (i == 0 || i == 5 ? 0.6 : 1), x + 7 + i, y + 7 + j));
        }
    }
    /** Moyeu rond sur un conduit : bague métal, cœur lumineux. */
    static void hub(int cx, int cy, int[] core) {
        disc(cx, cy, 11, OUT);
        for (int x = -10; x <= 10; x++) for (int y = -10; y <= 10; y++) {
            double d = Math.sqrt(x * x + y * y);
            if (d <= 10) px(cx + x, cy + y, d > 6.5 ? pick(new int[]{METAL[3], METAL[1], METAL_HI}, 0.5 - 0.5 * (x + y) / 12.0, cx + x, cy + y)
                    : d > 5.5 ? OUT : pick(core, 1 - d / 5.5, cx + x, cy + y));
        }
        px(cx - 2, cy - 2, 0xFFFFFFFF);
    }
    /** Tuyau horizontal (raccord entre une carte et un conduit). */
    static void pipe(int x, int yc, int w) {
        rect(x, yc - 4, w, 8, OUT);
        rect(x, yc - 3, w, 6, METAL[1]); rect(x, yc - 3, w, 1, METAL_HI); rect(x, yc + 2, w, 1, METAL[3]);
    }
    /** Rail de la ligne 5 (passe derrière les slots d'action). */
    static void rail() {
        int ry = sy(5) * S + 15;
        rect(10, ry - 1, 332, 4, OUT); rect(10, ry, 332, 1, METAL_HI); rect(10, ry + 1, 332, 1, METAL[2]);
    }

    /** Étincelle à 4 branches (super-ellipse concave), éclairée en haut à gauche. */
    static void sparkle(int cx, int cy, double R, int[] pal) {
        for (int x = (int) -R - 1; x <= R + 1; x++) for (int y = (int) -R - 1; y <= R + 1; y++) {
            if (!inSpark(x, y, R)) continue;
            boolean edge = !inSpark(x - 1, y, R) || !inSpark(x + 1, y, R) || !inSpark(x, y - 1, R) || !inSpark(x, y + 1, R);
            px(cx + x, cy + y, edge ? OUT : pick(pal, 0.6 - 0.9 * (x + y) / R, cx + x, cy + y));
        }
        px(cx - 1, cy - 1, 0xFFFFFFFF);
    }
    static boolean inSpark(int x, int y, double R) {
        return Math.pow(Math.abs(x) / R, 0.6) + Math.pow(Math.abs(y) / R, 0.6) <= 1.0;
    }
    static Badge sparkBadge() {
        return (cx, cy, r) -> { dial(cx, cy, r); sparkle(cx, cy, r - 7, new int[]{BLUE_LO, BLUE, BLUE_HI, BLUE_HI2, 0xFFFFFFFF}); };
    }
    static Badge catBadge(int[] p) {
        return (cx, cy, r) -> { dial(cx, cy, r); planet(cx, cy, r - 9, p, new int[]{p[2], p[4], p[5]}, -0.32, 4); };
    }

    /**
     * Carte de catégorie : 4 colonnes x 1 ligne à partir de la colonne c. Slot de l'icône à gauche
     * (ou à droite si mirror), plaque du nom sur les 3 autres colonnes.
     */
    static void catCard(int c, int r, String label, int[] p, boolean mirror) {
        int x = sx(c) * S - 2, y = sy(r) * S - 2;
        int slotC = mirror ? c + 3 : c, sxp = sx(slotC) * S - 2;
        int bx = mirror ? x : x + 38, ax = mirror ? x + 106 : x + 36;
        slot(sx(slotC), sy(r));
        bracket(sxp - 2, y - 2, 40, p[4]); bracket2(sxp - 2, y - 2, 40, 40, p[4]);
        rect(ax, y + 12, 2, 12, OUT); rect(ax, y + 13, 2, 10, p[1]); rect(ax, y + 13, 2, 1, p[3]);   // attache
        button(bx, y + 1, 106, 34, p[4], p[3], p[2], p[0], label, null);
    }

    static final int[][] COSMETICS_ITEMS = {{0, 0, PINK}, {8, 0, 0xFFE0B894},
            {0, 1, 0xFFF2A93B}, {8, 1, 0xFFE646C9}, {0, 2, 0xFF22C7D9}, {8, 2, 0xFF7A5CFF},
            {0, 3, 0xFFE23B3B}, {8, 3, 0xFF3FC46A}, {0, 4, 0xFFF5C542}, {8, 4, 0xFFE0E6F5}, {8, 5, RED}};

    static void drawCosmetics() {
        frame("COSMÉTIQUES", sparkBadge(), sparkBadge());
        accentSlot(0, 0, PINK);
        accentSlot(8, 0, BLUE);
        subtitle("CATÉGORIES");

        // conduit central (lignes 1-4) + moyeu
        conduit(166, 70, 144, BLUE_FLOW);
        hub(176, sy(3) * S - 2, new int[]{BLUE_LO, BLUE, BLUE_HI, BLUE_HI2});

        // 7 catégories + « retirer » : gauche (icône col 0, plaque col 1-3), droite en miroir (plaque col 5-7, icône col 8)
        for (int i = 0; i < 8; i++) {
            int r = 1 + i / 2;
            boolean right = i % 2 == 1;
            Cat cat = i < CATS.length ? CATS[i] : null;
            catCard(right ? 5 : 0, r, cat != null ? cat.name() : "RETIRER", cat != null ? cat.pal() : STEEL, right);
            int yc = sy(r) * S - 2 + 17;
            if (right) pipe(186, yc, 8); else pipe(158, yc, 8);
        }

        // ligne 5 : hublot sur l'espace (planète, lune, étoiles) + fermer
        int hy = sy(5) * S + 2;
        planet(62, hy + 14, 12, CATS[1].pal(), new int[]{0xFF4E5FA8, BLUE_HI, 0xFFE4EEFF}, -0.25, 31);
        moon(268, hy + 12, 9);
        planet(176, hy + 18, 6, new int[]{0xFF0A3F47, 0xFF0F6470, 0xFF169DAD, 0xFF22C7D9, 0xFF7FEBF7, 0xFFD2FAFF}, null, 0, 32);
        twinkle(120, hy + 6, 0xFFFFFFFF, BLUE_HI, 0xFF2C3A70);
        twinkle(222, hy + 26, 0xFFFFFFFF, BLUE_HI, 0xFF2C3A70);
        twinkle(14, hy + 26, 0xFFFFFFFF, 0xFFFF86E6, 0xFF4F0F45);
        sparkle(306 - 70, hy + 8, 4, new int[]{BLUE_LO, BLUE, BLUE_HI, BLUE_HI2, 0xFFFFFFFF});
        sparkle(100, hy + 26, 4, new int[]{0xFF7E1B6F, 0xFFE646C9, 0xFFFF86E6, 0xFFFFD1F5, 0xFFFFFFFF});
        accentSlot(8, 5, RED);
    }

    static final int[][] LIST_ITEMS = {{0, 0, 0}, {8, 0, 0xFF9AA4C0},
            {1, 1, 0}, {2, 1, 0}, {3, 1, 0}, {4, 1, 0}, {5, 1, 0}, {6, 1, 0}, {7, 1, 0}, {1, 2, 0}, {2, 2, 0}, {3, 2, 0},
            {4, 5, 0xFFE0E6F5}, {8, 5, 0xFF8DB8FF}};

    /** Page d'une catégorie : vitrine 7 x 4 (28 cosmétiques par page), page précédente / retirer / page suivante. */
    static void drawCosmeticList(Cat cat) {
        int[] p = cat.pal();
        frame(cat.name(), catBadge(p), catBadge(p));

        // vitrine : cadre à la couleur de la catégorie autour des colonnes 1-7, lignes 1-4
        int vx = sx(1) * S - 2, vy = sy(1) * S - 2, vw = 7 * 36, vh = 4 * 36;
        shape(vx - 4, vy - 4, vw + 8, vh + 8, 4, new int[]{p[4], p[3], p[2]}, OUT, p[5], p[1]);
        for (int r = 1; r <= 4; r++) for (int c = 1; c <= 7; c++) slot(sx(c), sy(r));

        // colonnes latérales : conduits à la couleur de la catégorie
        int[] flow = {mix(p[0], 0xFF0A0A0F, 0.5), p[1], p[3], p[4]};
        for (int x : new int[]{20, 312}) {
            conduit(x, vy, vh, flow);
            hub(x + 10, vy + vh / 2, new int[]{p[2], p[3], p[4], p[5]});
        }

        accentSlot(0, 0, p[4]);
        accentSlot(8, 0, BLUE);
        subtitle("COLLECTION");

        // ligne 5 : rail, flèches, précédent / retirer / suivant
        rail();
        int ry = sy(5) * S + 16;
        int[][] fade = {{0xFFFFFFFF, BLUE_HI2, BLUE_HI, BLUE}, {BLUE_HI2, BLUE_HI, BLUE, BLUE_LO}, {BLUE_HI, BLUE, BLUE_LO, BLUE_LO2}};
        for (int k = 0; k < 3; k++) {   // chevrons vers « précédent » (à gauche) et « suivant » (à droite)
            int ax = 58 + k * 14;
            icon(ax, ry - 9, "chevl", OUT, 0xFF060A18, fade[k]);
            icon(340 - ax, ry - 9, "chevr", OUT, 0xFF060A18, fade[k]);
        }
        accentSlot(0, 5, BLUE);
        accentSlot(4, 5, RED);
        accentSlot(8, 5, BLUE);
    }

    // ---------- Aperçu (échelle d'interface 4 : 1 sous-pixel = 2 px écran) ----------
    static final String[] ITEM = {
            "................",
            "......####......",
            ".....#wwhh#.....",
            "....#wwhhhm#....",
            "...#wwhhhmmm#...",
            "..#whhhhmmmmd#..",
            ".#############..",
            ".#hhhhmmmmmmdd#.",
            "..#hhhmmmmmdd#..",
            "...#hhmmmmdd#...",
            "....#hmmmdd#....",
            ".....#hmdd#.....",
            "......#md#......",
            ".......##.......",
            "................",
            "................"};
    static BufferedImage preview(int[][] items) {
        int P = 2;
        BufferedImage tex = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        tex.setData(img.getData());
        BufferedImage out = new BufferedImage(W * P + 80, H * P + 60, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < out.getWidth(); x++) for (int y = 0; y < out.getHeight(); y++) { // monde assombri (blocs 16 texels)
            int bx = x / 64, by = y / 64, tx = (x / 4) % 16, ty = (y / 4) % 16;
            int base = by > 12 ? 0x34502A : 0x3A4A66;
            int v = ((bx * 31 + by * 17 + tx * 7 + ty * 13) % 9) * 3;
            int rr = (((base >> 16) & 255) + v) * 2 / 5, gg = (((base >> 8) & 255) + v) * 2 / 5, bb = ((base & 255) + v) * 2 / 5;
            out.setRGB(x, y, 0xFF000000 | (rr << 16) | (gg << 8) | bb);
        }
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) {
            int c = tex.getRGB(x, y);
            if ((c >>> 24) == 0) continue;
            for (int i = 0; i < P; i++) for (int j = 0; j < P; j++) out.setRGB(40 + x * P + i, 30 + y * P + j, c);
        }
        // objets à la résolution réelle d'un item (16 texels = 16 unités GUI = 4 px écran par texel)
        for (int[] it : items) {
            int x0 = 40 + (OX + sx(it[0]) * S) * P, y0 = 30 + (OY + sy(it[1]) * S) * P;
            for (int r = 0; r < 16; r++) for (int k = 0; k < 16; k++) {
                char ch = ITEM[r].charAt(k);
                if (ch == '.') continue;
                int c = switch (ch) {
                    case '#' -> 0xFF101018; case 'w' -> mix(it[2], 0xFFFFFFFF, 0.6); case 'h' -> mix(it[2], 0xFFFFFFFF, 0.25);
                    case 'm' -> it[2]; default -> mix(it[2], 0xFF000000, 0.35);
                };
                for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) out.setRGB(x0 + k * 4 + i, y0 + r * 4 + j, c);
            }
        }
        return out;
    }

    /**
     * Version pour le pack : un pixel quasi transparent (alpha 1, jeté au rendu) dans la dernière colonne
     * de chaque tuile, pour que le jeu calcule une avance fixe de 99 unités par tuile (196 px x 0,5 + 1).
     */
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

    /** Simule le libellé « Inventaire » que le jeu dessine en #404040 : il doit être invisible sur l'aperçu. */
    static void vanillaLabel() {
        String s = "INVENTAIRE";
        int x = 8 * S, y = 128 * S, cx = 0;
        for (char ch : s.toCharArray()) {
            String[] gl = F.get(ch);
            for (int r = 0; r < 7; r++) for (int k = 0; k < 5; k++)
                if (gl[r].charAt(k) == '#') rect(x + cx + k * S, y + r * S, S, S, 0xFF404040);
            cx += 6 * S;
        }
    }

    /**
     * Premier caractère de chaque texture dans la police orbitdev:gui (8 caractères par texture : 4 rangées x 2 tuiles).
     * Doit correspondre à GuiTextures.Screen côté plugin.
     */
    static final Map<String, Integer> BASES = new LinkedHashMap<>();
    static {
        BASES.put("nav", 0xE200);
        BASES.put("shop", 0xE210);
        BASES.put("cosmetics", 0xE220);
        int base = 0xE230;
        for (String id : new String[]{"trail", "aura", "hat", "back", "balloon", "pet", "mount"}) { BASES.put("cosmetics_" + id, base); base += 0x10; }
    }
    static String u(int cp) { return String.format("\\u%04x", cp); }

    /** Écrit assets/orbitdev/font/gui.json : espaces négatifs + 4 fournisseurs bitmap (un par rangée de tuiles) par texture. */
    static void writeFont(File pack, List<String> names) throws Exception {
        StringBuilder b = new StringBuilder("{\n  \"providers\": [\n");
        b.append("    {\"type\": \"space\", \"advances\": {\"").append(u(0xF000)).append("\": -18, \"").append(u(0xF001))
                .append("\": -1, \"").append(u(0xF002)).append("\": -197}}");
        for (String n : names) {
            int base = BASES.get(n);
            b.append(",\n");
            for (int row = 0; row < 4; row++) {
                b.append("\n    {\"type\": \"bitmap\", \"file\": \"orbitdev:gui/").append(n).append(".png\", \"height\": 62, \"ascent\": ")
                        .append(37 - 62 * row).append(", \"chars\": [");
                for (int k = 0; k < 4; k++) {
                    b.append(k == 0 ? "\"" : ", \"");
                    b.append(k == row ? u(base + row * 2) + u(base + row * 2 + 1) : u(0) + u(0)).append('"');
                }
                b.append("]}").append(row < 3 ? "," : "");
            }
        }
        b.append("\n  ]\n}\n");
        File out = new File(pack, "assets/orbitdev/font/gui.json");
        out.getParentFile().mkdirs();
        java.nio.file.Files.writeString(out.toPath(), b.toString());
    }

    static final List<String> WRITTEN = new ArrayList<>();

    static void write(String name, int[][] items, File apercus, File pack) throws Exception {
        if (!BASES.containsKey(name)) throw new IllegalStateException("texture sans caractères dans BASES : " + name);
        WRITTEN.add(name);
        if (pack != null) {
            File out = new File(pack, "assets/orbitdev/textures/gui/" + name + ".png");
            out.getParentFile().mkdirs();
            ImageIO.write(forPack(), "png", out);
        }
        vanillaLabel();
        ImageIO.write(preview(items), "png", new File(apercus, name + "_gui_apercu.png"));
    }

    public static void main(String[] a) throws Exception {
        File apercus = new File(a.length > 0 ? a[0] : ".");
        File pack = a.length > 1 ? new File(a[1]) : null;
        apercus.mkdirs();
        drawShop();
        write("shop", SHOP_ITEMS, apercus, pack);
        drawNav();
        write("nav", NAV_ITEMS, apercus, pack);
        drawCosmetics();
        write("cosmetics", COSMETICS_ITEMS, apercus, pack);
        for (Cat cat : CATS) {
            drawCosmeticList(cat);
            int[][] items = new int[LIST_ITEMS.length][];
            for (int i = 0; i < items.length; i++)   // couleur 0 = couleur de la catégorie (aperçu seulement)
                items[i] = new int[]{LIST_ITEMS[i][0], LIST_ITEMS[i][1], LIST_ITEMS[i][2] == 0 ? cat.pal()[3 + i % 2] : LIST_ITEMS[i][2]};
            write("cosmetics_" + cat.id(), items, apercus, pack);
        }
        if (pack != null) writeFont(pack, WRITTEN);
        System.out.println("OK " + img.getWidth() + "x" + img.getHeight() + (pack != null ? " + pack" : ""));
    }
}
