package fr.orbit.reborn.ui;

import fr.orbit.reborn.RebornMMO;
import fr.orbit.reborn.clazz.GameClass;
import fr.orbit.reborn.item.Items;
import fr.orbit.reborn.party.PartyService.Party;
import fr.orbit.reborn.player.PlayerData;
import fr.orbit.reborn.player.Stats;
import fr.orbit.reborn.registry.Defs.FloorDef;
import fr.orbit.reborn.skill.Skill;
import fr.orbit.reborn.skill.Skills;
import fr.orbit.reborn.util.Fx;
import fr.orbit.reborn.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Tous les menus : choix de classe, menu principal, statut, compétences, étages, groupe, marchand. */
public final class Menus {

    private static final ItemStack PANE = Items.icon(Material.BLACK_STAINED_GLASS_PANE, " ");

    private Menus() {
    }

    public static List<String> guide() {
        return List.of(
                "<#D7DCE2>[F] avec votre arme : lancer la compétence",
                "<#D7DCE2>[Jeter] (A/Q) avec votre arme : changer de compétence",
                "<#D7DCE2>[Accroupi + F] ou /mmo : ouvrir le menu",
                "<#D7DCE2>Après une compétence : délai post-mouvement",
                "",
                "<#D7DCE2>Passez sous l'arche de la ville pour changer d'étage.",
                "<#D7DCE2>Battez le boss d'étage pour ouvrir l'étage suivant.",
                "<#D7DCE2>En groupe, enchaînez vos compétences : SWITCH !",
                "<#D7DCE2>Attaquer un joueur rend votre curseur orange.");
    }

    private static void frame(Menu m, int rows) {
        for (int i = 0; i < rows * 9; i++) {
            if (m.getInventory().getItem(i) == null) {
                m.getInventory().setItem(i, PANE);
            }
        }
    }

    private static String pct(double mult) {
        long v = Math.round((mult - 1) * 100);
        return v == 0 ? "normal" : (v > 0 ? "+" : "") + v + " %";
    }

