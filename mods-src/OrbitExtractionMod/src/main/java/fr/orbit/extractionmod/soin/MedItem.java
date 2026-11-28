package fr.orbit.extractionmod.soin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/** Soin : maintenir clic droit (on ralentit pendant ce temps), soigne à la fin. */
public final class MedItem extends Item {
	private final float soin;
	private final int duree;
	private final boolean regen;

	public MedItem(Properties properties, float soin, int duree, boolean regen) {
		super(properties);
		this.soin = soin;
		this.duree = duree;
		this.regen = regen;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.getHealth() >= player.getMaxHealth()) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.literal("Tu es déjà en pleine forme.").withColor(0x8A919B));
			}
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		if (!level.isClientSide()) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.8f, 1.2f);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return duree;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BOW;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (level instanceof ServerLevel sl) {
			entity.heal(soin);
			if (regen) {
				entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
			}
			sl.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.8f);
			if (!(entity instanceof Player p && p.isCreative())) {
				stack.shrink(1);
			}
		}
		return stack;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
	                            Consumer<Component> lines, TooltipFlag flag) {
		lines.accept(Component.literal(regen ? "Soin complet + régénération" : "Rend " + (int) (soin / 2) + " cœurs")
				.withStyle(ChatFormatting.GREEN));
		lines.accept(Component.literal("Clic droit maintenu " + (duree / 20) + " s").withStyle(ChatFormatting.AQUA));
	}
}
