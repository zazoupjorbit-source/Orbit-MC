package fr.orbit.tycoon;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Objets liés au joueur (Navigateur orbital, Outil orbital) : ils restent à leur emplacement,
 * ne peuvent être ni jetés, ni rangés, ni donnés, ni posés, et ne sont jamais perdus à la mort.
 */
final class Liens implements Listener {
    private final NamespacedKey cle;

    Liens(OrbitTycoon plugin) {
        this.cle = new NamespacedKey(plugin, "lie");
    }

    void marquer(ItemMeta meta) {
        meta.getPersistentDataContainer().set(cle, PersistentDataType.BYTE, (byte) 1);
    }

    boolean lie(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(cle, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void clic(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        boolean touche = lie(e.getCurrentItem()) || lie(e.getCursor());
        if (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0) touche |= lie(p.getInventory().getItem(e.getHotbarButton()));
        if (e.getClick() == ClickType.SWAP_OFFHAND) touche |= lie(p.getInventory().getItemInOffHand());
        if (touche) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void glisser(InventoryDragEvent e) {
        if (lie(e.getOldCursor())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void jeter(PlayerDropItemEvent e) {
        if (lie(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void mainSecondaire(PlayerSwapHandItemsEvent e) {
        if (lie(e.getMainHandItem()) || lie(e.getOffHandItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void entite(PlayerInteractEntityEvent e) {
        // Cadres, allays, supports d'armure... (l'épée frappe toujours : c'est une attaque, pas une interaction).
        if (lie(e.getPlayer().getInventory().getItem(e.getHand()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void entiteAt(PlayerInteractAtEntityEvent e) {
        if (lie(e.getPlayer().getInventory().getItem(e.getHand()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void poser(BlockPlaceEvent e) {
        if (lie(e.getItemInHand())) e.setCancelled(true);
    }

    @EventHandler
    public void mort(PlayerDeathEvent e) {
        e.getDrops().removeIf(this::lie);
        e.getItemsToKeep().removeIf(this::lie);
    }
}
