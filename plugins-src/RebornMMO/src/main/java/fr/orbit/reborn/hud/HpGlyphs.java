package fr.orbit.reborn.hud;

import fr.orbit.reborn.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Barre de vie dessinée avec la police du pack ({@code rebornmmo:hud}) dans le titre d'une barre de boss :
 * ligne 1 = nom, niveau et PV (police normale), ligne 2 = cadre et remplissage (glyphes du pack),
 * placés à l'endroit de la barre de boss, rendue invisible par le pack (couleur blanche).
 * Les décalages horizontaux utilisent des caractères d'espacement négatifs/positifs du pack.
 */
final class HpGlyphs {

    private static final Key FONT = Key.key("rebornmmo", "hud");
    private static final char FRAME_BIG = '';
    private static final char FRAME_SMALL = '';
    private static final char[] SEG2 = {'', '', ''};
    private static final char[] SEG8 = {'', '', ''};
    private static final TextColor GREY = TextColor.color(0x8C96A3);
    private static final TextColor MEMBER = TextColor.color(0xB8C0CA);

    private HpGlyphs() {
    }

    /**
     * @param self    barre du joueur (grande) ou d'un équipier (petite)
     * @param offsetX décalage horizontal en pixels d'interface (négatif = vers la gauche)
     */
    static Component bar(String name, int level, double hp, double max, boolean self, int offsetX) {
        int frameW = self ? 122 : 82;
        int fillW = self ? 112 : 72;
        double f = max <= 0 ? 0 : Math.max(0, Math.min(1, hp / max));
        int units = hp <= 0 ? 0 : Math.max(1, (int) Math.ceil(f * fillW / 2.0));
        int color = f > 0.5 ? 0 : f > 0.2 ? 1 : 2;

        StringBuilder g = new StringBuilder();
        if (offsetX > 0) {
            g.append(space(offsetX * 2));
        }
        g.append(self ? FRAME_BIG : FRAME_SMALL).append(space(-(frameW + 1)));
        g.append(space(2));
        for (int i = 0; i < units / 4; i++) {
            g.append(SEG8[color]).append(space(-1));
        }
        for (int i = 0; i < units % 4; i++) {
            g.append(SEG2[color]).append(space(-1));
        }
        g.append(space(-(2 + units * 2) + 2));

        TextComponent.Builder out = Component.text();
        out.append(glyphs(g.toString()));
        int rel = 2;
        out.append(Component.text(name, self ? NamedTextColor.WHITE : MEMBER).decoration(TextDecoration.BOLD, self));
        rel += width(name, self);
        if (self) {
            out.append(glyphs(space(5)));
            out.append(Component.text("Lv", GREY)).append(Component.text(" " + level, NamedTextColor.WHITE));
            rel += 5 + width("Lv " + level, false);
        }
        String hpText = (long) Math.ceil(Math.max(0, hp)) + "/" + (long) max;
        int hpW = width(hpText, false);
        int gap = Math.max(5, 2 + fillW - hpW - rel);
        out.append(glyphs(space(gap)));
        out.append(Component.text(hpText, TextColor.fromHexString(Text.hpColor(f))));
        rel += gap + hpW;
        int trail = Math.max(0, frameW - rel);
        if (offsetX < 0) {
            trail += -offsetX * 2;
        }
        if (trail > 0) {
            out.append(glyphs(space(trail)));
        }
        return out.build();
    }

    private static Component glyphs(String s) {
        return Component.text(s).font(FONT).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none())
                .decoration(TextDecoration.BOLD, false);
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

    /** Largeur (pixels d'interface) d'un texte dans la police par défaut du jeu. */
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
            case '!', '\'', ',', '.', ':', ';', 'i', '|' -> 2;
            case '`', 'l' -> 3;
            case '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
            case '<', '>', 'f', 'k' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }
}
