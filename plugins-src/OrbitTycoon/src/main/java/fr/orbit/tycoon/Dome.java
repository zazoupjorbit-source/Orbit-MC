package fr.orbit.tycoon;

import org.bukkit.Material;

/**
 * Grand dôme de verre posé sur tout le spawn (rayon 128) et vaisseau amiral.
 * Un trou rond, cerclé d'or, est percé dans le dôme côté vaisseau pour le voir sans vitre.
 * Le vaisseau (≈ 186 × 150 blocs) flotte juste derrière, incliné vers le spawn.
 * « /tycoon dome generer [confirmer] » construit le tout (confirmer = efface et reconstruit).
 * Au premier passage, l'ancien petit dôme d'observation (rayon 34, tunnel de verre, ancien vaisseau) est effacé
 * et le garde-corps du spawn est refermé.
 */
final class Dome {

    static final int Y = Zone.Y;
    static final int R_SPAWN = Zone.BASE.rayon;      // 125
    /** Rayon du grand dôme : 3 blocs au-delà du garde-corps du spawn. */
    static final int RG = R_SPAWN + 3;
    /** Direction (degrés) du vaisseau depuis le centre du spawn. */
    static final double ANGLE = 240;
    static final double FX = Math.cos(Math.toRadians(ANGLE)), FZ = Math.sin(Math.toRadians(ANGLE));

    /** Trou de vue : centre à TROU_Y blocs au-dessus du sol, rayon TROU_R, cadre doré autour. */
    private static final int TROU_Y = 20;
    private static final double TROU_R = 12;
    private static final double HP = Math.sqrt(RG * RG - TROU_Y * TROU_Y);
    private static final double PX = FX * HP, PZ = FZ * HP;

    /** Centre du vaisseau à 225 blocs du centre (son aile la plus proche passe à ≈ 25 blocs du dôme). */
    static final int DIST_VAISSEAU = RG + 97;
    static final int Y_VAISSEAU = Y + 20;
    private static final double ROULIS = Math.toRadians(15);

    // Ancien petit dôme d'observation (à effacer).
    private static final int ANC_RD = 34;
    private static final int ANC_DIST = R_SPAWN + 14 + ANC_RD;
    private static final int ANC_CX = (int) Math.round(FX * ANC_DIST), ANC_CZ = (int) Math.round(FZ * ANC_DIST);
    private static final int ANC_DIST_VAISSEAU = ANC_DIST + ANC_RD + 40 + 72;

    private final Plans p;

    Dome(Plans p) { this.p = p; }

    // ------------------------------------------------------------------ chantier

    /**
     * @param ancien  efface d'abord l'ancien petit dôme, son tunnel et l'ancien vaisseau
     * @param refaire efface d'abord le grand dôme et le vaisseau actuels (reconstruction)
     */
    void dessiner(Chantier c, boolean ancien, boolean refaire) {
        if (ancien) retirerAncien(c);
        if (refaire) {
            int[] h = posHolo();
            p.retirerDeco(c, h[0], h[1], 4, "dome");
            coque(c, true);
            new Vaisseau(DIST_VAISSEAU).poser(c, true);
        }
        sol(c);
        coque(c, false);
        new Vaisseau(DIST_VAISSEAU).poser(c, false);
        int[] h = posHolo();
        p.holo(c, "dome", h[0] + 0.5, Y + 6, h[1] + 0.5,
                "<#E0B864><bold>Vaisseau amiral</bold>\n<gray>Le croiseur de la station ORBIT", 1.6f);
    }

    private static int[] posHolo() { return new int[]{(int) Math.round(FX * 119), (int) Math.round(FZ * 119)}; }

    // ------------------------------------------------------------------ ancien dôme

