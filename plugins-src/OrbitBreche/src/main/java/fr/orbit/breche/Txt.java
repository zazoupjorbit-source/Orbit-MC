package fr.orbit.breche;

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

/** Messages de BRÈCHE (MiniMessage) aux couleurs ORBIT : bleu #3A7FFF, or #E0B864, glace #62CFF4. */
final class Txt {

    static final MiniMessage MM = MiniMessage.miniMessage();
    static final String TITLE = "<gradient:#3A7FFF:#E0B864><b>BRÈCHE</b></gradient>";
    static final String PREFIX = "<gradient:#3A7FFF:#E0B864><b>BRÈCHE</b></gradient> <#5A6270>»</#5A6270> <#E6EDF3>";
    static final String[] COUL = {"#3A7FFF", "#E0B864"};
    static final String[] EQUIPE = {"Bleu", "Or"};
    private static final NumberFormat NF = NumberFormat.getIntegerInstance(Locale.FRANCE);

    private Txt() {
    }

    static Component mm(String s) {
        return MM.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    static String n(long value) {
        return NF.format(value).replace(' ', ' ').replace(' ', ' ');
    }

    static String oc(long value) {
        return "<#E0B864>" + n(value) + " OC</#E0B864>";
    }

    static String cr(long value) {
        return "<#7CFF8A>" + n(value) + " ¤</#7CFF8A>";
    }

    static void send(CommandSender to, String message) {
        to.sendMessage(mm(PREFIX + message));
    }

    static void bar(Player p, String message) {
        p.sendActionBar(mm(message));
    }

    static void title(Player p, String title, String sub, int ms) {
        p.showTitle(Title.title(mm(title), mm(sub), Title.Times.times(Duration.ofMillis(80), Duration.ofMillis(ms), Duration.ofMillis(300))));
    }

    static Component button(String label, String color, String command, String hover) {
        return mm("<" + color + "><b>[" + label + "]</b></" + color + ">")
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(mm(hover)));
    }

    static String equipe(int e) {
        return "<" + COUL[e] + "><b>" + EQUIPE[e] + "</b></" + COUL[e] + ">";
    }

    /** Jauge de n cases : pleines dans la couleur, vides en gris. */
    static String bar(double ratio, int cells, String color) {
        int full = (int) Math.round(Math.max(0, Math.min(1, ratio)) * cells);
        return "<" + color + ">" + "▰".repeat(full) + "</" + color + "><#3A4250>" + "▱".repeat(cells - full) + "</#3A4250>";
    }

    static String chrono(int ticks) {
        int s = Math.max(0, (ticks + 19) / 20);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }
}
