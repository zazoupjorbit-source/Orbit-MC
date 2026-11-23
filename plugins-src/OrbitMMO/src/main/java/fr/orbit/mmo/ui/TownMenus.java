package fr.orbit.mmo.ui;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.item.Boost;
import fr.orbit.mmo.item.Gear;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Les PNJ de la ville : forgeron (améliorer, réparer), alchimiste (potions) et prêtre (parchemins, essences, bénédiction). */
public final class TownMenus {

    private TownMenus() {
    }

    /** Personnage valide et en ville (les PNJ n'en sortent pas). */
    private static PlayerData client(OrbitMMO plugin, Player p) {
        PlayerData d = plugin.players().get(p);
        if (!plugin.progression().valid(d)) {
            return null;
        }
        if (!plugin.floors().isSafe(p.getLocation())) {
            p.closeInventory();
            Text.msg(p, "<#FF5C5C>Les artisans se trouvent en ville.");
            return null;
        }
        return d;
    }

    private static boolean pay(Player p, PlayerData d, long price) {
        if (d.gold < price) {
            Text.msg(p, "<#FF5C5C>Il vous manque <#FFD66E>" + Text.num(price - d.gold) + " or</#FFD66E>.");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return false;
        }
        d.gold -= price;
        return true;
    }

    private static Component title(String hex, String name, PlayerData d) {
        return Text.mm("<" + hex + "><b>" + name + "</b></" + hex + "> <#5A6270>· <#FFD66E>" + Text.num(d.gold) + " or");
    }

    // ------------------------------------------------------------------ Forgeron

    /** Où se trouve l'objet travaillé : une pièce d'armure portée, ou une case de l'inventaire. */
    private record Ref(EquipmentSlot armor, int slot) {
        ItemStack get(Player p) {
            PlayerInventory inv = p.getInventory();
            return armor != null ? inv.getItem(armor) : inv.getItem(slot);
        }

        void put(Player p, ItemStack it) {
            PlayerInventory inv = p.getInventory();
            if (armor != null) {
                inv.setItem(armor, it);
            } else {
                inv.setItem(slot, it);
            }
        }
    }

