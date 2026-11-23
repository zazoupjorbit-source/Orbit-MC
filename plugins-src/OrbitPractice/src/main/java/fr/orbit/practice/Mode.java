package fr.orbit.practice;

/** Format d'un combat. */
public enum Mode {
    SOLO(1, "1v1"),
    DUO(2, "2v2");

    public final int teamSize;
    public final String label;

    Mode(int teamSize, String label) {
        this.teamSize = teamSize;
        this.label = label;
    }
}
