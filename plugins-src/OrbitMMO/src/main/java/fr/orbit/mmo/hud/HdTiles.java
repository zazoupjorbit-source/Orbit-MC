package fr.orbit.mmo.hud;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Grandes images du HUD découpées en tuiles. Le client range les glyphes des polices sur des pages de 256 x 256 px :
 * une image en haute définition plus grande est refusée (rectangle blanc). tools/gen-hud.ps1 découpe donc ces images
 * en tuiles (hud-tiles.txt) et on remplace ici chaque caractère d'origine par ses tuiles, recollées au pixel près
 * avec les espaces du pack. L'avance totale reste celle de l'image d'origine (largeur + 1).
 */
public final class HdTiles {

    private static final String[] EXPANSION = new String[0x10000];

    static {
        try (InputStream in = HdTiles.class.getResourceAsStream("/hud-tiles.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = r.readLine()) != null; ) {
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    // code premiereTuile colonnes rangees largeur largeurTuile
                    String[] p = line.trim().split(" ");
                    int code = Integer.parseInt(p[0], 16);
                    int first = Integer.parseInt(p[1], 16);
                    int cols = Integer.parseInt(p[2]);
                    int rows = Integer.parseInt(p[3]);
                    int width = Integer.parseInt(p[4]);
                    StringBuilder sb = new StringBuilder();
                    for (int row = 0; row < rows; row++) {
                        for (int col = 0; col < cols; col++) {
                            sb.append((char) (first + row * cols + col)).append(HpGlyphs.space(-1));
                        }
                        sb.append(HpGlyphs.space(-width));
                    }
                    sb.append(HpGlyphs.space(width + 1));
                    EXPANSION[code] = sb.toString();
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Sans table, les caractères restent tels quels.
        }
    }

    private HdTiles() {
    }

    /** Remplace les caractères des grandes images par leurs tuiles. */
    public static String expand(String s) {
        StringBuilder out = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String e = EXPANSION[c];
            if (e != null && out == null) {
                out = new StringBuilder(s.length() + 64).append(s, 0, i);
            }
            if (out != null) {
                if (e != null) {
                    out.append(e);
                } else {
                    out.append(c);
                }
            }
        }
        return out == null ? s : out.toString();
    }
}
