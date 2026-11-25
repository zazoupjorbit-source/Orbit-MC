package fr.orbit.mmo.ui;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.market.Market;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Hôtel des ventes : parcourir les annonces des joueurs, acheter, mettre en vente, suivre ses ventes. */
public final class MarketMenus {

    private MarketMenus() {
    }

    private static final String GOLD = "#FFD66E";

    /** Filtre par famille d'objets. */
    enum Cat {
        TOUT("Tout", Material.COMPASS),
        ARMES("Armes", Material.IRON_SWORD),
        ARMURES("Armures", Material.IRON_CHESTPLATE),
        CONSOMMABLES("Consommables", Material.POTION),
        MATERIAUX("Matériaux", Material.AMETHYST_SHARD),
        RUNES("Runes", Material.BLADE_POTTERY_SHERD),
        AUTRES("Autres", Material.CHEST);

        final String label;
        final Material icon;

        Cat(String label, Material icon) {
            this.label = label;
            this.icon = icon;
        }

        static Cat of(ItemStack it) {
            String t = Items.type(it);
            if (t == null) {
                return it.getType() == Material.SHIELD ? ARMURES : AUTRES;
            }
            return switch (t) {
                case "weapon" -> ARMES;
                case "armor", "lab", "shield" -> ARMURES;
                case "material" -> MATERIAUX;
                case "rune" -> RUNES;
                case "potion", "mana_potion", "heal_crystal", "tp_crystal" -> CONSOMMABLES;
                default -> t.startsWith("boost_") ? CONSOMMABLES : AUTRES;
            };
        }

