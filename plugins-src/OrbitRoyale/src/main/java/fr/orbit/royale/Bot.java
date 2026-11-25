package fr.orbit.royale;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Adversaire automatique (le réseau a peu de joueurs) : il défend ce qui entre dans son camp, contre les groupes
 * avec un sort, et attaque quand il a du plasma en réserve. Son niveau (0 à 3) suit les trophées du joueur :
 * plus lent et plus brouillon au début, plus vif ensuite.
 */
final class Bot {

    private static final String[] NAMES = {"Stratège du Néant", "Unité KX-9", "Commandant Vesper", "IA Sentinelle-7",
            "Amiral Morne", "Spectre d'Astraeos", "Drone-Maître 0R8"};

    final Match match;
    final Side me;
    final int level;
    private final Random rnd = new Random();
    private int think = 40, pushTimer;

    Bot(Match match, Side me, int level) {
        this.match = match;
        this.me = me;
        this.level = level;
    }

    static String name(int level) {
        return NAMES[new Random().nextInt(NAMES.length)] + " <#9AA4B2>(bot niv. " + (level + 1) + ")";
    }

    static int levelFor(int trophies) {
        return trophies < 60 ? 0 : trophies < 150 ? 1 : trophies < 300 ? 2 : 3;
    }

    /** Deck du bot : cartes débloquées autour des trophées du joueur, au moins un anti-volant et un sort. */
    static List<Card> deck(int trophies) {
        List<Card> pool = Card.unlocked(trophies + 40);
        java.util.Collections.shuffle(pool);
        List<Card> d = new ArrayList<>();
        for (Card c : pool) {
            if (d.size() < 8) {
                d.add(c);
            }
        }
        if (d.stream().noneMatch(c -> c.air && c.kind != Card.Kind.SPELL)) {
            d.set(0, Card.CANONNIER);
        }
        if (d.stream().noneMatch(c -> c.kind == Card.Kind.SPELL)) {
            d.set(d.size() - 1, Card.FRAPPE);
        }
        return d;
    }

    private int reaction() {
        return switch (level) {
            case 0 -> 30 + rnd.nextInt(25);
            case 1 -> 20 + rnd.nextInt(18);
            case 2 -> 12 + rnd.nextInt(14);
            default -> 8 + rnd.nextInt(9);
        };
    }

    /** Position vers son propre camp : positif = dans son demi-terrain. */
    private double toward(double z) {
        return z * me.sign();
    }

    void tick() {
        if (--think > 0) {
            return;
        }
        think = reaction();
        Side enemy = match.other(me);
        List<Body> threats = new ArrayList<>();
        for (Body b : match.bodies) {
            if (!b.dead && b.side == enemy.index && b.mover() && toward(b.z) > -4) {
                threats.add(b);
            }
        }
        if (!threats.isEmpty() && defend(threats)) {
            return;
        }
        // attaque
        double threshold = level == 0 ? 9.5 : level == 1 ? 9 : 8;
        if (++pushTimer > (level == 0 ? 6 : 3) && rnd.nextInt(4) == 0) {
            threshold -= 2;
        }
        if (me.plasma >= threshold) {
            push(enemy);
        } else if (me.plasma >= 6) {
            support(enemy);
        }
    }

    private boolean defend(List<Body> threats) {
        // groupe serré : un sort
        Body center = null;
        int best = 0;
        double hpIn = 0;
        for (Body t : threats) {
            int n = 0;
            double hp = 0;
            for (Body o : threats) {
                if (Math.hypot(o.x - t.x, o.z - t.z) <= 2.5) {
                    n++;
                    hp += o.hp;
                }
            }
            if (n > best || (n == best && hp > hpIn)) {
                best = n;
                hpIn = hp;
                center = t;
            }
        }
        if (center != null && (best >= 3 || hpIn >= 900)) {
            for (int i = 0; i < 4; i++) {
                Card c = me.hand[i];
                if (c != null && c.kind == Card.Kind.SPELL && me.plasma >= c.cost && (level > 0 || rnd.nextBoolean())) {
                    double lead = me.sign() * 0.8; // ils avancent vers nous
                    if (match.play(me, i, center.x, center.z + lead) == null) {
                        return true;
                    }
                }
            }
        }
        // la menace la plus avancée
        Body main = null;
        for (Body t : threats) {
            if (main == null || toward(t.z) > toward(main.z)) {
                main = t;
            }
        }
        // on laisse la menace entrer dans la portée de nos tours (sauf plasma plein) : les tours font leur part
        double gate = main != null && main.fly ? -1 : 1.5;
        if (main == null || (toward(main.z) < gate && me.plasma < 9.5)) {
            return false;
        }
        // déjà assez de défenseurs autour : on garde le plasma pour la contre-attaque
        double ours = 0;
        for (Body b : match.bodies) {
            if (!b.dead && b.side == me.index && b.mover() && !b.towersOnly && b.dist(main) <= 4.5 && (!main.fly || b.air)) {
                ours += b.hp;
            }
        }
        if (ours >= main.hp * (level == 0 ? 0.6 : 0.9)) {
            return false;
        }
        int pick = -1;
        double score = -1;
        for (int i = 0; i < 4; i++) {
            Card c = me.hand[i];
            if (c == null || c.kind == Card.Kind.SPELL || me.plasma < c.cost) {
                continue;
            }
            if (main.fly && !c.air) {
                continue;
            }
            if (c.towersOnly) {
                continue;
            }
            double s = c.dmg * 20.0 / Math.max(1, c.hit) * c.count + c.hp * 0.05 + rnd.nextDouble() * 20;
            if (main.max >= 1500 && c.kind == Card.Kind.BUILDING) {
                s += 60; // un bâtiment attire les gros
            }
            if (s > score) {
                score = s;
                pick = i;
            }
        }
        if (pick < 0) {
            return me.plasma < 9.5; // rien d'utile : on attend un peu
        }
        Card c = me.hand[pick];
        double x = Math.max(-8, Math.min(8, main.x * 0.7));
        double depth = Math.max(6, Math.min(13, toward(main.z) + (c.range >= 2 ? 5 : 3)));
        if (c.kind == Card.Kind.BUILDING) {
            x = 0;
            depth = 8;
        }
        if (level == 0 && rnd.nextInt(5) == 0) {
            x += rnd.nextGaussian() * 2.5;
            depth += rnd.nextGaussian() * 2;
        }
        return match.play(me, pick, clampX(x), me.sign() * clampDepth(depth)) == null;
    }

