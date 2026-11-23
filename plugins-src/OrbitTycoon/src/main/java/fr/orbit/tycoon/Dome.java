package fr.orbit.tycoon;

import org.bukkit.Material;

/**
 * Dôme d'observation du spawn et vaisseau amiral.
 * Le dôme (rayon 34) flotte juste à l'extérieur du disque du spawn, relié par un tunnel de verre.
 * Une grande baie de verre clair, côté extérieur, donne sur le vaisseau (≈ 186 × 150 blocs) qui flotte plus loin.
 * Tout est dessiné ici ; « /tycoon dome generer [confirmer] » le construit (confirmer = efface et reconstruit).
 */
final class Dome {

    static final int Y = Zone.Y;
    /** Direction (degrés) du dôme depuis le centre du spawn : entre l'avenue de la Mobzone (216°) et le pavillon de 252°. */
    static final double ANGLE = 240;
    static final int RD = 34;                       // rayon du dôme
    static final int R_SPAWN = Zone.BASE.rayon;      // 125
    static final int DIST = R_SPAWN + 14 + RD;       // centre du dôme à 173 blocs du centre du spawn
    static final int DIST_VAISSEAU = DIST + RD + 40 + 72;
    static final int Y_VAISSEAU = Y + 20;
    /** Inclinaison du vaisseau vers le dôme (on voit le dessus des ailes). */
    private static final double ROULIS = Math.toRadians(15);

    static final double FX = Math.cos(Math.toRadians(ANGLE)), FZ = Math.sin(Math.toRadians(ANGLE));
    static final int CX = (int) Math.round(FX * DIST), CZ = (int) Math.round(FZ * DIST);

    private final Plans p;

    Dome(Plans p) { this.p = p; }

    /** Vrai si (dx, dz) est dans le couloir qui relie le spawn au dôme (le garde-corps du spawn y est ouvert). */
    static boolean couloir(double dx, double dz) {
        double along = dx * FX + dz * FZ, perp = Math.abs(-dx * FZ + dz * FX);
        return along > R_SPAWN - 8 && perp <= 3.6;
    }

    // ------------------------------------------------------------------ chantier

    void dessiner(Chantier c, boolean nettoyer) {
        if (nettoyer) {
            p.retirerDeco(c, CX, CZ, RD + 4, "dome");
            for (int dx = -RD - 1; dx <= RD + 1; dx++) {
                final int fx = dx;
                c.etape(() -> {
                    for (int dz = -RD - 1; dz <= RD + 1; dz++)
                        if (fx * fx + dz * dz <= (RD + 1) * (RD + 1))
                            for (int y = Y - 30; y <= Y + RD + 2; y++) c.vider(CX + fx, y, CZ + dz);
                    c.compter(3000);
                });
            }
            vaisseau(c, true);
        }
        pont(c);
        sol(c);
        coque(c);
        decor(c);
        vaisseau(c, false);
    }

    // ------------------------------------------------------------------ pont / tunnel

    private void pont(Chantier c) {
        c.etape(() -> {
            for (int s = R_SPAWN - 3; s <= DIST - RD + 2; s++)
                for (int q = -6; q <= 6; q++) {
                    int x = (int) Math.round(FX * s - FZ * q), z = (int) Math.round(FZ * s + FX * q);
                    double along = x * FX + z * FZ, perp = Math.abs(-x * FZ + z * FX);
                    if (perp > 5.2) continue;
                    if (perp <= 3.6) {
                        Material sol = perp > 2.5 ? Material.GOLD_BLOCK
                                : (perp < 0.6 && ((int) along) % 4 == 0) ? Material.SEA_LANTERN : Material.LIGHT_BLUE_CONCRETE;
                        c.set(x, Y, z, sol);
                        c.set(x, Y - 1, z, Material.POLISHED_DEEPSLATE);
                        // Ouverture dans le garde-corps du spawn.
                        if (along < R_SPAWN + 1.5) for (int y = Y + 1; y <= Y + 7; y++) c.vider(x, y, z);
                    } else if (along < R_SPAWN + 1.5) continue;
                    else c.set(x, Y, z, Material.POLISHED_DEEPSLATE);
                    // Tunnel de verre (demi-cylindre) entre le bord du spawn et le dôme.
                    if (along >= R_SPAWN + 1 && along <= DIST - RD + 1)
                        for (int h = 0; h <= 6; h++) {
                            double r = Math.sqrt(perp * perp + h * h);
                            if (r < 4.6 || r > 5.6) continue;
                            boolean nervure = ((int) along) % 5 == 0;
                            c.set(x, Y + h, z, nervure ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_STAINED_GLASS);
                        }
                }
        });
    }

