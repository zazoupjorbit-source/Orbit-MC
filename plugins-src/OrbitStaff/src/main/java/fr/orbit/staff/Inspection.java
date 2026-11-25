package fr.orbit.staff;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inventaire et coffre de l'End d'un joueur, en direct (ce que le joueur bouge se voit tout de suite).
 * Lecture seule sauf avec orbitstaff.inspect.modifier ; toute modification est journalisée.
 */
public final class Inspection implements Listener {

    private record Vue(Inventory inv, String cible, boolean modifiable) {
    }

    private static final Map<UUID, Vue> vues = new ConcurrentHashMap<>();
    private final OrbitStaff plugin;

    public Inspection(OrbitStaff plugin) {
        this.plugin = plugin;
    }

    public static void inventaire(OrbitStaff plugin, Player staff, Player cible) {
        ouvrir(plugin, staff, cible, cible.getInventory(), "INVSEE");
    }

    public static void ender(OrbitStaff plugin, Player staff, Player cible) {
        ouvrir(plugin, staff, cible, cible.getEnderChest(), "ENDERSEE");
    }

    private static void ouvrir(OrbitStaff plugin, Player staff, Player cible, Inventory inv, String action) {
        if (!staff.hasPermission("orbitstaff.inspect")) {
            Txt.err(staff, "Permission manquante.");
            return;
        }
        boolean modif = staff.hasPermission("orbitstaff.inspect.modifier");
        staff.openInventory(inv);
        vues.put(staff.getUniqueId(), new Vue(inv, cible.getName(), modif));
        staff.sendActionBar(Txt.mm(modif ? "<#ff8c42>Modification possible (journalisée)" : "<gray>Lecture seule"));
        plugin.log(staff.getName(), action, cible.getName(), null);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        Vue v = vues.get(e.getWhoClicked().getUniqueId());
        if (v == null || e.getView().getTopInventory() != v.inv()) {
            return;
        }
        if (!v.modifiable()) {
            e.setCancelled(true);
            return;
        }
        if (e.getClickedInventory() == v.inv() && e.getCurrentItem() != null && !e.getCurrentItem().isEmpty()) {
            plugin.log(e.getWhoClicked().getName(), "INV_MODIF", v.cible(),
                    e.getCurrentItem().getType() + " x" + e.getCurrentItem().getAmount() + " (" + e.getClick() + ")");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        Vue v = vues.get(e.getWhoClicked().getUniqueId());
        if (v != null && e.getView().getTopInventory() == v.inv() && !v.modifiable()) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Vue v = vues.get(e.getPlayer().getUniqueId());
        if (v != null && e.getInventory() == v.inv()) {
            vues.remove(e.getPlayer().getUniqueId());
        }
    }
}
