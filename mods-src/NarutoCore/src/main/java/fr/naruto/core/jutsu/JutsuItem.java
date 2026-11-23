package fr.naruto.core.jutsu;

import fr.naruto.core.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Cree/lit l'item qui lance un jutsu (clic droit). L'id du jutsu est dans le PDC. */
public final class JutsuItem {

    private JutsuItem() {}

    public static ItemStack create(Jutsu j) {
        ItemStack it = new ItemStack(j.icon());
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.MM.deserialize("<#FFB347>" + j.displayName()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Text.MM.deserialize("<gray>Element : <" + j.element().color() + ">" + j.element().displayName()).decoration(TextDecoration.ITALIC, false));
        lore.add(Text.MM.deserialize("<gray>Chakra : <aqua>" + j.chakraCost() + " <dark_gray>| Recharge : " + (j.cooldownMs() / 1000) + "s").decoration(TextDecoration.ITALIC, false));
        for (String l : j.lore()) lore.add(Text.MM.deserialize("<dark_gray>" + l).decoration(TextDecoration.ITALIC, false));
        lore.add(Text.MM.deserialize("<yellow>Clic droit pour lancer").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(Keys.JUTSU, PersistentDataType.STRING, j.id());
        it.setItemMeta(meta);
        return it;
    }

    /** Renvoie l'id du jutsu porte par cet item, ou null. */
    public static String jutsuId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        NamespacedKey key = Keys.JUTSU;
        if (key == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
}
