package fr.orbit.mmo.hud;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;

/**
 * Carte du PNJ (pack chargé) : un panneau à droite du menu ouvert par un PNJ de ville, avec son portrait, son nom
 * et la phrase qu'il dit. Le chat, lui, est caché derrière le menu : c'est pour ça que la phrase est écrite ici.
 * Tout tient dans le titre du coffre ({@link MenuSkin}) : le panneau est un glyphe de {@code orbitmmo:hud}, le
 * texte utilise les polices {@code orbitmmo:txt81..135} (police du jeu abaissée, tools/gen-hud.ps1).
 * <p>
 * Repères, en pixels depuis le début du titre (x = 8 de la fenêtre) : la carte (112 x 160) commence en x = 176,
 * juste après le bord droit du menu, son haut est en y = -4 de la fenêtre, comme celui du panneau du menu.
 * Il faut un écran d'au moins 416 px d'interface de large, ce qui est le cas dès 1280 x 720.
 */
public final class NpcCard {

    /** Lignes de texte de la carte. */
    public static final int LINES = 7;
    /** Largeur utile d'une ligne, en pixels d'interface. */
    public static final int TEXT_WIDTH = 94;

    private static final char CARD = '';
    /** Police miniature du nom (hud_tiny, haut en y = 72 de la fenêtre). */
    private static final char T_NAME = '';
    private static final int X = 176, WIDTH = 112, NAME_MAX = 104;
    private static final Key[] LINE_FONTS = new Key[LINES];
    private static final TextColor TEXT = TextColor.color(0xEDE6D6);

    static {
        for (int i = 0; i < LINES; i++) {
            LINE_FONTS[i] = Key.key("orbitmmo", "txt" + (81 + 9 * i));
        }
    }

    /** Ce qu'un PNJ montre à un joueur : portrait (clé de {@link Portraits}, null possible), nom, couleur, phrase. */
    public static final class Card {
        final String portrait;
        final String name;
        final TextColor color;
        final List<String> lines;
        /** Vrai dès qu'un menu a affiché la carte (sinon la phrase part dans le chat). */
        private boolean shown;

        private Card(String portrait, String name, TextColor color, List<String> lines) {
            this.portrait = portrait;
            this.name = name;
            this.color = color;
            this.lines = lines;
        }

        public boolean shown() {
            return shown;
        }

        public void markShown() {
            shown = true;
        }
    }

    private NpcCard() {
    }

    /** Prépare une carte : le texte est coupé à la largeur de la carte, et à {@link #LINES} lignes au plus. */
    public static Card of(String portrait, String name, TextColor color, String text) {
        List<String> lines = new ArrayList<>(FontWidths.wrap(text == null ? "" : text, TEXT_WIDTH));
        if (lines.size() > LINES) {
            lines = new ArrayList<>(lines.subList(0, LINES));
            String last = lines.get(LINES - 1);
            while (!last.isEmpty() && FontWidths.width(last + "…", false) > TEXT_WIDTH) {
                last = last.substring(0, last.length() - 1);
            }
            lines.set(LINES - 1, last.stripTrailing() + "…");
        }
        return new Card(portrait, name == null ? "" : name, color == null ? NamedTextColor.WHITE : color, lines);
    }

    /** Ajoute la carte au titre de coffre en cours de composition. */
    static void draw(Console c, Card card) {
        c.glyph(X, CARD, WIDTH + 1, NamedTextColor.WHITE);
        if (Portraits.has(card.portrait)) {
            c.glyph(X + 32, Portraits.menu(card.portrait), Portraits.WIDTH + 1, NamedTextColor.WHITE);
        }
        String name = Console.norm(card.name);
        while (name.length() > 1 && Console.width(name) > NAME_MAX) {
            name = name.substring(0, name.length() - 1).stripTrailing();
        }
        c.textCenter(X + WIDTH / 2.0, name, T_NAME, card.color);
        for (int i = 0; i < card.lines.size() && i < LINES; i++) {
            String line = card.lines.get(i);
            if (line.isEmpty()) {
                continue;
            }
            c.plain(X + 9, Component.text(line).font(LINE_FONTS[i]).color(TEXT).decoration(TextDecoration.BOLD, false)
                    .decoration(TextDecoration.ITALIC, false), FontWidths.width(line, false));
        }
    }
}
