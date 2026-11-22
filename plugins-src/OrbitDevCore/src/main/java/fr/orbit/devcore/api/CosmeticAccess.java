package fr.orbit.devcore.api;

import org.bukkit.entity.Player;

/**
 * Point d'extension pour débloquer les cosmétiques (boutique ORBIT à venir).
 * <p>
 * Un autre plugin l'enregistre dans le ServicesManager de Bukkit :
 * <pre>
 * Bukkit.getServicesManager().register(CosmeticAccess.class, impl, plugin, ServicePriority.Normal);
 * </pre>
 * Dès qu'une implémentation est enregistrée, OrbitDevCore lui demande qui possède quoi, au lieu
 * de son réglage {@code cosmetics.unlock-all}. Appelé sur le thread principal : la réponse doit
 * venir d'un cache (pas d'accès base de données ici).
 */
public interface CosmeticAccess {

    /**
     * @param type {@code trail}, {@code aura}, {@code hat} ou {@code pet}
     * @param id   identifiant du cosmétique dans cosmetics.yml
     */
    boolean owns(Player player, String type, String id);
}
