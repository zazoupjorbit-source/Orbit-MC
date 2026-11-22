package fr.orbit.reborn;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Clés des données persistantes posées sur les items et les entités. */
public final class Keys {

    /** Entité : identifiant du monstre (MobDef). */
    public static NamespacedKey MOB;
    /** Item : type d'objet MMO (weapon, potion, heal_crystal, tp_crystal, material, lab). */
    public static NamespacedKey ITEM;
    /** Item : type d'arme (WeaponType). */
    public static NamespacedKey WEAPON;
    /** Item : attaque de l'arme. */
    public static NamespacedKey ATK;
    /** Item : prix de revente en Col. */
    public static NamespacedKey SELL;
    /** Entité : PNJ / étiquette de décor (merchant, label). */
    public static NamespacedKey NPC;
    /** Modificateur de vitesse lié à l'agilité. */
    public static NamespacedKey AGILITY;
    /** Modificateur d'armure des récompenses de boss. */
    public static NamespacedKey LAB_ARMOR;

    private Keys() {
    }

    static void init(Plugin plugin) {
        MOB = new NamespacedKey(plugin, "mob");
        ITEM = new NamespacedKey(plugin, "item");
        WEAPON = new NamespacedKey(plugin, "weapon");
        ATK = new NamespacedKey(plugin, "atk");
        SELL = new NamespacedKey(plugin, "sell");
        NPC = new NamespacedKey(plugin, "npc");
        AGILITY = new NamespacedKey(plugin, "agility");
        LAB_ARMOR = new NamespacedKey(plugin, "lab_armor");
    }
}
