package fr.orbit.mmo.hud;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;

/**
 * Suivi des quêtes à droite de l'écran, habillé par le pack : le tableau latéral du jeu sert de support (il est
 * ancré à droite et centré en hauteur par le client), et un cadre du pack (glyphe {@code quest_panel_<n>}, une
 * image par nombre de lignes) recouvre son fond gris.
 *
 * <p>Mise en page du client 26.3 (Hud.displayScoreboardSidebar) : largeur = la plus large des lignes et du titre,
 * gauche = largeur d'écran − largeur − 3, titre centré sur la largeur, lignes à gauche, 9 px par ligne. Toutes les
 * lignes et le titre font donc exactement {@link #W} px : le titre commence alors à la gauche des lignes, et le cadre
 * qu'il porte (6 px plus à gauche, 2 px du bord droit) couvre tout le fond du jeu.</p>
 */
public final class QuestPanel {

    private static final Key FONT = Key.key("orbitmmo", "hud");
    /** Texte lisse (Outfit en TTF, dessiné par le client) : normal et gras. */
    private static final Key TEXT_FONT = Key.key("orbitmmo", "qtext");
    private static final Key BOLD_FONT = Key.key("orbitmmo", "qtext_b");
    /** Ombre du texte lisse : noire, adoucie. */
    private static final ShadowColor SHADOW = ShadowColor.shadowColor(0x8C000000);

    /** Largeur du contenu (pixels d'interface). */
    public static final int W = 140;
    /** Nombre de lignes maximum (le jeu en montre 15 au plus). */
    public static final int MAX_ROWS = 14;
    /** Largeur de l'image du cadre (6 px à gauche du contenu, 2 px à droite). */
    private static final int FRAME_W = W + 8;
    /** Marge laissée à droite du texte, avant le bord du cadre. */
    private static final int MARGIN = 3;

    private static final char FRAME_0 = '';
    private static final char SEPARATOR = '';
    private static final char MARK_MAIN = '';
    private static final char MARK = '';
    private static final char BULLET = '';
    private static final char HERE = '';
    /** Titre en image, W px d'avance : « Quêtes » puis « Quests ». */
    private static final char TITLE_FR = '\uE740';
    private static final char TITLE_EN = '\uE741';
    private static final char ARROW_0 = '';

    private static final TextColor GOLD = TextColor.color(0xFFD66E);
    private static final TextColor GOLD_DIM = TextColor.color(0xC9A24A);
    private static final TextColor TEXT = TextColor.color(0xD7DCE2);
    private static final TextColor GREY = TextColor.color(0x8C96A3);
    private static final TextColor GREEN = TextColor.color(0x5EE89A);

    public enum Kind {
        /** Nom de la quête suivie. */
        QUEST_MAIN,
        /** Nom d'une autre quête. */
        QUEST,
        /** Objectif (une ligne, coupée en amont). */
        OBJECTIVE,
        /** Suite d'un objectif trop long. */
        OBJECTIVE_MORE,
        /** Distance vers l'objectif ({@code arrow} = 0 à 7, devant puis sens des aiguilles). */
        DISTANCE,
        /** Objectif atteint. */
        HERE,
        /** Objectif dans un autre monde. */
        ELSEWHERE,
        /** « +n autre(s) ». */
        MORE,
        /** Trait entre deux quêtes. */
        SEPARATOR,
        /** État d'une épreuve sous l'objectif (PV de l'escorté, temps restant, cargaison…). */
        STATUS,
        /** État qui demande d'agir vite (PV bas, chrono presque fini, tenue pas portée). */
        ALERT
    }

    public record Row(Kind kind, String text, int arrow) {
        public static Row of(Kind k, String text) {
            return new Row(k, text, 0);
        }
    }

    private QuestPanel() {
    }

    /** Largeur utile d'un objectif (après la puce). */
    public static int objectiveWidth() {
        return W - 9 - MARGIN;
    }

    // ------------------------------------------------------------------ Titre (porte le cadre)

    public static Component title(int rows, String label) {
        int n = Math.max(1, Math.min(MAX_ROWS, rows));
        char word = "Quests".equalsIgnoreCase(label) ? TITLE_EN : TITLE_FR;
        return glyphs(HpGlyphs.space(-6) + (char) (FRAME_0 + n) + HpGlyphs.space(-(FRAME_W + 1) + 6) + word,
                NamedTextColor.WHITE);
    }

    // ------------------------------------------------------------------ Lignes

