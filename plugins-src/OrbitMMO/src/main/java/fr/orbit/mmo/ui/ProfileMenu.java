package fr.orbit.mmo.ui;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.classes.ClassDef;
import fr.orbit.mmo.classes.Faction;
import fr.orbit.mmo.classes.Resource;
import fr.orbit.mmo.classes.SpecDef;
import fr.orbit.mmo.classes.SpellDef;
import fr.orbit.mmo.classes.TalentDef;
import fr.orbit.mmo.clazz.WeaponType;
import fr.orbit.mmo.hud.MenuSkin;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.player.Stats;
import fr.orbit.mmo.util.Text;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fiche « Mon personnage » (remplace la boussole hors du lobby) : identité, classe, race, faction,
 * vie, ressource, attaque, défense, sorts chiffrés, talents, bonus et statistiques.
 * Ouverte sur un autre joueur, elle sert d'inspection (sans les boutons d'action ni l'or).
 */
public final class ProfileMenu {

    private ProfileMenu() {
    }

    public static void open(OrbitMMO plugin, Player viewer, Player target) {
        PlayerData d = plugin.players().loaded(target.getUniqueId());
        if (d == null || !plugin.progression().valid(d)) {
            if (viewer.equals(target)) {
                Menus.factionSelect(plugin, viewer);
            } else {
                Text.msg(viewer, "<#8C96A3>" + Text.esc(target.getName()) + " n'a pas encore de personnage.");
            }
            return;
        }
        boolean self = viewer.equals(target);
        ClassDef c = plugin.progression().cls(d);
        SpecDef spec = plugin.progression().spec(d);
        Resource r = plugin.progression().resource(d);
        Menu m = new Menu(6, Text.mm(self ? "<#7FB2FF><b>Mon personnage</b>"
                : "<#7FB2FF><b>" + Text.esc(target.getName()) + "</b> <#8C96A3>· inspection"))
                .skin(MenuSkin.Screen.PROFILE, self ? "Mon personnage" : target.getName());

        // --- Identité
        List<String> id = new ArrayList<>();
        boolean maxed = d.level >= plugin.progression().maxLevel();
        id.add("<#8C96A3>Niveau : <white>" + d.level + (maxed ? " <#FFD66E>(max)"
                : " <#5A6270>(" + Text.num(d.xp) + " / " + Text.num(Stats.xpToNext(d.level)) + " XP)"));
        id.add("<#8C96A3>Faction : " + d.faction.tag());
        fr.orbit.mmo.guild.Guild guild = plugin.guilds().of(target.getUniqueId());
        id.add("<#8C96A3>Guilde : " + (guild == null ? "<#5A6270>aucune"
                : guild.tag() + " <#5A6270>· " + guild.rank(target.getUniqueId()).tag()));
        id.add("<#8C96A3>Race : <white>" + raceName(d.race));
        id.add("<#8C96A3>Classe : " + c.tag(d.faction) + (spec == null ? "" : " <#5A6270>· " + spec.tag()));
        if (spec != null) {
            id.add("<#8C96A3>Rôle : " + spec.role.tag());
        }
        id.add("<#8C96A3>Niveau d'objet : <#FFD66E>" + fr.orbit.mmo.item.Loot.averageItemLevel(plugin, target, d));
        id.add("<#8C96A3>Étage : <white>" + d.floor);
        if (self) {
            id.add("<#8C96A3>Or : <#FFD66E>" + Text.num(d.gold));
        }
        m.set(4, Menus.head(target, "<white><b>" + Text.esc(target.getName()) + "</b>", id), null);

        m.set(10, classIcon(plugin, d, c, spec, r), self ? t -> Menus.specSelect(plugin, viewer) : null);
        m.set(11, raceIcon(d), null);
        m.set(12, factionIcon(d.faction, guild), self ? t -> GuildMenus.open(plugin, viewer) : null);
        m.set(13, lifeIcon(d), null);
        m.set(14, resourceIcon(d, r), null);
        m.set(15, attackIcon(plugin, target, d), null);
        m.set(16, defenseIcon(plugin, target, d), null);

        // --- Sorts connus (rangées 3 à 5), avec leurs valeurs pour ce personnage
        int slot = 18;
        for (SpellDef s : plugin.progression().known(d)) {
            if (slot > 44) {
                break;
            }
            List<String> lore = new ArrayList<>(plugin.bar().describe(s, d));
            List<String> nums = plugin.bar().numbers(target, d, s);
            if (!nums.isEmpty()) {
                lore.add("");
                lore.addAll(nums);
            }
            lore.add("<#5A6270>Objet : " + s.category.tag() + (s.talentOnly ? " <#5A6270>· obtenu par un talent" : ""));
            m.set(slot++, Items.icon(s.icon, "<" + s.hex + "><b>" + s.name + "</b>", lore), null);
        }

        // --- Rangée du bas
        if (self) {
            m.set(45, Items.icon(Material.ARROW, "<#8C96A3>Menu"), t -> Menus.main(plugin, viewer));
        }
        m.set(46, talentsIcon(plugin, d, spec, self), self ? t -> Menus.talents(plugin, viewer, null) : null);
        m.set(47, bonusIcon(d), null);
        m.set(48, statsIcon(d), null);
        if (plugin.mounts() != null) {
            m.set(50, plugin.mounts().menuIcon(d, self), self ? t -> plugin.mounts().openStable(viewer) : null);
        }
        m.set(51, gearIcon(plugin, target, d), null);
        if (self) {
            m.set(52, Items.icon(Material.ARMOR_STAND, "<#B9A6FF><b>Garde-robe</b>",
                    "<#8C96A3>Changer l'apparence de vos pièces",
                    "<#8C96A3>sans changer leurs statistiques.",
                    "<#8C96A3>Apparences débloquées : <white>" + d.looks.size()), t -> WardrobeMenus.open(plugin, viewer));
            m.set(49, Items.icon(Material.ENCHANTED_BOOK, "<#7FB2FF><b>Livre de sorts</b>", "<#8C96A3>Choisir le sort de chaque objet"),
                    t -> Menus.spellbook(plugin, viewer));
            m.set(53, Items.icon(Material.NETHER_STAR, "<#FFD66E><b>Retour au lobby</b>", "<#8C96A3>Quitter Orbit MMO pour le lobby.",
                    "<#5A6270>Aussi : /lobby"), t -> {
                viewer.closeInventory();
                plugin.toLobby(viewer);
            });
        }
        Menus.frame(m, 6);
        m.open(viewer);
        Menus.click(viewer);
    }

