package fr.orbit.extractionmod.carte;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Écrit les blocs d'UN tronçon (chunk) : tout ce qui sort du tronçon est ignoré.
 * Ainsi chaque élément de la carte peut se dessiner en entier, tronçon par tronçon, sans rien garder en mémoire.
 */
public final class Ecrivain {
	public final ServerLevel level;
	public final int x0, z0, x1, z1;
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	Ecrivain(ServerLevel level, int chunkX, int chunkZ) {
		this.level = level;
		this.x0 = chunkX << 4;
		this.z0 = chunkZ << 4;
		this.x1 = x0 + 15;
		this.z1 = z0 + 15;
	}

	public boolean dedans(int x, int z) {
		return x >= x0 && x <= x1 && z >= z0 && z <= z1;
	}

	public void set(int x, int y, int z, BlockState s) {
		if (dedans(x, z)) {
			pos.set(x, y, z);
			// Vitres, clôtures, murets, escaliers : on calcule leurs raccords avec les voisins déjà posés.
			Block bl = s.getBlock();
			if (bl instanceof CrossCollisionBlock || bl instanceof WallBlock || bl instanceof StairBlock) {
				s = Block.updateFromNeighbourShapes(s, level, pos);
			}
			level.setBlock(pos, s, Block.UPDATE_ALL);
		}
	}

	public void set(int x, int y, int z, Block b) {
		set(x, y, z, b.defaultBlockState());
	}

	public void air(int x, int y, int z) {
		set(x, y, z, AIR);
	}

	public BlockState get(int x, int y, int z) {
		return level.getBlockState(pos.set(x, y, z));
	}

	public boolean vide(int x, int y, int z) {
		return dedans(x, z) && get(x, y, z).isAir();
	}

	/** Pavé plein, borné au tronçon. */
	public void pave(int ax, int ay, int az, int bx, int by, int bz, BlockState s) {
		for (int x = Math.max(Math.min(ax, bx), x0); x <= Math.min(Math.max(ax, bx), x1); x++) {
			for (int z = Math.max(Math.min(az, bz), z0); z <= Math.min(Math.max(az, bz), z1); z++) {
				for (int y = Math.min(ay, by); y <= Math.max(ay, by); y++) {
					level.setBlock(pos.set(x, y, z), s, Block.UPDATE_ALL);
				}
			}
		}
	}

	/** Remet le tronçon à zéro (pour une régénération forcée) : air au-dessus du sol, terre et herbe dessous. */
	void raz(int hautMax) {
		BlockState herbe = Blocks.GRASS_BLOCK.defaultBlockState();
		BlockState terre = Blocks.DIRT.defaultBlockState();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = Carte.SOL + 1; y <= hautMax; y++) {
					if (!level.getBlockState(pos.set(x, y, z)).isAir()) {
						level.setBlock(pos, AIR, Block.UPDATE_CLIENTS);
					}
				}
				for (int y = Carte.SOL - 4; y < Carte.SOL; y++) {
					level.setBlock(pos.set(x, y, z), terre, Block.UPDATE_CLIENTS);
				}
				level.setBlock(pos.set(x, Carte.SOL, z), herbe, Block.UPDATE_CLIENTS);
			}
		}
	}
}
