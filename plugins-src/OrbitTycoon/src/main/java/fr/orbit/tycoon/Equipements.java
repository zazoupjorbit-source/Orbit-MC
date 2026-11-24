package fr.orbit.tycoon;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Équipements de farm : un set de 4 pièces (casque, plastron, jambières, bottes) par outil, soit 20 pièces.
 * Chaque pièce PORTÉE donne +2,5 % (equipements.bonus-piece) d'XP d'outil, de ressources récoltées et de monnaie
 * (Crédits + Flux), seulement en récoltant avec l'outil de son set. Set complet : +15 % (equipements.bonus-set).
 * Obtenus seulement dans le coffre Farm (Coffres.java).
 */
final class Equipements implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Les 5 sets, dans l'ordre des outils. */
    enum Set {
        CRATERE(Outil.Mode.PIOCHE, "Cratère", "#B0BEC5", "Forgé dans la roche des astéroïdes"),
        SYLVANOVA(Outil.Mode.HACHE, "Sylvanova", "#C8894B", "Tissé dans l'écorce des forêts stellaires"),
        MOISSON(Outil.Mode.HOUE, "Moisson Solaire", "#F5D442", "Doré par la lumière de mille soleils"),
        MAREE(Outil.Mode.CANNE, "Marée Lunaire", "#3FA9F5", "Bercé par les marées de la Lune"),
        SUPERNOVA(Outil.Mode.EPEE, "Supernova", "#FF4D4D", "Né de l'explosion d'une étoile");

        final Outil.Mode outil;
        final String nom, couleur, devise;

        Set(Outil.Mode outil, String nom, String couleur, String devise) {
            this.outil = outil; this.nom = nom; this.couleur = couleur; this.devise = devise;
        }

        static Set de(Outil.Mode m) {
            for (Set s : values()) if (s.outil == m) return s;
            return null;
        }
    }

    enum Piece {
        CASQUE("Casque", Material.LEATHER_HELMET, EquipmentSlot.HEAD),
        PLASTRON("Plastron", Material.LEATHER_CHESTPLATE, EquipmentSlot.CHEST),
        JAMBIERES("Jambières", Material.LEATHER_LEGGINGS, EquipmentSlot.LEGS),
        BOTTES("Bottes", Material.LEATHER_BOOTS, EquipmentSlot.FEET);

        final String nom;
        final Material type;
        final EquipmentSlot slot;

        Piece(String nom, Material type, EquipmentSlot slot) { this.nom = nom; this.type = type; this.slot = slot; }
    }

    private final OrbitTycoon plugin;
    private final NamespacedKey cle;

    Equipements(OrbitTycoon plugin) {
        this.plugin = plugin;
        this.cle = new NamespacedKey(plugin, "equipement");
    }

    double bonusPiece() { return plugin.getConfig().getDouble("equipements.bonus-piece", 2.5); }
    double bonusSet() { return plugin.getConfig().getDouble("equipements.bonus-set", 15.0); }

    // ------------------------------------------------------------------ objets

    /** Nom du set dans le pack : equipment/&lt;set&gt;.json et textures armure_&lt;set&gt;_&lt;piece&gt; (tools/gen-armures.ps1). */
    private static String idPack(Set s) {
        return switch (s) {
            case CRATERE -> "cratere"; case SYLVANOVA -> "sylvanova"; case MOISSON -> "moisson";
            case MAREE -> "maree"; case SUPERNOVA -> "supernova";
        };
    }

    private static String idPiece(Piece p) {
        return switch (p) { case CASQUE -> "casque"; case PLASTRON -> "plastron"; case JAMBIERES -> "jambieres"; case BOTTES -> "bottes"; };
    }

    /**
     * La pièce p du set s (incassable) : combinaison spatiale du pack Tycoon, portée (composant equippable → equipment
     * orbittycoon:&lt;set&gt;) et en icône (item_model). Le cuir reste teint à la couleur du set (valeur d'armure du cuir).
     */
    ItemStack objet(Set s, Piece p) {
        ItemStack it = new ItemStack(p.type);
        it.editMeta(LeatherArmorMeta.class, m -> {
            m.itemName(MM.deserialize("<" + s.couleur + "><bold>" + p.nom + " " + s.nom));
            m.setColor(Color.fromRGB(Integer.parseInt(s.couleur.substring(1), 16)));
            m.setUnbreakable(true);
            m.addItemFlags(ItemFlag.HIDE_DYE, ItemFlag.HIDE_ARMOR_TRIM, ItemFlag.HIDE_UNBREAKABLE);
            m.lore(lore(s).stream().map(l -> MM.deserialize("<!italic>" + l)).toList());
            m.getPersistentDataContainer().set(cle, PersistentDataType.STRING, s.name() + ":" + p.name());
            m.setItemModel(new NamespacedKey("orbittycoon", "armure_" + idPack(s) + "_" + idPiece(p)));
        });
        it.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(p.slot)
                .assetId(Key.key("orbittycoon", idPack(s))).build());
        return it;
    }

    /** Remet à jour les pièces d'avant les textures (trim doré, pas de modèle) dans l'inventaire du joueur. */
    @EventHandler
    public void connexion(PlayerJoinEvent e) {
        PlayerInventory inv = e.getPlayer().getInventory();
        ItemStack[] contenu = inv.getContents();
        boolean change = false;
        for (int i = 0; i < contenu.length; i++) {
            ItemStack it = contenu[i];
            Set s = setDe(it);
            if (s == null || it.getItemMeta().hasItemModel()) continue;
            String v = it.getItemMeta().getPersistentDataContainer().get(cle, PersistentDataType.STRING);
            try {
                contenu[i] = objet(s, Piece.valueOf(v.substring(v.indexOf(':') + 1)));
                change = true;
            } catch (RuntimeException ignore) { }
        }
        if (change) inv.setContents(contenu);
    }

    /** Description d'une pièce : outil à utiliser, bonus de la pièce, bonus du set complet. */
    private List<String> lore(Set s) {
        String b = pct(bonusPiece()), set = pct(bonusSet());
        String outil = "<" + s.outil.couleur + ">" + s.outil.nom;
        List<String> l = new ArrayList<>();
        l.add("<#E0B864>Set " + s.nom + " <dark_gray>· <gray>Métier : " + outil);
        l.add("<dark_gray><italic>" + s.devise);
        l.add("");
        l.add("<gray>À utiliser avec : " + outil + " <dark_gray>(zone " + s.outil.zone + ")");
        l.add("<dark_gray>Le bonus ne compte qu'avec cet outil en main.");
        l.add("");
        l.add("<#62CFF4><bold>Cette pièce portée :</bold>");
        l.add("<dark_gray> • <#62CFF4>+" + b + " % <white>d'XP d'outil");
        l.add("<dark_gray> • <#62CFF4>+" + b + " % <white>de ressources récoltées");
        l.add("<dark_gray> • <#62CFF4>+" + b + " % <white>de monnaie <gray>(Crédits + Flux)");
        l.add("");
        l.add("<#E0B864><bold>Set complet (4/4) :</bold>");
        l.add("<dark_gray> • <#E0B864>+" + set + " % <white>d'XP, de ressources et de monnaie");
        l.add("<dark_gray>   au lieu de +" + pct(bonusPiece() * 4) + " % avec 4 pièces séparées");
        l.add("");
        l.add("<dark_gray>Obtenu dans le coffre Farm du spawn.");
        return l;
    }

    /** Fiche d'un set pour le Navigateur : métier, outil, les 4 pièces et les bonus. */
    List<String> presentation(Set s) {
        String b = pct(bonusPiece()), set = pct(bonusSet());
        List<String> l = new ArrayList<>();
        l.add("<gray>Métier : <" + s.outil.couleur + ">" + s.outil.zone + " <dark_gray>· <gray>outil : <"
                + s.outil.couleur + ">" + s.outil.nom);
        l.add("<dark_gray><italic>" + s.devise);
        l.add("");
        l.add("<gray>Les 4 pièces :");
        for (Piece p : Piece.values()) l.add("<dark_gray> • <" + s.couleur + ">" + p.nom + " " + s.nom);
        l.add("");
        l.add("<gray>Chaque pièce portée : <#62CFF4>+" + b + " %<gray> d'XP,");
        l.add("<gray>de ressources et de monnaie (avec la " + s.outil.nom + ").");
        l.add("<#E0B864>Set complet (4/4) : +" + set + " %<gray> sur les trois !");
        l.add("");
        l.add("<dark_gray>Obtenu dans le coffre Farm du spawn.");
        return l;
    }

    private static String pct(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.FRANCE, "%.1f", v);
    }

    /** Toutes les pièces (5 sets × 4), pour le coffre Farm. */
    List<ItemStack> toutes() {
        List<ItemStack> l = new ArrayList<>();
        for (Set s : Set.values()) for (Piece p : Piece.values()) l.add(objet(s, p));
        return l;
    }

    /** Une pièce au hasard (même chance pour les 20). */
    ItemStack tirer() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return objet(Set.values()[r.nextInt(Set.values().length)], Piece.values()[r.nextInt(Piece.values().length)]);
    }

    /** Set de cette pièce, ou null si ce n'est pas un équipement de farm. */
    Set setDe(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String v = it.getItemMeta().getPersistentDataContainer().get(cle, PersistentDataType.STRING);
        if (v == null) return null;
        try { return Set.valueOf(v.substring(0, v.indexOf(':'))); } catch (RuntimeException e) { return null; }
    }

    // ------------------------------------------------------------------ bonus

    /** Bonus total en % des pièces portées pour cet outil : 2,5 % par pièce, 15 % pour le set complet. */
    double bonus(Player p, Outil.Mode outil) {
        if (outil == null) return 0;
        int n = 0;
        for (ItemStack it : p.getInventory().getArmorContents()) {
            Set s = setDe(it);
            if (s != null && s.outil == outil) n++;
        }
        if (n <= 0) return 0;
        return n >= 4 ? bonusSet() : n * bonusPiece();
    }

    /** Quantité après le bonus (partie décimale tirée au hasard). */
    static int appliquer(int quantite, double pct) {
        if (pct <= 0 || quantite <= 0) return quantite;
        double extra = quantite * pct / 100.0;
        int plus = (int) extra;
        if (ThreadLocalRandom.current().nextDouble() < extra - plus) plus++;
        return quantite + plus;
    }

    // ------------------------------------------------------------------ armures premium (boutique, Plasma)

    /**
     * Armures premium vendues en Plasma à la boutique : bonus de Crédits, Flux et XP avec TOUS les outils
     * (réglages equipements.premium.&lt;id&gt; : bonus-piece, bonus-set, prix-piece, prix-set). Ne tombent pas des coffres.
     */
    enum Premium {
        NEBULEUSE("nebuleuse", "Nébuleuse", "Combinaison", "#C77DFF", "Tissée dans le cœur d'une nébuleuse", 5, 25, 1200, 4200),
        ZENITH("zenith", "Zénith", "Armure", "#E0B864", "Forgée au sommet du ciel, là où brille l'or", 10, 50, 3000, 10500);

        final String id, nom, type, couleur, devise;
        final double bonusPiece, bonusSet;
        final long prixPiece, prixSet;

        Premium(String id, String nom, String type, String couleur, String devise, double bp, double bs, long pp, long ps) {
            this.id = id; this.nom = nom; this.type = type; this.couleur = couleur; this.devise = devise;
            this.bonusPiece = bp; this.bonusSet = bs; this.prixPiece = pp; this.prixSet = ps;
        }
    }

    double bonusPiece(Premium a) { return plugin.getConfig().getDouble("equipements.premium." + a.id + ".bonus-piece", a.bonusPiece); }
    double bonusSet(Premium a) { return plugin.getConfig().getDouble("equipements.premium." + a.id + ".bonus-set", a.bonusSet); }
    long prixPiece(Premium a) { return plugin.getConfig().getLong("equipements.premium." + a.id + ".prix-piece", a.prixPiece); }
    long prixSet(Premium a) { return plugin.getConfig().getLong("equipements.premium." + a.id + ".prix-set", a.prixSet); }

    /** La pièce p de l'armure premium a (incassable, valeur d'armure du cuir, texture du pack). */
    ItemStack premium(Premium a, Piece p) {
        ItemStack it = new ItemStack(p.type);
        it.editMeta(LeatherArmorMeta.class, m -> {
            m.itemName(MM.deserialize("<" + a.couleur + "><bold>" + p.nom + " " + a.nom));
            m.setColor(Color.fromRGB(Integer.parseInt(a.couleur.substring(1), 16)));
            m.setUnbreakable(true);
            m.setEnchantmentGlintOverride(true);
            m.addItemFlags(ItemFlag.HIDE_DYE, ItemFlag.HIDE_ARMOR_TRIM, ItemFlag.HIDE_UNBREAKABLE);
            m.lore(lorePremium(a, false).stream().map(l -> MM.deserialize("<!italic>" + l)).toList());
            m.getPersistentDataContainer().set(cle, PersistentDataType.STRING, "PREMIUM:" + a.name() + ":" + p.name());
            m.setItemModel(new NamespacedKey("orbittycoon", "armure_" + a.id + "_" + idPiece(p)));
        });
        it.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(p.slot).assetId(Key.key("orbittycoon", a.id)).build());
        return it;
    }

    /** Description d'une armure premium (objet porté, ou fiche de la boutique avec les prix). */
    List<String> lorePremium(Premium a, boolean boutique) {
        String b = pct(bonusPiece(a)), set = pct(bonusSet(a));
        List<String> l = new ArrayList<>();
        l.add("<#C77DFF>✦ Armure premium <dark_gray>· <gray>tous les outils");
        l.add("<dark_gray><italic>" + a.devise);
        l.add("");
        l.add("<#62CFF4><bold>Chaque pièce portée :</bold>");
        l.add("<dark_gray> • <#62CFF4>+" + b + " % <white>de Crédits et de Flux");
        l.add("<dark_gray> • <#62CFF4>+" + b + " % <white>d'XP d'outil");
        l.add("<#E0B864><bold>Set complet (4/4) : +" + set + " %</bold>");
        l.add("<dark_gray>Se cumule avec les sets de farm.");
        if (boutique) {
            l.add("");
            l.add("<gray>Pièce : <#C77DFF>" + Monnaies.Monnaie.PLASMA.format(prixPiece(a)));
            l.add("<gray>Set complet : <#C77DFF>" + Monnaies.Monnaie.PLASMA.format(prixSet(a)));
        } else {
            l.add("");
            l.add("<dark_gray>Vendue à la boutique (Plasma).");
        }
        return l;
    }

    /** Armure premium de cette pièce, ou null. */
    Premium premiumDe(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String v = it.getItemMeta().getPersistentDataContainer().get(cle, PersistentDataType.STRING);
        if (v == null || !v.startsWith("PREMIUM:")) return null;
        String[] t = v.split(":");
        try { return Premium.valueOf(t[1]); } catch (RuntimeException e) { return null; }
    }

    /** Bonus total en % des armures premium portées (Crédits, Flux, XP), quel que soit l'outil. */
    double bonusPremium(Player p) {
        int[] n = new int[Premium.values().length];
        for (ItemStack it : p.getInventory().getArmorContents()) {
            Premium a = premiumDe(it);
            if (a != null) n[a.ordinal()]++;
        }
        double total = 0;
        for (Premium a : Premium.values()) {
            int k = n[a.ordinal()];
            if (k > 0) total += k >= 4 ? bonusSet(a) : k * bonusPiece(a);
        }
        return total;
    }

    // ------------------------------------------------------------------ staff

    /** /tycoon equipement donner <outil|tout> [joueur] : true si l'outil est connu. */
    boolean donner(Player cible, String quoi) {
        List<ItemStack> l = new ArrayList<>();
        Premium premium = null;
        for (Premium a : Premium.values()) if (a.id.equalsIgnoreCase(quoi)) premium = a;
        if (quoi.equalsIgnoreCase("tout")) l.addAll(toutes());
        else if (premium != null) {
            for (Piece p : Piece.values()) l.add(premium(premium, p));
        } else {
            Set s = Set.de(Outil.Mode.parse(quoi));
            if (s == null) return false;
            for (Piece p : Piece.values()) l.add(objet(s, p));
        }
        for (ItemStack reste : cible.getInventory().addItem(l.toArray(new ItemStack[0])).values())
            cible.getWorld().dropItemNaturally(cible.getLocation(), reste);
        return true;
    }
}
