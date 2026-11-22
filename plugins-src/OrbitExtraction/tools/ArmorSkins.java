import java.awt.Color;
import java.awt.Font;
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
 * Tenues d'Orbit Extraction, dessinées de zéro (aucune image du jeu n'est reprise) : cinq niveaux de protection,
 * de la tenue bricolée à l'armure lourde. Pour chaque tenue :
 *  - textures portées 64x32 (calque « humanoid » : casque, plastron, bottes ; « humanoid_leggings » : jambières) ;
 *  - définition d'équipement assets/orbitguns/equipment/&lt;tenue&gt;.json (composant equippable.asset_id) ;
 *  - quatre icônes 16x16 et leurs modèles d'objet « armor_&lt;tenue&gt;_&lt;pièce&gt; ».
 *
 * Usage : java ArmorSkins.java <dossier_pack> [<dossier_apercus>]
 */
public class ArmorSkins {

    record Part(int u, int v, int w, int h, int d) {
    }

    // Disposition d'une texture d'armure : le bras et la jambe gauches reprennent ceux de droite en miroir.
    static final Part HEAD = new Part(0, 0, 8, 8, 8), BODY = new Part(16, 16, 8, 12, 4),
            ARM = new Part(40, 16, 4, 12, 4), LEG = new Part(0, 16, 4, 12, 4);
    static final int TOP = 0, BOTTOM = 1, RIGHT = 2, FRONT = 3, LEFT = 4, BACK = 5;
    static final double[] FACE_LIGHT = {1.10, 0.70, 0.90, 1.0, 0.90, 0.84};
    static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};

    static BufferedImage img;          // calque en cours
    static boolean[][] flat;           // pixels lumineux : pas d'ombrage
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

    static void px(Part p, int f, int x, int y, int c) {
        int[] r = rectOf(p, f);
        if (x >= 0 && y >= 0 && x < r[2] && y < r[3]) {
            img.setRGB(r[0] + x, r[1] + y, c);
            flat[r[0] + x][r[1] + y] = false;
        }
    }

    static void glow(Part p, int f, int x, int y, int c) {
        px(p, f, x, y, c);
        int[] r = rectOf(p, f);
        flat[r[0] + x][r[1] + y] = true;
    }

    static void rect(Part p, int f, int x0, int y0, int x1, int y1, int c) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                px(p, f, x, y, c);
            }
        }
    }

    static void erase(Part p, int f, int x0, int y0, int x1, int y1) {
        rect(p, f, x0, y0, x1, y1, 0);
    }

    static void top(Part p, int c) {
        rect(p, TOP, 0, 0, p.w() - 1, p.d() - 1, c);
    }

    static void bottom(Part p, int c) {
        rect(p, BOTTOM, 0, 0, p.w() - 1, p.d() - 1, c);
    }

    /** Rangées [y0, y1] des quatre côtés. */
    static void wrap(Part p, int y0, int y1, int c) {
        for (int f : new int[]{RIGHT, FRONT, LEFT, BACK}) {
            int[] r = rectOf(p, f);
            rect(p, f, 0, y0, r[2] - 1, y1, c);
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

    /** Taches de camouflage sur les pixels de la couleur de base. */
    static void camo(Part p, int base, int n, int... colors) {
        int[] faces = {TOP, RIGHT, FRONT, LEFT, BACK};
        for (int i = 0; i < n; i++) {
            int f = faces[rnd.nextInt(faces.length)];
            int[] r = rectOf(p, f);
            int x = rnd.nextInt(r[2]), y = rnd.nextInt(r[3]);
            int c = colors[rnd.nextInt(colors.length)];
            for (int s = 0; s < 3 + rnd.nextInt(4); s++) {
                if (img.getRGB(r[0] + x, r[1] + y) == base) {
                    img.setRGB(r[0] + x, r[1] + y, c);
                }
                if (rnd.nextBoolean()) {
                    x = Math.max(0, Math.min(r[2] - 1, x + rnd.nextInt(3) - 1));
                } else {
                    y = Math.max(0, Math.min(r[3] - 1, y + rnd.nextInt(3) - 1));
                }
            }
        }
    }

    /** Usure : quelques pixels assombris ou éclaircis. */
    static void wear(Part p, int n, double k) {
        int[] faces = {TOP, RIGHT, FRONT, LEFT, BACK};
        for (int i = 0; i < n; i++) {
            int[] r = rectOf(p, faces[rnd.nextInt(faces.length)]);
            int x = r[0] + rnd.nextInt(r[2]), y = r[1] + rnd.nextInt(r[3]);
            if ((img.getRGB(x, y) >>> 24) != 0 && !flat[x][y]) {
                img.setRGB(x, y, mul(img.getRGB(x, y), k));
            }
        }
    }

    static BufferedImage layer() {
        img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        flat = new boolean[64][32];
        return img;
    }

    /** Volume : chaque face a sa lumière, léger dégradé vers le bas, grain. */
    static void shade() {
        for (Part p : new Part[]{HEAD, BODY, ARM, LEG}) {
            for (int f = 0; f < 6; f++) {
                int[] r = rectOf(p, f);
                for (int i = 0; i < r[2]; i++) {
                    for (int j = 0; j < r[3]; j++) {
                        int x = r[0] + i, y = r[1] + j;
                        if ((img.getRGB(x, y) >>> 24) == 0 || flat[x][y]) {
                            continue;
                        }
                        double v = f == TOP || f == BOTTOM ? 0 : j / (double) (r[3] - 1);
                        img.setRGB(x, y, mul(img.getRGB(x, y), FACE_LIGHT[f] * (1 - 0.09 * v) * (1 + (rnd.nextDouble() - 0.5) * 0.09)));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ les cinq tenues

    /** Niveau 1 — tenue de fortune : casque de chantier, veste de cuir, panneau de signalisation, pneu, ruban adhésif. */
    static BufferedImage[] fortune() {
        int leather = 0xFF6B4A2F, dark = 0xFF4A3220, tape = 0xFFA9A9A4, rust = 0xFF8A5A3A, yel = 0xFFD2A51E, yelD = 0xFFA87F14,
                red = 0xFFB3261E, white = 0xFFE2DED2, tire = 0xFF26262A, tread = 0xFF45454C, cloth = 0xFF5A4B3A, card = 0xFFB89B6A,
                boot = 0xFF4A3524;
        BufferedImage l1 = layer();
        // casque de chantier rayé, jugulaire en cuir
        top(HEAD, yel);
        rect(HEAD, TOP, 3, 0, 4, 7, mul(yel, 1.18));
        wrap(HEAD, 0, 2, yel);
        wrap(HEAD, 3, 3, yelD);
        rect(HEAD, RIGHT, 1, 0, 2, 2, tape);
        rect(HEAD, BACK, 5, 1, 6, 1, tape);
        for (int y = 4; y < 8; y++) {
            px(HEAD, RIGHT, 5, y, dark);
            px(HEAD, LEFT, 2, y, dark);
        }
        wear(HEAD, 14, 0.78);
        // veste de cuir, panneau « sens interdit » sanglé devant, tôle rivetée dans le dos
        wrap(BODY, 0, 10, leather);
        top(BODY, dark);
        erase(BODY, FRONT, 3, 0, 4, 0);
        rect(BODY, FRONT, 1, 2, 6, 7, red);
        rect(BODY, FRONT, 2, 4, 5, 5, white);
        rect(BODY, BACK, 1, 2, 6, 8, rust);
        for (int[] c : new int[][]{{1, 2}, {6, 2}, {1, 8}, {6, 8}}) {
            px(BODY, BACK, c[0], c[1], mul(rust, 1.5));
        }
        for (int y : new int[]{3, 7}) {
            rect(BODY, RIGHT, 0, y, 3, y, tape);
            rect(BODY, LEFT, 0, y, 3, y, tape);
            rect(BODY, BACK, 0, y, 7, y, tape);
            px(BODY, FRONT, 0, y, tape);
            px(BODY, FRONT, 7, y, tape);
        }
        for (int x = 0; x < 8; x++) {                       // ourlet déchiré
            if (rnd.nextInt(3) == 0) {
                erase(BODY, FRONT, x, 10, x, 10);
            }
            if (rnd.nextInt(3) == 0) {
                erase(BODY, BACK, x, 10, x, 10);
            }
        }
        // épaulière taillée dans un pneu, manche de cuir, avant-bras bandé
        top(ARM, tire);
        wrap(ARM, 0, 3, tire);
        for (int f : new int[]{RIGHT, FRONT, BACK}) {
            for (int x = 0; x < 4; x++) {
                px(ARM, f, x, x % 2 == 0 ? 1 : 2, tread);
            }
        }
        wrap(ARM, 4, 6, leather);
        wrap(ARM, 8, 9, tape);
        px(ARM, RIGHT, 1, 8, mul(tape, 0.75));
        px(ARM, FRONT, 2, 9, mul(tape, 0.75));
        // bottes usées, tour de ruban
        wrap(LEG, 7, 11, boot);
        wrap(LEG, 8, 8, tape);
        wrap(LEG, 11, 11, mul(boot, 0.6));
        bottom(LEG, mul(boot, 0.5));
        wear(BODY, 16, 0.8);
        wear(LEG, 6, 0.8);
        shade();

        BufferedImage l2 = layer();
        wrap(BODY, 9, 11, cloth);
        wrap(BODY, 9, 9, card);                              // corde en guise de ceinture
        px(BODY, FRONT, 3, 10, card);
        wrap(LEG, 0, 11, cloth);
        wrap(LEG, 3, 3, tape);
        wrap(LEG, 7, 7, tape);
        rect(LEG, FRONT, 0, 4, 3, 6, card);                  // genouillère en carton
        rect(LEG, RIGHT, 2, 4, 3, 6, card);
        px(LEG, BACK, 1, 9, 0xFF3D4F66);                     // pièce de jean
        px(LEG, BACK, 2, 9, 0xFF3D4F66);
        px(LEG, BACK, 2, 10, 0xFF3D4F66);
        wear(LEG, 12, 0.8);
        shade();
        return new BufferedImage[]{l1, l2};
    }

    /** Niveau 2 — tenue de patrouille : casque léger à damier, gilet pare-balles marine sur chemisette. */
    static BufferedImage[] police() {
        int navy = 0xFF22304E, navyD = 0xFF172038, shirt = 0xFF7FA0C8, black = 0xFF17181C, gold = 0xFFD9B23A, white = 0xFFE8E8E8;
        BufferedImage l1 = layer();
        top(HEAD, navy);
        wrap(HEAD, 0, 4, navy);
        erase(HEAD, FRONT, 0, 3, 7, 4);
        for (int f : new int[]{RIGHT, FRONT, LEFT, BACK}) {  // bande à damier
            for (int x = 0; x < 8; x++) {
                px(HEAD, f, x, 2, x % 2 == 0 ? white : black);
            }
        }
        rect(HEAD, FRONT, 3, 0, 4, 1, gold);
        px(HEAD, FRONT, 3, 1, mul(gold, 0.75));
        // gilet
        wrap(BODY, 0, 10, navy);
        top(BODY, navyD);
        px(BODY, FRONT, 3, 0, shirt);
        px(BODY, FRONT, 4, 0, shirt);
        rect(BODY, FRONT, 4, 2, 6, 2, white);
        px(BODY, FRONT, 1, 2, gold);
        px(BODY, FRONT, 1, 3, mul(gold, 0.75));
        px(BODY, FRONT, 6, 0, black);                        // radio d'épaule
        px(BODY, FRONT, 6, 1, black);
        rect(BODY, FRONT, 1, 8, 2, 9, black);
        rect(BODY, FRONT, 5, 8, 6, 9, black);
        rect(BODY, BACK, 1, 2, 6, 3, white);
        px(BODY, BACK, 2, 3, navy);
        px(BODY, BACK, 4, 2, navy);
        px(BODY, BACK, 5, 3, navy);
        for (int f : new int[]{RIGHT, LEFT}) {
            rect(BODY, f, 0, 5, 3, 6, black);                // sangles latérales
        }
        wrap(BODY, 10, 10, navyD);
        // chemisette, écusson d'épaule
        top(ARM, shirt);
        wrap(ARM, 0, 4, shirt);
        wrap(ARM, 4, 4, mul(shirt, 0.8));
        rect(ARM, RIGHT, 1, 1, 2, 2, navy);
        px(ARM, RIGHT, 1, 1, gold);
        // rangers noires
        wrap(LEG, 7, 11, black);
        wrap(LEG, 7, 7, mul(black, 1.7));
        px(LEG, FRONT, 1, 9, mul(black, 2.0));
        px(LEG, FRONT, 2, 9, mul(black, 2.0));
        bottom(LEG, black);
        shade();

        BufferedImage l2 = layer();
        wrap(BODY, 9, 11, navy);
        wrap(BODY, 9, 9, black);
        px(BODY, FRONT, 3, 9, gold);
        px(BODY, FRONT, 4, 9, gold);
        wrap(LEG, 0, 11, navy);
        for (int y = 0; y < 12; y++) {
            px(LEG, RIGHT, 2, y, navyD);
        }
        rect(LEG, RIGHT, 1, 0, 3, 3, black);                 // étui de cuisse
        px(LEG, RIGHT, 2, 1, mul(black, 2.2));
        shade();
        return new BufferedImage[]{l1, l2};
    }

    /** Niveau 3 — tenue anti-émeute : casque à visière, coques rigides noires, protège-tibias. */
    static BufferedImage[] emeute() {
        int shell = 0xFF282A30, panel = 0xFF424750, hi = 0xFF727986, cloth = 0xFF1E2024, glass = 0xFF7FA6BD, glassHi = 0xFFC8E0EC,
                white = 0xFFDADADA, strap = 0xFF5A5F68;
        BufferedImage l1 = layer();
        top(HEAD, shell);
        rect(HEAD, TOP, 3, 0, 4, 7, panel);
        wrap(HEAD, 0, 7, shell);
        rect(HEAD, FRONT, 0, 1, 7, 1, hi);                   // charnière de la visière
        rect(HEAD, FRONT, 1, 2, 6, 5, glass);
        rect(HEAD, RIGHT, 6, 2, 7, 5, glass);
        rect(HEAD, LEFT, 0, 2, 1, 5, glass);
        px(HEAD, FRONT, 2, 2, glassHi);
        px(HEAD, FRONT, 3, 3, glassHi);
        px(HEAD, FRONT, 2, 3, mix(glass, glassHi, 0.5));
        px(HEAD, FRONT, 5, 5, mul(glass, 0.8));
        erase(HEAD, FRONT, 1, 6, 6, 7);                      // menton dégagé sous la visière
        erase(HEAD, RIGHT, 6, 6, 7, 7);
        erase(HEAD, LEFT, 0, 6, 1, 7);
        rect(HEAD, BACK, 0, 6, 7, 7, panel);                 // couvre-nuque
        px(HEAD, RIGHT, 3, 3, hi);
        px(HEAD, LEFT, 4, 3, hi);
        // plastron rigide
        wrap(BODY, 0, 11, cloth);
        top(BODY, shell);
        rect(BODY, FRONT, 1, 1, 6, 5, panel);
        rect(BODY, FRONT, 1, 1, 6, 1, hi);
        rect(BODY, FRONT, 1, 3, 3, 3, white);
        rect(BODY, FRONT, 1, 7, 6, 10, panel);
        rect(BODY, FRONT, 1, 7, 6, 7, hi);
        rect(BODY, FRONT, 3, 8, 4, 10, shell);
        rect(BODY, BACK, 1, 1, 6, 10, panel);
        rect(BODY, BACK, 1, 1, 6, 1, hi);
        rect(BODY, BACK, 1, 3, 6, 4, white);
        px(BODY, BACK, 2, 4, panel);
        px(BODY, BACK, 4, 3, panel);
        px(BODY, BACK, 5, 4, panel);
        rect(BODY, BACK, 3, 6, 4, 10, shell);
        for (int f : new int[]{RIGHT, LEFT}) {
            rect(BODY, f, 0, 4, 3, 4, strap);
            rect(BODY, f, 0, 8, 3, 8, strap);
        }
        // épaulière, coudière, protège-avant-bras, gants
        wrap(ARM, 0, 9, cloth);
        top(ARM, panel);
        wrap(ARM, 0, 3, panel);
        wrap(ARM, 0, 0, hi);
        wrap(ARM, 5, 6, panel);
        rect(ARM, RIGHT, 0, 5, 3, 5, hi);
        wrap(ARM, 7, 9, panel);
        for (int y = 7; y < 10; y++) {
            px(ARM, RIGHT, 1, y, hi);
            px(ARM, RIGHT, 2, y, shell);
        }
        wrap(ARM, 10, 11, shell);
        bottom(ARM, shell);
        // bottes hautes coquées
        wrap(LEG, 6, 11, shell);
        rect(LEG, FRONT, 0, 6, 3, 8, panel);
        rect(LEG, FRONT, 0, 6, 3, 6, hi);
        px(LEG, FRONT, 1, 10, hi);
        px(LEG, FRONT, 2, 10, hi);
        wrap(LEG, 11, 11, mul(shell, 0.6));
        bottom(LEG, mul(shell, 0.6));
        shade();

        BufferedImage l2 = layer();
        wrap(BODY, 8, 11, cloth);
        wrap(BODY, 9, 9, strap);
        px(BODY, FRONT, 3, 9, hi);
        px(BODY, FRONT, 4, 9, hi);
        rect(BODY, FRONT, 2, 10, 5, 11, panel);
        wrap(LEG, 0, 11, cloth);
        for (int f : new int[]{FRONT, RIGHT}) {
            rect(LEG, f, 0, 1, 3, 3, panel);                 // cuissard
            rect(LEG, f, 0, 1, 3, 1, hi);
            rect(LEG, f, 0, 7, 3, 11, panel);                // protège-tibia
        }
        rect(LEG, FRONT, 0, 5, 3, 6, panel);                 // genouillère
        rect(LEG, FRONT, 0, 5, 3, 5, hi);
        for (int y = 8; y < 11; y++) {
            px(LEG, FRONT, 1, y, hi);
            px(LEG, FRONT, 2, y, shell);
        }
        shade();
        return new BufferedImage[]{l1, l2};
    }

    /** Niveau 4 — tenue tactique : casque balistique, porte-plaques sable, treillis camouflé. */
    static BufferedImage[] tactique() {
        int olive = 0xFF4E5638, oliveD = 0xFF363D28, brown = 0xFF5E4C33, tan = 0xFF8F7A52, tanL = 0xFFA8946A, tanD = 0xFF6F5D3D,
                black = 0xFF1C1D1A, green = 0xFF2E3A2A;
        BufferedImage l1 = layer();
        top(HEAD, olive);
        wrap(HEAD, 0, 3, olive);
        rect(HEAD, BACK, 0, 4, 7, 4, olive);
        erase(HEAD, FRONT, 0, 3, 7, 3);
        erase(HEAD, RIGHT, 3, 3, 7, 3);                      // coupe haute au-dessus de l'oreille
        erase(HEAD, LEFT, 0, 3, 4, 3);
        camo(HEAD, olive, 14, brown, oliveD);
        rect(HEAD, FRONT, 0, 2, 7, 2, oliveD);
        rect(HEAD, FRONT, 3, 0, 4, 1, black);                // support de vision nocturne
        rect(HEAD, RIGHT, 3, 3, 5, 5, green);                // casque radio
        px(HEAD, RIGHT, 4, 4, black);
        rect(HEAD, LEFT, 2, 3, 4, 5, green);
        px(HEAD, LEFT, 3, 4, black);
        for (int y = 3; y < 7; y++) {
            px(HEAD, RIGHT, 6, y, black);
            px(HEAD, LEFT, 1, y, black);
        }
        // treillis et porte-plaques
        wrap(BODY, 0, 10, olive);
        camo(BODY, olive, 12, brown, oliveD);
        top(BODY, tan);
        rect(BODY, FRONT, 0, 1, 7, 8, tan);
        rect(BODY, BACK, 0, 1, 7, 8, tan);
        for (int f : new int[]{FRONT, BACK}) {
            rect(BODY, f, 1, 0, 2, 0, tan);
            rect(BODY, f, 5, 0, 6, 0, tan);
        }
        for (int f : new int[]{RIGHT, LEFT}) {
            rect(BODY, f, 0, 4, 3, 8, tanD);
            rect(BODY, f, 0, 6, 3, 6, tan);
        }
        rect(BODY, FRONT, 2, 2, 5, 3, oliveD);               // bande auto-agrippante
        px(BODY, FRONT, 2, 2, 0xFFD8D8D8);
        for (int x : new int[]{0, 3, 6}) {                   // trois porte-chargeurs
            rect(BODY, FRONT, x, 5, x + 1, 5, black);
            rect(BODY, FRONT, x, 6, x + 1, 8, tanL);
        }
        rect(BODY, FRONT, 2, 5, 2, 8, tanD);
        rect(BODY, FRONT, 5, 5, 5, 8, tanD);
        for (int y : new int[]{2, 4, 6}) {                   // sangles MOLLE
            rect(BODY, BACK, 1, y, 6, y, tanD);
        }
        rect(BODY, BACK, 2, 7, 5, 8, tanL);
        rect(BODY, BACK, 3, 1, 4, 1, black);
        // manches, coudières, gants
        top(ARM, olive);
        wrap(ARM, 0, 9, olive);
        camo(ARM, olive, 10, brown, oliveD);
        wrap(ARM, 5, 6, tan);
        rect(ARM, RIGHT, 0, 5, 3, 5, tanL);
        rect(ARM, RIGHT, 1, 1, 2, 2, tanD);
        wrap(ARM, 10, 11, black);
        bottom(ARM, black);
        // rangers sable
        wrap(LEG, 7, 11, tan);
        wrap(LEG, 7, 7, tanL);
        px(LEG, FRONT, 1, 8, tanD);
        px(LEG, FRONT, 2, 9, tanD);
        px(LEG, FRONT, 1, 9, tanD);
        px(LEG, FRONT, 2, 8, tanD);
        wrap(LEG, 11, 11, 0xFF3A3226);
        bottom(LEG, 0xFF3A3226);
        shade();

        BufferedImage l2 = layer();
        wrap(BODY, 9, 11, olive);
        wrap(BODY, 9, 9, tan);
        px(BODY, FRONT, 3, 9, black);
        px(BODY, FRONT, 4, 9, black);
        wrap(LEG, 0, 11, olive);
        camo(LEG, olive, 16, brown, oliveD);
        camo(BODY, olive, 4, brown, oliveD);
        rect(LEG, FRONT, 0, 5, 3, 6, tan);                   // genouillère
        rect(LEG, FRONT, 0, 5, 3, 5, tanL);
        wrap(LEG, 3, 3, black);                              // sangle de l'étui
        rect(LEG, RIGHT, 0, 1, 3, 4, black);
        px(LEG, RIGHT, 2, 2, mul(black, 2.2));
        shade();
        return new BufferedImage[]{l1, l2};
    }

    /** Niveau 5 — armure lourde : casque intégral à fente lumineuse, plaques d'acier, liserés rouges. */
    static BufferedImage[] lourde() {
        int gun = 0xFF3C4149, dark = 0xFF24272C, steel = 0xFF6A727E, red = 0xFFC0392B, glowC = 0xFFFF7A2A, black = 0xFF101113;
        BufferedImage l1 = layer();
        top(HEAD, gun);
        rect(HEAD, TOP, 3, 0, 4, 7, red);
        wrap(HEAD, 0, 7, gun);
        rect(HEAD, FRONT, 0, 0, 7, 0, steel);
        rect(HEAD, FRONT, 3, 0, 4, 1, red);
        rect(HEAD, FRONT, 0, 2, 7, 4, dark);
        for (int x = 1; x < 7; x++) {
            glow(HEAD, FRONT, x, 3, x == 1 || x == 6 ? mul(glowC, 0.75) : glowC);
        }
        glow(HEAD, RIGHT, 7, 3, mul(glowC, 0.6));
        glow(HEAD, LEFT, 0, 3, mul(glowC, 0.6));
        rect(HEAD, FRONT, 3, 5, 4, 7, dark);                 // respirateur
        px(HEAD, FRONT, 2, 6, black);
        px(HEAD, FRONT, 5, 6, black);
        for (int y = 5; y < 8; y++) {
            px(HEAD, FRONT, 0, y, steel);
            px(HEAD, FRONT, 7, y, steel);
        }
        rect(HEAD, RIGHT, 3, 3, 5, 5, steel);
        px(HEAD, RIGHT, 4, 4, dark);
        rect(HEAD, LEFT, 2, 3, 4, 5, steel);
        px(HEAD, LEFT, 3, 4, dark);
        rect(HEAD, BACK, 0, 4, 7, 4, dark);
        rect(HEAD, BACK, 3, 6, 4, 6, red);
        // cuirasse
        wrap(BODY, 0, 11, gun);
        top(BODY, steel);
        wrap(BODY, 0, 0, steel);
        for (int[] c : new int[][]{{1, 2}, {2, 3}, {3, 4}, {4, 4}, {5, 3}, {6, 2}}) {
            px(BODY, FRONT, c[0], c[1], red);                // chevron
        }
        for (int y : new int[]{6, 8, 10}) {
            wrap(BODY, y, y, dark);                          // plaques abdominales
        }
        for (int y : new int[]{7, 9}) {
            px(BODY, FRONT, 1, y, steel);
            px(BODY, FRONT, 6, y, steel);
        }
        rect(BODY, BACK, 2, 2, 5, 7, dark);                  // bloc dorsal
        rect(BODY, BACK, 3, 3, 4, 3, steel);
        rect(BODY, BACK, 3, 5, 4, 5, steel);
        rect(BODY, BACK, 3, 9, 4, 9, red);
        // spallières, canons d'avant-bras, gantelets
        top(ARM, steel);
        wrap(ARM, 0, 4, steel);
        wrap(ARM, 4, 4, dark);
        rect(ARM, RIGHT, 1, 1, 2, 3, red);
        wrap(ARM, 5, 6, dark);
        wrap(ARM, 7, 9, gun);
        wrap(ARM, 7, 7, steel);
        wrap(ARM, 10, 11, black);
        bottom(ARM, black);
        // solerets
        wrap(LEG, 6, 11, gun);
        wrap(LEG, 6, 6, steel);
        rect(LEG, FRONT, 0, 10, 3, 11, steel);
        for (int f : new int[]{RIGHT, LEFT, BACK}) {
            rect(LEG, f, 0, 11, 3, 11, black);
        }
        bottom(LEG, black);
        shade();

        BufferedImage l2 = layer();
        wrap(BODY, 8, 11, dark);
        wrap(BODY, 9, 9, steel);
        px(BODY, FRONT, 3, 9, red);
        px(BODY, FRONT, 4, 9, red);
        wrap(LEG, 0, 4, gun);                                // tassettes
        wrap(LEG, 0, 0, steel);
        wrap(LEG, 4, 4, dark);
        wrap(LEG, 5, 6, steel);                              // genouillères
        px(LEG, FRONT, 1, 5, red);
        px(LEG, FRONT, 2, 5, red);
        wrap(LEG, 7, 11, dark);                              // grèves
        for (int y = 7; y < 12; y++) {
            px(LEG, FRONT, 1, y, gun);
            px(LEG, FRONT, 2, y, gun);
        }
        shade();
        return new BufferedImage[]{l1, l2};
    }

    // ------------------------------------------------------------------ icônes 16x16

    /** Lettre → couleur ; « . » = transparent. Le contour est assombri tout seul. */
    static BufferedImage icon(String[] rows, Map<Character, Integer> pal) {
        if (rows.length != 16) {
            throw new IllegalArgumentException("icône : " + rows.length + " lignes au lieu de 16");
        }
        BufferedImage im = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            if (rows[y].length() != 16) {
                throw new IllegalArgumentException("icône : ligne " + y + " « " + rows[y] + " » de " + rows[y].length() + " caractères");
            }
            for (int x = 0; x < 16; x++) {
                char ch = rows[y].charAt(x);
                if (ch == '.') {
                    continue;
                }
                Integer c = pal.get(ch);
                if (c == null) {
                    throw new IllegalArgumentException("icône : couleur « " + ch + " » inconnue");
                }
                im.setRGB(x, y, c);
            }
        }
        BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = im.getRGB(x, y);
                if ((c >>> 24) == 0) {
                    continue;
                }
                boolean edge = false;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = x + d[0], ny = y + d[1];
                    edge |= nx < 0 || ny < 0 || nx > 15 || ny > 15 || (im.getRGB(nx, ny) >>> 24) == 0;
                }
                out.setRGB(x, y, mul(c, (edge ? 0.62 : 1.0) * (1.10 - 0.012 * (x + y))));
            }
        }
        return out;
    }

    static final String E = "................";

    static final String[] H_FORTUNE = {E, E,
            ".....cccccc.....",
            "....caaadaac....",
            "...caaaadaaac...",
            "...aaaaadaaaa...",
            "...aaaaadaaaa...",
            "..eeeeeeeeeeee..",
            ".bbbbbbbbbbbbbb.",
            "....k......k....",
            "....k......k....",
            ".....k....k.....",
            "......kkkk......",
            E, E, E};
    static final String[] H_POLICE = {E, E,
            "....aaaaaaaa....",
            "...aaaaaaaaaa...",
            "..aaaaaddaaaaa..",
            "..aaaaaddaaaaa..",
            "..aaaaaaaaaaaa..",
            "..wkwkwkwkwkwk..",
            "..kwkwkwkwkwkw..",
            "..bbbbbbbbbbbb..",
            "..bb........bb..",
            "..bb........bb..",
            E, E, E, E};
    static final String[] H_EMEUTE = {E,
            ".....aaaaaa.....",
            "...aaaaaaaaaa...",
            "..aaacaaaaaaaa..",
            "..aacaaaaaaaaa..",
            "..aaaaaaaaaaaa..",
            "..bbbbbbbbbbbb..",
            "..bggggggggggb..",
            "..bgwggggggggb..",
            "..bggwgggggggb..",
            "..bggggggggggb..",
            "..bbggggggggbb..",
            "...bb......bb...",
            "...bb......bb...",
            E, E};
    static final String[] H_TACTIQUE = {E,
            "......kkkk......",
            "....aakkkkaa....",
            "...aaeaaaaaaa...",
            "..aaaaaaaeeaaa..",
            "..aeeaaaaaaaaa..",
            "..aaaaaaeaaaaa..",
            "..aaaaaaaaaeaa..",
            "..bbbbbbbbbbbb..",
            "..dd........dd..",
            "..dd........dd..",
            "..dk........kd..",
            "...k........k...",
            "....kk....kk....",
            E, E};
    static final String[] H_LOURDE = {E,
            ".....ccddcc.....",
            "...ccaaddaacc...",
            "..caaaaddaaaac..",
            "..aaaaaddaaaaa..",
            "..aaaaaaaaaaaa..",
            "..bbbbbbbbbbbb..",
            "..bggggggggggb..",
            "..bbbbbbbbbbbb..",
            "..aaaakbbkaaaa..",
            "..aaaakbbkaaaa..",
            "..aaaakbbkaaaa..",
            "...aaaabbaaaa...",
            "....aaaaaaaa....",
            E, E};

    static final String[] C_FORTUNE = {E,
            "..kkk......kkk..",
            ".kkkkaa..aakkkk.",
            ".kkkaaaaaaaakkk.",
            ".aa.aeeeeeea.aa.",
            ".aa.adddddda.aa.",
            ".aa.adwwwwda.aa.",
            ".ee.adddddda.ee.",
            ".aa.aeeeeeea.aa.",
            "....aaaaaaaa....",
            "....abaaaaba....",
            "....aaaaaaaa....",
            "....bbbbbbbb....",
            E, E, E};
    static final String[] C_POLICE = {E,
            "...aa......aa...",
            ".ccaaa....aaacc.",
            ".ccaaaaaaaaaacc.",
            ".cc.adaawwwa.cc.",
            ".cc.adaaaaaa.cc.",
            "....aaaaaaaa....",
            "....aaaaaaaa....",
            "....akkaakka....",
            "....akkaakka....",
            "....aaaaaaaa....",
            "....bbbbbbbb....",
            E, E, E, E};
    static final String[] C_EMEUTE = {E,
            "..ccc......ccc..",
            ".caaaaa..aaaaac.",
            ".aaaabbbbbbaaaa.",
            ".aaa.bwwwbb.aaa.",
            ".bbb.bbbbbb.bbb.",
            ".aaa.acaaca.aaa.",
            ".ccc.aaaaaa.ccc.",
            ".aaa.acaaca.aaa.",
            ".aaa.aaaaaa.aaa.",
            ".kkk.bbbbbb.kkk.",
            ".....aaaaaa.....",
            ".....bbbbbb.....",
            E, E, E};
    static final String[] C_TACTIQUE = {E,
            "...dd......dd...",
            ".aaddd....dddaa.",
            ".aaddddddddddaa.",
            ".ae.ddwwdddd.ea.",
            ".aa.dddddddd.aa.",
            ".ea.kkdkkdkk.ae.",
            ".dd.ccdccdcc.dd.",
            ".aa.ccdccdcc.aa.",
            ".aa.dddddddd.aa.",
            ".kk.aaaaaaaa.kk.",
            "....bbbbbbbb....",
            E, E, E, E};
    static final String[] C_LOURDE = {
            "......bbbb......",
            ".cccc.bbbb.cccc.",
            "cddcaaaaaaaacddc",
            "caacaadaadaacaac",
            "caacaaaddaaacaac",
            ".bb.aaaaaaaa.bb.",
            ".aa.bbbbbbbb.aa.",
            ".cc.acaaaaca.cc.",
            ".cc.bbbbbbbb.cc.",
            ".cc.acaaaaca.cc.",
            ".kk.bbbbbbbb.kk.",
            "....aaaddaaa....",
            "....bbbbbbbb....",
            E, E, E};

    static final String[] L_CLOTH = {E,
            "...kkkkkkkkkk...",
            "...aaaaddaaaa...",
            "...aaaaaaaaaa...",
            "...aaaaaaaaaa...",
            "...aaaa..aaaa...",
            "...aaaa..aaaa...",
            "...eeee..eeee...",
            "...eeee..eeee...",
            "...aaaa..aaaa...",
            "...aaaa..aaaa...",
            "...aaba..abaa...",
            "...aaaa..aaaa...",
            "...bbbb..bbbb...",
            E, E};
    static final String[] L_ARMOR = {E,
            "...kkkkkkkkkk...",
            "...aaaaddaaaa...",
            "..caaaabbaaaac..",
            "..caaaa..aaaac..",
            "..caaaa..aaaac..",
            "...bbbb..bbbb...",
            "...cccc..cccc...",
            "...cddc..cddc...",
            "...bbbb..bbbb...",
            "...acca..acca...",
            "...acca..acca...",
            "...acca..acca...",
            "...bbbb..bbbb...",
            E, E};
    static final String[] B_SOFT = {E, E, E, E, E,
            "..aaaa....aaaa..",
            "..aaaa....aaaa..",
            "..eeee....eeee..",
            "..aaaa....aaaa..",
            "..aaaa....aaaa..",
            ".caaaa....aaaac.",
            ".aaaaa....aaaaa.",
            ".kkkkk....kkkkk.",
            E, E, E};
    static final String[] B_ARMOR = {E, E, E, E,
            "..cccc....cccc..",
            "..acca....acca..",
            "..acca....acca..",
            "..bbbb....bbbb..",
            "..addc....cdda..",
            "..aaaa....aaaa..",
            ".ccaaa....aaacc.",
            ".ccaaa....aaacc.",
            ".kkkkk....kkkkk.",
            ".kkkkk....kkkkk.",
            E, E};

    static Map<Character, Integer> pal(Object... kv) {
        Map<Character, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((Character) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    /** Les quatre icônes d'une tenue : casque, plastron, jambières, bottes. */
    static BufferedImage[] icons(String set) {
        return switch (set) {
            case "fortune" -> new BufferedImage[]{
                    icon(H_FORTUNE, pal('a', 0xFFD2A51E, 'b', 0xFFA87F14, 'c', 0xFFE6BE3C, 'd', 0xFFF0D060, 'e', 0xFFA9A9A4, 'k', 0xFF5A3D26)),
                    icon(C_FORTUNE, pal('a', 0xFF7A5536, 'b', 0xFF4A3220, 'k', 0xFF2E2E33, 'd', 0xFFB3261E, 'w', 0xFFE2DED2, 'e', 0xFFA9A9A4)),
                    icon(L_CLOTH, pal('a', 0xFF6A5843, 'b', 0xFF46392B, 'k', 0xFFB89B6A, 'd', 0xFFB89B6A, 'e', 0xFFC2A878)),
                    icon(B_SOFT, pal('a', 0xFF5E4430, 'c', 0xFF76583F, 'e', 0xFFA9A9A4, 'k', 0xFF2B1F16))};
            case "police" -> new BufferedImage[]{
                    icon(H_POLICE, pal('a', 0xFF2A3B60, 'b', 0xFF172038, 'd', 0xFFD9B23A, 'w', 0xFFE8E8E8, 'k', 0xFF17181C)),
                    icon(C_POLICE, pal('a', 0xFF2A3B60, 'b', 0xFF172038, 'c', 0xFF7FA0C8, 'd', 0xFFD9B23A, 'w', 0xFFE8E8E8, 'k', 0xFF17181C)),
                    icon(L_CLOTH, pal('a', 0xFF2A3B60, 'b', 0xFF172038, 'k', 0xFF17181C, 'd', 0xFFD9B23A, 'e', 0xFF22304E)),
                    icon(B_SOFT, pal('a', 0xFF26272D, 'c', 0xFF3B3D46, 'e', 0xFF3B3D46, 'k', 0xFF0E0E10))};
            case "emeute" -> new BufferedImage[]{
                    icon(H_EMEUTE, pal('a', 0xFF34373E, 'b', 0xFF22242A, 'c', 0xFF6A707C, 'g', 0xFF7FA6BD, 'w', 0xFFC8E0EC)),
                    icon(C_EMEUTE, pal('a', 0xFF3A3E46, 'b', 0xFF24262B, 'c', 0xFF6A707C, 'w', 0xFFDADADA, 'k', 0xFF141518)),
                    icon(L_ARMOR, pal('a', 0xFF2A2C32, 'b', 0xFF1A1B1F, 'c', 0xFF4A4F59, 'd', 0xFF6A707C, 'k', 0xFF5A5F68)),
                    icon(B_ARMOR, pal('a', 0xFF2A2C32, 'b', 0xFF1A1B1F, 'c', 0xFF4A4F59, 'd', 0xFF6A707C, 'k', 0xFF0E0E10))};
            case "tactique" -> new BufferedImage[]{
                    icon(H_TACTIQUE, pal('a', 0xFF5A6340, 'b', 0xFF363D28, 'e', 0xFF6B5638, 'd', 0xFF2E3A2A, 'k', 0xFF1C1D1A)),
                    icon(C_TACTIQUE, pal('a', 0xFF5A6340, 'b', 0xFF363D28, 'e', 0xFF6B5638, 'd', 0xFF8F7A52, 'c', 0xFFA8946A, 'w', 0xFF363D28, 'k', 0xFF1C1D1A)),
                    icon(L_CLOTH, pal('a', 0xFF5A6340, 'b', 0xFF363D28, 'k', 0xFF8F7A52, 'd', 0xFF1C1D1A, 'e', 0xFF8F7A52)),
                    icon(B_SOFT, pal('a', 0xFF8F7A52, 'c', 0xFFA8946A, 'e', 0xFF6F5D3D, 'k', 0xFF3A3226))};
            default -> new BufferedImage[]{
                    icon(H_LOURDE, pal('a', 0xFF4A505A, 'b', 0xFF24272C, 'c', 0xFF7A838F, 'd', 0xFFC0392B, 'g', 0xFFFF7A2A, 'k', 0xFF101113)),
                    icon(C_LOURDE, pal('a', 0xFF4A505A, 'b', 0xFF24272C, 'c', 0xFF7A838F, 'd', 0xFFC0392B, 'k', 0xFF101113)),
                    icon(L_ARMOR, pal('a', 0xFF4A505A, 'b', 0xFF24272C, 'c', 0xFF7A838F, 'd', 0xFFC0392B, 'k', 0xFF7A838F)),
                    icon(B_ARMOR, pal('a', 0xFF4A505A, 'b', 0xFF24272C, 'c', 0xFF7A838F, 'd', 0xFFC0392B, 'k', 0xFF101113))};
        };
    }

    // ------------------------------------------------------------------ aperçu (face / dos)

    static void blit(BufferedImage dst, BufferedImage src, Part p, int f, int dx, int dy, int s, boolean mirror) {
        int[] r = rectOf(p, f);
        for (int i = 0; i < r[2]; i++) {
            for (int j = 0; j < r[3]; j++) {
                int c = src.getRGB(r[0] + (mirror ? r[2] - 1 - i : i), r[1] + j);
                if ((c >>> 24) == 0) {
                    continue;
                }
                for (int a = 0; a < s; a++) {
                    for (int b = 0; b < s; b++) {
                        dst.setRGB(dx + i * s + a, dy + j * s + b, c);
                    }
                }
            }
        }
    }

    static void box(BufferedImage dst, int x, int y, int w, int h, int c) {
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                dst.setRGB(x + i, y + j, c);
            }
        }
    }

    /** Mannequin gris, puis jambières (calque 2), puis casque / plastron / bottes (calque 1). */
    static void figure(BufferedImage dst, BufferedImage[] set, int x, int y, int s, int face) {
        int skin = 0xFFB9A792, under = 0xFF70757D;
        box(dst, x + 4 * s, y, 8 * s, 8 * s, skin);
        box(dst, x + 4 * s, y + 8 * s, 8 * s, 12 * s, under);
        box(dst, x, y + 8 * s, 4 * s, 12 * s, skin);
        box(dst, x + 12 * s, y + 8 * s, 4 * s, 12 * s, skin);
        box(dst, x + 4 * s, y + 20 * s, 8 * s, 12 * s, mul(under, 0.8));
        if (face == FRONT) {
            box(dst, x + 5 * s, y + 4 * s, 2 * s, s, 0xFF40362E);
            box(dst, x + 9 * s, y + 4 * s, 2 * s, s, 0xFF40362E);
        }
        boolean back = face == BACK;
        for (BufferedImage l : new BufferedImage[]{set[1], set[0]}) {
            if (l == set[0]) {
                blit(dst, l, HEAD, face, x + 4 * s, y, s, false);
            }
            blit(dst, l, BODY, face, x + 4 * s, y + 8 * s, s, false);
            if (l == set[0]) {
                blit(dst, l, ARM, face, x + (back ? 12 : 0) * s, y + 8 * s, s, false);
                blit(dst, l, ARM, face, x + (back ? 0 : 12) * s, y + 8 * s, s, true);
            }
            blit(dst, l, LEG, face, x + (back ? 8 : 4) * s, y + 20 * s, s, false);
            blit(dst, l, LEG, face, x + (back ? 4 : 8) * s, y + 20 * s, s, true);
        }
    }

    /** Profil droit : côté extérieur du bras et de la jambe. */
    static void profile(BufferedImage dst, BufferedImage[] set, int x, int y, int s) {
        int skin = 0xFFB9A792, under = 0xFF70757D;
        box(dst, x, y, 8 * s, 8 * s, skin);
        box(dst, x + 2 * s, y + 8 * s, 4 * s, 12 * s, skin);
        box(dst, x + 2 * s, y + 20 * s, 4 * s, 12 * s, mul(under, 0.8));
        blit(dst, set[1], LEG, RIGHT, x + 2 * s, y + 20 * s, s, false);
        blit(dst, set[0], HEAD, RIGHT, x, y, s, false);
        blit(dst, set[0], ARM, RIGHT, x + 2 * s, y + 8 * s, s, false);
        blit(dst, set[0], LEG, RIGHT, x + 2 * s, y + 20 * s, s, false);
    }

    public static void main(String[] a) throws Exception {
        File pack = new File(a[0]);
        File previews = a.length > 1 ? new File(a[1]) : null;
        Map<String, BufferedImage[]> sets = new LinkedHashMap<>();
        rnd = new Random(101);
        sets.put("fortune", fortune());
        rnd = new Random(202);
        sets.put("police", police());
        rnd = new Random(303);
        sets.put("emeute", emeute());
        rnd = new Random(404);
        sets.put("tactique", tactique());
        rnd = new Random(505);
        sets.put("lourde", lourde());

        int s = 6, cw = 62 * s, ch = 54 * s;
        BufferedImage sheet = new BufferedImage(sets.size() * cw, ch, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x3A4048));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        int n = 0;
        for (Map.Entry<String, BufferedImage[]> e : sets.entrySet()) {
            String id = e.getKey();
            write(e.getValue()[0], new File(pack, "assets/orbitguns/textures/entity/equipment/humanoid/" + id + ".png"));
            write(e.getValue()[1], new File(pack, "assets/orbitguns/textures/entity/equipment/humanoid_leggings/" + id + ".png"));
            text(new File(pack, "assets/orbitguns/equipment/" + id + ".json"),
                    "{\n  \"layers\": {\n    \"humanoid\": [{\"texture\": \"orbitguns:" + id + "\"}],\n"
                            + "    \"humanoid_leggings\": [{\"texture\": \"orbitguns:" + id + "\"}]\n  }\n}\n");
            BufferedImage[] ic = icons(id);
            for (int i = 0; i < 4; i++) {
                String name = "armor_" + id + "_" + PIECES[i];
                write(ic[i], new File(pack, "assets/orbitguns/textures/item/" + name + ".png"));
                text(new File(pack, "assets/orbitguns/models/item/" + name + ".json"),
                        "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\"layer0\": \"orbitguns:item/" + name + "\"}\n}\n");
                blit(sheet, ic[i], new Part(0, 0, 16, 16, 0), FRONT, n * cw + 2 * s + i * 14 * s, 39 * s, 4, false);
            }
            figure(sheet, e.getValue(), n * cw + 2 * s, 5 * s, s, FRONT);
            figure(sheet, e.getValue(), n * cw + 21 * s, 5 * s, s, BACK);
            profile(sheet, e.getValue(), n * cw + 42 * s, 5 * s, s);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            g.drawString((n + 1) + " - " + id, n * cw + 2 * s, 3 * s);
            n++;
        }
        g.dispose();
        if (previews != null) {
            previews.mkdirs();
            ImageIO.write(sheet, "png", new File(previews, "armures_apercu.png"));
        }
        System.out.println("OK " + sets.size() + " tenues (textures portees, icones, definitions d'equipement)");
    }

    static void write(BufferedImage im, File f) throws Exception {
        f.getParentFile().mkdirs();
        ImageIO.write(im, "png", f);
    }

    static void text(File f, String s) throws Exception {
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), s, StandardCharsets.UTF_8);
    }
}
