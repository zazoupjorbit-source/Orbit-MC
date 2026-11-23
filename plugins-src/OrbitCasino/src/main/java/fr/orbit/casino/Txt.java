package fr.orbit.casino;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.Locale;

/** Messages du casino (MiniMessage) aux couleurs ORBIT : or #E0B864, bleu #3A7FFF, glace #62CFF4. */
public final class Txt {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String GOLD = "<#E0B864>";
    public static final String PREFIX = "<#E0B864><b>CASINO</b></#E0B864> <#5A6270>»</#5A6270> <#E6EDF3>";
    private static final NumberFormat NF = NumberFormat.getIntegerInstance(Locale.FRANCE);

    private Txt() {
    }

    public static Component mm(String s) {
        return MM.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** 12 500 (espace insécable fine remplacée par une espace simple, lisible dans le chat). */
    public static String n(long value) {
        return NF.format(value).replace(' ', ' ').replace(' ', ' ');
    }

    /** « 1 250 OC » en or. */
    public static String oc(long value) {
        return GOLD + n(value) + " OC</#E0B864>";
    }

    public static void send(CommandSender to, String message) {
        to.sendMessage(mm(PREFIX + message));
    }

    public static void bar(Player p, String message) {
        p.sendActionBar(mm(message));
    }

    /** Bouton cliquable du chat qui lance une commande. */
    public static Component button(String label, String color, String command, String hover) {
        return mm("<" + color + "><b>[" + label + "]</b></" + color + ">")
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(mm(hover)));
    }

    public static void sound(Player p, Sound sound, float pitch) {
        p.playSound(p.getLocation(), sound, 0.8f, pitch);
    }
}
