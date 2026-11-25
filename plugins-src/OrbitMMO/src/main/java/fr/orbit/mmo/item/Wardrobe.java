package fr.orbit.mmo.item;

import fr.orbit.mmo.Keys;
import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.util.Text;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Garde-robe (transmogrification) : chaque pièce portée débloque son apparence pour son emplacement
 * (PlayerData.looks, « plaques_3:chest »). Choisir une apparence ne change que la clé orbitmmo:outfit de la pièce
 * (lue par le paquet outfit pour le rendu porté) et son icône : les statistiques ne bougent pas.
 * L'apparence d'origine reste dans orbitmmo:outfit_base.
 */
public final class Wardrobe {

    private Wardrobe() {
    }

    /** Débloque l'apparence d'origine de la pièce portée (true si elle est nouvelle). */
    public static boolean unlock(PlayerData d, ItemStack it) {
        Loot.Slot slot = slotOf(it);
        String base = base(it);
        if (slot == null || base == null) {
            return false;
        }
        return d.looks.add(base + ":" + slot.id);
    }

    public static Loot.Slot slotOf(ItemStack it) {
        return Loot.armorType(it) == null ? null : Loot.Slot.of(it.getType());
    }

    public static String base(ItemStack it) {
        return it == null || !it.hasItemMeta() ? null
                : it.getItemMeta().getPersistentDataContainer().get(Keys.OUTFIT_BASE, PersistentDataType.STRING);
    }

    public static String shown(ItemStack it) {
        return it == null || !it.hasItemMeta() ? null
                : it.getItemMeta().getPersistentDataContainer().get(Keys.OUTFIT, PersistentDataType.STRING);
    }

    /** Apparences débloquées pour un emplacement, dans l'ordre d'obtention. */
    public static List<String> looks(PlayerData d, Loot.Slot slot) {
        List<String> out = new ArrayList<>();
        String suffix = ":" + slot.id;
        for (String l : d.looks) {
            if (l.endsWith(suffix)) {
                out.add(l.substring(0, l.length() - suffix.length()));
            }
        }
        return out;
    }

    /** Apparences débloquées sur au moins un emplacement (tenues complètes). */
    public static List<String> outfits(PlayerData d) {
        List<String> out = new ArrayList<>();
        for (String l : d.looks) {
            int i = l.lastIndexOf(':');
            String look = i < 0 ? l : l.substring(0, i);
            if (!out.contains(look)) {
                out.add(look);
            }
        }
        return out;
    }

    public static boolean unlocked(PlayerData d, String look, Loot.Slot slot) {
        return d.looks.contains(look + ":" + slot.id);
    }

    // ------------------------------------------------------------------ Apparences « type_palier »

    /** Type d'armure d'une apparence (« plaques_3 » → plaques ; set à apparence propre → type du set). */
    public static ArmorType typeOf(String look) {
        int[] tier = new int[1];
        ArmorType t = parse(look, tier);
        if (t != null) {
            return t;
        }
        GearSet s = setOf(look);
        return s == null ? null : s.type;
    }

    /** Palier d'une apparence (−1 : inconnu). */
    public static int tierOf(String look) {
        int[] tier = {-1};
        if (parse(look, tier) != null) {
            return tier[0];
        }
        GearSet s = setOf(look);
        return s == null ? -1 : s.tier;
    }

    private static ArmorType parse(String look, int[] tier) {
        if (look == null) {
            return null;
        }
        int i = look.lastIndexOf('_');
        if (i <= 0) {
            return null;
        }
        ArmorType t = ArmorType.byId(look.substring(0, i));
        if (t == null) {
            return null;
        }
        try {
            tier[0] = Math.max(0, Math.min(Loot.TIERS - 1, Integer.parseInt(look.substring(i + 1))));
        } catch (NumberFormatException e) {
            return null;
        }
        return t;
    }

    private static GearSet setOf(String look) {
        OrbitMMO plugin = OrbitMMO.getPlugin(OrbitMMO.class);
        if (plugin.sets() == null || look == null) {
            return null;
        }
        for (GearSet s : plugin.sets().all()) {
            if (look.equals(s.look)) {
                return s;
            }
        }
        return null;
    }

    /** « Plaques · Cristal », « Rempart astral »… */
    public static String lookLabel(String look) {
        GearSet s = setOf(look);
        if (s != null) {
            return Text.esc(s.name);
        }
        int[] tier = new int[1];
        ArmorType t = parse(look, tier);
        return t == null ? Text.esc(String.valueOf(look)) : t.label + " · " + Loot.TIER_LABELS[tier[0]];
    }

    // ------------------------------------------------------------------ Application

    /** Donne à la pièce l'apparence choisie (null : celle d'origine). Les statistiques ne changent pas. */
    public static void apply(ItemStack it, String look) {
        String base = base(it);
        Loot.Slot slot = slotOf(it);
        if (base == null || slot == null) {
            return;
        }
        String target = look == null ? base : look;
        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.OUTFIT, PersistentDataType.STRING, target);
        it.setItemMeta(meta);
        ArmorType t = typeOf(target);
        int tier = tierOf(target);
        if (t != null && tier >= 0) {
            Items.model(it, slot.model(t, tier));
        }
        Loot.refresh(it);
    }

    /** Icône d'une apparence pour un emplacement (menus de la garde-robe). */
    public static ItemStack icon(String look, Loot.Slot slot, List<String> lore) {
        ArmorType t = typeOf(look);
        int tier = Math.max(0, tierOf(look));
        ArmorType ty = t == null ? ArmorType.PLAQUES : t;
        ItemStack it = Items.icon(slot.material(ty, tier), "<" + ty.hex + "><b>" + lookLabel(look) + "</b>", lore);
        Items.model(it, slot.model(ty, tier));
        return it;
    }
}
