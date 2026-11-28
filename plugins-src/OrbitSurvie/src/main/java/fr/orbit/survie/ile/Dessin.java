package fr.orbit.survie.ile;

import org.bukkit.generator.ChunkGenerator;

/**
 * Un spawn de la survie dessiné par code : l'île tropicale (l'ancien) ou le port orbital (depuis le 26/11/2026).
 * Choisi par {@code ile.style} dans la config ; le reste du plugin ne passe que par cette interface.
 */
public interface Dessin {

    /** Tout le plan (blocs, points, PNJ, panneaux, particules). Calcul pur, rien n'est posé. */
    Canvas dessiner();

    /** Demi-côté du carré couvert par le plan, autour de (0, 0). */
    int reach();

    int minY();

    int maxY();

    /** Hauteur du sol (bloc) des places : on marche à sol() + 1. */
    int sol();

    /** Centre et rayon de la fosse de duel ; son sol est à sol() - 3. */
    int areneX();

    int areneZ();

    double areneR();

    /** Vrai si le monde est vide hors du plan (pas d'océan). */
    boolean vide();

    ChunkGenerator generateur();

    /** Au-dessous de cette hauteur, un joueur tombé est ramené au spawn (Integer.MIN_VALUE : jamais). */
    default int chute() {
        return Integer.MIN_VALUE;
    }

    static Dessin tropical() {
        return new Dessin() {
            public Canvas dessiner() { return IleDesign.dessiner(); }
            public int reach() { return IleDesign.REACH; }
            public int minY() { return IleDesign.MIN_Y; }
            public int maxY() { return IleDesign.MAX_Y; }
            public int sol() { return IleDesign.G; }
            public int areneX() { return IleDesign.ARENE_X; }
            public int areneZ() { return IleDesign.ARENE_Z; }
            public double areneR() { return IleDesign.ARENE_R; }
            public boolean vide() { return false; }
            public ChunkGenerator generateur() { return new OceanGenerator(); }
        };
    }

    static Dessin orbital() {
        return new Dessin() {
            public Canvas dessiner() { return OrbitalDesign.dessiner(); }
            public int reach() { return OrbitalDesign.REACH; }
            public int minY() { return OrbitalDesign.MIN_Y; }
            public int maxY() { return OrbitalDesign.MAX_Y; }
            public int sol() { return OrbitalDesign.G; }
            public int areneX() { return OrbitalDesign.ARENE_X; }
            public int areneZ() { return OrbitalDesign.ARENE_Z; }
            public double areneR() { return OrbitalDesign.ARENE_R; }
            public boolean vide() { return true; }
            public ChunkGenerator generateur() { return new VideGenerator(); }
            public int chute() { return OrbitalDesign.G - 24; }
        };
    }
}
