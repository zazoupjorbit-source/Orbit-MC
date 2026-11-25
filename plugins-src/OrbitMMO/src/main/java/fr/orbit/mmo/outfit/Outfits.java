package fr.orbit.mmo.outfit;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Rendu porté des tenues 3D. Une pièce d'armure porte son apparence dans la clé {@code orbitmmo:outfit}
 * (« plaques_2 », posée par Loot et changée par la garde-robe). Si une tenue 3D existe sous ce nom, le service
 * des tenues habille la pièce portée : composant « équipable » vers la couche du corps HD, et pour le casque
 * un modèle 3D dessiné sur la tête. Une pièce dont l'apparence n'a pas (ou plus) de tenue 3D est rendue telle
 * quelle (les composants posés ici sont retirés). Rien d'autre n'est touché : stats, nom, icône de l'armurerie.
 * Voir docs\tenues.md.
 */
public final class Outfits {

    /** Clé PDC de la pièce : son apparence (la même que Keys.OUTFIT). */
    public static final NamespacedKey KEY = new NamespacedKey("orbitmmo", "outfit");
    /** Type et palier de la pièce (les mêmes que Keys.ARMOR_TYPE et Keys.TIER), pour les apparences de set. */
    private static final NamespacedKey ARMOR_TYPE = new NamespacedKey("orbitmmo", "armor_type");
    private static final NamespacedKey TIER = new NamespacedKey("orbitmmo", "tier");

    private Outfits() {
    }

    /** Apparence d'une pièce (clé orbitmmo:outfit), ou null. */
    public static String id(ItemStack it) {
        if (it == null || it.isEmpty()) {
            return null;
        }
        return it.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
    }

    /**
     * Tenue 3D d'une pièce, ou null si son apparence n'en a pas. Une apparence propre à un set (« set_… ») qui n'a
     * pas encore sa tenue 3D retombe sur celle de son type et de son palier (clés armor_type et tier de la pièce) ;
     * une apparence « type_palier » choisie à la garde-robe ne retombe sur rien : c'est le choix du joueur.
     */
    public static OutfitDef of(ItemStack it) {
        String id = id(it);
        OutfitDef def = OutfitDef.get(id);
        if (def != null || id == null || !id.startsWith("set_")) {
            return def;
        }
        try {
            var pdc = it.getPersistentDataContainer();
            String type = pdc.get(ARMOR_TYPE, PersistentDataType.STRING);
            Integer tier = pdc.get(TIER, PersistentDataType.INTEGER);
            return type == null || tier == null ? null : OutfitDef.get(type + "_" + tier);
        } catch (IllegalArgumentException wrongType) {
            return null;
        }
    }

    /** Emplacement d'une pièce d'armure (d'après son matériau, sinon son composant « équipable »), ou null. */
    public static EquipmentSlot slotOf(ItemStack it) {
        if (it == null || it.isEmpty()) {
            return null;
        }
        String n = it.getType().name();
        if (n.endsWith("_HELMET")) {
            return EquipmentSlot.HEAD;
        }
        if (n.endsWith("_CHESTPLATE")) {
            return EquipmentSlot.CHEST;
        }
        if (n.endsWith("_LEGGINGS")) {
            return EquipmentSlot.LEGS;
        }
        if (n.endsWith("_BOOTS")) {
            return EquipmentSlot.FEET;
        }
        Equippable e = it.getData(DataComponentTypes.EQUIPPABLE);
        return e == null ? null : e.slot();
    }

    /**
     * Pose l'apparence {@code outfitId} sur une pièce d'armure et, si c'est une tenue 3D, l'habille tout de suite.
     * Faux si l'objet n'est pas une pièce d'armure.
     */
    public static boolean apply(ItemStack it, String outfitId) {
        EquipmentSlot slot = slotOf(it);
        if (slot == null || !isArmor(slot) || outfitId == null) {
            return false;
        }
        it.editPersistentDataContainer(pdc -> pdc.set(KEY, PersistentDataType.STRING, outfitId));
        redress(it);
        return true;
    }

    /** Met les composants d'apparence d'accord avec la clé de la pièce. Vrai si l'objet a changé. */
    public static boolean redress(ItemStack it) {
        EquipmentSlot slot = slotOf(it);
        if (slot == null || !isArmor(slot)) {
            return false;
        }
        OutfitDef def = of(it);
        Key model = it.getData(DataComponentTypes.ITEM_MODEL);
        Equippable eq = it.getData(DataComponentTypes.EQUIPPABLE);
        Key asset = eq == null ? null : eq.assetId();
        boolean ourModel = model != null && model.namespace().equals("orbitmmo") && model.value().startsWith("outfit/");
        boolean ourAsset = asset != null && asset.namespace().equals("orbitmmo") && asset.value().startsWith("tenue_");
        if (def == null) {
            // Apparence sans tenue 3D (ou changée à la garde-robe) : on rend la pièce à son matériau.
            boolean changed = false;
            if (ourModel) {
                it.resetData(DataComponentTypes.ITEM_MODEL);
                changed = true;
            }
            if (ourAsset || (slot == EquipmentSlot.HEAD && ourModel)) {
                it.resetData(DataComponentTypes.EQUIPPABLE);
                changed = true;
            }
            return changed;
        }
        if (slot == EquipmentSlot.HEAD) {
            // Sans asset, le jeu dessine l'objet lui-même sur la tête : contexte « head » du modèle 3D du casque.
            if (def.helmet().equals(model) && eq != null && asset == null && eq.slot() == EquipmentSlot.HEAD) {
                return false;
            }
            it.setData(DataComponentTypes.ITEM_MODEL, def.helmet());
            it.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD).equipSound(def.equipSound()).build());
            return true;
        }
        if (def.equipment().equals(asset) && eq.slot() == slot) {
            return false;
        }
        it.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot).assetId(def.equipment()).equipSound(def.equipSound()).build());
        return true;
    }

    private static boolean isArmor(EquipmentSlot s) {
        return s == EquipmentSlot.HEAD || s == EquipmentSlot.CHEST || s == EquipmentSlot.LEGS || s == EquipmentSlot.FEET;
    }
}
