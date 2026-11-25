package fr.orbit.royale;

import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

/**
 * Tout ce qui se bat sur l'arène : tours (Bastion et Tourelles), unités et bâtiments posés par les cartes.
 * Positions en cases de l'arène (x de -9,5 à 9,5, z de -18,5 à 18,5 ; le camp 0 est en bas, z négatif).
 */
final class Body {

    final int side;
    final Card card;          // null pour une tour
    final boolean tower, main;
    double x, z, r;
    double hp, max, dmg, range, sight, speed, splash;
    int hit;
    boolean fly, air, towersOnly;

    int cd, deploy, frozen, life, lifeMax, spawnCd;
    boolean active = true, dead;
    Body target;
    int retarget;
    float scale;

    ItemDisplay view;
    TextDisplay bar;
    String lastBar = "";
    double lastX = Double.NaN, lastZ = Double.NaN;

    /** Tour d'un camp. */
    Body(int side, boolean main, double x, double z) {
        this.side = side;
        this.card = null;
        this.tower = true;
        this.main = main;
        this.x = x;
        this.z = z;
        this.r = main ? 2.4 : 1.5;
        this.max = this.hp = main ? 2400 : 1400;
        this.dmg = main ? 55 : 45;
        this.hit = main ? 20 : 16;
        this.range = main ? 6.5 : 6.0;
        this.air = true;
        this.active = !main; // le Bastion dort tant qu'il n'est pas touché ou qu'une Tourelle n'est pas tombée
    }

    /** Unité ou bâtiment d'une carte. */
    Body(int side, Card card, double x, double z) {
        this.side = side;
        this.card = card;
        this.tower = false;
        this.main = false;
        this.x = x;
        this.z = z;
        this.r = card.size;
        this.max = this.hp = card.hp;
        this.dmg = card.dmg;
        this.hit = card.hit;
        this.range = card.range;
        this.speed = card.speed;
        this.splash = card.splash;
        this.fly = card.fly;
        this.air = card.air;
        this.towersOnly = card.towersOnly;
        this.sight = Math.max(card.range + 1.0, 5.5);
        this.scale = card.scale;
        this.deploy = 20;
        if (card.life > 0) {
            this.life = this.lifeMax = card.life;
        }
        this.spawnCd = card.spawnEvery > 0 ? 30 : 0;
    }

    boolean building() {
        return tower || (card != null && card.kind == Card.Kind.BUILDING);
    }

    boolean mover() {
        return !building();
    }

    double dist(Body o) {
        return Math.hypot(o.x - x, o.z - z);
    }

    /** Distance entre les bords (rayons déduits). */
    double edge(Body o) {
        return dist(o) - r - o.r;
    }

    boolean canHit(Body o) {
        return o != this && !o.dead && o.side != side && (!o.fly || air) && (!towersOnly || o.building());
    }

    boolean inRange(Body o) {
        return edge(o) <= range + 0.05;
    }

    String label() {
        return card == null ? (main ? "Bastion" : "Tourelle") : card.nom;
    }
}
