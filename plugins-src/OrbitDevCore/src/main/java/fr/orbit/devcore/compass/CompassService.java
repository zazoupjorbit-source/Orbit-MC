package fr.orbit.devcore.compass;

import fr.orbit.devcore.OrbitDevCore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Création et détection de la boussole (marquée dans son PersistentDataContainer). */
public final class CompassService {

    public enum GiveResult { GIVEN, ALREADY, INVENTORY_FULL }

    private final OrbitDevCore plugin;
    private final NamespacedKey key;

    public CompassService(OrbitDevCore plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "teleport_compass");
    }

    public ItemStack create() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("compass.material", "COMPASS"));
        if (material == null || !material.isItem() || material.isAir()) {
            material = Material.COMPASS;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.messages().item("compass.name"));
        meta.lore(plugin.messages().itemLore("compass.lore"));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setEnchantmentGlintOverride(true); // effet enchanté, sans vrai enchantement
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isCompass(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Remplace les boussoles déjà possédées par la version actuelle (nom, lore, effet enchanté). */
    public void refresh(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isCompass(contents[i])) {
                inventory.setItem(i, create());
            }
        }
    }

    /** false : pas de boussole sur ce serveur (config compass.enabled, true seulement sur le lobby). */
    public boolean enabled() {
        return plugin.getConfig().getBoolean("compass.enabled", true);
    }

    /** Retire toutes les boussoles de l'inventaire (et du curseur). */
    public void removeAll(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isCompass(contents[i])) {
                inventory.setItem(i, null);
            }
        }
        if (isCompass(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    public boolean has(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isCompass(item)) {
                return true;
            }
        }
        return isCompass(player.getItemOnCursor());
    }

    public GiveResult give(Player player) {
        if (has(player)) {
            refresh(player);
            return GiveResult.ALREADY;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack compass = create();
        int slot = plugin.getConfig().getInt("compass.slot", 4);
        if (slot >= 0 && slot <= 8) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) {
                inventory.setItem(slot, compass);
                return GiveResult.GIVEN;
            }
        }
        return inventory.addItem(compass).isEmpty() ? GiveResult.GIVEN : GiveResult.INVENTORY_FULL;
    }
}
