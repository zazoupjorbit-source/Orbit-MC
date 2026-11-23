package fr.orbit.guns.gun;

import fr.orbit.guns.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Logique de tir, rechargement et munitions. */
public final class GunService {

    private final Plugin plugin;
    private final GunItem gunItem;
    private final String prefix;
    private final Random rng = new Random();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> reloading = ConcurrentHashMap.newKeySet();
    private final Set<UUID> firing = ConcurrentHashMap.newKeySet();
    private final Set<UUID> aiming = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> aimToggleAt = new ConcurrentHashMap<>();

    private static final float WALK_SPEED_DEFAULT = 0.2f;

    public GunService(Plugin plugin, GunItem gunItem, String prefix) {
        this.plugin = plugin;
        this.gunItem = gunItem;
        this.prefix = prefix;
    }

    public void forget(UUID id) { cooldowns.remove(id); reloading.remove(id); firing.remove(id); aimToggleAt.remove(id); }

    // ---------- visee (clic gauche = zoom, via la vitesse de marche -> FOV client) ----------

    public boolean isAiming(UUID id) { return aiming.contains(id); }

    /** Bascule la visee (debounce 400ms : le clic gauche peut spammer des events). */
    public void toggleAim(Player p, Gun gun) {
        long now = System.currentTimeMillis();
        Long last = aimToggleAt.get(p.getUniqueId());
        if (last != null && now - last < 400) return;
        aimToggleAt.put(p.getUniqueId(), now);
        if (aiming.contains(p.getUniqueId())) unaim(p);
        else aim(p, gun);
    }

    private void aim(Player p, Gun gun) {
        aiming.add(p.getUniqueId());
        p.setWalkSpeed(gun.aimZoom());
        p.playSound(p.getLocation(), "item.spyglass.use", 0.8f, 1.1f);
        p.sendActionBar(Text.of("&f🎯 &7Visee &8» &fclic gauche &7pour arreter"));
    }

    /** Sort de la visee et rend la vitesse normale (sans effet si le joueur ne visait pas). */
    public void unaim(Player p) {
        if (!aiming.remove(p.getUniqueId())) return;
        p.setWalkSpeed(WALK_SPEED_DEFAULT);
        p.playSound(p.getLocation(), "item.spyglass.stop_using", 0.8f, 1.0f);
    }

    /** Filet de securite : le walk speed PERSISTE dans le playerdata (crash/restart en pleine visee). */
    public void healWalkSpeed(Player p) {
        if (!aiming.contains(p.getUniqueId()) && p.getWalkSpeed() != WALK_SPEED_DEFAULT) {
            p.setWalkSpeed(WALK_SPEED_DEFAULT);
        }
    }

