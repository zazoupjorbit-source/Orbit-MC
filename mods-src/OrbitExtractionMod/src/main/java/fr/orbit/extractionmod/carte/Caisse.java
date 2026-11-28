package fr.orbit.extractionmod.carte;

import net.minecraft.core.BlockPos;

/** Emplacement d'un tonneau de butin, et ce qu'on y trouve. */
public record Caisse(BlockPos pos, Type type) {
	public enum Type {
		/** Nourriture, bandages, munitions de pistolet. */
		CIVILE,
		/** Bandages, trousses de soin. */
		MEDICALE,
		/** Munitions de fusil, grenades plus tard, parfois une arme. */
		MILITAIRE,
		/** Caisse d'armes : une arme presque à coup sûr. */
		ARMES
	}
}
