package fr.orbit.casino.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Force d'une main de Texas Hold'em : meilleure combinaison de 5 cartes parmi 7.
 * Le score se compare directement (plus grand = meilleur) : catégorie puis cartes départageantes.
 */
public final class PokerHand {

    public static final String[] NAMES = {"Carte haute", "Paire", "Double paire", "Brelan", "Quinte",
            "Couleur", "Full", "Carré", "Quinte flush", "Quinte flush royale"};

    private PokerHand() {
    }

    public record Result(long score, int category) {
        public String name() {
            return NAMES[category];
        }
    }

    public static Result best(List<Cards.Card> cards) {
        Result best = null;
        int n = cards.size();
        int[] idx = new int[5];
        // toutes les combinaisons de 5 cartes parmi n (21 pour 7)
        for (idx[0] = 0; idx[0] < n; idx[0]++)
            for (idx[1] = idx[0] + 1; idx[1] < n; idx[1]++)
                for (idx[2] = idx[1] + 1; idx[2] < n; idx[2]++)
                    for (idx[3] = idx[2] + 1; idx[3] < n; idx[3]++)
                        for (idx[4] = idx[3] + 1; idx[4] < n; idx[4]++) {
                            List<Cards.Card> five = new ArrayList<>(5);
                            for (int i : idx) {
                                five.add(cards.get(i));
                            }
                            Result r = five(five);
                            if (best == null || r.score() > best.score()) {
                                best = r;
                            }
                        }
        return best;
    }

    private static Result five(List<Cards.Card> five) {
        int[] ranks = new int[5];
        boolean flush = true;
        for (int i = 0; i < 5; i++) {
            ranks[i] = five.get(i).rank();
            if (five.get(i).suit() != five.get(0).suit()) {
                flush = false;
            }
        }
        Arrays.sort(ranks);
        int[] counts = new int[15];
        for (int r : ranks) {
            counts[r]++;
        }
        int straightHigh = 0;
        boolean distinct = ranks[0] != ranks[1] && ranks[1] != ranks[2] && ranks[2] != ranks[3] && ranks[3] != ranks[4];
        if (distinct && ranks[4] - ranks[0] == 4) {
            straightHigh = ranks[4];
        } else if (distinct && ranks[4] == 14 && ranks[0] == 2 && ranks[3] == 5) {
            straightHigh = 5; // roue A-2-3-4-5
        }
        // cartes triées par (nombre d'exemplaires, hauteur) décroissants
        Integer[] order = {ranks[0], ranks[1], ranks[2], ranks[3], ranks[4]};
        Arrays.sort(order, (a, b) -> counts[a] != counts[b] ? counts[b] - counts[a] : b - a);
        int category;
        int[] groups = Arrays.stream(counts).filter(c -> c > 0).sorted().toArray();
        int top = groups[groups.length - 1];
        if (straightHigh > 0 && flush) {
            category = straightHigh == 14 ? 9 : 8;
        } else if (top == 4) {
            category = 7;
        } else if (top == 3 && groups.length == 2) {
            category = 6;
        } else if (flush) {
            category = 5;
        } else if (straightHigh > 0) {
            category = 4;
        } else if (top == 3) {
            category = 3;
        } else if (top == 2 && groups.length == 3) {
            category = 2;
        } else if (top == 2) {
            category = 1;
        } else {
            category = 0;
        }
        long score = Math.min(category, 8); // la royale compte comme une quinte flush à l'as
        if (straightHigh > 0) {
            score = score * 15 + straightHigh;
            for (int i = 1; i < 5; i++) {
                score *= 15;
            }
        } else {
            for (int r : order) {
                score = score * 15 + r;
            }
        }
        return new Result(score, category);
    }
}
