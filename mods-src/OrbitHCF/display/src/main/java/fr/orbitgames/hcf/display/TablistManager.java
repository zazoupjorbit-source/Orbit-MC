package fr.orbitgames.hcf.display;

import fr.orbitgames.hcf.api.util.Text;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Tablist facultative : header et footer configures dans tablist.yml.
 */
public class TablistManager {

    private final OrbitHCFDisplayPlugin plugin;

    public TablistManager(OrbitHCFDisplayPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getTablistConfig().getBoolean("enabled", false);
    }

    public void tick() {
        if (!isEnabled()) {
            return;
        }
        List<String> header = plugin.getTablistConfig().getStringList("header");
        List<String> footer = plugin.getTablistConfig().getStringList("footer");
        for (Player player : fr.orbitgames.hcf.api.HcfWorlds.players()) {
            String headerText = Text.color(Text.replace(String.join("\n", header), "joueur", player.getName()));
            String footerText = Text.color(Text.replace(String.join("\n", footer), "joueur", player.getName()));
            player.sendPlayerListHeaderAndFooter(
                    LegacyComponentSerializer.legacySection().deserialize(headerText),
                    LegacyComponentSerializer.legacySection().deserialize(footerText));
        }
    }
}
