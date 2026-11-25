package fr.orbit.mmo.item;

import fr.orbit.mmo.util.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Un set de 5 pièces (sets.yml) : 4 pièces d'armure d'un même type + une arme ou un bouclier,
 * avec des bonus à 2, 4 et 5 pièces portées. Les bonus sont des clés de Mods (comme les talents).
 */
public final class GearSet {

    /** Pièces possibles : clé YAML → emplacement. */
    public static final List<String> PIECES = List.of("casque", "plastron", "jambieres", "bottes", "arme", "bouclier");

    /** Une source de butin : boss (ids, « * » = tous) ou monstres des étages du Pilier, avec une chance. */
    public record Drop(List<String> bosses, int floorMin, int floorMax, double chance) {

        boolean matchesBoss(String bossId) {
            return bosses != null && (bosses.contains("*") || (bossId != null && bosses.contains(bossId)));
        }

        boolean matchesFloor(int floor) {
            return bosses == null && floor >= floorMin && floor <= floorMax;
        }
    }

    public final String id;
    public String name;
    public ArmorType type = ArmorType.PLAQUES;
    public int tier;
    public Rarity rarity = Rarity.RARE;
    /** donjon, boss ou artisan (affiché ; l'artisanat fabriquera ces sets). */
    public String origin = "donjon";
    /** true : lié quand ramassé ; false : lié quand équipé. */
    public boolean bindOnPickup;
    /** Apparence propre (clé orbitmmo:outfit) ; null : celle du type et du palier. */
    public String look;
    public int itemLevelBonus;
    /** Clé de pièce → nom affiché. */
    public final Map<String, String> pieces = new LinkedHashMap<>();
    /** Modèle de l'arme (Loot.Base), si le set a une arme. */
    public Loot.Base weapon = Loot.Base.EPEE;
    /** Nombre de pièces → bonus (clés de Mods). */
    public final TreeMap<Integer, Map<String, Double>> bonuses = new TreeMap<>();
    /** Nombre de pièces → description du bonus (sinon générée d'après les clés). */
    public final Map<Integer, String> texts = new LinkedHashMap<>();
    public final List<Drop> drops = new ArrayList<>();
    public List<String> lore = List.of();

    GearSet(String id) {
        this.id = id;
        this.name = id;
    }

    public String originLabel() {
        return switch (origin) {
            case "boss" -> "Butin de boss";
            case "artisan" -> "Artisanat";
            case "raid" -> "Raid";
            default -> "Donjon";
        };
    }

    /** Lignes de description d'un palier de bonus. */
    public List<String> bonusLines(int count) {
        List<String> out = new ArrayList<>();
        String txt = texts.get(count);
        if (txt != null) {
            out.add(txt);
            return out;
        }
        Map<String, Double> b = bonuses.get(count);
        if (b == null) {
            return out;
        }
        for (Map.Entry<String, Double> e : b.entrySet()) {
            Stat s = Stat.byMod(e.getKey());
            out.add(s != null ? s.line(e.getValue()) : "+" + fr.orbit.mmo.classes.TalentDef.format(e.getValue()) + " % " + Text.esc(e.getKey()));
        }
        return out;
    }

    /** Lignes « (2) +4 % de PV max » colorées selon le nombre de pièces portées (-1 : sans compte). */
    public List<String> describe(int worn) {
        List<String> out = new ArrayList<>();
        for (int n : bonuses.keySet()) {
            boolean on = worn >= n;
            String color = worn < 0 ? "<#B8C0CA>" : on ? "<#7CFF8A>" : "<#5A6270>";
            List<String> lines = bonusLines(n);
            for (int i = 0; i < lines.size(); i++) {
                out.add(color + (i == 0 ? "(" + n + ") " : "     ") + lines.get(i));
            }
        }
        return out;
    }
}
