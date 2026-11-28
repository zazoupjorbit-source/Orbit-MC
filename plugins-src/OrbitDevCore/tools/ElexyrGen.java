import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Élexyr, le soldat casse-noisette squelette (easter egg) : modèle en cubes + texture HD dessinée par code.
 *
 * Écrit dans le pack du lobby :
 *   assets/orbitdev/textures/item/elexyr.png        atlas 1024 x 1024 (12 pixels par unité de modèle, le jeu en a 1)
 *   assets/orbitdev/models/item/elexyr_<piece>.json  une pièce par articulation (tête, mâchoire, œil, torse, bras, jambes)
 *   assets/orbitdev/items/elexyr_<piece>.json
 * et, pour le plugin, src/main/resources/elexyr.rig (pivot de chaque pièce, lu par fr.orbit.devcore.elexyr.Elexyr).
 * L'aperçu (maquettes/elexyr_apercu.png) dessine le modèle sous quatre angles, sans lancer le jeu.
 *
 * Repère du personnage : 1 unité = 1/16 de bloc à l'échelle 1, pieds à Y = 0, avant = -Z (nord), sa droite = +X.
 * Chaque pièce est centrée sur son pivot (point 8,8,8 du modèle) : le plugin la pose avec un ItemDisplay.
 * Les cubes ne tournent que par pas de 22,5° sur un seul axe (limite sûre des modèles du jeu).
 *
 * Usage : java tools/ElexyrGen.java pack src/main/resources/elexyr.rig ../../maquettes/elexyr_apercu.png
 * puis tools/gen-ui.ps1 (packs complets par thème) et build.ps1.
 */
public class ElexyrGen {

    static final int D = 12;        // pixels de texture par unité de modèle
    static final int ATLAS = 1024;
    static final int PAD = 2;       // marge recopiée autour de chaque face (évite les liserés)

    enum Face { NORTH, SOUTH, WEST, EAST, UP, DOWN }

    /** Point d'une face en cours de peinture. */
    static final class P {
        double x, y, z;     // position dans le repère du personnage (cube non tourné)
        double u, v, w, h;  // position sur la face (depuis le coin haut gauche vu de l'extérieur) et taille de la face
        int tx, ty;         // pixel dans l'atlas
        Face f;
        Box b;

        /** Coordonnée horizontale le long de la surface. */
        double a() {
            return switch (f) {
                case NORTH, SOUTH, UP, DOWN -> x;
                default -> z;
            };
        }

        /** Coordonnée verticale le long de la surface (Z pour le dessus et le dessous). */
        double b() {
            return f == Face.UP || f == Face.DOWN ? z : y;
        }

        boolean side() {
            return f != Face.UP && f != Face.DOWN;
        }

        /** Distance au bord de la face le plus proche. */
        double edge() {
            return Math.min(Math.min(u, w - u), Math.min(v, h - v));
        }
    }

    interface Mat {
        int at(P p);
    }

    static final class Box {
        final double x0, y0, z0, x1, y1, z1;
        final Mat mat;
        char axis;
        double angle, ox, oy, oz;
        final EnumSet<Face> faces = EnumSet.allOf(Face.class);
        double ao = 1;
        final int[][] rect = new int[6][];

        Box(double xa, double ya, double za, double xb, double yb, double zb, Mat mat) {
            x0 = Math.min(xa, xb);
            x1 = Math.max(xa, xb);
            y0 = Math.min(ya, yb);
            y1 = Math.max(ya, yb);
            z0 = Math.min(za, zb);
            z1 = Math.max(za, zb);
            this.mat = mat;
        }

        Box rot(char axis, double angle, double ox, double oy, double oz) {
            this.axis = axis;
            this.angle = angle;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            return this;
        }

        Box no(Face... f) {
            for (Face x : f) {
                faces.remove(x);
            }
            return this;
        }

        Box only(Face... f) {
            faces.clear();
            faces.addAll(List.of(f));
            return this;
        }

        Box ao(double a) {
            ao = a;
            return this;
        }

        double fw(Face f) {
            return f == Face.WEST || f == Face.EAST ? z1 - z0 : x1 - x0;
        }

        double fh(Face f) {
            return f == Face.UP || f == Face.DOWN ? z1 - z0 : y1 - y0;
        }

        /** Point de la face à (u, v) : même orientation que les UV par défaut du jeu. */
        void point(Face f, double u, double v, P p) {
            switch (f) {
                case NORTH -> { p.x = x1 - u; p.y = y1 - v; p.z = z0; }
                case SOUTH -> { p.x = x0 + u; p.y = y1 - v; p.z = z1; }
                case WEST -> { p.z = z0 + u; p.y = y1 - v; p.x = x0; }
                case EAST -> { p.z = z1 - u; p.y = y1 - v; p.x = x1; }
                case UP -> { p.x = x0 + u; p.z = z0 + v; p.y = y1; }
                case DOWN -> { p.x = x0 + u; p.z = z1 - v; p.y = y0; }
            }
        }
    }

    static final class Part {
        final String name;
        final double px, py, pz;
        final List<Box> boxes = new ArrayList<>();

        Part(String name, double px, double py, double pz) {
            this.name = name;
            this.px = px;
            this.py = py;
            this.pz = pz;
        }

        Box add(double xa, double ya, double za, double xb, double yb, double zb, Mat mat) {
            Box b = new Box(xa, ya, za, xb, yb, zb, mat);
            boxes.add(b);
            return b;
        }
    }

    // ------------------------------------------------------------------ couleurs et bruit

    static final int RED = 0xB21F2B, NAVY = 0x1B2E6E, GREY = 0x7E828C;
    static final int G_DK = 0x57370B, G_LO = 0x9C6715, G_MID = 0xE3A832, G_HI = 0xFFF2AE;

    static double c01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    /** Passage doux de 0 (x <= a) à 1 (x >= b). */
    static double sm(double a, double b, double x) {
        double t = c01((x - a) / (b - a));
        return t * t * (3 - 2 * t);
    }

    static double frac(double v) {
        return v - Math.floor(v);
    }

