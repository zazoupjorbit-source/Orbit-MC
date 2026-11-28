package fr.orbitgames.hcf.api.service;

import java.util.UUID;

/**
 * Argent du HCF : un solde par joueur et un solde par faction (meme registre, cle = UUID).
 * Implemente par OrbitHCF-Core.
 */
public interface EconomyService {

    /** Solde d'un joueur ou d'une faction (0 si inconnu). */
    long getBalance(UUID id);

    void setBalance(UUID id, long amount);

    void deposit(UUID id, long amount);

    /** Retire le montant s'il est disponible. Faux (et rien n'est retire) sinon. */
    boolean withdraw(UUID id, long amount);

    /** Montant ecrit avec le symbole de la monnaie, par exemple « $1 250 ». */
    String format(long amount);
}
