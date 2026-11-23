package games.orbit.orbitstaff.common.enums;

/**
 * Grades de staff OrbitStaff (systeme de permissions metier propre).
 *
 * <p>Le {@code weight} definit la hierarchie. Il sert notamment a decider si un
 * grade peut consulter une note d'une {@link NoteVisibility} donnee. Toute
 * verification effective se fait cote serveur : masquer un bouton ne protege rien.</p>
 */
public enum StaffRole {
    HELPER(1),
    MODERATOR(2),
    SENIOR_MODERATOR(3),
    ADMINISTRATOR(4),
    MANAGER(5);

    private final int weight;

    StaffRole(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }

    /**
     * @return true si ce grade est au moins aussi eleve que {@code other}.
     */
    public boolean isAtLeast(StaffRole other) {
        return this.weight >= other.weight;
    }

    /**
     * Decide si ce grade peut consulter une note de la visibilite indiquee.
     *
     * <p>Le mapping est aligne sur les {@code weight} : HELPER&lt;-&gt;HELPER,
     * MODERATOR&lt;-&gt;MODERATOR, SENIOR_MODERATOR&lt;-&gt;SENIOR,
     * ADMINISTRATOR&lt;-&gt;ADMIN, MANAGER&lt;-&gt;OWNER.</p>
     *
     * @param visibility niveau de sensibilite de la note
     * @return true si le grade a un poids suffisant
     */
    public boolean canView(NoteVisibility visibility) {
        return this.weight >= visibility.weight();
    }
}
