package fr.orbit.contrebande;

import fr.orbit.contrebande.Produit.Additif;
import fr.orbit.contrebande.Produit.Base;
import fr.orbit.contrebande.Produit.Effet;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Les menus de CONTREBANDE : accueil de la borne, boutique de Rix, terminal (améliorations persistantes), coffre de
 * l'organisation, table de mélange et carnet de recettes. Aucun objet ne bouge dans ces menus : tous les clics sont
 * annulés puis interprétés.
 *
 * <p>Tous font 6 rangées. Avec le pack de CONTREBANDE, le fond est une image au style « station » ORBIT (police
 * {@code contrebande:gui}, dessinée par tools/ContrebandePackGen.java : <b>les cases doivent rester les mêmes des deux
 * côtés</b>) ; sans le pack, mêmes cases sur fond de vitres noires.
 */
final class Menus {

    private Menus() {
    }

    interface Holder extends InventoryHolder {
        /** {@code haut} : clic dans le menu ; sinon dans l'inventaire du joueur ({@code slot} = case de son inventaire). */
        void click(Player p, int raw, ClickType type, boolean haut, int slot);
    }

    // ------------------------------------------------------------------ améliorations persistantes

    record Amelio(String cle, String nom, Material mat, int[] prix, int[] etoiles, String... desc) {
        int max() {
            return prix.length;
        }
    }

    static final List<Amelio> AMELIOS = List.of(
            new Amelio("bacs", "Bac de culture", Material.AMETHYST_CLUSTER, new int[]{120, 200, 320, 480, 700, 1000}, new int[]{0, 0, 1, 1, 2, 2},
                    "Un bac de plus dans la planque.", "Jusqu'à 8 bacs."),
            new Amelio("engrais", "Engrais stellaire", Material.BONE_MEAL, new int[]{250, 600, 1400}, new int[]{0, 1, 2},
                    "Les germes poussent plus vite :", "-20 %, puis -37 %, puis -50 %."),
            new Amelio("conds", "Condenseur à plasma", Material.BLAST_FURNACE, new int[]{300, 900}, new int[]{0, 1},
                    "Minerai sombre → 3 Plasma noir (45 cr).", "Débloque les commandes de plasma."),
            new Amelio("synth", "Synthétiseur", Material.CRAFTER, new int[]{1500}, new int[]{2},
                    "1 quartz + 1 plasma bruts → 2 Cristaux", "de nébuleuse (110 cr). La Haute-Ville en raffole."),
            new Amelio("marche", "Grilles du Marché", Material.IRON_BARS, new int[]{400}, new int[]{1},
                    "Ouvre le Marché : 8 nouveaux clients,", "mieux payés… et une patrouille de plus."),
            new Amelio("hv", "Grilles de la Haute-Ville", Material.GOLD_BLOCK, new int[]{2000}, new int[]{3},
                    "Ouvre la Haute-Ville : les clients les", "plus riches d'Orbite. Gardée par des sentinelles."),
            new Amelio("bottes", "Bottes de coursier", Material.LEATHER_BOOTS, new int[]{350}, new int[]{0},
                    "Vitesse I pendant les nuits,", "pour tous les membres."),
            new Amelio("skate", "Skate à sustentation", Material.HEAVY_WEIGHTED_PRESSURE_PLATE, new int[]{600, 1800}, new int[]{1, 3},
                    "Clic droit : +45 % de vitesse, de quoi semer", "un douanier. Batterie : 20 s, puis 35 s."),
            new Amelio("batte", "Batte du contrebandier", Material.STICK, new int[]{450, 1600}, new int[]{1, 3},
                    "En main, clic gauche : sonne et ralentit un", "douanier. Niveau 2 : onde de choc sur 3 blocs."),
            new Amelio("brouilleur", "Brouilleur de poche", Material.SCULK_SENSOR, new int[]{500, 1500}, new int[]{1, 3},
                    "La douane te repère de moins loin :", "-20 %, puis -35 % (agents, voitures, drones)."),
            new Amelio("jardinier", "Jardinier", Material.WOODEN_HOE, new int[]{800}, new int[]{2},
                    "Récolte et replante les bacs tout seul", "(germes pris au coffre). Salaire : 40 cr par nuit."),
            new Amelio("revendeur", "Revendeur", Material.EMERALD, new int[]{1000, 2500}, new int[]{3, 4},
                    "À l'aube, écoule la marchandise du coffre", "à 65 % (12 puis 30 unités). Salaire : 50 cr par niveau."));

