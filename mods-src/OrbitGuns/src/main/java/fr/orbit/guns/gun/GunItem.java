package fr.orbit.guns.gun;

import fr.orbit.guns.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Fabrique et lit les items "arme". */
public final class GunItem {

    private final NamespacedKey idKey;
    private final NamespacedKey magKey;

    public GunItem(NamespacedKey idKey, NamespacedKey magKey) {
        this.idKey = idKey;
        this.magKey = magKey;
    }

    public ItemStack build(Gun gun) {
        ItemStack is = new ItemStack(gun.baseItem());
        ItemMeta meta = is.getItemMeta();
        meta.displayName(Text.of(gun.display()));
        if (gun.modelData() >= 0) meta.setCustomModelData(gun.modelData());
        if (gun.itemModel() != null && !gun.itemModel().isBlank()) {
            NamespacedKey model = NamespacedKey.fromString(gun.itemModel());
            if (model != null) meta.setItemModel(model);
        }
        List<Component> lore = new ArrayList<>();
        lore.add(Text.of("&7Chargeur : &e" + gun.magazine()));
        lore.add(Text.of("&7Degats : &c" + gun.damage()));
        lore.add(Text.of(""));
        lore.add(Text.of("&7Clic droit &8» &ftirer"));
        lore.add(Text.of("&7Touche F &8» &frecharger"));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, gun.id());
        meta.getPersistentDataContainer().set(magKey, PersistentDataType.INTEGER, gun.magazine());
        is.setItemMeta(meta);
        return is;
    }

    public boolean isGun(ItemStack is) {
        return is != null && is.hasItemMeta()
                && is.getItemMeta().getPersistentDataContainer().has(idKey, PersistentDataType.STRING);
    }

    public String gunId(ItemStack is) {
        if (!isGun(is)) return null;
        return is.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public int getMag(ItemStack is) {
        if (is == null || !is.hasItemMeta()) return 0;
        Integer v = is.getItemMeta().getPersistentDataContainer().get(magKey, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    /** Ecrit le chargeur restant dans l'item (modifie l'ItemStack donne). */
    public void setMag(ItemStack is, int mag) {
        if (is == null) return;
        ItemMeta meta = is.getItemMeta();
        meta.getPersistentDataContainer().set(magKey, PersistentDataType.INTEGER, Math.max(0, mag));
        is.setItemMeta(meta);
    }
}
