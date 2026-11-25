package fr.orbit.royale;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Les cartes du Siège Orbital : unités, sorts et bâtiments de l'univers ORBIT (aucun personnage repris d'un autre jeu).
 * Unités en cases (1 case = 1 bloc), vitesses en cases par seconde, cadences en ticks.
 * Les 6 premières sont débloquées d'office ; les autres au record de trophées indiqué (jamais en payant).
 */
public enum Card {

    //            id            nom                     coût type             trophées matériau (sans pack)
    SENTINELLE("sentinelle", "Sentinelle", 3, Kind.TROOP, 0, Material.IRON_GOLEM_SPAWN_EGG,
            "Robot de garde de la station. Solide au corps à corps.") {{
        hp = 850; dmg = 95; hit = 24; range = 0.7; speed = 1.0; size = 0.45; scale = 1.5f;
    }},
    CANONNIER("canonnier", "Canonnier", 3, Kind.TROOP, 0, Material.SKELETON_SPAWN_EGG,
            "Tire de loin au canon à plasma. Touche les unités volantes.") {{
        hp = 340; dmg = 85; hit = 28; range = 5.0; speed = 1.0; size = 0.4; air = true; scale = 1.3f;
    }},
    ESSAIM("essaim", "Essaim d'Orbitos", 2, Kind.TROOP, 0, Material.SLIME_SPAWN_EGG,
            "Quatre petits Orbitos rapides et fragiles.") {{
        hp = 95; dmg = 50; hit = 20; range = 0.5; speed = 1.6; size = 0.3; count = 4; scale = 0.9f;
    }},
    CHASSEUR("chasseur", "Chasseur stellaire", 4, Kind.TROOP, 0, Material.PHANTOM_SPAWN_EGG,
            "Vaisseau léger qui vole au-dessus de la faille. Touche les volants.") {{
        hp = 520; dmg = 100; hit = 30; range = 3.0; speed = 1.3; size = 0.5; fly = true; air = true; scale = 1.6f;
    }},
    FRAPPE("frappe", "Frappe orbitale", 4, Kind.SPELL, 0, Material.FIRE_CHARGE,
            "Rayon tiré depuis l'orbite : gros dégâts de zone (35 % sur les tours).") {{
        dmg = 430; radius = 2.5; towerPct = 0.35;
    }},
    TOURELLE("tourelle", "Tourelle à plasma", 4, Kind.BUILDING, 0, Material.DISPENSER,
            "Défense fixe qui tire sur tout ce qui approche. S'use en 30 s.") {{
        hp = 900; dmg = 55; hit = 16; range = 6.0; size = 0.7; air = true; life = 600; scale = 1.6f;
    }},
    GIVRE("givre", "Onde de givre", 2, Kind.SPELL, 30, Material.SNOWBALL,
            "Gèle tout dans la zone pendant 2,5 s et fait de petits dégâts.") {{
        dmg = 100; radius = 3.0; towerPct = 0.35; freeze = 50;
    }},
    DRONES("drones", "Drones de garde", 3, Kind.TROOP, 60, Material.VEX_SPAWN_EGG,
            "Trois drones volants qui piquent vite.") {{
        hp = 130; dmg = 45; hit = 22; range = 2.5; speed = 1.5; size = 0.3; fly = true; air = true; count = 3; scale = 1.0f;
    }},
    COLOSSE("colosse", "Colosse du Néant", 5, Kind.TROOP, 100, Material.RAVAGER_SPAWN_EGG,
            "Géant lent venu du Néant. Ne vise que les tours et bâtiments.") {{
        hp = 2800; dmg = 150; hit = 30; range = 0.8; speed = 0.7; size = 0.8; towersOnly = true; scale = 2.3f;
    }},
    CHEVALIER("chevalier", "Chevalier de l'Aube", 4, Kind.TROOP, 150, Material.VINDICATOR_SPAWN_EGG,
            "Guerrier de la Lumière, rapide et qui frappe fort.") {{
        hp = 1150; dmg = 170; hit = 26; range = 0.9; speed = 1.5; size = 0.5; scale = 1.6f;
    }},
    MAGE("mage", "Mage de l'Ombre", 5, Kind.TROOP, 220, Material.EVOKER_SPAWN_EGG,
            "Sorts d'ombre à distance qui touchent toute une zone.") {{
        hp = 560; dmg = 120; hit = 32; range = 5.0; speed = 1.0; size = 0.45; air = true; splash = 1.5; scale = 1.5f;
    }},
    PORTAIL("portail", "Portail d'Orbitos", 5, Kind.BUILDING, 300, Material.END_PORTAL_FRAME,
            "Fait sortir un Orbito toutes les 4 s pendant 30 s.") {{
        hp = 700; size = 0.8; life = 600; spawnEvery = 80; scale = 1.8f;
    }};

    public enum Kind { TROOP, SPELL, BUILDING }

    public final String id, nom, description;
    public final int cost, trophies;
    public final Kind kind;
    public final Material fallback;

    // statistiques (remplies dans le corps de chaque carte)
    double hp, dmg, range, speed, size = 0.4, radius, towerPct = 1, splash;
    int hit = 20, count = 1, life, freeze, spawnEvery;
    boolean fly, air, towersOnly;
    float scale = 1.3f;

    Card(String id, String nom, int cost, Kind kind, int trophies, Material fallback, String description) {
        this.id = id;
        this.nom = nom;
        this.cost = cost;
        this.kind = kind;
        this.trophies = trophies;
        this.fallback = fallback;
        this.description = description;
    }

    public static Card byId(String id) {
        if (id == null) {
            return null;
        }
        String s = id.trim().toLowerCase(Locale.ROOT);
        for (Card c : values()) {
            if (c.id.equals(s)) {
                return c;
            }
        }
        return null;
    }

    /** Cartes débloquées avec ce record de trophées. */
    public static List<Card> unlocked(int record) {
        List<Card> l = new ArrayList<>();
        for (Card c : values()) {
            if (c.trophies <= record) {
                l.add(c);
            }
        }
        return l;
    }

    /** Deck de départ : les 6 cartes d'office. */
    public static List<Card> starter() {
        return new ArrayList<>(List.of(SENTINELLE, CANONNIER, ESSAIM, CHASSEUR, FRAPPE, TOURELLE));
    }

    public String typeLabel() {
        return switch (kind) {
            case SPELL -> "Sort";
            case BUILDING -> "Bâtiment";
            default -> fly ? "Unité volante" : range >= 2 ? "Unité à distance" : "Unité de mêlée";
        };
    }

    /** Couleur MiniMessage de la carte selon son coût (repère rapide dans la barre). */
    public String color() {
        return switch (kind) {
            case SPELL -> "#FF8A4A";
            case BUILDING -> "#E0B864";
            default -> fly ? "#62CFF4" : "#7FA8FF";
        };
    }
}
