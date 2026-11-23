package fr.orbit.tycoon;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Statue du centre de la Mobzone (50 × 50) : un astronaute d'Orbit debout sur un socle rond à 4 gradins,
 * bras droit levé qui brandit une planète bleue cerclée d'un anneau d'or. Visière dorée, DA bleu + or.
 * <p>
 * Dessinée en coordonnées locales : b = côté (droite de la statue +), a = avant (+ = vers où elle regarde),
 * h = hauteur au-dessus du socle. Chaque forme dit quel bloc elle met en (b, a, h) ; la première qui répond gagne.
 */
final class Statue {
    /** Demi-largeur du carré occupé (50 × 50) et hauteur max au-dessus du sol. */
    static final int DEMI = 25, HAUT = 72;
    /** Hauteur du socle (le personnage commence au-dessus). */
    private static final int SOCLE = 4;

    private interface Forme { Material en(double b, double a, double h); }

    private final List<Forme> formes = new ArrayList<>();

    Statue() {
        // Planète brandie + anneau d'or incliné (avant tout : elle dépasse au-dessus de la main).
        double pb = 12.6, pa = 0, ph = 53.5;
        double nb = Math.sin(0.45), nh = Math.cos(0.45), na = 0.18;
        double nn = Math.sqrt(nb * nb + nh * nh + na * na);
        final double ub = nb / nn, ua = na / nn, uh = nh / nn;
        formes.add((b, a, h) -> {
            double vb = b - pb, va = a - pa, vh = h - ph, d = Math.sqrt(vb * vb + va * va + vh * vh);
            double plan = Math.abs(vb * ub + va * ua + vh * uh);
            if (plan < 0.55 && d > 7 && d < 9.8) return d < 8.3 ? Material.GOLD_BLOCK : Material.YELLOW_STAINED_GLASS;
            if (d > 5.6) return null;
            int bande = (int) Math.floor((vh + 5.6) / 1.6);
            return switch (Math.floorMod(bande + (vb > 2 && va > 0 ? 1 : 0), 3)) {
                case 0 -> Material.BLUE_CONCRETE;
                case 1 -> Material.LIGHT_BLUE_CONCRETE;
                default -> Material.CYAN_CONCRETE;
            };
        });

        // Casque : lumières latérales, visière dorée, contour bleu, coque blanche ; antenne.
        ellipsoide(6.7, 0.5, 36.5, 0.9, 1.6, 1.6, Material.SEA_LANTERN);
        ellipsoide(-6.7, 0.5, 36.5, 0.9, 1.6, 1.6, Material.SEA_LANTERN);
        formes.add((b, a, h) -> dansSphere(b, a - 0.5, h - 36.5, 6.8) && a - 0.5 > 2.6 && h > 33 && h < 39.5 && Math.abs(b) < 5.2
                ? Material.GOLD_BLOCK : null);
        formes.add((b, a, h) -> dansSphere(b, a - 0.5, h - 36.5, 6.8) && a - 0.5 > 1.8 && h > 32.3 && h < 40.2 && Math.abs(b) < 5.9
                ? Material.LIGHT_BLUE_CONCRETE : null);
        formes.add((b, a, h) -> dansSphere(b, a - 0.5, h - 36.5, 6.8) ? Material.WHITE_CONCRETE : null);
        capsule(-4, 0, 41.5, -5.5, -0.5, 47, 0.6, Material.GRAY_CONCRETE);
        ellipsoide(-5.6, -0.5, 47.9, 1.1, 1.1, 1.1, Material.SEA_LANTERN);
        capsule(0, 0, 29.5, 0, 0, 31.5, 3.2, Material.GRAY_CONCRETE);                     // cou

        // Bras droit levé (vers la planète) : épaule, bras, manchette bleue, gant.
        ellipsoide(12.6, 0, 46.3, 2.5, 2.5, 2.3, Material.GRAY_CONCRETE);
        ellipsoide(12.5, 0, 44.4, 2.8, 2.8, 0.8, Material.LIGHT_BLUE_CONCRETE);
        capsule(11.5, 0, 37, 12.5, 0, 44, 2.3, Material.WHITE_CONCRETE);
        capsule(8.5, 0, 28, 11.5, 0, 37, 2.5, Material.WHITE_CONCRETE);
        // Bras gauche le long du corps.
        ellipsoide(-11, 1.5, 16, 2.4, 2.4, 2.4, Material.GRAY_CONCRETE);
        ellipsoide(-10.4, 1, 18.6, 2.8, 2.8, 0.8, Material.LIGHT_BLUE_CONCRETE);
        capsule(-8.5, 0, 27, -10.5, 1, 18, 2.5, Material.WHITE_CONCRETE);
        // Épaulettes (bande bleue sur le dessus).
        for (int s : new int[]{-1, 1}) {
            final double cb = 8 * s;
            formes.add((b, a, h) -> dansEllipsoide(b - cb, a, h - 28, 3.4, 3.4, 3.2)
                    ? (h > 29.6 ? Material.LIGHT_BLUE_CONCRETE : Material.WHITE_CONCRETE) : null);
        }

        // Sac à dos : deux lumières, réservoirs dorés, caisson gris.
        boite(-1.2, 1.2, -8.8, -7.8, 22, 24.5, Material.SEA_LANTERN);
        capsule(-3, -6.8, 18, -3, -6.8, 30, 1.6, Material.GOLD_BLOCK);
        capsule(3, -6.8, 18, 3, -6.8, 30, 1.6, Material.GOLD_BLOCK);
        boite(-5.5, 5.5, -8.5, -4, 17, 29, Material.LIGHT_GRAY_CONCRETE);

        // Torse : panneau bleu sur la poitrine avec emblème doré, combinaison blanche.
        formes.add((b, a, h) -> {
            if (!dansEllipsoide(b, a, h - 23, 7.9, 5.5, 7.4) || Math.abs(b) > 3.2 || a < 2.5 || h < 20 || h > 26) return null;
            return Math.abs(b) < 1.1 && Math.abs(h - 23) < 1.1 ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_CONCRETE;
        });
        ellipsoide(0, 0, 23, 7.6, 5.2, 7.2, Material.WHITE_CONCRETE);

        // Ceinture dorée, bassin.
        formes.add((b, a, h) -> dansEllipsoide(b, a, h - 16.5, 7.3, 4.9, 3.4) && Math.abs(h - 16.2) < 0.7 ? Material.GOLD_BLOCK : null);
        ellipsoide(0, 0, 16.5, 7, 4.6, 3.2, Material.WHITE_CONCRETE);

        // Jambes : genouillères bleues, jambes blanches, bottes grises.
        for (int s : new int[]{-1, 1}) {
            ellipsoide(4 * s, 2.3, 8.5, 2.1, 1.2, 1.5, Material.LIGHT_BLUE_CONCRETE);
            capsule(3.6 * s, 0, 15, 4 * s, 0, 3, 2.9, Material.WHITE_CONCRETE);
            ellipsoide(4 * s, 1, 1.5, 2.7, 4, 1.9, Material.GRAY_CONCRETE);
        }
    }

