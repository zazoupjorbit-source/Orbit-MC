package fr.orbit.extraction.rank;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Lecture du rôle dans LuckPerms (préfixe, couleur du pseudo, poids). Classe isolée : elle n'est chargée que si
 * LuckPerms est présent, donc le plugin démarre aussi sans lui.
 */
final class LuckPermsHook {

    private final LuckPerms lp;

    private LuckPermsHook(LuckPerms lp) {
        this.lp = lp;
    }

    /** @return null si le service LuckPerms n'est pas enregistré. */
    static LuckPermsHook create(Plugin plugin, Consumer<UUID> onChange) {
        LuckPerms api = plugin.getServer().getServicesManager().load(LuckPerms.class);
        if (api == null) {
            return null;
        }
        // Un rang donné ou retiré (site, console, /lp) se voit tout de suite dans le tab.
        api.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, e -> {
            UUID id = e.getUser().getUniqueId();
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> onChange.accept(id));
            }
        });
        return new LuckPermsHook(api);
    }

    /**
     * Préfixe du rôle au format MiniMessage, coloré comme dans le chat du réseau : si le préfixe n'a pas de couleur
     * à lui, il prend la métadonnée « namecolor » du rôle, qui colore aussi le pseudo placé derrière.
     *
     * @return null si le joueur n'a pas de préfixe
     */
    String prefix(Player p) {
        try {
            CachedMetaData meta = lp.getPlayerAdapter(Player.class).getMetaData(p);
            String prefix = meta.getPrefix();
            if (prefix == null || prefix.isEmpty()) {
                return null;
            }
            String color = meta.getMetaValue("namecolor");
            if (!prefix.contains("<") && color != null && color.matches("#[0-9A-Fa-f]{6}")) {
                return "<" + color + ">" + prefix;
            }
            return prefix;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Poids du rôle principal (les rôles du staff passent devant dans le tab). */
    int weight(Player p) {
        try {
            User u = lp.getPlayerAdapter(Player.class).getUser(p);
            Group g = lp.getGroupManager().getGroup(u.getPrimaryGroup());
            return g == null ? 0 : g.getWeight().orElse(0);
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
