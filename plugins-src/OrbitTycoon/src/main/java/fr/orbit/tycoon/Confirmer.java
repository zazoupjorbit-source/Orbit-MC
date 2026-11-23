package fr.orbit.tycoon;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static fr.orbit.tycoon.Navigateur.texte;

/**
 * Petit menu « Confirmer / Annuler » (3 rangées) : passage de prestige, achat à l'HDV…
 * Confirmer (case 11), objet concerné (case 13), Annuler (case 15).
 */
final class Confirmer implements Listener {

    private static final class Menu implements InventoryHolder {
        private Inventory inv;
        private Runnable oui, non;
        @Override public Inventory getInventory() { return inv; }
    }

    private final OrbitTycoon plugin;

    Confirmer(OrbitTycoon plugin) {
        this.plugin = plugin;
    }

    /** @param non action à l'annulation (souvent : rouvrir le menu précédent), peut être null. */
    void ouvrir(Player p, String titre, ItemStack objet, List<String> details, Runnable oui, Runnable non) {
        Menu h = new Menu();
        h.oui = oui;
        h.non = non;
        Inventory inv = Bukkit.createInventory(h, 27, Habillage.titre(plugin, p, 3, titre));
        h.inv = inv;
        inv.setItem(11, bouton(Material.LIME_CONCRETE, "<#7CFC9A><bold>✔ Confirmer", details));
        inv.setItem(13, objet);
        inv.setItem(15, bouton(Material.RED_CONCRETE, "<#FF4D4D><bold>✘ Annuler", List.of("<gray>Revenir en arrière.")));
        Habillage.fond(plugin, p, inv);
        p.openInventory(inv);
    }

    private static ItemStack bouton(Material m, String nom, List<String> lore) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(texte(nom));
            meta.lore(lore.stream().map(Navigateur::texte).toList());
        });
        return it;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void clic(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getSlot() == 11) {
            p.closeInventory();
            h.oui.run();
        } else if (e.getSlot() == 15) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
            p.closeInventory();
            if (h.non != null) h.non.run();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void glisser(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
