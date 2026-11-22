package fr.orbit.reborn.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Messages MiniMessage, titres, nombres. */
public final class Text {

    public static final String PREFIX = "<#3A7FFF><b>⚔</b></#3A7FFF> <#5A6270>»</#5A6270> <#D7DCE2>";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component mm(String mini) {
        return MM.deserialize(mini);
    }

    /** Nom ou ligne de description d'item (sans l'italique par défaut de Minecraft). */
    public static Component item(String mini) {
        return MM.deserialize(mini).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> lore(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(item(line));
        }
        return out;
    }

    public static String esc(String raw) {
        return MM.escapeTags(raw);
    }

    public static void msg(CommandSender to, String mini) {
        to.sendMessage(mm(PREFIX + mini));
    }

    public static void title(Player p, String title, String subtitle, int in, int stay, int out) {
        p.showTitle(Title.title(mm(title), mm(subtitle), Title.Times.times(
                Duration.ofMillis(in * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(out * 50L))));
    }

    /** 12345 → « 12 345 ». */
    public static String num(long n) {
        return String.format(Locale.ROOT, "%,d", n).replace(',', ' ');
    }

    public static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.1f s", millis / 1000.0).replace('.', ',');
    }

    /** Barre de vie en texte, verte / jaune / rouge comme dans l'anime. */
    public static String hpBar(double fraction, int length) {
        double f = Math.max(0, Math.min(1, fraction));
        int full = (int) Math.round(f * length);
        if (f > 0 && full == 0) {
            full = 1;
        }
        String color = hpColor(f);
        return "<" + color + ">" + "|".repeat(full) + "</" + color + "><#3A3F47>" + "|".repeat(length - full) + "</#3A3F47>";
    }

    public static String hpColor(double fraction) {
        return fraction > 0.5 ? "#5EE89A" : fraction > 0.2 ? "#FFE066" : "#FF5C5C";
    }
}