    private void retirerAncien(Chantier c) {
        p.retirerDeco(c, ANC_CX, ANC_CZ, ANC_RD + 4, "dome");
        for (int dx = -ANC_RD - 1; dx <= ANC_RD + 1; dx++) {
            final int fx = dx;
            c.etape(() -> {
                for (int dz = -ANC_RD - 1; dz <= ANC_RD + 1; dz++)
                    if (fx * fx + dz * dz <= (ANC_RD + 1) * (ANC_RD + 1))
                        for (int y = Y - 30; y <= Y + ANC_RD + 2; y++) c.vider(ANC_CX + fx, y, ANC_CZ + dz);
                c.compter(3000);
            });
        }
        // Pont + tunnel : vidé hors du spawn ; dans le spawn, sol et garde-corps remis comme à l'origine.
        c.etape(() -> {
            for (int s = R_SPAWN - 4; s <= ANC_DIST - ANC_RD + 3; s++)
                for (int q = -7; q <= 7; q++) {
                    int x = (int) Math.round(FX * s - FZ * q), z = (int) Math.round(FZ * s + FX * q);
                    double d = Math.sqrt((double) x * x + (double) z * z);
                    if (d <= R_SPAWN + 0.5) {
                        c.set(x, Y, z, d >= R_SPAWN - 2 ? Material.POLISHED_DEEPSLATE : Material.CALCITE);
                        c.set(x, Y - 1, z, Material.POLISHED_DEEPSLATE);
                        for (int y = Y + 1; y <= Y + 7; y++) c.vider(x, y, z);
                        if (d >= R_SPAWN - 0.9) gardeCorps(c, x, z);
                    } else for (int y = Y - 1; y <= Y + 7; y++) c.vider(x, y, z);
                }
        });
        new Vaisseau(ANC_DIST_VAISSEAU).poser(c, true);
    }

    /** Même garde-corps que le reste du bord du spawn (Plans.base). */
    private static void gardeCorps(Chantier c, int x, int z) {
        double arc = (Math.atan2(z, x) + Math.PI) * R_SPAWN;
        if (arc % 12 < 1.2) {
            for (int y = Y + 1; y <= Y + 5; y++) c.set(x, y, z, Material.GOLD_BLOCK);
            c.set(x, Y + 6, z, Material.SEA_LANTERN);
            c.set(x, Y + 7, z, Material.END_ROD);
        } else for (int y = Y + 1; y <= Y + 3; y++) c.set(x, y, z, Material.LIGHT_BLUE_STAINED_GLASS);
    }

    // ------------------------------------------------------------------ grand dôme

