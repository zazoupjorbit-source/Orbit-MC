package fr.orbit.extractionmod.client;

import fr.orbit.extractionmod.carte.Carte;
import fr.orbit.extractionmod.net.Payloads;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/** Bandeau de raid en haut au centre : chrono, balise la plus proche (flèche + distance), barre d'extraction. */
final class RaidHud implements HudElement {
	private static final int FOND = 0xC00B1A33;
	private static final int BORD = 0xFF2E6BD6;
	private static final int OR = 0xFFF2C14E;
	private static final int BLANC = 0xFFF0F0F0;
	private static final int GRIS = 0xFF8A919B;
	private static final int ROUGE = 0xFFE74C3C;
	private static final int VERT = 0xFF2ECC71;
	private static final String[] FLECHES = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	/** Dernier état reçu du serveur. */
	static int etat, restant, sortie;
	static long recu;

	static void recevoir(Payloads.RaidSync p) {
		etat = p.etat();
		restant = p.restant();
		sortie = p.sortie();
		recu = System.currentTimeMillis();
	}

	static void reset() {
		etat = 0;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || etat == 0 || System.currentTimeMillis() - recu > 3000) {
			return;
		}
		Font font = mc.font;
		int cx = g.guiWidth() / 2;
		if (etat == 1) {
			String l1 = "CAMP ORBIT · zone sûre";
			String l2 = "Porte dorée ou /raid : largage sur " + Carte.NOM;
			int w = Math.max(font.width(l1), font.width(l2)) + 16;
			int y = 4;
			cadre(g, cx - w / 2, y, w, 24);
			g.centeredText(font, l1, cx, y + 4, OR);
			g.centeredText(font, l2, cx, y + 14, GRIS);
			return;
		}

		// En raid : chrono (le serveur envoie toutes les 5 ticks, on interpole entre deux).
		long ecoule = (System.currentTimeMillis() - recu) / 50;
		int t = (int) Math.max(0, restant - ecoule);
		int s = t / 20;
		String chrono = String.format("%d:%02d", s / 60, s % 60);
		int couleur = s < 120 ? ROUGE : s < 300 ? OR : BLANC;

		// Balise la plus proche.
		Carte.Point best = null;
		double bd = Double.MAX_VALUE;
		for (Carte.Point pt : Carte.SORTIES) {
			double dx = pt.x() + 0.5 - p.getX(), dz = pt.z() + 0.5 - p.getZ();
			double d = dx * dx + dz * dz;
			if (d < bd) {
				bd = d;
				best = pt;
			}
		}
		String fleche = "";
		String ligne2 = "";
		if (best != null) {
			double dx = best.x() + 0.5 - p.getX(), dz = best.z() + 0.5 - p.getZ();
			double cap = Math.toDegrees(Math.atan2(-dx, dz)); // lacet Minecraft de la direction
			double rel = ((cap - p.getYRot()) % 360 + 360 + 22.5) % 360;
			fleche = FLECHES[(int) (rel / 45) % 8];
			ligne2 = best.nom() + " · " + (int) Math.sqrt(bd) + " m";
		}
		int w = Math.max(150, font.width(ligne2) + 40);
		int y = 4;
		cadre(g, cx - w / 2, y, w, 26);
		g.text(font, Carte.NOM.toUpperCase(), cx - w / 2 + 7, y + 4, GRIS, false);
		g.text(font, chrono, cx + w / 2 - 7 - font.width(chrono), y + 4, couleur, true);
		g.text(font, fleche, cx - w / 2 + 7, y + 15, OR, true);
		g.text(font, ligne2, cx - w / 2 + 20, y + 15, BLANC, false);

		if (sortie > 0) {
			float f = Math.min(1f, sortie / (float) Payloads.RaidSync.SORTIE_TICKS);
			int bw = 140, bx = cx - bw / 2, by = y + 32;
			g.fill(bx - 2, by - 2, bx + bw + 2, by + 16, FOND);
			g.fill(bx, by + 10, bx + Math.round(bw * f), by + 13, VERT);
			g.fill(bx + Math.round(bw * f), by + 10, bx + bw, by + 13, 0x55FFFFFF);
			g.centeredText(font, "EXTRACTION " + Math.round(f * 100) + " % · reste dans le cercle", cx, by, VERT);
		}
	}

	private static void cadre(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, FOND);
		g.fill(x, y, x + w, y + 1, BORD);
		g.fill(x, y + h - 1, x + w, y + h, OR);
	}
}
