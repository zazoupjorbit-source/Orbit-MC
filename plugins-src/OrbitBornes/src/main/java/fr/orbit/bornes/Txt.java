package fr.orbit.bornes;

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

/** Messages des bornes (MiniMessage) aux couleurs ORBIT et néons de l'arcade. */
public final class Txt {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<gradient:#C79BFF:#62CFF4><b>BORNES</b></gradient> <#5A6270>»</#5A6270> <#E6EDF3>";
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

    public static void title(Player p, String big, String small, int stayMs) {
        p.showTitle(Title.title(mm(big), mm(small),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(stayMs), Duration.ofMillis(250))));
    }

    /** Bouton cliquable du chat qui lance une commande. */
    public static Component button(String label, String color, String command, String hover) {
        return mm("<" + color + "><b>[" + label + "]</b></" + color + ">")
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(mm(hover)));
    }
}
