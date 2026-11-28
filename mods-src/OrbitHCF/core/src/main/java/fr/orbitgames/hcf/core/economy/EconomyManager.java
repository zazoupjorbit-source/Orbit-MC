package fr.orbitgames.hcf.core.economy;

import fr.orbitgames.hcf.api.service.EconomyService;
import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import fr.orbitgames.hcf.core.storage.Database;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Soldes des joueurs et des factions. Tout est garde en memoire (charge au demarrage) ;
 * chaque changement est ecrit en base hors du thread principal.
 */
public class EconomyManager implements EconomyService {

    private final OrbitHCFCorePlugin plugin;
    private final Database database;
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();
    private final DecimalFormat format;
    private String symbol = "$";

    public EconomyManager(OrbitHCFCorePlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        this.format = new DecimalFormat("#,##0", symbols);
    }

    public void load() {
        symbol = plugin.getServerConfig().getString("economie.symbole", "$");
        String table = database.getTablePrefix() + "balances";
        database.update("CREATE TABLE IF NOT EXISTS " + table
                + " (id VARCHAR(36) PRIMARY KEY, amount BIGINT NOT NULL DEFAULT 0)");
        database.query("SELECT id, amount FROM " + table, rs -> {
            while (rs.next()) {
                try {
                    balances.put(UUID.fromString(rs.getString(1)), rs.getLong(2));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return null;
        });
        plugin.getLogger().info(balances.size() + " soldes charges.");
    }

    /** Vrai si ce joueur (ou cette faction) n'a encore jamais eu de solde. */
    public boolean isNew(UUID id) {
        return !balances.containsKey(id);
    }

    public void forget(UUID id) {
        if (balances.remove(id) != null) {
            database.updateAsync("DELETE FROM " + database.getTablePrefix() + "balances WHERE id = ?", id.toString());
        }
    }

    private void store(UUID id, long amount) {
        database.updateAsync("REPLACE INTO " + database.getTablePrefix() + "balances (id, amount) VALUES (?, ?)",
                id.toString(), amount);
    }

    @Override
    public long getBalance(UUID id) {
        return balances.getOrDefault(id, 0L);
    }

    @Override
    public void setBalance(UUID id, long amount) {
        long value = Math.max(0L, amount);
        balances.put(id, value);
        store(id, value);
    }

    @Override
    public void deposit(UUID id, long amount) {
        if (amount <= 0) {
            return;
        }
        long value = balances.merge(id, amount, Long::sum);
        store(id, value);
    }

    @Override
    public boolean withdraw(UUID id, long amount) {
        if (amount <= 0) {
            return amount == 0;
        }
        boolean[] done = {false};
        Long value = balances.computeIfPresent(id, (k, current) -> {
            if (current < amount) {
                return current;
            }
            done[0] = true;
            return current - amount;
        });
        if (done[0]) {
            store(id, value);
        }
        return done[0];
    }

    @Override
    public String format(long amount) {
        synchronized (format) {
            return symbol + format.format(amount);
        }
    }
}
