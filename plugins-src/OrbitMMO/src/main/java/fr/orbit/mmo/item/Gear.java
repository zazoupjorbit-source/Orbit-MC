package fr.orbit.mmo.item;

import fr.orbit.mmo.Keys;
import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.classes.TalentDef;
import fr.orbit.mmo.clazz.WeaponType;
import fr.orbit.mmo.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Équipement chez le forgeron : usure (l'arme s'use en frappant, l'armure en encaissant) et améliorations +1 à +10.
 * Une pièce cassée n'est pas détruite : elle ne sert plus qu'à moitié jusqu'à la réparation.
 * L'usure vanilla est coupée : c'est le plugin qui use les objets.
 */
public final class Gear implements Listener {

    public static final int MAX_LEVEL = 10;
    public static final int WEAPON_DURABILITY = 800;
    public static final int ARMOR_DURABILITY = 600;
    /** Bonus d'attaque par niveau d'amélioration d'une arme (%). */
    public static final double WEAPON_BONUS = 6;
    /** Dégâts subis en moins par niveau d'amélioration d'une pièce d'armure (points de %). */
    public static final double ARMOR_BONUS = 0.75;

    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Points d'armure vanilla par matériau : casque, plastron, jambières, bottes. */
    private static final Map<String, int[]> ARMOR_POINTS = Map.of(
            "LEATHER", new int[]{1, 3, 2, 1},
            "CHAINMAIL", new int[]{2, 5, 4, 1},
            "IRON", new int[]{2, 6, 5, 2},
            "GOLDEN", new int[]{2, 5, 3, 1},
            "DIAMOND", new int[]{3, 8, 6, 3},
            "NETHERITE", new int[]{3, 8, 6, 3});

    private final OrbitMMO plugin;

    public Gear(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ Lecture

    /** Arme, armure ou butin de boss du MMO. */
    public static boolean isGear(ItemStack it) {
        String t = Items.type(it);
        return "weapon".equals(t) || "armor".equals(t) || "lab".equals(t);
    }

    public static boolean isArmorPiece(ItemStack it) {
        if (it == null) {
            return false;
        }
        String n = it.getType().name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    private static int intKey(ItemStack it, org.bukkit.NamespacedKey key) {
        if (it == null || !it.hasItemMeta()) {
            return 0;
        }
        Integer v = it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public static int level(ItemStack it) {
        return intKey(it, Keys.UPGRADE);
    }

    public static int wear(ItemStack it) {
        return intKey(it, Keys.WEAR);
    }

    public static int maxDurability(ItemStack it) {
        return isArmorPiece(it) ? ARMOR_DURABILITY : WEAPON_DURABILITY;
    }

    public static boolean broken(ItemStack it) {
        return isGear(it) && wear(it) >= maxDurability(it);
    }

    /** Multiplicateur d'attaque d'une arme : amélioration, et moitié si elle est cassée. */
    public static double attackMult(ItemStack it) {
        if (!isGear(it)) {
            return 1;
        }
        return (1 + WEAPON_BONUS * level(it) / 100.0) * (broken(it) ? 0.5 : 1);
    }

    /** Palier de l'objet (0 = recrue) pour les prix du forgeron. */
    private static int tier(ItemStack it) {
        if (Loot.isNew(it)) {
            return (int) Math.round(Loot.tier(it) * 3 / 7.0);
        }
        String n = it.getType().name();
        if (isArmorPiece(it)) {
            for (int t = Items.ARMOR_PREFIX.length - 1; t >= 0; t--) {
                if (n.startsWith(Items.ARMOR_PREFIX[t] + "_")) {
                    return t;
                }
            }
            return 3;
        }
        WeaponType wt = WeaponType.of(it);
        // Baguette, grimoire, arc, masse : même matériau à tous les paliers, le palier se lit dans l'attaque.
        Integer atk = it.getItemMeta() == null ? null
                : it.getItemMeta().getPersistentDataContainer().get(fr.orbit.mmo.Keys.ATK, org.bukkit.persistence.PersistentDataType.INTEGER);
        if (atk != null && wt.material(0) == wt.material(3)) {
            for (int t = 3; t >= 0; t--) {
                if (atk >= Items.weaponAtk(wt, t)) {
                    return t;
                }
            }
        }
        return Math.max(0, Math.min(3, wt.tierOf(it.getType())));
    }

    /** Prix pour passer au niveau suivant (0 si déjà au maximum). */
    public static long upgradeCost(ItemStack it) {
        int next = level(it) + 1;
        if (next > MAX_LEVEL) {
            return 0;
        }
        int t = tier(it);
        double base;
        if (Loot.isNew(it)) {
            // Nouveau format : d'après le prix du palier et la rareté (pièce d'armure = 1/4 d'ensemble).
            base = isArmorPiece(it) ? 12 + Loot.priceBase(it) * 0.015 : 30 + Loot.priceBase(it) * 0.04;
        } else {
            base = isArmorPiece(it) ? 12 + Items.ARMOR_PRICE[Math.min(t, Items.ARMOR_PRICE.length - 1)] * 0.015
                    : 30 + Items.TIER_PRICE[Math.min(t, 3)] * 0.04;
            if ("lab".equals(Items.type(it))) {
                base *= 1.5;
            }
        }
        return Math.round(base * next * next);
    }

    /** Prix de la réparation complète (0 si l'objet est neuf). */
    public static long repairCost(ItemStack it) {
        int w = Math.min(wear(it), maxDurability(it));
        if (w <= 0) {
            return 0;
        }
        int t = tier(it);
        double rate = isArmorPiece(it) ? 0.03 + 0.03 * t : 0.05 + 0.05 * t;
        return Math.max(1, (long) Math.ceil(w * rate));
    }

    /** Ligne « solidité » pour les menus. */
    public static String durabilityLine(ItemStack it) {
        int max = maxDurability(it);
        int left = Math.max(0, max - wear(it));
        double f = left / (double) max;
        String color = Text.hpColor(f);
        return "<#8C96A3>Solidité : <" + color + ">" + left + " / " + max + (left == 0 ? " <#FF5C5C>(cassée : efficacité ÷ 2)" : "");
    }

    /** Ligne « amélioration » pour les menus. */
    public static String bonusLine(ItemStack it) {
        int lvl = level(it);
        if (isArmorPiece(it)) {
            return "<#FFD66E>Amélioration +" + lvl + " <#8C96A3>· dégâts subis −" + TalentDef.format(lvl * ARMOR_BONUS) + " %";
        }
        return "<#FFD66E>Amélioration +" + lvl + " <#8C96A3>· attaque +" + TalentDef.format(lvl * WEAPON_BONUS) + " %";
    }

    // ------------------------------------------------------------------ Écriture

    /** Fixe l'usure (et la barre de durabilité affichée). L'objet n'est jamais détruit. */
    public static void setWear(ItemStack it, int wear) {
        int max = maxDurability(it);
        int w = Math.max(0, Math.min(max, wear));
        ItemMeta meta = it.getItemMeta();
        meta.setUnbreakable(false);
        meta.getPersistentDataContainer().set(Keys.WEAR, PersistentDataType.INTEGER, w);
        if (meta instanceof Damageable dm) {
            dm.setMaxDamage(max);
            dm.setDamage(Math.min(w, max - 1));
        }
        it.setItemMeta(meta);
    }

    /** Change le niveau d'amélioration et réécrit le nom (« +N ») et la description. */
    public static void setLevel(ItemStack it, int level) {
        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (Loot.isNew(it)) {
            pdc.set(Keys.UPGRADE, PersistentDataType.INTEGER, Math.max(0, Math.min(MAX_LEVEL, level)));
            it.setItemMeta(meta);
            Loot.refresh(it);
            return;
        }
        if (!pdc.has(Keys.BASE_NAME)) {
            Component name = meta.hasDisplayName() ? meta.displayName() : Component.translatable(it.getType().translationKey());
            pdc.set(Keys.BASE_NAME, PersistentDataType.STRING, GSON.serialize(name));
            List<Component> lore = meta.lore();
            StringBuilder sb = new StringBuilder();
            if (lore != null) {
                for (Component c : lore) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(GSON.serialize(c));
                }
            }
            pdc.set(Keys.BASE_LORE, PersistentDataType.STRING, sb.toString());
        }
        int lvl = Math.max(0, Math.min(MAX_LEVEL, level));
        pdc.set(Keys.UPGRADE, PersistentDataType.INTEGER, lvl);
        Component base = GSON.deserialize(pdc.get(Keys.BASE_NAME, PersistentDataType.STRING));
        meta.displayName(lvl > 0 ? base.append(Text.item(" <#FFD66E><b>+" + lvl)) : base);
        List<Component> lore = new ArrayList<>();
        String raw = pdc.get(Keys.BASE_LORE, PersistentDataType.STRING);
        if (raw != null && !raw.isEmpty()) {
            for (String line : raw.split("\n")) {
                lore.add(GSON.deserialize(line));
            }
        }
        it.setItemMeta(meta);
        if (lvl > 0) {
            lore.add(Text.item(""));
            lore.add(Text.item(bonusLine(it)));
        }
        ItemMeta again = it.getItemMeta();
        again.lore(lore);
        it.setItemMeta(again);
    }

    // ------------------------------------------------------------------ Usure en combat

    /** Coup porté : l'arme en main s'use d'un point. */
    public void wearWeapon(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack it = inv.getItemInMainHand();
        if (!"weapon".equals(Items.type(it))) {
            return;
        }
        if (addWear(p, it, 1)) {
            inv.setItemInMainHand(it);
        }
    }

    /** Coup reçu : une pièce d'armure portée (au hasard) s'use d'un point. */
    public void wearArmor(Player p) {
        PlayerInventory inv = p.getInventory();
        List<EquipmentSlot> worn = new ArrayList<>(4);
        for (EquipmentSlot s : ARMOR_SLOTS) {
            ItemStack it = inv.getItem(s);
            if (isGear(it) && isArmorPiece(it) && !broken(it)) {
                worn.add(s);
            }
        }
        if (worn.isEmpty()) {
            return;
        }
        EquipmentSlot s = worn.get(ThreadLocalRandom.current().nextInt(worn.size()));
        ItemStack it = inv.getItem(s);
        if (addWear(p, it, 1)) {
            inv.setItem(s, it);
        }
    }

    private boolean addWear(Player p, ItemStack it, int amount) {
        if (broken(it)) {
            return false;
        }
        int max = maxDurability(it);
        int before = wear(it);
        int after = Math.min(max, before + amount);
        setWear(it, after);
        int warn = (int) (max * 0.9);
        if (before < warn && after >= warn) {
            plugin.hud().flash(p, "<#FF8A3B>⚠ " + name(it) + " <#FF8A3B>s'use : passez chez le forgeron.", 50);
        } else if (after >= max) {
            plugin.hud().flash(p, "<#FF5C5C>✖ " + name(it) + " <#FF5C5C>est cassée : efficacité réduite de moitié !", 60);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1f);
        }
        return true;
    }

    private static String name(ItemStack it) {
        Component c = it.hasItemMeta() && it.getItemMeta().hasDisplayName() ? it.getItemMeta().displayName() : null;
        return c == null ? "Votre équipement" : net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(c);
    }

    // ------------------------------------------------------------------ Armure

    /** Dégâts subis en moins grâce aux améliorations des pièces portées (points de %). */
    public static double upgradeReduction(Player p) {
        double sum = 0;
        for (EquipmentSlot s : ARMOR_SLOTS) {
            ItemStack it = p.getInventory().getItem(s);
            if (isGear(it) && !broken(it)) {
                sum += level(it) * ARMOR_BONUS;
            }
        }
        return sum;
    }

    /** Points d'armure perdus à cause des pièces cassées (la moitié de leur protection). */
    public static double brokenArmor(Player p) {
        double lost = 0;
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            ItemStack it = p.getInventory().getItem(ARMOR_SLOTS[i]);
            if (!broken(it)) {
                continue;
            }
            lost += armorPoints(it, i) * 0.5;
        }
        return lost;
    }

    /** Points d'armure d'une pièce, quel que soit son emplacement. */
    public static double armorPointsOf(ItemStack it) {
        String n = it.getType().name();
        int slot = n.endsWith("_HELMET") ? 0 : n.endsWith("_CHESTPLATE") ? 1 : n.endsWith("_LEGGINGS") ? 2 : n.endsWith("_BOOTS") ? 3 : -1;
        return slot < 0 ? 0 : armorPoints(it, slot);
    }

    private static double armorPoints(ItemStack it, int slotIndex) {
        ItemMeta meta = it.getItemMeta();
        Collection<AttributeModifier> mods = meta == null ? null : meta.getAttributeModifiers(Attribute.ARMOR);
        if (mods != null && !mods.isEmpty()) {
            double sum = 0;
            for (AttributeModifier m : mods) {
                sum += m.getAmount();
            }
            return sum;
        }
        String n = it.getType().name();
        for (Map.Entry<String, int[]> e : ARMOR_POINTS.entrySet()) {
            if (n.startsWith(e.getKey() + "_")) {
                return e.getValue()[slotIndex];
            }
        }
        return 0;
    }

    /** L'usure vanilla ne touche pas l'équipement du MMO : c'est le plugin qui l'use. */
    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent e) {
        if (isGear(e.getItem())) {
            e.setCancelled(true);
        }
    }
}
