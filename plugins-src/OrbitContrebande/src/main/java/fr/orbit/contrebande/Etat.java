package fr.orbit.contrebande;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * État d'un joueur avant une partie (mode de jeu, vol, inventaire, vie, faim, effets), rendu tel quel à la fin.
 * L'inventaire et le mode de jeu sont aussi écrits sur le disque au lancement : après un arrêt brutal du serveur,
 * le joueur les retrouve à sa reconnexion ({@link #restaurerFichier}).
 */
final class Etat {

    private final GameMode mode;
    private final boolean allowFlight, flying;
    private final ItemStack[] contents;
    private final int held, food;
    private final double health;
    private final float saturation, walkSpeed;
    private final List<PotionEffect> effects;

    Etat(Player p) {
        mode = p.getGameMode();
        allowFlight = p.getAllowFlight();
        flying = p.isFlying();
        contents = p.getInventory().getContents().clone();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                contents[i] = contents[i].clone();
            }
        }
        held = p.getInventory().getHeldItemSlot();
        food = p.getFoodLevel();
        health = p.getHealth();
        saturation = p.getSaturation();
        walkSpeed = p.getWalkSpeed();
        effects = new ArrayList<>(p.getActivePotionEffects());
    }

    static File fichier(OrbitContrebande plugin, Player p) {
        return new File(new File(plugin.getDataFolder(), "sauvegardes"), p.getUniqueId() + ".yml");
    }

    /** Copie de secours sur le disque (inventaire + mode de jeu). */
    void ecrire(OrbitContrebande plugin, Player p) {
        File f = fichier(plugin, p);
        f.getParentFile().mkdirs();
        YamlConfiguration y = new YamlConfiguration();
        y.set("nom", p.getName());
        y.set("mode", mode.name());
        y.set("contenu", Arrays.asList(contents));
        try {
            y.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("Copie de secours de l'inventaire de " + p.getName() + " impossible : " + e);
        }
    }

    /** Après un arrêt brutal : rend l'inventaire et le mode de jeu écrits au lancement. Retourne vrai s'il y en avait. */
    static boolean restaurerFichier(OrbitContrebande plugin, Player p) {
        File f = fichier(plugin, p);
        if (!f.exists()) {
            return false;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        List<?> list = y.getList("contenu", List.of());
        ItemStack[] items = new ItemStack[p.getInventory().getSize()];
        for (int i = 0; i < Math.min(items.length, list.size()); i++) {
            items[i] = list.get(i) instanceof ItemStack it ? it : null;
        }
        p.getInventory().setContents(items);
        try {
            p.setGameMode(GameMode.valueOf(y.getString("mode", "ADVENTURE")));
        } catch (IllegalArgumentException ignored) {
            p.setGameMode(GameMode.ADVENTURE);
        }
        p.setWalkSpeed(0.2f);
        p.updateInventory();
        f.delete();
        return true;
    }

    /** Prépare le joueur : aventure, sans vol ni effets, inventaire vide, plein de vie. */
    static void preparer(Player p) {
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
        p.setWalkSpeed(0.2f);
    }

    void rendre(OrbitContrebande plugin, Player p) {
        if (p.isInsideVehicle()) {
            p.leaveVehicle();
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
        p.setFoodLevel(food);
        p.setSaturation(saturation);
        p.setWalkSpeed(walkSpeed);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(Math.max(0.5, Math.min(health, max == null ? 20 : max.getValue())));
        p.setFallDistance(0);
        p.updateInventory();
        fichier(plugin, p).delete();
    }
}
