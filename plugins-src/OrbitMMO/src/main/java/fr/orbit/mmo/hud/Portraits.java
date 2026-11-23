package fr.orbit.mmo.hud;

import java.util.List;
import java.util.Locale;

/**
 * Portraits des PNJ (pack chargé) : le visage du PNJ, 48 x 60, dessiné par tools/gen-hud.ps1 à partir des skins
 * du pack (tools/gen-skins.ps1) et, pour les neuf visages du jeu, de ses textures. Une clé par portrait ; l'ordre de {@link #KEYS} est celui de {@code $portraits} dans
 * gen-hud.ps1 (ne pas changer l'un sans l'autre). Les PNJ de ville portent leur étiquette (« blacksmith »…),
 * les mannequins des quêtes un des neuf visages du jeu ({@link #FACES}).
 */
public final class Portraits {

    /** Les neuf apparences fournies avec le jeu, pour les mannequins (clé « visage » des fichiers de quêtes). */
    public static final List<String> FACES = List.of("alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri");
    /** Apparence d'un mannequin sans « visage » ni « skin » (celle que le jeu lui donne). */
    public static final String DEFAULT_FACE = "alex";
    /** Largeur de l'image d'un portrait. */
    static final int WIDTH = 48;

    private static final List<String> KEYS = List.of("blacksmith", "priest", "runesmith", "merchant", "alchemist", "tower",
            "tower_ombre", "alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri", "villager",
            // PNJ de quête qui ont leur skin dans le pack (même nom que le skin). 64 portraits au plus.
            "maelis", "teo", "brune", "mourant", "varenne", "orsan", "elwen", "agathe", "gilles", "seraphine", "morvain",
            "tobias", "hilde", "bertin", "fenouil", "odile", "gaspard", "clotaire", "bastien", "hardouin", "lise");
    private static final char MENU = '', BOX = '';

    private Portraits() {
    }

    public static boolean has(String key) {
        return key != null && KEYS.contains(key);
    }

    /** Portrait d'un villageois d'après son métier (clé de registre du jeu, ex. « cleric ») ; villageois simple sinon. */
    public static String villager(String profession) {
        return switch (profession == null ? "" : profession.toLowerCase(Locale.ROOT)) {
            case "armorer" -> "blacksmith";
            case "cleric" -> "priest";
            case "librarian" -> "runesmith";
            case "cartographer" -> "merchant";
            default -> "villager";
        };
    }

    /** Glyphe du portrait dans la carte des menus (haut en y = 4 de la fenêtre). */
    static char menu(String key) {
        return (char) (MENU + KEYS.indexOf(key));
    }

    /** Glyphe du portrait dans la boîte de dialogue (haut à y0 + 1, soit 2 px sous le haut du cadre). */
    static char box(String key) {
        return (char) (BOX + KEYS.indexOf(key));
    }
}
