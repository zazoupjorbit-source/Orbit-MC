package fr.orbit.trieur.bloc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.orbit.trieur.TrieurConfig;
import fr.orbit.trieur.menu.TrieurMenu;
import fr.orbit.trieur.registry.ModBlocks;
import fr.orbit.trieur.tri.Moteur;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le trieur : un tampon de 9 cases où l'on dépose les objets (main, entonnoir, tuyaux de mods),
 * vidé tous les quelques ticks vers les inventaires du rayon selon les règles du panel.
 * Les règles sont gardées en positions relatives : le trieur et ses coffres peuvent être déplacés ensemble.
 */
public class TrieurBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, ExtendedMenuProvider<BlockPos> {
	/** Tampon de la taille d'un grand coffre : 6 rangées de 9. */
	public static final int RANGEES = 6;
	public static final int TAILLE = RANGEES * 9;
	private static final int[] CASES = java.util.stream.IntStream.range(0, TAILLE).toArray();

	/** Une règle enregistrée : décalage par rapport au trieur + texte du filtre. */
	public record Regle(BlockPos decalage, String filtre) {
		static final Codec<Regle> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("decalage").forGetter(Regle::decalage),
				Codec.STRING.fieldOf("filtre").forGetter(Regle::filtre)
		).apply(i, Regle::new));
	}

	private NonNullList<ItemStack> items = NonNullList.withSize(TAILLE, ItemStack.EMPTY);
	public boolean actif = true;
	/** Objets sans règle : vers un inventaire (sans filtre) qui en contient déjà. */
	public boolean regrouper = true;
	public int rayon = TrieurConfig.get().rayonDefaut;
	/** Filtres par inventaire (décalage relatif → texte). */
	public final Map<BlockPos, String> filtres = new LinkedHashMap<>();
	/** Inventaire « surplus » (décalage relatif) : reçoit ce que rien d'autre n'accepte. Null = aucun. */
	public BlockPos surplus;

	/** Cache moteur (non sauvegardé). */
	public final Moteur.Etat etat = new Moteur.Etat();

	public TrieurBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.TRIEUR_BE, pos, state);
	}

	public static void tickServeur(Level level, BlockPos pos, BlockState state, TrieurBlockEntity be) {
		Moteur.tick(level, pos, be);
	}

	// ---- Sauvegarde ----

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		items = NonNullList.withSize(TAILLE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(in, items);
		actif = in.getBooleanOr("actif", true);
		regrouper = in.getBooleanOr("regrouper", true);
		rayon = Math.clamp(in.getIntOr("rayon", TrieurConfig.get().rayonDefaut), 1, 128);
		filtres.clear();
		in.list("regles", Regle.CODEC).ifPresent(l -> l.forEach(r -> filtres.put(r.decalage(), r.filtre())));
		surplus = in.read("surplus", BlockPos.CODEC).orElse(null);
		etat.aRescanner = true;
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		ContainerHelper.saveAllItems(out, items);
		out.putBoolean("actif", actif);
		out.putBoolean("regrouper", regrouper);
		out.putInt("rayon", rayon);
		ValueOutput.TypedOutputList<Regle> liste = out.list("regles", Regle.CODEC);
		filtres.forEach((k, v) -> {
			if (!v.isBlank()) {
				liste.add(new Regle(k, v));
			}
		});
		if (surplus != null) {
			out.store("surplus", BlockPos.CODEC, surplus);
		}
	}

	/** Règles en positions absolues, pour le moteur et le panel. */
	public String filtreA(BlockPos absolu) {
		return filtres.getOrDefault(absolu.subtract(worldPosition), "");
	}

	public boolean estSurplus(BlockPos absolu) {
		return surplus != null && surplus.equals(absolu.subtract(worldPosition));
	}

	/** Remplace toutes les règles (appelé par le panel, positions absolues). */
	public void appliquer(boolean actif, boolean regrouper, int rayon, List<Map.Entry<BlockPos, String>> regles, BlockPos surplusAbs) {
		this.actif = actif;
		this.regrouper = regrouper;
		this.rayon = rayon;
		filtres.clear();
		for (Map.Entry<BlockPos, String> e : regles) {
			String f = e.getValue().strip();
			if (!f.isEmpty()) {
				filtres.put(e.getKey().subtract(worldPosition), f);
			}
		}
		surplus = surplusAbs == null ? null : surplusAbs.subtract(worldPosition);
		etat.aRescanner = true;
		setChanged();
	}

	public List<BlockPos> decalagesConfigures() {
		List<BlockPos> l = new ArrayList<>(filtres.keySet());
		if (surplus != null && !l.contains(surplus)) {
			l.add(surplus);
		}
		return l;
	}

	// ---- Inventaire ----

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.orbittrieur.trieur");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return TAILLE;
	}

	@Override
	public int[] getSlotsForFace(Direction face) {
		return CASES;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction face) {
		return true;
	}

	/** Les entonnoirs et tuyaux ne vident pas le trieur : il distribue lui-même. */
	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction face) {
		return false;
	}

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inv) {
		return new TrieurMenu(id, inv, this, worldPosition);
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return worldPosition;
	}
}
