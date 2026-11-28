package fr.orbit.extractionmod.carte;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Un élément de la carte (route, bâtiment, voiture…). Il connaît sa boîte englobante et sait se dessiner
 * à travers un {@link Ecrivain} (qui ne garde que le tronçon en cours). Tout le hasard part de {@link #graine} :
 * le même élément se dessine à l'identique quel que soit le tronçon.
 */
public abstract class Element {
	/** Boîte au sol (incluse). */
	public final int x0, z0, x1, z1;
	/** Ordre de dessin : sol (0), routes (1), bâtiments (2), décor (3), balises (4). */
	public final int couche;
	protected final long graine;
	public final List<Caisse> caisses = new ArrayList<>();

	protected static final int Y = Carte.SOL + 1;

	protected Element(int x0, int z0, int x1, int z1, int couche, long graine) {
		this.x0 = Math.min(x0, x1);
		this.z0 = Math.min(z0, z1);
		this.x1 = Math.max(x0, x1);
		this.z1 = Math.max(z0, z1);
		this.couche = couche;
		this.graine = graine;
	}

	public abstract void dessiner(Ecrivain w);

	public boolean touche(int ax, int az, int bx, int bz) {
		return x0 <= bx && x1 >= ax && z0 <= bz && z1 >= az;
	}

	protected Random rng() {
		return new Random(graine);
	}

	protected double h(int x, int y, int z) {
		return Carte.hash(graine, x, y, z);
	}

	/** Boucle sur les colonnes de la boîte qui tombent dans le tronçon. */
	protected interface Colonne {
		void a(int x, int z);
	}

	protected void colonnes(Ecrivain w, int ax, int az, int bx, int bz, Colonne c) {
		for (int x = Math.max(Math.min(ax, bx), w.x0); x <= Math.min(Math.max(ax, bx), w.x1); x++) {
			for (int z = Math.max(Math.min(az, bz), w.z0); z <= Math.min(Math.max(az, bz), w.z1); z++) {
				c.a(x, z);
			}
		}
	}

	protected void colonnes(Ecrivain w, Colonne c) {
		colonnes(w, x0, z0, x1, z1, c);
	}

	// ------------------------------------------------------------------ palette

	public static BlockState b(Block b) {
		return b.defaultBlockState();
	}

	public static BlockState beton(DyeColor c) {
		return Blocks.CONCRETE.pick(c).defaultBlockState();
	}

	public static BlockState terre(DyeColor c) {
		return Blocks.DYED_TERRACOTTA.pick(c).defaultBlockState();
	}

	public static BlockState vitre(DyeColor c) {
		return c == null ? b(Blocks.GLASS_PANE) : Blocks.STAINED_GLASS_PANE.pick(c).defaultBlockState();
	}

	public static BlockState laine(DyeColor c) {
		return Blocks.WOOL.pick(c).defaultBlockState();
	}

	public static BlockState marche(Block b, Direction d) {
		return b.defaultBlockState().setValue(StairBlock.FACING, d);
	}

	public static BlockState dalle(Block b, boolean haut) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, haut ? SlabType.TOP : SlabType.BOTTOM);
	}

	public static BlockState axe(Block b, Direction.Axis a) {
		return b.defaultBlockState().setValue(BlockStateProperties.AXIS, a);
	}

	protected void caisse(int x, int y, int z, Caisse.Type t) {
		caisses.add(new Caisse(new BlockPos(x, y, z), t));
	}

	/** Pose les tonneaux de butin de l'élément (avec un sol sous chacun). */
	protected void poserCaisses(Ecrivain w) {
		for (Caisse c : caisses) {
			BlockPos p = c.pos();
			if (w.dedans(p.getX(), p.getZ())) {
				if (w.get(p.getX(), p.getY() - 1, p.getZ()).isAir()) {
					w.set(p.getX(), p.getY() - 1, p.getZ(), Blocks.SMOOTH_STONE);
				}
				w.set(p.getX(), p.getY(), p.getZ(), Blocks.BARREL);
				w.air(p.getX(), p.getY() + 1, p.getZ());
			}
		}
	}
}
