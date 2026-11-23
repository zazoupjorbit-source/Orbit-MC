package games.orbit.orbitstaff.common.util;

import games.orbit.orbitstaff.common.error.InvalidDurationException;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyse stricte des durees de sanction.
 *
 * <p>Formats acceptes : {@code 30s}, {@code 30m}, {@code 2h}, {@code 3d},
 * {@code 2w}, {@code 1mo}, {@code 1y}, et le mot-cle {@code permanent}
 * (alias {@code perm}). La casse et les espaces de bord sont tolerants.</p>
 *
 * <p>Toute valeur incorrecte (unite inconnue, zero, negatif, depassement) leve
 * {@link InvalidDurationException}. Aucune valeur invalide n'est silencieusement
 * acceptee.</p>
 *
 * <p>Equivalences : {@code w = 7d}, {@code mo = 30d}, {@code y = 365d}.</p>
 */
public final class DurationParser {

    // "mo" avant "m" pour lever toute ambiguite d'unite.
    private static final Pattern PATTERN =
            Pattern.compile("^(\\d{1,10})(mo|s|m|h|d|w|y)$");

    private DurationParser() {
    }

    /**
     * Analyse une chaine de duree.
     *
     * @param input chaine brute (ex: "7d", "permanent")
     * @return la duree analysee
     * @throws InvalidDurationException si l'entree est invalide
     */
    public static ParsedDuration parse(String input) {
        if (input == null) {
            throw new InvalidDurationException("null");
        }
        String value = input.trim().toLowerCase();
        if (value.isEmpty()) {
            throw new InvalidDurationException(input);
        }
        if (value.equals("permanent") || value.equals("perm")) {
            return ParsedDuration.PERMANENT;
        }

        Matcher matcher = PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new InvalidDurationException(input);
        }

        long amount;
        try {
            amount = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            throw new InvalidDurationException(input);
        }
        if (amount <= 0) {
            throw new InvalidDurationException(input);
        }

        long unitSeconds = secondsPerUnit(matcher.group(2));
        long totalSeconds;
        try {
            totalSeconds = Math.multiplyExact(amount, unitSeconds);
        } catch (ArithmeticException overflow) {
            throw new InvalidDurationException(input);
        }
        return ParsedDuration.temporary(Duration.ofSeconds(totalSeconds));
    }

    private static long secondsPerUnit(String unit) {
        return switch (unit) {
            case "s" -> 1L;
            case "m" -> 60L;
            case "h" -> 3_600L;
            case "d" -> 86_400L;
            case "w" -> 604_800L;         // 7 jours
            case "mo" -> 2_592_000L;      // 30 jours
            case "y" -> 31_536_000L;      // 365 jours
            default -> throw new InvalidDurationException(unit);
        };
    }
}
