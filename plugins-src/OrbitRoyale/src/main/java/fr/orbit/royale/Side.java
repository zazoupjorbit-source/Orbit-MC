package fr.orbit.royale;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Un camp de la partie : joueur (ou bot), plasma, main de 4 cartes, file du deck, étoiles et tours. */
final class Side {

    final int index;
    final Player player;      // null pour le bot
    final Profile profile;    // null pour le bot
    final String name;
    Bot bot;

    double plasma;
    final Card[] hand = new Card[4];
    final Deque<Card> queue = new ArrayDeque<>();
    int stars, played;
    Body main, left, right;

    BossBar plasmaBar, infoBar;
    Saved saved;
    int lastClick;
    String handKey = "";
    boolean noReturn; // parti ailleurs (téléporté) : on ne le ramène pas devant le PNJ
    org.bukkit.entity.ItemDisplay ghost; // aperçu de la carte visée
    Card ghostCard;

    Side(int index, Player player, Profile profile, String name, List<Card> deck) {
        this.index = index;
        this.player = player;
        this.profile = profile;
        this.name = name;
        List<Card> d = new ArrayList<>(deck);
        Collections.shuffle(d);
        for (int i = 0; i < 4; i++) {
            hand[i] = d.get(i % d.size());
        }
        for (int i = 4; i < d.size(); i++) {
            queue.add(d.get(i));
        }
    }

    /** Signe de son demi-terrain : -1 pour le camp du bas, +1 pour celui du haut. */
    int sign() {
        return index == 0 ? -1 : 1;
    }

    boolean human() {
        return player != null;
    }

    Card next() {
        return queue.isEmpty() ? null : queue.peek();
    }

    /** Joue la carte de la main : elle repart au fond du deck, la suivante la remplace. */
    void cycle(int slot) {
        Card played = hand[slot];
        if (!queue.isEmpty()) {
            hand[slot] = queue.poll();
            queue.add(played);
        }
    }

    Body sideTower(int lane) {
        return lane < 0 ? left : right;
    }

    List<Body> towers() {
        List<Body> l = new ArrayList<>(3);
        for (Body b : new Body[]{left, right, main}) {
            if (b != null && !b.dead) {
                l.add(b);
            }
        }
        return l;
    }

    double towerHealth() {
        double sum = 0, max = 0;
        for (Body b : new Body[]{left, right, main}) {
            if (b != null) {
                sum += b.dead ? 0 : b.hp;
                max += b.max;
            }
        }
        return max == 0 ? 0 : sum / max;
    }
}
