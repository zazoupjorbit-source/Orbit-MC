package fr.orbit.extraction.gun;

import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.gun.GunRegistry.AmmoDef;
import fr.orbit.extraction.gun.GunRegistry.GunDef;
import fr.orbit.extraction.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

/** Tir (lancer de rayon), cadence, recul, rechargement et affichage des munitions. */
public final class GunService {

    private static final class Shooter {
        int nextShot;
        int triggerUntil = -1;
        int lastShot;
        double bloom;
        int lastDryClick = -100;
        int reloadStart;
        int reloadEnd = -1;
        int reloadSlot;
        String reloadUid;
    }

    /** Vitesse de marche vanilla : la visée la baisse pour resserrer le champ de vision (zoom côté client). */
    public static final float WALK_SPEED_DEFAULT = 0.2f;

    private final OrbitExtraction plugin;
    private final GunItems items;
    private final MagStore mags;
    private final Map<UUID, Shooter> shooters = new HashMap<>();
    private final Set<UUID> aiming = new HashSet<>();
    private final Map<UUID, Integer> aimToggledAt = new HashMap<>();
    private final Set<UUID> firing = new HashSet<>();
    private final Random random = new Random();

    public GunService(OrbitExtraction plugin, GunItems items, MagStore mags) {
        this.plugin = plugin;
        this.items = items;
        this.mags = mags;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void forget(Player p) {
        unaim(p);
        shooters.remove(p.getUniqueId());
        aimToggledAt.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------------ visée

    public boolean isAiming(Player p) {
        return aiming.contains(p.getUniqueId());
    }

    /** Clic gauche arme en main : bascule la visée (anti-rebond, le clic gauche envoie plusieurs événements). */
    public boolean toggleAim(Player p) {
        GunDef g = items.gunOf(p.getInventory().getItemInMainHand());
        if (g == null) {
            return false;
        }
        int now = Bukkit.getCurrentTick();
        Integer last = aimToggledAt.get(p.getUniqueId());
        if (last != null && now - last < 8) {
            return true;
        }
        aimToggledAt.put(p.getUniqueId(), now);
        if (aiming.contains(p.getUniqueId())) {
            unaim(p);
        } else {
            aiming.add(p.getUniqueId());
            p.setWalkSpeed(Math.max(-1f, Math.min(1f, g.aimZoom())));
            p.playSound(p.getLocation(), Sound.ITEM_SPYGLASS_USE, 0.8f, 1.1f);
        }
        return true;
    }

    public void unaim(Player p) {
        if (aiming.remove(p.getUniqueId())) {
            p.setWalkSpeed(WALK_SPEED_DEFAULT);
            p.playSound(p.getLocation(), Sound.ITEM_SPYGLASS_STOP_USING, 0.8f, 1f);
        }
    }

    /** La vitesse de marche est sauvegardée avec le joueur : on répare un zoom resté collé (crash en pleine visée). */
    public void healWalkSpeed(Player p) {
        if (!aiming.contains(p.getUniqueId()) && p.getWalkSpeed() != WALK_SPEED_DEFAULT) {
            p.setWalkSpeed(WALK_SPEED_DEFAULT);
        }
    }

    public void unaimAll() {
        for (UUID id : aiming) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setWalkSpeed(WALK_SPEED_DEFAULT);
            }
        }
        aiming.clear();
    }

    /** Vrai pendant qu'une balle ou une explosion applique ses dégâts (à ne pas confondre avec un coup de crosse). */
    public boolean isFiring(Player p) {
        return firing.contains(p.getUniqueId());
    }

    private void damageAs(Player shooter, LivingEntity target, double dmg) {
        target.setNoDamageTicks(0);
        if (shooter == null || !shooter.isOnline()) {
            target.damage(dmg);
            return;
        }
        boolean added = firing.add(shooter.getUniqueId());
        try {
            target.damage(dmg, shooter);
        } finally {
            if (added) {
                firing.remove(shooter.getUniqueId());
            }
        }
    }

