package games.orbit.orbitstaff.common.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Profil de permissions d'un joueur, expose via une integration (ex: LuckPerms).
 *
 * @param playerUuid       joueur concerne
 * @param primaryGroup     groupe principal
 * @param secondaryGroups  groupes secondaires
 * @param prefix           prefixe d'affichage (peut etre vide)
 * @param suffix           suffixe d'affichage (peut etre vide)
 * @param effectiveNodes   permissions effectives (noeud -&gt; valeur booleenne)
 */
public record PlayerPermissionProfile(
        UUID playerUuid,
        String primaryGroup,
        List<String> secondaryGroups,
        String prefix,
        String suffix,
        Map<String, Boolean> effectiveNodes) {

    public PlayerPermissionProfile {
        secondaryGroups = secondaryGroups == null ? List.of() : List.copyOf(secondaryGroups);
        effectiveNodes = effectiveNodes == null ? Map.of() : Map.copyOf(effectiveNodes);
    }
}
