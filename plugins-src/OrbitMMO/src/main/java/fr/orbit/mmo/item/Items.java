package fr.orbit.mmo.item;

import fr.orbit.mmo.Keys;
import fr.orbit.mmo.clazz.WeaponType;
import fr.orbit.mmo.registry.Defs.BossDef;
import fr.orbit.mmo.registry.Defs.DropDef;
import fr.orbit.mmo.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Armes, armures, consommables (potions, cristaux) et matériaux. */
public final class Items {

    public static final String[] TIER_NAMES = {"de recrue", "d'acier", "de cristal", "d'obsidienne"};
    public static final String[] TIER_COLORS = {"#D7DCE2", "#7FB2FF", "#B36BFF", "#FFB347"};
    public static final int[] TIER_ATK = {12, 20, 30, 44};
    public static final int[] TIER_PRICE = {0, 350, 2200, 9500};
    public static final int[] TIER_LEVEL = {1, 6, 16, 30};

    public static final String[] ARMOR_NAMES = {"Tenue de recrue", "Cotte de mailles", "Armure d'acier", "Armure de cristal", "Armure d'obsidienne"};
    public static final String[] ARMOR_PREFIX = {"LEATHER", "CHAINMAIL", "IRON", "DIAMOND", "NETHERITE"};
    public static final int[] ARMOR_PRICE = {0, 250, 1200, 5000, 14000};
    public static final int[] ARMOR_LEVEL = {1, 3, 10, 20, 32};

    public static final int POTION_PRICE = 40;
    public static final int MANA_POTION_PRICE = 60;
    public static final int HEAL_CRYSTAL_PRICE = 500;
    public static final int TP_CRYSTAL_PRICE = 150;

    private static final String CONTROLS_1 = "<#5A6270>Sorts : objets Attaque, Déplacement, Buff + [Clic droit]";
    private static final String CONTROLS_2 = "<#5A6270>[Accroupi + molette] changer de sort · [F] menu";

    private Items() {
    }

    // ------------------------------------------------------------------ Armes

    public static int weaponAtk(WeaponType type, int tier) {
        return (int) Math.round(TIER_ATK[Math.max(0, Math.min(3, tier))] * type.atkMult);
    }

    /** Arme de base de la catégorie (qualité Commune, palier 0 à 7 du nouveau format). */
    public static ItemStack weapon(WeaponType type, int tier) {
        return Loot.shopWeapon(Loot.Base.defaultFor(type), tier);
    }

    // ------------------------------------------------------------------ Modèles du pack de ressources

    /** Modèle du pack pour une arme (null : apparence normale, ex. lances et masses). */
    public static String weaponModel(WeaponType type, int tier) {
        String base = switch (type) {
            case ONE_HANDED -> "sword";
            case DAGGER -> "dagger";
            case STAFF -> "staff";
            case WAND -> "wand";
            case GRIMOIRE -> "grimoire";
            default -> null;
        };
        return base == null ? null : base + "_" + Math.max(0, Math.min(3, tier));
    }

