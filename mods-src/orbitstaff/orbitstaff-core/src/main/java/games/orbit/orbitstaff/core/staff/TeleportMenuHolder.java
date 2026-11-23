package games.orbit.orbitstaff.core.staff;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Marqueur du menu de teleportation staff : associe chaque slot a un joueur cible. */
public final class TeleportMenuHolder implements InventoryHolder {

    private final Map<Integer, UUID> targets = new HashMap<>();
    private Inventory inventory;

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    void mapSlot(int slot, UUID target) {
        targets.put(slot, target);
    }

    /** @return la cible du slot, ou null. */
    public UUID target(int slot) {
        return targets.get(slot);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
