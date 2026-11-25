package fr.orbit.mmo.ui;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.item.ArmorType;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.item.Loot;
import fr.orbit.mmo.item.Loot.Slot;
import fr.orbit.mmo.item.Wardrobe;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Garde-robe : choisir l'apparence affichée de chaque pièce d'armure portée parmi celles déjà portées
 * (même type d'armure), sans changer les statistiques. Ouvert depuis la fiche [Maj + F].
 */
public final class WardrobeMenus {

    private static final Slot[] ORDER = {Slot.HEAD, Slot.CHEST, Slot.LEGS, Slot.FEET};
    private static final int[] PIECE_SLOTS = {10, 12, 14, 16};
    private static final int[] OUTFIT_SLOTS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private WardrobeMenus() {
    }

    private static EquipmentSlot equip(Slot s) {
        return switch (s) {
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
        };
    }

    private static PlayerData data(OrbitMMO plugin, Player p) {
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        return d != null && plugin.progression().valid(d) ? d : null;
    }

    public static void open(OrbitMMO plugin, Player p) {
        PlayerData d = data(plugin, p);
        if (d == null) {
            return;
        }
        Menu m = new Menu(6, Text.mm("<#B9A6FF><b>Garde-robe</b>")).skin("Garde-robe", "Apparences", d.looks.size() + " débloquées");
        m.set(4, Items.icon(Material.ARMOR_STAND, "<#B9A6FF><b>La garde-robe</b>",
                "<#D7DCE2>Chaque pièce que vous portez débloque",
                "<#D7DCE2>son apparence pour cet emplacement.",
                "<#D7DCE2>Choisissez l'apparence affichée de vos",
                "<#D7DCE2>pièces : <white>les statistiques ne changent pas.",
                "",
                "<#8C96A3>Seules les apparences de votre type",
                "<#8C96A3>d'armure sont proposées."), null);
        for (int i = 0; i < ORDER.length; i++) {
            Slot s = ORDER[i];
            ItemStack worn = p.getInventory().getItem(equip(s));
            if (Wardrobe.slotOf(worn) == null) {
                m.set(PIECE_SLOTS[i], Items.icon(Material.GRAY_STAINED_GLASS_PANE, "<#5A6270>" + s.label + " : rien de porté",
                        "<#5A6270>Portez une pièce d'armure du MMO."), null);
                continue;
            }
            ItemStack icon = worn.clone();
            ItemMeta meta = icon.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Text.item("<#8C96A3>Apparence : <white>" + Wardrobe.lookLabel(Wardrobe.shown(worn))));
            lore.add(Text.item("<#8C96A3>Disponibles : <white>" + Wardrobe.looks(d, s).size()));
            lore.add(Text.item(""));
            lore.add(Text.item("<#B9A6FF>[Clic] changer l'apparence"));
            meta.lore(lore);
            icon.setItemMeta(meta);
            m.set(PIECE_SLOTS[i], icon, t -> {
                Menus.click(p);
                piece(plugin, p, s, 0);
            });
        }

        m.set(19, Items.icon(Material.LEATHER_CHESTPLATE, "<#FFD66E><b>Tenues complètes</b>",
                "<#8C96A3>En dessous : chaque apparence débloquée.",
                "<#8C96A3>[Clic] l'applique à toutes les pièces",
                "<#8C96A3>portées qui l'ont débloquée."), null);
        List<String> outfits = Wardrobe.outfits(d);
        for (int i = 0; i < Math.min(outfits.size(), OUTFIT_SLOTS.length); i++) {
            String look = outfits.get(i);
            int count = 0;
            for (Slot s : ORDER) {
                if (Wardrobe.unlocked(d, look, s)) {
                    count++;
                }
            }
            m.set(OUTFIT_SLOTS[i], Wardrobe.icon(look, Slot.CHEST, List.of("<#8C96A3>Pièces débloquées : <white>" + count + " / 4",
                    "<#5A6270>[Clic] porter cette tenue")), t -> {
                int done = 0;
                for (Slot s : ORDER) {
                    if (Wardrobe.unlocked(d, look, s) && applyTo(p, s, look)) {
                        done++;
                    }
                }
                feedback(plugin, p, done > 0 ? "<#B9A6FF>Tenue « " + Wardrobe.lookLabel(look) + " » : " + done + " pièce(s)."
                        : "<#FF8A3B>Aucune pièce portée ne peut prendre cette apparence.");
                open(plugin, p);
            });
        }
        if (outfits.isEmpty()) {
            m.set(31, Items.icon(Material.BOOK, "<#8C96A3>Aucune apparence débloquée",
                    "<#5A6270>Portez des pièces d'armure pour les débloquer."), null);
        }

        m.set(45, Items.icon(Material.ARROW, "<#8C96A3>Mon personnage"), t -> ProfileMenu.open(plugin, p, p));
        m.set(53, Items.icon(Material.WATER_BUCKET, "<#D7DCE2><b>Tout remettre d'origine</b>",
                "<#8C96A3>Chaque pièce portée reprend son apparence."), t -> {
            for (Slot s : ORDER) {
                applyTo(p, s, null);
            }
            feedback(plugin, p, "<#D7DCE2>Apparences d'origine remises.");
            open(plugin, p);
        });
        Menus.frame(m, 6);
        m.open(p);
    }

    /** Apparences d'un emplacement (36 par page). */
    static void piece(OrbitMMO plugin, Player p, Slot s, int page) {
        PlayerData d = data(plugin, p);
        if (d == null) {
            return;
        }
        ItemStack worn = p.getInventory().getItem(equip(s));
        if (Wardrobe.slotOf(worn) == null) {
            open(plugin, p);
            return;
        }
        ArmorType type = Loot.armorType(worn);
        List<String> looks = new ArrayList<>();
        for (String l : Wardrobe.looks(d, s)) {
            if (Wardrobe.typeOf(l) == null || Wardrobe.typeOf(l) == type) {
                looks.add(l);
            }
        }
        int pages = Math.max(1, (looks.size() + 35) / 36);
        int pg = Math.max(0, Math.min(pages - 1, page));
        Menu m = new Menu(6, Text.mm("<#B9A6FF><b>Garde-robe</b> <#8C96A3>· " + s.label))
                .skin("Garde-robe", s.label, (pg + 1) + " / " + pages);
        String shown = Wardrobe.shown(worn);
        String base = Wardrobe.base(worn);
        for (int i = 0; i < 36 && pg * 36 + i < looks.size(); i++) {
            String look = looks.get(pg * 36 + i);
            boolean current = look.equals(shown);
            ItemStack icon = Wardrobe.icon(look, s, List.of(
                    look.equals(base) ? "<#5A6270>Apparence d'origine de la pièce" : "<#5A6270>Débloquée",
                    current ? "<#7FD4FF>Affichée" : "<#B9A6FF>[Clic] afficher"));
            m.set(i, current ? Items.glow(icon) : icon, current ? null : t -> {
                applyTo(p, s, look);
                feedback(plugin, p, "<#B9A6FF>" + s.label + " : apparence « " + Wardrobe.lookLabel(look) + " ».");
                piece(plugin, p, s, pg);
            });
        }
        m.set(45, Items.icon(Material.ARROW, "<#8C96A3>Garde-robe"), t -> open(plugin, p));
        m.set(49, Items.icon(Material.WATER_BUCKET, "<#D7DCE2><b>Apparence d'origine</b>", "<#8C96A3>" + Wardrobe.lookLabel(base)),
                t -> {
                    applyTo(p, s, null);
                    feedback(plugin, p, "<#D7DCE2>" + s.label + " : apparence d'origine.");
                    piece(plugin, p, s, pg);
                });
        if (pg > 0) {
            m.set(48, Items.icon(Material.SPECTRAL_ARROW, "<#8C96A3>Page précédente"), t -> piece(plugin, p, s, pg - 1));
        }
        if (pg < pages - 1) {
            m.set(50, Items.icon(Material.SPECTRAL_ARROW, "<#8C96A3>Page suivante"), t -> piece(plugin, p, s, pg + 1));
        }
        Menus.frame(m, 6);
        m.open(p);
    }

    /** Applique l'apparence (null : d'origine) à la pièce portée ; false si impossible. */
    private static boolean applyTo(Player p, Slot s, String look) {
        EquipmentSlot es = equip(s);
        ItemStack worn = p.getInventory().getItem(es);
        if (Wardrobe.slotOf(worn) == null) {
            return false;
        }
        if (look != null) {
            ArmorType lt = Wardrobe.typeOf(look);
            if (lt != null && lt != Loot.armorType(worn)) {
                return false;
            }
        }
        Wardrobe.apply(worn, look);
        p.getInventory().setItem(es, worn);
        return true;
    }

    private static void feedback(OrbitMMO plugin, Player p, String msg) {
        plugin.hud().flash(p, msg, 40);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1.2f);
    }
}
