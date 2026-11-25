package fr.orbit.mmo.item;

import fr.orbit.mmo.Keys;
import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.classes.ClassDef;
import fr.orbit.mmo.classes.Mods;
import fr.orbit.mmo.classes.SpecDef;
import fr.orbit.mmo.clazz.WeaponType;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.registry.Defs.BossDef;
import fr.orbit.mmo.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Équipement façon MMO : 8 paliers de matériau (niveaux 1 à 72), 14 modèles d'armes, armures pièce par pièce,
 * boucliers, et 5 raretés (Commun → Légendaire) avec bonus tirés au hasard et châsses à runes.
 * Toutes les données sont sur l'objet (PDC) ; le nom et la description sont réécrits par {@link #refresh}.
 */
public final class Loot {

    public static final int TIERS = 8;
    public static final String[] TIER_NAMES = {"de recrue", "de cuivre", "d'acier", "d'électrum", "de cristal",
            "d'obsidienne", "d'astralite", "d'orbitium"};
    public static final String[] TIER_LABELS = {"Recrue", "Cuivre", "Acier", "Électrum", "Cristal", "Obsidienne",
            "Astralite", "Orbitium"};
    public static final int[] TIER_LEVEL = {1, 8, 16, 26, 36, 48, 60, 72};
    public static final int[] TIER_ATK = {12, 18, 27, 37, 48, 61, 76, 92};
    public static final long[] WEAPON_PRICE = {30, 250, 900, 2400, 5200, 9800, 17000, 28000};
    /** Prix d'une pièce d'armure (l'ensemble complet = 4 pièces, 10 % de remise). */
    public static final long[] ARMOR_PRICE = {20, 110, 380, 1000, 2200, 4200, 7400, 12000};
    public static final long[] SHIELD_PRICE = {40, 200, 700, 1800, 3900, 7400, 13000, 21000};
    /** Points d'armure d'un ensemble complet (2,2 % de dégâts en moins par point, 66 % au plus). */
    private static final int[] ARMOR_SET = {6, 9, 12, 14, 17, 20, 23, 26};

    private static final String[] METAL = {"STONE", "COPPER", "IRON", "GOLDEN", "DIAMOND", "NETHERITE", "NETHERITE", "NETHERITE"};
    private static final String[] SWORD_METAL = {"STONE", "STONE", "IRON", "GOLDEN", "DIAMOND", "NETHERITE", "NETHERITE", "NETHERITE"};
    private static final String[] AXE_MODEL_METAL = {"STONE", "STONE", "IRON", "IRON", "DIAMOND", "DIAMOND", "NETHERITE", "NETHERITE"};
    private static final String[] ARMOR_METAL = {"LEATHER", "COPPER", "IRON", "GOLDEN", "DIAMOND", "NETHERITE", "NETHERITE", "NETHERITE"};
    /** Modèle du pack (4 variantes) utilisé par palier. */
    private static final int[] PACK_TIER = {0, 0, 1, 1, 2, 2, 3, 3};
    private static final String[] LEGEND = {"de l'Éclipse", "des Comètes", "d'Astrée", "du Vide", "de l'Aurore", "des Lunes",
            "du Grand Orbite", "des Étoiles mortes"};
    private static final String[] LEGEND_LORE = {"Forgé dans la lumière d'une étoile mourante.",
            "On dit qu'il murmure le nom de son ancien porteur.", "Il pèse moins qu'une plume, frappe plus fort qu'un astre.",
            "Les Gardiens de l'Orbite le croyaient perdu."};

    private static final String CONTROLS_1 = "<#5A6270>Sorts : objets Attaque, Déplacement, Buff + [Clic droit]";
    private static final String CONTROLS_2 = "<#5A6270>[Accroupi + molette] changer de sort · [F] menu";

    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final Stat[] WEAPON_POOL = {Stat.PUISSANCE, Stat.PRECISION, Stat.FEROCITE, Stat.VAMPIRISME, Stat.CELERITE, Stat.FOCALISATION};
    private static final Stat[] CASTER_POOL = {Stat.PUISSANCE, Stat.PRECISION, Stat.FEROCITE, Stat.FOCALISATION, Stat.GRACE, Stat.CELERITE};
    private static final Stat[] ARMOR_POOL = {Stat.VITALITE, Stat.ROBUSTESSE, Stat.AGILITE, Stat.PUISSANCE, Stat.PRECISION,
            Stat.FEROCITE, Stat.CELERITE, Stat.FOCALISATION, Stat.EPINES, Stat.GRACE};
    private static final Stat[] SHIELD_POOL = {Stat.PARADE, Stat.ROBUSTESSE, Stat.VITALITE, Stat.EPINES};

    private Loot() {
    }

    // ------------------------------------------------------------------ Modèles

    enum Family {
        SWORD, AXE_MODEL, AXE, HOE, PICKAXE, SPEAR, MACE, BOW, STAFF, WAND, BOOK;

        Material material(int t) {
            int i = Math.max(0, Math.min(TIERS - 1, t));
            return switch (this) {
                case SWORD -> mat(SWORD_METAL[i] + "_SWORD", Material.IRON_SWORD);
                case AXE_MODEL -> mat(AXE_MODEL_METAL[i] + "_AXE", Material.IRON_AXE);
                case AXE -> mat(METAL[i] + "_AXE", Material.IRON_AXE);
                case HOE -> mat(METAL[i] + "_HOE", Material.IRON_HOE);
                case PICKAXE -> mat(METAL[i] + "_PICKAXE", Material.IRON_PICKAXE);
                case SPEAR -> mat(METAL[i] + "_SPEAR", Material.IRON_SPEAR);
                case MACE -> Material.MACE;
                case BOW -> Material.BOW;
                case STAFF -> i < 2 ? Material.STICK : i < 4 ? Material.BLAZE_ROD : Material.BREEZE_ROD;
                case WAND -> Material.END_ROD;
                case BOOK -> Material.BOOK;
            };
        }
    }

