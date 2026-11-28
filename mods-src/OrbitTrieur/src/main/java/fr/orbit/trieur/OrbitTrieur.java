package fr.orbit.trieur;

import fr.orbit.trieur.net.Payloads;
import fr.orbit.trieur.net.Reseau;
import fr.orbit.trieur.registry.ModBlocks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orbit Trieur : un bloc qui reçoit des objets (à la main, entonnoir, tuyaux de n'importe quel mod)
 * et les répartit dans les inventaires autour de lui selon des filtres réglés dans son panel en jeu.
 * <p>
 * Compatibilité : tout passe par l'API de transfert Fabric (ItemStorage.SIDED), que les mods de stockage
 * (coffres, tiroirs, barils, réseaux…) implémentent ; les inventaires vanilla y sont branchés d'office.
 */
public final class OrbitTrieur implements ModInitializer {
	public static final String MOD_ID = "orbittrieur";
	public static final Logger LOG = LoggerFactory.getLogger("OrbitTrieur");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		TrieurConfig.charger();
		ModBlocks.register();
		Payloads.register();
		Reseau.register();
		for (String onglet : new String[]{"redstone_blocks", "functional_blocks"}) {
			CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB,
					Identifier.withDefaultNamespace(onglet))).register(out -> out.accept(ModBlocks.TRIEUR_OBJET));
		}
		LOG.info("Orbit Trieur chargé (rayon max {}, {} objets par cycle).", TrieurConfig.get().rayonMax,
				TrieurConfig.get().objetsParCycle);
	}
}
