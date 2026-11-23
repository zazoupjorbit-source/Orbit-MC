package fr.orbit.devcore.chat;

import fr.orbit.devcore.OrbitDevCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Annonces automatiques dans le chat (section « annonces » de config.yml) : chaque annonce a son propre
 * intervalle en minutes et ses lignes MiniMessage (liens cliquables possibles, ex. vers /vote ou /boutique).
 * Envoyées seulement aux joueurs de CE serveur, et seulement s'il y a quelqu'un de connecté.
 */
public final class Annonces {

    private final OrbitDevCore plugin;
    private final List<BukkitTask> taches = new ArrayList<>();

    public Annonces(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    /** (Re)lance les annonces selon la config actuelle. */
    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("annonces.enabled", true)) return;
        MiniMessage mm = MiniMessage.miniMessage();
        for (Map<?, ?> entree : plugin.getConfig().getMapList("annonces.messages")) {
            int minutes = entier(entree.get("interval-minutes"));
            if (minutes <= 0 || !(entree.get("lines") instanceof List<?> brutes) || brutes.isEmpty()) {
                plugin.getLogger().warning("Annonce ignorée (interval-minutes > 0 et lines obligatoires) : " + entree);
                continue;
            }
            List<Component> lignes = new ArrayList<>();
            for (Object ligne : brutes) lignes.add(mm.deserialize(String.valueOf(ligne)));
            long ticks = minutes * 60L * 20L;
            taches.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> envoyer(lignes), ticks, ticks));
        }
    }

    public void stop() {
        taches.forEach(BukkitTask::cancel);
        taches.clear();
    }

    private void envoyer(List<Component> lignes) {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        Bukkit.getOnlinePlayers().forEach(p -> lignes.forEach(p::sendMessage));
    }

    private static int entier(Object valeur) {
        if (valeur instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(valeur).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
