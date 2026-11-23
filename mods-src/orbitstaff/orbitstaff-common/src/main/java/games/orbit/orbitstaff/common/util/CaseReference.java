package games.orbit.orbitstaff.common.util;

import games.orbit.orbitstaff.common.OrbitStaffConstants;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reference lisible d'un dossier disciplinaire : {@code OS-2026-00482}.
 *
 * <p>Format : prefixe {@code OS}, annee sur 4 chiffres, sequence sur au moins
 * 5 chiffres (zero-padding). La sequence est allouee par serveur et par annee.</p>
 */
public record CaseReference(int year, long sequence) {

    private static final int MIN_SEQUENCE_DIGITS = 5;
    private static final Pattern PATTERN = Pattern.compile("^OS-(\\d{4})-(\\d{5,})$");

    public CaseReference {
        if (year < 2000 || year > 9999) {
            throw new IllegalArgumentException("Annee de dossier invalide : " + year);
        }
        if (sequence < 1) {
            throw new IllegalArgumentException("Sequence de dossier invalide : " + sequence);
        }
    }

    /**
     * @return la representation textuelle canonique, ex: {@code OS-2026-00482}.
     */
    public String format() {
        return OrbitStaffConstants.CASE_REFERENCE_PREFIX
                + "-" + String.format("%04d", year)
                + "-" + String.format("%0" + MIN_SEQUENCE_DIGITS + "d", sequence);
    }

    @Override
    public String toString() {
        return format();
    }

    /**
     * Analyse une reference textuelle.
     *
     * @param raw ex: {@code OS-2026-00482}
     * @return la reference correspondante
     * @throws IllegalArgumentException si le format est invalide
     */
    public static CaseReference parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Reference nulle");
        }
        Matcher matcher = PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Reference de dossier invalide : " + raw);
        }
        return new CaseReference(
                Integer.parseInt(matcher.group(1)),
                Long.parseLong(matcher.group(2)));
    }
}
