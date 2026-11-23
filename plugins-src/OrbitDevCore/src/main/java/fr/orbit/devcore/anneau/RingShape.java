package fr.orbit.devcore.anneau;

import org.bukkit.Material;

/**
 * Forme d'un anneau-portail (même style que ceux d'OrbitTycoon) : couronne dorée et bleu glacé,
 * repères en lanternes aquatiques, autour d'un disque de rayon {@code r}. Calcul pur, sans accès au monde :
 * sert au dessin de La Station comme à la commande /anneau sur les autres serveurs.
 */
public final class RingShape {

    /** Épaisseur de la couronne autour du disque. */
    public static final double CROWN = 1.7;
    /** Écart entre le disque et le bord intérieur de la couronne. */
    public static final double GAP = 0.3;

    public interface Sink {
        void set(int x, int y, int z, Material m);
    }

    private RingShape() {
    }

    /** Rayon extérieur de la couronne pour un disque de rayon r. */
    public static double outer(double r) {
        return r + GAP + CROWN;
    }

    /**
     * Hauteur du centre du disque quand l'anneau est posé sur un sol dont le dessus est à {@code feetY} :
     * le bas du disque est un peu sous les pieds, un joueur qui marche passe donc dedans.
     */
    public static double centerY(double feetY, double r) {
        return feetY + r - 0.6;
    }

    /** Plus grand rayon de disque qui tient sous un plafond (dernier bloc libre à {@code topY}, sol à {@code feetY}). */
    public static double fit(double feetY, int topY) {
        return Math.max(1.5, (topY + 1 - feetY + 0.6 - GAP - CROWN) / 2.0);
    }

    /**
     * Calcule les blocs de la couronne. (cx, cy, cz) centre du disque, (nx, nz) normale horizontale
     * (sens de passage), yMin : aucun bloc en dessous (le sol coupe l'anneau).
     */
    public static void draw(double cx, double cy, double cz, double r, double nx, double nz, int yMin, Sink sink) {
        double len = Math.hypot(nx, nz);
        nx /= len;
        nz /= len;
        double tx = -nz, tz = nx;
        double rIn = r + GAP, rOut = outer(r);
        int reach = (int) Math.ceil(rOut) + 1;
        int bx0 = (int) Math.floor(cx), by0 = (int) Math.floor(cy), bz0 = (int) Math.floor(cz);
        for (int ix = -reach; ix <= reach; ix++) {
            for (int iy = -reach; iy <= reach; iy++) {
                for (int iz = -reach; iz <= reach; iz++) {
                    int bx = bx0 + ix, by = by0 + iy, bz = bz0 + iz;
                    if (by < yMin) {
                        continue;
                    }
                    double vx = bx + 0.5 - cx, vy = by + 0.5 - cy, vz = bz + 0.5 - cz;
                    double dn = vx * nx + vz * nz, u = vx * tx + vz * tz;
                    double rr = Math.sqrt(u * u + vy * vy);
                    if (Math.abs(dn) > 0.75 || rr < rIn || rr > rOut) {
                        continue;
                    }
                    double ang = Math.toDegrees(Math.atan2(vy, u)) + 360;
                    Material m;
                    if (ang % 40 < 7) {
                        m = Material.SEA_LANTERN;
                    } else if (rr > rOut - 0.6) {
                        m = Material.GOLD_BLOCK;
                    } else {
                        m = ((int) (ang / 20)) % 2 == 0 ? Material.LIGHT_BLUE_CONCRETE : Material.GOLD_BLOCK;
                    }
                    sink.set(bx, by, bz, m);
                }
            }
        }
    }

    /** true : ce bloc fait partie de la palette d'un anneau (pour le retirer sans toucher au reste). */
    public static boolean isRingBlock(Material m) {
        return m == Material.SEA_LANTERN || m == Material.GOLD_BLOCK || m == Material.LIGHT_BLUE_CONCRETE;
    }
}
