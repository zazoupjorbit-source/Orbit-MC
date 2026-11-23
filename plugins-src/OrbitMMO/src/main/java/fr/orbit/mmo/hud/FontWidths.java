package fr.orbit.mmo.hud;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Largeurs exactes des caractères de la police par défaut du jeu (font-widths.txt, mesurées dans le jar du client
 * par tools/gen-hud.ps1). Sert à placer au pixel près le texte de la boîte de dialogue et à le couper en lignes.
 */
public final class FontWidths {

    /** Avance d'un caractère absent de la table (il passe par la police de secours du jeu, plus large). */
    private static final int UNKNOWN = 9;
    private static final byte[] TABLE = new byte[0x10000];

    static {
        try (InputStream in = FontWidths.class.getResourceAsStream("/font-widths.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = r.readLine()) != null; ) {
                    int sp = line.indexOf(' ');
                    if (sp > 0) {
                        TABLE[Integer.parseInt(line.substring(0, sp), 16)] = (byte) Integer.parseInt(line.substring(sp + 1).trim());
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Table absente ou abîmée : les largeurs par défaut ci-dessous restent utilisables.
        }
    }

    private FontWidths() {
    }

    public static int advance(char c) {
        if (c == ' ') {
            return 4;
        }
        int w = TABLE[c];
        return w > 0 ? w : UNKNOWN;
    }

    /** Largeur affichée d'un texte, en pixels d'interface (le gras ajoute un pixel par caractère). */
    public static int width(String s, boolean bold) {
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

    /** Coupe un texte en lignes d'au plus {@code max} pixels, entre deux mots (un mot trop long est coupé). */
    public static List<String> wrap(String text, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int lineW = 0;
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            int w = width(word, false);
            while (w > max) {
                // Mot plus large que la boîte : on le coupe.
                if (line.length() > 0) {
                    out.add(line.toString());
                    line.setLength(0);
                    lineW = 0;
                }
                int cut = 1;
                while (cut < word.length() && width(word.substring(0, cut + 1), false) <= max) {
                    cut++;
                }
                out.add(word.substring(0, cut));
                word = word.substring(cut);
                w = width(word, false);
            }
            int need = line.length() == 0 ? w : lineW + 4 + w;
            if (need > max && line.length() > 0) {
                out.add(line.toString());
                line.setLength(0);
                lineW = 0;
                need = w;
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
            lineW = need;
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }
}