    // ------------------------------------------------------------------ formes

    private static boolean dansSphere(double x, double y, double z, double r) { return x * x + y * y + z * z <= r * r; }

    private static boolean dansEllipsoide(double x, double y, double z, double rx, double ry, double rz) {
        double u = x / rx, v = y / ry, w = z / rz;
        return u * u + v * v + w * w <= 1;
    }

    private void ellipsoide(double cb, double ca, double ch, double rb, double ra, double rh, Material m) {
        formes.add((b, a, h) -> dansEllipsoide(b - cb, a - ca, h - ch, rb, ra, rh) ? m : null);
    }

    private void boite(double b0, double b1, double a0, double a1, double h0, double h1, Material m) {
        formes.add((b, a, h) -> b >= b0 && b <= b1 && a >= a0 && a <= a1 && h >= h0 && h <= h1 ? m : null);
    }

    /** Tous les points à moins de r du segment (1) → (2). */
    private void capsule(double b1, double a1, double h1, double b2, double a2, double h2, double r, Material m) {
        double db = b2 - b1, da = a2 - a1, dh = h2 - h1, l2 = db * db + da * da + dh * dh;
        formes.add((b, a, h) -> {
            double t = l2 == 0 ? 0 : ((b - b1) * db + (a - a1) * da + (h - h1) * dh) / l2;
            t = Math.max(0, Math.min(1, t));
            double x = b - (b1 + t * db), y = a - (a1 + t * da), z = h - (h1 + t * dh);
            return x * x + y * y + z * z <= r * r ? m : null;
        });
    }

    private Material personnage(double b, double a, double h) {
        for (Forme f : formes) {
            Material m = f.en(b, a, h);
            if (m != null) return m;
        }
        return null;
    }

    /** Socle rond à gradins (y = 1..SOCLE au-dessus du sol), liseré d'or et de bleu, lumières dans le 1er gradin. */
    private static Material socle(int etage, double d, double angle) {
        return switch (etage) {
            case 1 -> d > 24.5 ? null : d > 23.5 ? Material.GOLD_BLOCK
                    : d > 22.5 ? ((int) Math.floor((angle + Math.PI) * 23 / 6) % 2 == 0 ? Material.SEA_LANTERN : Material.POLISHED_DEEPSLATE)
                    : Material.POLISHED_BLACKSTONE_BRICKS;
            case 2 -> d > 21 ? null : d > 20 ? Material.LIGHT_BLUE_CONCRETE : Material.POLISHED_DEEPSLATE;
            case 3 -> d > 18 ? null : d > 17 ? Material.GOLD_BLOCK : Material.SMOOTH_QUARTZ;
            case 4 -> d > 15 ? null : d > 14 ? Material.LIGHT_BLUE_CONCRETE : Material.SMOOTH_QUARTZ;
            default -> null;
        };
    }

    /**
     * Pose la statue centrée sur (cx, cz), socle posé sur ySol, tournée vers {@code face} (radians, sens de atan2(z, x)).
     * Une étape par tranche x. Les blocs vides du volume sont laissés tels quels (vider avant si besoin).
     */
    void dessiner(Chantier c, int cx, int cz, int ySol, double face) {
        double fx = Math.cos(face), fz = Math.sin(face);   // avant
        double rx = -fz, rz = fx;                          // côté droit de la statue
        for (int dx = -DEMI; dx <= DEMI; dx++) {
            final int fdx = dx;
            c.etape(() -> {
                for (int dz = -DEMI; dz <= DEMI; dz++) {
                    double d = Math.sqrt(fdx * fdx + dz * dz), angle = Math.atan2(dz, fdx);
                    double a = fdx * fx + dz * fz, b = fdx * rx + dz * rz;
                    for (int k = 1; k <= HAUT; k++) {
                        Material m = k <= SOCLE ? socle(k, d, angle) : null;
                        if (m == null && k > SOCLE) m = personnage(b, a, k - SOCLE - 0.5);
                        if (m != null) c.set(cx + fdx, ySol + k, cz + dz, m);
                    }
                }
            });
        }
    }
}
