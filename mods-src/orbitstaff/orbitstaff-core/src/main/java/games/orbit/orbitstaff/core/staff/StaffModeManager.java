package games.orbit.orbitstaff.core.staff;

import games.orbit.orbitstaff.core.i18n.Messages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gere le mode staff : activation/desactivation, vanish, vol, invincibilite,
 * hotbar, sauvegarde/restauration de l'inventaire et de la position, et
 * restauration de l'etat apres reconnexion/redemarrage.
 *
 * <p>Toutes les operations Bukkit sont executees sur le thread principal.</p>
 */
public final class StaffModeManager {

    private final Plugin plugin;
    private final Messages messages;
    private final StaffStateStore store;
    private final HotbarLayout layout;
    private final Set<UUID> activeStaff = ConcurrentHashMap.newKeySet();

    public StaffModeManager(Plugin plugin, Messages messages, StaffStateStore store, HotbarLayout layout) {
        this.plugin = plugin;
        this.messages = messages;
        this.store = store;
        this.layout = layout;
    }

    public boolean isStaff(UUID uuid) {
        return activeStaff.contains(uuid);
    }

    /** @return copie des UUID des membres du staff actuellement en mode staff. */
    public Set<UUID> activeStaff() {
        return Set.copyOf(activeStaff);
    }

    public void toggle(Player player) {
        if (isStaff(player.getUniqueId())) {
            exit(player);
        } else {
            enter(player);
        }
    }

    public void enter(Player player) {
        UUID id = player.getUniqueId();
        Location loc = player.getLocation();
        StaffModeState state = new StaffModeState(
                true,
                true,
                player.getGameMode().name(),
                InventorySerializer.toBase64(player.getInventory().getContents()),
                loc.getWorld() == null ? null : loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        store.save(id, state);
        activeStaff.add(id);
        applyStaffVisuals(player);
        giveHotbar(player);
        messages.send(player, "staff-mode.enabled");
    }

    public void exit(Player player) {
        UUID id = player.getUniqueId();
        Optional<StaffModeState> saved = store.load(id);

        player.setInvulnerable(false);
        player.setFlying(false);
        player.setAllowFlight(false);
        setVanished(player, false);
        player.getInventory().clear();

        saved.ifPresent(state -> {
            try {
                player.setGameMode(GameMode.valueOf(state.previousGameMode()));
            } catch (IllegalArgumentException ignored) {
                player.setGameMode(GameMode.SURVIVAL);
            }
            player.getInventory().setContents(InventorySerializer.fromBase64(state.inventoryData()));
            if (state.world() != null) {
                World world = Bukkit.getWorld(state.world());
                if (world != null) {
                    player.teleport(new Location(world, state.x(), state.y(), state.z(), state.yaw(), state.pitch()));
                }
            }
        });

        store.clear(id);
        activeStaff.remove(id);
        messages.send(player, "staff-mode.disabled");
    }

    /** Restaure l'etat staff a la reconnexion si le joueur etait en mode staff. */
    public void restoreOnJoin(Player player) {
        store.load(player.getUniqueId()).ifPresent(state -> {
            if (state.active()) {
                activeStaff.add(player.getUniqueId());
                applyStaffVisuals(player);
                giveHotbar(player);
                messages.send(player, "staff-mode.state-restored");
            }
        });
    }

    /** A l'arret du plugin, ramene les membres du staff en ligne a leur etat normal. */
    public void restoreAllOnDisable() {
        for (UUID id : new HashSet<>(activeStaff)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                exit(player);
            }
        }
    }

    private void applyStaffVisuals(Player player) {
        player.getInventory().clear();
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
        setVanished(player, true);
    }

    private void setVanished(Player player, boolean vanished) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }
            if (vanished) {
                other.hidePlayer(plugin, player);
            } else {
                other.showPlayer(plugin, player);
            }
        }
    }

    private void giveHotbar(Player player) {
        for (HotbarItem item : layout.items()) {
            Material material = Material.matchMaterial(item.material());
            if (material == null) {
                continue;
            }
            ItemStack stack = new ItemStack(material);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.displayName(messages.get(item.nameKey()));
                // Lie l'action a l'objet pour que le clic la declenche (StaffToolListener).
                StaffTools.tag(meta, plugin, item.action());
                stack.setItemMeta(meta);
            }
            player.getInventory().setItem(item.slot(), stack);
        }
    }
}
