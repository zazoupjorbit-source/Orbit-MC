package fr.orbitgames.hcf.api.util;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Lecteur de messages.yml partage par tous les modules (regle 10).
 * Placeholders au format {cle}.
 */
public class Messages {

    private final FileConfiguration config;
    private final String prefix;

    public Messages(FileConfiguration config) {
        this.config = config;
        this.prefix = config.getString("prefix", "");
    }

    /** Message brut (non colore), ou la cle elle-meme si absente. */
    public String raw(String key) {
        return config.getString(key, key);
    }

    /** Message colore avec placeholders, sans prefixe. */
    public String format(String key, Object... pairs) {
        return Text.color(Text.replace(raw(key), pairs));
    }

    /** Message colore avec placeholders, precede du prefixe. */
    public String formatPrefixed(String key, Object... pairs) {
        return Text.color(prefix + Text.replace(raw(key), pairs));
    }

    public void send(CommandSender target, String key, Object... pairs) {
        if (target != null) {
            target.sendMessage(formatPrefixed(key, pairs));
        }
    }

    public void sendRaw(CommandSender target, String key, Object... pairs) {
        if (target != null) {
            target.sendMessage(format(key, pairs));
        }
    }

    /** Envoie une liste de lignes (pour les aides de commandes). */
    public void sendList(CommandSender target, String key, Object... pairs) {
        List<String> lines = config.getStringList(key);
        if (lines.isEmpty()) {
            send(target, key, pairs);
            return;
        }
        for (String line : lines) {
            target.sendMessage(Text.color(Text.replace(line, pairs)));
        }
    }

    public void broadcast(String key, Object... pairs) {
        Bukkit.broadcastMessage(formatPrefixed(key, pairs));
    }

    public void broadcastRaw(String key, Object... pairs) {
        Bukkit.broadcastMessage(format(key, pairs));
    }

    public String getPrefix() {
        return Text.color(prefix);
    }
}
