package fr.orbit.guns.gun;

import org.bukkit.Material;

/** Definition d'une arme a feu. */
public record Gun(
        String id,
        String display,
        Material baseItem,
        int modelData,        // -1 = aucun
        String itemModel,     // null = aucun
        double damage,
        int magazine,
        int reloadTicks,
        long fireCooldownMs,
        Material ammoItem,
        double range,
        double spread,
        int pellets,
        double headshotMult,
        String sound,         // cle Minecraft (ex: entity.blaze.hurt)
        float aimZoom,        // vitesse de marche en visee (plus bas = plus de zoom ; negatif = gros zoom sniper)
        double aimSpreadMult  // multiplicateur de dispersion en visee (0.5 = 2x plus precis)
) {}
