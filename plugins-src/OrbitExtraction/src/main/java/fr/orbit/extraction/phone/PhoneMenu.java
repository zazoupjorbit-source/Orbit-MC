package fr.orbit.extraction.phone;

import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.gun.GunItems;
import fr.orbit.extraction.phone.PhoneService.Article;
import fr.orbit.extraction.phone.PhoneService.Offer;
import fr.orbit.extraction.raid.Profile;
import fr.orbit.extraction.rank.Kits;
import fr.orbit.extraction.rank.Kits.Kit;
import fr.orbit.extraction.rank.Ranks.Rank;
import fr.orbit.extraction.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Écrans de la tablette (coffre 6 lignes). Avec le pack : le fond est une image (tools/PhoneGuiGen.java) affichée par
 * des glyphes de la police {@code orbitguns:gui} dans le titre, et les zones cliquables sont des objets invisibles.
 * Sans le pack : même disposition, en vitres.
 *
 * Disposition : contenu sur les lignes 0 à 3 (cases 0 à 35), actions et pagination sur la ligne 4 (36 à 44),
 * barre d'onglets sur la ligne 5 (45 à 52) et crédits en case 53.
 */
public final class PhoneMenu implements Listener {

    /** L'ordre fixe les points de code de la police : même ordre que dans PhoneGuiGen.main. */
    public enum Screen {
        HOME("Tablette"), SHOP("Tablette — boutique"), COLIS("Tablette — colis"), MESSAGES("Tablette — messages"),
        KITS("Tablette — kits"), HDV("Tablette — hôtel des ventes"), PROFIL("Tablette — fiche de l'opérateur");

        private final String plain;

        Screen(String plain) {
            this.plain = plain;
        }

        int base() {
            return 0xE200 + 0x10 * ordinal();
        }
    }

    private static final class Holder implements InventoryHolder {
        final Screen screen;
        final int page;
        final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
        Inventory inv;

