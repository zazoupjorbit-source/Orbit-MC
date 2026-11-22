package fr.orbit.devcore.storage;

import org.bukkit.Material;

/** Point de téléportation du menu de la boussole. */
public record Warp(String name, StoredLocation location, Material icon) {
}
