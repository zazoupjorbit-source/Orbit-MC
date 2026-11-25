package fr.orbit.bornes.pulsar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Morceaux de PULSAR, joués avec les blocs musicaux. Seulement du domaine public ou de la composition originale ORBIT :
 * <ul>
 *   <li>« Au clair de la lune » : chanson traditionnelle française (XVIIIe siècle), domaine public ;</li>
 *   <li>« Ode à la joie » : thème de L. van Beethoven (1824), domaine public ;</li>
 *   <li>« Pulsar » : composition originale pour ORBIT (2026).</li>
 * </ul>
 * Notation : {@code NOTE/croches} (ex. {@code B4/2} = noire, {@code -/2} = silence) ; les notes hors de la tessiture
 * des blocs musicaux (fa♯3 à fa♯5) sont déplacées d'une octave.
 */
public final class Songs {

    /** Une note : début (en croches), hauteur (0 à 24, fa♯3 = 0), durée (croches). */
    public record Note(int at, int pitch, int len) {
    }

    /**
     * Un morceau. {@code tick8} : ticks par croche (5 = 120 battements par minute, 4 = 150).
     * {@code drums} : 0 aucune, 1 légère (charleston), 2 complète (grosse caisse, caisse claire, charleston).
     */
    public record Song(String id, String title, String credit, int tick8, List<Note> melody, List<Note> bass, int drums, int length) {
    }

    public static final List<Song> ALL = new ArrayList<>();

    static {
        String lune1 = "G4/2 G4/2 G4/2 A4/2 B4/4 A4/4 G4/2 B4/2 A4/2 A4/2 G4/8 ";
        String lune2 = "A4/2 A4/2 A4/2 A4/2 E4/4 E4/4 A4/2 G4/2 F#4/2 E4/2 D4/8 ";
        add("lune", "Au clair de la lune", "traditionnel, domaine public", 5,
                (lune1 + lune1 + lune2 + lune1).repeat(2), "G3/8 D4/8 G3/8 G3/8 G3/8 D4/8 G3/8 G3/8 A3/8 E4/8 A3/8 D4/8 G3/8 D4/8 G3/8 G3/8 ".repeat(2), 1);
        String ode1 = "B4/2 B4/2 C5/2 D5/2 D5/2 C5/2 B4/2 A4/2 G4/2 G4/2 A4/2 B4/2 ";
        String odeA = ode1 + "B4/3 A4/1 A4/4 ";
        String odeB = ode1 + "A4/3 G4/1 G4/4 ";
        String odeC = "A4/2 A4/2 B4/2 G4/2 A4/2 B4/1 C5/1 B4/2 G4/2 A4/2 B4/1 C5/1 B4/2 A4/2 G4/2 A4/2 D4/4 ";
        add("ode", "Ode à la joie", "L. van Beethoven, domaine public", 5, (odeA + odeB + odeC + odeB).repeat(2),
                "G3/8 D4/8 G3/8 D4/8 G3/8 D4/8 G3/8 D4/4 G3/4 ".repeat(4), 2);
        String arps = "A4/1 C5/1 E5/1 C5/1 A4/1 C5/1 E5/1 C5/1 F4/1 A4/1 C5/1 A4/1 F4/1 A4/1 C5/1 A4/1 "
                + "E4/1 G4/1 C5/1 G4/1 E4/1 G4/1 C5/1 G4/1 D4/1 G4/1 B4/1 G4/1 D4/1 G4/1 B4/1 D5/1 ";
        String theme = "E5/2 D5/1 C5/1 B4/2 C5/2 A4/4 -/2 C5/2 G4/2 A4/1 B4/1 C5/2 E5/2 D5/6 -/2 "
                + "E5/2 F5/2 E5/2 D5/2 C5/2 A4/2 C5/2 E5/2 D5/2 C5/2 B4/2 G4/2 A4/8 ";
        String run = "A4/1 B4/1 C5/1 D5/1 E5/2 A4/2 F4/1 G4/1 A4/1 C5/1 F5/2 C5/2 E4/1 G4/1 C5/1 E5/1 G4/2 C5/2 D5/1 C5/1 B4/1 A4/1 G4/2 B4/2 ";
        String bassBar = "A3/2 A3/2 A3/2 A3/2 F3/2 F3/2 F3/2 F3/2 C4/2 C4/2 C4/2 C4/2 G3/2 G3/2 G3/2 G3/2 ";
        add("pulsar", "Pulsar", "original ORBIT", 4, arps + theme + run + theme + arps + "A4/8",
                bassBar.repeat(7) + "A3/8", 2);
    }

    private static void add(String id, String title, String credit, int tick8, String melody, String bass, int drums) {
        List<Note> m = parse(melody), b = parse(bass);
        int len = 0;
        for (Note n : m) {
            len = Math.max(len, n.at() + n.len());
        }
        ALL.add(new Song(id, title, credit, tick8, m, b, drums, len));
    }

    private static final String NAMES = "C C#D D#E F F#G G#A A#B ";

    static List<Note> parse(String s) {
        List<Note> out = new ArrayList<>();
        int at = 0;
        for (String tok : s.trim().split("\\s+")) {
            String[] p = tok.split("/");
            int len = Integer.parseInt(p[1]);
            if (!p[0].equals("-")) {
                out.add(new Note(at, pitch(p[0]), len));
            }
            at += len;
        }
        return out;
    }

    /** NOTE (ex. F#4) → 0 à 24 (fa♯3 = 0). */
    static int pitch(String n) {
        int oct = n.charAt(n.length() - 1) - '0';
        String name = n.substring(0, n.length() - 1);
        int semi = NAMES.indexOf(name.length() == 1 ? name + " " : name) / 2;
        int midi = 12 * (oct + 1) + semi;
        int p = midi - 54;
        while (p < 0) {
            p += 12;
        }
        while (p > 24) {
            p -= 12;
        }
        return p;
    }

    public static Song byId(String id) {
        for (Song s : ALL) {
            if (s.id().equalsIgnoreCase(id == null ? "" : id.toLowerCase(Locale.ROOT))) {
                return s;
            }
        }
        return null;
    }

    private Songs() {
    }
}
