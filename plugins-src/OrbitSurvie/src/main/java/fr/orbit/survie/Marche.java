package fr.orbit.survie;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.CreativeCategory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.TransmuteRecipe;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Le marché de la survie : tous les objets du jeu, à prix de référence calculés depuis marche.yml (matières
 * premières) puis les recettes du jeu. Prix vivants : vendre fait baisser le prix d'un objet, acheter le fait
 * monter, et l'écart se résorbe avec le temps. L'état (pression de chaque objet) est gardé en base
 * (table survie_marche).
 */
public final class Marche {

    public enum Categorie {
        MINERAIS("Minerais & métaux", Material.DIAMOND),
        BOIS("Bois", Material.OAK_LOG),
        CONSTRUCTION("Pierres & construction", Material.STONE_BRICKS),
        COULEURS("Couleurs & verre", Material.CYAN_WOOL),
        NATURE("Nature & plantes", Material.OAK_SAPLING),
        NOURRITURE("Agriculture & nourriture", Material.WHEAT),
        MER("Mer & pêche", Material.TROPICAL_FISH),
        BUTIN("Butin de créatures", Material.BONE),
        REDSTONE("Redstone & mécanismes", Material.REDSTONE),
        OUTILS("Outils & armes", Material.IRON_PICKAXE),
        ARMURES("Armures", Material.IRON_CHESTPLATE),
        MAGIE("Enchantement & alchimie", Material.ENCHANTING_TABLE),
        DIVERS("Divers & décoration", Material.PAINTING);

        public final String nom;
        public final Material icone;

        Categorie(String nom, Material icone) {
            this.nom = nom;
            this.icone = icone;
        }
    }

    /** Un objet du marché. ref = prix d'achat de référence. */
    public record Article(Material objet, double ref, Categorie categorie, boolean achetable) {}

    /** Pression de l'offre et de la demande : > 0 = les joueurs ont beaucoup vendu (prix en baisse). */
    private static final class Etat {
        double pression;
        long achetes, vendus;
        /** Roulement des prix : cours actuel (multiplie le prix) et cours visé, vers lequel il glisse. */
        double cours = 1, cible = 1;
        boolean modifie;
    }

    private final OrbitSurvie plugin;
    private final Map<Material, Article> articles = new LinkedHashMap<>();
    private final Map<Categorie, List<Article>> parCategorie = new EnumMap<>(Categorie.class);
    private final Map<Material, Etat> etats = new HashMap<>();
    private final List<Material> sansPrix = new ArrayList<>();

    private double venteRatio, artisanat, cuisson, volume, facteurMin, facteurMax, retour;
    private int minutes;

    // Roulement des prix (cours qui montent et descendent tout seuls) et bornes par objet.
    private final Map<Material, double[]> limites = new HashMap<>();
    private final java.util.Random alea = new java.util.Random();
    private boolean roulementActif;
    private int roulementMinutes, roulementNombre, roulementAnnonce;
    private double roulementHausse, roulementBaisse, roulementAmpMin, roulementVitesse, roulementRefMin;
    private final Set<Categorie> roulementCategories = java.util.EnumSet.noneOf(Categorie.class);

