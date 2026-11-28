package fr.orbit.extractionmod.carte;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/** Tous les éléments qui composent Cendreville. */
public final class Elements {
	private Elements() {
	}

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final Direction[] HORIZ = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

	// ================================================================== sol et bordure

	/** Terrain vague : herbe sèche, terre battue, gravier, buissons morts. */
	public static final class Sol extends Element {
		Sol() {
			super(Carte.MIN, Carte.MIN, Carte.MAX, Carte.MAX, 0, Carte.GRAINE ^ 0x501L);
		}

		@Override
		public void dessiner(Ecrivain w) {
			colonnes(w, (x, z) -> {
				if (Carte.surRoute(x, z)) {
					return;
				}
				double n = Carte.bruit(graine, x * 0.045, 0, z * 0.045);
				double r = h(x, 0, z);
				BlockState s;
				if (n < 0.30) {
					s = b(Blocks.COARSE_DIRT);
				} else if (n < 0.37) {
					s = b(Blocks.ROOTED_DIRT);
				} else if (n > 0.80) {
					s = r < 0.5 ? b(Blocks.GRAVEL) : b(Blocks.COARSE_DIRT);
				} else {
					s = b(Blocks.GRASS_BLOCK);
				}
				w.set(x, Carte.SOL, z, s);
				if (s.is(Blocks.GRASS_BLOCK)) {
					if (r < 0.07) {
						w.set(x, Y, z, Blocks.SHORT_GRASS);
					} else if (r < 0.076) {
						w.set(x, Y, z, Blocks.FERN);
					}
				} else if (s.is(Blocks.COARSE_DIRT) && r < 0.025) {
					w.set(x, Y, z, Blocks.DEAD_BUSH);
				}
			});
		}
	}

	/** Mur d'enceinte en ruine au bord de la carte + barrière invisible (on ne sort pas de la carte). */
	public static final class Bordure extends Element {
		Bordure() {
			super(Carte.MIN - 1, Carte.MIN - 1, Carte.MAX + 1, Carte.MAX + 1, 3, Carte.GRAINE ^ 0xB0BL);
		}

		@Override
		public void dessiner(Ecrivain w) {
			colonnes(w, (x, z) -> {
				boolean dehors = x < Carte.MIN || x > Carte.MAX || z < Carte.MIN || z > Carte.MAX;
				boolean bord = x == Carte.MIN || x == Carte.MAX || z == Carte.MIN || z == Carte.MAX;
				if (dehors) {
					for (int y = Y; y <= Y + 44; y++) {
						w.set(x, y, z, Blocks.BARRIER);
					}
				} else if (bord) {
					int haut = (int) Math.round(Carte.bruit(graine, x * 0.15, 0, z * 0.15) * 4.2) - 1;
					for (int y = Y; y < Y + haut; y++) {
						double r = h(x, y, z);
						w.set(x, y, z, r < 0.5 ? beton(DyeColor.GRAY) : r < 0.8 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.COBBLESTONE));
					}
				}
			});
		}

