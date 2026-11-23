package games.orbit.orbitstaff.integrations;

import games.orbit.orbitstaff.common.enums.IntegrationAvailability;
import games.orbit.orbitstaff.common.spi.BlockLogProvider;
import games.orbit.orbitstaff.common.spi.EconomyProvider;
import games.orbit.orbitstaff.common.spi.HomeProvider;
import games.orbit.orbitstaff.common.spi.PermissionProvider;
import games.orbit.orbitstaff.common.spi.PunishmentProvider;
import games.orbit.orbitstaff.integrations.unsupported.UnsupportedBlockLogProvider;
import games.orbit.orbitstaff.integrations.unsupported.UnsupportedEconomyProvider;
import games.orbit.orbitstaff.integrations.unsupported.UnsupportedHomeProvider;
import games.orbit.orbitstaff.integrations.unsupported.UnsupportedPermissionProvider;
import games.orbit.orbitstaff.integrations.unsupported.UnsupportedPunishmentProvider;

import java.util.EnumMap;
import java.util.Map;

/**
 * Point central des integrations. Chaque port demarre sur une implementation
 * "Unsupported" ; les adaptateurs reels sont enregistres au demarrage lorsque le
 * plugin correspondant est present. Ainsi OrbitStaff fonctionne toujours, meme
 * sans aucune integration.
 */
public final class IntegrationManager {

    private final PluginPresence presence;

    private HomeProvider homeProvider = new UnsupportedHomeProvider();
    private PermissionProvider permissionProvider = new UnsupportedPermissionProvider();
    private EconomyProvider economyProvider = new UnsupportedEconomyProvider();
    private BlockLogProvider blockLogProvider = new UnsupportedBlockLogProvider();
    private PunishmentProvider punishmentProvider = new UnsupportedPunishmentProvider();

    public IntegrationManager(PluginPresence presence) {
        this.presence = presence;
    }

    /** @return true si le plugin de l'integration est present et actif. */
    public boolean isPresent(IntegrationType type) {
        return presence.isPresent(type.pluginName());
    }

    /** @return la disponibilite de chaque integration connue. */
    public Map<IntegrationType, IntegrationAvailability> report() {
        Map<IntegrationType, IntegrationAvailability> map = new EnumMap<>(IntegrationType.class);
        for (IntegrationType type : IntegrationType.values()) {
            map.put(type, isPresent(type) ? IntegrationAvailability.AVAILABLE : IntegrationAvailability.ABSENT);
        }
        return map;
    }

    // --- Enregistrement des adaptateurs reels (appele par la couche de cablage) ---

    public void registerHomeProvider(HomeProvider provider) {
        this.homeProvider = provider;
    }

    public void registerPermissionProvider(PermissionProvider provider) {
        this.permissionProvider = provider;
    }

    public void registerEconomyProvider(EconomyProvider provider) {
        this.economyProvider = provider;
    }

    public void registerBlockLogProvider(BlockLogProvider provider) {
        this.blockLogProvider = provider;
    }

    public void registerPunishmentProvider(PunishmentProvider provider) {
        this.punishmentProvider = provider;
    }

    // --- Acces aux providers actifs ---

    public HomeProvider homeProvider() {
        return homeProvider;
    }

    public PermissionProvider permissionProvider() {
        return permissionProvider;
    }

    public EconomyProvider economyProvider() {
        return economyProvider;
    }

    public BlockLogProvider blockLogProvider() {
        return blockLogProvider;
    }

    public PunishmentProvider punishmentProvider() {
        return punishmentProvider;
    }
}
