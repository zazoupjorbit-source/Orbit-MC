package fr.orbit.royale;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Profil d'un commandant du Siège (table royale_profils) : trophées, record, bilan et deck. */
final class Profile {

    final UUID uuid;
    String name;
    int trophies, record, wins, losses, draws;
    final List<Card> deck = new ArrayList<>();
    boolean saved = true; // false : base injoignable, rien n'est enregistré

    Profile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    List<Card> unlocked() {
        return Card.unlocked(record);
    }

    /** Taille de deck attendue : 8, ou toutes les cartes débloquées s'il y en a moins. */
    int deckSize() {
        return Math.min(8, unlocked().size());
    }

    /** Garde seulement les cartes débloquées, sans doublon, et complète jusqu'à la taille attendue. */
    void fixDeck() {
        List<Card> ok = unlocked();
        List<Card> clean = new ArrayList<>();
        for (Card c : deck) {
            if (ok.contains(c) && !clean.contains(c) && clean.size() < 8) {
                clean.add(c);
            }
        }
        for (Card c : ok) {
            if (clean.size() >= deckSize()) {
                break;
            }
            if (!clean.contains(c)) {
                clean.add(c);
            }
        }
        deck.clear();
        deck.addAll(clean);
    }

    String deckCsv() {
        StringBuilder sb = new StringBuilder();
        for (Card c : deck) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(c.id);
        }
        return sb.toString();
    }

    void deckFromCsv(String csv) {
        deck.clear();
        if (csv != null) {
            for (String s : csv.split(",")) {
                Card c = Card.byId(s);
                if (c != null) {
                    deck.add(c);
                }
            }
        }
        if (deck.isEmpty()) {
            deck.addAll(Card.starter());
        }
        fixDeck();
    }
}
