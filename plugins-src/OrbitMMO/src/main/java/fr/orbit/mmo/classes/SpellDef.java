package fr.orbit.mmo.classes;

import fr.orbit.mmo.clazz.WeaponType;
import fr.orbit.mmo.util.Fx;
import org.bukkit.Color;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/** Un sort (ou une technique) lu dans le fichier YAML de sa classe. Durées en secondes. */
public final class SpellDef {

    public enum Type {
        /** Coup de mêlée devant soi (cône). */
        FRAPPE,
        /** Coup(s) tout autour de soi. */
        TOURBILLON,
        /** Ruée sur la cible visée, puis coup. */
        CHARGE,
        /** Saut vers le point visé, impact en zone à l'atterrissage. */
        BOND,
        /** Cri en zone autour de soi : buff des alliés et/ou effet sur les ennemis. */
        CRI,
        /** Effet sur soi. */
        BUFF,
        /** Projectile qui part droit devant (éclate en zone si « zone » > 0). */
        PROJECTILE,
        /** Soin sur l'allié visé (ou sur soi). */
        SOIN,
        /** Zone au sol au point visé, qui frappe à chaque seconde pendant la durée. */
        ZONE
    }

    /** Les trois objets de sort du joueur : chaque sort est rangé dans l'un d'eux. */
    public enum Category {
        ATTAQUE("attaque", "Attaque", "⚔", "#FF8A3B", 1),
        DEPLACEMENT("deplacement", "Déplacement", "➶", "#7FD4FF", 2),
        BUFF("buff", "Buff", "✦", "#5EE89A", 3);

        public final String id;
        public final String label;
        public final String symbol;
        public final String hex;
        /** Case préférée dans la barre (0 à 8). */
        public final int slot;

        Category(String id, String label, String symbol, String hex, int slot) {
            this.id = id;
            this.label = label;
            this.symbol = symbol;
            this.hex = hex;
            this.slot = slot;
        }

        /** Type d'objet (clé ITEM) de l'objet de cette catégorie. */
        public String itemType() {
            return "spells_" + id;
        }

        public String tag() {
            return "<" + hex + ">" + symbol + " " + label + "</" + hex + ">";
        }

        public static Category byId(String id) {
            if (id == null) {
                return null;
            }
            for (Category c : values()) {
                if (c.id.equalsIgnoreCase(id) || c.name().equalsIgnoreCase(id) || c.itemType().equals(id)) {
                    return c;
                }
            }
            return null;
        }

        /** Catégorie par défaut d'un type de sort. */
        public static Category of(Type t) {
            return switch (t) {
                case CHARGE, BOND -> DEPLACEMENT;
                case CRI, BUFF, SOIN -> BUFF;
                default -> ATTAQUE;
            };
        }
    }

    public final String id;
    public final String classId;
    public String name;
    public Type type = Type.FRAPPE;
    /** Objet qui porte ce sort (clé « categorie », sinon déduit du type). */
    public Category category = Category.ATTAQUE;
    public Material icon = Material.PAPER;
    public String hex = "#FFFFFF";
    /** Spécialisations qui ont ce sort (vide = toute la classe). */
    public List<String> specs = List.of();
    /** Forme de faction (null = les deux). */
    public Faction faction;
    public int level = 1;
    /** Sort obtenu seulement par un talent. */
    public boolean talentOnly;
    public double cost;
    public double gain;
    public double cooldown;
    public double castTime;
    public boolean gcd = true;
    public double range = 3.5;
    public double minRange;
    public double arc = 100;
    public double radius;
    public int hits = 1;
    public int hitInterval = 4;
    public double power = 1.0;
    public double duration;
    public List<WeaponType> weapons = List.of();
    public boolean shield;
    /** Condition d'utilisation (ex. « cible-pv<20 »). */
    public String condition;
    /** Effets sur la cible ou sur soi (clé → valeur). */
    public Map<String, Double> effects = Map.of();
    public String desc = "";
    private Color color;

    public SpellDef(String id, String classId) {
        this.id = id;
        this.classId = classId;
        this.name = id;
    }

    public Color color() {
        if (color == null) {
            color = Fx.color(hex);
        }
        return color;
    }

    public double effect(String key) {
        Double v = effects.get(key);
        return v == null ? 0 : v;
    }

    public boolean has(String key) {
        return effects.containsKey(key);
    }

    public String tag() {
        return "<" + hex + ">" + name + "</" + hex + ">";
    }
}
