import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Skins des PNJ d'Orbit Tycoon, dessinés de zéro aux couleurs d'Orbit MC (bleu glacé + or).
 * Disposition 64x64 d'un skin de joueur (bras de 4 px, modèle « classic »), portés par des mannequins
 * via le skin du pack (clé orbittycoon:entity/pnj/<nom>).
 *  - astronaute : PNJ boutique des avenues ;
 *  - vendeur    : PNJ du dôme « Marché orbital » ;
 *  - aventurier : PNJ du dôme « Quêtes de la station ».
 *
 * Usage : java PnjSkins.java <dossier_pack> [<image_apercu.png>]
 */
public class PnjSkins {

    record Part(int u, int v, int w, int h, int d) {
    }

    static final Part HEAD = new Part(0, 0, 8, 8, 8), HAT = new Part(32, 0, 8, 8, 8),
            BODY = new Part(16, 16, 8, 12, 4), JACKET = new Part(16, 32, 8, 12, 4),
            RARM = new Part(40, 16, 4, 12, 4), LARM = new Part(32, 48, 4, 12, 4),
            RLEG = new Part(0, 16, 4, 12, 4), LLEG = new Part(16, 48, 4, 12, 4);
    static final int TOP = 0, BOTTOM = 1, RIGHT = 2, FRONT = 3, LEFT = 4, BACK = 5;
    static final double[] LUMIERE = {1.10, 0.70, 0.90, 1.0, 0.90, 0.85};

    // Palette Orbit.
    static final int BLEU_GLACE = 0x62CFF4, OR = 0xE0B864, NUIT = 0x0B2A5B, BLANC = 0xE9EDF2, GRIS = 0x8A939E,
            ANTHRACITE = 0x2B3038, PEAU = 0xD9A27E, PEAU_OMBRE = 0xB9805E;

    interface Peintre {
        /** Couleur RGB (ou -1 = transparent) du pixel (x, y) d'une face de largeur w et hauteur h. */
        int c(int face, int x, int y, int w, int h);
    }

    static BufferedImage img;
    static Random rnd;

    static int[] rect(Part p, int f) {
        return switch (f) {
            case TOP -> new int[]{p.u() + p.d(), p.v(), p.w(), p.d()};
            case BOTTOM -> new int[]{p.u() + p.d() + p.w(), p.v(), p.w(), p.d()};
            case RIGHT -> new int[]{p.u(), p.v() + p.d(), p.d(), p.h()};
            case FRONT -> new int[]{p.u() + p.d(), p.v() + p.d(), p.w(), p.h()};
            case LEFT -> new int[]{p.u() + p.d() + p.w(), p.v() + p.d(), p.d(), p.h()};
            default -> new int[]{p.u() + 2 * p.d() + p.w(), p.v() + p.d(), p.w(), p.h()};
        };
    }

    static int mul(int c, double k) {
        int r = (int) Math.max(0, Math.min(255, ((c >> 16) & 255) * k));
        int g = (int) Math.max(0, Math.min(255, ((c >> 8) & 255) * k));
        int b = (int) Math.max(0, Math.min(255, (c & 255) * k));
        return (r << 16) | (g << 8) | b;
    }

    static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Peint les 6 faces d'une partie, avec lumière par face et un léger grain. */
    static void peindre(Part p, Peintre pe) {
        for (int f = 0; f < 6; f++) {
            int[] r = rect(p, f);
            for (int x = 0; x < r[2]; x++)
                for (int y = 0; y < r[3]; y++) {
                    int c = pe.c(f, x, y, r[2], r[3]);
                    if (c < 0) { img.setRGB(r[0] + x, r[1] + y, 0); continue; }
                    double k = LUMIERE[f] * (0.96 + rnd.nextDouble() * 0.08);
                    if (f != TOP && f != BOTTOM) k *= 1.04 - 0.10 * y / Math.max(1, r[3] - 1);
                    img.setRGB(r[0] + x, r[1] + y, 0xFF000000 | mul(c, k));
                }
        }
    }

    static boolean cote(int f) { return f == RIGHT || f == LEFT; }

    // ================================================================== astronaute

