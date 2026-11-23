package fr.naruto.core.jutsu;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Cles PersistentData partagees. */
public final class Keys {
    public static NamespacedKey JUTSU;

    private Keys() {}

    public static void init(Plugin plugin) {
        JUTSU = new NamespacedKey(plugin, "jutsu");
    }
}
