package fr.orbitgames.hcf.api;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin porteur de l'API OrbitHCF.
 * Ne contient aucune logique de gameplay : il expose uniquement les
 * interfaces, modeles et evenements partages entre les modules OrbitHCF.
 */
public final class OrbitHCFApiPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("OrbitHCF-API " + getDescription().getVersion() + " chargee.");
    }

    @Override
    public void onDisable() {
        OrbitHCF.clear();
        getLogger().info("OrbitHCF-API dechargee, registre de services vide.");
    }
}
