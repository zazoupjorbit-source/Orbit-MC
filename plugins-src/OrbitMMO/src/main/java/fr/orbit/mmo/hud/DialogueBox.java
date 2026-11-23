package fr.orbit.mmo.hud;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;

/**
 * Boîte de dialogue des PNJ (pack chargé) : un cadre en haut de l'écran, centré, avec le nom de celui qui parle,
 * jusqu'à trois lignes de texte et une ligne d'aide ; à sa droite, la case du portrait de celui qui parle.
 * Tout tient dans le titre d'une barre de boss blanche (rendue invisible par le pack) : le cadre, la case et les
 * portraits sont des glyphes de la police {@code orbitmmo:hud}, le texte utilise les polices
 * {@code orbitmmo:txt6, txt20, txt30, txt40}, copies de la police du jeu abaissées d'autant de pixels
 * (tools/gen-hud.ps1). Le titre a une avance nulle : le jeu le centre, chaque élément est placé en pixels depuis
 * le centre de l'écran. Dans un même titre, le jeu dessine les glyphes dans l'ordre du texte : le cadre d'abord,
 * puis ce qui s'écrit dessus.
 */
public final class DialogueBox {

    /** Lignes de texte par page. */
    public static final int LINES = 3;
    /** Largeur utile d'une ligne de texte, en pixels d'interface. */
    public static final int TEXT_WIDTH = 220;

    private static final char FRAME = '', SLOT = '';
    /** Police miniature de la ligne d'aide (hud_tiny, haut à y0 + 52). */
    private static final char T_HINT = '';
    private static final int FRAME_WIDTH = 246, SLOT_WIDTH = 54, GAP = 2;
    private static final Key NAME_FONT = Key.key("orbitmmo", "txt6");
    private static final Key[] LINE_FONTS = {Key.key("orbitmmo", "txt20"), Key.key("orbitmmo", "txt30"), Key.key("orbitmmo", "txt40")};
    private static final TextColor HINT = TextColor.color(0x8C96A3);

    private DialogueBox() {
    }

    /**
     * @param name      nom affiché dans le bandeau (null : pas de nom, pour la narration)
     * @param nameColor couleur du nom
     * @param lines     lignes de la page (déjà coupées par {@link FontWidths#wrap}), {@link #LINES} au plus
     * @param shown     nombre de caractères déjà « écrits » sur la page (effet machine à écrire)
     * @param textColor couleur du texte
     * @param italic    texte en italique (narration)
     * @param hint      ligne d'aide en bas à droite (police miniature : majuscules sans accents)
     * @param slot      vrai : la case du portrait est dessinée à droite (elle reste en place pendant tout le
     *                  dialogue, même quand celui qui parle n'a pas de portrait)
     * @param portrait  clé de {@link Portraits} de celui qui parle (null : case vide)
     */
    public static Component build(String name, TextColor nameColor, List<String> lines, int shown, TextColor textColor,
                                  boolean italic, String hint, boolean slot, String portrait) {
        Console c = new Console();
        int total = FRAME_WIDTH + (slot ? GAP + SLOT_WIDTH : 0);
        int left = -total / 2;
        int center = left + FRAME_WIDTH / 2;
        int textLeft = center - TEXT_WIDTH / 2;
        c.glyph(left, FRAME, FRAME_WIDTH + 1, NamedTextColor.WHITE);
        if (slot) {
            int sx = left + FRAME_WIDTH + GAP;
            c.glyph(sx, SLOT, SLOT_WIDTH + 1, NamedTextColor.WHITE);
            if (Portraits.has(portrait)) {
                c.glyph(sx + 3, Portraits.box(portrait), Portraits.WIDTH + 1, NamedTextColor.WHITE);
            }
        }
        if (name != null && !name.isEmpty()) {
            int w = FontWidths.width(name, true);
            c.plain(center - w / 2, Component.text(name).font(NAME_FONT).color(nameColor).decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false), w);
        }
        int rest = shown;
        for (int i = 0; i < lines.size() && i < LINES && rest > 0; i++) {
            String line = lines.get(i);
            String part = rest >= line.length() ? line : line.substring(0, rest);
            rest -= line.length();
            if (part.isEmpty()) {
                continue;
            }
            c.plain(textLeft, Component.text(part).font(LINE_FONTS[i]).color(textColor).decoration(TextDecoration.BOLD, false)
                    .decoration(TextDecoration.ITALIC, italic), FontWidths.width(part, false));
        }
        if (hint != null && !hint.isEmpty()) {
            c.textRight(center + TEXT_WIDTH / 2, hint, T_HINT, HINT);
        }
        return c.build(null);
    }
}
