package fr.orbitgames.hcf.core.command;

import fr.orbitgames.hcf.api.util.Text;
import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import fr.orbitgames.hcf.core.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/** /balance [joueur], /pay <joueur> <montant>, /eco <give|take|set> <joueur> <montant>. */
public class MoneyCommand implements CommandExecutor {

    private final OrbitHCFCorePlugin plugin;
    private final EconomyManager economy;

    public MoneyCommand(OrbitHCFCorePlugin plugin) {
        this.plugin = plugin;
        this.economy = plugin.getEconomy();
    }

    private void say(CommandSender sender, String message) {
        sender.sendMessage(Text.color(plugin.getMessages().getPrefix() + message));
    }

    /** Joueur connecte, ou deja venu sur le serveur. */
    private UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null && !economy.isNew(cached.getUniqueId()) ? cached.getUniqueId() : null;
    }

    /** Montant entier positif, ou -1. */
    public static long parseAmount(String input) {
        try {
            long value = Long.parseLong(input.replace("$", "").replace(" ", ""));
            return value > 0 ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "balance" -> balance(sender, args);
            case "pay" -> pay(sender, args);
            case "eco" -> eco(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void balance(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                say(sender, "&eTon argent : &a" + economy.format(economy.getBalance(player.getUniqueId())));
            } else {
                say(sender, "&c/balance <joueur>");
            }
            return;
        }
        UUID target = resolve(args[0]);
        if (target == null) {
            say(sender, "&cJoueur introuvable : &f" + args[0]);
            return;
        }
        say(sender, "&eArgent de &f" + args[0] + "&e : &a" + economy.format(economy.getBalance(target)));
    }

    private void pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            say(sender, "&cEn jeu seulement.");
            return;
        }
        if (args.length < 2) {
            say(sender, "&c/pay <joueur> <montant>");
            return;
        }
        UUID target = resolve(args[0]);
        long amount = parseAmount(args[1]);
        if (target == null) {
            say(sender, "&cJoueur introuvable : &f" + args[0]);
            return;
        }
        if (target.equals(player.getUniqueId())) {
            say(sender, "&cTu ne peux pas te payer toi-meme.");
            return;
        }
        if (amount < 0) {
            say(sender, "&cMontant invalide : &f" + args[1]);
            return;
        }
        if (!economy.withdraw(player.getUniqueId(), amount)) {
            say(sender, "&cTu n'as que &f" + economy.format(economy.getBalance(player.getUniqueId())) + "&c.");
            return;
        }
        economy.deposit(target, amount);
        say(sender, "&aTu as envoye &f" + economy.format(amount) + "&a a &f" + args[0] + "&a.");
        Player receiver = Bukkit.getPlayer(target);
        if (receiver != null) {
            say(receiver, "&f" + player.getName() + " &at'a envoye &f" + economy.format(amount) + "&a.");
        }
    }

    private void eco(CommandSender sender, String[] args) {
        if (!sender.hasPermission("orbithcf.admin")) {
            say(sender, "&cVous n'avez pas la permission.");
            return;
        }
        if (args.length < 3) {
            say(sender, "&c/eco <give|take|set> <joueur> <montant>");
            return;
        }
        UUID target = resolve(args[1]);
        long amount = args[0].equalsIgnoreCase("set") && args[2].equals("0") ? 0 : parseAmount(args[2]);
        if (target == null) {
            say(sender, "&cJoueur introuvable : &f" + args[1]);
            return;
        }
        if (amount < 0) {
            say(sender, "&cMontant invalide : &f" + args[2]);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> economy.deposit(target, amount);
            case "take" -> economy.setBalance(target, economy.getBalance(target) - amount);
            case "set" -> economy.setBalance(target, amount);
            default -> {
                say(sender, "&c/eco <give|take|set> <joueur> <montant>");
                return;
            }
        }
        say(sender, "&aArgent de &f" + args[1] + "&a : &f" + economy.format(economy.getBalance(target)));
    }
}