    // ------------------------------------------------------------------ Icônes

    private static String raceName(String race) {
        if (race == null || race.isEmpty()) {
            return "Humain";
        }
        return race.substring(0, 1).toUpperCase(Locale.ROOT) + race.substring(1);
    }

    /** Équipement : niveau d'objet moyen, niveau de chaque pièce, sets portés et leurs bonus. */
    private static ItemStack gearIcon(OrbitMMO plugin, Player target, PlayerData d) {
        List<String> lore = new ArrayList<>();
        ItemStack weapon = plugin.bar().weapon(target, d);
        lore.add("<#8C96A3>Moyenne : arme + 4 pièces" + (fr.orbit.mmo.item.Loot.shieldUser(plugin, d) ? " + bouclier" : ""));
        lore.add("<#5A6270>(un emplacement vide compte 0)");
        lore.add("");
        lore.add(pieceLine("Arme", "weapon".equals(Items.type(weapon)) ? weapon : null));
        String[] labels = {"Bottes", "Jambières", "Plastron", "Casque"};
        ItemStack[] armor = target.getInventory().getArmorContents();
        for (int i = armor.length - 1; i >= 0; i--) {
            lore.add(pieceLine(labels[i], armor[i]));
        }
        if (fr.orbit.mmo.item.Loot.shieldUser(plugin, d)) {
            ItemStack off = target.getInventory().getItemInOffHand();
            lore.add(pieceLine("Bouclier", "shield".equals(Items.type(off)) ? off : null));
        }
        java.util.Map<fr.orbit.mmo.item.GearSet, Integer> sets = fr.orbit.mmo.item.Loot.setCounts(target, d, weapon);
        for (java.util.Map.Entry<fr.orbit.mmo.item.GearSet, Integer> e : sets.entrySet()) {
            lore.add("");
            lore.add("<#FFD66E>Set " + Text.esc(e.getKey().name) + " <#8C96A3>(" + e.getValue() + " / " + e.getKey().pieces.size() + ")");
            lore.addAll(e.getKey().describe(e.getValue()));
        }
        int min = plugin.getConfig().getInt("niveau-objet.pilier", 0);
        if (min > 0) {
            lore.add("");
            lore.add("<#8C96A3>Minimum pour le Pilier : <white>" + min);
        }
        return Items.icon(Material.IRON_CHESTPLATE, "<#FFD66E><b>Niveau d'objet : "
                + fr.orbit.mmo.item.Loot.averageItemLevel(plugin, target, d) + "</b>", lore);
    }

