package games.orbit.orbitstaff.integrations;

import org.bukkit.Bukkit;

/** Detection de presence basee sur le gestionnaire de plugins Bukkit. */
public final class BukkitPluginPresence implements PluginPresence {

    @Override
    public boolean isPresent(String pluginName) {
        return Bukkit.getPluginManager().isPluginEnabled(pluginName);
    }
}
