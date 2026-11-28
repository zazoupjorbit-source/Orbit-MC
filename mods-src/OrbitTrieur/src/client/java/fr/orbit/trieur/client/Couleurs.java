package fr.orbit.trieur.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** DA Orbit MC : bleu nuit + or. */
final class Couleurs {
	private Couleurs() {
	}

	static final int FOND = 0xF20B1A33;
	static final int FOND_CLAIR = 0xFF13294D;
	static final int BORD = 0xFF2E6BD6;
	static final int OR = 0xFFF2C14E;
	static final int BLANC = 0xFFF0F0F0;
	static final int GRIS = 0xFF8A919B;
	static final int VERT = 0xFF5BD178;
	static final int ROUGE = 0xFFE74C3C;
	static final int CASE = 0xFF0A1428;
	static final int CASE_BORD = 0xFF35507E;

	/** Cadre du panel : fond, liseré bleu, barre d'or à gauche. */
	static void cadre(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, FOND);
		g.fill(x, y, x + w, y + 1, BORD);
		g.fill(x, y + h - 1, x + w, y + h, BORD);
		g.fill(x + w - 1, y, x + w, y + h, BORD);
		g.fill(x, y, x + 2, y + h, OR);
	}

	/** Case d'inventaire 18 × 18 (l'objet est dessiné en x+1, y+1). */
	static void caseVide(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x, y, x + 18, y + 18, CASE_BORD);
		g.fill(x + 1, y + 1, x + 17, y + 17, CASE);
	}
}
