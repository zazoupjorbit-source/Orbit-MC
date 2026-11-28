package fr.orbit.trieur.client;

import fr.orbit.trieur.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Panel de réglages d'un trieur : marche/arrêt, mode « regrouper », rayon, et pour chaque inventaire détecté
 * son filtre, le bouton « surplus » et « remplir avec le contenu actuel ».
 */
public class ReglagesScreen extends Screen {
	private static final int LIGNE = 24;
	private static final int ENTETE = 64;

	/** Une ligne de la liste, modifiable (les valeurs survivent au défilement). */
	private static final class Ligne {
		final Payloads.Cible cible;
		String filtre;

		Ligne(Payloads.Cible c) {
			cible = c;
			filtre = c.filtre();
		}
	}

	private Payloads.Reglages data;
	private boolean actif;
	private boolean regrouper;
	private int rayon;
	private BlockPos surplus;
	private final List<Ligne> lignes = new ArrayList<>();
	private int defilement;

	private int x0;
	private int y0;
	private int w;
	private int h;

	public ReglagesScreen(Payloads.Reglages data) {
		super(Component.translatable("orbittrieur.panel.titre"));
		charger(data);
	}

	public BlockPos pos() {
		return data.pos();
	}

	private void charger(Payloads.Reglages d) {
		data = d;
		actif = d.actif();
		regrouper = d.regrouper();
		rayon = d.rayon();
		surplus = null;
		lignes.clear();
		for (Payloads.Cible c : d.cibles()) {
			lignes.add(new Ligne(c));
			if (c.surplus()) {
				surplus = c.pos();
			}
		}
	}

	/** Le serveur a renvoyé l'état (après « Appliquer ») : on recharge sans fermer. */
	public void recharger(Payloads.Reglages d) {
		charger(d);
		defilement = Math.min(defilement, maxDefilement());
		rebuildWidgets();
	}

	private int lignesVisibles() {
		return Math.max(1, (h - ENTETE - 34) / LIGNE);
	}

	private int maxDefilement() {
		return Math.max(0, lignes.size() - lignesVisibles());
	}

	@Override
	protected void init() {
		w = Math.min(380, width - 16);
		h = Math.min(height - 16, ENTETE + 34 + LIGNE * Math.max(3, Math.min(lignes.size(), 9)));
		x0 = (width - w) / 2;
		y0 = (height - h) / 2;

		// Ligne d'options
		int bx = x0 + 8;
		int by = y0 + 20;
		addRenderableWidget(Button.builder(onOff("orbittrieur.panel.actif", actif), b -> {
			actif = !actif;
			b.setMessage(onOff("orbittrieur.panel.actif", actif));
		}).bounds(bx, by, 92, 18).tooltip(Tooltip.create(Component.translatable("orbittrieur.panel.actif.aide"))).build());
		addRenderableWidget(Button.builder(onOff("orbittrieur.panel.regrouper", regrouper), b -> {
			regrouper = !regrouper;
			b.setMessage(onOff("orbittrieur.panel.regrouper", regrouper));
		}).bounds(bx + 96, by, 110, 18).tooltip(Tooltip.create(Component.translatable("orbittrieur.panel.regrouper.aide"))).build());
		int rx = bx + 212;
		addRenderableWidget(Button.builder(Component.literal("-"), b -> rayon = Math.max(1, rayon - pas()))
				.bounds(rx, by, 18, 18).tooltip(Tooltip.create(Component.translatable("orbittrieur.panel.rayon.aide",
						data.rayonMax()))).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> rayon = Math.min(data.rayonMax(), rayon + pas()))
				.bounds(rx + 68, by, 18, 18).tooltip(Tooltip.create(Component.translatable("orbittrieur.panel.rayon.aide",
						data.rayonMax()))).build());

		// Liste des inventaires
		defilement = Math.clamp(defilement, 0, maxDefilement());
		int ly = y0 + ENTETE;
		int filtreX = x0 + 132;
		int filtreW = w - 132 - 60;
		for (int i = 0; i < lignesVisibles() && defilement + i < lignes.size(); i++) {
			Ligne l = lignes.get(defilement + i);
			int y = ly + i * LIGNE;
			EditBox box = new EditBox(font, filtreX, y + 2, filtreW, 18, Component.translatable("orbittrieur.panel.filtre"));
			box.setMaxLength(Payloads.MAX_FILTRE);
			box.setValue(l.filtre);
			box.setHint(Component.translatable("orbittrieur.panel.filtre.vide"));
			box.setResponder(v -> l.filtre = v);
			addRenderableWidget(box);

			boolean estSurplus = l.cible.pos().equals(surplus);
			addRenderableWidget(Button.builder(Component.literal(estSurplus ? "§6S" : "S"), b -> {
				surplus = l.cible.pos().equals(surplus) ? null : l.cible.pos();
				rebuildWidgets();
			}).bounds(filtreX + filtreW + 4, y + 2, 22, 18)
					.tooltip(Tooltip.create(Component.translatable("orbittrieur.panel.surplus.aide"))).build());

			Button remplir = Button.builder(Component.literal("+"), b -> {
				String s = String.join(", ", l.cible.contenu());
				l.filtre = s.length() > Payloads.MAX_FILTRE ? s.substring(0, Payloads.MAX_FILTRE) : s;
				rebuildWidgets();
			}).bounds(filtreX + filtreW + 28, y + 2, 22, 18)
					.tooltip(Tooltip.create(Component.translatable("orbittrieur.panel.remplir.aide"))).build();
			remplir.active = !l.cible.contenu().isEmpty();
			addRenderableWidget(remplir);
		}

