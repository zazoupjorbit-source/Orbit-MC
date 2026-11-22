package fr.orbit.mmo.player;

/** Formules d'équilibrage (PV, XP, monstres, or). */
public final class Stats {

    private Stats() {
    }

    public static double maxHp(PlayerData d) {
        double hp = 200 + 45.0 * (d.level - 1);
        return Math.round(hp * d.hpMult * (1 + d.mods.get("pv") / 100.0));
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

    /** Pièces d'or lâchées par un monstre. */
    public static long mobGold(int level) {
        return 4 + 2L * level;
    }
}
