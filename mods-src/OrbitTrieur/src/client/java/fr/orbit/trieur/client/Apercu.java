package fr.orbit.trieur.client;

import fr.orbit.trieur.bloc.TrieurBlockEntity;
import fr.orbit.trieur.net.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Outil de développement : avec -Dorbittrieur.apercu=x,y,z (monde solo), ouvre tout seul le tampon puis le panel
 * du trieur à cette position et fait une capture d'écran de chacun (dossier screenshots). Inactif sinon.
 */
final class Apercu {
	private Apercu() {
	}

	private static int ticks;

	static void installer() {
		String prop = System.getProperty("orbittrieur.apercu");
		if (prop == null) {
			return;
		}
		String[] p = prop.split(",");
		BlockPos pos = new BlockPos(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null) {
				return;
			}
			ticks++;
			IntegratedServer srv = mc.getSingleplayerServer();
			switch (ticks) {
				case 80 -> srv.execute(() -> {
					ServerPlayer sp = srv.getPlayerList().getPlayers().getFirst();
					if (sp.level().getBlockEntity(pos) instanceof TrieurBlockEntity be) {
						be.actif = false; // pour voir des objets dans le tampon
						be.setItem(0, new ItemStack(Items.IRON_INGOT, 12));
						be.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
						sp.openMenu(be);
					}
				});
				case 140 -> Screenshot.grab(mc, true);
				case 160 -> ClientPlayNetworking.send(new Payloads.Demander(pos));
				case 220 -> Screenshot.grab(mc, true);
				case 240 -> mc.stop();
				default -> {
				}
			}
		});
	}
}
