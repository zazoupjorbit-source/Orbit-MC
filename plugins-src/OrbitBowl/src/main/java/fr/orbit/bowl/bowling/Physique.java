package fr.orbit.bowl.bowling;

import java.util.Random;

/**
 * Physique d'une piste, vue de dessus (aucune dépendance au serveur : testable seule). Unités : blocs et secondes.
 * La piste va vers le nord (z décroissant) : ligne de faute en z = 0, quille de tête en z = -18, fond du pin deck
 * en z = -21, fosse derrière. Une quille debout est un disque ; une quille qui tombe est un segment (sa base glisse,
 * son corps bascule dans le sens du choc) qui peut faucher les voisines, comme les vraies.
 */
public final class Physique {

    public static final double S = 0.88, RANG = S * Math.sqrt(3) / 2;
    public static final double RB = 0.29, RP = 0.16, EP = 0.17, H = 1.1;
    public static final double DEMI = 1.5, PAROI = 2.45, TETE = -18.0, DECK = -21.0, FOSSE = -23.0, PAROI_Z = -16.0;
    static final double MB = 5.0, MP = 1.5, E_BOULE = 0.72, E_QUILLES = 0.62, SEUIL = 0.3;
    static final int SOUS_PAS = 10;
    static final double DT = 0.05 / SOUS_PAS;

    public static final int DEBOUT = 0, TOMBE = 1, COUCHEE = 2, FOSSE_Q = 3, RETIREE = 4;

    /** Position des quilles (n° 1 à 10) par rapport au centre de la piste et à la tête. */
    public static final double[] DX = new double[10], DZ = new double[10];

    static {
        int k = 0;
        for (int r = 0; r < 4; r++) {
            for (int j = 0; j <= r; j++) {
                DX[k] = (j - r / 2.0) * S;
                DZ[k] = -r * RANG;
                k++;
            }
        }
    }

    public final double cx;
    private final Random rnd;

    // boule
    public double bx, bz, bvx, bvz, effet, by;
    public boolean roule, gouttiere, dansFosse;
    public int ticksBoule;

    // quilles
    public final double[] px = new double[10], pz = new double[10], pvx = new double[10], pvz = new double[10];
    public final double[] dirX = new double[10], dirZ = new double[10], angle = new double[10], vitAngle = new double[10];
    public final int[] etat = new int[10];
    public final double[] tremble = new double[10];
    /** Rotation du corps autour de la verticale (rad/s) : une quille qui vole tournoie et balaie ses voisines. */
    public final double[] tourne = new double[10];
    /** Chocs du dernier tick (pour les sons) : 0 rien, 1 léger, 2 fort. */
    public int chocBoule, chocQuilles;
    private int calme;

    public Physique(double cx, long graine) {
        this.cx = cx;
        this.rnd = new Random(graine);
        rack();
    }

    public void rack() {
        for (int k = 0; k < 10; k++) {
            px[k] = cx + DX[k];
            pz[k] = TETE + DZ[k];
            pvx[k] = pvz[k] = 0;
            angle[k] = vitAngle[k] = tremble[k] = tourne[k] = 0;
            etat[k] = DEBOUT;
        }
    }

    /** Retire les quilles tombées (balayage de la machine) : il ne reste que les debout. */
    public void balayer() {
        for (int k = 0; k < 10; k++) {
            if (etat[k] != DEBOUT) {
                etat[k] = RETIREE;
            }
            pvx[k] = pvz[k] = 0;
            tremble[k] = 0;
        }
    }

    /**
     * Lance la boule depuis {@code x} (juste après la ligne de faute), {@code angle} en radians (0 = tout droit, positif
     * vers l'est), {@code vitesse} en blocs/s, {@code effet} de -1 (crochet vers l'ouest) à 1 (vers l'est).
     */
    public void lancer(double x, double angleRad, double vitesse, double effet) {
        bx = Math.max(cx - DEMI + RB * 0.5, Math.min(cx + DEMI - RB * 0.5, x));
        bz = -0.3;
        bvx = Math.sin(angleRad) * vitesse;
        bvz = -Math.cos(angleRad) * vitesse;
        this.effet = effet;
        by = 0;
        roule = true;
        gouttiere = false;
        dansFosse = false;
        ticksBoule = 0;
        calme = 0;
    }

    public int debout() {
        int n = 0;
        for (int e : etat) {
            n += e == DEBOUT ? 1 : 0;
        }
        return n;
    }

    /** Lancer terminé : boule dans la fosse (ou arrêtée) et quilles immobiles depuis un moment. */
    public boolean fini() {
        return !roule && calme > 12 || ticksBoule > 20 * 12;
    }

    // ------------------------------------------------------------------ un tick (1/20 s)

