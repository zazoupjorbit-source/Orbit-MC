package fr.orbit.casino;

import fr.orbit.devcore.api.CosmeticAccess;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;

/**
 * Branche la boutique sur les cosmétiques d'OrbitDevCore : un cosmétique est débloqué s'il a été acheté en
 * Orbit Coins (ou donné par permission). Classe à part : chargée seulement si OrbitDevCore est présent.
 */
final class CasinoAccess implements CosmeticAccess {

    private final Shop shop;

    private CasinoAccess(Shop shop) {
        this.shop = shop;
    }

    @Override
    public boolean owns(Player player, String type, String id) {
        return shop.owns(player, type, id);
    }

    static void register(OrbitCasino plugin, Shop shop) {
        Bukkit.getServicesManager().register(CosmeticAccess.class, new CasinoAccess(shop), plugin, ServicePriority.Normal);
        plugin.getLogger().info("Cosmétiques d'OrbitDevCore branchés sur la boutique en Orbit Coins.");
    }
}