    static Amelio amelio(String cle) {
        for (Amelio a : AMELIOS) {
            if (a.cle().equals(cle)) {
                return a;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ habillage (pack de CONTREBANDE)

    private static final Key POLICE = Key.key("contrebande", "gui");
    private static final NamespacedKey INVISIBLE = new NamespacedKey("contrebande", "invisible");
    /** Bloc de 8 glyphes de chaque fond dans la police du pack : mêmes valeurs que dans tools/ContrebandePackGen.java. */
    private static final int G_ACCUEIL = 0xE300, G_RIX = 0xE310, G_TERMINAL = 0xE320, G_COFFRE = 0xE330, G_MELANGE = 0xE340, G_CARNET = 0xE350;

    static boolean hd(Nuit n, Player p) {
        return n.plugin.pack().charge(p);
    }

    /**
     * Titre du coffre. Avec le pack : le fond du menu, 2 x 4 tuiles de 99 de large
     * ([-18] puis, par rangée : tuile gauche, [-1], tuile droite, [-197]), en blanc car la couleur du texte teinte l'image.
     */
    static Component titre(boolean hd, int base, String simple) {
        if (!hd) {
            return Txt.mm(simple);
        }
        StringBuilder b = new StringBuilder().append((char) 0xF000);
        for (int row = 0; row < 4; row++) {
            b.appendCodePoint(base + row * 2).append((char) 0xF001).appendCodePoint(base + row * 2 + 1).append((char) 0xF002);
        }
        return Component.text(b.toString()).font(POLICE).color(NamedTextColor.WHITE);
    }

    /** Zone cliquable : invisible par-dessus un bouton peint (pack), sinon une icône classique. Même infobulle. */
    static ItemStack zone(boolean hd, Material m, String name, String... lore) {
        ItemStack it = icon(hd ? Material.PAPER : m, 1, name, lore);
        if (hd) {
            ItemMeta meta = it.getItemMeta();
            meta.setItemModel(INVISIBLE);
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Icône d'information : un modèle 3D du pack (caisse…) pour ceux qui l'ont, sinon l'objet vanilla. */
    static ItemStack vue(boolean hd, String modele, Material m, String name, String... lore) {
        ItemStack it = icon(hd ? Material.PAPER : m, 1, name, lore);
        if (hd) {
            ItemMeta meta = it.getItemMeta();
            meta.setItemModel(new NamespacedKey("contrebande", modele));
            it.setItemMeta(meta);
        }
        return it;
    }

    static ItemStack vue(boolean hd, String modele, Material m, String name, List<String> lore) {
        return vue(hd, modele, m, name, lore.toArray(new String[0]));
    }

    // ------------------------------------------------------------------ outils

    static ItemStack icon(Material m, int n, String name, String... lore) {
        ItemStack it = new ItemStack(m, Math.max(1, Math.min(m.getMaxStackSize(), n)));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.mm(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) {
            l.add(Txt.mm(s));
        }
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    static ItemStack icon(Material m, int n, String name, List<String> lore) {
        return icon(m, n, name, lore.toArray(new String[0]));
    }

    private static ItemStack withLore(ItemStack base, boolean glint, String... extra) {
        ItemStack it = base.clone();
        ItemMeta meta = it.getItemMeta();
        List<Component> l = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        for (String s : extra) {
            l.add(Txt.mm(s));
        }
        meta.lore(l);
        if (glint) {
            meta.setEnchantmentGlintOverride(true);
        }
        it.setItemMeta(meta);
        return it;
    }

    /** Sans le pack : vitres noires dans les cases vides. Avec : rien, le fond est dessiné par le titre. */
    private static void fill(Inventory inv, boolean hd) {
        if (hd) {
            return;
        }
        ItemStack pane = icon(Material.BLACK_STAINED_GLASS_PANE, 1, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, pane);
            }
        }
    }

    private static void tic(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
    }

    /** Le menu est-il encore utilisable par ce joueur (nuit en cours, joueur dedans) ? */
    private static boolean valide(Nuit n, Player p) {
        if (n.ended || !n.joueurs.containsKey(p.getUniqueId())) {
            p.closeInventory();
            return false;
        }
        return true;
    }

    private static String orgaLigne(Orga o) {
        return "<#E0B864>" + o.titre() + "</#E0B864> <#5A6270>·</#5A6270> " + Txt.stars(o.etoiles());
    }

    // ------------------------------------------------------------------ accueil de la borne (salle d'arcade)

    static void accueil(OrbitContrebande plugin, Player p, Orga o) {
        Accueil a = new Accueil(plugin, o, plugin.pack().charge(p));
        a.render();
        p.openInventory(a.inv);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.4f);
    }

    static final class Accueil implements Holder {
        /** Case de l'icône sans le pack. Avec le pack, tout le bouton peint est cliquable (voir {@link #bouton}). */
        private static final int CARTE = 4, JOUER = 29, TOP = 24, REGLES = 33, ORGA = 42;
        final OrbitContrebande plugin;
        final Orga o;
        final boolean hd;
        final Inventory inv;

        Accueil(OrbitContrebande plugin, Orga o, boolean hd) {
            this.plugin = plugin;
            this.o = o;
            this.hd = hd;
            this.inv = Bukkit.createInventory(this, 54, titre(hd, G_ACCUEIL, "<#3A4250>CONTREBANDE · borne d'arcade"));
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }

        /** Bouton d'une case : JOUER (rangées 2 à 4, colonnes 0 à 3), puis un bouton par rangée à droite (colonnes 5 à 8). */
        private int bouton(int raw) {
            if (!hd) {
                return raw == JOUER || raw == TOP || raw == REGLES || raw == ORGA ? raw : -1;
            }
            int r = raw / 9, c = raw % 9;
            if (raw < 0 || r < 2 || r > 4) {
                return -1;
            }
            if (c <= 3) {
                return JOUER;
            }
            return c < 5 ? -1 : r == 2 ? TOP : r == 3 ? REGLES : ORGA;
        }

        private void poser(int bouton, Material m, String name, String... lore) {
            ItemStack it = zone(hd, m, name, lore);
            for (int k = 18; k < 45; k++) {
                if (bouton(k) == bouton) {
                    inv.setItem(k, it);
                }
            }
        }

        void render() {
            inv.clear();
            List<String> l = new ArrayList<>();
            l.add(orgaLigne(o));
            l.add("");
            l.add("<#9AA4B2>Fortune : " + Txt.cr(o.fortune) + " <#5A6270>(gagnée depuis la création)");
            l.add("<#9AA4B2>En caisse : " + Txt.cr(o.credits));
            l.add("<#9AA4B2>Nuits : <#E6EDF3>" + o.nuits + "</#E6EDF3> · réputation <#E6EDF3>" + o.rep);
            int t = o.titreIndex();
            if (t + 1 < Orga.TITRES.length) {
                l.add("<#9AA4B2>Prochain titre : <#E0B864>" + Orga.TITRES[t + 1] + "</#E0B864> à " + Txt.cr(Orga.SEUILS[t + 1]));
            }
            l.add("");
            l.add("<#9AA4B2>Membres (" + o.membres.size() + "/" + Orga.MAX_MEMBRES + ") :");
            for (Map.Entry<java.util.UUID, String> m : o.membres.entrySet()) {
                l.add("  <#E6EDF3>" + m.getValue() + (m.getKey().equals(o.chef) ? " <#E0B864>(chef)" : ""));
            }
            inv.setItem(CARTE, vue(hd, "caisse", Material.ENDER_CHEST, "<#E0B864><b>" + o.nom + "</b>", l));
            Nuit n = plugin.sessions().nuitDe(o);
            if (n != null) {
                poser(JOUER, Material.LIME_CONCRETE, "<#7CFF8A><b>Rejoindre la nuit en cours</b>",
                        "<#9AA4B2>" + n.joueurs.size() + " membre(s) aux Docks · " + Txt.clock((n.duree - n.tick) / 20) + " avant l'aube",
                        "", "<#62CFF4>Clic pour les rejoindre");
            } else {
                poser(JOUER, Material.ENDER_EYE, "<#E0B864><b>Lancer une nuit</b>",
                        "<#9AA4B2>10 minutes aux Docks d'Orbite. Tout ce que",
                        "<#9AA4B2>ton organisation gagne est gardé pour la suite.",
                        "<#9AA4B2>Tes amis membres pourront te rejoindre.",
                        "", "<#62CFF4>Clic pour jouer");
            }
            poser(TOP, Material.GOLDEN_HELMET, "<#62CFF4><b>Classement</b>", "<#9AA4B2>Les plus grosses fortunes d'Orbite.");
            poser(REGLES, Material.BOOK, "<#9AA4B2><b>Comment jouer</b>", "<#9AA4B2>Les bases du métier.");
            poser(ORGA, Material.NAME_TAG, "<#C79BFF><b>Organisation</b>",
                    "<#9AA4B2>Jusqu'à " + Orga.MAX_MEMBRES + " amis partagent le même empire :",
                    "<#9AA4B2>caisse, coffre, améliorations, réputation.",
                    "",
                    "<#E6EDF3>/contrebande orga inviter <pseudo>",
                    "<#E6EDF3>/contrebande orga renommer <nom>",
                    "<#E6EDF3>/contrebande orga quitter",
                    "", "<#62CFF4>Clic : toutes les commandes");
            fill(inv, hd);
        }

        @Override
        public void click(Player p, int raw, ClickType type, boolean haut, int slot) {
            int b = haut ? bouton(raw) : -1;
            if (b < 0) {
                return;
            }
            tic(p);
            // hors de l'événement de clic : fermer le menu et téléporter y sont sans risque
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) {
                    return;
                }
                p.closeInventory();
                switch (b) {
                    case JOUER -> plugin.sessions().play(p);
                    case TOP -> plugin.sessions().showTop(p);
                    case REGLES -> Nuit.regles(p);
                    default -> plugin.sessions().orgaAide(p);
                }
            });
        }
    }

