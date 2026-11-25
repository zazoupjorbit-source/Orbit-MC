package fr.orbit.arene;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.Locale;

/** Messages de l'arène (MiniMessage) aux couleurs ORBIT : bleu #3A7FFF, or #E0B864, glace #62CFF4. */
public final class Txt {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<gradient:#62CFF4:#3A7FFF><b>ARÈNE</b></gradient> <#5A6270>»</#5A6270> <#E6EDF3>";
    private static final NumberFormat NF = NumberFormat.getIntegerInstance(Locale.FRANCE);

    private Txt() {
    }

    public static Component mm(String s) {
        return MM.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String n(long value) {
        return NF.format(value).replace(' ', ' ').replace(' ', ' ');
    }

    public static String oc(long value) {
        return "<#E0B864>" + n(value) + " OC</#E0B864>";
    }

    public static void send(CommandSender to, String message) {
        to.sendMessage(mm(PREFIX + message));
    }

    public static void bar(Player p, String message) {
        p.sendActionBar(mm(message));
    }

    public static void title(Player p, String title, String sub, int stayMs) {
        p.showTitle(Title.title(mm(title), mm(sub),
                Title.Times.times(Duration.ofMillis(80), Duration.ofMillis(stayMs), Duration.ofMillis(250))));
    }

    /** Bouton cliquable du chat qui lance une commande. */
    public static Component button(String label, String color, String command, String hover) {
        return mm("<" + color + "><b>[" + label + "]</b></" + color + ">")
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(mm(hover)));
    }

    /** 2:05 */
    public static String chrono(int ticks) {
        int s = Math.max(0, (ticks + 19) / 20);
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }
}
