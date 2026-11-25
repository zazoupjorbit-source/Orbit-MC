package fr.orbit.vol;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Messages de « Vole mon Orbito » (MiniMessage) aux couleurs ORBIT : or #E0B864, bleu #3A7FFF, glace #62CFF4. */
public final class Txt {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String OR = "<#E0B864>";
    public static final String PREFIX = "<#E0B864><b>ORBITO</b></#E0B864> <#5A6270>»</#5A6270> <#E6EDF3>";

    private Txt() {
    }

    public static Component mm(String s) {
        return MM.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Nombre court lisible par tous : 950, 12,5 k, 3,2 M, 1,4 Md. */
    public static String n(double v) {
        if (v < 1000) return String.valueOf((long) Math.floor(v));
        String[] suffixes = {" k", " M", " Md", " T"};
        int i = -1;
        while (v >= 1000 && i < suffixes.length - 1) {
            v /= 1000;
            i++;
        }
        String s = v >= 100 ? String.valueOf((long) v) : String.format(Locale.FRANCE, "%.1f", v).replace(",0", "");
        return s + suffixes[i];
    }

    /** « 1,2 k ✦ » en or. */
    public static String cr(double v) {
        return OR + n(v) + " ✦</#E0B864>";
    }

    public static void send(CommandSender to, String message) {
        to.sendMessage(mm(PREFIX + message));
    }

    public static void bar(Player p, String message) {
        p.sendActionBar(mm(message));
    }

    public static void sound(Player p, Sound sound, float pitch) {
        p.playSound(p.getLocation(), sound, 0.8f, pitch);
    }
}
