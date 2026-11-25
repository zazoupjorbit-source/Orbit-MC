package fr.orbit.tele;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cinq meilleurs scores par jeu de la console (un seul par joueur), gardés dans scores.yml. */
final class Scores {

    record Ligne(String nom, int score) {
    }

    private final OrbitTele plugin;
    private final File fichier;
    private final Map<String, List<Ligne>> tops = new HashMap<>();

    Scores(OrbitTele plugin) {
        this.plugin = plugin;
        this.fichier = new File(plugin.getDataFolder(), "scores.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        for (String jeu : y.getKeys(false)) {
            List<Ligne> l = new ArrayList<>();
            for (String s : y.getStringList(jeu)) {
                int i = s.lastIndexOf(' ');
                try {
                    l.add(new Ligne(s.substring(0, i), Integer.parseInt(s.substring(i + 1))));
                } catch (RuntimeException ignored) {
                    // ligne abîmée : on la saute
                }
            }
            tops.put(jeu, l);
        }
    }

    synchronized List<Ligne> top(String jeu) {
        return List.copyOf(tops.getOrDefault(jeu, List.of()));
    }

    synchronized int meilleur(String jeu) {
        List<Ligne> l = tops.get(jeu);
        return l == null || l.isEmpty() ? 0 : l.get(0).score();
    }

    /** @return la place obtenue (1 à 5), 0 si le score n'entre pas au classement. */
    synchronized int ajouter(String jeu, String nom, int score) {
        if (score <= 0) {
            return 0;
        }
        List<Ligne> l = tops.computeIfAbsent(jeu, k -> new ArrayList<>());
        for (Ligne a : l) {
            if (a.nom().equalsIgnoreCase(nom) && a.score() >= score) {
                return 0;
            }
        }
        l.removeIf(a -> a.nom().equalsIgnoreCase(nom));
        int place = 0;
        while (place < l.size() && l.get(place).score() >= score) {
            place++;
        }
        if (place >= 5) {
            return 0;
        }
        l.add(place, new Ligne(nom, score));
        while (l.size() > 5) {
            l.remove(5);
        }
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, this::enregistrer);
        }
        return place + 1;
    }

    private synchronized void enregistrer() {
        YamlConfiguration y = new YamlConfiguration();
        tops.forEach((jeu, l) -> y.set(jeu, l.stream().map(a -> a.nom() + " " + a.score()).toList()));
        try {
            y.save(fichier);
        } catch (Exception e) {
            plugin.getLogger().warning("scores.yml non enregistré : " + e.getMessage());
        }
    }
}
