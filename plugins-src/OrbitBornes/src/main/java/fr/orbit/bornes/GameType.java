package fr.orbit.bornes;

import org.bukkit.entity.Villager;

/** Les 4 bornes : identifiant (arcade_jeux.jeu, ≤ 16 caractères), noms, unité du tableau, PNJ. */
public enum GameType {
    ASTROBLOCS("astroblocs", "ASTROBLOCS", "#62CFF4", "#3A7FFF", "pts", 20, -17.5, Villager.Profession.MASON,
            "Empile les blocs qui tombent du ciel\nSolo ou duel (lignes envoyées à l'autre)"),
    CRISTAUX("cristaux", "CRISTAUX D'ORBIT", "#FF4FD8", "#E0B864", "★", 21, -13.5, Villager.Profession.CLERIC,
            "Aligne 3 cristaux ou plus · 15 niveaux\nÉcran HD (pack des bornes)"),
    DEFENSE("defense", "DÉFENSE ORBITALE", "#7CFF8A", "#62CFF4", "pts", 22, -9.5, Villager.Profession.WEAPONSMITH,
            "Repousse les vagues de drones\navant qu'ils n'atteignent la station"),
    PULSAR("pulsar", "PULSAR", "#C79BFF", "#FF4FD8", "pts", 23, -5.5, Villager.Profession.LIBRARIAN,
            "Frappe les notes en rythme\nQ S Z D (ou A S W D)");

    public final String id, name, c1, c2, unit, description;
    public final int order;
    public final double npcX;
    public final Villager.Profession profession;

    GameType(String id, String name, String c1, String c2, String unit, int order, double npcX, Villager.Profession profession,
             String description) {
        this.id = id;
        this.name = name;
        this.c1 = c1;
        this.c2 = c2;
        this.unit = unit;
        this.order = order;
        this.npcX = npcX;
        this.profession = profession;
        this.description = description;
    }

    /** Nom en dégradé MiniMessage (aussi écrit dans arcade_jeux.titre). */
    public String title() {
        return "<gradient:" + c1 + ":" + c2 + "><b>" + name + "</b></gradient>";
    }

    public static GameType of(String s) {
        for (GameType t : values()) {
            if (t.id.equalsIgnoreCase(s) || t.name().equalsIgnoreCase(s)) {
                return t;
            }
        }
        return null;
    }
}
