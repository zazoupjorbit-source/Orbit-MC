package fr.orbit.tycoon;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
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
final class Equipements {

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
        CASQUE("Casque", Material.LEATHER_HELMET),
        PLASTRON("Plastron", Material.LEATHER_CHESTPLATE),
        JAMBIERES("Jambières", Material.LEATHER_LEGGINGS),
        BOTTES("Bottes", Material.LEATHER_BOOTS);

        final String nom;
        final Material type;

        Piece(String nom, Material type) { this.nom = nom; this.type = type; }
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

    /** La pièce p du set s (cuir teint à la couleur de l'outil, liseré doré, incassable). */
    ItemStack objet(Set s, Piece p) {
        ItemStack it = new ItemStack(p.type);
        it.editMeta(LeatherArmorMeta.class, m -> {
            m.itemName(MM.deserialize("<" + s.couleur + "><bold>" + p.nom + " " + s.nom));
            m.setColor(Color.fromRGB(Integer.parseInt(s.couleur.substring(1), 16)));
            m.setUnbreakable(true);
            m.addItemFlags(ItemFlag.HIDE_DYE, ItemFlag.HIDE_ARMOR_TRIM, ItemFlag.HIDE_UNBREAKABLE);
            m.lore(lore(s).stream().map(l -> MM.deserialize("<!italic>" + l)).toList());
            m.getPersistentDataContainer().set(cle, PersistentDataType.STRING, s.name() + ":" + p.name());
        });
        // Liseré doré (DA or) : composant « trim » (LeatherArmorMeta n'a plus setTrim en 26.3).
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.TRIM,
                io.papermc.paper.datacomponent.item.ItemArmorTrim.itemArmorTrim(new ArmorTrim(TrimMaterial.GOLD, TrimPattern.SPIRE)));
        return it;
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

    // ------------------------------------------------------------------ staff

    /** /tycoon equipement donner <outil|tout> [joueur] : true si l'outil est connu. */
    boolean donner(Player cible, String quoi) {
        List<ItemStack> l = new ArrayList<>();
        if (quoi.equalsIgnoreCase("tout")) l.addAll(toutes());
        else {
            Set s = Set.de(Outil.Mode.parse(quoi));
            if (s == null) return false;
            for (Piece p : Piece.values()) l.add(objet(s, p));
        }
        for (ItemStack reste : cible.getInventory().addItem(l.toArray(new ItemStack[0])).values())
            cible.getWorld().dropItemNaturally(cible.getLocation(), reste);
        return true;
    }
}