    public void tick() {
        chocBoule = chocQuilles = 0;
        for (int s = 0; s < SOUS_PAS; s++) {
            pas();
        }
        ticksBoule++;
        boolean bouge = roule;
        for (int k = 0; k < 10; k++) {
            if (etat[k] == TOMBE || etat[k] == COUCHEE) {
                // la base glisse en frottant, le corps finit de basculer
                pvx[k] *= 0.9;
                pvz[k] *= 0.9;
                tourne[k] *= etat[k] == TOMBE ? 0.93 : 0.8;
                if (etat[k] == TOMBE) {
                    angle[k] = Math.min(Math.PI / 2, angle[k] + vitAngle[k] * 0.05);
                    vitAngle[k] = Math.min(14, vitAngle[k] + 9 * 0.05);
                    if (angle[k] >= Math.PI / 2 - 1e-6) {
                        etat[k] = COUCHEE;
                    }
                    bouge = true;
                }
                if (Math.hypot(pvx[k], pvz[k]) > 0.15) {
                    bouge = true;
                }
            }
            if (tremble[k] > 0) {
                tremble[k] = Math.max(0, tremble[k] - 0.05);
                bouge = true;
            }
        }
        calme = bouge ? 0 : calme + 1;
    }

    private void pas() {
        if (roule) {
            boule();
        }
        for (int k = 0; k < 10; k++) {
            if (etat[k] != TOMBE && etat[k] != COUCHEE) {
                continue;
            }
            px[k] += pvx[k] * DT;
            pz[k] += pvz[k] * DT;
            if (tourne[k] != 0) {
                double c = Math.cos(tourne[k] * DT), s = Math.sin(tourne[k] * DT);
                double nx = dirX[k] * c - dirZ[k] * s;
                dirZ[k] = dirX[k] * s + dirZ[k] * c;
                dirX[k] = nx;
            }
            // parois (kickbacks) de part et d'autre du pin deck : la quille rebondit vers le jeu
            if (pz[k] < PAROI_Z) {
                if (px[k] < cx - PAROI + EP) {
                    px[k] = cx - PAROI + EP;
                    pvx[k] = Math.abs(pvx[k]) * 0.7;
                    chocQuilles = Math.max(chocQuilles, 1);
                } else if (px[k] > cx + PAROI - EP) {
                    px[k] = cx + PAROI - EP;
                    pvx[k] = -Math.abs(pvx[k]) * 0.7;
                    chocQuilles = Math.max(chocQuilles, 1);
                }
            }
            if (pz[k] < DECK - 0.15) {
                etat[k] = FOSSE_Q;
            }
        }
        // quilles entre elles
        for (int a = 0; a < 10; a++) {
            if (etat[a] != TOMBE && etat[a] != COUCHEE) {
                continue;
            }
            for (int b = 0; b < 10; b++) {
                if (a == b) {
                    continue;
                }
                if (etat[b] == DEBOUT) {
                    corpsSurDebout(a, b);
                } else if ((etat[b] == TOMBE || etat[b] == COUCHEE) && a < b) {
                    entreCouchees(a, b);
                }
            }
        }
    }

    private void boule() {
        // crochet : la boule glisse d'abord, puis l'effet la fait tourner dans le dernier tiers
        double d = -bz;
        if (!gouttiere && d > 6.5) {
            double a = effet * 2.6 * Math.min(1, (d - 6.5) / 5);
            bvx += a * DT;
        }
        bvz *= 1 - 0.012 * DT;
        bx += bvx * DT;
        bz += bvz * DT;
        if (!gouttiere && bz > DECK && Math.abs(bx - cx) > DEMI) {
            gouttiere = true;
            bx = cx + Math.signum(bx - cx) * (DEMI + 0.5);
            bvx = 0;
            by = -0.7;
        }
        if (gouttiere) {
            bvx = 0;
        }
        if (bz < DECK) {
            if (!dansFosse) {
                dansFosse = true;
                by = -1.0;
            }
            if (bz < FOSSE + RB) {
                bz = FOSSE + RB;
                roule = false;
            }
            return;
        }
        if (gouttiere) {
            return;
        }
        for (int k = 0; k < 10; k++) {
            if (etat[k] == DEBOUT) {
                double dx = px[k] - bx, dz = pz[k] - bz, dist = Math.hypot(dx, dz);
                if (dist < RB + RP && dist > 1e-6) {
                    double nx = dx / dist, nz = dz / dist;
                    double vn = bvx * nx + bvz * nz;
                    if (vn <= 0) {
                        continue;
                    }
                    double j = (1 + E_BOULE) * vn / (1 / MB + 1 / MP);
                    bvx -= j / MB * nx;
                    bvz -= j / MB * nz;
                    renverser(k, j / MP * nx, j / MP * nz, 1.0);
                    px[k] = bx + nx * (RB + RP);
                    pz[k] = bz + nz * (RB + RP);
                    chocBoule = 2;
                }
            } else if (etat[k] == TOMBE || etat[k] == COUCHEE) {
                // la boule pousse la quille au sol (point le plus proche de son corps)
                double[] q = proche(k, bx, bz);
                double dx = q[0] - bx, dz = q[1] - bz, dist = Math.hypot(dx, dz);
                if (dist < RB + EP && dist > 1e-6) {
                    double nx = dx / dist, nz = dz / dist;
                    double vn = (bvx - pvx[k]) * nx + (bvz - pvz[k]) * nz;
                    if (vn <= 0) {
                        continue;
                    }
                    double j = (1 + 0.4) * vn / (1 / MB + 1 / MP);
                    bvx -= j / MB * nx;
                    bvz -= j / MB * nz;
                    pvx[k] += j / MP * nx;
                    pvz[k] += j / MP * nz;
                    chocBoule = Math.max(chocBoule, 1);
                }
            }
        }
    }