        Cat next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    enum Sort {
        RECENT("Plus récentes"),
        PRIX_CROISSANT("Prix croissant"),
        PRIX_DECROISSANT("Prix décroissant");

        final String label;

        Sort(String label) {
            this.label = label;
        }

        Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Personnage valide et en ville. */
    private static PlayerData client(OrbitMMO plugin, Player p) {
        PlayerData d = plugin.players().get(p);
        if (!plugin.progression().valid(d)) {
            return null;
        }
        if (!plugin.floors().isSafe(p.getLocation())) {
            p.closeInventory();
            Text.msg(p, "<#FF5C5C>L'hôtel des ventes se trouve en ville.");
            return null;
        }
        return d;
    }

    private static Component title(String page, PlayerData d) {
        return Text.mm("<" + GOLD + "><b>Hôtel des ventes</b></" + GOLD + "> <#5A6270>· " + page + " · <" + GOLD + ">" + Text.num(d.gold) + " or");
    }

    public static void open(OrbitMMO plugin, Player p) {
        browse(plugin, p, Cat.TOUT, Sort.RECENT, 0);
    }

    // ------------------------------------------------------------------ Parcourir

    static void browse(OrbitMMO plugin, Player p, Cat cat, Sort sort, int page) {
        PlayerData d = client(plugin, p);
        if (d == null) {
            return;
        }
        Market market = plugin.market();
        List<Market.Listing> all = new ArrayList<>();
        for (Market.Listing l : market.active()) {
            if (cat == Cat.TOUT || Cat.of(l.item) == cat) {
                all.add(l);
            }
        }
        switch (sort) {
            case PRIX_CROISSANT -> all.sort(Comparator.comparingLong(l -> l.price));
            case PRIX_DECROISSANT -> all.sort(Comparator.comparingLong((Market.Listing l) -> l.price).reversed());
            default -> {
            }
        }
        int pages = Math.max(1, (all.size() + 44) / 45);
        int pg = Math.max(0, Math.min(page, pages - 1));
        Menu m = new Menu(6, title(cat.label + (pages > 1 ? " · " + (pg + 1) + "/" + pages : ""), d))
                .skin("Hôtel des ventes", cat.label + (pages > 1 ? " · page " + (pg + 1) + "/" + pages : ""), Menus.gold(d));
        for (int i = 0; i < 45; i++) {
            int idx = pg * 45 + i;
            if (idx >= all.size()) {
                break;
            }
            Market.Listing l = all.get(idx);
            boolean mine = l.seller.equals(p.getUniqueId());
            m.set(i, listingIcon(l, d, mine), click -> {
                Menus.click(p);
                if (mine) {
                    if (plugin.market().cancel(p, l.id)) {
                        Text.msg(p, "<#8C96A3>Annonce retirée, l'objet revient dans votre inventaire.");
                    }
                    browse(plugin, p, cat, sort, pg);
                } else {
                    confirm(plugin, p, l.id, () -> browse(plugin, p, cat, sort, pg));
                }
            });
        }
        if (all.isEmpty()) {
            m.set(22, Items.icon(Material.BARRIER, "<#8C96A3>Aucune annonce" + (cat == Cat.TOUT ? "" : " dans « " + cat.label + " »"),
                    "<#5A6270>Soyez le premier : « Mettre en vente »."), null);
        }

        // Barre du bas
        m.set(45, Items.icon(Material.ARROW, "<#8C96A3>Menu"), t -> Menus.main(plugin, p));
        if (pg > 0) {
            m.set(46, Items.icon(Material.SPECTRAL_ARROW, "<#D7DCE2>◀ Page précédente"), t -> browse(plugin, p, cat, sort, pg - 1));
        }
        List<String> cats = new ArrayList<>();
        for (Cat c : Cat.values()) {
            cats.add((c == cat ? "<#FFD66E>▶ " : "<#5A6270>  ") + c.label);
        }
        cats.add("");
        cats.add("<#5A6270>[Clic] filtre suivant");
        m.set(47, Items.icon(cat.icon, "<#7FD4FF><b>Filtre : " + cat.label + "</b>", cats), t -> {
            Menus.click(p);
            browse(plugin, p, cat.next(), sort, 0);
        });
        List<String> sorts = new ArrayList<>();
        for (Sort s : Sort.values()) {
            sorts.add((s == sort ? "<#FFD66E>▶ " : "<#5A6270>  ") + s.label);
        }
        sorts.add("");
        sorts.add("<#5A6270>[Clic] tri suivant");
        m.set(48, Items.icon(Material.HOPPER, "<#7FD4FF><b>Tri : " + sort.label + "</b>", sorts), t -> {
            Menus.click(p);
            browse(plugin, p, cat, sort.next(), 0);
        });
        m.set(49, Items.icon(Material.OAK_HANGING_SIGN, "<" + GOLD + "><b>Hôtel des ventes</b>",
                "<#D7DCE2>Achetez et vendez entre joueurs,",
                "<#D7DCE2>au prix fixé par le vendeur.",
                "",
                "<#8C96A3>Annonces en cours : <white>" + market.active().size(),
                "<#8C96A3>Taxe à la vente : <white>" + market.taxPercent() + " %",
                "<#8C96A3>Durée d'une annonce : <white>" + market.durationHours() + " h",
                "<#8C96A3>Annonces par joueur : <white>" + market.maxListings(),
                "",
                "<#5A6270>Invendu = rendu dans « Mes ventes ».",
                "<#5A6270>Vendu hors ligne = or versé à la connexion."), null);
        int mineCount = market.of(p.getUniqueId()).size();
        int back = market.returns(p.getUniqueId()).size();
        m.set(50, Items.icon(back > 0 ? Material.CHEST_MINECART : Material.CHEST, "<#5EE89A><b>Mes ventes</b>",
                "<#8C96A3>Annonces en cours : <white>" + mineCount + " / " + market.maxListings(),
                back > 0 ? "<#FFD66E>" + back + " objet(s) invendu(s) à récupérer" : "<#5A6270>Rien à récupérer",
                "", "<#5A6270>[Clic] ouvrir"), t -> mine(plugin, p));
        m.set(51, Items.icon(Material.WRITABLE_BOOK, "<" + GOLD + "><b>Mettre en vente</b>",
                "<#8C96A3>Choisissez un objet de votre inventaire,",
                "<#8C96A3>puis tapez son prix dans le chat.",
                "", "<#5A6270>[Clic] ouvrir"), t -> sell(plugin, p));
        m.set(52, Items.icon(Material.GOLD_INGOT, "<#D7DCE2><b>Revendre au forgeron</b>",
                "<#8C96A3>Torvald rachète le butin tout de suite,",
                "<#8C96A3>à prix fixe (bien moins qu'entre joueurs).",
                "", "<#5A6270>[Clic] ouvrir"), t -> Menus.buyback(plugin, p));
        if (pg < pages - 1) {
            m.set(53, Items.icon(Material.SPECTRAL_ARROW, "<#D7DCE2>Page suivante ▶"), t -> browse(plugin, p, cat, sort, pg + 1));
        }
        Menus.frame(m, 6);
        m.open(p);
    }

    private static ItemStack listingIcon(Market.Listing l, PlayerData d, boolean mine) {
        ItemStack icon = l.item.clone();
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        List<String> add = new ArrayList<>();
        add.add("");
        add.add("<" + GOLD + ">── Hôtel des ventes ──");
        add.add("<#8C96A3>Prix : <" + (mine || d.gold >= l.price ? GOLD : "#FF5C5C") + "><b>" + Text.num(l.price) + " or</b>"
                + (l.item.getAmount() > 1 ? " <#5A6270>(" + unit(l) + " l'unité)" : ""));
        add.add("<#8C96A3>Vendeur : <white>" + Text.esc(l.sellerName));
        add.add("<#8C96A3>Expire dans : <white>" + left(l.expires - System.currentTimeMillis()));
        add.add("");
        add.add(mine ? "<#FF8A3B>Votre annonce · [Clic] la retirer" : "<#5EE89A>[Clic] acheter");
        lore.addAll(Text.lore(add));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private static String unit(Market.Listing l) {
        double u = (double) l.price / l.item.getAmount();
        return u >= 10 ? Text.num(Math.round(u)) : String.format(java.util.Locale.FRANCE, "%.1f", u);
    }

    static String left(long ms) {
        if (ms <= 60_000) {
            return "< 1 min";
        }
        long min = ms / 60_000;
        long h = min / 60;
        return h > 0 ? h + " h " + (min % 60 > 0 ? (min % 60) + " min" : "") : min + " min";
    }

    // ------------------------------------------------------------------ Achat

    private static void confirm(OrbitMMO plugin, Player p, java.util.UUID id, Runnable back) {
        PlayerData d = client(plugin, p);
        if (d == null) {
            return;
        }
        Market.Listing l = plugin.market().get(id);
        if (l == null) {
            Text.msg(p, "<#FF5C5C>Cette annonce n'est plus disponible.");
            back.run();
            return;
        }
        Menu m = new Menu(3, title("Confirmer l'achat", d)).skin("Hôtel des ventes", "Confirmer l'achat", Menus.gold(d));
        m.set(4, listingIcon(l, d, false), null);
        boolean ok = d.gold >= l.price;
        m.set(11, Items.icon(ok ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, ok ? "<#5EE89A><b>Acheter</b>" : "<#FF5C5C><b>Pas assez d'or</b>",
                "<#8C96A3>Prix : <" + GOLD + ">" + Text.num(l.price) + " or",
                "<#8C96A3>Votre or : <white>" + Text.num(d.gold),
                ok ? "<#8C96A3>Après achat : <white>" + Text.num(d.gold - l.price) : "<#FF5C5C>Il manque " + Text.num(l.price - d.gold) + " or"),
                t -> {
                    PlayerData dd = client(plugin, p);
                    if (dd == null) {
                        return;
                    }
                    String err = plugin.market().buy(p, dd, id);
                    if (err != null) {
                        Text.msg(p, "<#FF5C5C>" + err);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
                    }
                    back.run();
                });
        m.set(15, Items.icon(Material.RED_CONCRETE, "<#FF5C5C><b>Annuler</b>"), t -> {
            Menus.click(p);
            back.run();
        });
        Menus.frame(m, 3);
        m.open(p);
    }

    // ------------------------------------------------------------------ Mettre en vente

    static void sell(OrbitMMO plugin, Player p) {
        PlayerData d = client(plugin, p);
        if (d == null) {
            return;
        }
        Market market = plugin.market();
        int count = market.of(p.getUniqueId()).size();
        Menu m = new Menu(3, title("Mettre en vente", d)).skin("Hôtel des ventes", "Mettre en vente", Menus.gold(d));
        m.set(4, Items.icon(Material.OAK_SIGN, "<" + GOLD + "><b>Comment vendre ?</b>",
                "<#D7DCE2>1. Cliquez un objet dans <white>votre inventaire</white>",
                "<#D7DCE2>   (en bas) : toute la pile est proposée.",
                "<#D7DCE2>2. Tapez le <white>prix total</white> dans le chat.",
                "",
                "<#8C96A3>Taxe : <white>" + market.taxPercent() + " %</white> retenus à la vente",
                "<#8C96A3>Durée : <white>" + market.durationHours() + " h</white>, puis l'objet vous revient",
                "<#8C96A3>Vos annonces : <white>" + count + " / " + market.maxListings(),
                "",
                "<#5A6270>Objets de sort et « Mon personnage » : invendables.",
                "<#5A6270>Objets liés (butin de boss, pièces déjà équipées) : invendables.",
                "<#5A6270>Pour vendre une partie d'une pile, séparez-la d'abord."), null);
        m.set(18, Items.icon(Material.ARROW, "<#8C96A3>Retour"), t -> open(plugin, p));
        m.set(22, Items.icon(Material.CHEST, "<#5EE89A><b>Mes ventes</b>", "<#5A6270>[Clic] ouvrir"), t -> mine(plugin, p));
        m.onBottomClick((slot, type) -> {
            if (client(plugin, p) != null) {
                plugin.market().askPrice(p, slot);
            }
        });
        Menus.frame(m, 3);
        m.open(p);
    }

    // ------------------------------------------------------------------ Mes ventes

    private static final int[] MINE_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};
    private static final int RETURN_FIRST = 36;

    static void mine(OrbitMMO plugin, Player p) {
        PlayerData d = client(plugin, p);
        if (d == null) {
            return;
        }
        Market market = plugin.market();
        List<Market.Listing> list = market.of(p.getUniqueId());
        List<ItemStack> back = market.returns(p.getUniqueId());
        Menu m = new Menu(6, title("Mes ventes", d)).skin("Hôtel des ventes", "Mes ventes", Menus.gold(d));
        m.set(4, Items.icon(Material.BOOK, "<#5EE89A><b>Mes annonces</b> <#8C96A3>(" + list.size() + " / " + market.maxListings() + ")",
                "<#8C96A3>Clic sur une annonce : la retirer",
                "<#8C96A3>(l'objet revient dans votre inventaire).",
                "",
                "<#5A6270>Vendu : l'or arrive tout seul,",
                "<#5A6270>taxe de " + market.taxPercent() + " % déduite."), null);
        for (int i = 0; i < list.size() && i < MINE_SLOTS.length; i++) {
            Market.Listing l = list.get(i);
            ItemStack icon = listingIcon(l, d, true);
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>(meta.lore());
            lore.add(lore.size() - 2, Text.item("<#8C96A3>Vous toucherez : <" + GOLD + ">" + Text.num(market.net(l.price)) + " or"));
            meta.lore(lore);
            icon.setItemMeta(meta);
            m.set(MINE_SLOTS[i], icon, t -> {
                Menus.click(p);
                if (plugin.market().cancel(p, l.id)) {
                    Text.msg(p, "<#8C96A3>Annonce retirée, l'objet revient dans votre inventaire.");
                }
                mine(plugin, p);
            });
        }
        if (list.isEmpty()) {
            m.set(13, Items.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<#8C96A3>Aucune annonce en cours"), null);
        }
        m.set(31, Items.icon(back.isEmpty() ? Material.HOPPER : Material.CHEST_MINECART,
                "<#FFD66E><b>Invendus à récupérer</b> <#8C96A3>(" + back.size() + ")",
                back.isEmpty() ? "<#5A6270>Rien pour l'instant." : "<#8C96A3>Clic sur un objet : le reprendre."), null);
        for (int i = 0; i < back.size() && i < 9; i++) {
            int index = i;
            ItemStack icon = back.get(i).clone();
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Text.item(""));
            lore.add(Text.item("<#5EE89A>[Clic] récupérer"));
            meta.lore(lore);
            icon.setItemMeta(meta);
            m.set(RETURN_FIRST + i, icon, t -> {
                if (plugin.market().collect(p, index)) {
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
                }
                mine(plugin, p);
            });
        }
        m.set(45, Items.icon(Material.ARROW, "<#8C96A3>Retour"), t -> open(plugin, p));
        long gold = market.pendingGold(p.getUniqueId());
        if (gold > 0) {
            m.set(48, Items.icon(Material.GOLD_BLOCK, "<#FFD66E><b>Récupérer " + Text.num(gold) + " or</b>",
                    "<#8C96A3>Ventes faites pendant votre absence."), t -> {
                PlayerData dd = client(plugin, p);
                if (dd != null) {
                    plugin.market().claimGold(p, dd);
                }
                mine(plugin, p);
            });
        }
        m.set(50, Items.icon(Material.WRITABLE_BOOK, "<" + GOLD + "><b>Mettre en vente</b>", "<#5A6270>[Clic] ouvrir"), t -> sell(plugin, p));
        if (back.size() > 1) {
            m.set(53, Items.icon(Material.HOPPER_MINECART, "<#5EE89A><b>Tout récupérer</b>",
                    "<#8C96A3>" + back.size() + " objet(s), selon la place libre"), t -> {
                int got = 0;
                while (!plugin.market().returns(p.getUniqueId()).isEmpty() && p.getInventory().firstEmpty() >= 0
                        && plugin.market().collect(p, 0)) {
                    got++;
                }
                if (got > 0) {
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.0f);
                }
                mine(plugin, p);
            });
        }
        Menus.frame(m, 6);
        m.open(p);
    }
}
