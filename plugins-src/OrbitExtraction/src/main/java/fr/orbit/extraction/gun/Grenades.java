package fr.orbit.extraction.gun;

import fr.orbit.extraction.Keys;
import fr.orbit.extraction.gun.GunRegistry.GrenadeDef;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Grenades (reprises d'OrbitGuns) : lancer au clic droit, mèche, explosion maison.
 * En PvPvE elles blessent aussi les joueurs, et leur détonation attire les infestés.
 */
public final class Grenades implements Listener {

    private final Plugin plugin;
    private final GunRegistry reg;
    private final GunItems items;
    private final GunService guns;
    private final Map<UUID, Long> lastThrow = new HashMap<>();

    public Grenades(Plugin plugin, GunRegistry reg, GunItems items, GunService guns) {
        this.plugin = plugin;
        this.reg = reg;
        this.items = items;
        this.guns = guns;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = e.getItem();
        GrenadeDef d = items.grenadeOf(item);
        if (d == null) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastThrow.get(p.getUniqueId());
        if (last != null && now - last < 700) {
            return;
        }
        lastThrow.put(p.getUniqueId(), now);

        item.setAmount(item.getAmount() - 1);
        p.getInventory().setItemInMainHand(item.getAmount() <= 0 ? null : item);
        Snowball ball = p.launchProjectile(Snowball.class, p.getEyeLocation().getDirection().multiply(d.speed()));
        ball.setItem(items.grenade(d, 1));
        ball.getPersistentDataContainer().set(Keys.GRENADE, PersistentDataType.STRING, d.id());
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 0.7f);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball ball)) {
            return;
        }
        GrenadeDef d = reg.grenade(ball.getPersistentDataContainer().get(Keys.GRENADE, PersistentDataType.STRING));
        if (d == null) {
            return;
        }
        Location loc = ball.getLocation();
        if (e.getHitBlockFace() != null) {
            loc.add(e.getHitBlockFace().getDirection().multiply(0.3));
        }
        Player thrower = ball.getShooter() instanceof Player pl ? pl : null;
        if (d.fuseTicks() <= 0) {
            guns.explode(loc, d.radius(), d.damage(), thrower, d.noise());
            return;
        }
        // La grenade reste au sol (non ramassable) le temps de la mèche.
        Item rest = loc.getWorld().dropItem(loc, items.grenade(d, 1));
        rest.setPickupDelay(Short.MAX_VALUE);
        rest.setCanMobPickup(false);
        rest.setWillAge(false);
        rest.setVelocity(new Vector(0, 0.05, 0));
        loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_HAT, 0.9f, 1.6f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Location at = rest.isValid() ? rest.getLocation().add(0, 0.3, 0) : loc;
            rest.remove();
            guns.explode(at, d.radius(), d.damage(), thrower, d.noise());
        }, d.fuseTicks());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastThrow.remove(e.getPlayer().getUniqueId());
    }
}