    /** La quille {@code k} se met à tomber avec cette vitesse ; {@code force} : 1 = choc franc. */
    private void renverser(int k, double vx, double vz, double force) {
        // un choc en 3D ne part jamais exactement dans l'axe : léger écart aléatoire
        double ecart = rnd.nextGaussian() * 0.18;
        double c = Math.cos(ecart), s = Math.sin(ecart);
        double rx = vx * c - vz * s;
        vz = vx * s + vz * c;
        vx = rx;
        double v = Math.hypot(vx, vz);
        etat[k] = TOMBE;
        pvx[k] = vx * 0.85;
        pvz[k] = vz * 0.85;
        tourne[k] = (rnd.nextBoolean() ? 1 : -1) * (2 + rnd.nextDouble() * 9) * Math.min(1, 0.3 + v / 5);
        if (v > 1e-6) {
            dirX[k] = vx / v;
            dirZ[k] = vz / v;
        } else {
            double a = rnd.nextDouble() * Math.PI * 2;
            dirX[k] = Math.cos(a);
            dirZ[k] = Math.sin(a);
        }
        angle[k] = 0.15;
        vitAngle[k] = 3 + Math.min(9, v * 1.4) * force;
    }

    /** Point du corps de la quille {@code k} (segment base → tête) le plus proche de (x, z). */
    private double[] proche(int k, double x, double z) {
        double len = H * Math.sin(angle[k]);
        double ax = px[k], az = pz[k], bx2 = ax + dirX[k] * len, bz2 = az + dirZ[k] * len;
        double ex = bx2 - ax, ez = bz2 - az, l2 = ex * ex + ez * ez;
        double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - ax) * ex + (z - az) * ez) / l2));
        return new double[]{ax + ex * t, az + ez * t, t};
    }

    /** Le corps d'une quille qui tombe touche une quille debout : elle la pousse, et la fait tomber si le choc est franc. */
    private void corpsSurDebout(int a, int b) {
        double[] q = proche(a, px[b], pz[b]);
        double dx = px[b] - q[0], dz = pz[b] - q[1], dist = Math.hypot(dx, dz);
        if (dist >= RP + EP || dist < 1e-6) {
            return;
        }
        double nx = dx / dist, nz = dz / dist;
        // vitesse du point de contact : glissement de la base + bascule du corps (plus vite vers la tête)
        double bras = q[2] * H * Math.cos(angle[a]) * vitAngle[a];
        double cvx = pvx[a] + dirX[a] * bras, cvz = pvz[a] + dirZ[a] * bras;
        double vn = cvx * nx + cvz * nz;
        if (vn <= 0.05) {
            return;
        }
        double j = (1 + E_QUILLES) * vn / 2;
        pvx[a] -= j * nx * 0.6;
        pvz[a] -= j * nz * 0.6;
        double v = j;
        if (v > SEUIL || rnd.nextDouble() < (v - 0.2) / (SEUIL - 0.2)) {
            renverser(b, j * nx, j * nz, 0.8);
            chocQuilles = 2;
        } else {
            // simple frôlement : la quille vacille et reste debout
            tremble[b] = Math.max(tremble[b], 0.5);
            px[b] = q[0] + nx * (RP + EP);
            pz[b] = q[1] + nz * (RP + EP);
            chocQuilles = Math.max(chocQuilles, 1);
        }
    }

    private void entreCouchees(int a, int b) {
        double dx = px[b] - px[a], dz = pz[b] - pz[a], dist = Math.hypot(dx, dz);
        double r = 2 * EP + 0.06;
        if (dist >= r || dist < 1e-6) {
            return;
        }
        double nx = dx / dist, nz = dz / dist;
        double vn = (pvx[a] - pvx[b]) * nx + (pvz[a] - pvz[b]) * nz;
        if (vn <= 0) {
            return;
        }
        double j = (1 + 0.3) * vn / 2;
        pvx[a] -= j * nx;
        pvz[a] -= j * nz;
        pvx[b] += j * nx;
        pvz[b] += j * nz;
    }

    // ------------------------------------------------------------------ aide au bot

    /** Abscisse de la boule (sans quilles) quand elle arrive à {@code zCible}, pour un lancer donné. */
    public static double trajectoire(double cx, double x, double angleRad, double vitesse, double effet, double zCible) {
        Physique p = new Physique(cx, 1);
        for (int k = 0; k < 10; k++) {
            p.etat[k] = RETIREE;
        }
        p.lancer(x, angleRad, vitesse, effet);
        for (int i = 0; i < 20 * 8 && p.roule; i++) {
            for (int s = 0; s < SOUS_PAS; s++) {
                p.boule();
                if (p.bz <= zCible) {
                    return p.gouttiere ? Double.NaN : p.bx;
                }
            }
        }
        return Double.NaN;
    }
}
