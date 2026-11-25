package fr.orbit.arene;

import fr.orbit.casino.OrbitCasino;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Versement des Orbit Coins par le portefeuille d'OrbitCasino (classe à part : rien de chargé si OrbitCasino
 * est absent). Les OC ne s'achètent jamais ; ils se gagnent en jouant, avec un plafond quotidien (Scores).
 */
final class Caisse {

    private Caisse() {
    }

    static boolean dispo() {
        Plugin p = Bukkit.getPluginManager().getPlugin("OrbitCasino");
        return p != null && p.isEnabled();
    }

    static void crediter(UUID uuid, int oc, String jeu, String detail) {
        if (!dispo()) {
            Bukkit.getLogger().warning("[OrbitArene] OrbitCasino absent : " + oc + " OC non versés à " + uuid + " (" + jeu + ").");
            return;
        }
        Plugin p = Bukkit.getPluginManager().getPlugin("OrbitCasino");
        ((OrbitCasino) p).wallet().credit(uuid, oc, jeu, detail, false);
    }
}