		@Override
		public boolean touche(int ax, int az, int bx, int bz) {
			// Seulement les tronçons qui longent le bord (pas tout l'intérieur de la carte).
			boolean interieur = ax > Carte.MIN && bx < Carte.MAX && az > Carte.MIN && bz < Carte.MAX;
			return super.touche(ax, az, bx, bz) && !interieur;
		}
	}

	// ================================================================== routes

	public static final class Route extends Element {
		private final boolean selonX;
		private final int c;

		/** @param selonX la route file selon l'axe X (z constant = c) */
		Route(boolean selonX, int c) {
			super(selonX ? Carte.MIN : c - Carte.DEMI_ROUTE, selonX ? c - Carte.DEMI_ROUTE : Carte.MIN,
					selonX ? Carte.MAX : c + Carte.DEMI_ROUTE, selonX ? c + Carte.DEMI_ROUTE : Carte.MAX,
					1, Carte.GRAINE ^ (selonX ? 0x1000L : 0x2000L) ^ c);
			this.selonX = selonX;
			this.c = c;
		}

		static boolean chaussee(int x, int z) {
			for (int a : Carte.AVENUES) {
				if (Math.abs(x - a) <= 3 || Math.abs(z - a) <= 3) {
					return true;
				}
			}
			return false;
		}

		static boolean croisement(int x, int z) {
			boolean ax = false, az = false;
			for (int a : Carte.AVENUES) {
				ax |= Math.abs(x - a) <= Carte.DEMI_ROUTE;
				az |= Math.abs(z - a) <= Carte.DEMI_ROUTE;
			}
			return ax && az;
		}

		static int distCroisement(int along) {
			int d = Integer.MAX_VALUE;
			for (int a : Carte.AVENUES) {
				d = Math.min(d, Math.abs(along - a));
			}
			return d;
		}

		@Override
		public void dessiner(Ecrivain w) {
			colonnes(w, (x, z) -> {
				int perp = selonX ? z - c : x - c;
				int along = selonX ? x : z;
				double r = h(x, 0, z);
				if (chaussee(x, z)) {
					BlockState s = Carte.bruit(graine, x * 0.2, 0, z * 0.2) < 0.5 ? beton(DyeColor.BLACK) : beton(DyeColor.GRAY);
					if (r < 0.035) {
						s = b(Blocks.ANDESITE);
					} else if (r < 0.05) {
						s = b(Blocks.COARSE_DIRT);
					} else if (r < 0.058) {
						s = b(Blocks.GRAVEL);
					}
					boolean crois = croisement(x, z);
					int dc = distCroisement(along);
					if (!crois && Math.abs(perp) <= 3) {
						if (perp == 0 && Math.floorMod(along, 6) < 3 && r > 0.15 && dc > 9) {
							s = beton(DyeColor.YELLOW);
						} else if ((dc == 7 || dc == 8) && Math.floorMod(perp, 2) == 0 && r > 0.2) {
							s = beton(DyeColor.WHITE);
						}
					}
					w.set(x, Carte.SOL, z, s);
					if (s.is(Blocks.COARSE_DIRT) && r < 0.045) {
						w.set(x, Y, z, Blocks.SHORT_GRASS);
					}
				} else {
					BlockState s = r < 0.18 ? b(Blocks.CRACKED_STONE_BRICKS) : r < 0.24 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.SMOOTH_STONE);
					w.set(x, Carte.SOL, z, s);
					if (Math.abs(perp) == Carte.DEMI_ROUTE && Math.floorMod(along + (perp > 0 ? 8 : 0), 16) == 0
							&& distCroisement(along) > 6) {
						lampadaire(w, x, z, r);
					}
				}
			});
		}

		private void lampadaire(Ecrivain w, int x, int z, double r) {
			int haut = r < 0.3 ? 2 : 4;
			for (int y = Y; y < Y + haut; y++) {
				w.set(x, y, z, Blocks.ANDESITE_WALL);
			}
			if (haut == 4 && r > 0.5) {
				w.set(x, Y + haut, z, Blocks.LANTERN);
			}
		}
	}

	// ================================================================== bâtiments

	/** Immeuble générique : murs, fenêtres, étages, échelle d'angle jusqu'au toit, dégâts et effondrement. */
	public static final class Batiment extends Element {
		final int etages, hE, porte, demiPorte, hautPorte;
		final BlockState mur, mur2, coin, sol, vitre, toit;
		final double degats;
		BlockState bandeau;
		boolean vitrine, croix;

		/** @param porte côté de l'entrée : 0 nord, 1 sud, 2 ouest, 3 est */
		Batiment(int x0, int z0, int x1, int z1, int etages, int hE, BlockState mur, BlockState mur2, BlockState coin,
		         BlockState sol, BlockState vitre, BlockState toit, double degats, int porte, long graine) {
			super(x0, z0, x1, z1, 2, graine);
			this.etages = etages;
			this.hE = hE;
			this.mur = mur;
			this.mur2 = mur2;
			this.coin = coin;
			this.sol = sol;
			this.vitre = vitre;
			this.toit = toit;
			this.degats = degats;
			this.porte = porte;
			this.demiPorte = hE >= 6 ? 3 : 1;
			this.hautPorte = hE >= 6 ? 5 : 3;
		}

		int hauteur() {
			return etages * hE;
		}

		/** Ajoute des tonneaux de butin (probabilité par étage). */
		Batiment caisses(double p, Caisse.Type type) {
			Random r = new Random(graine ^ 0xCA155EL ^ (caisses.size() * 7919L + type.ordinal()));
			int lx = x1 - x0, lz = z1 - z0;
			if (lx < 4 || lz < 4) {
				return this;
			}
			for (int k = 0; k < etages; k++) {
				if (r.nextDouble() < p) {
					int x = x0 + 2 + r.nextInt(lx - 3);
					int z = z0 + 2 + r.nextInt(lz - 3);
					int y = Y + (k == 0 ? 0 : k * hE + 1);
					caisse(x, y, z, type);
				}
			}
			return this;
		}

		private boolean abime(int x, int y, int z) {
			if (degats <= 0) {
				return false;
			}
			double rel = (y - Y) / (double) hauteur();
			return Carte.bruit(graine, x * 0.13, y * 0.13, z * 0.13) > 0.80 - degats * 0.32 - rel * degats * 0.28;
		}

		private int effondrement(int x, int z) {
			double n = Carte.bruit(graine ^ 7, x * 0.09, 0, z * 0.09) - 0.45;
			return n <= 0 ? 0 : (int) Math.round(n * degats * 2.2 * hauteur());
		}

		private boolean porteIci(int x, int z, int rel) {
			if (rel >= hautPorte) {
				return false;
			}
			int mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
			return switch (porte) {
				case 0 -> z == z0 && Math.abs(x - mx) <= demiPorte;
				case 1 -> z == z1 && Math.abs(x - mx) <= demiPorte;
				case 2 -> x == x0 && Math.abs(z - mz) <= demiPorte && z != z0 + 1;
				default -> x == x1 && Math.abs(z - mz) <= demiPorte;
			};
		}

		private boolean faceAvant(int x, int z) {
			return switch (porte) {
				case 0 -> z == z0;
				case 1 -> z == z1;
				case 2 -> x == x0;
				default -> x == x1;
			};
		}

		private boolean fenetre(int x, int z) {
			boolean selonX = z == z0 || z == z1;
			int a = selonX ? x : z, d = selonX ? x0 : z0, f = selonX ? x1 : z1;
			return a - d >= 2 && f - a >= 2 && Math.floorMod(a - d, 3) != 0;
		}

		private boolean croixIci(int x, int z, int y) {
			if (!croix || !faceAvant(x, z)) {
				return false;
			}
			int along = (porte <= 1 ? x : z) - (porte <= 1 ? (x0 + x1) / 2 : (z0 + z1) / 2);
			int v = y - (Y + hauteur() - 5);
			return (Math.abs(along) <= 1 && Math.abs(v) <= 3) || (Math.abs(along) <= 3 && Math.abs(v) <= 1);
		}

		@Override
		public void dessiner(Ecrivain w) {
			int lx = x0 + 1, lz = z0 + 1;
			BlockState echelle = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
			int H = hauteur();
			colonnes(w, (x, z) -> {
				boolean bx = x == x0 || x == x1, bz = z == z0 || z == z1;
				boolean bord = bx || bz, angle = bx && bz;
				boolean colEchelle = x == lx && z == lz;
				boolean protege = colEchelle || (x == x0 && z == lz);
				int haut = Y + H - (protege ? 0 : effondrement(x, z));
				if (!bord) {
					w.set(x, Carte.SOL, z, sol);
				}
				for (int y = Y; y <= haut; y++) {
					int rel = y - Y, e = rel % hE;
					boolean dessus = rel == H;
					if (!protege && abime(x, y, z)) {
						continue;
					}
					if (bord) {
						if (dessus || angle) {
							w.set(x, y, z, coin);
						} else if (porteIci(x, z, rel)) {
							// ouverture
						} else if (croixIci(x, z, y)) {
							w.set(x, y, z, beton(DyeColor.RED));
						} else if (vitrine && rel >= 1 && rel <= 2 && faceAvant(x, z) && fenetre(x, z)) {
							w.set(x, y, z, h(x, y, z) < degats * 0.6 ? AIR : vitre);
						} else if (e >= hE - 3 && e <= hE - 2 && fenetre(x, z) && !protege) {
							w.set(x, y, z, h(x, y, z) < degats * 0.55 ? AIR : vitre);
						} else if (bandeau != null && e == 0 && rel > 0) {
							w.set(x, y, z, bandeau);
						} else {
							w.set(x, y, z, h(x, y, z) < 0.22 ? mur2 : mur);
						}
					} else if (colEchelle) {
						w.set(x, y, z, echelle);
					} else if (dessus) {
						w.set(x, y, z, toit);
					} else if (rel > 0 && e == 0) {
						w.set(x, y, z, sol);
					} else if (e == hE - 1 && h(x, y, z) < 0.035) {
						w.set(x, y, z, Blocks.COBWEB);
					} else if (rel == 0 && h(x, y, z) < 0.012) {
						w.set(x, y, z, Blocks.COBBLESTONE);
					}
				}
			});
			poserCaisses(w);
		}
	}

	/** Maison individuelle à toit en pente (escaliers), jardin autour. */
	public static final class Maison extends Element {
		final int mx0, mz0, mx1, mz1, etages, porte;
		final BlockState mur, mur2, sol, vitre;
		final Block toit, toitDalle;
		final double degats;

		Maison(int mx0, int mz0, int mx1, int mz1, int etages, BlockState mur, BlockState mur2, BlockState sol,
		       Block toit, Block toitDalle, double degats, int porte, long graine) {
			super(mx0 - 1, mz0 - 1, mx1 + 1, mz1 + 1, 2, graine);
			this.mx0 = mx0;
			this.mz0 = mz0;
			this.mx1 = mx1;
			this.mz1 = mz1;
			this.etages = etages;
			this.mur = mur;
			this.mur2 = mur2;
			this.sol = sol;
			this.vitre = vitre(null);
			this.toit = toit;
			this.toitDalle = toitDalle;
			this.degats = degats;
			this.porte = porte;
			Random r = new Random(graine ^ 0x4A15L);
			if (r.nextDouble() < 0.65) {
				caisse(mx0 + 2 + r.nextInt(Math.max(1, mx1 - mx0 - 3)), Y, mz0 + 2 + r.nextInt(Math.max(1, mz1 - mz0 - 3)), Caisse.Type.CIVILE);
			}
		}

		@Override
		public void dessiner(Ecrivain w) {
			int H = etages * 4;
			int mx = (mx0 + mx1) / 2, mz = (mz0 + mz1) / 2;
			BlockState echelle = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
			colonnes(w, mx0, mz0, mx1, mz1, (x, z) -> {
				boolean bord = x == mx0 || x == mx1 || z == mz0 || z == mz1;
				boolean angle = (x == mx0 || x == mx1) && (z == mz0 || z == mz1);
				boolean colEchelle = etages > 1 && x == mx0 + 1 && z == mz0 + 1;
				if (!bord) {
					w.set(x, Carte.SOL, z, sol);
				}
				for (int y = Y; y < Y + H; y++) {
					int rel = y - Y, e = rel % 4;
					boolean trou = !colEchelle && !(x == mx0 && z == mz0 + 1)
							&& Carte.bruit(graine, x * 0.2, y * 0.2, z * 0.2) > 0.86 - degats * 0.3;
					if (trou) {
						continue;
					}
					if (bord) {
						boolean ouverture = rel < 3 && switch (porte) {
							case 0 -> z == mz0 && Math.abs(x - mx) <= 0;
							case 1 -> z == mz1 && Math.abs(x - mx) <= 0;
							case 2 -> x == mx0 && Math.abs(z - mz) <= 0;
							default -> x == mx1 && Math.abs(z - mz) <= 0;
						};
						boolean selonX = z == mz0 || z == mz1;
						int a = selonX ? x - mx0 : z - mz0, f = selonX ? mx1 - x : mz1 - z;
						boolean fen = !angle && (e == 1 || e == 2) && a >= 2 && f >= 2 && Math.floorMod(a, 3) == 2;
						if (ouverture) {
							continue;
						}
						if (angle) {
							w.set(x, y, z, b(Blocks.STRIPPED_SPRUCE_LOG));
						} else if (fen) {
							w.set(x, y, z, h(x, y, z) < degats * 0.6 ? AIR : vitre);
						} else {
							w.set(x, y, z, h(x, y, z) < 0.25 ? mur2 : mur);
						}
					} else if (colEchelle) {
						w.set(x, y, z, echelle);
					} else if (rel > 0 && e == 0) {
						w.set(x, y, z, sol);
					} else if (e == 3 && h(x, y, z) < 0.04) {
						w.set(x, y, z, Blocks.COBWEB);
					}
				}
			});
			// Toit : pente de part et d'autre d'un faîte selon X.
			for (int k = 0; ; k++) {
				int za = mz0 - 1 + k, zb = mz1 + 1 - k, y = Y + H + k;
				if (za > zb) {
					break;
				}
				final int fk = k;
				colonnes(w, mx0 - 1, mz0 - 1, mx1 + 1, mz1 + 1, (x, z) -> {
					if (z != za && z != zb && !((x == mx0 || x == mx1) && z > za && z < zb)) {
						return;
					}
					if (h(x, y, z) < degats * 0.22) {
						return;
					}
					if ((x == mx0 || x == mx1) && z > za && z < zb) {
						w.set(x, y, z, mur);
					} else if (za == zb) {
						w.set(x, y, z, dalle(toitDalle, false));
					} else if (z == za) {
						w.set(x, y, z, marche(toit, Direction.SOUTH));
					} else {
						w.set(x, y, z, marche(toit, Direction.NORTH));
					}
				});
				if (fk > 12) {
					break;
				}
			}
			poserCaisses(w);
		}
	}

	/** Clôture de jardin, avec des trous. */
	public static final class Cloture extends Element {
		Cloture(int x0, int z0, int x1, int z1, long graine) {
			super(x0, z0, x1, z1, 3, graine);
		}

		@Override
		public void dessiner(Ecrivain w) {
			int mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
			colonnes(w, (x, z) -> {
				boolean bord = x == x0 || x == x1 || z == z0 || z == z1;
				if (!bord || (Math.abs(x - mx) <= 1 && (z == z0 || z == z1)) || (Math.abs(z - mz) <= 1 && (x == x0 || x == x1))) {
					return;
				}
				if (h(x, 0, z) > 0.35 && w.vide(x, Y, z)) {
					w.set(x, Y, z, Blocks.SPRUCE_FENCE);
				}
			});
		}
	}

	// ================================================================== décor

	public static final class Voiture extends Element {
		final boolean selonX, brulee;
		final DyeColor couleur;

		Voiture(int x, int z, boolean selonX, DyeColor couleur, boolean brulee, long graine) {
			super(x, z, selonX ? x + 3 : x + 1, selonX ? z + 1 : z + 3, 3, graine);
			this.selonX = selonX;
			this.couleur = couleur;
			this.brulee = brulee;
		}

		@Override
		public void dessiner(Ecrivain w) {
			colonnes(w, (x, z) -> {
				int along = selonX ? x - x0 : z - z0;
				w.set(x, Y, z, brulee ? (h(x, 0, z) < 0.5 ? b(Blocks.BLACKSTONE) : b(Blocks.COAL_BLOCK)) : beton(couleur));
				if (along == 1 || along == 2) {
					if (brulee) {
						if (h(x, 1, z) < 0.5) {
							w.set(x, Y + 1, z, Blocks.POLISHED_BLACKSTONE_SLAB);
						}
					} else {
						w.set(x, Y + 1, z, h(x, 1, z) < 0.3 ? AIR : b(Blocks.TINTED_GLASS));
					}
				} else if (!brulee) {
					w.set(x, Y + 1, z, Blocks.CONCRETE_SLAB.pick(couleur).defaultBlockState());
				}
			});
		}
	}

	public static final class Bus extends Element {
		final boolean selonX;

		Bus(int x, int z, boolean selonX, long graine) {
			super(x, z, selonX ? x + 8 : x + 2, selonX ? z + 2 : z + 8, 3, graine);
			this.selonX = selonX;
		}

		@Override
		public void dessiner(Ecrivain w) {
			boolean brule = h(0, 0, 0) < 0.5;
			colonnes(w, (x, z) -> {
				boolean cote = selonX ? (z == z0 || z == z1) : (x == x0 || x == x1);
				boolean bout = selonX ? (x == x0 || x == x1) : (z == z0 || z == z1);
				BlockState carrosserie = brule ? b(Blocks.BLACKSTONE) : beton(DyeColor.YELLOW);
				w.set(x, Y, z, carrosserie);
				if (cote || bout) {
					w.set(x, Y + 1, z, (h(x, 1, z) < 0.35) ? AIR : (brule ? b(Blocks.IRON_BARS) : b(Blocks.TINTED_GLASS)));
				}
				if (h(x, 2, z) > 0.2) {
					w.set(x, Y + 2, z, brule ? b(Blocks.POLISHED_BLACKSTONE_SLAB) : beton(DyeColor.WHITE));
				}
			});
		}
	}

	public static final class Gravats extends Element {
		final int cx, cz, r;

		Gravats(int cx, int cz, int r, long graine) {
			super(cx - r, cz - r, cx + r, cz + r, 3, graine);
			this.cx = cx;
			this.cz = cz;
			this.r = r;
		}

		@Override
		public void dessiner(Ecrivain w) {
			colonnes(w, (x, z) -> {
				double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
				double haut = (r - d) * 0.7 + h(x, 0, z) * 1.2 - 0.4;
				for (int i = 0; i < haut && i < 5; i++) {
					double q = h(x, i, z);
					BlockState s = q < 0.35 ? b(Blocks.COBBLESTONE) : q < 0.55 ? b(Blocks.GRAVEL) : q < 0.75 ? b(Blocks.ANDESITE) : b(Blocks.CRACKED_STONE_BRICKS);
					if (i + 1 >= haut && q > 0.6) {
						s = dalle(Blocks.COBBLESTONE_SLAB, false);
					}
					if (w.vide(x, Y + i, z)) {
						w.set(x, Y + i, z, s);
					}
				}
			});
		}
	}

	public static final class Cratere extends Element {
		final int cx, cz, r;

		Cratere(int cx, int cz, int r, long graine) {
			super(cx - r - 2, cz - r - 2, cx + r + 2, cz + r + 2, 3, graine);
			this.cx = cx;
			this.cz = cz;
			this.r = r;
		}

		@Override
		public void dessiner(Ecrivain w) {
			colonnes(w, (x, z) -> {
				double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
				if (d <= r) {
					int prof = (int) Math.round((1 - d / r) * r * 0.55) + 1;
					for (int y = Carte.SOL; y > Carte.SOL - prof; y--) {
						w.air(x, y, z);
					}
					w.air(x, Y, z);
					double q = h(x, 0, z);
					w.set(x, Carte.SOL - prof, z, q < 0.4 ? b(Blocks.BLACKSTONE) : q < 0.75 ? b(Blocks.COARSE_DIRT) : b(Blocks.BASALT));
				} else if (d <= r + 2 && h(x, 1, z) < 0.6) {
					w.set(x, Carte.SOL, z, Blocks.CONCRETE_POWDER.pick(DyeColor.GRAY).defaultBlockState());
				}
			});
		}
	}

	public static final class ArbreMort extends Element {
		final int cx, cz, haut;

		ArbreMort(int cx, int cz, int haut, long graine) {
			super(cx - 3, cz - 3, cx + 3, cz + 3, 3, graine);
			this.cx = cx;
			this.cz = cz;
			this.haut = haut;
		}

		@Override
		public void dessiner(Ecrivain w) {
			Random r = rng();
			Block bois = r.nextBoolean() ? Blocks.DARK_OAK_LOG : Blocks.SPRUCE_LOG;
			for (int y = Y; y < Y + haut; y++) {
				w.set(cx, y, cz, bois);
			}
			int branches = 2 + r.nextInt(3);
			for (int i = 0; i < branches; i++) {
				int y = Y + haut / 2 + r.nextInt(Math.max(1, haut / 2));
				Direction d = HORIZ[r.nextInt(4)];
				int len = 1 + r.nextInt(3);
				for (int k = 1; k <= len; k++) {
					w.set(cx + d.getStepX() * k, y + (k == len ? 1 : 0), cz + d.getStepZ() * k, axe(bois, d.getAxis()));
				}
			}
		}
	}

	public static final class FeuDeCamp extends Element {
		FeuDeCamp(int x, int z, long graine) {
			super(x, z, x, z, 3, graine);
		}

		@Override
		public void dessiner(Ecrivain w) {
			w.set(x0, Carte.SOL, z0, Blocks.HAY_BLOCK);
			w.set(x0, Y, z0, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true));
		}
	}

	/** Barrage de fortune en travers d'une rue : murets de béton, sacs de sable, barbelés (toiles). */
	public static final class Barricade extends Element {
		final boolean selonX;

		Barricade(int x0, int z0, int x1, int z1, boolean selonX, long graine) {
			super(x0, z0, x1, z1, 3, graine);
			this.selonX = selonX;
		}

		@Override
		public void dessiner(Ecrivain w) {
			colonnes(w, (x, z) -> {
				double q = h(x, 0, z);
				if (q < 0.15) {
					return;
				}
				if (q < 0.55) {
					w.set(x, Y, z, Blocks.STONE_BRICK_WALL);
				} else if (q < 0.85) {
					w.set(x, Y, z, Blocks.PACKED_MUD);
					if (q > 0.7) {
						w.set(x, Y + 1, z, dalle(Blocks.MUD_BRICK_SLAB, false));
					}
				} else {
					w.set(x, Y, z, Blocks.COBWEB);
				}
			});
		}
	}

	/** Conteneur de fret : boîte colorée, ouverte d'un bout, avec parfois un tonneau dedans. */
	public static final class Conteneur extends Element {
		final boolean selonX;
		final DyeColor couleur;
		final int etage;

		Conteneur(int x, int z, boolean selonX, DyeColor couleur, int etage, long graine) {
			super(x, z, selonX ? x + 5 : x + 2, selonX ? z + 2 : z + 5, 3, graine);
			this.selonX = selonX;
			this.couleur = couleur;
			this.etage = etage;
			if (etage == 0 && new Random(graine).nextDouble() < 0.5) {
				caisse(selonX ? x + 3 : x + 1, Y, selonX ? z + 1 : z + 3, Caisse.Type.CIVILE);
			}
		}

		@Override
		public void dessiner(Ecrivain w) {
			int yb = Y + etage * 3;
			colonnes(w, (x, z) -> {
				int along = selonX ? x - x0 : z - z0;
				boolean cote = selonX ? (z == z0 || z == z1) : (x == x0 || x == x1);
				for (int y = yb; y < yb + 3; y++) {
					boolean interieur = !cote && y > yb - 1 && y < yb + 2 && y >= yb && along > 0 && y != yb + 2 && along <= 5;
					if (interieur && along >= 1) {
						if (y == yb) {
							continue;
						}
						continue;
					}
					w.set(x, y, z, (along == 0 && !cote && y < yb + 2) ? b(Blocks.IRON_BARS) : beton(couleur));
				}
			});
			poserCaisses(w);
		}
	}

	/** Parc central : allées, fontaine à sec, arbres morts, bancs. */
	public static final class Parc extends Element {
		Parc(int x0, int z0, int x1, int z1, long graine) {
			super(x0, z0, x1, z1, 2, graine);
		}

		@Override
		public void dessiner(Ecrivain w) {
			int mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
			colonnes(w, (x, z) -> {
				double q = h(x, 0, z);
				boolean allee = Math.abs(x - mx) <= 1 || Math.abs(z - mz) <= 1;
				double d = Math.sqrt((x - mx) * (x - mx) + (z - mz) * (z - mz));
				if (d <= 6.5) {
					w.set(x, Carte.SOL, z, Blocks.STONE_BRICKS);
					if (d > 5.5) {
						w.set(x, Y, z, Blocks.STONE_BRICKS);
					} else if (d < 1.5) {
						for (int y = Y; y < Y + 4; y++) {
							w.set(x, y, z, Blocks.POLISHED_ANDESITE);
						}
					}
				} else if (allee) {
					w.set(x, Carte.SOL, z, q < 0.8 ? b(Blocks.GRAVEL) : b(Blocks.COARSE_DIRT));
				} else {
					w.set(x, Carte.SOL, z, q < 0.25 ? b(Blocks.PODZOL) : b(Blocks.GRASS_BLOCK));
					if (q > 0.92) {
						w.set(x, Y, z, Blocks.SHORT_GRASS);
					}
				}
				if ((Math.abs(x - mx) == 3 && Math.floorMod(z - mz, 7) == 4) || (Math.abs(z - mz) == 3 && Math.floorMod(x - mx, 7) == 4)) {
					if (d > 8) {
						Direction f = Math.abs(x - mx) == 3 ? (x > mx ? Direction.EAST : Direction.WEST) : (z > mz ? Direction.SOUTH : Direction.NORTH);
						w.set(x, Y, z, marche(Blocks.SPRUCE_STAIRS, f));
					}
				}
			});
		}
	}

	/** Auvent de station-service : piliers, toit en dalles, pompes (blocs de fer + chaudrons). */
	public static final class Auvent extends Element {
		Auvent(int x0, int z0, int x1, int z1, long graine) {
			super(x0, z0, x1, z1, 2, graine);
		}

		@Override
		public void dessiner(Ecrivain w) {
			colonnes(w, (x, z) -> {
				boolean pilier = (x == x0 + 1 || x == x1 - 1) && (z == z0 + 1 || z == z1 - 1);
				boolean pompe = (z == (z0 + z1) / 2) && Math.floorMod(x - x0, 5) == 2 && x > x0 + 1 && x < x1 - 1;
				w.set(x, Carte.SOL, z, h(x, 0, z) < 0.1 ? b(Blocks.CRACKED_STONE_BRICKS) : beton(DyeColor.LIGHT_GRAY));
				if (pilier) {
					for (int y = Y; y < Y + 5; y++) {
						w.set(x, y, z, beton(DyeColor.WHITE));
					}
				}
				if (pompe) {
					w.set(x, Y, z, Blocks.IRON_BLOCK);
					w.set(x, Y + 1, z, h(x, 1, z) < 0.3 ? b(Blocks.CAULDRON) : beton(DyeColor.RED));
				}
				if (h(x, 5, z) > 0.18 + (Carte.bruit(graine, x * 0.25, 0, z * 0.25) > 0.6 ? 0.5 : 0)) {
					boolean bord = x == x0 || x == x1 || z == z0 || z == z1;
					w.set(x, Y + 5, z, bord ? beton(DyeColor.RED) : dalle(Blocks.SMOOTH_STONE_SLAB, true));
				}
			});
		}
	}

	// ================================================================== lieux uniques

	/** Base militaire (point de sortie nord) : sacs de sable, barbelés, tours de guet, tentes. */
	public static final class BaseMilitaire extends Element {
		BaseMilitaire(int cx, int cz, long graine) {
			super(cx - 18, cz - 12, cx + 18, cz + 12, 2, graine);
			caisse(cx - 10, Y, cz - 6, Caisse.Type.ARMES);
			caisse(cx + 10, Y, cz - 6, Caisse.Type.MILITAIRE);
			caisse(cx - 12, Y, cz + 5, Caisse.Type.MILITAIRE);
			caisse(cx + 12, Y, cz + 5, Caisse.Type.MEDICALE);
			caisse(cx + 1, Y, cz - 9, Caisse.Type.ARMES);
		}

		@Override
		public void dessiner(Ecrivain w) {
			int mx = (x0 + x1) / 2;
			colonnes(w, (x, z) -> {
				double q = h(x, 0, z);
				w.set(x, Carte.SOL, z, q < 0.5 ? b(Blocks.GRAVEL) : b(Blocks.COARSE_DIRT));
				boolean bord = x == x0 + 1 || x == x1 - 1 || z == z0 + 1 || z == z1 - 1;
				boolean dehors = x == x0 || x == x1 || z == z0 || z == z1;
				boolean entree = z == z1 - 1 && Math.abs(x - mx) <= 2 || z == z1 && Math.abs(x - mx) <= 2;
				if (entree) {
					return;
				}
				if (bord) {
					w.set(x, Y, z, Blocks.PACKED_MUD);
					if (q > 0.25) {
						w.set(x, Y + 1, z, Blocks.MUD_BRICKS);
					}
				} else if (dehors && q > 0.3) {
					w.set(x, Y, z, Blocks.COBWEB);
				}
			});
			tour(w, x0 + 3, z0 + 3);
			tour(w, x1 - 5, z0 + 3);
			tente(w, x0 + 6, z1 - 8);
			tente(w, x1 - 11, z1 - 8);
			// Barrières de béton devant l'entrée.
			for (int dx = -4; dx <= 4; dx += 8) {
				w.set(mx + dx, Y, z1 + 1, Blocks.STONE_BRICK_WALL);
			}
			poserCaisses(w);
		}

		private void tour(Ecrivain w, int x, int z) {
			for (int y = Y; y < Y + 6; y++) {
				w.set(x, y, z, Blocks.SPRUCE_LOG);
				w.set(x + 2, y, z, Blocks.SPRUCE_LOG);
				w.set(x, y, z + 2, Blocks.SPRUCE_LOG);
				w.set(x + 2, y, z + 2, Blocks.SPRUCE_LOG);
			}
			BlockState echelle = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
			for (int y = Y; y < Y + 6; y++) {
				w.set(x + 1, y, z + 2, Blocks.SPRUCE_PLANKS);
				w.set(x + 1, y, z + 3, echelle);
			}
			for (int dx = -1; dx <= 3; dx++) {
				for (int dz = -1; dz <= 3; dz++) {
					w.set(x + dx, Y + 6, z + dz, Blocks.SPRUCE_PLANKS);
					if (dx == -1 || dx == 3 || dz == -1 || dz == 3) {
						w.set(x + dx, Y + 7, z + dz, Blocks.SPRUCE_FENCE);
					}
				}
			}
			w.air(x + 1, Y + 6, z + 3);
			w.set(x + 1, Y + 6, z + 2, Blocks.SPRUCE_PLANKS);
		}

		private void tente(Ecrivain w, int x, int z) {
			for (int dx = 0; dx < 5; dx++) {
				for (int dz = 0; dz < 4; dz++) {
					boolean bord = dz == 0 || dz == 3;
					if (bord) {
						w.set(x + dx, Y, z + dz, laine(DyeColor.GREEN));
					}
					w.set(x + dx, Y + 1, z + dz, (dz == 1 || dz == 2) ? laine(DyeColor.GREEN) : dalle(Blocks.WOOL_SLAB.pick(DyeColor.GREEN), true));
				}
			}
			for (int dz = 1; dz <= 2; dz++) {
				w.air(x + 2, Y + 1, z + dz);
			}
		}
	}

	/** Héliport (point de sortie sud-est) : piste marquée H, feux, cabane. */
	public static final class Heliport extends Element {
		Heliport(int cx, int cz, long graine) {
			super(cx - 8, cz - 8, cx + 8, cz + 8, 2, graine);
			caisse(cx - 6, Y, cz + 7, Caisse.Type.MILITAIRE);
		}

		@Override
		public void dessiner(Ecrivain w) {
			int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
			colonnes(w, (x, z) -> {
				int dx = x - cx, dz = z - cz;
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				BlockState s;
				if (m == 8) {
					s = (Math.abs(dx) == 8 && Math.abs(dz) == 8) ? b(Blocks.GLOWSTONE) : beton(DyeColor.YELLOW);
				} else if (m <= 6 && ((Math.abs(dx) == 3 && Math.abs(dz) <= 4) || (dz == 0 && Math.abs(dx) <= 3)) && !(dx == 0 && dz == 0)) {
					s = beton(DyeColor.WHITE);
				} else {
					s = h(x, 0, z) < 0.12 ? b(Blocks.CRACKED_STONE_BRICKS) : beton(DyeColor.GRAY);
				}
				w.set(x, Carte.SOL, z, s);
			});
			poserCaisses(w);
		}
	}

	/** Pont effondré (sortie ouest) : tablier en morceaux sur piles, gravats dessous. */
	public static final class Pont extends Element {
		final int cz;

		Pont(int cz, long graine) {
			super(Carte.MIN, cz - 3, Carte.MIN + 30, cz + 3, 2, graine);
			this.cz = cz;
		}

		@Override
		public void dessiner(Ecrivain w) {
			colonnes(w, (x, z) -> {
				boolean trou = x >= Carte.MIN + 12 && x <= Carte.MIN + 18;
				boolean pile = Math.floorMod(x - Carte.MIN, 8) == 3 && (z == z0 || z == z1);
				if (pile) {
					for (int y = Y; y < Y + 5; y++) {
						w.set(x, y, z, Blocks.STONE_BRICKS);
					}
				}
				if (!trou && h(x, 5, z) > 0.08) {
					w.set(x, Y + 5, z, h(x, 6, z) < 0.2 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.SMOOTH_STONE));
					if (z == z0 || z == z1) {
						w.set(x, Y + 6, z, Blocks.STONE_BRICK_WALL);
					}
				}
				if (trou && h(x, 0, z) < 0.55) {
					w.set(x, Y, z, h(x, 1, z) < 0.5 ? b(Blocks.COBBLESTONE) : b(Blocks.CRACKED_STONE_BRICKS));
				}
			});
		}
	}

	/** Entrée de tunnel (sortie est) : colline, arche de pierre, galerie bouchée par un éboulement. */
	public static final class Tunnel extends Element {
		final int cz;

		Tunnel(int cz, long graine) {
			super(Carte.MAX - 14, cz - 12, Carte.MAX, cz + 12, 2, graine);
			this.cz = cz;
		}

		@Override
		public void dessiner(Ecrivain w) {
			colonnes(w, (x, z) -> {
				int dz = Math.abs(z - cz);
				int fond = x - x0;
				double haut = Math.min(11, fond * 1.1) - Math.max(0, dz - 7) * 1.3 + h(x, 0, z) * 1.5;
				boolean galerie = dz <= 2 && fond >= 1 && fond <= 10;
				for (int y = Y; y < Y + haut; y++) {
					int rel = y - Y;
					if (galerie && rel < 4 && !(dz == 2 && rel == 3)) {
						if (fond >= 9) {
							w.set(x, y, z, Blocks.COBBLESTONE);
						}
						continue;
					}
					boolean arche = fond <= 2 && dz <= 3 && rel <= 4;
					w.set(x, y, z, arche ? b(Blocks.STONE_BRICKS) : (y + 1 >= Y + haut ? b(Blocks.COARSE_DIRT) : b(Blocks.STONE)));
				}
				if (galerie) {
					w.set(x, Carte.SOL, z, beton(DyeColor.BLACK));
				}
			});
		}
	}

	/** Balise de sortie : pyramide de fer + balise + verre jaune (faisceau doré visible de loin), cercle jaune au sol. */
	public static final class Balise extends Element {
		final int cx, cz;

		Balise(int cx, int cz, long graine) {
			super(cx - 4, cz - 4, cx + 4, cz + 4, 4, graine);
			this.cx = cx;
			this.cz = cz;
		}

		@Override
		public void dessiner(Ecrivain w) {
			colonnes(w, (x, z) -> {
				int dx = x - cx, dz = z - cz;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
					w.set(x, Carte.SOL - 1, z, Blocks.IRON_BLOCK);
					w.set(x, Carte.SOL, z, Blocks.IRON_BLOCK);
				} else if (d >= 3.5 && d <= 4.4) {
					w.set(x, Carte.SOL, z, beton(DyeColor.YELLOW));
				}
				// Dégagement : rien autour de la balise sur 3 blocs de haut, et le ciel libre au-dessus du faisceau.
				for (int y = Y; y < Y + 3; y++) {
					w.air(x, y, z);
				}
				if (dx == 0 && dz == 0) {
					for (int y = Y + 3; y < Y + 60; y++) {
						w.air(x, y, z);
					}
				}
			});
			// Pyramide de fer : 3 × 3 sous la balise (une couche suffit pour l'allumer).
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					w.set(cx + dx, Carte.SOL, cz + dz, Blocks.IRON_BLOCK);
				}
			}
			w.set(cx, Y, cz, Blocks.BEACON);
			w.set(cx, Y + 1, cz, Blocks.STAINED_GLASS.pick(DyeColor.YELLOW));
		}
	}

	/** Camp ORBIT (zone sûre, hors carte) : dalle bleu et or, tentes, lumières, porte de largage. */
	public static final class Camp extends Element {
		Camp() {
			super(Carte.CAMP_X - Carte.CAMP_R - 2, Carte.CAMP_Z - Carte.CAMP_R - 2, Carte.CAMP_X + Carte.CAMP_R + 2,
					Carte.CAMP_Z + Carte.CAMP_R + 3, 2, Carte.GRAINE ^ 0xCA3BL);
		}

		@Override
		public void dessiner(Ecrivain w) {
			int cx = Carte.CAMP_X, cz = Carte.CAMP_Z, R = Carte.CAMP_R;
			colonnes(w, (x, z) -> {
				int dx = x - cx, dz = z - cz;
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				boolean porte = Math.abs(dx) <= 2 && dz > 0;
				if (m <= R) {
					BlockState s = (Math.floorMod(dx, 8) == 0 || Math.floorMod(dz, 8) == 0) ? beton(DyeColor.YELLOW) : beton(DyeColor.BLUE);
					if (Math.floorMod(dx, 8) == 0 && Math.floorMod(dz, 8) == 0) {
						s = b(Blocks.SEA_LANTERN);
					}
					if (m >= R - 1) {
						s = beton(DyeColor.LIGHT_BLUE);
					}
					w.set(x, Carte.SOL, z, s);
				}
				if (m == R + 1 && !(porte && dz == R + 1)) {
					w.set(x, Y, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
					for (int y = Y + 1; y <= Y + 8; y++) {
						w.set(x, y, z, Blocks.BARRIER);
					}
				}
				if (porte && dz == R + 2) {
					for (int y = Y; y <= Y + 8; y++) {
						w.set(x, y, z, Blocks.BARRIER);
					}
				}
			});
			// Porte de largage (z = PORTE_Z0..Z1) : cadre doré.
			int pz = Carte.PORTE_Z1;
			for (int y = Y; y <= Y + 4; y++) {
				w.set(Carte.PORTE_X0 - 1, y, pz, Blocks.GOLD_BLOCK);
				w.set(Carte.PORTE_X1 + 1, y, pz, Blocks.GOLD_BLOCK);
			}
			for (int x = Carte.PORTE_X0 - 1; x <= Carte.PORTE_X1 + 1; x++) {
				w.set(x, Y + 5, pz, Blocks.GOLD_BLOCK);
				if (x >= Carte.PORTE_X0 && x <= Carte.PORTE_X1) {
					w.set(x, Y + 4, pz, Blocks.STAINED_GLASS_PANE.pick(DyeColor.YELLOW));
					for (int z = Carte.PORTE_Z0; z <= Carte.PORTE_Z1; z++) {
						w.set(x, Carte.SOL, z, Blocks.GOLD_BLOCK);
					}
				}
			}
			tente(w, cx - 14, cz - 14);
			tente(w, cx + 9, cz - 14);
			tente(w, cx - 14, cz + 6);
			tente(w, cx + 9, cz + 6);
			// Mât aux couleurs d'Orbit.
			for (int y = Y; y < Y + 9; y++) {
				w.set(cx, y, cz, Blocks.IRON_BARS);
			}
			for (int i = 1; i <= 4; i++) {
				w.set(cx + i, Y + 8, cz, laine(DyeColor.BLUE));
				w.set(cx + i, Y + 7, cz, laine(DyeColor.YELLOW));
			}
		}

		private void tente(Ecrivain w, int x, int z) {
			for (int dx = 0; dx < 6; dx++) {
				for (int dz = 0; dz < 5; dz++) {
					boolean bord = dz == 0 || dz == 4;
					if (bord && dx != 2 && dx != 3) {
						w.set(x + dx, Y, z + dz, laine(DyeColor.BLUE));
					}
					if (dz >= 1 && dz <= 3) {
						w.set(x + dx, Y + 1, z + dz, dz == 2 ? laine(DyeColor.YELLOW) : laine(DyeColor.BLUE));
					}
				}
			}
			w.set(x + 1, Y, z + 2, Blocks.BARREL);
			w.set(x + 4, Y, z + 2, Blocks.CRAFTING_TABLE);
		}
	}
}