    public Marche(OrbitSurvie plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ chargement

    private File fichier() {
        return new File(plugin.getDataFolder(), "marche.yml");
    }

    /** (Re)calcule tous les prix de référence depuis marche.yml et les recettes du jeu. */
    public void charger() {
        if (!fichier().isFile()) {
            plugin.saveResource("marche.yml", false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(fichier());
        var defaut = plugin.getResource("marche.yml");
        if (defaut != null) {
            cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaut, StandardCharsets.UTF_8)));
        }
        venteRatio = cfg.getDouble("vente-ratio", 0.30);
        artisanat = cfg.getDouble("artisanat", 1.05);
        cuisson = cfg.getDouble("cuisson", 0.4);
        volume = Math.max(1, cfg.getDouble("dynamique.volume", 6000));
        facteurMin = cfg.getDouble("dynamique.min", 0.5);
        facteurMax = cfg.getDouble("dynamique.max", 1.5);
        minutes = Math.max(1, cfg.getInt("dynamique.minutes", 5));
        retour = Math.min(1, Math.max(0, cfg.getDouble("dynamique.retour", 0.93)));

        roulementActif = cfg.getBoolean("roulement.actif", true);
        roulementMinutes = Math.max(5, cfg.getInt("roulement.minutes", 60));
        roulementNombre = Math.max(0, cfg.getInt("roulement.objets", 12));
        roulementHausse = Math.max(0, cfg.getDouble("roulement.hausse-max", 0.40));
        roulementBaisse = Math.max(0, Math.min(0.9, cfg.getDouble("roulement.baisse-max", 0.35)));
        roulementAmpMin = Math.max(0, cfg.getDouble("roulement.ecart-min", 0.10));
        roulementVitesse = Math.min(1, Math.max(0.01, cfg.getDouble("roulement.vitesse", 0.2)));
        roulementRefMin = cfg.getDouble("roulement.prix-ref-min", 4);
        roulementAnnonce = Math.max(0, cfg.getInt("roulement.annonce", 3));
        roulementCategories.clear();
        for (String c : cfg.getStringList("roulement.categories")) {
            try {
                roulementCategories.add(Categorie.valueOf(c.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignore) {
                plugin.getLogger().warning("marche.yml : catégorie de roulement inconnue « " + c + " »");
            }
        }

        // Multiplicateurs de prix (inflation) : global, puis bonus des produits de farm.
        double inflation = Math.max(0.01, cfg.getDouble("inflation.global", 1));
        List<Map.Entry<String, Double>> bonus = new ArrayList<>();
        ConfigurationSection bs = cfg.getConfigurationSection("inflation.bonus");
        if (bs != null) {
            for (String groupe : bs.getKeys(false)) {
                double mult = bs.getDouble(groupe + ".multiplicateur", 1);
                for (String motif : bs.getStringList(groupe + ".objets")) {
                    bonus.add(Map.entry(motif.toUpperCase(Locale.ROOT), mult));
                }
            }
        }

        List<String> exclus = cfg.getStringList("exclus");
        List<String> venteSeule = cfg.getStringList("vente-seule");

        // 1. Matières premières.
        Map<String, Double> exacts = new HashMap<>();
        List<Map.Entry<String, Double>> motifs = new ArrayList<>();
        ConfigurationSection vs = cfg.getConfigurationSection("valeurs");
        if (vs != null) {
            for (String k : vs.getKeys(false)) {
                double v = vs.getDouble(k);
                String cle = k.toUpperCase(Locale.ROOT);
                if (cle.contains("*")) {
                    motifs.add(Map.entry(cle, v));
                } else {
                    exacts.put(cle, v);
                }
            }
        }
        List<Material> tous = new ArrayList<>();
        for (Material m : Material.values()) {
            if (!m.isLegacy() && m.isItem() && !m.isAir() && !correspond(m.name(), exclus)) {
                tous.add(m);
            }
        }
        Map<Material, Double> base = new HashMap<>();
        for (Material m : tous) {
            Double v = exacts.get(m.name());
            if (v == null) {
                for (Map.Entry<String, Double> e : motifs) {
                    if (glob(m.name(), e.getKey())) {
                        v = e.getValue();
                        break;
                    }
                }
            }
            if (v != null && v > 0) {
                double mult = inflation;
                for (Map.Entry<String, Double> e : bonus) {
                    if (glob(m.name(), e.getKey())) {
                        mult *= e.getValue();
                        break;
                    }
                }
                base.put(m, v * mult);
            }
        }

        // Bornes de prix par objet (par rapport à la référence), offre/demande et roulement compris.
        limites.clear();
        ConfigurationSection ls = cfg.getConfigurationSection("limites");
        if (ls != null) {
            for (String groupe : ls.getKeys(false)) {
                double lmin = ls.getDouble(groupe + ".min", facteurMin);
                double lmax = ls.getDouble(groupe + ".max", facteurMax);
                List<String> objets = ls.getStringList(groupe + ".objets");
                for (Material m : tous) {
                    if (!limites.containsKey(m) && correspond(m.name(), objets)) {
                        limites.put(m, new double[]{lmin, lmax});
                    }
                }
            }
        }

        // 2. Recettes du jeu.
        Map<Material, Double> valeurs = new HashMap<>(base);
        List<Rec> recettes = recettes();
        for (int passe = 0; passe < 60; passe++) {
            boolean change = false;
            for (Rec r : recettes) {
                if (base.containsKey(r.resultat) || r.quantite <= 0) {
                    continue;
                }
                double cout = 0;
                boolean connu = true;
                for (RecipeChoice c : r.ingredients) {
                    double v = valeurChoix(c, valeurs);
                    if (v < 0) {
                        connu = false;
                        break;
                    }
                    cout += v;
                }
                if (!connu) {
                    continue;
                }
                double cand = (cout * r.mult + r.extra) / r.quantite;
                Double avant = valeurs.get(r.resultat);
                if (avant == null || cand < avant - 1e-6) {
                    valeurs.put(r.resultat, cand);
                    change = true;
                }
            }
            // Transformations sans recette : cuivre oxydé = cuivre neuf (l'oxydation est gratuite),
            // béton = poudre de béton (il suffit de la mouiller).
            for (Material m : tous) {
                if (valeurs.containsKey(m)) {
                    continue;
                }
                String n = m.name();
                String neuf = n.replace("EXPOSED_", "").replace("WEATHERED_", "").replace("OXIDIZED_", "");
                if (neuf.equals("COPPER") || neuf.equals("WAXED_COPPER")) {
                    neuf += "_BLOCK";
                }
                if (n.endsWith("_CONCRETE")) {
                    neuf = n + "_POWDER";
                }
                if (!neuf.equals(n)) {
                    Material mm = Material.getMaterial(neuf);
                    if (mm != null && valeurs.containsKey(mm)) {
                        valeurs.put(m, valeurs.get(mm));
                        change = true;
                    }
                }
            }
            if (!change) {
                break;
            }
        }

        // 3. Articles.
        Map<String, String> forcees = new HashMap<>();
        ConfigurationSection cs = cfg.getConfigurationSection("categories");
        if (cs != null) {
            for (String k : cs.getKeys(false)) {
                forcees.put(k.toUpperCase(Locale.ROOT), cs.getString(k, "").toUpperCase(Locale.ROOT));
            }
        }
        articles.clear();
        parCategorie.clear();
        sansPrix.clear();
        for (Categorie c : Categorie.values()) {
            parCategorie.put(c, new ArrayList<>());
        }
        for (Material m : tous) {
            Double v = valeurs.get(m);
            if (v == null) {
                sansPrix.add(m);
                continue;
            }
            double ref = arrondir(Math.max(0.05, v));
            Categorie cat = categorie(m);
            String f = forcees.get(m.name());
            if (f != null) {
                try {
                    cat = Categorie.valueOf(f);
                } catch (IllegalArgumentException ignore) {
                    plugin.getLogger().warning("marche.yml : catégorie inconnue « " + f + " » pour " + m.name());
                }
            }
            Article a = new Article(m, ref, cat, !correspond(m.name(), venteSeule));
            articles.put(m, a);
            parCategorie.get(cat).add(a);
        }
        for (List<Article> l : parCategorie.values()) {
            l.sort(Comparator.comparing((Article a) -> a.objet().name()));
        }
        plugin.getLogger().info("Marché : " + articles.size() + " objets en vente, " + sansPrix.size()
                + " sans prix (/shop admin sansprix).");
    }

    private record Rec(Material resultat, int quantite, List<RecipeChoice> ingredients, double mult, double extra) {}

    private List<Rec> recettes() {
        List<Rec> out = new ArrayList<>();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r;
            try {
                r = it.next();
            } catch (Exception e) {
                continue;
            }
            ItemStack res = r.getResult();
            if (res == null || res.getType().isAir()) {
                continue;
            }
            List<RecipeChoice> ing = new ArrayList<>();
            double mult = artisanat, extra = 0;
            if (r instanceof ShapedRecipe s) {
                Map<Character, RecipeChoice> choix = s.getChoiceMap();
                for (String ligne : s.getShape()) {
                    for (char ch : ligne.toCharArray()) {
                        RecipeChoice c = choix.get(ch);
                        if (c != null) {
                            ing.add(c);
                        }
                    }
                }
            } else if (r instanceof ShapelessRecipe s) {
                ing.addAll(s.getChoiceList());
            } else if (r instanceof TransmuteRecipe t) {
                ing.add(t.getInput());
                ing.add(t.getMaterial());
            } else if (r instanceof CookingRecipe<?> c) {
                ing.add(c.getInputChoice());
                mult = 1;
                extra = cuisson;
            } else if (r instanceof StonecuttingRecipe s) {
                ing.add(s.getInputChoice());
                mult = 1;
            } else if (r instanceof SmithingTransformRecipe s) {
                ing.add(s.getTemplate());
                ing.add(s.getBase());
                ing.add(s.getAddition());
                mult = 1;
            } else {
                continue;
            }
            ing.removeIf(c -> c == null || c == RecipeChoice.empty());
            if (!ing.isEmpty()) {
                out.add(new Rec(res.getType(), res.getAmount(), ing, mult, extra));
            }
        }
        return out;
    }

    /** Valeur la plus basse parmi les objets acceptés (moins ce qui est rendu, ex. le seau) ; -1 si inconnue. */
    private static double valeurChoix(RecipeChoice c, Map<Material, Double> valeurs) {
        List<Material> mats = new ArrayList<>();
        if (c instanceof RecipeChoice.MaterialChoice mc) {
            mats.addAll(mc.getChoices());
        } else if (c instanceof RecipeChoice.ExactChoice ec) {
            ec.getChoices().forEach(i -> mats.add(i.getType()));
        } else {
            return -1;
        }
        double min = -1;
        for (Material m : mats) {
            Double v = valeurs.get(m);
            if (v == null) {
                continue;
            }
            Material reste = m.getCraftingRemainingItem();
            double net = v;
            if (reste != null && valeurs.containsKey(reste)) {
                net = Math.max(0, v - valeurs.get(reste));
            }
            if (min < 0 || net < min) {
                min = net;
            }
        }
        return min;
    }

    static boolean glob(String nom, String motif) {
        if (!motif.contains("*")) {
            return nom.equals(motif);
        }
        String regex = "\\Q" + motif.replace("*", "\\E.*\\Q") + "\\E";
        return nom.matches(regex);
    }

    private static boolean correspond(String nom, List<String> motifs) {
        for (String m : motifs) {
            if (glob(nom, m.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ catégories

    private static final String[] BOIS = {"OAK_", "SPRUCE_", "BIRCH_", "JUNGLE_", "ACACIA_", "DARK_OAK_", "PALE_OAK_",
            "MANGROVE_", "CHERRY_", "BAMBOO", "CRIMSON_", "WARPED_", "POPLAR_", "STRIPPED_"};
    private static final String[] COULEURS = {"WHITE_", "ORANGE_", "MAGENTA_", "LIGHT_BLUE_", "YELLOW_", "LIME_", "PINK_",
            "GRAY_", "LIGHT_GRAY_", "CYAN_", "PURPLE_", "BLUE_", "BROWN_", "GREEN_", "RED_", "BLACK_"};
    private static final Set<String> MER = Set.of("COD", "SALMON", "TROPICAL_FISH", "PUFFERFISH", "COOKED_COD", "COOKED_SALMON",
            "KELP", "DRIED_KELP", "DRIED_KELP_BLOCK", "SEA_PICKLE", "SEAGRASS", "SEA_LANTERN", "SPONGE", "WET_SPONGE",
            "NAUTILUS_SHELL", "HEART_OF_THE_SEA", "CONDUIT", "INK_SAC", "GLOW_INK_SAC", "FISHING_ROD", "TRIDENT", "LILY_PAD",
            "TURTLE_EGG", "TURTLE_SCUTE", "TURTLE_HELMET");
    private static final Set<String> OUTILS = Set.of("BOW", "CROSSBOW", "MACE", "ARROW", "SPECTRAL_ARROW", "FLINT_AND_STEEL",
            "SHEARS", "BRUSH", "SPYGLASS", "COMPASS", "RECOVERY_COMPASS", "CLOCK", "LEAD", "NAME_TAG", "BUCKET",
            "CARROT_ON_A_STICK", "WARPED_FUNGUS_ON_A_STICK", "BUNDLE", "WIND_CHARGE", "FIRE_CHARGE", "MAP", "FIREWORK_ROCKET",
            "SADDLE", "TOTEM_OF_UNDYING", "WATER_BUCKET", "LAVA_BUCKET", "POWDER_SNOW_BUCKET");
    private static final Set<String> MAGIE = Set.of("BREWING_STAND", "CAULDRON", "ENCHANTING_TABLE", "BOOKSHELF",
            "CHISELED_BOOKSHELF", "BOOK", "WRITABLE_BOOK", "EXPERIENCE_BOTTLE", "GLASS_BOTTLE", "NETHER_WART", "BLAZE_POWDER",
            "BLAZE_ROD", "MAGMA_CREAM", "GHAST_TEAR", "FERMENTED_SPIDER_EYE", "GLISTERING_MELON_SLICE", "DRAGON_BREATH",
            "ENDER_EYE", "ANVIL", "CHIPPED_ANVIL", "DAMAGED_ANVIL", "GRINDSTONE", "BEACON", "NETHER_STAR", "END_CRYSTAL",
            "RESPAWN_ANCHOR", "LODESTONE", "LECTERN", "SMITHING_TABLE");
    private static final Set<String> MINERAIS = Set.of("COAL", "CHARCOAL", "DIAMOND", "EMERALD", "REDSTONE", "LAPIS_LAZULI",
            "QUARTZ", "AMETHYST_SHARD", "NETHERITE_SCRAP", "ANCIENT_DEBRIS", "GLOWSTONE_DUST", "FLINT", "ECHO_SHARD",
            "COAL_BLOCK", "IRON_BLOCK", "GOLD_BLOCK", "DIAMOND_BLOCK", "EMERALD_BLOCK", "LAPIS_BLOCK", "REDSTONE_BLOCK",
            "NETHERITE_BLOCK", "COPPER_BLOCK", "AMETHYST_BLOCK", "AMETHYST_CLUSTER", "SMALL_AMETHYST_BUD",
            "MEDIUM_AMETHYST_BUD", "LARGE_AMETHYST_BUD", "OBSIDIAN", "CRYING_OBSIDIAN", "GILDED_BLACKSTONE");
    private static final Set<String> REDSTONE = Set.of("REPEATER", "COMPARATOR", "PISTON", "STICKY_PISTON", "OBSERVER",
            "HOPPER", "DROPPER", "DISPENSER", "CRAFTER", "LEVER", "TRIPWIRE_HOOK", "DAYLIGHT_DETECTOR", "TARGET",
            "REDSTONE_TORCH", "REDSTONE_LAMP", "NOTE_BLOCK", "TNT", "SCULK_SENSOR", "CALIBRATED_SCULK_SENSOR", "SLIME_BLOCK",
            "HONEY_BLOCK", "JUKEBOX", "TRAPPED_CHEST", "BELL");
    private static final Set<String> BUTIN = Set.of("ROTTEN_FLESH", "BONE", "STRING", "SPIDER_EYE", "GUNPOWDER", "LEATHER",
            "FEATHER", "RABBIT_FOOT", "RABBIT_HIDE", "ENDER_PEARL", "SLIME_BALL", "PHANTOM_MEMBRANE", "SHULKER_SHELL",
            "ARMADILLO_SCUTE", "BREEZE_ROD", "DRAGON_EGG", "HEAVY_CORE", "DISC_FRAGMENT_5", "TRIAL_KEY", "OMINOUS_TRIAL_KEY",
            "SNIFFER_EGG", "DRIED_GHAST", "PRISMARINE_SHARD", "PRISMARINE_CRYSTALS", "SCULK", "SCULK_VEIN", "SCULK_CATALYST",
            "SCULK_SHRIEKER", "COBWEB");
    private static final Set<String> NOURRITURE = Set.of("WHEAT", "CARROT", "POTATO", "BEETROOT", "PUMPKIN", "MELON",
            "MELON_SLICE", "CARVED_PUMPKIN", "JACK_O_LANTERN", "SUGAR_CANE", "SUGAR", "COCOA_BEANS", "EGG", "BLUE_EGG",
            "BROWN_EGG", "HAY_BLOCK", "CAKE", "MILK_BUCKET", "HONEY_BOTTLE", "HONEYCOMB", "HONEYCOMB_BLOCK", "BEEHIVE",
            "BEE_NEST", "COMPOSTER", "BONE_MEAL", "PITCHER_POD", "BOWL");
    private static final String[] NATURE = {"SAPLING", "LEAVES", "PROPAGULE", "GRASS", "FERN", "DIRT", "PODZOL", "MYCELIUM",
            "MOSS", "VINE", "LICHEN", "CACTUS", "BUSH", "DRIPLEAF", "MUSHROOM", "FUNGUS", "ROOTS", "NYLIUM", "SPROUTS",
            "WART_BLOCK", "SHROOMLIGHT", "AZALEA", "PETALS", "TULIP", "BLOSSOM", "LITTER", "FLOWER", "DANDELION", "POPPY",
            "ORCHID", "ALLIUM", "BLUET", "DAISY", "CORNFLOWER", "LILY_OF_THE_VALLEY", "WITHER_ROSE", "LILAC", "ROSE_BUSH",
            "PEONY", "TORCHFLOWER", "PITCHER_PLANT", "CHORUS", "SNOW", "ICE", "CLAY", "FROGLIGHT", "CORAL", "HANGING_MOSS",
            "CREAKING_HEART", "MUD"};

    static Categorie categorie(Material m) {
        String n = m.name();
        if (MER.contains(n) || n.endsWith("_BOAT") || n.endsWith("_RAFT") || n.contains("CORAL") || n.contains("PRISMARINE")
                && !n.endsWith("_SHARD") && !n.endsWith("_CRYSTALS") || n.endsWith("FISH_BUCKET") || n.equals("COD_BUCKET")
                || n.equals("SALMON_BUCKET") || n.equals("AXOLOTL_BUCKET") || n.equals("TADPOLE_BUCKET")
                || n.endsWith("NAUTILUS_ARMOR")) {
            return Categorie.MER;
        }
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
                || n.endsWith("HORSE_ARMOR") || n.equals("WOLF_ARMOR") || n.equals("SHIELD") || n.equals("ELYTRA")
                || n.endsWith("_HARNESS")) {
            return Categorie.ARMURES;
        }
        if (OUTILS.contains(n) || n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")
                || n.endsWith("_SPEAR") || n.endsWith("_BUNDLE")) {
            return Categorie.OUTILS;
        }
        if (MAGIE.contains(n)) {
            return Categorie.MAGIE;
        }
        if (MINERAIS.contains(n) || n.endsWith("_ORE") || n.startsWith("RAW_") || n.endsWith("_INGOT") || n.endsWith("_NUGGET")) {
            return Categorie.MINERAIS;
        }
        if (REDSTONE.contains(n) || n.contains("RAIL") || n.contains("MINECART") || n.endsWith("_BUTTON")
                || n.endsWith("_PRESSURE_PLATE") || n.endsWith("LIGHTNING_ROD")) {
            return Categorie.REDSTONE;
        }
        if (BUTIN.contains(n) || n.endsWith("_SKULL") || n.endsWith("_HEAD") && !n.equals("PISTON_HEAD")
                || n.startsWith("MUSIC_DISC") || n.endsWith("_POTTERY_SHERD") || n.endsWith("_SMITHING_TEMPLATE")
                || n.endsWith("_BANNER_PATTERN")) {
            return Categorie.BUTIN;
        }
        if (NOURRITURE.contains(n) || n.endsWith("_SEEDS") || m.isEdible()) {
            return Categorie.NOURRITURE;
        }
        for (String s : NATURE) {
            if (n.contains(s) && !n.contains("BRICK") && !n.contains("STAIRS") && !n.contains("SLAB") && !n.contains("WALL")) {
                return Categorie.NATURE;
            }
        }
        if (n.equals("STICK") || n.equals("CRAFTING_TABLE") || n.equals("CHEST") || n.equals("BARREL") || n.equals("LADDER")) {
            return Categorie.BOIS;
        }
        for (String s : BOIS) {
            if (n.startsWith(s)) {
                return Categorie.BOIS;
            }
        }
        if (n.endsWith("_DYE") || n.contains("TERRACOTTA") || n.contains("GLASS") || n.contains("CANDLE") || n.contains("WOOL")
                || n.contains("CONCRETE") || n.contains("SHULKER_BOX") || n.endsWith("_BED") || n.endsWith("_BANNER")
                || n.endsWith("_CARPET")) {
            return Categorie.COULEURS;
        }
        for (String s : COULEURS) {
            if (n.startsWith(s) && !n.contains("SAND") && !n.contains("NETHER_BRICK")) {
                return Categorie.COULEURS;
            }
        }
        CreativeCategory cc = null;
        try {
            cc = m.getCreativeCategory();
        } catch (Throwable ignore) {
            // API absente : on se contente du nom
        }
        if (cc == CreativeCategory.REDSTONE) {
            return Categorie.REDSTONE;
        }
        if (cc == CreativeCategory.BREWING) {
            return Categorie.MAGIE;
        }
        if (cc == CreativeCategory.BUILDING_BLOCKS || m.isBlock() && cc != CreativeCategory.DECORATIONS) {
            return Categorie.CONSTRUCTION;
        }
        return Categorie.DIVERS;
    }

    // ------------------------------------------------------------------ prix

    public Article article(Material m) {
        return articles.get(m);
    }

    public List<Article> categorie(Categorie c) {
        return parCategorie.getOrDefault(c, List.of());
    }

    public List<Material> sansPrix() {
        return sansPrix;
    }

    public int taille() {
        return articles.size();
    }

    public List<Article> chercher(String texte) {
        String t = texte.toLowerCase(Locale.ROOT).replace(' ', '_');
        List<Article> out = new ArrayList<>();
        for (Article a : articles.values()) {
            if (a.objet().name().toLowerCase(Locale.ROOT).contains(t)) {
                out.add(a);
            }
        }
        return out;
    }

    /** Objets dont le prix s'écarte le plus de la référence. */
    public List<Article> tendances(int n) {
        List<Article> out = new ArrayList<>();
        for (Map.Entry<Material, Etat> e : etats.entrySet()) {
            Article a = articles.get(e.getKey());
            if (a != null && Math.abs(facteur(a) - 1) >= 0.02) {
                out.add(a);
            }
        }
        out.sort(Comparator.comparingDouble((Article a) -> -Math.abs(facteur(a) - 1)));
        return out.size() > n ? out.subList(0, n) : out;
    }

    private double pression(Material m) {
        Etat e = etats.get(m);
        return e == null ? 0 : e.pression;
    }

    /** Nombre d'objets à vendre pour diviser le prix par e (plus l'objet est cher, plus c'est petit). */
    private double elasticite(Article a) {
        return Math.max(32, volume / a.ref());
    }

    private double facteur(Article a, double pression) {
        Etat e = etats.get(a.objet());
        double f = Math.exp(-pression / elasticite(a)) * (e == null ? 1 : e.cours);
        f = Math.max(facteurMin, Math.min(facteurMax, f));
        double[] l = limites.get(a.objet());
        return l == null ? f : Math.max(l[0], Math.min(l[1], f));
    }

    /** Bornes du cours d'un objet : la limite de l'objet si elle existe, sinon le roulement normal. */
    private double[] bornesCours(Material m) {
        double[] l = limites.get(m);
        double lo = 1 - roulementBaisse, hi = 1 + roulementHausse;
        return l == null ? new double[]{lo, hi} : new double[]{Math.max(lo, l[0]), Math.min(hi, l[1])};
    }

    /**
     * Nouveau roulement : quelques objets tirés au hasard voient leur cours partir à la hausse ou à la baisse
     * (dans leurs bornes) ; ceux du roulement précédent repartent vers leur prix normal.
     */
    public void roulement(boolean annoncer) {
        for (Etat e : etats.values()) {
            if (e.cible != 1) {
                e.cible = 1;
                e.modifie = true;
            }
        }
        List<Article> candidats = new ArrayList<>();
        for (Article a : articles.values()) {
            if (a.ref() >= roulementRefMin && (roulementCategories.isEmpty() || roulementCategories.contains(a.categorie()))) {
                candidats.add(a);
            }
        }
        java.util.Collections.shuffle(candidats, alea);
        List<Article> hausses = new ArrayList<>(), baisses = new ArrayList<>();
        for (Article a : candidats.subList(0, Math.min(roulementNombre, candidats.size()))) {
            double[] b = bornesCours(a.objet());
            boolean monte = alea.nextBoolean();
            double ecartMax = monte ? b[1] - 1 : 1 - b[0];
            if (ecartMax <= 0.005) {
                continue;
            }
            double ecart = Math.min(ecartMax, roulementAmpMin + alea.nextDouble() * Math.max(0, ecartMax - roulementAmpMin));
            Etat e = etats.computeIfAbsent(a.objet(), k -> new Etat());
            e.cible = monte ? 1 + ecart : 1 - ecart;
            e.modifie = true;
            (monte ? hausses : baisses).add(a);
        }
        if (annoncer && roulementAnnonce > 0 && (!hausses.isEmpty() || !baisses.isEmpty())) {
            StringBuilder sb = new StringBuilder("<gray>Le marché bouge : ");
            if (!hausses.isEmpty()) {
                sb.append("<#3DDCB0>▲ ").append(noms(hausses)).append(" ");
            }
            if (!baisses.isEmpty()) {
                sb.append("<#FF6B6B>▼ ").append(noms(baisses)).append(" ");
            }
            sb.append("<dark_gray>· <white>/shop<gray> → Tendances");
            Bukkit.broadcast(Msg.msg(sb.toString()));
        }
    }

    private String noms(List<Article> l) {
        List<String> n = new ArrayList<>();
        for (Article a : l.subList(0, Math.min(roulementAnnonce, l.size()))) {
            n.add("<white>" + Msg.nom(a.objet()));
        }
        return String.join("<gray>, ", n);
    }

    /** Prix actuel / référence (1 = au prix de référence). */
    public double facteur(Article a) {
        return facteur(a, pression(a.objet()));
    }

    // Les prix affichés et payés sont des entiers ou des demis (1 ; 1,50 ; 2…). L'achat est arrondi au demi
    // supérieur et la vente au demi inférieur : l'arrondi ne peut jamais rendre un achat-revente gagnant.

    /** Arrondi au demi supérieur (1,71 → 2 ; 1,20 → 1,50). */
    public static double demiHaut(double v) {
        return Math.ceil(v * 2 - 1e-9) / 2;
    }

    /** Arrondi au demi inférieur (1,71 → 1,50 ; 0,30 → 0). */
    public static double demiBas(double v) {
        return Math.floor(v * 2 + 1e-9) / 2;
    }

    public double prixAchat(Article a) {
        return Math.max(0.5, demiHaut(a.ref() * facteur(a)));
    }

    /** Prix de vente à l'unité ; 0 pour les objets qui ne se vendent que par lot (voir {@link #lotVente}). */
    public double prixVente(Article a) {
        return demiBas(a.ref() * venteRatio * facteur(a));
    }

    /** Plus petit nombre d'exemplaires qui rapporte au moins un demi-crédit (1 si l'objet se vend à l'unité). */
    public int lotVente(Article a) {
        double unite = a.ref() * venteRatio * facteur(a);
        return unite >= 0.5 ? 1 : (int) Math.ceil(0.5 / Math.max(unite, 1e-6));
    }

    /** Coût total de n achats (le prix monte à chaque unité). */
    public double totalAchat(Article a, int n) {
        double p = pression(a.objet()), total = 0;
        for (int k = 0; k < n; k++) {
            total += a.ref() * facteur(a, p - k);
        }
        return Math.max(0.5, demiHaut(total));
    }

    /** Gain total de n ventes (le prix baisse à chaque unité). */
    public double totalVente(Article a, int n) {
        return demiBas(venteExacte(a, n));
    }

    /** Gain de n ventes avant arrondi (pour additionner plusieurs objets puis arrondir une seule fois). */
    public double venteExacte(Article a, int n) {
        double p = pression(a.objet()), total = 0;
        for (int k = 0; k < n; k++) {
            total += a.ref() * venteRatio * facteur(a, p + k);
        }
        return total;
    }

    /** Enregistre un mouvement : n > 0 = vendus par les joueurs, n < 0 = achetés. */
    public void mouvement(Material m, int n) {
        Etat e = etats.computeIfAbsent(m, k -> new Etat());
        e.pression += n;
        if (n > 0) {
            e.vendus += n;
        } else {
            e.achetes -= n;
        }
        e.modifie = true;
    }

    /** Annule un mouvement (transaction refusée par la base) sans toucher aux compteurs. */
    public void annuler(Material m, int n) {
        Etat e = etats.computeIfAbsent(m, k -> new Etat());
        e.pression -= n;
        if (n > 0) {
            e.vendus -= n;
        } else {
            e.achetes += n;
        }
        e.modifie = true;
    }

    static double arrondir(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // ------------------------------------------------------------------ base de données

    /** Crée la table, relit l'état du marché, puis lance le retour des prix et la sauvegarde. */
    public void demarrer() {
        long periode = minutes * 1200L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::retourPrix, periode, periode);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> sauver(false), 1200L, 1200L);
        long tour = roulementMinutes * 1200L;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (roulementActif) {
                roulement(true);
            }
        }, tour, tour);
        if (!plugin.db().available()) {
            if (roulementActif) {
                roulement(false);
            }
            return;
        }
        plugin.db().then(plugin.db().run(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS survie_marche ("
                        + "objet VARCHAR(64) NOT NULL PRIMARY KEY, pression DOUBLE NOT NULL DEFAULT 0, "
                        + "achetes BIGINT NOT NULL DEFAULT 0, vendus BIGINT NOT NULL DEFAULT 0, "
                        + "maj TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
                st.executeUpdate("ALTER TABLE survie_marche ADD COLUMN IF NOT EXISTS cours DOUBLE NOT NULL DEFAULT 1");
                st.executeUpdate("ALTER TABLE survie_marche ADD COLUMN IF NOT EXISTS cible DOUBLE NOT NULL DEFAULT 1");
            }
            Map<String, double[]> lu = new HashMap<>();
            try (PreparedStatement st = c.prepareStatement("SELECT objet, pression, achetes, vendus, cours, cible FROM survie_marche");
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    lu.put(rs.getString(1), new double[]{rs.getDouble(2), rs.getLong(3), rs.getLong(4),
                            rs.getDouble(5), rs.getDouble(6)});
                }
            }
            return lu;
        }), lu -> {
            boolean enCours = false;
            for (Map.Entry<String, double[]> e : lu.entrySet()) {
                Material m = Material.getMaterial(e.getKey());
                if (m == null) {
                    continue;
                }
                Etat et = etats.computeIfAbsent(m, k -> new Etat());
                // Ce qui s'est passé pendant la lecture s'ajoute à l'état relu.
                et.pression += e.getValue()[0];
                et.achetes += (long) e.getValue()[1];
                et.vendus += (long) e.getValue()[2];
                et.cours = e.getValue()[3];
                et.cible = e.getValue()[4];
                enCours |= et.cible != 1;
            }
            // Premier démarrage (ou plus aucun roulement en cours) : on en lance un tout de suite.
            if (roulementActif && !enCours) {
                roulement(false);
            }
        }, () -> plugin.getLogger().warning("Marché : état non relu (base)."));
    }

    private void retourPrix() {
        for (Etat e : etats.values()) {
            if (e.cours != e.cible) {
                e.cours += (e.cible - e.cours) * roulementVitesse;
                if (Math.abs(e.cible - e.cours) < 0.005) {
                    e.cours = e.cible;
                }
                e.modifie = true;
            }
            if (e.pression == 0) {
                continue;
            }
            e.pression *= retour;
            if (Math.abs(e.pression) < 0.5) {
                e.pression = 0;
            }
            e.modifie = true;
        }
    }

    /** Envoie en base les objets modifiés. bloquer = attendre la fin (arrêt du serveur). */
    public void sauver(boolean bloquer) {
        if (!plugin.db().available()) {
            return;
        }
        List<Object[]> lignes = new ArrayList<>();
        for (Map.Entry<Material, Etat> e : etats.entrySet()) {
            Etat et = e.getValue();
            if (et.modifie) {
                lignes.add(new Object[]{e.getKey().name(), et.pression, et.achetes, et.vendus, et.cours, et.cible});
                et.modifie = false;
            }
        }
        if (lignes.isEmpty()) {
            return;
        }
        var f = plugin.db().run(c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO survie_marche (objet, pression, achetes, vendus, cours, cible) "
                    + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE pression = VALUES(pression), achetes = VALUES(achetes), "
                    + "vendus = VALUES(vendus), cours = VALUES(cours), cible = VALUES(cible)")) {
                for (Object[] l : lignes) {
                    st.setString(1, (String) l[0]);
                    st.setDouble(2, (Double) l[1]);
                    st.setLong(3, (Long) l[2]);
                    st.setLong(4, (Long) l[3]);
                    st.setDouble(5, (Double) l[4]);
                    st.setDouble(6, (Double) l[5]);
                    st.addBatch();
                }
                st.executeBatch();
            }
            return null;
        });
        if (bloquer) {
            try {
                f.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                plugin.getLogger().warning("Marché : sauvegarde finale ratée (" + e.getMessage() + ").");
            }
        } else {
            f.exceptionally(e -> {
                plugin.getLogger().warning("Marché : sauvegarde ratée, nouvel essai dans une minute.");
                Bukkit.getScheduler().runTask(plugin, () -> lignes.forEach(l -> {
                    Material m = Material.getMaterial((String) l[0]);
                    Etat et = m == null ? null : etats.get(m);
                    if (et != null) {
                        et.modifie = true;
                    }
                }));
                return null;
            });
        }
    }

    private void sauver() {
        sauver(false);
    }

    /** Remet un objet (ou tout le marché si null) au prix de référence. */
    public void reinitialiser(Material m) {
        if (m == null) {
            etats.values().forEach(e -> {
                e.pression = 0;
                e.cours = e.cible = 1;
                e.modifie = true;
            });
        } else {
            Etat e = etats.get(m);
            if (e != null) {
                e.pression = 0;
                e.cours = e.cible = 1;
                e.modifie = true;
            }
        }
    }
}
