package fr.orbitgames.hcf.api;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Set;

/**
 * Plugin porteur de l'API OrbitHCF.
 * Ne contient aucune logique de gameplay : il expose les interfaces, modeles et evenements partages
 * entre les modules, et tient la frontiere des mondes HCF ({@link HcfWorlds}) quand le serveur
 * accueille aussi un autre mode de jeu.
 */
public final class OrbitHCFApiPlugin extends JavaPlugin implements Listener {

    /** Commandes d'administration utilisables hors du monde HCF par un admin. */
    private static final Set<String> ADMIN_ANYWHERE = Set.of("koth", "citadel", "conquest", "sotw", "eotw", "lives", "revive");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        HcfWorlds.configure(getConfig().getStringList("mondes-hcf"));
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("OrbitHCF-API " + getDescription().getVersion() + " chargee. Mondes HCF : "
                + (HcfWorlds.isScoped() ? String.join(", ", HcfWorlds.names()) : "tous") + ".");
    }

    @Override
    public void onDisable() {
        OrbitHCF.clear();
        HcfWorlds.reset();
        getLogger().info("OrbitHCF-API dechargee, registre de services vide.");
    }

    /** Passage d'un mode a l'autre : entrer en HCF vaut une connexion, en sortir une deconnexion. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        boolean was = HcfWorlds.isHcf(event.getFrom());
        boolean is = HcfWorlds.isHcf(event.getPlayer());
        if (was && !is) {
            HcfWorlds.fireLeave(event.getPlayer());
        } else if (!was && is) {
            HcfWorlds.fireEnter(event.getPlayer());
            checkAllowed(event.getPlayer());
        }
    }

    /** Un joueur deathban qui se reconnecte directement dans le monde HCF en est sorti. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (HcfWorlds.isScoped() && HcfWorlds.isHcf(event.getPlayer())) {
            checkAllowed(event.getPlayer());
        }
    }

    private void checkAllowed(Player player) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline() || !HcfWorlds.isHcf(player)) {
                return;
            }
            String reason = HcfWorlds.denyReason(player.getUniqueId());
            if (reason != null) {
                HcfWorlds.eject(player, reason);
            }
        }, 2L);
    }

    /**
     * Pas de sortie du HCF en combat : sinon la sanction de combat log (objets laches) tomberait
     * dans le monde d'arrivee. Seules les sorties de {@link HcfWorlds#eject} passent.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        if (!HcfWorlds.isScoped() || event.getTo() == null
                || !HcfWorlds.isHcf(event.getFrom().getWorld()) || HcfWorlds.isHcf(event.getTo().getWorld())) {
            return;
        }
        String reason = HcfWorlds.exitDenyReason(event.getPlayer());
        if (reason != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + reason);
        }
    }

    /** Les commandes HCF (/f, /pvp, /class...) n'existent que dans le monde HCF. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!HcfWorlds.isScoped() || HcfWorlds.isHcf(event.getPlayer())) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        PluginCommand command = Bukkit.getPluginCommand(label);
        if (command == null || !command.getPlugin().getName().startsWith("OrbitHCF")) {
            return;
        }
        if (ADMIN_ANYWHERE.contains(command.getName()) && event.getPlayer().hasPermission("orbithcf.admin")) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "Cette commande n'existe que dans le mode HCF. "
                + ChatColor.GRAY + "/hcf pour y entrer.");
    }
}
