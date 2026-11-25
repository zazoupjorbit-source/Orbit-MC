package fr.orbit.chasseurs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.text.NumberFormat;
import java.util.Locale;

/** Messages de CHASSEURS D'ORBITE (MiniMessage) aux couleurs ORBIT : bleu #3A7FFF, or #E0B864, glace #62CFF4. */
final class Txt {

    static final MiniMessage MM = MiniMessage.miniMessage();
    static final String TITLE = "<gradient:#3A7FFF:#62CFF4><b>CHASSEURS D'ORBITE</b></gradient>";
    static final String PREFIX = "<gradient:#3A7FFF:#62CFF4><b>CHASSEURS</b></gradient> <#5A6270>»</#5A6270> <#E6EDF3>";
    private static final NumberFormat NF = NumberFormat.getIntegerInstance(Locale.FRANCE);

    private Txt() {
    }

    static Component mm(String s) {
        return MM.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    static String n(long value) {
        return NF.format(value).replace(' ', ' ').replace(' ', ' ');
    }

    static String oc(long value) {
        return "<#E0B864>" + n(value) + " OC</#E0B864>";
    }

    static void send(CommandSender to, String message) {
        to.sendMessage(mm(PREFIX + message));
    }

    static Component button(String label, String color, String command, String hover) {
        return mm("<" + color + "><b>[" + label + "]</b></" + color + ">")
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(mm(hover)));
    }

    static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    /** Jauge de n cases : pleines dans la couleur, vides en gris. */
    static String bar(double ratio, int cells, String color) {
        int full = (int) Math.round(Math.max(0, Math.min(1, ratio)) * cells);
        return "<" + color + ">" + "▰".repeat(full) + "</" + color + "><#3A4250>" + "▱".repeat(cells - full) + "</#3A4250>";
    }

    static String time(int ticks) {
        int s = Math.max(0, ticks / 20);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }
}