    /** Explosion maison (dégâts dégressifs, pas de blocs détruits) : lance-grenades, BFG, grenades. */
    public void explode(Location at, double radius, double damage, Player source, double noise) {
        World w = at.getWorld();
        w.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1, 0, 0, 0, 0);
        w.spawnParticle(Particle.EXPLOSION, at, 12, radius * 0.35, 0.6, radius * 0.35, 0);
        w.spawnParticle(Particle.LARGE_SMOKE, at, 20, radius * 0.3, 0.5, radius * 0.3, 0.02);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, (float) Math.max(1.5, noise / 16.0), 0.9f);
        for (LivingEntity le : w.getNearbyLivingEntities(at, radius, radius, radius)) {
            if (le instanceof ArmorStand || le instanceof Player pl
                    && (pl.getGameMode() == GameMode.CREATIVE || pl.getGameMode() == GameMode.SPECTATOR)) {
                continue;
            }
            double dist = le.getLocation().add(0, le.getHeight() / 2, 0).distance(at);
            if (dist > radius) {
                continue;
            }
            damageAs(source, le, damage * (1.0 - 0.6 * (dist / radius)));
            Vector push = le.getLocation().toVector().subtract(at.toVector());
            if (push.lengthSquared() > 1e-4) {
                le.setVelocity(le.getVelocity().add(push.normalize().multiply(0.5).setY(0.35)));
            }
        }
        plugin.infected().noise(at, noise, source);
        plugin.vehicles().blast(at, radius, damage, source);
    }

    /** Dégâts attribués à un joueur sans passer pour un coup de crosse (véhicule qui écrase quelqu'un). */
    public void hurt(Player source, LivingEntity target, double dmg) {
        damageAs(source, target, dmg);
    }

    private Shooter st(Player p) {
        return shooters.computeIfAbsent(p.getUniqueId(), k -> new Shooter());
    }

    /** Clic droit avec une arme en main. Renvoie false si l'objet tenu n'est pas une arme. */
    public boolean trigger(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        GunDef g = items.gunOf(hand);
        if (g == null) {
            return false;
        }
        Shooter s = st(p);
        int now = Bukkit.getCurrentTick();
        if (g.auto()) {
            // Le client renvoie le clic droit toutes les 4 ticks tant qu'il est maintenu.
            s.triggerUntil = now + 5;
        }
        tryFire(p, g, hand, s, now);
        return true;
    }

    /** Touche F avec une arme en main. */
    public boolean reloadKey(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        GunDef g = items.gunOf(hand);
        if (g == null) {
            return false;
        }
        startReload(p, g, hand, st(p), Bukkit.getCurrentTick());
        return true;
    }

    private void tick() {
        int now = Bukkit.getCurrentTick();
        Iterator<Map.Entry<UUID, Shooter>> it = shooters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Shooter> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            Shooter s = e.getValue();
            ItemStack hand = p.getInventory().getItemInMainHand();
            GunDef g = items.gunOf(hand);
            if (g == null && aiming.contains(p.getUniqueId())) {
                unaim(p);
            }
            if (s.reloadEnd >= 0) {
                if (g == null || p.getInventory().getHeldItemSlot() != s.reloadSlot || !s.reloadUid.equals(items.uidOf(hand))) {
                    s.reloadEnd = -1;
                    Text.bar(p, "<#C0392B>Rechargement interrompu");
                } else if (now >= s.reloadEnd) {
                    finishReload(p, g, s);
                }
            }
            if (g != null && g.auto() && now <= s.triggerUntil) {
                tryFire(p, g, hand, s, now);
            }
        }
        if (now % 2 == 0) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                hud(p, now);
            }
        }
        if (now % 100 == 0) {
            // Munitions d'avant leur modèle 3D (inventaire, ou ressorties du coffre) : mises à jour, elles s'empilent avec les neuves.
            for (Player p : Bukkit.getOnlinePlayers()) {
                org.bukkit.inventory.PlayerInventory inv = p.getInventory();
                for (int i = 0; i < 41; i++) {       // 36 à 39 : armure portée, 40 : main gauche
                    ItemStack stack = inv.getItem(i);
                    if (stack == null) {
                        continue;
                    }
                    if (i < 36 && items.ensureAmmoModel(stack)) {
                        inv.setItem(i, stack);
                        continue;
                    }
                    // Armure vanilla d'avant les tenues : remplacée par la pièce de tenue du même matériau.
                    ItemStack tenue = plugin.armors().upgrade(stack);
                    if (tenue != null) {
                        inv.setItem(i, tenue);
                    }
                }
            }
        }
    }

    private void tryFire(Player p, GunDef g, ItemStack hand, Shooter s, int now) {
        if (s.reloadEnd >= 0 || now < s.nextShot || p.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        String uid = items.uidOf(hand);
        int mag = mags.get(uid);
        if (mag <= 0) {
            if (now - s.lastDryClick >= 8) {
                s.lastDryClick = now;
                p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.7f, 1.7f);
                if (items.reserve(p, g.ammo()) > 0) {
                    startReload(p, g, hand, s, now);
                }
            }
            s.triggerUntil = -1;
            return;
        }
        mags.set(uid, mag - 1);
        s.nextShot = now + g.interval();
        double bloom = Math.max(0, s.bloom - (now - s.lastShot) * 0.35);
        s.lastShot = now;
        double spread = (g.spread() + bloom) * stanceFactor(p) * (aiming.contains(p.getUniqueId()) ? g.aimSpread() : 1.0);
        s.bloom = Math.min(g.bloomMax(), bloom + g.bloom());
        shoot(p, g, spread);
    }

    private static double stanceFactor(Player p) {
        if (!p.isOnGround()) {
            return 2.5;
        }
        if (p.isSprinting()) {
            return 1.8;
        }
        return p.isSneaking() ? 0.7 : 1.0;
    }

    private void shoot(Player p, GunDef g, double spreadDeg) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector base = eye.getDirection();
        Predicate<Entity> filter = e -> e != p && e instanceof LivingEntity le && le.isValid() && !(e instanceof ArmorStand)
                && !(e instanceof Player pl && (pl.getGameMode() == GameMode.SPECTATOR || pl.getGameMode() == GameMode.CREATIVE));

        Map<LivingEntity, double[]> hits = new LinkedHashMap<>();
        for (int i = 0; i < g.pellets(); i++) {
            Vector dir = spread(base, spreadDeg);
            RayTraceResult r = w.rayTrace(eye, dir, g.range(), FluidCollisionMode.NEVER, true, 0.1, filter);
            Vector end = r == null ? eye.toVector().add(dir.clone().multiply(g.range())) : r.getHitPosition();
            // Carrosserie sur la trajectoire : le véhicule encaisse ; la balle s'y arrête, ou blesse moins l'occupant visé.
            double through = 1;
            fr.orbit.extraction.vehicle.Vehicles.Impact vi = plugin.vehicles().bullet(p, eye, dir,
                    r == null ? g.range() : eye.toVector().distance(end), g.damage(), r == null ? null : r.getHitEntity());
            if (vi != null) {
                if (vi.stopped()) {
                    end = vi.point();
                    r = null;
                } else {
                    through = vi.factor();
                }
            }
            tracer(w, eye.toVector(), end, dir);
            if (g.explosive()) {
                // Recule un peu du point d'impact : l'explosion ne doit pas partir de l'intérieur d'un bloc.
                Location blast = end.clone().subtract(dir.clone().multiply(0.4)).toLocation(w);
                explode(blast, g.blastRadius(), g.blastDamage(), p, g.noise());
            }
            if (r == null) {
                continue;
            }
            if (r.getHitEntity() instanceof LivingEntity raw) {
                LivingEntity le = plugin.infected().hostOf(raw);
                boolean head = end.getY() >= le.getEyeLocation().getY() - 0.3 * scale(le);
                double[] acc = hits.computeIfAbsent(le, k -> new double[2]);
                acc[0] += g.damage() * (head ? g.headshot() : 1) * through;
                if (head) {
                    acc[1] = 1;
                }
                w.spawnParticle(Particle.BLOCK, end.toLocation(w), 6, 0.08, 0.08, 0.08, 0, Material.REDSTONE_BLOCK.createBlockData());
            } else if (r.getHitBlock() != null) {
                Block b = r.getHitBlock();
                w.spawnParticle(Particle.BLOCK, end.toLocation(w), 5, 0.05, 0.05, 0.05, 0, b.getBlockData());
                w.spawnParticle(Particle.SMOKE, end.toLocation(w), 1, 0, 0, 0, 0.01);
            }
        }

        boolean anyHead = false;
        for (Map.Entry<LivingEntity, double[]> e : hits.entrySet()) {
            damageAs(p, e.getKey(), e.getValue()[0]);
            anyHead |= e.getValue()[1] > 0;
        }
        if (!hits.isEmpty()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.5f, anyHead ? 1.8f : 1.1f);
        }

        Location muzzle = eye.clone().add(base.clone().multiply(0.9)).add(0, -0.15, 0);
        w.spawnParticle(Particle.SMOKE, muzzle, 2, 0.02, 0.02, 0.02, 0.01);
        w.spawnParticle(Particle.ELECTRIC_SPARK, muzzle, 3, 0.03, 0.03, 0.03, 0.02);
        float volume = (float) Math.max(1.0, g.noise() / 16.0);
        w.playSound(eye, g.sound(), SoundCategory.PLAYERS, volume, g.pitch());
        plugin.infected().noise(p.getLocation(), g.noise(), p);
    }

    private static double scale(LivingEntity le) {
        return le.getHeight() / 1.8;
    }

    private Vector spread(Vector base, double deg) {
        if (deg <= 0) {
            return base.clone();
        }
        double max = Math.tan(Math.toRadians(deg));
        Vector off = new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize()
                .multiply(max * Math.sqrt(random.nextDouble()));
        return base.clone().add(off).normalize();
    }

    private static void tracer(World w, Vector from, Vector to, Vector dir) {
        double len = from.distance(to);
        int points = (int) Math.min(30, len / 1.2);
        for (int i = 1; i <= points; i++) {
            double d = 1.5 + i * 1.2;
            if (d >= len) {
                break;
            }
            Vector v = from.clone().add(dir.clone().multiply(d));
            w.spawnParticle(Particle.CRIT, v.getX(), v.getY(), v.getZ(), 1, 0, 0, 0, 0);
        }
    }

    private void startReload(Player p, GunDef g, ItemStack hand, Shooter s, int now) {
        if (s.reloadEnd >= 0) {
            return;
        }
        String uid = items.uidOf(hand);
        if (mags.get(uid) >= g.mag()) {
            return;
        }
        if (items.reserve(p, g.ammo()) <= 0) {
            AmmoDef a = plugin.guns().ammo(g.ammo());
            Text.bar(p, "<#C0392B>Plus de munitions</#C0392B> <#5A6270>(" + (a == null ? g.ammo() : a.name()) + "<#5A6270>)");
            return;
        }
        s.reloadStart = now;
        s.reloadEnd = now + g.reloadTicks();
        s.reloadSlot = p.getInventory().getHeldItemSlot();
        s.reloadUid = uid;
        s.triggerUntil = -1;
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.6f, 1.6f);
    }

    private void finishReload(Player p, GunDef g, Shooter s) {
        s.reloadEnd = -1;
        int mag = mags.get(s.reloadUid);
        int taken = items.takeAmmo(p, g.ammo(), g.mag() - mag);
        mags.set(s.reloadUid, mag + taken);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.7f, 1.8f);
    }

    /**
     * Arme en main pour la console du HUD.
     *
     * @param reload avancement du rechargement (0 à 1), -1 s'il n'y en a pas
     */
    public record GunStatus(String name, int mag, int magMax, int reserve, String ammo, double reload, double reloadLeft, boolean aiming) {
    }

    /** Arme tenue en main, null si le joueur ne tient pas d'arme à feu. */
    public GunStatus status(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        GunDef g = items.gunOf(hand);
        if (g == null) {
            return null;
        }
        AmmoDef a = plugin.guns().ammo(g.ammo());
        Shooter s = shooters.get(p.getUniqueId());
        double reload = -1, left = 0;
        if (s != null && s.reloadEnd >= 0) {
            int now = Bukkit.getCurrentTick();
            reload = Math.max(0, Math.min(1, (now - s.reloadStart) / (double) Math.max(1, s.reloadEnd - s.reloadStart)));
            left = Math.max(0, (s.reloadEnd - now) / 20.0);
        }
        return new GunStatus(g.name(), mags.get(items.uidOf(hand)), g.mag(), items.reserve(p, g.ammo()),
                a == null ? g.ammo() : a.name(), reload, left, aiming.contains(p.getUniqueId()));
    }

    private void hud(Player p, int now) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        GunDef g = items.gunOf(hand);
        if (g == null) {
            return;
        }
        if (items.ensureModel(hand, g)) {
            p.getInventory().setItemInMainHand(hand);
        }
        if (plugin.raid().isExtracting(p) || plugin.hud().drawn(p)) {
            return;    // avec le pack, les munitions sont dans la console (hud/Hud.java)
        }
        Shooter s = shooters.get(p.getUniqueId());
        if (s != null && s.reloadEnd >= 0) {
            double ratio = (now - s.reloadStart) / (double) Math.max(1, s.reloadEnd - s.reloadStart);
            Text.bar(p, "<#D9C27A>Rechargement</#D9C27A>  " + Text.gauge(ratio, 12, "#D9C27A", "#3A3F47"));
            return;
        }
        int mag = mags.get(items.uidOf(hand));
        String color = mag == 0 ? "#C0392B" : mag <= g.mag() / 4 ? "#E67E22" : "#FFFFFF";
        Text.bar(p, (aiming.contains(p.getUniqueId()) ? "<#D7DCE2>🎯</#D7DCE2> " : "") + g.name() + "   <" + color + "><b>" + mag + "</b></" + color + "><#8A919B> / " + g.mag()
                + "   <#5A6270>|</#5A6270>   <#8A919B>réserve <#D7DCE2>" + items.reserve(p, g.ammo()));
    }
}
