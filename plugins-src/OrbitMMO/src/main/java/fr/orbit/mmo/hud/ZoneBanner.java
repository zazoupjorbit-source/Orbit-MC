package fr.orbit.mmo.hud;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Locale;

/**
 * Bandeau d'entrée de zone (pack chargé) : une plaque HD en haut du centre de l'écran avec une étiquette (type de
 * lieu ou faction), le nom de la zone et une ligne du bas (niveaux, zone sûre…), qui apparaît puis s'efface en fondu.
 *
 * <p>Il passe par le titre du jeu, qui fait lui-même le fondu (Hud.extractTitle du client 26.3) :</p>
 * <ul>
 *   <li>le <b>titre</b> (échelle 4, 1 unité = 4 px d'interface) porte la plaque : police {@code orbitmmo:zone}, glyphes
 *   découpés en tuiles de {@value #TILE} unités ; un calque
 *   de fond blanc puis un calque de filets (depuis le 25/11 : glyphes de 27 unités posés de 120 à 12 px au-dessus du centre,
 *   la plaque occupe le haut, de 118 à 74 px) teinté par la couleur de la zone. Sa largeur totale est exactement celle de
 *   la plaque : le client la centre au pixel ;</li>
 *   <li>le <b>sous-titre</b> (échelle 2, 1 unité = 2 px) porte les textes, en polices lisses {@code orbitmmo:zone_*}
 *   (Outfit en TTF, sur-échantillonnée {@value #OVERSAMPLE} fois) remontées dans la plaque par leur décalage. Chaque
 *   ligne est centrée avec les espaces fins de ces polices (1/8 d'unité) d'après les avances d'Outfit
 *   ({@code zone-widths.txt}) ; la largeur totale du sous-titre est ramenée à zéro, le client le commence donc au centre.</li>
 * </ul>
 * Tailles, décalages et codes : tools/hd/ZoneBanner.cs (tools/gen-zone.ps1). Sans le pack : titre classique.
 */
public final class ZoneBanner {

    public enum Size {
        /** Régions, villes, hameaux, donjons, camps : étiquette, nom, ligne du bas. */
        LARGE,
        /** Lieux-dits : « LIEU-DIT » et le nom, plaque plus petite. */
        SMALL
    }

    /**
     * Une annonce.
     *
     * @param label     étiquette au-dessus du nom (écrite en capitales espacées), peut être null
     * @param info      ligne du bas (grand bandeau seulement), peut être null
     * @param accent    teinte des filets et de l'étiquette
     * @param nameColor couleur du nom
     * @param infoColor couleur de la ligne du bas
     */
    public record Banner(Size size, String label, String name, String info, TextColor accent, TextColor nameColor,
                         TextColor infoColor) {
    }

    // ------------------------------------------------------------------ Polices (tools/hd/ZoneBanner.cs)
    private static final Key BAND = font("zone");
    private static final Key LABEL = font("zone_label"), NAME = font("zone_name"), NAME_S = font("zone_name_s"), INFO = font("zone_info");
    private static final Key MINI_LABEL = font("zone_mini_label"), MINI_NAME = font("zone_mini_name");
    private static final double SZ_LABEL = 3.5, SZ_NAME = 6.5, SZ_NAME_S = 5.25, SZ_INFO = 4, SZ_MINI_LABEL = 3, SZ_MINI_NAME = 5;
    private static final int OVERSAMPLE = 8;
    /** Espacement des lettres des étiquettes, en unités du sous-titre (1 px d'interface). */
    private static final double TRACK = 0.5;

    // ------------------------------------------------------------------ Plaque
    /** Largeur d'une tuile, en unités du titre. */
    private static final int TILE = 15;
    private static final int LARGE_W = 64, SMALL_W = 48;
    private static final char LARGE_BODY = '', LARGE_TRIM = '', SMALL_BODY = '', SMALL_TRIM = '';

    /** Largeurs maximales des textes, en unités du sous-titre (2 px) : elles tiennent dans la plaque. */
    private static final double NAME_MAX = 88, NAME_S_MAX = 98, LINE_MAX = 92, MINI_NAME_MAX = 72;

    private static final TextColor DIM = TextColor.color(0x5A6270);

    /** Avances d'Outfit en 1/10000 de cadratin (0 = caractère absent). */
    private static final int[] REGULAR = new int[0x10000];
    private static final int[] BOLD = new int[0x10000];

