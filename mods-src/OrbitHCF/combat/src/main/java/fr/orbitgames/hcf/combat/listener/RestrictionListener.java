package fr.orbitgames.hcf.combat.listener;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.service.ClaimService;
import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.combat.OrbitHCFCombatPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Restrictions en combat : commandes bloquees, entree en safezone interdite,
 * objets illegaux.
 */
public class RestrictionListener implements Listener {

    private final OrbitHCFCombatPlugin plugin;
    private final Set<Material> illegalItems;

    public RestrictionListener(OrbitHCFCombatPlugin plugin) {
        this.plugin = plugin;
        this.illegalItems = plugin.getCombatConfig().getStringList("illegal-items").stream()
                .map(name -> Material.matchMaterial(name))
                .filter(m -> m != null)
                .collect(Collectors.toSet());
    }

    // ------------------------------------------------------------------
    // Commandes en combat
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getCombatConfig().getBoolean("combat-tag.block-commands", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.getCombatManager().isCombatTagged(player.getUniqueId())) {
            return;
        }
        String command = event.getMessage().substring(1).toLowerCase(Locale.ROOT).trim();
        List<String> allowed = plugin.getCombatConfig().getStringList("combat-tag.allowed-commands");
        for (String prefix : allowed) {
            if (command.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return;
            }
        }
        event.setCancelled(true);
        plugin.getMessages().send(player, "tag.command-blocked", "temps",
                DurationFormat.compact(plugin.getCombatManager().getCombatTagRemaining(player.getUniqueId())));
    }

    // ------------------------------------------------------------------
    // Entree en safezone en combat
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getCombatConfig().getBoolean("combat-tag.deny-safezone-entry", true)) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.getCombatManager().isCombatTagged(player.getUniqueId())) {
            return;
        }
        ClaimService claims = OrbitHCF.claims();
        if (claims == null) {
            return;
        }
        if (claims.isSafezone(to) && !claims.isSafezone(from)) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "tag.safezone-blocked", "temps",
                    DurationFormat.compact(plugin.getCombatManager().getCombatTagRemaining(player.getUniqueId())));
        }
    }

    // ------------------------------------------------------------------
    // Objets illegaux
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (illegalItems.contains(event.getBlock().getType())
                && !event.getPlayer().hasPermission("orbithcf.combat.admin")) {
            event.setCancelled(true);
            plugin.getMessages().send(event.getPlayer(), "limits.illegal-item");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() == null) {
            return;
        }
        if (illegalItems.contains(event.getItem().getType())
                && !event.getPlayer().hasPermission("orbithcf.combat.admin")) {
            event.setCancelled(true);
            plugin.getMessages().send(event.getPlayer(), "limits.illegal-item");
        }
    }
}
