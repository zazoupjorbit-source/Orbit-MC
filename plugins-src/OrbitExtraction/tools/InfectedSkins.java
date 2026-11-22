import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Textures des infectés d'Orbit Extraction, dessinées de zéro (aucune image du jeu n'est reprise).
 * Toutes suivent la disposition 64x64 d'un skin de joueur :
 *  - zombie.png / husk.png remplacent la texture des zombies et des husks vanilla (marcheur, traîneur) ;
 *  - les autres sont des skins portés par des entités « mannequin » (runner, rampant, cracheur).
 *
 * Usage : java InfectedSkins.java <dossier_pack> [<dossier_apercus>]
 */
public class InfectedSkins {

    record Part(int u, int v, int w, int h, int d) {
    }

    static final Part HEAD = new Part(0, 0, 8, 8, 8), HAT = new Part(32, 0, 8, 8, 8),
            BODY = new Part(16, 16, 8, 12, 4),
            RARM = new Part(40, 16, 4, 12, 4), LARM = new Part(32, 48, 4, 12, 4),
            RLEG = new Part(0, 16, 4, 12, 4), LLEG = new Part(16, 48, 4, 12, 4);
    static final int TOP = 0, BOTTOM = 1, RIGHT = 2, FRONT = 3, LEFT = 4, BACK = 5;
    static final int NONE = 0, SKIN = 1, CLOTH = 2, HAIR = 3, SHOE = 4, FEATURE = 5;
    static final double[] FACE_LIGHT = {1.08, 0.72, 0.92, 1.0, 0.92, 0.86};

    static final class Look {
        String name;
        long seed;
        int skin, shirt, pants, shoes = 0xFF1E1B1A, hair = 0xFF1A1512, eye = 0xFFDAD6C2, blood = 0xFF6E0F0F, rot = 0xFF4E5A3C;
        int stripe;                 // bande sur les manches / jambes (0 = aucune)
        int sleeve = 12, legLen = 12;
        boolean bald, shirtless, ribs, hood, boils, barefoot, tie, mangledLegs, longHair;
        double bloodAmt = 0.3, tears = 0.3, rotAmt = 0.25;
    }

    static BufferedImage img;
    static int[][] kind, faceOf;
    static double[][] vfrac;
    static Random rnd;

    static int[] rectOf(Part p, int f) {
        return switch (f) {
            case TOP -> new int[]{p.u() + p.d(), p.v(), p.w(), p.d()};
            case BOTTOM -> new int[]{p.u() + p.d() + p.w(), p.v(), p.w(), p.d()};
            case RIGHT -> new int[]{p.u(), p.v() + p.d(), p.d(), p.h()};
            case FRONT -> new int[]{p.u() + p.d(), p.v() + p.d(), p.w(), p.h()};
            case LEFT -> new int[]{p.u() + p.d() + p.w(), p.v() + p.d(), p.d(), p.h()};
            default -> new int[]{p.u() + 2 * p.d() + p.w(), p.v() + p.d(), p.w(), p.h()};
        };
    }

    static void set(int x, int y, int c, int k) {
        img.setRGB(x, y, c);
        kind[x][y] = k;
    }

    /** Pixel (fx, fy) d'une face. */
    static void put(Part p, int f, int fx, int fy, int c, int k) {
        int[] r = rectOf(p, f);
        if (fx >= 0 && fy >= 0 && fx < r[2] && fy < r[3]) {
            set(r[0] + fx, r[1] + fy, c, k);
        }
    }

    static int kindAt(Part p, int f, int fx, int fy) {
        int[] r = rectOf(p, f);
        return fx >= 0 && fy >= 0 && fx < r[2] && fy < r[3] ? kind[r[0] + fx][r[1] + fy] : NONE;
    }

    static void fill(Part p, int f, int c, int k) {
        int[] r = rectOf(p, f);
        for (int i = 0; i < r[2]; i++) {
            for (int j = 0; j < r[3]; j++) {
                set(r[0] + i, r[1] + j, c, k);
                faceOf[r[0] + i][r[1] + j] = f;
                vfrac[r[0] + i][r[1] + j] = f == TOP || f == BOTTOM ? 0 : j / (double) (r[3] - 1);
            }
        }
    }

    static void fillAll(Part p, int c, int k) {
        for (int f = 0; f < 6; f++) {
            fill(p, f, c, k);
        }
    }

