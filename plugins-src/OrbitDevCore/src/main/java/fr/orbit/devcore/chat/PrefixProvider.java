package fr.orbit.devcore.chat;

import org.bukkit.entity.Player;

/** Source du préfixe de rôle d'un joueur (MiniMessage). Appelé depuis le thread du chat (async) : doit être thread-safe. */
@FunctionalInterface
public interface PrefixProvider {

    /** Préfixe de repli : pseudo en gris clair, sans rôle. */
    String FALLBACK = "<#D7DCE2>";

    String prefix(Player player);
}
