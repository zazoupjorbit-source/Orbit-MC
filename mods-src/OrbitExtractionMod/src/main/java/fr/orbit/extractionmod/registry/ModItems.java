package fr.orbit.extractionmod.registry;

import fr.orbit.extractionmod.OrbitExtractionMod;
import fr.orbit.extractionmod.gun.Ammo;
import fr.orbit.extractionmod.gun.GunItem;
import fr.orbit.extractionmod.gun.GunStats;
import fr.orbit.extractionmod.soin.MedItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Objets du mod : de vrais objets (pas des objets vanilla maquillés par le pack de ressources). */
public final class ModItems {
	private ModItems() {
	}

	/** Toutes les armes, par identifiant (g18, ak12…), dans l'ordre de GunStats.TOUTES. */
	public static final Map<String, GunItem> ARMES = new LinkedHashMap<>();
	public static final Map<Ammo, Item> MUNITIONS = new EnumMap<>(Ammo.class);

	static {
		for (GunStats s : GunStats.TOUTES) {
			Rarity r = switch (s.categorie()) {
				case PISTOLET -> Rarity.UNCOMMON;
				case PM, POMPE -> Rarity.RARE;
				default -> Rarity.EPIC;
			};
			ARMES.put(s.id(), register(s.id(), p -> new GunItem(p, s),
					new Item.Properties().stacksTo(1).rarity(r).component(ModComponents.AMMO, s.mag())));
		}
		for (Ammo a : Ammo.values()) {
			MUNITIONS.put(a, register(a.item, Item::new, new Item.Properties().stacksTo(64)));
		}
	}

	public static final GunItem G18 = ARMES.get("g18");
	public static final Item AMMO_9MM = MUNITIONS.get(Ammo.P9);

	public static final MedItem BANDAGE = register("bandage", p -> new MedItem(p, 6f, 40, false),
			new Item.Properties().stacksTo(8));
	public static final MedItem TROUSSE = register("trousse_soin", p -> new MedItem(p, 40f, 80, true),
			new Item.Properties().stacksTo(2).rarity(Rarity.RARE));

	private static <T extends Item> T register(String path, Function<Item.Properties, T> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, OrbitExtractionMod.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
	}

	public static void register() {
		// Chargement de la classe = enregistrement.
	}
}
