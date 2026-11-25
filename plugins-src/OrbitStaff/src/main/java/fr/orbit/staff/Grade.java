package fr.orbit.staff;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Grade LuckPerms (isolé : si LuckPerms manque, la classe n'est jamais chargée). */
final class Grade {

    private Grade() {
    }

    static CompletableFuture<String> de(UUID id) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User u = lp.getUserManager().getUser(id);
            if (u != null) {
                return CompletableFuture.completedFuture(u.getPrimaryGroup());
            }
            return lp.getUserManager().loadUser(id).thenApply(User::getPrimaryGroup);
        } catch (Throwable t) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
