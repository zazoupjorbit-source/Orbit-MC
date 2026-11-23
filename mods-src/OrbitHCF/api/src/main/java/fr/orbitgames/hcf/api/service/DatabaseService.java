package fr.orbitgames.hcf.api.service;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Acces partage a la base de donnees d'OrbitHCF (SQLite par defaut,
 * MySQL/MariaDB en option). Implemente par OrbitHCF-Core.
 * <p>
 * La connexion est partagee : toute sequence de requetes doit etre
 * synchronisee sur l'instance de ce service.
 */
public interface DatabaseService {

    /** Connexion partagee, ouverte pour toute la duree de vie du serveur. */
    Connection getConnection() throws SQLException;

    boolean isMySql();

    /** Prefixe applique aux tables OrbitHCF. */
    String getTablePrefix();
}
