package fr.orbit.extractionmod.gun;

import java.util.List;

/**
 * Caractéristiques d'une arme, reprises du guns.yml du plugin OrbitExtraction (même équilibrage PvPvE).
 *
 * @param damage      demi-cœurs par balle (par plomb pour le fusil à pompe)
 * @param headshot    multiplicateur à la tête
 * @param intervalTicks ticks entre deux tirs (1200 / cadence en coups par minute)
 * @param spread      dispersion en degrés au repos
 * @param recoil      recul de caméra par tir (degrés)
 * @param noise       rayon (blocs) dans lequel les infectés entendent le tir
 * @param auto        tir automatique (clic maintenu) ou coup par coup
 * @param pellets     plombs par tir (1 sauf fusil à pompe)
 */
public record GunStats(String id, String name, String desc, Ammo ammo, float damage, float headshot, int mag,
                       int intervalTicks, int reloadTicks, double range, double spread, float recoil, int noise,
                       boolean auto, int pellets, Son son, float pitch, Categorie categorie) {

	public enum Son {
		LEGER, LOURD, EXPLOSION, SILENCIEUX
	}

	public enum Categorie {
		PISTOLET("Pistolet"), PM("Pistolet-mitrailleur"), FUSIL("Fusil d'assaut"), PRECISION("Précision"),
		LOURDE("Arme lourde"), POMPE("Fusil à pompe");
		public final String nom;

		Categorie(String nom) {
			this.nom = nom;
		}
	}

	private static GunStats g(String id, String name, String desc, Categorie cat, Ammo ammo, float dmg, float tete, int mag,
	                          int cadence, double rech, double portee, double disp, float recul, int bruit, boolean auto,
	                          int plombs, Son son, float ton) {
		int interval = Math.max(1, Math.round(1200f / cadence));
		return new GunStats(id, name, desc, ammo, dmg, tete, mag, interval, (int) Math.round(rech * 20), portee, disp,
				recul, bruit, auto, plombs, son, ton, cat);
	}

	// Pistolets (9 mm)
	public static final GunStats G18 = g("g18", "Glock 18", "Pistolet automatique. Vide un chargeur en un éclair.", Categorie.PISTOLET,
			Ammo.P9, 4f, 1.8f, 17, 600, 1.5, 40, 2.2, 0.9f, 32, true, 1, Son.LEGER, 1.9f);
	public static final GunStats C96 = g("c96", "C96", "Vieux mais précis.", Categorie.PISTOLET,
			Ammo.P9, 5.5f, 2.0f, 10, 300, 1.6, 50, 1.2, 1.2f, 34, false, 1, Son.LEGER, 1.6f);
	public static final GunStats TEC9 = g("tec9", "Tec-9", "Arrose plus qu'il ne vise.", Categorie.PISTOLET,
			Ammo.P9, 3.5f, 1.6f, 20, 700, 1.8, 32, 3, 0.9f, 32, true, 1, Son.LEGER, 2.0f);
	public static final GunStats ELCOVERT = g("elcovert", "El Covert", "Silencieux : n'attire presque pas les infectés.", Categorie.PISTOLET,
			Ammo.P9, 6f, 2.2f, 8, 280, 1.6, 45, 1, 0.9f, 10, false, 1, Son.SILENCIEUX, 1.8f);
	// Pistolets-mitrailleurs (9 mm)
	public static final GunStats P90 = g("p90", "P90", "Chargeur de 50.", Categorie.PM,
			Ammo.P9, 3.5f, 1.6f, 50, 900, 2.6, 40, 2.6, 0.5f, 40, true, 1, Son.LEGER, 2.0f);
	public static final GunStats PDW = g("pdw", "PDW", "Compact et stable.", Categorie.PM,
			Ammo.P9, 4f, 1.6f, 30, 850, 2.0, 40, 2.4, 0.55f, 38, true, 1, Son.LEGER, 1.9f);
	public static final GunStats VECTOR = g("vector", "Vector", "La plus rapide du lot.", Categorie.PM,
			Ammo.P9, 3.5f, 1.6f, 25, 1100, 1.9, 35, 2.8, 0.5f, 38, true, 1, Son.LEGER, 2.0f);
	// Fusils d'assaut (5,56 / 7,62)
	public static final GunStats AK12 = g("ak12", "AK-12", "Robuste, recul marqué.", Categorie.FUSIL,
			Ammo.R556, 6.5f, 1.7f, 30, 650, 2.4, 75, 1.8, 0.85f, 50, true, 1, Son.LOURD, 1.7f);
	public static final GunStats M4 = g("m4", "M4", "Polyvalent et maniable.", Categorie.FUSIL,
			Ammo.R556, 6f, 1.8f, 30, 750, 2.3, 80, 1.5, 0.7f, 48, true, 1, Son.LOURD, 1.9f);
	public static final GunStats FAMAS = g("famas", "FAMAS", "Le clairon. Cadence infernale.", Categorie.FUSIL,
			Ammo.R556, 5.5f, 1.8f, 25, 950, 2.4, 70, 1.7, 0.75f, 48, true, 1, Son.LOURD, 2.0f);
	public static final GunStats AUG = g("aug", "AUG", "Optique intégrée : tir plus serré.", Categorie.FUSIL,
			Ammo.R556, 6f, 1.9f, 30, 700, 2.3, 85, 1.1, 0.6f, 48, true, 1, Son.LOURD, 1.8f);
	public static final GunStats TACTM4 = g("tactm4", "M4 Tactique", "Silencieux : la discrétion paie en raid.", Categorie.FUSIL,
			Ammo.R556, 6f, 1.8f, 30, 750, 2.3, 80, 1.4, 0.7f, 20, true, 1, Son.SILENCIEUX, 1.4f);
	public static final GunStats SCAR = g("scar", "SCAR", "Gros calibre, gros recul.", Categorie.FUSIL,
			Ammo.R762, 8f, 1.8f, 20, 600, 2.5, 85, 1.6, 1.1f, 54, true, 1, Son.LOURD, 1.5f);
	public static final GunStats FAL = g("fal", "FAL", "Semi-automatique de bataille.", Categorie.FUSIL,
			Ammo.R762, 9f, 2.0f, 20, 400, 2.6, 95, 0.9, 1.5f, 56, false, 1, Son.LOURD, 1.4f);
	// Précision (7,62)
	public static final GunStats M24 = g("m24", "M24", "Fusil à verrou. Un tir, un mort.", Categorie.PRECISION,
			Ammo.R762, 20f, 2.5f, 5, 45, 3.2, 160, 0.35, 3.5f, 90, false, 1, Son.EXPLOSION, 1.4f);
	public static final GunStats VSS = g("vssvintorez", "VSS Vintorez", "Silencieux : tireur d'élite discret.", Categorie.PRECISION,
			Ammo.R762, 11f, 2.2f, 10, 220, 2.8, 110, 0.6, 1.2f, 18, false, 1, Son.SILENCIEUX, 1.0f);
	// Lourdes
	public static final GunStats LMG = g("lmg", "LMG", "100 coups. Lent à recharger.", Categorie.LOURDE,
			Ammo.R556, 6f, 1.5f, 100, 700, 5, 70, 2.6, 0.45f, 60, true, 1, Son.LOURD, 1.5f);
	public static final GunStats POMPE = g("pompe", "Fusil à pompe R-12", "Dévastateur à courte portée.", Categorie.POMPE,
			Ammo.G12, 3f, 1.5f, 6, 70, 3, 24, 6, 2.5f, 56, false, 8, Son.EXPLOSION, 1.6f);

	public static final List<GunStats> TOUTES = List.of(G18, C96, TEC9, ELCOVERT, P90, PDW, VECTOR, AK12, M4, FAMAS, AUG,
			TACTM4, SCAR, FAL, M24, VSS, LMG, POMPE);
}
