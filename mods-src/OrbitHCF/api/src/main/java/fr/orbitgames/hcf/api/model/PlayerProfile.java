package fr.orbitgames.hcf.api.model;

import java.util.UUID;

/**
 * Profil persistant d'un joueur.
 */
public interface PlayerProfile {

    UUID getUuid();

    String getLastKnownName();

    /** Faction du joueur, ou null. */
    UUID getFactionId();

    int getKills();

    int getDeaths();

    /** Premiere connexion (epoch millis). */
    long getFirstJoin();

    /** Derniere deconnexion (epoch millis), 0 si jamais vu hors ligne. */
    long getLastSeen();
}