    /** Nos unités ont passé la faille (souvent des défenseurs qui survivent) : on les accompagne. */
    private void support(Side enemy) {
        double hp = 0, x = 0;
        for (Body b : match.bodies) {
            if (!b.dead && b.side == me.index && b.mover() && toward(b.z) < 1) {
                hp += b.hp;
                x += b.x * b.hp;
            }
        }
        if (hp < 300 || rnd.nextInt(level == 0 ? 3 : 2) != 0) {
            return;
        }
        int lane = x < 0 ? -1 : 1;
        int pick = -1;
        for (int i = 0; i < 4; i++) {
            Card c = me.hand[i];
            if (c != null && c.kind == Card.Kind.TROOP && me.plasma >= c.cost + 1 && (pick < 0 || c.cost < me.hand[pick].cost)) {
                pick = i;
            }
        }
        if (pick >= 0) {
            match.play(me, pick, clampX(Arena.towerX(lane) + rnd.nextGaussian() * 0.6), me.sign() * 3.5);
        }
    }

    private void push(Side enemy) {
        // voie où la Tourelle adverse est la plus faible
        int lane;
        Body l = enemy.left, r = enemy.right;
        double lh = l == null || l.dead ? 0 : l.hp, rh = r == null || r.dead ? 0 : r.hp;
        lane = lh == rh ? (rnd.nextBoolean() ? -1 : 1) : lh < rh ? -1 : 1;
        // finir une tour à la Frappe
        for (int i = 0; i < 4; i++) {
            Card c = me.hand[i];
            if (c != null && c.kind == Card.Kind.SPELL && c.dmg * c.towerPct > 0 && me.plasma >= c.cost) {
                for (Body t : enemy.towers()) {
                    if (t.hp <= c.dmg * c.towerPct) {
                        match.play(me, i, t.x, t.z);
                        return;
                    }
                }
            }
        }
        int pick = -1;
        double score = -1;
        for (int i = 0; i < 4; i++) {
            Card c = me.hand[i];
            if (c == null || c.kind != Card.Kind.TROOP || me.plasma < c.cost) {
                continue;
            }
            double s = c.hp * (c.towersOnly ? 1.4 : 1) + rnd.nextDouble() * 400;
            if (s > score) {
                score = s;
                pick = i;
            }
        }
        if (pick < 0) {
            // que des sorts / bâtiments en main : on fait tourner le deck avec la moins chère
            for (int i = 0; i < 4; i++) {
                Card c = me.hand[i];
                if (c != null && c.kind == Card.Kind.BUILDING && me.plasma >= c.cost) {
                    match.play(me, i, 0, me.sign() * 7);
                    return;
                }
            }
            return;
        }
        Card c = me.hand[pick];
        double depth = c.speed < 1 || c.hp > 1500 ? 17.5 : c.speed >= 1.5 ? 3 : 9;
        double x = Arena.towerX(lane) * (c.hp > 1500 ? 0.4 : 0.9);
        match.play(me, pick, clampX(x + rnd.nextGaussian() * 0.7), me.sign() * clampDepth(depth));
        pushTimer = 0;
    }

    private static double clampX(double x) {
        return Math.max(-8.5, Math.min(8.5, x));
    }

    private static double clampDepth(double d) {
        return Math.max(2.5, Math.min(17.5, d));
    }
}
