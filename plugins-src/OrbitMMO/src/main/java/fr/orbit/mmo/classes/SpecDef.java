package fr.orbit.mmo.classes;

import fr.orbit.mmo.clazz.WeaponType;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Une spécialisation : rôle, bonus passifs, arme de départ et arbre de talents. */
public final class SpecDef {

    public enum Role {
        TANK("Tank", "#7FB2FF", "🛡"),
        SOIN("Soin", "#5EE89A", "✚"),
        DEGATS("Dégâts", "#FF5C5C", "⚔");

        public final String label;
        public final String hex;
        public final String glyph;

        Role(String label, String hex, String glyph) {
            this.label = label;
            this.hex = hex;
            this.glyph = glyph;
        }

        public String tag() {
            return "<" + hex + ">" + glyph + " " + label + "</" + hex + ">";
        }
    }

    public final String id;
    public final String classId;
    public String name;
    public Role role = Role.DEGATS;
    public Faction faction;
    public Material icon = Material.BOOK;
    public String hex = "#FFFFFF";
    public List<String> lore = List.of();
    /** Remplace la ressource de la classe (null = celle de la classe). */
    public Resource resource;
    /** Arme donnée en choisissant la spécialisation (null = aucune). */
    public WeaponType weapon;
    public boolean shield;
    /** Bonus permanents de la spécialisation (mêmes clés que les talents). */
    public Map<String, Double> passives = Map.of();
    public final List<TalentDef> talents = new ArrayList<>();

    public SpecDef(String id, String classId) {
        this.id = id;
        this.classId = classId;
        this.name = id;
    }

    public TalentDef talent(String id) {
        for (TalentDef t : talents) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    public int totalRanks() {
        int n = 0;
        for (TalentDef t : talents) {
            n += t.maxRank;
        }
        return n;
    }

    public String tag() {
        return "<" + hex + ">" + name + "</" + hex + ">";
    }
}
