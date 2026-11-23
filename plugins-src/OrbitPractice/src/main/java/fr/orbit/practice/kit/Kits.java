package fr.orbit.practice.kit;

import fr.orbit.practice.Ladder;
import fr.orbit.practice.Msg;
import org.bukkit.Bukkit;
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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Contenu des kits et menu de choix de la classe HCF (ouvert pendant le décompte de la 1re manche). */
public final class Kits implements Listener {

    private static final class ClassMenu implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final NamespacedKey classKey;
    private final Map<UUID, HcfClass> chosen = new HashMap<>();

    public Kits(Plugin plugin) {
        this.classKey = new NamespacedKey(plugin, "classe");
    }

    // ------------------------------------------------------------------ Choix de la classe

    public void openClassMenu(Player p) {
        ClassMenu holder = new ClassMenu();
        Inventory inv = Bukkit.createInventory(holder, 27, Msg.raw("<dark_gray>Choisis ta classe"));
        holder.inventory = inv;
        int slot = 10;
        for (HcfClass c : HcfClass.values()) {
            ItemStack icon = new ItemStack(c.icon);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Msg.item(Msg.OR + "<bold>" + c.label));
            meta.lore(List.of(Msg.item("<gray>" + c.description)));
            meta.getPersistentDataContainer().set(classKey, PersistentDataType.STRING, c.name());
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            slot += 2;
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ClassMenu)) {
            return;
        }
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || !(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(classKey, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        HcfClass c = HcfClass.valueOf(id);
        chosen.put(p.getUniqueId(), c);
        p.sendMessage(Msg.msg("Classe choisie : " + Msg.OR + c.label + "<gray>."));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.4f);
        p.closeInventory();
    }

    public void closeClassMenu(Player p) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof ClassMenu) {
            p.closeInventory();
        }
    }

    public HcfClass chosen(UUID uuid) {
        return chosen.getOrDefault(uuid, HcfClass.DIAMOND);
    }

    public void clearChoice(UUID uuid) {
        chosen.remove(uuid);
    }

    // ------------------------------------------------------------------ Équipement

    /** Vide l'inventaire puis donne le kit du type de combat (ou de la classe choisie en HCF). */
    public void equip(Player p, Ladder ladder) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        // Combo : quasiment pas de temps d'invincibilité entre deux coups.
        p.setMaximumNoDamageTicks(ladder.rule == Ladder.Rule.COMBO ? 3 : 20);
        if (ladder.rule == Ladder.Rule.BUILD) {
            p.setGameMode(org.bukkit.GameMode.SURVIVAL); // l'aventure interdit de poser des blocs
        }
        if (ladder != Ladder.NODEBUFF && ladder != Ladder.DEBUFF && ladder != Ladder.HCF) {
            otherKit(p, ladder);
            p.updateInventory();
            return;
        }
        if (ladder == Ladder.NODEBUFF || ladder == Ladder.DEBUFF) {
            armor(p, "DIAMOND", 2);
            inv.setItem(0, sword(Material.DIAMOND_SWORD, 2));
            inv.setItem(1, new ItemStack(Material.ENDER_PEARL, 16));
            inv.setItem(8, new ItemStack(Material.COOKED_BEEF, 64));
            speedColumn(inv);
            if (ladder == Ladder.DEBUFF) {
                inv.setItem(2, potion(Material.SPLASH_POTION, PotionType.STRONG_POISON));
                inv.setItem(3, potion(Material.SPLASH_POTION, PotionType.STRONG_SLOWNESS));
                inv.setItem(16, potion(Material.SPLASH_POTION, PotionType.STRONG_POISON));
                inv.setItem(25, potion(Material.SPLASH_POTION, PotionType.STRONG_SLOWNESS));
                inv.setItem(34, potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE));
            }
        } else {
            HcfClass c = chosen(p.getUniqueId());
            armor(p, c.armor);
            inv.setItem(1, new ItemStack(Material.ENDER_PEARL, 16));
            inv.setItem(8, new ItemStack(Material.COOKED_BEEF, 64));
            switch (c) {
                case DIAMOND -> {
                    inv.setItem(0, sword(Material.DIAMOND_SWORD, 2));
                    speedColumn(inv);
                }
                case BARD -> {
                    inv.setItem(0, sword(Material.DIAMOND_SWORD, 1));
                    inv.setItem(2, new ItemStack(Material.BLAZE_POWDER, 32));
                    inv.setItem(3, new ItemStack(Material.SUGAR, 32));
                    inv.setItem(4, new ItemStack(Material.IRON_INGOT, 16));
                    inv.setItem(5, new ItemStack(Material.GHAST_TEAR, 16));
                    inv.setItem(6, new ItemStack(Material.FEATHER, 16));
                    inv.setItem(7, new ItemStack(Material.SPIDER_EYE, 8));
                }
                case ARCHER -> {
                    inv.setItem(0, sword(Material.DIAMOND_SWORD, 1));
                    ItemStack bow = new ItemStack(Material.BOW);
                    bow.addUnsafeEnchantment(Enchantment.POWER, 3);
                    bow.addUnsafeEnchantment(Enchantment.INFINITY, 1);
                    unbreakable(bow);
                    inv.setItem(2, bow);
                    inv.setItem(9, new ItemStack(Material.ARROW, 1));
                }
                case ROGUE -> {
                    inv.setItem(0, sword(Material.DIAMOND_SWORD, 2));
                    for (int slot = 2; slot <= 5; slot++) {
                        inv.setItem(slot, new ItemStack(Material.GOLDEN_SWORD));
                    }
                }
            }
        }
        // Tout le reste : potions de soin instantané II à lancer.
        for (int slot = 0; slot < 36; slot++) {
            if (inv.getItem(slot) == null) {
                inv.setItem(slot, potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING));
            }
        }
        p.updateInventory();
    }

    /** Les kits sans potions de soin : Gapple, BuildUHC, Combo, Sumo, Boxing, Soup, Archer. */
    private void otherKit(Player p, Ladder ladder) {
        PlayerInventory inv = p.getInventory();
        switch (ladder) {
            case GAPPLE -> {
                armor(p, "DIAMOND", 4);
                inv.setItem(0, sword(Material.DIAMOND_SWORD, 5));
                inv.setItem(1, new ItemStack(Material.GOLDEN_APPLE, 64));
                inv.setItem(2, potion(Material.POTION, PotionType.STRONG_STRENGTH));
                inv.setItem(3, potion(Material.POTION, PotionType.STRONG_STRENGTH));
                inv.setItem(4, potion(Material.POTION, PotionType.STRONG_SWIFTNESS));
                inv.setItem(5, potion(Material.POTION, PotionType.STRONG_SWIFTNESS));
                inv.setItem(8, new ItemStack(Material.COOKED_BEEF, 64));
                // Une armure de rechange.
                inv.setItem(9, piece("DIAMOND_HELMET", 4));
                inv.setItem(10, piece("DIAMOND_CHESTPLATE", 4));
                inv.setItem(11, piece("DIAMOND_LEGGINGS", 4));
                inv.setItem(12, piece("DIAMOND_BOOTS", 4));
            }
            case BUILDUHC -> {
                inv.setHelmet(piece("DIAMOND_HELMET", 2));
                inv.setChestplate(piece("IRON_CHESTPLATE", 2));
                inv.setLeggings(piece("DIAMOND_LEGGINGS", 2));
                inv.setBoots(piece("DIAMOND_BOOTS", 2));
                inv.setItem(0, sword(Material.DIAMOND_SWORD, 3));
                ItemStack bow = new ItemStack(Material.BOW);
                bow.addUnsafeEnchantment(Enchantment.POWER, 3);
                inv.setItem(1, unbreakable(bow));
                inv.setItem(2, new ItemStack(Material.WATER_BUCKET));
                inv.setItem(3, new ItemStack(Material.LAVA_BUCKET));
                inv.setItem(4, new ItemStack(Material.COBBLESTONE, 64));
                inv.setItem(5, new ItemStack(Material.GOLDEN_APPLE, 9));
                inv.setItem(6, new ItemStack(Material.OAK_PLANKS, 64));
                inv.setItem(7, unbreakable(new ItemStack(Material.DIAMOND_PICKAXE)));
                inv.setItem(8, new ItemStack(Material.COOKED_BEEF, 64));
                inv.setItem(9, new ItemStack(Material.ARROW, 32));
                inv.setItem(10, unbreakable(new ItemStack(Material.DIAMOND_AXE)));
                inv.setItem(11, new ItemStack(Material.WATER_BUCKET));
                inv.setItem(12, new ItemStack(Material.LAVA_BUCKET));
                inv.setItem(13, new ItemStack(Material.COBBLESTONE, 64));
            }
            case COMBO -> {
                armor(p, "DIAMOND", 4);
                inv.setItem(0, sword(Material.DIAMOND_SWORD, 3));
                inv.setItem(1, new ItemStack(Material.GOLDEN_APPLE, 64));
                inv.setItem(8, new ItemStack(Material.COOKED_BEEF, 64));
                speedColumn(inv);
            }
            case SUMO -> {
                // À mains nues.
            }
            case BOXING -> {
                inv.setItem(0, sword(Material.DIAMOND_SWORD, 1));
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SPEED, org.bukkit.potion.PotionEffect.INFINITE_DURATION, 1, false, false));
            }
            case SOUP -> {
                armor(p, "IRON", 1);
                inv.setItem(0, sword(Material.DIAMOND_SWORD, 1));
                inv.setItem(1, new ItemStack(Material.ENDER_PEARL, 8));
                speedColumn(inv);
                for (int slot = 0; slot < 36; slot++) {
                    if (inv.getItem(slot) == null) {
                        inv.setItem(slot, new ItemStack(Material.MUSHROOM_STEW));
                    }
                }
            }
            case ARCHER -> {
                armor(p, "LEATHER", 2);
                ItemStack bow = new ItemStack(Material.BOW);
                bow.addUnsafeEnchantment(Enchantment.POWER, 2);
                bow.addUnsafeEnchantment(Enchantment.PUNCH, 1);
                bow.addUnsafeEnchantment(Enchantment.INFINITY, 1);
                inv.setItem(0, unbreakable(bow));
                inv.setItem(1, new ItemStack(Material.GOLDEN_APPLE, 4));
                inv.setItem(2, new ItemStack(Material.ENDER_PEARL, 4));
                inv.setItem(8, new ItemStack(Material.COOKED_BEEF, 64));
                inv.setItem(9, new ItemStack(Material.ARROW, 1));
            }
            default -> { }
        }
    }

    /** Quatre potions de vitesse II, une par rangée, toujours au même endroit. */
    private static void speedColumn(PlayerInventory inv) {
        for (int slot : new int[] {7, 17, 26, 35}) {
            inv.setItem(slot, potion(Material.POTION, PotionType.STRONG_SWIFTNESS));
        }
    }

    private static void armor(Player p, String prefix) {
        armor(p, prefix, 2);
    }

    private static void armor(Player p, String prefix, int protection) {
        PlayerInventory inv = p.getInventory();
        inv.setHelmet(piece(prefix + "_HELMET", protection));
        inv.setChestplate(piece(prefix + "_CHESTPLATE", protection));
        inv.setLeggings(piece(prefix + "_LEGGINGS", protection));
        inv.setBoots(piece(prefix + "_BOOTS", protection));
    }

    private static ItemStack piece(String material, int protection) {
        ItemStack item = new ItemStack(Material.valueOf(material));
        item.addUnsafeEnchantment(Enchantment.PROTECTION, protection);
        return unbreakable(item);
    }

    private static ItemStack sword(Material material, int sharpness) {
        ItemStack item = new ItemStack(material);
        item.addUnsafeEnchantment(Enchantment.SHARPNESS, sharpness);
        return unbreakable(item);
    }

    /** Un combat dure quelques minutes : l'usure n'apporterait que des armures cassées au hasard. */
    private static ItemStack unbreakable(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack potion(Material material, PotionType type) {
        ItemStack item = new ItemStack(material);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(type);
        item.setItemMeta(meta);
        return item;
    }
}
