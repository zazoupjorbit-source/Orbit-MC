package games.orbit.orbitstaff.core.staff;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Marque et identifie les outils de la hotbar staff via le PersistentDataContainer.
 * L'action est ainsi liee a l'objet lui-meme (et non a un slot), ce qui rend le
 * dispatch fiable meme si le joueur deplace l'objet.
 */
public final class StaffTools {

    private static final String KEY = "staff_tool";

    private StaffTools() {
    }

    private static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, KEY);
    }

    /** Marque une meta d'objet avec l'action staff associee. */
    public static void tag(ItemMeta meta, Plugin plugin, StaffAction action) {
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, action.name());
    }

    /**
     * @return l'action staff portee par l'objet, ou null si ce n'est pas un outil staff.
     */
    public static StaffAction read(ItemStack item, Plugin plugin) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String value = meta.getPersistentDataContainer().get(key(plugin), PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        try {
            return StaffAction.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
