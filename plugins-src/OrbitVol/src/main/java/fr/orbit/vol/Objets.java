package fr.orbit.vol;

import fr.orbit.vol.Ameliorations.Amelioration;
import fr.orbit.vol.Catalogue.Mutation;
import fr.orbit.vol.Catalogue.Orbito;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Objets du mode et tous les menus : menu principal (l'étoile), objets, améliorations de base, quêtes du jour,
 * Orbidex, classement, et la fiche d'un Orbito (améliorer / revendre). Tout se paie en crédits du jeu.
 * Avec le pack HD, chaque objet porte son modèle orbitvol:&lt;modele&gt; (sinon l'objet du jeu s'affiche).
 */
public final class Objets implements Listener {

    static NamespacedKey CLE, MODELE;
    static Jeu jeu;

    enum Page { MENU, OBJETS, BASE, QUETES, DEX, CLASSEMENT, ORBITO, VENTE }

    record Article(int case_, String id, Material mat, int quantite, String nom, long prix, String... description) {
    }

    static final List<Article> ARTICLES = List.of(
            new Article(10, "batte", Material.STICK, 1, "<#E0B864><b>Batte stellaire</b>", 1_500,
                    "<gray>Envoie les voleurs valser bien plus loin.", "<gray>Un coup = l'Orbito volé est lâché.", "<#5A6270>Se garde pour toujours."),
            new Article(11, "filet", Material.SNOWBALL, 3, "<#62CFF4><b>Filet ×3</b>", 900,
                    "<gray>À lancer : ralentit pendant 3 s", "<gray>et fait lâcher l'Orbito porté.", "<gray>Recharge 6 s · 3 max sur toi."),
            new Article(12, "cape", Material.PHANTOM_MEMBRANE, 1, "<#A64DFF><b>Cape d'ombre</b>", 2_000,
                    "<gray>Clic droit : invisible 6 secondes.", "<gray>Recharge 30 s · 1 max sur toi."),
            new Article(13, "propulseur", Material.FEATHER, 3, "<#FF7A1A><b>Propulseur ×3</b>", 800,
                    "<gray>Clic droit : bond en avant.", "<gray>Recharge 12 s · 3 max sur toi."),
            new Article(14, "comete", Material.SUGAR, 3, "<#FFE08A><b>Comète ×3</b>", 900,
                    "<gray>Clic droit : vitesse I et saut", "<gray>pendant 5 secondes.", "<gray>Recharge 30 s · 3 max sur toi."),
            new Article(15, "teleporteur", Material.AMETHYST_SHARD, 1, "<#C77DFF><b>Téléporteur de poche</b>", 1_500,
                    "<gray>Clic droit : retour direct dans ta base.", "<#FF4D6D>Impossible en portant un Orbito.", "<gray>Recharge 45 s · 1 max sur toi."),
            new Article(16, "mur", Material.PRISMARINE_SHARD, 2, "<#62CFF4><b>Mur d'énergie ×2</b>", 2_500,
                    "<gray>Clic droit : mur de 3 × 3 devant toi", "<gray>pendant 5 secondes.", "<gray>Recharge 20 s · 2 max sur toi."),
            new Article(20, "bombe", Material.FIRE_CHARGE, 2, "<#A64DFF><b>Bombe à gravité ×2</b>", 3_000,
                    "<gray>À lancer : onde de choc de 3 blocs,", "<gray>repousse et fait tout lâcher.", "<gray>Recharge 20 s · 2 max sur toi."),
            new Article(21, "grappin", Material.TRIPWIRE_HOOK, 1, "<#E0B864><b>Grappin stellaire</b>", 5_000,
                    "<gray>Clic droit : te tire jusqu'au bloc visé", "<gray>(28 blocs, recharge 8 s).", "<#5A6270>Se garde pour toujours."),
            new Article(23, "cryo", Material.BREEZE_ROD, 1, "<#9FE8FF><b>Pistolet cryo</b>", 12_000,
                    "<gray>Clic droit : rayon de glace (22 blocs).", "<gray>Ralentit la cible 2 s et lui fait lâcher l'Orbito.", "<gray>Recharge 15 s.", "<#5A6270>Se garde pour toujours."),
            new Article(24, "marteau", Material.GOLDEN_AXE, 1, "<#FFE08A><b>Marteau cosmique</b>", 25_000,
                    "<gray>Recul énorme sur la cible et onde de choc", "<gray>autour d'elle (toutes les 6 s).", "<#5A6270>Se garde pour toujours.")
    );

    static final class Holder implements InventoryHolder {
        final Page page;
        final Jeu.Spec spec;
        Inventory inv;

        Holder(Page page, Jeu.Spec spec) {
            this.page = page;
            this.spec = spec;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private Objets() {
    }

    static Objets init(OrbitVol plugin, Jeu j) {
        CLE = new NamespacedKey(plugin, "objet");
        MODELE = new NamespacedKey(plugin, "modele");
        jeu = j;
        return new Objets();
    }

    /** Objet avec nom, description, identifiant de jeu (peut être null) et modèle HD (peut être null). */
    static ItemStack objet(Material m, int n, String id, String modele, String nom, String... lignes) {
        ItemStack it = new ItemStack(m, n);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.mm(nom));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String l : lignes) lore.add(Txt.mm(l));
        meta.lore(lore);
        if (id != null) meta.getPersistentDataContainer().set(CLE, PersistentDataType.STRING, id);
        if (modele != null) meta.getPersistentDataContainer().set(MODELE, PersistentDataType.STRING, modele);
        it.setItemMeta(meta);
        return it;
    }

    /** Ramène chaque objet consommable au stock maximum (vieux stocks d'avant l'équilibrage). */
    static void limiter(Player p) {
        for (Article a : ARTICLES) {
            if (Gadgets.permanent(a.id())) continue;
            int reste = Gadgets.stockMax(a.id());
            for (ItemStack it : p.getInventory().getContents()) {
                if (!est(it, a.id())) continue;
                int garde = Math.min(reste, it.getAmount());
                it.setAmount(garde);
                reste -= garde;
            }
        }
    }

    static int compter(Player p, String id) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (est(it, id)) n += it.getAmount();
        return n;
    }

    static boolean possede(Player p, String id) {
        for (ItemStack it : p.getInventory().getContents()) if (est(it, id)) return true;
        return false;
    }

    static boolean est(ItemStack it, String id) {
        if (it == null || !it.hasItemMeta()) return false;
        return id.equals(it.getItemMeta().getPersistentDataContainer().get(CLE, PersistentDataType.STRING));
    }

    /** Met (ou retire) le modèle HD sur un objet qui en a un. */
    static void habiller(ItemStack it, boolean hd) {
        if (it == null || !it.hasItemMeta()) return;
        ItemMeta meta = it.getItemMeta();
        String m = meta.getPersistentDataContainer().get(MODELE, PersistentDataType.STRING);
        if (m == null) {
            // Objets donnés avant le pack HD : le modèle se déduit de leur identifiant.
            String id = meta.getPersistentDataContainer().get(CLE, PersistentDataType.STRING);
            if (id == null) return;
            m = id.equals("boutique") ? "menu" : id;
        }
        meta.setItemModel(hd ? new NamespacedKey("orbitvol", m) : null);
        it.setItemMeta(meta);
    }

    static void habiller(Player p, boolean hd) {
        for (ItemStack it : p.getInventory().getContents()) habiller(it, hd);
    }

    static void habiller(Inventory inv, Player p) {
        boolean hd = jeu.pack.charge(p);
        for (ItemStack it : inv.getContents()) habiller(it, hd);
    }

    static ItemStack etoile() {
        return objet(Material.NETHER_STAR, 1, "boutique", "menu", "<#E0B864><b>Menu ORBITO</b> <gray>(clic droit)",
                "<gray>Objets, améliorations de base, quêtes,", "<gray>Orbidex, classement et Ascension.");
    }

    static void donnerDepart(Player p) {
        for (int i = 0; i < p.getInventory().getSize(); i++)
            if (est(p.getInventory().getItem(i), "boutique")) { p.getInventory().setItem(i, etoile()); return; }
        p.getInventory().setItem(8, etoile());
    }

    private static Inventory creer(Page page, Jeu.Spec spec, int taille, String titre) {
        Holder h = new Holder(page, spec);
        h.inv = Bukkit.createInventory(h, taille, Txt.mm(titre));
        return h.inv;
    }

    private static void ouvrir(Player p, Inventory inv) {
        habiller(inv, p);
        p.openInventory(inv);
    }

    static ItemStack retour() {
        return objet(Material.ARROW, 1, null, "m_retour", "<#E6EDF3>← Retour");
    }

    // ------------------------------------------------------------------ pages

    static void ouvrirMenu(Player p, Profil pr) {
        if (pr == null) return;
        Quetes.verifier(pr, pr.revenu);
        long faites = Quetes.liste(pr).stream().filter(q -> q.faite).count();
        Tuto.avancer(jeu, p, pr, 3);
        Inventory inv = creer(Page.MENU, null, 27, "<#E0B864><b>ORBITO</b> <#5A6270>· " + Txt.cr(pr.credits));
        inv.setItem(10, objet(Material.STICK, 1, null, "m_objets", "<#E0B864><b>Objets</b>", "<gray>11 objets : batte, filets, grappin,", "<gray>pistolet cryo, bombes, marteau…"));
        inv.setItem(11, objet(Material.SEA_LANTERN, 1, null, "m_base", "<#62CFF4><b>Améliorer ma base</b>",
                "<gray>Places, bouclier, alarme,", "<gray>générateur et banque orbitale."));
        inv.setItem(12, objet(Material.WRITABLE_BOOK, 1, null, "m_quetes", "<#62CFF4><b>Quêtes du jour</b>",
                "<gray>Faites : <#7CFC9A>" + faites + "/3",
                "<gray>Série : <#E0B864>" + pr.serie + " jour(s)</#E0B864> <gray>(+10 % par jour)"));
        inv.setItem(14, objet(Material.BOOK, 1, null, "m_orbidex", "<#A64DFF><b>Orbidex</b>",
                "<gray>Ta collection : " + pr.dex.size() + "/" + Catalogue.TOUS.size() * Mutation.values().length,
                "<gray>Chaque entrée = <#7CFC9A>+1 %</#7CFC9A> de revenus."));
        inv.setItem(15, objet(Material.GOLD_INGOT, 1, null, "m_classement", "<#E0B864><b>Classement</b>",
                "<gray>Les plus riches, les plus grands", "<gray>voleurs et les plus hautes Ascensions."));
        inv.setItem(16, objet(Material.BEACON, 1, null, "m_ascension", "<#E0B864><b>Ascension " + (pr.ascension + 1) + "</b>",
                "<gray>Repars de zéro (crédits et Orbitos)…",
                "<gray>…tu gardes ta base, ses améliorations et ton Orbidex,",
                "<gray>et ton bonus d'Ascension passe à <#7CFC9A>×" + (1 + 0.5 * (pr.ascension + 1)) + "</#7CFC9A>.",
                "",
                "<#E6EDF3>Prix : " + Txt.cr(pr.coutAscension())));
        inv.setItem(22, objet(Material.PAPER, 1, null, "m_astuces", "<#E6EDF3><b>Astuces</b>",
                "<gray>Clique ton Orbito : l'améliorer ou le revendre.",
                "<gray>Clique celui d'un autre : le voler !",
                "<gray>Plaque dorée = encaisser. Bouton bleu = bouclier."));
        inv.setItem(13, objet(Material.TURTLE_EGG, 1, null, "m_oeufs", "<gradient:#E0B864:#62CFF4><b>Œufs mystères & fusion</b>",
                "<gray>Un Orbito au hasard, avec roulette !", "<gray>3 identiques = 1 de la rareté au-dessus."));
        inv.setItem(19, objet(Material.NAME_TAG, 1, null, "m_collection", "<#E0B864><b>Titres et auras</b>",
                "<gray>Titres : " + pr.titres.size() + "/" + Recompenses.TITRES.size() + " · Auras : " + pr.auras.size() + "/" + Recompenses.AURAS.size()));
        inv.setItem(20, objet(Material.PAINTING, 1, null, "m_perso", "<#62CFF4><b>Couleurs de ma base</b>", "<gray>Sol et vitres à ton goût."));
        inv.setItem(24, objet(Material.EMERALD, 1, null, "m_echange", "<#7CFC9A><b>Échanger</b>", "<gray>Échange un Orbito avec un autre joueur,", "<gray>ou offre-le."));
        inv.setItem(25, objet(Material.JUKEBOX, 1, null, "m_musique", "<#A64DFF><b>Musique</b>", pr.musique ? "<#7CFC9A>Activée <gray>— clic pour couper" : "<#FF4D6D>Coupée <gray>— clic pour l'activer"));
        ouvrir(p, inv);
    }

    static void ouvrirObjets(Player p, Profil pr) {
        Inventory inv = creer(Page.OBJETS, null, 36, "<#E0B864><b>Objets</b> <#5A6270>· " + Txt.cr(pr.credits));
        for (Article a : ARTICLES) {
            String[] l = new String[a.description().length + 2];
            System.arraycopy(a.description(), 0, l, 0, a.description().length);
            l[l.length - 2] = "";
            l[l.length - 1] = "<#E6EDF3>Prix : " + Txt.cr(a.prix());
            inv.setItem(a.case_(), objet(a.mat(), a.quantite(), null, a.id(), a.nom(), l));
        }
        inv.setItem(27, retour());
        ouvrir(p, inv);
    }

    static void ouvrirBase(Player p, Profil pr) {
        Inventory inv = creer(Page.BASE, null, 27, "<#62CFF4><b>Ma base</b> <#5A6270>· " + Txt.cr(pr.credits));
        for (Amelioration a : Ameliorations.TOUTES) {
            int n = pr.niveau(a.id());
            List<String> l = new ArrayList<>();
            l.add("<gray>" + a.explication());
            l.add("");
            l.add("<#E6EDF3>Niveau " + n + "/" + a.max() + " : <#7CFC9A>" + a.effets()[n]);
            if (n < a.max()) {
                l.add("<#E6EDF3>Suivant : <#62CFF4>" + a.effets()[n + 1]);
                l.add("");
                l.add("<#E6EDF3>Prix : " + Txt.cr(a.prix()[n]) + (pr.credits >= a.prix()[n] ? " <#7CFC9A>✔" : " <#FF4D6D>✘"));
            } else {
                l.add("<#E0B864><b>MAXIMUM</b>");
            }
            inv.setItem(a.case_(), objet(a.icone(), Math.max(1, n), null, "a_" + a.id(), a.nom(), l.toArray(String[]::new)));
        }
        inv.setItem(18, retour());
        ouvrir(p, inv);
    }

    static void ouvrirQuetes(Player p, Profil pr) {
        Quetes.verifier(pr, pr.revenu);
        Inventory inv = creer(Page.QUETES, null, 27, "<#62CFF4><b>Quêtes du jour</b> <#5A6270>· série " + pr.serie);
        int[] cases = {11, 13, 15};
        List<Quetes.Quete> l = Quetes.liste(pr);
        double bonus = 1 + 0.1 * Math.min(10, pr.serie);
        for (int i = 0; i < l.size() && i < 3; i++) {
            Quetes.Quete q = l.get(i);
            String prog = q.type == Quetes.Type.ENCAISSER ? Txt.n(q.progres) + " / " + Txt.n(q.objectif) : (long) q.progres + " / " + (long) q.objectif;
            inv.setItem(cases[i], objet(q.faite ? Material.LIME_DYE : Material.PAPER, 1, null, q.faite ? "oui" : "m_quetes",
                    (q.faite ? "<#7CFC9A><b>✔ " : "<#62CFF4><b>") + q.description(),
                    "<#E6EDF3>Avancée : " + (q.faite ? "<#7CFC9A>finie" : "<#62CFF4>" + prog),
                    "<#E6EDF3>Récompense : " + Txt.cr(q.recompense * bonus)));
        }
        inv.setItem(22, objet(Material.CHEST, 1, null, pr.coffreDuJour ? "oui" : "revendre",
                pr.coffreDuJour ? "<#7CFC9A><b>Coffre du jour ouvert</b>" : "<#E0B864><b>Coffre du jour</b>",
                "<gray>Finis les 3 quêtes pour l'ouvrir :",
                "<gray>un gros paquet de crédits et ta série +1.",
                "<gray>Nouvelles quêtes chaque jour à minuit."));
        inv.setItem(18, retour());
        ouvrir(p, inv);
    }

    static void ouvrirClassement(Player p, Profil pr) {
        Classement c = jeu.classement;
        Inventory inv = creer(Page.CLASSEMENT, null, 54, "<#E0B864><b>Classement</b>");
        for (int i = 0; i < c.parRevenu.size() && i < 9; i++) {
            Classement.Ligne x = c.parRevenu.get(i);
            inv.setItem(i, objet(Material.EMERALD, i + 1, null, i == 0 ? "max" : "a_generateur",
                    Classement.rang(i) + " <#E6EDF3>" + x.pseudo(), "<#7CFC9A>+" + Txt.n(x.revenu()) + "/s"));
        }
        for (int i = 0; i < c.parVols.size() && i < 9; i++) {
            Classement.Ligne x = c.parVols.get(i);
            inv.setItem(18 + i, objet(Material.REDSTONE, i + 1, null, i == 0 ? "max" : "batte",
                    Classement.rang(i) + " <#E6EDF3>" + x.pseudo(), "<#FF4D6D>" + x.vols() + " vols réussis"));
        }
        for (int i = 0; i < c.parAscension.size() && i < 9; i++) {
            Classement.Ligne x = c.parAscension.get(i);
            inv.setItem(36 + i, objet(Material.GOLD_NUGGET, i + 1, null, i == 0 ? "max" : "m_ascension",
                    Classement.rang(i) + " <#E6EDF3>" + x.pseudo(), "<#E0B864>Ascension " + x.ascension()));
        }
        inv.setItem(45, retour());
        inv.setItem(49, objet(Material.PAPER, 1, null, "m_astuces", "<#E6EDF3><b>Légende</b>",
                "<gray>1re ligne : les plus riches (revenu/s)", "<gray>3e ligne : les plus grands voleurs", "<gray>5e ligne : les plus hautes Ascensions",
                "<gray>Mis à jour chaque minute."));
        ouvrir(p, inv);
    }

    static Material oeuf(Orbito o) {
        Material m = Material.matchMaterial(o.type().name() + "_SPAWN_EGG");
        return m != null ? m : Material.EGG;
    }

    static void ouvrirDex(Player p, Profil pr) {
        Inventory inv = creer(Page.DEX, null, 54, "<#A64DFF><b>Orbidex</b> <#5A6270>· " + pr.dex.size() + "/" + Catalogue.TOUS.size() * Mutation.values().length);
        int i = 0;
        for (Orbito o : Catalogue.TOUS.values()) {
            List<String> l = new ArrayList<>();
            int vus = 0;
            for (Mutation m : Mutation.values()) {
                boolean ok = pr.dex.contains(o.id() + "|" + m.name());
                if (ok) vus++;
                String nom = m == Mutation.AUCUNE ? "<#B8C2CC>Normal " : m.prefixe;
                l.add((ok ? "<#7CFC9A>✔ " : "<#5A6270>✘ ") + nom + "<reset>");
            }
            l.add(0, "<#5A6270>[" + o.rarete().etiquette() + "<#5A6270>] " + Txt.cr(o.prix()) + " <#7CFC9A>+" + Txt.n(o.revenu()) + "/s");
            l.add(1, "");
            ItemStack it = vus > 0 ? objet(oeuf(o), 1, null, "o_" + o.id(), o.nomComplet(Mutation.AUCUNE), l.toArray(String[]::new))
                    : objet(Material.GRAY_DYE, 1, null, "inconnu", "<#5A6270>???", "<#5A6270>[" + o.rarete().etiquette() + "<#5A6270>]", "<gray>Pas encore découvert.");
            inv.setItem(i++, it);
        }
        inv.setItem(45, retour());
        ouvrir(p, inv);
    }

    static void ouvrirOrbito(Player p, Jeu.Spec s) {
        Profil pr = jeu.profils.get(p.getUniqueId());
        if (pr == null) return;
        Inventory inv = creer(Page.ORBITO, s, 27, s.o.nomComplet(s.m) + " <#5A6270>· niv. " + s.niveau);
        inv.setItem(11, objet(oeuf(s.o), 1, null, "o_" + s.o.id(), s.o.nomComplet(s.m),
                "<#5A6270>[" + s.o.rarete().etiquette() + "<#5A6270>]",
                "<#E6EDF3>Niveau : <#62CFF4>" + s.niveau + "/" + Ameliorations.NIVEAU_MAX,
                "<#E6EDF3>Revenu : <#7CFC9A>+" + Txt.n(s.revenu() * pr.multiplicateur()) + "/s <gray>(avec tes bonus)"));
        if (s.niveau < Ameliorations.NIVEAU_MAX) {
            double cout = Ameliorations.prixNiveau(Jeu.prix(s), s.niveau);
            double apres = s.o.revenu() * s.m.mult * Ameliorations.multNiveau(s.niveau + 1) * pr.multiplicateur();
            inv.setItem(13, objet(Material.EXPERIENCE_BOTTLE, 1, null, "ameliorer", "<#7CFC9A><b>Améliorer → niv. " + (s.niveau + 1) + "</b>",
                    "<#E6EDF3>Revenu : <#7CFC9A>+" + Txt.n(apres) + "/s",
                    "",
                    "<#E6EDF3>Prix : " + Txt.cr(cout) + (pr.credits >= cout ? " <#7CFC9A>✔" : " <#FF4D6D>✘")));
        } else {
            inv.setItem(13, objet(Material.NETHER_STAR, 1, null, "max", "<#E0B864><b>Niveau MAX</b>"));
        }
        inv.setItem(15, objet(Material.GOLD_NUGGET, 1, null, "revendre", "<#FF4D6D><b>Revendre</b>",
                "<gray>Rend la moitié de ce que tu y as mis :", Txt.cr(s.valeur() / 2)));
        int identiques = Oeufs.identiques(jeu, p, s).size();
        if (s.o.rarete() != Catalogue.Rarete.SECRET)
            inv.setItem(22, objet(Material.ANVIL, 1, null, "fusion", "<#FF7A1A><b>Fusionner</b> <#5A6270>(" + Math.min(identiques, 3) + "/3)",
                    "<gray>3 × " + s.o.nom() + " → 1 Orbito " + Catalogue.Rarete.values()[s.o.rarete().ordinal() + 1].etiquette() + " <gray>au hasard.",
                    "<gray>La meilleure mutation des trois est gardée.",
                    identiques >= 3 ? "<#7CFC9A>Clic pour fusionner !" : "<#FF4D6D>Il t'en faut 3 dans ta base."));
        ouvrir(p, inv);
    }

    static void ouvrirVente(Player p, Jeu.Spec s) {
        Inventory inv = creer(Page.VENTE, s, 27, "<#FF4D6D>Revendre " + s.o.nom() + " ?");
        inv.setItem(11, objet(Material.LIME_CONCRETE, 1, null, "oui", "<#7CFC9A><b>Oui, revendre</b>", Txt.cr(s.valeur() / 2)));
        inv.setItem(15, objet(Material.RED_CONCRETE, 1, null, "non", "<#FF4D6D><b>Non, je le garde</b>"));
        ouvrir(p, inv);
    }

    // ------------------------------------------------------------------ clics

    @EventHandler
    public void onClic(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        Profil pr = jeu.profils.get(p.getUniqueId());
        if (pr == null) return;
        int slot = e.getRawSlot();
        switch (h.page) {
            case MENU -> {
                switch (slot) {
                    case 10 -> ouvrirObjets(p, pr);
                    case 11 -> ouvrirBase(p, pr);
                    case 12 -> ouvrirQuetes(p, pr);
                    case 14 -> ouvrirDex(p, pr);
                    case 15 -> ouvrirClassement(p, pr);
                    case 16 -> { p.closeInventory(); jeu.ascension(p, false); }
                    case 13 -> Oeufs.ouvrir(jeu, p);
                    case 19 -> jeu.recompenses.ouvrirCollection(p);
                    case 20 -> jeu.recompenses.ouvrirBase(p);
                    case 24 -> jeu.echange.ouvrirJoueurs(p);
                    case 25 -> { jeu.recompenses.basculerMusique(p); ouvrirMenu(p, pr); }
                    default -> { }
                }
            }
            case OBJETS -> {
                if (slot == 27) { ouvrirMenu(p, pr); return; }
                for (Article a : ARTICLES) {
                    if (a.case_() != slot) continue;
                    if (Gadgets.permanent(a.id()) && possede(p, a.id())) { Txt.send(p, "Tu as déjà cet objet, il se garde pour toujours."); Txt.sound(p, Sound.ENTITY_VILLAGER_NO, 1f); return; }
                    int max = Gadgets.stockMax(a.id());
                    if (!Gadgets.permanent(a.id()) && compter(p, a.id()) + a.quantite() > max) { Txt.send(p, "Tu en as déjà assez sur toi (" + max + " max). Utilise-les d'abord !"); Txt.sound(p, Sound.ENTITY_VILLAGER_NO, 1f); return; }
                    if (pr.credits < a.prix()) {
                        Txt.send(p, "Il te manque " + Txt.cr(a.prix() - pr.credits) + ".");
                        Txt.sound(p, Sound.ENTITY_VILLAGER_NO, 1f);
                        return;
                    }
                    pr.credits -= a.prix();
                    ItemStack it = objet(a.mat(), a.quantite(), a.id(), a.id(), a.nom(), a.description());
                    habiller(it, jeu.pack.charge(p));
                    p.getInventory().addItem(it);
                    Txt.sound(p, Sound.ENTITY_PLAYER_LEVELUP, 1.6f);
                    ouvrirObjets(p, pr);
                    jeu.hud(p);
                    return;
                }
            }
            case BASE -> {
                if (slot == 18) { ouvrirMenu(p, pr); return; }
                for (Amelioration a : Ameliorations.TOUTES)
                    if (a.case_() == slot) { jeu.ameliorerBase(p, a.id()); ouvrirBase(p, pr); return; }
            }
            case QUETES -> {
                if (slot == 18) ouvrirMenu(p, pr);
            }
            case DEX, CLASSEMENT -> {
                if (slot == 45) ouvrirMenu(p, pr);
            }
            case ORBITO -> {
                if (!h.spec.mob.isValid() || h.spec.etat != Jeu.Etat.POSE || !p.getUniqueId().equals(h.spec.proprio)) { p.closeInventory(); return; }
                if (slot == 13) { jeu.ameliorer(p, h.spec); ouvrirOrbito(p, h.spec); }
                else if (slot == 15) ouvrirVente(p, h.spec);
                else if (slot == 22) Oeufs.fusionner(jeu, p, h.spec);
            }
            case VENTE -> {
                if (slot == 11) { p.closeInventory(); jeu.vendre(p, h.spec); }
                else if (slot == 15) ouvrirOrbito(p, h.spec);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
