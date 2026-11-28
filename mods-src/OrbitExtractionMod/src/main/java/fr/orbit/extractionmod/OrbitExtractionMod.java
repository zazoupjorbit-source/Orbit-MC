package fr.orbit.extractionmod;

import fr.orbit.extractionmod.carte.Generateur;
import fr.orbit.extractionmod.gun.GunService;
import fr.orbit.extractionmod.net.Payloads;
import fr.orbit.extractionmod.raid.Commandes;
import fr.orbit.extractionmod.raid.InfecteService;
import fr.orbit.extractionmod.raid.LootService;
import fr.orbit.extractionmod.raid.RaidService;
import fr.orbit.extractionmod.registry.ModComponents;
import fr.orbit.extractionmod.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prototype moddé du mode extraction d'Orbit MC (côté commun : serveur + client).
 * Le serveur reste maître de tout : munitions, cadence, dégâts. Le client n'envoie que des intentions (tirer, recharger).
 */
public final class OrbitExtractionMod implements ModInitializer {
	public static final String MOD_ID = "orbitextraction";
	public static final Logger LOG = LoggerFactory.getLogger("OrbitExtraction");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModComponents.register();
		ModItems.register();
		Payloads.register();
		GunService.register();
		Generateur.register();
		RaidService.register();
		InfecteService.register();
		LootService.register();
		Commandes.register();
		LOG.info("Orbit Extraction (prototype moddé) chargé.");
	}
}
