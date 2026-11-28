package fr.orbit.trieur.client;

import fr.orbit.trieur.net.Payloads;
import fr.orbit.trieur.registry.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

public final class OrbitTrieurClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModBlocks.TRIEUR_MENU, TrieurScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(Payloads.Reglages.TYPE, (p, ctx) -> {
			if (ctx.client().gui.screen() instanceof ReglagesScreen ouvert && ouvert.pos().equals(p.pos())) {
				ouvert.recharger(p);
			} else {
				ctx.client().gui.setScreen(new ReglagesScreen(p));
			}
		});
		Apercu.installer();
	}
}