    static {
        try (InputStream in = ZoneBanner.class.getResourceAsStream("/zone-widths.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = r.readLine()) != null; ) {
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    String[] p = line.trim().split(" ");
                    int c = Integer.parseInt(p[0], 16);
                    REGULAR[c] = Integer.parseInt(p[1]);
                    BOLD[c] = Integer.parseInt(p[2]);
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Table absente : les textes sont filtrés à l'ASCII et centrés à peu près (voir advance).
        }
    }

    private ZoneBanner() {
    }

    private static Key font(String name) {
        return Key.key("orbitmmo", name);
    }

    // ------------------------------------------------------------------ Affichage

    /** Affiche le bandeau ; sans le pack, le titre classique d'avant (nom en grand, étiquette et ligne du bas dessous). */
    public static void show(OrbitMMO plugin, Player p, Banner b) {
        if (plugin.pack().isLoaded(p.getUniqueId())) {
            p.showTitle(title(b));
            return;
        }
        String name = Text.esc(b.name());
        if (b.size() == Size.SMALL) {
            Text.title(p, "", hex(b.nameColor()) + name + " <#5A6270>·</#5A6270> <#8C96A3>" + Text.esc(b.label() == null ? "lieu-dit" : b.label().toLowerCase(Locale.FRENCH)), 6, 40, 14);
            return;
        }
        StringBuilder sub = new StringBuilder("<#D7DCE2>");
        if (b.label() != null) {
            sub.append(Text.esc(b.label()));
        }
        if (b.info() != null) {
            sub.append(b.label() != null ? " <#5A6270>·</#5A6270> " : "").append(hex(b.infoColor())).append(Text.esc(b.info()));
        }
        Text.title(p, hex(b.nameColor()) + "<b>" + name + "</b>", sub.toString(), 8, 50, 16);
    }

    private static String hex(TextColor c) {
        return "<" + c.asHexString() + ">";
    }

    /** Titre du jeu portant le bandeau (pack chargé). */
    public static Title title(Banner b) {
        boolean large = b.size() == Size.LARGE;
        Title.Times times = large
                ? Title.Times.times(ticks(10), ticks(60), ticks(25))
                : Title.Times.times(ticks(8), ticks(45), ticks(20));
        return Title.title(band(b), texts(b), times);
    }

    private static Duration ticks(int t) {
        return Duration.ofMillis(t * 50L);
    }

    /** Plaque : le fond, retour au début, puis les filets teintés. Largeur totale = largeur de la plaque. */
    private static Component band(Banner b) {
        boolean large = b.size() == Size.LARGE;
        int w = large ? LARGE_W : SMALL_W;
        Component body = Component.text(tiles(large ? LARGE_BODY : SMALL_BODY, w) + space(-w));
        Component trim = Component.text(tiles(large ? LARGE_TRIM : SMALL_TRIM, w));
        return Component.text().append(style(body, BAND, NamedTextColor.WHITE)).append(style(trim, BAND, b.accent())).build();
    }

    /** Tuiles d'une image de la plaque : chacune avance de sa largeur + 1, puis recule d'une unité. */
    private static String tiles(char first, int width) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i * TILE < width; i++) {
            sb.append((char) (first + i)).append(space(-1));
        }
        return sb.toString();
    }

    /** Textes du sous-titre, chacun centré (le nom d'abord : c'est lui qui doit tomber le plus juste). */
    private static Component texts(Banner b) {
        Layout l = new Layout();
        if (b.size() == Size.LARGE) {
            String name = clean(b.name());
            Key nameFont = NAME;
            double nameSize = SZ_NAME;
            if (width(name, true, SZ_NAME, 0) > NAME_MAX) {
                nameFont = NAME_S;
                nameSize = SZ_NAME_S;
                name = fit(name, true, SZ_NAME_S, NAME_S_MAX);
            }
            l.line(name, nameFont, true, nameSize, 0, b.nameColor());
            if (b.label() != null && !b.label().isBlank()) {
                String label = fit(clean(b.label().toUpperCase(Locale.FRENCH)), true, SZ_LABEL, LINE_MAX, TRACK);
                l.line(label, LABEL, true, SZ_LABEL, TRACK, b.accent());
            }
            if (b.info() != null && !b.info().isBlank()) {
                l.line(fit(clean(b.info()), false, SZ_INFO, LINE_MAX), INFO, false, SZ_INFO, 0, b.infoColor());
            }
        } else {
            l.line(fit(clean(b.name()), true, SZ_MINI_NAME, MINI_NAME_MAX), MINI_NAME, true, SZ_MINI_NAME, 0, b.nameColor());
            String label = b.label() == null ? "Lieu-dit" : b.label();
            l.line(clean(label.toUpperCase(Locale.FRENCH)), MINI_LABEL, true, SZ_MINI_LABEL, TRACK, b.accent());
        }
        return l.build();
    }

    /** Lignes de texte centrées sur une même ligne du sous-titre ; le curseur (estimé) revient à 0 à la fin. */
    private static final class Layout {
        private final TextComponent.Builder out = Component.text();
        private double cur;
        private Key lastFont = BAND;

        void line(String text, Key font, boolean bold, double size, double track, TextColor color) {
            if (text.isEmpty()) {
                return;
            }
            double w = width(text, bold, size, track);
            String shift = move(-w / 2);
            out.append(style(Component.text(shift + tracked(text, track)), font, color));
            cur += w;
            lastFont = font;
        }

        /** Espaces pour aller de cur à x (au 1/8 d'unité près). */
        private String move(double x) {
            double d = Math.round((x - cur) * 8) / 8.0;
            cur += d;
            return space(d);
        }

        Component build() {
            String back = move(0);
            if (!back.isEmpty()) {
                out.append(style(Component.text(back), lastFont, NamedTextColor.WHITE));
            }
            return out.build();
        }
    }

    private static Component style(Component c, Key font, TextColor color) {
        return c.font(font).color(color).shadowColor(ShadowColor.none())
                .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false);
    }

    // ------------------------------------------------------------------ Espaces fins et largeurs

    /** Espaces des polices du bandeau : F900 + k = −2^(k−3) unités, F910 + k = +2^(k−3) unités (1/8 à 128). */
    static String space(double units) {
        long n = Math.round(units * 8);
        if (n == 0) {
            return "";
        }
        int base = n < 0 ? 0xF900 : 0xF910;
        long m = Math.abs(n);
        StringBuilder sb = new StringBuilder();
        while (m > 2047) {
            sb.append((char) (base + 10));
            m -= 1024;
        }
        for (int k = 0; k <= 10; k++) {
            if (((m >> k) & 1) != 0) {
                sb.append((char) (base + k));
            }
        }
        return sb.toString();
    }

    /** Lettres séparées par un espace fin de {@code track} unités. */
    private static String tracked(String s, double track) {
        if (track <= 0 || s.length() < 2) {
            return s;
        }
        String gap = space(track);
        StringBuilder sb = new StringBuilder(s.length() * (1 + gap.length()));
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) {
                sb.append(gap);
            }
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    /**
     * Avance d'un caractère dans une police lisse, en unités : comme le client (FreeType à {@code taille x}
     * {@value #OVERSAMPLE} px, avance arrondie au pixel, divisée par le sur-échantillonnage).
     */
    private static double advance(char c, boolean bold, double size) {
        int a = (bold ? BOLD : REGULAR)[c];
        long ppem = Math.round(size * OVERSAMPLE);
        if (a <= 0) {
            a = 5600; // table absente : largeur moyenne
        }
        return Math.round(a / 10000.0 * ppem) / (double) OVERSAMPLE;
    }

    static double width(String s, boolean bold, double size, double track) {
        double w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += advance(s.charAt(i), bold, size);
        }
        return w + (s.length() > 1 ? track * (s.length() - 1) : 0);
    }

    private static String fit(String s, boolean bold, double size, double max) {
        return fit(s, bold, size, max, 0);
    }

    /** Coupe un texte trop large (avec « … »). */
    private static String fit(String s, boolean bold, double size, double max, double track) {
        if (width(s, bold, size, track) <= max) {
            return s;
        }
        String dots = has('…') ? "…" : "...";
        StringBuilder out = new StringBuilder(s);
        while (out.length() > 1 && width(out.toString().stripTrailing() + dots, bold, size, track) > max) {
            out.setLength(out.length() - 1);
        }
        return out.toString().stripTrailing() + dots;
    }

    private static boolean has(char c) {
        return REGULAR[c] > 0 && BOLD[c] > 0;
    }

    /**
     * Ne garde que les caractères qu'Outfit sait dessiner (sinon le client prendrait une police de secours, mal
     * placée) : accents retirés en dernier recours, le reste supprimé.
     */
    static String clean(String s) {
        if (s == null) {
            return "";
        }
        boolean table = BOLD[' '] > 0;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                c = ' ';
            }
            if (table ? has(c) : c >= 0x20 && c < 0x7F) {
                out.append(c);
                continue;
            }
            String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            char b = base.isEmpty() ? 0 : base.charAt(0);
            if (b != c && (table ? has(b) : b >= 0x20 && b < 0x7F)) {
                out.append(b);
            }
        }
        return out.toString().replaceAll(" {2,}", " ").trim();
    }
}
