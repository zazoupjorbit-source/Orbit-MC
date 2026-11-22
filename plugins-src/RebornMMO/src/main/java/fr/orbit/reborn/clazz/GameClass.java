package fr.orbit.reborn.clazz;

import org.bukkit.Material;

import java.util.List;

/** Les classes (styles de combat). Chacune a sa catégorie d'arme et son arbre de compétences d'épée. */
public enum GameClass {
    EPEISTE("epeiste", "Épéiste", "#7FB2FF", Material.IRON_SWORD, WeaponType.ONE_HANDED, false, 1.0, 1.0,
            "Épée à une main, polyvalent et rapide.",
            "Le style du combattant solitaire. Le seul",
            "à pouvoir éveiller une compétence unique..."),
    ESCRIMEUR("escrimeur", "Escrimeur", "#FF8AD8", Material.GOLDEN_SWORD, WeaponType.RAPIER, false, 0.9, 1.05,
            "Rapière, estocs fulgurants.",
            "Délais très courts et enchaînements",
            "d'estocs plus rapides que l'œil."),
    GARDIEN("gardien", "Gardien", "#E6C35C", Material.SHIELD, WeaponType.ONE_HANDED, true, 1.35, 0.85,
            "Épée et bouclier, le rempart du groupe.",
            "Provoque les monstres, encaisse les",
            "coups et protège ses alliés."),
    LANCIER("lancier", "Lancier", "#5EE89A", Material.IRON_SPEAR, WeaponType.SPEAR, false, 1.05, 1.0,
            "Lance, grande portée.",
            "Percées en ligne et attaques",
            "plongeantes depuis les airs."),
    ASSASSIN("assassin", "Assassin", "#B36BFF", Material.STONE_SWORD, WeaponType.DAGGER, false, 0.85, 1.1,
            "Dague, discrétion et coups critiques.",
            "Se dissimule, frappe dans le dos",
            "et empoisonne ses cibles."),
    SAMOURAI("samourai", "Samouraï", "#FF5C5C", Material.NETHERITE_SWORD, WeaponType.KATANA, false, 0.95, 1.08,
            "Katana, frappes dégainées.",
            "Des coups d'une précision mortelle",
            "et des enchaînements en arc."),
    BERSERKER("berserker", "Berserker", "#FF8A3B", Material.IRON_AXE, WeaponType.TWO_HANDED, false, 1.15, 1.15,
            "Arme à deux mains, dégâts bruts.",
            "Lent mais dévastateur, il entre en",
            "rage pour tout balayer."),
    FORGERON("forgeron", "Forgeron", "#C8A27A", Material.MACE, WeaponType.MACE, false, 1.1, 1.0,
            "Masse, étourdissements.",
            "Fracasse le sol, étourdit les",
            "monstres et brise leur élan.");

    public final String id;
    public final String label;
    public final String hex;
    public final Material icon;
    public final WeaponType weapon;
    /** Commence avec un bouclier ; ses compétences l'exigent en main secondaire. */
    public final boolean shield;
    public final double hpMult;
    public final double dmgMult;
    public final List<String> lore;

    GameClass(String id, String label, String hex, Material icon, WeaponType weapon, boolean shield,
              double hpMult, double dmgMult, String... lore) {
        this.id = id;
        this.label = label;
        this.hex = hex;
        this.icon = icon;
        this.weapon = weapon;
        this.shield = shield;
        this.hpMult = hpMult;
        this.dmgMult = dmgMult;
        this.lore = List.of(lore);
    }

    public static GameClass byId(String id) {
        if (id == null) {
            return null;
        }
        for (GameClass c : values()) {
            if (c.id.equalsIgnoreCase(id)) {
                return c;
            }
        }
        return null;
    }
}
