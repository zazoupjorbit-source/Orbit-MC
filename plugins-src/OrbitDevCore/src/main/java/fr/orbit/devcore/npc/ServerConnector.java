package fr.orbit.devcore.npc;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.Teleports;
import fr.orbit.devcore.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Envoie un joueur vers un autre serveur du proxy via le canal « BungeeCord »
 * (sous-commande Connect), compris par BungeeCord et par Velocity.
 */
public final class ServerConnector {

    public static final String CHANNEL = "BungeeCord";

    private final OrbitDevCore plugin;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public ServerConnector(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    public boolean proxyEnabled() {
        return plugin.getConfig().getBoolean("proxy.enabled", false);
    }

    /** Clic sur un PNJ : téléportation vers son monde, ou envoi vers son serveur du proxy. */
    public void use(Player player, ServerNpc npc) {
        if (!passCooldown(player)) {
            return;
        }
        if (npc.world() != null) {
            World world = Bukkit.getWorld(npc.world());
            if (world == null) {
                plugin.messages().send(player, "npc.world-missing", Messages.ph("world", npc.world()));
                return;
            }
            Teleports.teleport(plugin, player, plugin.worlds().spawnOf(world), "npc.world-teleported",
                    Messages.ph("world", world.getName()));
            return;
        }
        connect(player, npc.server());
    }

    /** Envoi direct vers un serveur du proxy (menu de la boussole). */
    public void send(Player player, String server) {
        if (passCooldown(player)) {
            connect(player, server);
        }
    }

    /** Nom de ce serveur dans velocity.toml (config proxy.server-name). */
    public String currentServer() {
        return plugin.getConfig().getString("proxy.server-name", "");
    }

    private boolean passCooldown(Player player) {
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("npc.click-cooldown-ms", 1000L);
        Long last = lastClick.get(player.getUniqueId());
        if (last != null && now - last < cooldown) {
            return false;
        }
        lastClick.put(player.getUniqueId(), now);
        return true;
    }

    private void connect(Player player, String server) {
        if (server.equalsIgnoreCase(currentServer())) {
            plugin.messages().send(player, "menu.already-here", Messages.ph("server", server));
            return;
        }
        if (!proxyEnabled()) {
            plugin.messages().send(player, "npc.no-proxy", Messages.ph("server", server));
            return;
        }
        plugin.messages().send(player, "npc.connecting", Messages.ph("server", server));
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de préparer l'envoi de " + player.getName() + " vers " + server + " : " + e.getMessage());
        }
    }

    public void forget(UUID player) {
        lastClick.remove(player);
    }
}
