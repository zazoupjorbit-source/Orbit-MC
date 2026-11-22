package fr.orbit.mmo.combat;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.classes.Faction;
import fr.orbit.mmo.classes.SpecDef;
import fr.orbit.mmo.clazz.WeaponType;
import fr.orbit.mmo.mob.MobState;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.player.Stats;
import fr.orbit.mmo.spell.SpellBar;
import fr.orbit.mmo.util.Fx;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Attaque de base des lanceurs de sorts (baguette, bâton, grimoire) : un trait magique tiré au clic gauche,
 * jamais un coup de mêlée. Le coup vanilla avec ces armes est annulé avant {@link CombatListener}.
 * Dégâts : la même formule que l'attaque de base ({@link Combat#autoAttack}), avec un coefficient par arme.
 */
public final class CasterAttack implements Listener {

    private static final double RANGE = 22;
    private static final double STEP = 0.5;
    private static final int STEPS_PER_TICK = 4; // 2 blocs par tick
    private static final double HEAL_RADIUS = 15;

    /** Cadence (ms), coefficient de dégâts, part des dégâts autour de l'impact, part rendue en soin à un allié. */
    private record Profile(long delayMs, double coef, double splash, double splashRadius, double heal, float size) {
    }

    private static Profile profile(WeaponType t) {
        return switch (t) {
            case WAND -> new Profile(600, 0.75, 0, 0, 0, 1.0f);
            case STAFF -> new Profile(1100, 1.3, 0.5, 2.0, 0, 1.6f);
            case GRIMOIRE -> new Profile(800, 0.95, 0, 0, 0.3, 1.2f);
            default -> null;
        };
    }

    private final OrbitMMO plugin;
    private final Map<UUID, Long> ready = new HashMap<>();

