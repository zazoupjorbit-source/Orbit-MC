package fr.orbit.devcore.profile;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Grade lu dans LuckPerms. Classe isolée : chargée seulement si LuckPerms est présent,
 * donc OrbitDevCore démarre aussi sur les instances sans LuckPerms.
 */
public final class LuckPermsRankProvider implements RankProvider {

    private final LuckPerms luckPerms;

    private LuckPermsRankProvider(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    /** @return le fournisseur, ou null si le service LuckPerms n'est pas enregistré. */
    public static RankProvider create(Server server) {
        LuckPerms api = server.getServicesManager().load(LuckPerms.class);
        return api == null ? null : new LuckPermsRankProvider(api);
    }

    @Override
    public Rank rank(Player player) {
        try {
            User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
            String groupName = user.getPrimaryGroup();
            Group group = luckPerms.getGroupManager().getGroup(groupName);
            String display = group != null && group.getDisplayName() != null ? group.getDisplayName() : capitalize(groupName);
            String prefix = user.getCachedData().getMetaData().getPrefix();
            return new Rank(display, prefix == null ? "" : prefix);
        } catch (RuntimeException e) {
            return NONE;
        }
    }

    private static String capitalize(String value) {
        return value == null || value.isEmpty() ? "Joueur" : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