    static BufferedImage astronaute() {
        nouveau(1);
        // Casque : coque blanche, visière bleu nuit → bleu glacé avec un reflet doré.
        peindre(HEAD, (f, x, y, w, h) -> {
            if (f == FRONT && x >= 1 && x <= 6 && y >= 2 && y <= 6) {
                if ((x == 2 && y == 3) || (x == 3 && y == 3) || (x == 2 && y == 4)) return OR;
                return mix(NUIT, BLEU_GLACE, (y - 2) / 6.0 + (x == 6 ? 0.25 : 0));
            }
            if (f == FRONT && (x == 0 || x == 7) && y >= 2 && y <= 6) return OR;      // joint doré de la visière
            if (cote(f) && y == 4 && x >= 3 && x <= 4) return BLEU_GLACE;          // voyants latéraux
            if (f == BOTTOM) return GRIS;
            if (y == 7 && f != TOP) return OR;                                     // col
            return BLANC;
        });
        // Antenne et bande dorée sur le calque du casque.
        peindre(HAT, (f, x, y, w, h) -> {
            if (f == TOP && x == 6 && y == 1) return OR;
            if (f == LEFT && x == 1 && y <= 1) return OR;
            return -1;
        });
        // Combinaison : torse blanc, panneau de commande bleu/or, ceinture dorée, sac de survie au dos.
        peindre(BODY, (f, x, y, w, h) -> {
            if (y == 8) return OR;                                                 // ceinture
            if (f == FRONT && x >= 2 && x <= 5 && y >= 2 && y <= 5) {
                if (y == 2 || y == 5 || x == 2 || x == 5) return GRIS;
                return (x + y) % 2 == 0 ? BLEU_GLACE : OR;                         // boutons du panneau
            }
            if (f == FRONT && x == 1 && y == 1) return BLEU_GLACE;                 // écusson Orbit
            if (f == BACK && x >= 1 && x <= 6 && y >= 1 && y <= 9) {
                if (x == 1 || x == 6 || y == 1 || y == 9) return ANTHRACITE;
                return y == 3 && (x == 2 || x == 5) ? BLEU_GLACE : GRIS;           // sac à oxygène
            }
            return BLANC;
        });
        Peintre bras = (f, x, y, w, h) -> {
            if (y >= 10) return GRIS;                                              // gants
            if (y == 9) return OR;
            if (cote(f) && y >= 2 && y <= 7 && x >= 1 && x <= 2) return BLEU_GLACE; // bande bleue
            if (f == BOTTOM) return GRIS;
            return BLANC;
        };
        peindre(RARM, bras);
        peindre(LARM, bras);
        Peintre jambe = (f, x, y, w, h) -> {
            if (y >= 9) return ANTHRACITE;                                         // bottes
            if (y == 8) return OR;
            if (y == 4 && f == FRONT) return GRIS;                                 // genouillère
            if (f == BOTTOM) return ANTHRACITE;
            return BLANC;
        };
        peindre(RLEG, jambe);
        peindre(LLEG, jambe);
        return img;
    }

    // ================================================================== vendeur

    static void visage(int f, int x, int y, int[] out, int cheveux, boolean moustache) {
        out[0] = PEAU;
        if (f != FRONT) return;
        if (y == 3 && (x == 1 || x == 6)) out[0] = mul(cheveux, 1.1);                          // sourcils
        if (y == 4 && (x == 1 || x == 5)) out[0] = 0xF4F4F4;                                   // blanc de l'œil
        if (y == 4 && (x == 2 || x == 6)) out[0] = 0x2A6BB5;                                   // iris bleus
        if (y == 5 && (x == 3 || x == 4)) out[0] = PEAU_OMBRE;                                 // nez
        if (moustache && y == 6 && x >= 2 && x <= 5) out[0] = cheveux;
        if (!moustache && y == 6 && x >= 3 && x <= 4) out[0] = 0x9E5A4A;                       // bouche
    }

    static BufferedImage vendeur() {
        nouveau(2);
        int cheveux = 0x3B2A20, veste = 0x1B2E4F;
        peindre(HEAD, (f, x, y, w, h) -> {
            if (f == TOP) return cheveux;
            if (y <= 1 && f != BOTTOM) return cheveux;
            if ((cote(f) || f == BACK) && y <= 3) return cheveux;
            if (f == BACK) return y <= 5 ? cheveux : PEAU;
            int[] o = new int[1];
            visage(f, x, y, o, cheveux, true);
            return o[0];
        });
        // Calque : petite casquette de marchand bleue à visière et insigne doré.
        peindre(HAT, (f, x, y, w, h) -> {
            if (f == TOP) return veste;
            if (y <= 1 && f != BOTTOM) return f == FRONT && y == 1 && (x == 3 || x == 4) ? OR : veste;
            if (y == 2 && f == FRONT) return mul(veste, 0.7);                                   // visière
            return -1;
        });
        // Veste bleue à boutons dorés, chemise blanche, sacoche en bandoulière.
        peindre(BODY, (f, x, y, w, h) -> {
            if (f == FRONT && x >= 3 && x <= 4 && y <= 6) return y <= 1 ? BLANC : (y % 2 == 0 ? OR : BLANC);
            if ((f == FRONT || f == BACK) && x == (f == FRONT ? 7 - y / 2 : y / 2) && y <= 11) return 0x7A4E2D; // bandoulière
            if (y == 8) return 0x5A3A22;                                                         // ceinture
            if (f == FRONT && y >= 9 && x >= 1 && x <= 3) return 0x8B5A33;                       // sacoche
            return veste;
        });
        peindre(JACKET, (f, x, y, w, h) -> f == FRONT && y == 0 && (x == 2 || x == 5) ? OR : -1); // épaulettes
        Peintre bras = (f, x, y, w, h) -> {
            if (y >= 10) return PEAU;
            if (y == 9) return OR;                                                               // poignets dorés
            return veste;
        };
        peindre(RARM, bras);
        peindre(LARM, bras);
        Peintre jambe = (f, x, y, w, h) -> {
            if (y >= 10) return 0x3A2416;                                                        // chaussures
            if (cote(f) && x == 1) return OR;                                                    // passepoil
            return 0x2A2F3A;
        };
        peindre(RLEG, jambe);
        peindre(LLEG, jambe);
        return img;
    }

