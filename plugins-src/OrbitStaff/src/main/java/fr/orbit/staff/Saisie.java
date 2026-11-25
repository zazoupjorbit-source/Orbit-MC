package fr.orbit.staff;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Saisie dans le chat (motif, note, recherche) et chat staff basculé (/sc sans texte).
 * Passe avant le mute et avant le chat normal.
 */
public final class Saisie implements Listener {

    private record Attente(Consumer<String> suite, long expire) {
    }

    private final OrbitStaff plugin;
    private final Map<UUID, Attente> attentes = new ConcurrentHashMap<>();
    private final Set<UUID> chatStaff = ConcurrentHashMap.newKeySet();

    public Saisie(OrbitStaff plugin) {
        this.plugin = plugin;
    }

    public void demander(Player p, String question, Consumer<String> suite) {
        p.closeInventory();
        attentes.put(p.getUniqueId(), new Attente(suite, System.currentTimeMillis() + 60_000));
        Txt.send(p, question + " <dark_gray>(écris dans le chat, « annuler » pour annuler)");
    }

    public boolean basculerChatStaff(UUID id) {
        if (!chatStaff.remove(id)) {
            chatStaff.add(id);
            return true;
        }
        return false;
    }

    public void chatStaff(Player p, String message) {
        plugin.flux().poster(Flux.CHAT, null, "<#f5c542>" + Txt.esc(p.getName()) + " <dark_gray>» <white>" + Txt.esc(message));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Attente a = attentes.remove(p.getUniqueId());
        String texte = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        if (a != null && a.expire() > System.currentTimeMillis()) {
            e.setCancelled(true);
            if (texte.equalsIgnoreCase("annuler")) {
                Bukkit.getScheduler().runTask(plugin, () -> Txt.send(p, "Annulé."));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> a.suite().accept(texte));
            return;
        }
        if (chatStaff.contains(p.getUniqueId()) && p.hasPermission("orbitstaff.chat")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> chatStaff(p, texte));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        attentes.remove(e.getPlayer().getUniqueId());
    }
}
