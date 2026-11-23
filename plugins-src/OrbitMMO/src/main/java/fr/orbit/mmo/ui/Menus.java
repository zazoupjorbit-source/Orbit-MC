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
import fr.orbit.mmo.party.PartyService.Party;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.player.Stats;
import fr.orbit.mmo.registry.Defs.FloorDef;
import fr.orbit.mmo.util.Fx;
import fr.orbit.mmo.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tous les menus : création du personnage, menu principal, sorts, spécialisation, talents, étages, groupe, marchand. */
public final class Menus {

    private static final ItemStack PANE = Items.icon(Material.BLACK_STAINED_GLASS_PANE, " ");

    private Menus() {
    }

    /** Les 6 premières lignes (contrôles) sont aussi envoyées à la création du personnage. */
    public static List<String> guide() {
        return List.of(
                "<#D7DCE2>Clic gauche : attaquer avec votre arme",
                "<#D7DCE2>3 objets de sort : <#FF8A3B>⚔ Attaque</#FF8A3B>, <#7FD4FF>➶ Déplacement</#7FD4FF>, <#5EE89A>✦ Buff</#5EE89A>",
                "<#D7DCE2>  tenez-en un puis <white>[Clic droit]</white> : le sort part, la main revient sur l'arme",
                "<#D7DCE2>  <white>[Accroupi + molette]</white> sur l'objet : changer de sort",
                "<#D7DCE2>Potions et cristaux : <white>[Clic droit]</white> · <white>[F]</white> ou /mmo : menu",
                "<#D7DCE2>Mon personnage (tête) : stats, sorts et dégâts · /lobby pour repartir",
                "",
                "<#D7DCE2>Talents : 1 point par niveau à partir du niveau 10.",
                "<#D7DCE2>Changer de spécialisation : en ville, depuis /mmo.",
                "<#D7DCE2>En ville : hôtel des ventes (entre joueurs), forgeron",
                "<#D7DCE2>(armes, armures, améliorer, réparer, rachat du butin), alchimiste",
                "<#D7DCE2>(potions, cristaux) et prêtre (parchemins, essences).",
                "<#D7DCE2>Les joueurs de la faction adverse peuvent vous attaquer",
                "<#D7DCE2>hors des zones sûres.",
                "<#D7DCE2>Guilde (/guilde) : une faction, des étages du Pilier,",
                "<#D7DCE2>un classement, des primes et une banque en commun.");
    }

    /** Vitres dans les cases vides, posées à l'ouverture et seulement si le menu n'est pas habillé. */
    static void frame(Menu m, int rows) {
        m.framed = true;
    }

