package fr.orbit.reborn.boss;

import fr.orbit.reborn.RebornMMO;
import fr.orbit.reborn.mob.MobState;
import fr.orbit.reborn.player.PlayerData;
import fr.orbit.reborn.registry.Defs.BossDef;
import fr.orbit.reborn.registry.Defs.MobDef;
import fr.orbit.reborn.util.Fx;
import fr.orbit.reborn.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Un combat de boss d'étage : plusieurs barres de vie, renforts, changement d'arme sur la dernière barre. */
final class BossFight {

    private static final Color DANGER = Color.fromRGB(0xFF3B3B);

    final int floor;
    final BossDef def;
    final MobState boss;
    final Location center;
    private final RebornMMO plugin;
    private final BossBar bar;
    private final Set<UUID> viewers = new HashSet<>();
    private final List<MobState> adds = new ArrayList<>();
    private final Random random = new Random();
    private int barsLeft;
    private boolean phase2;
    private long nextAbility;
    private long emptySince;

    BossFight(RebornMMO plugin, int floor, BossDef def, MobState boss, Location center) {
        this.plugin = plugin;
        this.floor = floor;
        this.def = def;
        this.boss = boss;
        this.center = center;
        this.barsLeft = def.bars();
        this.bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        this.nextAbility = System.currentTimeMillis() + 6000;
        updateBar();
    }

