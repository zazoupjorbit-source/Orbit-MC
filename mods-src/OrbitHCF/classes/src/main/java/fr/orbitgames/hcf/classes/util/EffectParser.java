package fr.orbitgames.hcf.classes.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Analyse des effets configures au format "effet:niveau" (niveau humain).
 */
public final class EffectParser {

    /** Effet + niveau humain (1 = I). */
    public record ParsedEffect(PotionEffectType type, int level) {

        public PotionEffect toEffect(int durationTicks, boolean ambient) {
            return new PotionEffect(type, durationTicks, Math.max(0, level - 1), ambient, false, true);
        }
    }

    private EffectParser() {
    }

    public static PotionEffectType resolveType(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.toLowerCase(Locale.ROOT).replace('-', '_').trim();
        return Registry.EFFECT.get(NamespacedKey.minecraft(key));
    }

    /** Analyse "speed:2". Retourne null si l'effet est inconnu. */
    public static ParsedEffect parse(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String[] parts = input.split(":");
        PotionEffectType type = resolveType(parts[0]);
        if (type == null) {
            return null;
        }
        int level = 1;
        if (parts.length > 1) {
            try {
                level = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return new ParsedEffect(type, level);
    }

    public static List<ParsedEffect> parseAll(List<String> inputs) {
        List<ParsedEffect> result = new ArrayList<>();
        for (String input : inputs) {
            ParsedEffect parsed = parse(input);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }
}
