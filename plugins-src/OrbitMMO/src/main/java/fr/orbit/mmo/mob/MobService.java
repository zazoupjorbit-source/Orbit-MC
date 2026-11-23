package fr.orbit.mmo.mob;

import fr.orbit.mmo.Keys;
import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.player.Stats;
import fr.orbit.mmo.registry.Defs.BossDef;
import fr.orbit.mmo.registry.Defs.FloorDef;
import fr.orbit.mmo.registry.Defs.MobDef;
import fr.orbit.mmo.util.Fx;
import fr.orbit.mmo.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Apparition, IA, mort (éclats de polygones) et récompenses des monstres. */
public final class MobService implements Listener {

    private final OrbitMMO plugin;
    private final Map<UUID, MobState> tracked = new HashMap<>();
    private final Random random = new Random();

    public MobService(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int every = Math.max(20, plugin.getConfig().getInt("spawning.interval-ticks", 80));
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::spawnTick, 100L, every);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::aiTick, 40L, 20L);
        for (World w : Bukkit.getWorlds()) {
            purge(w.getEntities());
        }
    }

    public MobState state(Entity e) {
        return e == null ? null : tracked.get(e.getUniqueId());
    }

    public Collection<MobState> all() {
        return tracked.values();
    }

    public MobState spawn(MobDef def, int level, Location loc, int floor, BossDef boss, double hpFactor) {
        @SuppressWarnings("unchecked")
        Class<LivingEntity> cls = (Class<LivingEntity>) (Class<?>) def.type().getEntityClass();
        if (cls == null) {
            return null;
        }
        double maxHp = Stats.mobHp(level) * def.hp() * hpFactor;
        LivingEntity e = loc.getWorld().spawn(loc, cls, ent -> setup(ent, def, boss != null));
        MobState ms = new MobState(e, def, level, floor, boss, maxHp);
        tracked.put(e.getUniqueId(), ms);
        updateName(ms);
        return ms;
    }

    private void setup(LivingEntity e, MobDef def, boolean boss) {
        e.getPersistentDataContainer().set(Keys.MOB, PersistentDataType.STRING, def.id());
        e.setPersistent(false);
        e.setRemoveWhenFarAway(false);
        e.setCanPickupItems(false);
        EntityEquipment eq = e.getEquipment();
        if (eq != null) {
            eq.clear();
            if (def.weapon() != null) {
                eq.setItemInMainHand(new ItemStack(def.weapon()));
            }
            eq.setItemInMainHandDropChance(0);
            eq.setItemInOffHandDropChance(0);
            eq.setHelmetDropChance(0);
            eq.setChestplateDropChance(0);
            eq.setLeggingsDropChance(0);
            eq.setBootsDropChance(0);
        }
        if (e instanceof Ageable a) {
            a.setAdult();
        }
        if (e instanceof Zombie z) {
            z.setShouldBurnInDay(false);
        }
        if (e instanceof AbstractSkeleton s) {
            s.setShouldBurnInDay(false);
        }
        if (e instanceof Hoglin h) {
            h.setImmuneToZombification(true);
            h.setIsAbleToBeHunted(false);
        }
        if (e instanceof Wolf w) {
            w.setAngry(true);
        }
        // Apparence du pack, choisie par l'identifiant du monstre : tenue complète, variante de loup, ours brun.
        try {
            fr.orbit.mmo.ambient.Looks.applyMob(e, def.id());
        } catch (RuntimeException | LinkageError ex) {
            // Une apparence refusée par le serveur ne doit jamais empêcher le monstre d'apparaître.
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Apparence du monstre « " + def.id() + " » refusée", ex);
        }
        attr(e, Attribute.SCALE, def.scale(), false);
        attr(e, Attribute.MOVEMENT_SPEED, def.speed(), true);
        attr(e, Attribute.FOLLOW_RANGE, 28, false);
        if (boss) {
            attr(e, Attribute.KNOCKBACK_RESISTANCE, 1, false);
        }
    }

    private static void attr(LivingEntity e, Attribute a, double value, boolean multiply) {
        AttributeInstance inst = e.getAttribute(a);
        if (inst == null || (!multiply && value == inst.getBaseValue())) {
            return;
        }
        inst.setBaseValue(multiply ? inst.getBaseValue() * value : value);
    }

    /** Nom au-dessus de la tête : nom, niveau, et barre de vie une fois touché. */
    public void updateName(MobState ms) {
        String name = ms.boss != null
                ? "<#FF3B3B><b>" + Text.esc(ms.def.name()) + "</b></#FF3B3B>"
                : "<#FF8A7A>" + Text.esc(ms.def.name()) + "</#FF8A7A>";
        String text = name + " <#8C96A3>Lv." + ms.level + "</#8C96A3>";
        if (ms.revealed) {
            text += " " + Text.hpBar(ms.hp / ms.maxHp, 10);
        }
        ms.entity.customName(Text.mm(text));
        ms.entity.setCustomNameVisible(ms.revealed || ms.boss != null);
    }

    /** Mort : éclats, récompenses, retrait. */
    public void kill(MobState ms, Player killer) {
        if (ms.dead) {
            return;
        }
        ms.dead = true;
        tracked.remove(ms.entity.getUniqueId());
        LivingEntity e = ms.entity;
        double size = Math.max(1, e.getHeight() / 1.6);
        Fx.shatter(e.getLocation().add(0, e.getHeight() / 2, 0), size);
        Bukkit.getPluginManager().callEvent(new MobKilledEvent(ms, killer));
        if (ms.boss != null) {
            plugin.bosses().onBossDeath(ms, killer);
        } else {
            reward(ms, killer);
        }
        e.remove();
    }

    /** Retire sans récompense (dépop, réinitialisation d'un boss). */
    public void remove(MobState ms) {
        ms.dead = true;
        ms.despawned = true;
        tracked.remove(ms.entity.getUniqueId());
        ms.entity.remove();
    }

    private void reward(MobState ms, Player killer) {
        if (killer == null || !killer.isOnline()) {
            return;
        }
        long xp = Math.round(Stats.mobXp(ms.level) * ms.def.xp());
        long gold = Math.round(Stats.mobGold(ms.level) * ms.def.col() * (0.8 + random.nextDouble() * 0.4));
        PlayerData kd = plugin.players().get(killer);
        StringBuilder line = new StringBuilder("<#7FD4FF>+" + Text.num(xp) + " XP</#7FD4FF>  <#FFD66E>+" + Text.num(gold) + " or</#FFD66E>");
        kd.gold += gold;
        kd.kills++;
        plugin.progression().addXp(killer, kd, xp);
        for (Player mate : plugin.parties().nearby(killer, 40)) {
            if (mate.equals(killer)) {
                continue;
            }
            PlayerData md = plugin.players().get(mate);
            long share = Math.round(xp * 0.75);
            plugin.progression().addXp(mate, md, share);
            plugin.hud().flash(mate, "<#8C96A3>Groupe :</#8C96A3> <#7FD4FF>+" + Text.num(share) + " XP", 30);
        }
        if (ms.def.drop() != null && random.nextDouble() < ms.def.drop().chance()) {
            Items.give(killer, Items.material(ms.def.drop()));
            line.append("  <#D7DCE2>+1 ").append(Text.esc(ms.def.drop().name())).append("</#D7DCE2>");
        }
        // Équipement (Inhabituel → Légendaire) et runes : petites chances à chaque monstre.
        fr.orbit.mmo.item.Loot.rollMobLoot(plugin, killer, kd, ms.level);
        plugin.hud().flash(killer, line.toString(), 40);
    }

    // ------------------------------------------------------------------ Apparition

    private void spawnTick() {
        int perPlayer = plugin.getConfig().getInt("spawning.per-player", 6);
        int worldCap = plugin.getConfig().getInt("spawning.per-floor", 60);
        for (World w : Bukkit.getWorlds()) {
            int floor = plugin.floors().floorOf(w);
            if (floor <= 0 || w.getPlayers().isEmpty()) {
                continue;
            }
            FloorDef fd = plugin.floors().def(floor);
            List<MobDef> pool = new ArrayList<>();
            for (String id : fd.mobs()) {
                MobDef def = plugin.registry().mob(id);
                if (def != null) {
                    pool.add(def);
                }
            }
            if (pool.isEmpty()) {
                continue;
            }
            int inWorld = 0;
            for (MobState ms : tracked.values()) {
                if (ms.entity.getWorld().equals(w) && ms.boss == null) {
                    inWorld++;
                }
            }
            for (Player p : w.getPlayers()) {
                if (!eligible(p) || plugin.floors().isSafe(p.getLocation()) || plugin.floors().inArena(p.getLocation())) {
                    continue;
                }
                int near = countNear(p.getLocation(), 36);
                for (int i = 0; i < 2 && near < perPlayer && inWorld < worldCap; i++) {
                    Location at = findSpot(p.getLocation());
                    if (at == null) {
                        continue;
                    }
                    MobDef def = pool.get(random.nextInt(pool.size()));
                    int level = fd.minLevel() + random.nextInt(Math.max(1, fd.maxLevel() - fd.minLevel() + 1));
                    spawn(def, level, at, floor, null, 1);
                    near++;
                    inWorld++;
                }
            }
        }
        // Le continent a sa propre faune (régions, camps, ruines).
        if (plugin.continent() != null && plugin.continent().fauna() != null) {
            plugin.continent().fauna().spawnTick();
        }
    }

    public boolean eligible(Player p) {
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) {
            return false;
        }
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        return d != null && d.classId != null;
    }

    /** Nombre de monstres (hors boss) dans ce monde. */
    public int count(World w) {
        int n = 0;
        for (MobState ms : tracked.values()) {
            if (ms.boss == null && ms.entity.getWorld().equals(w)) {
                n++;
            }
        }
        return n;
    }

    public int countNear(Location l, double r) {
        int n = 0;
        double r2 = r * r;
        for (MobState ms : tracked.values()) {
            Location ml = ms.entity.getLocation();
            if (ml.getWorld().equals(l.getWorld()) && ml.distanceSquared(l) <= r2) {
                n++;
            }
        }
        return n;
    }

    private Location findSpot(Location center) {
        World w = center.getWorld();
        int radius = plugin.floors().radius() - 8;
        int town = plugin.floors().townRadius() + 6;
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 14 + random.nextDouble() * 16;
            int x = (int) Math.floor(center.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(center.getZ() + Math.sin(angle) * dist);
            if ((double) x * x + (double) z * z > (double) radius * radius || (double) x * x + (double) z * z < (double) town * town) {
                continue;
            }
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            double ax = x - plugin.floors().arenaX();
            if (ax * ax + (double) z * z < 24 * 24) {
                continue;
            }
            int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (y < 40) {
                continue;
            }
            Block ground = w.getBlockAt(x, y, z);
            Block feet = ground.getRelative(BlockFace.UP);
            if (!ground.getType().isSolid() || ground.isLiquid() || !feet.isPassable() || !feet.getRelative(BlockFace.UP).isPassable()) {
                continue;
            }
            return new Location(w, x + 0.5, y + 1, z + 0.5, random.nextFloat() * 360f, 0f);
        }
        return null;
    }

    // ------------------------------------------------------------------ IA

    private void aiTick() {
        double townR = plugin.floors().townRadius() + 2;
        for (MobState ms : new ArrayList<>(tracked.values())) {
            LivingEntity e = ms.entity;
            if (!e.isValid()) {
                tracked.remove(e.getUniqueId());
                continue;
            }
            if (ms.boss != null) {
                continue;
            }
            Player nearest = null;
            double best = Double.MAX_VALUE;
            for (Player p : e.getWorld().getPlayers()) {
                double d2 = p.getLocation().distanceSquared(e.getLocation());
                if (d2 < best && eligible(p)) {
                    best = d2;
                    nearest = p;
                }
            }
            if (nearest == null || best > 72 * 72) {
                remove(ms);
                continue;
            }
            Location l = e.getLocation();
            boolean onContinent = plugin.continent() != null && plugin.continent().isContinent(l.getWorld());
            if (onContinent && plugin.continent().fauna() != null && plugin.continent().fauna().stray(ms)) {
                remove(ms);
                continue;
            }
            double d = Math.sqrt(l.getX() * l.getX() + l.getZ() * l.getZ());
            if (!onContinent && d < townR) {
                // Les monstres n'entrent pas en ville.
                Vector out = new Vector(l.getX(), 0, l.getZ());
                if (out.lengthSquared() < 1.0E-4) {
                    out = new Vector(1, 0, 0);
                }
                e.setVelocity(out.normalize().multiply(0.7).setY(0.25));
                if (e instanceof Mob m) {
                    m.setTarget(null);
                }
                continue;
            }
            if (e instanceof Mob m && m.getTarget() == null && best < 14 * 14 && !hidden(nearest)
                    && !plugin.floors().isSafe(nearest.getLocation())) {
                m.setTarget(nearest);
            }
        }
    }

    private boolean hidden(Player p) {
        // Effet « furtif » (camouflage, invisibilité) : les monstres ne vous repèrent pas.
        return plugin.auras().sum(p.getUniqueId(), "furtif") > 0;
    }

    public void shutdown() {
        for (MobState ms : new ArrayList<>(tracked.values())) {
            ms.entity.remove();
        }
        tracked.clear();
    }

    private void purge(Collection<? extends Entity> entities) {
        for (Entity e : entities) {
            if (e.getPersistentDataContainer().has(Keys.MOB, PersistentDataType.STRING) && !tracked.containsKey(e.getUniqueId())) {
                e.remove();
            }
        }
    }

    // ------------------------------------------------------------------ Événements

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        purge(e.getEntities());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        if (tracked.containsKey(e.getEntity().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) {
        if (tracked.containsKey(e.getEntity().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onVanillaDeath(EntityDeathEvent e) {
        MobState ms = tracked.remove(e.getEntity().getUniqueId());
        if (ms != null) {
            ms.dead = true;
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player p)) {
            return;
        }
        if (!tracked.containsKey(e.getEntity().getUniqueId())) {
            return;
        }
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        if (d == null || d.classId == null || hidden(p) || plugin.floors().isSafe(p.getLocation())) {
            e.setCancelled(true);
        }
    }
}