    /** Modèle d'arme : catégorie (pour les sorts), apparence, bonus propre. */
    public enum Base {
        EPEE("epee", "Épée longue", WeaponType.ONE_HANDED, Family.SWORD, "sword", 1.0, Stat.PRECISION),
        RAPIERE("rapiere", "Rapière", WeaponType.ONE_HANDED, Family.SWORD, "rapier", 0.95, Stat.AGILITE),
        KATANA("katana", "Katana", WeaponType.ONE_HANDED, Family.SWORD, "katana", 1.03, Stat.FEROCITE),
        ESPADON("espadon", "Espadon", WeaponType.TWO_HANDED, Family.AXE_MODEL, "greatsword", 1.0, Stat.PRECISION),
        HACHE("hache", "Hache de guerre", WeaponType.TWO_HANDED, Family.AXE, null, 1.02, Stat.FEROCITE),
        FAUX("faux", "Faux", WeaponType.TWO_HANDED, Family.HOE, null, 0.98, Stat.VAMPIRISME),
        BEC("bec", "Bec de corbin", WeaponType.TWO_HANDED, Family.PICKAXE, null, 1.03, Stat.PUISSANCE),
        DAGUE("dague", "Dague", WeaponType.DAGGER, Family.SWORD, "dagger", 1.0, Stat.PRECISION),
        LANCE("lance", "Lance", WeaponType.SPEAR, Family.SPEAR, null, 1.0, Stat.AGILITE),
        MASSE("masse", "Masse d'armes", WeaponType.MACE, Family.MACE, null, 1.0, Stat.FEROCITE),
        ARC("arc", "Arc long", WeaponType.BOW, Family.BOW, null, 1.0, Stat.PRECISION),
        BATON("baton", "Bâton", WeaponType.STAFF, Family.STAFF, "staff", 1.0, Stat.FOCALISATION),
        BAGUETTE("baguette", "Baguette", WeaponType.WAND, Family.WAND, "wand", 1.0, Stat.PRECISION),
        GRIMOIRE("grimoire", "Grimoire", WeaponType.GRIMOIRE, Family.BOOK, "grimoire", 1.0, Stat.GRACE);

        public final String id;
        public final String label;
        public final WeaponType type;
        final Family family;
        final String model;
        final double atkMult;
        public final Stat implicit;

        Base(String id, String label, WeaponType type, Family family, String model, double atkMult, Stat implicit) {
            this.id = id;
            this.label = label;
            this.type = type;
            this.family = family;
            this.model = model;
            this.atkMult = atkMult;
            this.implicit = implicit;
        }

        public Material material(int tier) {
            return family.material(tier);
        }

        public static Base byId(String id) {
            for (Base b : values()) {
                if (b.id.equals(id)) {
                    return b;
                }
            }
            return null;
        }

        public static Base defaultFor(WeaponType t) {
            return switch (t == null ? WeaponType.NONE : t) {
                case TWO_HANDED -> ESPADON;
                case DAGGER -> DAGUE;
                case SPEAR -> LANCE;
                case MACE -> MASSE;
                case BOW -> ARC;
                case STAFF -> BATON;
                case WAND -> BAGUETTE;
                case GRIMOIRE -> GRIMOIRE;
                default -> EPEE;
            };
        }

        public static List<Base> of(WeaponType t) {
            List<Base> out = new ArrayList<>();
            for (Base b : values()) {
                if (b.type == t) {
                    out.add(b);
                }
            }
            return out;
        }
    }

    /** Pièce d'armure. */
    public enum Slot {
        HEAD("head", "HELMET", "Casque", EquipmentSlotGroup.HEAD, 0.20, Stat.PRECISION),
        CHEST("chest", "CHESTPLATE", "Plastron", EquipmentSlotGroup.CHEST, 0.35, Stat.VITALITE),
        LEGS("legs", "LEGGINGS", "Jambières", EquipmentSlotGroup.LEGS, 0.30, Stat.ROBUSTESSE),
        FEET("feet", "BOOTS", "Bottes", EquipmentSlotGroup.FEET, 0.15, Stat.CELERITE);

        public final String id;
        final String piece;
        public final String label;
        final EquipmentSlotGroup group;
        final double share;
        public final Stat implicit;

        Slot(String id, String piece, String label, EquipmentSlotGroup group, double share, Stat implicit) {
            this.id = id;
            this.piece = piece;
            this.label = label;
            this.group = group;
            this.share = share;
            this.implicit = implicit;
        }

        /** Matériau d'une pièce de plaques (icônes des menus). */
        public Material material(int tier) {
            return material(ArmorType.PLAQUES, tier);
        }

        public Material material(ArmorType type, int tier) {
            return mat(type.metal(tier) + "_" + piece, mat("IRON_" + piece, Material.IRON_CHESTPLATE));
        }

        /** Modèle d'icône HD du pack : « armure/plaques_chest_2 » (4 variantes pour 8 paliers). */
        public String model(ArmorType type, int tier) {
            return "armure/" + type.id + "_" + id + "_" + PACK_TIER[Math.max(0, Math.min(TIERS - 1, tier))];
        }

        public static Slot byId(String id) {
            for (Slot s : values()) {
                if (s.id.equals(id)) {
                    return s;
                }
            }
            return null;
        }

        static Slot of(Material m) {
            String n = m.name();
            for (Slot s : values()) {
                if (n.endsWith("_" + s.piece)) {
                    return s;
                }
            }
            return CHEST;
        }
    }

    private static Material mat(String name, Material fallback) {
        Material m = Material.matchMaterial(name);
        return m == null ? fallback : m;
    }

    public static int tierForLevel(int level) {
        int t = 0;
        for (int i = 0; i < TIERS; i++) {
            if (level >= TIER_LEVEL[i]) {
                t = i;
            }
        }
        return t;
    }

    private static NamespacedKey key(String id) {
        return new NamespacedKey(Keys.ITEM.getNamespace(), id);
    }

    // ------------------------------------------------------------------ Création

    /** Arme vendue par Torvald : qualité Commune, une châsse. */
    public static ItemStack shopWeapon(Base b, int tier) {
        int t = clampTier(tier);
        return weapon(b, t, Rarity.COMMUN, TIER_LEVEL[t], TIER_LEVEL[t], null);
    }

    public static ItemStack shopShield(int tier) {
        int t = clampTier(tier);
        return shield(t, Rarity.COMMUN, TIER_LEVEL[t], TIER_LEVEL[t], null);
    }

    /** Pièce de plaques (ancienne signature). */
    public static ItemStack shopArmor(Slot s, int tier, Color dye) {
        return shopArmor(ArmorType.PLAQUES, s, tier, dye);
    }

    public static ItemStack shopArmor(ArmorType type, Slot s, int tier, Color dye) {
        int t = clampTier(tier);
        return armor(type, s, t, Rarity.COMMUN, TIER_LEVEL[t], TIER_LEVEL[t], dye, null);
    }

    /** Ensemble de plaques complet (ancienne signature). */
    public static ItemStack[] armorSet(int tier, Color dye) {
        return armorSet(ArmorType.PLAQUES, tier, dye);
    }

    /** Ensemble complet (casque, plastron, jambières, bottes) du type donné. */
    public static ItemStack[] armorSet(ArmorType type, int tier, Color dye) {
        ItemStack[] out = new ItemStack[4];
        for (Slot s : Slot.values()) {
            out[s.ordinal()] = shopArmor(type == null ? ArmorType.PLAQUES : type, s, tier, dye);
        }
        return out;
    }

    private static int clampTier(int t) {
        return Math.max(0, Math.min(TIERS - 1, t));
    }

