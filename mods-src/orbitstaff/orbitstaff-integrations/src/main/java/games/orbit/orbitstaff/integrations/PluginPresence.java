package games.orbit.orbitstaff.integrations;

/**
 * Detecte la presence d'un plugin. Abstrait pour rendre la selection des
 * integrations testable sans serveur Bukkit.
 */
@FunctionalInterface
public interface PluginPresence {

    boolean isPresent(String pluginName);
}
