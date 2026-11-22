package fr.orbit.devcore.chat;

import net.luckperms.api.LuckPerms;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Préfixe lu dans LuckPerms (lp group <rôle> meta setprefix ...).
 * Isolé dans sa propre classe : elle n'est chargée que si LuckPerms est présent,
 * donc OrbitDevCore démarre aussi sans LuckPerms.
 */
public final class LuckPermsPrefixProvider implements PrefixProvider {

    private final LuckPerms luckPerms;

    private LuckPermsPrefixProvider(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    /** @return le fournisseur, ou null si le service LuckPerms n'est pas enregistré. */
    public static PrefixProvider create(Server server) {
        LuckPerms api = server.getServicesManager().load(LuckPerms.class);
        return api == null ? null : new LuckPermsPrefixProvider(api);
    }

    @Override
    public String prefix(Player player) {
        try {
            // Cache de métadonnées de LuckPerms : thread-safe, aucune I/O.
            String prefix = luckPerms.getPlayerAdapter(Player.class).getMetaData(player).getPrefix();
            return prefix == null ? FALLBACK : prefix;
        } catch (RuntimeException e) {
            return FALLBACK;
        }
    }
}
