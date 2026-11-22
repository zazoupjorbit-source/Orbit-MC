package fr.orbit.mmo.hud;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Console du bas de l'écran (pack chargé), dessinée dans la barre d'action avec la police {@code orbitmmo:hud}
 * (textures générées par tools/gen-hud.ps1). Chaque élément est placé en pixels d'interface par rapport au
 * centre de l'écran : la ligne entière a une avance nulle, le jeu la commence donc au centre. La hauteur de
 * chaque glyphe est fixée par le pack (ascent) ; le texte normal (sort incanté, messages) reste centré sur la
 * ligne habituelle de la barre d'action.
 */
final class Console {

    private static final Key FONT = Key.key("orbitmmo", "hud");

    /** Police miniature (3x5 + ombre), une plage par hauteur : en-têtes, barres, gemme du niveau, or, incantation. */
    static final char T_HEAD = '', T_BAR = '', T_GEM = '', T_GOLD = '', T_CAST = '';
    private static final String CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ/%.,:-+_'!?◆⇧·<>|";
    private static final String NARROW = ".,:'!·|";
    private static final String WIDE = "MNW⇧";
    /** Espace entre deux mots de la police miniature. */
    private static final int WORD = 3;

    static final char WING_L = '', WING_L_HOLE = '', WING_R = '', BRIDGE = '';
    static final char F8_1 = '', F8_8 = '', F4_1 = '', F4_8 = '', F3_1 = '', F3_8 = '';
    static final char FC_1 = '', FC_8 = '', PIP = '', PIP_EMPTY = '';
    static final char EMB_LUMIERE = '', EMB_OMBRE = '', EMB_NONE = '';
    static final char ZONE_SAFE = '', ZONE_PVP = '', ZONE_BOSS = '';
    static final char CAST_FRAME = '';

    private final List<Component> parts = new ArrayList<>();
    private final StringBuilder run = new StringBuilder();
    private TextColor runColor = NamedTextColor.WHITE;
    /** Position du curseur (pixels depuis le centre de l'écran). */
    private int cur;
    /** Décalage pas encore écrit (caractères d'espacement du pack). */
    private int pending;

    private void space(int px) {
        pending += px;
        cur += px;
    }

    void move(int x) {
        space(x - cur);
    }

    /** Glyphe posé en x ; {@code advance} = largeur de l'image + 1. */
    void glyph(int x, char c, int advance, TextColor color) {
        move(x);
        put(c, advance, color);
    }

    private void put(char c, int advance, TextColor color) {
        if (!color.equals(runColor) && run.length() > 0) {
            flush();
        }
        runColor = color;
        if (pending != 0) {
            run.append(HpGlyphs.space(pending));
            pending = 0;
        }
        run.append(c);
        cur += advance;
    }

    private void flush() {
        if (pending != 0) {
            run.append(HpGlyphs.space(pending));
            pending = 0;
        }
        if (run.length() == 0) {
            return;
        }
        parts.add(Component.text(run.toString()).font(FONT).color(runColor).shadowColor(ShadowColor.none())
                .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false));
        run.setLength(0);
    }

    /** Remplissage de {@code w} pixels à partir de x (segments de 8 puis de 1), teinté par {@code color}. */
    void fill(int x, int w, char one, char eight, TextColor color) {
        if (w <= 0) {
            return;
        }
        move(x);
        for (int i = 0; i < w / 8; i++) {
            put(eight, 9, color);
            space(-1);
        }
        for (int i = 0; i < w % 8; i++) {
            put(one, 2, color);
            space(-1);
        }
    }

    // ------------------------------------------------------------------ Police miniature

    /** Majuscules sans accents : la police miniature n'a que ces caractères. */
    static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }

    private static int core(char c) {
        return NARROW.indexOf(c) >= 0 ? 1 : WIDE.indexOf(c) >= 0 ? 5 : 3;
    }

    /** Largeur affichée (pixels) d'un texte miniature déjà normalisé. */
    static int width(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            w += c == ' ' || CHARSET.indexOf(c) < 0 ? WORD : core(c) + 1;
        }
        return w;
    }

    /** Texte miniature à partir de x ; renvoie le x de fin. */
    int text(int x, String raw, char base, TextColor color) {
        String s = norm(raw);
        move(x);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int idx = CHARSET.indexOf(c);
            if (c == ' ' || idx < 0) {
                space(WORD);
                continue;
            }
            // Glyphe = coeur + ombre d'un pixel : avance coeur + 2, puis recul d'un pixel (l'ombre sert d'espace).
            put((char) (base + idx), core(c) + 2, color);
            space(-1);
        }
        return cur;
    }

    int textCenter(double cx, String raw, char base, TextColor color) {
        return text((int) Math.round(cx - width(norm(raw)) / 2.0), raw, base, color);
    }

    int textRight(int end, String raw, char base, TextColor color) {
        return text(end - width(norm(raw)), raw, base, color);
    }

    /**
     * Texte miniature agrandi deux fois (titres des menus, plage {@code base} à l'échelle 2) à partir de x : chaque
     * glyphe avance de 2·(coeur + 1) + 1, puis recule d'un pixel. {@code s} doit être déjà normalisé.
     */
    int bigText(int x, String s, char base, TextColor color) {
        move(x);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int idx = CHARSET.indexOf(c);
            if (c == ' ' || idx < 0) {
                space(2 * WORD);
                continue;
            }
            put((char) (base + idx), 2 * (core(c) + 1) + 1, color);
            space(-1);
        }
        return cur;
    }

    // ------------------------------------------------------------------ Ligne finale

    /**
     * Ligne de la barre d'action : la console, puis {@code status} (police normale, peut être null) centré sur la
     * ligne de texte habituelle. Sa largeur est estimée : une petite erreur décale la console d'un pixel au plus
     * pendant l'affichage du message.
     */
    Component build(Component status) {
        move(0);
        if (status != null) {
            int w = HpGlyphs.width(status);
            int a = w / 2;
            space(-a);
            flush();
            parts.add(status);
            cur += w;
            move(0);
        }
        flush();
        TextComponent.Builder out = Component.text();
        for (Component c : parts) {
            out.append(c);
        }
        return out.build();
    }
}
