package games.orbit.orbitstaff.core.staff;

import java.util.List;
import java.util.Optional;

/**
 * Disposition de la hotbar staff. Logique pure et testable ; le rendu des
 * ItemStack est realise par la couche Bukkit a partir de cette description.
 */
public final class HotbarLayout {

    private final List<HotbarItem> items;

    public HotbarLayout(List<HotbarItem> items) {
        this.items = List.copyOf(items);
    }

    public List<HotbarItem> items() {
        return items;
    }

    /** @return l'action associee a un slot, si un objet y est defini. */
    public Optional<StaffAction> actionForSlot(int slot) {
        return items.stream().filter(i -> i.slot() == slot).map(HotbarItem::action).findFirst();
    }

    /**
     * Disposition par defaut (section 8 du cahier des charges). Configurable via
     * staff-items.yml : ce layout sert de valeur par defaut sure.
     */
    public static HotbarLayout defaultLayout() {
        return new HotbarLayout(List.of(
                new HotbarItem(0, "COMPASS", StaffAction.TELEPORT_PLAYER, "hotbar.teleport"),
                new HotbarItem(1, "BOOK", StaffAction.PLAYER_INFO, "hotbar.player-info"),
                new HotbarItem(2, "ICE", StaffAction.FREEZE, "hotbar.freeze"),
                new HotbarItem(3, "CHEST", StaffAction.INSPECT_INVENTORY, "hotbar.inventory"),
                new HotbarItem(4, "ENDER_CHEST", StaffAction.INSPECT_ENDERCHEST, "hotbar.enderchest"),
                new HotbarItem(5, "PAPER", StaffAction.DISCIPLINARY_FILE, "hotbar.file"),
                new HotbarItem(6, "IRON_AXE", StaffAction.SANCTION, "hotbar.sanction"),
                new HotbarItem(7, "PLAYER_HEAD", StaffAction.ONLINE_STAFF, "hotbar.online-staff"),
                new HotbarItem(8, "LIME_DYE", StaffAction.EXIT_STAFF_MODE, "hotbar.exit")));
    }
}
