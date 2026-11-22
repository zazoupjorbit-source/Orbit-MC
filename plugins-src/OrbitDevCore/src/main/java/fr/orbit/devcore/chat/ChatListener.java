package fr.orbit.devcore.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Chat et messages de connexion/déconnexion aux couleurs des rôles.
 * Les formats viennent de la section « chat » du config.yml (MiniMessage, balises {@code <prefix>} et {@code <name>}).
 */
public final class ChatListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PrefixProvider prefixes;

    /** Formats lus sur le thread principal au chargement, puis lus par le thread du chat (async). */
    private volatile Formats formats = Formats.DEFAULTS;

    public ChatListener(PrefixProvider prefixes) {
        this.prefixes = prefixes;
    }

    /** (Re)charge les formats depuis la config (thread principal). */
    public void load(ConfigurationSection section) {
        formats = section == null ? Formats.DEFAULTS : new Formats(
                section.getBoolean("enabled", true),
                section.getString("format", Formats.DEFAULTS.format()),
                section.getString("join", Formats.DEFAULTS.join()),
                section.getString("quit", Formats.DEFAULTS.quit()),
                parseColor(section.getString("message-color"), Formats.DEFAULTS.messageColor()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Formats f = formats;
        if (!f.enabled()) {
            return;
        }
        Component head = render(f.format(), event.getPlayer());
        TextColor color = f.messageColor();
        event.renderer((source, displayName, message, viewer) -> head.append(message.colorIfAbsent(color)));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Formats f = formats;
        if (f.enabled() && !f.join().isEmpty()) {
            event.joinMessage(render(f.join(), event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Formats f = formats;
        if (f.enabled() && !f.quit().isEmpty()) {
            event.quitMessage(render(f.quit(), event.getPlayer()));
        }
    }

    /**
     * Le préfixe (défini par le staff dans LuckPerms) est inséré tel quel pour que sa couleur finale
     * colore aussi le pseudo ; le pseudo, lui, est inséré sans interprétation :
     * un joueur ne peut pas injecter de formatage via son nom.
     */
    private Component render(String template, Player player) {
        String withPrefix = template.replace("<prefix>", prefixes.prefix(player));
        return MM.deserialize(withPrefix, Placeholder.unparsed("name", player.getName()));
    }

    private static TextColor parseColor(String raw, TextColor fallback) {
        if (raw == null) {
            return fallback;
        }
        TextColor c = TextColor.fromHexString(raw.trim());
        return c == null ? fallback : c;
    }

    private record Formats(boolean enabled, String format, String join, String quit, TextColor messageColor) {
        static final Formats DEFAULTS = new Formats(true,
                "<prefix><name> <#5A6270>»</#5A6270> ",
                "<#3A7FFF><b>+</b></#3A7FFF> <prefix><name>",
                "<#5A6270><b>-</b></#5A6270> <prefix><name>",
                TextColor.fromHexString("#E6EDF3"));
    }
}
