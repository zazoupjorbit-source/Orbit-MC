package games.orbit.orbitstaff.integrations.luckperms;

import games.orbit.orbitstaff.common.enums.IntegrationAvailability;
import games.orbit.orbitstaff.common.model.PlayerPermissionProfile;
import games.orbit.orbitstaff.common.spi.PermissionProvider;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur LuckPerms du port {@link PermissionProvider} (API officielle
 * {@code net.luckperms.api}).
 *
 * <p>Ne lit que des donnees deja en cache ({@code getUser}), sans blocage : le
 * profil n'est disponible que pour un joueur charge (typiquement en ligne).</p>
 */
public final class LuckPermsPermissionProvider implements PermissionProvider {

    private final LuckPerms luckPerms;

    public LuckPermsPermissionProvider(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    /** @return l'adaptateur si l'API LuckPerms est chargee, vide sinon. */
    public static Optional<LuckPermsPermissionProvider> tryCreate() {
        try {
            return Optional.of(new LuckPermsPermissionProvider(LuckPermsProvider.get()));
        } catch (IllegalStateException notLoaded) {
            return Optional.empty();
        }
    }

    @Override
    public IntegrationAvailability availability() {
        return IntegrationAvailability.AVAILABLE;
    }

    @Override
    public Optional<PlayerPermissionProfile> getProfile(UUID playerUuid) {
        User user = luckPerms.getUserManager().getUser(playerUuid);
        if (user == null) {
            return Optional.empty();
        }

        String primaryGroup = user.getPrimaryGroup();
        List<String> secondaryGroups = user.getNodes(NodeType.INHERITANCE).stream()
                .map(InheritanceNode::getGroupName)
                .filter(group -> !group.equalsIgnoreCase(primaryGroup))
                .distinct()
                .toList();

        CachedMetaData meta = user.getCachedData().getMetaData();
        String prefix = meta.getPrefix() == null ? "" : meta.getPrefix();
        String suffix = meta.getSuffix() == null ? "" : meta.getSuffix();

        Map<String, Boolean> effectiveNodes = new LinkedHashMap<>();
        for (PermissionNode node : user.getNodes(NodeType.PERMISSION)) {
            effectiveNodes.put(node.getPermission(), node.getValue());
        }

        return Optional.of(new PlayerPermissionProfile(
                playerUuid, primaryGroup, secondaryGroups, prefix, suffix, effectiveNodes));
    }
}
