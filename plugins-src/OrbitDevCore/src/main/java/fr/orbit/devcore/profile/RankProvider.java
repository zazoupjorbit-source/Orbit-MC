package fr.orbit.devcore.profile;

import org.bukkit.entity.Player;

/** Grade d'un joueur (groupe principal + préfixe), lu dans LuckPerms quand il est installé. */
@FunctionalInterface
public interface RankProvider {

    /**
     * @param group  nom affiché du groupe principal (ex. « Admin »)
     * @param prefix préfixe MiniMessage du groupe, ou chaîne vide
     */
    record Rank(String group, String prefix) {
    }

    Rank NONE = new Rank("Joueur", "");

    Rank rank(Player player);
}
