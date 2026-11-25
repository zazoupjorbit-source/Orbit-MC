package fr.orbit.devcore.chat;

import fr.orbit.devcore.OrbitDevCore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Trident;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Clear lag (section « clear-lag » de config.yml) : toutes les N minutes, retire les objets au sol,
 * l'expérience et les flèches plantées, avec des avertissements dans le chat avant le nettoyage.
 * Épargnés : objets nommés, impossibles à ramasser, sans fin de vie ou marqués par un plugin (vitrines, hologrammes…),
 * et les tridents (objet du joueur).
 */
public final class ClearLag {

    private final OrbitDevCore plugin;
    private final List<BukkitTask> taches = new ArrayList<>();

    public ClearLag(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    /** (Re)lance le cycle selon la config actuelle. */
    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("clear-lag.enabled", true)) return;
        int minutes = plugin.getConfig().getInt("clear-lag.interval-minutes", 10);
        if (minutes <= 0) return;
        long cycle = minutes * 60L * 20L;
        // Un avertissement par délai (en secondes avant le nettoyage), dans le même cycle.
        for (int secondes : plugin.getConfig().getIntegerList("clear-lag.warnings-seconds")) {
            long avant = secondes * 20L;
            if (secondes <= 0 || avant >= cycle) continue;
            taches.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> avertir(secondes), cycle - avant, cycle));
        }
        taches.add(Bukkit.getScheduler().runTaskTimer(plugin, this::nettoyer, cycle, cycle));
    }

    public void stop() {
        taches.forEach(BukkitTask::cancel);
        taches.clear();
    }

    private void avertir(int secondes) {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        String format = plugin.getConfig().getString("clear-lag.warning-message", "");
        if (format.isEmpty()) return;
        var message = MiniMessage.miniMessage().deserialize(format, Placeholder.unparsed("seconds", String.valueOf(secondes)));
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(message));
    }

    /** Nettoie tous les mondes et renvoie le nombre d'entités retirées. */
    public int nettoyer() {
        boolean objets = plugin.getConfig().getBoolean("clear-lag.items", true);
        boolean xp = plugin.getConfig().getBoolean("clear-lag.xp-orbs", true);
        boolean fleches = plugin.getConfig().getBoolean("clear-lag.arrows", true);
        int total = 0;
        for (World monde : Bukkit.getWorlds()) {
            for (Entity e : monde.getEntities()) {
                boolean retirer = switch (e) {
                    case Item item -> objets && itemOrdinaire(item);
                    case ExperienceOrb ignored -> xp;
                    case Trident ignored -> false;
                    case AbstractArrow fleche -> fleches && fleche.isInBlock();
                    default -> false;
                };
                if (retirer) {
                    e.remove();
                    total++;
                }
            }
        }
        String format = plugin.getConfig().getString("clear-lag.done-message", "");
        if (!format.isEmpty() && !Bukkit.getOnlinePlayers().isEmpty()) {
            var message = MiniMessage.miniMessage().deserialize(format, Placeholder.unparsed("count", String.valueOf(total)));
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(message));
        }
        plugin.getLogger().info("Clear lag : " + total + " entité(s) retirée(s).");
        return total;
    }

    /** Vrai objet lâché par un joueur, un mob ou un bloc — pas un objet de décor posé par un plugin. */
    private static boolean itemOrdinaire(Item item) {
        return item.customName() == null
                && item.canPlayerPickup()
                && !item.isUnlimitedLifetime()
                && !item.isInvulnerable()
                && item.getPersistentDataContainer().isEmpty();
    }
}
