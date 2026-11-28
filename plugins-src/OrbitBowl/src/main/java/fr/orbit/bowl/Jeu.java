package fr.orbit.bowl;

import java.util.Locale;

/** Les activités d'ORBIT BOWL. {@code record} : code du tableau des records de l'arcade (null = pas de record). */
public enum Jeu {

    BOWLING("bowling", "ORBIT BOWL", "#3A7FFF", "#E0B864", "Bowling 10 quilles, 6 pistes", "bowling", "pts", 40),
    FLECHETTES("flechettes", "Fléchettes Stellaires", "#62CFF4", "#E0B864", "Cibles ORBIT : score max, 301, tour de l'horloge", "flechettes", "pts", 41),
    TIR("tir", "Tir aux Comètes", "#FF8A3D", "#E0B864", "Stand de tir forain : 60 s pour viser juste", "tir", "pts", 42),
    BASKET("basket", "Panier Orbital", "#FF8A3D", "#62CFF4", "Paniers en 45 s, panier mobile", "basket", "pts", 43),
    GOLF("golf", "Golf des Astéroïdes", "#7CFF8A", "#62CFF4", "Mini-golf 9 trous sous les étoiles", "golf", "pts", 44),
    BABY("baby", "Baby-Orbit", "#3A7FFF", "#E0B864", "Baby-foot 1 contre 1 (bot ou ami)", null, "buts", 45),
    LASER("laser", "Laser Nova", "#FF4FD8", "#62CFF4", "Laser game Bleu contre Or, bots en renfort", "laser", "pts", 46);

    public final String id, nom, c1, c2, description, record, unite;
    public final int ordre;

    Jeu(String id, String nom, String c1, String c2, String description, String record, String unite, int ordre) {
        this.id = id;
        this.nom = nom;
        this.c1 = c1;
        this.c2 = c2;
        this.description = description;
        this.record = record;
        this.unite = unite;
        this.ordre = ordre;
    }

    public String grad(String s) {
        return "<gradient:" + c1 + ":" + c2 + ">" + s + "</gradient>";
    }

    public String titre() {
        return grad("<b>" + nom.toUpperCase(Locale.ROOT) + "</b>");
    }

    public static Jeu parse(String s) {
        if (s == null) {
            return null;
        }
        String k = s.toLowerCase(Locale.ROOT);
        for (Jeu j : values()) {
            if (j.id.equals(k)) {
                return j;
            }
        }
        return null;
    }
}
