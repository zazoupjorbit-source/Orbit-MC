package fr.orbitversus;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Pont économie **optionnel** vers Vault, branché par réflexion (aucune dépendance de build).
 * Si Vault + un plugin d'économie sont présents, les paris et récompenses en argent fonctionnent ;
 * sinon le Mode Versus reste jouable en Elo seul.
 */
public final class EconomyHook {

    private Object economy;
    private Method has, withdraw, deposit;
    private boolean ready;

    public EconomyHook(Logger logger) {
        try {
            Class<?> econClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(econClass);
            if (rsp == null) {
                logger.info("Vault/économie absente : paris et gains en argent désactivés (Elo seul).");
                return;
            }
            this.economy = rsp.getProvider();
            this.has = econClass.getMethod("has", OfflinePlayer.class, double.class);
            this.withdraw = econClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            this.deposit = econClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            this.ready = true;
            logger.info("Économie branchée (Vault) : paris et gains en argent actifs.");
        } catch (Throwable t) {
            logger.info("Économie non branchée (" + t.getClass().getSimpleName() + ") : Elo seul.");
        }
    }

    public boolean available() {
        return ready;
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (!ready) return false;
        try {
            return (boolean) has.invoke(economy, player, amount);
        } catch (Throwable t) {
            return false;
        }
    }

    public void withdraw(OfflinePlayer player, double amount) {
        if (!ready || amount <= 0) return;
        try {
            withdraw.invoke(economy, player, amount);
        } catch (Throwable ignored) {
        }
    }

    public void deposit(OfflinePlayer player, double amount) {
        if (!ready || amount <= 0) return;
        try {
            deposit.invoke(economy, player, amount);
        } catch (Throwable ignored) {
        }
    }
}
