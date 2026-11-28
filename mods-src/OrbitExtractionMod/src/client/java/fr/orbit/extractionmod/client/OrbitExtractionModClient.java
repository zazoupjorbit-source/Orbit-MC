package fr.orbit.extractionmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.orbit.extractionmod.OrbitExtractionMod;
import fr.orbit.extractionmod.gun.GunItem;
import fr.orbit.extractionmod.gun.GunStats;
import fr.orbit.extractionmod.net.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Côté client du prototype : la touche de rechargement (réglable dans Options > Commandes > Orbit MC),
 * le tir automatique au clic droit maintenu avec recul de caméra, et le HUD de munitions.
 */
public final class OrbitExtractionModClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORIE = KeyMapping.Category.register(OrbitExtractionMod.id("orbit"));
	private static KeyMapping recharger;

	private static int cooldown;
	private static boolean etaitAppuye;
	/** Rechargement en cours (pour la barre du HUD) : tick de fin et durée, 0 = aucun. */
	static int reloadLeft;
	static int reloadTotal;

	@Override
	public void onInitializeClient() {
		recharger = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.orbitextraction.recharger",
				InputConstants.Type.KEYBOARD, InputConstants.KEY_R, CATEGORIE));

		ClientPlayNetworking.registerGlobalReceiver(Payloads.ReloadStarted.TYPE, (p, ctx) -> {
			reloadLeft = p.ticks();
			reloadTotal = p.ticks();
		});

		ClientPlayNetworking.registerGlobalReceiver(Payloads.RaidSync.TYPE, (p, ctx) -> RaidHud.recevoir(p));
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> RaidHud.reset());

		ClientTickEvents.END_CLIENT_TICK.register(OrbitExtractionModClient::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, OrbitExtractionMod.id("munitions"), new AmmoHud());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, OrbitExtractionMod.id("raid"), new RaidHud());
	}

	private static void tick(Minecraft mc) {
		if (reloadLeft > 0) {
			reloadLeft--;
		}
		if (cooldown > 0) {
			cooldown--;
		}
		LocalPlayer player = mc.player;
		if (player == null || !ClientPlayNetworking.canSend(Payloads.Fire.TYPE)) {
			while (recharger.consumeClick()) {
				// serveur non moddé : on ignore la touche
			}
			return;
		}
		ItemStack stack = player.getMainHandItem();
		if (!(stack.getItem() instanceof GunItem gun)) {
			reloadLeft = 0;
			while (recharger.consumeClick()) {
			}
			return;
		}
		GunStats s = gun.stats();
		while (recharger.consumeClick()) {
			ClientPlayNetworking.send(Payloads.Reload.INSTANCE);
		}
		boolean appui = mc.options.keyUse.isDown();
		boolean front = appui && !etaitAppuye;
		etaitAppuye = appui;
		// Automatique : tant que le clic est maintenu. Coup par coup : un tir par appui.
		if (mc.gui.screen() == null && appui && (s.auto() || front) && cooldown == 0 && reloadLeft == 0) {
			cooldown = s.intervalTicks();
			ClientPlayNetworking.send(Payloads.Fire.INSTANCE);
			if (GunItem.ammo(stack) > 0) {
				// Recul : la caméra remonte un peu, avec une petite dérive latérale.
				player.setXRot(player.getXRot() - s.recoil());
				player.setYRot(player.getYRot() + (player.getRandom().nextFloat() - 0.5f) * s.recoil() * 0.6f);
			}
		}
	}
}
