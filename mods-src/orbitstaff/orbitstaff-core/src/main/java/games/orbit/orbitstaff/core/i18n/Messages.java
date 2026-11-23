package games.orbit.orbitstaff.core.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Fournit les messages localises (MiniMessage). Aucun message n'est ecrit en dur
 * dans le code : tout provient de messages_&lt;lang&gt;.yml.
 */
public final class Messages {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private FileConfiguration lang;
    private Component prefix;

    public Messages(File dataFolder, String language) {
        reload(dataFolder, language);
    }

    /** Recharge les messages depuis le disque (utilise par /orbitstaff reload). */
    public void reload(File dataFolder, String language) {
        File file = new File(dataFolder, "messages_" + language + ".yml");
        if (!file.exists()) {
            // repli sur le francais si la langue demandee est absente
            file = new File(dataFolder, "messages_fr.yml");
        }
        this.lang = YamlConfiguration.loadConfiguration(file);
        this.prefix = miniMessage.deserialize(lang.getString("prefix", ""));
    }

    /** @return le composant du message a la cle donnee, avec placeholders resolus. */
    public Component get(String path, TagResolver... resolvers) {
        String raw = lang.getString(path, path);
        return miniMessage.deserialize(raw, resolvers);
    }

    /** Envoie un message prefixe a un destinataire. */
    public void send(CommandSender sender, String path, TagResolver... resolvers) {
        sender.sendMessage(prefix.append(get(path, resolvers)));
    }

    /** Envoie un message sans prefixe (ecrans multi-lignes). */
    public void sendRaw(CommandSender sender, String path, TagResolver... resolvers) {
        sender.sendMessage(get(path, resolvers));
    }

    /** Raccourci pour construire un placeholder texte non interprete. */
    public static TagResolver placeholder(String key, String value) {
        return Placeholder.unparsed(key, value == null ? "" : value);
    }
}
