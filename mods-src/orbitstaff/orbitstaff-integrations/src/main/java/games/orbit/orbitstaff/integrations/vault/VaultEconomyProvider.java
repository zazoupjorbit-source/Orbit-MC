package games.orbit.orbitstaff.integrations.vault;

import games.orbit.orbitstaff.common.enums.IntegrationAvailability;
import games.orbit.orbitstaff.common.spi.EconomyProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur Vault du port {@link EconomyProvider}. Recupere le fournisseur
 * d'economie actif via le ServicesManager de Bukkit (API {@code net.milkbowl.vault}).
 */
public final class VaultEconomyProvider implements EconomyProvider {

    private final Economy economy;

    public VaultEconomyProvider(Economy economy) {
        this.economy = economy;
    }

    /** @return l'adaptateur si Vault et un fournisseur d'economie sont presents. */
    public static Optional<VaultEconomyProvider> tryCreate(Server server) {
        if (server.getPluginManager().getPlugin("Vault") == null) {
            return Optional.empty();
        }
        RegisteredServiceProvider<Economy> registration =
                server.getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            return Optional.empty();
        }
        return Optional.of(new VaultEconomyProvider(registration.getProvider()));
    }

    @Override
    public IntegrationAvailability availability() {
        return IntegrationAvailability.AVAILABLE;
    }

    @Override
    public Optional<BigDecimal> getBalance(UUID playerUuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        return Optional.of(BigDecimal.valueOf(economy.getBalance(player)));
    }
}