    public static Component row(Row r) {
        TextComponent.Builder b = Component.text();
        int x;
        switch (r.kind()) {
            case SEPARATOR -> {
                b.append(glyphs(String.valueOf(SEPARATOR), NamedTextColor.WHITE));
                x = W - 3;
            }
            case QUEST_MAIN, QUEST -> {
                boolean main = r.kind() == Kind.QUEST_MAIN;
                b.append(glyphs(String.valueOf(main ? MARK_MAIN : MARK), main ? NamedTextColor.WHITE : GOLD_DIM));
                b.append(glyphs(HpGlyphs.space(2), NamedTextColor.WHITE));
                x = 10;
                String t = fit(r.text(), W - x - MARGIN, true);
                b.append(text(t, main ? GOLD : TextColor.color(0xE8C77A), true, false));
                x += width(t, true);
            }
            case OBJECTIVE, OBJECTIVE_MORE -> {
                if (r.kind() == Kind.OBJECTIVE) {
                    b.append(glyphs(HpGlyphs.space(2) + BULLET, GOLD));
                } else {
                    b.append(glyphs(HpGlyphs.space(8), NamedTextColor.WHITE));
                }
                b.append(glyphs(HpGlyphs.space(1), NamedTextColor.WHITE));
                x = 9;
                String t = fit(r.text(), W - x - MARGIN, false);
                b.append(text(t, TEXT, false, false));
                x += width(t, false);
            }
            case DISTANCE -> {
                b.append(glyphs(HpGlyphs.space(9) + (char) (ARROW_0 + Math.floorMod(r.arrow(), 8)), GOLD));
                b.append(glyphs(HpGlyphs.space(2), NamedTextColor.WHITE));
                x = 19;
                String t = fit(r.text(), W - x - MARGIN, false);
                b.append(text(t, GREY, false, false));
                x += width(t, false);
            }
            case HERE -> {
                b.append(glyphs(HpGlyphs.space(9) + HERE, GREEN));
                b.append(glyphs(HpGlyphs.space(2), NamedTextColor.WHITE));
                x = 19;
                String t = fit(r.text(), W - x - MARGIN, false);
                b.append(text(t, GREEN, false, false));
                x += width(t, false);
            }
            case STATUS, ALERT -> {
                b.append(glyphs(HpGlyphs.space(9), NamedTextColor.WHITE));
                x = 9;
                String t = fit(r.text(), W - x - MARGIN, false);
                b.append(text(t, r.kind() == Kind.ALERT ? TextColor.color(0xFF8A7A) : TextColor.color(0xE8C77A), false, false));
                x += width(t, false);
            }
            default -> {
                b.append(glyphs(HpGlyphs.space(9), NamedTextColor.WHITE));
                x = 9;
                String t = fit(r.text(), W - x - MARGIN, false);
                b.append(text(t, GREY, false, r.kind() == Kind.MORE));
                x += width(t, false);
            }
        }
        if (x < W) {
            b.append(glyphs(HpGlyphs.space(W - x), NamedTextColor.WHITE));
        }
        return b.build();
    }

    static String fit(String s, int max, boolean bold) {
        return HdText.fit(s, max, bold);
    }

    /** Coupe un objectif en deux lignes au plus (la seconde finit par « … » si besoin). */
    public static List<String> wrapObjective(String s) {
        List<String> lines = HdText.wrap(s, objectiveWidth(), false);
        List<String> out = new ArrayList<>();
        if (lines.isEmpty()) {
            out.add(s);
            return out;
        }
        out.add(lines.get(0));
        if (lines.size() > 1) {
            String rest = String.join(" ", lines.subList(1, lines.size()));
            out.add(lines.size() > 2 ? fit(rest, objectiveWidth(), false) : rest);
        }
        return out;
    }

    /**
     * Largeur d'un texte de ligne, jamais sous-estimée (voir {@link HdText}) : une ligne un peu plus étroite que
     * prévu ne gêne pas (le titre impose la largeur) ; une ligne plus large décalerait le cadre.
     */
    private static int width(String s, boolean bold) {
        return HdText.width(s, bold);
    }

    private static Component text(String s, TextColor color, boolean bold, boolean italic) {
        return Component.text(s).font(bold ? BOLD_FONT : TEXT_FONT).color(color).shadowColor(SHADOW)
                .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, italic);
    }
    private static Component glyphs(String s, TextColor color) {
        return Component.text(HdTiles.expand(s)).font(FONT).color(color).shadowColor(ShadowColor.none())
                .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false);
    }
}
