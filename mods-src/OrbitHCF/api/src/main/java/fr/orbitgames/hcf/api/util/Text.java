package fr.orbitgames.hcf.api.util;

import org.bukkit.ChatColor;

/**
 * Aide au formatage des messages, partagee par tous les modules (regle 10).
 */
public final class Text {

    private Text() {
    }

    /** Traduit les codes couleur '&amp;' en codes section. */
    public static String color(String input) {
        return input == null ? "" : ChatColor.translateAlternateColorCodes('&', input);
    }

    /** Retire tous les codes couleur. */
    public static String strip(String input) {
        return input == null ? "" : ChatColor.stripColor(color(input));
    }

    /** Remplace des paires {cle} -> valeur : replace(msg, "joueur", nom, "temps", duree). */
    public static String replace(String input, Object... pairs) {
        if (input == null) {
            return "";
        }
        String result = input;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result = result.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return result;
    }

    /** Barre de progression coloree (pour l'energie du Bard par exemple). */
    public static String progressBar(double value, double max, int segments, String filledColor, String emptyColor, char symbol) {
        double ratio = max <= 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, value / max));
        int filled = (int) Math.round(ratio * segments);
        StringBuilder sb = new StringBuilder(color(filledColor));
        for (int i = 0; i < segments; i++) {
            if (i == filled) {
                sb.append(color(emptyColor));
            }
            sb.append(symbol);
        }
        return sb.toString();
    }
}