    private static List<Ref> gear(Player p) {
        List<Ref> out = new ArrayList<>();
        PlayerInventory inv = p.getInventory();
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (Gear.isGear(inv.getItem(s))) {
                out.add(new Ref(s, -1));
            }
        }
        for (int i = 0; i < 36; i++) {
            if (Gear.isGear(inv.getItem(i))) {
                out.add(new Ref(null, i));
            }
        }
        return out;
    }

    public static void forge(OrbitMMO plugin, Player p) {
        PlayerData d = client(plugin, p);
        if (d == null) {
            return;
        }
        Menu m = new Menu(5, title("#FF8A3B", "Forge de Torvald", d)).skin("Forge", "Torvald", Menus.gold(d));
        m.set(4, Items.icon(Material.ANVIL, "<#FF8A3B><b>Forge</b>",
                "<#D7DCE2>Votre arme s'use en frappant, votre armure",
                "<#D7DCE2>en encaissant. Cassée, elle ne sert plus",
                "<#D7DCE2>qu'à moitié : faites-la réparer ici.",
                "",
                "<#FFD66E>Amélioration : +1 à +" + Gear.MAX_LEVEL,
                "<#8C96A3>  arme : +" + fmt(Gear.WEAPON_BONUS) + " % d'attaque par niveau",
                "<#8C96A3>  armure : −" + fmt(Gear.ARMOR_BONUS) + " % de dégâts subis par niveau et par pièce"), null);
        List<Ref> refs = gear(p);
        // Rangée 2 : armure portée ; rangées 3 et 4 : armes et pièces de l'inventaire.
        int[] invSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int armorSlot = 10;
        int invIndex = 0;
        long repairAll = 0;
        for (Ref ref : refs) {
            int at;
            if (ref.armor() != null) {
                at = armorSlot++;
            } else {
                if (invIndex >= invSlots.length) {
                    continue;
                }
                at = invSlots[invIndex++];
            }
            ItemStack it = ref.get(p);
            repairAll += Gear.repairCost(it);
            m.set(at, forgeIcon(it, d), click -> forgeClick(plugin, p, ref, click));
        }
        if (refs.isEmpty()) {
            m.set(22, Items.icon(Material.BARRIER, "<#8C96A3>Rien à travailler", "<#5A6270>Ni arme ni armure du MMO sur vous."), null);
        }
        long total = repairAll;
        m.set(40, Items.icon(Material.GRINDSTONE, "<#5EE89A><b>Tout réparer</b>",
                total > 0 ? "<#8C96A3>Prix : <#FFD66E>" + Text.num(total) + " or" : "<#5A6270>Tout est en parfait état."), total <= 0 ? null : t -> {
            PlayerData dd = client(plugin, p);
            if (dd == null) {
                return;
            }
            long cost = 0;
            for (Ref ref : gear(p)) {
                cost += Gear.repairCost(ref.get(p));
            }
            if (cost <= 0 || !pay(p, dd, cost)) {
                return;
            }
            for (Ref ref : gear(p)) {
                ItemStack it = ref.get(p);
                Gear.setWear(it, 0);
                ref.put(p, it);
            }
            forgeFx(p, false);
            Text.msg(p, "<#FF8A3B>Torvald <#5A6270>» <#D7DCE2>Et voilà, comme neuf. <#8C96A3>(−" + Text.num(cost) + " or)");
            forge(plugin, p);
        });
        m.set(36, Items.icon(Material.IRON_SWORD, "<#7FB2FF><b>Armurerie</b>",
                "<#8C96A3>Armes de votre classe, bouclier,", "<#8C96A3>ensembles d'armure", "", "<#5A6270>[Clic] acheter"),
                t -> Menus.armory(plugin, p));
        m.set(44, Items.icon(Material.GOLD_INGOT, "<#FFD66E><b>Vendre mon butin</b>",
                "<#8C96A3>Torvald rachète matériaux, armes,", "<#8C96A3>armures, potions et butin de boss.", "",
                "<#5A6270>[Clic] ouvrir"), t -> Menus.buyback(plugin, p));
        Menus.frame(m, 5);
        m.open(p);
    }

    private static ItemStack forgeIcon(ItemStack it, PlayerData d) {
        ItemStack icon = it.clone();
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        List<String> add = new ArrayList<>();
        add.add("");
        add.add("<#FF8A3B>── Forge ──");
        add.add(Gear.durabilityLine(it));
        int lvl = Gear.level(it);
        add.add(lvl > 0 ? Gear.bonusLine(it) : "<#8C96A3>Amélioration : <white>aucune");
        long up = Gear.upgradeCost(it);
        if (lvl >= Gear.MAX_LEVEL) {
            add.add("<#5EE89A>Amélioration maximale atteinte");
        } else {
            add.add("<#FFD66E>Clic gauche : améliorer en +" + (lvl + 1) + " <#8C96A3>— <" + (d.gold >= up ? "#FFD66E" : "#FF5C5C") + ">"
                    + Text.num(up) + " or");
        }
        long rep = Gear.repairCost(it);
        add.add(rep > 0 ? "<#5EE89A>Clic droit : réparer <#8C96A3>— <" + (d.gold >= rep ? "#FFD66E" : "#FF5C5C") + ">" + Text.num(rep) + " or"
                : "<#5A6270>En parfait état");
        lore.addAll(Text.lore(add));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private static void forgeClick(OrbitMMO plugin, Player p, Ref ref, ClickType click) {
        PlayerData d = client(plugin, p);
        if (d == null) {
            return;
        }
        ItemStack it = ref.get(p);
        if (!Gear.isGear(it)) {
            forge(plugin, p);
            return;
        }
        if (click.isRightClick()) {
            long cost = Gear.repairCost(it);
            if (cost > 0 && pay(p, d, cost)) {
                Gear.setWear(it, 0);
                ref.put(p, it);
                forgeFx(p, false);
            }
        } else if (Gear.level(it) < Gear.MAX_LEVEL) {
            long cost = Gear.upgradeCost(it);
            if (pay(p, d, cost)) {
                Gear.setLevel(it, Gear.level(it) + 1);
                ref.put(p, it);
                forgeFx(p, true);
                Text.msg(p, "<#FF8A3B>Torvald <#5A6270>» <#D7DCE2>Belle pièce. La voilà en <#FFD66E><b>+" + Gear.level(it)
                        + "</b><#D7DCE2>. <#8C96A3>(−" + Text.num(cost) + " or)");
            }
        }
        forge(plugin, p);
    }

    private static void forgeFx(Player p, boolean upgrade) {
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, upgrade ? 1.0f : 1.5f);
        if (upgrade) {
            p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1.0f);
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1.2, 0), 18, 0.4, 0.5, 0.4, 0.03);
        }
    }

    // ------------------------------------------------------------------ Alchimiste

    public static void alchemist(OrbitMMO plugin, Player p) {
        PlayerData d = client(plugin, p);
        if (d == null) {
            return;
        }
        Menu m = new Menu(3, title("#B36BFF", "Échoppe de Mirelle", d)).skin("Alchimie", "Mirelle", Menus.gold(d));
        product(plugin, m, p, d, 10, Items.potion(1), Items.POTION_PRICE, "Rend 35 % des PV en 5 secondes.", n -> Items.potion(n), TownMenus::alchemist);
        product(plugin, m, p, d, 11, Items.manaPotion(1), Items.MANA_POTION_PRICE, "Rend 50 % de votre ressource.", n -> Items.manaPotion(n), TownMenus::alchemist);
        // Le cristal de téléportation n'est plus vendu : réutilisable, chacun a le sien (l'aubergiste le lie et le rend).
        m.set(12, Items.icon(Material.PRISMARINE_CRYSTALS, "<#7FD4FF>Cristal de téléportation",
                "<#8C96A3>Réutilisable : chacun a le sien.", "<#5A6270>L'aubergiste le lie à son auberge."), null);
        if (d.level >= 5) {
            product(plugin, m, p, d, 13, Items.healCrystal(), Items.HEAL_CRYSTAL_PRICE, "Soin d'urgence (clic droit).",
                    n -> amount(Items.healCrystal(), n), TownMenus::alchemist);
        } else {
            m.set(13, Items.icon(Material.GRAY_DYE, "<#8C96A3>Cristal de soin", "<#FF5C5C>Niveau 5 requis."), null);
        }
        int slot = 14;
        for (Boost b : Boost.values()) {
            if (b.seller == Boost.Seller.ALCHEMIST && b.price > 0 && slot <= 17) {
                product(plugin, m, p, d, slot++, b.item(1), b.price, b.effect + " pendant " + b.duration() + ".", b::item, TownMenus::alchemist);
            }
        }
        m.set(22, activeIcon(d), null);
        Menus.frame(m, 3);
        m.open(p);
    }

    // ------------------------------------------------------------------ Prêtre

    public static void priest(OrbitMMO plugin, Player p) {
        PlayerData d = client(plugin, p);
        if (d == null) {
            return;
        }
        Menu m = new Menu(3, title("#FFF3B0", "Autel de Frère Anselme", d)).skin("Autel", "Frère Anselme", Menus.gold(d));
        int slot = 10;
        for (Boost b : Boost.values()) {
            if (b.seller == Boost.Seller.PRIEST && b.price > 0 && slot <= 14) {
                product(plugin, m, p, d, slot++, b.item(1), b.price, b.effect + " pendant " + b.duration() + ".", b::item, TownMenus::priest);
            }
        }
        // Bénédiction : gratuite, directement sur le joueur.
        Boost bless = Boost.BENEDICTION;
        long left = d.boosts.getOrDefault(bless.id, 0L) - System.currentTimeMillis();
        m.set(15, Items.icon(Material.NETHER_STAR, "<" + bless.hex + "><b>Bénédiction</b>",
                "<#D7DCE2>" + bless.effect + " pendant " + bless.duration() + ".",
                "<#8C96A3>Offerte par Frère Anselme.",
                "",
                left > 0 ? "<#5EE89A>✔ Active encore " + minutes(left) + " · clic : la renouveler" : "<#5EE89A>▶ Clic : recevoir la bénédiction"),
                t -> {
                    PlayerData dd = client(plugin, p);
                    if (dd == null) {
                        return;
                    }
                    plugin.progression().applyBoost(p, dd, bless);
                    p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 2.2, 0), 20, 0.3, 0.2, 0.3, 0.02);
                    Text.msg(p, "<#FFF3B0>Frère Anselme <#5A6270>» <#D7DCE2><i>Que les étoiles veillent sur toi.");
                    priest(plugin, p);
                });
        m.set(22, activeIcon(d), null);
        Menus.frame(m, 3);
        m.open(p);
    }

    // ------------------------------------------------------------------ Commun

    private interface Maker {
        ItemStack make(int amount);
    }

    private interface Reopen {
        void open(OrbitMMO plugin, Player p);
    }

    /** Article à vendre : clic = 1, Maj + clic = 5. */
    private static void product(OrbitMMO plugin, Menu m, Player p, PlayerData d, int slot, ItemStack shown, long price, String desc,
                                Maker maker, Reopen reopen) {
        ItemStack icon = shown.clone();
        ItemMeta meta = icon.getItemMeta();
        meta.lore(Text.lore(List.of(
                "<#D7DCE2>" + desc,
                "",
                "<#8C96A3>Prix : <" + (d.gold >= price ? "#FFD66E" : "#FF5C5C") + ">" + Text.num(price) + " or",
                "<#5EE89A>Clic : acheter 1 <#5A6270>· <#5EE89A>Maj + clic : acheter 5")));
        icon.setItemMeta(meta);
        m.set(slot, icon, click -> {
            PlayerData dd = client(plugin, p);
            if (dd == null) {
                return;
            }
            int n = click.isShiftClick() ? 5 : 1;
            if (pay(p, dd, price * n)) {
                Items.give(p, maker.make(n));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.6f, 1.2f);
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.4f);
            }
            reopen.open(plugin, p);
        });
    }

    /** Effets en cours et temps restant. */
    private static ItemStack activeIcon(PlayerData d) {
        long now = System.currentTimeMillis();
        List<String> lore = new ArrayList<>();
        for (Boost b : Boost.values()) {
            long left = d.boosts.getOrDefault(b.id, 0L) - now;
            if (left > 0) {
                lore.add(b.tag() + " <#8C96A3>· " + minutes(left) + " <#5A6270>(" + b.effect + ")");
            }
        }
        if (lore.isEmpty()) {
            lore.add("<#5A6270>Aucun effet en cours.");
        }
        lore.add("");
        lore.add("<#5A6270>Les objets se consomment au [Clic droit].");
        return Items.icon(Material.CLOCK, "<#D7DCE2><b>Vos effets actifs</b>", lore);
    }

    private static ItemStack amount(ItemStack it, int n) {
        it.setAmount(Math.max(1, Math.min(it.getMaxStackSize(), n)));
        return it;
    }

    static String minutes(long millis) {
        long s = Math.max(1, millis / 1000);
        return s >= 60 ? (s + 59) / 60 + " min" : s + " s";
    }

    private static String fmt(double v) {
        return fr.orbit.mmo.classes.TalentDef.format(v);
    }
}
