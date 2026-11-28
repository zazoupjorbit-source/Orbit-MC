package fr.orbit.trieur.menu;

import fr.orbit.trieur.bloc.TrieurBlockEntity;
import fr.orbit.trieur.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Le tampon du trieur (grand coffre : 6 × 9 cases) + l'inventaire du joueur, disposés comme un grand coffre vanilla. */
public class TrieurMenu extends AbstractContainerMenu {
	/** Même hauteur qu'un grand coffre vanilla. */
	public static final int HAUTEUR = 114 + TrieurBlockEntity.RANGEES * 18;
	/** Haut de l'inventaire du joueur. */
	public static final int Y_JOUEUR = (TrieurBlockEntity.RANGEES - 4) * 18 + 103;
	private final Container tampon;
	private final BlockPos pos;

	/** Côté client (ouvert par le serveur via ExtendedMenuType, avec la position du bloc). */
	public TrieurMenu(int id, Inventory inv, BlockPos pos) {
		this(id, inv, new SimpleContainer(TrieurBlockEntity.TAILLE), pos);
	}

	public TrieurMenu(int id, Inventory inv, Container tampon, BlockPos pos) {
		super(ModBlocks.TRIEUR_MENU, id);
		checkContainerSize(tampon, TrieurBlockEntity.TAILLE);
		this.tampon = tampon;
		this.pos = pos;
		tampon.startOpen(inv.player);
		for (int r = 0; r < TrieurBlockEntity.RANGEES; r++) {
			for (int c = 0; c < 9; c++) {
				addSlot(new Slot(tampon, c + r * 9, 8 + c * 18, 18 + r * 18));
			}
		}
		addStandardInventorySlots(inv, 8, Y_JOUEUR);
	}

	public BlockPos pos() {
		return pos;
	}

	@Override
	public boolean stillValid(Player player) {
		return tampon.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack copie = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot.hasItem()) {
			ItemStack s = slot.getItem();
			copie = s.copy();
			int n = TrieurBlockEntity.TAILLE;
			if (index < n) {
				if (!moveItemStackTo(s, n, slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!moveItemStackTo(s, 0, n, false)) {
				return ItemStack.EMPTY;
			}
			if (s.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return copie;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		tampon.stopOpen(player);
	}
}