    /**
     * @param statLevel niveau qui fixe la force des bonus (et un peu l'attaque)
     * @param req       niveau requis pour profiter de l'objet
     * @param rnd       null : aucun bonus aléatoire
     */
    public static ItemStack weapon(Base b, int tier, Rarity r, int statLevel, int req, Random rnd) {
        int t = clampTier(tier);
        double within = 1 + 0.01 * Math.max(0, Math.min(12, statLevel - TIER_LEVEL[t]));
        int atk = (int) Math.round(TIER_ATK[t] * b.type.atkMult * b.atkMult * r.atkMult * within);
        ItemStack it = new ItemStack(b.material(t));
        ItemMeta meta = it.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        if (b.family == Family.BOW) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.INFINITY, 1, true); // le carquois ne s'épuise jamais
        }
        if (b.family == Family.HOE || b.family == Family.PICKAXE) {
            // Houes et pioches frappent trop vite en vanilla : cadence d'une hache de guerre.
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(key("weapon_speed"), -3.1,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
        if (t >= 6 || r == Rarity.LEGENDAIRE) {
            meta.setEnchantmentGlintOverride(true);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.ITEM, PersistentDataType.STRING, "weapon");
        pdc.set(Keys.WEAPON, PersistentDataType.STRING, b.type.name());
        pdc.set(Keys.ATK, PersistentDataType.INTEGER, atk);
        Map<Stat, Double> stats = roll(b.implicit, 0.6, b.type.magic() ? CASTER_POOL : WEAPON_POOL, r, statLevel, rnd);
        write(pdc, r, t, req, b.id, title(b.label, TIER_NAMES[t], r, stats, b.implicit, rnd), stats, r.sockets);
        it.setItemMeta(meta);
        if (b.model != null) {
            Items.model(it, b.model + "_" + PACK_TIER[t]);
        }
        refresh(it);
        return it;
    }

    /** Pièce de plaques (ancienne signature). */
    public static ItemStack armor(Slot s, int tier, Rarity r, int statLevel, int req, Color dye, Random rnd) {
        return armor(ArmorType.PLAQUES, s, tier, r, statLevel, req, dye, rnd);
    }

    /**
     * Pièce d'armure : points d'armure selon le type (plaques 100 %, mailles 80 %, cuir 60 %, tissu 40 %),
     * bonus propre selon le type et la pièce, bonus aléatoires tirés dans la réserve du type.
     */
    public static ItemStack armor(ArmorType type, Slot s, int tier, Rarity r, int statLevel, int req, Color dye, Random rnd) {
        ArmorType ty = type == null ? ArmorType.PLAQUES : type;
        int t = clampTier(tier);
        int pts = Math.max(1, (int) Math.round(ARMOR_SET[t] * s.share * ty.armorMult * (1 + 0.04 * r.ordinal())));
        ItemStack it = new ItemStack(s.material(ty, t));
        ItemMeta meta = it.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DYE);
        meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(key("armor_" + s.id), pts,
                AttributeModifier.Operation.ADD_NUMBER, s.group));
        Color tint = ty.dye(t, dye);
        if (meta instanceof LeatherArmorMeta leather && tint != null) {
            leather.setColor(tint);
        }
        if (t >= 6 || r == Rarity.LEGENDAIRE) {
            meta.setEnchantmentGlintOverride(true);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.ITEM, PersistentDataType.STRING, "armor");
        pdc.set(Keys.ARMOR_PTS, PersistentDataType.INTEGER, pts);
        pdc.set(Keys.ARMOR_TYPE, PersistentDataType.STRING, ty.id);
        pdc.set(Keys.OUTFIT, PersistentDataType.STRING, ty.outfit(t));
        pdc.set(Keys.OUTFIT_BASE, PersistentDataType.STRING, ty.outfit(t));
        Stat implicit = ty.implicit(s);
        Map<Stat, Double> stats = roll(implicit, ty.implicitFactor * s.share / 0.25, ty.pool, r, statLevel, rnd);
        write(pdc, r, t, req, s.id, title(ty.piece(s), ty.tierName(t), r, stats, implicit, rnd), stats, 0);
        it.setItemMeta(meta);
        Items.model(it, s.model(ty, t));
        refresh(it);
        return it;
    }

    public static ItemStack shield(int tier, Rarity r, int statLevel, int req, Random rnd) {
        int t = clampTier(tier);
        ItemStack it = new ItemStack(Material.SHIELD);
        ItemMeta meta = it.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (meta instanceof org.bukkit.inventory.meta.ShieldMeta sm) {
            org.bukkit.DyeColor[] colors = {org.bukkit.DyeColor.BROWN, org.bukkit.DyeColor.ORANGE, org.bukkit.DyeColor.LIGHT_GRAY,
                    org.bukkit.DyeColor.YELLOW, org.bukkit.DyeColor.LIGHT_BLUE, org.bukkit.DyeColor.BLACK,
                    org.bukkit.DyeColor.PURPLE, org.bukkit.DyeColor.CYAN};
            sm.setBaseColor(colors[t]);
        }
        if (t >= 6 || r == Rarity.LEGENDAIRE) {
            meta.setEnchantmentGlintOverride(true);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.ITEM, PersistentDataType.STRING, "shield");
        Map<Stat, Double> stats = roll(Stat.PARADE, 1.0, SHIELD_POOL, r, statLevel, rnd);
        write(pdc, r, t, req, "shield", title("Bouclier", TIER_NAMES[t], r, stats, Stat.PARADE, rnd), stats, 0);
        it.setItemMeta(meta);
        refresh(it);
        return it;
    }

    /** Bonus propre au modèle, puis les bonus tirés au hasard selon la rareté. */
    private static Map<Stat, Double> roll(Stat implicit, double factor, Stat[] pool, Rarity r, int level, Random rnd) {
        Map<Stat, Double> out = new LinkedHashMap<>();
        out.put(implicit, round(implicit.value(level) * factor));
        if (rnd == null || r.affixes <= 0) {
            return out;
        }
        List<Stat> picks = new ArrayList<>(Arrays.asList(pool));
        picks.remove(implicit);
        Collections.shuffle(picks, rnd);
        for (int i = 0; i < Math.min(r.affixes, picks.size()); i++) {
            Stat s = picks.get(i);
            out.put(s, round(s.value(level) * r.statMult * (0.8 + rnd.nextDouble() * 0.4)));
        }
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static String title(String label, String tierName, Rarity r, Map<Stat, Double> stats, Stat implicit, Random rnd) {
        if (r == Rarity.LEGENDAIRE) {
            return label + " " + LEGEND[(rnd == null ? ThreadLocalRandom.current() : rnd).nextInt(LEGEND.length)];
        }
        Stat main = null;
        for (Stat s : stats.keySet()) {
            if (s != implicit) {
                main = s;
                break;
            }
        }
        return label + " " + tierName + (main == null ? "" : " " + main.suffix);
    }

    private static void write(PersistentDataContainer pdc, Rarity r, int t, int req, String base, String title,
                              Map<Stat, Double> stats, int sockets) {
        pdc.set(Keys.RARITY, PersistentDataType.STRING, r.name());
        pdc.set(Keys.TIER, PersistentDataType.INTEGER, t);
        pdc.set(Keys.ILVL, PersistentDataType.INTEGER, Math.max(0, req));
        pdc.set(Keys.BASE, PersistentDataType.STRING, base);
        pdc.set(Keys.TITLE, PersistentDataType.STRING, title);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Stat, Double> e : stats.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(e.getKey().mod).append('=').append(String.format(Locale.ROOT, "%.1f", e.getValue()));
        }
        pdc.set(Keys.STATS, PersistentDataType.STRING, sb.toString());
        if (!pdc.has(Keys.BIND)) {
            pdc.set(Keys.BIND, PersistentDataType.STRING, Binding.EQUIP);
        }
        if (sockets > 0) {
            pdc.set(Keys.SOCKETS, PersistentDataType.INTEGER, sockets);
            pdc.set(Keys.RUNES, PersistentDataType.STRING, ",".repeat(sockets - 1));
        }
    }

    // ------------------------------------------------------------------ Butin des boss

    /** Bonus de niveau d'objet du butin de boss. */
    public static final int BOSS_ITEM_LEVEL_BONUS = 5;

    /**
     * Récompense du dernier coup d'un boss : objet Épique nommé d'après le boss, lié quand ramassé
     * (à lier au joueur qui le reçoit : {@link Binding#onLoot}). L'armure prend le type de la classe.
     */
    public static ItemStack bossLab(BossDef boss, WeaponType held, ArmorType type, int level, Color dye) {
        Random rnd = ThreadLocalRandom.current();
        int t = tierForLevel(level);
        int req = Math.max(1, Math.min(level, TIER_LEVEL[t]));
        ItemStack it;
        if ("armor".equalsIgnoreCase(boss.labKind())) {
            Material m = boss.labMaterial() == null ? Material.LEATHER_CHESTPLATE : boss.labMaterial();
            Slot s = Slot.of(m);
            ArmorType ty = type == null ? ArmorType.PLAQUES : type;
            it = armor(ty, s, t, Rarity.EPIQUE, level + 2, req, dye, rnd);
            ItemMeta meta = it.getItemMeta();
            if (meta instanceof LeatherArmorMeta leather && ty == ArmorType.PLAQUES) {
                leather.setColor(Color.fromRGB(0x15161C));
            }
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            int pts = Math.max(pdc.getOrDefault(Keys.ARMOR_PTS, PersistentDataType.INTEGER, 1),
                    (int) Math.round(boss.labValue() * ty.armorMult));
            meta.removeAttributeModifier(Attribute.ARMOR);
            meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(key("armor_" + s.id), pts,
                    AttributeModifier.Operation.ADD_NUMBER, s.group));
            pdc.set(Keys.ARMOR_PTS, PersistentDataType.INTEGER, pts);
            pdc.set(Keys.TITLE, PersistentDataType.STRING, boss.labName());
            pdc.set(Keys.FLAVOR, PersistentDataType.STRING, "<#FFD66E>Butin de boss · " + Text.esc(boss.name()));
            pdc.set(Keys.BIND, PersistentDataType.STRING, Binding.PICKUP);
            pdc.set(Keys.ILVL_BONUS, PersistentDataType.INTEGER, BOSS_ITEM_LEVEL_BONUS);
            it.setItemMeta(meta);
        } else {
            Base b = Base.defaultFor(held);
            it = weapon(b, t, Rarity.EPIQUE, level + 2, req, rnd);
            ItemMeta meta = it.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(Keys.ATK, PersistentDataType.INTEGER, pdc.getOrDefault(Keys.ATK, PersistentDataType.INTEGER, 10) + boss.labValue());
            pdc.set(Keys.TITLE, PersistentDataType.STRING, boss.labName());
            pdc.set(Keys.FLAVOR, PersistentDataType.STRING, "<#FFD66E>Butin de boss · " + Text.esc(boss.name()));
            pdc.set(Keys.BIND, PersistentDataType.STRING, Binding.PICKUP);
            pdc.set(Keys.ILVL_BONUS, PersistentDataType.INTEGER, BOSS_ITEM_LEVEL_BONUS);
            it.setItemMeta(meta);
        }
        refresh(it);
        return it;
    }

    // ------------------------------------------------------------------ Butin aléatoire

    public static Rarity rollRarity(Random rnd, boolean boss) {
        double x = rnd.nextDouble() * 100;
        if (boss) {
            return x < 5 ? Rarity.LEGENDAIRE : x < 40 ? Rarity.EPIQUE : Rarity.RARE;
        }
        return x < 0.5 ? Rarity.LEGENDAIRE : x < 5 ? Rarity.EPIQUE : x < 30 ? Rarity.RARE : Rarity.INHABITUEL;
    }

    /** Ancienne signature : armure en plaques. */
    public static ItemStack randomGear(int level, Rarity r, List<WeaponType> prefer, boolean shieldUser, Color dye, Random rnd) {
        return randomGear(level, r, prefer, shieldUser, ArmorType.PLAQUES, dye, rnd);
    }

    /** Arme (de préférence de la classe), pièce d'armure (du type de la classe) ou bouclier, du niveau du monstre. */
    public static ItemStack randomGear(int level, Rarity r, List<WeaponType> prefer, boolean shieldUser, ArmorType type, Color dye, Random rnd) {
        int lvl = Math.max(1, level);
        int t = tierForLevel(lvl);
        int req = Math.max(TIER_LEVEL[t], Math.min(lvl - 2, OrbitMMO.getPlugin(OrbitMMO.class).progression().maxLevel()));
        double x = rnd.nextDouble();
        if (x < 0.5) {
            List<Base> pool = new ArrayList<>();
            if (prefer != null) {
                for (WeaponType wt : prefer) {
                    pool.addAll(Base.of(wt));
                }
            }
            if (pool.isEmpty() || rnd.nextDouble() < 0.3) {
                pool = Arrays.asList(Base.values());
            }
            return weapon(pool.get(rnd.nextInt(pool.size())), t, r, lvl, req, rnd);
        }
        if (x < 0.58 && shieldUser) {
            return shield(t, r, lvl, req, rnd);
        }
        Slot s = Slot.values()[rnd.nextInt(Slot.values().length)];
        ArmorType ty = type != null ? type : ArmorType.values()[rnd.nextInt(ArmorType.values().length)];
        return armor(ty, s, t, r, lvl, req, dye, rnd);
    }

    public static Rune.Ref randomRune(Random rnd, boolean boss) {
        List<Rune> stat = new ArrayList<>();
        List<Rune> elem = new ArrayList<>();
        for (Rune r : Rune.values()) {
            (r.elemental() ? elem : stat).add(r);
        }
        List<Rune> pool = rnd.nextDouble() < 0.25 ? elem : stat;
        double x = rnd.nextDouble();
        int rank = boss ? (x < 0.10 ? 3 : x < 0.80 ? 2 : 1) : (x < 0.01 ? 3 : x < 0.15 ? 2 : 1);
        return new Rune.Ref(pool.get(rnd.nextInt(pool.size())), rank);
    }

    /** Ancienne signature (hors des étages du Pilier). */
    public static void rollMobLoot(OrbitMMO plugin, Player p, PlayerData d, int level) {
        rollMobLoot(plugin, p, d, level, 0);
    }

    /** Monstre tué : petite chance d'équipement et de rune ; dans le Pilier, pièces des sets « donjon » de l'étage. */
    public static void rollMobLoot(OrbitMMO plugin, Player p, PlayerData d, int level, int floor) {
        Random rnd = ThreadLocalRandom.current();
        if (rnd.nextDouble() < plugin.getConfig().getDouble("butin.equipement", 0.06)) {
            Rarity r = rollRarity(rnd, false);
            give(plugin, p, randomFor(plugin, d, level, r, rnd), r);
        }
        if (rnd.nextDouble() < plugin.getConfig().getDouble("butin.runes", 0.025)) {
            Rune.Ref ref = randomRune(rnd, false);
            give(plugin, p, ref.rune().item(ref.rank()), ref.rank() >= 3 ? Rarity.EPIQUE : ref.rank() == 2 ? Rarity.RARE : Rarity.INHABITUEL);
        }
        if (floor > 0 && plugin.sets() != null) {
            ItemStack piece = plugin.sets().rollFloor(floor, classDye(plugin, d), rnd);
            if (piece != null) {
                Binding.onLoot(piece, p);
                give(plugin, p, piece, rarity(piece));
            }
        }
    }

    /** Ancienne signature (sans boss précis). */
    public static void rollBossLoot(OrbitMMO plugin, Player p, PlayerData d, int level) {
        rollBossLoot(plugin, p, d, level, null);
    }

    /**
     * Boss vaincu : chaque participant reçoit un objet Rare ou mieux (lié quand ramassé), parfois une rune,
     * et peut-être une pièce d'un set « boss » de ce boss.
     */
    public static void rollBossLoot(OrbitMMO plugin, Player p, PlayerData d, int level, String bossId) {
        Random rnd = ThreadLocalRandom.current();
        Rarity r = rollRarity(rnd, true);
        ItemStack gear = randomFor(plugin, d, level + 2, r, rnd);
        ItemMeta meta = gear.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.BIND, PersistentDataType.STRING, Binding.PICKUP);
        meta.getPersistentDataContainer().set(Keys.ILVL_BONUS, PersistentDataType.INTEGER, BOSS_ITEM_LEVEL_BONUS);
        gear.setItemMeta(meta);
        Binding.onLoot(gear, p);
        give(plugin, p, gear, r);
        if (rnd.nextDouble() < 0.4) {
            Rune.Ref ref = randomRune(rnd, true);
            give(plugin, p, ref.rune().item(ref.rank()), ref.rank() >= 3 ? Rarity.EPIQUE : Rarity.RARE);
        }
        if (plugin.sets() != null) {
            ItemStack piece = plugin.sets().rollBoss(bossId, classDye(plugin, d), rnd);
            if (piece != null) {
                Binding.onLoot(piece, p);
                give(plugin, p, piece, rarity(piece));
            }
        }
    }

    private static Color classDye(OrbitMMO plugin, PlayerData d) {
        ClassDef c = plugin.progression().cls(d);
        return c == null ? null : fr.orbit.mmo.util.Fx.color(c.hex);
    }

    private static ItemStack randomFor(OrbitMMO plugin, PlayerData d, int level, Rarity r, Random rnd) {
        ClassDef c = plugin.progression().cls(d);
        SpecDef s = plugin.progression().spec(d);
        List<WeaponType> prefer = new ArrayList<>();
        boolean shield = false;
        Color dye = null;
        ArmorType type = null;
        if (c != null) {
            prefer.add(c.weapon);
            shield = c.shield;
            dye = fr.orbit.mmo.util.Fx.color(c.hex);
            type = ArmorType.of(c);
        }
        if (s != null) {
            if (s.weapon != null && !prefer.contains(s.weapon)) {
                prefer.add(s.weapon);
            }
            shield |= s.shield;
        }
        return randomGear(level, r, prefer, shield, type, dye, rnd);
    }

    private static void give(OrbitMMO plugin, Player p, ItemStack it, Rarity r) {
        Items.give(p, it);
        Component name = it.displayName();
        p.sendMessage(Text.mm(Text.PREFIX + "<#8C96A3>Butin : ").append(name));
        if (r.atLeast(Rarity.RARE)) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, r.atLeast(Rarity.EPIQUE) ? 1.6f : 1.9f);
        } else {
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
        }
        if (r == Rarity.LEGENDAIRE && "rune".equals(Items.type(it)) == false) {
            Bukkit.broadcast(Text.mm(Text.PREFIX + "<#FF8A3B><b>✦ " + Text.esc(p.getName()) + "</b> a trouvé un objet légendaire : ").append(name));
            Text.title(p, "<gradient:#FFD66E:#FF8A3B><b>Légendaire !</b></gradient>", "", 5, 50, 15);
        } else if (r == Rarity.EPIQUE) {
            for (Player o : p.getWorld().getPlayers()) {
                if (o != p) {
                    o.sendMessage(Text.mm(Text.PREFIX + "<#B36BFF>" + Text.esc(p.getName()) + " a obtenu : ").append(name));
                }
            }
        }
    }

    // ------------------------------------------------------------------ Lecture

    public static boolean isNew(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(Keys.RARITY);
    }

    private static <T, Z> Z get(ItemStack it, NamespacedKey k, PersistentDataType<T, Z> type) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(k, type);
    }

    public static Rarity rarity(ItemStack it) {
        return Rarity.of(get(it, Keys.RARITY, PersistentDataType.STRING));
    }

    public static int tier(ItemStack it) {
        Integer t = get(it, Keys.TIER, PersistentDataType.INTEGER);
        return t == null ? 0 : clampTier(t);
    }

    /** Niveau requis pour profiter des bonus de l'objet (0 : aucun). */
    public static int requiredLevel(ItemStack it) {
        Integer v = get(it, Keys.ILVL, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public static Map<String, Double> stats(ItemStack it) {
        Map<String, Double> out = new LinkedHashMap<>();
        String raw = get(it, Keys.STATS, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String part : raw.split(";")) {
            int i = part.indexOf('=');
            if (i <= 0) {
                continue;
            }
            try {
                out.merge(part.substring(0, i), Double.parseDouble(part.substring(i + 1)), Double::sum);
            } catch (NumberFormatException ignored) {
                // valeur invalide
            }
        }
        return out;
    }

    public static int sockets(ItemStack it) {
        Integer v = get(it, Keys.SOCKETS, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    /** Runes serties, une entrée par châsse (null : châsse vide). */
    public static List<Rune.Ref> runes(ItemStack it) {
        int n = sockets(it);
        List<Rune.Ref> out = new ArrayList<>(n);
        String raw = get(it, Keys.RUNES, PersistentDataType.STRING);
        String[] parts = raw == null ? new String[0] : raw.split(",", -1);
        for (int i = 0; i < n; i++) {
            out.add(i < parts.length ? Rune.parse(parts[i]) : null);
        }
        return out;
    }

    public static void setRunes(ItemStack it, List<Rune.Ref> runes) {
        ItemMeta meta = it.getItemMeta();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < runes.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            Rune.Ref r = runes.get(i);
            if (r != null) {
                sb.append(r.key());
            }
        }
        meta.getPersistentDataContainer().set(Keys.RUNES, PersistentDataType.STRING, sb.toString());
        it.setItemMeta(meta);
        refresh(it);
    }

    /** Prix de référence (forge : améliorations) d'un objet au nouveau format. */
    public static double priceBase(ItemStack it) {
        int t = tier(it);
        String kind = Items.type(it);
        long base = "armor".equals(kind) || "lab".equals(kind) ? ARMOR_PRICE[t] * 4 : "shield".equals(kind) ? SHIELD_PRICE[t] : WEAPON_PRICE[t];
        return base * (1 + 0.25 * rarity(it).ordinal());
    }

    /** Rachat par le forgeron : un cinquième du prix, selon la rareté, plus les runes serties. */
    public static long sellPrice(ItemStack it) {
        int t = tier(it);
        String kind = Items.type(it);
        long base = "armor".equals(kind) ? ARMOR_PRICE[t] : "shield".equals(kind) ? SHIELD_PRICE[t] : WEAPON_PRICE[t];
        long runes = 0;
        for (Rune.Ref r : runes(it)) {
            if (r != null) {
                runes += r.rune().sellPrice(r.rank());
            }
        }
        return Math.max(3, Math.round(base / 5.0 * rarity(it).priceMult)) + runes;
    }

    // ------------------------------------------------------------------ Bonus portés

    /** Ajoute aux bonus du personnage ceux de l'arme, de l'armure portée et du bouclier (si le niveau requis est atteint). */
    public static void addGearMods(Mods m, Player p, PlayerData d, ItemStack weapon) {
        for (ItemStack it : worn(p, weapon)) {
            if (!isNew(it) || requiredLevel(it) > d.level) {
                continue;
            }
            m.add(stats(it), 1);
            for (Rune.Ref r : runes(it)) {
                if (r != null && r.rune().stat != null) {
                    m.add(Map.of(r.rune().stat.mod, r.rune().value(r.rank())), 1);
                }
            }
        }
        // Bonus de set : 2, 4, 5 pièces portées (au niveau requis).
        OrbitMMO plugin = OrbitMMO.getPlugin(OrbitMMO.class);
        if (plugin.sets() == null) {
            return;
        }
        for (Map.Entry<GearSet, Integer> e : setCounts(p, d, weapon).entrySet()) {
            for (Map.Entry<Integer, Map<String, Double>> b : e.getKey().bonuses.entrySet()) {
                if (e.getValue() >= b.getKey()) {
                    m.add(b.getValue(), 1);
                }
            }
        }
    }

    /** Pièces de chaque set portées (une par emplacement, niveau requis atteint). */
    public static Map<GearSet, Integer> setCounts(Player p, PlayerData d, ItemStack weapon) {
        Map<GearSet, Integer> out = new LinkedHashMap<>();
        OrbitMMO plugin = OrbitMMO.getPlugin(OrbitMMO.class);
        if (plugin.sets() == null) {
            return out;
        }
        for (ItemStack it : worn(p, weapon)) {
            GearSet s = plugin.sets().of(it);
            if (s != null && requiredLevel(it) <= d.level) {
                out.merge(s, 1, Integer::sum);
            }
        }
        return out;
    }

    /** Objets portés : arme active, 4 pièces d'armure, bouclier en main secondaire. */
    public static List<ItemStack> worn(Player p, ItemStack weapon) {
        PlayerInventory inv = p.getInventory();
        List<ItemStack> out = new ArrayList<>(6);
        if (weapon != null && "weapon".equals(Items.type(weapon))) {
            out.add(weapon);
        }
        for (ItemStack a : inv.getArmorContents()) {
            if (a != null && ("armor".equals(Items.type(a)) || "lab".equals(Items.type(a)))) {
                out.add(a);
            }
        }
        ItemStack off = inv.getItemInOffHand();
        if ("shield".equals(Items.type(off))) {
            out.add(off);
        }
        return out;
    }

    /** Empreinte de l'équipement porté : quand elle change, les bonus sont recalculés. */
    public static int signature(Player p, ItemStack weapon) {
        int h = weapon == null ? 0 : weapon.hashCode();
        PlayerInventory inv = p.getInventory();
        for (ItemStack a : inv.getArmorContents()) {
            h = 31 * h + (a == null ? 0 : a.hashCode());
        }
        ItemStack off = inv.getItemInOffHand();
        return 31 * h + off.hashCode();
    }

    // ------------------------------------------------------------------ Nom et description

    /** Réécrit le nom et la description à partir des données de l'objet. */
    public static void refresh(ItemStack it) {
        if (!isNew(it)) {
            return;
        }
        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Rarity r = Rarity.of(pdc.get(Keys.RARITY, PersistentDataType.STRING));
        String kind = pdc.getOrDefault(Keys.ITEM, PersistentDataType.STRING, "weapon");
        String title = Text.esc(pdc.getOrDefault(Keys.TITLE, PersistentDataType.STRING, "Équipement"));
        int t = clampTier(pdc.getOrDefault(Keys.TIER, PersistentDataType.INTEGER, 0));
        int upgrade = pdc.getOrDefault(Keys.UPGRADE, PersistentDataType.INTEGER, 0);
        String name = switch (r) {
            case LEGENDAIRE -> "<gradient:#FFD66E:#FF8A3B><b>" + title + "</b></gradient>";
            case EPIQUE -> "<" + r.hex + "><b>" + title + "</b>";
            default -> "<" + r.hex + ">" + title;
        };
        meta.displayName(Text.item(name + (upgrade > 0 ? " <#FFD66E><b>+" + upgrade : "")));

        List<String> lore = new ArrayList<>();
        boolean weapon = "weapon".equals(kind);
        if (weapon) {
            Base b = Base.byId(pdc.get(Keys.BASE, PersistentDataType.STRING));
            WeaponType wt = WeaponType.byName(pdc.get(Keys.WEAPON, PersistentDataType.STRING));
            String type = b != null ? b.label + " <#5A6270>(" + b.type.label + ")" : wt != null ? wt.label : "Arme";
            lore.add(r.tag() + " <#5A6270>· <#8C96A3>" + type);
            lore.add("<#8C96A3>Attaque : <white>" + pdc.getOrDefault(Keys.ATK, PersistentDataType.INTEGER, 0)
                    + " <#5A6270>· " + TIER_LABELS[t]);
        } else if ("shield".equals(kind)) {
            lore.add(r.tag() + " <#5A6270>· <#8C96A3>Bouclier (main secondaire)");
            lore.add("<#8C96A3>Matériau : <white>" + TIER_LABELS[t]);
        } else {
            Slot s = Slot.of(it.getType());
            ArmorType at = armorType(it);
            lore.add(r.tag() + " <#5A6270>· <#8C96A3>Armure " + (at == null ? "" : "de " + at.tag().toLowerCase(Locale.ROOT) + " ")
                    + "<#5A6270>· <#8C96A3>" + s.label);
            lore.add("<#8C96A3>Armure : <white>" + pdc.getOrDefault(Keys.ARMOR_PTS, PersistentDataType.INTEGER, 0)
                    + " <#5A6270>· " + TIER_LABELS[t]);
        }
        lore.add("<#8C96A3>Niveau d'objet : <#FFD66E>" + itemLevel(it));
        Map<String, Double> stats = stats(it);
        if (!stats.isEmpty()) {
            lore.add("");
            for (Map.Entry<String, Double> e : stats.entrySet()) {
                Stat s = Stat.byMod(e.getKey());
                if (s != null) {
                    lore.add("<#7CFF8A>" + s.line(e.getValue()));
                }
            }
        }
        List<Rune.Ref> runes = runes(it);
        if (!runes.isEmpty()) {
            lore.add("");
            for (Rune.Ref ref : runes) {
                if (ref == null) {
                    lore.add("<#5A6270>◇ Châsse vide <#3E4450>(Ysolde, la runiste)");
                } else {
                    lore.add("<#D7DCE2>◆ " + ref.rune().tag(ref.rank()));
                    lore.add("  <#9AD8FF>" + ref.rune().effect(ref.rank()));
                }
            }
        }
        if (upgrade > 0) {
            lore.add("");
            lore.add(Gear.bonusLine(it));
        }
        OrbitMMO plugin = OrbitMMO.getPlugin(OrbitMMO.class);
        GearSet set = plugin.sets() == null ? null : plugin.sets().of(it);
        if (set != null) {
            lore.add("");
            lore.add("<#FFD66E>Set : " + Text.esc(set.name) + " <#5A6270>(" + set.pieces.size() + " pièces)");
            lore.addAll(set.describe(-1));
        }
        String look = pdc.get(Keys.OUTFIT, PersistentDataType.STRING);
        String base = pdc.get(Keys.OUTFIT_BASE, PersistentDataType.STRING);
        if (look != null && base != null && !look.equals(base)) {
            lore.add("");
            lore.add("<#B9A6FF>Apparence : <white>" + Wardrobe.lookLabel(look) + " <#5A6270>(garde-robe)");
        }
        lore.add("");
        int req = pdc.getOrDefault(Keys.ILVL, PersistentDataType.INTEGER, 0);
        if (req > 1) {
            lore.add("<#8C96A3>Niveau requis : <white>" + req + " <#5A6270>(bonus inactifs en dessous)");
        }
        String bind = Binding.line(it);
        if (bind != null) {
            lore.add(bind);
        }
        String flavor = pdc.get(Keys.FLAVOR, PersistentDataType.STRING);
        if (flavor != null) {
            lore.add(flavor);
        } else if (r == Rarity.LEGENDAIRE) {
            lore.add("<#FF8A3B><i>« " + LEGEND_LORE[Math.floorMod(title.hashCode(), LEGEND_LORE.length)] + " »");
        }
        if (weapon) {
            lore.add(CONTROLS_1);
            lore.add(CONTROLS_2);
        }
        while (!lore.isEmpty() && lore.get(lore.size() - 1).isEmpty()) {
            lore.remove(lore.size() - 1);
        }
        meta.lore(Text.lore(lore));
        it.setItemMeta(meta);
    }

    // ------------------------------------------------------------------ Anciens objets

    /**
     * Passe une arme ou une armure d'avant les raretés au nouveau format (Commune, une châsse pour une arme ;
     * Épique pour un butin de boss), sans changer ses valeurs. Renvoie true si l'objet a changé.
     */
    public static boolean adopt(ItemStack it) {
        if (it == null || it.isEmpty() || isNew(it)) {
            return false;
        }
        String kind = Items.type(it);
        if (!"weapon".equals(kind) && !"armor".equals(kind) && !"lab".equals(kind)) {
            return false;
        }
        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String name;
        String stored = pdc.get(Keys.BASE_NAME, PersistentDataType.STRING);
        if (stored != null) {
            name = PLAIN.serialize(GSON.deserialize(stored));
        } else if (meta.hasDisplayName()) {
            name = PLAIN.serialize(meta.displayName());
        } else {
            name = "Équipement";
        }
        boolean boss = "lab".equals(kind);
        if (!boss && meta.lore() != null) {
            for (Component c : meta.lore()) {
                if (PLAIN.serialize(c).contains("Butin de boss")) {
                    boss = true;
                    break;
                }
            }
        }
        Rarity r = boss ? Rarity.EPIQUE : Rarity.COMMUN;
        int tier;
        if ("weapon".equals(kind)) {
            int old = WeaponType.of(it).tierOf(it.getType());
            tier = new int[]{0, 1, 2, 4}[Math.max(0, Math.min(3, old))];
        } else {
            String n = it.getType().name();
            tier = n.startsWith("NETHERITE") ? 5 : n.startsWith("DIAMOND") ? 4 : n.startsWith("IRON") ? 2
                    : n.startsWith("CHAINMAIL") || n.startsWith("GOLDEN") ? 1 : 0;
            pdc.set(Keys.ARMOR_PTS, PersistentDataType.INTEGER, (int) Math.round(Gear.armorPointsOf(it)));
        }
        pdc.set(Keys.RARITY, PersistentDataType.STRING, r.name());
        pdc.set(Keys.TIER, PersistentDataType.INTEGER, tier);
        pdc.set(Keys.ILVL, PersistentDataType.INTEGER, 0);
        pdc.set(Keys.TITLE, PersistentDataType.STRING, name);
        pdc.set(Keys.STATS, PersistentDataType.STRING, "");
        if (boss) {
            pdc.set(Keys.FLAVOR, PersistentDataType.STRING, "<#FFD66E>Butin de boss");
        }
        if ("weapon".equals(kind)) {
            pdc.set(Keys.SOCKETS, PersistentDataType.INTEGER, r.sockets);
            pdc.set(Keys.RUNES, PersistentDataType.STRING, ",".repeat(r.sockets - 1));
        }
        it.setItemMeta(meta);
        refresh(it);
        return true;
    }

    /**
     * Refonte du 24/11 (types d'armure, liaison, garde-robe) : une armure sans type devient des plaques
     * (tout ce qui existait était porté par des Guerriers), avec sa clé de tenue et son icône HD ;
     * le butin de boss devient « lié quand ramassé » et se lie au joueur qui l'a ; le reste « lié quand équipé ».
     * Renvoie true si l'objet a changé.
     */
    public static boolean upgrade(ItemStack it, Player holder) {
        if (!isNew(it) || !Gear.isGear(it) && !"shield".equals(Items.type(it))) {
            return false;
        }
        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(Keys.BIND) && (pdc.has(Keys.ARMOR_TYPE) || !Gear.isArmorPiece(it))) {
            return false;
        }
        int t = clampTier(pdc.getOrDefault(Keys.TIER, PersistentDataType.INTEGER, 0));
        if (Gear.isArmorPiece(it) && !pdc.has(Keys.ARMOR_TYPE)) {
            pdc.set(Keys.ARMOR_TYPE, PersistentDataType.STRING, ArmorType.PLAQUES.id);
            pdc.set(Keys.OUTFIT, PersistentDataType.STRING, ArmorType.PLAQUES.outfit(t));
            pdc.set(Keys.OUTFIT_BASE, PersistentDataType.STRING, ArmorType.PLAQUES.outfit(t));
        }
        boolean bound = false;
        if (!pdc.has(Keys.BIND)) {
            String flavor = pdc.get(Keys.FLAVOR, PersistentDataType.STRING);
            boolean boss = "lab".equals(Items.type(it)) || (flavor != null && flavor.contains("Butin de boss"));
            pdc.set(Keys.BIND, PersistentDataType.STRING, boss ? Binding.PICKUP : Binding.EQUIP);
            if (boss) {
                pdc.set(Keys.ILVL_BONUS, PersistentDataType.INTEGER, BOSS_ITEM_LEVEL_BONUS);
                bound = holder != null;
            }
        }
        it.setItemMeta(meta);
        if (Gear.isArmorPiece(it)) {
            Items.model(it, Slot.of(it.getType()).model(ArmorType.PLAQUES, t));
        }
        if (bound) {
            Binding.bind(it, holder);
        } else {
            refresh(it);
        }
        return true;
    }

    /** À la connexion : convertit les anciennes armes et armures du joueur (raretés, puis types d'armure et liaison). */
    public static void migrate(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            boolean changed = adopt(it);
            changed |= upgrade(it, p);
            if (changed) {
                inv.setItem(i, it);
            }
        }
    }

    // ------------------------------------------------------------------ Type d'armure et niveau d'objet

    /** Type d'une pièce d'armure (null : pas une armure du MMO). Une armure d'avant la refonte compte comme plaques. */
    public static ArmorType armorType(ItemStack it) {
        if (!isNew(it) || !Gear.isArmorPiece(it)) {
            return null;
        }
        ArmorType t = ArmorType.byId(it.getItemMeta().getPersistentDataContainer().get(Keys.ARMOR_TYPE, PersistentDataType.STRING));
        return t == null ? ArmorType.PLAQUES : t;
    }

    private static final int[] RARITY_ITEM_LEVEL = {0, 4, 8, 13, 20};

    /**
     * Niveau d'objet : 20 + 20 par palier (recrue 20 → orbitium 160), + rareté (0 / 4 / 8 / 13 / 20),
     * + niveau dans le palier (0 à 10, d'après le niveau requis), + 3 par amélioration (+1 à +10),
     * + bonus de l'objet (butin de boss +5, sets). 0 : pas un équipement du MMO.
     */
    public static int itemLevel(ItemStack it) {
        if (!isNew(it)) {
            return 0;
        }
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        int t = clampTier(pdc.getOrDefault(Keys.TIER, PersistentDataType.INTEGER, 0));
        int req = pdc.getOrDefault(Keys.ILVL, PersistentDataType.INTEGER, 0);
        int within = Math.max(0, Math.min(10, req - TIER_LEVEL[t]));
        int upgrade = pdc.getOrDefault(Keys.UPGRADE, PersistentDataType.INTEGER, 0);
        int bonus = pdc.getOrDefault(Keys.ILVL_BONUS, PersistentDataType.INTEGER, 0);
        Rarity r = Rarity.of(pdc.get(Keys.RARITY, PersistentDataType.STRING));
        return 20 + 20 * t + RARITY_ITEM_LEVEL[r.ordinal()] + within + 3 * upgrade + bonus;
    }

    /** Le personnage se sert-il d'un bouclier (classe ou spécialisation) ? */
    public static boolean shieldUser(OrbitMMO plugin, PlayerData d) {
        ClassDef c = plugin.progression().cls(d);
        SpecDef spec = plugin.progression().spec(d);
        if (c == null) {
            return false;
        }
        return spec != null && spec.weapon != null ? spec.shield : c.shield || (spec != null && spec.shield);
    }

    /**
     * Niveau d'objet moyen : arme active + 4 pièces d'armure (+ main secondaire pour une classe à bouclier) ;
     * un emplacement vide compte 0.
     */
    public static int averageItemLevel(OrbitMMO plugin, Player p, PlayerData d) {
        ItemStack weapon = plugin.bar().weapon(p, d);
        int sum = "weapon".equals(Items.type(weapon)) ? itemLevel(weapon) : 0;
        int slots = 5;
        for (ItemStack a : p.getInventory().getArmorContents()) {
            sum += itemLevel(a);
        }
        if (shieldUser(plugin, d)) {
            slots++;
            ItemStack off = p.getInventory().getItemInOffHand();
            sum += "shield".equals(Items.type(off)) ? itemLevel(off) : 0;
        }
        return (int) Math.floor(sum / (double) slots);
    }

    /**
     * Niveau d'objet minimum pour entrer (config « niveau-objet.pilier » / « niveau-objet.raid », 0 = pas de minimum).
     * Renvoie le message de refus, ou null si l'entrée est permise.
     */
    public static String itemLevelGate(OrbitMMO plugin, Player p, PlayerData d, String what) {
        int min = plugin.getConfig().getInt("niveau-objet." + what, 0);
        if (min <= 0 || p.hasPermission("orbitmmo.admin")) {
            return null;
        }
        int mine = averageItemLevel(plugin, p, d);
        if (mine >= min) {
            return null;
        }
        return "Niveau d'objet " + min + " requis (le vôtre : " + mine + "). Améliorez votre équipement chez Torvald.";
    }
}
