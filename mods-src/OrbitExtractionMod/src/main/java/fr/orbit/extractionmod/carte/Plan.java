package fr.orbit.extractionmod.carte;

import fr.orbit.extractionmod.carte.Elements.*;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static fr.orbit.extractionmod.carte.Element.b;
import static fr.orbit.extractionmod.carte.Element.beton;
import static fr.orbit.extractionmod.carte.Element.terre;
import static fr.orbit.extractionmod.carte.Element.vitre;

/**
 * Plan de Cendreville : place tous les éléments (déterministe, même graine = même ville).
 * Centre-ville en tours, ceinture de commerces, quartiers pavillonnaires, zone industrielle au nord-est,
 * lieux uniques (hôpital, commissariat, supermarché, stations-service, parc) et périphérie en friche.
 */
public final class Plan {
	public final List<Element> elements = new ArrayList<>();
	public final List<Caisse> caisses = new ArrayList<>();
	/** Tronçons à générer, du plus proche du camp au plus loin. */
	public final List<Long> troncons;
	private final Map<Long, List<Element>> index = new HashMap<>();

	private static final DyeColor[] VIVES = {DyeColor.RED, DyeColor.BLUE, DyeColor.GREEN, DyeColor.WHITE, DyeColor.BLACK,
			DyeColor.GRAY, DyeColor.LIGHT_GRAY, DyeColor.ORANGE, DyeColor.CYAN, DyeColor.BROWN};
	private static final DyeColor[] CONTENEURS = {DyeColor.RED, DyeColor.BLUE, DyeColor.ORANGE, DyeColor.GREEN, DyeColor.CYAN,
			DyeColor.BROWN, DyeColor.LIGHT_GRAY};

	public Plan() {
		construire();
		for (Element e : elements) {
			caisses.addAll(e.caisses);
			for (int cx = e.x0 >> 4; cx <= e.x1 >> 4; cx++) {
				for (int cz = e.z0 >> 4; cz <= e.z1 >> 4; cz++) {
					if (e.touche(cx << 4, cz << 4, (cx << 4) + 15, (cz << 4) + 15)) {
						index.computeIfAbsent(cle(cx, cz), k -> new ArrayList<>()).add(e);
					}
				}
			}
		}
		for (List<Element> l : index.values()) {
			l.sort(Comparator.comparingInt(e -> e.couche));
		}
		troncons = new ArrayList<>(index.keySet());
		int ccx = Carte.CAMP_X >> 4, ccz = Carte.CAMP_Z >> 4;
		troncons.sort(Comparator.comparingLong(k -> {
			long dx = (int) (k >> 32) - ccx, dz = (int) (long) k - ccz;
			return dx * dx + dz * dz;
		}));
	}

	public static long cle(int cx, int cz) {
		return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
	}

	public List<Element> dans(long cle) {
		return index.getOrDefault(cle, List.of());
	}

	private void add(Element e) {
		elements.add(e);
	}

	// ================================================================== construction