    static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static int mul(int c, double k) {
        int r = (int) Math.max(0, Math.min(255, ((c >> 16) & 255) * k));
        int g = (int) Math.max(0, Math.min(255, ((c >> 8) & 255) * k));
        int b = (int) Math.max(0, Math.min(255, (c & 255) * k));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Vêtement sur les rangées [0, rows) des quatre côtés, bord inférieur déchiré. */
    static void clothe(Part p, int rows, int c, int stripe) {
        if (rows <= 0) {
            return;
        }
        fill(p, TOP, c, CLOTH);
        for (int f : new int[]{RIGHT, FRONT, LEFT, BACK}) {
            int[] r = rectOf(p, f);
            for (int i = 0; i < r[2]; i++) {
                int cut = rows >= r[3] ? r[3] : Math.max(1, rows + rnd.nextInt(3) - 1);
                for (int j = 0; j < Math.min(cut, r[3]); j++) {
                    boolean st = stripe != 0 && (f == RIGHT || f == LEFT) && i >= 1 && i <= 2;
                    set(r[0] + i, r[1] + j, st ? stripe : c, CLOTH);
                }
            }
        }
    }

    /** Tache par marche aléatoire, limitée à une face ; only = type de pixel accepté (NONE = tous sauf les yeux). */
    static void blob(Part p, int f, int steps, int c, double strength, int only) {
        int[] r = rectOf(p, f);
        int x = rnd.nextInt(r[2]), y = rnd.nextInt(r[3]);
        for (int s = 0; s < steps; s++) {
            int k = kind[r[0] + x][r[1] + y];
            if (k != NONE && k != FEATURE && (only == NONE || k == only)) {
                int px = img.getRGB(r[0] + x, r[1] + y);
                img.setRGB(r[0] + x, r[1] + y, mix(px, c, strength * (0.7 + rnd.nextDouble() * 0.3)));
            }
            switch (rnd.nextInt(4)) {
                case 0 -> x = Math.min(r[2] - 1, x + 1);
                case 1 -> x = Math.max(0, x - 1);
                case 2 -> y = Math.min(r[3] - 1, y + 1);
                default -> y = Math.max(0, y - 1);
            }
        }
    }

    static BufferedImage paint(Look k) {
        img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        kind = new int[64][64];
        faceOf = new int[64][64];
        vfrac = new double[64][64];
        rnd = new Random(k.seed);
        Part[] limbs = {RARM, LARM, RLEG, LLEG};

        // 1. peau partout
        for (Part p : new Part[]{HEAD, BODY, RARM, LARM, RLEG, LLEG}) {
            fillAll(p, k.skin, SKIN);
        }

        // 2. vêtements
        int waist = mul(k.pants, 0.8);
        if (!k.shirtless) {
            clothe(BODY, 12, k.shirt, 0);
        }
        for (int f : new int[]{RIGHT, FRONT, LEFT, BACK}) {       // ceinture / haut du pantalon
            int[] r = rectOf(BODY, f);
            for (int i = 0; i < r[2]; i++) {
                int from = k.shirtless ? 10 : 11 - (rnd.nextInt(4) == 0 ? 1 : 0);
                for (int j = from; j < 12; j++) {
                    set(r[0] + i, r[1] + j, j == from ? waist : k.pants, CLOTH);
                }
            }
        }
        fill(BODY, BOTTOM, k.pants, CLOTH);
        clothe(RARM, k.sleeve, k.shirt, k.stripe);
        clothe(LARM, k.sleeve, k.shirt, k.stripe);
        clothe(RLEG, k.legLen, k.pants, k.stripe);
        clothe(LLEG, k.legLen, k.pants, k.stripe);
        for (Part leg : new Part[]{RLEG, LLEG}) {
            if (k.barefoot) {
                fill(leg, BOTTOM, mul(k.skin, 0.7), SKIN);
                continue;
            }
            fill(leg, BOTTOM, mul(k.shoes, 0.7), SHOE);
            for (int f : new int[]{RIGHT, FRONT, LEFT, BACK}) {
                for (int i = 0; i < 4; i++) {
                    put(leg, f, i, 10, k.shoes, SHOE);
                    put(leg, f, i, 11, mul(k.shoes, 0.75), SHOE);
                }
            }
            put(leg, FRONT, 1, 10, mul(k.shoes, 1.5), SHOE);          // lacet
            put(leg, FRONT, 2, 10, mul(k.shoes, 1.5), SHOE);
        }
        if (k.tie && !k.shirtless) {
            for (int j = 0; j < 7; j++) {
                put(BODY, FRONT, 3 + (j > 4 ? 1 : 0), j, 0xFF7A1E1E, CLOTH);
                if (j < 5) {
                    put(BODY, FRONT, 4, j, 0xFF5E1515, CLOTH);
                }
            }
            put(BODY, FRONT, 2, 0, mul(k.shirt, 1.12), CLOTH);       // col
            put(BODY, FRONT, 5, 0, mul(k.shirt, 1.12), CLOTH);
        }

        // 3. tête : cheveux ou capuche
        int cover = k.hood ? k.shirt : k.hair;
        int coverKind = k.hood ? CLOTH : HAIR;
        if (k.hood || !k.bald) {
            fill(HEAD, TOP, cover, coverKind);
            for (int f : new int[]{RIGHT, LEFT, BACK}) {
                int[] r = rectOf(HEAD, f);
                int depth = k.hood ? 8 : f == BACK ? (k.longHair ? 8 : 4) : (k.longHair ? 6 : 3);
                for (int i = 0; i < r[2]; i++) {
                    int d = k.hood ? 8 : depth + rnd.nextInt(2) - (rnd.nextInt(3) == 0 ? 1 : 0);
                    for (int j = 0; j < Math.min(8, d); j++) {
                        set(r[0] + i, r[1] + j, cover, coverKind);
                    }
                }
            }
            for (int i = 0; i < 8; i++) {                           // frange / bord de capuche
                put(HEAD, FRONT, i, 0, cover, coverKind);
                if (k.hood || rnd.nextInt(3) != 0) {
                    put(HEAD, FRONT, i, 1, k.hood && i > 0 && i < 7 ? mul(cover, 0.7) : cover, coverKind);
                }
            }
            if (k.hood) {
                for (int j = 0; j < 8; j++) {
                    put(HEAD, FRONT, 0, j, cover, coverKind);
                    put(HEAD, FRONT, 7, j, cover, coverKind);
                }
                for (int i = 0; i < 8; i++) {
                    put(HEAD, FRONT, i, 7, cover, coverKind);
                }
            }
        } else {
            for (int n = 0; n < 4; n++) {                           // crâne nu : plaies
                blob(HEAD, TOP, 6, k.blood, 0.6, SKIN);
            }
        }

        // 4. visage (face avant 8x8)
        int socket = mul(k.skin, 0.55), dark = 0xFF1A0808;
        for (int i : new int[]{1, 2, 5, 6}) {
            put(HEAD, FRONT, i, 3, socket, SKIN);
            put(HEAD, FRONT, i, 4, k.eye, FEATURE);
            put(HEAD, FRONT, i, 5, mul(k.skin, 0.78), SKIN);
        }
        put(HEAD, FRONT, 2, 4, mul(k.eye, 0.55), FEATURE);           // pupille voilée
        put(HEAD, FRONT, 5, 4, mul(k.eye, 0.55), FEATURE);
        put(HEAD, FRONT, 3, 5, mul(k.skin, 0.82), SKIN);             // nez
        put(HEAD, FRONT, 4, 5, mul(k.skin, 0.82), SKIN);
        if (!k.hood) {
            for (int i = 2; i <= 5; i++) {
                put(HEAD, FRONT, i, 6, dark, FEATURE);               // bouche ouverte
                if (rnd.nextInt(3) != 0) {
                    put(HEAD, FRONT, i, 7, mix(k.skin, k.blood, 0.8), SKIN);
                }
            }
            put(HEAD, FRONT, 3, 6, 0xFFCFC7A8, FEATURE);             // dents
            put(HEAD, FRONT, 1 + rnd.nextInt(2), 6, mix(k.skin, k.blood, 0.6), SKIN);
        } else {
            for (int i = 2; i <= 5; i++) {
                put(HEAD, FRONT, i, 6, dark, FEATURE);
            }
            put(HEAD, FRONT, 3, 6, k.blood, FEATURE);                // bave
            put(HEAD, FRONT, 4, 6, mul(k.blood, 1.2), FEATURE);
        }

        // 5. côtes et colonne (torse nu)
        if (k.shirtless && k.ribs) {
            int hi = mul(k.skin, 1.16), lo = mul(k.skin, 0.66);
            for (int j = 1; j < 9; j += 2) {
                for (int i = 0; i < 8; i++) {
                    if (i == 3 || i == 4) {
                        continue;
                    }
                    put(BODY, FRONT, i, j, hi, SKIN);
                    put(BODY, FRONT, i, j + 1, lo, SKIN);
                }
            }
            for (int j = 0; j < 10; j++) {
                put(BODY, FRONT, 3, j, lo, SKIN);
                put(BODY, BACK, 3, j, j % 2 == 0 ? hi : lo, SKIN);  // vertèbres
                put(BODY, BACK, 4, j, j % 2 == 0 ? hi : lo, SKIN);
                if (j % 2 == 1) {
                    put(BODY, BACK, 1, j, lo, SKIN);
                    put(BODY, BACK, 2, j, lo, SKIN);
                    put(BODY, BACK, 5, j, lo, SKIN);
                    put(BODY, BACK, 6, j, lo, SKIN);
                }
            }
        }

        // 6. déchirures : la peau apparaît sous le tissu
        List<Part> clothed = new ArrayList<>(List.of(BODY, RARM, LARM, RLEG, LLEG));
        int holes = (int) Math.round(k.tears * 14);
        for (int n = 0; n < holes; n++) {
            Part p = clothed.get(rnd.nextInt(clothed.size()));
            int f = new int[]{FRONT, BACK, RIGHT, LEFT}[rnd.nextInt(4)];
            int[] r = rectOf(p, f);
            int x = rnd.nextInt(r[2]), y = rnd.nextInt(Math.max(1, r[3] - 2));
            int len = 1 + rnd.nextInt(3);
            for (int j = 0; j < len && y + j < r[3]; j++) {
                if (kind[r[0] + x][r[1] + y + j] == CLOTH) {
                    set(r[0] + x, r[1] + y + j, j == 0 ? mul(k.skin, 0.6) : mix(k.skin, k.blood, 0.35), SKIN);
                }
            }
        }

        // 7. pourriture, pustules, sang
        Part[] all = {HEAD, BODY, RARM, LARM, RLEG, LLEG};
        int[] sides = {FRONT, BACK, RIGHT, LEFT, TOP};
        for (int n = 0; n < (int) (k.rotAmt * 40); n++) {
            blob(all[rnd.nextInt(all.length)], sides[rnd.nextInt(sides.length)], 4 + rnd.nextInt(5), k.rot, 0.45, SKIN);
        }
        if (k.boils) {
            for (int n = 0; n < 26; n++) {
                Part p = all[rnd.nextInt(all.length)];
                int f = sides[rnd.nextInt(4)];
                int[] r = rectOf(p, f);
                int x = r[0] + rnd.nextInt(r[2]), y = r[1] + rnd.nextInt(r[3]);
                if (kind[x][y] == SKIN) {
                    img.setRGB(x, y, 0xFFD6D25A);
                }
            }
        }
        for (int n = 0; n < (int) (k.bloodAmt * 30); n++) {
            Part p = n % 3 == 0 ? BODY : all[rnd.nextInt(all.length)];
            blob(p, n % 3 == 0 ? FRONT : sides[rnd.nextInt(4)], 5 + rnd.nextInt(8), k.blood, 0.75, NONE);
        }
        for (Part arm : new Part[]{RARM, LARM}) {                   // mains souillées
            for (int f : new int[]{RIGHT, FRONT, LEFT, BACK}) {
                for (int i = 0; i < 4; i++) {
                    for (int j = 9; j < 12; j++) {
                        if (kindAt(arm, f, i, j) == SKIN && rnd.nextDouble() < 0.35 + k.bloodAmt * 0.6) {
                            int[] r = rectOf(arm, f);
                            img.setRGB(r[0] + i, r[1] + j, mix(img.getRGB(r[0] + i, r[1] + j), k.blood, 0.7));
                        }
                    }
                }
            }
        }
        if (k.mangledLegs) {
            for (Part leg : new Part[]{RLEG, LLEG}) {
                for (int n = 0; n < 10; n++) {
                    blob(leg, sides[rnd.nextInt(4)], 8, k.blood, 0.85, NONE);
                }
            }
            for (int f : new int[]{RIGHT, FRONT, LEFT, BACK}) {     // moignon : os à nu au bout de la jambe gauche
                for (int i = 0; i < 4; i++) {
                    put(LLEG, f, i, 9, 0xFF3A0808, SKIN);
                    put(LLEG, f, i, 10, i == 1 || i == 2 ? 0xFFD8CFB4 : 0xFF520C0C, SKIN);
                    put(LLEG, f, i, 11, i == 1 || i == 2 ? 0xFFBEB497 : 0xFF2C0606, SKIN);
                }
            }
        }
        for (int n = 0; n < 3; n++) {                               // coulures sous la bouche
            int x = 2 + rnd.nextInt(4);
            for (int j = 0; j < 1 + rnd.nextInt(4); j++) {
                int[] r = rectOf(BODY, FRONT);
                img.setRGB(r[0] + x, r[1] + j, mix(img.getRGB(r[0] + x, r[1] + j), k.blood, 0.8));
            }
        }

        // 8. grain et volume
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                if (kind[x][y] == NONE) {
                    continue;
                }
                double f = FACE_LIGHT[faceOf[x][y]] * (1.0 - 0.10 * vfrac[x][y]) * (1 + (rnd.nextDouble() - 0.5) * 0.11);
                if (kind[x][y] == FEATURE) {
                    f = 1;
                }
                img.setRGB(x, y, mul(img.getRGB(x, y), f));
            }
        }

