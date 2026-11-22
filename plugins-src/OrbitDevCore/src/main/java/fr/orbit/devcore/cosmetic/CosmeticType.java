package fr.orbit.devcore.cosmetic;

import org.bukkit.Material;

import java.util.Locale;

/** Catégories de cosmétiques (section du cosmetics.yml, icône de catégorie). */
public enum CosmeticType {
    TRAIL("trails", Material.BLAZE_POWDER),
    AURA("auras", Material.AMETHYST_SHARD),
    HAT("hats", Material.LEATHER_HELMET),
    BACK("backs", Material.ELYTRA),
    BALLOON("balloons", Material.LEAD),
    PET("pets", Material.BONE),
    MOUNT("mounts", Material.SADDLE);

    private final String section;
    private final Material icon;

    CosmeticType(String section, Material icon) {
        this.section = section;
        this.icon = icon;
    }

    /** Identifiant court : trail, aura, hat, pet (fichiers joueurs, permissions, API). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String section() {
        return section;
    }

    public Material icon() {
        return icon;
    }

    public static CosmeticType parse(String value) {
        for (CosmeticType type : values()) {
            if (type.id().equalsIgnoreCase(value) || type.section.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}
