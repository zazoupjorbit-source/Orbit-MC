package fr.orbit.mmo.ui;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.item.Loot;
import fr.orbit.mmo.item.Rune;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.util.Text;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Atelier d'Ysolde, la runiste : sertir une rune dans une châsse d'arme, la retirer, fusionner trois runes
 * identiques en une du rang supérieur, acheter des runes de rang I.
 */
public final class RuneMenus {

    private static final String HEX = "#9B59FF";
    private static final String SPEAKER = "<" + HEX + ">Ysolde <#5A6270>» <#D7DCE2>";
    /** Prix pour sertir / retirer une rune, par rang. */
    private static final long SOCKET_PRICE = 15;
    private static final long UNSOCKET_PRICE = 60;
    /** Arme choisie (case de l'inventaire) par joueur. */
    private static final Map<UUID, Integer> TARGET = new HashMap<>();

    private RuneMenus() {
    }

    /** Case de l'arme travaillée : celle cliquée dans l'inventaire, sinon l'arme de la barre (-1 : aucune). */
    private static int target(OrbitMMO plugin, Player p, PlayerData d) {
        Integer s = TARGET.get(p.getUniqueId());
        if (s != null && s >= 0 && s < 36 && "weapon".equals(Items.type(p.getInventory().getItem(s)))) {
            return s;
        }
        return plugin.bar().weaponSlot(p, d);
    }