    // ================================================================== aventurier

    static BufferedImage aventurier() {
        nouveau(3);
        int cheveux = 0x7A4A22, cuir = 0x8B5A33, cuirFonce = 0x5E3B1F, echarpe = 0x2F86C9;
        peindre(HEAD, (f, x, y, w, h) -> {
            if (f == TOP) return cheveux;
            if (y <= 1 && f != BOTTOM) return cheveux;
            if (cote(f) && y <= 4) return cheveux;
            if (f == BACK) return y <= 6 ? cheveux : PEAU;
            int[] o = new int[1];
            visage(f, x, y, o, cheveux, false);
            if (f == FRONT && y == 6 && x == 6) o[0] = PEAU_OMBRE;                               // cicatrice
            return o[0];
        });
        // Calque : lunettes de pilote relevées sur le front (verres bleu glacé cerclés d'or).
        peindre(HAT, (f, x, y, w, h) -> {
            if (y != 2 || f == TOP || f == BOTTOM) return -1;
            if (f == FRONT && (x == 1 || x == 2 || x == 5 || x == 6)) return x == 1 || x == 6 ? OR : BLEU_GLACE;
            return cuirFonce;
        });
        // Veste de cuir, écharpe bleue, ceinture à poches.
        peindre(BODY, (f, x, y, w, h) -> {
            if (y <= 1) return echarpe;
            if (f == FRONT && y == 2 && x >= 5 && x <= 6) return echarpe;                        // pan d'écharpe
            if (y == 8) return f == FRONT && (x == 3 || x == 4) ? OR : cuirFonce;                 // boucle dorée
            if (f == FRONT && y >= 9 && (x == 1 || x == 6)) return 0x6B4A2A;                     // poches
            if (f == FRONT && x >= 3 && x <= 4 && y >= 2 && y <= 7) return 0xC9B48A;              // chemise
            return cuir;
        });
        peindre(JACKET, (f, x, y, w, h) -> f == BACK && y >= 2 && y <= 7 && x >= 2 && x <= 5
                ? (y == 2 || y == 7 || x == 2 || x == 5 ? cuirFonce : 0x4E6B3A) : -1);           // sac roulé
        Peintre bras = (f, x, y, w, h) -> {
            if (y >= 10) return 0x4A3020;                                                        // gants
            if (y <= 3) return cuir;
            return y == 4 ? cuirFonce : cuir;
        };
        peindre(RARM, bras);
        peindre(LARM, bras);
        Peintre jambe = (f, x, y, w, h) -> {
            if (y >= 8) return y == 8 ? 0x2B1C12 : 0x3A2416;                                     // bottes
            return 0x6E5A3E;                                                                     // pantalon
        };
        peindre(RLEG, jambe);
        peindre(LLEG, jambe);
        return img;
    }

    // ================================================================== forgeron

