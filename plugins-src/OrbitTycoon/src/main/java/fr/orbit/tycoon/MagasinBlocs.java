package fr.orbit.tycoon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Magasin de blocs (PNJ « Bâtisseur » à côté du Marchand, ou /blocs) : tous les blocs posables du jeu pour construire
 * son île /is, payés en Crédits orbitaux, rangés par catégorie. Jamais les blocs des générateurs (ni les blocs techniques
 * ou dangereux). Prix = barème par catégorie, × « magasin-blocs.multiplicateur », remplaçable bloc par bloc dans la config.
 * Les blocs achetés sont des blocs normaux (sans marque) : ils ne se revendent pas avec /vendre.
 */
final class MagasinBlocs implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int PAR_PAGE = 45;
    private static final int SLOT_RETOUR = 45, SLOT_PRECEDENT = 48, SLOT_INFO = 49, SLOT_SUIVANT = 50, SLOT_FERMER = 53;

    /** Catégories du magasin (ordre d'affichage). */
    enum Categorie {
        PIERRES("Pierres & briques", Material.STONE_BRICKS, 3),
        BOIS("Bois", Material.OAK_PLANKS, 3),
        NATURE("Terre, sable & glace", Material.GRASS_BLOCK, 2),
        VEGETATION("Végétation", Material.AZALEA_LEAVES, 5),
        COULEURS("Couleurs & verre", Material.LIGHT_BLUE_CONCRETE, 5),
        LUMIERES("Lumières", Material.LANTERN, 12),
        METAUX("Métaux & minerais", Material.GOLD_BLOCK, 30),
        FONCTIONNELS("Utilitaires & redstone", Material.CRAFTING_TABLE, 25),
        DIVERS("Divers", Material.DECORATED_POT, 10);

        final String nom;
        final Material icone;
        final long base;
        Categorie(String nom, Material icone, long base) { this.nom = nom; this.icone = icone; this.base = base; }
    }

    /** Emplacements des catégories dans le menu principal (54 cases). */
    private static final int[] SLOTS_CAT = {19, 20, 21, 22, 23, 24, 25, 30, 32};

    /** Blocs jamais vendus : techniques, impossibles à obtenir, dangereux ou qui feraient apparaître des monstres. */
    private static final Set<String> EXCLUS = Set.of("BEDROCK", "BARRIER", "LIGHT", "STRUCTURE_BLOCK", "STRUCTURE_VOID",
            "JIGSAW", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "SPAWNER", "TRIAL_SPAWNER", "VAULT",
            "END_PORTAL_FRAME", "REINFORCED_DEEPSLATE", "BUDDING_AMETHYST", "PETRIFIED_OAK_SLAB", "DRAGON_EGG", "TNT",
            "RESPAWN_ANCHOR", "CREAKING_HEART", "SUSPICIOUS_SAND", "SUSPICIOUS_GRAVEL", "TEST_BLOCK", "TEST_INSTANCE_BLOCK",
            "FROGSPAWN", "FIRE", "SOUL_FIRE", "WATER", "LAVA", "END_GATEWAY", "END_PORTAL", "NETHER_PORTAL", "FARMLAND",
            "DIRT_PATH", "SCULK_SHRIEKER", "CHORUS_FLOWER");

    /** Prix fixés à la main (le reste suit le barème de la catégorie). */
    private static final Map<String, Long> PRIX = new HashMap<>();
    static {
        Object[][] t = {
                {"DIRT", 1}, {"COARSE_DIRT", 1}, {"SAND", 1}, {"RED_SAND", 1}, {"GRAVEL", 1}, {"COBBLESTONE", 1},
                {"COBBLED_DEEPSLATE", 1}, {"NETHERRACK", 1}, {"STONE", 2}, {"DEEPSLATE", 2}, {"GRASS_BLOCK", 3},
                {"GLASS", 3}, {"GLASS_PANE", 1}, {"LADDER", 3}, {"SCAFFOLDING", 3}, {"TORCH", 2}, {"SOUL_TORCH", 3},
                {"CRAFTING_TABLE", 5}, {"CHEST", 10}, {"BARREL", 10}, {"TRAPPED_CHEST", 15}, {"FURNACE", 10},
                {"SMOKER", 20}, {"BLAST_FURNACE", 40}, {"BOOKSHELF", 20}, {"CHISELED_BOOKSHELF", 30},
                {"OBSIDIAN", 25}, {"CRYING_OBSIDIAN", 40}, {"SEA_LANTERN", 15}, {"GLOWSTONE", 12},
                {"COAL_BLOCK", 40}, {"REDSTONE_BLOCK", 80}, {"LAPIS_BLOCK", 120}, {"RAW_IRON_BLOCK", 120},
                {"IRON_BLOCK", 150}, {"RAW_COPPER_BLOCK", 50}, {"COPPER_BLOCK", 60}, {"RAW_GOLD_BLOCK", 250},
                {"GOLD_BLOCK", 300}, {"DIAMOND_BLOCK", 1500}, {"EMERALD_BLOCK", 1500}, {"ANCIENT_DEBRIS", 1000},
                {"NETHERITE_BLOCK", 8000}, {"AMETHYST_BLOCK", 20}, {"ANVIL", 300}, {"CHIPPED_ANVIL", 200},
                {"DAMAGED_ANVIL", 100}, {"ENCHANTING_TABLE", 2000}, {"BREWING_STAND", 300}, {"ENDER_CHEST", 800},
                {"JUKEBOX", 200}, {"LODESTONE", 500}, {"BEACON", 25000}, {"CONDUIT", 10000}, {"HEAVY_CORE", 3000},
                {"SPONGE", 80}, {"WET_SPONGE", 80}, {"SCULK_CATALYST", 200}, {"PLAYER_HEAD", 200},
                {"DRAGON_HEAD", 5000}, {"DRAGON_WALL_HEAD", 5000}, {"HOPPER", 40}, {"CRAFTER", 60},
        };
        for (Object[] l : t) PRIX.put((String) l[0], ((Number) l[1]).longValue());
    }

    /** Objets posables qui ne sont pas des blocs, vendus en plus (rayon Divers). */
    private static final Map<String, Long> EXTRAS = new java.util.LinkedHashMap<>();
    static {
        EXTRAS.put("ITEM_FRAME", 20L);
        EXTRAS.put("GLOW_ITEM_FRAME", 40L);
        EXTRAS.put("PAINTING", 15L);
        EXTRAS.put("ARMOR_STAND", 30L);
        EXTRAS.put("REDSTONE", 2L);
        EXTRAS.put("STRING", 2L);
        EXTRAS.put("WATER_BUCKET", 50L);
    }

    private record Article(Material bloc, long prix) { }

    private final OrbitTycoon plugin;
    private Map<Categorie, List<Article>> catalogue;

    MagasinBlocs(OrbitTycoon plugin) { this.plugin = plugin; }

    /** Après /tycoon reload (prix, exclusions, générateurs changés). */
    void recharger() { catalogue = null; }

    // ------------------------------------------------------------------ catalogue

    private Map<Categorie, List<Article>> catalogue() {
        if (catalogue != null) return catalogue;
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("magasin-blocs");
        double mult = cfg == null ? 1.0 : Math.max(0.01, cfg.getDouble("multiplicateur", 1.0));
        Set<String> exclus = new HashSet<>(EXCLUS);
        if (cfg != null) for (String s : cfg.getStringList("exclus")) exclus.add(s.toUpperCase(Locale.ROOT));
        ConfigurationSection prixCfg = cfg == null ? null : cfg.getConfigurationSection("prix");

        Map<Categorie, List<Article>> c = new EnumMap<>(Categorie.class);
        for (Categorie k : Categorie.values()) c.put(k, new ArrayList<>());
        for (Material m : Material.values()) {
            String n = m.name();
            if (n.startsWith("LEGACY_") || !m.isBlock() || !m.isItem() || m.isAir()) continue;
            if (exclus.contains(n) || n.startsWith("INFESTED_") || plugin.generateurs().blocGenerateur(m)) continue;
            Categorie k = categorie(n);
            long prix;
            if (prixCfg != null && prixCfg.contains(n)) prix = Math.max(1, prixCfg.getLong(n));
            else prix = Math.max(1, Math.round(prixBase(n, k) * mult));
            c.get(k).add(new Article(m, prix));
        }
        // Objets de déco qui ne sont pas des blocs (cadres, tableaux, porte-armures…).
        for (Map.Entry<String, Long> x : EXTRAS.entrySet()) {
            Material m = Material.matchMaterial(x.getKey());
            if (m == null || exclus.contains(x.getKey())) continue;
            long prix = prixCfg != null && prixCfg.contains(x.getKey()) ? Math.max(1, prixCfg.getLong(x.getKey()))
                    : Math.max(1, Math.round(x.getValue() * mult));
            c.get(Categorie.DIVERS).add(new Article(m, prix));
        }
        catalogue = c;
        return c;
    }

    /** Classement d'un bloc par son nom (l'ordre des tests compte). */
    private static Categorie categorie(String n) {
        if ((n.contains("MOSS") && !n.startsWith("MOSSY_")) || a(n, "LEAVES", "SAPLING", "PROPAGULE", "FLOWER", "TULIP", "DAISY", "ORCHID", "ALLIUM",
                "BLUET", "POPPY", "DANDELION", "LILY", "ROSE", "PEONY", "LILAC", "FERN", "SHORT_GRASS", "TALL_GRASS", "BUSH",
                "VINE", "MUSHROOM", "FUNGUS", "ROOTS", "CORAL", "KELP", "SEAGRASS", "SEA_PICKLE", "DRIPLEAF", "AZALEA",
                "CACTUS", "SUGAR_CANE", "CHORUS", "SPORE", "PITCHER", "PETALS", "LEAF_LITTER", "EYEBLOSSOM", "LICHEN",
                "NETHER_WART", "WART_BLOCK", "HAY_BLOCK", "PUMPKIN", "MELON", "COCOA", "SHROOMLIGHT", "DRIED_KELP", "BAMBOO_SAPLING"))
            return n.equals("JACK_O_LANTERN") || n.equals("SHROOMLIGHT") ? Categorie.LUMIERES : Categorie.VEGETATION;
        if (a(n, "WOOL", "CARPET", "CONCRETE", "TERRACOTTA", "GLASS", "_BED", "BANNER", "SHULKER_BOX"))
            return Categorie.COULEURS;
        if (a(n, "LANTERN", "TORCH", "GLOWSTONE", "FROGLIGHT", "END_ROD", "REDSTONE_LAMP", "CAMPFIRE", "CANDLE", "BULB",
                "JACK_O_LANTERN"))
            return Categorie.LUMIERES;
        if (a(n, "DIRT", "GRASS_BLOCK", "PODZOL", "MYCELIUM", "SAND", "GRAVEL", "CLAY", "MUD", "SNOW", "ICE", "NYLIUM",
                "SOUL_SOIL", "MAGMA", "SPONGE", "SCULK", "HONEY", "SLIME", "BONE_BLOCK", "DRIPSTONE", "NETHERRACK", "COBWEB")
                && !n.contains("SANDSTONE") && !n.contains("MUD_BRICK") && !n.equals("PACKED_MUD") && !n.contains("SLICE"))
            return Categorie.NATURE;
        if (a(n, "OAK_", "SPRUCE_", "BIRCH_", "JUNGLE_", "ACACIA_", "MANGROVE_", "CHERRY_", "BAMBOO", "CRIMSON_", "WARPED_")
                && !n.contains("FUNGUS") && !n.contains("ROOTS"))
            return Categorie.BOIS;
        if (a(n, "_ORE", "RAW_", "AMETHYST", "ANCIENT_DEBRIS", "COAL_BLOCK", "HEAVY_CORE"))
            return Categorie.METAUX;
        if (a(n, "CHEST", "BARREL", "FURNACE", "SMOKER", "CRAFTING", "TABLE", "ANVIL", "HOPPER", "DISPENSER", "DROPPER",
                "OBSERVER", "PISTON", "LEVER", "BUTTON", "PRESSURE_PLATE", "REPEATER", "COMPARATOR", "RAIL", "REDSTONE",
                "TARGET", "DAYLIGHT", "NOTE_BLOCK", "JUKEBOX", "BELL", "LECTERN", "COMPOSTER", "CAULDRON", "STONECUTTER",
                "GRINDSTONE", "LOOM", "BREWING", "BEACON", "CONDUIT", "LODESTONE", "CRAFTER", "SCAFFOLDING", "LADDER",
                "FLOWER_POT", "BOOKSHELF", "TRIPWIRE", "DOOR", "TRAPDOOR", "_HEAD", "SKULL", "BED", "SCULK_SENSOR", "SHELF"))
            return Categorie.FONCTIONNELS;
        if (a(n, "IRON", "GOLD", "DIAMOND", "EMERALD", "NETHERITE", "LAPIS", "COPPER", "CHAIN"))
            return Categorie.METAUX;
        if (a(n, "STONE", "COBBLE", "BRICK", "DEEPSLATE", "GRANITE", "DIORITE", "ANDESITE", "TUFF", "BLACKSTONE",
                "BASALT", "PURPUR", "PRISMARINE", "QUARTZ", "CALCITE", "OBSIDIAN", "END_STONE", "TILES", "POLISHED",
                "SMOOTH", "CHISELED", "CUT_", "PACKED_MUD", "RESIN"))
            return Categorie.PIERRES;
        return Categorie.DIVERS;
    }

    private static boolean a(String n, String... mots) {
        for (String m : mots) if (n.contains(m)) return true;
        return false;
    }

    /** Barème : prix fixé, sinon base de la catégorie (minerais un peu plus chers), moitié prix pour les demi-blocs. */
    private static long prixBase(String n, Categorie k) {
        Long fixe = PRIX.get(n);
        if (fixe != null) return fixe;
        long p = k.base;
        if (n.endsWith("_ORE")) p = n.startsWith("DEEPSLATE_") ? 50 : 40;
        else if (n.contains("SHULKER_BOX")) p = 500;
        else if (n.endsWith("_HEAD") || n.endsWith("_SKULL")) p = 500;
        else if (n.contains("STAINED_GLASS")) p = 5;
        if (a(n, "_SLAB", "CARPET", "_PANE", "BUTTON", "PRESSURE_PLATE")) p = Math.max(1, (p + 1) / 2);
        return p;
    }

    // ------------------------------------------------------------------ commande

    boolean commande(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { OrbitTycoon.msg(s, "Commande réservée aux joueurs."); return true; }
        ouvrir(p);
        return true;
    }

    // ------------------------------------------------------------------ menus

    private static final class Accueil implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private static final class Rayon implements InventoryHolder {
        private Inventory inv;
        Categorie cat;
        int page;
        final List<Article> articles = new ArrayList<>();
        @Override public Inventory getInventory() { return inv; }
    }

    void ouvrir(Player p) {
        Accueil h = new Accueil();
        Inventory inv = Bukkit.createInventory(h, 54, Habillage.titre(plugin, p, 6, "Magasin de blocs"));
        h.inv = inv;
        Map<Categorie, List<Article>> c = catalogue();
        int total = 0;
        for (Categorie k : Categorie.values()) {
            List<Article> l = c.get(k);
            total += l.size();
            long min = l.stream().mapToLong(Article::prix).min().orElse(0);
            inv.setItem(SLOTS_CAT[k.ordinal()], objet(k.icone, "<#E0B864><bold>" + k.nom,
                    "<gray>" + l.size() + " blocs", "<gray>À partir de <#E0B864>" + Hud.nombre(min) + " ⛃",
                    "", "<#62CFF4>➜ Clic pour voir le rayon"));
        }
        inv.setItem(4, objet(Material.BRICKS, "<gradient:#62CFF4:#E0B864><bold>Magasin de blocs</bold></gradient>",
                "<gray>" + total + " blocs pour décorer ton île <#62CFF4>/is",
                "<gray>Payés en <#E0B864>Crédits orbitaux ⛃",
                "",
                "<gray>Ton solde : <#E0B864>" + Hud.nombre(plugin.monnaies().solde(p.getUniqueId(), Monnaies.Monnaie.CREDIT)) + " ⛃",
                "<dark_gray>Les générateurs s'achètent avec /gen."));
        inv.setItem(SLOT_FERMER, objet(Material.BARRIER, "<#FF4D4D>Fermer"));
        Habillage.fond(plugin, p, inv);
        p.openInventory(inv);
    }

    void rayon(Player p, Categorie k, int page) {
        List<Article> liste = catalogue().get(k);
        int pages = Math.max(1, (liste.size() + PAR_PAGE - 1) / PAR_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        Rayon h = new Rayon();
        h.cat = k;
        h.page = page;
        h.inv = Bukkit.createInventory(h, 54, Habillage.titre(plugin, p, 6, k.nom));
        for (int i = 0; i < PAR_PAGE && page * PAR_PAGE + i < liste.size(); i++) h.articles.add(liste.get(page * PAR_PAGE + i));
        remplir(p, h, pages);
        Habillage.fond(plugin, p, h.inv);
        p.openInventory(h.inv);
    }

    /** (Re)dessine un rayon : appelé à l'ouverture et après chaque achat (solde, prix en rouge). */
    private void remplir(Player p, Rayon h, int pages) {
        Inventory inv = h.inv;
        Categorie k = h.cat;
        int page = h.page;
        long solde = plugin.monnaies().solde(p.getUniqueId(), Monnaies.Monnaie.CREDIT);
        for (int i = 0; i < h.articles.size(); i++) inv.setItem(i, affichage(h.articles.get(i), solde));
        inv.setItem(SLOT_RETOUR, objet(Material.OAK_DOOR, "<#62CFF4>← Catégories"));
        if (page > 0) inv.setItem(SLOT_PRECEDENT, objet(Material.ARROW, "<#62CFF4>← Page précédente", "<gray>Page " + page + " / " + pages));
        if (page < pages - 1) inv.setItem(SLOT_SUIVANT, objet(Material.ARROW, "<#62CFF4>Page suivante →", "<gray>Page " + (page + 2) + " / " + pages));
        inv.setItem(SLOT_INFO, objet(Material.EMERALD, "<#E0B864><bold>" + k.nom,
                "<gray>Ton solde : <#E0B864>" + Hud.nombre(solde) + " ⛃",
                "<gray>Page " + (page + 1) + " / " + pages,
                "",
                "<white>Clic gauche <gray>: une pile",
                "<white>Clic droit <gray>: 16",
                "<white>Shift + clic <gray>: 1"));
        inv.setItem(SLOT_FERMER, objet(Material.BARRIER, "<#FF4D4D>Fermer"));
    }

    private static ItemStack affichage(Article a, long solde) {
        ItemStack it = new ItemStack(a.bloc());
        int pile = a.bloc().getMaxStackSize();
        it.lore(List.of(
                texte("<gray>Prix : " + (solde >= a.prix() ? "<#E0B864>" : "<#FF4D4D>") + Hud.nombre(a.prix()) + " ⛃ <gray>l'unité"),
                texte(""),
                texte("<white>Clic gauche <gray>: x" + pile + " <dark_gray>(" + Hud.nombre(a.prix() * pile) + " ⛃)"),
                texte(pile >= 16 ? "<white>Clic droit <gray>: x16 <dark_gray>(" + Hud.nombre(a.prix() * 16) + " ⛃)" : "<dark_gray>—"),
                texte("<white>Shift + clic <gray>: x1")));
        return it;
    }

    private void acheter(Player p, Article a, int nombre) {
        nombre = Math.max(1, Math.min(nombre, a.bloc().getMaxStackSize()));
        if (p.getInventory().firstEmpty() < 0 && !p.getInventory().containsAtLeast(new ItemStack(a.bloc()), 1)) {
            OrbitTycoon.msg(p, "Ton inventaire est plein.");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }
        long cout = a.prix() * nombre;
        Monnaies mon = plugin.monnaies();
        if (!mon.retirer(p.getUniqueId(), Monnaies.Monnaie.CREDIT, cout)) {
            OrbitTycoon.msg(p, "<#FF4D4D>Pas assez de Crédits</#FF4D4D> <gray>: il faut " + Monnaies.Monnaie.CREDIT.format(cout) + "<gray>.");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }
        // Ce qui ne rentre pas dans l'inventaire est remboursé.
        int reste = 0;
        for (ItemStack r : p.getInventory().addItem(new ItemStack(a.bloc(), nombre)).values()) reste += r.getAmount();
        if (reste > 0) mon.ajouter(p.getUniqueId(), Monnaies.Monnaie.CREDIT, a.prix() * reste);
        int recu = nombre - reste;
        if (recu <= 0) { OrbitTycoon.msg(p, "Ton inventaire est plein."); return; }
        OrbitTycoon.msg(p, "Acheté : <white>" + recu + " × </white><lang:" + a.bloc().translationKey() + "> <gray>pour "
                + Monnaies.Monnaie.CREDIT.format(a.prix() * recu) + "<gray>." + (reste > 0 ? " <dark_gray>(inventaire plein : " + reste + " remboursé(s))" : ""));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
    }

    private static ItemStack objet(Material m, String nom, String... lignes) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(texte(nom));
            List<Component> lore = new ArrayList<>();
            for (String l : lignes) lore.add(texte(l));
            if (!lore.isEmpty()) meta.lore(lore);
        });
        return it;
    }

    private static Component texte(String mm) { return MM.deserialize(mm).decoration(TextDecoration.ITALIC, false); }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.HIGHEST)
    public void clic(InventoryClickEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (!(holder instanceof Accueil) && !(holder instanceof Rayon)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getSlot();
        if (slot == SLOT_FERMER) { p.closeInventory(); return; }
        if (holder instanceof Accueil) {
            for (Categorie k : Categorie.values())
                if (SLOTS_CAT[k.ordinal()] == slot) {
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                    rayon(p, k, 0);
                    return;
                }
        } else if (holder instanceof Rayon h) {
            if (slot < h.articles.size()) {
                Article a = h.articles.get(slot);
                ClickType c = e.getClick();
                int n = c.isShiftClick() ? 1 : c.isRightClick() ? 16 : a.bloc().getMaxStackSize();
                acheter(p, a, n);
                int pages = Math.max(1, (catalogue().get(h.cat).size() + PAR_PAGE - 1) / PAR_PAGE);
                remplir(p, h, pages);   // met à jour le solde et les prix en rouge
            } else if (slot == SLOT_RETOUR) ouvrir(p);
            else if (slot == SLOT_PRECEDENT && h.page > 0) rayon(p, h.cat, h.page - 1);
            else if (slot == SLOT_SUIVANT && e.getCurrentItem() != null) rayon(p, h.cat, h.page + 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void glisser(InventoryDragEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof Accueil || holder instanceof Rayon) e.setCancelled(true);
    }
}
