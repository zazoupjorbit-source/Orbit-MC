package fr.orbit.tycoon;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import static fr.orbit.tycoon.Navigateur.texte;

/**
 * Habillage Orbit Tycoon des menus (coffres de 1 à 6 rangées) : le titre du coffre affiche, via la police
 * orbittycoon:menu du pack, un fond dessiné (tools/gen-hud.ps1, glyphes U+E002..E007) puis le nom du menu
 * en or, centré sur le bandeau. Sans le pack : titre en dégradé et vitres bleues/noires.
 */
final class Habillage {

    private static final Key POLICE = Key.key("orbittycoon", "menu");
    /** Recul de 18 px avant le fond : l'image commence 10 px à gauche de la fenêtre. */
    private static final String RECUL_18 = "";
    /** Avance du glyphe de fond (image de 196 px + 1). */
    private static final int AVANCE_FOND = 197;
    /** Centre du bandeau du titre, en pixels depuis le début du titre. */
    private static final int CENTRE = 80;

    private Habillage() { }

    /** Le joueur a-t-il le pack Tycoon chargé (fonds dessinés disponibles) ? */
    static boolean actif(OrbitTycoon plugin, Player p) {
        return plugin.pack() != null && plugin.pack().charge(p);
    }

    /**
     * Titre d'un menu de {@code lignes} rangées : fond dessiné + nom centré si le pack est chargé,
     * sinon un simple titre en dégradé bleu → or.
     */
    static Component titre(OrbitTycoon plugin, Player p, int lignes, String nom) {
        if (!actif(plugin, p)) return texte("<gradient:#62CFF4:#E0B864><bold>" + nom + "</bold></gradient>");
        int l = Math.max(1, Math.min(6, lignes));
        char fond = (char) (0xE001 + l);
        int largeur = largeur(nom, true);
        int recul = (-18 + AVANCE_FOND) - (CENTRE - largeur / 2);
        return Component.text()
                .append(Component.text(RECUL_18 + fond + recul(recul)).font(POLICE).color(NamedTextColor.WHITE))
                .append(Component.text(nom).color(TextColor.color(0xE0B864)).decoration(TextDecoration.BOLD, true))
                .build();
    }

    /** Remplit les cases vides de vitres si le pack n'est pas chargé (avec le pack, le fond suffit). */
    static void fond(OrbitTycoon plugin, Player p, Inventory inv) {
        if (actif(plugin, p)) return;
        ItemStack a = vitre(Material.BLUE_STAINED_GLASS_PANE), b = vitre(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < inv.getSize(); i++)
            if (inv.getItem(i) == null) inv.setItem(i, i % 2 == 0 ? a : b);
    }

    static ItemStack vitre(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    /** Suite d'espaces négatifs (U+F810 = -1 px … U+F817 = -128 px) qui recule de {@code px} pixels. */
    static String recul(int px) {
        StringBuilder b = new StringBuilder();
        while (px > 0) {
            int k = 7;
            while ((1 << k) > px) k--;
            b.append((char) (0xF810 + k));
            px -= 1 << k;
        }
        return b.toString();
    }

    /** Largeur en pixels d'un texte dans la police par défaut de Minecraft (avance comprise ; +1 par lettre en gras). */
    static int largeur(String s, boolean gras) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += avance(s.charAt(i)) + (gras ? 1 : 0);
        return w;
    }

    private static int avance(char c) {
        return switch (c) {
            case ' ' -> 4;
            case '!', '\'', ',', '.', ':', ';', '|', 'i', 'í', 'ì', '·' -> 2;
            case '`', 'l' -> 3;
            case 'I', 't', '[', ']', 'î', 'ï' -> 4;
            case '"', '(', ')', '*', '<', '>', 'f', 'k', '{', '}' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }
}
