package fr.naruto.core.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/** Marqueur des inventaires NarutoCore + table slot -> identifiant (element/clan/jutsu). */
public final class NarutoHolder implements InventoryHolder {

    public enum Type { ELEMENT, CLAN, JUTSU }

    private final Type type;
    private final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;

    public NarutoHolder(Type type) { this.type = type; }

    public Type type() { return type; }
    public Map<Integer, String> actions() { return actions; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
