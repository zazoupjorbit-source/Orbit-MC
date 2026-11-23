package fr.naruto.core.listener;

import fr.naruto.core.gui.Menus;
import fr.naruto.core.ninja.Ninja;
import fr.naruto.core.ninja.NinjaService;
import fr.naruto.core.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/** Charge/decharge le profil ninja et propose la selection a la 1ere connexion. */
public final class JoinListener implements Listener {

    private final NinjaService ninjas;
    private final Plugin plugin;

    public JoinListener(NinjaService ninjas, Plugin plugin) {
        this.ninjas = ninjas;
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ninjas.load(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Ninja n = ninjas.get(player.getUniqueId());
            if (n != null && !n.isComplete()) {
                player.sendMessage(Text.msg("<#FFB347>Bienvenue, jeune ninja ! Choisis ton affinite."));
                Menus.openElement(player);
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ninjas.unload(event.getPlayer());
    }
}
