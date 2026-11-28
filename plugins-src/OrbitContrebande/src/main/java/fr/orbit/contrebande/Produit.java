package fr.orbit.contrebande;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * La marchandise de CONTREBANDE : deux bases (quartz cultivé, plasma condensé), huit additifs et les propriétés
 * qu'un mélange donne. Un additif ajoute sa propriété et en transforme certaines déjà présentes : c'est tout le jeu
 * de trouver les bonnes suites de mélanges (seul ce qui est ajouté est affiché chez le fournisseur ; la table de
 * mélange montre le résultat avant de mélanger, sans rien coûter).
 */
public record Produit(Base base, List<Effet> effets) {

    public static final int MAX_EFFETS = 6;

    public enum Base {
        QUARTZ("Quartz d'orbite", Material.AMETHYST_SHARD, 20, "#C79BFF"),
        PLASMA("Plasma noir", Material.MAGMA_CREAM, 45, "#FF4FD8"),
        CRISTAL("Cristal de nébuleuse", Material.PRISMARINE_CRYSTALS, 110, "#62CFF4");

        public final String nom, couleur;
        public final Material mat;
        public final int valeur;

        Base(String nom, Material mat, int valeur, String couleur) {
            this.nom = nom;
            this.mat = mat;
            this.valeur = valeur;
            this.couleur = couleur;
        }

        public String joli() {
            return "<" + couleur + ">" + nom + "</" + couleur + ">";
        }
    }

    public enum Effet {
        LUMINEUX("Lumineux", "#FFE680", 0.20),
        GLACE("Glacé", "#9FE7FF", 0.25),
        MAGNETIQUE("Magnétique", "#B0B8C8", 0.20),
        BRULANT("Brûlant", "#FF8A3D", 0.15),
        CHANTANT("Chantant", "#C79BFF", 0.35),
        APAISANT("Apaisant", "#7CFF8A", 0.15),
        DORE("Doré", "#E0B864", 0.40),
        LEGER("Antigravité", "#62CFF4", 0.45),
        INSTABLE("Instable", "#E0484A", -0.20),
        PRISMATIQUE("Prismatique", "#FF9BE8", 0.60),
        SUPRACONDUCTEUR("Supraconducteur", "#5AE0FF", 0.70),
        FOUDROYANT("Foudroyant", "#FFF35A", 0.50),
        SPECTRAL("Spectral", "#C8D8FF", 0.55),
        HARMONIQUE("Harmonique", "#B98CFF", 0.60),
        SOLAIRE("Solaire", "#FFB347", 0.65),
        STELLAIRE("Stellaire", "#FFFFFF", 1.00);

        public final String nom, couleur;
        public final double mult;

        Effet(String nom, String couleur, double mult) {
            this.nom = nom;
            this.couleur = couleur;
            this.mult = mult;
        }

        public String joli() {
            return "<" + couleur + ">" + nom + "</" + couleur + ">";
        }
    }

    public enum Additif {
        LUNE("Poussière de lune", Material.GLOWSTONE_DUST, 3, Effet.LUMINEUX),
        COMETE("Glace de comète", Material.BLUE_ICE, 4, Effet.GLACE),
        FER("Fer météorique", Material.IRON_NUGGET, 3, Effet.MAGNETIQUE),
        SOUFRE("Soufre solaire", Material.BLAZE_POWDER, 4, Effet.BRULANT),
        ECHO("Écho du vide", Material.ECHO_SHARD, 6, Effet.CHANTANT),
        ALGUE("Algue de Titan", Material.KELP, 2, Effet.APAISANT),
        OR("Pépite d'or pur", Material.GOLD_NUGGET, 6, Effet.DORE),
        PLUME("Plume de nébuleuse", Material.PHANTOM_MEMBRANE, 8, Effet.LEGER);

        public final String nom;
        public final Material mat;
        public final int prix;
        public final Effet ajoute;
        /** Propriété présente -> ce qu'elle devient avec cet additif. */
        public final Map<Effet, Effet> regles = new EnumMap<>(Effet.class);

        Additif(String nom, Material mat, int prix, Effet ajoute) {
            this.nom = nom;
            this.mat = mat;
            this.prix = prix;
            this.ajoute = ajoute;
        }

