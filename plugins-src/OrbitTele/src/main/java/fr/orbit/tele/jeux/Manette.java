package fr.orbit.tele.jeux;

/**
 * Touches du joueur. Le serveur les reçoit sur son fil ({@link #recevoir}), le jeu les lit sur le sien
 * ({@link #lire} une fois par image). Un appui très court, relâché entre deux images, n'est pas perdu.
 */
public final class Manette {

    public static final int HAUT = 0, BAS = 1, GAUCHE = 2, DROITE = 3, A = 4, B = 5;

    private final boolean[] brut = new boolean[6], verrou = new boolean[6];
    private final boolean[] etat = new boolean[6], avant = new boolean[6];
    public volatile long dernier = System.currentTimeMillis();

    public synchronized void recevoir(int touche, boolean enfoncee) {
        if (enfoncee && !brut[touche]) {
            verrou[touche] = true;
            dernier = System.currentTimeMillis();
        }
        brut[touche] = enfoncee;
    }

    public synchronized void lire() {
        for (int i = 0; i < 6; i++) {
            avant[i] = etat[i];
            etat[i] = brut[i] || verrou[i];
            verrou[i] = false;
        }
    }

    public boolean tenu(int touche) {
        return etat[touche];
    }

    /** Vrai seulement à l'image où la touche vient d'être enfoncée. */
    public boolean appui(int touche) {
        return etat[touche] && !avant[touche];
    }
}
