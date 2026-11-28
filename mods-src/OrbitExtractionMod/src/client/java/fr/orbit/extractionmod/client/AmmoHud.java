package fr.orbit.extractionmod.client;

import fr.orbit.extractionmod.gun.GunItem;
import fr.orbit.extractionmod.gun.GunService;
import fr.orbit.extractionmod.gun.GunStats;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/** Compteur de munitions en bas à droite, aux couleurs d'Orbit (bleu nuit + or). Natif : pas d'action bar, pas de pack. */
final class AmmoHud implements HudElement {
	private static final int FOND = 0xC00B1A33;
	private static final int BORD = 0xFF2E6BD6;
	private static final int OR = 0xFFF2C14E;
	private static final int BLANC = 0xFFF0F0F0;
	private static final int GRIS = 0xFF8A919B;
	private static final int ROUGE = 0xFFE74C3C;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		ItemStack stack = player.getMainHandItem();
		if (!(stack.getItem() instanceof GunItem gun)) {
			return;
		}
		GunStats s = gun.stats();
		Font font = mc.font;
		int ammo = GunItem.ammo(stack);
		int reserve = player.isCreative() ? -1 : GunService.countAmmo(player, s.ammo());

		int w = 110, h = 34;
		int x = g.guiWidth() - w - 6;
		int y = g.guiHeight() - h - 6;
		g.fill(x, y, x + w, y + h, FOND);
		g.fill(x, y, x + w, y + 1, BORD);
		g.fill(x, y + h - 1, x + w, y + h, BORD);
		g.fill(x, y, x + 2, y + h, OR);

		g.text(font, s.name().toUpperCase(), x + 7, y + 5, GRIS, false);
		String mag = String.valueOf(ammo);
		g.text(font, mag, x + 7, y + 18, ammo * 4 <= s.mag() ? ROUGE : BLANC, true);
		int mx = x + 7 + font.width(mag);
		g.text(font, " / " + s.mag(), mx, y + 18, GRIS, false);
		String res = reserve < 0 ? "∞" : String.valueOf(reserve);
		g.text(font, res, x + w - 7 - font.width(res), y + 18, OR, true);

		// Pastilles : une par balle du chargeur.
		int px = x + 7;
		int py = y + 29;
		int large = w - 14;
		if (s.mag() <= large / 2) {
			int pas = large / s.mag();
			for (int i = 0; i < s.mag(); i++) {
				g.fill(px + i * pas, py, px + i * pas + pas - 1, py + 2, i < ammo ? OR : 0x55FFFFFF);
			}
		} else {
			// Gros chargeur (LMG, P90) : une jauge continue.
			int plein = Math.round(large * ammo / (float) s.mag());
			g.fill(px, py, px + plein, py + 2, OR);
			g.fill(px + plein, py, px + large, py + 2, 0x55FFFFFF);
		}

		if (OrbitExtractionModClient.reloadLeft > 0 && OrbitExtractionModClient.reloadTotal > 0) {
			float done = 1f - (OrbitExtractionModClient.reloadLeft - delta.getGameTimeDeltaPartialTick(false))
					/ OrbitExtractionModClient.reloadTotal;
			int bw = 80;
			int bx = g.guiWidth() / 2 - bw / 2;
			int by = g.guiHeight() / 2 + 12;
			g.fill(bx - 1, by - 1, bx + bw + 1, by + 4, FOND);
			g.fill(bx, by, bx + Math.round(bw * Math.min(1f, Math.max(0f, done))), by + 3, OR);
			g.centeredText(font, "RECHARGEMENT", g.guiWidth() / 2, by + 6, BLANC);
		}
	}
}
