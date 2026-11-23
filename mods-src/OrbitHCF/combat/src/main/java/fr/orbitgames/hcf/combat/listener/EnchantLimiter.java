package fr.orbitgames.hcf.combat.listener;

import fr.orbitgames.hcf.combat.OrbitHCFCombatPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Limites d'enchantements : table d'enchantement, enclume,
 * et nettoyage des objets ramasses ou presents dans l'inventaire.
 */
public class EnchantLimiter implements Listener {

    private final OrbitHCFCombatPlugin plugin;
    /** Limites par enchantement : 0 = interdit, -1 = illimite. */
    private final Map<Enchantment, Integer> limits = new HashMap<>();

    public EnchantLimiter(OrbitHCFCombatPlugin plugin) {
        this.plugin = plugin;
        var section = plugin.getEnchantmentsConfig().getConfigurationSection("limits");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String mcKey = key.toLowerCase(Locale.ROOT).replace('-', '_');
                Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(mcKey));
                if (enchantment != null) {
                    limits.put(enchantment, section.getInt(key));
                } else {
                    plugin.getLogger().warning("Enchantement inconnu dans enchantments.yml : " + key);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Iterator<Map.Entry<Enchantment, Integer>> iterator = event.getEnchantsToAdd().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Enchantment, Integer> entry = iterator.next();
            Integer limit = limits.get(entry.getKey());
            if (limit == null || limit < 0) {
                continue;
            }
            if (limit == 0) {
                iterator.remove();
            } else if (entry.getValue() > limit) {
                entry.setValue(limit);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result != null && clampItem(result)) {
            event.setResult(result);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        clampInventory(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            clampInventory(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            ItemStack stack = event.getItem().getItemStack();
            if (clampItem(stack)) {
                event.getItem().setItemStack(stack);
            }
        }
    }

    private void clampInventory(Player player) {
        boolean changed = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && clampItem(item)) {
                changed = true;
            }
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && clampItem(item)) {
                changed = true;
            }
        }
        if (changed) {
            player.updateInventory();
        }
    }

    /** Applique les limites a un objet. Retourne vrai si l'objet a ete modifie. */
    public boolean clampItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        boolean changed = false;

        for (Map.Entry<Enchantment, Integer> entry : new HashMap<>(meta.getEnchants()).entrySet()) {
            Integer limit = limits.get(entry.getKey());
            if (limit == null || limit < 0 || entry.getValue() <= limit) {
                continue;
            }
            meta.removeEnchant(entry.getKey());
            if (limit > 0) {
                meta.addEnchant(entry.getKey(), limit, true);
            }
            changed = true;
        }

        // Livres enchantes
        if (meta instanceof EnchantmentStorageMeta storage) {
            for (Map.Entry<Enchantment, Integer> entry : new HashMap<>(storage.getStoredEnchants()).entrySet()) {
                Integer limit = limits.get(entry.getKey());
                if (limit == null || limit < 0 || entry.getValue() <= limit) {
                    continue;
                }
                storage.removeStoredEnchant(entry.getKey());
                if (limit > 0) {
                    storage.addStoredEnchant(entry.getKey(), limit, true);
                }
                changed = true;
            }
        }

        if (changed) {
            item.setItemMeta(meta);
        }
        return changed;
    }
}
