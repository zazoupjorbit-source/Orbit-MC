package games.orbit.orbitstaff.core.inspect;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Ouvre des vues d'inventaire et d'Ender Chest en lecture seule.
 *
 * <p>Par defaut, l'inspection est en lecture seule (section 10). La modification
 * eventuelle passera par un flux dedie (permission + confirmation + motif +
 * snapshot + journalisation) ajoute ulterieurement.</p>
 */
public final class InspectionService {

    private static final int INVENTORY_VIEW_SIZE = 45;
    private static final int ENDERCHEST_VIEW_SIZE = 27;

    public void openInventory(Player viewer, Player target) {
        openView(viewer, target.getInventory().getContents(), INVENTORY_VIEW_SIZE,
                Component.text("Inventaire : " + target.getName()));
    }

    public void openEnderChest(Player viewer, Player target) {
        openView(viewer, target.getEnderChest().getContents(), ENDERCHEST_VIEW_SIZE,
                Component.text("Ender Chest : " + target.getName()));
    }

    private void openView(Player viewer, ItemStack[] contents, int size, Component title) {
        InspectionHolder holder = new InspectionHolder();
        Inventory view = Bukkit.createInventory(holder, size, title);
        holder.setInventory(view);
        for (int i = 0; i < contents.length && i < size; i++) {
            view.setItem(i, contents[i]);
        }
        viewer.openInventory(view);
    }
}
