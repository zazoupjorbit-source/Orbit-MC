package fr.orbit.extraction.hud;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Outils de la police {@code orbitextraction:hud} (textures : tools/HudGen.java) : caractères d'espacement
 * (décalages au pixel près) et largeur estimée d'un texte en police normale. Même mécanique que la console d'OrbitMMO.
 */
final class Glyphs {

    static final Key FONT = Key.key("orbitextraction", "hud");

    private Glyphs() {
    }

    /** Suite de caractères d'espacement du pack totalisant {@code px} pixels (négatif = recul). */
    static String space(int px) {
        StringBuilder sb = new StringBuilder();
        char base = px < 0 ? '' : '';
        int n = Math.abs(px);
        for (int bit = 7; bit >= 0; bit--) {
            int val = 1 << bit;
            while (n >= val) {
                sb.append((char) (base + bit));
                n -= val;
            }
        }
        return sb.toString();
    }

    /** Largeur estimée (pixels d'interface) d'un composant en police normale (gras hérité compris). */
    static int width(Component c) {
        return width(c, false);
    }

    private static int width(Component c, boolean parentBold) {
        TextDecoration.State st = c.decoration(TextDecoration.BOLD);
        boolean bold = st == TextDecoration.State.NOT_SET ? parentBold : st == TextDecoration.State.TRUE;
        int w = c instanceof TextComponent t ? width(t.content(), bold) : 0;
        for (Component ch : c.children()) {
            w += width(ch, bold);
        }
        return w;
    }

    static int width(String s, boolean bold) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            w += advance(c);
            if (bold && c != ' ') {
                w++;
            }
        }
        return w;
    }

    private static int advance(char c) {
        return switch (c) {
            case ' ' -> 4;
            case '!', '\'', ',', '.', ':', ';', 'i', '|', '·' -> 2;
            case '`', 'l' -> 3;
            case '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
            case '<', '>', 'f', 'k' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }
}
