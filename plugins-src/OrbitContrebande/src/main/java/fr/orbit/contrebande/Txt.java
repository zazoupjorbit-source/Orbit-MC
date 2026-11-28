package fr.orbit.contrebande;

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

/** Messages de CONTREBANDE (MiniMessage) aux couleurs ORBIT. */
public final class Txt {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String TITLE = "<gradient:#E0B864:#62CFF4><b>CONTREBANDE</b></gradient>";
    public static final String PREFIX = TITLE + " <#5A6270>»</#5A6270> <#E6EDF3>";
    public static final String COM = "<#62CFF4><b>[COM]</b></#62CFF4> <#E6EDF3>";
    private static final NumberFormat NF = NumberFormat.getIntegerInstance(Locale.FRANCE);

    private Txt() {
    }

    public static Component mm(String s) {
        return MM.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String n(long value) {
        return NF.format(value).replace(' ', ' ').replace(' ', ' ');
    }

    /** Crédits de la partie. */
    public static String cr(long value) {
        return "<#E0B864>" + n(value) + " cr</#E0B864>";
    }

    public static String oc(long value) {
        return "<#E0B864>" + n(value) + " OC</#E0B864>";
    }

    public static void send(CommandSender to, String message) {
        to.sendMessage(mm(PREFIX + message));
    }

    public static void com(Player p, String message) {
        p.sendMessage(mm(COM + message));
    }

    public static void bar(Player p, String message) {
        p.sendActionBar(mm(message));
    }

    public static void title(Player p, String big, String small, int stayMs) {
        p.showTitle(Title.title(mm(big), mm(small),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(stayMs), Duration.ofMillis(300))));
    }

    /** Bouton cliquable du chat qui lance une commande. */
    public static Component button(String label, String color, String command, String hover) {
        return mm("<" + color + "><b>[" + label + "]</b></" + color + ">")
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(mm(hover)));
    }

    /** Étoiles de réputation (0 à 5 pleines sur 5). */
    public static String stars(int level) {
        StringBuilder sb = new StringBuilder("<#E0B864>");
        for (int i = 0; i < 5; i++) {
            if (i == level) {
                sb.append("</#E0B864><#3A4250>");
            }
            sb.append('★');
        }
        sb.append(level >= 5 ? "</#E0B864>" : "</#3A4250>");
        return sb.toString();
    }

    /** mm:ss */
    public static String clock(int seconds) {
        seconds = Math.max(0, seconds);
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }
}
