package fr.orbit.casino.hud;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/**
 * Titre de l'écran de table écrit avec la police {@code orbitcasino:hud} du pack : fond de table en 4 tuiles,
 * puis des lignes de texte placées au pixel près grâce aux espaces négatives / positives.
 * Les coordonnées x sont celles de l'écran (0 = bord gauche du coffre, 176 = bord droit).
 */
public final class Hud {

    public static final Key FONT = Key.key("orbitcasino", "hud");
    public static final int BG_BLACKJACK = 0xE600, BG_POKER = 0xE610, BG_ROULETTE = 0xE620, BG_CRASH = 0xE630, BG_MINES = 0xE640;
    public enum Align { LEFT, CENTER, RIGHT }

    private static final Map<Integer, Integer> INDEX = new HashMap<>();
    private static final Map<Integer, Integer> ADVANCE = new HashMap<>();

    private Hud() {
    }

    public static void load(Plugin plugin) {
        INDEX.clear();
        ADVANCE.clear();
        try (InputStream in = plugin.getResource("hud-font.txt")) {
            if (in == null) {
                plugin.getLogger().warning("hud-font.txt absent : écran de table sans texte.");
                return;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            int i = 0;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t");
                int cp = Integer.parseInt(parts[0].trim());
                INDEX.put(cp, i++);
                ADVANCE.put(cp, Integer.parseInt(parts[1].trim()));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("hud-font.txt illisible : " + e.getMessage());
        }
    }

    /** Largeur d'un texte en unités GUI. */
    public static int width(String s) {
        int w = 0;
        for (int cp : clean(s).codePoints().toArray()) {
            w += cp == ' ' ? 3 : ADVANCE.getOrDefault(cp, 0);
        }
        return w;
    }

    /** Garde les caractères de l'atlas ; un accent inconnu perd son accent, le reste disparaît. */
    private static String clean(String s) {
        StringBuilder out = new StringBuilder();
        s.codePoints().forEach(cp -> {
            if (cp == ' ' || INDEX.containsKey(cp)) {
                out.appendCodePoint(cp);
                return;
            }
            String plain = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            plain.codePoints().filter(c -> c == ' ' || INDEX.containsKey(c)).forEach(out::appendCodePoint);
        });
        return out.toString();
    }

    /** Coupe un texte trop long avec « . » pour tenir dans {@code max} unités. */
    public static String fit(String s, int max) {
        String c = clean(s);
        if (width(c) <= max) {
            return c;
        }
        while (!c.isEmpty() && width(c + ".") > max) {
            c = c.substring(0, c.length() - 1);
        }
        return c.stripTrailing() + ".";
    }

    public static Builder title() {
        return new Builder();
    }

    public static final class Builder {
        private final TextComponent.Builder root = Component.text().font(FONT).color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false);
        private int cursor; // x écran
        private final StringBuilder pending = new StringBuilder();
        private TextColor pendingColor = NamedTextColor.WHITE;

        private Builder() {
        }

        private void flush() {
            if (!pending.isEmpty()) {
                root.append(Component.text(pending.toString(), pendingColor));
                pending.setLength(0);
            }
        }

        private void put(String chars, TextColor color) {
            if (!color.equals(pendingColor)) {
                flush();
                pendingColor = color;
            }
            pending.append(chars);
        }

        /** Le titre commence en x = 8 ; le fond démarre au bord (x = 0) et y revient. */
        public Builder background(int base) {
            cursor = 8;
            move(0);
            put(new String(Character.toChars(base)) + neg(1) + new String(Character.toChars(base + 1)) + neg(177)
                    + new String(Character.toChars(base + 2)) + neg(1) + new String(Character.toChars(base + 3)) + neg(177),
                    NamedTextColor.WHITE);
            return this;
        }

        /** Déplace le curseur jusqu'à x (écran). */
        public Builder move(int x) {
            int dx = x - cursor;
            if (dx != 0) {
                put(dx < 0 ? neg(-dx) : pos(dx), pendingColor);
            }
            cursor = x;
            return this;
        }

        /** Texte sur une des 4 lignes (0 à 3), aligné sur x. */
        public Builder text(int line, int x, Align align, String s, TextColor color) {
            String c = clean(s);
            if (c.isEmpty()) {
                return this;
            }
            int w = width(c);
            int start = switch (align) {
                case LEFT -> x;
                case CENTER -> x - w / 2;
                case RIGHT -> x - w;
            };
            move(start);
            StringBuilder glyphs = new StringBuilder();
            c.codePoints().forEach(cp -> {
                if (cp == ' ') {
                    glyphs.append('');
                } else {
                    glyphs.appendCodePoint(0xE700 + line * 0x100 + INDEX.get(cp));
                }
            });
            put(glyphs.toString(), color);
            cursor = start + w;
            return this;
        }

        public Builder text(int line, int x, Align align, String s, int rgb) {
            return text(line, x, align, s, TextColor.color(rgb));
        }

        public Component build() {
            flush();
            return root.build();
        }

        private static String neg(int n) {
            return spaces(n, 0xF801);
        }

        private static String pos(int n) {
            return spaces(n, 0xF811);
        }

        private static String spaces(int n, int base) {
            StringBuilder sb = new StringBuilder();
            while (n >= 256) {
                sb.append((char) (base + 8));
                n -= 256;
            }
            for (int bit = 7; bit >= 0; bit--) {
                if ((n & (1 << bit)) != 0) {
                    sb.append((char) (base + bit));
                }
            }
            return sb.toString();
        }
    }
}
