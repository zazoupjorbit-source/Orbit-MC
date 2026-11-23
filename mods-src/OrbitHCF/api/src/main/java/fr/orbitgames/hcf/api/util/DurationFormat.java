package fr.orbitgames.hcf.api.util;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Format de duree partage par tous les modules OrbitHCF (regle 10).
 */
public final class DurationFormat {

    private static final Pattern PARSE = Pattern.compile("(\\d+)\\s*([a-zA-Z]+)");

    private DurationFormat() {
    }

    /**
     * Format compact type HCF :
     * "2j3h", "1h05m", "4m30s", "29s", puis "9.5s" sous 10 secondes.
     */
    public static String compact(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "0s";
        }
        long millis = duration.toMillis();
        long seconds = millis / 1000L;
        if (seconds < 10L) {
            return String.format(Locale.US, "%.1fs", millis / 1000.0D);
        }
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (days > 0L) {
            return days + "j" + (hours > 0L ? hours + "h" : "");
        }
        if (hours > 0L) {
            return hours + "h" + String.format(Locale.US, "%02dm", minutes);
        }
        if (minutes > 0L) {
            return minutes + "m" + (secs > 0L ? String.format(Locale.US, "%02ds", secs) : "");
        }
        return secs + "s";
    }

    /** Format horloge : "1:05:09" ou "4:30". */
    public static String clock(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return "0:00";
        }
        long seconds = duration.getSeconds();
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.US, "%d:%02d", minutes, secs);
    }

    /** Format long en francais : "2 jours 3 heures 5 minutes". */
    public static String verbose(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "0 seconde";
        }
        long seconds = duration.getSeconds();
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        StringBuilder sb = new StringBuilder();
        if (days > 0L) {
            sb.append(days).append(days > 1L ? " jours " : " jour ");
        }
        if (hours > 0L) {
            sb.append(hours).append(hours > 1L ? " heures " : " heure ");
        }
        if (minutes > 0L) {
            sb.append(minutes).append(minutes > 1L ? " minutes " : " minute ");
        }
        if (secs > 0L && days == 0L && hours == 0L) {
            sb.append(secs).append(secs > 1L ? " secondes" : " seconde");
        }
        return sb.toString().trim();
    }

    /**
     * Analyse une duree au format "1h30m", "45s", "2j" / "2d", "10min".
     * Retourne {@link Duration#ZERO} si rien n'est reconnu.
     */
    public static Duration parse(String input) {
        if (input == null || input.isBlank()) {
            return Duration.ZERO;
        }
        // Nombre brut : interprete en secondes
        if (input.matches("\\d+")) {
            return Duration.ofSeconds(Long.parseLong(input));
        }
        Matcher matcher = PARSE.matcher(input.toLowerCase(Locale.ROOT));
        long totalSeconds = 0L;
        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            totalSeconds += switch (unit) {
                case "j", "d", "jour", "jours", "day", "days" -> value * 86400L;
                case "h", "heure", "heures", "hour", "hours" -> value * 3600L;
                case "m", "min", "mins", "minute", "minutes" -> value * 60L;
                case "s", "sec", "secs", "seconde", "secondes", "second", "seconds" -> value;
                default -> 0L;
            };
        }
        return Duration.ofSeconds(totalSeconds);
    }
}