    // ------------------------------------------------------------------ boutique de Rix

    static void boutique(Nuit n, Player p) {
        Boutique b = new Boutique(n, hd(n, p));
        b.render();
        p.openInventory(b.inv);
        p.playSound(p.getLocation(), Sound.ENTITY_WANDERING_TRADER_YES, 0.6f, 1f);
    }

    static final class Boutique implements Holder {
        private static final int CARTE = 8, GERME = 10, MINERAI = 12, POTDEVIN = 14, AIDE = 16;
        /** Les 8 additifs, dans l'ordre de {@link Additif} : deux rangées de quatre. */
        private static final int[] ADDS = {28, 30, 32, 34, 37, 39, 41, 43};
        final Nuit n;
        final boolean hd;
        final Inventory inv;

        Boutique(Nuit n, boolean hd) {
            this.n = n;
            this.hd = hd;
            this.inv = Bukkit.createInventory(this, 54, titre(hd, G_RIX, "<#3A4250>Rix · fournisseur discret"));
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }

        int prixGerme() {
            return n.plugin.getConfig().getInt("prix.germe", 25);
        }

        int prixMinerai() {
            return n.plugin.getConfig().getInt("prix.minerai", 60);
        }

        int potDeVin() {
            return 100 + n.orga.chaleur * 5;
        }

        void render() {
            inv.clear();
            inv.setItem(CARTE, vue(hd, "caisse", Material.GOLD_INGOT, "<#E0B864><b>" + Txt.n(n.orga.credits) + " cr</b> <#9AA4B2>· caisse de " + n.orga.nom,
                    "<#9AA4B2>Réputation : " + Txt.stars(n.orga.etoiles()),
                    "<#5A6270>Livraison +1, +2 avec le souhait, +5 pour un VIP,",
                    "<#5A6270>-1 si un client repart sans rien.",
                    "",
                    "<#9AA4B2>Clic gauche : acheter 1", "<#9AA4B2>Clic droit : acheter 8", "<#9AA4B2>Maj + clic : acheter 32",
                    "<#5A6270>Ce qui ne rentre pas va au coffre."));
            inv.setItem(GERME, withLore(Produit.skin(Produit.germe(1), hd), false, "", "<#E0B864>Prix : " + Txt.cr(prixGerme())));
            inv.setItem(MINERAI, withLore(Produit.skin(Produit.minerai(1), hd), false, "", n.orga.conds() > 0 ? "<#E0B864>Prix : " + Txt.cr(prixMinerai())
                    : "<#E0484A>Achète d'abord un condenseur au terminal."));
            inv.setItem(AIDE, icon(Material.BOOK, 1, "<#62CFF4><b>Le métier</b>",
                    "<#E6EDF3>Germe → bac → <#C79BFF>4 quartz</#C79BFF> (20 cr)",
                    "<#E6EDF3>Minerai → condenseur → <#FF4FD8>3 plasma</#FF4FD8> (45 cr)",
                    "<#E6EDF3>Quartz + plasma → synthétiseur → <#62CFF4>2 cristaux</#62CFF4> (110 cr)",
                    "<#E6EDF3>Un additif ajoute une propriété et peut",
                    "<#E6EDF3>en <b>transformer</b> d'autres : trouve les",
                    "<#E6EDF3>bonnes suites, le carnet les retient."));
            int i = 0;
            for (Additif a : Additif.values()) {
                inv.setItem(ADDS[i++], withLore(Produit.skin(Produit.additif(a, 1), hd), false, "", "<#E0B864>Prix : " + Txt.cr(a.prix)));
            }
            int ch = n.orga.chaleur;
            inv.setItem(POTDEVIN, ch <= 0 ? icon(Material.PAPER, 1, "<#7CFF8A>Avis de recherche : aucun", "<#9AA4B2>La douane ne te connaît pas.")
                    : icon(Material.WRITABLE_BOOK, 1, "<#E0B864><b>Pot-de-vin</b> <#9AA4B2>(avis de recherche " + ch + " %)",
                    "<#9AA4B2>Rix connaît un douanier peu regardant :",
                    "<#9AA4B2>l'avis de recherche baisse de 30 points.",
                    "<#9AA4B2>Au-delà de 40 %, un chasseur te traque",
                    "<#9AA4B2>dès le début de la nuit.",
                    "", "<#E0B864>Prix : " + Txt.cr(potDeVin())));
            fill(inv, hd);
        }

