package fr.orbit.reborn.skill;

import fr.orbit.reborn.player.PlayerData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static fr.orbit.reborn.skill.Skill.Kind.BUFF;
import static fr.orbit.reborn.skill.Skill.Kind.DASH;
import static fr.orbit.reborn.skill.Skill.Kind.SHADOW_STEP;
import static fr.orbit.reborn.skill.Skill.Kind.SLAM;
import static fr.orbit.reborn.skill.Skill.Kind.SLASH;
import static fr.orbit.reborn.skill.Skill.Kind.SPIN;
import static fr.orbit.reborn.skill.Skill.Kind.THRUST;

/**
 * Catalogue des compétences d'épée. Débloquées par le niveau et la maîtrise de l'arbre (0 à 1000).
 * Durées en ticks (20 = 1 s).
 */
public final class Skills {

    public static final String DUAL = "dual";
    private static final Map<String, Skill> ALL = new LinkedHashMap<>();
    private static final double[] COMBO = {0, 90, 45, -45, 135, -135, 20, -70};

    private Skills() {
    }

    private static Skill s(String owner, String id, String name, Skill.Kind kind) {
        Skill skill = new Skill(owner, id, name, kind);
        ALL.put(id, skill);
        return skill;
    }

    static {
        // ---------- Épéiste (une main) ----------
        s("epeiste", "horizontal", "Horizontal", SLASH).hex("#6EC1FF").mult(1.7).range(3.6).arc(160).cd(60).post(8)
                .desc("Un large coup horizontal.");
        s("epeiste", "vertical", "Vertical", SLASH).hex("#6EC1FF").mult(2.0).range(3.4).arc(70).rolls(90).cd(60).post(8)
                .desc("Une taille verticale puissante.");
        s("epeiste", "saut_sonique", "Saut sonique", DASH).hex("#B6FF6E").req(40, 2).dash(1.5).leap().mult(2.0).range(2.6)
                .cd(120).post(12).desc("Bond vers l'avant, lame en avant.");
        s("epeiste", "carre_horizontal", "Carré horizontal", SLASH).hex("#6EC1FF").req(250, 6).hits(4, 4).mult(1.1)
                .range(3.6).arc(180).cd(200).post(18).desc("Quatre coups horizontaux enchaînés.");
        s("epeiste", "estoc_ecarlate", "Estoc écarlate", DASH).hex("#FF4040").req(450, 10).dash(1.9).mult(3.4).range(2.8)
                .cd(260).post(24).desc("Charge fulgurante, estoc dévastateur.");
        s("epeiste", "arc_celeste", "Arc céleste", SPIN).hex("#9AD8FF").req(650, 15).hits(2, 5).mult(1.6).range(4)
                .cd(240).post(16).desc("Deux tours de lame tout autour de soi.");
        s("epeiste", "ascension", "Ascension nova", SLASH).hex("#FFD66E").req(850, 22).hits(5, 4).rolls(90, 0, 45, -45, 90)
                .mult(1.15).range(3.8).arc(140).cd(360).post(28).desc("Cinq coups qui s'élèvent en étoile.");

        // ---------- Escrimeur (rapière) ----------
        s("escrimeur", "lineaire", "Linéaire", THRUST).hex("#FFFFFF").mult(1.6).range(4.6).cd(30).post(4)
                .desc("Un estoc d'une vitesse inouïe.");
        s("escrimeur", "etoile_filante", "Étoile filante", DASH).hex("#FFE88A").req(60, 2).dash(1.8).mult(2.3).range(2.6)
                .cd(140).post(12).desc("Ruée lumineuse terminée par un estoc.");
        s("escrimeur", "quadruple", "Quadruple estoc", THRUST).hex("#FF9AE0").req(250, 6).hits(4, 3).mult(0.95).range(4.4)
                .cd(180).post(14).desc("Quatre estocs en un battement de cils.");
        s("escrimeur", "pluie_etoiles", "Pluie d'étoiles", THRUST).hex("#FFC6F0").req(550, 12).hits(8, 2).mult(0.7)
                .range(4.2).cd(320).post(22).desc("Huit estocs en gerbe d'étoiles.");
        s("escrimeur", "rosace", "Rosace", THRUST).hex("#B66BFF").req(900, 25).hits(11, 2).mult(0.65).range(4.4)
                .cd(520).post(40).desc("Onze estocs, la technique ultime de la rapière.");

        // ---------- Gardien (épée + bouclier) ----------
        s("gardien", "coup_bouclier", "Coup de bouclier", SLASH).hex("#FFE066").shield().mult(1.3).range(3).arc(90)
                .effect("stun", 30).cd(100).post(8).desc("Frappe au bouclier qui étourdit.");
        s("gardien", "provocation", "Provocation", BUFF).hex("#FF8A3B").shield().range(12).effect("taunt", 0).cd(200).post(4)
                .desc("Attire sur soi tous les monstres proches.");
        s("gardien", "rempart", "Rempart", BUFF).hex("#E6C35C").shield().req(120, 3).effect("guard", 100).cd(400).post(0)
                .desc("Réduit les dégâts subis de 60 % pendant 5 s.");
        s("gardien", "charge_bouclier", "Charge du bouclier", DASH).hex("#FFD66E").shield().req(300, 8).dash(1.6).mult(1.6)
                .range(2.6).effect("knockback", 0).cd(200).post(14).desc("Charge qui renverse les ennemis.");
        s("gardien", "jugement", "Jugement sacré", SLAM).hex("#FFF3B0").shield().req(700, 18).mult(2.6).range(5)
                .effect("knockup", 0).cd(420).post(26).desc("Onde sacrée qui projette tout en l'air.");

        // ---------- Lancier (lance) ----------
        s("lancier", "percee", "Percée", THRUST).hex("#5EE89A").mult(1.8).range(5.8).cd(60).post(8)
                .desc("Long estoc qui transperce la ligne.");
        s("lancier", "helice", "Hélice", SPIN).hex("#7FFFC4").req(50, 2).hits(2, 5).mult(1.3).range(4.5).cd(120).post(12)
                .desc("La lance tournoie deux fois.");
        s("lancier", "lance_celeste", "Lance céleste", SLAM).hex("#9AFFD0").req(300, 8).leap().mult(2.6).range(4)
                .cd(260).post(22).desc("Saut puis plongeon, lance la première.");
        s("lancier", "pluie_lances", "Pluie de lances", THRUST).hex("#3FD68A").req(600, 14).hits(6, 3).mult(0.95)
                .range(5.5).cd(320).post(22).desc("Six percées en rafale.");

        // ---------- Assassin (dague) ----------
        s("assassin", "taillade", "Taillade", SLASH).hex("#B36BFF").hits(2, 3).rolls(30, -30).mult(0.9).range(3).arc(120)
                .cd(30).post(4).desc("Deux entailles rapides.");
        s("assassin", "pas_ombre", "Pas de l'ombre", SHADOW_STEP).hex("#6A3FA0").req(60, 3).mult(2.4).range(12).cd(200)
                .post(6).desc("Apparaît dans le dos de la cible : coup critique.");
        s("assassin", "dissimulation", "Dissimulation", BUFF).hex("#4A4F5A").req(150, 5).effect("hide", 160).cd(500)
                .post(0).desc("Invisible 8 s, les monstres vous perdent. Prochain coup ×2,5.");
        s("assassin", "lame_venin", "Lame venimeuse", SLASH).hex("#6EFF6E").req(350, 9).mult(1.4).range(3).arc(100)
                .effect("poison", 100).cd(160).post(8).desc("Entaille empoisonnée (dégâts sur 5 s).");
        s("assassin", "danse_lames", "Danse des lames", SPIN).hex("#D08CFF").req(700, 18).hits(7, 2).mult(0.7).range(3.5)
                .cd(360).post(20).desc("Sept coups tourbillonnants.");

        // ---------- Samouraï (katana) ----------
        s("samourai", "iai", "Iai", SLASH).hex("#FF5C5C").mult(2.0).range(3.8).arc(140).cd(80).post(10)
                .desc("Dégainer et trancher d'un seul geste.");
        s("samourai", "croissant", "Croissant", SLASH).hex("#FF8A8A").req(40, 2).hits(2, 4).rolls(60, -60).mult(1.3)
                .range(3.6).arc(130).cd(120).post(12).desc("Deux arcs en forme de lune.");
        s("samourai", "vent_tranchant", "Vent tranchant", DASH).hex("#FFB0B0").req(280, 7).dash(2.0).mult(2.4).range(2.8)
                .cd(220).post(18).desc("Traverse l'ennemi comme une rafale.");
        s("samourai", "roue_vent", "Roue de vent", SPIN).hex("#FF6B6B").req(450, 11).hits(2, 5).mult(1.6).range(4)
                .cd(200).post(14).desc("Deux tours de katana.");
        s("samourai", "mille_petales", "Mille pétales", SLASH).hex("#FFC0CB").req(800, 20).hits(5, 3)
                .rolls(0, 90, 45, -45, 0).mult(1.1).range(3.8).arc(140).cd(360).post(24).desc("Cinq coups comme des pétales.");

        // ---------- Berserker (deux mains) ----------
        s("berserker", "fracas", "Fracas", SLASH).hex("#FF8A3B").mult(2.4).range(3.6).arc(120).rolls(70).cd(100).post(14)
                .desc("Un coup lourd qui fend l'armure.");
        s("berserker", "cyclone", "Cyclone", SPIN).hex("#FFB347").req(40, 2).mult(2.0).range(4.2).cd(160).post(16)
                .desc("Balaye tout autour de soi.");
        s("berserker", "rage", "Rage", BUFF).hex("#FF3B3B").req(200, 5).effect("rage", 160).cd(600).post(0)
                .desc("+40 % de dégâts pendant 8 s (mais +20 % subis).");
        s("berserker", "ecrasement", "Écrasement", SLAM).hex("#FF6A00").req(500, 12).leap().mult(3.2).range(4.5)
                .effect("knockup", 0).cd(320).post(26).desc("Saut et impact qui soulève le sol.");

        // ---------- Forgeron (masse) ----------
        s("forgeron", "coup_masse", "Coup de masse", SLASH).hex("#C8A27A").mult(1.9).range(3.2).arc(90).rolls(90)
                .effect("stun", 20).cd(80).post(10).desc("Coup vertical qui étourdit.");
        s("forgeron", "onde_choc", "Onde de choc", SLAM).hex("#E0C090").req(60, 3).mult(1.7).range(4.2)
                .effect("knockup", 0).cd(160).post(14).desc("Frappe le sol, projette les ennemis.");
        s("forgeron", "martelement", "Martèlement", SLASH).hex("#D9A066").req(300, 8).hits(3, 5).rolls(90).mult(1.4)
                .range(3.2).arc(90).effect("stun", 15).cd(220).post(18).desc("Trois coups de masse étourdissants.");
        s("forgeron", "meteore", "Météore", SLAM).hex("#FF9A3B").req(650, 16).leap().mult(3.4).range(5)
                .effect("knockup", 0).cd(400).post(28).desc("Tombe du ciel comme un météore.");

        // ---------- Compétence unique : Double lame ----------
        s(DUAL, "croix_double", "Croix double", SLASH).hex("#9AD8FF").hits(2, 2).rolls(45, -45).mult(1.7).range(3.6)
                .arc(130).cd(100).post(8).desc("Deux lames croisées en X.");
        s(DUAL, "rafale_etoiles", "Rafale d'étoiles", SLASH).hex("#7FD4FF").req(200, 25).hits(16, 2).rolls(COMBO)
                .mult(0.6).range(3.8).arc(120).cd(600).post(40).desc("Seize coups d'une vitesse surhumaine.");
        s(DUAL, "eclipse", "Éclipse", SLASH).hex("#FFE8A0").req(800, 35).hits(27, 1).rolls(COMBO).mult(0.5).range(3.8)
                .arc(130).cd(1200).post(60).desc("Vingt-sept coups. Le délai qui suit est immense.");
    }

    public static Skill get(String id) {
        return id == null ? null : ALL.get(id);
    }

    public static Collection<Skill> all() {
        return Collections.unmodifiableCollection(ALL.values());
    }

    /** Toutes les compétences d'un arbre (id de classe ou « dual »). */
    public static List<Skill> tree(String owner) {
        List<Skill> out = new ArrayList<>();
        for (Skill s : ALL.values()) {
            if (s.owner.equals(owner)) {
                out.add(s);
            }
        }
        return out;
    }

    public static boolean isUnlocked(PlayerData d, Skill s) {
        if (DUAL.equals(s.owner)) {
            if (!d.dualBlade) {
                return false;
            }
        } else if (!s.owner.equals(d.classId)) {
            return false;
        }
        return d.level >= s.level && d.proficiency(s.owner) >= s.prof;
    }

    /** Compétences débloquées par l'avatar. */
    public static List<Skill> available(PlayerData d) {
        List<Skill> out = new ArrayList<>();
        for (Skill s : ALL.values()) {
            if (isUnlocked(d, s)) {
                out.add(s);
            }
        }
        return out;
    }
}
