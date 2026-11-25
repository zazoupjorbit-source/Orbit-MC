package fr.orbit.royale;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * État du joueur avant la partie (inventaire, mode de jeu, vol, vie, faim), rendu à la fin. Copié aussi dans
 * plugins/OrbitRoyale/etats/<uuid>.yml pendant la partie : si le serveur s'arrête net, il est rendu à la reconnexion.
 */
final class Saved {

    ItemStack[] contents;
    GameMode mode;
    boolean allowFlight, flying;
    double health;
    int food, heldSlot;
    float saturation, walkSpeed;
    Collection<PotionEffect> effects;

    static Saved of(Player p) {
        Saved s = new Saved();
        s.contents = p.getInventory().getContents().clone();
        for (int i = 0; i < s.contents.length; i++) {
            s.contents[i] = s.contents[i] == null ? null : s.contents[i].clone();
        }
        s.mode = p.getGameMode();
        s.allowFlight = p.getAllowFlight();
        s.flying = p.isFlying();
        s.health = p.getHealth();
        s.food = p.getFoodLevel();
        s.saturation = p.getSaturation();
        s.walkSpeed = p.getWalkSpeed();
        s.heldSlot = p.getInventory().getHeldItemSlot();
        s.effects = new ArrayList<>(p.getActivePotionEffects());
        return s;
    }

    void restore(Player p) {
        p.getInventory().clear();
        p.getInventory().setContents(contents);
        p.getInventory().setHeldItemSlot(heldSlot);
        p.setGameMode(mode);
        p.setAllowFlight(allowFlight);
        p.setFlying(allowFlight && flying);
        try {
            p.setHealth(Math.min(health, p.getMaxHealth()));
        } catch (IllegalArgumentException ignored) {
        }
        p.setFoodLevel(food);
        p.setSaturation(saturation);
        p.setWalkSpeed(walkSpeed);
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        p.addPotionEffects(effects);
        p.setFallDistance(0);
        p.updateInventory();
    }

    static File file(File folder, Player p) {
        return new File(new File(folder, "etats"), p.getUniqueId() + ".yml");
    }

    void write(File f) {
        try {
            f.getParentFile().mkdirs();
            YamlConfiguration y = new YamlConfiguration();
            List<ItemStack> list = new ArrayList<>();
            for (ItemStack it : contents) {
                list.add(it);
            }
            y.set("inventaire", list);
            y.set("mode", mode.name());
            y.set("vol", allowFlight);
            y.set("vitesse", (double) walkSpeed);
            y.save(f);
        } catch (Exception ignored) {
        }
    }

    /** Rend un état resté sur le disque (arrêt brutal pendant une partie). */
    @SuppressWarnings("unchecked")
    static boolean recover(File f, Player p) {
        if (!f.isFile()) {
            return false;
        }
        try {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            List<ItemStack> list = (List<ItemStack>) y.getList("inventaire", new ArrayList<>());
            p.getInventory().clear();
            p.getInventory().setContents(list.toArray(new ItemStack[0]));
            p.setGameMode(GameMode.valueOf(y.getString("mode", "ADVENTURE")));
            p.setAllowFlight(y.getBoolean("vol", false));
            p.setWalkSpeed((float) y.getDouble("vitesse", 0.2));
            p.updateInventory();
        } catch (Exception ignored) {
        }
        return f.delete();
    }

    static Location safe(Location l) {
        return l == null ? null : l.clone();
    }
}
