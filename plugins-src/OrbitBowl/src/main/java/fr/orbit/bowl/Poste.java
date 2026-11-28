package fr.orbit.bowl;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Un poste de jeu de la salle (une piste, une cible, un stand…) : sa console cliquable et l'activité en cours.
 * {@code index} : numéro du poste dans son jeu (0, 1, 2…), qui donne sa géométrie (voir Salle).
 */
public final class Poste {

    public final String id, nom;
    public final Jeu jeu;
    public final int index;
    /** Console : là où l'on clique pour jouer (entité Interaction + texte). */
    public final double cx, cy, cz;
    public final float yaw;
    public Activite activite;

    public Poste(String id, String nom, Jeu jeu, int index, double cx, double cy, double cz, float yaw) {
        this.id = id;
        this.nom = nom;
        this.jeu = jeu;
        this.index = index;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.yaw = yaw;
    }

    public Location console(World w) {
        return new Location(w, cx, cy, cz, yaw, 0f);
    }

    public boolean libre() {
        return activite == null || activite.finie();
    }
}
