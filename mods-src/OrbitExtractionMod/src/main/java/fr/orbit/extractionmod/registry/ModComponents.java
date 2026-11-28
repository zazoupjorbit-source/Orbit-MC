package fr.orbit.extractionmod.registry;

import com.mojang.serialization.Codec;
import fr.orbit.extractionmod.OrbitExtractionMod;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

/** Données portées par les objets (sauvegardées et synchronisées avec le client). */
public final class ModComponents {
	private ModComponents() {
	}

	/** Balles dans le chargeur de l'arme. */
	public static final DataComponentType<Integer> AMMO = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			OrbitExtractionMod.id("munitions"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	public static void register() {
		// Chargement de la classe = enregistrement.
	}
}
