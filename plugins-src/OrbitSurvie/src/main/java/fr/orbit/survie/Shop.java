package fr.orbit.survie;

import fr.orbit.survie.Marche.Article;
import fr.orbit.survie.Marche.Categorie;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Menus du marché : /shop (catégories → objets → quantité), /vendre (main ou tout l'inventaire), /prix.
 * Les PNJ marchands de l'île ouvrent directement leur catégorie.
 */
public final class Shop implements CommandExecutor, TabCompleter, Listener {

    private static final int[] SLOTS_CATEGORIES = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24};
    private static final int SLOT_TENDANCES = 25;
    /** « Vendre tout » ne touche pas aux objets plus chers que ça (têtes, étoile du Nether…). */
    private static final double VENDRE_TOUT_MAX = 400;

    private final OrbitSurvie plugin;
    private final Marche marche;

    public Shop(OrbitSurvie plugin, Marche marche) {
        this.plugin = plugin;
        this.marche = marche;
    }

    private boolean ouvert(Player p) {
        if (!plugin.eco().active()) {
            Msg.send(p, "<red>Le marché est fermé : la base de données est injoignable.");
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ accueil

    public void accueil(Player p) {
        if (!ouvert(p)) {
            return;
        }
        Menu menu = new Menu(5, Msg.BLEU + "<bold>✦ MARCHÉ ORBIT ✦");
        cadre(menu, 5);
        Categorie[] cats = Categorie.values();
        for (int i = 0; i < cats.length && i < SLOTS_CATEGORIES.length; i++) {
            Categorie c = cats[i];
            int n = marche.categorie(c).size();
            menu.set(SLOTS_CATEGORIES[i], Menu.icone(c.icone, Msg.OR + "<bold>" + c.nom,
                    "<gray>" + n + " objets", "", Msg.BLEU + "Clic <gray>: ouvrir"),
                    (pl, click) -> categorie(pl, c, 0));
        }
        menu.set(SLOT_TENDANCES, Menu.icone(Material.SPYGLASS, Msg.OR + "<bold>Tendances du marché",
                "<gray>Les objets dont le prix a le plus bougé", "<gray>(beaucoup vendus ou beaucoup achetés).", "",
                Msg.BLEU + "Clic <gray>: voir"), (pl, click) -> tendances(pl));
        menu.set(37, Menu.icone(Material.BOOK, Msg.OR + "<bold>Comment ça marche ?",
                "<gray>Les prix suivent l'offre et la demande :",
                "<gray>chaque vente fait <#FF6B6B>baisser<gray> le prix d'un objet,",
                "<gray>chaque achat le fait <#3DDCB0>monter<gray>, et il revient",
                "<gray>doucement à la normale avec le temps.",
                "",
                "<white>/shop <mot> <gray>: chercher (nom anglais : diamond, oak…)",
                "<white>/vendre <gray>: vendre l'objet en main",
                "<white>/vendre tout <gray>: vendre tout l'inventaire",
                "<white>/prix <gray>: prix de l'objet en main"), null);
        menu.set(43, Menu.icone(Material.HOPPER, Msg.OR + "<bold>Vendre tout l'inventaire",
                "<gray>Vend d'un coup tes ressources", "<gray>(ni outils, ni armures, ni objets rares).", "",
                Msg.BLEU + "Clic <gray>: voir le total avant de vendre"), (pl, click) -> confirmerVenteTotale(pl, () -> accueil(pl)));
        solde(p, menu, 40);
        menu.open(p);
        p.playSound(p.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.6f, 1.2f);
    }

    public void categorie(Player p, Categorie c, int page) {
        liste(p, c.nom, marche.categorie(c), page, () -> accueil(p));
    }

    private void tendances(Player p) {
        List<Article> l = marche.tendances(45);
        if (l.isEmpty()) {
            Msg.send(p, "Le marché est calme : tous les prix sont à leur valeur normale.");
            return;
        }
        liste(p, "Tendances", l, 0, () -> accueil(p));
    }

    // ------------------------------------------------------------------ liste d'objets

    private void liste(Player p, String titre, List<Article> articles, int page, Runnable retour) {
        if (!ouvert(p)) {
            return;
        }
        int pages = Math.max(1, (articles.size() + 44) / 45);
        int pg = Math.max(0, Math.min(page, pages - 1));
        Menu menu = new Menu(6, Msg.BLEU + "<bold>" + titre + (pages > 1 ? " <dark_gray>(" + (pg + 1) + "/" + pages + ")" : ""));
        for (int i = 0; i < 45; i++) {
            int idx = pg * 45 + i;
            if (idx >= articles.size()) {
                break;
            }
            Article a = articles.get(idx);
            Runnable ici = () -> liste(p, titre, articles, pg, retour);
            menu.set(i, icone(p, a, true), (pl, click) -> {
                if (click == ClickType.SHIFT_RIGHT) {
                    vendre(pl, a, Integer.MAX_VALUE, ici);
                } else {
                    quantite(pl, a, ici);
                }
            });
        }
        for (int s = 45; s < 54; s++) {
            menu.set(s, vitre(Material.BLUE_STAINED_GLASS_PANE), null);
        }
        if (pg > 0) {
            menu.set(45, Menu.icone(Material.ARROW, Msg.BLEU + "<bold>« Page précédente"), (pl, click) -> liste(pl, titre, articles, pg - 1, retour));
        }
        if (pg < pages - 1) {
            menu.set(53, Menu.icone(Material.ARROW, Msg.BLEU + "<bold>Page suivante »"), (pl, click) -> liste(pl, titre, articles, pg + 1, retour));
        }
        menu.set(48, Menu.icone(Material.DARK_OAK_DOOR, Msg.OR + "<bold>Retour"), (pl, click) -> retour.run());
        solde(p, menu, 49);
        menu.open(p);
    }

    private ItemStack icone(Player p, Article a, boolean aideClic) {
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>" + a.categorie().nom);
        lore.add("");
        lore.add(a.achetable()
                ? "<gray>Achat <dark_gray>» " + Msg.argent(marche.prixAchat(a)) + tendance(a)
                : "<gray>Achat <dark_gray>» <dark_gray><i>objet rare, vente seule</i>");
        lore.add("<gray>Vente <dark_gray>» " + prixVenteTexte(a));
        int possede = Objets.compter(p, new ItemStack(a.objet()));
        if (possede > 0) {
            lore.add("<gray>Tu en as <white>" + possede + " <dark_gray>(" + Msg.nombre(marche.totalVente(a, possede)) + " en tout)");
        }
        if (aideClic) {
            lore.add("");
            lore.add(Msg.BLEU + "Clic <gray>: acheter / vendre");
            if (possede > 0) {
                lore.add(Msg.OR + "Maj + clic droit <gray>: tout vendre");
            }
        }
        ItemStack it = new ItemStack(a.objet());
        it.editMeta(meta -> meta.lore(lore.stream().map(Msg::item).toList()));
        return it;
    }

    /** « 3 crédits », ou pour les objets bon marché qui ne valent rien à l'unité : « 9,50 crédits les 64 ». */
    private String prixVenteTexte(Article a) {
        int lot = marche.lotVente(a);
        if (lot <= 1) {
            return Msg.argent(marche.prixVente(a));
        }
        int par = Math.max(64, lot);
        return Msg.argent(marche.totalVente(a, par)) + " les " + par + " <dark_gray>(par lot)";
    }

    private String tendance(Article a) {
        long pct = Math.round((marche.facteur(a) - 1) * 100);
        if (pct >= 1) {
            return " <#3DDCB0>▲ +" + pct + " %";
        }
        if (pct <= -1) {
            return " <#FF6B6B>▼ " + pct + " %";
        }
        return "";
    }

    // ------------------------------------------------------------------ quantité

    private void quantite(Player p, Article a, Runnable retour) {
        if (!ouvert(p)) {
            return;
        }
        Menu menu = new Menu(5, Msg.BLEU + "<bold>Marché <dark_gray>» " + Msg.nom(a.objet()));
        remplirQuantite(p, menu, a, retour);
        menu.open(p);
    }

    private void remplirQuantite(Player p, Menu menu, Article a, Runnable retour) {
        cadre(menu, 5);
        Runnable maj = () -> remplirQuantite(p, menu, a, retour);
        menu.set(4, icone(p, a, false), null);

        int[] achats = {1, 8, 16, 32, 64};
        for (int i = 0; i < achats.length; i++) {
            int n = achats[i];
            int slot = 11 + i;
            if (!a.achetable()) {
                menu.set(slot, Menu.icone(Material.GRAY_STAINED_GLASS_PANE, "<gray>Vente seule",
                        "<dark_gray>Objet trop rare pour être acheté."), null);
                continue;
            }
            ItemStack b = Menu.icone(Material.LIME_STAINED_GLASS_PANE, "<#3DDCB0><bold>Acheter ×" + n,
                    "<gray>Total <dark_gray>» " + Msg.argent(marche.totalAchat(a, n)));
            b.setAmount(n);
            menu.set(slot, b, (pl, click) -> acheter(pl, a, n, maj));
        }

        int possede = Objets.compter(p, new ItemStack(a.objet()));
        int[] ventes = {1, 16, 64};
        for (int i = 0; i < ventes.length; i++) {
            int n = ventes[i];
            int slot = 29 + i;
            double gain = marche.totalVente(a, n);
            boolean possible = possede >= n && gain > 0;
            ItemStack b = Menu.icone(possible ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    "<#FF6B6B><bold>Vendre ×" + n,
                    possede < n ? "<dark_gray>Tu n'en as pas assez."
                            : gain <= 0 ? "<dark_gray>Ne vaut rien à l'unité : vends-en au moins " + marche.lotVente(a) + "."
                            : "<gray>Gain <dark_gray>» " + Msg.argent(gain));
            b.setAmount(n);
            BiConsumer<Player, ClickType> action = null;
            if (possible) {
                action = (pl, click) -> vendre(pl, a, n, maj);
            }
            menu.set(slot, b, action);
        }
        menu.set(32, vitre(Material.BLUE_STAINED_GLASS_PANE), null);
        double gainTout = possede > 0 ? marche.totalVente(a, possede) : 0;
        BiConsumer<Player, ClickType> toutVendre = null;
        if (gainTout > 0) {
            toutVendre = (pl, click) -> vendre(pl, a, Integer.MAX_VALUE, maj);
        }
        menu.set(33, Menu.icone(Material.HOPPER, "<#FF6B6B><bold>Tout vendre <gray>(" + possede + ")",
                possede <= 0 ? "<dark_gray>Tu n'en as pas."
                        : gainTout <= 0 ? "<dark_gray>Trop peu : il en faut au moins " + marche.lotVente(a) + "."
                        : "<gray>Gain <dark_gray>» " + Msg.argent(gainTout)),
                toutVendre);

        menu.set(36, Menu.icone(Material.DARK_OAK_DOOR, Msg.OR + "<bold>Retour"), (pl, click) -> retour.run());
        solde(p, menu, 40);
    }

    // ------------------------------------------------------------------ transactions

    private void acheter(Player p, Article a, int voulu, Runnable apres) {
        if (!a.achetable() || !ouvert(p)) {
            return;
        }
        ItemStack modele = new ItemStack(a.objet());
        int n = Math.min(voulu, Objets.place(p, modele));
        if (n <= 0) {
            Msg.send(p, "<red>Ton inventaire est plein.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        double total = marche.totalAchat(a, n);
        marche.mouvement(a.objet(), -n); // réservé tout de suite : deux clics rapides ne paient pas le même prix
        plugin.db().then(plugin.eco().retirer(p.getUniqueId(), total), ok -> {
            if (!ok) {
                marche.annuler(a.objet(), -n);
                Msg.send(p, "<red>Pas assez de crédits : il faut " + Msg.argent(total) + "<red>.");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            if (!p.isOnline()) {
                marche.annuler(a.objet(), -n);
                plugin.eco().crediter(p.getUniqueId(), p.getName(), total);
                return;
            }
            ItemStack lot = modele.clone();
            lot.setAmount(n);
            Objets.donner(p, lot);
            Msg.send(p, "Acheté : <white>" + n + " × " + Msg.nom(a.objet()) + "<gray> pour " + Msg.argent(total) + ".");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1.2f);
            rafraichir(p, apres);
        }, () -> {
            marche.annuler(a.objet(), -n);
            Msg.send(p, "<red>Achat impossible pour le moment (base de données).");
        });
    }

    private void vendre(Player p, Article a, int voulu, Runnable apres) {
        if (!ouvert(p)) {
            return;
        }
        ItemStack modele = new ItemStack(a.objet());
        int n = Math.min(voulu, Objets.compter(p, modele));
        if (n <= 0) {
            Msg.send(p, "<red>Tu n'as pas de " + Msg.nom(a.objet()) + "<red> neuf à vendre "
                    + "<dark_gray>(objets renommés, enchantés ou abîmés refusés)<red>.");
            return;
        }
        double total = marche.totalVente(a, n);
        if (total <= 0) {
            Msg.send(p, "<red>Ça ne vaut rien à l'unité : vends-en au moins <white>" + marche.lotVente(a) + "<red> d'un coup.");
            return;
        }
        int retires = Objets.retirer(p, modele, n);
        marche.mouvement(a.objet(), retires);
        plugin.db().then(plugin.eco().crediter(p.getUniqueId(), p.getName(), total), v -> {
            Msg.send(p, "Vendu : <white>" + retires + " × " + Msg.nom(a.objet()) + "<gray> pour " + Msg.argent(total) + ".");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
            rafraichir(p, apres);
        }, () -> {
            marche.annuler(a.objet(), retires);
            ItemStack lot = modele.clone();
            lot.setAmount(retires);
            Objets.donner(p, lot);
            Msg.send(p, "<red>Vente impossible pour le moment (base de données) : objets rendus.");
        });
    }

    /** Ce que « vendre tout » prendrait : objet → quantité. */
    private Map<Article, Integer> venteTotale(Player p) {
        Map<Article, Integer> out = new LinkedHashMap<>();
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) {
                continue;
            }
            Article a = marche.article(it.getType());
            if (a == null || a.categorie() == Categorie.OUTILS || a.categorie() == Categorie.ARMURES
                    || a.ref() > VENDRE_TOUT_MAX || !it.isSimilar(new ItemStack(it.getType()))) {
                continue;
            }
            out.merge(a, it.getAmount(), Integer::sum);
        }
        return out;
    }

    private void confirmerVenteTotale(Player p, Runnable retour) {
        if (!ouvert(p)) {
            return;
        }
        Map<Article, Integer> lot = venteTotale(p);
        if (lot.isEmpty()) {
            Msg.send(p, "Rien à vendre : ni ressources, ni nourriture, ni blocs neufs dans ton inventaire.");
            return;
        }
        double total = 0;
        int objets = 0;
        for (Map.Entry<Article, Integer> e : lot.entrySet()) {
            total += marche.venteExacte(e.getKey(), e.getValue());
            objets += e.getValue();
        }
        total = Marche.demiBas(total); // arrondi une seule fois, sur la somme
        if (total <= 0) {
            Msg.send(p, "Ce que tu as ne vaut pas encore un demi-crédit : récolte-en un peu plus.");
            return;
        }
        Menu menu = new Menu(3, Msg.BLEU + "<bold>Vendre tout ?");
        cadre(menu, 3);
        List<String> detail = new ArrayList<>();
        lot.entrySet().stream()
                .sorted((x, y) -> Double.compare(marche.totalVente(y.getKey(), y.getValue()), marche.totalVente(x.getKey(), x.getValue())))
                .limit(10)
                .forEach(e -> detail.add("<white>" + e.getValue() + " × " + Msg.nom(e.getKey().objet()) + " <dark_gray>» "
                        + Msg.argent(marche.totalVente(e.getKey(), e.getValue()))));
        if (lot.size() > 10) {
            detail.add("<dark_gray>… et " + (lot.size() - 10) + " autres");
        }
        detail.add(0, "");
        detail.add(0, "<gray>" + objets + " objets <dark_gray>» " + Msg.argent(total));
        menu.set(13, Menu.icone(Material.GOLD_INGOT, Msg.OR + "<bold>Total de la vente", detail.toArray(String[]::new)), null);
        menu.set(11, Menu.icone(Material.LIME_CONCRETE, "<#3DDCB0><bold>Vendre", "<gray>Gain <dark_gray>» " + Msg.argent(total)),
                (pl, click) -> vendreTout(pl, retour));
        menu.set(15, Menu.icone(Material.RED_CONCRETE, "<#FF6B6B><bold>Annuler"), (pl, click) -> retour.run());
        menu.open(p);
    }

    private void vendreTout(Player p, Runnable apres) {
        Map<Article, Integer> lot = venteTotale(p);
        Map<Article, Integer> pris = new LinkedHashMap<>();
        double total = 0;
        int objets = 0;
        for (Map.Entry<Article, Integer> e : lot.entrySet()) {
            Article a = e.getKey();
            double gain = marche.venteExacte(a, e.getValue());
            int r = Objets.retirer(p, new ItemStack(a.objet()), e.getValue());
            if (r <= 0) {
                continue;
            }
            if (r != e.getValue()) {
                gain = marche.venteExacte(a, r);
            }
            marche.mouvement(a.objet(), r);
            pris.put(a, r);
            total += gain;
            objets += r;
        }
        if (pris.isEmpty()) {
            p.closeInventory();
            return;
        }
        double somme = Marche.demiBas(total);
        int nb = objets;
        plugin.db().then(plugin.eco().crediter(p.getUniqueId(), p.getName(), somme), v -> {
            Msg.send(p, "Vendu : <white>" + nb + " objets<gray> (" + pris.size() + " sortes) pour " + Msg.argent(somme) + ".");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
            rafraichir(p, apres);
        }, () -> {
            pris.forEach((a, n) -> {
                marche.annuler(a.objet(), n);
                ItemStack it = new ItemStack(a.objet());
                it.setAmount(n);
                Objets.donner(p, it);
            });
            Msg.send(p, "<red>Vente impossible pour le moment (base de données) : objets rendus.");
        });
    }

    /** Ré-affiche le menu si le joueur l'a encore ouvert. */
    private static void rafraichir(Player p, Runnable apres) {
        if (apres != null && p.isOnline() && p.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) {
            apres.run();
        }
    }

    // ------------------------------------------------------------------ outils de menu

    private void solde(Player p, Menu menu, int slot) {
        menu.set(slot, Menu.icone(Material.GOLD_INGOT, Msg.OR + "<bold>Ton solde", "<gray>Chargement…"), null);
        plugin.db().then(plugin.eco().solde(p.getUniqueId()), s ->
                menu.set(slot, Menu.icone(Material.GOLD_INGOT, Msg.OR + "<bold>Ton solde", Msg.argent(s)), null), null);
    }

    private static ItemStack vitre(Material m) {
        return Menu.icone(m, " ");
    }

    /** Bordure bleue, coins dorés. */
    private static void cadre(Menu menu, int lignes) {
        int taille = lignes * 9;
        for (int s = 0; s < taille; s++) {
            int ligne = s / 9, col = s % 9;
            boolean bord = ligne == 0 || ligne == lignes - 1 || col == 0 || col == 8;
            boolean coin = (ligne == 0 || ligne == lignes - 1) && (col == 0 || col == 8);
            menu.set(s, bord ? vitre(coin ? Material.YELLOW_STAINED_GLASS_PANE : Material.BLUE_STAINED_GLASS_PANE) : null, null);
        }
    }

    // ------------------------------------------------------------------ commandes

    /** OrbitDevCore a /shop comme alias de sa boutique de cosmétiques : sur la survie, /shop = le marché. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommande(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        int espace = msg.indexOf(' ');
        String cmd = (espace < 0 ? msg : msg.substring(0, espace)).toLowerCase(Locale.ROOT);
        if (cmd.equals("/shop") || cmd.equals("/marche") || cmd.equals("/market")) {
            event.setMessage("/orbitsurvie:shop" + (espace < 0 ? "" : msg.substring(espace)));
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (cmd.getName().equals("shop") && a.length > 0 && a[0].equalsIgnoreCase("admin")) {
            admin(s, a);
            return true;
        }
        if (!(s instanceof Player p)) {
            Msg.send(s, "Commande réservée aux joueurs.");
            return true;
        }
        switch (cmd.getName()) {
            case "shop" -> {
                if (a.length == 0) {
                    accueil(p);
                    return true;
                }
                String mot = String.join(" ", a);
                for (Categorie c : Categorie.values()) {
                    if (c.name().equalsIgnoreCase(mot)) {
                        categorie(p, c, 0);
                        return true;
                    }
                }
                List<Article> trouves = marche.chercher(mot);
                if (trouves.isEmpty()) {
                    Msg.send(p, "<red>Aucun objet ne contient « " + mot.replace("<", "")
                            + " ». <gray>Cherche avec le nom anglais (diamond, oak, wool…).");
                    return true;
                }
                liste(p, "Recherche : " + mot.replace("<", ""), trouves, 0, () -> accueil(p));
            }
            case "vendre" -> {
                if (a.length > 0 && a[0].equalsIgnoreCase("tout")) {
                    Player joueur = p;
                    confirmerVenteTotale(p, () -> joueur.closeInventory());
                    return true;
                }
                ItemStack main = p.getInventory().getItemInMainHand();
                Article art = main.getType().isAir() ? null : marche.article(main.getType());
                if (art == null) {
                    Msg.send(p, "<red>Le marché ne reprend pas cet objet. <gray>Prends en main ce que tu veux vendre, ou <white>/vendre tout<gray>.");
                    return true;
                }
                if (!main.isSimilar(new ItemStack(main.getType()))) {
                    Msg.send(p, "<red>Objet renommé, enchanté ou abîmé : le marché n'en veut pas. <gray>Essaie l'hôtel des ventes (/hdv).");
                    return true;
                }
                vendre(p, art, main.getAmount(), null);
            }
            case "prix" -> {
                ItemStack main = p.getInventory().getItemInMainHand();
                Article art = main.getType().isAir() ? null : marche.article(main.getType());
                if (art == null) {
                    Msg.send(p, "<red>Cet objet n'a pas de prix au marché.");
                    return true;
                }
                Msg.send(p, Msg.nom(art.objet()) + " <dark_gray>(" + art.categorie().nom + ")");
                p.sendMessage(Msg.raw("  <gray>Achat <dark_gray>» " + (art.achetable() ? Msg.argent(marche.prixAchat(art)) + tendance(art)
                        : "<dark_gray>vente seule")));
                p.sendMessage(Msg.raw("  <gray>Vente <dark_gray>» " + prixVenteTexte(art)
                        + (main.getAmount() > 1 ? " <dark_gray>(" + main.getAmount() + " en main : "
                        + Msg.nombre(marche.totalVente(art, main.getAmount())) + ")" : "")));
                p.sendMessage(Msg.raw("  <gray>Prix normal <dark_gray>» " + Msg.argent(Math.max(0.5, Marche.demiHaut(art.ref())))));
            }
            default -> { }
        }
        return true;
    }

    private void admin(CommandSender s, String[] a) {
        if (!s.hasPermission("survie.admin")) {
            Msg.send(s, "<red>Réservé aux administrateurs.");
            return;
        }
        String sous = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (sous) {
            case "recharger" -> {
                marche.charger();
                Msg.send(s, "Marché recalculé : <white>" + marche.taille() + "<gray> objets, <white>"
                        + marche.sansPrix().size() + "<gray> sans prix.");
            }
            case "sansprix" -> {
                List<Material> l = marche.sansPrix();
                Msg.send(s, "<white>" + l.size() + "<gray> objets sans prix (à ajouter dans marche.yml → valeurs) :");
                s.sendMessage(Msg.raw("<gray>" + String.join(", ", l.stream().map(Enum::name).toList())));
            }
            case "prix" -> {
                for (int i = 2; i < a.length; i++) {
                    Material m = Material.matchMaterial(a[i]);
                    Article art = m == null ? null : marche.article(m);
                    s.sendMessage(Msg.raw(art == null ? "<red>" + a[i] + " : pas de prix"
                            : "<white>" + m.name() + " <dark_gray>[" + art.categorie().name() + "] <gray>achat "
                            + (art.achetable() ? Msg.nombre(marche.prixAchat(art)) : "—") + " / vente "
                            + Msg.nombre(marche.prixVente(art)) + " <dark_gray>(lot " + marche.lotVente(art)
                            + ", ×64 = " + Msg.nombre(marche.totalVente(art, 64)) + ", réf. " + Msg.nombre(art.ref()) + ")"));
                }
            }
            case "reset" -> {
                if (a.length > 2 && !a[2].equalsIgnoreCase("tout")) {
                    Material m = Material.matchMaterial(a[2]);
                    if (m == null) {
                        Msg.send(s, "<red>Objet inconnu.");
                        return;
                    }
                    marche.reinitialiser(m);
                    Msg.send(s, "Prix de <white>" + m.name() + "<gray> remis à la normale.");
                } else {
                    marche.reinitialiser(null);
                    Msg.send(s, "Tous les prix sont remis à la normale.");
                }
            }
            case "roulement" -> {
                marche.roulement(true);
                Msg.send(s, "Nouveau roulement des prix lancé (les cours glissent vers leur cible en ~1 h).");
            }
            default -> {
                Msg.send(s, "<white>/shop admin roulement<gray> : lance tout de suite un nouveau roulement des prix");
                Msg.send(s, "<white>/shop admin recharger<gray> : relit marche.yml et recalcule les prix");
                Msg.send(s, "<white>/shop admin sansprix<gray> : objets qui n'ont pas de prix");
                Msg.send(s, "<white>/shop admin reset [objet|tout]<gray> : remet les prix à la normale");
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equals("shop")) {
            if (a.length == 1) {
                for (Categorie c : Categorie.values()) {
                    out.add(c.name().toLowerCase(Locale.ROOT));
                }
                if (s.hasPermission("survie.admin")) {
                    out.add("admin");
                }
            } else if (a.length == 2 && a[0].equalsIgnoreCase("admin")) {
                out.addAll(List.of("roulement", "recharger", "sansprix", "reset", "prix"));
            }
        } else if (cmd.getName().equals("vendre") && a.length == 1) {
            out.add("tout");
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.startsWith(last));
        return out;
    }
}
