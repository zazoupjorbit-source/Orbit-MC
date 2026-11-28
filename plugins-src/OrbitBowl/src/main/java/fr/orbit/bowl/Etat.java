package fr.orbit.bowl;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * État d'un joueur avant une partie (mode de jeu, vol, inventaire, vie, taille, vitesse, saut…), rendu tel
 * quel à la fin, quoi qu'il arrive (fin normale, abandon, déconnexion, arrêt du serveur).
 */
public final class Etat {

    private enum Attr { SCALE, SPEED, JUMP, STEP }

    private static Attribute attr(Attr a) {
        return switch (a) {
            case SCALE -> Attribute.SCALE;
            case SPEED -> Attribute.MOVEMENT_SPEED;
            case JUMP -> Attribute.JUMP_STRENGTH;
            case STEP -> Attribute.STEP_HEIGHT;
        };
    }

    private final GameMode mode;
    private final boolean allowFlight, flying, collidable, invisible;
    private final ItemStack[] contents;
    private final int held, food, fire;
    private final double health;
    private final float saturation, walkSpeed;
    private final List<PotionEffect> effects;
    private final Map<Attr, Double> bases = new EnumMap<>(Attr.class);

    public Etat(Player p) {
        mode = p.getGameMode();
        allowFlight = p.getAllowFlight();
        flying = p.isFlying();
        collidable = p.isCollidable();
        invisible = p.isInvisible();
        contents = p.getInventory().getContents().clone();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                contents[i] = contents[i].clone();
            }
        }
        held = p.getInventory().getHeldItemSlot();
        food = p.getFoodLevel();
        fire = p.getFireTicks();
        health = p.getHealth();
        saturation = p.getSaturation();
        walkSpeed = p.getWalkSpeed();
        effects = new ArrayList<>(p.getActivePotionEffects());
        for (Attr a : Attr.values()) {
            AttributeInstance inst = p.getAttribute(attr(a));
            if (inst != null) {
                bases.put(a, inst.getBaseValue());
            }
        }
    }

    /** Prépare le joueur pour une partie : aventure, sans vol ni effets, inventaire vide, plein de vie. */
    public static void preparer(Player p) {
        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
        p.setGameMode(GameMode.ADVENTURE);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.getInventory().clear();
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setInvisible(false);
        p.setCollidable(true);
        p.setWalkSpeed(0.2f);
    }

    public static void base(Player p, Attribute a, double v) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst != null && Math.abs(inst.getBaseValue() - v) > 1e-4) {
            inst.setBaseValue(v);
        }
    }

    public void rendre(Player p) {
        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
        for (Map.Entry<Attr, Double> e : bases.entrySet()) {
            base(p, attr(e.getKey()), e.getValue());
        }
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        p.addPotionEffects(effects);
        p.setGameMode(mode);
        p.getInventory().setContents(contents);
        p.getInventory().setHeldItemSlot(held);
        p.setAllowFlight(allowFlight || mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR);
        p.setFlying(flying && p.getAllowFlight());
        p.setCollidable(collidable);
        p.setInvisible(invisible);
        p.setFoodLevel(food);
        p.setSaturation(saturation);
        p.setFireTicks(fire);
        p.setWalkSpeed(walkSpeed);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(Math.max(0.5, Math.min(health, max == null ? 20 : max.getValue())));
        p.setFallDistance(0);
        p.updateInventory();
    }
}
