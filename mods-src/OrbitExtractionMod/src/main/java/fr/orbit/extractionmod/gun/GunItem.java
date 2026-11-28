package fr.orbit.extractionmod.gun;

import fr.orbit.extractionmod.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Une arme : le tir ne passe pas par le clic droit vanilla (il arrive par le paquet « tirer » du mod client),
 * donc l'utilisation classique de l'objet est neutralisée. La barre sous l'icône montre les balles restantes.
 */
public final class GunItem extends Item {
	private static final int OR = 0xF2C14E;
	private static final int ROUGE = 0xE74C3C;

	private final GunStats stats;

	public GunItem(Properties properties, GunStats stats) {
		super(properties);
		this.stats = stats;
	}

	public GunStats stats() {
		return stats;
	}

	public static int ammo(ItemStack stack) {
		Integer a = stack.get(ModComponents.AMMO);
		return a == null ? 0 : a;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return InteractionResult.FAIL;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		return InteractionResult.FAIL;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13f * ammo(stack) / stats.mag());
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return ammo(stack) * 4 <= stats.mag() ? ROUGE : OR;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
	                            Consumer<Component> lines, TooltipFlag flag) {
		lines.accept(Component.literal(stats.categorie().nom + " • " + stats.ammo().nom + (stats.auto() ? " • auto" : " • coup par coup"))
				.withStyle(ChatFormatting.BLUE));
		lines.accept(Component.literal(stats.desc()).withStyle(ChatFormatting.GRAY));
		lines.accept(Component.literal("Chargeur : " + ammo(stack) + " / " + stats.mag()).withStyle(ChatFormatting.GOLD));
		lines.accept(Component.literal((stats.auto() ? "Clic droit maintenu" : "Clic droit") + " : tirer   •   R : recharger")
				.withStyle(ChatFormatting.AQUA));
	}
}
