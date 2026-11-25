package fr.orbit.tycoon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Boutique du spawn : un PNJ astronaute (mannequin au skin du pack) sous une arche dorée, sur chacune des
 * 5 avenues qui mènent aux anneaux-portails. Clic droit sur un PNJ ou /boutique : ouvre le menu.
 * Rayons payés en Plasma : Grades (Grades.java), Items (Collecteur orbital), Clés de coffres, Titres, HDV.
 * Blocs posés une fois par « /tycoon boutique poser » ; PNJ et titres recréés au chargement de leur chunk.
 */
final class Boutique implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int Y = Zone.Y;
    private static final String TAG = "orbit_boutique";
    /** Distance au centre du spawn, et décalage du PNJ sur le côté de l'avenue. */
    private static final double R = 34, COTE = 7.5, ARCHE = 9.5;

    private final OrbitTycoon plugin;
    private final int n = Zone.farms().size();
    private final Entity[] pnjs = new Entity[n];
    private final TextDisplay[] titres = new TextDisplay[n];

    Boutique(OrbitTycoon plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------------ positions

    /** Direction de l'avenue i (vers l'anneau-portail de la zone i). */
    private static double angle(int i) { return Math.toRadians(Zone.farms().get(i).angle); }

    /** Point (x, z) de l'avenue i, à la distance r du centre, décalé de q sur le côté (tangente). */
    private static double[] point(int i, double r, double q) {
        double a = angle(i);
        return new double[]{Math.cos(a) * r - Math.sin(a) * q, Math.sin(a) * r + Math.cos(a) * q};
    }

    private Location posPnj(int i) {
        double[] p = point(i, R, COTE);
        return new Location(plugin.monde(), Math.floor(p[0]) + 0.5, Y + 1, Math.floor(p[1]) + 0.5,
                Zone.yaw(-Math.cos(angle(i)), -Math.sin(angle(i))), 0f);
    }

    /** Point de téléportation : au milieu de la 1re avenue, tourné vers le PNJ Boutique. */
    Location pointTp() {
        double[] p = point(0, R, 2);
        return new Location(plugin.monde(), Math.floor(p[0]) + 0.5, Y + 1, Math.floor(p[1]) + 0.5,
                Zone.yaw(-Math.sin(angle(0)), Math.cos(angle(0))), 0f);
    }

    private Location posTitre(int i) {
        double[] p = point(i, R, 0);
        return new Location(plugin.monde(), Math.floor(p[0]) + 0.5, Y + 12, Math.floor(p[1]) + 0.5);
    }

    // ------------------------------------------------------------------ cycle de vie

    void demarrer() {
        for (Entity e : plugin.monde().getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        rafraichir();
    }

    void arreter() {
        for (int i = 0; i < n; i++) {
            if (pnjs[i] != null && pnjs[i].isValid()) pnjs[i].remove();
            if (titres[i] != null && titres[i].isValid()) titres[i].remove();
        }
    }

    /** Vrai si l'avenue i a une boutique (config boutique.avenues = identifiants des zones). */
    private boolean active(int i) {
        List<String> l = plugin.getConfig().getStringList("boutique.avenues");
        return l.isEmpty() ? Zone.farms().get(i) == Zone.MOBZONE : l.contains(Zone.farms().get(i).id);
    }

    private void rafraichir() {
        for (int i = 0; i < n; i++) {
            if (active(i)) rafraichirAvenue(i);
            else {
                if (pnjs[i] != null && pnjs[i].isValid()) pnjs[i].remove();
                if (titres[i] != null && titres[i].isValid()) titres[i].remove();
            }
        }
    }

    /** Après /tycoon reload : PNJ des avenues ajoutées ou retirées. */
    void recharger() { rafraichir(); }

    private void rafraichirAvenue(int i) {
        Location l = posPnj(i);
        if (l.isChunkLoaded() && (pnjs[i] == null || !pnjs[i].isValid())) pnjs[i] = l.getWorld().spawn(l, Mannequin.class, m -> {
            Pnjs.preparer(m, l, "astronaute", "<gradient:#62CFF4:#E0B864><bold>Boutique</bold></gradient>");
            m.addScoreboardTag(TAG);
        });
        Location t = posTitre(i);
        if (t.isChunkLoaded() && (titres[i] == null || !titres[i].isValid())) titres[i] = t.getWorld().spawn(t, TextDisplay.class, d -> {
            d.text(MM.deserialize("<gradient:#62CFF4:#E0B864><bold>BOUTIQUE ORBIT</bold></gradient>\n<gray>Grades · Armures · Items\n<gray>Clés · Titres"));
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setShadowed(true);
            d.setViewRange(3f);
            d.setBackgroundColor(Color.fromARGB(130, 8, 16, 32));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(2.2f, 2.2f, 2.2f), new AxisAngle4f()));
            d.addScoreboardTag(TAG);
        });
    }

    @EventHandler
    public void chunk(ChunkLoadEvent e) {
        if (!e.getWorld().equals(plugin.monde())) return;
        for (int i = 0; i < n; i++)
            for (Location l : new Location[]{posPnj(i), posTitre(i)})
                if (l.getBlockX() >> 4 == e.getChunk().getX() && l.getBlockZ() >> 4 == e.getChunk().getZ()) {
                    Bukkit.getScheduler().runTask(plugin, this::rafraichir);
                    return;
                }
    }

    @EventHandler
    public void degats(EntityDamageEvent e) {
        if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    @EventHandler
    public void clicPnj(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getScoreboardTags().contains(TAG)) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) ouvrir(e.getPlayer());
    }

    @EventHandler
    public void clicPnjPrecis(PlayerInteractAtEntityEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ menu

    private static final class Menu implements InventoryHolder {
        private Inventory inv;
        private final java.util.Map<Integer, java.util.function.Consumer<Player>> actions = new java.util.HashMap<>();
        /** Action du clic droit (rayon Clés : ×5). */
        private final java.util.Map<Integer, java.util.function.Consumer<Player>> droit = new java.util.HashMap<>();
        @Override public Inventory getInventory() { return inv; }
    }

    private static final String PLASMA = "<#C77DFF>";
    /** Prix des clés en Plasma (dans l'ordre de coffres.noms : Vote, Farm, Event, Saturne, Neptune, Pets). */
    private static final long[] PRIX_CLES = {50, 80, 150, 500, 1000, 300};

    private Menu menu(Player p, int lignes, String titre) {
        Menu h = new Menu();
        h.inv = Bukkit.createInventory(h, lignes * 9, Habillage.titre(plugin, p, lignes, titre));
        return h;
    }

    private void montrer(Player p, Menu h) {
        Habillage.fond(plugin, p, h.inv);
        p.openInventory(h.inv);
    }

    private void poser(Menu h, int slot, ItemStack it, java.util.function.Consumer<Player> action) {
        h.inv.setItem(slot, it);
        if (action != null) h.actions.put(slot, action);
    }

    private long plasma(Player p) { return plugin.monnaies().solde(p.getUniqueId(), Monnaies.Monnaie.PLASMA); }

    private ItemStack soldePlasma(Player p) {
        return objet(Material.NETHER_STAR, PLASMA + "<bold>Ton Plasma", "<white>" + Monnaies.Monnaie.PLASMA.format(plasma(p)),
                "", "<gray>La monnaie premium de la station.");
    }

    private ItemStack retour() { return objet(Material.ARROW, "<#E0B864><bold>← Retour", "<gray>Rayons de la boutique"); }

    private static ItemStack modele(ItemStack it, String id) {
        it.editMeta(m -> m.setItemModel(new org.bukkit.NamespacedKey("orbittycoon", id)));
        return it;
    }

    private static String pct(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.FRANCE, "%.1f", v);
    }

    private static final String VOIR = "<#E0B864>➜ Clic pour voir le rayon";

    /** Accueil : les 7 rayons de la boutique, sur 6 lignes (en-tête, 2 rangées de rayons, solde). */
    void ouvrir(Player p) {
        Menu h = menu(p, 6, "Boutique Orbit");
        Equipements eq = plugin.equipements();
        Butin butin = plugin.butin();

        // En-tête.
        poser(h, 4, modele(objet(Material.BEACON, "<gradient:#62CFF4:#E0B864><bold>BOUTIQUE ORBIT</bold></gradient>",
                "<gray>Bienvenue à bord, <white>" + p.getName() + "<gray> !",
                "<gray>Tout se paie en " + PLASMA + "Plasma<gray>.", "",
                "<dark_gray>Aussi au PNJ Boutique du spawn."), "nav_boutique_ouvrir"), null);

        // Rangée 1 : Grades, Armures, Items.
        Grades.Grade g = plugin.grades().grade(p);
        ItemStack grades = objet(Material.NETHER_STAR, "<#E0B864><bold>Grades",
                "<gray>Pilote · Commandant · Amiral · Astral",
                "<gray>Bonus de gains, vol sur ton île,", "<gray>commandes, titre et particules.", "",
                "<gray>Ton grade : " + (g == null ? "<dark_gray>aucun" : g.joli), "", VOIR);
        poser(h, 20, modele(grades, "nav_boutique_grades"), this::ouvrirGrades);

        List<Equipements.Premium> enVente = Equipements.premiumsBoutique();
        Equipements.Premium haut = enVente.get(enVente.size() - 1);
        ItemStack armures = objet(Material.LEATHER_CHESTPLATE, "<#C77DFF><bold>Armures premium",
                "<gray>Combinaison <#C77DFF>Nébuleuse</#C77DFF> et armure <#E0B864>Zénith</#E0B864>.",
                "<gray>Bonus de Crédits, Flux et XP", "<gray>avec <white>tous</white> les outils.",
                "<gray>Jusqu'à <#7CFC9A>+" + pct(eq.bonusSet(haut)) + " %<gray> en set complet.", "", VOIR);
        poser(h, 22, modele(armures, "nav_boutique_armures"), this::ouvrirArmures);

        ItemStack items = objet(Material.CHEST, "<#62CFF4><bold>Items",
                "<gray>Collecteurs orbitaux,", "<gray>baguettes de vente,",
                "<gray>boosters de Crédits, Flux et XP.", "", VOIR);
        poser(h, 24, modele(items, "nav_boutique_items"), this::ouvrirItems);

        // Rangée 2 : Clés, Titres, Hôtel des ventes.
        ItemStack cles = objet(Material.TRIPWIRE_HOOK, "<#62CFF4><bold>Clés de coffres",
                "<gray>Les clés des " + Coffres.NB + " coffres orbitaux.",
                "<gray>À l'unité, par 5, ou en lot ×" + PACK, "<gray>avec <#7CFC9A>-" + reductionPack() + " %<gray> de réduction.", "", VOIR);
        poser(h, 29, modele(cles, "nav_boutique_cles"), this::ouvrirCles);

        int total = butin.titres().size(), possedes = 0;
        for (String id : butin.titres().keySet()) if (butin.possede(p.getUniqueId(), id)) possedes++;
        ItemStack titres = objet(Material.NAME_TAG, "<#E0B864><bold>Titres",
                "<gray>Les titres affichés devant ton pseudo :", "<gray>achète-les ou choisis lequel porter.", "",
                "<gray>Collection : <white>" + possedes + "<dark_gray>/" + total, "", VOIR);
        poser(h, 31, modele(titres, "nav_titre_galaxie"), this::ouvrirTitres);

        poser(h, 33, objet(Material.EMERALD, "<#7CFC9A><bold>Hôtel des ventes",
                "<gray>Achète et vends entre joueurs", "<gray>en <#E0B864>Crédits orbitaux</#E0B864>.", "",
                "<#E0B864>➜ Clic pour ouvrir <dark_gray>(/hdv)"), j -> plugin.hdv().ouvrir(j, 0));

        // Bas : solde et fermer.
        poser(h, 49, soldePlasma(p), null);
        poser(h, 53, objet(Material.BARRIER, "<#FF4D4D><bold>Fermer"), Player::closeInventory);
        montrer(p, h);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
    }

    /** Rayon Armures : une ligne par armure premium (4 pièces à l'unité + le set complet). */
    void ouvrirArmures(Player p) {
        Equipements eq = plugin.equipements();
        Menu h = menu(p, 5, "Armures premium");
        Equipements.Premium[] tout = Equipements.premiumsBoutique().toArray(new Equipements.Premium[0]);
        for (int r = 0; r < tout.length && r < 2; r++) {
            Equipements.Premium a = tout[r];
            int base = 10 + r * 18;
            long piece = eq.prixPiece(a), set = eq.prixSet(a);
            Equipements.Piece[] pieces = Equipements.Piece.values();
            for (int k = 0; k < pieces.length; k++) {
                Equipements.Piece pc = pieces[k];
                ItemStack it = eq.premium(a, pc);
                it.editMeta(m -> {
                    List<Component> l = new ArrayList<>();
                    for (String s : eq.lorePremium(a, false)) if (!s.startsWith("<dark_gray>Vendue")) l.add(ligne(s));
                    l.add(ligne("<gray>Prix : " + PLASMA + Monnaies.Monnaie.PLASMA.format(piece)));
                    l.add(ligne("<#E0B864>➜ Clic pour acheter"));
                    m.lore(l);
                });
                poser(h, base + k, it, j -> confirmerArmure(j, a, pc));
            }
            ItemStack s = eq.premium(a, Equipements.Piece.PLASTRON);
            long economie = piece * pieces.length - set;
            s.editMeta(m -> {
                m.itemName(MM.deserialize("<" + a.couleur + "><bold>" + a.type + " " + a.nom + " · set complet"));
                List<Component> l = new ArrayList<>();
                l.add(ligne("<gray>Les <white>4 pièces</white> d'un coup."));
                l.add(ligne("<#E0B864><bold>Set complet (4/4) : +" + pct(eq.bonusSet(a)) + " %</bold>"));
                l.add(ligne("<dark_gray>Crédits, Flux et XP, tous les outils."));
                l.add(Component.empty());
                if (economie > 0) l.add(ligne("<gray>Prix normal : <dark_gray><st>" + Monnaies.Monnaie.PLASMA.format(piece * pieces.length) + "</st>"));
                l.add(ligne("<gray>Prix : " + PLASMA + Monnaies.Monnaie.PLASMA.format(set)));
                if (economie > 0) l.add(ligne("<green>Tu économises " + Monnaies.Monnaie.PLASMA.format(economie) + "<green> !"));
                l.add(Component.empty());
                l.add(ligne("<#E0B864>➜ Clic pour acheter le set"));
                m.lore(l);
            });
            poser(h, base + 6, s, j -> confirmerArmure(j, a, null));
        }
        poser(h, 36, retour(), this::ouvrir);
        poser(h, 40, soldePlasma(p), null);
        montrer(p, h);
    }

    /** Achat d'une pièce premium (pc) ou du set complet (pc == null). */
    private void confirmerArmure(Player p, Equipements.Premium a, Equipements.Piece pc) {
        Equipements eq = plugin.equipements();
        long prix = pc == null ? eq.prixSet(a) : eq.prixPiece(a);
        String nom = pc == null ? a.type + " " + a.nom + " (set complet)" : pc.nom + " " + a.nom;
        ItemStack vitrine = eq.premium(a, pc == null ? Equipements.Piece.PLASTRON : pc);
        plugin.confirmer().ouvrir(p, nom, vitrine,
                List.of("<gray>Payer " + PLASMA + Monnaies.Monnaie.PLASMA.format(prix), "<gray>pour : <white>" + nom),
                () -> {
                    if (!plugin.monnaies().retirer(p.getUniqueId(), Monnaies.Monnaie.PLASMA, prix)) {
                        OrbitTycoon.msg(p, "<#FF4D4D>Il te faut " + Monnaies.Monnaie.PLASMA.format(prix) + "<#FF4D4D>.");
                        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.6f, 1.3f);
                    } else {
                        List<ItemStack> l = new ArrayList<>();
                        if (pc == null) for (Equipements.Piece x : Equipements.Piece.values()) l.add(eq.premium(a, x));
                        else l.add(eq.premium(a, pc));
                        donner(p, l);
                        plugin.getLogger().info(p.getName() + " achète " + nom + " pour " + prix + " Plasma.");
                        OrbitTycoon.msg(p, "<gray>Achat réussi : <white>" + nom + "<gray> !");
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
                    }
                    ouvrirArmures(p);
                }, () -> ouvrirArmures(p));
    }

    /** Rayon Grades : les 4 grades, prix à payer (différence avec le grade actuel). */
    void ouvrirGrades(Player p) {
        Grades gr = plugin.grades();
        Menu h = menu(p, 4, "Grades");
        Material[] icones = {Material.IRON_INGOT, Material.DIAMOND, Material.GOLD_BLOCK, Material.NETHER_STAR};
        int[] slots = {10, 12, 14, 16};
        Grades.Grade actuel = gr.grade(p);
        for (Grades.Grade g : Grades.Grade.values()) {
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>Grade du Tycoon");
            lore.add("");
            lore.addAll(gr.avantages(g));
            lore.add("");
            long prix = gr.prixPour(p, g);
            // Prix toujours affiché tout en bas, sous les caractéristiques.
            lore.add("<#E0B864><bold>Prix :</bold> " + PLASMA + Monnaies.Monnaie.PLASMA.format(gr.prix(g)));
            // Grade requis : toujours affiché (sauf Pilote, le premier).
            if (g.ordinal() > 0) lore.add("<gray>Requis : le grade " + Grades.Grade.values()[g.ordinal() - 1].joli);
            lore.add("");
            Grades.Grade avant = gr.manquant(p, g);
            if (prix < 0) lore.add(actuel == g ? "<#7CFC9A>✔ Ton grade actuel" : "<#7CFC9A>✔ Inclus dans ton grade");
            else if (avant != null) lore.add("<#FF4D4D>🔒 Verrouillé <gray>(achète d'abord " + avant.joli + "<gray>)");
            else lore.add("<#E0B864>➜ Clic pour acheter");
            ItemStack it = objet(icones[g.ordinal()], g.joli, lore.toArray(new String[0]));
            it.editMeta(m -> m.setItemModel(new org.bukkit.NamespacedKey("orbittycoon", "nav_grade_" + g.id)));
            if (actuel == g) it.editMeta(m -> m.setEnchantmentGlintOverride(true));
            poser(h, slots[g.ordinal()], it, prix < 0 ? null : avant != null ? j -> {
                OrbitTycoon.msg(j, "<#FF4D4D>Achète d'abord le grade " + avant.joli + " <#FF4D4D>.");
                j.playSound(j.getLocation(), org.bukkit.Sound.BLOCK_CHEST_LOCKED, 0.6f, 1.3f);
            } : j -> confirmerGrade(j, g));
        }
        poser(h, 27, retour(), this::ouvrir);
        poser(h, 31, soldePlasma(p), null);
        montrer(p, h);
    }

    private void confirmerGrade(Player p, Grades.Grade g) {
        long prix = plugin.grades().prixPour(p, g);
        ItemStack insigne = objet(Material.NETHER_STAR, g.joli);
        insigne.editMeta(m -> m.setItemModel(new org.bukkit.NamespacedKey("orbittycoon", "nav_grade_" + g.id)));
        plugin.confirmer().ouvrir(p, "Grade " + g.nom, insigne,
                List.of("<gray>Payer " + PLASMA + Monnaies.Monnaie.PLASMA.format(prix), "<gray>pour devenir " + g.joli),
                () -> { plugin.grades().acheter(p, g); ouvrirGrades(p); }, () -> ouvrirGrades(p));
    }

    private static final int[] CASES_TITRES = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    /**
     * Rayon Titres : tous les titres (titres.liste), chacun avec une image spatiale (titres.icones.&lt;id&gt;).
     * À vendre si titres.boutique.&lt;id&gt; a un prix ; les titres grade_* viennent avec le grade ; les autres des coffres.
     */
    void ouvrirTitres(Player p) {
        Butin butin = plugin.butin();
        Menu h = menu(p, 6, "Titres");
        var cfg = plugin.getConfig();
        String porte = butin.affiche(p.getUniqueId());
        int i = 0;
        for (var en : butin.titres().entrySet()) {
            if (i >= CASES_TITRES.length) break;
            String id = en.getKey();
            boolean a = butin.possede(p.getUniqueId(), id), affiche = id.equals(porte);
            long prix = cfg.getLong("titres.boutique." + id, 0);
            String icone = id.startsWith("grade_") ? "nav_" + id : "nav_" + cfg.getString("titres.icones." + id, "titre_etoile");
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Devant ton pseudo dans le chat :");
            lore.add("<dark_gray>[</dark_gray>" + en.getValue() + "<dark_gray>]</dark_gray> <white>" + p.getName());
            lore.add("");
            java.util.function.Consumer<Player> action = null;
            if (affiche) lore.add("<#7CFC9A>✔ Affiché");
            else if (a) { lore.add("<#7CFC9A>✔ Possédé"); lore.add("<#62CFF4>➜ Clic pour l'afficher"); action = j -> { butin.afficher(j, id); ouvrirTitres(j); }; }
            else if (prix > 0) {
                lore.add("<gray>Prix : " + PLASMA + Monnaies.Monnaie.PLASMA.format(prix));
                lore.add("<#E0B864>➜ Clic pour acheter");
                action = j -> confirmerTitre(j, id, prix);
            } else if (id.startsWith("grade_")) lore.add("<#E0B864>Inclus avec le grade " + MM.stripTags(en.getValue()).replace("✦", "").trim());
            else lore.add("<gray>À trouver dans les coffres orbitaux.");
            ItemStack it = objet(Material.NAME_TAG, en.getValue(), lore.toArray(new String[0]));
            it.editMeta(m -> {
                m.setItemModel(new org.bukkit.NamespacedKey("orbittycoon", icone));
                if (affiche) m.setEnchantmentGlintOverride(true);
            });
            poser(h, CASES_TITRES[i++], it, action);
        }
        poser(h, 45, retour(), this::ouvrir);
        poser(h, 48, objet(Material.BARRIER, "<#FF4D4D>Ne plus afficher de titre", "<dark_gray>(/titre)"),
                j -> { j.closeInventory(); j.performCommand("titre"); });
        poser(h, 50, soldePlasma(p), null);
        montrer(p, h);
    }

    private void confirmerTitre(Player p, String id, long prix) {
        String texte = plugin.butin().texteTitre(id);
        ItemStack it = objet(Material.NAME_TAG, texte);
        it.editMeta(m -> m.setItemModel(new org.bukkit.NamespacedKey("orbittycoon",
                "nav_" + plugin.getConfig().getString("titres.icones." + id, "titre_etoile"))));
        plugin.confirmer().ouvrir(p, "Titre", it, List.of("<gray>Payer " + PLASMA + Monnaies.Monnaie.PLASMA.format(prix), "<gray>pour le titre " + texte),
                () -> {
                    if (plugin.butin().possede(p.getUniqueId(), id)) { ouvrirTitres(p); return; }
                    if (!plugin.monnaies().retirer(p.getUniqueId(), Monnaies.Monnaie.PLASMA, prix)) {
                        OrbitTycoon.msg(p, "<#FF4D4D>Il te faut " + Monnaies.Monnaie.PLASMA.format(prix) + "<#FF4D4D>.");
                        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.6f, 1.3f);
                    } else {
                        plugin.butin().debloquer(p, id);
                        plugin.getLogger().info(p.getName() + " achète le titre " + id + " pour " + prix + " Plasma.");
                    }
                    ouvrirTitres(p);
                }, () -> ouvrirTitres(p));
    }

    /** Rayon Items : objets utiles payés en Plasma (Collecteur orbital ; d'autres viendront). */
    void ouvrirItems(Player p) {
        Menu h = menu(p, 5, "Items");
        // Rangée 1 : collecteurs.
        poser(h, 11, plugin.collecteurs().vitrine(), j -> plugin.collecteurs().acheter(j));
        poser(h, 15, plugin.collecteurs().vitrineCoffre(), j -> plugin.collecteurs().acheterCoffre(j));
        // Rangée 2 : baguettes de vente.
        Baguettes bg = plugin.baguettes();
        int[] casesB = {20, 22, 24};
        for (Baguettes.Modele md : Baguettes.Modele.values()) {
            long prix = bg.prix(md);
            ItemStack it = bg.objet(md, bg.utilisations(md));
            it.editMeta(m -> {
                List<Component> l = new ArrayList<>(m.lore() == null ? List.of() : m.lore());
                l.add(Component.empty());
                l.add(ligne("<gray>Prix : " + PLASMA + Monnaies.Monnaie.PLASMA.format(prix)));
                l.add(ligne("<#E0B864>➜ Clic pour acheter"));
                m.lore(l);
            });
            poser(h, casesB[md.ordinal()], it, j -> confirmerObjet(j, md.nom + " " + Baguettes.fois(md.mult), prix,
                    () -> bg.objet(md, bg.utilisations(md))));
        }
        // Rangée 3 : boosters ×2 (30 min).
        Boosters bo = plugin.boosters();
        int[] casesBo = {29, 31, 33};
        int k = 0;
        for (Boosters.Sorte s : Boosters.Sorte.values()) {
            if (k >= casesBo.length) break;
            long prix = plugin.getConfig().getLong("boutique.boosters." + s.id, 250);
            int minutes = plugin.getConfig().getInt("boutique.boosters.minutes", 30);
            double mult = plugin.getConfig().getDouble("boutique.boosters.mult", 2.0);
            ItemStack it = bo.objet(s, mult, minutes, 1);
            it.editMeta(m -> {
                List<Component> l = new ArrayList<>(m.lore() == null ? List.of() : m.lore());
                l.add(Component.empty());
                l.add(ligne("<gray>Prix : " + PLASMA + Monnaies.Monnaie.PLASMA.format(prix)));
                l.add(ligne("<#E0B864>➜ Clic pour acheter"));
                m.lore(l);
            });
            poser(h, casesBo[k++], it, j -> confirmerObjet(j, "Booster " + s.nom + " " + Baguettes.fois(mult), prix,
                    () -> bo.objet(s, mult, minutes, 1)));
        }
        poser(h, 36, retour(), this::ouvrir);
        poser(h, 40, soldePlasma(p), null);
        montrer(p, h);
    }

    /** Achat confirmé d'un objet du rayon Items payé en Plasma. */
    private void confirmerObjet(Player p, String nom, long prix, java.util.function.Supplier<ItemStack> objet) {
        plugin.confirmer().ouvrir(p, nom, objet.get(),
                List.of("<gray>Payer " + PLASMA + Monnaies.Monnaie.PLASMA.format(prix), "<gray>pour : <white>" + nom),
                () -> {
                    if (!plugin.monnaies().retirer(p.getUniqueId(), Monnaies.Monnaie.PLASMA, prix)) {
                        OrbitTycoon.msg(p, "<#FF4D4D>Il te faut " + Monnaies.Monnaie.PLASMA.format(prix) + "<#FF4D4D>.");
                        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.6f, 1.3f);
                    } else {
                        donner(p, List.of(objet.get()));
                        plugin.getLogger().info(p.getName() + " achète " + nom + " pour " + prix + " Plasma.");
                        OrbitTycoon.msg(p, "<gray>Achat réussi : <white>" + nom + "<gray> !");
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
                    }
                    ouvrirItems(p);
                }, () -> ouvrirItems(p));
    }

    /** Rayon Clés : une clé par coffre (clic gauche ×1, clic droit ×5). */
    void ouvrirCles(Player p) {
        Menu h = menu(p, 4, "Clés de coffres");
        int[] slots = {10, 11, 12, 14, 15, 16};
        for (int n = 0; n < Coffres.NB; n++) {
            final int c = n;
            long prix = prixCle(n);
            ItemStack it = plugin.coffres().cle(n, 1);
            it.editMeta(m -> {
                List<Component> l = new ArrayList<>(m.lore() == null ? List.of() : m.lore());
                l.add(Component.empty());
                l.add(ligne("<gray>Prix : " + PLASMA + Monnaies.Monnaie.PLASMA.format(prix) + " <gray>la clé"));
                l.add(ligne("<#E0B864>➜ Clic gauche : ×1"));
                l.add(ligne("<#E0B864>➜ Clic droit : ×5"));
                m.lore(l);
            });
            poser(h, slots[n], it, j -> acheterCles(j, c, 1));
            h.droit.put(slots[n], j -> acheterCles(j, c, 5));

            // Seconde ligne : lot de 16 clés à prix réduit.
            int reduc = reductionPack();
            long sansReduc = prix * PACK;
            long avecReduc = prixPack(n);
            ItemStack pack = plugin.coffres().cle(n, PACK);
            pack.editMeta(m -> {
                List<Component> l = new ArrayList<>(m.lore() == null ? List.of() : m.lore());
                l.add(Component.empty());
                l.add(ligne("<#E0B864><bold>LOT DE ×" + PACK + "</bold> <#62CFF4>· RÉDUCTION -" + reduc + "%"));
                l.add(ligne("<gray>Prix normal : <dark_gray><st>" + Monnaies.Monnaie.PLASMA.format(sansReduc) + "</st>"));
                l.add(ligne("<gray>Prix réduit : " + PLASMA + Monnaies.Monnaie.PLASMA.format(avecReduc)));
                l.add(ligne("<green>Tu économises " + Monnaies.Monnaie.PLASMA.format(sansReduc - avecReduc) + "<green> !"));
                l.add(Component.empty());
                l.add(ligne("<#E0B864>➜ Clic : acheter ×" + PACK));
                m.lore(l);
                m.setEnchantmentGlintOverride(true);
            });
            poser(h, slots[n] + 9, pack, j -> acheterCles(j, c, PACK));
        }
        poser(h, 27, retour(), this::ouvrir);
        poser(h, 31, soldePlasma(p), null);
        montrer(p, h);
    }

    private long prixCle(int n) {
        List<Long> l = plugin.getConfig().getLongList("boutique.prix-cles");
        return n < l.size() ? l.get(n) : PRIX_CLES[Math.min(n, PRIX_CLES.length - 1)];
    }

    /** Taille du lot de clés à prix réduit (seconde ligne du rayon). */
    private static final int PACK = 16;

    /** Réduction du lot ×16 en % (config boutique.reduction-pack-cles, 20 par défaut). */
    private int reductionPack() {
        return Math.max(0, Math.min(90, plugin.getConfig().getInt("boutique.reduction-pack-cles", 20)));
    }

    private long prixPack(int n) {
        return Math.round(prixCle(n) * PACK * (100 - reductionPack()) / 100.0);
    }

    private void acheterCles(Player p, int n, int nombre) {
        long prix = nombre == PACK ? prixPack(n) : prixCle(n) * nombre;
        if (!plugin.monnaies().retirer(p.getUniqueId(), Monnaies.Monnaie.PLASMA, prix)) {
            OrbitTycoon.msg(p, "<#FF4D4D>Il te faut " + Monnaies.Monnaie.PLASMA.format(prix) + "<#FF4D4D>.");
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.6f, 1.3f);
            return;
        }
        donner(p, List.of(plugin.coffres().cle(n, nombre)));
        plugin.getLogger().info(p.getName() + " achète " + nombre + " clé(s) " + plugin.coffres().nom(n) + " pour " + prix + " Plasma.");
        OrbitTycoon.msg(p, "<gray>Tu as acheté <white>" + nombre + " Clé" + (nombre > 1 ? "s " : " ")
                + MM.escapeTags(plugin.coffres().nom(n)) + "<gray> pour " + Monnaies.Monnaie.PLASMA.format(prix) + "<gray>.");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        ouvrirCles(p);
    }

    private static void donner(Player p, List<ItemStack> l) {
        for (ItemStack reste : p.getInventory().addItem(l.toArray(new ItemStack[0])).values())
            p.getWorld().dropItemNaturally(p.getLocation(), reste);
    }

    private static Component ligne(String mm) { return MM.deserialize(mm).decoration(TextDecoration.ITALIC, false); }

    private static ItemStack objet(Material m, String nom, String... lignes) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(MM.deserialize(nom).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String l : lignes) lore.add(MM.deserialize(l).decoration(TextDecoration.ITALIC, false));
            if (!lore.isEmpty()) meta.lore(lore);
        });
        return it;
    }

    @EventHandler
    public void clicMenu(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory() || !(e.getWhoClicked() instanceof Player p)) return;
        var action = e.isRightClick() && h.droit.containsKey(e.getSlot()) ? h.droit.get(e.getSlot()) : h.actions.get(e.getSlot());
        if (action == null) return;
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
        action.accept(p);
    }

    @EventHandler
    public void glisser(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ construction (une seule fois)

    /** Arche dorée au-dessus de l'avenue, petite estrade sous le PNJ. Ne remplace que de l'air (et le sol de l'estrade). */
    int poser() {
        int poses = 0;
        for (int i = 0; i < n; i++) if (active(i)) poses += poser(i);
        return poses;
    }

    private static final java.util.Set<Material> BLOCS_ARCHE = java.util.EnumSet.of(Material.GOLD_BLOCK,
            Material.LIGHT_BLUE_CONCRETE, Material.SEA_LANTERN, Material.END_ROD, Material.LIGHT_BLUE_STAINED_GLASS);

    /**
     * Retire l'arche et l'estrade des avenues sans boutique (seulement les blocs que poser() met à ces endroits)
     * et remet le dallage d'origine du spawn sous l'estrade (même motif que Plans.base). Renvoie le nombre de blocs changés.
     */
    int retirer() {
        World w = plugin.monde();
        int changes = 0;
        for (int i = 0; i < n; i++) {
            if (active(i)) continue;
            for (int s = -1; s <= 1; s += 2) {
                double[] p = point(i, R, ARCHE * s);
                int x = (int) Math.floor(p[0]), z = (int) Math.floor(p[1]);
                for (int y = Y + 1; y <= Y + 11; y++) changes += vider(w, x, y, z);
            }
            for (double q = -ARCHE; q <= ARCHE; q += 0.5) {
                double[] p = point(i, R, q);
                int x = (int) Math.floor(p[0]), z = (int) Math.floor(p[1]);
                changes += vider(w, x, Y + 9, z) + vider(w, x, Y + 8, z);
            }
            Location l = posPnj(i);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                int x = l.getBlockX() + dx, z = l.getBlockZ() + dz;
                Block b = w.getBlockAt(x, Y, z);
                if (!BLOCS_ARCHE.contains(b.getType())) continue;
                b.setType((((x >> 2) + (z >> 2)) & 1) == 0 ? Material.SMOOTH_QUARTZ : Material.CALCITE, false);
                changes++;
            }
        }
        return changes;
    }

    private static int vider(World w, int x, int y, int z) {
        Block b = w.getBlockAt(x, y, z);
        if (!BLOCS_ARCHE.contains(b.getType())) return 0;
        b.setType(Material.AIR, false);
        return 1;
    }

    private int poser(int i) {
        World w = plugin.monde();
        int poses = 0;
        // Piliers de l'arche, de part et d'autre de l'avenue.
        for (int s = -1; s <= 1; s += 2) {
            double[] p = point(i, R, ARCHE * s);
            int x = (int) Math.floor(p[0]), z = (int) Math.floor(p[1]);
            for (int y = Y + 1; y <= Y + 9; y++) poses += air(w, x, y, z, y % 2 == 0 ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_CONCRETE);
            poses += air(w, x, Y + 10, z, Material.SEA_LANTERN);
            poses += air(w, x, Y + 11, z, Material.END_ROD);
        }
        // Poutre de l'arche.
        for (double q = -ARCHE; q <= ARCHE; q += 0.5) {
            double[] p = point(i, R, q);
            int x = (int) Math.floor(p[0]), z = (int) Math.floor(p[1]);
            poses += air(w, x, Y + 9, z, Math.abs(q) < 0.6 ? Material.SEA_LANTERN : Material.GOLD_BLOCK);
            poses += air(w, x, Y + 8, z, Material.LIGHT_BLUE_STAINED_GLASS);
        }
        // Estrade du PNJ (3 × 3), lanterne au centre.
        Location l = posPnj(i);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            Block b = w.getBlockAt(l.getBlockX() + dx, Y, l.getBlockZ() + dz);
            if (!w.getBlockAt(b.getX(), Y + 1, b.getZ()).getType().isAir()) continue;
            b.setType(dx == 0 && dz == 0 ? Material.SEA_LANTERN : (dx == 0 || dz == 0) ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_CONCRETE, false);
            poses++;
        }
        return poses;
    }

    private static int air(World w, int x, int y, int z, Material m) {
        Block b = w.getBlockAt(x, y, z);
        if (!b.getType().isAir()) return 0;
        b.setType(m, false);
        return 1;
    }
}