    /** Pose le modèle du pack (custom_model_data) ; sans le pack, l'objet garde son apparence normale. */
    public static void model(ItemStack it, String id) {
        if (it == null || id == null) {
            return;
        }
        it.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString("orbitmmo:" + id).build());
    }

    /** Ajoute les modèles aux objets du plugin créés avant le pack (inventaire d'un joueur). */
    public static void applyModels(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || it.isEmpty() || it.hasData(DataComponentTypes.CUSTOM_MODEL_DATA)) {
                continue;
            }
            String kind = type(it);
            if (kind == null) {
                continue;
            }
            String id = switch (kind) {
                case "weapon" -> {
                    WeaponType wt = WeaponType.of(it);
                    yield weaponModel(wt, wt.tierOf(it.getType()));
                }
                case "potion", "heal_crystal", "tp_crystal" -> kind;
                default -> null;
            };
            if (id != null) {
                model(it, id);
                p.getInventory().setItem(i, it);
            }
        }
    }

    public static ItemStack weaponItem(ItemStack item, WeaponType type, int atk, String nameMini, int level, String extra) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(nameMini));
        List<String> lore = new ArrayList<>();
        lore.add("<#8C96A3>Type : <white>" + type.label);
        lore.add("<#8C96A3>Attaque : <white>" + atk);
        lore.add("<#8C96A3>Niveau conseillé : <white>" + level);
        if (extra != null) {
            lore.add(extra);
        }
        lore.add("");
        lore.add(CONTROLS_1);
        lore.add(CONTROLS_2);
        meta.lore(Text.lore(lore));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        if (item.getType() == org.bukkit.Material.BOW) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.INFINITY, 1, true); // le carquois ne s'épuise jamais
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.ITEM, PersistentDataType.STRING, "weapon");
        pdc.set(Keys.WEAPON, PersistentDataType.STRING, type.name());
        pdc.set(Keys.ATK, PersistentDataType.INTEGER, atk);
        item.setItemMeta(meta);
        return item;
    }

    /** Attaque de l'arme tenue : valeur du plugin, sinon estimée d'après le matériau. */
    public static double attackOf(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 4;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Integer atk = meta.getPersistentDataContainer().get(Keys.ATK, PersistentDataType.INTEGER);
            if (atk != null) {
                // Améliorations du forgeron, et moitié si l'arme est cassée.
                return atk * Gear.attackMult(item);
            }
        }
        WeaponType type = WeaponType.of(item);
        if (type == WeaponType.NONE) {
            return 4;
        }
        if (type == WeaponType.MACE) {
            return 18;
        }
        String n = item.getType().name();
        double base = n.startsWith("NETHERITE_") ? 28 : n.startsWith("DIAMOND_") ? 22 : n.startsWith("IRON_") ? 17
                : n.startsWith("COPPER_") ? 14 : n.startsWith("STONE_") ? 13 : n.startsWith("GOLDEN_") ? 12 : 10;
        return type == WeaponType.TWO_HANDED ? base * 1.15 : base;
    }

    /** Bouclier de recrue (qualité Commune). */
    public static ItemStack shield() {
        return Loot.shopShield(0);
    }

    // ------------------------------------------------------------------ Armures

    /** Casque, plastron, jambières, bottes (palier 0 à 7 du nouveau format). */
    public static ItemStack[] armorSet(int tier, Color dye) {
        return Loot.armorSet(tier, dye);
    }

    public static void equip(Player p, ItemStack[] set) {
        p.getInventory().setHelmet(set[0]);
        p.getInventory().setChestplate(set[1]);
        p.getInventory().setLeggings(set[2]);
        p.getInventory().setBoots(set[3]);
    }

    // ------------------------------------------------------------------ Consommables

    public static ItemStack potion(int amount) {
        ItemStack it = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) it.getItemMeta();
        meta.setColor(Color.fromRGB(0xFF5C8A));
        meta.displayName(Text.item("<#FF8AAE>Potion de soin"));
        meta.lore(Text.lore(List.of("<#8C96A3>Rend 35 % des PV en 5 secondes.", "<#5A6270>[Clic droit] boire")));
        meta.setMaxStackSize(16);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, "potion");
        it.setItemMeta(meta);
        it.setAmount(Math.max(1, Math.min(16, amount)));
        model(it, "potion");
        return it;
    }

    public static ItemStack manaPotion(int amount) {
        ItemStack it = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) it.getItemMeta();
        meta.setColor(Color.fromRGB(0x4FA3FF));
        meta.displayName(Text.item("<#4FA3FF>Potion de mana"));
        meta.lore(Text.lore(List.of("<#8C96A3>Rend 50 % de votre ressource", "<#8C96A3>(mana, énergie, rage…).", "<#5A6270>[Clic droit] boire")));
        meta.setMaxStackSize(16);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, "mana_potion");
        it.setItemMeta(meta);
        it.setAmount(Math.max(1, Math.min(16, amount)));
        return it;
    }

    public static ItemStack healCrystal() {
        ItemStack it = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.item("<#FF8AD8>Cristal de soin"));
        meta.lore(Text.lore(List.of("<#8C96A3>Restaure tous les PV instantanément.", "<#5A6270>[Clic droit] « Soin ! »")));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, "heal_crystal");
        it.setItemMeta(meta);
        model(it, "heal_crystal");
        return it;
    }

    public static ItemStack tpCrystal() {
        ItemStack it = new ItemStack(Material.PRISMARINE_CRYSTALS);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.item("<#7FD4FF>Cristal de téléportation"));
        meta.lore(Text.lore(List.of("<#8C96A3>Ramène à la ville de l'étage.", "<#5A6270>[Clic droit] « Téléportation ! »")));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, "tp_crystal");
        it.setItemMeta(meta);
        model(it, "tp_crystal");
        return it;
    }

    public static ItemStack material(DropDef drop) {
        ItemStack it = new ItemStack(drop.item());
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.item("<#D7DCE2>" + Text.esc(drop.name())));
        meta.lore(Text.lore(List.of("<#8C96A3>Matériau · se revend <#FFD66E>" + drop.sell() + " or</#FFD66E> au forgeron")));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.ITEM, PersistentDataType.STRING, "material");
        pdc.set(Keys.SELL, PersistentDataType.INTEGER, drop.sell());
        it.setItemMeta(meta);
        return it;
    }

    /** Butin d'un boss : armure, ou arme du type que tient le joueur. */
    public static ItemStack lab(BossDef boss, WeaponType held, int floor) {
        String name = "<gradient:#FFD66E:#FF8A3B><b>" + Text.esc(boss.labName()) + "</b></gradient>";
        String extra = "<#FFD66E>Butin de boss · " + Text.esc(boss.name());
        if ("armor".equalsIgnoreCase(boss.labKind())) {
            ItemStack it = new ItemStack(boss.labMaterial());
            ItemMeta meta = it.getItemMeta();
            meta.displayName(Text.item(name));
            meta.lore(Text.lore(List.of(extra, "<#8C96A3>Armure : <white>+" + boss.labValue())));
            meta.setUnbreakable(true);
            meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(Keys.LAB_ARMOR, boss.labValue(),
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ARMOR));
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DYE);
            if (meta instanceof LeatherArmorMeta leather) {
                leather.setColor(Color.fromRGB(0x15161C));
            }
            meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, "lab");
            it.setItemMeta(meta);
            return it;
        }
        WeaponType type = held == null || held == WeaponType.NONE ? WeaponType.ONE_HANDED : held;
        int tier = Math.min(3, 1 + floor / 3);
        int atk = weaponAtk(type, tier) + boss.labValue();
        ItemStack it = weaponItem(new ItemStack(type.material(tier)), type, atk, name, TIER_LEVEL[tier], extra);
        model(it, weaponModel(type, tier));
        return it;
    }

    // ------------------------------------------------------------------ Utilitaires

    public static String type(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(Keys.ITEM, PersistentDataType.STRING);
    }

    public static int sellValue(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer v = item.getItemMeta().getPersistentDataContainer().get(Keys.SELL, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    /**
     * Prix de revente au marchand d'UN exemplaire (0 : invendable).
     * Matériaux : valeur posée sur l'objet ; équipement : un quart du prix d'achat ; consommables : la moitié.
     */
    public static long unitSellPrice(ItemStack item) {
        String kind = type(item);
        if (kind == null) {
            return 0;
        }
        if (Loot.isNew(item) && ("weapon".equals(kind) || "armor".equals(kind) || "shield".equals(kind))) {
            return Loot.sellPrice(item);
        }
        String n = item.getType().name();
        return switch (kind) {
            case "rune" -> {
                Rune.Ref r = Rune.of(item);
                yield r == null ? 0 : r.rune().sellPrice(r.rank());
            }
            case "material" -> sellValue(item);
            case "weapon" -> {
                Integer atk = item.getItemMeta().getPersistentDataContainer().get(Keys.ATK, PersistentDataType.INTEGER);
                int tier = WeaponType.of(item).tierOf(item.getType());
                long base = Math.max(10, TIER_PRICE[Math.max(0, Math.min(3, tier))] / 4);
                // Arme de boss : son bonus d'attaque se paie aussi.
                int extra = atk == null ? 0 : Math.max(0, atk - weaponAtk(WeaponType.of(item), tier));
                yield base + extra * 25L;
            }
            case "armor" -> {
                for (int t = ARMOR_PREFIX.length - 1; t >= 0; t--) {
                    if (n.startsWith(ARMOR_PREFIX[t] + "_")) {
                        yield Math.max(5, ARMOR_PRICE[t] / 16);
                    }
                }
                yield 5;
            }
            case "lab" -> 600;
            case "potion" -> POTION_PRICE / 2;
            case "mana_potion" -> MANA_POTION_PRICE / 2;
            case "heal_crystal" -> HEAL_CRYSTAL_PRICE / 2;
            case "tp_crystal" -> TP_CRYSTAL_PRICE / 2;
            default -> {
                Boost b = Boost.byItemType(kind);
                yield b == null ? 0 : b.price / 2;
            }
        };
    }

    /** Donne un objet ; s'il n'y a plus de place, il tombe aux pieds du joueur. */
    public static void give(Player p, ItemStack item) {
        Map<Integer, ItemStack> rest = p.getInventory().addItem(item);
        for (ItemStack it : rest.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), it);
        }
    }

    public static ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.item(name));
        if (lore != null && !lore.isEmpty()) {
            meta.lore(Text.lore(lore));
        }
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack icon(Material m, String name, String... lore) {
        return icon(m, name, Arrays.asList(lore));
    }

    public static ItemStack glow(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }
}
