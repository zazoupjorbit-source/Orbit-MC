package fr.orbit.casino.game;

import org.bukkit.entity.Player;

import java.util.UUID;

/** Une table où l'on s'assoit (blackjack, poker) : reçoit les actions de /table. */
public interface Seated {

    boolean has(UUID player);

    void action(Player p, String action, String arg);

    /** Le joueur quitte la table (commande, trop loin, déconnexion). */
    void leave(Player p, String reason);

    /** Toutes les secondes. */
    void tick();

    /** Arrêt du serveur : rend ce qui doit l'être, sans attendre. */
    void shutdown();
}
