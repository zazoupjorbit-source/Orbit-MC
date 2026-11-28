package fr.orbit.bowl;

import fr.orbit.casino.OrbitCasino;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Orbit Coins par le portefeuille d'OrbitCasino (classe à part : rien de chargé si OrbitCasino est absent).
 * Les OC ne s'achètent jamais : ils se gagnent en jouant (plafond quotidien, Scores) ou se misent entre joueurs
 * dans les défis (le gagnant prend tout, aucune commission).
 */
final class Caisse {

    private Caisse() {
    }

    static boolean dispo() {
        Plugin p = Bukkit.getPluginManager().getPlugin("OrbitCasino");
        return p != null && p.isEnabled();
    }

    static void crediter(UUID uuid, long oc, String jeu, String detail, boolean gain) {
        if (oc <= 0) {
            return;
        }
        if (!dispo()) {
            Bukkit.getLogger().warning("[OrbitBowl] OrbitCasino absent : " + oc + " OC non versés à " + uuid + " (" + jeu + ").");
            return;
        }
        ((OrbitCasino) Bukkit.getPluginManager().getPlugin("OrbitCasino")).wallet().credit(uuid, oc, jeu, detail, gain);
    }

    /** Retire la mise si le solde suffit ; {@code fait(true)} sur le fil du serveur si c'est fait. */
    static void debiter(Player p, long oc, String jeu, String detail, Consumer<Boolean> fait) {
        if (!dispo()) {
            fait.accept(false);
            return;
        }
        ((OrbitCasino) Bukkit.getPluginManager().getPlugin("OrbitCasino")).wallet().debit(p, oc, jeu, detail, fait);
    }

    static long solde(Player p) {
        if (!dispo()) {
            return 0;
        }
        return ((OrbitCasino) Bukkit.getPluginManager().getPlugin("OrbitCasino")).wallet().balance(p);
    }
}