    static void fillPanes(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, PANE);
            }
        }
    }

    static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
    }

    private static String fmt(double v) {
        return TalentDef.format(v);
    }

    // ------------------------------------------------------------------ Création du personnage

    /** Étape 1 : la faction. */
    public static void factionSelect(OrbitMMO plugin, Player p) {
        Menu m = new Menu(3, Text.mm("<#FFD66E><b>Choisissez votre camp</b>")).skin("Votre camp", "Lumière ou Ombre", null);
        m.forceOpen = true;
        int[] slots = {11, 15};
        Faction[] fs = Faction.values();
        for (int i = 0; i < fs.length; i++) {
            Faction f = fs[i];
            List<String> lore = new ArrayList<>();
            for (String l : f.lore) {
                lore.add("<#D7DCE2>" + l);
            }
            lore.add("");
            lore.add("<#8C96A3>Capitale : <white>" + f.capital);
            lore.add("<#8C96A3>Le mage, le prêtre et le paladin prennent");
            lore.add("<#8C96A3>la forme de votre camp.");
            lore.add("");
            lore.add("<#FF8A3B>⚠ Le camp ne pourra plus être changé.");
            lore.add("<#5EE89A>▶ Cliquez pour rejoindre " + f.label);
            m.set(slots[i], Items.icon(f.icon, "<" + f.hex + "><b>" + f.label + "</b>", lore), t -> classSelect(plugin, p, f));
        }
        m.set(22, Items.icon(Material.BOOK, "<#FFD66E><b>Comment jouer</b>", guide()), null);
        frame(m, 3);
        m.open(p);
    }

    /** Étape 2 : la classe (la race est « Humain » en attendant les races). */
    public static void classSelect(OrbitMMO plugin, Player p, Faction f) {
        Menu m = new Menu(4, Text.mm("<" + f.hex + "><b>" + f.label + "</b></" + f.hex + "> <#8C96A3>· choisissez votre classe"))
                .skin("Votre classe", f.label, null);
        m.forceOpen = true;
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 22};
        int i = 0;
        for (ClassDef c : plugin.classes().all()) {
            if (i >= slots.length) {
                break;
            }
            List<String> lore = new ArrayList<>();
            lore.add("<#8C96A3>Ressource : <" + c.resource.hex + ">" + c.resource.label);
            lore.add("<#8C96A3>Armure : <white>" + c.armor.label + "   <#8C96A3>Arme : <white>" + c.weapon.label + (c.shield ? " + bouclier" : ""));
            lore.add("");
            for (String line : c.lore) {
                lore.add("<#D7DCE2>" + line);
            }
            lore.add("");
            lore.add("<#8C96A3>Spécialisations :");
            for (SpecDef s : c.specsFor(f)) {
                lore.add("  " + s.tag() + " " + s.role.tag());
            }
            lore.add("");
            ItemStack icon;
            if (c.playable) {
                lore.add("<#5EE89A>▶ Cliquez pour choisir");
                icon = Items.icon(c.icon, "<" + c.hex + "><b>" + c.name(f) + "</b>", lore);
                m.set(slots[i], icon, t -> specCreate(plugin, p, f, c));
            } else {
                lore.add("<#FF8A3B>Bientôt disponible");
                icon = Items.icon(Material.GRAY_DYE, "<#8C96A3><b>" + c.name(f) + "</b> <#5A6270>(bientôt)", lore);
                m.set(slots[i], icon, null);
            }
            i++;
        }
        m.set(27, Items.icon(Material.ARROW, "<#8C96A3>Changer de camp"), t -> factionSelect(plugin, p));
        m.set(31, Items.icon(Material.BOOK, "<#FFD66E><b>Comment jouer</b>", guide()), null);
        frame(m, 4);
        m.open(p);
    }

    /** Lignes d'une spécialisation (rôle, description, bonus, sorts propres). */
    private static List<String> specLore(ClassDef c, SpecDef s) {
        List<String> lore = new ArrayList<>();
        lore.add(s.role.tag());
        lore.add("");
        for (String l : s.lore) {
            lore.add("<#D7DCE2>" + l);
        }
        WeaponType weapon = s.weapon != null ? s.weapon : c.weapon;
        boolean shield = s.weapon != null ? s.shield : c.shield || s.shield;
        lore.add("<#8C96A3>Arme : <white>" + weapon.label + (shield ? " + bouclier" : ""));
        if (!s.passives.isEmpty()) {
            lore.add("");
            lore.add("<#8C96A3>Bonus permanents :");
            for (Map.Entry<String, Double> e : s.passives.entrySet()) {
                lore.add("  <#D7DCE2>" + modLabel(e.getKey(), e.getValue()));
            }
        }
        List<String> own = new ArrayList<>();
        for (SpellDef sp : c.spells.values()) {
            if (sp.specs.contains(s.id) && !sp.talentOnly) {
                own.add(sp.category.symbol + " " + sp.name + " <#5A6270>(niv. " + sp.level + ")");
            }
        }
        if (!own.isEmpty()) {
            lore.add("");
            lore.add("<#8C96A3>Sorts propres :");
            for (String l : own) {
                lore.add("  <#D7DCE2>" + l);
            }
        }
        return lore;
    }

    /** Étape 3 : la spécialisation (changeable ensuite en ville). */
    private static void specCreate(OrbitMMO plugin, Player p, Faction f, ClassDef c) {
        Menu m = new Menu(3, Text.mm("<" + c.hex + "><b>" + c.name(f) + "</b></" + c.hex + "> <#8C96A3>· choisissez votre spécialisation"))
                .skin("Spécialisation", c.name(f), null);
        m.forceOpen = true;
        List<SpecDef> specs = c.specsFor(f);
        int[] slots = specs.size() <= 2 ? new int[]{11, 15} : new int[]{10, 13, 16};
        for (int i = 0; i < specs.size() && i < slots.length; i++) {
            SpecDef s = specs.get(i);
            List<String> lore = specLore(c, s);
            lore.add("");
            lore.add("<#5A6270>Changeable plus tard, en ville.");
            lore.add("<#5EE89A>▶ Cliquez pour commencer en " + s.name);
            m.set(slots[i], Items.icon(s.icon, "<" + s.hex + "><b>" + s.name + "</b>", lore), t -> create(plugin, p, f, c, s));
        }
        m.set(18, Items.icon(Material.ARROW, "<#8C96A3>Changer de classe"), t -> classSelect(plugin, p, f));
        m.set(22, Items.icon(Material.BOOK, "<#FFD66E><b>Comment jouer</b>", guide()), null);
        frame(m, 3);
        m.open(p);
    }

    private static void create(OrbitMMO plugin, Player p, Faction f, ClassDef c, SpecDef spec) {
        PlayerData d = plugin.players().get(p);
        if (plugin.progression().valid(d)) {
            return;
        }
        d.faction = f;
        d.race = "humain";
        d.classId = c.id;
        d.specId = spec.id;
        d.level = 1;
        d.xp = 0;
        d.talents.clear();
        d.cooldowns.clear();
        d.picks.clear();
        d.weaponSlot = 0;
        d.gold += plugin.getConfig().getLong("start.gold", 100);
        p.getInventory().clear();
        plugin.progression().recompute(p, d);
        d.hp = Stats.maxHp(d);
        Resource r = plugin.progression().resource(d);
        d.resource = r.startsFull ? r.max : 0;
        WeaponType weapon = spec.weapon != null ? spec.weapon : c.weapon;
        boolean shield = spec.weapon != null ? spec.shield : c.shield || spec.shield;
        p.getInventory().setItem(0, Items.weapon(weapon, 0));
        if (shield) {
            p.getInventory().setItemInOffHand(Items.shield());
        }
        Items.equip(p, Items.armorSet(0, Fx.color(c.hex)));
        p.closeInventory();
        plugin.progression().learnNew(p, d, java.util.Set.of("*"));
        Items.give(p, Items.potion(5));
        Items.give(p, plugin.inns().crystal(p, d));
        plugin.playerListener().prepareBody(p, d);
        Fx.teleport(p.getLocation());
        // Départ : là où commence la quête d'introduction de sa faction s'il y en a une (elle mène ensuite à la capitale),
        // sinon directement la capitale de sa faction.
        org.bukkit.Location start = plugin.quests() == null || d.buildMode ? null : plugin.quests().introStart(p, d);
        if (start == null && plugin.continent() != null) {
            start = plugin.continent().home(f.id);
        }
        if (start != null) {
            d.capitalHome = true;
            org.bukkit.Location to = start;
            p.teleportAsync(to).thenAccept(ok -> {
                if (ok && p.isOnline()) {
                    Fx.teleport(to);
                }
            });
        }
        Text.title(p, "<" + c.hex + "><b>" + c.name(f) + "</b> <" + spec.hex + ">" + spec.name, f.tag() + " <#D7DCE2>· votre aventure commence…", 10, 60, 20);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        Text.msg(p, "<#D7DCE2>Vous êtes <b>" + c.tag(f) + "</b> " + spec.tag() + " de la faction " + f.tag() + ". Contrôles :");
        for (String line : guide().subList(0, 6)) {
            p.sendMessage(Text.mm("  " + line));
        }
        plugin.players().save(d);
    }

    // ------------------------------------------------------------------ Menu principal

    public static void main(OrbitMMO plugin, Player p) {
        PlayerData d = plugin.players().get(p);
        if (!plugin.progression().valid(d)) {
            factionSelect(plugin, p);
            return;
        }
        ClassDef c = plugin.progression().cls(d);
        SpecDef spec = plugin.progression().spec(d);
        if (spec == null) {
            // Personnage créé avant le choix de spé à la création : on la choisit d'abord.
            specSelect(plugin, p);
            return;
        }
        Menu m = new Menu(3, Text.mm("<#7FB2FF><b>Menu</b></#7FB2FF> <#8C96A3>· " + Text.esc(p.getName())))
                .skin(MenuSkin.Screen.MAIN, "Menu");
        m.set(4, head(p, "<white><b>" + Text.esc(p.getName()) + "</b>", List.of(
                "<#8C96A3>Faction : " + d.faction.tag(),
                "<#8C96A3>Classe : " + c.tag(d.faction) + (spec == null ? "" : " <#5A6270>· " + spec.tag()),
                "<#8C96A3>Niveau : <white>" + d.level + " <#5A6270>(" + Text.num(d.xp) + " / " + Text.num(Stats.xpToNext(d.level)) + " XP)",
                "<#8C96A3>PV : <white>" + Text.num((long) Math.ceil(d.hp)) + " / " + Text.num((long) Stats.maxHp(d)),
                "<#8C96A3>Or : <#FFD66E>" + Text.num(d.gold),
                "<#8C96A3>Monstres vaincus : <white>" + Text.num(d.kills),
                "",
                "<#5EE89A>Clic : fiche complète (stats, sorts, dégâts)")), t -> ProfileMenu.open(plugin, p, p));
        m.set(26, Items.icon(Material.NETHER_STAR, "<#FFD66E><b>Retour au lobby</b>", "<#8C96A3>Quitter Orbit MMO pour le lobby.",
                "<#5A6270>Aussi : /lobby"), t -> {
            p.closeInventory();
            plugin.toLobby(p);
        });
        m.set(10, Items.icon(Material.ENCHANTED_BOOK, "<#7FB2FF><b>Livre de sorts</b>",
                "<#8C96A3>" + plugin.progression().known(d).size() + " sort(s) connu(s)", "<#8C96A3>Choisir le sort de chaque objet"),
                t -> spellbook(plugin, p));
        m.set(11, Items.icon(spec.icon, "<#FFD66E><b>Spécialisation</b>", "<#8C96A3>Actuelle : " + spec.tag(),
                "<#5A6270>Changement en ville"), t -> specSelect(plugin, p));
        fr.orbit.mmo.guild.Guild guild = plugin.guilds().of(p.getUniqueId());
        int free = plugin.progression().talentPointsFree(d);
        m.set(12, Items.icon(Material.EXPERIENCE_BOTTLE, "<#5EE89A><b>Talents</b>",
                d.level < plugin.progression().specLevel() ? "<#5A6270>À partir du niveau " + plugin.progression().specLevel()
                        : free > 0 ? "<#FFD66E>" + free + " point(s) à dépenser !" : "<#8C96A3>Arbre de " + spec.name),
                t -> talents(plugin, p, null));
        m.set(13, Items.icon(Material.ENDER_EYE, "<#7FD4FF><b>Le Pilier des Astres</b>",
                "<#8C96A3>Donjon de " + plugin.floors().maxFloor() + " étages, à gravir en groupe.",
                "<#8C96A3>Étages ouverts : <white>" + Math.min(plugin.floors().maxFloor(), plugin.floors().cleared(d) + 1)
                        + " / " + plugin.floors().maxFloor() + (guild == null ? " <#5A6270>(sans guilde)" : " <#5A6270>(avec votre guilde)"),
                "<#5A6270>Entrée : le gardien du Pilier, devant", "<#5A6270>le donjon de votre capitale"),
                t -> floors(plugin, p, false));
        m.set(14, Items.icon(Material.WHITE_BANNER, "<#FFB347><b>Groupe</b>", "<#8C96A3>/groupe inviter <joueur>"),
                t -> party(plugin, p));
        m.set(15, Items.icon(Material.EMERALD, "<#FFD66E><b>Hôtel des ventes</b>", "<#8C96A3>Achat et vente entre joueurs,",
                "<#8C96A3>(le butin se revend au forgeron)", "<#5A6270>Uniquement en ville"), t -> MarketMenus.open(plugin, p));
        List<String> crystalLore = new java.util.ArrayList<>();
        crystalLore.add("<#8C96A3>Utilise votre cristal de téléportation.");
        crystalLore.addAll(plugin.inns().status(d));
        crystalLore.add("<#5A6270>Un aubergiste le lie à son auberge.");
        m.set(16, Items.icon(Material.PRISMARINE_CRYSTALS, "<#7FD4FF><b>Retour à l'auberge</b>", crystalLore), t -> {
            p.closeInventory();
            useTpCrystal(plugin, p, d);
        });
        m.set(22, Items.icon(Material.BOOK, "<#D7DCE2><b>Comment jouer</b>", guide()), null);
        // Quêtes : journal, quête suivie et traînée de guidage.
        if (plugin.quests() != null) {
            fr.orbit.mmo.quest.QuestProgress qp = plugin.quests().progress(p);
            m.set(21, Items.icon(Material.WRITABLE_BOOK, "<#FFD66E><b>Quêtes</b>",
                    "<#8C96A3>En cours : <white>" + qp.active.size() + " <#5A6270>· terminées : " + qp.done.size(),
                    "<#8C96A3>Journal, objectifs et quête suivie.",
                    "<#8C96A3>Perdu ? La <#FFD66E>traînée de guidage</#FFD66E> trace",
                    "<#8C96A3>au sol le chemin vers votre objectif.",
                    "<#5A6270>Aussi : /quetes"), t -> plugin.quests().openJournal(p));
            // Langue des dialogues et des quêtes (français / anglais).
            m.set(24, plugin.quests().langIcon(p), t -> {
                plugin.quests().cycleLang(p);
                main(plugin, p);
            });
        }
        // Guilde : sous le bouton du groupe.
        String ghex = fr.orbit.mmo.guild.GuildService.HEX;
        Faction gf = guild == null ? d.faction : guild.faction();
        m.set(23, Items.icon(gf == Faction.OMBRE ? Material.PURPLE_BANNER : Material.YELLOW_BANNER, "<" + ghex + "><b>Guilde</b>",
                guild == null ? List.of("<#8C96A3>Vous n'avez pas de guilde.",
                        "<#8C96A3>En fonder une : <#FFD66E>" + Text.num(plugin.guilds().createCost()) + " or",
                        "<#5A6270>Étages du Pilier, classement,", "<#5A6270>primes et banque en commun.")
                        : List.of("<#8C96A3>" + Text.esc(guild.name()) + " <#5A6270>· " + guild.rank(p.getUniqueId()).label,
                        "<#8C96A3>Membres : <white>" + guild.size() + "/" + plugin.guilds().maxMembers()
                                + " <#5A6270>(" + plugin.guilds().online(guild) + " en ligne)",
                        "<#8C96A3>Étage franchi : <white>" + (guild.towerCleared() > 0 ? guild.towerCleared() : "aucun")
                                + " <#5A6270>· " + fr.orbit.mmo.guild.GuildService.ordinal(plugin.guilds().place(guild)) + " au classement",
                        "<#8C96A3>Banque : <#FFD66E>" + Text.num(guild.bank()) + " or")),
                t -> GuildMenus.open(plugin, p));
        // Montures (session montures, 23/11) : l'écurie.
        if (plugin.mounts() != null) {
            m.set(25, plugin.mounts().menuIcon(d, true), t -> plugin.mounts().openStable(p));
        }
        frame(m, 3);
        m.open(p);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.8f);
    }

    private static void useTpCrystal(OrbitMMO plugin, Player p, PlayerData d) {
        ItemStack[] contents = p.getInventory().getContents();
        for (ItemStack it : contents) {
            if ("tp_crystal".equals(Items.type(it))) {
                plugin.inns().use(p, d, it);
                return;
            }
        }
        Text.msg(p, "<#FF5C5C>Vous n'avez plus votre cristal de téléportation : un aubergiste vous le rend.");
    }

    static ItemStack head(Player p, String name, List<String> lore) {
        ItemStack it = Items.icon(Material.PLAYER_HEAD, name, lore);
        SkullMeta meta = (SkullMeta) it.getItemMeta();
        meta.setOwningPlayer(p);
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------------ Livre de sorts

    public static void spellbook(OrbitMMO plugin, Player p) {
        PlayerData d = plugin.players().get(p);
        ClassDef c = plugin.progression().cls(d);
        if (c == null) {
            return;
        }
        Menu m = new Menu(6, Text.mm("<#7FB2FF><b>Livre de sorts</b></#7FB2FF> <#8C96A3>· " + c.name(d.faction)))
                .skin("Livre de sorts", c.name(d.faction), null);
        // Une rangée par objet de sort (rangées 2 à 4) : l'objet à gauche, ses sorts à droite.
        SpellDef.Category[] cats = SpellDef.Category.values();
        for (int row = 0; row < cats.length; row++) {
            SpellDef.Category cat = cats[row];
            int base = (row + 1) * 9;
            List<SpellDef> list = new ArrayList<>();
            for (SpellDef s : c.spells.values()) {
                if (s.category != cat || (s.faction != null && s.faction != d.faction)
                        || (!s.specs.isEmpty() && (d.specId == null || !s.specs.contains(d.specId)))) {
                    continue;
                }
                list.add(s);
            }
            SpellDef cur = plugin.bar().picked(d, cat);
            m.set(base, Items.icon(cat == SpellDef.Category.ATTAQUE ? Material.IRON_SWORD
                            : cat == SpellDef.Category.DEPLACEMENT ? Material.FEATHER : Material.GOAT_HORN,
                    "<" + cat.hex + "><b>" + cat.symbol + " " + cat.label + "</b>",
                    "<#8C96A3>Objet de sort de la barre.",
                    cur == null ? "<#5A6270>Aucun sort de ce type pour l'instant." : "<#8C96A3>Sort choisi : " + cur.tag(),
                    "<#5A6270>Accroupi + molette en le tenant pour changer."), null);
            int col = 1;
            for (SpellDef s : list) {
                if (col > 8) {
                    break;
                }
                boolean known = plugin.progression().knows(d, s);
                List<String> lore = new ArrayList<>(plugin.bar().describe(s, d));
                lore.add("");
                if (!known) {
                    lore.add(s.talentOnly ? "<#FF8A3B>S'obtient par un talent" : "<#FF5C5C>Appris au niveau " + s.level);
                    m.set(base + col++, Items.icon(Material.GRAY_DYE, "<#5A6270><b>" + s.name + "</b>", lore), null);
                    continue;
                }
                boolean chosen = cur != null && cur.id.equals(s.id);
                lore.add(chosen ? "<#5EE89A>✔ Sort choisi dans l'objet " + cat.label : "<#FFD66E>Clic : le mettre dans l'objet " + cat.label);
                ItemStack it = Items.icon(s.icon, "<" + s.hex + "><b>" + s.name + "</b>", lore);
                m.set(base + col++, chosen ? Items.glow(it) : it, t -> {
                    plugin.bar().pick(p, d, s);
                    click(p);
                    spellbook(plugin, p);
                });
            }
        }
        m.set(45, Items.icon(Material.ARROW, "<#8C96A3>Menu"), t -> main(plugin, p));
        m.set(49, Items.icon(Material.BOOK, "<#D7DCE2><b>Lancer un sort</b>",
                "<#D7DCE2>Vos sorts sont rangés dans 3 objets :",
                "<#FF8A3B>⚔ Attaque <#7FD4FF>➶ Déplacement <#5EE89A>✦ Buff",
                "<#D7DCE2>Tenez l'objet puis <white>[Clic droit]</white> : le sort part",
                "<#D7DCE2>et la main revient seule sur l'arme.",
                "<#D7DCE2><white>[Accroupi + molette]</white> sur l'objet : changer de sort.",
                "",
                "<#5A6270>La recharge du sort choisi s'affiche sur l'objet."), null);
        m.set(53, Items.icon(Material.CHEST, "<#5EE89A><b>Redonner les objets</b>", "<#8C96A3>Remet dans l'inventaire les objets de sort",
                "<#8C96A3>qui manquent (case libre nécessaire)."), t -> {
            plugin.bar().sync(p, d);
            click(p);
            spellbook(plugin, p);
        });
        frame(m, 6);
        m.open(p);
    }

    // ------------------------------------------------------------------ Spécialisation

    public static void specSelect(OrbitMMO plugin, Player p) {
        PlayerData d = plugin.players().get(p);
        ClassDef c = plugin.progression().cls(d);
        if (c == null) {
            return;
        }
        // Pas encore de spé (personnage d'avant le choix à la création) : choix obligatoire, partout.
        boolean first = plugin.progression().spec(d) == null;
        Menu m = new Menu(3, Text.mm("<#FFD66E><b>Spécialisation</b></#FFD66E> <#8C96A3>· " + c.name(d.faction)))
                .skin("Spécialisation", c.name(d.faction), null);
        m.forceOpen = first;
        List<SpecDef> specs = c.specsFor(d.faction);
        int[] slots = specs.size() <= 2 ? new int[]{11, 15} : new int[]{10, 13, 16};
        for (int i = 0; i < specs.size() && i < slots.length; i++) {
            SpecDef s = specs.get(i);
            boolean current = s.id.equals(d.specId);
            List<String> lore = specLore(c, s);
            lore.add("");
            lore.add("<#8C96A3>Talents : <white>" + s.talents.size() + " <#5A6270>(" + d.spent(s.id) + " point(s) placé(s))");
            lore.add("");
            if (current) {
                lore.add("<#5EE89A>✔ Spécialisation active");
            } else {
                lore.add("<#5EE89A>▶ Clic : " + (first ? "choisir " : "passer en ") + s.name + (first ? "" : " <#5A6270>(en ville)"));
                lore.add("<#5A6270>Maj + clic : voir l'arbre de talents");
            }
            ItemStack it = Items.icon(s.icon, "<" + s.hex + "><b>" + s.name + "</b>", lore);
            m.set(slots[i], current ? Items.glow(it) : it, t -> {
                if (t.isShiftClick() || current) {
                    talents(plugin, p, s.id);
                    return;
                }
                if (!first && System.currentTimeMillis() - d.lastCombat < 5000) {
                    Text.msg(p, "<#FF5C5C>Impossible en plein combat.");
                    return;
                }
                if (!first && !plugin.floors().isSafe(p.getLocation())) {
                    Text.msg(p, "<#FF5C5C>Le changement de spécialisation se fait en ville.");
                    return;
                }
                p.closeInventory();
                plugin.progression().chooseSpec(p, d, s);
            });
        }
        if (!first) {
            m.set(18, Items.icon(Material.ARROW, "<#8C96A3>Menu"), t -> main(plugin, p));
        }
        frame(m, 3);
        m.open(p);
    }

    /** Libellé lisible d'un bonus (clé de Mods). */
    public static String modLabel(String key, double v) {
        String n = (v > 0 ? "+" : "") + fmt(v);
        return switch (key) {
            case "pv" -> n + " % de points de vie";
            case "degats" -> n + " % de dégâts";
            case "soins" -> n + " % de soins";
            case "crit" -> n + " % de chances de coup critique";
            case "critdeg" -> n + " % de dégâts critiques";
            case "armure" -> n + " % de réduction des dégâts subis";
            case "esquive" -> n + " % d'esquive";
            case "blocage" -> n + " % de chances de bloquer";
            case "vitesse" -> n + " % de vitesse";
            case "vol-de-vie" -> n + " % de vol de vie";
            case "ressource" -> n + " % de ressource générée";
            default -> key + " " + n;
        };
    }

    // ------------------------------------------------------------------ Talents

    /** Arbre de talents (celui de la spé active si specId est null), page des paliers 1 à 6. */
    public static void talents(OrbitMMO plugin, Player p, String specId) {
        talents(plugin, p, specId, 0);
    }

    /** Page 0 : paliers 1 à 6 ; page 1 : paliers avancés 7 à 9. */
    public static void talents(OrbitMMO plugin, Player p, String specId, int page) {
        PlayerData d = plugin.players().get(p);
        SpecDef spec = plugin.classes().spec(specId != null ? specId : d.specId);
        if (spec == null || !spec.classId.equals(d.classId)) {
            specSelect(plugin, p);
            return;
        }
        boolean active = spec.id.equals(d.specId);
        int spent = d.spent(spec.id);
        int free = active ? plugin.progression().talentPointsFree(d) : 0;
        int pages = (TalentDef.TIERS + TalentDef.TIERS_PER_PAGE - 1) / TalentDef.TIERS_PER_PAGE;
        int pg = Math.max(0, Math.min(pages - 1, page));
        int firstTier = pg * TalentDef.TIERS_PER_PAGE;
        int lastTier = Math.min(TalentDef.TIERS, firstTier + TalentDef.TIERS_PER_PAGE) - 1;
        Menu m = new Menu(6, Text.mm("<" + spec.hex + "><b>" + spec.name + "</b></" + spec.hex + "> <#8C96A3>· "
                + (active ? free + " point(s) libre(s)" : "aperçu") + (pages > 1 ? " <#5A6270>· paliers " + (firstTier + 1) + "-" + (lastTier + 1) : "")))
                .skin(spec.name, "Talents" + (pages > 1 ? " · paliers " + (firstTier + 1) + "-" + (lastTier + 1) : ""),
                        active ? free + (free > 1 ? " points libres" : " point libre") : "Aperçu");
        for (TalentDef t : spec.talents) {
            if (t.tier < firstTier || t.tier > lastTier) {
                continue;
            }
            int slot = (t.tier - firstTier) * 9 + 2 + t.column * 2;
            m.set(slot, talentIcon(plugin, d, spec, t, active), click -> {
                if (!active) {
                    return;
                }
                if (plugin.progression().invest(p, d, spec, t)) {
                    talents(plugin, p, spec.id, pg);
                }
            });
        }
        for (int tier = firstTier; tier <= lastTier; tier++) {
            int need = TalentDef.required(tier);
            boolean ok = spent >= need;
            m.set((tier - firstTier) * 9 + 1, Items.icon(ok ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                    (ok ? "<#5EE89A>" : "<#FF5C5C>") + "Palier " + (tier + 1), "<#8C96A3>" + need + " point(s) dépensé(s) requis"), null);
        }
        if (pg == 0 && pages > 1) {
            int need = TalentDef.required(TalentDef.TIERS_PER_PAGE);
            boolean ok = spent >= need;
            m.set(45, Items.icon(ok ? Material.NETHER_STAR : Material.FIREWORK_STAR, "<#FFD66E><b>Paliers avancés ▶</b>",
                    "<#8C96A3>Paliers " + (TalentDef.TIERS_PER_PAGE + 1) + " à " + TalentDef.TIERS + " : les talents de fin de",
                    "<#8C96A3>progression (" + TalentDef.required(TalentDef.TIERS_PER_PAGE) + ", " + TalentDef.required(TalentDef.TIERS_PER_PAGE + 1)
                            + " et " + TalentDef.required(TalentDef.TIERS - 1) + " points dans l'arbre).",
                    ok ? "<#5EE89A>Ouverts" : "<#5A6270>" + spent + " / " + need + " points pour ouvrir le palier " + (TalentDef.TIERS_PER_PAGE + 1),
                    "", "<#5A6270>[Clic] voir"), t -> talents(plugin, p, spec.id, 1));
        } else if (pg > 0) {
            m.set(45, Items.icon(Material.SPECTRAL_ARROW, "<#D7DCE2>◀ Paliers 1 à " + TalentDef.TIERS_PER_PAGE),
                    t -> talents(plugin, p, spec.id, pg - 1));
            int maxPoints = Math.max(0, plugin.progression().maxLevel() - plugin.progression().specLevel() + 1);
            m.set(40, Items.icon(Material.OAK_HANGING_SIGN, "<#FFD66E><b>Paliers avancés</b>",
                    "<#D7DCE2>Les talents de fin de progression.",
                    "<#8C96A3>Au niveau " + plugin.progression().maxLevel() + " : <white>" + maxPoints + " points</white>,",
                    "<#8C96A3>l'arbre en compte <white>" + spec.totalRanks() + "</white> : impossible",
                    "<#8C96A3>de tout prendre, à vous de choisir."), null);
        }
        m.set(0, Items.icon(spec.icon, "<" + spec.hex + "><b>" + spec.name + "</b>", List.of(
                spec.role.tag(),
                "<#8C96A3>Points placés : <white>" + spent + " / " + spec.totalRanks(),
                active ? "<#8C96A3>Points libres : <#FFD66E>" + free : "<#5A6270>Spécialisation inactive (aperçu)",
                "<#5A6270>1 point par niveau à partir du niveau " + plugin.progression().specLevel())), null);
        if (active) {
            long price = plugin.progression().resetPrice(d);
            m.set(9, Items.icon(Material.WATER_BUCKET, "<#7FD4FF><b>Réinitialiser</b>",
                    "<#8C96A3>Rend tous les points de cet arbre.", "<#8C96A3>Prix : " + (price == 0 ? "<#5EE89A>gratuit" : "<#FFD66E>" + Text.num(price) + " or"),
                    "<#5A6270>Maj + clic pour confirmer"), t -> {
                if (!t.isShiftClick()) {
                    Text.msg(p, "<#8C96A3>Maj + clic pour confirmer la réinitialisation.");
                    return;
                }
                if (d.gold < price) {
                    Text.msg(p, "<#FF5C5C>Pas assez d'or.");
                    return;
                }
                d.gold -= price;
                plugin.progression().resetTalents(p, d);
                p.playSound(p.getLocation(), Sound.ITEM_BUCKET_EMPTY, 0.8f, 1.2f);
                talents(plugin, p, spec.id);
            });
        }
        // Autre spécialisation de la classe (aperçu).
        ClassDef c = plugin.progression().cls(d);
        int row = 1;
        for (SpecDef other : c.specsFor(d.faction)) {
            if (other.id.equals(spec.id) || row > 4) {
                continue;
            }
            m.set(row * 9 + 8, Items.icon(other.icon, "<#8C96A3>Voir l'arbre " + other.tag()), t -> talents(plugin, p, other.id, pg));
            row++;
        }
        m.set(53, Items.icon(Material.ARROW, "<#8C96A3>Menu"), t -> main(plugin, p));
        frame(m, 6);
        m.open(p);
    }

    private static ItemStack talentIcon(OrbitMMO plugin, PlayerData d, SpecDef spec, TalentDef t, boolean active) {
        int rank = d.rank(spec.id, t.id);
        List<String> lore = new ArrayList<>();
        lore.add("<#8C96A3>Rang <white>" + rank + " / " + t.maxRank);
        lore.add("");
        lore.add("<#D7DCE2>" + t.describe(Math.max(1, rank)));
        if (t.spell != null) {
            SpellDef s = plugin.classes().spell(t.spell);
            if (s != null) {
                lore.add("<#8C96A3>Sort : " + s.tag() + " <#5A6270>— " + s.desc);
            }
        }
        if (rank > 0 && rank < t.maxRank) {
            lore.add("");
            lore.add("<#8C96A3>Rang suivant :");
            lore.add("<#D7DCE2>" + t.describe(rank + 1));
        }
        if (t.requires != null) {
            TalentDef req = spec.talent(t.requires);
            if (req != null) {
                boolean ok = d.rank(spec.id, req.id) >= req.maxRank;
                lore.add((ok ? "<#5EE89A>" : "<#FF5C5C>") + "Requiert : " + req.name + " " + req.maxRank + "/" + req.maxRank);
            }
        }
        lore.add("");
        String why = active ? plugin.progression().cannotInvest(d, spec, t) : "Aperçu";
        if (rank >= t.maxRank) {
            lore.add("<#FFD66E>✔ Maîtrisé");
        } else if (why == null) {
            lore.add("<#5EE89A>▶ Clic : +1 rang");
        } else {
            lore.add("<#5A6270>" + why);
        }
        String color = rank >= t.maxRank ? "#FFD66E" : rank > 0 ? "#5EE89A" : why == null ? "#FFFFFF" : "#5A6270";
        boolean locked = rank == 0 && d.spent(spec.id) < t.requiredPoints();
        ItemStack it = Items.icon(locked ? Material.GRAY_DYE : t.icon, "<" + color + "><b>" + t.name + "</b> <#8C96A3>" + rank + "/" + t.maxRank, lore);
        if (rank > 0) {
            it.setAmount(Math.min(64, rank));
        }
        return rank >= t.maxRank ? Items.glow(it) : it;
    }

    // ------------------------------------------------------------------ Étages

    public static void floors(OrbitMMO plugin, Player p, boolean fromGate) {
        PlayerData d = plugin.players().get(p);
        if (!plugin.progression().valid(d)) {
            factionSelect(plugin, p);
            return;
        }
        // Le Pilier des Astres est un donjon : on y entre par son gardien (dans sa capitale) et on change d'étage depuis une de ses villes.
        int here = plugin.floors().floorOf(p.getWorld());
        boolean canTravel = fromGate || (here > 0 && plugin.floors().isSafe(p.getLocation()));
        // Étages ouverts à ce joueur : les siens, ceux de sa guilde, ou le socle commun.
        int cleared = plugin.floors().cleared(d);
        fr.orbit.mmo.guild.Guild guild = plugin.guilds().of(p.getUniqueId());
        int open = Math.min(plugin.floors().maxFloor(), cleared + 1);
        int shown = Math.min(45, Math.min(plugin.floors().maxFloor(), open + 1));
        Menu m = new Menu(6, Text.mm("<#7FD4FF><b>Le Pilier des Astres</b></#7FD4FF> <#8C96A3>· " + open + "/" + plugin.floors().maxFloor()))
                .skin("Le Pilier des Astres", "Étages ouverts : " + open + "/" + plugin.floors().maxFloor(), null);
        for (int n = 1; n <= shown; n++) {
            int floor = n;
            FloorDef def = plugin.floors().def(n);
            if (n <= open) {
                List<String> lore = new ArrayList<>();
                lore.add("<#8C96A3>" + Text.esc(def.town()));
                lore.add("<#8C96A3>Monstres : <white>niveau " + def.minLevel() + " à " + def.maxLevel());
                lore.add(n <= cleared ? "<#5EE89A>Boss vaincu" : "<#FF8A3B>Boss invaincu");
                fr.orbit.mmo.guild.GuildService.First first = plugin.guilds().first(n);
                if (first != null) {
                    lore.add("<#5A6270>Premier passage : <" + fr.orbit.mmo.guild.GuildService.HEX + ">" + Text.esc(first.guildName()));
                } else {
                    lore.add("<#5A6270>Premier passage : <#FFD66E>à prendre <#5A6270>(prime de " + Text.num(plugin.guilds().prime(n)) + " or)");
                }
                lore.add("");
                lore.add(n == here ? "<#7FD4FF>Vous êtes ici" : canTravel ? "<#FFD66E>Clic pour vous téléporter"
                        : here > 0 ? "<#5A6270>Allez à la porte d'une ville du Pilier" : "<#5A6270>Allez voir le gardien du Pilier");
                ItemStack it = Items.icon(def.theme().icon, "<#7FD4FF><b>Étage " + n + "</b> <#D7DCE2>" + Text.esc(def.name()), lore);
                m.set(n - 1, n == here ? Items.glow(it) : it, t -> {
                    if (!canTravel) {
                        Text.msg(p, here > 0 ? "<#FF5C5C>Utilisez la porte de téléportation d'une ville du Pilier."
                                : "<#FF5C5C>On entre dans le Pilier des Astres par son gardien, devant le donjon de votre capitale.");
                        return;
                    }
                    p.closeInventory();
                    if (floor != plugin.floors().floorOf(p.getWorld()) || !plugin.floors().isSafe(p.getLocation())) {
                        plugin.floors().teleport(p, floor, true);
                    }
                });
            } else {
                m.set(n - 1, Items.icon(Material.BARRIER, "<#5A6270><b>Étage " + n + "</b>",
                        "<#5A6270>Verrouillé : battez le boss de l'étage " + (n - 1),
                        guild == null ? "<#5A6270>(vous-même, ou la guilde que vous rejoindrez)" : "<#5A6270>(vous ou votre guilde)"), null);
            }
        }
        m.set(49, Items.icon(Material.ARROW, "<#8C96A3>Menu"), t -> main(plugin, p));
        m.set(45, Items.icon(Material.GOLDEN_HELMET, "<#FFD66E><b>Guildes dans le Pilier</b>",
                guild == null ? "<#8C96A3>Sans guilde : vous gravissez pour vous seul."
                        : "<#8C96A3>" + Text.esc(guild.name()) + " : étage franchi <white>" + (guild.towerCleared() > 0 ? guild.towerCleared() : "aucun"),
                "<#8C96A3>Une guilde ouvre ses étages à tous ses membres.",
                "<#8C96A3>La première à franchir un étage touche une prime.",
                "", "<#5EE89A>▶ Clic : classement des guildes"), t -> GuildMenus.ranking(plugin, p));
        if (here > 0 && plugin.continent() != null && plugin.continent().capital(d.faction.id) != null) {
            String capital = plugin.continent().capital(d.faction.id).name();
            m.set(53, Items.icon(Material.OAK_DOOR, "<#FFD66E><b>Quitter le Pilier</b>", "<#8C96A3>Retour à " + Text.esc(capital) + ", votre capitale.",
                    canTravel ? "<#FFD66E>Clic pour sortir" : "<#5A6270>Depuis la porte d'une ville du Pilier"), t -> {
                if (!canTravel) {
                    Text.msg(p, "<#FF5C5C>On sort du Pilier par la porte de téléportation d'une de ses villes.");
                    return;
                }
                p.closeInventory();
                plugin.continent().sendHome(p, d.faction.id, true);
            });
        }
        frame(m, 6);
        m.open(p);
        if (fromGate) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.8f, 1.6f);
        }
    }

    // ------------------------------------------------------------------ Groupe

    public static void party(OrbitMMO plugin, Player p) {
        Party party = plugin.parties().party(p.getUniqueId());
        Menu m = new Menu(3, Text.mm("<#FFB347><b>Groupe</b></#FFB347> <#8C96A3>· " + (party == null ? 1 : party.members().size()) + "/" + plugin.parties().maxSize()))
                .skin("Groupe", "Membres : " + (party == null ? 1 : party.members().size()) + "/" + plugin.parties().maxSize(), null);
        if (party == null) {
            m.set(13, Items.icon(Material.WHITE_BANNER, "<#8C96A3>Vous jouez seul",
                    "<#D7DCE2>/groupe inviter <joueur>", "<#5A6270>XP partagée, barres de vie des équipiers", "<#5A6270>et cris de guerre partagés."), null);
        } else {
            int slot = 10;
            for (UUID id : party.members()) {
                Player mp = Bukkit.getPlayer(id);
                PlayerData md = mp == null ? null : plugin.players().loaded(id);
                String name = mp == null ? String.valueOf(Bukkit.getOfflinePlayer(id).getName()) : mp.getName();
                List<String> lore = new ArrayList<>();
                if (md != null && plugin.progression().valid(md)) {
                    SpecDef ms = plugin.progression().spec(md);
                    lore.add("<#8C96A3>" + plugin.progression().cls(md).name(md.faction) + (ms == null ? "" : " · " + ms.name + " " + ms.role.glyph)
                            + " · Niv." + md.level);
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

    /** Vérifie que le joueur peut commercer (personnage valide, en ville). */
    static PlayerData trader(OrbitMMO plugin, Player p) {
        PlayerData d = plugin.players().get(p);
        if (!plugin.progression().valid(d)) {
            return null;
        }
        if (!plugin.floors().isSafe(p.getLocation())) {
            p.closeInventory();
            Text.msg(p, "<#FF5C5C>Le commerce se fait en ville.");
            return null;
        }
        return d;
    }

    /** Or du joueur pour le bandeau des menus habillés. */
    static String gold(PlayerData d) {
        return "◆ " + Text.num(d.gold) + " or";
    }

    static net.kyori.adventure.text.Component shopTitle(String hex, String name, PlayerData d) {
        return Text.mm("<" + hex + "><b>" + name + "</b></" + hex + "> <#5A6270>· <#FFD66E>" + Text.num(d.gold) + " or");
    }

    /** Armurerie de Torvald (forgeron) : voir ArmoryMenus (toutes les armes, armures et boucliers, 8 paliers). */
    public static void armory(OrbitMMO plugin, Player p) {
        ArmoryMenus.open(plugin, p);
    }

    /** Torvald rachète le butin : revente immédiate au PNJ (clic sur un objet de son inventaire = la pile). */
    static void buyback(OrbitMMO plugin, Player p) {
        PlayerData d = trader(plugin, p);
        if (d == null) {
            return;
        }
        long materials = 0;
        long gear = 0;
        int boss = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            String kind = Items.type(it);
            if (kind == null) {
                continue;
            }
            long v = Items.unitSellPrice(it) * it.getAmount();
            switch (kind) {
                case "material" -> materials += v;
                case "weapon", "armor" -> gear += v;
                case "lab" -> boss++;
                default -> {
                }
            }
        }
        Menu m = new Menu(3, shopTitle("#FF8A3B", "Torvald rachète votre butin", d)).skin("Rachat du butin", "Torvald", gold(d));
        m.set(4, Items.icon(Material.OAK_SIGN, "<#FFD66E><b>Comment vendre ?</b>",
                "<#D7DCE2>Cliquez un objet dans <white>votre inventaire</white>",
                "<#D7DCE2>(en bas) : la pile entière est vendue.",
                "",
                "<#8C96A3>Matériaux : prix indiqué sur l'objet",
                "<#8C96A3>Équipement : 1/5 du prix, plus selon la rareté",
                "<#8C96A3>  (et la valeur des runes serties)",
                "<#8C96A3>Runes : selon leur rang",
                "<#8C96A3>Potions et cristaux : 1/2 du prix",
                "<#5A6270>Sorts et « Mon personnage » : invendables"), null);
        m.set(11, Items.icon(Material.GOLD_NUGGET, "<#FFD66E><b>Vendre tous les matériaux</b>",
                "<#8C96A3>Valeur : <#FFD66E>" + Text.num(materials) + " or"), t -> sellAll(plugin, p, d, false));
        m.set(15, Items.icon(Material.GOLD_INGOT, "<#FFD66E><b>Vendre matériaux + équipement en trop</b>",
                "<#8C96A3>Valeur : <#FFD66E>" + Text.num(materials + gear) + " or",
                "<#8C96A3>Garde l'arme en main, l'armure portée,",
                "<#8C96A3>les objets Rares ou mieux, ceux qui portent",
                "<#8C96A3>des runes et le butin de boss" + (boss > 0 ? " (" + boss + ")" : "")), t -> sellAll(plugin, p, d, true));
        m.set(18, Items.icon(Material.ARROW, "<#8C96A3>Retour à la forge"), t -> TownMenus.forge(plugin, p));
        m.set(22, Items.icon(Material.EMERALD, "<#FFD66E><b>Mieux payé ?</b>",
                "<#8C96A3>Proposez vos plus belles pièces",
                "<#8C96A3>aux joueurs à l'hôtel des ventes (Corvin)."), null);
        m.onBottomClick((slot, type) -> sellSlot(plugin, p, d, slot));
        frame(m, 3);
        m.open(p);
    }

    private static void sellSlot(OrbitMMO plugin, Player p, PlayerData d, int slot) {
        if (trader(plugin, p) == null) {
            return;
        }
        ItemStack it = p.getInventory().getItem(slot);
        long unit = Items.unitSellPrice(it);
        if (it == null || unit <= 0) {
            Text.msg(p, "<#8C96A3>Torvald n'achète pas cet objet : proposez-le aux joueurs à l'hôtel des ventes.");
            return;
        }
        if ("weapon".equals(Items.type(it)) && countType(p, "weapon") <= 1) {
            Text.msg(p, "<#FF5C5C>Gardez au moins une arme.");
            return;
        }
        long total = unit * it.getAmount();
        p.getInventory().setItem(slot, null);
        d.gold += total;
        p.sendMessage(Text.mm(Text.PREFIX + "Vendu : " + it.getAmount() + "× ").append(it.effectiveName()).append(Text.mm(" <#FFD66E>+" + Text.num(total) + " or")));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        buyback(plugin, p);
    }

    private static int countType(Player p, String kind) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (kind.equals(Items.type(it))) {
                n++;
            }
        }
        return n;
    }

    static ItemStack priced(ItemStack base, long price, int level, PlayerData d) {
        ItemStack it = base.clone();
        var meta = it.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Text.item(""));
        lore.add(Text.item(price <= 0 ? "<#7CFF8A>Gratuit" : (d.gold >= price ? "<#FFD66E>" : "<#FF5C5C>") + "Prix : " + Text.num(price) + " or"));
        if (level > 1) {
            lore.add(Text.item((d.level >= level ? "<#8C96A3>" : "<#FF5C5C>") + "Niveau requis : " + level));
        }
        lore.add(Text.item("<#5A6270>[Clic] acheter"));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    static void buy(OrbitMMO plugin, Player p, PlayerData d, long price, int level, Runnable give, Runnable reopen) {
        if (trader(plugin, p) == null) {
            return;
        }
        if (d.level < level) {
            Text.msg(p, "<#FF5C5C>Niveau " + level + " requis.");
            return;
        }
        if (d.gold < price) {
            Text.msg(p, "<#FF5C5C>Pas assez d'or (" + Text.num(price - d.gold) + " manquants).");
            return;
        }
        d.gold -= price;
        give.run();
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1.2f);
        reopen.run();
    }

    /** Vend les matériaux ; avec withGear, aussi les armes et armures du sac (pas l'arme tenue ni le butin de boss). */
    private static void sellAll(OrbitMMO plugin, Player p, PlayerData d, boolean withGear) {
        if (trader(plugin, p) == null) {
            return;
        }
        long total = 0;
        int held = p.getInventory().getHeldItemSlot();
        int keepWeapon = "weapon".equals(Items.type(p.getInventory().getItem(held))) ? held : -1;
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            String kind = Items.type(it);
            if (kind == null) {
                continue;
            }
            boolean sell = "material".equals(kind) || (withGear && ("armor".equals(kind) || "weapon".equals(kind)));
            if (!sell) {
                continue;
            }
            // Les objets Rares ou mieux, et ceux qui portent des runes, ne partent jamais en lot.
            if (!"material".equals(kind) && (fr.orbit.mmo.item.Loot.rarity(it).atLeast(fr.orbit.mmo.item.Rarity.RARE)
                    || fr.orbit.mmo.item.Loot.runes(it).stream().anyMatch(java.util.Objects::nonNull))) {
                continue;
            }
            if ("weapon".equals(kind)) {
                if (keepWeapon < 0) {
                    keepWeapon = i; // aucune arme en main : on garde la première trouvée
                    continue;
                }
                if (i == keepWeapon) {
                    continue;
                }
            }
            total += Items.unitSellPrice(it) * it.getAmount();
            p.getInventory().setItem(i, null);
        }
        if (total == 0) {
            Text.msg(p, "<#8C96A3>Rien à vendre.");
            return;
        }
        d.gold += total;
        Text.msg(p, "Butin vendu : <#FFD66E>+" + Text.num(total) + " or");
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        buyback(plugin, p);
    }
}