		// Pied
		int py = y0 + h - 26;
		int pw = (w - 24) / 3;
		addRenderableWidget(Button.builder(Component.translatable("orbittrieur.panel.appliquer"), b -> envoyer(true))
				.bounds(x0 + 8, py, pw, 20)
				.tooltip(Tooltip.create(Component.translatable("orbittrieur.panel.appliquer.aide"))).build());
		addRenderableWidget(Button.builder(Component.translatable("orbittrieur.panel.enregistrer"), b -> {
			envoyer(false);
			onClose();
		}).bounds(x0 + 12 + pw, py, pw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("orbittrieur.panel.annuler"), b -> onClose())
				.bounds(x0 + 16 + pw * 2, py, pw, 20).build());
	}

	/** Pas du rayon : 1 bloc, 8 avec Maj. Molette sur le rayon : idem. */
	private int pas() {
		return minecraft.hasShiftDown() ? 8 : 1;
	}

	private static Component onOff(String cle, boolean v) {
		return Component.translatable(cle).append(": ").append(Component.translatable(v ? "options.on" : "options.off"));
	}

	private void envoyer(boolean rescan) {
		List<Payloads.Regle> regles = new ArrayList<>();
		for (Ligne l : lignes) {
			if (!l.filtre.isBlank()) {
				regles.add(new Payloads.Regle(l.cible.pos(), l.filtre.strip()));
			}
		}
		ClientPlayNetworking.send(new Payloads.Enregistrer(data.pos(), actif, regrouper, rayon, regles,
				Optional.ofNullable(surplus), rescan));
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		int rx = x0 + 8 + 212;
		if (mx >= rx && mx < rx + 86 && my >= y0 + 20 && my < y0 + 38 && sy != 0) {
			rayon = Math.clamp(rayon + (int) Math.signum(sy) * pas(), 1, data.rayonMax());
			return true;
		}
		int avant = defilement;
		defilement = Math.clamp(defilement - (int) Math.signum(sy), 0, maxDefilement());
		if (avant != defilement) {
			rebuildWidgets();
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractBackground(g, mouseX, mouseY, delta);
		Couleurs.cadre(g, x0, y0, w, h);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.text(font, title, x0 + 8, y0 + 7, Couleurs.OR, false);
		String etat = data.enAttente() > 0
				? Component.translatable("orbittrieur.panel.attente", data.enAttente()).getString()
				: Component.translatable("orbittrieur.panel.vide").getString();
		g.text(font, etat, x0 + w - 8 - font.width(etat), y0 + 7, data.enAttente() > 0 ? Couleurs.OR : Couleurs.GRIS, false);

		// Rayon (entre les boutons - et +)
		String r = Component.translatable("orbittrieur.panel.rayon", rayon).getString();
		int rx = x0 + 8 + 212 + 18;
		g.text(font, r, rx + 25 - font.width(r) / 2, y0 + 25, rayon != data.rayon() ? Couleurs.OR : Couleurs.BLANC, false);

		g.text(font, Component.translatable("orbittrieur.panel.syntaxe").getString(), x0 + 8, y0 + 44, Couleurs.GRIS, false);
		g.fill(x0 + 6, y0 + ENTETE - 6, x0 + w - 6, y0 + ENTETE - 5, 0x552E6BD6);

		int ly = y0 + ENTETE;
		if (lignes.isEmpty()) {
			g.text(font, Component.translatable("orbittrieur.panel.aucun", data.rayon()).getString(), x0 + 10, ly + 8,
					Couleurs.GRIS, false);
		}
		for (int i = 0; i < lignesVisibles() && defilement + i < lignes.size(); i++) {
			Ligne l = lignes.get(defilement + i);
			int y = ly + i * LIGNE;
			Couleurs.caseVide(g, x0 + 7, y + 2);
			g.item(l.cible.icone(), x0 + 8, y + 3);
			boolean estSurplus = l.cible.pos().equals(surplus);
			int couleurNom = estSurplus ? Couleurs.OR : (l.filtre.isBlank() ? Couleurs.BLANC : Couleurs.VERT);
			g.text(font, font.plainSubstrByWidth(l.cible.nom(), 100), x0 + 29, y + 3, couleurNom, false);
			BlockPos d = l.cible.pos().subtract(data.pos());
			String coord = d.getX() + " " + d.getY() + " " + d.getZ() + (estSurplus ? "  surplus" : "");
			g.text(font, coord, x0 + 29, y + 13, Couleurs.GRIS, false);
		}
		if (maxDefilement() > 0) {
			// Barre de défilement à droite de la liste
			int haut = lignesVisibles() * LIGNE;
			int curseur = Math.max(10, haut * lignesVisibles() / lignes.size());
			int cy = ly + (haut - curseur) * defilement / maxDefilement();
			g.fill(x0 + w - 5, ly, x0 + w - 3, ly + haut, 0x552E6BD6);
			g.fill(x0 + w - 5, cy, x0 + w - 3, cy + curseur, Couleurs.OR);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