    private static String pieceLine(String label, ItemStack it) {
        int lvl = fr.orbit.mmo.item.Loot.itemLevel(it);
        if (lvl <= 0) {
            return "<#5A6270>" + label + " : —";
        }
        fr.orbit.mmo.item.Rarity r = fr.orbit.mmo.item.Loot.rarity(it);
        return "<#8C96A3>" + label + " : <" + r.hex + ">" + lvl;
    }

    private static ItemStack classIcon(OrbitMMO plugin, PlayerData d, ClassDef c, SpecDef spec, Resource r) {
        List<String> lore = new ArrayList<>();
        for (String line : c.lore) {
            lore.add("<#8C96A3>" + line);
        }
        lore.add("");
        if (spec != null) {
            lore.add("<#8C96A3>Spécialisation : " + spec.tag() + " <#5A6270>· " + spec.role.tag());
            for (String line : spec.lore) {
                lore.add("<#5A6270>" + line);
            }
        } else {
            lore.add("<#8C96A3>Spécialisation : <#FFD66E>à choisir !");
        }
        WeaponType weapon = spec != null && spec.weapon != null ? spec.weapon : c.weapon;
        boolean shield = spec != null && spec.weapon != null ? spec.shield : c.shield || (spec != null && spec.shield);
        lore.add("<#8C96A3>Arme : <white>" + weapon.label + (shield ? " + bouclier" : ""));
        lore.add("<#8C96A3>Armure : <white>" + c.armor.label);
        lore.add("<#8C96A3>Ressource : <" + r.hex + ">" + r.label);
        if (c.hpMult != 1.0) {
            lore.add("<#8C96A3>Points de vie de classe : <white>×" + TalentDef.format(c.hpMult));
        }
        return Items.icon(spec != null ? spec.icon : c.icon, "<" + c.hex + "><b>Classe : " + c.name(d.faction) + "</b>", lore);
    }

    private static ItemStack raceIcon(PlayerData d) {
        return Items.icon(Material.TOTEM_OF_UNDYING, "<#D7DCE2><b>Race : " + raceName(d.race) + "</b>",
                "<#8C96A3>Polyvalent : aucune caractéristique spéciale.",
                "",
                "<#5A6270>D'autres races arrivent bientôt.");
    }

    private static ItemStack factionIcon(Faction f, fr.orbit.mmo.guild.Guild guild) {
        List<String> lore = new ArrayList<>();
        for (String line : f.lore) {
            lore.add("<#8C96A3>" + line);
        }
        lore.add("");
        lore.add("<#8C96A3>Capitale : <white>" + f.capital);
        lore.add("<#5A6270>Combat contre l'autre faction hors des zones sûres.");
        lore.add("");
        if (guild == null) {
            lore.add("<#8C96A3>Guilde : <#5A6270>aucune");
        } else {
            lore.add("<#8C96A3>Guilde : " + guild.tag() + " <#5A6270>· " + guild.size() + " membre(s)");
            lore.add("<#8C96A3>Pilier des Astres : étage franchi <white>" + (guild.towerCleared() > 0 ? guild.towerCleared() : "aucun"));
        }
        return Items.icon(f.icon, "<" + f.hex + "><b>Faction : " + f.label + "</b>", lore);
    }

