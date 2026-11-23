package fr.orbit.survie;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import java.util.Locale;

/** Mise en forme des messages (MiniMessage), couleurs Orbit MC : bleu + or. */
public final class Msg {

    public static final String BLEU = "<#4DA3FF>";
    public static final String OR = "<#F5C542>";
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = BLEU + "<bold>SURVIE</bold> <dark_gray>» <gray>";
    private static String monnaie = "crédits";

    private Msg() {}

    static void monnaie(String nom) {
        monnaie = nom;
    }

    public static Component msg(String mini) {
        return MM.deserialize(PREFIX + mini);
    }

    public static void send(CommandSender to, String mini) {
        to.sendMessage(msg(mini));
    }

    public static Component raw(String mini) {
        return MM.deserialize(mini);
    }

    /** Nom ou description d'objet : sans l'italique mis par défaut par le jeu. */
    public static Component item(String mini) {
        return MM.deserialize(mini).decoration(TextDecoration.ITALIC, false);
    }

    /** Nom d'un objet, traduit par le jeu du joueur. */
    public static String nom(Material m) {
        return "<lang:" + m.translationKey() + ">";
    }

    /** Montant lisible : « 1 250 crédits », « 2,50 crédits ». */
    public static String argent(double montant) {
        return OR + nombre(montant) + " " + monnaie + "<gray>";
    }

    public static String nombre(double montant) {
        boolean entier = Math.abs(montant - Math.rint(montant)) < 0.005;
        return String.format(Locale.FRANCE, entier ? "%,.0f" : "%,.2f", montant).replace(' ', ' ').replace(' ', ' ');
    }
}
