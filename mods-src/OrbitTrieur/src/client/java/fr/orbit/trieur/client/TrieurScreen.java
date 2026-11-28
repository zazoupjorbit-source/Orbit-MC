package fr.orbit.trieur.client;

import fr.orbit.trieur.menu.TrieurMenu;
import fr.orbit.trieur.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Écran du tampon : on y dépose les objets à trier ; bouton vers le panel de réglages. */
public class TrieurScreen extends AbstractContainerScreen<TrieurMenu> {
	public TrieurScreen(TrieurMenu menu, Inventory inv, Component title) {
		super(menu, inv, title, 176, TrieurMenu.HAUTEUR);
		inventoryLabelY = TrieurMenu.HAUTEUR - 94;
	}

	@Override
	protected void init() {
		super.init();
		addRenderableWidget(Button.builder(Component.translatable("orbittrieur.bouton.reglages"),
						b -> ClientPlayNetworking.send(new Payloads.Demander(menu.pos())))
				.bounds(leftPos + imageWidth - 66, topPos + 3, 62, 13)
				.tooltip(Tooltip.create(Component.translatable("orbittrieur.bouton.reglages.aide")))
				.build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractBackground(g, mouseX, mouseY, delta);
		Couleurs.cadre(g, leftPos, topPos, imageWidth, imageHeight);
		int sep = topPos + TrieurMenu.Y_JOUEUR - 14;
		g.fill(leftPos + 7, sep, leftPos + imageWidth - 7, sep + 1, 0x552E6BD6);
		for (Slot s : menu.slots) {
			Couleurs.caseVide(g, leftPos + s.x - 1, topPos + s.y - 1);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(font, title, titleLabelX, titleLabelY, Couleurs.OR, false);
		g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Couleurs.GRIS, false);
	}
}
