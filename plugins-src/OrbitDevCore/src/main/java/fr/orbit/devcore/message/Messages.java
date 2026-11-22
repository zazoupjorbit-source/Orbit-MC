package fr.orbit.devcore.message;

import fr.orbit.devcore.OrbitDevCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Messages FR/EN au format MiniMessage, chargés depuis plugins/OrbitDevCore/lang/. */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final String[] BUNDLED = {"fr", "en"};

    private final OrbitDevCore plugin;
    private YamlConfiguration lang = new YamlConfiguration();
    private YamlConfiguration fallback = new YamlConfiguration();
    private TagResolver prefix = TagResolver.empty();

    public Messages(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        for (String code : BUNDLED) {
            if (!new File(plugin.getDataFolder(), path(code)).exists()) {
                plugin.saveResource(path(code), false);
            }
        }
        String code = plugin.getConfig().getString("language", "fr").toLowerCase(Locale.ROOT);
        File file = new File(plugin.getDataFolder(), path(code));
        if (!file.exists()) {
            plugin.getLogger().warning("Langue inconnue « " + code + " », utilisation du français.");
            code = "fr";
            file = new File(plugin.getDataFolder(), path(code));
        }
        lang = YamlConfiguration.loadConfiguration(file);
        fallback = bundled(code);
        prefix = Placeholder.parsed("prefix", raw("prefix"));
    }

    private static String path(String code) {
        return "lang/messages_" + code + ".yml";
    }

    private YamlConfiguration bundled(String code) {
        InputStream in = plugin.getResource(path(code));
        if (in == null) {
            in = plugin.getResource(path("fr"));
        }
        if (in == null) {
            return new YamlConfiguration();
        }
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException e) {
            return new YamlConfiguration();
        }
    }

    /** Texte brut d'un message (sans interprétation MiniMessage). */
    public String raw(String key) {
        String value = lang.getString(key);
        if (value == null) {
            value = fallback.getString(key);
        }
        return value != null ? value : "<red>[message manquant : " + key + "]";
    }

    private List<String> rawList(String key) {
        List<String> value = lang.getStringList(key);
        if (value.isEmpty()) {
            value = fallback.getStringList(key);
        }
        return value;
    }

    public Component get(String key, TagResolver... placeholders) {
        return MINI.deserialize(raw(key), TagResolver.resolver(prefix, TagResolver.resolver(placeholders)));
    }

    /** Version pour noms et lore d'objets : sans l'italique ajouté par défaut. */
    public Component item(String key, TagResolver... placeholders) {
        return get(key, placeholders).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public List<Component> itemLore(String key, TagResolver... placeholders) {
        TagResolver resolver = TagResolver.resolver(prefix, TagResolver.resolver(placeholders));
        List<Component> lines = new ArrayList<>();
        for (String line : rawList(key)) {
            lines.add(MINI.deserialize(line, resolver).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return lines;
    }

    public void send(CommandSender sender, String key, TagResolver... placeholders) {
        sender.sendMessage(get(key, placeholders));
    }

    public void sendList(CommandSender sender, String key) {
        for (String line : rawList(key)) {
            sender.sendMessage(MINI.deserialize(line, prefix));
        }
    }

    /** Placeholder texte brut (le contenu n'est pas interprété comme MiniMessage). */
    public static TagResolver ph(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    /** Placeholder contenant un composant déjà formaté (ex. préfixe de grade). */
    public static TagResolver comp(String name, Component value) {
        return Placeholder.component(name, value);
    }

    /** Interprète du MiniMessage fourni par la config ou un admin (noms de PNJ). */
    public static Component mini(String text) {
        return MINI.deserialize(text);
    }
}
