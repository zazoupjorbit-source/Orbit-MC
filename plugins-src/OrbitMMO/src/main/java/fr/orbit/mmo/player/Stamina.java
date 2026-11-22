package fr.orbit.mmo.player;

import fr.orbit.mmo.OrbitMMO;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Endurance (0–100) : la course la consomme hors des zones sûres et elle remonte dès qu'on s'arrête de courir.
 * À zéro, le personnage est essoufflé : plus de course jusqu'à {@link #RECOVER}. Le blocage passe par la barre
 * de faim du jeu (6 ou moins = course impossible), qui sert aussi de jauge d'endurance sans le pack.
 */
public final class Stamina {

    public static final double MAX = 100;
    /** Points perdus par seconde de course (10 s de course depuis le plein). */
    private static final double DRAIN = 10;
    /** Points regagnés par seconde, {@link #DELAY_MS} après la dernière course. */
    private static final double REGEN = 22;
    private static final long DELAY_MS = 800;
    /** Essoufflé : la course revient à ce seuil. */
    private static final double RECOVER = 30;

    private Stamina() {
    }

    /** Appelé toutes les {@code dt} secondes (HUD) pour chaque personnage valide. */
    public static void tick(OrbitMMO plugin, Player p, PlayerData d, double dt) {
        long now = System.currentTimeMillis();
        boolean free = d.buildMode || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR
                || p.isFlying() || plugin.floors().isSafe(p.getLocation());
        if (p.isSprinting() && !free) {
            d.stamina = Math.max(0, d.stamina - DRAIN * dt);
            d.staminaUsed = now;
            if (d.stamina <= 0 && !d.winded) {
                d.winded = true;
                p.setSprinting(false);
                plugin.hud().flash(p, "<#FF7A45>Essoufflé <#5A6270>· <#8C96A3>la course revient dans un instant", 40);
            }
        } else if (now - d.staminaUsed >= DELAY_MS) {
            d.stamina = Math.min(MAX, d.stamina + REGEN * dt);
        }
        if (d.winded && d.stamina >= RECOVER) {
            d.winded = false;
        }
        int food = food(d);
        if (p.getFoodLevel() != food) {
            p.setFoodLevel(food);
        }
        if (p.getSaturation() < 5) {
            p.setSaturation(20);
        }
        p.setExhaustion(0);
    }

    /** Niveau de faim du jeu correspondant à l'endurance (6 = essoufflé : la course est bloquée). */
    public static int food(PlayerData d) {
        if (d.winded) {
            return 6;
        }
        return d.stamina >= MAX - 0.5 ? 20 : 7 + (int) Math.round(d.stamina / MAX * 13);
    }

    public static void reset(PlayerData d) {
        d.stamina = MAX;
        d.winded = false;
        d.staminaUsed = 0;
    }
}
