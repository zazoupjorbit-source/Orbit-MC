package fr.orbit.extractionmod.carte;

import java.util.List;

/**
 * Constantes de la carte « Cendreville » : ville en ruines de 500 × 500 blocs, générée par code
 * dans un monde plat (sol en y = 63). Le camp (zone sûre) est à l'extérieur, au nord.
 */
public final class Carte {
	private Carte() {
	}

	public static final String NOM = "Cendreville";
	/** Bornes de la carte, incluses. */
	public static final int MIN = -250;
	public static final int MAX = 249;
	/** Dernier bloc de sol (le monde plat a son herbe en y = 63) : on marche en y = 64. */
	public static final int SOL = 63;
	/** Graine fixe : la même carte à chaque génération (et les mêmes emplacements de caisses). */
	public static final long GRAINE = 0x0EB1CE7DL;

	/** Camp ORBIT : zone sûre hors carte. */
	public static final int CAMP_X = 0;
	public static final int CAMP_Z = -312;
	public static final int CAMP_R = 20;
	/** Porte de largage : boîte qu'on traverse pour partir en raid. */
	public static final int PORTE_X0 = -2, PORTE_X1 = 2, PORTE_Z0 = -293, PORTE_Z1 = -292;

	/** Avenues (centres) : grille tous les 50 blocs. Largeur 11 (2 trottoir + 7 chaussée + 2 trottoir). */
	public static final int[] AVENUES = {-200, -150, -100, -50, 0, 50, 100, 150, 200};
	public static final int DEMI_ROUTE = 5;

	public record Point(String nom, int x, int z) {
	}

	/** Points de sortie (balise jaune visible de loin). */
	public static final List<Point> SORTIES = List.of(
			new Point("Checkpoint militaire", 0, -228),
			new Point("Héliport", 226, 226),
			new Point("Pont effondré", -230, 36),
			new Point("Tunnel Est", 230, -75));

	/** Points de largage, en périphérie. */
	public static final List<Point> LARGAGES = List.of(
			new Point("Nord-Ouest", -222, -222),
			new Point("Nord-Est", 222, -214),
			new Point("Ouest", -236, -96),
			new Point("Est", 236, 74),
			new Point("Sud-Ouest", -218, 228),
			new Point("Sud", -40, 236),
			new Point("Sud-Est", 120, 238),
			new Point("Nord", -120, -236));

	public static boolean dansCarte(double x, double z) {
		return x >= MIN && x <= MAX + 1 && z >= MIN && z <= MAX + 1;
	}

	public static boolean dansCamp(double x, double z) {
		return Math.abs(x - CAMP_X) <= CAMP_R + 1 && Math.abs(z - CAMP_Z) <= CAMP_R + 1;
	}

	/** Vrai si (x, z) est sur une avenue (chaussée + trottoirs). */
	public static boolean surRoute(int x, int z) {
		for (int a : AVENUES) {
			if (Math.abs(x - a) <= DEMI_ROUTE || Math.abs(z - a) <= DEMI_ROUTE) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ bruit déterministe

	public static double hash(long seed, int x, int y, int z) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}

	/** Bruit de valeur 3D lissé, entre 0 et 1. */
	public static double bruit(long seed, double x, double y, double z) {
		int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
		double fx = lisse(x - x0), fy = lisse(y - y0), fz = lisse(z - z0);
		double r = 0;
		for (int dx = 0; dx <= 1; dx++) {
			for (int dy = 0; dy <= 1; dy++) {
				for (int dz = 0; dz <= 1; dz++) {
					double w = (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy) * (dz == 0 ? 1 - fz : fz);
					r += w * hash(seed, x0 + dx, y0 + dy, z0 + dz);
				}
			}
		}
		return r;
	}

	private static double lisse(double t) {
		return t * t * (3 - 2 * t);
	}
}
