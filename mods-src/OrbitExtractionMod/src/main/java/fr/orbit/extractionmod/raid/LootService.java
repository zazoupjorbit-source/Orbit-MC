package fr.orbit.extractionmod.raid;

import fr.orbit.extractionmod.carte.Caisse;
import fr.orbit.extractionmod.carte.Generateur;
import fr.orbit.extractionmod.gun.Ammo;
import fr.orbit.extractionmod.gun.GunItem;
import fr.orbit.extractionmod.registry.ModComponents;
import fr.orbit.extractionmod.registry.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Tonneaux de butin de Cendreville (placés par le générateur, liste dans le Plan) : remplis à l'approche d'un joueur,
 * puis de nouveau 5 minutes après avoir été vidés. Tables par type : civile, médicale, militaire, armes.
 */
public final class LootService {
	private LootService() {
	}

	private static final int DELAI = 5 * 60 * 20;
	private static final double RAYON = 14;
	private static final Random RNG = new Random();
	private static final Map<BlockPos, Integer> dernier = new HashMap<>();

	private record Lot(int poids, Supplier<ItemStack> objet) {
	}

	private static final Map<Caisse.Type, List<Lot>> TABLES = new HashMap<>();

	private static ItemStack m(Ammo a, int min, int max) {
		return new ItemStack(ModItems.MUNITIONS.get(a), min + RNG.nextInt(max - min + 1));
	}

	private static ItemStack arme(String... ids) {
		GunItem g = ModItems.ARMES.get(ids[RNG.nextInt(ids.length)]);
		ItemStack s = new ItemStack(g);
		s.set(ModComponents.AMMO, RNG.nextInt(g.stats().mag() + 1));
		return s;
	}

	private static ItemStack o(Item i, int min, int max) {
		return new ItemStack(i, min + RNG.nextInt(max - min + 1));
	}

	static {
		TABLES.put(Caisse.Type.CIVILE, List.of(
				new Lot(30, () -> m(Ammo.P9, 6, 16)),
				new Lot(18, () -> new ItemStack(ModItems.BANDAGE)),
				new Lot(14, () -> o(Items.BREAD, 1, 3)),
				new Lot(10, () -> o(Items.APPLE, 1, 3)),
				new Lot(6, () -> o(Items.COOKED_BEEF, 1, 2)),
				new Lot(8, () -> m(Ammo.G12, 2, 6)),
				new Lot(4, () -> m(Ammo.R556, 5, 12)),
				new Lot(5, () -> arme("g18", "c96", "tec9")),
				new Lot(1, () -> arme("pompe", "elcovert"))));
		TABLES.put(Caisse.Type.MEDICALE, List.of(
				new Lot(45, () -> o(ModItems.BANDAGE, 1, 3)),
				new Lot(18, () -> new ItemStack(ModItems.TROUSSE)),
				new Lot(12, () -> m(Ammo.P9, 6, 14)),
				new Lot(10, () -> o(Items.BREAD, 1, 2)),
				new Lot(3, () -> arme("g18", "c96"))));
		TABLES.put(Caisse.Type.MILITAIRE, List.of(
				new Lot(28, () -> m(Ammo.R556, 10, 30)),
				new Lot(18, () -> m(Ammo.R762, 6, 18)),
				new Lot(14, () -> m(Ammo.P9, 10, 24)),
				new Lot(10, () -> m(Ammo.G12, 4, 8)),
				new Lot(8, () -> new ItemStack(ModItems.TROUSSE)),
				new Lot(10, () -> o(ModItems.BANDAGE, 1, 2)),
				new Lot(5, () -> new ItemStack(RNG.nextBoolean() ? Items.CHAINMAIL_CHESTPLATE : Items.CHAINMAIL_HELMET)),
				new Lot(2, () -> new ItemStack(RNG.nextBoolean() ? Items.IRON_CHESTPLATE : Items.IRON_LEGGINGS)),
				new Lot(7, () -> arme("p90", "pdw", "vector", "pompe", "tec9")),
				new Lot(4, () -> arme("ak12", "m4", "famas", "scar", "fal")),
				new Lot(1, () -> arme("aug", "tactm4", "vssvintorez"))));
		TABLES.put(Caisse.Type.ARMES, List.of(
				new Lot(20, () -> arme("p90", "pdw", "vector", "pompe")),
				new Lot(22, () -> arme("ak12", "m4", "famas", "scar", "fal")),
				new Lot(8, () -> arme("aug", "tactm4", "vssvintorez", "elcovert")),
				new Lot(4, () -> arme("m24", "lmg")),
				new Lot(24, () -> m(Ammo.R556, 15, 40)),
				new Lot(14, () -> m(Ammo.R762, 10, 25)),
				new Lot(8, () -> m(Ammo.G12, 6, 12))));
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (RaidService.actif() && !Generateur.enCours() && s.getTickCount() % 40 == 0) {
				tick(s);
			}
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel l = server.overworld();
		int now = server.getTickCount();
		List<Caisse> caisses = Generateur.plan().caisses;
		for (ServerPlayer p : l.players()) {
			if (p.isSpectator()) {
				continue;
			}
			for (Caisse c : caisses) {
				BlockPos pos = c.pos();
				if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > RAYON * RAYON) {
					continue;
				}
				Integer d = dernier.get(pos);
				if (d != null && now - d < DELAI) {
					continue;
				}
				BlockEntity be = l.getBlockEntity(pos);
				if (!(be instanceof Container ct)) {
					continue;
				}
				dernier.put(pos, now);
				if (ct.isEmpty()) {
					remplir(ct, c.type());
					be.setChanged();
				}
			}
		}
	}

	/** Remplit (ou force le remplissage, pour les tests) les caisses proches. */
	public static int remplirAutour(ServerPlayer p, double rayon) {
		ServerLevel l = (ServerLevel) p.level();
		int n = 0;
		for (Caisse c : Generateur.plan().caisses) {
			if (p.distanceToSqr(c.pos().getX(), c.pos().getY(), c.pos().getZ()) <= rayon * rayon
					&& l.getBlockEntity(c.pos()) instanceof Container ct) {
				ct.clearContent();
				remplir(ct, c.type());
				dernier.put(c.pos(), l.getServer().getTickCount());
				n++;
			}
		}
		return n;
	}

	private static void remplir(Container ct, Caisse.Type type) {
		List<Lot> table = TABLES.get(type);
		int total = table.stream().mapToInt(Lot::poids).sum();
		int nb = switch (type) {
			case CIVILE -> 1 + RNG.nextInt(3);
			case MEDICALE -> 2 + RNG.nextInt(2);
			case MILITAIRE -> 2 + RNG.nextInt(3);
			case ARMES -> 2 + RNG.nextInt(2);
		};
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < ct.getContainerSize(); i++) {
			slots.add(i);
		}
		java.util.Collections.shuffle(slots, RNG);
		for (int k = 0; k < nb && k < slots.size(); k++) {
			int t = RNG.nextInt(total);
			for (Lot lot : table) {
				t -= lot.poids();
				if (t < 0) {
					ct.setItem(slots.get(k), lot.objet().get());
					break;
				}
			}
		}
	}
}