    static int mix(int c1, int c2, double t) {
        t = c01(t);
        int r = (int) Math.round(((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) Math.round(((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) Math.round((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    static int mul(int c, double f) {
        int r = (int) Math.max(0, Math.min(255, Math.round(((c >> 16) & 255) * f)));
        int g = (int) Math.max(0, Math.min(255, Math.round(((c >> 8) & 255) * f)));
        int b = (int) Math.max(0, Math.min(255, Math.round((c & 255) * f)));
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Couleur transparente (découpe) : on garde la teinte pour les mipmaps. */
    static final int CUT = 0x01000000;

    static int cut(int c) {
        return CUT | (c & 0xFFFFFF);
    }

    static double hash(int x, int y, int s) {
        int h = x * 374761393 + y * 668265263 + s * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    static double vnoise(double x, double y, int s) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        double fx = x - xi, fy = y - yi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(xi, yi, s), b = hash(xi + 1, yi, s), c = hash(xi, yi + 1, s), d = hash(xi + 1, yi + 1, s);
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
    }

    static double fbm(double x, double y, int s, int oct) {
        double sum = 0, amp = 0.5, tot = 0;
        for (int i = 0; i < oct; i++) {
            sum += amp * vnoise(x, y, s + i * 17);
            tot += amp;
            x *= 2.03;
            y *= 2.03;
            amp *= 0.5;
        }
        return sum / tot;
    }

    static double segDist(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double t = c01(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy));
        return Math.hypot(px - ax - t * dx, py - ay - t * dy);
    }

    // ------------------------------------------------------------------ matières

    static int gold(double t) {
        t = c01(t);
        if (t < 0.33) {
            return mix(G_DK, G_LO, t / 0.33);
        }
        if (t < 0.72) {
            return mix(G_LO, G_MID, (t - 0.33) / 0.39);
        }
        return mix(G_MID, G_HI, (t - 0.72) / 0.28);
    }

    static int steel(double t) {
        t = c01(t);
        if (t < 0.5) {
            return mix(0x34383F, 0x8B929C, t / 0.5);
        }
        return mix(0x8B929C, 0xE6EBF0, (t - 0.5) / 0.5);
    }

    /** Galon tressé : {@code along} le long du galon, {@code across} de -1 à 1 dans sa largeur. */
    static int braid(double along, double across) {
        double twist = 0.5 + 0.5 * Math.sin((along * 2.6 + across * 0.55) * Math.PI * 2);
        double round = 1 - 0.55 * across * across;
        return gold((0.22 + 0.7 * twist) * round);
    }

    static int goldPlain(P p) {
        double n = fbm(p.a() * 2 + 3, p.b() * 2, 5, 2);
        double e = p.edge();
        double bevel = e < 0.12 ? (p.u < 0.12 || p.v < 0.12 ? 0.3 : -0.3) : 0;
        return 0xFF000000 | gold(0.5 + 0.25 * (n - 0.5) + bevel);
    }

    /** Drap de laine : sergé en diagonale, fils, plis doux. */
    static int cloth(P p, int base, int seed) {
        double a = p.a(), b = p.b();
        double twill = Math.sin((a + b) * Math.PI * 2 * 2.6);
        double thread = Math.sin((a - b) * Math.PI * 2 * 5.2);
        double mott = fbm(a * 0.9 + p.z * 0.31 + seed, b * 0.9, seed, 3) - 0.5;
        double folds = fbm(a * 0.42 + p.z * 0.2, b * 0.17, seed + 7, 2) - 0.5;
        double fuzz = hash(p.tx, p.ty, seed) - 0.5;
        return mul(base, 1 + 0.05 * twill + 0.02 * thread + 0.2 * mott + 0.2 * folds + 0.05 * fuzz);
    }

    static int blackLeather(P p) {
        double n = fbm(p.a() * 2.4 + p.z * 0.7, p.b() * 2.4, 31, 3);
        int c = mix(0x121114, 0x2C2A30, n);
        if (hash(p.tx, p.ty, 33) < 0.035) {
            c = mul(c, 0.6);
        }
        return c;
    }

    static int leatherM(P p) {
        return 0xFF000000 | blackLeather(p);
    }

    /** Cotte de mailles : rangées d'anneaux décalées, qui se chevauchent. */
    static int mail(double a, double b, int seed) {
        final double rw = 0.5, rh = 0.36, rad = 0.235;
        double best = -1;
        int row0 = (int) Math.floor(b / rh);
        for (int row = row0 - 1; row <= row0 + 1; row++) {
            double ax = a + ((row & 1) == 0 ? 0 : rw / 2);
            for (int k = -1; k <= 1; k++) {
                double cx = (Math.floor(ax / rw) + k + 0.5) * rw, cy = (row + 0.5) * rh;
                double dx = ax - cx, dy = b - cy, r = Math.hypot(dx, dy);
                double band = Math.abs(r - rad);
                if (band < 0.075) {
                    double lit = 0.5 + 0.42 * (-dx + dy) / (r + 1e-6) * 0.72;
                    lit *= 1 - 0.55 * (band / 0.075) * (band / 0.075);
                    // l'anneau du rang du dessous passe par-dessus : un peu plus clair
                    lit += row == row0 - 1 ? 0.06 : 0;
                    best = Math.max(best, lit);
                }
            }
        }
        double n = fbm(a * 1.3, b * 1.3, seed, 2);
        if (best < 0) {
            return mix(0x14161A, 0x2A2D33, n);
        }
        return steel(best * (0.75 + 0.5 * n));
    }

    static int mailM(P p) {
        return 0xFF000000 | mail(p.a() + p.z * 0.37, p.b(), 41);
    }

    /** Bas de cotte en dents de scie (découpé) : pour une face verticale, {@code depth} unités en bas. */
    static int mailHem(P p, double depth) {
        int c = mailM(p);
        if (p.side()) {
            double tri = Math.abs(frac(p.a() * 0.8 + 0.25) - 0.5) * 2;
            if (p.v > p.h - depth * tri) {
                return cut(c);
            }
            if (p.v > p.h - depth * tri - 0.14) {
                return 0xFF000000 | steel(0.25);
            }
        }
        return c;
    }

    static int bone(P p) {
        double n = fbm(p.x * 0.8 + p.z * 0.6 + 3, p.y * 0.8 + p.z * 0.3, 51, 4);
        int c = mix(0xB9AE92, 0xEFE8D2, n);
        double stain = fbm(p.x * 0.35 + 9 + p.z * 0.2, p.y * 0.35 + p.z * 0.15, 53, 2);
        if (stain > 0.56) {
            c = mix(c, 0xA38F62, (stain - 0.56) * 1.8);
        }
        double crack = Math.abs(fbm(p.x * 0.55 + 20 + p.z * 0.4, p.y * 0.55 + p.z * 0.2, 57, 3) - 0.5);
        if (crack < 0.011) {
            c = mix(c, 0x55492F, 0.8);
        } else if (crack < 0.026) {
            c = mix(c, 0x8C8062, 0.35);
        }
        if (hash(p.tx, p.ty, 59) < 0.03) {
            c = mul(c, 0.86);
        }
        return c;
    }

    static int boneM(P p) {
        return 0xFF000000 | bone(p);
    }

    /** Os en relief (arcades, pommettes) : éclairé du dessus. */
    static int boneRelief(P p) {
        return 0xFF000000 | mul(bone(p), p.f == Face.UP ? 1.08 : p.f == Face.DOWN ? 0.7 : 1.0 - 0.12 * p.v / p.h);
    }

    /** Rangée de dents :{@code t} de 0 (gencive) à 1 (bout de la dent). */
    static int teeth(double along, double t) {
        double k = along / 0.74 + 0.5;
        double f = frac(k);
        int idx = (int) Math.floor(k);
        if (f < 0.07 || f > 0.93 || (t > 0.78 && (f < 0.2 || f > 0.8))) {
            return 0x1E1612;
        }
        if (idx == 2) {
            return gold(0.45 + 0.45 * (1 - Math.abs(f - 0.4) * 2) * (1 - t * 0.4)); // la dent en or
        }
        int c = mix(0xF6F1E0, 0xCBC0A0, t * 0.6 + Math.abs(f - 0.5) * 0.5);
        return t < 0.14 ? mul(c, 0.7) : c;
    }

    // ---- tête

    static int skull(P p) {
        int c = bone(p);
        switch (p.f) {
            case NORTH -> {
                c = mul(c, 1 - 0.34 * sm(30.3, 32.0, p.y));              // ombre de la visière sur le front
                for (int s = -1; s <= 1; s += 2) {                       // pommettes creusées
                    double d = Math.hypot(p.x - s * 2.75, (p.y - 27.35) * 1.15);
                    if (d < 1.15) {
                        c = mul(c, 0.78 + 0.22 * d / 1.15);
                    }
                }
                c = mul(c, 1 + 0.1 * Math.exp(-Math.pow((p.y - 30.55) / 0.22, 2)));   // arcade sourcilière
                double er = Math.hypot((p.x - 1.95) / 1.32, (p.y - 29.2) / 1.15);     // orbite (son œil droit)
                if (er < 1) {
                    c = mix(0x0B0807, 0x3B2F24, Math.pow(er, 3.2));
                    double ir = Math.hypot(p.x - 1.95, p.y - 29.2);
                    if (ir < 0.5) {
                        c = mix(0xFFD964, 0xB54A08, ir / 0.5);
                    } else if (ir < 0.95) {
                        c = mix(c, 0x8A3A06, (0.95 - ir) / 0.45 * 0.65);
                    }
                } else if (er < 1.28) {
                    c = mul(c, 0.66 + 0.34 * (er - 1) / 0.28);
                }
                double ny = (28.25 - p.y) / 1.3;                          // cavité du nez
                if (ny > 0 && ny < 1) {
                    double hw = 0.16 + 0.52 * Math.pow(ny, 0.8), ax = Math.abs(p.x);
                    if (ax < hw && ax > 0.05) {
                        c = mix(0x0E0A08, 0x35291F, ax / hw * 0.6);
                    } else if (ax < hw + 0.16) {
                        c = mul(c, 0.78);
                    }
                }
                if (p.y < 26.9 && Math.abs(p.x) < 3.4) {                  // dents du haut
                    c = teeth(p.x, (26.9 - p.y) / 0.9);
                } else if (p.y < 27.05) {
                    c = mul(c, 0.7);
                }
                double st = segDist(p.x, p.y, -1.1, 30.4, 3.4, 32.1);     // sangle du cache-œil
                double st2 = segDist(p.x, p.y, -3.6, 29.5, -4.1, 29.55);
                double sd = Math.min(st, st2);
                if (sd < 0.17) {
                    c = mix(0x151316, 0x3A3840, sm(0.17, 0, sd) * 0.5);
                } else if (sd < 0.32) {
                    c = mul(c, 0.72);
                }
            }
            case WEST, EAST -> {
                c = mul(c, 1 - 0.3 * sm(30.6, 32.0, p.y));
                double d = Math.hypot(p.z + 1.4, (p.y - 29.0) * 0.9);    // tempe
                if (d < 1.5) {
                    c = mul(c, 0.8 + 0.2 * d / 1.5);
                }
                if (p.y < 26.9 && p.z < -1.6) {
                    c = teeth(p.z, (26.9 - p.y) / 0.9);
                }
                if (p.f == Face.WEST && Math.abs(p.y - (29.55 + (p.z + 4) * 0.22)) < 0.17) {
                    c = 0x1A181C;
                }
            }
            case DOWN -> {                                               // palais et dents du haut vues d'en dessous
                c = mix(0x1A0D0B, 0x2E1512, fbm(p.x, p.z, 61, 2));
                if (p.z < -3.2 && Math.abs(p.x) < 3.4) {
                    c = teeth(p.x, 0.5);
                } else if (Math.abs(p.x) > 2.7 && p.z < -1.4) {
                    c = teeth(p.z, 0.5);
                }
            }
            default -> { }
        }
        return 0xFF000000 | c;
    }

    static int jaw(P p) {
        int c = bone(p);
        switch (p.f) {
            case NORTH -> {
                if (p.y > 25.2) {
                    c = teeth(p.x + 0.37, (p.y - 25.2) / 0.8);
                } else if (p.y > 25.05) {
                    c = mul(c, 0.68);
                }
                c = p.y <= 25.2 ? mul(c, 0.86 + 0.14 * sm(23.9, 25.0, p.y)) : c;
                if (p.y <= 25.05 && Math.abs(Math.abs(p.x) - 2.85) < 0.07) {
                    c = mul(c, 0.62);                                     // fente du levier de casse-noisette
                }
                double chin = Math.hypot(p.x, (p.y - 24.5) * 1.6);
                if (p.y <= 25.05 && chin < 0.9) {
                    c = mul(c, 1.07);
                }
            }
            case WEST, EAST -> {
                if (p.y > 25.2 && p.z < -1.6) {
                    c = teeth(p.z, (p.y - 25.2) / 0.8);
                }
            }
            case UP -> {
                c = mix(0x1A0D0B, 0x30140F, fbm(p.x, p.z, 63, 2));
                if (p.z < -3.5 && Math.abs(p.x) < 3.3) {
                    c = teeth(p.x + 0.37, 0.5);
                } else if (Math.abs(p.x) > 2.6 && p.z < -1.6) {
                    c = teeth(p.z, 0.5);
                }
                double tongue = Math.hypot(p.x / 1.5, (p.z + 1.6) / 1.7);
                if (tongue < 1 && p.z > -3.4) {
                    c = mix(0x3A1512, 0x220B0A, tongue);
                }
            }
            default -> { }
        }
        return 0xFF000000 | c;
    }

    static int hair(P p) {
        double flow = Math.sin((p.b() * 2.3 + p.a() * 0.5 + fbm(p.a() * 0.9, p.b() * 0.9, 67, 2) * 1.2) * Math.PI * 2);
        double t = Math.pow(c01(0.5 + 0.5 * flow), 3);
        return 0xFF000000 | mix(0x0D0C10, 0x34323D, t * 0.8);
    }

    static int patch(P p) {
        int c = blackLeather(p);
        if (p.f == Face.NORTH) {
            double e = p.edge();
            c = mix(c, 0x4A4852, 0.5 * Math.exp(-(Math.pow((p.u - 0.9) / 0.7, 2) + Math.pow((p.v - 0.7) / 0.5, 2))));
            if (e > 0.13 && e < 0.24 && frac((p.u + p.v) * 2.4) < 0.55) {
                c = 0x77727E;
            }
        }
        return 0xFF000000 | c;
    }

    /** Monocle (plaque découpée) : anneau doré autour de son œil droit, reflet, chaînette qui pend. */
    static int monocle(P p) {
        double r = Math.hypot(p.x - 1.95, p.y - 29.2);
        if (r > 1.2 && r < 1.48) {
            double ang = (-(p.x - 1.95) + (p.y - 29.2)) / r;      // lumière du haut, côté gauche de l'image
            return 0xFF000000 | gold(0.5 + 0.38 * ang + 0.12 * Math.sin(r * 40));
        }
        if (r > 0.82 && r < 0.93) {                                // reflet du verre
            double ang = Math.atan2(p.y - 29.2, -(p.x - 1.95));
            if (ang > 0.5 && ang < 1.5) {
                return 0xFFF4F8FF;
            }
        }
        for (int i = 0; i <= 14; i++) {                             // chaînette
            double t = i / 14.0;
            double cx = 3.25 + 0.95 * t + 0.25 * Math.sin(t * Math.PI), cy = 28.55 - 3.3 * t - 0.5 * Math.sin(t * Math.PI);
            double d = Math.hypot(p.x - cx, p.y - cy);
            if (d < 0.105) {
                return 0xFF000000 | gold(0.85 - d * 5);
            }
        }
        return CUT | G_MID;
    }

    /** Lueur de l'œil (pièce à part, affichée en pleine lumière). */
    static int eye(P p) {
        double r = Math.hypot(p.u - p.w / 2, p.v - p.h / 2) / (p.w / 2);
        if (r > 1) {
            return CUT | 0xFFB030;
        }
        int c = r < 0.28 ? mix(0xFFFFE8, 0xFFE27A, r / 0.28) : mix(0xFFD24A, 0xE0650A, (r - 0.28) / 0.72);
        return 0xFF000000 | c;
    }

    static int strapScales(P p) {
        // jugulaire : écailles dorées qui se recouvrent
        double a = p.a(), b = p.b();
        int row = (int) Math.floor(b / 0.62);
        double fy = frac(b / 0.62);
        double fx = frac(a / 0.6 + ((row & 1) == 0 ? 0 : 0.5)) - 0.5;
        double arc = fy - (0.25 - fx * fx * 1.2);
        if (Math.abs(arc) < 0.1) {
            return 0xFF000000 | gold(0.15);
        }
        return 0xFF000000 | gold(0.45 + 0.45 * (1 - fy) - Math.abs(fx) * 0.3);
    }

    // ---- shako

    static int shako(P p) {
        double a = p.a();
        double fib = fbm(a * 6 + 5 + p.z, p.y * 1.3, 71, 3);
        int c = mix(0x100F13, 0x2A2830, fib);
        c = mix(c, 0x4A4852, 0.3 * sm(38.0, 39.4, p.y) * fib);
        if (p.f == Face.NORTH) {
            for (int k = 0; k < 2; k++) {                          // cordons dorés en feston
                double sag = k == 0 ? 3.5 : 2.3;
                double yc = 38.75 - sag * (1 - Math.pow(p.x / 4.3, 2));
                double d = p.y - yc;
                if (Math.abs(d) < 0.2) {
                    return 0xFF000000 | braid(p.x * 1.2, d / 0.2);
                }
                if (d < 0 && d > -0.38) {
                    c = mul(c, 0.6);
                }
            }
        }
        if (p.f == Face.WEST || p.f == Face.EAST) {
            double d = segDist(p.z, p.y, -4.3, 38.75, 0, 38.35);    // le cordon rejoint la rosette
            double r = Math.hypot(p.z, p.y - 38.35);
            if (r < 0.78) {
                return 0xFF000000 | gold(0.35 + 0.5 * (0.5 + 0.5 * Math.cos(r * 16)) + (r < 0.2 ? 0.2 : 0));
            }
            if (d < 0.18) {
                return 0xFF000000 | braid(p.z * 1.2, d / 0.18 * (p.y > 38.4 ? 1 : -1));
            }
            if (p.f == Face.WEST) {                                 // glands qui pendent à sa gauche
                for (int k = 0; k < 2; k++) {
                    double zc = k == 0 ? -0.45 : 0.5, bot = k == 0 ? 35.4 : 34.6;
                    if (Math.abs(p.z - zc) < 0.11 && p.y < 38.2 && p.y > bot + 0.9) {
                        return 0xFF000000 | braid(p.y * 1.4, (p.z - zc) / 0.11);
                    }
                    double tr = Math.hypot((p.z - zc) / 0.36, (p.y - bot - 0.75) / 0.3);
                    if (tr < 1) {
                        return 0xFF000000 | gold(0.8 - tr * 0.4);
                    }
                    if (p.y <= bot + 0.6 && p.y > bot - 0.5 && Math.abs(p.z - zc) < 0.34) {
                        return 0xFF000000 | gold(0.3 + 0.5 * (0.5 + 0.5 * Math.sin(p.z * Math.PI * 2 * 4.2)));
                    }
                }
            }
        }
        return 0xFF000000 | c;
    }

    static int shakoTop(P p) {
        int c = blackLeather(p);
        if (p.side()) {
            c = mix(c, 0x5A5864, 0.4 * Math.exp(-Math.pow((p.v - 0.35) / 0.18, 2)));    // cuir verni
            if (p.v < 0.2 || p.v > p.h - 0.16) {
                return 0xFF000000 | braid(p.a() * 1.4, 0.3);
            }
        } else if (p.f == Face.UP) {
            double r = Math.hypot(p.x, p.z);
            if (Math.abs(r - 3.9) < 0.07 && frac(Math.atan2(p.z, p.x) * 9) < 0.6) {
                c = 0x6F6A78;                                       // piqûre en cercle
            }
            c = mix(c, 0x4E4C58, 0.45 * Math.exp(-(Math.pow((p.x + 1.6) / 2.2, 2) + Math.pow((p.z + 1.6) / 1.4, 2))));
            if (r < 0.55) {
                c = gold(0.85 - r);
            }
            if (p.edge() < 0.2) {
                c = braid(p.a() * 1.4 + p.b() * 1.4, 0.2);
            }
        }
        return 0xFF000000 | c;
    }

    static int band(P p) {
        if (p.side()) {
            double across = (p.v / p.h) * 2 - 1;
            if (Math.abs(across) > 0.8) {
                return 0xFF000000 | gold(0.18);
            }
            return 0xFF000000 | braid(p.a(), across / 0.8);
        }
        return 0xFF000000 | gold(0.3);
    }

    static int visor(P p) {
        int c = blackLeather(p);
        switch (p.f) {
            case UP -> {
                double gl = Math.exp(-Math.pow((p.v - 1.0) / 0.42, 2)) * (0.65 + 0.35 * fbm(p.x * 1.2, 3, 73, 2));
                c = mix(c, 0x8E90A4, 0.55 * gl);
                if (p.v < 0.24 || p.u < 0.2 || p.u > p.w - 0.2) {
                    c = braid(p.x * 1.4 + p.z * 1.4, 0.2);
                }
            }
            case DOWN -> c = mix(0x10231A, 0x1F3D2B, fbm(p.x * 2, p.z * 2, 75, 2));   // dessous vert, comme les vrais
            default -> c = gold(0.35 + 0.4 * (1 - p.v / p.h));
        }
        return 0xFF000000 | c;
    }

    /** Plaque du shako : soleil gravé et médaillon d'émail bleu à l'orbite d'or (clin d'œil ORBIT). */
    static int plate(P p) {
        if (p.f != Face.NORTH) {
            return 0xFF000000 | gold(p.f == Face.UP || p.f == Face.EAST ? 0.75 : 0.25);
        }
        double dx = -p.x, dy = p.y - 36.3, r = Math.hypot(dx, dy), th = Math.atan2(dy, dx);
        // contour en écusson : coins coupés
        double corner = Math.abs(p.x) / 1.9 + Math.abs(dy) / 2.3;
        if (corner > 1.62) {
            return CUT | G_MID;
        }
        double rays = 0.5 + 0.5 * Math.cos(th * 18);
        int c = gold(0.34 + 0.42 * rays * sm(1.1, 2.0, r) + 0.14 * (fbm(p.x * 3, p.y * 3, 77, 2) - 0.5));
        if (corner > 1.48 || p.edge() < 0.14) {
            c = gold(p.u < 0.14 || p.v < 0.14 || (dx < 0 && dy > 0) ? 0.95 : 0.14);
        }
        if (r < 1.3) {
            if (r > 1.1) {
                c = gold(0.55 + 0.4 * (dx * -0.7 + dy * 0.7) / r);
            } else {
                c = mix(0x0A2585, 0x3F7BFF, c01(0.5 + (-dx * 0.5 + dy * 0.6)));
                if (hash(p.tx / 2, p.ty / 2, 79) < 0.035) {
                    c = 0xEAF2FF;                                   // étoiles
                }
                double ca = Math.cos(Math.toRadians(-24)), sa = Math.sin(Math.toRadians(-24));
                double ex = dx * ca + dy * sa, ey = -dx * sa + dy * ca;
                double el = Math.hypot(ex / 0.92, ey / 0.36);
                if (Math.abs(el - 1) < 0.15 && !(r < 0.4 && ey > 0)) {
                    c = gold(0.88);
                }
                if (r < 0.36) {
                    c = mix(0xFFF0B0, 0xD9962A, c01(r / 0.36 + (dx * 0.6 - dy * 0.6)));
                }
                if (Math.hypot(ex - 0.86, ey - 0.12) < 0.14) {
                    c = 0xFFFFFF;                                   // petit satellite
                }
            }
        }
        return 0xFF000000 | c;
    }

    static int plume(P p) {
        double center = (p.f == Face.WEST || p.f == Face.EAST) ? -3.45 : 0;
        double off = p.side() ? p.a() - center : Math.hypot(p.x, p.z + 3.45);
        double barb = Math.sin((p.y * 2.6 - Math.abs(off) * 2.4 + fbm(p.a() * 2, p.y * 2, 81, 2)) * Math.PI * 2);
        int base = mix(0xC4212C, 0xF6F3EC, sm(42.3, 43.3, p.y + 0.25 * Math.sin(p.a() * 5)));
        int c = mul(base, 0.9 + 0.1 * barb - 0.12 * sm(0.6, 1.5, Math.abs(off)));
        if (p.side() && Math.abs(off) < 0.07) {
            c = mul(c, 0.82);
        }
        return 0xFF000000 | c;
    }

    // ---- torse

    static final double[] ROWS = {16.6, 18.4, 20.2, 22.0};

    static int coat(P p) {
        int c = cloth(p, RED, 11);
        if (p.side()) {
            c = mul(c, 1 - 0.3 * sm(22.6, 24.0, p.y));              // ombre du col et des épaulettes
            c = mul(c, 1 - 0.18 * sm(15.9, 15.2, p.y));
        }
        switch (p.f) {
            case NORTH -> {
                for (double yk : ROWS) {                            // brandebourgs
                    double dy = p.y - yk;
                    for (int s = -1; s <= 1; s += 2) {
                        double r = Math.hypot(p.x - s * 3.05, dy);
                        if (r > 0.27 && r < 0.52) {
                            return 0xFF000000 | gold(0.35 + 0.5 * (0.5 + 0.5 * Math.sin(Math.atan2(dy, p.x - s * 3.05) * 6)));
                        }
                        if (r <= 0.27) {
                            c = mul(c, 0.7);
                        }
                    }
                    if (Math.abs(dy) < 0.24 && Math.abs(p.x) < 2.8) {
                        return 0xFF000000 | braid(p.x * 1.3, dy / 0.24);
                    }
                    if (dy < -0.24 && dy > -0.46 && Math.abs(p.x) < 2.9) {
                        c = mul(c, 0.68);
                    }
                }
                if (Math.abs(p.x) < 0.06) {
                    c = mul(c, 0.55);
                } else if (p.x > 0.06 && p.x < 0.2) {
                    c = mul(c, 1.12);
                }
            }
            case SOUTH -> {
                if (Math.abs(p.x) < 0.07) {
                    c = mul(c, 0.6);
                }
                double curve = 1.1 + 2.2 * Math.pow(c01((p.y - 14.5) / 9.5), 1.6);
                double dsm = Math.abs(Math.abs(p.x) - curve);
                if (dsm < 0.06) {
                    c = mul(c, 0.62);
                } else if (dsm < 0.16) {
                    c = mul(c, 1.08);
                }
                for (int s = -1; s <= 1; s += 2) {                  // boutons de taille
                    double r = Math.hypot(p.x - s * 1.15, p.y - 16.3);
                    if (r < 0.36) {
                        return 0xFF000000 | gold(0.9 - r * 1.6 + (p.x - s * 1.15 < 0 ? -0.1 : 0.1));
                    }
                    if (r < 0.48) {
                        c = mul(c, 0.65);
                    }
                }
            }
            case WEST, EAST -> {
                if (Math.abs(p.z) < 0.06) {
                    c = mul(c, 0.65);
                }
            }
            case UP -> {
                if (Math.abs(p.z) < 0.07) {
                    c = mul(c, 0.65);
                }
            }
            default -> { }
        }
        return 0xFF000000 | c;
    }

    static int collar(P p) {
        int c = cloth(p, NAVY, 13);
        if (p.side()) {
            if (p.v < 0.24 || p.v > p.h - 0.22) {
                return 0xFF000000 | braid(p.a() * 1.4, 0.2);
            }
            if (p.f == Face.NORTH) {
                double ax = Math.abs(p.x);
                if (ax < 0.3 + 0.25 * (1 - p.v / p.h)) {
                    return 0xFF000000 | mix(0x120E0D, 0x2A211C, ax / 0.5);    // ouverture du col
                }
                if (ax < 0.5 + 0.25 * (1 - p.v / p.h)) {
                    return 0xFF000000 | gold(0.7);
                }
                if (ax > 0.85 && ax < 2.35 && frac((ax - p.y * 0.7) * 1.9) < 0.34 && p.v > 0.42 && p.v < p.h - 0.4) {
                    return 0xFF000000 | gold(0.55 + 0.3 * frac(ax * 3));       // broderie
                }
            }
        } else if (p.edge() < 0.24) {
            return 0xFF000000 | braid(p.a() * 1.4 + p.b() * 1.4, 0.2);
        } else {
            c = 0x15100E;
        }
        return 0xFF000000 | c;
    }

    static int belt(P p) {
        int c = blackLeather(p);
        if (p.side()) {
            c = mix(c, 0x55535E, 0.35 * Math.exp(-Math.pow((p.v - 0.45) / 0.2, 2)));
            double e = Math.min(p.v, p.h - p.v);
            if (e > 0.12 && e < 0.2 && frac(p.a() * 2.6) < 0.6) {
                c = 0x7C7784;
            }
        }
        return 0xFF000000 | c;
    }

    static int buckle(P p) {
        if (p.f != Face.NORTH) {
            return 0xFF000000 | gold(p.f == Face.UP ? 0.8 : 0.3);
        }
        double e = p.edge();
        if (e < 0.34) {
            return 0xFF000000 | gold(e < 0.1 ? (p.u < 0.1 || p.v < 0.1 ? 0.95 : 0.15) : 0.62 + 0.2 * Math.sin((p.u + p.v) * 9));
        }
        if (Math.abs(p.v - p.h / 2) < 0.11) {
            return 0xFF000000 | gold(0.8);                           // ardillon
        }
        return 0xFF000000 | blackLeather(p);
    }

    static int button(P p) {
        if (p.f == Face.NORTH || p.f == Face.SOUTH || ((p.f == Face.WEST || p.f == Face.EAST) && p.w > 0.5)) {
            double r = Math.hypot(p.u - p.w / 2, p.v - p.h / 2) / (p.w / 2);
            if (r > 1.02) {
                return CUT | G_LO;
            }
            double hl = Math.exp(-(Math.pow((p.u - p.w * 0.36) / (p.w * 0.2), 2) + Math.pow((p.v - p.h * 0.34) / (p.h * 0.2), 2)));
            return 0xFF000000 | gold(r > 0.8 ? 0.2 : 0.55 + 0.45 * hl - 0.2 * r);
        }
        return 0xFF000000 | gold(0.3);
    }

    static int whiteBelt(P p) {
        double n = fbm(p.u * 1.6 + p.z, p.v * 1.6, 91, 3);
        int c = mix(0xCFC7B1, 0xF6F2E6, n);
        if (p.f == Face.NORTH || p.f == Face.SOUTH) {
            double e = Math.min(p.u, p.w - p.u);
            if (e < 0.07) {
                c = mul(c, 0.72);
            } else if (e > 0.2 && e < 0.29 && frac(p.v * 2.3) < 0.58) {
                c = 0x9F957B;                                        // piqûre
            }
            double crease = Math.abs(fbm(p.u * 0.6, p.v * 1.1, 93, 2) - 0.5);
            if (crease < 0.014) {
                c = mul(c, 0.84);
            }
        } else {
            c = mul(c, 0.8);
        }
        return 0xFF000000 | c;
    }

    static int beltPlate(P p) {
        if (p.f != Face.NORTH) {
            return 0xFF000000 | gold(0.3);
        }
        double r = Math.hypot((p.u - p.w / 2) / (p.w / 2), (p.v - p.h / 2) / (p.h / 2));
        if (r > 1) {
            return CUT | G_MID;
        }
        if (r > 0.78) {
            return 0xFF000000 | gold(0.5 + 0.4 * ((p.w / 2 - p.u) + (p.h / 2 - p.v)));
        }
        double star = Math.cos(Math.atan2(p.v - p.h / 2, p.u - p.w / 2) * 5);
        return 0xFF000000 | gold(0.45 + 0.35 * (star > 0.2 * r * 4 ? 1 : 0) * (1 - r));
    }

    static int epaulette(P p) {
        boolean out = Math.abs(p.x) > 7.1;
        if (p.f == Face.UP) {
            if (p.edge() < 0.28) {
                return 0xFF000000 | braid(p.x * 1.5 + p.z * 1.5, (p.edge() / 0.28) * 2 - 1);
            }
            double br = Math.hypot(Math.abs(p.x) - 3.7, p.z);       // bouton côté col
            if (br < 0.42) {
                return 0xFF000000 | gold(0.95 - br * 1.5);
            }
            if (out) {                                               // croissant bombé
                return 0xFF000000 | gold(0.5 + 0.4 * Math.sin((Math.abs(p.x) - 7.1) / 1.1 * Math.PI) + 0.1 * Math.sin(p.z * 8));
            }
            boolean chk = ((int) Math.floor(p.x * 2.1) + (int) Math.floor(p.z * 2.1) & 1) == 0;
            double w = chk ? Math.sin(p.x * Math.PI * 2 * 4.2) : Math.sin(p.z * Math.PI * 2 * 4.2);
            return 0xFF000000 | gold(0.48 + 0.28 * w);
        }
        if (p.f == Face.DOWN) {
            return 0xFF000000 | gold(0.15);
        }
        return 0xFF000000 | braid(p.a() * 1.5, (p.v / p.h) * 2 - 1);
    }

    static int fringe(P p) {
        if (!p.side()) {
            return 0xFF000000 | gold(0.2);
        }
        double a = p.a() * 2.5;
        int k = (int) Math.floor(a);
        double f = frac(a) - 0.5;
        double len = p.h - 0.1 - 0.4 * hash(k, p.f.ordinal(), 95);
        if (p.v < 0.3) {
            return 0xFF000000 | braid(p.a() * 1.5, p.v / 0.15 - 1);
        }
        if (p.v > len || Math.abs(f) > 0.42 || (p.v > len - 0.2 && Math.hypot(f / 0.42, (p.v - (len - 0.2)) / 0.2) > 1)) {
            return CUT | G_LO;
        }
        double twist = Math.sin((p.v * 3.2 + f * 1.2) * Math.PI * 2);
        return 0xFF000000 | gold((0.3 + 0.55 * (1 - 4 * f * f)) * (0.85 + 0.15 * twist));
    }

    static int coatTail(P p) {
        int c = cloth(p, RED, 17);
        if (p.f == Face.SOUTH || p.f == Face.NORTH) {
            double ax = Math.abs(p.x), drop = 14.5 - p.y;
            double split = 0.06 + 0.1 * drop;
            if (ax < split) {
                return cut(c);                                       // fente du dos
            }
            double turn = 0.25 + drop * 0.42;                        // retroussis bleu bordé d'or
            if (ax < turn && drop > 0.6) {
                c = cloth(p, NAVY, 19);
                if (turn - ax < 0.2 || ax - split < 0.16) {
                    return 0xFF000000 | braid(p.y * 1.5, 0.2);
                }
            } else if (ax < turn + 0.12 && drop > 0.6) {
                c = mul(c, 0.7);
            }
            if (p.v > p.h - 0.22) {
                return 0xFF000000 | braid(p.x * 1.5, 0.2);
            }
            if (ax > 4.15 - 0.2) {
                return 0xFF000000 | braid(p.y * 1.5, 0.2);
            }
        }
        return 0xFF000000 | c;
    }

    static int quiver(P p) {
        double a = p.a(), b = p.b();
        double n = fbm(a * 1.4 + p.z * 0.8, b * 1.4, 101, 3);
        int c = mix(0x48290F, 0x8A5730, n);
        double l1 = Math.abs(frac((a + b) * 0.85) - 0.5), l2 = Math.abs(frac((a - b) * 0.85) - 0.5);
        double line = Math.min(l1, l2);
        if (line < 0.04) {
            c = mul(c, 0.55);                                        // cuir repoussé en losanges
        } else if (line < 0.09) {
            c = mul(c, 1.18);
        }
        if (l1 < 0.09 && l2 < 0.09) {
            c = gold(0.8);                                           // clous
        }
        if (p.side() && p.v > p.h - 1.3) {                           // culot renforcé
            c = mix(0x2E190A, 0x4B2B14, n);
            if (p.v < p.h - 1.12 && frac(p.a() * 2.4) < 0.55) {
                c = 0xC9B48A;
            }
        }
        return 0xFF000000 | c;
    }

    static int quiverRim(P p) {
        if (p.f == Face.UP) {
            return 0xFF000000 | (p.edge() < 0.3 ? gold(0.6) : 0x120B06);
        }
        return 0xFF000000 | braid(p.a() * 1.4, (p.v / p.h) * 2 - 1);
    }

    static int wood(double along, double across, int seed) {
        double g = Math.sin((across * 5 + fbm(along * 0.7, across * 3, seed, 3) * 5) * Math.PI);
        return mix(0x3F2610, 0x8A5A2A, 0.5 + 0.45 * g);
    }

    static int shaft(P p) {
        return 0xFF000000 | wood(p.b(), p.a(), 105);
    }

    /** Empenne (plaque découpée) : plume de couleur, barbes en chevrons. */
    static Mat feather(int color) {
        return p -> {
            double c = p.u - p.w / 2, t = p.v / p.h;
            double half = (p.w / 2) * (t < 0.2 ? t / 0.2 : 1 - 0.35 * sm(0.75, 1, t));
            if (Math.abs(c) > half) {
                return CUT | color;
            }
            if (Math.abs(c) < 0.06) {
                return 0xFF000000 | 0x7A5A32;
            }
            double barb = Math.sin((p.v * 4 - Math.abs(c) * 5) * Math.PI * 2);
            if (barb > 0.86 && Math.abs(c) > half - 0.25) {
                return CUT | color;                                  // barbes ébouriffées
            }
            return 0xFF000000 | mul(color, 0.82 + 0.16 * barb);
        };
    }

    // ---- bras et arc

    static int sleeve(P p) {
        int c = cloth(p, RED, 23);
        if (p.side()) {
            c = mul(c, 1 - 0.26 * sm(22.4, 24.0, p.y));              // ombre de l'épaulette
            double wr = Math.sin((p.y * 1.3 + fbm(p.a() * 0.6 + p.z, p.y * 0.4, 25, 2) * 1.5) * Math.PI * 2);
            c = mul(c, 1 + 0.09 * wr * sm(16.6, 17.6, p.y) * sm(19.6, 18.6, p.y));    // plis du coude
            boolean outer = (p.f == Face.EAST && p.x > 0) || (p.f == Face.WEST && p.x < 0);
            if (outer) {
                for (double yk : new double[]{19.3, 20.4, 21.5}) {  // chevrons de grade
                    double d = (p.y - yk) + Math.abs(p.z) * 0.55;
                    if (Math.abs(d) < 0.2 && Math.abs(p.z) < 1.55) {
                        return 0xFF000000 | braid(p.z * 1.6, d / 0.2);
                    }
                    if (d < -0.2 && d > -0.36 && Math.abs(p.z) < 1.6) {
                        c = mul(c, 0.7);
                    }
                }
            }
        } else if (Math.abs(p.z) < 0.07) {
            c = mul(c, 0.65);
        }
        return 0xFF000000 | c;
    }

    static int cuff(P p) {
        int c = cloth(p, NAVY, 27);
        if (p.side()) {
            if (p.v < 0.26) {
                return 0xFF000000 | braid(p.a() * 1.5, p.v / 0.13 - 1);
            }
            boolean outer = (p.f == Face.EAST && p.x > 0) || (p.f == Face.WEST && p.x < 0);
            if (outer) {
                for (double zc : new double[]{-0.9, 0.9}) {
                    double r = Math.hypot(p.z - zc, p.y - 14.6);
                    if (r < 0.34) {
                        return 0xFF000000 | gold(0.9 - r * 1.7);
                    }
                    if (r < 0.46) {
                        c = mul(c, 0.6);
                    }
                }
            }
            double pt = (p.v - 0.26) - (0.9 - Math.abs(p.u - p.w / 2) * 0.75);   // pointe dorée
            if (p.f == Face.NORTH && Math.abs(pt) < 0.1 && Math.abs(p.u - p.w / 2) < 1.2) {
                return 0xFF000000 | gold(0.7);
            }
        } else if (p.f == Face.DOWN) {
            c = 0x0E1226;
        }
        return 0xFF000000 | c;
    }

    static int hand(P p) {
        int c = bone(p);
        if (p.side()) {
            double f = frac(p.a() / 0.66 + 0.1);
            if (p.v > 0.75 && (f < 0.1 || f > 0.9)) {
                c = 0x2A2118;                                        // entre les doigts
            } else if (p.v > 0.75) {
                c = mul(c, 0.9 + 0.2 * (0.5 - Math.abs(f - 0.5)));
            }
            if (Math.abs(p.v - 1.45) < 0.07 || Math.abs(p.v - 0.72) < 0.06) {
                c = mul(c, 0.62);                                    // phalanges
            }
            if (p.v < 0.7 && hash((int) Math.floor(p.a() / 0.45), (int) Math.floor(p.v / 0.35), 111) < 0.5) {
                c = mul(c, 0.88);                                    // petits os du poignet
            }
        }
        return 0xFF000000 | c;
    }

    /** Bois de l'arc : {@code alongZ} = segment couché le long de Z (sinon le long de Y). */
    static Mat bow(boolean alongZ) {
        return p -> {
            double along = alongZ ? p.z : p.y, across = alongZ ? (p.side() && p.f != Face.NORTH && p.f != Face.SOUTH ? p.y : p.x) : p.a();
            return 0xFF000000 | wood(along, across, 107);
        };
    }

    static int grip(P p) {
        double wrap = Math.sin((p.z * 2.2 + p.y * 2.2 + p.x * 2.2) * Math.PI * 2);
        int c = mix(0x5A1016, 0x8E1F27, 0.5 + 0.4 * wrap);
        if (wrap > 0.9) {
            c = gold(0.8);                                           // fil d'or
        }
        return 0xFF000000 | c;
    }

    static int horn(P p) {
        return 0xFF000000 | mix(0xE4DCC6, 0x7A6D52, fbm(p.z * 2, p.y * 2, 109, 2));
    }

    static int string(P p) {
        return 0xFF000000 | mix(0xD6CFBB, 0xF7F3E8, frac((p.z + p.y) * 3) < 0.5 ? 0 : 1);
    }

    // ---- jambes

    static int trousers(P p) {
        int c = cloth(p, GREY, 29);
        boolean outer = (p.f == Face.EAST && p.x > 2) || (p.f == Face.WEST && p.x < -2);
        if (outer) {
            double az = Math.abs(p.z);
            if (az < 0.62) {
                c = cloth(p, NAVY, 30);                              // bande de pantalon
            }
            if (az > 0.62 && az < 0.84) {
                return 0xFF000000 | braid(p.y * 1.6, 0.2);
            }
        }
        if (p.f == Face.NORTH) {
            double knee = Math.hypot((p.u - p.w / 2) / 1.3, (p.y - 8.9) / 1.0);
            if (knee < 1) {
                c = mul(c, 1.1 - 0.1 * knee);                        // genou usé
            }
            double wr = Math.abs(fbm(p.x * 0.6, p.y * 1.8, 35, 2) - 0.5);
            if (wr < 0.016) {
                c = mul(c, 0.8);
            }
        }
        if (p.side()) {
            c = mul(c, 1 - 0.25 * sm(10.4, 11.4, p.y));              // ombre de la cotte
        }
        return 0xFF000000 | c;
    }

    static int boot(P p) {
        int c = blackLeather(p);
        if (p.side()) {
            if (p.y < 0.5) {
                return 0xFF000000 | mix(0x1A120C, 0x33241A, fbm(p.a() * 3, p.y * 6, 121, 2));     // semelle
            }
            if (p.y < 0.68) {
                return 0xFF000000 | (frac(p.a() * 3.2) < 0.6 ? 0x8A7150 : 0x54402B);               // trépointe cousue
            }
            double gl = Math.exp(-Math.pow((p.u - p.w * 0.38) / 0.5, 2)) * (0.6 + 0.4 * fbm(p.a(), p.y * 0.7, 123, 2));
            c = mix(c, 0x7A7C90, 0.42 * gl * sm(0.7, 2.0, p.y));
            double wr = Math.sin((p.y * 1.55 + fbm(p.a() * 0.9 + p.z, p.y * 0.5, 125, 2) * 1.7) * Math.PI * 2);
            if (p.y > 2.3 && p.y < 4.9) {                            // plis de la cheville
                if (wr > 0.8) {
                    c = mix(c, 0x6A6C80, 0.5);
                } else if (wr < -0.75) {
                    c = mul(c, 0.5);
                }
            }
            if (p.f == Face.SOUTH && Math.abs(p.u - p.w / 2) < 0.06) {
                c = mul(c, 0.5);                                     // couture arrière
            }
        } else if (p.f == Face.DOWN) {
            c = mix(0x1A120C, 0x2C2017, fbm(p.x * 2, p.z * 2, 127, 2));
            if (frac(p.z * 1.5) < 0.22) {
                c = mul(c, 0.6);
            }
        }
        return 0xFF000000 | c;
    }

    static int bootCuff(P p) {
        int c = blackLeather(p);
        if (p.side()) {
            c = mix(c, 0x6A6C80, 0.35 * Math.exp(-Math.pow((p.v - 0.7) / 0.25, 2)));
            if (p.v < 0.24) {
                return 0xFF000000 | braid(p.a() * 1.5, p.v / 0.12 - 1);
            }
            double e = p.h - p.v;
            if (e > 0.13 && e < 0.21 && frac(p.a() * 2.6) < 0.58) {
                c = 0x7C7784;
            }
        } else if (p.f == Face.UP) {
            c = p.edge() < 0.3 ? braid(p.a() * 1.5 + p.b() * 1.5, 0.2) : 0x0C0B0D;
        }
        return 0xFF000000 | c;
    }

    static int toe(P p) {
        int c = blackLeather(p);
        if (p.side() && p.y < 0.5) {
            return 0xFF000000 | mix(0x1A120C, 0x33241A, fbm(p.a() * 3, p.y * 6, 121, 2));
        }
        if (p.side() && p.y < 0.68) {
            return 0xFF000000 | (frac(p.a() * 3.2) < 0.6 ? 0x8A7150 : 0x54402B);
        }
        if (p.f == Face.UP) {
            c = mix(c, 0x9A9CB2, 0.6 * Math.exp(-(Math.pow((p.u - p.w * 0.4) / 0.9, 2) + Math.pow((p.v - p.h * 0.45) / 0.3, 2))));
            if (p.v > p.h - 0.16 && frac(p.x * 2.6) < 0.58) {
                c = 0x7C7784;
            }
        } else if (p.f == Face.NORTH) {
            c = mix(c, 0x7A7C90, 0.4 * Math.exp(-Math.pow((p.v - 0.5) / 0.3, 2)));
        }
        return 0xFF000000 | c;
    }

    // ------------------------------------------------------------------ le modèle

    static final Face N = Face.NORTH, S = Face.SOUTH, W = Face.WEST, E = Face.EAST, U = Face.UP, DN = Face.DOWN;

    static List<Part> build() {
        List<Part> parts = new ArrayList<>();

        // ---- tête (pivot : base du cou) ----
        Part head = new Part("tete", 0, 24, 0);
        parts.add(head);
        head.add(-4, 26, -4, 4, 32, 4, ElexyrGen::skull);
        head.add(-4.35, 24.2, -1.2, 4.35, 32.3, 4.35, ElexyrGen::mailM).no(U);                       // camail
        head.add(-4.55, 23.5, -0.8, 4.55, 25.3, 4.55, p -> mailHem(p, 0.55)).no(U, DN);               // bas du camail
        head.add(0.55, 30.45, -4.3, 3.6, 31.0, -4, ElexyrGen::boneRelief).no(S).ao(0.7);              // arcades sourcilières
        head.add(-3.75, 30.6, -4.28, -0.9, 31.05, -4, ElexyrGen::boneRelief).no(S).ao(0.7);
        for (int s = -1; s <= 1; s += 2) {                                                             // pommettes
            head.add(s * 2.2, 27.35, -4.26, s * 4.08, 27.95, -3.2, ElexyrGen::boneRelief).no(S).ao(0.7);
        }
        head.add(-0.3, 28.3, -4.3, 0.3, 29.9, -4, ElexyrGen::boneRelief).no(S).ao(0.7);               // arête du nez
        head.add(-3.7, 27.95, -4.42, -0.95, 30.55, -4, ElexyrGen::patch).no(S);                       // cache-œil
        head.add(0.4, 25.0, -4.52, 4.4, 30.8, -4.45, ElexyrGen::monocle).only(N).ao(0);               // monocle
        for (int s = -1; s <= 1; s += 2) {                                                             // jugulaires
            head.add(s * 4.0, 25.4, -2.45, s * 4.32, 32.4, -1.2, ElexyrGen::strapScales);
        }
        // moustache en guidon
        head.add(-1.7, 26.35, -4.85, 1.7, 27.3, -4, ElexyrGen::hair).no(S);
        for (int s = -1; s <= 1; s += 2) {
            head.add(s * 1.7, 26.05, -4.8, s * 3.5, 26.95, -4, ElexyrGen::hair).no(S);
            head.add(s * 3.5, 26.25, -4.75, s * 4.45, 27.55, -3.9, ElexyrGen::hair);
            head.add(s * 4.45, 27.05, -4.7, s * 5.1, 28.35, -3.95, ElexyrGen::hair);
            head.add(s * 4.2, 28.2, -4.65, s * 4.85, 28.85, -4.0, ElexyrGen::hair);
        }
        // shako
        head.add(-4.3, 32.4, -4.3, 4.3, 39.4, 4.3, ElexyrGen::shako).no(U, DN);
        head.add(-4.55, 32.3, -4.55, 4.55, 33.55, 4.55, ElexyrGen::band);
        head.add(-4.8, 39.4, -4.8, 4.8, 40.5, 4.8, ElexyrGen::shakoTop);
        head.add(-4.5, 31.9, -7.1, 4.5, 32.4, -4.0, ElexyrGen::visor).rot('x', -22.5, 0, 32.4, -4.3);
        head.add(-1.9, 34.0, -4.82, 1.9, 38.6, -4.3, ElexyrGen::plate).no(S);
        // plumet blanc sur rouge
        head.add(-0.45, 40.5, -3.9, 0.45, 41.6, -3.0, ElexyrGen::goldPlain);
        double[][] tuft = {{41.4, 42.4, 0.9}, {42.4, 43.7, 1.35}, {43.7, 45.1, 1.5}, {45.1, 46.1, 1.2}, {46.1, 46.8, 0.7}};
        for (double[] t : tuft) {
            head.add(-t[2], t[0], -3.45 - t[2], t[2], t[1], -3.45 + t[2], ElexyrGen::plume).ao(0.6);
        }

        // ---- œil (même pivot que la tête, affiché en pleine lumière) ----
        Part eye = new Part("oeil", 0, 24, 0);
        parts.add(eye);
        eye.add(1.4, 28.65, -4.1, 2.5, 29.75, -4.04, ElexyrGen::eye).only(N).ao(0);

        // ---- mâchoire de casse-noisette (pivot : charnière) ----
        Part jaw = new Part("machoire", 0, 26, 1.0);
        parts.add(jaw);
        jaw.add(-3.3, 23.9, -4.3, 3.3, 26, 0.8, ElexyrGen::jaw);

        // ---- torse (pivot : bassin) ----
        Part t = new Part("torse", 0, 12, 0);
        parts.add(t);
        t.add(-4, 14.5, -2, 4, 24, 2, ElexyrGen::coat);
        t.add(-4.3, 10.4, -2.3, 4.3, 14.5, 2.3, p -> mailHem(p, 0.7)).no(U, DN);                       // cotte de mailles
        t.add(-4.4, 14.2, -2.4, 4.4, 15.6, 2.4, ElexyrGen::belt);
        t.add(-1.05, 13.95, -2.8, 1.05, 15.85, -2.4, ElexyrGen::buckle).no(S);
        t.add(-2.7, 23.2, -2.45, 2.7, 25.0, 2.45, ElexyrGen::collar).no(DN);
        for (double yk : ROWS) {
            for (int s = -1; s <= 1; s += 2) {
                t.add(s * 1.5 - 0.4, yk - 0.4, -2.42, s * 1.5 + 0.4, yk + 0.4, -2.0, ElexyrGen::button).no(S).ao(0);
            }
        }
        // baudrier blanc (épaule droite -> hanche gauche), devant et dos, avec sa plaque
        t.add(-0.95, 14.0, -2.58, 0.95, 24.6, -2.05, ElexyrGen::whiteBelt).rot('z', -45, 0, 19.3, 0).no(S, U, DN);
        t.add(-0.95, 14.0, 2.05, 0.95, 24.6, 2.4, ElexyrGen::whiteBelt).rot('z', -45, 0, 19.3, 0).no(N, U, DN);
        t.add(-0.85, 18.45, -2.85, 0.85, 20.15, -2.58, ElexyrGen::beltPlate).rot('z', -45, 0, 19.3, 0).only(N).ao(0);
        // épaulettes à franges
        for (int s = -1; s <= 1; s += 2) {
            t.add(s * 3.0, 23.7, -2.7, s * 8.5, 24.7, 2.7, ElexyrGen::epaulette);
            Box fr = t.add(s * 7.25, 21.6, -2.82, s * 8.72, 23.8, 2.82, ElexyrGen::fringe).no(U, DN);
            fr.no(s > 0 ? W : E);
        }
        // basques de l'habit
        t.add(-4.15, 9.0, 1.3, 4.15, 14.5, 2.6, ElexyrGen::coatTail).no(U, N);
        // carquois dans le dos, flèches par-dessus l'épaule droite
        double qx = 1.2, qy = 18.0;
        t.add(qx - 1.4, qy - 5.2, 2.65, qx + 1.4, qy + 4.9, 4.75, ElexyrGen::quiver).rot('z', -45, qx, qy, 0).no(U);
        t.add(qx - 1.6, qy + 4.4, 2.5, qx + 1.6, qy + 5.2, 4.9, ElexyrGen::quiverRim).rot('z', -45, qx, qy, 0);
        double[][] arrows = {{-0.8, 3.15, 9.8}, {0.15, 3.5, 10.6}, {0.9, 3.2, 9.4}, {-0.35, 4.15, 10.2}, {0.65, 4.25, 9.0}};
        int[] feathers = {0xD8262F, 0xF2EEE2, 0xE3A832, 0xF2EEE2, 0x2C5FE0};
        for (int i = 0; i < arrows.length; i++) {
            double ax = qx + arrows[i][0], az = arrows[i][1], top = qy + arrows[i][2];
            t.add(ax - 0.14, qy + 4.6, az - 0.14, ax + 0.14, top, az + 0.14, ElexyrGen::shaft).rot('z', -45, qx, qy, 0).ao(0);
            t.add(ax - 0.55, top - 2.5, az - 0.03, ax + 0.55, top - 0.3, az + 0.03, feather(feathers[i]))
                    .rot('z', -45, qx, qy, 0).only(N, S).ao(0);
            t.add(ax - 0.03, top - 2.5, az - 0.55, ax + 0.03, top - 0.3, az + 0.55, feather(feathers[i]))
                    .rot('z', -45, qx, qy, 0).only(W, E).ao(0);
        }

        // ---- bras (pivot : épaule) ----
        for (int s = 1; s >= -1; s -= 2) {
            Part a = new Part(s > 0 ? "bras_d" : "bras_g", s * 6, 22.8, 0);
            parts.add(a);
            a.add(s * 4, 15.6, -2, s * 8, 24, 2, ElexyrGen::sleeve);
            a.add(s * 3.75, 13.3, -2.25, s * 8.25, 16.2, 2.25, ElexyrGen::cuff);
            a.add(s * 4.7, 10.9, -1.4, s * 7.3, 13.3, 1.4, ElexyrGen::hand);
            a.add(s * 4.25, 11.5, -1.95, s * 4.75, 13.0, -0.85, ElexyrGen::hand);                    // pouce
            if (s < 0) {
                bowInto(a, -6.0, 11.7, -0.2);
            }
        }

        // ---- jambes (pivot : hanche) ----
        for (int s = 1; s >= -1; s -= 2) {
            Part l = new Part(s > 0 ? "jambe_d" : "jambe_g", s * 2, 12, 0);
            parts.add(l);
            l.add(s * 0.0, 6.6, -2, s * 4, 12, 2, ElexyrGen::trousers);
            l.add(s * 0.03, 0, -2.35, s * 4.35, 7.2, 2.35, ElexyrGen::boot);
            l.add(s * 0.01, 6.5, -2.6, s * 4.6, 8.0, 2.6, ElexyrGen::bootCuff);
            l.add(s * 0.35, 0, -3.5, s * 4.1, 2.3, -2.35, ElexyrGen::toe).no(S);
            l.add(s * 1.25, 1.1, 2.35, s * 2.75, 1.8, 3.2, ElexyrGen::goldPlain);                    // éperon
            l.add(s * 1.75, 0.9, 3.2, s * 2.25, 2.0, 3.7, ElexyrGen::goldPlain);
        }
        return parts;
    }

    /** Direction dans le plan YZ : angle {@code phi} depuis l'avant (-Z) en montant vers +Y. */
    static double[] dir(double phi) {
        double r = Math.toRadians(phi);
        return new double[]{Math.sin(r), -Math.cos(r)};
    }

    /** Segment de l'arc dans le plan YZ : part de (y, z), longueur {@code len}, direction {@code phi} ; renvoie son bout. */
    static double[] seg(Part part, double x, double hx, double thick, double y, double z, double len, double phi, Mat alongZ, Mat alongY) {
        phi = ((phi + 180) % 360 + 360) % 360 - 180;
        Box b;
        if (phi >= -45 && phi <= 45) {
            b = part.add(x - hx, y - thick, z - len, x + hx, y + thick, z, alongZ).rot('x', phi, x, y, z);
        } else if (phi > 45 && phi < 135) {
            b = part.add(x - hx, y, z - thick, x + hx, y + len, z + thick, alongY).rot('x', phi - 90, x, y, z);
        } else if (phi < -45 && phi > -135) {
            b = part.add(x - hx, y - len, z - thick, x + hx, y, z + thick, alongY).rot('x', phi + 90, x, y, z);
        } else {
            b = part.add(x - hx, y - thick, z, x + hx, y + thick, z + len, alongZ).rot('x', phi > 0 ? phi - 180 : phi + 180, x, y, z);
        }
        b.ao(0.4);
        double[] d = dir(phi);
        return new double[]{y + d[0] * len, z + d[1] * len};
    }

    /** Arc tenu par la poignée, branches vers le bas et corde en dessous (comme un arc porté à bout de bras). */
    static void bowInto(Part a, double x, double gy, double gz) {
        double axis = 22.5;                                         // l'arc pointe vers l'avant en montant un peu
        double[] f = dir(axis), bk = dir(axis + 180);
        Mat wz = bow(true), wy = bow(false);
        // poignée gainée
        seg(a, x, 0.5, 0.5, gy + bk[0] * 1.6, gz + bk[1] * 1.6, 3.2, axis, ElexyrGen::grip, ElexyrGen::grip);
        double[] p1 = seg(a, x, 0.42, 0.42, gy + f[0] * 1.6, gz + f[1] * 1.6, 5.2, axis - 22.5, wz, wy);
        double[] p2 = seg(a, x, 0.36, 0.34, p1[0], p1[1], 4.2, axis - 45, wz, wy);
        double[] p3 = seg(a, x, 0.3, 0.27, p2[0], p2[1], 0.9, axis - 45, ElexyrGen::horn, ElexyrGen::horn);
        double[] q1 = seg(a, x, 0.42, 0.42, gy + bk[0] * 1.6, gz + bk[1] * 1.6, 5.2, axis + 180 + 22.5, wz, wy);
        double[] q2 = seg(a, x, 0.36, 0.34, q1[0], q1[1], 4.2, axis + 180 + 45, wz, wy);
        double[] q3 = seg(a, x, 0.3, 0.27, q2[0], q2[1], 0.9, axis + 180 + 45, ElexyrGen::horn, ElexyrGen::horn);
        double len = Math.hypot(p3[0] - q3[0], p3[1] - q3[1]) - 0.5;
        double[] d = dir(axis);
        seg(a, x, 0.09, 0.09, q3[0] + d[0] * 0.25 + 0.15, q3[1] + d[1] * 0.25, len, axis, ElexyrGen::string, ElexyrGen::string);
    }

    // ------------------------------------------------------------------ atlas

    record Slot(Box b, Face f, int w, int h) {
    }

    static BufferedImage paint(List<Part> parts) {
        List<Slot> slots = new ArrayList<>();
        for (Part part : parts) {
            for (Box b : part.boxes) {
                for (Face f : b.faces) {
                    slots.add(new Slot(b, f, Math.max(2, (int) Math.round(b.fw(f) * D)), Math.max(2, (int) Math.round(b.fh(f) * D))));
                }
            }
        }
        slots.sort((s1, s2) -> s2.h != s1.h ? s2.h - s1.h : s2.w - s1.w);
        int x = PAD, y = PAD, rowH = 0;
        for (Slot s : slots) {
            if (x + s.w + PAD > ATLAS) {
                x = PAD;
                y += rowH + 2 * PAD;
                rowH = 0;
            }
            if (y + s.h + PAD > ATLAS) {
                throw new IllegalStateException("Atlas trop petit (" + ATLAS + ") : baisser D ou agrandir.");
            }
            s.b.rect[s.f.ordinal()] = new int[]{x, y, s.w, s.h};
            x += s.w + 2 * PAD;
            rowH = Math.max(rowH, s.h);
        }
        System.out.println("Atlas : " + slots.size() + " faces, rempli jusqu'à la ligne " + (y + rowH) + " / " + ATLAS);
        BufferedImage img = new BufferedImage(ATLAS, ATLAS, BufferedImage.TYPE_INT_ARGB);
        for (int j = 0; j < ATLAS; j++) {
            for (int i = 0; i < ATLAS; i++) {
                img.setRGB(i, j, 0x00402020);
            }
        }
        slots.parallelStream().forEach(s -> {
            P p = new P();
            p.b = s.b;
            p.f = s.f;
            p.w = s.b.fw(s.f);
            p.h = s.b.fh(s.f);
            int[] r = s.b.rect[s.f.ordinal()];
            double ew = Math.min(0.5, Math.min(p.w, p.h) * 0.3);
            for (int j = -PAD; j < s.h + PAD; j++) {
                for (int i = -PAD; i < s.w + PAD; i++) {
                    int ci = Math.max(0, Math.min(s.w - 1, i)), cj = Math.max(0, Math.min(s.h - 1, j));
                    p.u = (ci + 0.5) / s.w * p.w;
                    p.v = (cj + 0.5) / s.h * p.h;
                    p.tx = r[0] + ci;
                    p.ty = r[1] + cj;
                    s.b.point(s.f, p.u, p.v, p);
                    int c = s.b.mat.at(p);
                    if ((c >>> 24) >= 128 && s.b.ao > 0) {
                        c = mul(c, 1 - 0.2 * s.b.ao * (1 - sm(0, ew, p.edge())));     // arêtes assombries
                    }
                    synchronized (img) {
                        img.setRGB(r[0] + i, r[1] + j, (c >>> 24) >= 128 ? c | 0xFF000000 : c & 0x00FFFFFF);
                    }
                }
            }
        });
        return img;
    }

    // ------------------------------------------------------------------ fichiers du pack

    static String n(double v) {
        String s = String.format(Locale.ROOT, "%.4f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.equals("-0") ? "0" : s;
    }

    static void writeModels(Path pack, List<Part> parts) throws IOException {
        Path models = pack.resolve("assets/orbitdev/models/item"), items = pack.resolve("assets/orbitdev/items");
        Files.createDirectories(models);
        Files.createDirectories(items);
        for (Part part : parts) {
            StringBuilder sb = new StringBuilder("{\n  \"textures\": {\"t\": \"orbitdev:item/elexyr\", \"particle\": \"orbitdev:item/elexyr\"},\n  \"elements\": [\n");
            for (int k = 0; k < part.boxes.size(); k++) {
                Box b = part.boxes.get(k);
                double[] from = {b.x0 - part.px + 8, b.y0 - part.py + 8, b.z0 - part.pz + 8};
                double[] to = {b.x1 - part.px + 8, b.y1 - part.py + 8, b.z1 - part.pz + 8};
                for (int i = 0; i < 3; i++) {
                    if (from[i] < -16 || to[i] > 32) {
                        throw new IllegalStateException(part.name + " : cube hors des limites du jeu (-16..32) : " + from[i] + " / " + to[i]);
                    }
                }
                sb.append("    {\"from\": [").append(n(from[0])).append(", ").append(n(from[1])).append(", ").append(n(from[2]))
                        .append("], \"to\": [").append(n(to[0])).append(", ").append(n(to[1])).append(", ").append(n(to[2])).append("], ");
                if (b.axis != 0) {
                    sb.append("\"rotation\": {\"origin\": [").append(n(b.ox - part.px + 8)).append(", ").append(n(b.oy - part.py + 8)).append(", ")
                            .append(n(b.oz - part.pz + 8)).append("], \"axis\": \"").append(b.axis).append("\", \"angle\": ").append(n(b.angle)).append("}, ");
                }
                sb.append("\"faces\": {");
                boolean first = true;
                for (Face f : b.faces) {
                    int[] r = b.rect[f.ordinal()];
                    sb.append(first ? "" : ", ").append('"').append(f.name().toLowerCase(Locale.ROOT)).append("\": {\"uv\": [")
                            .append(n(r[0] * 16.0 / ATLAS)).append(", ").append(n(r[1] * 16.0 / ATLAS)).append(", ")
                            .append(n((r[0] + r[2]) * 16.0 / ATLAS)).append(", ").append(n((r[1] + r[3]) * 16.0 / ATLAS))
                            .append("], \"texture\": \"#t\"}");
                    first = false;
                }
                sb.append("}}").append(k < part.boxes.size() - 1 ? "," : "").append('\n');
            }
            sb.append("  ]\n}\n");
            Files.writeString(models.resolve("elexyr_" + part.name + ".json"), sb, StandardCharsets.UTF_8);
            Files.writeString(items.resolve("elexyr_" + part.name + ".json"),
                    "{\n  \"model\": {\"type\": \"minecraft:model\", \"model\": \"orbitdev:item/elexyr_" + part.name + "\"}\n}\n", StandardCharsets.UTF_8);
        }
    }

    static void writeRig(Path rig, List<Part> parts) throws IOException {
        StringBuilder sb = new StringBuilder("# Pivots des pièces d'Élexyr (écrit par tools/ElexyrGen.java, ne pas modifier à la main).\n"
                + "# pièce  x  y  z   (unités du modèle : 1/16 de bloc, pieds à Y = 0, avant = -Z)\n");
        double top = 0;
        for (Part part : parts) {
            sb.append(part.name).append(' ').append(n(part.px)).append(' ').append(n(part.py)).append(' ').append(n(part.pz)).append('\n');
            for (Box b : part.boxes) {
                top = Math.max(top, b.y1);
            }
        }
        sb.append("hauteur ").append(n(top)).append(" 0 0\n");
        Files.createDirectories(rig.getParent());
        Files.writeString(rig, sb, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ aperçu (lancer de rayons, vue orthographique)

    static final int VW = 430, VH = 640, SS = 2;

    static void preview(Path out, List<Part> parts, BufferedImage atlas) throws IOException {
        List<Box> boxes = new ArrayList<>();
        for (Part part : parts) {
            boxes.addAll(part.boxes);
        }
        double[][] views = {{0, 8, 12.4, 23.5}, {35, 12, 12.4, 23.5}, {150, 12, 12.4, 23.5}, {180, 8, 12.4, 23.5},
                {14, 5, 44, 29.5}, {22, 10, 34, 18.5}};
        int cols = 4;
        BufferedImage img = new BufferedImage(VW * cols, VH * 2, BufferedImage.TYPE_INT_RGB);
        for (int k = 0; k < views.length; k++) {
            int ox = (k < 4 ? k : (k - 4) * 2) * VW, oy = k < 4 ? 0 : VH, w = k < 4 ? VW : VW * 2;
            render(img, ox, oy, w, VH, boxes, atlas, views[k][0], views[k][1], views[k][2], views[k][3]);
        }
        Files.createDirectories(out.getParent());
        ImageIO.write(img, "png", out.toFile());
    }

    static double[] rotate(double[] p, char axis, double deg, boolean point, Box b) {
        if (axis == 0) {
            return p;
        }
        double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
        double x = p[0] - (point ? b.ox : 0), y = p[1] - (point ? b.oy : 0), z = p[2] - (point ? b.oz : 0);
        double nx = x, ny = y, nz = z;
        switch (axis) {
            case 'x' -> { ny = y * c - z * s; nz = y * s + z * c; }
            case 'y' -> { nx = x * c + z * s; nz = -x * s + z * c; }
            default -> { nx = x * c - y * s; ny = x * s + y * c; }
        }
        return new double[]{nx + (point ? b.ox : 0), ny + (point ? b.oy : 0), nz + (point ? b.oz : 0)};
    }

    static void render(BufferedImage img, int ox, int oy, int w, int h, List<Box> boxes, BufferedImage atlas,
                       double yawDeg, double pitchDeg, double pxPerUnit, double centerY) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        double[] dir = {-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
        double[] right = {-Math.cos(yaw), 0, -Math.sin(yaw)};
        double[] up = {dir[1] * right[2] - dir[2] * right[1], dir[2] * right[0] - dir[0] * right[2], dir[0] * right[1] - dir[1] * right[0]};
        if (up[1] < 0) {
            up = new double[]{-up[0], -up[1], -up[2]};
        }
        final double[] upF = up;
        double[] light = {-0.35, 0.8, -0.5};
        double ll = Math.sqrt(light[0] * light[0] + light[1] * light[1] + light[2] * light[2]);
        double[][] normals = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
        IntStream.range(0, h).parallel().forEach(py -> {
            P p = new P();
            for (int px = 0; px < w; px++) {
                double ar = 0, ag = 0, ab = 0;
                for (int sy = 0; sy < SS; sy++) {
                    for (int sx = 0; sx < SS; sx++) {
                        double scx = (px + (sx + 0.5) / SS - w / 2.0) / pxPerUnit, scy = (h / 2.0 - py - (sy + 0.5) / SS) / pxPerUnit;
                        double[] o = new double[3];
                        double[] c = {0, centerY, 0};
                        for (int k = 0; k < 3; k++) {
                            o[k] = c[k] + right[k] * scx + upF[k] * scy - dir[k] * 200;
                        }
                        double best = Double.MAX_VALUE;
                        double bg = 0.13 + 0.05 * (1 - (double) py / h);
                        double r = bg * 0.85, g = bg, b = bg * 1.3;
                        for (Box bx : boxes) {
                            double[] lo = rotate(o, bx.axis, -bx.angle, true, bx), ld = rotate(dir, bx.axis, -bx.angle, false, bx);
                            for (Face f : bx.faces) {
                                double[] nrm = normals[f.ordinal()];
                                double dn = nrm[0] * ld[0] + nrm[1] * ld[1] + nrm[2] * ld[2];
                                if (dn >= -1e-9) {
                                    continue;                                 // face de dos : pas dessinée (comme le jeu)
                                }
                                double plane = switch (f) {
                                    case NORTH -> bx.z0;
                                    case SOUTH -> bx.z1;
                                    case WEST -> bx.x0;
                                    case EAST -> bx.x1;
                                    case UP -> bx.y1;
                                    case DOWN -> bx.y0;
                                };
                                int ax = f == Face.NORTH || f == Face.SOUTH ? 2 : f == Face.WEST || f == Face.EAST ? 0 : 1;
                                double tt = (plane - lo[ax]) / ld[ax];
                                if (tt <= 0 || tt >= best) {
                                    continue;
                                }
                                double hx = lo[0] + ld[0] * tt, hy = lo[1] + ld[1] * tt, hz = lo[2] + ld[2] * tt;
                                double u, v;
                                switch (f) {
                                    case NORTH -> { u = bx.x1 - hx; v = bx.y1 - hy; }
                                    case SOUTH -> { u = hx - bx.x0; v = bx.y1 - hy; }
                                    case WEST -> { u = hz - bx.z0; v = bx.y1 - hy; }
                                    case EAST -> { u = bx.z1 - hz; v = bx.y1 - hy; }
                                    case UP -> { u = hx - bx.x0; v = hz - bx.z0; }
                                    default -> { u = hx - bx.x0; v = bx.z1 - hz; }
                                }
                                double fw = bx.fw(f), fh = bx.fh(f);
                                if (u < 0 || v < 0 || u > fw || v > fh) {
                                    continue;
                                }
                                int[] rc = bx.rect[f.ordinal()];
                                int tx = rc[0] + Math.min(rc[2] - 1, (int) (u / fw * rc[2])), ty = rc[1] + Math.min(rc[3] - 1, (int) (v / fh * rc[3]));
                                int col = atlas.getRGB(tx, ty);
                                if ((col >>> 24) < 128) {
                                    continue;
                                }
                                double[] wn = rotate(nrm, bx.axis, bx.angle, false, bx);
                                double lit = 0.6 + 0.4 * Math.max(0, (wn[0] * light[0] + wn[1] * light[1] + wn[2] * light[2]) / ll);
                                best = tt;
                                r = ((col >> 16) & 255) / 255.0 * lit;
                                g = ((col >> 8) & 255) / 255.0 * lit;
                                b = (col & 255) / 255.0 * lit;
                            }
                        }
                        ar += r;
                        ag += g;
                        ab += b;
                    }
                }
                int n = SS * SS;
                int rgb = ((int) Math.min(255, ar / n * 255) << 16) | ((int) Math.min(255, ag / n * 255) << 8) | (int) Math.min(255, ab / n * 255);
                img.setRGB(ox + px, oy + py, rgb);
            }
        });
    }

    public static void main(String[] args) throws IOException {
        Path pack = Path.of(args.length > 0 ? args[0] : "pack");
        Path rig = Path.of(args.length > 1 ? args[1] : "src/main/resources/elexyr.rig");
        List<Part> parts = build();
        BufferedImage atlas = paint(parts);
        Path tex = pack.resolve("assets/orbitdev/textures/item/elexyr.png");
        Files.createDirectories(tex.getParent());
        ImageIO.write(atlas, "png", tex.toFile());
        writeModels(pack, parts);
        writeRig(rig, parts);
        int cubes = parts.stream().mapToInt(p -> p.boxes.size()).sum();
        System.out.println("Élexyr : " + parts.size() + " pièces, " + cubes + " cubes, texture " + ATLAS + " px (" + Files.size(tex) / 1024 + " Ko).");
        if (args.length > 2) {
            preview(Path.of(args[2]), parts, atlas);
            System.out.println("Aperçu : " + args[2]);
        }
    }
}