        static {
            regle(LUNE, Effet.GLACE, Effet.PRISMATIQUE);
            regle(LUNE, Effet.APAISANT, Effet.SPECTRAL);
            regle(COMETE, Effet.BRULANT, Effet.INSTABLE);
            regle(COMETE, Effet.MAGNETIQUE, Effet.SUPRACONDUCTEUR);
            regle(FER, Effet.LUMINEUX, Effet.FOUDROYANT);
            regle(FER, Effet.LEGER, Effet.INSTABLE);
            regle(SOUFRE, Effet.GLACE, Effet.INSTABLE);
            regle(SOUFRE, Effet.DORE, Effet.SOLAIRE);
            regle(ECHO, Effet.APAISANT, Effet.HARMONIQUE);
            regle(ECHO, Effet.LUMINEUX, Effet.SPECTRAL);
            regle(ALGUE, Effet.INSTABLE, Effet.APAISANT);
            regle(ALGUE, Effet.FOUDROYANT, Effet.MAGNETIQUE);
            regle(OR, Effet.SUPRACONDUCTEUR, Effet.STELLAIRE);
            regle(OR, Effet.BRULANT, Effet.SOLAIRE);
            regle(PLUME, Effet.PRISMATIQUE, Effet.STELLAIRE);
            regle(PLUME, Effet.CHANTANT, Effet.HARMONIQUE);
        }

        private static void regle(Additif a, Effet de, Effet vers) {
            a.regles.put(de, vers);
        }
    }

    // ------------------------------------------------------------------ règles

    /** Propriétés après un mélange : on transforme ce qui est présent, puis on ajoute la propriété de l'additif. */
    public static List<Effet> melanger(List<Effet> effets, Additif a) {
        List<Effet> out = new ArrayList<>();
        for (Effet e : effets) {
            Effet t = a.regles.getOrDefault(e, e);
            if (!out.contains(t)) {
                out.add(t);
            }
        }
        if (!out.contains(a.ajoute) && out.size() < MAX_EFFETS) {
            out.add(a.ajoute);
        }
        return out;
    }

    public Produit avec(Additif a) {
        return new Produit(base, melanger(effets, a));
    }

    /** Valeur d'une unité (crédits). */
    public int valeur() {
        double m = 1;
        for (Effet e : effets) {
            m += e.mult;
        }
        return (int) Math.max(1, Math.round(base.valeur * m));
    }

    public String signature() {
        StringBuilder sb = new StringBuilder(base.name());
        for (Effet e : effets) {
            sb.append(':').append(e.name());
        }
        return sb.toString();
    }