    void begin(List<Player> inside) {
        for (Player p : inside) {
            Text.title(p, "<#FF3B3B><b>⚔ BOSS D'ÉTAGE ⚔</b>", "<#D7DCE2>" + Text.esc(def.name()), 5, 50, 15);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 0.9f);
        }
        spawnAdds(2);
    }

    /** @return false quand le combat est terminé (réinitialisé). */
    boolean tick() {
        if (boss.dead || !boss.entity.isValid()) {
            cleanup();
            return false;
        }
        long now = System.currentTimeMillis();
        double arenaR = plugin.floors().arenaRadius();

        // Spectateurs de la barre du boss.
        Set<UUID> near = new HashSet<>();
        boolean fighter = false;
        for (Player p : center.getWorld().getPlayers()) {
            double d = horizontal(p.getLocation(), center);
            if (d <= arenaR + 14) {
                near.add(p.getUniqueId());
                if (!viewers.contains(p.getUniqueId())) {
                    p.showBossBar(bar);
                }
            }
            PlayerData pd = plugin.players().loaded(p.getUniqueId());
            if (d <= arenaR + 4 && !p.isDead() && pd != null && pd.classId != null
                    && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)) {
                fighter = true;
            }
        }
        for (UUID id : viewers) {
            if (!near.contains(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.hideBossBar(bar);
                }
            }
        }
        viewers.clear();
        viewers.addAll(near);

        // Plus personne dans la salle : le boss se réinitialise.
        if (!fighter) {
            if (emptySince == 0) {
                emptySince = now;
            } else if (now - emptySince > 15000) {
                plugin.mobs().remove(boss);
                cleanup();
                return false;
            }
        } else {
            emptySince = 0;
        }

        // Laisse : le boss ne quitte pas sa salle.
        if (horizontal(boss.entity.getLocation(), center) > arenaR - 1) {
            boss.entity.teleport(center);
        }
        adds.removeIf(a -> a.dead || !a.entity.isValid());

        if (fighter && now >= nextAbility) {
            nextAbility = now + 6000 + random.nextInt(3000);
            if (random.nextBoolean()) {
                slam();
            } else {
                charge();
            }
        }
        return true;
    }

    void onDamaged() {
        double per = boss.maxHp / def.bars();
        int left = Math.max(1, (int) Math.ceil(boss.hp / per));
        if (left < barsLeft) {
            barsLeft = left;
            for (UUID id : viewers) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    Text.msg(p, "<#FF8A3B>Une barre de vie de <b>" + Text.esc(def.name()) + "</b> s'effondre ! <#8C96A3>(" + barsLeft + " restante" + (barsLeft > 1 ? "s" : "") + ")");
                }
            }
            boss.entity.getWorld().playSound(boss.entity.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 0.7f);
            if (left == 1 && !phase2) {
                phase2();
            } else {
                spawnAdds(def.addsPerBar());
            }
        }
        updateBar();
    }

    /** Dernière barre : le boss jette son arme et en dégaine une autre, plus rapide et plus forte. */
    private void phase2() {
        phase2 = true;
        boss.damageMult *= 1.3;
        LivingEntity e = boss.entity;
        EntityEquipment eq = e.getEquipment();
        if (eq != null && def.finalWeapon() != null) {
            eq.setItemInMainHand(new ItemStack(def.finalWeapon()));
        }
        AttributeInstance speed = e.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * 1.25);
        }
        e.getWorld().spawnParticle(Particle.LARGE_SMOKE, e.getLocation().add(0, e.getHeight() / 2, 0), 60, 0.8, 1.2, 0.8, 0.05);
        e.getWorld().playSound(e.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.2f, 0.6f);
        for (UUID id : viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                Text.title(p, "", "<#FF3B3B><b>" + Text.esc(def.name()) + "</b> <#D7DCE2>change d'arme !", 0, 40, 10);
                Text.msg(p, "<#FF3B3B>Attention : ses attaques sont plus rapides et plus fortes.");
            }
        }
    }

    private void updateBar() {
        double per = boss.maxHp / def.bars();
        double within = Math.max(0, Math.min(1, (boss.hp - (barsLeft - 1) * per) / per));
        bar.progress((float) within);
        bar.color(barsLeft > 1 ? BossBar.Color.GREEN : within > 0.5 ? BossBar.Color.YELLOW : BossBar.Color.RED);
        String bars = "<#FF3B3B>" + "▮".repeat(barsLeft) + "</#FF3B3B><#3A3F47>" + "▮".repeat(def.bars() - barsLeft) + "</#3A3F47>";
        bar.name(Text.mm("<#FF3B3B><b>" + Text.esc(def.name()) + "</b></#FF3B3B>  <#8C96A3>Lv." + boss.level + "</#8C96A3>  " + bars));
    }

    private void spawnAdds(int count) {
        MobDef add = plugin.registry().mob(def.adds());
        if (add == null || count <= 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            Location l = center.clone().add(Math.cos(a) * 8, 0, Math.sin(a) * 8);
            MobState ms = plugin.mobs().spawn(add, Math.max(1, boss.level - 1), l, floor, null, 1);
            if (ms != null) {
                adds.add(ms);
                Fx.dust(l.clone().add(0, 1, 0), DANGER, 1.6f, 20, 0.5);
            }
        }
    }

    /** Frappe au sol annoncée par un cercle rouge. */
    private void slam() {
        LivingEntity e = boss.entity;
        double r = 6;
        if (e instanceof Mob m) {
            m.setAware(false);
        }
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_WARDEN_ROAR, 0.7f, 1.3f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (boss.dead || !e.isValid()) {
                    cancel();
                    return;
                }
                Location l = e.getLocation();
                if (t < 30) {
                    if (t % 5 == 0) {
                        Fx.ring(l.clone().add(0, 0.2, 0), r, DANGER, 1.8f);
                        Fx.ring(l.clone().add(0, 0.2, 0), r * (t / 30.0), DANGER, 1.2f);
                    }
                    t++;
                    return;
                }
                cancel();
                if (e instanceof Mob m) {
                    m.setAware(true);
                }
                l.getWorld().spawnParticle(Particle.EXPLOSION, l, 3, 1.5, 0.2, 1.5, 0);
                l.getWorld().playSound(l, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 0.6f);
                for (Player p : l.getWorld().getPlayers()) {
                    if (horizontal(p.getLocation(), l) <= r + 0.5 && Math.abs(p.getLocation().getY() - l.getY()) < 3) {
                        plugin.combat().damagePlayer(p, boss.damage() * 1.8, null, false);
                        Vector away = p.getLocation().toVector().subtract(l.toVector()).setY(0);
                        if (away.lengthSquared() < 1.0E-4) {
                            away = new Vector(1, 0, 0);
                        }
                        p.setVelocity(away.normalize().multiply(0.8).setY(0.6));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Charge vers un joueur, annoncée par une ligne rouge. */
    private void charge() {
        List<Player> targets = new ArrayList<>();
        for (UUID id : viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !p.isDead() && horizontal(p.getLocation(), center) <= plugin.floors().arenaRadius()
                    && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                targets.add(p);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        Player target = targets.get(random.nextInt(targets.size()));
        LivingEntity e = boss.entity;
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 1.2f);
        new BukkitRunnable() {
            int t = 0;
            Vector dir;
            final Set<UUID> struck = new HashSet<>();

            @Override
            public void run() {
                if (boss.dead || !e.isValid() || !target.isOnline()) {
                    cancel();
                    return;
                }
                if (t < 15) {
                    dir = target.getLocation().toVector().subtract(e.getLocation().toVector()).setY(0);
                    if (dir.lengthSquared() < 1.0E-4) {
                        dir = new Vector(1, 0, 0);
                    }
                    dir.normalize();
                    if (t % 3 == 0) {
                        Fx.line(e.getLocation().add(0, 0.3, 0), dir, 12, DANGER);
                    }
                } else if (t == 15) {
                    e.setVelocity(dir.clone().multiply(1.8).setY(0.25));
                } else {
                    for (Player p : e.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(e.getLocation()) < 2.4 * 2.4 && struck.add(p.getUniqueId())) {
                            plugin.combat().damagePlayer(p, boss.damage() * 1.4, null, false);
                            p.setVelocity(dir.clone().multiply(1.0).setY(0.5));
                        }
                    }
                    if (t >= 25) {
                        cancel();
                    }
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    void cleanup() {
        for (UUID id : viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(bar);
            }
        }
        viewers.clear();
        for (Iterator<MobState> it = adds.iterator(); it.hasNext(); ) {
            MobState a = it.next();
            if (!a.dead) {
                plugin.mobs().remove(a);
            }
            it.remove();
        }
    }

    private static double horizontal(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
