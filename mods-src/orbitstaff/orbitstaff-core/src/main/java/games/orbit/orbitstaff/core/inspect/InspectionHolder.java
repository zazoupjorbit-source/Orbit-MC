package games.orbit.orbitstaff.core.inspect;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Marqueur d'un inventaire d'inspection (lecture seule). Permet a
 * {@code InspectionListener} d'annuler toute modification.
 */
public final class InspectionHolder implements InventoryHolder {

    private Inventory inventory;

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
