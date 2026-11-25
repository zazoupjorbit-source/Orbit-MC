package fr.orbit.mmo.ui;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.classes.ClassDef;
import fr.orbit.mmo.classes.SpecDef;
import fr.orbit.mmo.clazz.WeaponType;
import fr.orbit.mmo.item.ArmorType;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.item.Loot;
import fr.orbit.mmo.item.Loot.Base;
import fr.orbit.mmo.item.Loot.Slot;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.util.Fx;
import fr.orbit.mmo.util.Text;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Armurerie de Torvald : toutes les armes (14 modèles), les boucliers et les armures pièce par pièce,
 * sur 8 paliers (recrue → orbitium). Tout est de qualité Commune ; le mieux tombe des monstres et des boss.
 */
public final class ArmoryMenus {

    enum Cat {
        UNE_MAIN("Armes à une main", Material.IRON_SWORD, Base.EPEE, Base.RAPIERE, Base.KATANA),
        DEUX_MAINS("Armes à deux mains", Material.IRON_AXE, Base.ESPADON, Base.HACHE, Base.FAUX, Base.BEC),
        AGILES("Dagues et lances", Material.IRON_SPEAR, Base.DAGUE, Base.LANCE),
        LOURDES("Masses et arcs", Material.MACE, Base.MASSE, Base.ARC),
        MAGIE("Armes de lanceur de sorts", Material.BLAZE_ROD, Base.BATON, Base.BAGUETTE, Base.GRIMOIRE),
        BOUCLIERS("Boucliers", Material.SHIELD),
        ARMURES("Armures", Material.IRON_CHESTPLATE);

        final String label;
        final Material icon;
        final Base[] bases;

        Cat(String label, Material icon, Base... bases) {
            this.label = label;
            this.icon = icon;
            this.bases = bases;
        }
    }

    private ArmoryMenus() {
    }

    private static List<WeaponType> classWeapons(OrbitMMO plugin, PlayerData d) {
        List<WeaponType> out = new ArrayList<>();
        ClassDef c = plugin.progression().cls(d);
        SpecDef s = plugin.progression().spec(d);
        if (c != null) {
            out.add(c.weapon);
        }
        if (s != null && s.weapon != null) {
            out.add(s.weapon);
        }
        return out;
    }

    private static boolean shieldUser(OrbitMMO plugin, PlayerData d) {
        ClassDef c = plugin.progression().cls(d);
        SpecDef s = plugin.progression().spec(d);
        return (c != null && c.shield) || (s != null && s.shield);
    }

    private static boolean advised(OrbitMMO plugin, PlayerData d, Cat cat) {
        if (cat == Cat.ARMURES) {
            return true;
        }
        if (cat == Cat.BOUCLIERS) {
            return shieldUser(plugin, d);
        }
        List<WeaponType> mine = classWeapons(plugin, d);
        for (Base b : cat.bases) {
            if (mine.contains(b.type)) {
                return true;
            }
        }
        return false;
    }

    public static void open(OrbitMMO plugin, Player p) {
        PlayerData d = Menus.trader(plugin, p);
        if (d == null) {
            return;
        }
        Menu m = new Menu(4, Menus.shopTitle("#FF8A3B", "Armurerie de Torvald", d)).skin("Armurerie", "Torvald", Menus.gold(d));
        m.set(4, Items.icon(Material.OAK_HANGING_SIGN, "<#FF8A3B><b>L'armurerie</b>",
                "<#D7DCE2>Toutes les armes, armures et boucliers,",
                "<#D7DCE2>du palier recrue (niv. 1) à l'orbitium (niv. 72).",
                "",
                "<#8C96A3>Ici, tout est de qualité <#D7DCE2>Commune<#8C96A3>.",
                "<#8C96A3>Les objets <#5CE65C>Inhabituels<#8C96A3>, <#4FA3FF>Rares<#8C96A3>, <#B36BFF>Épiques",
                "<#8C96A3>et <#FF8A3B>Légendaires<#8C96A3> tombent des monstres et des boss.",
                "<#8C96A3>Chaque arme a au moins une châsse à rune (Ysolde)."), null);
        int slot = 10;
        for (Cat cat : Cat.values()) {
            List<String> lore = new ArrayList<>();
            for (Base b : cat.bases) {
                lore.add("<#D7DCE2>· " + b.label);
            }
            if (cat == Cat.BOUCLIERS) {
                lore.add("<#D7DCE2>· Boucliers (main secondaire)");
            } else if (cat == Cat.ARMURES) {
                lore.add("<#D7DCE2>· Tissu, cuir, mailles et plaques");
                lore.add("<#D7DCE2>· Casques, plastrons, jambières, bottes");
                lore.add("<#D7DCE2>· Ensembles complets (−10 %)");
            }
            lore.add("");
            lore.add(advised(plugin, d, cat) ? "<#5EE89A>✔ Pour votre classe" : "<#8C96A3>Pas l'arme de votre classe");
            lore.add("<#5A6270>[Clic] voir le rayon");
            m.set(slot++, Items.icon(cat.icon, "<#FFD66E><b>" + cat.label + "</b>", lore), t -> {
                Menus.click(p);
                category(plugin, p, cat);
            });
        }
        m.set(27, Items.icon(Material.ARROW, "<#8C96A3>Retour à la forge"), t -> TownMenus.forge(plugin, p));
        m.set(31, Items.icon(Material.GOLD_INGOT, "<#FFD66E><b>Vendre mon butin</b>", "<#8C96A3>Torvald rachète ce que vous rapportez."),
                t -> Menus.buyback(plugin, p));
        Menus.frame(m, 4);
        m.open(p);
    }

