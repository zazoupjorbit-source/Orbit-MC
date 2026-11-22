package fr.orbit.extraction.armor;

import fr.orbit.extraction.Keys;
import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.attribute.AttributeModifierDisplay;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tenues : cinq niveaux de protection, de la tenue bricolée à l'armure lourde. Une pièce est une pièce d'armure
 * vanilla (son « materiau ») dont l'apparence portée vient du pack (composant equippable → orbitguns:&lt;tenue&gt;),
 * l'icône de la donnée de modèle « orbitguns:armor_&lt;tenue&gt;_&lt;pièce&gt; », et la protection de la configuration.
 * Sans le pack, l'icône reste celle de l'armure vanilla.
 */
public final class Armors {

    public enum Piece {
        HELMET("casque", "helmet", EquipmentSlot.HEAD, EquipmentSlotGroup.HEAD),
        CHESTPLATE("plastron", "chestplate", EquipmentSlot.CHEST, EquipmentSlotGroup.CHEST),
        LEGGINGS("jambieres", "leggings", EquipmentSlot.LEGS, EquipmentSlotGroup.LEGS),
        BOOTS("bottes", "boots", EquipmentSlot.FEET, EquipmentSlotGroup.FEET);

        private final String id, file;
        private final EquipmentSlot slot;
        private final EquipmentSlotGroup group;

        Piece(String id, String file, EquipmentSlot slot, EquipmentSlotGroup group) {
            this.id = id;
            this.file = file;
            this.slot = slot;
            this.group = group;
        }

        /** Nom dans la configuration et les descriptions d'objets (« casque », « plastron », « jambieres », « bottes »). */
        public String id() {
            return id;
        }

        /** « casque », « helmet », « jambières », « gilet »… → pièce, ou null. */
        public static Piece of(String s) {
            if (s == null) {
                return null;
            }
            return switch (s.toLowerCase(Locale.ROOT).replace('è', 'e')) {
                case "casque", "helmet", "tete" -> HELMET;
                case "plastron", "gilet", "chestplate", "torse" -> CHESTPLATE;
                case "jambieres", "pantalon", "leggings", "jambes" -> LEGGINGS;
                case "bottes", "boots", "pieds" -> BOOTS;
                default -> null;
            };
        }
    }

    /** speed = part de vitesse de marche en plus (négatif = plus lent) ; durability 0 = celle du matériau. */
    public record PieceDef(String name, double armor, double toughness, double knockback, double speed, int durability) {
    }

    public record SetDef(String id, String name, String color, int tier, String material, String description,
                         Map<Piece, PieceDef> pieces) {
    }

    private static final String MODEL_NS = "orbitguns";
    private static final int MAX_TIER = 5;

    private final OrbitExtraction plugin;
    private final Map<String, SetDef> sets = new LinkedHashMap<>();
    /** Armure vanilla → tenue qui la remplace (objets d'avant les tenues). */
    private final Map<Material, String> vanilla = new HashMap<>();
    private boolean convert = true;

    public Armors(OrbitExtraction plugin) {
        this.plugin = plugin;
    }

    public void load(ConfigurationSection c) {
        sets.clear();
        vanilla.clear();
        if (c == null) {
            return;
        }
        convert = c.getBoolean("convertir-vanilla", true);
        ConfigurationSection list = c.getConfigurationSection("tenues");
        if (list == null) {
            return;
        }
        for (String id : list.getKeys(false)) {
            ConfigurationSection s = list.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            String material = s.getString("materiau", "IRON").toUpperCase(Locale.ROOT);
            Map<Piece, PieceDef> pieces = new EnumMap<>(Piece.class);
            ConfigurationSection ps = s.getConfigurationSection("pieces");
            for (Piece p : Piece.values()) {
                ConfigurationSection d = ps == null ? null : ps.getConfigurationSection(p.id);
                if (d == null) {
                    continue;
                }
                if (material(material, p) == null) {
                    plugin.getLogger().warning("Tenue « " + id + " » : pas d'armure vanilla « " + material + "_" + p.name() + " », pièce ignorée.");
                    continue;
                }
                pieces.put(p, new PieceDef(d.getString("nom", p.id), Math.max(0, d.getDouble("armure", 0)),
                        Math.max(0, d.getDouble("robustesse", 0)), Math.max(0, d.getDouble("recul", 0)),
                        d.getDouble("vitesse", 0) / 100.0, Math.max(0, d.getInt("durabilite", 0))));
            }
            SetDef def = new SetDef(id.toLowerCase(Locale.ROOT), s.getString("nom", id), s.getString("couleur", "#D7DCE2"),
                    Math.max(1, Math.min(MAX_TIER, s.getInt("niveau", 1))), material, s.getString("description", ""), pieces);
            sets.put(def.id(), def);
            for (Piece p : pieces.keySet()) {
                vanilla.putIfAbsent(material(material, p), def.id() + ":" + p.id);
            }
        }
        plugin.getLogger().info("Tenues : " + sets.size() + " (" + String.join(", ", sets.keySet()) + ").");
    }

    private static Material material(String prefix, Piece p) {
        Material m = Material.matchMaterial(prefix + "_" + p.name());
        return m != null && m.isItem() ? m : null;
    }

    public Collection<SetDef> defs() {
        return sets.values();
    }

    public SetDef def(String id) {
        return id == null ? null : sets.get(id.toLowerCase(Locale.ROOT));
    }

