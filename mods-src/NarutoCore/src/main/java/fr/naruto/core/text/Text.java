package fr.naruto.core.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class Text {
    public static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<gradient:#E8650A:#FFB347><bold>NINJA</bold></gradient> <dark_gray>» ";

    private Text() {}

    public static Component msg(String s) { return MM.deserialize(PREFIX + s); }
    public static Component raw(String s) { return MM.deserialize(s); }
}