    // ------------------------------------------------------------------ sol (carte stellaire) et coque dessous

    private static double cosVersVaisseau(int dx, int dz) {
        double hr = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        return (dx * FX + dz * FZ) / hr;
    }

    /** Côté baie (vers le vaisseau) : secteur de ± 42°. */
    private static boolean secteurBaie(int dx, int dz) { return cosVersVaisseau(dx, dz) > Math.cos(Math.toRadians(42)); }

    private void sol(Chantier c) {
        p.disque(c, CX, CZ, RD, (dx, dz, d) -> {
            int x = CX + dx, z = CZ + dz;
            Material m;
            if (d < 2.2) m = Material.SHROOMLIGHT;
            else if (d < 13) {
                // Carte stellaire : fond noir étoilé, orbites bleues, planètes.
                m = Math.floorMod(dx * 7 + dz * 13, 23) == 0 ? Material.WHITE_CONCRETE : Material.BLACK_CONCRETE;
                if (Math.abs(d - 4.5) < 0.5 || Math.abs(d - 7.5) < 0.5 || Math.abs(d - 10.5) < 0.5) m = Material.LIGHT_BLUE_CONCRETE;
                if (pres(dx, dz, 4.5, 30, 0.9)) m = Material.RED_CONCRETE;
                if (pres(dx, dz, 7.5, 150, 1.4)) m = Material.LIME_CONCRETE;
                if (pres(dx, dz, 10.5, 260, 1.6)) m = Material.CYAN_CONCRETE;
                if (pres(dx, dz, 10.5, 40, 1.1)) m = Material.ORANGE_CONCRETE;
            } else if (d < 14.2) m = Material.GOLD_BLOCK;
            else if (d > RD - 1.2) m = Material.GOLD_BLOCK;
            else if (Math.abs(d - (RD - 3)) < 0.5 && Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dz, dx))), 12) < 3)
                m = Material.SEA_LANTERN;
            else if (Math.abs(d - 24) < 0.5) m = Material.LIGHT_BLUE_CONCRETE;
            else m = (((dx >> 2) + (dz >> 2)) & 1) == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES;
            c.set(x, Y, z, m);
            c.set(x, Y - 1, z, Material.POLISHED_DEEPSLATE);
            // Coque dessous : demi-sphère aplatie de métal sombre, cerclée de lumière.
            int prof = 2 + (int) (Math.sqrt(Math.max(0, 1 - (d / RD) * (d / RD))) * 22);
            for (int k = Math.max(2, prof - 2); k <= prof; k++)
                c.set(x, Y - k, z, k % 5 == 0 ? Material.SEA_LANTERN : Material.DEEPSLATE_TILES);
        });
        // Réacteur sous le dôme.
        c.etape(() -> {
            for (int y = Y - 30; y <= Y - 23; y++)
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    c.set(CX + dx, y, CZ + dz, (dx == 0 && dz == 0) ? Material.SEA_LANTERN
                            : y % 2 == 0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.GOLD_BLOCK);
        });
    }

    private static boolean pres(int dx, int dz, double r, double angDeg, double rayon) {
        double px = Math.cos(Math.toRadians(angDeg)) * r, pz = Math.sin(Math.toRadians(angDeg)) * r;
        return (dx - px) * (dx - px) + (dz - pz) * (dz - pz) <= rayon * rayon;
    }

    // ------------------------------------------------------------------ coque de verre

    private void coque(Chantier c) {
        for (int dx = -RD; dx <= RD; dx++) {
            final int fx = dx;
            c.etape(() -> {
                for (int dz = -RD; dz <= RD; dz++)
                    for (int dy = 1; dy <= RD; dy++) {
                        double d = Math.sqrt(fx * fx + dy * dy + dz * dz);
                        if (d > RD || d < RD - 1.0) continue;
                        // Entrance (côté spawn) : arche de 7 de large, 7 de haut.
                        double along = -(fx * FX + dz * FZ), perp = Math.abs(-fx * FZ + dz * FX);
                        if (along > 0 && perp < 3.6 && dy <= 6) continue;
                        double hr = Math.max(1, Math.sqrt(fx * fx + dz * dz));
                        double cos = (fx * FX + dz * FZ) / hr;
                        boolean baie = cos > Math.cos(Math.toRadians(42)) && dy <= 24;
                        Material m;
                        if (baie) {
                            // Grande baie de verre clair, cadre doré sur les bords.
                            boolean bord = cos < Math.cos(Math.toRadians(40)) || dy >= 23;
                            m = bord ? Material.GOLD_BLOCK : Material.GLASS;
                        } else {
                            double ang = Math.toDegrees(Math.atan2(dz, fx)) + 180;
                            boolean nervure = (ang % 30) < Math.toDegrees(1.0 / hr) || dy % 9 == 0;
                            m = nervure ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_STAINED_GLASS;
                        }
                        if (dy >= RD - 1 && hr < 4) m = Material.SEA_LANTERN;   // oculus lumineux au sommet
                        c.set(CX + fx, Y + dy, CZ + dz, m);
                    }
            });
        }
        // Cadre doré de l'entrée.
        c.etape(() -> {
            for (int q = -4; q <= 4; q++)
                for (int h = 1; h <= 7; h++) {
                    if (Math.abs(q) < 4 && h < 7) continue;
                    int x = (int) Math.round(CX - FX * (RD - 0.5) - FZ * q);
                    int z = (int) Math.round(CZ - FZ * (RD - 0.5) + FX * q);
                    c.set(x, Y + h, z, Material.GOLD_BLOCK);
                }
        });
    }

    // ------------------------------------------------------------------ décor intérieur

    /** Position dans le dôme : angle (degrés, 0 = vers le vaisseau) et distance au centre. */
    private static int[] pos(double angDeg, double r) {
        double a = Math.toRadians(ANGLE + angDeg);
        return new int[]{CX + (int) Math.round(Math.cos(a) * r), CZ + (int) Math.round(Math.sin(a) * r)};
    }

    private void decor(Chantier c) {
        gradins(c);
        telescope(c);
        planete(c);
        String[] postes = {"NAVIGATION", "COMMUNICATIONS", "RÉACTEUR", "CARTOGRAPHIE", "SCANNER", "LOGISTIQUE"};
        double[] angles = {70, -70, 110, -110, 145, -145};
        for (int i = 0; i < 6; i++) console(c, angles[i], 21, postes[i]);
        double[] jardins = {55, -55, 92, -92, 128, -128, 163, -163};
        for (double a : jardins) jardiniere(c, a, 30);
        for (double a : new double[]{60, -60, 100, -100, 140, -140, 180}) {
            int[] q = pos(a, 16.5);
            c.etape(() -> p.lampadaire(c, q[0], Y, q[1]));
        }
        fusee(c, 162, 25);
        fusee(c, -162, 25);
        int[] e = pos(180, RD - 3);
        p.holo(c, "dome", e[0] + 0.5, Y + 9, e[1] + 0.5,
                "<gradient:#62CFF4:#E0B864><bold>DÔME D'OBSERVATION</bold></gradient>\n<gray>Base Orbit", 2.0f);
        int[] b = pos(0, 22);
        p.holo(c, "dome", b[0] + 0.5, Y + 7.5, b[1] + 0.5,
                "<#E0B864><bold>Vaisseau amiral</bold>\n<gray>Le croiseur de la station ORBIT", 1.6f);
    }

    /** Gradins côté baie : trois marches qui montent vers la vitre, garde-corps de verre au bord. */
    private void gradins(Chantier c) {
        p.disque(c, CX, CZ, RD - 3, (dx, dz, d) -> {
            if (d < 14 || !secteurBaie(dx, dz)) return;
            int h = d < 18 ? 1 : d < 22 ? 2 : 3;
            int x = CX + dx, z = CZ + dz;
            for (int y = Y + 1; y <= Y + h; y++) {
                boolean bord = (h == 1 && d > 17.2) || (h == 2 && d > 21.2);
                c.set(x, y, z, y < Y + h ? Material.POLISHED_DEEPSLATE : bord ? Material.GOLD_BLOCK
                        : (((dx >> 1) + (dz >> 1)) & 1) == 0 ? Material.SMOOTH_QUARTZ : Material.CALCITE);
            }
            if (d > RD - 4.2) c.set(x, Y + 4, z, Material.LIGHT_BLUE_STAINED_GLASS);   // garde-corps
        });
    }

    /** Télescope sur les gradins, pointé vers le vaisseau. */
    private void telescope(Chantier c) {
        c.etape(() -> {
            int[] b = pos(0, 26);
            for (int[] q : new int[][]{b}) {
                c.set(q[0], Y + 4, q[1], Material.GOLD_BLOCK);
                c.set(q[0], Y + 5, q[1], Material.POLISHED_DEEPSLATE);
                c.set(q[0], Y + 6, q[1], Material.POLISHED_DEEPSLATE);
                double ox = q[0] + 0.5 - FX * 3, oy = Y + 7.5, oz = q[1] + 0.5 - FZ * 3;
                double ux = FX * Math.cos(Math.toRadians(12)), uy = Math.sin(Math.toRadians(12)), uz = FZ * Math.cos(Math.toRadians(12));
                for (double t = 0; t <= 9; t += 0.4) {
                    double ax = ox + ux * t, ay = oy + uy * t, az = oz + uz * t;
                    double rayon = t > 7.5 ? 1.5 : 1.1;
                    for (int ix = -2; ix <= 2; ix++) for (int iy = -2; iy <= 2; iy++) for (int iz = -2; iz <= 2; iz++) {
                        double bx = Math.floor(ax) + ix + 0.5, by = Math.floor(ay) + iy + 0.5, bz = Math.floor(az) + iz + 0.5;
                        double vx = bx - ax, vy = by - ay, vz = bz - az;
                        double dl = vx * ux + vy * uy + vz * uz;
                        double perp2 = vx * vx + vy * vy + vz * vz - dl * dl;
                        if (perp2 > rayon * rayon || Math.abs(dl) > 0.3) continue;
                        Material m = t > 8.6 ? Material.LIGHT_BLUE_STAINED_GLASS : (t < 0.5 || (t > 7.4 && t < 8)) ? Material.GOLD_BLOCK
                                : Material.LIGHT_BLUE_CONCRETE;
                        c.set((int) Math.floor(bx), (int) Math.floor(by), (int) Math.floor(bz), m);
                    }
                }
            }
            // Deux bancs de part et d'autre.
            for (int s = -1; s <= 1; s += 2) {
                int[] q = pos(18 * s, 20);
                c.set(q[0], Y + 3, q[1], Material.SMOOTH_QUARTZ_SLAB);
            }
        });
    }

    /** Planète-hologramme suspendue au-dessus de la carte stellaire. */
    private void planete(Chantier c) {
        int py = Y + 17;
        c.etape(() -> {
            for (int dx = -5; dx <= 5; dx++) for (int dy = -5; dy <= 5; dy++) for (int dz = -5; dz <= 5; dz++) {
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d > 4.3) continue;
                c.set(CX + dx, py + dy, CZ + dz, d < 2.5 ? Material.SEA_LANTERN
                        : (dy == 0 || dy == 2) ? Material.CYAN_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS);
            }
            for (int y = py + 5; y < Y + RD - 1; y++) c.set(CX, y, CZ, Material.IRON_CHAIN);
        });
        p.anneauIncline(c, CX, py, CZ, 7, Math.toRadians(18), Math.toRadians(ANGLE), Material.GOLD_BLOCK, Material.LIGHT_BLUE_STAINED_GLASS);
        p.anneauIncline(c, CX, py, CZ, 9.5, Math.toRadians(-12), Math.toRadians(ANGLE + 70), Material.LIGHT_BLUE_STAINED_GLASS, Material.GOLD_BLOCK);
    }

    /** Console de contrôle : pupitre, écran lumineux, étiquette. */
    private void console(Chantier c, double ang, double r, String nom) {
        c.etape(() -> {
            double a = Math.toRadians(ANGLE + ang), tx = -Math.sin(a), tz = Math.cos(a), ox = Math.cos(a), oz = Math.sin(a);
            for (int q = -1; q <= 1; q++) {
                int x = (int) Math.round(CX + ox * r + tx * q), z = (int) Math.round(CZ + oz * r + tz * q);
                int xe = (int) Math.round(CX + ox * (r + 1) + tx * q), ze = (int) Math.round(CZ + oz * (r + 1) + tz * q);
                int xl = (int) Math.round(CX + ox * (r + 2) + tx * q), zl = (int) Math.round(CZ + oz * (r + 2) + tz * q);
                c.set(x, Y + 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
                c.set(x, Y + 2, z, q == 0 ? Material.DAYLIGHT_DETECTOR : Material.POLISHED_BLACKSTONE_SLAB);
                c.set(xe, Y + 1, ze, Material.POLISHED_BLACKSTONE_BRICKS);
                c.set(xe, Y + 2, ze, Material.LIGHT_BLUE_STAINED_GLASS);
                c.set(xe, Y + 3, ze, Material.LIGHT_BLUE_STAINED_GLASS);
                c.set(xe, Y + 4, ze, Material.GOLD_BLOCK);
                c.set(xl, Y + 2, zl, Material.SEA_LANTERN);
                c.set(xl, Y + 3, zl, Material.SEA_LANTERN);
            }
        });
        int[] e = pos(ang, r + 1);
        p.holo(c, "dome", e[0] + 0.5, Y + 5.3, e[1] + 0.5, "<#62CFF4><bold>" + nom, 0.9f);
    }

    /** Jardinière ronde : mousse, azalée fleurie au centre, bord sombre. */
    private void jardiniere(Chantier c, double ang, double r) {
        int[] q = pos(ang, r);
        c.etape(() -> {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 2.3) continue;
                int x = q[0] + dx, z = q[1] + dz;
                c.set(x, Y + 1, z, d > 1.6 ? Material.POLISHED_DEEPSLATE : Material.MOSS_BLOCK);
                if (d <= 1.6 && d > 0) c.set(x, Y + 2, z, (dx + dz) % 2 == 0 ? Material.MOSS_CARPET : Material.AZALEA);
            }
            c.set(q[0], Y + 2, q[1], Material.FLOWERING_AZALEA);
        });
    }

    /** Maquette de fusée (9 de haut) : corps blanc, bandes bleues, pointe dorée, ailerons. */
    private void fusee(Chantier c, double ang, double r) {
        int[] q = pos(ang, r);
        c.etape(() -> {
            int x0 = q[0], z0 = q[1];
            for (int y = 1; y <= 9; y++) {
                int rr = y <= 7 ? 1 : 0;
                for (int dx = -rr; dx <= rr; dx++) for (int dz = -rr; dz <= rr; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > 1 && y > 1) continue;
                    Material m = y >= 8 ? Material.GOLD_BLOCK : y % 3 == 0 ? Material.LIGHT_BLUE_CONCRETE : Material.WHITE_CONCRETE;
                    if (y == 5 && (dx != 0 || dz != 0)) m = Material.LIGHT_BLUE_STAINED_GLASS;   // hublots
                    c.set(x0 + dx, Y + y, z0 + dz, m);
                }
            }
            c.set(x0, Y + 10, z0, Material.LIGHTNING_ROD);
            for (int[] f : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
                c.set(x0 + f[0], Y + 1, z0 + f[1], Material.LIGHT_BLUE_CONCRETE);
                c.set(x0 + f[0], Y + 2, z0 + f[1], Material.GOLD_BLOCK);
            }
            c.set(x0, Y, z0, Material.SHROOMLIGHT);
        });
    }

    // ------------------------------------------------------------------ vaisseau amiral

    // Repère du vaisseau : U = longueur (le long de la tangente), V = envergure (vers l'extérieur, incliné), W = hauteur.
    private static final double OX = FX * DIST_VAISSEAU, OY = Y_VAISSEAU + 0.5, OZ = FZ * DIST_VAISSEAU;
    private static final double UX = -FZ, UZ = FX;
    private static final double VX = FX * Math.cos(ROULIS), VY = Math.sin(ROULIS), VZ = FZ * Math.cos(ROULIS);
    private static final double WX = -FX * Math.sin(ROULIS), WY = Math.cos(ROULIS), WZ = -FZ * Math.sin(ROULIS);

    private static Material bloc(int x, int y, int z) {
        double dx = x + 0.5 - OX, dy = y + 0.5 - OY, dz = z + 0.5 - OZ;
        double u = dx * UX + dz * UZ;
        double v = dx * VX + dy * VY + dz * VZ;
        double w = dx * WX + dy * WY + dz * WZ;
        return forme(u, v, w);
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

    /** Pose (ou efface) la coque du vaisseau : seuls les blocs de surface sont posés, l'intérieur reste vide. */
    private void vaisseau(Chantier c, boolean effacer) {
        double[][] coins = new double[8][];
        int i = 0;
        for (double u : new double[]{-106, 84}) for (double v : new double[]{-78, 78}) for (double w : new double[]{-14, 37})
            coins[i++] = new double[]{OX + u * UX + v * VX + w * WX, OY + v * VY + w * WY, OZ + u * UZ + v * VZ + w * WZ};
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (double[] k : coins) {
            x0 = Math.min(x0, (int) Math.floor(k[0])); x1 = Math.max(x1, (int) Math.ceil(k[0]));
            y0 = Math.min(y0, (int) Math.floor(k[1])); y1 = Math.max(y1, (int) Math.ceil(k[1]));
            z0 = Math.min(z0, (int) Math.floor(k[2])); z1 = Math.max(z1, (int) Math.ceil(k[2]));
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
