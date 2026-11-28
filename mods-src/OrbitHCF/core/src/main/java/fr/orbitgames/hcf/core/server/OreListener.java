package fr.orbitgames.hcf.core.server;

import fr.orbitgames.hcf.api.util.Text;
import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayDeque;
import java.util.Deque;

/** Annonce [FD] : un joueur vient de trouver un filon de diamants (compte une seule fois par filon). */
public class OreListener implements Listener {

    private static final String SEEN = "orbithcf-fd";

    private final OrbitHCFCorePlugin plugin;

    public OreListener(OrbitHCFCorePlugin plugin) {
        this.plugin = plugin;
    }

    private static boolean isDiamond(Material type) {
        return type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isDiamond(event.getBlock().getType())) {
            event.getBlock().setMetadata(SEEN, new FixedMetadataValue(plugin, true));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block start = event.getBlock();
        if (!isDiamond(start.getType()) || start.hasMetadata(SEEN)
                || !plugin.getServerConfig().getBoolean("diamants.annonce", true)) {
            return;
        }
        int count = 0;
        Deque<Block> queue = new ArrayDeque<>();
        start.setMetadata(SEEN, new FixedMetadataValue(plugin, true));
        queue.add(start);
        while (!queue.isEmpty() && count < 64) {
            Block block = queue.poll();
            count++;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block near = block.getRelative(dx, dy, dz);
                        if (isDiamond(near.getType()) && !near.hasMetadata(SEEN)) {
                            near.setMetadata(SEEN, new FixedMetadataValue(plugin, true));
                            queue.add(near);
                        }
                    }
                }
            }
        }
        Bukkit.broadcastMessage(Text.color("&f[FD] &b" + event.getPlayer().getName() + " &fa trouve &b" + count
                + " &fdiamant" + (count > 1 ? "s" : "") + "."));
    }
}