        // 9. calque du chapeau : quelques mèches (reste transparent ailleurs)
        if (!k.bald && !k.hood) {
            int[] r = rectOf(HAT, FRONT);
            for (int i = 0; i < 8; i++) {
                int len = k.longHair ? 2 + rnd.nextInt(4) : rnd.nextInt(3);
                for (int j = 0; j < len; j++) {
                    if (j < 2 || i < 2 || i > 5) {
                        img.setRGB(r[0] + i, r[1] + j, mul(k.hair, 0.9 + rnd.nextDouble() * 0.2));
                    }
                }
            }
        }
        return img;
    }

    // ------------------------------------------------------------------ aperçu (face / dos)

    static void blit(BufferedImage dst, BufferedImage src, Part p, int f, int dx, int dy, int scale, boolean mirror) {
        int[] r = rectOf(p, f);
        for (int i = 0; i < r[2]; i++) {
            for (int j = 0; j < r[3]; j++) {
                int c = src.getRGB(r[0] + (mirror ? r[2] - 1 - i : i), r[1] + j);
                if ((c >>> 24) == 0) {
                    continue;
                }
                for (int a = 0; a < scale; a++) {
                    for (int b = 0; b < scale; b++) {
                        dst.setRGB(dx + i * scale + a, dy + j * scale + b, c);
                    }
                }
            }
        }
    }

    static void figure(BufferedImage dst, BufferedImage skin, int x, int y, int s, boolean vanilla) {
        Part la = vanilla ? RARM : LARM, ll = vanilla ? RLEG : LLEG;
        // face
        blit(dst, skin, HEAD, FRONT, x + 4 * s, y, s, false);
        blit(dst, skin, HAT, FRONT, x + 4 * s, y, s, false);
        blit(dst, skin, BODY, FRONT, x + 4 * s, y + 8 * s, s, false);
        blit(dst, skin, RARM, FRONT, x, y + 8 * s, s, false);
        blit(dst, skin, la, FRONT, x + 12 * s, y + 8 * s, s, vanilla);
        blit(dst, skin, RLEG, FRONT, x + 4 * s, y + 20 * s, s, false);
        blit(dst, skin, ll, FRONT, x + 8 * s, y + 20 * s, s, vanilla);
        // dos
        int bx = x + 20 * s;
        blit(dst, skin, HEAD, BACK, bx + 4 * s, y, s, false);
        blit(dst, skin, BODY, BACK, bx + 4 * s, y + 8 * s, s, false);
        blit(dst, skin, la, BACK, bx, y + 8 * s, s, vanilla);
        blit(dst, skin, RARM, BACK, bx + 12 * s, y + 8 * s, s, false);
        blit(dst, skin, ll, BACK, bx + 4 * s, y + 20 * s, s, vanilla);
        blit(dst, skin, RLEG, BACK, bx + 8 * s, y + 20 * s, s, false);
    }

    static Look look(String name, long seed, int skin, int shirt, int pants) {
        Look k = new Look();
        k.name = name;
        k.seed = seed;
        k.skin = skin;
        k.shirt = shirt;
        k.pants = pants;
        return k;
    }

    public static void main(String[] a) throws Exception {
        File pack = new File(a[0]);
        File previews = a.length > 1 ? new File(a[1]) : null;
        List<Look> looks = new ArrayList<>();
        List<String> paths = new ArrayList<>();

        // Marcheur : remplace le zombie vanilla. Civil en chemise sale, peau gris-vert, yeux voilés.
        Look k = look("marcheur", 11, 0xFF8C9A7C, 0xFF4B5968, 0xFF3F3629);
        k.sleeve = 5;
        k.tears = 0.5;
        k.bloodAmt = 0.35;
        looks.add(k);
        paths.add("assets/minecraft/textures/entity/zombie/zombie.png");

        // Traîneur : remplace le husk vanilla. Employé de bureau, chemise blanche trempée de sang, yeux rouges.
        k = look("traineur", 23, 0xFFA59C8E, 0xFFBDB7A6, 0xFF2D3138);
        k.tie = true;
        k.eye = 0xFFD2331F;
        k.bloodAmt = 0.75;
        k.tears = 0.35;
        k.hair = 0xFF3A3028;
        looks.add(k);
        paths.add("assets/minecraft/textures/entity/zombie/husk.png");

        // Runners : infectés récents, encore vifs.
        k = look("runner_1", 31, 0xFFB59E8E, 0xFF8E2A22, 0xFF24262B);
        k.stripe = 0xFFD9D4C8;
        k.eye = 0xFFFF4A32;
        k.bloodAmt = 0.5;
        k.rotAmt = 0.12;
        k.hair = 0xFF4A3424;
        k.shoes = 0xFFCFCBC0;
        looks.add(k);
        paths.add("assets/orbitguns/textures/entity/infecte/runner_1.png");

        k = look("runner_2", 32, 0xFFA8917F, 0xFFC9C5B8, 0xFF2F4A7A);
        k.sleeve = 0;
        k.legLen = 6;
        k.eye = 0xFFFF4A32;
        k.bloodAmt = 0.55;
        k.rotAmt = 0.2;
        k.shoes = 0xFF3C3C40;
        looks.add(k);
        paths.add("assets/orbitguns/textures/entity/infecte/runner_2.png");

        k = look("runner_3", 33, 0xFF9CA291, 0xFFC8641E, 0xFFC8641E);
        k.bald = true;
        k.eye = 0xFFFFD23A;
        k.bloodAmt = 0.4;
        k.tears = 0.45;
        looks.add(k);
        paths.add("assets/orbitguns/textures/entity/infecte/runner_3.png");

        // Rampants : décharnés, jambes broyées. Vus surtout de dos et du dessus.
        k = look("rampant_1", 41, 0xFF8D9085, 0xFF000000, 0xFF2B2A28);
        k.shirtless = true;
        k.ribs = true;
        k.bald = true;
        k.sleeve = 0;
        k.legLen = 5;
        k.barefoot = true;
        k.mangledLegs = true;
        k.bloodAmt = 0.45;
        k.rotAmt = 0.45;
        looks.add(k);
        paths.add("assets/orbitguns/textures/entity/infecte/rampant_1.png");

        k = look("rampant_2", 42, 0xFFAAA497, 0xFF8FB3A8, 0xFF8FB3A8);
        k.sleeve = 3;
        k.legLen = 2;
        k.barefoot = true;
        k.mangledLegs = true;
        k.longHair = true;
        k.hair = 0xFF16110F;
        k.bloodAmt = 0.6;
        k.tears = 0.6;
        looks.add(k);
        paths.add("assets/orbitguns/textures/entity/infecte/rampant_2.png");

        // Cracheur : combinaison de décontamination, peau boursouflée, bave acide.
        k = look("cracheur_1", 51, 0xFF7E9C4C, 0xFFC9A91E, 0xFFC9A91E);
        k.hood = true;
        k.boils = true;
        k.stripe = 0xFF2A2A2A;
        k.eye = 0xFFE8F06A;
        k.blood = 0xFF6FB02A;
        k.rot = 0xFF4F6B2A;
        k.bloodAmt = 0.45;
        k.tears = 0.4;
        k.shoes = 0xFF1C1C1C;
        looks.add(k);
        paths.add("assets/orbitguns/textures/entity/infecte/cracheur_1.png");

        int s = 6;
        BufferedImage sheet = new BufferedImage(looks.size() * 40 * s, 36 * s, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < sheet.getWidth(); x++) {
            for (int y = 0; y < sheet.getHeight(); y++) {
                sheet.setRGB(x, y, 0xFF3A4048);
            }
        }
        for (int i = 0; i < looks.size(); i++) {
            BufferedImage skin = paint(looks.get(i));
            File out = new File(pack, paths.get(i));
            out.getParentFile().mkdirs();
            ImageIO.write(skin, "png", out);
            figure(sheet, skin, i * 40 * s + 2 * s, 2 * s, s, paths.get(i).contains("/minecraft/"));
        }
        if (previews != null) {
            previews.mkdirs();
            ImageIO.write(sheet, "png", new File(previews, "infectes_apercu.png"));
        }
        System.out.println("OK " + looks.size() + " textures d'infectes");
    }
}
