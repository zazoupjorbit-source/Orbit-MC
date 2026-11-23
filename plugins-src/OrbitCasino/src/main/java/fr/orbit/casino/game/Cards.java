package fr.orbit.casino.game;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Cartes à jouer : 2 à 14 (valet 11, dame 12, roi 13, as 14), couleurs ♠ ♥ ♦ ♣. */
public final class Cards {

    public static final Random RANDOM = new SecureRandom();
    private static final String[] SUITS = {"♠", "♥", "♦", "♣"};

    private Cards() {
    }

    public record Card(int rank, int suit) {
        public String label() {
            return switch (rank) {
                case 14 -> "A";
                case 13 -> "R";
                case 12 -> "D";
                case 11 -> "V";
                default -> String.valueOf(rank);
            } + SUITS[suit];
        }

        /** Carte en MiniMessage : rouge pour cœur et carreau, blanc pour pique et trèfle, sur fond « carte ». */
        public String mini() {
            String color = suit == 1 || suit == 2 ? "#E0484A" : "#F2F4F7";
            return "<" + color + "><b>" + label() + "</b></" + color + ">";
        }
    }

    public static String hidden() {
        return "<#3A7FFF><b>▒▒</b></#3A7FFF>";
    }

    public static String row(List<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (Card c : cards) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(c.mini());
        }
        return sb.toString();
    }

    /** Sabot de N jeux de 52 cartes, rebattu quand il en reste moins d'un quart. */
    public static final class Shoe {
        private final int decks;
        private final List<Card> cards = new ArrayList<>();

        public Shoe(int decks) {
            this.decks = decks;
            shuffle();
        }

        public void shuffle() {
            cards.clear();
            for (int d = 0; d < decks; d++) {
                for (int s = 0; s < 4; s++) {
                    for (int r = 2; r <= 14; r++) {
                        cards.add(new Card(r, s));
                    }
                }
            }
            Collections.shuffle(cards, RANDOM);
        }

        public boolean low() {
            return cards.size() < decks * 13;
        }

        public Card draw() {
            if (cards.isEmpty()) {
                shuffle();
            }
            return cards.remove(cards.size() - 1);
        }
    }
}