	private void construire() {
		add(new Sol());
		add(new Bordure());
		add(new Camp());
		for (int a : Carte.AVENUES) {
			add(new Route(true, a));
			add(new Route(false, a));
		}
		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 8; j++) {
				ilot(i, j);
			}
		}
		lieuxUniques();
		peripherie();
		rues();
		for (Carte.Point p : Carte.SORTIES) {
			add(new Balise(p.x(), p.z(), Carte.GRAINE ^ p.x() * 31L ^ p.z()));
		}
	}

	/** Un îlot entre quatre avenues : 39 × 39 blocs. */
	private void ilot(int i, int j) {
		int x0 = -194 + 50 * i, z0 = -194 + 50 * j, x1 = x0 + 38, z1 = z0 + 38;
		int cx = x0 + 19, cz = z0 + 19;
		long g = Carte.GRAINE ^ (i * 0x9E37L + j * 0x7F4AL + 0x11L);
		Random r = new Random(g);
		int anneau = Math.max(Math.abs(cx), Math.abs(cz));

		if (i == 4 && j == 4) {
			parc(x0, z0, x1, z1, g);
		} else if (i == 2 && j == 3) {
			hopital(x0, z0, x1, z1, g);
		} else if (i == 5 && j == 2) {
			commissariat(x0, z0, x1, z1, g);
		} else if (i == 1 && j == 5) {
			supermarche(x0, z0, x1, z1, g);
		} else if ((i == 6 && j == 6) || (i == 0 && j == 1)) {
			stationService(x0, z0, x1, z1, g);
		} else if (cx > 100 && cz < 50) {
			industriel(x0, z0, x1, z1, r, g);
		} else if (anneau <= 75) {
			centreVille(x0, z0, x1, z1, r, g, anneau);
		} else if (anneau <= 125) {
			commerces(x0, z0, x1, z1, r, g);
		} else {
			pavillons(x0, z0, x1, z1, r, g);
		}
	}

	private static int porteVers(Random r) {
		return r.nextInt(4);
	}

	private void centreVille(int x0, int z0, int x1, int z1, Random r, long g, int anneau) {
		int min = anneau <= 25 ? 6 : 4, ecart = anneau <= 25 ? 5 : 4;
		if (r.nextDouble() < 0.5) {
			// Deux tours côte à côte.
			tour(x0 + 1, z0 + 2, x0 + 17, z1 - 2, min + r.nextInt(ecart), r, g ^ 1);
			if (r.nextDouble() < 0.15) {
				add(new Gravats(x0 + 29, z0 + 19, 7, g ^ 2));
			} else {
				tour(x0 + 21, z0 + 2, x1 - 1, z1 - 2, min + r.nextInt(ecart), r, g ^ 2);
			}
		} else {
			// Quatre tours plus fines.
			for (int k = 0; k < 4; k++) {
				int ax = k % 2 == 0 ? x0 + 1 : x0 + 21, az = k < 2 ? z0 + 1 : z0 + 21;
				if (r.nextDouble() < 0.12) {
					add(new Gravats(ax + 8, az + 8, 6, g ^ (k + 10)));
					continue;
				}
				tour(ax, az, ax + 16, az + 16, min - 1 + r.nextInt(ecart), r, g ^ (k + 10));
			}
		}
		if (r.nextDouble() < 0.5) {
			add(new FeuDeCamp(x0 + 19, z0 + 19, g ^ 99));
		}
	}

	private void tour(int x0, int z0, int x1, int z1, int etages, Random r, long g) {
		int style = r.nextInt(4);
		BlockState mur, mur2, coin, vitre;
		switch (style) {
			case 0 -> {
				mur = beton(DyeColor.GRAY);
				mur2 = beton(DyeColor.LIGHT_GRAY);
				coin = b(Blocks.POLISHED_ANDESITE);
				vitre = vitre(DyeColor.LIGHT_BLUE);
			}
			case 1 -> {
				mur = beton(DyeColor.WHITE);
				mur2 = b(Blocks.SMOOTH_STONE);
				coin = b(Blocks.STONE_BRICKS);
				vitre = vitre(DyeColor.GRAY);
			}
			case 2 -> {
				mur = b(Blocks.STONE_BRICKS);
				mur2 = b(Blocks.CRACKED_STONE_BRICKS);
				coin = b(Blocks.POLISHED_ANDESITE);
				vitre = vitre(DyeColor.LIGHT_GRAY);
			}
			default -> {
				mur = b(Blocks.BRICKS);
				mur2 = b(Blocks.MUD_BRICKS);
				coin = beton(DyeColor.GRAY);
				vitre = vitre(null);
			}
		}
		double degats = 0.25 + r.nextDouble() * 0.45;
		Batiment t = new Batiment(x0, z0, x1, z1, etages, 4, mur, mur2, coin, b(Blocks.SMOOTH_STONE), vitre,
				b(Blocks.SMOOTH_STONE), degats, porteVers(r), g);
		if (r.nextBoolean()) {
			t.bandeau = beton(DyeColor.GRAY);
		}
		t.caisses(0.45, Caisse.Type.CIVILE).caisses(0.12, Caisse.Type.MEDICALE).caisses(0.08, Caisse.Type.MILITAIRE);
		add(t);
	}

	private void commerces(int x0, int z0, int x1, int z1, Random r, long g) {
		for (int k = 0; k < 4; k++) {
			int ax = k % 2 == 0 ? x0 : x0 + 20, az = k < 2 ? z0 : z0 + 20;
			int bx = ax + 18, bz = az + 18;
			long gk = g ^ (k * 0x51L + 3);
			double q = r.nextDouble();
			if (q < 0.12) {
				add(new Gravats(ax + 9, az + 9, 6, gk));
				add(new Voiture(ax + 3, az + 3, r.nextBoolean(), VIVES[r.nextInt(VIVES.length)], true, gk ^ 5));
				continue;
			}
			if (q < 0.25) {
				// Petit parking.
				for (int c = 0; c < 4; c++) {
					if (r.nextDouble() < 0.7) {
						add(new Voiture(ax + 2 + c * 4, az + 4 + r.nextInt(8), false, VIVES[r.nextInt(VIVES.length)],
								r.nextDouble() < 0.4, gk ^ (c + 9)));
					}
				}
				continue;
			}
			DyeColor teinte = VIVES[r.nextInt(VIVES.length)];
			BlockState mur, mur2;
			switch (r.nextInt(3)) {
				case 0 -> {
					mur = b(Blocks.BRICKS);
					mur2 = b(Blocks.MUD_BRICKS);
				}
				case 1 -> {
					mur = terre(DyeColor.WHITE);
					mur2 = b(Blocks.TERRACOTTA);
				}
				default -> {
					mur = beton(DyeColor.LIGHT_GRAY);
					mur2 = beton(DyeColor.WHITE);
				}
			}
			int porte = porteVers(r);
			Batiment c = new Batiment(ax + 2, az + 2, bx - 2, bz - 2, 2 + r.nextInt(2), 4, mur, mur2, b(Blocks.STONE_BRICKS),
					b(Blocks.SPRUCE_PLANKS), vitre(null), b(Blocks.SMOOTH_STONE), 0.2 + r.nextDouble() * 0.4, porte, gk);
			c.vitrine = true;
			c.bandeau = terre(teinte);
			c.caisses(0.7, Caisse.Type.CIVILE).caisses(0.15, Caisse.Type.MEDICALE);
			add(c);
		}
	}

	private void pavillons(int x0, int z0, int x1, int z1, Random r, long g) {
		Block[] toits = {Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.BRICK_STAIRS, Blocks.DEEPSLATE_TILE_STAIRS};
		Block[] dalles = {Blocks.DARK_OAK_SLAB, Blocks.SPRUCE_SLAB, Blocks.BRICK_SLAB, Blocks.DEEPSLATE_TILE_SLAB};
		for (int k = 0; k < 4; k++) {
			int ax = k % 2 == 0 ? x0 : x0 + 20, az = k < 2 ? z0 : z0 + 20;
			long gk = g ^ (k * 0x77L + 5);
			if (r.nextDouble() < 0.1) {
				add(new Gravats(ax + 9, az + 9, 5, gk));
				add(new FeuDeCamp(ax + 4, az + 14, gk ^ 3));
				continue;
			}
			add(new Cloture(ax + 1, az + 1, ax + 17, az + 17, gk ^ 7));
			boolean large = r.nextBoolean();
			int lx = large ? 10 : 8, lz = large ? 8 : 10;
			int mx0 = ax + 3 + r.nextInt(17 - lx - 4), mz0 = az + 3 + r.nextInt(17 - lz - 4);
			int t = r.nextInt(toits.length);
			BlockState mur, mur2;
			switch (r.nextInt(4)) {
				case 0 -> {
					mur = b(Blocks.SPRUCE_PLANKS);
					mur2 = b(Blocks.DARK_OAK_PLANKS);
				}
				case 1 -> {
					mur = b(Blocks.BIRCH_PLANKS);
					mur2 = b(Blocks.STRIPPED_BIRCH_LOG);
				}
				case 2 -> {
					mur = b(Blocks.BRICKS);
					mur2 = b(Blocks.MUD_BRICKS);
				}
				default -> {
					mur = terre(DyeColor.WHITE);
					mur2 = terre(DyeColor.LIGHT_GRAY);
				}
			}
			add(new Maison(mx0, mz0, mx0 + lx, mz0 + lz, r.nextDouble() < 0.4 ? 2 : 1, mur, mur2, b(Blocks.OAK_PLANKS),
					toits[t], dalles[t], 0.15 + r.nextDouble() * 0.5, porteVers(r), gk));
			if (r.nextDouble() < 0.45) {
				add(new ArbreMort(ax + 15, az + 15, 4 + r.nextInt(4), gk ^ 11));
			}
			if (r.nextDouble() < 0.3) {
				add(new Voiture(ax + 2, az + 13, true, VIVES[r.nextInt(VIVES.length)], r.nextDouble() < 0.4, gk ^ 13));
			}
		}
	}

	private void industriel(int x0, int z0, int x1, int z1, Random r, long g) {
		boolean nord = r.nextBoolean();
		int ez0 = nord ? z0 + 1 : z0 + 15, ez1 = nord ? z0 + 23 : z1 - 1;
		Batiment e = new Batiment(x0 + 2, ez0, x1 - 2, ez1, 1, 8, beton(DyeColor.LIGHT_GRAY), b(Blocks.SMOOTH_STONE),
				beton(DyeColor.GRAY), b(Blocks.SMOOTH_STONE), vitre(DyeColor.GRAY), b(Blocks.SMOOTH_STONE),
				0.3 + r.nextDouble() * 0.4, nord ? 1 : 0, g);
		e.bandeau = beton(DyeColor.YELLOW);
		e.caisses(0.9, Caisse.Type.CIVILE).caisses(0.5, Caisse.Type.MILITAIRE);
		add(e);
		int cz0 = nord ? z0 + 27 : z0 + 1;
		for (int k = 0; k < 5; k++) {
			if (r.nextDouble() < 0.75) {
				int x = x0 + 2 + k * 7;
				if (x + 2 > x1 - 1) {
					break;
				}
				long gk = g ^ (k * 0x33L + 17);
				DyeColor c = CONTENEURS[r.nextInt(CONTENEURS.length)];
				add(new Conteneur(x, cz0, false, c, 0, gk));
				if (r.nextDouble() < 0.35) {
					add(new Conteneur(x, cz0, false, CONTENEURS[r.nextInt(CONTENEURS.length)], 1, gk ^ 1));
				}
			}
		}
	}

	private void parc(int x0, int z0, int x1, int z1, long g) {
		add(new Parc(x0, z0, x1, z1, g));
		Random r = new Random(g ^ 0xFA4CL);
		for (int k = 0; k < 10; k++) {
			int x = x0 + 3 + r.nextInt(33), z = z0 + 3 + r.nextInt(33);
			if (Math.abs(x - (x0 + 19)) > 3 && Math.abs(z - (z0 + 19)) > 3 && Math.hypot(x - x0 - 19, z - z0 - 19) > 9) {
				add(new ArbreMort(x, z, 4 + r.nextInt(5), g ^ (k + 1)));
			}
		}
		add(new FeuDeCamp(x0 + 8, z0 + 30, g ^ 0x77));
		add(new Barricade(x0 + 16, z0 + 1, x0 + 22, z0 + 1, true, g ^ 0x78));
	}

	private void hopital(int x0, int z0, int x1, int z1, long g) {
		Batiment h = new Batiment(x0 + 2, z0 + 2, x1 - 2, z1 - 8, 4, 4, beton(DyeColor.WHITE), b(Blocks.SMOOTH_QUARTZ),
				b(Blocks.QUARTZ_BLOCK), beton(DyeColor.WHITE), vitre(DyeColor.LIGHT_BLUE), b(Blocks.SMOOTH_STONE), 0.35, 1, g);
		h.croix = true;
		h.bandeau = beton(DyeColor.LIGHT_BLUE);
		h.caisses(0.95, Caisse.Type.MEDICALE).caisses(0.8, Caisse.Type.MEDICALE).caisses(0.4, Caisse.Type.CIVILE);
		add(h);
		// Ambulances devant l'entrée.
		add(new Voiture(x0 + 8, z1 - 4, true, DyeColor.WHITE, false, g ^ 1));
		add(new Voiture(x0 + 26, z1 - 4, true, DyeColor.WHITE, true, g ^ 2));
		add(new Barricade(x0 + 14, z1 - 6, x0 + 24, z1 - 6, true, g ^ 3));
	}

	private void commissariat(int x0, int z0, int x1, int z1, long g) {
		Batiment p = new Batiment(x0 + 3, z0 + 3, x1 - 3, z1 - 9, 3, 4, beton(DyeColor.BLUE), beton(DyeColor.LIGHT_BLUE),
				b(Blocks.POLISHED_ANDESITE), b(Blocks.SMOOTH_STONE), vitre(DyeColor.GRAY), b(Blocks.SMOOTH_STONE), 0.3, 1, g);
		p.bandeau = beton(DyeColor.WHITE);
		p.caisses(0.75, Caisse.Type.ARMES).caisses(0.8, Caisse.Type.MILITAIRE).caisses(0.3, Caisse.Type.MEDICALE);
		add(p);
		add(new Voiture(x0 + 6, z1 - 5, true, DyeColor.BLACK, false, g ^ 1));
		add(new Voiture(x0 + 14, z1 - 5, true, DyeColor.WHITE, true, g ^ 2));
		add(new Barricade(x0 + 22, z1 - 4, x1 - 3, z1 - 4, true, g ^ 3));
	}

	private void supermarche(int x0, int z0, int x1, int z1, long g) {
		Batiment s = new Batiment(x0 + 2, z0 + 2, x1 - 2, z0 + 20, 1, 6, beton(DyeColor.WHITE), beton(DyeColor.LIGHT_GRAY),
				beton(DyeColor.RED), b(Blocks.SMOOTH_STONE), vitre(null), b(Blocks.SMOOTH_STONE), 0.35, 1, g);
		s.vitrine = true;
		s.bandeau = beton(DyeColor.RED);
		s.caisses(1, Caisse.Type.CIVILE).caisses(1, Caisse.Type.CIVILE).caisses(0.6, Caisse.Type.MEDICALE);
		add(s);
		Random r = new Random(g ^ 0x5L);
		for (int row = 0; row < 2; row++) {
			for (int c = 0; c < 8; c++) {
				if (r.nextDouble() < 0.55) {
					add(new Voiture(x0 + 3 + c * 4, z0 + 24 + row * 7, false, VIVES[r.nextInt(VIVES.length)],
							r.nextDouble() < 0.35, g ^ (row * 16 + c + 1)));
				}
			}
		}
	}

	private void stationService(int x0, int z0, int x1, int z1, long g) {
		add(new Auvent(x0 + 4, z0 + 4, x1 - 4, z0 + 16, g));
		Batiment b = new Batiment(x0 + 10, z0 + 23, x1 - 10, z1 - 3, 1, 4, beton(DyeColor.WHITE), beton(DyeColor.LIGHT_GRAY),
				beton(DyeColor.RED), b(Blocks.SMOOTH_STONE), vitre(null), b(Blocks.SMOOTH_STONE), 0.4, 0, g ^ 1);
		b.vitrine = true;
		b.bandeau = beton(DyeColor.RED);
		b.caisses(1, Caisse.Type.CIVILE);
		add(b);
		add(new Voiture(x0 + 9, z0 + 8, true, DyeColor.GRAY, true, g ^ 2));
	}

	// ================================================================== lieux uniques et périphérie

	private void lieuxUniques() {
		add(new BaseMilitaire(0, -228, Carte.GRAINE ^ 0xBA5EL));
		add(new Heliport(226, 226, Carte.GRAINE ^ 0x4E11L));
		add(new Pont(36, Carte.GRAINE ^ 0x9047L));
		add(new Tunnel(-75, Carte.GRAINE ^ 0x7077L));
		// Ferme abandonnée (nord-ouest).
		long gf = Carte.GRAINE ^ 0xFE3EL;
		add(new Cloture(-244, -184, -212, -156, gf));
		add(new Maison(-238, -178, -228, -170, 1, b(Blocks.SPRUCE_PLANKS), b(Blocks.STRIPPED_SPRUCE_LOG), b(Blocks.OAK_PLANKS),
				Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, 0.4, 1, gf ^ 1));
		add(new Batiment(-224, -180, -214, -162, 1, 6, b(Blocks.DARK_OAK_PLANKS), b(Blocks.SPRUCE_PLANKS), b(Blocks.DARK_OAK_LOG),
				b(Blocks.HAY_BLOCK), vitre(null), b(Blocks.SPRUCE_PLANKS), 0.5, 2, gf ^ 2).caisses(1, Caisse.Type.CIVILE));
		// Casse auto (est) : carcasses empilées.
		Random r = new Random(Carte.GRAINE ^ 0xCA55EL);
		for (int k = 0; k < 14; k++) {
			add(new Voiture(212 + r.nextInt(28), 112 + r.nextInt(40), r.nextBoolean(), VIVES[r.nextInt(VIVES.length)],
					r.nextDouble() < 0.6, Carte.GRAINE ^ (0xCA00L + k)));
		}
	}

	private boolean reserve(int x, int z) {
		for (Carte.Point p : Carte.SORTIES) {
			if (Math.abs(x - p.x()) < 24 && Math.abs(z - p.z()) < 24) {
				return true;
			}
		}
		for (Carte.Point p : Carte.LARGAGES) {
			if (Math.abs(x - p.x()) < 7 && Math.abs(z - p.z()) < 7) {
				return true;
			}
		}
		if (x < -205 && z > -190 && z < -150) {
			return true; // ferme
		}
		return x > 205 && z > 105 && z < 160; // casse
	}

	private void peripherie() {
		Random r = new Random(Carte.GRAINE ^ 0x9E31L);
		for (int x = -246; x <= 244; x += 9) {
			for (int z = -246; z <= 244; z += 9) {
				int px = x + r.nextInt(5), pz = z + r.nextInt(5);
				boolean dehors = Math.abs(px) > 206 || Math.abs(pz) > 206;
				if (!dehors || Carte.surRoute(px, pz) || reserve(px, pz)) {
					continue;
				}
				boolean foret = px < -150 && pz > 150;
				double q = r.nextDouble();
				long gk = Carte.GRAINE ^ ((long) px * 73856093L) ^ ((long) pz * 19349663L);
				if (q < (foret ? 0.7 : 0.28)) {
					add(new ArbreMort(px, pz, 4 + r.nextInt(5), gk));
				} else if (q < (foret ? 0.75 : 0.36)) {
					add(new Gravats(px, pz, 2 + r.nextInt(3), gk));
				} else if (q < (foret ? 0.77 : 0.41)) {
					add(new Cratere(px, pz, 3 + r.nextInt(3), gk));
				} else if (q < (foret ? 0.78 : 0.44)) {
					add(new Voiture(px, pz, r.nextBoolean(), VIVES[r.nextInt(VIVES.length)], true, gk));
				} else if (q < 0.45) {
					add(new FeuDeCamp(px, pz, gk));
				}
			}
		}
	}

	/** Épaves, bus, cratères et barrages sur les avenues. */
	private void rues() {
		Random r = new Random(Carte.GRAINE ^ 0x5EE7L);
		for (int a : Carte.AVENUES) {
			for (int sens = 0; sens < 2; sens++) {
				boolean selonX = sens == 0;
				for (int along = -244; along <= 240; along += 9) {
					int p = along + r.nextInt(4);
					if (Route.distCroisement(p) < 9) {
						continue;
					}
					double q = r.nextDouble();
					long gk = Carte.GRAINE ^ (a * 0x1F1FL) ^ (p * 0x3C3CL) ^ sens;
					int voie = r.nextBoolean() ? -3 : 1;
					int vx = selonX ? p : a + voie, vz = selonX ? a + voie : p;
					if (reserve(vx, vz)) {
						continue;
					}
					if (q < 0.20) {
						add(new Voiture(vx, vz, selonX, VIVES[r.nextInt(VIVES.length)], r.nextDouble() < 0.4, gk));
					} else if (q < 0.225 && Route.distCroisement(p + 9) >= 6) {
						add(new Bus(vx, vz, selonX, gk));
					} else if (q < 0.24) {
						add(new Cratere(selonX ? p : a, selonX ? a : p, 3, gk));
					} else if (q < 0.255) {
						add(selonX ? new Barricade(p, a - 3, p, a + 3, false, gk) : new Barricade(a - 3, p, a + 3, p, true, gk));
					}
				}
			}
		}
	}
}
