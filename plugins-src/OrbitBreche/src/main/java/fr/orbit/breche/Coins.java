package fr.orbit.breche;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/** Versement des Orbit Coins par le portefeuille d'OrbitCasino (dépendance souple : sans lui, rien n'est versé). */
final class Coins {

    private Coins() {
    }

    static boolean credit(Plugin self, UUID uuid, long amount, String detail) {
        try {
            Plugin pl = Bukkit.getPluginManager().getPlugin("OrbitCasino");
            if (pl == null || !pl.isEnabled()) {
                return false;
            }
            return Bridge.credit(pl, uuid, amount, detail);
        } catch (Throwable t) {
            self.getLogger().warning("Orbit Coins non versés : " + t);
            return false;
        }
    }

    /** Classe à part : OrbitCasino n'est chargé que si le plugin est là. */
    private static final class Bridge {
        static boolean credit(Plugin pl, UUID uuid, long amount, String detail) {
            if (pl instanceof fr.orbit.casino.OrbitCasino casino && casino.wallet() != null) {
                casino.wallet().credit(uuid, amount, "breche", detail.length() > 60 ? detail.substring(0, 60) : detail, false);
                return true;
            }
            return false;
        }
    }
}
