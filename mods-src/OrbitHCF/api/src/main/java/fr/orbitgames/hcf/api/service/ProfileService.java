package fr.orbitgames.hcf.api.service;

import fr.orbitgames.hcf.api.model.PlayerProfile;

import java.util.Collection;
import java.util.UUID;

/**
 * Acces public aux profils joueurs. Implemente par OrbitHCF-Core.
 */
public interface ProfileService {

    /** Profil charge du joueur, ou null s'il n'a jamais ete vu. */
    PlayerProfile getProfile(UUID playerId);

    /** Profils actuellement charges en memoire. */
    Collection<PlayerProfile> getLoadedProfiles();
}
