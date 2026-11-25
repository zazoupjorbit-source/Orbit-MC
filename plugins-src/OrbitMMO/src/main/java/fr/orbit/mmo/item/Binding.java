package fr.orbit.mmo.item;

import fr.orbit.mmo.Keys;
import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.ui.Menu;
import fr.orbit.mmo.util.Text;
import org.bukkit.Sound;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Liaison des objets : « lié quand ramassé » (butin de boss, sets de boss) ou « lié quand équipé » (le reste).
 * Un objet lié appartient à son propriétaire (clé OWNER) : il ne se met pas en vente à l'hôtel des ventes,
 * ne se jette pas, ne se pose pas dans un coffre, un cadre ou sur un support d'armure. Le forgeron le rachète.
 * <p>
 * Vérifie aussi l'équipement porté (à chaque changement) : type d'armure de la classe, liaison à l'équipement,
 * apparences débloquées pour la garde-robe.
 */
public final class Binding implements Listener {

    public static final String PICKUP = "ramasse";
    public static final String EQUIP = "equipe";

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final OrbitMMO plugin;

    public Binding(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ Lecture

    /** « ramasse », « equipe » ou null (objet qui ne se lie pas). */
    public static String kind(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(Keys.BIND, PersistentDataType.STRING);
    }

    /** Équipement du MMO lié à un joueur. */
    public static boolean isBound(ItemStack it) {
        return kind(it) != null && Items.owner(it) != null;
    }

    /** Ligne de description : « Lié quand équipé », « Lié · Pseudo »… (null : rien à afficher). */
    public static String line(ItemStack it) {
        String k = kind(it);
        if (k == null) {
            return null;
        }
        if (Items.owner(it) != null) {
            String name = it.getItemMeta().getPersistentDataContainer().get(Keys.BOUND_NAME, PersistentDataType.STRING);
            return "<#B36BFF>Lié" + (name == null ? "" : " · " + Text.esc(name)) + " <#5A6270>(ni vendu à l'HDV ni échangé)";
        }
        return PICKUP.equals(k) ? "<#D7DCE2>Lié quand ramassé" : "<#D7DCE2>Lié quand équipé";
    }

    /** Raison pour laquelle l'objet ne peut pas être échangé (null : il peut l'être). */
    public static String tradeBlock(ItemStack it) {
        return isBound(it) ? "Cet objet est lié à " + ownerName(it) + " : il ne se vend pas à l'hôtel des ventes et ne s'échange pas." : null;
    }

    private static String ownerName(ItemStack it) {
        String name = it.getItemMeta().getPersistentDataContainer().get(Keys.BOUND_NAME, PersistentDataType.STRING);
        return name == null ? "son propriétaire" : name;
    }

    // ------------------------------------------------------------------ Écriture

    /** Lie l'objet au joueur (sans effet s'il ne se lie pas ou l'est déjà). */
    public static boolean bind(ItemStack it, Player p) {
        if (kind(it) == null || Items.owner(it) != null || p == null) {
            return false;
        }
        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.OWNER, PersistentDataType.STRING, p.getUniqueId().toString());
        pdc.set(Keys.BOUND_NAME, PersistentDataType.STRING, p.getName());
        it.setItemMeta(meta);
        Loot.refresh(it);
        return true;
    }

    /** Butin reçu : un objet « lié quand ramassé » se lie tout de suite. */
    public static void onLoot(ItemStack it, Player p) {
        if (PICKUP.equals(kind(it))) {
            bind(it, p);
        }
    }

    // ------------------------------------------------------------------ Équipement porté

