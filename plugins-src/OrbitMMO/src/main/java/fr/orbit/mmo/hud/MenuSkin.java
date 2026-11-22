package fr.orbit.mmo.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Habillage des menus (pack chargé) : le titre du coffre porte un glyphe de fond qui recouvre toute la fenêtre
 * (cadre, cartouche du titre, inventaire du joueur), une case teintée sous chaque bouton, le titre doré dans la
 * cartouche et un bandeau facultatif sous celle-ci (sous-titre à gauche, or à droite). Textures : tools/gen-hud.ps1.
 * <p>
 * Repères : le titre d'un coffre commence en x = 8 de la fenêtre ; le fond est posé 18 px plus à gauche
 * (x = -10), son avance vaut 197 ; le centre de la cartouche est en x = 88 de la fenêtre, soit 80 depuis le début
 * du titre. La case du bouton n de la rangée r est en x = 7 + 18n, y = 17 + 18r de la fenêtre. Le client pose les
 * objets au-dessus du titre (couche supérieure dès qu'ils le recouvrent), les icônes restent donc visibles.
 */
public final class MenuSkin {

    /** Fonds dessinés à la main (cases et filets déjà dans l'image). */
    public enum Screen {
        /** Menu F, 3 rangées. */
        MAIN(''),
        /** Mon personnage (Maj+F) et inspection, 6 rangées. */
        PROFILE('');

        private final char glyph;

        Screen(char glyph) {
            this.glyph = glyph;
        }
    }

    private static final char T_TITLE = '', T_TITLE_SMALL = '', T_BAND = '';
    /** Fonds génériques : 3 rangées, puis 4, 5, 6. */
    private static final char BG_GENERIC = '';
    /** Socle puis coins de la case, deux caractères par rangée. */
    private static final char SOCKET = '';
    private static final TextColor GOLD = TextColor.color(0xFFD66E), SUB = TextColor.color(0xB8C0CA),
            NEUTRAL = TextColor.color(0x5B7BB5);
    private static final int BG_X = -18, BG_ADVANCE = 197, CENTER = 80, MAX_WIDTH = 146, BAND_END = 160, BAND_GAP = 8;

    private MenuSkin() {
    }

    /** Nombre de rangées pour lequel un fond générique existe. */
    public static boolean supports(int rows) {
        return rows >= 3 && rows <= 6;
    }

    /**
     * Titre de coffre complet. {@code screen} null : fond générique de {@code rows} rangées et une case sous chaque
     * objet de {@code items}, liseré de la couleur de son nom. {@code sub} et {@code right} peuvent être null.
     */
    public static Component title(Screen screen, int rows, String title, String sub, String right, ItemStack[] items) {
        Console c = new Console();
        c.glyph(BG_X, screen != null ? screen.glyph : (char) (BG_GENERIC + rows - 3), BG_ADVANCE, NamedTextColor.WHITE);
        if (screen == null && items != null) {
            for (int i = 0; i < Math.min(items.length, rows * 9); i++) {
                ItemStack it = items[i];
                if (it == null || it.getType().isAir()) {
                    continue;
                }
                int x = -1 + 18 * (i % 9);
                char base = (char) (SOCKET + 2 * (i / 9));
                c.glyph(x, base, 19, NamedTextColor.WHITE);
                c.glyph(x, (char) (base + 1), 19, accent(it));
            }
        }

        String s = Console.norm(title);
        if (2 * Console.width(s) <= MAX_WIDTH) {
            c.bigText((int) Math.round(CENTER - Console.width(s)), s, T_TITLE, GOLD);
        } else {
            s = fit(s, MAX_WIDTH);
            c.text((int) Math.round(CENTER - Console.width(s) / 2.0), s, T_TITLE_SMALL, GOLD);
        }

        int room = BAND_END;
        if (right != null && !right.isBlank()) {
            String r = Console.norm(right);
            room = BAND_END - Console.width(r) - BAND_GAP;
            c.text(BAND_END - Console.width(r), r, T_BAND, GOLD);
        }
        if (sub != null && !sub.isBlank() && room > 10) {
            c.text(0, fit(Console.norm(sub), room), T_BAND, SUB);
        }
        return c.build(null);
    }

    /** Coupe un texte normalisé pour qu'il tienne en {@code max} pixels. */
    private static String fit(String s, int max) {
        while (s.length() > 1 && Console.width(s) > max) {
            s = s.substring(0, s.length() - 1).stripTrailing();
        }
        return s;
    }

    /** Couleur du liseré : celle du nom de l'objet (premier texte coloré), sinon bleu neutre. */
    private static TextColor accent(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            TextColor col = firstColor(meta.displayName(), null);
            if (col != null) {
                return col;
            }
        }
        return NEUTRAL;
    }

    private static TextColor firstColor(Component c, TextColor inherited) {
        if (c == null) {
            return null;
        }
        TextColor col = c.color() != null ? c.color() : inherited;
        if (c instanceof TextComponent t && !t.content().isBlank()) {
            return col;
        }
        for (Component child : c.children()) {
            TextColor r = firstColor(child, col);
            if (r != null) {
                return r;
            }
        }
        return null;
    }
}
