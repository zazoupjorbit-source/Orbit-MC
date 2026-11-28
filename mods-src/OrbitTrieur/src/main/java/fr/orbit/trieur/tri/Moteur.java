package fr.orbit.trieur.tri;

import fr.orbit.trieur.TrieurConfig;
import fr.orbit.trieur.bloc.TrieurBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Le moteur de tri, côté serveur.
 * <p>
 * Détection : tout bloc du rayon qui expose un stockage d'objets via l'API de transfert Fabric
 * (coffres vanilla, barils, boîtes de Shulker, et les stockages des mods qui l'implémentent).
 * <p>
 * Ordre de tri d'un objet : 1) inventaires dont le filtre l'accepte (du plus proche au plus loin),
 * 2) si « regrouper » : inventaires sans filtre qui en contiennent déjà, 3) l'inventaire surplus.
 * Ce qui ne trouve aucune place reste dans le tampon.
 */
public final class Moteur {
	private Moteur() {
	}

	private static final Direction[] FACES = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST,
			Direction.WEST, Direction.DOWN};
	private static final int RESCAN_TICKS = 100;

	/** Cache par trieur : cibles détectées et face d'accès de chacune. */
	public static final class Etat {
		public boolean aRescanner = true;
		int compteur;
		int depuisScan;
		public final List<BlockPos> cibles = new ArrayList<>();
		final Map<BlockPos, Direction> faces = new HashMap<>();
	}

	public static void tick(Level level, BlockPos origine, TrieurBlockEntity be) {
		Etat e = be.etat;
		TrieurConfig cfg = TrieurConfig.get();
		if (++e.depuisScan >= RESCAN_TICKS) {
			e.aRescanner = true;
		}
		if (!be.actif || ++e.compteur < cfg.intervalleTicks) {
			return;
		}
		e.compteur = 0;
		if (be.isEmpty()) {
			return;
		}
		if (e.aRescanner) {
			scanner(level, origine, be);
		}
		if (trier(level, be, cfg.objetsParCycle)) {
			be.setChanged();
		}
	}

	/** Recherche les inventaires du rayon, du plus proche au plus loin. */
	public static void scanner(Level level, BlockPos origine, TrieurBlockEntity be) {
		Etat e = be.etat;
		e.aRescanner = false;
		e.depuisScan = 0;
		e.cibles.clear();
		e.faces.clear();
		TrieurConfig cfg = TrieurConfig.get();
		int r = Math.clamp(be.rayon, 1, cfg.rayonMax);
		// On parcourt les blocs à inventaire des chunks chargés (et non chaque position du cube :
		// à 64 blocs, ça ferait plus de 2 millions de cases). Les chunks non chargés sont ignorés.
		List<BlockPos> candidats = new ArrayList<>();
		int cx0 = (origine.getX() - r) >> 4, cx1 = (origine.getX() + r) >> 4;
		int cz0 = (origine.getZ() - r) >> 4, cz1 = (origine.getZ() + r) >> 4;
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int cz = cz0; cz <= cz1; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				for (BlockPos p : level.getChunk(cx, cz).getBlockEntities().keySet()) {
					if (!p.equals(origine) && Math.abs(p.getX() - origine.getX()) <= r
							&& Math.abs(p.getY() - origine.getY()) <= r && Math.abs(p.getZ() - origine.getZ()) <= r) {
						candidats.add(p.immutable());
					}
				}
			}
		}
		candidats.sort(Comparator.comparingDouble(p -> p.distSqr(origine)));
		Set<BlockPos> vus = new LinkedHashSet<>();
		for (BlockPos p : candidats) {
			if (vus.size() >= cfg.maxCibles) {
				break;
			}
			BlockState st = level.getBlockState(p);
			BlockEntity cible = level.getBlockEntity(p);
			if (cible instanceof TrieurBlockEntity) {
				continue; // pas de ping-pong entre trieurs
			}
			if (cfg.estIgnore(BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString())) {
				continue;
			}
			// Grand coffre : une seule moitié (le stockage Fabric couvre les deux).
			if (st.getBlock() instanceof ChestBlock && st.hasProperty(ChestBlock.TYPE)
					&& st.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
				BlockPos autre = p.relative(ChestBlock.getConnectedDirection(st));
				if (vus.contains(autre)) {
					continue;
				}
			}
			Direction face = trouverFace(level, p, st, cible);
			if (face != null) {
				vus.add(p);
				e.faces.put(p, face);
			}
		}
		e.cibles.addAll(vus);
	}

	private static Direction trouverFace(Level level, BlockPos p, BlockState st, BlockEntity be) {
		for (Direction d : FACES) {
			Storage<ItemVariant> s = ItemStorage.SIDED.find(level, p, st, be, d);
			if (s != null && s.supportsInsertion()) {
				return d;
			}
		}
		return null;
	}

	public static Storage<ItemVariant> stockage(Level level, BlockPos p, Etat e) {
		Direction d = e.faces.get(p);
		if (d == null || !level.isLoaded(p)) { // ne jamais charger un chunk pour trier
			return null;
		}
		return ItemStorage.SIDED.find(level, p, d);
	}

	/** Vide le tampon autant que possible. @return vrai si quelque chose a bougé. */
	private static boolean trier(Level level, TrieurBlockEntity be, int budgetMax) {
		Etat e = be.etat;
		int budget = budgetMax;
		boolean bouge = false;
		// Filtres compilés une fois par cycle.
		Map<BlockPos, Filtre> filtres = new HashMap<>();
		BlockPos surplus = null;
		for (BlockPos p : e.cibles) {
			Filtre f = Filtre.of(be.filtreA(p));
			if (!f.estVide()) {
				filtres.put(p, f);
			}
			if (be.estSurplus(p)) {
				surplus = p;
			}
		}
		for (int slot = 0; slot < TrieurBlockEntity.TAILLE && budget > 0; slot++) {
			ItemStack stack = be.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			int avant = stack.getCount();
			// 1) règles explicites
			for (BlockPos p : e.cibles) {
				Filtre f = filtres.get(p);
				if (f != null && f.accepte(stack)) {
					budget -= verser(level, p, e, stack, budget);
					if (stack.isEmpty() || budget <= 0) {
						break;
					}
				}
			}
			// 2) regrouper avec l'existant
			if (!stack.isEmpty() && budget > 0 && be.regrouper) {
				for (BlockPos p : e.cibles) {
					if (filtres.containsKey(p) || p.equals(surplus)) {
						continue;
					}
					Storage<ItemVariant> s = stockage(level, p, e);
					if (s != null && contient(s, stack.getItem())) {
						budget -= verser(level, p, e, stack, budget);
						if (stack.isEmpty() || budget <= 0) {
							break;
						}
					}
				}
			}
			// 3) surplus
			if (!stack.isEmpty() && budget > 0 && surplus != null) {
				budget -= verser(level, surplus, e, stack, budget);
			}
			if (stack.getCount() != avant) {
				be.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
				bouge = true;
			}
		}
		return bouge;
	}

	/** Insère jusqu'à {@code max} objets de la pile dans l'inventaire ; la pile est réduite d'autant. */
	private static int verser(Level level, BlockPos p, Etat e, ItemStack stack, int max) {
		Storage<ItemVariant> s = stockage(level, p, e);
		if (s == null) {
			e.aRescanner = true;
			return 0;
		}
		ItemVariant v = ItemVariant.of(stack);
		long voulu = Math.min(stack.getCount(), max);
		try (Transaction tx = Transaction.openOuter()) {
			long mis = s.insert(v, voulu, tx);
			if (mis > 0) {
				tx.commit();
				stack.shrink((int) mis);
				return (int) mis;
			}
		}
		return 0;
	}

	static boolean contient(Storage<ItemVariant> s, Item item) {
		for (StorageView<ItemVariant> view : s.nonEmptyViews()) {
			if (view.getResource().getItem() == item) {
				return true;
			}
		}
		return false;
	}

	/** Jusqu'à {@code max} identifiants d'objets différents présents dans l'inventaire (pour « Remplir »). */
	public static List<String> apercu(Storage<ItemVariant> s, int max) {
		Set<String> ids = new LinkedHashSet<>();
		if (s != null) {
			for (StorageView<ItemVariant> view : s.nonEmptyViews()) {
				ids.add(BuiltInRegistries.ITEM.getKey(view.getResource().getItem()).toString());
				if (ids.size() >= max) {
					break;
				}
			}
		}
		return new ArrayList<>(ids);
	}
}