    static BufferedImage forgeron() {
        nouveau(4);
        int barbe = 0x2E1E14, chemise = 0x2B3038, tablier = 0x6B4226, tablierFonce = 0x4A2C18;
        peindre(HEAD, (f, x, y, w, h) -> {
            if (f == TOP) return mul(PEAU, 0.95);                                                 // crâne rasé
            if (f == FRONT && y >= 6) return barbe;                                              // barbe
            if (f == FRONT && y == 5 && (x <= 1 || x >= 6)) return barbe;
            if (cote(f) && y >= 5 && x >= 4) return barbe;
            int[] o = new int[1];
            visage(f, x, y, o, barbe, true);
            return o[0];
        });
        // Calque : bandeau bleu sur le front, lunettes de forge dorées.
        peindre(HAT, (f, x, y, w, h) -> {
            if (y != 2 || f == TOP || f == BOTTOM) return -1;
            if (f == FRONT && (x == 2 || x == 5)) return OR;
            return BLEU_GLACE;
        });
        // Chemise sombre, grand tablier de cuir à poche, rivets dorés.
        peindre(BODY, (f, x, y, w, h) -> {
            if (f == FRONT) {
                if (y == 0 && (x == 1 || x == 6)) return tablierFonce;                            // bretelles
                if (y >= 1 && x >= 1 && x <= 6) {
                    if (y == 1 && (x == 1 || x == 6)) return OR;                                  // rivets
                    if (y >= 6 && y <= 8 && x >= 2 && x <= 5) return y == 6 ? tablierFonce : 0x5A361E; // poche
                    return tablier;
                }
            }
            if (f == BACK && y == 8) return tablierFonce;                                        // lien du tablier
            if (y == 8 && cote(f)) return tablierFonce;
            return chemise;
        });
        // Bras nus et musclés, manches retroussées, gros gants de cuir.
        Peintre bras = (f, x, y, w, h) -> {
            if (y <= 2) return chemise;
            if (y == 3) return mul(chemise, 1.3);
            if (y >= 9) return y == 9 ? OR : tablierFonce;
            return (f == FRONT && y == 5 && x == 1) ? PEAU_OMBRE : PEAU;
        };
        peindre(RARM, bras);
        peindre(LARM, bras);
        Peintre jambe = (f, x, y, w, h) -> {
            if (y >= 9) return y == 9 ? GRIS : 0x26201C;                                         // bottes coquées
            if (f == FRONT && y <= 3) return tablier;                                            // bas du tablier
            return 0x3E4654;
        };
        peindre(RLEG, jambe);
        peindre(LLEG, jambe);
        return img;
    }

    // ================================================================== sortie

    static void nouveau(long graine) {
        img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        rnd = new Random(graine);
    }

    static void blit(BufferedImage dst, BufferedImage src, Part p, int f, int dx, int dy, int s) {
        int[] r = rect(p, f);
        for (int i = 0; i < r[2]; i++)
            for (int j = 0; j < r[3]; j++) {
                int c = src.getRGB(r[0] + i, r[1] + j);
                if ((c >>> 24) == 0) continue;
                for (int a = 0; a < s; a++) for (int b = 0; b < s; b++) dst.setRGB(dx + i * s + a, dy + j * s + b, c);
            }
    }

    static void figure(BufferedImage dst, BufferedImage sk, int x, int y, int s) {
        for (int dos = 0; dos < 2; dos++) {
            int f = dos == 0 ? FRONT : BACK, bx = x + dos * 20 * s;
            blit(dst, sk, HEAD, f, bx + 4 * s, y, s);
            blit(dst, sk, HAT, f, bx + 4 * s, y, s);
            blit(dst, sk, BODY, f, bx + 4 * s, y + 8 * s, s);
            blit(dst, sk, JACKET, f, bx + 4 * s, y + 8 * s, s);
            blit(dst, sk, dos == 0 ? RARM : LARM, f, bx, y + 8 * s, s);
            blit(dst, sk, dos == 0 ? LARM : RARM, f, bx + 12 * s, y + 8 * s, s);
            blit(dst, sk, dos == 0 ? RLEG : LLEG, f, bx + 4 * s, y + 20 * s, s);
            blit(dst, sk, dos == 0 ? LLEG : RLEG, f, bx + 8 * s, y + 20 * s, s);
        }
    }

    public static void main(String[] a) throws Exception {
        File dossier = new File(a[0], "assets/orbittycoon/textures/entity/pnj");
        dossier.mkdirs();
        String[] noms = {"astronaute", "vendeur", "aventurier", "forgeron"};
        BufferedImage[] skins = {astronaute(), vendeur(), aventurier(), forgeron()};
        for (int i = 0; i < noms.length; i++) ImageIO.write(skins[i], "png", new File(dossier, noms[i] + ".png"));
        if (a.length > 1) {
            int s = 8;
            BufferedImage ap = new BufferedImage(4 * 40 * s, 34 * s, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < ap.getWidth(); x++) for (int y = 0; y < ap.getHeight(); y++) ap.setRGB(x, y, 0xFF0A1428);
            for (int i = 0; i < 4; i++) figure(ap, skins[i], i * 40 * s + s, s, s);
            ImageIO.write(ap, "png", new File(a[1]));
        }
        System.out.println("Skins écrits dans " + dossier);
    }
}
