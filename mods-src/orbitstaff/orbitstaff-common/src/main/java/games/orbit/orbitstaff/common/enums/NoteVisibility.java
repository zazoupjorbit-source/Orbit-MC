package games.orbit.orbitstaff.common.enums;

/**
 * Niveau de visibilite d'une note interne.
 *
 * <p>Le {@code weight} croissant traduit la sensibilite : une note {@code OWNER}
 * n'est visible que par les grades les plus eleves. Une note interne n'est
 * <strong>jamais</strong> affichee au joueur concerne.</p>
 */
public enum NoteVisibility {
    HELPER(1),
    MODERATOR(2),
    SENIOR(3),
    ADMIN(4),
    OWNER(5);

    private final int weight;

    NoteVisibility(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
