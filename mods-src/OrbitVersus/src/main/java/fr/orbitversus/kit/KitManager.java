package fr.orbitversus.kit;

import fr.orbitversus.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Kits de combat + menu de selection (avant chaque duel). */
public final class KitManager implements Listener {

    public record Kit(String id, String display, Material icon, Consumer<Player> equip) {}

    private static final class KitMenuHolder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private final NamespacedKey key;
    private final LinkedHashMap<String, Kit> kits = new LinkedHashMap<>();
    private final Map<UUID, String> chosen = new HashMap<>();

    public KitManager(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "kit_id");
        register();
    }

    private ItemStack ench(ItemStack it, Enchantment e, int lvl) { it.addUnsafeEnchantment(e, lvl); return it; }
    private ItemStack amount(Material m, int n) { return new ItemStack(m, n); }
    private void clearPlayer(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(20); p.setFoodLevel(20); p.setSaturation(20f); p.setFireTicks(0);
    }
    private ItemStack splashHeal() {
        ItemStack it = new ItemStack(Material.SPLASH_POTION);
        PotionMeta m = (PotionMeta) it.getItemMeta();
        if (m != null) { m.setBasePotionType(PotionType.STRONG_HEALING); it.setItemMeta(m); }
        return it;
    }
    private void diamondArmor(Player p) {
        p.getInventory().setHelmet(ench(new ItemStack(Material.DIAMOND_HELMET), Enchantment.PROTECTION, 1));
        p.getInventory().setChestplate(ench(new ItemStack(Material.DIAMOND_CHESTPLATE), Enchantment.PROTECTION, 1));
        p.getInventory().setLeggings(ench(new ItemStack(Material.DIAMOND_LEGGINGS), Enchantment.PROTECTION, 1));
        p.getInventory().setBoots(ench(new ItemStack(Material.DIAMOND_BOOTS), Enchantment.PROTECTION, 1));
    }

    private void register() {
        kits.put("diamant", new Kit("diamant", "Diamant", Material.DIAMOND_SWORD, p -> {
            clearPlayer(p); diamondArmor(p);
            p.getInventory().setItem(0, ench(new ItemStack(Material.DIAMOND_SWORD), Enchantment.SHARPNESS, 2));
            p.getInventory().setItem(1, amount(Material.GOLDEN_APPLE, 8));
            p.getInventory().setItem(2, amount(Material.COOKED_BEEF, 16));
        }));
        kits.put("nodebuff", new Kit("nodebuff", "NoDebuff", Material.SPLASH_POTION, p -> {
            clearPlayer(p); diamondArmor(p);
            p.getInventory().setItem(0, ench(new ItemStack(Material.DIAMOND_SWORD), Enchantment.SHARPNESS, 1));
            p.getInventory().setItem(1, amount(Material.GOLDEN_APPLE, 16));
            for (int i = 2; i < 9; i++) p.getInventory().setItem(i, splashHeal());
        }));
        kits.put("archer", new Kit("archer", "Archer", Material.BOW, p -> {
            clearPlayer(p); diamondArmor(p);
            p.getInventory().setItem(0, ench(new ItemStack(Material.DIAMOND_SWORD), Enchantment.SHARPNESS, 1));
            ItemStack bow = ench(new ItemStack(Material.BOW), Enchantment.POWER, 2);
            ench(bow, Enchantment.INFINITY, 1);
            p.getInventory().setItem(1, bow);
            p.getInventory().setItem(2, amount(Material.GOLDEN_APPLE, 8));
            p.getInventory().setItem(8, amount(Material.ARROW, 1));
        }));
        kits.put("classique", new Kit("classique", "Classique", Material.IRON_SWORD, p -> {
            clearPlayer(p);
            p.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
            p.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            p.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            p.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
            p.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD));
            p.getInventory().setItem(1, amount(Material.COOKED_BEEF, 32));
            p.getInventory().setItem(2, amount(Material.COBBLESTONE, 64));
        }));
    }

    public void openMenu(Player p) {
        KitMenuHolder h = new KitMenuHolder();
        Inventory inv = Bukkit.createInventory(h, 27, Msg.raw("<gold><bold>Choisis ton kit"));
        h.inv = inv;
        int slot = 11;
        for (Kit k : kits.values()) {
            ItemStack icon = new ItemStack(k.icon());
            ItemMeta m = icon.getItemMeta();
            if (m != null) {
                m.displayName(Msg.raw("<green><bold>" + k.display()));
                m.getPersistentDataContainer().set(key, PersistentDataType.STRING, k.id());
                icon.setItemMeta(m);
            }
            if (slot < 27) inv.setItem(slot, icon);
            slot += 2;
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof KitMenuHolder)) return;
        e.setCancelled(true);
        ItemStack it = e.getCurrentItem();
        if (it == null || it.getItemMeta() == null) return;
        String id = it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null || !kits.containsKey(id)) return;
        Player p = (Player) e.getWhoClicked();
        chosen.put(p.getUniqueId(), id);
        p.sendMessage(Msg.msg("<green>Kit selectionne : <white>" + kits.get(id).display()));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.4f);
        p.closeInventory();
    }

    /** Applique le kit choisi (ou le 1er par defaut) et ferme le menu. */
    public void applyChosen(Player p) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof KitMenuHolder) p.closeInventory();
        String id = chosen.get(p.getUniqueId());
        Kit k = (id != null) ? kits.get(id) : null;
        if (k == null) k = kits.values().iterator().next();
        k.equip().accept(p);
    }

    public void clearChoice(UUID u) { chosen.remove(u); }
}
