package fr.orbit.royale;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Lien optionnel vers OrbitCasino (softdepend) : verse les Orbit Coins gagnés au Siège dans le portefeuille du
 * réseau. Les classes d'OrbitCasino ne sont touchées que si le plugin est chargé.
 */
final class CasinoBridge {

    private CasinoBridge() {
    }

    static boolean available() {
        Plugin p = Bukkit.getPluginManager().getPlugin("OrbitCasino");
        return p != null && p.isEnabled();
    }

    static boolean credit(UUID uuid, long amount, String detail) {
        if (amount <= 0 || !available()) {
            return false;
        }
        try {
            Impl.credit(uuid, amount, detail);
            return true;
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[OrbitRoyale] Versement d'Orbit Coins impossible : " + t);
            return false;
        }
    }

    private static final class Impl {
        static void credit(UUID uuid, long amount, String detail) {
            fr.orbit.casino.OrbitCasino casino = (fr.orbit.casino.OrbitCasino) Bukkit.getPluginManager().getPlugin("OrbitCasino");
            casino.wallet().credit(uuid, amount, "siege", detail, false);
        }
    }
}
