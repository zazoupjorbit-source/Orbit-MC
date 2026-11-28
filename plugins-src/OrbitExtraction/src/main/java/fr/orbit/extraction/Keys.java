package fr.orbit.extraction;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Clés de PersistentDataContainer. */
public final class Keys {

    /** Id de l'arme (guns.yml). */
    public static NamespacedKey GUN;
    /** Identifiant unique de l'exemplaire d'arme (son chargeur est suivi par MagStore). */
    public static NamespacedKey GUN_UID;
    /** Id du type de munition. */
    public static NamespacedKey AMMO;
    /** Id de grenade (item et projectile). */
    public static NamespacedKey GRENADE;
    /** Id du type d'infesté. */
    public static NamespacedKey INFECTED;
    /** Dégâts d'un crachat (projectile d'un cracheur). */
    public static NamespacedKey SPIT_DAMAGE;
    /** Durée du poison d'un crachat (ticks). */
    public static NamespacedKey SPIT_POISON;
    /** Marque la tablette (objet de menu, jamais perdu). */
    public static NamespacedKey PHONE;
    /** Id d'un colis largué (sur son entité d'interaction). */
    public static NamespacedKey PARCEL;
    /** Id d'un objet de soin (bandage, trousse). */
    public static NamespacedKey MED;
    /** Case encore cachée d'un conteneur en cours de fouille. */
    public static NamespacedKey SEARCH;
    /** Id d'une caisse de butin (sur son entité d'interaction). */
    public static NamespacedKey CRATE;
    /** Marque la carte tactique (mini-carte en main gauche, jamais perdue). */
    public static NamespacedKey MINIMAP;
    /** Id d'un véhicule (sur son entité d'interaction). */
    public static NamespacedKey VEHICLE;
    /** Outil de véhicule : « jerrican » ou « reparation ». */
    public static NamespacedKey VEHICLE_TOOL;
    /** Pièce de tenue : « <tenue>:<pièce> » (emeute:casque). */
    public static NamespacedKey ARMOR;

    private Keys() {
    }

    static void init(Plugin p) {
        GUN = new NamespacedKey(p, "gun");
        GUN_UID = new NamespacedKey(p, "gun_uid");
        AMMO = new NamespacedKey(p, "ammo");
        GRENADE = new NamespacedKey(p, "grenade");
        INFECTED = new NamespacedKey(p, "infected");
        SPIT_DAMAGE = new NamespacedKey(p, "spit_damage");
        SPIT_POISON = new NamespacedKey(p, "spit_poison");
        PHONE = new NamespacedKey(p, "phone");
        PARCEL = new NamespacedKey(p, "parcel");
        MED = new NamespacedKey(p, "med");
        CRATE = new NamespacedKey(p, "crate");
        SEARCH = new NamespacedKey(p, "search");
        MINIMAP = new NamespacedKey(p, "minimap");
        VEHICLE = new NamespacedKey(p, "vehicle");
        VEHICLE_TOOL = new NamespacedKey(p, "vehicle_tool");
        ARMOR = new NamespacedKey(p, "armor");
    }
}
