package fr.orbit.mmo;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Clés des données persistantes posées sur les items et les entités. */
public final class Keys {

    /** Entité : identifiant du monstre (MobDef). */
    public static NamespacedKey MOB;
    /** Item : type d'objet MMO (weapon, potion, heal_crystal, tp_crystal, material, lab, spell). */
    public static NamespacedKey ITEM;
    /** Item : type d'arme (WeaponType). */
    public static NamespacedKey WEAPON;
    /** Item : attaque de l'arme. */
    public static NamespacedKey ATK;
    /** Item : prix de revente en pièces d'or. */
    public static NamespacedKey SELL;
    /** Entité : PNJ / étiquette de décor (merchant, label). */
    public static NamespacedKey NPC;
    /** Modificateur de vitesse lié aux talents. */
    public static NamespacedKey SPEED;
    /** Modificateur d'armure des récompenses de boss. */
    public static NamespacedKey LAB_ARMOR;
    /** Item de la barre de sorts : identifiant du sort. */
    public static NamespacedKey SPELL;
    /** Arme / armure : niveau d'amélioration du forgeron (+1 à +10). */
    public static NamespacedKey UPGRADE;
    /** Arme / armure : usure (0 = neuve). */
    public static NamespacedKey WEAR;
    /** Arme / armure : nom et description d'origine (JSON), avant l'amélioration. */
    public static NamespacedKey BASE_NAME;
    public static NamespacedKey BASE_LORE;
    /** Équipement (nouveau format, voir item/Loot) : rareté, palier 0-7, niveau requis, modèle, nom sans couleur. */
    public static NamespacedKey RARITY;
    public static NamespacedKey TIER;
    public static NamespacedKey ILVL;
    public static NamespacedKey BASE;
    public static NamespacedKey TITLE;
    /** Bonus de l'objet (« crit=2.4;degats=3.1 »), points d'armure, ligne d'histoire. */
    public static NamespacedKey STATS;
    public static NamespacedKey ARMOR_PTS;
    public static NamespacedKey FLAVOR;
    /** Arme : nombre de châsses et runes serties (« feu:2,,puissance:1 »). */
    public static NamespacedKey SOCKETS;
    public static NamespacedKey RUNES;
    /** Objet rune : « feu:2 ». */
    public static NamespacedKey RUNE;
    /** Item lié : UUID du propriétaire (cristal de téléportation). */
    public static NamespacedKey OWNER;
    /** Entité : aubergiste, id du lieu (capitale ou village) de son auberge. */
    public static NamespacedKey INN;
    /** Groupe de recharge du cristal de téléportation (affiché sur l'icône). */
    public static NamespacedKey TP_CRYSTAL_COOLDOWN;
    /** Armure : type (tissu, cuir, mailles, plaques ; voir item/ArmorType). */
    public static NamespacedKey ARMOR_TYPE;
    /** Armure : apparence affichée « plaques_3 » (lue par le paquet outfit) et apparence d'origine (garde-robe). */
    public static NamespacedKey OUTFIT;
    public static NamespacedKey OUTFIT_BASE;
    /** Équipement : id du set (sets.yml). */
    public static NamespacedKey SET;
    /** Équipement : liaison « ramasse » (lié quand ramassé) ou « equipe » (lié quand équipé) ; lié = OWNER posé. */
    public static NamespacedKey BIND;
    /** Équipement lié : pseudo du propriétaire au moment de la liaison (affichage). */
    public static NamespacedKey BOUND_NAME;
    /** Équipement : bonus de niveau d'objet (butin de boss, sets). */
    public static NamespacedKey ILVL_BONUS;

    private Keys() {
    }

    static void init(Plugin plugin) {
        MOB = new NamespacedKey(plugin, "mob");
        ITEM = new NamespacedKey(plugin, "item");
        WEAPON = new NamespacedKey(plugin, "weapon");
        ATK = new NamespacedKey(plugin, "atk");
        SELL = new NamespacedKey(plugin, "sell");
        NPC = new NamespacedKey(plugin, "npc");
        SPEED = new NamespacedKey(plugin, "speed");
        LAB_ARMOR = new NamespacedKey(plugin, "lab_armor");
        SPELL = new NamespacedKey(plugin, "spell");
        UPGRADE = new NamespacedKey(plugin, "upgrade");
        WEAR = new NamespacedKey(plugin, "wear");
        BASE_NAME = new NamespacedKey(plugin, "base_name");
        BASE_LORE = new NamespacedKey(plugin, "base_lore");
        RARITY = new NamespacedKey(plugin, "rarity");
        TIER = new NamespacedKey(plugin, "tier");
        ILVL = new NamespacedKey(plugin, "ilvl");
        BASE = new NamespacedKey(plugin, "base");
        TITLE = new NamespacedKey(plugin, "title");
        STATS = new NamespacedKey(plugin, "stats");
        ARMOR_PTS = new NamespacedKey(plugin, "armor_pts");
        FLAVOR = new NamespacedKey(plugin, "flavor");
        SOCKETS = new NamespacedKey(plugin, "sockets");
        RUNES = new NamespacedKey(plugin, "runes");
        RUNE = new NamespacedKey(plugin, "rune");
        OWNER = new NamespacedKey(plugin, "owner");
        INN = new NamespacedKey(plugin, "inn");
        TP_CRYSTAL_COOLDOWN = new NamespacedKey(plugin, "tp_crystal");
        ARMOR_TYPE = new NamespacedKey(plugin, "armor_type");
        OUTFIT = new NamespacedKey(plugin, "outfit");
        OUTFIT_BASE = new NamespacedKey(plugin, "outfit_base");
        SET = new NamespacedKey(plugin, "set");
        BIND = new NamespacedKey(plugin, "bind");
        BOUND_NAME = new NamespacedKey(plugin, "bound_name");
        ILVL_BONUS = new NamespacedKey(plugin, "ilvl_bonus");
    }

    /** Groupe de recharge (affiché sur l'icône de la barre) d'un sort. */
    public static NamespacedKey cooldownGroup(String spellId) {
        return new NamespacedKey(ITEM.getNamespace(), "sort_" + spellId);
    }
}
