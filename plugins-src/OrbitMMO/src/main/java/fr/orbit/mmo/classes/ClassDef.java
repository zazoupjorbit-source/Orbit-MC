package fr.orbit.mmo.classes;

import fr.orbit.mmo.clazz.WeaponType;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Une classe jouable, lue dans plugins/OrbitMMO/classes/&lt;id&gt;.yml (ou le fichier par défaut du plugin). */
public final class ClassDef {

    public enum Armor {
        TISSU("Tissu", "LEATHER"),
        CUIR("Cuir", "LEATHER"),
        MAILLES("Mailles", "CHAINMAIL"),
        PLAQUES("Plaques", "IRON");

        public final String label;
        public final String prefix;

        Armor(String label, String prefix) {
            this.label = label;
            this.prefix = prefix;
        }
    }

    public final String id;
    public String name;
    public String hex = "#FFFFFF";
    public Material icon = Material.IRON_SWORD;
    /** false : affichée « bientôt » dans le choix de classe. */
    public boolean playable;
    public Resource resource = Resource.MANA;
    public Armor armor = Armor.TISSU;
    public double hpMult = 1.0;
    public WeaponType weapon = WeaponType.ONE_HANDED;
    public boolean shield;
    public List<String> lore = List.of();
    /** Nom de la classe selon la faction (mage de Lumière / de l'Ombre…). Vide = même nom. */
    public final Map<Faction, String> formNames = new EnumMap<>(Faction.class);
    public final Map<String, SpecDef> specs = new LinkedHashMap<>();
    public final Map<String, SpellDef> spells = new LinkedHashMap<>();

    public ClassDef(String id) {
        this.id = id;
        this.name = id;
    }

    public String name(Faction f) {
        String n = f == null ? null : formNames.get(f);
        return n == null ? name : n;
    }

    public String tag(Faction f) {
        return "<" + hex + ">" + name(f) + "</" + hex + ">";
    }

    /** Spécialisations proposées à un joueur de cette faction. */
    public List<SpecDef> specsFor(Faction f) {
        List<SpecDef> out = new ArrayList<>();
        for (SpecDef s : specs.values()) {
            if (s.faction == null || s.faction == f) {
                out.add(s);
            }
        }
        return out;
    }
}