    /** Anneau de sol de 3 blocs au-delà du garde-corps, sur lequel repose le dôme. */
    private void sol(Chantier c) {
        p.disque(c, 0, 0, RG + 1, (dx, dz, d) -> {
            if (d <= R_SPAWN + 0.5 || d > RG + 0.5) return;
            boolean lampe = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dz, dx)) * 2), 15) == 0;
            c.set(dx, Y, dz, d > RG - 0.6 ? Material.GOLD_BLOCK : lampe ? Material.SEA_LANTERN : Material.DEEPSLATE_TILES);
            c.set(dx, Y - 1, dz, Material.POLISHED_DEEPSLATE);
        });
    }

    /** Coque de verre (demi-sphère creuse, 1 bloc d'épaisseur) ; effacer = la retire. */
    private void coque(Chantier c, boolean effacer) {
        for (int dx = -RG; dx <= RG; dx++) {
            final int fx = dx;
            c.etape(() -> {
                for (int dz = -RG; dz <= RG; dz++) {
                    double hr2 = (double) fx * fx + (double) dz * dz;
                    if (hr2 > (double) RG * RG) continue;
                    int y0 = Math.max(1, (int) Math.ceil(Math.sqrt(Math.max(0, (RG - 1.0) * (RG - 1.0) - hr2))));
                    int y1 = (int) Math.floor(Math.sqrt((double) RG * RG - hr2));
                    for (int dy = y0; dy <= y1; dy++) {
                        Material m = matCoque(fx, dy, dz);
                        if (m == null) continue;
                        if (effacer) c.vider(fx, Y + dy, dz);
                        else c.set(fx, Y + dy, dz, m);
                    }
                }
            });
        }
    }

    /** Matériau de la coque en (dx, dy, dz), ou null dans le trou de vue. */
    private static Material matCoque(int dx, int dy, int dz) {
        double t = Math.sqrt(sq(dx - PX) + sq(dy - TROU_Y) + sq(dz - PZ));
        if (t < TROU_R) return null;
        if (t < TROU_R + 1.5) return Material.GOLD_BLOCK;                 // cadre du trou
        double hr = Math.max(1, Math.sqrt((double) dx * dx + (double) dz * dz));
        if (hr < 4) return Material.SEA_LANTERN;                           // oculus au sommet
        if (dy <= 1) return Material.GOLD_BLOCK;                           // bandeau au pied du dôme
        double ang = Math.toDegrees(Math.atan2(dz, dx)) + 180;
        boolean meridien = (ang % 30) < Math.toDegrees(1.0 / hr);
        if (meridien || dy % 16 == 0) return Material.GOLD_BLOCK;
        return Material.LIGHT_BLUE_STAINED_GLASS;
    }

    // ------------------------------------------------------------------ vaisseau amiral

    /** Le vaisseau, centré à dist blocs du spawn dans la direction ANGLE. */
    private static final class Vaisseau {
        // Repère : U = longueur (le long de la tangente), V = envergure (vers l'extérieur, incliné), W = hauteur.
        private final double ox, oy = Y_VAISSEAU + 0.5, oz;
        private static final double UX = -FZ, UZ = FX;
        private static final double VX = FX * Math.cos(ROULIS), VY = Math.sin(ROULIS), VZ = FZ * Math.cos(ROULIS);
        private static final double WX = -FX * Math.sin(ROULIS), WY = Math.cos(ROULIS), WZ = -FZ * Math.sin(ROULIS);

        Vaisseau(int dist) { ox = FX * dist; oz = FZ * dist; }

        private Material bloc(int x, int y, int z) {
            double dx = x + 0.5 - ox, dy = y + 0.5 - oy, dz = z + 0.5 - oz;
            return forme(dx * UX + dz * UZ, dx * VX + dy * VY + dz * VZ, dx * WX + dy * WY + dz * WZ);
        }

        /** Pose (ou efface) la coque : seuls les blocs de surface sont posés, l'intérieur reste vide. */
        void poser(Chantier c, boolean effacer) {
            int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
            for (double u : new double[]{-106, 84}) for (double v : new double[]{-78, 78}) for (double w : new double[]{-14, 37}) {
                double kx = ox + u * UX + v * VX + w * WX, ky = oy + v * VY + w * WY, kz = oz + u * UZ + v * VZ + w * WZ;
                x0 = Math.min(x0, (int) Math.floor(kx)); x1 = Math.max(x1, (int) Math.ceil(kx));
                y0 = Math.min(y0, (int) Math.floor(ky)); y1 = Math.max(y1, (int) Math.ceil(ky));
                z0 = Math.min(z0, (int) Math.floor(kz)); z1 = Math.max(z1, (int) Math.ceil(kz));
            }
            final int fy0 = y0, fy1 = y1, fz0 = z0, fz1 = z1;
            for (int x = x0; x <= x1; x++) {
                final int fx = x;
                c.etape(() -> {
                    int evals = 0;
                    for (int z = fz0; z <= fz1; z++)
                        for (int y = fy0; y <= fy1; y++) {
                            Material m = bloc(fx, y, z);
                            evals++;
                            if (m == null) continue;
                            if (effacer) { c.vider(fx, y, z); continue; }
                            boolean surface = bloc(fx + 1, y, z) == null || bloc(fx - 1, y, z) == null
                                    || bloc(fx, y + 1, z) == null || bloc(fx, y - 1, z) == null
                                    || bloc(fx, y, z + 1) == null || bloc(fx, y, z - 1) == null;
                            if (surface) c.set(fx, y, z, m);
                        }
                    c.compter(evals / 20);
                });
            }
        }
    }

    /** Matériau du vaisseau au point local (u, v, w), ou null si vide. */
    static Material forme(double u, double v, double w) {
        double av = Math.abs(v);
        // Verrière du cockpit.
        double ck = sq((u - 48) / 16) + sq(v / 6) + sq((w - 8) / 5);
        if (ck <= 1 && w > 6) return av < 0.6 ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_STAINED_GLASS;
        // Tour de commandement.
        if (u >= -48 && u <= -18 - Math.max(0, (w - 8) * 0.5) && w >= 7 && w <= 19 && av <= 8 - (w - 8) * 0.3) {
            if (w > 18) return Material.GOLD_BLOCK;
            if (w >= 15 && w <= 16.5) return Material.LIGHT_BLUE_STAINED_GLASS;
            return Material.WHITE_CONCRETE;
        }
        // Dérive (aileron dorsal).
        if (av <= 1.0 && u >= -74 && u <= -40) {
            double haut = u < -60 ? 34 : 34 - (u + 60) * 1.3;
            if (w >= 8 && w <= haut) {
                if (w > haut - 1.5) return u < -70 ? Material.SEA_LANTERN : Material.GOLD_BLOCK;
                return u < -72 ? Material.LIGHT_BLUE_CONCRETE : Material.WHITE_CONCRETE;
            }
        }
        // Panaches des réacteurs.
        if (u >= -104 && u < -90 && Math.sqrt(v * v + w * w) <= 6.5 * (u + 104) / 14) return Material.LIGHT_BLUE_STAINED_GLASS;
        for (int s = -1; s <= 1; s += 2) {
            double rv = v - 19 * s, rw = w + 3;
            if (u >= -95 && u < -84 && Math.sqrt(rv * rv + rw * rw) <= 4.2 * (u + 95) / 11) return Material.LIGHT_BLUE_STAINED_GLASS;
        }
        // Réacteur principal.
        double rp = Math.sqrt(v * v + w * w);
        if (u >= -90 && u <= -66 && rp <= 9.5) return moteur(u, rp, -90);
        // Réacteurs latéraux.
        for (int s = -1; s <= 1; s += 2) {
            double rv = v - 19 * s, rw = w + 3, rl = Math.sqrt(rv * rv + rw * rw);
            if (u >= -84 && u <= -56 && rl <= 6) return moteur(u, rl * 9.5 / 6, -84);
        }
        // Coque principale.
        if (u >= -75 && u <= 82) {
            double s = u > 15 ? Math.sqrt(Math.max(0, 1 - sq((u - 15) / 67))) : u < -60 ? 1 - (-60 - u) / 40 : 1;
            double A = 16 * s, B = 11 * s;
            if (A > 0.6 && Math.pow(av / A, 2.4) + Math.pow(Math.abs(w) / B, 2.4) <= 1) {
                if (u > 78) return Material.GOLD_BLOCK;
                if (w < -B * 0.75 && Math.floorMod((int) Math.floor(u), 10) < 1) return Material.SEA_LANTERN;
                if (Math.abs(w - 2.5) < 0.7 && u > -60 && u < 40 && Math.floorMod((int) Math.floor(u), 4) < 2)
                    return Material.LIGHT_BLUE_STAINED_GLASS;
                if (Math.abs(w + 1) < 0.6) return Material.GOLD_BLOCK;
                if (Math.abs(w - 5.5) < 0.6) return Material.LIGHT_BLUE_CONCRETE;
                if (Math.floorMod((int) Math.floor(u), 8) == 0) return Material.LIGHT_GRAY_CONCRETE;
                return w >= 0 ? Material.WHITE_CONCRETE : Material.LIGHT_GRAY_CONCRETE;
            }
        }
        // Ailes en flèche (75 de chaque côté).
        if (u >= -62 && u <= 10 && av >= 8) {
            double vmax = 75 - (u + 62) * (57.0 / 72);
            double wc = Math.max(0, av - 16) * 0.07, ht = 2.2 - av / 75 * 1.2;
            if (av <= vmax && Math.abs(w - wc + 1) <= ht) {
                if (av > 72) return Material.SEA_LANTERN;
                if (av > vmax - 1.6 || u < -60.5) return Material.GOLD_BLOCK;
                if (Math.floorMod((int) Math.floor(av), 12) < 2) return Material.LIGHT_BLUE_CONCRETE;
                return w - wc + 1 > 0 ? Material.LIGHT_GRAY_CONCRETE : Material.GRAY_CONCRETE;
            }
        }
        // Nacelles en bout d'aile.
        if (u >= -66 && u <= -34 && av > 60) {
            double rv = av - 68, rw = w - (68 - 16) * 0.07 + 1;
            double r = u > -42 ? 2.6 * (-34 - u) / 8 : 2.6;
            if (rv * rv + rw * rw <= r * r) {
                if (u < -65) return Material.SEA_LANTERN;
                return Math.floorMod((int) Math.floor(u), 5) == 0 ? Material.GOLD_BLOCK : Material.WHITE_CONCRETE;
            }
        }
        return null;
    }

    private static Material moteur(double u, double r, double arriere) {
        if (u < arriere + 1.5) return r < 4.5 ? Material.SEA_LANTERN : r < 7 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.GOLD_BLOCK;
        if (Math.floorMod((int) Math.floor(u), 6) == 0) return Material.GOLD_BLOCK;
        return r > 8.5 ? Material.GRAY_CONCRETE : Material.POLISHED_DEEPSLATE;
    }

    private static double sq(double x) { return x * x; }
}
