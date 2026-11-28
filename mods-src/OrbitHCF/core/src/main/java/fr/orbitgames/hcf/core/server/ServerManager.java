package fr.orbitgames.hcf.core.server;

import fr.orbitgames.hcf.api.HcfWorlds;
import fr.orbitgames.hcf.api.util.Text;
import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.io.File;
import java.io.IOException;

/**
 * Ce qui fait du serveur un serveur HCF a lui seul : point de spawn, reapparition au spawn,
 * bordure de la carte, mode survie, argent de depart et prime de kill.
 */
public class ServerManager implements Listener {

    private final OrbitHCFCorePlugin plugin;

    public ServerManager(OrbitHCFCorePlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration config() {
        return plugin.getServerConfig();
    }

    // ------------------------------------------------------------------ Mondes

    /** Le monde principal du HCF : celui des claims et des zones (premier de la liste « worlds »). */
    public World mainWorld() {
        for (String name : plugin.getConfig().getStringList("worlds")) {
            World world = Bukkit.getWorld(name);
            if (world != null) {
                return world;
            }
        }
        return Bukkit.getWorlds().get(0);
    }

    // ------------------------------------------------------------------ Spawn

    public Location spawn() {
        World world = Bukkit.getWorld(config().getString("spawn.monde", ""));
        if (world == null) {
            Location s = mainWorld().getSpawnLocation();
            return new Location(s.getWorld(), s.getBlockX() + 0.5, s.getY(), s.getBlockZ() + 0.5, s.getYaw(), 0f);
        }
        return new Location(world, config().getDouble("spawn.x"), config().getDouble("spawn.y"),
                config().getDouble("spawn.z"), (float) config().getDouble("spawn.yaw"), 0f);
    }

    public void setSpawn(Location location) {
        config().set("spawn.monde", location.getWorld().getName());
        config().set("spawn.x", location.getBlockX() + 0.5);
        config().set("spawn.y", (double) location.getBlockY());
        config().set("spawn.z", location.getBlockZ() + 0.5);
        config().set("spawn.yaw", (double) Math.round(location.getYaw() / 90f) * 90f);
        location.getWorld().setSpawnLocation(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        save();
    }

    public void save() {
        try {
            config().save(new File(plugin.getDataFolder(), "serveur.yml"));
        } catch (IOException e) {
            plugin.getLogger().severe("serveur.yml non enregistre : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ Bordure

    /** Pose la bordure de chaque monde HCF. Renvoie un resume lisible. */
    public String applyBorders() {
        StringBuilder summary = new StringBuilder();
        for (World world : Bukkit.getWorlds()) {
            if (!HcfWorlds.isHcf(world)) {
                continue;
            }
            String key = switch (world.getEnvironment()) {
                case NETHER -> "nether";
                case THE_END -> "end";
                default -> "monde";
            };
            int radius = config().getInt("bordure." + key, 0);
            if (radius <= 0) {
                world.getWorldBorder().reset();
                summary.append(world.getName()).append(" : aucune ; ");
                continue;
            }
            world.getWorldBorder().setCenter(0.0, 0.0);
            world.getWorldBorder().setSize(radius * 2.0);
            world.getWorldBorder().setWarningDistance(16);
            summary.append(world.getName()).append(" : ").append(radius).append(" blocs ; ");
        }
        return summary.toString();
    }

    // ------------------------------------------------------------------ Joueurs

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getEconomy().isNew(player.getUniqueId())) {
            long start = config().getLong("economie.depart", 250L);
            plugin.getEconomy().setBalance(player.getUniqueId(), start);
            // Premiere venue sur le HCF : arrivee au spawn, quel que soit l'endroit ou le serveur l'a pose.
            player.teleport(spawn());
            player.sendMessage(Text.color(plugin.getMessages().getPrefix()
                    + "&eBienvenue en &6HCF&e. Tu pars avec &a" + plugin.getEconomy().format(start)
                    + "&e. Cree ou rejoins une faction : &f/f create <nom>&e. Aide : &f/f help&e."));
        }
        if (config().getBoolean("spawn.forcer-survie", true) && player.getGameMode() != GameMode.SURVIVAL
                && !fr.orbitgames.hcf.api.HcfWorlds.staffExempt(player, "orbithcf.admin")) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    /** Commandes des autres plugins du reseau qui n'ont pas leur place en HCF (/spawn, /warp, montures...). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        if (fr.orbitgames.hcf.api.HcfWorlds.staffExempt(event.getPlayer(), "orbithcf.admin")) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(java.util.Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (config().getStringList("commandes-interdites").contains(label)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Text.color(plugin.getMessages().getPrefix()
                    + "&cCette commande n'existe pas en HCF."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (config().getBoolean("spawn.reapparition-au-spawn", true)
                && event.getRespawnReason() == PlayerRespawnEvent.RespawnReason.DEATH) {
            event.setRespawnLocation(spawn());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        long reward = config().getLong("economie.prime-kill", 100L);
        if (killer == null || killer.equals(victim) || reward <= 0) {
            return;
        }
        plugin.getEconomy().deposit(killer.getUniqueId(), reward);
        killer.sendMessage(Text.color(plugin.getMessages().getPrefix() + "&a+" + plugin.getEconomy().format(reward)
                + " &7pour avoir tue &f" + victim.getName() + "&7."));
    }
}
