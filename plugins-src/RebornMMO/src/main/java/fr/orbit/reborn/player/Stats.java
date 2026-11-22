package fr.orbit.reborn.player;

import fr.orbit.reborn.clazz.GameClass;

/** Formules d'équilibrage (PV, XP, monstres). */
public final class Stats {

    private Stats() {
    }

    public static double maxHp(PlayerData d) {
        GameClass c = d.gameClass();
        double hp = 200 + 45.0 * (d.level - 1) + 15.0 * d.vit;
        return Math.round(hp * (c == null ? 1 : c.hpMult));
    }

    public static long xpToNext(int level) {
        return Math.round(50 * Math.pow(level, 1.6) + 50);
    }

    public static double mobHp(int level) {
        return 30 + 14 * Math.pow(level, 1.25);
    }

    public static double mobDamage(int level) {
        return 8 + 5 * Math.pow(level, 1.15);
    }

    public static long mobXp(int level) {
        return Math.round(8 + 5 * Math.pow(level, 1.25));
    }

    public static long mobCol(int level) {
        return 4 + 2L * level;
    }
}
