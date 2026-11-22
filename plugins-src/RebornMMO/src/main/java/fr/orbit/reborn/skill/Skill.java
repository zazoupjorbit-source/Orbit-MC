package fr.orbit.reborn.skill;

import fr.orbit.reborn.util.Fx;
import org.bukkit.Color;

/** Une compétence d'épée : pré-mouvement, coups assistés par le système, puis délai post-mouvement. */
public final class Skill {

    public enum Kind {
        /** Arc(s) de lame devant le joueur. */
        SLASH,
        /** Estoc(s) en ligne droite. */
        THRUST,
        /** Coup(s) circulaire(s) autour du joueur. */
        SPIN,
        /** Ruée vers l'avant qui frappe tout sur son passage. */
        DASH,
        /** Frappe au sol en zone (avec saut éventuel). */
        SLAM,
        /** Effet sur soi ou les monstres proches (provocation, garde, rage, dissimulation). */
        BUFF,
        /** Téléportation dans le dos de la cible puis coup critique. */
        SHADOW_STEP
    }

    public final String owner;
    public final String id;
    public final String name;
    public final Kind kind;

    public String hex = "#FFFFFF";
    public int prof;
    public int level = 1;
    public int hits = 1;
    public int interval = 3;
    public double mult = 1.5;
    public double range = 3.5;
    public double arc = 120;
    public int cooldown = 60;
    public int post = 8;
    public double dash;
    public boolean leap;
    public boolean shield;
    public double[] rolls = {0};
    public String effect = "";
    public int effectTicks;
    public String desc = "";
    private Color color;

    Skill(String owner, String id, String name, Kind kind) {
        this.owner = owner;
        this.id = id;
        this.name = name;
        this.kind = kind;
    }

    public Color color() {
        if (color == null) {
            color = Fx.color(hex);
        }
        return color;
    }

    public double roll(int hit) {
        return rolls[hit % rolls.length];
    }

    /** Durée de l'exécution (hors pré-mouvement), en ticks. */
    public int duration() {
        return switch (kind) {
            case DASH -> 9;
            case SLAM -> leap ? 26 : 3;
            case BUFF, SHADOW_STEP -> 1;
            default -> (hits - 1) * interval + 1;
        };
    }

    // --- Construction fluide (utilisée par Skills) ---

    Skill hex(String v) { hex = v; return this; }
    Skill req(int proficiency, int lvl) { prof = proficiency; level = lvl; return this; }
    Skill hits(int count, int every) { hits = count; interval = every; return this; }
    Skill mult(double v) { mult = v; return this; }
    Skill range(double v) { range = v; return this; }
    Skill arc(double v) { arc = v; return this; }
    Skill cd(int ticks) { cooldown = ticks; return this; }
    Skill post(int ticks) { post = ticks; return this; }
    Skill dash(double v) { dash = v; return this; }
    Skill leap() { leap = true; return this; }
    Skill shield() { shield = true; return this; }
    Skill rolls(double... v) { rolls = v; return this; }
    Skill effect(String v, int ticks) { effect = v; effectTicks = ticks; return this; }
    Skill desc(String v) { desc = v; return this; }
}
