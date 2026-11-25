package fr.orbit.royale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.Locale;

/** Messages du Siège Orbital (MiniMessage) aux couleurs ORBIT : bleu #3A7FFF, or #E0B864, glace #62CFF4. */
public final class Txt {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String TITLE = "<gradient:#3A7FFF:#62CFF4:#E0B864><b>SIÈGE ORBITAL</b></gradient>";
    public static final String PREFIX = "<gradient:#3A7FFF:#E0B864><b>SIÈGE</b></gradient> <#5A6270>»</#5A6270> <#E6EDF3>";
    private static final NumberFormat NF = NumberFormat.getIntegerInstance(Locale.FRANCE);

    private Txt() {
    }

    public static Component mm(String s) {
        return MM.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String n(long value) {
        return NF.format(value).replace(' ', ' ').replace(' ', ' ');
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

    public static Component button(String label, String color, String command, String hover) {
        return mm("<" + color + "><b>[" + label + "]</b></" + color + ">")
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(mm(hover)));
    }

    /** Boutons de fin de partie : [Rejouer] [Classement] [Deck]. */
    public static Component endButtons() {
        return mm("  ").append(button("Rejouer", "#3A7FFF", "/siege jouer", "Une nouvelle bataille"))
                .append(mm(" ")).append(button("Classement", "#E0B864", "/siege top", "Les 10 meilleurs commandants"))
                .append(mm(" ")).append(button("Deck", "#62CFF4", "/siege deck", "Modifier ton deck de 8 cartes"));
    }
}
