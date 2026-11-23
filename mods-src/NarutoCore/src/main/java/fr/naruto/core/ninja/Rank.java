package fr.naruto.core.ninja;

/** Rang ninja : determine le chakra max de base et l'acces aux jutsu. */
public enum Rank {
    GENIN("Genin", "#9AA0B4", 100, 1),
    CHUNIN("Chunin", "#5FAE5A", 150, 2),
    JONIN("Jonin", "#33AAFF", 220, 3),
    ANBU("ANBU", "#B23BE8", 300, 4),
    KAGE("Kage", "#FFB347", 400, 5);

    private final String displayName;
    private final String color;
    private final int baseMaxChakra;
    private final int tier;

    Rank(String displayName, String color, int baseMaxChakra, int tier) {
        this.displayName = displayName;
        this.color = color;
        this.baseMaxChakra = baseMaxChakra;
        this.tier = tier;
    }

    public String displayName() { return displayName; }
    public String color() { return color; }
    public int baseMaxChakra() { return baseMaxChakra; }
    public int tier() { return tier; }

    public Rank next() {
        Rank[] v = values();
        return ordinal() + 1 < v.length ? v[ordinal() + 1] : this;
    }

    public static Rank byId(String id) {
        if (id == null) return GENIN;
        try { return valueOf(id.toUpperCase()); } catch (IllegalArgumentException e) { return GENIN; }
    }
}