    private static ItemStack lifeIcon(PlayerData d) {
        double max = Stats.maxHp(d);
        List<String> lore = new ArrayList<>();
        lore.add("<#8C96A3>PV : <white>" + Text.num((long) Math.ceil(d.hp)) + " / " + Text.num((long) max));
        double bonus = d.mods.get("pv");
        if (bonus != 0) {
            lore.add("<#8C96A3>Bonus de PV : <white>" + signed(bonus) + " %");
        }
        lore.add("");
        lore.add("<#8C96A3>Régénération :");
        lore.add("<#5A6270> · en ville : <white>5 %/s");
        lore.add("<#5A6270> · hors combat (après 6 s) : <white>2 %/s");
        lore.add("<#5A6270> · potion de soin : <white>+7 %/s pendant 5 s");
        if (d.mods.get("soin-sur-kill") > 0) {
            lore.add("<#5A6270> · à chaque ennemi vaincu : <white>" + TalentDef.format(d.mods.get("soin-sur-kill")) + " % des PV");
        }
        return Items.icon(Material.GLISTERING_MELON_SLICE, "<#FF5C5C><b>Points de vie</b>", lore);
    }

    private static ItemStack resourceIcon(PlayerData d, Resource r) {
        List<String> lore = new ArrayList<>();
        String cur = r == Resource.ORBES ? String.valueOf((int) Math.floor(d.resource)) : String.valueOf(Math.round(d.resource));
        lore.add("<" + r.hex + ">" + r.label + " : <white>" + cur + " / " + TalentDef.format(r.max));
        lore.add("<#8C96A3>En combat : <white>" + regen(r.regenCombat));
        lore.add("<#8C96A3>Hors combat : <white>" + regen(r.regenRest));
        double gen = d.mods.get("ressource");
        if (gen != 0) {
            lore.add("<#8C96A3>Génération : <white>" + signed(gen) + " %");
        }
        lore.add("");
        switch (r) {
            case RAGE -> {
                double extra = d.mods.get("rage-par-coup");
                lore.add("<#8C96A3>Se remplit en frappant avec l'arme (<white>+" + TalentDef.format(8 + extra) + "</white> par coup)");
                lore.add("<#8C96A3>et en subissant des dégâts. Retombe hors combat.");
            }
            case MANA -> lore.add("<#8C96A3>Se régénère lentement en combat, vite au repos.");
            case ENERGIE -> lore.add("<#8C96A3>Se recharge très vite : enchaînez les techniques.");
            case CONCENTRATION -> lore.add("<#8C96A3>Se recharge régulièrement, même en combat.");
            case FERVEUR -> {
                lore.add("<#8C96A3>Se gagne en frappant (<white>+5</white> par coup)");
                lore.add("<#8C96A3>et retombe hors combat.");
            }
            case ORBES -> lore.add("<#8C96A3>Se gagnent avec certains sorts, s'éteignent hors combat.");
        }
        lore.add("<#5A6270>Coût et gain de chaque sort : voir les sorts ci-dessous.");
        Material icon = switch (r) {
            case RAGE -> Material.BLAZE_POWDER;
            case MANA -> Material.LAPIS_LAZULI;
            case ENERGIE -> Material.SUGAR;
            case CONCENTRATION -> Material.SPECTRAL_ARROW;
            case FERVEUR -> Material.GLOWSTONE_DUST;
            case ORBES -> Material.ENDER_EYE;
        };
        return Items.icon(icon, "<" + r.hex + "><b>Ressource : " + r.label + "</b>", lore);
    }

