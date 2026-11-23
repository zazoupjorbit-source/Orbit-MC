package fr.orbitversus.match;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Un groupe en file d'attente : 1 joueur (1v1) ou une équipe nommée (2v2), avec une mise éventuelle. */
public final class Party {

    private final List<UUID> members = new ArrayList<>();
    private final String teamName;   // null en 1v1
    private long bet;                // mise par joueur (0 = pas de pari)

    public Party(String teamName) {
        this.teamName = teamName;
    }

    public List<UUID> members() {
        return members;
    }

    public String teamName() {
        return teamName;
    }

    public long bet() {
        return bet;
    }

    public void setBet(long bet) {
        this.bet = Math.max(0, bet);
    }

    public boolean contains(UUID uuid) {
        return members.contains(uuid);
    }

    public int size() {
        return members.size();
    }
}
