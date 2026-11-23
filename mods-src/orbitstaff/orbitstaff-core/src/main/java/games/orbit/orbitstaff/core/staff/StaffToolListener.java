package games.orbit.orbitstaff.core.staff;

import games.orbit.orbitstaff.core.OrbitStaffServices;
import games.orbit.orbitstaff.core.api.dto.SanctionDto;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Rend les outils de la hotbar staff fonctionnels : lit l'action portee par
 * l'objet (PersistentDataContainer) et la declenche.
 *
 * <ul>
 *   <li>Clic-droit sur un joueur : freeze, inspection inventaire/ender chest, dossier.</li>
 *   <li>Clic-droit dans le vide : menu de teleportation, staff en ligne, quitter.</li>
 * </ul>
 */
public final class StaffToolListener implements Listener {

    private static final String FREEZE_REASON = "Verification staff";

    private final OrbitStaffServices services;

    public StaffToolListener(OrbitStaffServices services) {
        this.services = services;
    }

    /** Outil utilise sur un joueur cible. */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player staff = event.getPlayer();
        if (!services.staffMode().isStaff(staff.getUniqueId())) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }
        StaffAction action = StaffTools.read(staff.getInventory().getItemInMainHand(), services.plugin());
        if (action == null) {
            return;
        }
        event.setCancelled(true);

        switch (action) {
            case FREEZE -> toggleFreeze(staff, target);
            case INSPECT_INVENTORY -> services.inspection().openInventory(staff, target);
            case INSPECT_ENDERCHEST -> services.inspection().openEnderChest(staff, target);
            case PLAYER_INFO, DISCIPLINARY_FILE -> showDossier(staff, target);
            case SANCTION -> {
                staff.sendMessage(Component.text(
                        "Sanctionnez " + target.getName() + " via /warn /mute /ban ...", NamedTextColor.YELLOW));
                showDossier(staff, target);
            }
            default -> { /* les autres actions se declenchent dans le vide */ }
        }
    }

    /** Outil utilise dans le vide / sur un bloc. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player staff = event.getPlayer();
        if (!services.staffMode().isStaff(staff.getUniqueId())) {
            return;
        }
        StaffAction action = StaffTools.read(event.getItem(), services.plugin());
        if (action == null) {
            return;
        }
        event.setCancelled(true);

        switch (action) {
            case EXIT_STAFF_MODE -> services.staffMode().exit(staff);
            case TELEPORT_PLAYER -> openTeleportMenu(staff);
            case ONLINE_STAFF -> showOnlineStaff(staff);
            case FREEZE, INSPECT_INVENTORY, INSPECT_ENDERCHEST, PLAYER_INFO, DISCIPLINARY_FILE, SANCTION ->
                    staff.sendMessage(Component.text("Visez un joueur puis clic-droit.", NamedTextColor.GRAY));
            default -> { /* rien */ }
        }
    }

    /** Clic dans le menu de teleportation. */
    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TeleportMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player staff)) {
            return;
        }
        UUID targetId = holder.target(event.getRawSlot());
        if (targetId == null) {
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            staff.sendMessage(Component.text("Ce joueur n'est plus en ligne.", NamedTextColor.RED));
            return;
        }
        staff.closeInventory();
        staff.teleport(target.getLocation());
        staff.sendMessage(Component.text("Teleporte vers " + target.getName() + ".", NamedTextColor.GREEN));
    }

    private void toggleFreeze(Player staff, Player target) {
        if (services.freeze().isFrozen(target.getUniqueId())) {
            services.freeze().unfreeze(target, staff);
        } else {
            services.freeze().freeze(target, FREEZE_REASON, staff);
        }
    }

    private void showDossier(Player staff, Player target) {
        staff.sendMessage(Component.text("Dossier de " + target.getName(), NamedTextColor.GOLD));
        UUID uuid = target.getUniqueId();
        services.apiExecutor().<List<SanctionDto>>call(
                () -> services.apiClient().history(uuid),
                history -> staff.sendMessage(Component.text(
                        "  " + history.size() + " entree(s) de moderation", NamedTextColor.GRAY)),
                error -> staff.sendMessage(Component.text(
                        "  Dossier indisponible (API injoignable).", NamedTextColor.RED)));
    }

    private void openTeleportMenu(Player staff) {
        List<Player> online = Bukkit.getOnlinePlayers().stream()
                .filter(player -> !player.getUniqueId().equals(staff.getUniqueId()))
                .map(player -> (Player) player)
                .toList();
        if (online.isEmpty()) {
            staff.sendMessage(Component.text("Aucun autre joueur en ligne.", NamedTextColor.GRAY));
            return;
        }

        int size = Math.min(54, Math.max(9, ((online.size() + 8) / 9) * 9));
        TeleportMenuHolder holder = new TeleportMenuHolder();
        Inventory menu = Bukkit.createInventory(holder, size, Component.text("Teleportation"));
        holder.setInventory(menu);

        for (int i = 0; i < online.size() && i < size; i++) {
            Player player = online.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            if (meta instanceof SkullMeta skull) {
                skull.setOwningPlayer(player);
                skull.displayName(Component.text(player.getName(), NamedTextColor.AQUA));
                head.setItemMeta(skull);
            }
            menu.setItem(i, head);
            holder.mapSlot(i, player.getUniqueId());
        }
        staff.openInventory(menu);
    }

    private void showOnlineStaff(Player staff) {
        List<String> names = services.staffMode().activeStaff().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .map(Player::getName)
                .toList();
        String joined = names.isEmpty() ? "-" : String.join(", ", names);
        staff.sendMessage(Component.text("Staff en mode staff : " + joined, NamedTextColor.AQUA));
    }
}