    public static void open(OrbitMMO plugin, Player p) {
        PlayerData d = Menus.trader(plugin, p);
        if (d == null) {
            return;
        }
        PlayerInventory inv = p.getInventory();
        int ws = target(plugin, p, d);
        ItemStack weapon = ws < 0 ? null : inv.getItem(ws);
        if (weapon != null && Loot.adopt(weapon)) {
            inv.setItem(ws, weapon);
        }
        Menu m = new Menu(5, Menus.shopTitle(HEX, "Atelier d'Ysolde", d)).skin("Atelier des runes", "Ysolde", Menus.gold(d));
        m.set(4, Items.icon(Material.OAK_HANGING_SIGN, "<" + HEX + "><b>Comment ça marche ?</b>",
                "<#D7DCE2>1. Cliquez une <white>arme</white> dans votre inventaire",
                "<#D7DCE2>   (en bas) pour la poser sur l'établi.",
                "<#D7DCE2>2. Cliquez une <white>rune</white> : elle se sertit dans",
                "<#D7DCE2>   la première châsse libre (" + SOCKET_PRICE + " or × rang).",
                "<#D7DCE2>3. Cliquez une rune sertie (au centre) pour",
                "<#D7DCE2>   la retirer (" + UNSOCKET_PRICE + " or × rang) : elle revient au sac.",
                "",
                "<#8C96A3>Runes de statistique : bonus tant que l'arme est tenue.",
                "<#8C96A3>Runes élémentaires : chance d'effet à chaque coup.",
                "<#8C96A3>Plus l'arme est rare, plus elle a de châsses (1 à 3)."), null);
        if (weapon == null) {
            m.set(13, Items.icon(Material.BARRIER, "<#FF5C5C>Aucune arme sur l'établi",
                    "<#8C96A3>Cliquez une arme dans votre inventaire."), null);
        } else {
            ItemStack shown = weapon.clone();
            m.set(13, shown, null);
            List<Rune.Ref> runes = Loot.runes(weapon);
            int n = runes.size();
            int[] slots = n <= 1 ? new int[]{22} : n == 2 ? new int[]{21, 23} : new int[]{20, 22, 24};
            for (int i = 0; i < Math.min(n, slots.length); i++) {
                Rune.Ref ref = runes.get(i);
                int index = i;
                if (ref == null) {
                    m.set(slots[i], Items.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<#8C96A3>◇ Châsse vide",
                            "<#5A6270>Cliquez une rune dans votre inventaire", "<#5A6270>pour la sertir ici."), null);
                } else {
                    ItemStack icon = ref.rune().item(ref.rank());
                    var meta = icon.getItemMeta();
                    List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                    lore.add(Text.item(""));
                    lore.add(Text.item((d.gold >= UNSOCKET_PRICE * ref.rank() ? "<#FFD66E>" : "<#FF5C5C>")
                            + "[Clic] retirer : " + UNSOCKET_PRICE * ref.rank() + " or"));
                    meta.lore(lore);
                    icon.setItemMeta(meta);
                    m.set(slots[i], icon, t -> unsocket(plugin, p, index));
                }
            }
        }
        m.set(38, Items.icon(Material.EMERALD, "<" + HEX + "><b>Acheter des runes</b>",
                "<#8C96A3>Runes de rang I.", "<#5A6270>[Clic] ouvrir"), t -> {
            Menus.click(p);
            shop(plugin, p);
        });
        m.set(40, Items.icon(Material.AMETHYST_CLUSTER, "<" + HEX + "><b>Fusionner des runes</b>",
                "<#8C96A3>3 runes identiques → 1 rune du rang supérieur.",
                "<#8C96A3>Rang II : " + Rune.FUSE_PRICE[2] + " or · rang III : " + Rune.FUSE_PRICE[3] + " or.",
                "<#5A6270>[Clic] ouvrir"), t -> {
            Menus.click(p);
            fuse(plugin, p);
        });
        m.set(42, Items.icon(Material.ENCHANTED_BOOK, "<" + HEX + "><b>Où trouver des runes ?</b>",
                "<#8C96A3>Rang I : ici, ou sur les monstres.",
                "<#8C96A3>Rangs II et III : fusion, butin des monstres",
                "<#8C96A3>(rare) et des boss, hôtel des ventes."), null);
        m.onBottomClick((slot, type) -> bottom(plugin, p, slot));
        Menus.frame(m, 5);
        m.open(p);
    }

    private static void bottom(OrbitMMO plugin, Player p, int slot) {
        if (slot < 0 || slot >= 36) {
            return;
        }
        ItemStack it = p.getInventory().getItem(slot);
        String kind = Items.type(it);
        if ("weapon".equals(kind)) {
            TARGET.put(p.getUniqueId(), slot);
            Menus.click(p);
            open(plugin, p);
        } else if ("rune".equals(kind)) {
            socket(plugin, p, slot);
        } else {
            Text.msg(p, "<#8C96A3>Cliquez une arme (pour la choisir) ou une rune (pour la sertir).");
        }
    }

    private static void socket(OrbitMMO plugin, Player p, int runeSlot) {
        PlayerData d = Menus.trader(plugin, p);
        if (d == null) {
            return;
        }
        PlayerInventory inv = p.getInventory();
        int ws = target(plugin, p, d);
        ItemStack weapon = ws < 0 ? null : inv.getItem(ws);
        if (weapon == null) {
            Text.msg(p, SPEAKER + "Montre-moi d'abord l'arme : clique-la dans ton inventaire.");
            return;
        }
        Loot.adopt(weapon);
        List<Rune.Ref> runes = Loot.runes(weapon);
        int free = runes.indexOf(null);
        if (free < 0) {
            Text.msg(p, SPEAKER + "Plus une seule châsse libre. Retire d'abord une rune.");
            return;
        }
        ItemStack runeItem = inv.getItem(runeSlot);
        Rune.Ref ref = Rune.of(runeItem);
        if (ref == null) {
            return;
        }
        long cost = SOCKET_PRICE * ref.rank();
        if (d.gold < cost) {
            Text.msg(p, "<#FF5C5C>Pas assez d'or (" + cost + " or).");
            return;
        }
        d.gold -= cost;
        runeItem.setAmount(runeItem.getAmount() - 1);
        inv.setItem(runeSlot, runeItem.getAmount() > 0 ? runeItem : null);
        runes.set(free, ref);
        Loot.setRunes(weapon, runes);
        inv.setItem(ws, weapon);
        plugin.progression().recompute(p, d);
        p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1.3f);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.2f);
        p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1.2, 0), 40, 0.4, 0.5, 0.4, 0.8);
        p.sendMessage(Text.mm(Text.PREFIX + ref.rune().tag(ref.rank()) + " <#D7DCE2>sertie dans ").append(weapon.effectiveName())
                .append(Text.mm(" <#5A6270>(−" + cost + " or)")));
        open(plugin, p);
    }

    private static void unsocket(OrbitMMO plugin, Player p, int index) {
        PlayerData d = Menus.trader(plugin, p);
        if (d == null) {
            return;
        }
        PlayerInventory inv = p.getInventory();
        int ws = target(plugin, p, d);
        ItemStack weapon = ws < 0 ? null : inv.getItem(ws);
        if (weapon == null) {
            return;
        }
        List<Rune.Ref> runes = Loot.runes(weapon);
        if (index < 0 || index >= runes.size() || runes.get(index) == null) {
            return;
        }
        Rune.Ref ref = runes.get(index);
        long cost = UNSOCKET_PRICE * ref.rank();
        if (d.gold < cost) {
            Text.msg(p, "<#FF5C5C>Pas assez d'or (" + cost + " or).");
            return;
        }
        d.gold -= cost;
        runes.set(index, null);
        Loot.setRunes(weapon, runes);
        inv.setItem(ws, weapon);
        Items.give(p, ref.rune().item(ref.rank()));
        plugin.progression().recompute(p, d);
        p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.7f, 1.2f);
        Text.msg(p, ref.rune().tag(ref.rank()) + " <#D7DCE2>retirée et rendue <#5A6270>(−" + cost + " or)");
        open(plugin, p);
    }

    // ------------------------------------------------------------------ Boutique

    static void shop(OrbitMMO plugin, Player p) {
        PlayerData d = Menus.trader(plugin, p);
        if (d == null) {
            return;
        }
        Menu m = new Menu(4, Menus.shopTitle(HEX, "Runes d'Ysolde", d)).skin("Runes", "Ysolde", Menus.gold(d));
        Runnable reopen = () -> shop(plugin, p);
        int stat = 10;
        int elem = 20;
        for (Rune r : Rune.values()) {
            int level = r.elemental() ? Rune.ELEMENT_LEVEL : 1;
            int slot = r.elemental() ? elem++ : stat++;
            m.set(slot, Menus.priced(r.item(1), r.buyPrice(), level, d),
                    t -> Menus.buy(plugin, p, d, r.buyPrice(), level, () -> Items.give(p, r.item(1)), reopen));
        }
        m.set(27, Items.icon(Material.ARROW, "<#8C96A3>Retour à l'établi"), t -> open(plugin, p));
        m.set(31, Items.icon(Material.OAK_SIGN, "<" + HEX + "><b>Rangs supérieurs</b>",
                "<#8C96A3>Je ne vends que des runes de rang I.",
                "<#8C96A3>Les rangs II et III : fusion (3 runes identiques),",
                "<#8C96A3>butin des monstres et des boss."), null);
        Menus.frame(m, 4);
        m.open(p);
    }

    // ------------------------------------------------------------------ Fusion

    private static Map<String, Integer> owned(Player p) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (ItemStack it : p.getInventory().getStorageContents()) {
            Rune.Ref r = Rune.of(it);
            if (r != null) {
                out.merge(r.key(), it.getAmount(), Integer::sum);
            }
        }
        return out;
    }

    static void fuse(OrbitMMO plugin, Player p) {
        PlayerData d = Menus.trader(plugin, p);
        if (d == null) {
            return;
        }
        Menu m = new Menu(5, Menus.shopTitle(HEX, "Fusion des runes", d)).skin("Fusion des runes", "Ysolde", Menus.gold(d));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int i = 0;
        for (Map.Entry<String, Integer> e : owned(p).entrySet()) {
            if (i >= slots.length) {
                break;
            }
            Rune.Ref ref = Rune.parse(e.getKey());
            if (ref == null) {
                continue;
            }
            int count = e.getValue();
            ItemStack icon = ref.rune().item(ref.rank());
            icon.setAmount(Math.max(1, Math.min(64, count)));
            var meta = icon.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Text.item("<#8C96A3>Vous en avez : <white>" + count));
            if (ref.rank() >= Rune.MAX_RANK) {
                lore.add(Text.item("<#8C96A3>Rang maximum."));
                meta.lore(lore);
                icon.setItemMeta(meta);
                m.set(slots[i++], icon, null);
                continue;
            }
            long price = Rune.FUSE_PRICE[ref.rank() + 1];
            lore.add(Text.item("<#D7DCE2>3× → " + ref.rune().tag(ref.rank() + 1)));
            lore.add(Text.item("<#7CFF8A>" + ref.rune().effect(ref.rank() + 1)));
            lore.add(Text.item(count >= 3 ? (d.gold >= price ? "<#FFD66E>[Clic] fusionner : " : "<#FF5C5C>Fusion : ") + price + " or"
                    : "<#FF5C5C>Il en faut 3."));
            meta.lore(lore);
            icon.setItemMeta(meta);
            m.set(slots[i++], icon, t -> fuseOne(plugin, p, ref));
        }
        if (i == 0) {
            m.set(22, Items.icon(Material.GRAY_DYE, "<#8C96A3>Aucune rune dans votre sac"), null);
        }
        m.set(36, Items.icon(Material.ARROW, "<#8C96A3>Retour à l'établi"), t -> open(plugin, p));
        Menus.frame(m, 5);
        m.open(p);
    }

    private static void fuseOne(OrbitMMO plugin, Player p, Rune.Ref ref) {
        PlayerData d = Menus.trader(plugin, p);
        if (d == null || ref.rank() >= Rune.MAX_RANK) {
            return;
        }
        if (owned(p).getOrDefault(ref.key(), 0) < 3) {
            Text.msg(p, "<#FF5C5C>Il faut 3 runes identiques (même rune, même rang).");
            return;
        }
        long price = Rune.FUSE_PRICE[ref.rank() + 1];
        if (d.gold < price) {
            Text.msg(p, "<#FF5C5C>Pas assez d'or (" + price + " or).");
            return;
        }
        d.gold -= price;
        int left = 3;
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            Rune.Ref r = Rune.of(contents[i]);
            if (r == null || !r.key().equals(ref.key())) {
                continue;
            }
            int take = Math.min(left, contents[i].getAmount());
            left -= take;
            ItemStack it = contents[i];
            it.setAmount(it.getAmount() - take);
            inv.setItem(i, it.getAmount() > 0 ? it : null);
        }
        Items.give(p, ref.rune().item(ref.rank() + 1));
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9f, 1.1f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1.4, 0), 25, 0.3, 0.4, 0.3, 0.05);
        Text.msg(p, "Fusion réussie : " + ref.rune().tag(ref.rank() + 1) + " <#5A6270>(−" + price + " or)");
        fuse(plugin, p);
    }
}