    /**
     * À chaque changement d'équipement : retire les pièces d'un autre type d'armure que celui de la classe,
     * lie ce qui est « lié quand équipé », débloque les apparences portées (garde-robe).
     */
    public void checkWorn(Player p, PlayerData d) {
        PlayerInventory inv = p.getInventory();
        ArmorType allowed = ArmorType.of(plugin.progression().cls(d));
        for (EquipmentSlot es : ARMOR) {
            ItemStack it = inv.getItem(es);
            if (it == null || it.isEmpty()) {
                continue;
            }
            boolean changed = Loot.adopt(it);
            changed |= Loot.upgrade(it, p);
            ArmorType type = Loot.armorType(it);
            if (type != null && allowed != null && type != allowed) {
                inv.setItem(es, null);
                Items.give(p, it);
                plugin.hud().flash(p, "<#FF8A3B>Votre classe porte l'armure de " + allowed.tag().toLowerCase(java.util.Locale.ROOT)
                        + "<#FF8A3B> : la pièce en " + type.label.toLowerCase(java.util.Locale.ROOT) + " revient dans le sac.", 60);
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
                continue;
            }
            changed |= bindOnEquip(p, it);
            if (type != null) {
                Wardrobe.unlock(d, it);
            }
            if (changed) {
                inv.setItem(es, it);
            }
        }
        ItemStack off = inv.getItemInOffHand();
        if ("shield".equals(Items.type(off))) {
            boolean changed = Loot.upgrade(off, p);
            changed |= bindOnEquip(p, off);
            if (changed) {
                inv.setItemInOffHand(off);
            }
        }
        ItemStack hand = inv.getItemInMainHand();
        if ("weapon".equals(Items.type(hand))) {
            boolean changed = Loot.adopt(hand);
            changed |= Loot.upgrade(hand, p);
            changed |= bindOnEquip(p, hand);
            if (changed) {
                inv.setItemInMainHand(hand);
            }
        } else {
            int slot = plugin.bar().weaponSlot(p, d);
            ItemStack w = slot < 0 ? null : inv.getItem(slot);
            if ("weapon".equals(Items.type(w))) {
                boolean changed = Loot.upgrade(w, p);
                changed |= bindOnEquip(p, w);
                if (changed) {
                    inv.setItem(slot, w);
                }
            }
        }
    }

    private boolean bindOnEquip(Player p, ItemStack it) {
        if (!EQUIP.equals(kind(it)) || Items.owner(it) != null) {
            return false;
        }
        bind(it, p);
        plugin.hud().flash(p, "<#B36BFF>Objet lié : <#D7DCE2>il ne pourra plus être vendu à l'HDV ni échangé.", 50);
        return true;
    }

    // ------------------------------------------------------------------ Protections

    private void refuse(Player p, ItemStack it) {
        plugin.hud().flash(p, "<#FF5C5C>" + tradeBlock(it), 50);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack it = e.getItemDrop().getItemStack();
        if (isBound(it)) {
            e.setCancelled(true);
            refuse(e.getPlayer(), it);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        ItemStack it = e.getItem().getItemStack();
        if (!isBound(it)) {
            return;
        }
        UUID owner = Items.owner(it);
        if (!(e.getEntity() instanceof Player p) || !p.getUniqueId().equals(owner)) {
            e.setCancelled(true);
        }
    }

    /** Inventaire du haut où l'on pourrait déposer un objet pour quelqu'un d'autre (coffre, tonneau, entonnoir…). */
    private static boolean foreign(Inventory top) {
        if (top == null || top.getHolder() instanceof Menu) {
            return false;
        }
        InventoryType t = top.getType();
        return t != InventoryType.CRAFTING && t != InventoryType.PLAYER && t != InventoryType.CREATIVE
                && t != InventoryType.WORKBENCH && t != InventoryType.ENCHANTING && t != InventoryType.ANVIL
                && t != InventoryType.SMITHING && t != InventoryType.GRINDSTONE && t != InventoryType.ENDER_CHEST;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        Inventory top = e.getView().getTopInventory();
        if (!foreign(top)) {
            return;
        }
        boolean intoTop = e.getClickedInventory() == top;
        ItemStack moving = null;
        if (intoTop) {
            moving = e.getCursor();
            if (e.getClick().isKeyboardClick() && e.getHotbarButton() >= 0) {
                moving = p.getInventory().getItem(e.getHotbarButton());
            }
        } else if (e.isShiftClick()) {
            moving = e.getCurrentItem();
        }
        if (moving != null && isBound(moving)) {
            e.setCancelled(true);
            refuse(p, moving);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !isBound(e.getOldCursor())) {
            return;
        }
        Inventory top = e.getView().getTopInventory();
        if (!foreign(top)) {
            return;
        }
        int size = top.getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < size) {
                e.setCancelled(true);
                refuse(p, e.getOldCursor());
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (isBound(e.getPlayerItem())) {
            e.setCancelled(true);
            refuse(e.getPlayer(), e.getPlayerItem());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrame(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof ItemFrame)) {
            return;
        }
        ItemStack it = e.getHand() == EquipmentSlot.OFF_HAND ? e.getPlayer().getInventory().getItemInOffHand()
                : e.getPlayer().getInventory().getItemInMainHand();
        if (isBound(it)) {
            e.setCancelled(true);
            refuse(e.getPlayer(), it);
        }
    }
}