    public String effetsJolis() {
        if (effets.isEmpty()) {
            return "<#9AA4B2>brut</#9AA4B2>";
        }
        StringBuilder sb = new StringBuilder();
        for (Effet e : effets) {
            if (!sb.isEmpty()) {
                sb.append("<#5A6270>, </#5A6270>");
            }
            sb.append(e.joli());
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ objets

    private static NamespacedKey objet, produitKey;

    static void keys(OrbitContrebande plugin) {
        objet = new NamespacedKey(plugin, "objet");
        produitKey = new NamespacedKey(plugin, "produit");
    }

    private static String kind(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(objet, PersistentDataType.STRING);
    }

    private static ItemStack make(Material m, int n, String kind, String name, List<String> lore, boolean glint) {
        ItemStack it = new ItemStack(m, Math.max(1, Math.min(m.getMaxStackSize(), n)));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.mm(name));
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lore) {
            l.add(Txt.mm(s));
        }
        meta.lore(l);
        if (glint) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.getPersistentDataContainer().set(objet, PersistentDataType.STRING, kind);
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack item(int n) {
        List<String> lore = new ArrayList<>();
        lore.add("<#9AA4B2>Propriétés : " + effetsJolis());
        lore.add("<#9AA4B2>Valeur : " + Txt.cr(valeur()) + " <#9AA4B2>l'unité");
        lore.add("");
        lore.add("<#5A6270>Marchandise : la douane la confisque.");
        String name = base.joli() + (effets.isEmpty() ? "" : " <#5A6270>·</#5A6270> " + effets.get(effets.size() - 1).joli());
        ItemStack it = make(base.mat, n, "produit", name, lore, !effets.isEmpty());
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(produitKey, PersistentDataType.STRING, signature());
        // teinte de la matière dans le pack (couleur 0) : celle de la dernière propriété, sinon celle de la base
        String hex = effets.isEmpty() ? base.couleur : effets.get(effets.size() - 1).couleur;
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setColors(List.of(org.bukkit.Color.fromRGB(Integer.parseInt(hex.substring(1), 16))));
        meta.setCustomModelDataComponent(cmd);
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------------ habillage (pack de CONTREBANDE)

    /** Modèle du pack pour un objet du jeu (null si ce n'en est pas un). */
    private static String modele(ItemStack it) {
        String k = kind(it);
        if (k == null) {
            return null;
        }
        if (k.equals("produit")) {
            Produit p = lire(it);
            return p == null ? null : "produit_" + p.base.name().toLowerCase(java.util.Locale.ROOT);
        }
        if (k.startsWith("additif:")) {
            return "add_" + k.substring(8).toLowerCase(java.util.Locale.ROOT);
        }
        return k.equals("com") ? "communicateur" : k;
    }

    /**
     * Habille un objet du jeu pour celui qui le voit : modèle du pack ({@code hd}) ou objet vanilla. À appeler chaque fois
     * qu'un objet est donné ou montré à un joueur : sans le pack, un modèle inconnu s'afficherait en damier violet.
     */
    public static ItemStack skin(ItemStack it, boolean hd) {
        String id = modele(it);
        if (id == null) {
            return it;
        }
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(hd ? new NamespacedKey("contrebande", id) : null);
        if ("produit".equals(kind(it))) {
            // avec le pack, la couleur dit déjà la propriété : pas de reflet d'enchantement par-dessus
            Produit p = lire(it);
            meta.setEnchantmentGlintOverride(hd ? Boolean.FALSE : p != null && !p.effets.isEmpty() ? Boolean.TRUE : null);
        }
        it.setItemMeta(meta);
        return it;
    }

    /** Le produit d'un objet (null si ce n'en est pas un). */
    public static Produit lire(ItemStack it) {
        if (!"produit".equals(kind(it))) {
            return null;
        }
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        String sig = pdc.get(produitKey, PersistentDataType.STRING);
        if (sig == null) {
            return null;
        }
        String[] parts = sig.split(":");
        try {
            Base b = Base.valueOf(parts[0]);
            List<Effet> e = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                e.add(Effet.valueOf(parts[i]));
            }
            return new Produit(b, e);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static ItemStack germe(int n) {
        return make(Material.SMALL_AMETHYST_BUD, n, "germe", "<#C79BFF>Germe de quartz",
                List.of("<#9AA4B2>Clic droit sur un bac de culture libre :", "<#E6EDF3>4 Quartz d'orbite<#9AA4B2> à récolter."), false);
    }

    public static ItemStack minerai(int n) {
        return make(Material.NETHERITE_SCRAP, n, "minerai", "<#FF4FD8>Minerai sombre",
                List.of("<#9AA4B2>Clic droit sur le condenseur :", "<#E6EDF3>3 Plasma noir<#9AA4B2> à récupérer."), false);
    }

    public static ItemStack additif(Additif a, int n) {
        return make(a.mat, n, "additif:" + a.name(), "<#E6EDF3>" + a.nom,
                List.of("<#9AA4B2>Additif · ajoute " + a.ajoute.joli(), "<#9AA4B2>Peut aussi transformer d'autres propriétés.",
                        "<#5A6270>Se mélange à la table de la planque."), false);
    }

    public static ItemStack communicateur() {
        return make(Material.CLOCK, 1, "com", "<#62CFF4><b>Communicateur</b>",
                List.of("<#9AA4B2>Clic droit : commandes en cours", "<#9AA4B2>(client, quantité, où le trouver)."), false);
    }

    /** Skate à sustentation : sa batterie se lit sur la barre d'usure de l'objet. */
    public static ItemStack skate(int niveau) {
        ItemStack it = make(Material.HEAVY_WEIGHTED_PRESSURE_PLATE, 1, "skate", "<#62CFF4><b>Skate à sustentation</b>",
                List.of("<#9AA4B2>Clic droit : monter ou descendre.",
                        "<#E6EDF3>+45 % de vitesse<#9AA4B2>, " + (niveau >= 2 ? 35 : 20) + " s d'autonomie.",
                        "<#9AA4B2>La batterie se recharge quand tu marches."), false);
        ItemMeta m = it.getItemMeta();
        m.setMaxStackSize(1);
        if (m instanceof org.bukkit.inventory.meta.Damageable d) {
            d.setMaxDamage(100);
            d.setDamage(0);
        }
        it.setItemMeta(m);
        return it;
    }

    /** Batte du contrebandier (niveau 1) ou batte à impulsion (niveau 2) : à tenir en main pour frapper un douanier. */
    public static ItemStack batte(int niveau) {
        boolean impulsion = niveau >= 2;
        ItemStack it = make(impulsion ? Material.BLAZE_ROD : Material.STICK, 1, impulsion ? "batte_impulsion" : "batte",
                impulsion ? "<#62CFF4><b>Batte à impulsion</b>" : "<#E0B864><b>Batte du contrebandier</b>",
                impulsion
                        ? List.of("<#9AA4B2>En main, clic gauche sur un douanier.",
                        "<#E6EDF3>Onde de choc<#9AA4B2> : touche tous les agents à 3 blocs.",
                        "<#E6EDF3>Les sonne 2 s<#9AA4B2> et les blesse : ils boitent 10 s.",
                        "<#5A6270>Chaque coup fait monter l'avis de recherche.")
                        : List.of("<#9AA4B2>En main, clic gauche sur un douanier.",
                        "<#E6EDF3>Le sonne 1,5 s<#9AA4B2> et le blesse deux fois plus qu'à mains nues.",
                        "<#9AA4B2>Un douanier ne tombe jamais : frappe, puis file.",
                        "<#5A6270>Chaque coup fait monter l'avis de recherche."), false);
        ItemMeta m = it.getItemMeta();
        m.setMaxStackSize(1);
        it.setItemMeta(m);
        return it;
    }

    /** 0 : ce n'est pas une batte ; 1 : batte ; 2 : batte à impulsion. */
    public static int niveauBatte(ItemStack it) {
        String k = kind(it);
        return "batte".equals(k) ? 1 : "batte_impulsion".equals(k) ? 2 : 0;
    }

    public static boolean estSkate(ItemStack it) {
        return "skate".equals(kind(it));
    }

    // ------------------------------------------------------------------ clés du coffre

    public static final String CLE_GERME = "germe", CLE_MINERAI = "minerai";

    /** Clé de rangement d'un objet du jeu (null si ce n'en est pas un, ou si c'est le communicateur). */
    public static String cle(ItemStack it) {
        String k = kind(it);
        if (k == null || k.equals("com") || k.equals("skate") || k.startsWith("batte")) {
            return null;
        }
        if (k.equals("produit")) {
            Produit p = lire(it);
            return p == null ? null : "prod:" + p.signature();
        }
        if (k.startsWith("additif:")) {
            return "add:" + k.substring(8);
        }
        return k;
    }

    /** L'objet d'une clé du coffre (null si la clé est inconnue). */
    public static ItemStack objet(String cle, int n) {
        try {
            if (cle.equals(CLE_GERME)) {
                return germe(n);
            }
            if (cle.equals(CLE_MINERAI)) {
                return minerai(n);
            }
            if (cle.startsWith("add:")) {
                return additif(Additif.valueOf(cle.substring(4)), n);
            }
            if (cle.startsWith("prod:")) {
                Produit p = depuisSignature(cle.substring(5));
                return p == null ? null : p.item(n);
            }
        } catch (IllegalArgumentException ignored) {
            // clé d'une ancienne version
        }
        return null;
    }

    public static Produit depuisSignature(String sig) {
        String[] parts = sig.split(":");
        try {
            Base b = Base.valueOf(parts[0]);
            List<Effet> e = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                e.add(Effet.valueOf(parts[i]));
            }
            return new Produit(b, e);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static boolean estGerme(ItemStack it) {
        return "germe".equals(kind(it));
    }

    public static boolean estMinerai(ItemStack it) {
        return "minerai".equals(kind(it));
    }

    public static boolean estCom(ItemStack it) {
        return "com".equals(kind(it));
    }

    public static Additif lireAdditif(ItemStack it) {
        String k = kind(it);
        if (k == null || !k.startsWith("additif:")) {
            return null;
        }
        try {
            return Additif.valueOf(k.substring(8));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
