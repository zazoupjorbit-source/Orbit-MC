package fr.orbit.mmo.hud;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Largeurs du texte lisse du suivi des quêtes (polices TTF orbitmmo:qtext et qtext_b, Outfit, dessinées par le
 * client). Les avances viennent de font-widths-hd.txt (tools/hd/HudHd.cs) en 1/{@value #OVERSAMPLE} de pixel ; le
 * client les arrondit à sa façon (hinting), d'où une marge d'une unité par caractère : la largeur calculée n'est
 * jamais plus petite que la vraie, ce qui garde le cadre aligné. Un caractère absent d'Outfit passe par la police du
 * jeu ({@link FontWidths}).
 */
public final class HdText {

    public static final int OVERSAMPLE = 8;
    private static final short[] REGULAR = new short[0x10000];
    private static final short[] BOLD = new short[0x10000];

    static {
        try (InputStream in = HdText.class.getResourceAsStream("/font-widths-hd.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = r.readLine()) != null; ) {
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    String[] p = line.trim().split(" ");
                    int c = Integer.parseInt(p[0], 16);
                    REGULAR[c] = (short) (Integer.parseInt(p[1]) + 1);
                    BOLD[c] = (short) (Integer.parseInt(p[2]) + 1);
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Table absente : tout passe par les largeurs de la police du jeu (plus larges, donc sans risque).
        }
    }

    private HdText() {
    }

    /** Avance d'un caractère en 1/OVERSAMPLE de pixel. */
    private static int units(char c, boolean bold) {
        int u = (bold ? BOLD : REGULAR)[c];
        return u > 0 ? u : FontWidths.advance(c) * OVERSAMPLE;
    }

    /** Largeur affichée d'un texte, en pixels d'interface, arrondie vers le haut. */
    public static int width(String s, boolean bold) {
        int u = 0;
        for (int i = 0; i < s.length(); i++) {
            u += units(s.charAt(i), bold);
        }
        return (u + OVERSAMPLE - 1) / OVERSAMPLE;
    }

    /** Coupe un texte pour qu'il tienne dans {@code max} px (avec « … » s'il est coupé). */
    public static String fit(String s, int max, boolean bold) {
        if (width(s, bold) <= max) {
            return s;
        }
        String dots = "…";
        int budget = max * OVERSAMPLE - units('…', bold);
        StringBuilder out = new StringBuilder();
        int u = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int cu = units(c, bold);
            if (u + cu > budget) {
                break;
            }
            out.append(c);
            u += cu;
        }
        return out.toString().stripTrailing() + dots;
    }

    /** Coupe un texte en lignes d'au plus {@code max} pixels, entre deux mots (un mot trop long est coupé). */
    public static List<String> wrap(String text, int max, boolean bold) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            while (width(word, bold) > max) {
                if (line.length() > 0) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                int cut = 1;
                while (cut < word.length() && width(word.substring(0, cut + 1), bold) <= max) {
                    cut++;
                }
                out.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && width(candidate, bold) > max) {
                out.add(line.toString());
                line.setLength(0);
                candidate = word;
            }
            line.setLength(0);
            line.append(candidate);
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }
}