    /** Toutes les pièces d'une tenue, dans l'ordre casque → bottes. */
    public List<ItemStack> items(SetDef s) {
        List<ItemStack> out = new ArrayList<>();
        for (Piece p : s.pieces().keySet()) {
            out.add(item(s, p));
        }
        return out;
    }

    public ItemStack item(SetDef s, Piece p) {
        PieceDef d = s.pieces().get(p);
        Material m = d == null ? null : material(s.material(), p);
        if (m == null) {
            return null;
        }
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(Text.item("<" + s.color() + ">" + d.name()));
            List<String> lore = new ArrayList<>();
            lore.add("<#8A919B>" + s.name() + "  <#5A6270>•  niveau " + s.tier());
            lore.add("<#8A919B>Protection <" + s.color() + ">" + "▰".repeat(s.tier()) + "</" + s.color() + "><#3A404A>"
                    + "▱".repeat(MAX_TIER - s.tier()));
            StringBuilder stats = new StringBuilder("<#8A919B>Armure <#D7DCE2>+" + fmt(d.armor()) + "</#D7DCE2>");
            if (d.toughness() > 0) {
                stats.append("  •  robustesse <#D7DCE2>+").append(fmt(d.toughness())).append("</#D7DCE2>");
            }
            lore.add(stats.toString());
            if (d.knockback() > 0) {
                lore.add("<#8A919B>Résistance au recul <#D7DCE2>+" + Math.round(d.knockback() * 100) + " %");
            }
            if (d.speed() != 0) {
                lore.add("<#8A919B>Vitesse <" + (d.speed() < 0 ? "#C0392B" : "#2ECC71") + ">" + (d.speed() > 0 ? "+" : "−")
                        + Math.round(Math.abs(d.speed()) * 100) + " %");
            }
            if (!s.description().isEmpty()) {
                lore.add("<#5A6270>" + s.description());
            }
            meta.lore(Text.lore(lore));
            meta.getPersistentDataContainer().set(Keys.ARMOR, PersistentDataType.STRING, s.id() + ":" + p.id);
        });
        Equippable base = it.getData(DataComponentTypes.EQUIPPABLE);
        Equippable.Builder eq = base != null ? base.toBuilder() : Equippable.equippable(p.slot);
        it.setData(DataComponentTypes.EQUIPPABLE, eq.assetId(Key.key(MODEL_NS, s.id())).build());
        it.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData().addString(MODEL_NS + ":armor_" + s.id() + "_" + p.file).build());
        // La protection vient d'ici, pas du matériau ; cachée de l'infobulle (les lignes ci-dessus la donnent en français).
        ItemAttributeModifiers.Builder attrs = ItemAttributeModifiers.itemAttributes();
        add(attrs, Attribute.ARMOR, "armure", d.armor(), AttributeModifier.Operation.ADD_NUMBER, p);
        add(attrs, Attribute.ARMOR_TOUGHNESS, "robustesse", d.toughness(), AttributeModifier.Operation.ADD_NUMBER, p);
        add(attrs, Attribute.KNOCKBACK_RESISTANCE, "recul", d.knockback(), AttributeModifier.Operation.ADD_NUMBER, p);
        add(attrs, Attribute.MOVEMENT_SPEED, "vitesse", d.speed(), AttributeModifier.Operation.MULTIPLY_SCALAR_1, p);
        it.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, attrs.build());
        if (d.durability() > 0) {
            it.setData(DataComponentTypes.MAX_DAMAGE, d.durability());
        }
        return it;
    }

    private void add(ItemAttributeModifiers.Builder b, Attribute attr, String name, double amount, AttributeModifier.Operation op, Piece p) {
        if (amount == 0) {
            return;
        }
        b.addModifier(attr, new AttributeModifier(new NamespacedKey(plugin, "tenue_" + name + "_" + p.file), amount, op, p.group),
                p.group, AttributeModifierDisplay.hidden());
    }

    /** « emeute:casque » pour une pièce de tenue, sinon null. */
    public String keyOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(Keys.ARMOR, PersistentDataType.STRING);
    }

    /**
     * Armure vanilla d'avant les tenues (cuir, mailles, fer, diamant…) : renvoie la pièce de tenue qui la remplace,
     * usure conservée ; null si l'objet n'est pas concerné.
     */
    public ItemStack upgrade(ItemStack it) {
        if (!convert || it == null) {
            return null;
        }
        String key = vanilla.get(it.getType());
        if (key == null || keyOf(it) != null) {
            return null;
        }
        if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) {
            return null;        // objet renommé à la main : on n'y touche pas
        }
        String[] k = key.split(":");
        SetDef s = def(k[0]);
        Piece p = Piece.of(k[1]);
        ItemStack out = s == null || p == null ? null : item(s, p);
        if (out == null) {
            return null;
        }
        if (it.getItemMeta() instanceof Damageable from && from.hasDamage()) {
            int max = out.getType().getMaxDurability();
            Integer custom = out.getData(DataComponentTypes.MAX_DAMAGE);
            int limit = custom != null ? custom : max;
            int used = (int) Math.round(from.getDamage() / (double) Math.max(1, it.getType().getMaxDurability()) * limit);
            out.setData(DataComponentTypes.DAMAGE, Math.max(0, Math.min(limit - 1, used)));
        }
        return out;
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d).replace('.', ',');
    }
}
