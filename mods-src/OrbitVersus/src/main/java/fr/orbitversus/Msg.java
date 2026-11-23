package fr.orbitversus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** Identité visuelle du Mode Versus (orange/rouge combat) + rendu MiniMessage. */
public final class Msg {

    private Msg() {}

    public static final String C1 = "#E8650A";
    public static final String C2 = "#FF3B3B";
    public static final String GRAD = "<gradient:" + C1 + ":" + C2 + ">";
    public static final String PREFIX = GRAD + "⚔ VERSUS</gradient> <dark_gray>» </dark_gray>";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static Component raw(String mini) {
        return MM.deserialize(mini);
    }

    public static Component msg(String mini) {
        return MM.deserialize(PREFIX + mini);
    }
}
