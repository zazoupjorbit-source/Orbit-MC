package fr.orbitversus.match;

/** Format d'un combat. */
public enum VersusMode {
    ONE_V_ONE(1, "1v1"),
    TWO_V_TWO(2, "2v2");

    public final int teamSize;
    public final String label;

    VersusMode(int teamSize, String label) {
        this.teamSize = teamSize;
        this.label = label;
    }
}
