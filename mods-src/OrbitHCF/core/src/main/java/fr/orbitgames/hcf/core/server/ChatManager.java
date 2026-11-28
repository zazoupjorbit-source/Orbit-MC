package fr.orbitgames.hcf.core.server;

import fr.orbitgames.hcf.api.model.Faction;
import fr.orbitgames.hcf.api.util.Text;
import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import fr.orbitgames.hcf.core.faction.FactionImpl;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Canaux de chat du HCF : public, faction, allies. /f chat pour changer.
 * Un message qui commence par « ! » part toujours en public, par « @ » toujours a la faction.
 */
public class ChatManager implements Listener {

    public enum Channel {
        PUBLIC("public"), FACTION("faction"), ALLY("allies");

        public final String label;

        Channel(String label) {
            this.label = label;
        }
    }

    private final OrbitHCFCorePlugin plugin;
    private final Map<UUID, Channel> channels = new ConcurrentHashMap<>();

    public ChatManager(OrbitHCFCorePlugin plugin) {
        this.plugin = plugin;
    }

    public Channel get(UUID playerId) {
        return channels.getOrDefault(playerId, Channel.PUBLIC);
    }

    public void set(UUID playerId, Channel channel) {
        if (channel == Channel.PUBLIC) {
            channels.remove(playerId);
        } else {
            channels.put(playerId, channel);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        channels.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        Channel channel = get(player.getUniqueId());
        if (text.startsWith("!") && text.length() > 1) {
            if (channel != Channel.PUBLIC) {
                event.message(net.kyori.adventure.text.Component.text(text.substring(1).trim()));
            }
            return;
        }
        if (text.startsWith("@") && text.length() > 1) {
            channel = Channel.FACTION;
            text = text.substring(1).trim();
        }
        if (channel == Channel.PUBLIC) {
            return;
        }
        FactionImpl faction = plugin.getFactionManager().getImplByPlayer(player.getUniqueId());
        if (faction == null) {
            channels.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
        send(player, faction, channel, text);
    }

    /** Envoie un message sur le canal de faction ou d'allies. */
    public void send(Player player, FactionImpl faction, Channel channel, String text) {
        Set<Player> targets = new LinkedHashSet<>(faction.getOnlinePlayers());
        String line;
        if (channel == Channel.ALLY) {
            for (UUID allyId : faction.getAllies()) {
                Faction ally = plugin.getFactionManager().getFaction(allyId);
                if (ally != null) {
                    targets.addAll(ally.getOnlinePlayers());
                }
            }
            line = Text.color("&9(Allies) &b" + faction.getName() + " &3" + player.getName() + "&7: &f") + text;
        } else {
            line = Text.color("&2(Faction) &a" + player.getName() + "&7: &f") + text;
        }
        targets.add(player);
        for (Player target : targets) {
            target.sendMessage(line);
        }
        plugin.getLogger().info("[" + channel.label + " " + faction.getName() + "] " + player.getName() + ": " + text);
    }
}
