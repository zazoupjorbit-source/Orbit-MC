package games.orbit.orbitstaff.integrations;

import games.orbit.orbitstaff.common.enums.IntegrationAvailability;
import games.orbit.orbitstaff.common.model.PlayerHome;
import games.orbit.orbitstaff.common.spi.HomeProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("IntegrationManager")
class IntegrationManagerTest {

    /** Presence factice : seuls les plugins listes sont "presents". */
    private static PluginPresence presentOnly(String... names) {
        Set<String> set = Set.of(names);
        return set::contains;
    }

    @Test
    @DisplayName("detecte la presence par type")
    void detectsPresence() {
        IntegrationManager manager = new IntegrationManager(presentOnly("LuckPerms", "Vault"));
        assertTrue(manager.isPresent(IntegrationType.LUCKPERMS));
        assertTrue(manager.isPresent(IntegrationType.VAULT));
        assertFalse(manager.isPresent(IntegrationType.ESSENTIALSX));
    }

    @Test
    @DisplayName("le rapport reflete la disponibilite de chaque integration")
    void reportReflectsAvailability() {
        IntegrationManager manager = new IntegrationManager(presentOnly("LuckPerms"));
        var report = manager.report();
        assertEquals(IntegrationAvailability.AVAILABLE, report.get(IntegrationType.LUCKPERMS));
        assertEquals(IntegrationAvailability.ABSENT, report.get(IntegrationType.ESSENTIALSX));
        assertEquals(IntegrationType.values().length, report.size());
    }

    @Test
    @DisplayName("les providers par defaut sont Unsupported (absents, resultats vides)")
    void defaultsAreUnsupported() {
        IntegrationManager manager = new IntegrationManager(presentOnly());
        assertEquals(IntegrationAvailability.ABSENT, manager.homeProvider().availability());
        assertTrue(manager.homeProvider().getHomes(UUID.randomUUID()).isEmpty());
        assertEquals(IntegrationAvailability.ABSENT, manager.permissionProvider().availability());
        assertTrue(manager.permissionProvider().getProfile(UUID.randomUUID()).isEmpty());
        assertEquals(IntegrationAvailability.ABSENT, manager.economyProvider().availability());
        assertTrue(manager.economyProvider().getBalance(UUID.randomUUID()).isEmpty());
    }

    @Test
    @DisplayName("un adaptateur reel enregistre remplace le repli")
    void registeredProviderReplacesFallback() {
        IntegrationManager manager = new IntegrationManager(presentOnly("Essentials"));
        HomeProvider real = new HomeProvider() {
            @Override
            public IntegrationAvailability availability() {
                return IntegrationAvailability.AVAILABLE;
            }

            @Override
            public List<PlayerHome> getHomes(UUID playerUuid) {
                return List.of(new PlayerHome("base", "world", 0, 64, 0, 0, 0));
            }
        };
        manager.registerHomeProvider(real);
        assertSame(real, manager.homeProvider());
        assertEquals(IntegrationAvailability.AVAILABLE, manager.homeProvider().availability());
        assertEquals(1, manager.homeProvider().getHomes(UUID.randomUUID()).size());
    }
}