        Holder(Screen screen, int page) {
            this.screen = screen;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final int SIZE = 54;
    private static final int PAGE = 36;
    private static final Key FONT = Key.key("orbitguns", "gui");
    private static final NamespacedKey INVISIBLE = new NamespacedKey("orbitguns", "invisible");
    private static final char START = '', NEXT_TILE = '', NEXT_ROW = '';

    private final OrbitExtraction plugin;
    private final PhoneService phone;
    /** Joueurs qui ne regardent que leurs propres annonces à l'hôtel des ventes. */
    private final java.util.Set<java.util.UUID> mineOnly = new java.util.HashSet<>();

    public PhoneMenu(OrbitExtraction plugin, PhoneService phone) {
        this.plugin = plugin;
        this.phone = phone;
    }

    // ------------------------------------------------------------------ briques

    private static Component title(Screen s, boolean textured) {
        if (!textured) {
            return Text.mm("<#8E2A20>☣</#8E2A20> <#2B2F36>" + s.plain);
        }
        StringBuilder b = new StringBuilder().append(START);
        for (int row = 0; row < 4; row++) {
            b.appendCodePoint(s.base() + row * 2).append(NEXT_TILE).appendCodePoint(s.base() + row * 2 + 1).append(NEXT_ROW);
        }
        return Component.text(b.toString()).font(FONT).color(NamedTextColor.WHITE);
    }

    /** Zone cliquable : invisible par-dessus la texture, ou vitre colorée sans le pack. */
    private static ItemStack zone(boolean textured, Material glass, String name, List<String> lore) {
        ItemStack it = new ItemStack(textured ? Material.PAPER : glass);
        it.editMeta(m -> {
            m.displayName(Text.item(name));
            m.lore(Text.lore(lore));
            m.addItemFlags(ItemFlag.values());
            if (textured) {
                m.setItemModel(INVISIBLE);
            }
        });
        return it;
    }

    private static ItemStack icon(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        it.editMeta(m -> {
            m.displayName(Text.item(name));
            m.lore(Text.lore(lore));
            m.addItemFlags(ItemFlag.values());
        });
        return it;
    }

    private static ItemStack withLore(ItemStack base, List<String> extra) {
        ItemStack it = base.clone();
        it.editMeta(m -> {
            List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
            lore.addAll(Text.lore(extra));
            m.lore(lore);
        });
        return it;
    }

    private Holder create(Player p, Screen s, int page) {
        boolean textured = plugin.pack().isLoaded(p.getUniqueId());
        Holder h = new Holder(s, page);
        h.inv = Bukkit.createInventory(h, SIZE, title(s, textured));
        if (!textured) {
            ItemStack bg = icon(Material.BLACK_STAINED_GLASS_PANE, "<#000000> ", List.of());
            for (int i = 0; i < SIZE; i++) {
                h.inv.setItem(i, bg);
            }
        }
        return h;
    }

    private void show(Player p, Holder h) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                p.openInventory(h.inv);
            }
        });
    }

    private void set(Holder h, int slot, ItemStack it, Consumer<InventoryClickEvent> action) {
        h.inv.setItem(slot, it);
        if (action != null) {
            h.actions.put(slot, action);
        }
    }

    private ItemStack creditsItem(Player p) {
        return icon(Material.SUNFLOWER, "<#F5C542><b>" + phone.credits(p) + " crédits</b>", List.of(
                "<#8A919B>Gagnés en jeu : infectés abattus, joueurs",
                "<#8A919B>abattus, extractions réussies, butin revendu."));
    }

    /** Ligne 4 : pagination et actions. Ligne 5 : onglets (45 à 52) et crédits (53). */
    private static final int ACTIONS = 36, TABS = 45, CREDITS = 53;

    /** Barre d'onglets de la tablette (même ordre que PhoneGuiGen.TABS) et solde de crédits. */
    private void header(Player p, Holder h, boolean textured) {
        tab(p, h, textured, 0, Screen.HOME, Material.COMPASS, "<#E8695C><b>Accueil</b>", "Raid, applications, carte.");
        tab(p, h, textured, 1, Screen.SHOP, Material.SUNFLOWER, "<#F5C542><b>Boutique</b>", "Armes, munitions, vivres.");
        tab(p, h, textured, 2, Screen.COLIS, Material.BARREL, "<#3FC46A><b>Colis</b>", "Un largage près de toi, en plein raid.");
        tab(p, h, textured, 3, Screen.MESSAGES, Material.WRITABLE_BOOK, "<#6EA2FF><b>Messages</b>", "Écris aux joueurs connectés.");
        tab(p, h, textured, 4, Screen.KITS, Material.LEATHER_CHESTPLATE, "<#FF9A3D><b>Kits</b>", "Rangs et kits.");
        tab(p, h, textured, 5, Screen.HDV, Material.GOLD_INGOT, "<#E05CB8><b>Hôtel des ventes</b>", "Achète et vends entre joueurs.");
        tab(p, h, textured, 6, Screen.PROFIL, Material.PLAYER_HEAD, "<#22C7D9><b>Fiche de l'opérateur</b>", "État, équipement, bilan. <#3A3F47>(Maj + F)");
        boolean raid = plugin.raid().inRaid(p);
        ItemStack cache = zone(textured, Material.CHEST, "<#A48CFF><b>Cache</b>", List.of(
                "<#8A919B>Le coffre de ta planque (54 cases).", raid ? "<#C0392B>Inaccessible en raid." : "<#5A6270>Clic : ouvrir"));
        set(h, TABS + 7, cache, e -> openStash(p));
        set(h, CREDITS, creditsItem(p), null);
    }

    private void tab(Player p, Holder h, boolean textured, int index, Screen target, Material fallback, String name, String desc) {
        boolean here = h.screen == target;
        ItemStack it = zone(textured, fallback, name, List.of("<#8A919B>" + desc, "",
                here ? "<#E8695C>▶ Onglet ouvert" : "<#5A6270>Clic : ouvrir"));
        if (here && !textured) {
            it.editMeta(m -> m.setEnchantmentGlintOverride(true));
        }
        set(h, TABS + index, it, e -> open(p, target));
    }

    private void openStash(Player p) {
        if (plugin.raid().inRaid(p)) {
            deny(p, "<#C0392B>Ton coffre est à la planque.</#C0392B> Extrais-toi d'abord.");
            return;
        }
        p.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> plugin.stash().open(p));
    }

    private static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    private static void deny(Player p, String mini) {
        Text.msg(p, mini);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
    }

    public void open(Player p, Screen s) {
        open(p, s, 0);
    }

    public void open(Player p, Screen s, int page) {
        switch (s) {
            case HOME -> home(p);
            case SHOP -> shop(p, page);
            case COLIS -> colis(p);
            case MESSAGES -> messages(p);
            case KITS -> kits(p);
            case HDV -> market(p, page);
            case PROFIL -> profile(p);
        }
    }

    // ------------------------------------------------------------------ accueil

    private void home(Player p) {
        Holder h = create(p, Screen.HOME, 0);
        boolean textured = plugin.pack().isLoaded(p.getUniqueId());
        boolean raid = plugin.raid().inRaid(p);
        Profile pr = plugin.profiles().get(p);
        header(p, h, textured);

        long left = raid ? Math.max(0, (pr.raidEnds - System.currentTimeMillis()) / 1000) : 0;
        card(h, textured, 0, Material.RED_STAINED_GLASS_PANE, "<#E23B3B><b>Raid</b>", raid
                ? List.of("<#E67E22>Raid en cours</#E67E22> <#8A919B>— " + Text.clock(left) + " restantes",
                "<#8A919B>Extractions : <#2ECC71>" + String.join("<#5A6270>, </#5A6270>", pr.extracts), "",
                "<#5A6270>Clic : distances et directions")
                : List.of("<#8A919B>Largage au hasard sur l'île.", "<#8A919B>Extrais-toi pour garder ton butin ;",
                "<#8A919B>meurs, et tout reste sur place.", "", "<#5A6270>Clic : partir en raid"), e -> {
            p.closeInventory();
            if (plugin.raid().inRaid(p)) {
                plugin.raid().info(p);
            } else {
                plugin.raid().deploy(p);
            }
        });
        card(h, textured, 1, Material.YELLOW_STAINED_GLASS_PANE, "<#F5C542><b>Boutique</b>", List.of(
                "<#8A919B>Armes, munitions, vivres.", raid ? "<#C0392B>Livraison à la planque uniquement." : "<#8A919B>Livraison immédiate à la planque.",
                "", "<#5A6270>Clic : ouvrir"), e -> open(p, Screen.SHOP));
        card(h, textured, 2, Material.LIME_STAINED_GLASS_PANE, "<#3FC46A><b>Colis</b>", List.of(
                "<#8A919B>Un largage près de toi, en plein raid.", "<#8A919B>Attention : le bruit attire les infectés",
                "<#8A919B>et n'importe qui peut le piller.", "", "<#5A6270>Clic : ouvrir"), e -> open(p, Screen.COLIS));
        int unread = pr.unread();
        card(h, textured, 3, Material.BLUE_STAINED_GLASS_PANE, "<#6EA2FF><b>Messages</b>", List.of(
                unread > 0 ? "<#6EA2FF>" + unread + " non lu" + (unread > 1 ? "s" : "") : "<#8A919B>Aucun nouveau message.",
                "<#8A919B>Écris aux joueurs connectés.", "", "<#5A6270>Clic : ouvrir"), e -> open(p, Screen.MESSAGES));
        card(h, textured, 4, Material.PURPLE_STAINED_GLASS_PANE, "<#A48CFF><b>Coffre</b>", List.of(
                "<#8A919B>Le coffre de ta planque (54 cases).", raid ? "<#C0392B>Inaccessible en raid." : "<#8A919B>Ce que tu y ranges est à l'abri.",
                "", "<#5A6270>Clic : ouvrir"), e -> openStash(p));
        card(h, textured, 5, Material.CYAN_STAINED_GLASS_PANE, "<#22C7D9><b>Stats</b>", List.of(
                "<#8A919B>Raids : <#D7DCE2>" + pr.raids + "</#D7DCE2>  •  Extraits : <#2ECC71>" + pr.survived,
                "<#8A919B>Morts : <#C0392B>" + pr.deaths,
                "<#8A919B>Joueurs abattus : <#D7DCE2>" + pr.pvpKills,
                "<#8A919B>Infectés abattus : <#D7DCE2>" + pr.infectedKills, "",
                "<#5A6270>Clic : fiche de l'opérateur <#3A3F47>(Maj + F)"), e -> open(p, Screen.PROFIL));

        // Ligne 4 : trois boutons larges (3 cases chacun) : kits, hôtel des ventes, carte.
        Rank rank = plugin.ranks().of(p);
        int ready = 0;
        for (Kit k : plugin.kits().all()) {
            if (plugin.kits().unlocked(p, k) && plugin.kits().remaining(p, k) == 0) {
                ready++;
            }
        }
        ItemStack kitsBtn = zone(textured, Material.ORANGE_STAINED_GLASS_PANE, "<#FF9A3D><b>Kits</b>", List.of(
                "<#8A919B>Ton rang : " + rank.tag(),
                ready > 0 ? "<#2ECC71>" + ready + " kit" + (ready > 1 ? "s" : "") + " disponible" + (ready > 1 ? "s" : "") : "<#8A919B>Aucun kit disponible pour l'instant.",
                "", "<#5A6270>Clic : ouvrir"));
        ItemStack mapBtn = zone(textured, Material.LIGHT_BLUE_STAINED_GLASS_PANE, "<#5CC8FF><b>Carte</b>", List.of(
                "<#8A919B>Carte tactique en main gauche :",
                "<#8A919B>ta position, tes extractions, les caisses.",
                pr.minimap ? "<#2ECC71>Affichée" : "<#C0392B>Rangée", "",
                "<#5A6270>Clic : ranger / ressortir", "<#5A6270>Maj + clic : changer de zoom"));
        Market market = plugin.market();
        int mine = market.count(p.getUniqueId()), back = market.waiting(p.getUniqueId());
        ItemStack hdvBtn = zone(textured, Material.MAGENTA_STAINED_GLASS_PANE, "<#E05CB8><b>Hôtel des ventes</b>", List.of(
                "<#8A919B>Achète et vends entre joueurs.",
                "<#8A919B>Annonces en cours : <#D7DCE2>" + market.list(null).size() + "</#D7DCE2>  •  les tiennes : <#D7DCE2>" + mine,
                back > 0 ? "<#E67E22>" + back + " objet" + (back > 1 ? "s" : "") + " à récupérer" : "<#5A6270>Taxe de " + market.taxPercent() + " % sur chaque vente.",
                "", "<#5A6270>Clic : ouvrir"));
        for (int c = 0; c < 3; c++) {
            set(h, ACTIONS + c, kitsBtn, e -> open(p, Screen.KITS));
            set(h, ACTIONS + 3 + c, hdvBtn, e -> open(p, Screen.HDV));
            set(h, ACTIONS + 6 + c, mapBtn, e -> {
                if (e.isShiftClick()) {
                    plugin.miniMap().cycleZoom(p);
                } else {
                    p.closeInventory();
                    plugin.miniMap().toggle(p);
                }
            });
        }
        show(p, h);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.6f);
    }

    /** Carte d'application : 3 colonnes x 2 lignes (lignes 0-1 ou 2-3), toutes cliquables. */
    private void card(Holder h, boolean textured, int index, Material glass, String name, List<String> lore,
                      Consumer<InventoryClickEvent> action) {
        int c0 = (index % 3) * 3, r0 = (index / 3) * 2;
        ItemStack it = zone(textured, glass, name, lore);
        for (int r = r0; r < r0 + 2; r++) {
            for (int c = c0; c < c0 + 3; c++) {
                set(h, r * 9 + c, it, action);
            }
        }
    }

    // ------------------------------------------------------------------ fiche de l'opérateur (Maj + F)

    /**
     * Fiche de l'opérateur, comme la fiche « Mon personnage » d'Orbit MMO : quatre bandeaux de huit cases, chacun
     * précédé de son icône (dessinée dans la texture) : état, équipement porté, bilan, planque et compte.
     */
    private void profile(Player p) {
        Holder h = create(p, Screen.PROFIL, 0);
        boolean textured = plugin.pack().isLoaded(p.getUniqueId());
        boolean raid = plugin.raid().inRaid(p);
        Profile pr = plugin.profiles().get(p);
        Rank rank = plugin.ranks().of(p);
        header(p, h, textured);

        // --- Bandeau 1 : état
        set(h, 0, zone(textured, Material.RED_STAINED_GLASS_PANE, "<#E8695C><b>État</b>",
                List.of("<#8A919B>Ce que tu portes sur toi, maintenant.")), null);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, m -> {
            m.setOwningPlayer(p);
            m.displayName(Text.item("<#E8695C><b>" + Text.esc(p.getName()) + "</b>"));
            m.lore(Text.lore(List.of(
                    "<#8A919B>Rang : " + rank.tag(),
                    "<#8A919B>Statut : " + (raid ? "<#E67E22>en raid" : "<#2ECC71>à la planque"),
                    "<#8A919B>Crédits : <#F5C542>" + phone.credits(p))));
        });
        set(h, 1, head, null);

        double max = maxHealth(p);
        long hp = Math.round(Math.max(0, p.getHealth()) * 100 / Math.max(1, max));
        String hpCol = hp > 50 ? "#2ECC71" : hp > 25 ? "#F1C40F" : "#E74C3C";
        set(h, 2, icon(Material.GLISTERING_MELON_SLICE, "<#FF5C5C><b>Vie</b>", List.of(
                "<#8A919B>Vie : <" + hpCol + ">" + hp + "</" + hpCol + "> <#5A6270>/ 100",
                "<#5A6270>" + fmt(p.getHealth() / 2) + " coeurs sur " + fmt(max / 2), "",
                "<#8A919B>Pas de régénération en raid :",
                "<#8A919B>bandages et trousses de soin (clic droit).")), null);

        org.bukkit.attribute.AttributeInstance armorAttr = p.getAttribute(org.bukkit.attribute.Attribute.ARMOR);
        org.bukkit.attribute.AttributeInstance toughAttr = p.getAttribute(org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS);
        set(h, 3, icon(Material.SHIELD, "<#D7DCE2><b>Protection</b>", List.of(
                "<#8A919B>Armure : <white>" + fmt(armorAttr == null ? 0 : armorAttr.getValue()),
                "<#8A919B>Robustesse : <white>" + fmt(toughAttr == null ? 0 : toughAttr.getValue()), "",
                "<#5A6270>Le chiffre de l'écrou, à gauche de la console.")), null);

        int food = p.getFoodLevel();
        set(h, 4, icon(Material.COOKED_BEEF, "<#E6A23C><b>Vivres</b>", List.of(
                "<#8A919B>Faim : " + (food <= 6 ? "<#C0392B>" : "<white>") + food + " <#5A6270>/ 20",
                food <= 6 ? "<#C0392B>Affamé : tu ne peux plus courir." : "<#5A6270>La petite barre sous la vie.")), null);

        fr.orbit.extraction.gun.GunService.GunStatus g = plugin.gunService().status(p);
        set(h, 5, g == null
                ? icon(Material.IRON_HORSE_ARMOR, "<#D9C27A><b>Arme en main</b>", List.of("<#5A6270>Aucune arme à feu en main."))
                : withLore(p.getInventory().getItemInMainHand(), List.of("", "<#D9C27A>Chargeur : <white>" + g.mag() + " <#5A6270>/ " + g.magMax(),
                "<#D9C27A>Réserve : <white>" + g.reserve() + " <#5A6270>(" + g.ammo() + ")")), null);

        int meds = plugin.meds().count(p);
        set(h, 6, icon(Material.PAPER, "<#2ECC71><b>Soins</b>", List.of(
                "<#8A919B>Dans le sac : <white>" + meds,
                "<#5A6270>Clic droit avec l'objet, sans bouger", "<#5A6270>d'objet ni prendre de coup.")), null);

        long left = raid ? Math.max(0, (pr.raidEnds - System.currentTimeMillis()) / 1000) : 0;
        set(h, 7, icon(raid ? Material.CLOCK : Material.CAMPFIRE, raid ? "<#E67E22><b>Raid en cours</b>" : "<#2ECC71><b>À la planque</b>", raid
                ? List.of("<#8A919B>Temps restant : <white>" + Text.clock(left),
                "<#8A919B>Extractions : <#2ECC71>" + String.join("<#5A6270>, </#5A6270><#2ECC71>", pr.extracts), "",
                "<#5A6270>Clic : distances et directions")
                : List.of("<#8A919B>En sécurité. Prépare ton équipement,", "<#8A919B>puis pars en raid depuis la tablette.")), e -> {
            if (plugin.raid().inRaid(p)) {
                p.closeInventory();
                plugin.raid().info(p);
            }
        });

        List<String> rankLore = new ArrayList<>();
        rankLore.add("<#8A919B>Rang : " + rank.tag());
        if (rank.creditBonus() > 0) {
            rankLore.add("<#8A919B>Crédits gagnés : <#2ECC71>+" + Math.round(rank.creditBonus() * 100) + " %");
        }
        if (rank.shopDiscount() > 0) {
            rankLore.add("<#8A919B>Boutique : <#2ECC71>-" + Math.round(rank.shopDiscount() * 100) + " %");
        }
        if (rank.parcelCut() > 0) {
            rankLore.add("<#8A919B>Attente des colis : <#2ECC71>-" + Math.round(rank.parcelCut() * 100) + " %");
        }
        if (rankLore.size() == 1) {
            rankLore.add("<#5A6270>Aucun avantage de rang.");
        }
        rankLore.add("");
        rankLore.add("<#5A6270>Les rangs ne donnent que du confort :");
        rankLore.add("<#5A6270>armes et tenues se trouvent en jeu.");
        set(h, 8, icon(Material.NETHER_STAR, "<" + rank.color() + "><b>Rang</b>", rankLore), null);

        // --- Bandeau 2 : équipement porté
        set(h, 9, zone(textured, Material.GRAY_STAINED_GLASS_PANE, "<#B8C0CA><b>Équipement</b>",
                List.of("<#8A919B>Tenue, armes et objets en main.")), null);
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        worn(h, textured, 10, inv.getHelmet(), "Casque");
        worn(h, textured, 11, inv.getChestplate(), "Plastron");
        worn(h, textured, 12, inv.getLeggings(), "Jambières");
        worn(h, textured, 13, inv.getBoots(), "Bottes");
        set(h, 14, outfitIcon(p), null);
        int grenades = 0;
        for (ItemStack it : inv.getContents()) {
            if (it != null && plugin.items().grenadeOf(it) != null) {
                grenades += it.getAmount();
            }
        }
        set(h, 15, icon(Material.FIREWORK_STAR, "<#E67E22><b>Grenades</b>", List.of("<#8A919B>Dans le sac : <white>" + grenades)), null);
        worn(h, textured, 16, inv.getItemInMainHand(), "Main droite");
        worn(h, textured, 17, inv.getItemInOffHand(), "Main gauche");

        // --- Bandeau 3 : bilan
        set(h, 18, zone(textured, Material.CYAN_STAINED_GLASS_PANE, "<#22C7D9><b>Bilan</b>",
                List.of("<#8A919B>Tes statistiques depuis ton premier raid.")), null);
        int survival = pr.raids == 0 ? 0 : (int) Math.round(pr.survived * 100.0 / pr.raids);
        set(h, 19, stat(Material.COMPASS, "Raids", String.valueOf(pr.raids), "Largages sur l'île."), null);
        set(h, 20, stat(Material.LIME_DYE, "Extractions", String.valueOf(pr.survived), "Raids terminés vivant, butin gardé."), null);
        set(h, 21, stat(Material.EMERALD, "Taux de survie", survival + " %", "Extractions / raids."), null);
        set(h, 22, stat(Material.SKELETON_SKULL, "Morts", String.valueOf(pr.deaths), "Tout l'inventaire reste sur place."), null);
        set(h, 23, stat(Material.IRON_SWORD, "Joueurs abattus", String.valueOf(pr.pvpKills), "Opérateurs éliminés en raid."), null);
        set(h, 24, stat(Material.ROTTEN_FLESH, "Infectés abattus", String.valueOf(pr.infectedKills), "Chaque infecté rapporte des crédits."), null);
        String kd = String.format(java.util.Locale.ROOT, "%.2f", pr.pvpKills / (double) Math.max(1, pr.deaths)).replace('.', ',');
        set(h, 25, stat(Material.TARGET, "Ratio J/M", kd, "Joueurs abattus par mort."), null);
        set(h, 26, stat(Material.BONE, "Éliminations", String.valueOf(pr.pvpKills + pr.infectedKills), "Joueurs et infectés confondus."), null);

        // --- Bandeau 4 : planque et compte
        set(h, 27, zone(textured, Material.YELLOW_STAINED_GLASS_PANE, "<#F5C542><b>Planque</b>",
                List.of("<#8A919B>Ton coffre, tes kits, tes échanges.")), null);
        int used = 0;
        for (String s : pr.stash) {
            if (s != null && !s.isEmpty()) {
                used++;
            }
        }
        set(h, 28, icon(Material.CHEST, "<#A48CFF><b>Coffre</b>", List.of("<#8A919B>Cases occupées : <white>" + used + " <#5A6270>/ " + Profile.STASH_SIZE,
                raid ? "<#C0392B>Inaccessible en raid." : "<#5A6270>Clic : ouvrir")), e -> {
            if (plugin.raid().inRaid(p)) {
                deny(p, "<#C0392B>Ton coffre est à la planque.</#C0392B> Extrais-toi d'abord.");
                return;
            }
            p.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> plugin.stash().open(p));
        });
        int ready = 0;
        for (Kit k : plugin.kits().all()) {
            if (plugin.kits().unlocked(p, k) && plugin.kits().remaining(p, k) == 0) {
                ready++;
            }
        }
        set(h, 29, icon(Material.LEATHER_CHESTPLATE, "<#FF9A3D><b>Kits</b>", List.of(
                ready > 0 ? "<#2ECC71>" + ready + " disponible" + (ready > 1 ? "s" : "") : "<#8A919B>Aucun kit disponible.", "<#5A6270>Clic : ouvrir")),
                e -> open(p, Screen.KITS));
        int unread = pr.unread();
        set(h, 30, icon(Material.WRITABLE_BOOK, "<#6EA2FF><b>Messages</b>", List.of(
                unread > 0 ? "<#6EA2FF>" + unread + " non lu" + (unread > 1 ? "s" : "") : "<#8A919B>Aucun nouveau message.", "<#5A6270>Clic : ouvrir")),
                e -> open(p, Screen.MESSAGES));
        Market market = plugin.market();
        int mine = market.count(p.getUniqueId()), back = market.waiting(p.getUniqueId());
        set(h, 31, icon(Material.GOLD_INGOT, "<#E05CB8><b>Hôtel des ventes</b>", List.of(
                "<#8A919B>Tes annonces : <white>" + mine,
                back > 0 ? "<#E67E22>" + back + " objet" + (back > 1 ? "s" : "") + " à récupérer" : "<#5A6270>Rien à récupérer.", "<#5A6270>Clic : ouvrir")),
                e -> open(p, Screen.HDV));
        set(h, 32, icon(Material.FILLED_MAP, "<#5CC8FF><b>Carte tactique</b>", List.of(
                pr.minimap ? "<#2ECC71>Affichée en main gauche" : "<#C0392B>Rangée",
                "<#5A6270>Clic : ranger / ressortir", "<#5A6270>Maj + clic : changer de zoom")), e -> {
            if (e.isShiftClick()) {
                plugin.miniMap().cycleZoom(p);
            } else {
                p.closeInventory();
                plugin.miniMap().toggle(p);
            }
        });
        set(h, 33, icon(Material.SUNFLOWER, "<#F5C542><b>Boutique</b>", List.of("<#8A919B>Armes, munitions, vivres.", "<#5A6270>Clic : ouvrir")),
                e -> open(p, Screen.SHOP));
        set(h, 34, icon(Material.BARREL, "<#3FC46A><b>Colis</b>", List.of("<#8A919B>Un largage près de toi, en plein raid.", "<#5A6270>Clic : ouvrir")),
                e -> open(p, Screen.COLIS));
        set(h, 35, icon(Material.EXPERIENCE_BOTTLE, "<#B8C0CA><b>Commandes</b>", List.of(
                "<#D7DCE2>Clic droit <#8A919B>tirer  <#5A6270>·  <#D7DCE2>clic gauche <#8A919B>viser",
                "<#D7DCE2>F <#8A919B>recharger (ou zoom de la carte)",
                "<#D7DCE2>Maj + F <#8A919B>cette fiche",
                "<#D7DCE2>/tablette <#8A919B>la tablette")), null);

        set(h, ACTIONS + 4, zone(textured, Material.BARRIER, "<#E8695C>Fermer", List.of()), e -> p.closeInventory());
        show(p, h);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.6f);
    }

    /** Pièce portée (copie avec une ligne d'aide), ou case vide nommée. */
    private void worn(Holder h, boolean textured, int slot, ItemStack it, String label) {
        if (it == null || it.isEmpty()) {
            set(h, slot, zone(textured, Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<#5A6270>" + label + " : aucun", List.of()), null);
            return;
        }
        set(h, slot, withLore(it, List.of("", "<#5A6270>" + label)), null);
    }

    private ItemStack outfitIcon(Player p) {
        fr.orbit.extraction.armor.Armors.SetDef set = null;
        for (ItemStack it : new ItemStack[]{p.getInventory().getChestplate(), p.getInventory().getHelmet(),
                p.getInventory().getLeggings(), p.getInventory().getBoots()}) {
            String key = plugin.armors().keyOf(it);
            if (key != null) {
                int sep = key.indexOf(':');
                set = plugin.armors().def(sep < 0 ? key : key.substring(0, sep));
                if (set != null) {
                    break;
                }
            }
        }
        if (set == null) {
            return icon(Material.LEATHER_CHESTPLATE, "<#5A6270><b>Sans tenue</b>", List.of(
                    "<#8A919B>Les tenues (fortune → lourde) se trouvent", "<#8A919B>dans les caisses et à la boutique."));
        }
        List<String> lore = new ArrayList<>();
        lore.add("<#8A919B>Niveau : <white>" + set.tier() + " <#5A6270>/ 5");
        if (set.description() != null && !set.description().isEmpty()) {
            lore.add("<#5A6270>" + set.description());
        }
        return icon(Material.NETHERITE_CHESTPLATE, "<" + set.color() + "><b>Tenue : " + Text.esc(set.name()) + "</b>", lore);
    }

    private static ItemStack stat(Material mat, String name, String value, String hint) {
        return icon(mat, "<#22C7D9><b>" + name + "</b>", List.of("<white>" + value, "<#5A6270>" + hint));
    }

    private static double maxHealth(Player p) {
        org.bukkit.attribute.AttributeInstance a = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return a == null ? 20 : a.getValue();
    }

    private static String fmt(double v) {
        return (v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v)).replace('.', ',');
    }

    // ------------------------------------------------------------------ boutique

    private void shop(Player p, int page) {
        List<Article> all = phone.articles();
        int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        Holder h = create(p, Screen.SHOP, pg);
        boolean textured = plugin.pack().isLoaded(p.getUniqueId());
        header(p, h, textured);
        Rank rank = plugin.ranks().of(p);
        for (int i = 0; i < PAGE && pg * PAGE + i < all.size(); i++) {
            Article a = all.get(pg * PAGE + i);
            long price = plugin.ranks().discounted(p, a.price());
            set(h, i, withLore(a.icon(), List.of("",
                    "<#8A919B>Prix : <#F5C542>" + price + " crédits</#F5C542>"
                            + (price < a.price() ? " <#5A6270><st>" + a.price() + "</st></#5A6270> " + rank.tag() : "")
                            + (a.amount() > 1 ? " <#5A6270>(x" + a.amount() + ")" : ""),
                    "<#5A6270>Clic : acheter")), e -> buy(p, a));
        }
        if (pg > 0) {
            set(h, ACTIONS, icon(Material.ARROW, "<#D7DCE2>← Page précédente", List.of()), e -> open(p, Screen.SHOP, pg - 1));
        }
        if (pg < pages - 1) {
            set(h, ACTIONS + 8, icon(Material.ARROW, "<#D7DCE2>Page suivante →", List.of()), e -> open(p, Screen.SHOP, pg + 1));
        }
        set(h, ACTIONS + 4, zone(textured, Material.GRAY_STAINED_GLASS_PANE, "<#F5C542>Revendre du butin", List.of(
                "<#8A919B>Maj + clic sur un objet de ton inventaire",
                "<#8A919B>pour le revendre (la pile entière).",
                "<#5A6270>Les objets sans valeur sont refusés.")), null);
        show(p, h);
    }

    private void buy(Player p, Article a) {
        if (plugin.raid().inRaid(p)) {
            deny(p, "<#C0392B>La boutique ne livre qu'à la planque.</#C0392B> En raid, commande un colis.");
            return;
        }
        long price = plugin.ranks().discounted(p, a.price());
        if (!phone.pay(p, price)) {
            deny(p, "<#C0392B>Pas assez de crédits</#C0392B> <#8A919B>(il t'en faut " + price + ", tu en as " + phone.credits(p) + ").");
            return;
        }
        for (ItemStack it : plugin.specs().create(a.spec(), true)) {
            GunItems.give(p, it);
        }
        plugin.profiles().save(plugin.profiles().get(p));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
        Text.bar(p, "<#2ECC71>Acheté</#2ECC71> <#8A919B>— reste <#F5C542>" + phone.credits(p) + " crédits");
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof Holder h && h.screen == Screen.SHOP) {
            top.setItem(CREDITS, creditsItem(p));
        }
    }

    private void sell(Player p, InventoryClickEvent e) {
        ItemStack it = e.getCurrentItem();
        long unit = phone.sellPrice(it);
        if (it == null || it.getType().isAir()) {
            return;
        }
        if (plugin.raid().inRaid(p)) {
            deny(p, "<#C0392B>La boutique ne rachète qu'à la planque.");
            return;
        }
        if (unit <= 0) {
            deny(p, "<#8A919B>La boutique ne rachète pas cet objet.");
            return;
        }
        long total = unit * it.getAmount();
        e.setCurrentItem(null);
        phone.add(p, total);
        plugin.profiles().save(plugin.profiles().get(p));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 0.9f);
        Text.bar(p, "<#2ECC71>+" + total + " crédits</#2ECC71> <#8A919B>(revente)");
        e.getView().getTopInventory().setItem(CREDITS, creditsItem(p));
    }

    // ------------------------------------------------------------------ hôtel des ventes

    private void market(Player p, int page) {
        Market market = plugin.market();
        boolean only = mineOnly.contains(p.getUniqueId());
        List<Market.Listing> all = market.list(only ? p.getUniqueId() : null);
        int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        Holder h = create(p, Screen.HDV, pg);
        boolean textured = plugin.pack().isLoaded(p.getUniqueId());
        header(p, h, textured);
        for (int i = 0; i < PAGE && pg * PAGE + i < all.size(); i++) {
            Market.Listing l = all.get(pg * PAGE + i);
            boolean own = l.seller().equals(p.getUniqueId());
            long hours = market.hoursLeft(l);
            set(h, i, withLore(l.item(), List.of("",
                    "<#8A919B>Prix : <#F5C542>" + l.price() + " crédits</#F5C542>",
                    "<#8A919B>Vendeur : <#D7DCE2>" + Text.esc(l.sellerName()),
                    "<#5A6270>Expire dans " + (hours >= 1 ? hours + " h" : "moins d'une heure"),
                    own ? "<#E05CB8>Ton annonce</#E05CB8> <#5A6270>— Maj + clic : retirer" : "<#5A6270>Clic : acheter")), e -> {
                if (own) {
                    if (e.isShiftClick()) {
                        market.withdraw(p, l.id());
                        open(p, Screen.HDV, pg);
                    }
                } else if (market.buy(p, l.id())) {
                    open(p, Screen.HDV, pg);
                } else if (market.list(null).stream().noneMatch(x -> x.id().equals(l.id()))) {
                    open(p, Screen.HDV, pg);        // annonce disparue entre-temps : on rafraîchit
                }
            });
        }
        if (all.isEmpty()) {
            set(h, 13, icon(Material.BARRIER, only ? "<#8A919B>Tu n'as aucune annonce en cours" : "<#8A919B>Aucune annonce pour l'instant", List.of(
                    "<#5A6270>Maj + clic sur un objet de ton inventaire", "<#5A6270>pour le mettre en vente.")), null);
        }
        if (pg > 0) {
            set(h, ACTIONS, icon(Material.ARROW, "<#D7DCE2>← Page précédente", List.of()), e -> open(p, Screen.HDV, pg - 1));
        }
        if (pg < pages - 1) {
            set(h, ACTIONS + 8, icon(Material.ARROW, "<#D7DCE2>Page suivante →", List.of()), e -> open(p, Screen.HDV, pg + 1));
        }
        int mine = market.count(p.getUniqueId()), max = market.maxListings(p), back = market.waiting(p.getUniqueId());
        set(h, ACTIONS + 2, icon(only ? Material.WRITABLE_BOOK : Material.BOOK, "<#E05CB8><b>" + (only ? "Mes annonces" : "Toutes les annonces") + "</b>", List.of(
                "<#8A919B>Tes annonces : <#D7DCE2>" + mine + "</#D7DCE2> / " + max,
                "", "<#5A6270>Clic : " + (only ? "voir toutes les annonces" : "ne voir que les miennes"))), e -> {
            if (!mineOnly.remove(p.getUniqueId())) {
                mineOnly.add(p.getUniqueId());
            }
            open(p, Screen.HDV);
        });
        set(h, ACTIONS + 4, icon(Material.GOLD_INGOT, "<#F5C542><b>Vendre un objet</b>", List.of(
                "<#8A919B>Maj + clic sur un objet de ton inventaire,",
                "<#8A919B>puis écris son prix dans le chat.",
                "<#8A919B>La pile entière part en vente.",
                "", "<#5A6270>Taxe de " + market.taxPercent() + " % prélevée à la vente.",
                "<#5A6270>À la planque uniquement.")), null);
        ItemStack chest = icon(Material.CHEST, "<#E67E22><b>Récupérer</b>", List.of(
                back > 0 ? "<#E67E22>" + back + " objet" + (back > 1 ? "s" : "") + " invendu" + (back > 1 ? "s" : "") + " t'attend" + (back > 1 ? "ent" : "")
                        : "<#8A919B>Aucun objet à récupérer.",
                "<#8A919B>Les annonces expirées reviennent ici.",
                "", "<#5A6270>Clic : tout récupérer"));
        if (back > 0) {
            chest.editMeta(m -> m.setEnchantmentGlintOverride(true));
        }
        set(h, ACTIONS + 6, chest, e -> {
            market.collect(p);
            open(p, Screen.HDV, pg);
        });
        show(p, h);
    }

    // ------------------------------------------------------------------ colis

    private void colis(Player p) {
        Holder h = create(p, Screen.COLIS, 0);
        boolean textured = plugin.pack().isLoaded(p.getUniqueId());
        header(p, h, textured);
        List<Offer> offers = phone.offers();
        for (int i = 0; i < PAGE && i < offers.size(); i++) {
            Offer o = offers.get(i);
            List<String> lore = new ArrayList<>();
            fr.orbit.extraction.vehicle.Vehicles.Def veh = o.vehicle() == null ? null : plugin.vehicles().def(o.vehicle());
            if (veh != null) {
                lore.addAll(plugin.vehicles().describe(veh));
                lore.add("<#8A919B>Laissé sur place à la fin du raid.");
            } else {
                lore.add("<#8A919B>Contenu :");
                for (String spec : o.contents()) {
                    lore.add("<#5A6270> • <#D7DCE2>" + plugin.parcels().describe(spec));
                }
            }
            lore.add("");
            lore.add("<#8A919B>Prix : <#F5C542>" + o.price() + " crédits");
            lore.add("<#8A919B>Largage en <#D7DCE2>" + plugin.parcels().delaySeconds() + " s</#D7DCE2>, près de toi.");
            lore.add("<#5A6270>Clic : commander (en raid)");
            set(h, i, icon(o.icon(), "<#3FC46A><b>" + Text.esc(o.name()) + "</b>", lore), e -> {
                if (plugin.parcels().order(p, o)) {
                    p.closeInventory();
                }
            });
        }
        show(p, h);
    }

    // ------------------------------------------------------------------ kits

    private void kits(Player p) {
        Holder h = create(p, Screen.KITS, 0);
        boolean textured = plugin.pack().isLoaded(p.getUniqueId());
        header(p, h, textured);
        Rank rank = plugin.ranks().of(p);

        List<String> perks = new ArrayList<>();
        perks.add("<#8A919B>Ton rang : " + rank.tag());
        perks.add("");
        for (Rank r : plugin.ranks().all()) {
            perks.add((r == rank ? "<#D7DCE2>▶ " : "<#5A6270>  ") + r.tag() + " <#8A919B>" + perkLine(r));
        }
        if (!plugin.ranks().shopLink().isEmpty()) {
            perks.add("");
            perks.add("<#8A919B>Rangs : <#D7DCE2>" + Text.esc(plugin.ranks().shopLink()));
        }
        set(h, 4, icon(Material.NETHER_STAR, "<" + rank.color() + "><b>Rangs et avantages</b>", perks), null);

        List<Kit> all = plugin.kits().all();
        int[] slots = all.size() <= 4 ? new int[]{19, 21, 23, 25} : new int[]{18, 19, 20, 21, 22, 23, 24, 25, 26};
        for (int i = 0; i < all.size() && i < slots.length; i++) {
            Kit k = all.get(i);
            boolean unlocked = plugin.kits().unlocked(p, k);
            long left = plugin.kits().remaining(p, k);
            List<String> lore = new ArrayList<>();
            lore.add("<#8A919B>Rang : " + k.rank().tag() + " <#5A6270>• tous les " + Kits.duration(k.cooldownMs()));
            lore.add("");
            for (String spec : k.contents()) {
                lore.add("<#5A6270> • <#D7DCE2>" + plugin.parcels().describe(spec));
            }
            lore.add("");
            if (!unlocked) {
                lore.add("<#C0392B>Réservé au rang " + Text.esc(k.rank().name()) + " et au-dessus");
            } else if (left > 0) {
                lore.add("<#E67E22>Disponible dans " + Kits.duration(left));
            } else {
                lore.add("<#2ECC71>Disponible</#2ECC71> <#5A6270>— clic : prendre (à la planque)");
            }
            ItemStack it = icon(unlocked ? k.icon() : Material.IRON_BARS, "<" + k.rank().color() + "><b>" + Text.esc(k.name()) + "</b>", lore);
            if (unlocked && left == 0) {
                it.editMeta(m -> m.setEnchantmentGlintOverride(true));
            }
            set(h, slots[i], it, e -> {
                plugin.kits().claim(p, k);
                open(p, Screen.KITS);
            });
        }
        show(p, h);
    }

    private static String perkLine(Rank r) {
        List<String> parts = new ArrayList<>();
        if (r.creditBonus() > 0) {
            parts.add("+" + Math.round(r.creditBonus() * 100) + " % de crédits");
        }
        if (r.shopDiscount() > 0) {
            parts.add("-" + Math.round(r.shopDiscount() * 100) + " % en boutique");
        }
        if (r.parcelCut() > 0) {
            parts.add("colis " + Math.round(r.parcelCut() * 100) + " % plus souvent");
        }
        return parts.isEmpty() ? "kit de base" : String.join(", ", parts);
    }

    // ------------------------------------------------------------------ messages

    private void messages(Player p) {
        Holder h = create(p, Screen.MESSAGES, 0);
        boolean textured = plugin.pack().isLoaded(p.getUniqueId());
        header(p, h, textured);
        int slot = 0;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o == p || slot >= 18 || !p.canSee(o)) {
                continue;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            head.editMeta(SkullMeta.class, m -> {
                m.setOwningPlayer(o);
                m.displayName(Text.item("<#6EA2FF><b>" + Text.esc(o.getName()) + "</b>"));
                m.lore(Text.lore(List.of("<#8A919B>" + (plugin.raid().inRaid(o) ? "En raid" : "À la planque"), "<#5A6270>Clic : écrire un message")));
            });
            set(h, slot++, head, e -> phone.compose(p, o));
        }
        if (slot == 0) {
            set(h, 4, icon(Material.BARRIER, "<#8A919B>Personne d'autre en ligne", List.of("<#5A6270>Les contacts sont les joueurs connectés.")), null);
        }
        Profile pr = plugin.profiles().get(p);
        boolean changed = false;
        int n = pr.messages.size();
        for (int i = 0; i < n && i < 18; i++) {
            int index = n - 1 - i;                   // du plus récent au plus ancien
            Profile.Message m = pr.messages.get(index);
            List<String> lore = new ArrayList<>();
            for (String line : wrap(m.text(), 34)) {
                lore.add("<#D7DCE2>" + Text.esc(line));
            }
            lore.add("");
            lore.add("<#5A6270>" + ago(m.time()) + "  •  Clic : répondre  •  Maj + clic : supprimer");
            set(h, 18 + i, icon(m.read() ? Material.PAPER : Material.WRITABLE_BOOK,
                    (m.read() ? "<#9AA1AD>" : "<#6EA2FF><b>") + "De " + Text.esc(m.from()), lore), e -> {
                if (e.isShiftClick()) {
                    pr.messages.remove(m);
                    plugin.profiles().save(pr);
                    open(p, Screen.MESSAGES);
                    return;
                }
                Player to = Bukkit.getPlayerExact(m.from());
                if (to == null) {
                    deny(p, "<#C0392B>" + Text.esc(m.from()) + " n'est pas en ligne.");
                } else {
                    phone.compose(p, to);
                }
            });
            if (!m.read()) {
                pr.messages.set(index, new Profile.Message(m.time(), m.from(), m.text(), true));
                changed = true;
            }
        }
        if (changed) {
            plugin.profiles().save(pr);
        }
        show(p, h);
    }

    private static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    private static String ago(long time) {
        long s = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        if (s < 60) {
            return "à l'instant";
        }
        if (s < 3600) {
            return "il y a " + (s / 60) + " min";
        }
        if (s < 86400) {
            return "il y a " + (s / 3600) + " h";
        }
        return "il y a " + (s / 86400) + " j";
    }

    // ------------------------------------------------------------------ événements

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() == null) {
            return;
        }
        if (e.getClickedInventory() != e.getView().getTopInventory()) {
            if (h.screen == Screen.SHOP && e.isShiftClick()) {
                sell(p, e);
            } else if (h.screen == Screen.HDV && e.isShiftClick()) {
                plugin.market().startSale(p, e.getSlot(), e.getCurrentItem());
            }
            return;
        }
        Consumer<InventoryClickEvent> action = h.actions.get(e.getSlot());
        if (action != null) {
            click(p);
            action.accept(e);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }
}