        @Override
        public void click(Player p, int raw, ClickType type, boolean haut, int slot) {
            if (!haut || !valide(n, p)) {
                return;
            }
            int q = type.isShiftClick() ? 32 : type.isRightClick() ? 8 : 1;
            boolean ok = false;
            if (raw == GERME) {
                ok = acheter(p, Produit.germe(q), prixGerme() * q, q);
            } else if (raw == MINERAI && n.orga.conds() > 0) {
                ok = acheter(p, Produit.minerai(q), prixMinerai() * q, q);
            } else if (raw == POTDEVIN && n.orga.chaleur > 0 && n.payer(p, potDeVin())) {
                n.orga.chaleur = Math.max(0, n.orga.chaleur - 30);
                n.broadcast("<#E0B864>" + p.getName() + " a graissé la patte d'un douanier :</#E0B864> l'avis de recherche baisse.");
                ok = true;
            } else {
                for (int i = 0; i < ADDS.length; i++) {
                    if (ADDS[i] == raw) {
                        Additif a = Additif.values()[i];
                        ok = acheter(p, Produit.additif(a, q), a.prix * q, q);
                    }
                }
            }
            if (ok) {
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
                render();
            }
        }

        private boolean acheter(Player p, ItemStack it, int prix, int q) {
            if (!n.payer(p, prix)) {
                return false;
            }
            it.setAmount(q);
            n.donner(p, it);
            return true;
        }
    }

    // ------------------------------------------------------------------ terminal (améliorations)

