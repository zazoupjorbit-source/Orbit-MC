package fr.orbit.mmo.item;

import fr.orbit.mmo.classes.ClassDef;
import org.bukkit.Color;

import java.util.Locale;

/**
 * Les 4 types d'armure. Chaque classe ne porte que le sien (clé « armure » du YAML de la classe) :
 * tissu (Mage, Prêtre, Astromancien), cuir (Druide, Chasseur), mailles, plaques (Guerrier, Paladin).
 * Moins d'armure = bonus propres plus forts et tournés vers le rôle (ressource et soins pour le tissu,
 * esquive et vitesse pour le cuir, critique pour les mailles, PV et dégâts subis en moins pour les plaques).
 */
public enum ArmorType {
    TISSU("tissu", "Tissu", "#B9A6FF", 0.40, 0.85,
            new String[]{"Capuche", "Robe", "Jupe", "Chaussons"},
            new Stat[]{Stat.FOCALISATION, Stat.GRACE, Stat.VITALITE, Stat.FOCALISATION},
            new Stat[]{Stat.FOCALISATION, Stat.GRACE, Stat.PUISSANCE, Stat.PRECISION, Stat.FEROCITE, Stat.VITALITE, Stat.CELERITE},
            new String[]{"LEATHER", "LEATHER", "LEATHER", "LEATHER", "LEATHER", "LEATHER", "LEATHER", "LEATHER"},
            new int[]{0xD8CBB0, 0x6E8FC9, 0x4E6FB8, 0x8B5CC9, 0x5B3A99, 0x2C2346, 0xC9A64E, 0xE8E2F5}),
    CUIR("cuir", "Cuir", "#C98B4E", 0.60, 0.70,
            new String[]{"Coiffe", "Veste", "Jambières", "Bottes"},
            new Stat[]{Stat.PRECISION, Stat.AGILITE, Stat.AGILITE, Stat.CELERITE},
            new Stat[]{Stat.AGILITE, Stat.PRECISION, Stat.FEROCITE, Stat.CELERITE, Stat.VAMPIRISME, Stat.PUISSANCE, Stat.VITALITE},
            new String[]{"LEATHER", "LEATHER", "LEATHER", "LEATHER", "LEATHER", "LEATHER", "LEATHER", "LEATHER"},
            new int[]{0x9A6B42, 0x7A4E2C, 0x5E3A20, 0x6B5A3A, 0x3E4A2E, 0x2B2420, 0x6E2E2E, 0x30302E}),
    MAILLES("mailles", "Mailles", "#9FB4C4", 0.80, 0.60,
            new String[]{"Camail", "Haubert", "Chausses", "Solerets"},
            new Stat[]{Stat.PRECISION, Stat.FEROCITE, Stat.AGILITE, Stat.CELERITE},
            new Stat[]{Stat.PRECISION, Stat.FEROCITE, Stat.AGILITE, Stat.PUISSANCE, Stat.VITALITE, Stat.ROBUSTESSE, Stat.CELERITE},
            new String[]{"CHAINMAIL", "CHAINMAIL", "CHAINMAIL", "CHAINMAIL", "CHAINMAIL", "CHAINMAIL", "CHAINMAIL", "CHAINMAIL"},
            null),
    PLAQUES("plaques", "Plaques", "#D7DCE2", 1.00, 0.50,
            new String[]{"Casque", "Plastron", "Jambières", "Bottes"},
            new Stat[]{Stat.PRECISION, Stat.VITALITE, Stat.ROBUSTESSE, Stat.CELERITE},
            new Stat[]{Stat.VITALITE, Stat.ROBUSTESSE, Stat.AGILITE, Stat.PUISSANCE, Stat.PRECISION, Stat.FEROCITE, Stat.CELERITE,
                    Stat.FOCALISATION, Stat.EPINES, Stat.GRACE},
            new String[]{"LEATHER", "COPPER", "IRON", "GOLDEN", "DIAMOND", "NETHERITE", "NETHERITE", "NETHERITE"},
            null);

    public final String id;
    public final String label;
    public final String hex;
    /** Part des points d'armure d'une pièce de plaques. */
    public final double armorMult;
    /** Force du bonus propre à la pièce (les plaques : 0,5 comme avant la refonte). */
    public final double implicitFactor;
    /** Nom de chaque pièce : casque, plastron, jambières, bottes (ordre de Loot.Slot). */
    final String[] pieces;
    final Stat[] implicits;
    final Stat[] pool;
    private final String[] metal;
    /** Teinte du cuir par palier (null : pas teint, ou teint aux couleurs de la classe). */
    private final int[] dyes;

    ArmorType(String id, String label, String hex, double armorMult, double implicitFactor, String[] pieces, Stat[] implicits,
              Stat[] pool, String[] metal, int[] dyes) {
        this.id = id;
        this.label = label;
        this.hex = hex;
        this.armorMult = armorMult;
        this.implicitFactor = implicitFactor;
        this.pieces = pieces;
        this.implicits = implicits;
        this.pool = pool;
        this.metal = metal;
        this.dyes = dyes;
    }

    private static final String[] CLOTH = {"de lin", "de laine", "de soie", "de soie d'électrum", "de tisse-cristal",
            "de soie d'ombre", "de tisse-astral", "de tisse-orbite"};
    private static final String[] HIDE = {"de peau", "de cuir tanné", "de cuir clouté", "de cuir d'électrum", "de cuir cristallin",
            "de cuir d'obsidienne", "de cuir astral", "de cuir d'orbitium"};

    /** « de soie », « de cuir clouté », « d'acier »… selon le palier. */
    public String tierName(int tier) {
        int t = Math.max(0, Math.min(Loot.TIERS - 1, tier));
        return switch (this) {
            case TISSU -> CLOTH[t];
            case CUIR -> HIDE[t];
            default -> Loot.TIER_NAMES[t];
        };
    }

    public String tag() {
        return "<" + hex + ">" + label + "</" + hex + ">";
    }

    public String piece(Loot.Slot s) {
        return pieces[s.ordinal()];
    }

    public Stat implicit(Loot.Slot s) {
        return implicits[s.ordinal()];
    }

    /** Matériau vanilla de la pièce (il donne l'emplacement ; l'icône vient du pack, le rendu porté du paquet outfit). */
    String metal(int tier) {
        return metal[Math.max(0, Math.min(metal.length - 1, tier))];
    }

    /** Teinte de la pièce en cuir : celle du palier, sinon celle donnée (couleur de classe). */
    Color dye(int tier, Color fallback) {
        if (dyes == null) {
            return fallback;
        }
        return Color.fromRGB(dyes[Math.max(0, Math.min(dyes.length - 1, tier))]);
    }

    /** Apparence d'origine d'une pièce : « plaques_3 » (clé orbitmmo:outfit, voir docs/objets.md). */
    public String outfit(int tier) {
        return id + "_" + Math.max(0, Math.min(Loot.TIERS - 1, tier));
    }

    public static ArmorType byId(String s) {
        if (s == null) {
            return null;
        }
        String k = s.toLowerCase(Locale.ROOT);
        for (ArmorType t : values()) {
            if (t.id.equals(k) || t.name().toLowerCase(Locale.ROOT).equals(k)) {
                return t;
            }
        }
        return null;
    }

    /** Type porté par une classe (null : classe inconnue, tout est permis). */
    public static ArmorType of(ClassDef c) {
        return c == null || c.armor == null ? null : byId(c.armor.name());
    }
}