    private static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
    }

    // ------------------------------------------------------------------ Choix de classe

    public static void classSelect(RebornMMO plugin, Player p) {
        Menu m = new Menu(4, Text.mm("<#3A7FFF><b>Choisissez votre voie</b>"));
        m.forceOpen = true;
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 22};
        GameClass[] classes = GameClass.values();
        for (int i = 0; i < classes.length && i < slots.length; i++) {
            GameClass c = classes[i];
            List<String> lore = new ArrayList<>();
            lore.add("<#8C96A3>Arme : <white>" + c.weapon.label + (c.shield ? " + bouclier" : ""));
            lore.add("<#8C96A3>PV : <white>" + pct(c.hpMult) + "   <#8C96A3>Dégâts : <white>" + pct(c.dmgMult));
            lore.add("");
            for (String line : c.lore) {
                lore.add("<#D7DCE2>" + line);
            }
            lore.add("");
            lore.add("<#8C96A3>Compétences : <white>" + Skills.tree(c.id).size());
            lore.add("<#5EE89A>▶ Cliquez pour choisir");
            m.set(slots[i], Items.icon(c.icon, "<" + c.hex + "><b>" + c.label + "</b>", lore), t -> choose(plugin, p, c));
        }
        m.set(31, Items.icon(Material.BOOK, "<#FFD66E><b>Comment jouer</b>", guide()), null);
        frame(m, 4);
        m.open(p);
    }

    private static void choose(RebornMMO plugin, Player p, GameClass c) {
        PlayerData d = plugin.players().get(p);
        if (d.classId != null) {
            return;
        }
        d.classId = c.id;
        d.hp = Stats.maxHp(d);
        d.equipped.clear();
        d.selected = 0;
        for (Skill s : Skills.available(d)) {
            if (d.equipped.size() < 4) {
                d.equipped.add(s.id);
            }
        }
        d.col += plugin.getConfig().getLong("start.col", 100);
        p.getInventory().clear();
        p.getInventory().setItem(0, Items.weapon(c.weapon, 0));
        if (c.shield) {
            p.getInventory().setItemInOffHand(Items.shield());
        }
        Items.equip(p, Items.armorSet(0, Fx.color(c.hex)));
        p.getInventory().setItem(1, Items.potion(5));
        p.getInventory().setItem(2, Items.tpCrystal());
        p.closeInventory();
        plugin.playerListener().prepareBody(p, d);
        Fx.teleport(p.getLocation());
        Text.title(p, "<" + c.hex + "><b>" + c.label + "</b>", "<#D7DCE2>Votre aventure commence…", 10, 60, 20);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        Text.msg(p, "<#D7DCE2>Vous êtes <" + c.hex + "><b>" + c.label + "</b></" + c.hex + ">. Contrôles :");
        for (String line : guide().subList(0, 4)) {
            p.sendMessage(Text.mm("  " + line));
        }
        plugin.players().save(d);
    }

    // ------------------------------------------------------------------ Menu principal

    public static void main(RebornMMO plugin, Player p) {
        PlayerData d = plugin.players().get(p);
        if (d.classId == null) {
            classSelect(plugin, p);
            return;
        }
        GameClass c = d.gameClass();
        Menu m = new Menu(3, Text.mm("<#7FB2FF><b>Menu</b></#7FB2FF> <#8C96A3>· " + Text.esc(p.getName())));
        m.set(4, head(p, "<white><b>" + Text.esc(p.getName()) + "</b>", List.of(
                "<#8C96A3>Classe : <" + c.hex + ">" + c.label,
                "<#8C96A3>Niveau : <white>" + d.level + " <#5A6270>(" + Text.num(d.xp) + " / " + Text.num(Stats.xpToNext(d.level)) + " XP)",
                "<#8C96A3>PV : <white>" + Text.num((long) Math.ceil(d.hp)) + " / " + Text.num((long) Stats.maxHp(d)),
                "<#8C96A3>Col : <#FFD66E>" + Text.num(d.col),
                "<#8C96A3>Étage : <white>" + d.floor,
                "<#8C96A3>Monstres vaincus : <white>" + Text.num(d.kills))), null);
        m.set(10, Items.icon(Material.TOTEM_OF_UNDYING, "<#5EE89A><b>Statut</b>",
                "<#8C96A3>Force, agilité, vitalité", d.points > 0 ? "<#FFD66E>" + d.points + " point(s) à répartir !" : "<#5A6270>Aucun point disponible"),
                t -> status(plugin, p));
        m.set(11, Items.icon(Material.ENCHANTED_BOOK, "<#7FB2FF><b>Compétences d'épée</b>",
                "<#8C96A3>Maîtrise : <white>" + d.proficiency(d.classId) + " / 1000", "<#8C96A3>Équiper jusqu'à 4 compétences"),
                t -> skills(plugin, p));
        m.set(12, Items.icon(Material.ENDER_EYE, "<#7FD4FF><b>Étages</b>",
                "<#8C96A3>Étages ouverts : <white>" + Math.min(plugin.floors().maxFloor(), plugin.floors().highestCleared() + 1)
                        + " / " + plugin.floors().maxFloor(), "<#5A6270>Téléportation depuis une ville"),
                t -> floors(plugin, p, false));
        m.set(13, Items.icon(Material.WHITE_BANNER, "<#FFB347><b>Groupe</b>", "<#8C96A3>/groupe inviter <joueur>"),
                t -> party(plugin, p));
        m.set(14, Items.icon(Material.EMERALD, "<#FFD66E><b>Marchand</b>", "<#8C96A3>Armes, armures, potions, cristaux",
                "<#5A6270>Uniquement en ville"), t -> shop(plugin, p));
        m.set(15, Items.icon(Material.BOOK, "<#D7DCE2><b>Comment jouer</b>", guide()), null);
        m.set(16, Items.icon(Material.BARRIER, "<#5A6270><st>Se déconnecter</st>", "<#5A6270>Ce bouton semble avoir disparu…"), t -> {
            p.closeInventory();
            Text.msg(p, "<#8C96A3>Le bouton de déconnexion est introuvable. Il va falloir atteindre le dernier étage…");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f);
        });
        frame(m, 3);
        m.open(p);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.8f);
    }

    private static ItemStack head(Player p, String name, List<String> lore) {
        ItemStack it = Items.icon(Material.PLAYER_HEAD, name, lore);
        SkullMeta meta = (SkullMeta) it.getItemMeta();
        meta.setOwningPlayer(p);
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------------ Statut

    public static void status(RebornMMO plugin, Player p) {
        PlayerData d = plugin.players().get(p);
        Menu m = new Menu(3, Text.mm("<#5EE89A><b>Statut</b></#5EE89A> <#8C96A3>· " + d.points + " point(s)"));
        m.set(4, head(p, "<white><b>" + Text.esc(p.getName()) + "</b> <#8C96A3>Lv." + d.level, List.of(
                "<#8C96A3>Attaque : <white>" + Math.round(plugin.combat().attackPower(p, d)),
                "<#8C96A3>Critique : <white>" + Math.round(plugin.combat().critChance(d) * 100) + " %",
                "<#8C96A3>PV max : <white>" + Text.num((long) Stats.maxHp(d)),
                "<#8C96A3>Réduction des dégâts : <white>" + Math.round(plugin.combat().armorReduction(p) * 100) + " %")), null);
        String hint = "<#5A6270>Clic : +1 · Maj + clic : +5";
        m.set(11, Items.icon(Material.IRON_SWORD, "<#FF8A7A><b>Force</b> <white>" + d.str,
                "<#8C96A3>+2,5 % de dégâts par point", hint), t -> spend(plugin, p, d, "str", t));
        m.set(13, Items.icon(Material.FEATHER, "<#7FD4FF><b>Agilité</b> <white>" + d.agi,
                "<#8C96A3>+0,4 % de critique, -0,6 % de délai", "<#8C96A3>et +0,3 % de vitesse par point", hint), t -> spend(plugin, p, d, "agi", t));
        m.set(15, Items.icon(Material.GOLDEN_APPLE, "<#5EE89A><b>Vitalité</b> <white>" + d.vit,
                "<#8C96A3>+15 PV max par point", hint), t -> spend(plugin, p, d, "vit", t));
        m.set(22, Items.icon(Material.ARROW, "<#8C96A3>Retour"), t -> main(plugin, p));
        frame(m, 3);
        m.open(p);
    }

    private static void spend(RebornMMO plugin, Player p, PlayerData d, String stat, ClickType type) {
        int n = Math.min(d.points, type.isShiftClick() ? 5 : 1);
        if (n <= 0) {
            Text.msg(p, "<#FF5C5C>Aucun point de statut disponible (3 par niveau).");
            return;
        }
        d.points -= n;
        switch (stat) {
            case "str" -> d.str += n;
            case "agi" -> d.agi += n;
            default -> {
                d.vit += n;
                d.hp += 15.0 * n;
            }
        }
        plugin.progression().applyAgility(p, d);
        plugin.combat().sync(p, d);
        click(p);
        status(plugin, p);
    }

    // ------------------------------------------------------------------ Compétences

    public static void skills(RebornMMO plugin, Player p) {
        PlayerData d = plugin.players().get(p);
        GameClass c = d.gameClass();
        if (c == null) {
            return;
        }
        Menu m = new Menu(5, Text.mm("<#7FB2FF><b>Compétences d'épée</b></#7FB2FF> <#8C96A3>· " + d.equipped.size() + "/4 équipées"));
        m.set(4, Items.icon(Material.EXPERIENCE_BOTTLE, "<" + c.hex + "><b>" + c.label + "</b>",
                "<#8C96A3>Maîtrise : <white>" + d.proficiency(c.id) + " / 1000",
                "<#5A6270>La maîtrise augmente en utilisant vos compétences",
                "<#5A6270>et en battant des monstres avec votre arme."), null);
        int slot = 10;
        for (Skill s : Skills.tree(c.id)) {
            m.set(slot, skillIcon(d, s), t -> toggle(plugin, p, d, s));
            slot = next(slot);
        }
        if ("epeiste".equals(c.id)) {
            slot = 28;
            if (d.dualBlade) {
                m.set(27, Items.glow(Items.icon(Material.NETHER_STAR, "<gradient:#7FD4FF:#FFFFFF><b>Double lame</b></gradient>",
                        "<#8C96A3>Compétence unique", "<#8C96A3>Maîtrise : <white>" + d.proficiency(Skills.DUAL) + " / 1000")), null);
                for (Skill s : Skills.tree(Skills.DUAL)) {
                    m.set(slot, skillIcon(d, s), t -> toggle(plugin, p, d, s));
                    slot++;
                }
            } else {
                m.set(31, Items.icon(Material.GRAY_DYE, "<#5A6270><b>Compétence unique : ???</b>",
                        "<#5A6270>Une seule personne au monde", "<#5A6270>peut l'éveiller…"), null);
            }
        }
        m.set(40, Items.icon(Material.ARROW, "<#8C96A3>Retour"), t -> main(plugin, p));
        frame(m, 5);
        m.open(p);
    }

    private static int next(int slot) {
        int n = slot + 1;
        return n % 9 == 8 ? n + 2 : n;
    }

    private static ItemStack skillIcon(PlayerData d, Skill s) {
        boolean unlocked = Skills.isUnlocked(d, s);
        int idx = d.equipped.indexOf(s.id);
        List<String> lore = new ArrayList<>();
        lore.add("<#D7DCE2>" + s.desc);
        lore.add("");
        lore.add("<#8C96A3>Coups : <white>" + s.hits + "   <#8C96A3>Puissance : <white>" + Math.round(s.mult * 100) + " %");
        lore.add("<#8C96A3>Recharge : <white>" + String.format(Locale.ROOT, "%.1f", s.cooldown / 20.0).replace('.', ',') + " s"
                + "   <#8C96A3>Délai : <white>" + String.format(Locale.ROOT, "%.1f", s.post / 20.0).replace('.', ',') + " s");
        if (s.shield) {
            lore.add("<#8C96A3>Exige un bouclier en main secondaire");
        }
        lore.add("");
        if (!unlocked) {
            lore.add("<#FF5C5C>Verrouillée : niveau " + s.level + ", maîtrise " + s.prof);
            return Items.icon(Material.GRAY_DYE, "<#5A6270><b>" + s.name + "</b>", lore);
        }
        lore.add(idx >= 0 ? "<#5EE89A>Équipée (emplacement " + (idx + 1) + ") · clic pour retirer" : "<#FFD66E>Clic pour équiper");
        ItemStack it = Items.icon(idx >= 0 ? Material.ENCHANTED_BOOK : Material.BOOK, "<" + s.hex + "><b>" + s.name + "</b>", lore);
        return idx >= 0 ? Items.glow(it) : it;
    }

    private static void toggle(RebornMMO plugin, Player p, PlayerData d, Skill s) {
        if (!Skills.isUnlocked(d, s)) {
            Text.msg(p, "<#FF5C5C>Compétence verrouillée : niveau " + s.level + " et maîtrise " + s.prof + " requis.");
            return;
        }
        if (d.equipped.remove(s.id)) {
            d.selected = 0;
        } else if (d.equipped.size() >= 4) {
            Text.msg(p, "<#FF5C5C>4 compétences au maximum. Retirez-en une d'abord.");
            return;
        } else {
            d.equipped.add(s.id);
        }
        click(p);
        skills(plugin, p);
    }

    // ------------------------------------------------------------------ Étages

    public static void floors(RebornMMO plugin, Player p, boolean fromGate) {
        PlayerData d = plugin.players().get(p);
        boolean canTravel = fromGate || plugin.floors().isSafe(p.getLocation());
        int open = Math.min(plugin.floors().maxFloor(), plugin.floors().highestCleared() + 1);
        int shown = Math.min(45, Math.min(plugin.floors().maxFloor(), open + 1));
        Menu m = new Menu(6, Text.mm("<#7FD4FF><b>Porte de téléportation</b></#7FD4FF> <#8C96A3>· " + open + "/" + plugin.floors().maxFloor()));
        for (int n = 1; n <= shown; n++) {
            int floor = n;
            FloorDef def = plugin.floors().def(n);
            if (plugin.floors().unlocked(n)) {
                List<String> lore = new ArrayList<>();
                lore.add("<#8C96A3>" + Text.esc(def.town()));
                lore.add("<#8C96A3>Monstres : <white>niveau " + def.minLevel() + " à " + def.maxLevel());
                lore.add(n <= plugin.floors().highestCleared() ? "<#5EE89A>Boss vaincu" : "<#FF8A3B>Boss invaincu");
                lore.add("");
                lore.add(n == d.floor ? "<#7FD4FF>Vous êtes ici" : canTravel ? "<#FFD66E>Clic pour vous téléporter" : "<#5A6270>Allez à une porte de téléportation");
                ItemStack it = Items.icon(def.theme().icon, "<#7FD4FF><b>Étage " + n + "</b> <#D7DCE2>" + Text.esc(def.name()), lore);
                m.set(n - 1, n == d.floor ? Items.glow(it) : it, t -> {
                    if (!canTravel) {
                        Text.msg(p, "<#FF5C5C>Utilisez la porte de téléportation d'une ville.");
                        return;
                    }
                    p.closeInventory();
                    if (floor != d.floor || !plugin.floors().isSafe(p.getLocation())) {
                        plugin.floors().teleport(p, floor, true);
                    }
                });
            } else {
                m.set(n - 1, Items.icon(Material.BARRIER, "<#5A6270><b>Étage " + n + "</b>",
                        "<#5A6270>Verrouillé : battez le boss de l'étage " + (n - 1)), null);
            }
        }
        m.set(49, Items.icon(Material.ARROW, "<#8C96A3>Menu"), t -> main(plugin, p));
        frame(m, 6);
        m.open(p);
        if (fromGate) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.8f, 1.6f);
        }
    }

    // ------------------------------------------------------------------ Groupe

    public static void party(RebornMMO plugin, Player p) {
        Party party = plugin.parties().party(p.getUniqueId());
        Menu m = new Menu(3, Text.mm("<#FFB347><b>Groupe</b></#FFB347> <#8C96A3>· " + (party == null ? 1 : party.members().size()) + "/" + plugin.parties().maxSize()));
        if (party == null) {
            m.set(13, Items.icon(Material.WHITE_BANNER, "<#8C96A3>Vous jouez seul",
                    "<#D7DCE2>/groupe inviter <joueur>", "<#5A6270>XP partagée, barres de vie des équipiers", "<#5A6270>et bonus SWITCH en enchaînant."), null);
        } else {
            int slot = 10;
            for (UUID id : party.members()) {
                Player mp = Bukkit.getPlayer(id);
                PlayerData md = mp == null ? null : plugin.players().loaded(id);
                String name = mp == null ? String.valueOf(Bukkit.getOfflinePlayer(id).getName()) : mp.getName();
                List<String> lore = new ArrayList<>();
                if (md != null && md.gameClass() != null) {
                    lore.add("<#8C96A3>" + md.gameClass().label + " · Lv." + md.level);
                    lore.add(Text.hpBar(md.hp / Stats.maxHp(md), 20));
                }
                if (id.equals(party.leader())) {
                    lore.add("<#FFD66E>Chef du groupe");
                }
                ItemStack it = mp == null ? Items.icon(Material.SKELETON_SKULL, "<#5A6270>" + Text.esc(name), lore)
                        : head(mp, "<white>" + Text.esc(name), lore);
                m.set(slot++, it, null);
            }
            m.set(22, Items.icon(Material.RED_DYE, "<#FF5C5C>Quitter le groupe"), t -> {
                plugin.parties().leave(p);
                p.closeInventory();
            });
        }
        m.set(18, Items.icon(Material.ARROW, "<#8C96A3>Menu"), t -> main(plugin, p));
        frame(m, 3);
        m.open(p);
    }

    // ------------------------------------------------------------------ Marchand

    public static void shop(RebornMMO plugin, Player p) {
        PlayerData d = plugin.players().get(p);
        if (d.classId == null) {
            return;
        }
        if (!plugin.floors().isSafe(p.getLocation())) {
            Text.msg(p, "<#FF5C5C>Le marchand se trouve en ville.");
            return;
        }
        GameClass c = d.gameClass();
        Menu m = new Menu(6, Text.mm("<#FFD66E><b>Marchand</b></#FFD66E> <#8C96A3>· " + Text.num(d.col) + " Col"));
        m.set(0, Items.icon(Material.IRON_SWORD, "<#8C96A3><b>Armes</b> <#5A6270>(" + c.weapon.label + ")"), null);
        for (int t = 0; t < 4; t++) {
            int tier = t;
            ItemStack it = Items.weapon(c.weapon, tier);
            m.set(1 + t, priced(it, Items.TIER_PRICE[t], Items.TIER_LEVEL[t], d), click -> buy(plugin, p, d,
                    Items.TIER_PRICE[tier], Items.TIER_LEVEL[tier], () -> Items.give(p, Items.weapon(c.weapon, tier))));
        }
        if (c.shield) {
            m.set(5, priced(Items.shield(), 60, 1, d), click -> buy(plugin, p, d, 60, 1, () -> Items.give(p, Items.shield())));
        }
        m.set(9, Items.icon(Material.IRON_CHESTPLATE, "<#8C96A3><b>Armures</b> <#5A6270>(équipées à l'achat)"), null);
        for (int t = 0; t < Items.ARMOR_NAMES.length; t++) {
            int tier = t;
            ItemStack[] set = Items.armorSet(tier, Fx.color(c.hex));
            ItemStack icon = Items.icon(set[1].getType(), "<#D7DCE2>" + Items.ARMOR_NAMES[t], "<#8C96A3>Ensemble complet (4 pièces)");
            m.set(10 + t, priced(icon, Items.ARMOR_PRICE[t], Items.ARMOR_LEVEL[t], d), click -> buy(plugin, p, d,
                    Items.ARMOR_PRICE[tier], Items.ARMOR_LEVEL[tier], () -> {
                        for (ItemStack old : new ItemStack[]{p.getInventory().getHelmet(), p.getInventory().getChestplate(),
                                p.getInventory().getLeggings(), p.getInventory().getBoots()}) {
                            if (old != null && !old.getType().isAir()) {
                                Items.give(p, old);
                            }
                        }
                        Items.equip(p, Items.armorSet(tier, Fx.color(c.hex)));
                    }));
        }
        m.set(18, Items.icon(Material.POTION, "<#8C96A3><b>Consommables</b>"), null);
        m.set(19, priced(Items.potion(1), Items.POTION_PRICE, 1, d), t -> buy(plugin, p, d, Items.POTION_PRICE, 1, () -> Items.give(p, Items.potion(1))));
        m.set(20, priced(Items.potion(5), Items.POTION_PRICE * 5, 1, d), t -> buy(plugin, p, d, Items.POTION_PRICE * 5, 1, () -> Items.give(p, Items.potion(5))));
        m.set(21, priced(Items.healCrystal(), Items.HEAL_CRYSTAL_PRICE, 1, d), t -> buy(plugin, p, d, Items.HEAL_CRYSTAL_PRICE, 1, () -> Items.give(p, Items.healCrystal())));
        m.set(22, priced(Items.tpCrystal(), Items.TP_CRYSTAL_PRICE, 1, d), t -> buy(plugin, p, d, Items.TP_CRYSTAL_PRICE, 1, () -> Items.give(p, Items.tpCrystal())));

        long value = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && "material".equals(Items.type(it))) {
                value += (long) Items.sellValue(it) * it.getAmount();
            }
        }
        m.set(40, Items.icon(Material.GOLD_INGOT, "<#FFD66E><b>Vendre tous les matériaux</b>", "<#8C96A3>Valeur : <#FFD66E>" + Text.num(value) + " Col"),
                t -> sellAll(plugin, p, d));
        m.set(45, Items.icon(Material.ARROW, "<#8C96A3>Menu"), t -> main(plugin, p));
        frame(m, 6);
        m.open(p);
    }

    private static ItemStack priced(ItemStack base, long price, int level, PlayerData d) {
        ItemStack it = base.clone();
        var meta = it.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Text.item(""));
        lore.add(Text.item((d.col >= price ? "<#FFD66E>" : "<#FF5C5C>") + "Prix : " + Text.num(price) + " Col"));
        if (level > 1) {
            lore.add(Text.item((d.level >= level ? "<#8C96A3>" : "<#FF5C5C>") + "Niveau requis : " + level));
        }
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private static void buy(RebornMMO plugin, Player p, PlayerData d, long price, int level, Runnable give) {
        if (!plugin.floors().isSafe(p.getLocation())) {
            p.closeInventory();
            return;
        }
        if (d.level < level) {
            Text.msg(p, "<#FF5C5C>Niveau " + level + " requis.");
            return;
        }
        if (d.col < price) {
            Text.msg(p, "<#FF5C5C>Pas assez de Col (" + Text.num(price - d.col) + " manquants).");
            return;
        }
        d.col -= price;
        give.run();
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1.2f);
        shop(plugin, p);
    }

    private static void sellAll(RebornMMO plugin, Player p, PlayerData d) {
        long total = 0;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && "material".equals(Items.type(it))) {
                total += (long) Items.sellValue(it) * it.getAmount();
                p.getInventory().setItem(i, null);
            }
        }
        if (total == 0) {
            Text.msg(p, "<#8C96A3>Aucun matériau à vendre.");
            return;
        }
        d.col += total;
        Text.msg(p, "Matériaux vendus : <#FFD66E>+" + Text.num(total) + " Col");
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        shop(plugin, p);
    }
}