    static void terminal(Nuit n, Player p) {
        Terminal t = new Terminal(n, hd(n, p));
        t.render();
        p.openInventory(t.inv);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 1.8f);
    }

    static final class Terminal implements Holder {
        private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 21, 22, 23, 24};
        final Nuit n;
        final boolean hd;
        final Inventory inv;

        Terminal(Nuit n, boolean hd) {
            this.n = n;
            this.hd = hd;
            this.inv = Bukkit.createInventory(this, 54, titre(hd, G_TERMINAL, "<#3A4250>Terminal · " + Store.cut(n.orga.nom, 24)));
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }

        void render() {
            inv.clear();
            Orga o = n.orga;
            inv.setItem(8, vue(hd, "caisse", Material.GOLD_INGOT, "<#E0B864><b>" + Txt.n(o.credits) + " cr</b> <#9AA4B2>en caisse",
                    orgaLigne(o), "<#9AA4B2>Fortune : " + Txt.cr(o.fortune), "",
                    "<#9AA4B2>Les améliorations sont gardées pour toujours",
                    "<#9AA4B2>et servent à tous les membres."));
            for (int i = 0; i < AMELIOS.size(); i++) {
                inv.setItem(SLOTS[i], carte(AMELIOS.get(i)));
            }
            inv.setItem(40, icon(Material.WRITTEN_BOOK, 1, "<#62CFF4><b>Registre</b>",
                    "<#9AA4B2>Nuits : <#E6EDF3>" + o.nuits,
                    "<#9AA4B2>Livraisons : <#E6EDF3>" + o.data.ventes,
                    "<#9AA4B2>Saisies de la douane : <#E6EDF3>" + o.data.saisies,
                    "<#9AA4B2>Meilleure nuit : " + Txt.cr(o.data.meilleureNuit),
                    "<#9AA4B2>Recettes au carnet : <#E6EDF3>" + o.data.recettes.size()));
            fill(inv, hd);
        }

        private ItemStack carte(Amelio a) {
            Orga o = n.orga;
            int lvl = o.niveau(a.cle());
            List<String> l = new ArrayList<>();
            for (String d : a.desc()) {
                l.add("<#9AA4B2>" + d);
            }
            l.add("");
            if (a.max() > 1) {
                l.add("<#E6EDF3>Niveau " + lvl + " / " + a.max());
            }
            boolean max = lvl >= a.max();
            if (max) {
                l.add("<#7CFF8A>✔ " + (a.max() > 1 ? "Au maximum" : "Acquis"));
            } else {
                int req = a.etoiles()[lvl];
                if (o.etoiles() < req) {
                    l.add("<#E0484A>Réputation requise : " + Txt.stars(req));
                }
                l.add("<#E0B864>Prix : " + Txt.cr(a.prix()[lvl]));
                l.add("<#62CFF4>Clic pour acheter");
            }
            String nom = (max ? "<#7CFF8A>" : lvl > 0 ? "<#62CFF4>" : "<#E0B864>") + "<b>" + a.nom() + "</b>";
            ItemStack it = icon(a.mat(), Math.max(1, lvl), nom, l);
            if (max) {
                ItemMeta m = it.getItemMeta();
                m.setEnchantmentGlintOverride(true);
                it.setItemMeta(m);
            }
            return it;
        }

        @Override
        public void click(Player p, int raw, ClickType type, boolean haut, int slot) {
            if (!haut || !valide(n, p)) {
                return;
            }
            for (int i = 0; i < AMELIOS.size(); i++) {
                if (SLOTS[i] != raw) {
                    continue;
                }
                Amelio a = AMELIOS.get(i);
                Orga o = n.orga;
                int lvl = o.niveau(a.cle());
                if (lvl >= a.max()) {
                    return;
                }
                if (o.etoiles() < a.etoiles()[lvl]) {
                    Txt.bar(p, "<#E0484A>Il faut " + Txt.stars(a.etoiles()[lvl]) + " <#E0484A>de réputation.");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
                    return;
                }
                if (!n.payer(p, a.prix()[lvl])) {
                    return;
                }
                o.niveau(a.cle(), lvl + 1);
                n.appliquer(a.cle());
                n.broadcast("<#E0B864>" + p.getName() + "</#E0B864> a acheté <#62CFF4>" + a.nom() + (a.max() > 1 ? " (niv. " + (lvl + 1) + ")" : "")
                        + "</#62CFF4> pour l'organisation.");
                n.sonAll(Sound.BLOCK_ANVIL_USE, 0.5f, 1.4f);
                n.sonAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.4f, 1.6f);
                n.plugin.store().sauver(o);
                render();
                return;
            }
        }
    }

    // ------------------------------------------------------------------ coffre de l'organisation

    static void coffre(Nuit n, Player p) {
        Coffre c = new Coffre(n, hd(n, p));
        c.render();
        p.openInventory(c.inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1f);
    }

    /** Ordre d'affichage : germes, minerai, additifs, puis marchandise de la plus chère à la moins chère. */
    static int rang(String cle) {
        if (cle.equals(Produit.CLE_GERME)) {
            return -3_000_000;
        }
        if (cle.equals(Produit.CLE_MINERAI)) {
            return -2_000_000;
        }
        if (cle.startsWith("add:")) {
            return -1_000_000;
        }
        return -Nuit.valeurCle(cle);
    }

    static final class Coffre implements Holder {
        private static final int PAGE = 45, PREC = 45, DEPOT = 47, INFO = 49, MARCH = 51, SUIV = 53;
        final Nuit n;
        final boolean hd;
        final Inventory inv;
        private final List<String> cles = new ArrayList<>();
        int page;

        Coffre(Nuit n, boolean hd) {
            this.n = n;
            this.hd = hd;
            this.inv = Bukkit.createInventory(this, 54, titre(hd, G_COFFRE, "<#3A4250>Coffre · " + Store.cut(n.orga.nom, 26)));
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }

        void render() {
            inv.clear();
            cles.clear();
            cles.addAll(n.orga.data.coffre.keySet());
            cles.removeIf(k -> Produit.objet(k, 1) == null);
            cles.sort((a, b) -> Integer.compare(rang(a), rang(b)));
            int pages = Math.max(1, (cles.size() + PAGE - 1) / PAGE);
            page = Math.max(0, Math.min(page, pages - 1));
            long valeur = 0;
            int unites = 0;
            for (String k : cles) {
                int q = n.orga.stock(k);
                unites += q;
                valeur += (long) Nuit.valeurCle(k) * q;
            }
            for (int i = 0; i < PAGE && page * PAGE + i < cles.size(); i++) {
                String k = cles.get(page * PAGE + i);
                int q = n.orga.stock(k);
                inv.setItem(i, withLore(Produit.skin(Produit.objet(k, Math.min(64, q)), hd), false, "", "<#E6EDF3>Au coffre : " + Txt.n(q),
                        "<#62CFF4>Clic : prendre 1 · droit : 16 · Maj : tout"));
            }
            if (cles.isEmpty()) {
                inv.setItem(22, icon(Material.BARRIER, 1, "<#9AA4B2>Le coffre est vide",
                        "<#9AA4B2>Clique un objet de ton inventaire pour le ranger."));
            }
            if (page > 0) {
                inv.setItem(PREC, icon(Material.ARROW, 1, "<#E6EDF3>Page précédente"));
            }
            if (page < pages - 1) {
                inv.setItem(SUIV, icon(Material.ARROW, 1, "<#E6EDF3>Page suivante"));
            }
            inv.setItem(DEPOT, icon(Material.HOPPER, 1, "<#7CFF8A><b>Tout ranger</b>", "<#9AA4B2>Range tout ce que tu portes",
                    "<#9AA4B2>(sauf le communicateur)."));
            inv.setItem(MARCH, icon(Material.CHEST_MINECART, 1, "<#E0B864><b>Ranger la marchandise</b>",
                    "<#9AA4B2>Range seulement quartz, plasma et cristaux :", "<#9AA4B2>la douane ne pourra pas la saisir."));
            inv.setItem(INFO, vue(hd, "caisse", Material.ENDER_CHEST, "<#C79BFF><b>Réserve de " + n.orga.nom + "</b>",
                    "<#9AA4B2>" + Txt.n(unites) + " objet(s) · marchandise estimée " + Txt.cr(valeur),
                    "<#9AA4B2>Page " + (page + 1) + " / " + pages,
                    "", "<#9AA4B2>Clique un objet de ton inventaire",
                    "<#9AA4B2>pour le ranger. Tout ce que tu portes",
                    "<#9AA4B2>est rangé ici à l'aube."));
            fill(inv, hd);
        }

        @Override
        public void click(Player p, int raw, ClickType type, boolean haut, int slot) {
            if (!valide(n, p)) {
                return;
            }
            if (!haut) {
                ItemStack it = p.getInventory().getItem(slot);
                String cle = Produit.cle(it);
                if (cle != null) {
                    n.orga.ajouter(cle, it.getAmount());
                    p.getInventory().setItem(slot, null);
                    p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.6f, 1f);
                    render();
                }
                return;
            }
            if (raw == PREC) {
                page--;
                tic(p);
                render();
            } else if (raw == SUIV) {
                page++;
                tic(p);
                render();
            } else if (raw == DEPOT) {
                int q = n.deposerTout(p, true);
                Txt.bar(p, q > 0 ? "<#7CFF8A>" + q + " objet(s) rangé(s) au coffre." : "<#9AA4B2>Rien à ranger.");
                p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.7f, 0.8f);
                render();
            } else if (raw == MARCH) {
                int q = 0;
                for (int i = 0; i < p.getInventory().getSize(); i++) {
                    ItemStack it = p.getInventory().getItem(i);
                    Produit pr = Produit.lire(it);
                    if (pr != null) {
                        n.orga.ajouter("prod:" + pr.signature(), it.getAmount());
                        q += it.getAmount();
                        p.getInventory().setItem(i, null);
                    }
                }
                Txt.bar(p, q > 0 ? "<#7CFF8A>" + q + " unité(s) de marchandise rangée(s)." : "<#9AA4B2>Tu ne portes pas de marchandise.");
                p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.7f, 0.8f);
                render();
            } else if (raw < PAGE && page * PAGE + raw < cles.size()) {
                String k = cles.get(page * PAGE + raw);
                int veut = type.isShiftClick() ? Integer.MAX_VALUE : type.isRightClick() ? 16 : 1;
                int libre = place(p, k);
                int q = n.orga.retirer(k, Math.min(veut, libre));
                if (q <= 0) {
                    Txt.bar(p, "<#E0484A>Inventaire plein.");
                    return;
                }
                for (int left = q; left > 0; ) {
                    int s = Math.min(64, left);
                    n.donner(p, Produit.objet(k, s));
                    left -= s;
                }
                p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_REMOVE_ONE, 0.6f, 1f);
                render();
            }
        }

        /** Combien d'unités de cet objet rentrent dans l'inventaire du joueur. */
        private int place(Player p, String cle) {
            ItemStack modele = Produit.skin(Produit.objet(cle, 1), n.hd(p));
            int max = modele.getMaxStackSize(), libre = 0;
            for (ItemStack it : p.getInventory().getStorageContents()) {
                if (it == null || it.getType().isAir()) {
                    libre += max;
                } else if (it.isSimilar(modele)) {
                    libre += max - it.getAmount();
                }
            }
            return libre;
        }
    }

    // ------------------------------------------------------------------ table de mélange

    static void melange(Nuit n, Player p) {
        Melange m = new Melange(n, p);
        m.render();
        p.openInventory(m.inv);
        p.playSound(p.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.5f, 1.2f);
    }

    /** Recette connue d'un produit (suite d'additifs depuis la base brute), null si inconnue. */
    static String recette(Orga o, Produit pr) {
        if (pr.effets().isEmpty()) {
            return "";
        }
        return o.data.recettes.get(pr.signature());
    }

    /** Note une recette au carnet (si elle est nouvelle ou plus courte). Renvoie vrai si elle est nouvelle. */
    static boolean noter(Orga o, Produit avant, Additif a, Produit apres) {
        String r = recette(o, avant);
        if (r == null || apres.effets().isEmpty()) {
            return false;
        }
        String nouvelle = r.isEmpty() ? a.name() : r + "," + a.name();
        String ancienne = o.data.recettes.get(apres.signature());
        if (ancienne != null && ancienne.split(",").length <= nouvelle.split(",").length) {
            return false;
        }
        o.data.recettes.put(apres.signature(), nouvelle);
        // carnet limité aux 90 recettes les plus chères
        if (o.data.recettes.size() > 90) {
            String pire = null;
            int pv = Integer.MAX_VALUE;
            for (String sig : o.data.recettes.keySet()) {
                Produit x = Produit.depuisSignature(sig);
                int v = x == null ? -1 : x.valeur();
                if (v < pv) {
                    pv = v;
                    pire = sig;
                }
            }
            o.data.recettes.remove(pire);
        }
        return ancienne == null;
    }

    static final class Melange implements Holder {
        private static final int PROD0 = 9, PRODN = 18, ADD0 = 28, CARNET = 46, APERCU = 48, UN = 50, TOUT = 52;
        final Nuit n;
        final Player p;
        final boolean hd;
        final Inventory inv;
        String sel;
        Additif add;
        private final List<String> sigs = new ArrayList<>();
        private final Map<String, Produit> produits = new LinkedHashMap<>();

        Melange(Nuit n, Player p) {
            this.n = n;
            this.p = p;
            this.hd = hd(n, p);
            this.inv = Bukkit.createInventory(this, 54, titre(hd, G_MELANGE, "<#3A4250>Table de mélange"));
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }

        void render() {
            inv.clear();
            sigs.clear();
            produits.clear();
            Map<String, Integer> stock = n.stock(p);
            for (ItemStack it : p.getInventory().getStorageContents()) {
                Produit pr = Produit.lire(it);
                if (pr != null) {
                    produits.putIfAbsent(pr.signature(), pr);
                }
            }
            if (sel != null && !stock.containsKey(sel)) {
                sel = null;
            }
            if (add != null && n.additifs(p, add) == 0) {
                add = null;
            }
            inv.setItem(4, icon(Material.BREWING_STAND, 1, "<#62CFF4><b>Table de mélange</b>",
                    "<#9AA4B2>1. Choisis une marchandise que tu portes (haut).", "<#9AA4B2>2. Choisis un additif que tu portes (milieu).",
                    "<#9AA4B2>3. Regarde le résultat, puis mélange.", "<#5A6270>1 additif par unité mélangée.",
                    "<#5A6270>Chaque nouvelle recette va au carnet."));
            int i = 0;
            for (Map.Entry<String, Integer> e : stock.entrySet()) {
                if (i >= PRODN) {
                    break;
                }
                Produit pr = produits.get(e.getKey());
                boolean on = e.getKey().equals(sel);
                inv.setItem(PROD0 + i, withLore(Produit.skin(pr.item(Math.min(64, e.getValue())), hd), on, "<#E6EDF3>Tu en as : " + e.getValue(),
                        on ? "<#7CFF8A>▶ sélectionné" : "<#62CFF4>Clic pour choisir"));
                sigs.add(e.getKey());
                i++;
            }
            if (stock.isEmpty()) {
                inv.setItem(PROD0 + 4, icon(Material.BARRIER, 1, "<#9AA4B2>Aucune marchandise sur toi",
                        "<#9AA4B2>Récolte un bac, un condenseur,", "<#9AA4B2>ou prends-en au coffre."));
            }
            inv.setItem(27, zone(hd, Material.PAPER, "<#E6EDF3><b>Additifs</b>", "<#9AA4B2>Rix en vend."));
            int k = 0;
            for (Additif a : Additif.values()) {
                int q = n.additifs(p, a);
                boolean on = a == add;
                ItemStack ic = q > 0 ? Produit.skin(Produit.additif(a, Math.min(64, q)), hd) : icon(Material.GRAY_DYE, 1, "<#5A6270>" + a.nom,
                        "<#5A6270>Ajoute " + a.ajoute.joli(), "<#5A6270>Tu n'en portes pas : Rix en vend, le coffre en garde.");
                inv.setItem(ADD0 + k++, q > 0 ? withLore(ic, on, "<#E6EDF3>Tu en as : " + q,
                        on ? "<#7CFF8A>▶ sélectionné" : "<#62CFF4>Clic pour choisir") : ic);
            }
            inv.setItem(CARNET, icon(Material.KNOWLEDGE_BOOK, 1, "<#E0B864><b>Carnet de recettes</b> <#9AA4B2>(" + n.orga.data.recettes.size() + ")",
                    "<#9AA4B2>Refais d'un clic une recette connue,", "<#9AA4B2>avec les additifs de ton sac ou du coffre."));
            Produit pr = sel == null ? null : produits.get(sel);
            if (pr == null || add == null) {
                inv.setItem(APERCU, icon(Material.GLASS_BOTTLE, 1, "<#9AA4B2>Résultat", "<#9AA4B2>Choisis une marchandise et un additif."));
            } else {
                Produit res = pr.avec(add);
                int q = Math.min(stock.getOrDefault(sel, 0), n.additifs(p, add));
                if (res.signature().equals(pr.signature())) {
                    inv.setItem(APERCU, icon(Material.GLASS_BOTTLE, 1, "<#9AA4B2>Aucun changement",
                            "<#9AA4B2>Cet additif n'apporte rien de plus", "<#9AA4B2>(propriété déjà là, ou 6 au maximum)."));
                } else {
                    boolean connue = n.orga.data.recettes.containsKey(res.signature());
                    inv.setItem(APERCU, withLore(Produit.skin(res.item(1), hd), true, "", "<#9AA4B2>Avant : " + pr.effetsJolis() + " <#5A6270>(" + pr.valeur() + " cr)",
                            "<#7CFF8A>Après : " + res.effetsJolis() + " <#5A6270>(" + res.valeur() + " cr)",
                            connue ? "<#5A6270>Déjà au carnet." : "<#E0B864>Nouvelle recette !"));
                    inv.setItem(UN, icon(Material.LIME_DYE, 1, "<#7CFF8A><b>Mélanger 1</b>", "<#9AA4B2>1 unité + 1 " + add.nom));
                    inv.setItem(TOUT, icon(Material.EMERALD, Math.min(64, q), "<#7CFF8A><b>Mélanger tout (" + q + ")</b>",
                            "<#9AA4B2>" + q + " unité(s) + " + q + " " + add.nom));
                }
            }
            fill(inv, hd);
        }

        @Override
        public void click(Player who, int raw, ClickType type, boolean haut, int slot) {
            if (!haut || !valide(n, p)) {
                return;
            }
            if (raw >= PROD0 && raw < PROD0 + sigs.size()) {
                sel = sigs.get(raw - PROD0);
                tic(p);
                render();
            } else if (raw >= ADD0 && raw < ADD0 + Additif.values().length) {
                Additif a = Additif.values()[raw - ADD0];
                if (n.additifs(p, a) > 0) {
                    add = a;
                    tic(p);
                    render();
                }
            } else if (raw == UN || raw == TOUT) {
                melanger(raw == UN);
            } else if (raw == CARNET) {
                tic(p);
                Bukkit.getScheduler().runTask(n.plugin, () -> {
                    if (p.isOnline() && !n.ended && n.joueurs.containsKey(p.getUniqueId())) {
                        carnet(n, p);
                    }
                });
            }
        }

        private void melanger(boolean un) {
            Produit pr = sel == null ? null : produits.get(sel);
            if (pr == null || add == null) {
                return;
            }
            Produit res = pr.avec(add);
            if (res.signature().equals(pr.signature())) {
                return;
            }
            int q = Math.min(n.stock(p).getOrDefault(sel, 0), n.additifs(p, add));
            if (un) {
                q = Math.min(q, 1);
            }
            if (q <= 0) {
                render();
                return;
            }
            String sig = sel;
            Additif a = add;
            n.retirer(p, it -> {
                Produit x = Produit.lire(it);
                return x != null && x.signature().equals(sig);
            }, q);
            n.retirer(p, it -> Produit.lireAdditif(it) == a, q);
            for (int left = q; left > 0; left -= 64) {
                n.donner(p, res.item(Math.min(64, left)));
            }
            p.playSound(p.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 1f);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.4f);
            decouverte(n, p, res, noter(n.orga, pr, a, res));
            sel = res.signature(); // on enchaîne sur le résultat
            render();
        }
    }

    /** Annonce d'une nouvelle recette (et d'une propriété jamais vue par l'organisation). */
    static void decouverte(Nuit n, Player p, Produit res, boolean nouvelle) {
        if (!nouvelle) {
            return;
        }
        Set<Effet> connus = new HashSet<>();
        for (String sig : n.orga.data.recettes.keySet()) {
            if (sig.equals(res.signature())) {
                continue;
            }
            Produit x = Produit.depuisSignature(sig);
            if (x != null) {
                connus.addAll(x.effets());
            }
        }
        List<String> neuves = new ArrayList<>();
        for (Effet e : res.effets()) {
            if (!connus.contains(e) && n.decouvertes.add(e)) {
                neuves.add(e.joli());
            }
        }
        if (!neuves.isEmpty()) {
            Txt.title(p, "<#E0B864><b>DÉCOUVERTE</b>", String.join("<#5A6270>, </#5A6270>", neuves), 2200);
            n.broadcast("<#E0B864>" + p.getName() + " a découvert</#E0B864> " + String.join("<#5A6270>, </#5A6270>", neuves)
                    + " <#9AA4B2>: " + res.base().joli() + " <#9AA4B2>à " + Txt.cr(res.valeur()) + " l'unité, recette notée au carnet.");
            n.sonAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.6f);
        } else {
            Txt.bar(p, "<#E0B864>Nouvelle recette au carnet :</#E0B864> " + res.effetsJolis() + " <#5A6270>· " + Txt.cr(res.valeur()));
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
        }
    }

    // ------------------------------------------------------------------ carnet de recettes

    static void carnet(Nuit n, Player p) {
        Carnet c = new Carnet(n, p);
        c.render();
        p.openInventory(c.inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1f);
    }

    static final class Carnet implements Holder {
        private static final int PAGE = 45, PREC = 45, RETOUR = 49, SUIV = 53;
        final Nuit n;
        final Player p;
        final boolean hd;
        final Inventory inv;
        private final List<String> sigs = new ArrayList<>();
        int page;

        Carnet(Nuit n, Player p) {
            this.n = n;
            this.p = p;
            this.hd = hd(n, p);
            this.inv = Bukkit.createInventory(this, 54, titre(hd, G_CARNET, "<#3A4250>Carnet de recettes"));
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }

        void render() {
            inv.clear();
            sigs.clear();
            for (String sig : n.orga.data.recettes.keySet()) {
                if (Produit.depuisSignature(sig) != null) {
                    sigs.add(sig);
                }
            }
            sigs.sort((a, b) -> Integer.compare(Produit.depuisSignature(b).valeur(), Produit.depuisSignature(a).valeur()));
            int pages = Math.max(1, (sigs.size() + PAGE - 1) / PAGE);
            page = Math.max(0, Math.min(page, pages - 1));
            for (int i = 0; i < PAGE && page * PAGE + i < sigs.size(); i++) {
                String sig = sigs.get(page * PAGE + i);
                Produit pr = Produit.depuisSignature(sig);
                String[] seq = n.orga.data.recettes.get(sig).split(",");
                StringBuilder chemin = new StringBuilder(pr.base().joli());
                for (String s : seq) {
                    try {
                        chemin.append(" <#5A6270>→</#5A6270> <#E6EDF3>").append(Additif.valueOf(s).nom);
                    } catch (IllegalArgumentException ignored) {
                        chemin.append(" <#5A6270>→ ?");
                    }
                }
                int q = possible(sig);
                inv.setItem(i, withLore(Produit.skin(pr.item(1), hd), false, "", "<#9AA4B2>Recette : " + chemin,
                        q > 0 ? "<#7CFF8A>Tu peux en faire " + q : "<#E0484A>Il manque la base brute ou des additifs",
                        "<#62CFF4>Clic : fabriquer 1 · Maj ou droit : le maximum"));
            }
            if (sigs.isEmpty()) {
                inv.setItem(22, icon(Material.BOOK, 1, "<#9AA4B2>Carnet vide",
                        "<#9AA4B2>Mélange à la table : chaque nouvelle", "<#9AA4B2>combinaison est notée ici."));
            }
            if (page > 0) {
                inv.setItem(PREC, icon(Material.ARROW, 1, "<#E6EDF3>Page précédente"));
            }
            if (page < pages - 1) {
                inv.setItem(SUIV, icon(Material.ARROW, 1, "<#E6EDF3>Page suivante"));
            }
            inv.setItem(RETOUR, icon(Material.BREWING_STAND, 1, "<#62CFF4><b>Retour à la table</b>",
                    "<#9AA4B2>La base brute et les additifs sont pris", "<#9AA4B2>dans ton sac, puis dans le coffre."));
            fill(inv, hd);
        }

        private Additif[] suite(String sig) {
            String[] seq = n.orga.data.recettes.get(sig).split(",");
            Additif[] out = new Additif[seq.length];
            for (int i = 0; i < seq.length; i++) {
                try {
                    out[i] = Additif.valueOf(seq[i]);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
            return out;
        }

        private int brut(Base b) {
            return n.stock(p).getOrDefault(b.name(), 0) + n.orga.stock("prod:" + b.name());
        }

        private int dispo(Additif a) {
            return n.additifs(p, a) + n.orga.stock("add:" + a.name());
        }

        /** Combien d'unités on peut fabriquer avec ce qu'on porte et ce que contient le coffre. */
        private int possible(String sig) {
            Additif[] s = suite(sig);
            Produit pr = Produit.depuisSignature(sig);
            if (s == null || pr == null) {
                return 0;
            }
            Map<Additif, Integer> besoin = new EnumMap<>(Additif.class);
            for (Additif a : s) {
                besoin.merge(a, 1, Integer::sum);
            }
            int q = brut(pr.base());
            for (Map.Entry<Additif, Integer> e : besoin.entrySet()) {
                q = Math.min(q, dispo(e.getKey()) / e.getValue());
            }
            return q;
        }

        @Override
        public void click(Player who, int raw, ClickType type, boolean haut, int slot) {
            if (!haut || !valide(n, p)) {
                return;
            }
            if (raw == PREC) {
                page--;
                tic(p);
                render();
                return;
            }
            if (raw == SUIV) {
                page++;
                tic(p);
                render();
                return;
            }
            if (raw == RETOUR) {
                tic(p);
                Bukkit.getScheduler().runTask(n.plugin, () -> {
                    if (p.isOnline() && !n.ended && n.joueurs.containsKey(p.getUniqueId())) {
                        melange(n, p);
                    }
                });
                return;
            }
            if (raw >= PAGE || page * PAGE + raw >= sigs.size()) {
                return;
            }
            String sig = sigs.get(page * PAGE + raw);
            Additif[] s = suite(sig);
            Produit cible = Produit.depuisSignature(sig);
            int q = possible(sig);
            if (type.isLeftClick() && !type.isShiftClick()) {
                q = Math.min(q, 1);
            }
            if (s == null || cible == null || q <= 0) {
                Txt.bar(p, "<#E0484A>Il te manque de quoi la fabriquer.</#E0484A> <#9AA4B2>(base brute et additifs : sac ou coffre)");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
                return;
            }
            // on rejoue la recette pour vérifier qu'elle donne bien ce produit
            Produit res = new Produit(cible.base(), List.of());
            for (Additif a : s) {
                res = res.avec(a);
            }
            if (!res.signature().equals(sig)) {
                n.orga.data.recettes.remove(sig);
                render();
                return;
            }
            prendre(p, "prod:" + cible.base().name(), cible.base().name(), q);
            Map<Additif, Integer> besoin = new EnumMap<>(Additif.class);
            for (Additif a : s) {
                besoin.merge(a, q, Integer::sum);
            }
            for (Map.Entry<Additif, Integer> e : besoin.entrySet()) {
                Additif a = e.getKey();
                int dansSac = Math.min(e.getValue(), n.additifs(p, a));
                n.retirer(p, it -> Produit.lireAdditif(it) == a, dansSac);
                n.orga.retirer("add:" + a.name(), e.getValue() - dansSac);
            }
            for (int left = q; left > 0; left -= 64) {
                n.donner(p, res.item(Math.min(64, left)));
            }
            Txt.bar(p, "<#7CFF8A>+" + q + " " + res.base().joli() + " <#5A6270>·</#5A6270> " + res.effetsJolis());
            p.playSound(p.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 1f);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.4f);
            render();
        }

        /** Retire q unités brutes : sac d'abord, puis coffre. */
        private void prendre(Player p, String cle, String sig, int q) {
            int dansSac = Math.min(q, n.stock(p).getOrDefault(sig, 0));
            n.retirer(p, it -> {
                Produit x = Produit.lire(it);
                return x != null && x.signature().equals(sig);
            }, dansSac);
            n.orga.retirer(cle, q - dansSac);
        }
    }
}
