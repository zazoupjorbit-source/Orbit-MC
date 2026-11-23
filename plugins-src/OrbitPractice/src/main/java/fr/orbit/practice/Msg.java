package fr.orbit.practice;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** Mise en forme des messages (MiniMessage), couleurs Orbit MC : bleu + or. */
public final class Msg {

    public static final String BLEU = "<#4DA3FF>";
    public static final String OR = "<#F5C542>";
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = BLEU + "<bold>PRACTICE</bold> <dark_gray>» <gray>";

    private Msg() {}

    /** Message de chat avec le préfixe du mode. */
    public static Component msg(String mini) {
        return MM.deserialize(PREFIX + mini);
    }

    public static Component raw(String mini) {
        return MM.deserialize(mini);
    }

    /** Nom ou description d'objet : sans l'italique mis par défaut par le jeu. */
    public static Component item(String mini) {
        return MM.deserialize(mini).decoration(TextDecoration.ITALIC, false);
    }
}