    public CasterAttack(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    private static WeaponType magicWeapon(ItemStack it) {
        if (!SpellBar.isWeapon(it)) {
            return null;
        }
        WeaponType t = WeaponType.of(it);
        return t.magic() ? t : null;
    }

    // ------------------------------------------------------------------ Déclencheurs

    // Pas d'ignoreCancelled : Paper envoie le clic gauche dans le vide déjà « annulé ».
    @EventHandler(priority = EventPriority.LOW)
    public void onSwing(PlayerInteractEvent e) {
        Action a = e.getAction();
        if ((a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) || e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = e.getPlayer();
        WeaponType t = magicWeapon(p.getInventory().getItemInMainHand());
        if (t == null) {
            return;
        }
        if (a == Action.LEFT_CLICK_BLOCK) {
            e.setCancelled(true);
        }
        fire(p, t);
    }

    /** Clic gauche sur une cible avec une arme magique : pas de coup de bâton, le trait part vers elle. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) {
            return;
        }
        WeaponType t = magicWeapon(p.getInventory().getItemInMainHand());
        if (t == null) {
            return;
        }
        e.setCancelled(true);
        fire(p, t);
    }

    /** La baguette (bâton de l'End) ne se pose jamais comme un bloc. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (magicWeapon(e.getItemInHand()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        ready.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ Le trait

    private void fire(Player p, WeaponType type) {
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        if (d == null || d.buildMode || !plugin.progression().valid(d) || d.hp <= 0 || d.cast != null
                || (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE)) {
            return;
        }
        Profile pr = profile(type);
        long now = System.currentTimeMillis();
        Long next = ready.get(p.getUniqueId());
        if (next != null && now < next) {
            return; // trop tôt : le clic est ignoré
        }
        ready.put(p.getUniqueId(), now + pr.delayMs());

        Style st = style(d);
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location start = eye.clone().subtract(0, 0.2, 0).add(dir.clone().multiply(0.4));
        World w = p.getWorld();
        launchSound(w, start, type);
        Particle.DustOptions dust = new Particle.DustOptions(st.color, pr.size());
        Particle.DustOptions core = new Particle.DustOptions(Color.WHITE, pr.size() * 0.5f);
        UUID id = p.getUniqueId();

        new BukkitRunnable() {
            final Location pos = start.clone();
            double traveled;
            int step;

            @Override
            public void run() {
                Player shooter = Bukkit.getPlayer(id);
                if (shooter == null || !shooter.isOnline() || !shooter.getWorld().equals(pos.getWorld())) {
                    cancel();
                    return;
                }
                for (int i = 0; i < STEPS_PER_TICK; i++) {
                    pos.add(dir.clone().multiply(STEP));
                    traveled += STEP;
                    step++;
                    w.spawnParticle(Particle.DUST, pos, 1, 0.02, 0.02, 0.02, 0, dust);
                    if (step % 2 == 0) {
                        w.spawnParticle(Particle.DUST, pos, 1, 0, 0, 0, 0, core);
                    }
                    if (step % 3 == 0) {
                        spawnSafe(w, st.trail, pos, 1, 0.05, 0.01);
                    }
                    LivingEntity target = targetAt(shooter, pos);
                    if (target != null) {
                        impact(shooter, type, pr, st, pos, target);
                        cancel();
                        return;
                    }
                    Block b = pos.getBlock();
                    if (!b.isPassable() || traveled >= RANGE) {
                        if (!b.isPassable()) {
                            impact(shooter, type, pr, st, pos, null);
                        } else {
                            spawnSafe(w, st.trail, pos, 4, 0.15, 0.02); // le trait s'éteint
                        }
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Première cible valide dont la boîte (légèrement élargie) contient le point. */
    private LivingEntity targetAt(Player shooter, Location pos) {
        Vector v = pos.toVector();
        for (Entity e : pos.getWorld().getNearbyEntities(pos, 1.5, 2.0, 1.5)) {
            if (e instanceof LivingEntity le && e != shooter && le.getBoundingBox().expand(0.3).contains(v)
                    && plugin.combat().isValidTarget(shooter, le)) {
                return le;
            }
        }
        return null;
    }

    private void impact(Player p, WeaponType type, Profile pr, Style st, Location pos, LivingEntity target) {
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        World w = pos.getWorld();
        Fx.dust(pos, st.color, pr.size() + 0.3f, 10, 0.18);
        spawnSafe(w, st.trail, pos, 6, 0.2, 0.04);
        if (d == null || !plugin.progression().valid(d)) {
            return;
        }
        if (target == null && pr.splash() <= 0) {
            w.playSound(pos, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.5f, 1.6f);
            return;
        }
        double dmg = plugin.combat().autoAttack(p, d, pr.coef());
        if (target != null) {
            hit(p, target, dmg);
            plugin.gear().wearWeapon(p);
            w.playSound(pos, type == WeaponType.STAFF ? Sound.ENTITY_GENERIC_EXPLODE : Sound.BLOCK_AMETHYST_BLOCK_RESONATE,
                    type == WeaponType.STAFF ? 0.35f : 0.8f, type == WeaponType.STAFF ? 1.6f : 1.4f);
        }
        if (pr.splash() > 0) {
            Fx.ring(pos, pr.splashRadius() * 0.6, st.color, 1.2f);
            if (target == null) {
                w.playSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 0.3f, 1.7f);
            }
            for (Entity e : w.getNearbyEntities(pos, pr.splashRadius(), pr.splashRadius(), pr.splashRadius())) {
                if (e instanceof LivingEntity le && le != target && e != p && plugin.combat().isValidTarget(p, le)
                        && le.getLocation().add(0, le.getHeight() / 2, 0).distanceSquared(pos) <= pr.splashRadius() * pr.splashRadius() * 2.25) {
                    hit(p, le, dmg * pr.splash());
                }
            }
        }
        if (target != null && pr.heal() > 0) {
            healAlly(p, d, dmg * pr.heal(), st);
        }
    }

    private void hit(Player p, LivingEntity target, double dmg) {
        MobState ms = plugin.mobs().state(target);
        if (ms != null) {
            plugin.combat().damageMob(p, ms, dmg);
        } else if (target instanceof Player v) {
            plugin.combat().damagePlayer(v, dmg, p, false);
        }
        plugin.combat().afterBasicHit(p, plugin.players().loaded(p.getUniqueId()), target, dmg);
    }

    /** Grimoire : l'allié le plus blessé à portée (le lanceur compris) récupère une part des dégâts. */
    private void healAlly(Player p, PlayerData d, double amount, Style st) {
        Player best = null;
        PlayerData bestData = null;
        double bestRatio = 1.0;
        for (Player o : p.getWorld().getPlayers()) {
            if (o.getLocation().distanceSquared(p.getLocation()) > HEAL_RADIUS * HEAL_RADIUS || (o != p && !plugin.combat().isAlly(p, o))) {
                continue;
            }
            PlayerData od = plugin.players().loaded(o.getUniqueId());
            if (od == null || !plugin.progression().valid(od) || od.hp <= 0) {
                continue;
            }
            double ratio = od.hp / Math.max(1, Stats.maxHp(od));
            if (ratio < bestRatio) {
                bestRatio = ratio;
                best = o;
                bestData = od;
            }
        }
        if (best == null) {
            return;
        }
        plugin.combat().heal(best, bestData, amount * d.mods.mult("soins", null));
        Location at = best.getLocation().add(0, best.getHeight() * 0.6, 0);
        Fx.dust(at, st.color, 1.0f, 8, 0.3);
        spawnSafe(best.getWorld(), Particle.HEART, at.add(0, 0.6, 0), 1, 0.1, 0);
    }

    // ------------------------------------------------------------------ Apparence

    private record Style(Color color, Particle trail) {
    }

    /** Couleur de la spécialisation ; particule assortie à son thème. */
    private Style style(PlayerData d) {
        SpecDef spec = plugin.progression().spec(d);
        Color color;
        try {
            color = Fx.color(spec != null ? spec.hex : d.faction == Faction.OMBRE ? "#9A5CFF" : "#FFE08A");
        } catch (RuntimeException ex) {
            color = Color.fromRGB(0xB98CFF);
        }
        String id = spec == null ? "" : spec.id;
        Particle trail = switch (id) {
            case "pyromancien" -> Particle.SMALL_FLAME;
            case "foudroyeur", "chatiment" -> Particle.ELECTRIC_SPARK;
            case "necromancien", "ames" -> Particle.SOUL;
            case "pestilence" -> Particle.MYCELIUM;
            case "maledictions" -> Particle.WITCH;
            case "sacre", "cosmos" -> Particle.END_ROD;
            case "sylvestre" -> Particle.HAPPY_VILLAGER;
            default -> Particle.ENCHANT;
        };
        return new Style(color, trail);
    }

    private static void launchSound(World w, Location l, WeaponType type) {
        switch (type) {
            case WAND -> w.playSound(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.7f);
            case STAFF -> w.playSound(l, Sound.ENTITY_BLAZE_SHOOT, 0.45f, 1.5f);
            case GRIMOIRE -> {
                w.playSound(l, Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
                w.playSound(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.3f);
            }
            default -> {
            }
        }
    }

    /** Particule sans donnée seulement (certaines en exigent une depuis Minecraft 26 : on les saute). */
    private static void spawnSafe(World w, Particle particle, Location l, int count, double spread, double speed) {
        if (particle.getDataType() != Void.class) {
            return;
        }
        w.spawnParticle(particle, l, count, spread, spread, spread, speed);
    }
}
