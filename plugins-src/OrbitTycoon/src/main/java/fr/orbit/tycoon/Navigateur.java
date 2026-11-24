package fr.orbit.tycoon;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Navigateur orbital : objet lié, clic droit = panneau des catégories du Tycoon (coffre de 6 rangées)
 * (Outils, Boutique, Quêtes, Tuto, Île, Vote, Coffres, Shop, HDV, Zones de farm).
 * Chaque catégorie ouvre un sous-menu : téléportation, ouverture d'un menu, commande ou explication.
 * Avec le pack chargé, chaque case affiche une icône HD dessinée par tools/NavIcones.java (orbittycoon:nav_<id>).
 */
final class Navigateur implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int LIGNES = 6;
    /** Cases des 10 catégories du panneau principal (rangées 2 et 4, 5 par rangée). */
    private static final int[] SLOTS_CATEGORIES = {11, 12, 13, 14, 15, 21, 23, 29, 30, 31, 32, 33};
    private static final int SLOT_ENTETE = 4, SLOT_RETOUR = 48, SLOT_FERMER = 50;
    private static final String CLIC_TP = "<#62CFF4>➜ Clic pour t'y téléporter";
    private static final String CLIC_OUVRIR = "<#62CFF4>➜ Clic pour ouvrir";
    private static final String CLIC = "<#62CFF4>➜ Clic";

    private final OrbitTycoon plugin;
    private final Liens liens;
    private final NamespacedKey cle;
    private IlesJoueurs iles;

    // Menu à grandes cartes (pack chargé) : géométrie identique à tools/NavIcones.java.
    private static final Key NAV = Key.key("orbittycoon", "nav");
    private static final double PAS_X = 40.5;
    /** Index des glyphes d'icônes (ordre de nav-icones.txt, écrit par tools/NavIcones.java). */
    private final Map<String, Integer> glyphes = new HashMap<>();
    /** Nom court écrit au-dessus de chaque carte (le nom complet est dans l'infobulle). */
    private static final Map<String, String> NOMS = Map.ofEntries(
            Map.entry("cat_outils", "Outils"), Map.entry("cat_boutique", "Boutique"), Map.entry("cat_quetes", "Quêtes"),
            Map.entry("cat_tuto", "Tuto"), Map.entry("cat_ile", "Île"), Map.entry("cat_vote", "Vote"),
            Map.entry("cat_coffres", "Coffres"), Map.entry("cat_shop", "Shop"), Map.entry("cat_hdv", "HDV"),
            Map.entry("cat_zones", "Zones"), Map.entry("info", "Infos"), Map.entry("retour", "Retour"),
            Map.entry("outil_interface", "Outil"), Map.entry("outil_ressources", "Récolte"), Map.entry("outil_atelier", "Atelier"),
            Map.entry("outil_classement", "Podium"), Map.entry("boutique_ouvrir", "Boutique"), Map.entry("boutique_pnj", "PNJ"),
            Map.entry("quetes_mes", "Quêtes"), Map.entry("quetes_pavillon", "Pavillon"), Map.entry("tuto_consigne", "Étape"),
            Map.entry("tuto_recommencer", "Refaire"), Map.entry("tuto_passer", "Passer"), Map.entry("tuto_lire", "Guide"),
            Map.entry("tuto_livre", "Livre"), Map.entry("ile_creer", "Créer"), Map.entry("ile_mon", "Mon île"),
            Map.entry("ile_niveau", "Niveau"), Map.entry("ile_agrandir", "Taille"), Map.entry("ile_inviter", "Inviter"),
            Map.entry("ile_renommer", "Nom"), Map.entry("ile_rejoindre", "Entrer"), Map.entry("ile_visiter", "Visiter"),
            Map.entry("vote_voter", "Voter"), Map.entry("vote_party", "Party"), Map.entry("coffres_aller", "Coffres"),
            Map.entry("coffres_liste", "Clés"), Map.entry("shop_generateurs", "Usines"), Map.entry("shop_blocs", "Blocs"),
            Map.entry("shop_vendre", "Vendre"), Map.entry("shop_marche", "Marché"), Map.entry("hdv_ouvrir", "HDV"),
            Map.entry("hdv_ventes", "Ventes"), Map.entry("hdv_vendre", "Vendre"), Map.entry("zone_peche", "Pêche"),
            Map.entry("zone_bucheron", "Bois"), Map.entry("zone_agriculture", "Champs"), Map.entry("zone_mobzone", "Mobs"),
            Map.entry("zone_mine", "Mine"), Map.entry("zone_base", "Spawn"), Map.entry("zone_observatoire", "Astres"),
            Map.entry("cat_equipements", "Équipement"), Map.entry("equip_cratere", "Cratère"), Map.entry("equip_sylvanova", "Sylvanova"),
            Map.entry("equip_moisson", "Moisson"), Map.entry("equip_maree", "Marée"), Map.entry("equip_supernova", "Supernova"));

    Navigateur(OrbitTycoon plugin, Liens liens) {
        this.plugin = plugin;
        this.liens = liens;
        this.cle = new NamespacedKey(plugin, "navigateur");
        try (var in = plugin.getResource("nav-icones.txt")) {
            if (in != null) {
                String[] ids = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R");
                for (int i = 0; i < ids.length; i++) if (!ids[i].isBlank()) glyphes.put(ids[i].trim(), i);
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("nav-icones.txt illisible : " + e);
        }
    }

    void iles(IlesJoueurs iles) { this.iles = iles; }

    /** Gardé pour compatibilité : l'habillage passe maintenant par {@link Habillage}. */
    void pack(Pack pack) { }

    static Component texte(String mm) {
        return MM.deserialize(mm).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    // ------------------------------------------------------------------ objet

    ItemStack creer() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(texte("<gradient:#62CFF4:#E0B864><bold>Navigateur orbital</bold></gradient> <gray>(clic droit)"));
        meta.lore(List.of(
                texte("<gray>Tout le Tycoon à portée de main :"),
                texte("<gray>outils, boutique, quêtes, tuto, île, vote,"),
                texte("<gray>coffres, pets, shop, HDV et zones de farm."),
                Component.empty(),
                texte("<#E0B864>✦ Lié à toi : impossible à jeter ou échanger.")));
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(cle, PersistentDataType.BYTE, (byte) 1);
        liens.marquer(meta);
        item.setItemMeta(meta);
        return item;
    }

    boolean est(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(cle, PersistentDataType.BYTE);
    }

    void donner(Player p) {
        PlayerInventory inv = p.getInventory();
        int slot = Math.max(0, Math.min(8, plugin.getConfig().getInt("navigateur.slot", 8)));
        for (int i = 0; i < inv.getSize(); i++) if (est(inv.getItem(i))) inv.setItem(i, null);
        if (est(p.getItemOnCursor())) p.setItemOnCursor(null);
        ItemStack occupant = inv.getItem(slot);
        if (occupant != null && !occupant.getType().isAir()) {
            inv.setItem(slot, null);
            for (ItemStack reste : inv.addItem(occupant).values()) p.getWorld().dropItemNaturally(p.getLocation(), reste);
        }
        inv.setItem(slot, creer());
    }

    // ------------------------------------------------------------------ menus

    /** Un menu du navigateur : chaque case cliquable a son action. */
    private static final class Menu implements InventoryHolder {
        private Inventory inv;
        private boolean pack;
        private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
        @Override public Inventory getInventory() { return inv; }
    }

    /**
     * Une entrée de menu : icône dessinée du pack (nav_&lt;modele&gt;), objet de secours sans le pack, nom,
     * lignes d'explication, action au clic (null = simple explication).
     */
    private record Entree(String modele, Material icone, String nom, List<String> lore, Consumer<Player> action, boolean brille) {
        Entree(String modele, Material icone, String nom, List<String> lore, Consumer<Player> action) {
            this(modele, icone, nom, lore, action, false);
        }
    }

    /** Les 11 catégories, dans l'ordre du panneau. */
    private enum Categorie {
        OUTILS("outils", Material.DIAMOND_PICKAXE, "<#62CFF4><bold>Outils</bold>", "Ton Outil orbital, ses ressources,", "l'Atelier et le classement prestige."),
        BOUTIQUE("boutique", Material.BEACON, "<#E0B864><bold>Boutique</bold>", "Cosmétiques Orbit (aucun pay-to-win)", "et le PNJ Boutique du spawn."),
        QUETES("quetes", Material.WRITABLE_BOOK, "<#7CFC9A><bold>Quêtes</bold>", "Tes quêtes de prestige et le", "pavillon de l'Aventurier."),
        TUTO("tuto", Material.BOOK, "<#C77DFF><bold>Tuto</bold>", "Revoir, recommencer ou passer le tuto,", "et le livre-guide."),
        ILE("ile", Material.GRASS_BLOCK, "<#7CFC9A><bold>Île</bold> <gray>(/is)", "Créer, rejoindre, agrandir", "et gérer ton île."),
        VOTE("vote", Material.SUNFLOWER, "<#E0B864><bold>Vote</bold>", "Vote pour Orbit MC, gagne des", "récompenses et lance la Vote Party."),
        COFFRES("coffres", Material.ENDER_CHEST, "<#C77DFF><bold>Coffres</bold>", "Les coffres orbitaux du spawn", "et comment les ouvrir."),
        PETS("pets", Material.LEAD, "<#FF8FC7><bold>Pets</bold>", "Ta collection de pets : équipe-en", "jusqu'à 3 pour booster tes outils."),
        EQUIPEMENTS("equipements", Material.LEATHER_CHESTPLATE, "<#62CFF4><bold>Équipements</bold>", "Les 5 sets de farm, un par métier :", "+XP, +ressources, +monnaie."),
        SHOP("shop", Material.EMERALD, "<#7CFC9A><bold>Shop</bold>", "Générateurs, blocs de déco,", "vente de ressources."),
        HDV("hdv", Material.GOLD_INGOT, "<#E0B864><bold>HDV</bold>", "Hôtel des ventes : achète et vends", "entre joueurs."),
        ZONES("zones", Material.IRON_HOE, "<#3FA9F5><bold>Zones de farm</bold>", "Pêche, Bûcheron, Agriculture,", "Mobzone, Mine et le spawn.");

        final String id;
        final Material icone;
        final String nom;
        final String[] description;
        Categorie(String id, Material icone, String nom, String... description) {
            this.id = id; this.icone = icone; this.nom = nom; this.description = description;
        }
        /** Nom sans balises, pour le titre du sous-menu. */
        String brut() { return MM.stripTags(nom).replace(" (/is)", ""); }
    }

    private Menu nouveauMenu(Player p, String titre) {
        Menu menu = new Menu();
        menu.pack = Habillage.actif(plugin, p);
        menu.inv = Bukkit.createInventory(menu, LIGNES * 9, Habillage.titre(plugin, p, LIGNES, titre));
        return menu;
    }

    /** Panneau principal : les 11 catégories. */
    void ouvrir(Player p) {
        plugin.tuto().fait(p, Tuto.Etape.NAVIGATEUR);
        Categorie[] cats = Categorie.values();
        List<Entree> entrees = new ArrayList<>();
        for (Categorie c : cats) {
            List<String> lore = new ArrayList<>(List.of("<gray>" + c.description[0], "<gray>" + c.description[1]));
            lore.add("");
            lore.add(CLIC_OUVRIR);
            entrees.add(new Entree("cat_" + c.id, c.icone, c.nom, lore, j -> ouvrir(j, c)));
        }
        if (Habillage.actif(plugin, p) && !glyphes.isEmpty()) {
            ouvrirCartes(p, List.of(entrees.subList(0, 4), entrees.subList(4, 8), entrees.subList(8, entrees.size())));
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f);
            return;
        }
        Menu menu = nouveauMenu(p, "Navigateur orbital");
        for (int i = 0; i < entrees.size(); i++) poser(menu, SLOTS_CATEGORIES[i], entrees.get(i));
        poser(menu, 49, fermer());
        Habillage.fond(plugin, p, menu.inv);
        p.openInventory(menu.inv);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f);
    }

    /** Sous-menu d'une catégorie : en-tête, entrées centrées, retour et fermer. */
    private void ouvrir(Player p, Categorie c) {
        if (c == Categorie.PETS) { plugin.pets().ouvrirMenu(p, 0); p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f); return; }
        if (Habillage.actif(plugin, p) && !glyphes.isEmpty()) {
            List<Entree> e = entrees(p, c);
            Entree retour = new Entree("retour", Material.ARROW, "<#E0B864><bold>← Retour</bold>",
                    List.of("<gray>Panneau des catégories"), this::ouvrir);
            int n = e.size(), haut = (n + 1) / 2;
            List<List<Entree>> bandes = n <= 4 ? List.of(List.of(), e, List.of(retour))
                    : List.of(e.subList(0, haut), e.subList(haut, n), List.of(retour));
            ouvrirCartes(p, bandes);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
            return;
        }
        Menu menu = nouveauMenu(p, c.brut());
        poser(menu, SLOT_ENTETE, new Entree("cat_" + c.id, c.icone, c.nom,
                List.of("<gray>" + c.description[0], "<gray>" + c.description[1]), null, true));
        List<Entree> entrees = entrees(p, c);
        int[] slots = cases(entrees.size());
        for (int i = 0; i < entrees.size(); i++) poser(menu, slots[i], entrees.get(i));
        poser(menu, SLOT_RETOUR, new Entree("retour", Material.ARROW, "<#E0B864><bold>← Retour</bold>",
                List.of("<gray>Panneau des catégories"), this::ouvrir));
        poser(menu, SLOT_FERMER, fermer());
        Habillage.fond(plugin, p, menu.inv);
        p.openInventory(menu.inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
    }

    // ------------------------------------------------------------------ menu à grandes cartes

    /**
     * Menu à grandes cartes (pack chargé) : trois bandes de cartes (nom au-dessus, icône 32 px) dessinées dans le
     * titre du coffre avec les polices orbittycoon:nav et nav_t0..2 ; sous chaque carte, des objets invisibles
     * (2 rangées × 2 ou 3 colonnes) portent l'infobulle et le clic.
     */
    private void ouvrirCartes(Player p, List<List<Entree>> bandes) {
        Menu menu = new Menu();
        menu.pack = true;
        var titre = Component.text();
        int[] x = {8 - 18 + 197};
        titre.append(Component.text(espaces(-18) + "", NamedTextColor.WHITE).font(NAV));
        double[][] centres = new double[3][];
        for (int b = 0; b < bandes.size() && b < 3; b++) {
            List<Entree> l = bandes.get(b);
            centres[b] = new double[l.size()];
            for (int k = 0; k < l.size(); k++) {
                double xc = 88 + (k - (l.size() - 1) / 2.0) * PAS_X;
                centres[b][k] = xc;
                Entree e = l.get(k);
                int cadre = (int) Math.round(xc) - 19;
                StringBuilder s = new StringBuilder(espaces(cadre - x[0])).append((char) (0xE010 + b));
                x[0] = cadre + 39;
                Integer g = glyphes.get(e.modele());
                if (g != null) {
                    s.append(espaces(cadre + 3 - x[0])).append((char) (0xE100 + 256 * b + g));
                    x[0] = cadre + 3 + 33;
                }
                String nom = NOMS.getOrDefault(e.modele(), MM.stripTags(e.nom()));
                int w = Habillage.largeur(nom, false);
                int debut = (int) Math.round(xc - (w - 1) / 2.0);
                s.append(espaces(debut - x[0]));
                x[0] = debut + w;
                titre.append(Component.text(s.toString(), NamedTextColor.WHITE).font(NAV));
                titre.append(Component.text(nom, TextColor.color(0xF2D48A)).font(Key.key("orbittycoon", "nav_t" + b)));
            }
        }
        menu.inv = Bukkit.createInventory(menu, LIGNES * 9, titre.build());
        // Cases cliquables : chaque colonne va à la carte la plus proche de sa bande (à 22 px au plus).
        for (int b = 0; b < 3 && b < bandes.size(); b++) {
            if (centres[b].length == 0) continue;
            for (int col = 0; col < 9; col++) {
                double cx = 16 + 18 * col;
                int best = -1;
                double d = 22.01;
                for (int k = 0; k < centres[b].length; k++)
                    if (Math.abs(centres[b][k] - cx) < d) { d = Math.abs(centres[b][k] - cx); best = k; }
                if (best < 0) continue;
                Entree e = bandes.get(b).get(best);
                for (int r = 2 * b; r <= 2 * b + 1; r++) {
                    int slot = r * 9 + col;
                    poser(menu, slot, e);
                    ItemStack it = menu.inv.getItem(slot);
                    it.editMeta(m -> m.setItemModel(new NamespacedKey("orbittycoon", "nav_vide")));
                }
            }
        }
        p.openInventory(menu.inv);
    }

    /** Suite d'espaces de la police nav : U+F810.. recule de 1, 2, 4… 128 px ; U+F820.. avance d'autant. */
    private static String espaces(int px) {
        StringBuilder b = new StringBuilder();
        int base = px < 0 ? 0xF810 : 0xF820;
        px = Math.abs(px);
        for (int k = 7; k >= 0; k--) while (px >= (1 << k)) { b.append((char) (base + k)); px -= 1 << k; }
        return b.toString();
    }

    private static Entree fermer() {
        return new Entree("fermer", Material.BARRIER, "<#FF4D4D><bold>Fermer</bold>", List.of(), Player::closeInventory);
    }

    /**
     * Cases des entrées d'un sous-menu (au centre du coffre) : jusqu'à 4, espacées d'une case sur la rangée 3 ;
     * de 5 à 7, côte à côte sur la rangée 3 ; au-delà, deux rangées (3 et 4).
     */
    private static int[] cases(int n) {
        int[] r = new int[n];
        if (n <= 4) {
            for (int i = 0; i < n; i++) r[i] = 22 - (n - 1) + 2 * i;
        } else if (n <= 7) {
            for (int i = 0; i < n; i++) r[i] = 22 - n / 2 + i + (n % 2 == 0 && i >= n / 2 ? 1 : 0);
        } else {
            int haut = (n + 1) / 2;
            int[] a = cases(haut), b = cases(n - haut);
            for (int i = 0; i < haut; i++) r[i] = a[i];
            for (int i = 0; i < n - haut; i++) r[haut + i] = b[i] + 9;
        }
        return r;
    }

    private void poser(Menu menu, int slot, Entree e) {
        ItemStack it = new ItemStack(e.icone());
        ItemMeta meta = it.getItemMeta();
        meta.displayName(texte(e.nom()));
        List<Component> lore = new ArrayList<>();
        for (String l : e.lore()) lore.add(l.isEmpty() ? Component.empty() : texte(l));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        if (menu.pack && e.modele() != null) meta.setItemModel(new NamespacedKey("orbittycoon", "nav_" + e.modele()));
        if (e.brille()) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        menu.inv.setItem(slot, it);
        if (e.action() != null) menu.actions.put(slot, e.action());
    }

    // ------------------------------------------------------------------ contenu des catégories

    private List<Entree> entrees(Player p, Categorie c) {
        return switch (c) {
            case OUTILS -> List.of(
                    new Entree("outil_interface", Material.NETHERITE_PICKAXE, "<#62CFF4><bold>Interface de l'outil</bold>",
                            List.of("<gray>Choisis ton outil (pioche, hache,", "<gray>houe, canne à pêche, épée).",
                                    "<dark_gray>Raccourci : Shift + clic droit", "", CLIC_OUVRIR),
                            j -> { j.closeInventory(); plugin.outil().ouvrir(j); }),
                    new Entree("outil_ressources", Material.AMETHYST_SHARD, "<#C77DFF><bold>Ressources de l'outil</bold>",
                            List.of("<gray>Les ressources de ton outil actuel,", "<gray>débloquées par les quêtes de prestige.", "", CLIC_OUVRIR),
                            j -> { j.closeInventory(); var m = plugin.outil().modeActuel(j);
                                plugin.ressources().ouvrir(j, m == null ? Outil.Mode.PIOCHE : m); }),
                    new Entree("outil_atelier", Material.ANVIL, "<#E0B864><bold>Atelier des outils</bold>",
                            List.of("<gray>Le pavillon du <#E0B864>Forgeron</#E0B864>.", "", CLIC_TP),
                            j -> tpPnj(j, "forgeron")),
                    new Entree("outil_classement", Material.GOLDEN_HELMET, "<#E0B864><bold>Classement prestige</bold>",
                            List.of("<gray>Le podium des meilleurs joueurs", "<gray>par outil. <dark_gray>(/classements <outil>)", "", CLIC_TP),
                            j -> tp(j, plugin.classement().pointTp())),
                    info("Comment ça marche ?",
                            "<gray>Un seul objet : pioche, hache, houe,",
                            "<gray>canne à pêche et épée à la fois.",
                            "<gray>Chaque récolte te paie directement",
                            "<gray>et fait monter le niveau de l'outil.",
                            "<#E0B864>Shift + F<gray> : outil suivant."));
            case BOUTIQUE -> List.of(
                    new Entree("boutique_ouvrir", Material.BEACON, "<#E0B864><bold>Ouvrir la boutique</bold>",
                            List.of("<gray>Les rayons de la boutique Orbit.", "<dark_gray>(/boutique)", "", CLIC_OUVRIR),
                            j -> { j.closeInventory(); plugin.boutique().ouvrir(j); }),
                    new Entree("boutique_pnj", Material.ARMOR_STAND, "<#62CFF4><bold>PNJ Boutique</bold>",
                            List.of("<gray>Un PNJ astronaute t'attend sur", "<gray>chaque avenue du spawn.", "", CLIC_TP),
                            j -> tp(j, plugin.boutique().pointTp())),
                    info("La boutique, c'est quoi ?",
                            "<gray>Uniquement des <#E0B864>cosmétiques</#E0B864>",
                            "<gray>(payés en <#C77DFF>Plasma</#C77DFF>).",
                            "<gray>Aucun avantage en jeu :",
                            "<#7CFC9A>zéro pay-to-win."));
            case QUETES -> List.of(
                    new Entree("quetes_mes", Material.WRITABLE_BOOK, "<#7CFC9A><bold>Mes quêtes</bold>",
                            List.of("<gray>Tes quêtes de prestige en cours", "<gray>et leurs récompenses.", "", CLIC_OUVRIR),
                            j -> { j.closeInventory(); plugin.quetes().ouvrir(j); }),
                    new Entree("quetes_pavillon", Material.COMPASS, "<#62CFF4><bold>Pavillon des quêtes</bold>",
                            List.of("<gray>Devant l'<#E0B864>Aventurier</#E0B864>. <dark_gray>(/quetes)", "", CLIC_TP),
                            j -> tpPnj(j, "quetes")),
                    info("Comment ça marche ?",
                            "<gray>Chaque outil a ses quêtes.",
                            "<gray>Les finir fait monter son prestige",
                            "<gray>et <#7CFC9A>débloque de nouvelles ressources</#7CFC9A>."));
            case TUTO -> List.of(
                    new Entree("tuto_consigne", Material.LECTERN, "<#C77DFF><bold>Revoir la consigne</bold>",
                            List.of("<gray>Où en es-tu dans le tuto ?", "<dark_gray>(/tuto)", "", CLIC),
                            j -> cmd(j, "tuto")),
                    new Entree("tuto_recommencer", Material.RECOVERY_COMPASS, "<#62CFF4><bold>Recommencer le tuto</bold>",
                            List.of("<gray>Refaire les étapes depuis le début.", "<dark_gray>(pas de 2e récompense)", "", CLIC),
                            j -> cmd(j, "tuto recommencer")),
                    new Entree("tuto_passer", Material.BARRIER, "<#C77DFF><bold>Passer le tuto</bold>",
                            List.of("<gray>Tu connais déjà ? Saute-le.", "", CLIC),
                            j -> cmd(j, "tuto passer")),
                    new Entree("tuto_lire", Material.WRITTEN_BOOK, "<#E0B864><bold>Lire le livre-guide</bold>",
                            List.of("<gray>Toutes les explications d'Orbit Tycoon.", "", CLIC_OUVRIR),
                            j -> cmd(j, "guide lire")),
                    new Entree("tuto_livre", Material.BOOK, "<#E0B864><bold>Reprendre le livre-guide</bold>",
                            List.of("<gray>Remet le guide dans ton inventaire.", "<dark_gray>(/guide)", "", CLIC),
                            j -> cmd(j, "guide")));
            case ILE -> {
                IlesJoueurs.Ile ile = iles == null ? null : iles.ileDe(p.getUniqueId());
                List<Entree> l = new ArrayList<>();
                l.add(entreeIle(p, ile));
                if (ile != null) {
                    l.add(new Entree("ile_niveau", Material.EXPERIENCE_BOTTLE, "<#E0B864><bold>Niveau de l'île</bold>",
                            List.of("<gray>Niveau, taille et membres.", "<dark_gray>(/is level)", "", CLIC),
                            j -> cmd(j, "is level")));
                    l.add(new Entree("ile_agrandir", Material.SCAFFOLDING, "<#7CFC9A><bold>Agrandir l'île</bold>",
                            List.of("<gray>Passe au niveau suivant", "<gray>pour une zone plus grande.", "<dark_gray>(/is upgrade)", "", CLIC),
                            j -> cmd(j, "is upgrade")));
                    l.add(suggerer("ile_inviter", Material.PLAYER_HEAD, "Inviter un joueur", "/is invite ", "<gray>Ajoute un ami à ton île."));
                    l.add(suggerer("ile_renommer", Material.NAME_TAG, "Renommer l'île", "/is nom ", "<gray>Donne un nom à ton île."));
                } else {
                    l.add(suggerer("ile_rejoindre", Material.OAK_DOOR, "Rejoindre une île", "/is join ", "<gray>Accepte l'invitation d'un ami."));
                }
                l.add(suggerer("ile_visiter", Material.SPYGLASS, "Visiter une île", "/is visit ", "<gray>Va voir l'île d'un autre joueur."));
                l.add(info("Les îles",
                        "<gray>Ton île perso pour poser tes",
                        "<gray>générateurs et construire.",
                        "<gray>Joue seul ou en coop (/is invite).",
                        "<dark_gray>Toutes les commandes : /is aide"));
                yield l;
            }
            case VOTE -> List.of(
                    new Entree("vote_voter", Material.SUNFLOWER, "<#E0B864><bold>Voter</bold>",
                            List.of("<gray>Ouvre les liens de vote.", "<dark_gray>(/vote)", "", CLIC),
                            j -> cmd(j, "vote")),
                    new Entree("vote_party", Material.FIREWORK_ROCKET, "<#C77DFF><bold>Vote Party</bold>",
                            List.of("<gray>Votes du serveur : <white>" + plugin.voteParty().votes() + "<gray>/<white>" + plugin.voteParty().objectif(),
                                    "<gray>À l'objectif, <#E0B864>tout le monde</#E0B864>", "<gray>reçoit des récompenses !"),
                            null),
                    info("Pourquoi voter ?",
                            "<gray>Chaque vote fait connaître Orbit MC",
                            "<gray>et te rapporte des récompenses.",
                            "<gray>Tu peux voter chaque jour."));
            case COFFRES -> {
                List<String> noms = new ArrayList<>(List.of("<gray>Les coffres du spawn :"));
                for (int n = 0; n < Coffres.NB; n++) noms.add("<dark_gray> • <white>" + MM.escapeTags(plugin.coffres().nom(n)));
                yield List.of(
                        new Entree("coffres_aller", Material.ENDER_CHEST, "<#C77DFF><bold>Aller aux coffres</bold>",
                                List.of("<gray>L'espace des coffres orbitaux.", "<dark_gray>(/coffres)", "", CLIC_TP),
                                j -> tp(j, plugin.coffres().pointTp())),
                        new Entree("coffres_liste", Material.TRIPWIRE_HOOK, "<#E0B864><bold>Les coffres</bold>", noms, null),
                        info("Comment les ouvrir ?",
                                "<#E0B864>Clic gauche<gray> sur un coffre :",
                                "<gray>voir ce qu'il contient.",
                                "<#E0B864>Clic droit<gray> avec la bonne <#E0B864>clé</#E0B864>",
                                "<gray>en main : l'ouvrir.",
                                "<gray>Les clés : votes, quêtes, boutique..."));
            }
            case SHOP -> List.of(
                    new Entree("shop_generateurs", Material.OBSERVER, "<#7CFC9A><bold>Générateurs</bold>",
                            List.of("<gray>Acheter, débloquer et vendre", "<gray>des générateurs. <dark_gray>(/gen)", "", CLIC_OUVRIR),
                            j -> { j.closeInventory(); plugin.generateurs().ouvrir(j); }),
                    new Entree("shop_blocs", Material.BRICKS, "<#E0B864><bold>Magasin de blocs</bold>",
                            List.of("<gray>Blocs de déco pour ton île.", "<dark_gray>(/blocs)", "", CLIC_OUVRIR),
                            j -> { j.closeInventory(); plugin.magasinBlocs().ouvrir(j); }),
                    new Entree("shop_vendre", Material.HOPPER, "<#62CFF4><bold>Tout vendre</bold>",
                            List.of("<gray>Vend toutes les ressources de", "<gray>générateur de ton inventaire.", "<dark_gray>(/vendre tout)", "", CLIC),
                            j -> cmd(j, "vendre tout")),
                    new Entree("shop_marche", Material.EMERALD_BLOCK, "<#E0B864><bold>Marché orbital</bold>",
                            List.of("<gray>Le pavillon du <#E0B864>Marchand</#E0B864>", "<gray>et du <#E0B864>Bâtisseur</#E0B864>.", "", CLIC_TP),
                            j -> tpPnj(j, "marchand")));
            case HDV -> List.of(
                    new Entree("hdv_ouvrir", Material.GOLD_INGOT, "<#E0B864><bold>Ouvrir l'HDV</bold>",
                            List.of("<gray>Les objets mis en vente", "<gray>par les joueurs. <dark_gray>(/hdv)", "", CLIC_OUVRIR),
                            j -> { j.closeInventory(); plugin.hdv().ouvrir(j, 0); }),
                    new Entree("hdv_ventes", Material.CHEST_MINECART, "<#62CFF4><bold>Mes ventes</bold>",
                            List.of("<gray>Tes objets en vente.", "<dark_gray>(/hdv ventes)", "", CLIC_OUVRIR),
                            j -> cmd(j, "hdv ventes")),
                    suggerer("hdv_vendre", Material.GOLD_NUGGET, "Vendre l'objet en main", "/hdv vendre ", "<gray>Tape le prix en Crédits orbitaux."),
                    new Entree("shop_marche", Material.EMERALD_BLOCK, "<#E0B864><bold>Le Marchand</bold>",
                            List.of("<gray>Le Marché orbital du spawn.", "", CLIC_TP),
                            j -> tpPnj(j, "marchand")),
                    info("Comment ça marche ?",
                            "<gray>Prends un objet en main et tape",
                            "<#E0B864>/hdv vendre <prix><gray>.",
                            "<gray>Les objets liés (Outil, Navigateur)",
                            "<gray>ne peuvent pas être vendus.",
                            "<dark_gray>Plus d'aide : /hdv aide"));
            case PETS -> List.of();
            case EQUIPEMENTS -> {
                List<Entree> l = new ArrayList<>();
                for (Equipements.Set s : Equipements.Set.values())
                    l.add(new Entree("equip_" + s.name().toLowerCase(java.util.Locale.ROOT), Material.LEATHER_CHESTPLATE,
                            "<" + s.couleur + "><bold>Set " + s.nom + "</bold>", plugin.equipements().presentation(s), null));
                l.add(new Entree("coffres_aller", Material.ENDER_CHEST, "<#E0B864><bold>Coffre Farm</bold>",
                        List.of("<gray>Les équipements s'obtiennent", "<gray>seulement dans le <#E0B864>coffre Farm</#E0B864> du spawn.",
                                "<gray>Sa clé tombe parfois en farmant", "<gray>avec l'Outil orbital en main.", "", CLIC_TP),
                        j -> tp(j, plugin.coffres().pointTp())));
                yield l;
            }
            case ZONES -> {
                Zone ici = IlesJoueurs.estMonde(p.getWorld()) ? null : Zone.at(p.getLocation());
                List<Entree> l = new ArrayList<>();
                for (Zone z : Zone.farms()) l.add(entreeZone(z, z == ici));
                l.add(entreeZone(Zone.BASE, ici == Zone.BASE));
                l.add(new Entree("zone_observatoire", Material.SPYGLASS, "<#C77DFF><bold>Observatoire</bold>",
                        List.of("<gray>Les animaux spatiaux du spawn.", "", CLIC_TP),
                        j -> tpPnj(j, "observatoire")));
                yield l;
            }
        };
    }

    private Entree entreeZone(Zone z, boolean ici) {
        List<String> lore = new ArrayList<>(z.description);
        lore.add("");
        lore.add(ici ? "<#E0B864>● Tu es ici" : CLIC_TP);
        return new Entree("zone_" + z.name().toLowerCase(), z.icone, z.nom, lore, j -> { j.closeInventory(); teleporter(j, z); }, ici);
    }

    private Entree entreeIle(Player p, IlesJoueurs.Ile ile) {
        if (ile == null) return new Entree("ile_creer", Material.GRASS_BLOCK, "<#7CFC9A><bold>Créer mon île</bold>",
                List.of("<gray>Tu n'as pas encore d'île.", "", "<#62CFF4>➜ Clic pour créer ton île"),
                j -> cmd(j, "is create"));
        boolean ici = IlesJoueurs.estMonde(p.getWorld()) && iles.ileA(p.getX(), p.getZ()) == ile;
        int t = iles.taille(ile);
        return new Entree("ile_mon", Material.GRASS_BLOCK, "<#7CFC9A><bold>Mon île</bold>",
                List.of("<white>" + MM.escapeTags(ile.nom),
                        "<gray>Niveau <#E0B864>" + (ile.palier + 1) + "<gray> — zone <#E0B864>" + t + " × " + t,
                        "<gray>Membres : <white>" + ile.membres.size(), "",
                        ici ? "<#E0B864>● Tu es ici" : CLIC_TP),
                j -> { j.closeInventory(); iles.home(j); }, ici);
    }

    /** Entrée d'explication seule (pas d'action). */
    private static Entree info(String titre, String... lignes) {
        return new Entree("info", Material.OAK_SIGN, "<#62CFF4><bold>" + titre + "</bold>", List.of(lignes), null);
    }

    /** Entrée qui ferme le menu et propose une commande à compléter dans le chat. */
    private static Entree suggerer(String modele, Material icone, String titre, String commande, String explication) {
        return new Entree(modele, icone, "<#62CFF4><bold>" + titre + "</bold>",
                List.of(explication, "<dark_gray>(" + commande.trim() + " <joueur>)", "", "<#62CFF4>➜ Clic : la commande s'écrit dans le chat"),
                j -> {
                    j.closeInventory();
                    OrbitTycoon.msg(j, "<click:suggest_command:'" + commande + "'><#E0B864><u>Clique ici</u></#E0B864></click><gray> puis complète : <white>"
                            + MM.escapeTags(commande) + "...");
                });
    }

    private void cmd(Player p, String commande) {
        p.closeInventory();
        p.performCommand(commande);
    }

    private void tp(Player p, Location dest) {
        p.closeInventory();
        if (dest == null) { OrbitTycoon.msg(p, "Destination introuvable."); return; }
        teleporter(p, Zone.BASE, dest);
    }

    private void tpPnj(Player p, String lieu) {
        tp(p, plugin.pnjs().pointTp(lieu));
    }

    // ------------------------------------------------------------------ téléportation

    void teleporter(Player p, Zone z) { teleporter(p, z, z.arrivee(plugin.monde())); }

    void teleporter(Player p, Zone z, Location dest) {
        p.teleportAsync(dest).thenAccept(ok -> {
            if (!ok) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.25f, 1.6f);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f);
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
                p.showTitle(Title.title(MM.deserialize(z.nom), MM.deserialize(z.description.get(0)),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(400))));
            });
        });
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.HIGH)
    public void clicDroit(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!est(e.getItem())) return;
        e.setCancelled(true);
        ouvrir(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void menu(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView().getTopInventory().getHolder() instanceof Menu menu) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;
            Consumer<Player> action = menu.actions.get(e.getRawSlot());
            if (action == null) return;
            // Une tick plus tard : on ne ferme / rouvre pas un inventaire pendant son propre événement de clic.
            Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) action.accept(p); });
            return;
        }
        // Clic sur le navigateur dans son propre inventaire : ouvre le menu.
        if (est(e.getCurrentItem()) && e.getView().getTopInventory().getType() == InventoryType.CRAFTING)
            Bukkit.getScheduler().runTask(plugin, () -> ouvrir(p));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void glisser(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
