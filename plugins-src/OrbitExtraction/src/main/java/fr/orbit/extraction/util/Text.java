package fr.orbit.extraction.util;

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

/** Messages MiniMessage, titres, durées. */
public final class Text {

    public static final String PREFIX = "<#C0392B><b>☣</b></#C0392B> <#5A6270>»</#5A6270> <#D7DCE2>";
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

    /**
     * Destination des messages de barre d'action : la console du HUD (hud/Hud.java) les affiche sur sa ligne de
     * texte quand elle est dessinée (renvoie true) ; sinon, barre d'action normale.
     */
    private static java.util.function.BiPredicate<Player, Component> barSink;

    public static void barSink(java.util.function.BiPredicate<Player, Component> sink) {
        barSink = sink;
    }

    public static void bar(Player p, String mini) {
        Component c = mm(mini);
        if (barSink != null && barSink.test(p, c)) {
            return;
        }
        p.sendActionBar(c);
    }

    public static void title(Player p, String title, String subtitle, int in, int stay, int out) {
        p.showTitle(Title.title(mm(title), mm(subtitle), Title.Times.times(
                Duration.ofMillis(in * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(out * 50L))));
    }

    /** 754 → « 12:34 ». */
    public static String clock(long seconds) {
        seconds = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    /** Barre de progression « ▮▮▮▯▯ ». */
    public static String gauge(double ratio, int width, String on, String off) {
        int full = (int) Math.round(Math.max(0, Math.min(1, ratio)) * width);
        return "<" + on + ">" + "▮".repeat(full) + "</" + on + "><" + off + ">" + "▮".repeat(width - full) + "</" + off + ">";
    }
}