    private static ItemStack attackIcon(OrbitMMO plugin, Player p, PlayerData d) {
        ItemStack weapon = plugin.bar().weapon(p, d);
        WeaponType type = WeaponType.of(weapon);
        double atk = Items.attackOf(weapon);
        double power = plugin.combat().power(p, d);
        double degats = d.mods.get("degats");
        double hit = power * d.mods.mult("degats", null);
        List<String> lore = new ArrayList<>();
        lore.add("<#8C96A3>Arme : <white>" + (weapon == null || type == WeaponType.NONE ? "aucune" : type.label)
                + " <#5A6270>(attaque " + TalentDef.format(atk) + ")");
        lore.add("<#8C96A3>Puissance : <white>" + Text.num(Math.round(power)) + " <#5A6270>(attaque +4 % par niveau)");
        lore.add("<#8C96A3>Coup d'arme : <white>≈ " + Text.num(Math.round(hit)) + " <#5A6270>(critique ≈ "
                + Text.num(Math.round(hit * plugin.combat().critMult(d))) + ")");
        if (degats != 0) {
            lore.add("<#8C96A3>Bonus de dégâts : <white>" + signed(degats) + " %");
        }
        lore.add("<#8C96A3>Coup critique : <white>" + Math.round(plugin.combat().critChance(d, null) * 100) + " %"
                + " <#5A6270>· dégâts ×" + TalentDef.format(plugin.combat().critMult(d)));
        if (d.mods.get("vol-de-vie") > 0) {
            lore.add("<#8C96A3>Vol de vie : <white>" + TalentDef.format(d.mods.get("vol-de-vie")) + " %");
        }
        if (d.mods.get("soins") != 0) {
            lore.add("<#8C96A3>Soins : <white>" + signed(d.mods.get("soins")) + " %");
        }
        lore.add("");
        lore.add("<#5A6270>Dégâts des sorts = puissance × coefficient du sort.");
        Material icon = weapon == null || weapon.getType().isAir() ? Material.IRON_SWORD : weapon.getType();
        return Items.icon(icon, "<#FFB347><b>Attaque</b>", lore);
    }

    private static ItemStack defenseIcon(OrbitMMO plugin, Player p, PlayerData d) {
        AttributeInstance a = p.getAttribute(Attribute.ARMOR);
        double armor = a == null ? 0 : a.getValue();
        List<String> lore = new ArrayList<>();
        lore.add("<#8C96A3>Armure : <white>" + TalentDef.format(Math.round(armor)) + " <#5A6270>(−"
                + Math.round(plugin.combat().armorReduction(p) * 100) + " % de dégâts subis)");
        if (d.mods.get("armure") > 0) {
            lore.add("<#8C96A3>Réduction des dégâts : <white>" + TalentDef.format(d.mods.get("armure")) + " %");
        }
        if (d.mods.get("esquive") > 0) {
            lore.add("<#8C96A3>Esquive : <white>" + TalentDef.format(d.mods.get("esquive")) + " % <#5A6270>(coups de mêlée)");
        }
        if (d.mods.get("blocage") > 0) {
            lore.add("<#8C96A3>Blocage : <white>" + TalentDef.format(d.mods.get("blocage")) + " % <#5A6270>(bouclier : −50 % sur le coup)");
        }
        if (d.mods.get("epines") > 0) {
            lore.add("<#8C96A3>Épines : <white>" + TalentDef.format(d.mods.get("epines")) + " % <#5A6270>des coups renvoyés");
        }
        if (d.mods.get("vitesse") != 0) {
            lore.add("<#8C96A3>Vitesse : <white>" + signed(d.mods.get("vitesse")) + " %");
        }
        return Items.icon(Material.SHIELD, "<#E6C35C><b>Défense</b>", lore);
    }

    private static ItemStack talentsIcon(OrbitMMO plugin, PlayerData d, SpecDef spec, boolean self) {
        List<String> lore = new ArrayList<>();
        if (spec == null) {
            lore.add("<#5A6270>Choisissez d'abord une spécialisation.");
            return Items.icon(Material.EXPERIENCE_BOTTLE, "<#5EE89A><b>Talents</b>", lore);
        }
        int spent = d.spent(spec.id);
        int free = plugin.progression().talentPointsFree(d);
        lore.add("<#8C96A3>Arbre : " + spec.tag());
        lore.add("<#8C96A3>Points dépensés : <white>" + spent + " / " + plugin.progression().talentPointsTotal(d)
                + (free > 0 ? " <#FFD66E>(" + free + " à dépenser)" : ""));
        lore.add("");
        int shown = 0;
        for (TalentDef t : spec.talents) {
            int rank = d.rank(spec.id, t.id);
            if (rank <= 0) {
                continue;
            }
            if (shown++ >= 12) {
                lore.add("<#5A6270>…");
                break;
            }
            lore.add("<#D7DCE2>" + t.name + " <#5A6270>" + rank + "/" + t.maxRank);
        }
        if (shown == 0) {
            lore.add("<#5A6270>Aucun talent pris pour l'instant.");
        }
        if (self) {
            lore.add("");
            lore.add("<#5EE89A>Clic : arbre de talents");
        }
        return Items.icon(Material.EXPERIENCE_BOTTLE, "<#5EE89A><b>Talents</b>", lore);
    }

