package fr.orbit.bowl.bowling;

import java.util.ArrayList;
import java.util.List;

/**
 * Feuille de score officielle d'un joueur : 10 frames, strike = 10 + les 2 lancers suivants, spare = 10 + le suivant,
 * 10e frame en 3 lancers si strike ou spare. Score parfait : 300.
 */
public final class Feuille {

    private final List<Integer> lancers = new ArrayList<>();

    public void ajouter(int quilles) {
        lancers.add(quilles);
    }

    public List<Integer> lancers() {
        return lancers;
    }

    /** Index du premier lancer de chaque frame commencée (au plus 10). */
    private List<Integer> debuts() {
        List<Integer> d = new ArrayList<>();
        int i = 0;
        while (i < lancers.size() && d.size() < 10) {
            d.add(i);
            if (d.size() == 10) {
                break;
            }
            i += lancers.get(i) == 10 ? 1 : 2;
        }
        return d;
    }

    /** Frame en cours (0 à 9) ; 10 si la partie est finie. */
    public int frame() {
        if (termine()) {
            return 10;
        }
        List<Integer> d = debuts();
        if (d.isEmpty()) {
            return 0;
        }
        int f = d.size() - 1;
        return frameFinie(f, d) ? f + 1 : f;
    }

    /** Lancers déjà joués dans la frame en cours. */
    public int lancerDansFrame() {
        int f = frame();
        List<Integer> d = debuts();
        if (f >= d.size()) {
            return 0;
        }
        return lancers.size() - d.get(f);
    }

    private boolean frameFinie(int f, List<Integer> d) {
        int a = d.get(f), n = lancers.size() - a;
        if (f < 9) {
            return n >= 1 && lancers.get(a) == 10 || n >= 2;
        }
        if (n < 2) {
            return false;
        }
        if (lancers.get(a) == 10 || lancers.get(a) + lancers.get(a + 1) == 10) {
            return n >= 3;
        }
        return true;
    }

    public boolean termine() {
        List<Integer> d = debuts();
        return d.size() == 10 && frameFinie(9, d);
    }

    /** Après le dernier lancer : faut-il remettre les 10 quilles (nouvelle frame, strike ou spare en 10e) ? */
    public boolean remettreTout() {
        if (termine()) {
            return true;
        }
        if (lancerDansFrame() == 0) {
            return true; // la frame précédente vient de finir
        }
        int f = frame();
        List<Integer> d = debuts();
        int a = d.get(f), n = lancers.size() - a;
        if (f < 9) {
            return false;
        }
        // 10e frame
        if (n == 1) {
            return lancers.get(a) == 10;
        }
        int r1 = lancers.get(a), r2 = lancers.get(a + 1);
        return r1 == 10 ? r2 == 10 : r1 + r2 == 10;
    }

    /** Score cumulé de chaque frame calculable (null si on attend encore des lancers bonus). */
    public Integer[] cumuls() {
        Integer[] c = new Integer[10];
        List<Integer> d = debuts();
        int total = 0;
        for (int f = 0; f < d.size(); f++) {
            int a = d.get(f);
            Integer s;
            if (f < 9) {
                if (lancers.get(a) == 10) {
                    s = a + 2 < lancers.size() ? 10 + lancers.get(a + 1) + lancers.get(a + 2) : null;
                } else if (a + 1 < lancers.size()) {
                    int base = lancers.get(a) + lancers.get(a + 1);
                    s = base == 10 ? (a + 2 < lancers.size() ? 10 + lancers.get(a + 2) : null) : base;
                } else {
                    s = null;
                }
            } else {
                s = frameFinie(9, d) ? somme(a, lancers.size()) : null;
            }
            if (s == null) {
                break;
            }
            total += s;
            c[f] = total;
        }
        return c;
    }

    private int somme(int de, int a) {
        int s = 0;
        for (int i = de; i < a; i++) {
            s += lancers.get(i);
        }
        return s;
    }

    /** Total des frames calculables. */
    public int total() {
        Integer[] c = cumuls();
        int t = 0;
        for (Integer v : c) {
            if (v != null) {
                t = v;
            }
        }
        return t;
    }

    /** Marques d'une frame : « X », « 7/ », « 9- », « 81 »… (10e frame : jusqu'à 3 marques). */
    public String marques(int f) {
        List<Integer> d = debuts();
        if (f >= d.size()) {
            return "";
        }
        int a = d.get(f), n = f < 9 ? (lancers.get(a) == 10 ? 1 : 2) : 3;
        StringBuilder sb = new StringBuilder();
        int deboutAvant = 10;
        for (int i = 0; i < n && a + i < lancers.size(); i++) {
            int v = lancers.get(a + i);
            boolean nouveauRack = deboutAvant == 10;
            if (v == 10 && nouveauRack) {
                sb.append('X');
                deboutAvant = 10;
            } else if (!nouveauRack && v == deboutAvant) {
                sb.append('/');
                deboutAvant = 10;
            } else {
                sb.append(v == 0 ? "-" : String.valueOf(v));
                deboutAvant = nouveauRack ? 10 - v : 10;
            }
        }
        return sb.toString();
    }
}
