package fr.orbit.practice;

import org.bukkit.Material;

/**
 * Un kit de combat. En classé, chacun a son propre Elo ; en casual, on joue le même kit sans Elo.
 * Les règles particulières (construction, sumo, boxe…) sont portées par {@link Rule}.
 */
public enum Ladder {
    NODEBUFF("nodebuff", "NoDebuff", Material.SPLASH_POTION,
            "Armure diamant, potions de soin, perles.", Rule.NONE),
    DEBUFF("debuff", "Debuff", Material.FERMENTED_SPIDER_EYE,
            "Comme NoDebuff, avec poison et lenteur à lancer.", Rule.NONE),
    GAPPLE("gapple", "Gapple", Material.GOLDEN_APPLE,
            "Armure diamant Prot IV, pommes d'or et potions de force.", Rule.NONE),
    BUILDUHC("builduhc", "BuildUHC", Material.LAVA_BUCKET,
            "Kit UHC : blocs, seaux, arc. Pas de régénération naturelle.", Rule.BUILD),
    COMBO("combo", "Combo", Material.PUFFERFISH,
            "Coups sans temps d'invincibilité : enchaîne les combos.", Rule.COMBO),
    SUMO("sumo", "Sumo", Material.LEAD,
            "À mains nues sur une petite plateforme : fais tomber l'autre.", Rule.SUMO),
    BOXING("boxing", "Boxing", Material.DIAMOND_SWORD,
            "Sans armure ni dégâts : le premier à 100 coups gagne.", Rule.BOXING),
    SOUP("soup", "Soup", Material.MUSHROOM_STEW,
            "Armure fer, soupes : clic droit pour te soigner.", Rule.SOUP),
    ARCHER("archer", "Archer", Material.BOW,
            "Armure cuir, arc Puissance et Frappe : tout à distance.", Rule.NONE),
    HCF("hcf", "Classes HCF", Material.GOLDEN_CHESTPLATE,
            "Diamant, Barde, Archer ou Voleur : choisis ta classe.", Rule.NONE);

    /** Règle de combat particulière d'un kit. */
    public enum Rule { NONE, BUILD, COMBO, SUMO, BOXING, SOUP }

    public final String id;
    public final String label;
    public final Material icon;
    public final String description;
    public final Rule rule;

    Ladder(String id, String label, Material icon, String description, Rule rule) {
        this.id = id;
        this.label = label;
        this.icon = icon;
        this.description = description;
        this.rule = rule;
    }

    /** Pas de régénération par la faim (seulement pommes d'or et potions). */
    public boolean noNaturalRegen() {
        return rule == Rule.BUILD;
    }

    public static Ladder byId(String id) {
        for (Ladder l : values()) {
            if (l.id.equalsIgnoreCase(id)) {
                return l;
            }
        }
        return null;
    }
}
