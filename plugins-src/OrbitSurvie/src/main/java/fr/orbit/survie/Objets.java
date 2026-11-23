package fr.orbit.survie;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Outils sur les objets des joueurs. */
public final class Objets {

    private Objets() {}

    /** Donne, et pose au sol ce qui ne rentre pas. */
    public static void donner(Player p, ItemStack item) {
        int max = item.getMaxStackSize();
        int reste = item.getAmount();
        while (reste > 0) {
            ItemStack part = item.clone();
            part.setAmount(Math.min(max, reste));
            reste -= part.getAmount();
            p.getInventory().addItem(part).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
        }
    }

    /** Combien d'exemplaires de cet objet rentrent encore dans l'inventaire (hors armure et main gauche). */
    public static int place(Player p, ItemStack modele) {
        int max = modele.getMaxStackSize();
        int place = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) {
                place += max;
            } else if (it.isSimilar(modele)) {
                place += Math.max(0, max - it.getAmount());
            }
        }
        return place;
    }

    /** Nombre d'exemplaires « neufs » (identiques au modèle) dans l'inventaire. */
    public static int compter(Player p, ItemStack modele) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.isSimilar(modele)) {
                n += it.getAmount();
            }
        }
        return n;
    }

    /** Retire jusqu'à {@code max} exemplaires identiques au modèle ; renvoie le nombre retiré. */
    public static int retirer(Player p, ItemStack modele, int max) {
        int retires = 0;
        ItemStack[] contenu = p.getInventory().getStorageContents();
        for (int i = 0; i < contenu.length && retires < max; i++) {
            ItemStack it = contenu[i];
            if (it == null || !it.isSimilar(modele)) {
                continue;
            }
            int pris = Math.min(it.getAmount(), max - retires);
            retires += pris;
            if (pris == it.getAmount()) {
                contenu[i] = null;
            } else {
                it.setAmount(it.getAmount() - pris);
            }
        }
        if (retires > 0) {
            p.getInventory().setStorageContents(contenu);
        }
        return retires;
    }
}