    static void category(OrbitMMO plugin, Player p, Cat cat) {
        PlayerData pd = Menus.trader(plugin, p);
        ArmorType mineType = pd == null ? null : ArmorType.of(plugin.progression().cls(pd));
        category(plugin, p, cat, mineType == null ? ArmorType.PLAQUES : mineType);
    }

    /** Rayon ; pour les armures, celles du type donné (la classe ne porte que le sien). */
    static void category(OrbitMMO plugin, Player p, Cat cat, ArmorType type) {
        PlayerData d = Menus.trader(plugin, p);
        if (d == null) {
            return;
        }
        Runnable reopen = () -> category(plugin, p, cat, type);
        ArmorType classType = ArmorType.of(plugin.progression().cls(d));
        ClassDef c = plugin.progression().cls(d);
        Color dye = c == null ? null : Fx.color(c.hex);
        int content = cat == Cat.ARMURES ? 5 : cat == Cat.BOUCLIERS ? 1 : cat.bases.length;
        int rows = Math.max(3, content + 1);
        Menu m = new Menu(rows, Menus.shopTitle("#FF8A3B", cat.label, d)).skin(cat.label, "Armurerie de Torvald", Menus.gold(d));
        List<WeaponType> mine = classWeapons(plugin, d);

        if (cat == Cat.ARMURES) {
            boolean wearable = classType == null || classType == type;
            for (Slot s : Slot.values()) {
                int row = s.ordinal();
                ItemStack head = Items.icon(s.material(type, 2), "<" + type.hex + "><b>" + type.piece(s) + "</b> <#8C96A3>(" + s.label + ")",
                        "<#8C96A3>Armure de " + type.tag().toLowerCase(java.util.Locale.ROOT),
                        "<#8C96A3>Bonus propre : <#7CFF8A>" + type.implicit(s).label);
                Items.model(head, s.model(type, 2));
                m.set(row * 9, head, null);
                for (int t = 0; t < Loot.TIERS; t++) {
                    int tier = t;
                    long price = Loot.ARMOR_PRICE[t];
                    m.set(row * 9 + 1 + t, Menus.priced(Loot.shopArmor(type, s, t, dye), price, Loot.TIER_LEVEL[t], d),
                            click -> Menus.buy(plugin, p, d, price, Loot.TIER_LEVEL[tier], () -> Items.give(p, Loot.shopArmor(type, s, tier, dye)), reopen));
                }
            }
            int row = 4;
            m.set(row * 9, Items.icon(Material.ARMOR_STAND, "<#FFD66E><b>Ensembles complets</b>",
                    wearable ? "<#8C96A3>Les 4 pièces, équipées à l'achat," : "<#FF8A3B>Votre classe ne porte pas cette armure :",
                    wearable ? "<#8C96A3>10 % moins cher." : "<#FF8A3B>les pièces iront dans le sac.",
                    "<#5A6270>L'ancienne armure revient dans le sac."), null);
            for (int t = 0; t < Loot.TIERS; t++) {
                int tier = t;
                long price = Math.round(Loot.ARMOR_PRICE[t] * 4 * 0.9);
                ItemStack icon = Items.icon(Slot.CHEST.material(type, t), "<#D7DCE2>Ensemble " + type.tierName(t),
                        "<#8C96A3>4 pièces de " + type.label.toLowerCase(java.util.Locale.ROOT) + " (qualité Commune).");
                Items.model(icon, Slot.CHEST.model(type, t));
                m.set(row * 9 + 1 + t, Menus.priced(icon, price, Loot.TIER_LEVEL[t], d),
                        click -> Menus.buy(plugin, p, d, price, Loot.TIER_LEVEL[tier], () -> {
                            ItemStack[] set = Loot.armorSet(type, tier, dye);
                            if (!wearable) {
                                for (ItemStack piece : set) {
                                    Items.give(p, piece);
                                }
                                return;
                            }
                            for (ItemStack old : p.getInventory().getArmorContents()) {
                                if (old != null && !old.getType().isAir()) {
                                    Items.give(p, old);
                                }
                            }
                            Items.equip(p, set);
                        }, reopen));
            }
            // Autres types : on peut les acheter (pour l'hôtel des ventes) mais pas les porter.
            int col = 5;
            for (ArmorType other : ArmorType.values()) {
                boolean here = other == type;
                ItemStack b = Items.icon(Slot.CHEST.material(other, 2), "<" + other.hex + "><b>Armures de " + other.label.toLowerCase(java.util.Locale.ROOT) + "</b>",
                        other == classType ? "<#5EE89A>✔ Le type de votre classe" : "<#8C96A3>Votre classe ne le porte pas",
                        here ? "<#7FD4FF>Affiché" : "<#5A6270>[Clic] voir ce rayon");
                Items.model(b, Slot.CHEST.model(other, 2));
                m.set((rows - 1) * 9 + col++, here ? Items.glow(b) : b, here ? null : t -> {
                    Menus.click(p);
                    category(plugin, p, cat, other);
                });
            }
        } else if (cat == Cat.BOUCLIERS) {
            m.set(0, Items.icon(Material.SHIELD, "<#FFD66E><b>Bouclier</b>", "<#8C96A3>En main secondaire.",
                    "<#8C96A3>Bonus propre : <#7CFF8A>blocage",
                    shieldUser(plugin, d) ? "<#5EE89A>✔ Pour votre classe" : "<#8C96A3>Votre classe ne s'en sert pas"), null);
            for (int t = 0; t < Loot.TIERS; t++) {
                int tier = t;
                long price = Loot.SHIELD_PRICE[t];
                m.set(1 + t, Menus.priced(Loot.shopShield(t), price, Loot.TIER_LEVEL[t], d),
                        click -> Menus.buy(plugin, p, d, price, Loot.TIER_LEVEL[tier], () -> Items.give(p, Loot.shopShield(tier)), reopen));
            }
        } else {
            for (int i = 0; i < cat.bases.length; i++) {
                Base b = cat.bases[i];
                int row = i;
                m.set(row * 9, Items.icon(b.material(2), "<#FFD66E><b>" + b.label + "</b>",
                        "<#8C96A3>Catégorie : <white>" + b.type.label,
                        "<#8C96A3>Bonus propre : <#7CFF8A>" + b.implicit.label,
                        mine.contains(b.type) ? "<#5EE89A>✔ Pour votre classe" : "<#8C96A3>Pas l'arme de votre classe"), null);
                for (int t = 0; t < Loot.TIERS; t++) {
                    int tier = t;
                    long price = Loot.WEAPON_PRICE[t];
                    m.set(row * 9 + 1 + t, Menus.priced(Loot.shopWeapon(b, t), price, Loot.TIER_LEVEL[t], d),
                            click -> Menus.buy(plugin, p, d, price, Loot.TIER_LEVEL[tier], () -> Items.give(p, Loot.shopWeapon(b, tier)), reopen));
                }
            }
        }
        int nav = (rows - 1) * 9;
        m.set(nav, Items.icon(Material.ARROW, "<#8C96A3>Retour à l'armurerie"), t -> open(plugin, p));
        m.set(nav + 4, Items.icon(Material.OAK_SIGN, "<#FFD66E><b>Paliers</b>",
                "<#8C96A3>De gauche à droite : recrue, cuivre, acier,",
                "<#8C96A3>électrum, cristal, obsidienne, astralite, orbitium.",
                "<#8C96A3>Survolez un objet pour voir ses bonus."), null);
        Menus.frame(m, rows);
        m.open(p);
    }

    /** Pour les messages : « Niveau 26 ». */
    static String level(int lvl) {
        return Text.num(lvl);
    }
}
