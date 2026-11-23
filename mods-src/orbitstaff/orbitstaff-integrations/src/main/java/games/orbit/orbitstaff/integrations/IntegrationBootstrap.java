package games.orbit.orbitstaff.integrations;

import games.orbit.orbitstaff.integrations.luckperms.LuckPermsPermissionProvider;
import games.orbit.orbitstaff.integrations.vault.VaultEconomyProvider;
import org.bukkit.Server;

/**
 * Cable les adaptateurs reels dans l'{@link IntegrationManager} lorsque le plugin
 * correspondant est present.
 *
 * <p>Ce point d'entree isole les types tiers (LuckPerms, Vault...) : le plugin
 * appelle uniquement {@link #wire}, sans jamais referencer ces API. Les classes
 * d'adaptateur ne sont chargees que lorsque l'integration est detectee, ce qui
 * garantit un demarrage sans erreur meme en leur absence.</p>
 */
public final class IntegrationBootstrap {

    private IntegrationBootstrap() {
    }

    /**
     * Detecte et enregistre les integrations disponibles.
     *
     * @param manager gestionnaire d'integrations a completer
     * @param server  serveur Bukkit courant
     */
    public static void wire(IntegrationManager manager, Server server) {
        if (manager.isPresent(IntegrationType.LUCKPERMS)) {
            LuckPermsPermissionProvider.tryCreate().ifPresent(manager::registerPermissionProvider);
        }
        if (manager.isPresent(IntegrationType.VAULT)) {
            VaultEconomyProvider.tryCreate(server).ifPresent(manager::registerEconomyProvider);
        }
        // EssentialsX (homes), CoreProtect (logs), PlaceholderAPI, WorldGuard :
        // detection en place, adaptateurs reels a livrer (restent en Unsupported).
    }
}
