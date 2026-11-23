package fr.naruto.core.ninja;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Profil d'un joueur ninja (persiste) + son chakra courant (runtime). */
public final class Ninja {

    private final UUID uuid;
    private Element element;
    private Clan clan;
    private Rank rank;
    private int xp;
    private final Set<String> learnedJutsu = new LinkedHashSet<>();

    private double chakra;     // courant (runtime)

    public Ninja(UUID uuid, Element element, Clan clan, Rank rank, int xp) {
        this.uuid = uuid;
        this.element = element;
        this.clan = clan;
        this.rank = rank;
        this.xp = xp;
        this.chakra = maxChakra();
    }

    public UUID uuid() { return uuid; }
    public Element element() { return element; }
    public Clan clan() { return clan; }
    public Rank rank() { return rank; }
    public int xp() { return xp; }
    public Set<String> learnedJutsu() { return learnedJutsu; }

    public void setElement(Element e) { this.element = e; }
    public void setClan(Clan c) { this.clan = c; clampChakra(); }
    public void setRank(Rank r) { this.rank = r; clampChakra(); }
    public void setXp(int xp) { this.xp = xp; }
    public void addXp(int amount) { this.xp += amount; }

    public boolean isComplete() { return element != Element.NONE && clan != null; }

    public int maxChakra() { return rank.baseMaxChakra() + (clan != null ? clan.chakraBonus() : 0); }
    public double chakra() { return chakra; }
    public void setChakra(double c) { this.chakra = Math.max(0, Math.min(maxChakra(), c)); }
    public boolean hasChakra(double cost) { return chakra >= cost; }
    public void spend(double cost) { setChakra(chakra - cost); }
    public void restore(double amount) { setChakra(chakra + amount); }
    private void clampChakra() { setChakra(chakra); }
}