    private static ItemStack bonusIcon(PlayerData d) {
        Map<String, Double> general = new TreeMap<>();
        int perSpell = 0;
        for (Map.Entry<String, Double> e : d.mods.values().entrySet()) {
            if (e.getValue() == 0) {
                continue;
            }
            if (e.getKey().contains(".")) {
                perSpell++;
            } else {
                general.put(e.getKey(), e.getValue());
            }
        }
        List<String> lore = new ArrayList<>();
        lore.add("<#5A6270>Spécialisation + talents :");
        if (general.isEmpty() && perSpell == 0) {
            lore.add("<#5A6270>Aucun bonus pour l'instant.");
        }
        for (Map.Entry<String, Double> e : general.entrySet()) {
            lore.add("<#D7DCE2>" + label(e.getKey(), e.getValue()));
        }
        if (perSpell > 0) {
            lore.add("<#8C96A3>+ " + perSpell + " bonus propres à certains sorts");
            lore.add("<#5A6270>(déjà comptés dans les valeurs des sorts)");
        }
        long now = System.currentTimeMillis();
        boolean header = false;
        for (fr.orbit.mmo.item.Boost b : fr.orbit.mmo.item.Boost.values()) {
            long left = d.boosts.getOrDefault(b.id, 0L) - now;
            if (left <= 0) {
                continue;
            }
            if (!header) {
                lore.add("");
                lore.add("<#5A6270>Effets de la ville (compris ci-dessus) :");
                header = true;
            }
            lore.add(b.tag() + " <#8C96A3>· " + TownMenus.minutes(left));
        }
        return Items.icon(Material.AMETHYST_SHARD, "<#B36BFF><b>Bonus actifs</b>", lore);
    }

    private static String label(String key, double v) {
        return switch (key) {
            case "epines" -> signed(v) + " % des coups renvoyés (épines)";
            case "soin-sur-kill" -> signed(v) + " % des PV par ennemi vaincu";
            case "rage-par-coup" -> signed(v) + " rage par coup d'arme";
            case "recharge" -> "−" + TalentDef.format(v) + " s de recharge";
            case "cout" -> "−" + TalentDef.format(v) + " de coût des sorts";
            case "duree" -> signed(v) + " s de durée des effets";
            case "incantation" -> "−" + TalentDef.format(v) + " s d'incantation";
            default -> Menus.modLabel(key, v);
        };
    }

    private static ItemStack statsIcon(PlayerData d) {
        return Items.icon(Material.WRITABLE_BOOK, "<#D7DCE2><b>Statistiques</b>",
                "<#8C96A3>Monstres vaincus : <white>" + Text.num(d.kills),
                "<#8C96A3>Victoires contre l'autre faction : <white>" + Text.num(d.playerKills),
                "<#8C96A3>Morts : <white>" + Text.num(d.deaths),
                "<#8C96A3>Étage actuel : <white>" + d.floor);
    }

    // ------------------------------------------------------------------ Formats

    private static String regen(double perSecond) {
        if (perSecond > 0) {
            return "+" + TalentDef.format(perSecond) + " /s";
        }
        if (perSecond < 0) {
            return "baisse de " + TalentDef.format(-perSecond) + " /s";
        }
        return "aucune régénération";
    }

    private static String signed(double v) {
        return (v > 0 ? "+" : "") + TalentDef.format(v);
    }
}