    /** A l'arret du plugin : rend leur vitesse a tous les viseurs. */
    public void unaimAll() {
        for (UUID id : aiming) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setWalkSpeed(WALK_SPEED_DEFAULT);
        }
        aiming.clear();
    }

    /** true pendant que l'arme applique ses degats (pour ne pas les confondre avec une melee). */
    public boolean isFiring(UUID id) { return firing.contains(id); }

    /** Applique des degats attribues au joueur en contournant le garde anti-melee (balles, grenades). */
    public void damageAs(Player shooter, org.bukkit.entity.LivingEntity target, double dmg) {
        UUID id = shooter.getUniqueId();
        boolean added = firing.add(id);
        try { target.damage(dmg, shooter); }
        finally { if (added) firing.remove(id); }
    }

    // ---------- tir ----------

    public void fire(Player p, Gun gun, ItemStack item) {
        UUID id = p.getUniqueId();
        if (reloading.contains(id)) return;
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(id);
        if (last != null && now - last < gun.fireCooldownMs()) return;

        int mag = gunItem.getMag(item);
        if (mag <= 0) {
            p.playSound(p.getLocation(), "block.dispenser.fail", 1f, 1.2f);
            p.sendActionBar(Text.of("&cChargeur vide ! &7Appuie sur &fF &7pour recharger"));
            return;
        }
        cooldowns.put(id, now);

        Location eye = p.getEyeLocation();
        double spreadDeg = gun.spread() * (aiming.contains(id) ? gun.aimSpreadMult() : 1.0);
        firing.add(id);
        try {
            for (int i = 0; i < gun.pellets(); i++) {
                Vector dir = spread(eye, spreadDeg);
                RayTraceResult ray = p.getWorld().rayTrace(eye, dir, gun.range(), FluidCollisionMode.NEVER, true, 0.25,
                        e -> e instanceof LivingEntity && !e.equals(p));
                Location end;
                if (ray != null) {
                    end = ray.getHitPosition().toLocation(p.getWorld());
                    if (ray.getHitEntity() instanceof LivingEntity le) {
                        double dmg = gun.damage();
                        if (ray.getHitPosition().getY() >= le.getEyeLocation().getY() - 0.25) dmg *= gun.headshotMult();
                        le.damage(dmg, p);
                        le.getWorld().spawnParticle(Particle.CRIT, end, 6, 0.1, 0.1, 0.1, 0.05);
                    }
                } else {
                    end = eye.clone().add(dir.clone().multiply(gun.range()));
                }
                tracer(eye, end);
            }
        } finally {
            firing.remove(id);
        }
        p.getWorld().playSound(eye, gun.sound(), 1f, 1f);
        eye.getWorld().spawnParticle(Particle.SMOKE, eye.clone().add(eye.getDirection().multiply(1.2)), 3, 0.02, 0.02, 0.02, 0.01);

        mag--;
        gunItem.setMag(item, mag);
        p.getInventory().setItemInMainHand(item);
        showAmmo(p, gun, mag);
    }

    private Vector spread(Location eye, double deg) {
        if (deg <= 0) return eye.getDirection();
        Location t = eye.clone();
        t.setYaw(t.getYaw() + (float) ((rng.nextDouble() - 0.5) * 2 * deg));
        t.setPitch(t.getPitch() + (float) ((rng.nextDouble() - 0.5) * 2 * deg));
        return t.getDirection();
    }

    private void tracer(Location from, Location to) {
        Vector diff = to.toVector().subtract(from.toVector());
        double len = diff.length();
        if (len < 0.1) return;
        Vector step = diff.normalize().multiply(0.6);
        Location pt = from.clone().add(from.getDirection().multiply(0.7));
        for (double d = 0.7; d < len; d += 0.6) {
            pt.add(step);
            pt.getWorld().spawnParticle(Particle.CRIT, pt, 1, 0, 0, 0, 0);
        }
    }

    // ---------- rechargement ----------

    public void reload(Player p, Gun gun, ItemStack item) {
        UUID id = p.getUniqueId();
        if (reloading.contains(id)) return;
        int mag = gunItem.getMag(item);
        if (mag >= gun.magazine()) {
            p.sendActionBar(Text.of("&7Chargeur deja plein"));
            return;
        }
        if (countAmmo(p, gun.ammoItem()) <= 0) {
            p.playSound(p.getLocation(), "block.dispenser.fail", 1f, 0.8f);
            p.sendActionBar(Text.of("&cPas de munitions ! &7(&f" + ammoName(gun) + "&7)"));
            return;
        }
        reloading.add(id);
        p.playSound(p.getLocation(), "block.piston.contract", 1f, 1.3f);
        p.sendActionBar(Text.of("&e⟳ Rechargement..."));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            reloading.remove(id);
            if (!p.isOnline()) return;
            ItemStack cur = p.getInventory().getItemInMainHand();
            if (!gunItem.isGun(cur) || !gun.id().equals(gunItem.gunId(cur))) return; // arme changee
            int curMag = gunItem.getMag(cur);
            int take = Math.min(gun.magazine() - curMag, countAmmo(p, gun.ammoItem()));
            if (take <= 0) return;
            removeAmmo(p, gun.ammoItem(), take);
            gunItem.setMag(cur, curMag + take);
            p.getInventory().setItemInMainHand(cur);
            p.playSound(p.getLocation(), "block.iron_trapdoor.close", 1f, 1.4f);
            showAmmo(p, gun, curMag + take);
        }, gun.reloadTicks());
    }

    // ---------- munitions / affichage ----------

    private int countAmmo(Player p, Material mat) {
        int c = 0;
        for (ItemStack is : p.getInventory().getContents()) {
            if (is != null && is.getType() == mat) c += is.getAmount();
        }
        return c;
    }

    private void removeAmmo(Player p, Material mat, int n) {
        int rem = n;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && rem > 0; i++) {
            ItemStack is = contents[i];
            if (is == null || is.getType() != mat) continue;
            int take = Math.min(rem, is.getAmount());
            is.setAmount(is.getAmount() - take);
            rem -= take;
            if (is.getAmount() <= 0) p.getInventory().setItem(i, null);
        }
    }

    public void showAmmo(Player p, Gun gun, int mag) {
        int reserve = countAmmo(p, gun.ammoItem());
        p.sendActionBar(Text.of(gun.display() + "  &e" + mag + "&7/" + gun.magazine()
                + "  &8| &7Munitions : &f" + reserve));
    }

    private String ammoName(Gun gun) {
        return gun.ammoItem().name().toLowerCase().replace('_', ' ');
    }
}
