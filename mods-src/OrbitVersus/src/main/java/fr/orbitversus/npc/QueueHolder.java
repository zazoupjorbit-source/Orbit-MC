package fr.orbitversus.npc;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Marqueur d'inventaire du menu de matchmaking. */
public final class QueueHolder implements InventoryHolder {

    public static final int SLOT_1V1 = 11;
    public static final int SLOT_2V2 = 13;
    public static final int SLOT_LEAVE = 15;

    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
