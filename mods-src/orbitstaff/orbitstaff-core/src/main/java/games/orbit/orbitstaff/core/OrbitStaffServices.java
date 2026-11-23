package games.orbit.orbitstaff.core;

import games.orbit.orbitstaff.core.api.ApiExecutor;
import games.orbit.orbitstaff.core.api.OrbitStaffApiClient;
import games.orbit.orbitstaff.core.config.OrbitStaffConfig;
import games.orbit.orbitstaff.core.freeze.FreezeManager;
import games.orbit.orbitstaff.core.i18n.Messages;
import games.orbit.orbitstaff.core.inspect.InspectionService;
import games.orbit.orbitstaff.core.staff.StaffModeManager;
import games.orbit.orbitstaff.integrations.IntegrationManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Regroupe les dependances partagees injectees dans les commandes et listeners.
 */
public record OrbitStaffServices(
        JavaPlugin plugin,
        OrbitStaffConfig config,
        Messages messages,
        OrbitStaffApiClient apiClient,
        ApiExecutor apiExecutor,
        StaffModeManager staffMode,
        FreezeManager freeze,
        InspectionService inspection,
        IntegrationManager integrations) {
}
