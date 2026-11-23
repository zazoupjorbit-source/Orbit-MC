package fr.orbitgames.hcf.api.service;

import fr.orbitgames.hcf.api.model.Faction;
import fr.orbitgames.hcf.api.model.FactionType;
import fr.orbitgames.hcf.api.model.Relation;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

/**
 * Acces public aux factions. Implemente par OrbitHCF-Core.
 */
public interface FactionService {

    /** Faction par identifiant, ou null. */
    Faction getFaction(UUID id);

    /** Faction par nom (insensible a la casse), ou null. */
    Faction getFactionByName(String name);

    /** Faction d'un joueur, ou null s'il n'en a pas. */
    Faction getFactionByPlayer(UUID playerId);

    /** Toutes les factions (joueurs et systeme). */
    Collection<Faction> getFactions();

    /** Factions de joueurs uniquement. */
    Collection<Faction> getPlayerFactions();

    /** Faction systeme Wilderness. */
    Faction getWilderness();

    /** Premiere faction systeme du type donne, ou null. */
    Faction getSystemFaction(FactionType type);

    // ------------------------------------------------------------------
    // Relations
    // ------------------------------------------------------------------

    Relation getRelation(Faction a, Faction b);

    Relation getRelation(Player a, Player b);

    default boolean isFriendly(Player a, Player b) {
        return getRelation(a, b).isFriendly();
    }

    // ------------------------------------------------------------------
    // Mutations autorisees aux autres modules
    // ------------------------------------------------------------------

    /** Ajoute des points de faction (evenements HCF). */
    void addPoints(Faction faction, int points);

    /** Definit la faction focus. {@code target} peut etre null pour retirer le focus. */
    void setFocus(Faction faction, UUID target);

    /**
     * Cree (ou recupere si elle existe deja) une faction systeme, par exemple
     * une faction d'evenement pour un KOTH.
     */
    Faction createSystemFaction(String name, FactionType type);

    /** Supprime une faction systeme creee par un module (jamais une faction joueur). */
    void removeSystemFaction(Faction faction);
}
