package fr.orbit.devcore.cosmetic;

import java.util.Locale;

/** Rareté affichée dans le menu (et utilisable plus tard par la boutique pour les prix). */
public enum Rarity {
    COMMON, RARE, EPIC, LEGENDARY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Accepte aussi les noms français (commun, rare, epique, legendaire). */
    public static Rarity parse(String value) {
        if (value == null) {
            return COMMON;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "rare" -> RARE;
            case "epic", "epique", "épique" -> EPIC;
            case "legendary", "legendaire", "légendaire" -> LEGENDARY;
            default -> COMMON;
        };
    }
}
