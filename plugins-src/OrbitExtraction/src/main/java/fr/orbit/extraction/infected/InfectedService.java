package fr.orbit.extraction.infected;

import fr.orbit.extraction.Keys;
import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.util.Text;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Infestés : apparition autour des joueurs en raid, attirance par le bruit, cracheurs, butin. */
public final class InfectedService implements Listener {

    public record Spit(double range, int cooldown, double damage, int poisonTicks) {
    }

    /**
     * @param skins     textures du pack (orbitguns:entity/infecte/&lt;nom&gt;) portées par un corps « mannequin » ;
     *                  vide = l'entité vanilla est visible telle quelle (texture du pack pour zombie et husk)
     * @param pose      pose du corps : debout, accroupi (SNEAKING) ou à plat ventre (SWIMMING)
     * @param bodyScale taille du corps mannequin ({@code scale} règle l'entité qui porte l'IA et la boîte de collision)
     */
    public record InfectedDef(String id, String name, EntityType type, double health, double damage, double speed,
                              double scale, double knockbackRes, int weight, Color color, int lootRolls, Spit spit,
                              long bounty, List<String> skins, Pose pose, double bodyScale) {
    }

    private record LootEntry(String spec, double chance) {
    }

    private static final String BODY_TEAM = "oe_corps";

    private static final class Infected {
        final Mob mob;
        final InfectedDef def;
        /** Corps visible (mannequin) quand le type a des skins ; l'entité {@code mob} est alors invisible. */
        Mannequin body;
        Location investigate;
        int investigateUntil;
        int nextSpit;

        Infected(Mob mob, InfectedDef def) {
            this.mob = mob;
            this.def = def;
        }
    }

    private static final Set<CreatureSpawnEvent.SpawnReason> NATURAL = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.NATURAL, CreatureSpawnEvent.SpawnReason.PATROL,
            CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION, CreatureSpawnEvent.SpawnReason.REINFORCEMENTS,
            CreatureSpawnEvent.SpawnReason.JOCKEY);

    private final OrbitExtraction plugin;
    private final Map<String, InfectedDef> types = new LinkedHashMap<>();
    private final List<LootEntry> loot = new ArrayList<>();
    private final Map<UUID, Infected> live = new HashMap<>();
    /** Corps mannequin → infecté qui le porte. */
    private final Map<UUID, Infected> bodies = new HashMap<>();
    private final Random random = new Random();
    private boolean useBodies = true;

    private int maxPerPlayer;
    private int maxTotal;
    private int spawnInterval;
    private double minDist;
    private double maxDist;
    private double despawnDist;
    private int noiseMemory;
    private boolean blockNatural;
    private int defaultLootRolls;
    private int spawnTask = -1;

    public InfectedService(OrbitExtraction plugin) {
        this.plugin = plugin;
    }

    public void load(ConfigurationSection c) {
        types.clear();
        loot.clear();
        if (c == null) {
            return;
        }
        maxPerPlayer = c.getInt("max-par-joueur", 12);
        maxTotal = c.getInt("max-total", 80);
        spawnInterval = Math.max(10, c.getInt("intervalle-apparition", 40));
        minDist = c.getDouble("distance-apparition-min", 22);
        maxDist = Math.max(minDist + 1, c.getDouble("distance-apparition-max", 44));
        despawnDist = c.getDouble("distance-disparition", 90);
        noiseMemory = (int) (c.getDouble("memoire-bruit-secondes", 12) * 20);
        blockNatural = c.getBoolean("bloquer-monstres-naturels", true);
        defaultLootRolls = c.getInt("butin-tirages", 1);
        useBodies = c.getBoolean("corps-mannequin", true);

        for (Map<?, ?> m : c.getMapList("butin")) {
            Object spec = m.get("objet");
            Object chance = m.get("chance");
            if (spec != null) {
                loot.add(new LootEntry(spec.toString(), chance instanceof Number n ? n.doubleValue() : 0.1));
            }
        }

        ConfigurationSection ts = c.getConfigurationSection("types");
        if (ts != null) {
            for (String id : ts.getKeys(false)) {
                ConfigurationSection s = ts.getConfigurationSection(id);
                EntityType type;
                try {
                    type = EntityType.valueOf(s.getString("entite", "ZOMBIE").toUpperCase());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Infesté " + id + " : entité inconnue, ZOMBIE utilisé.");
                    type = EntityType.ZOMBIE;
                }
                if (type.getEntityClass() == null || !Mob.class.isAssignableFrom(type.getEntityClass())) {
                    plugin.getLogger().warning("Infesté " + id + " : " + type + " n'est pas un monstre, ignoré.");
                    continue;
                }
                ConfigurationSection sp = s.getConfigurationSection("crachat");
                Spit spit = sp == null ? null : new Spit(sp.getDouble("portee", 14), sp.getInt("recharge-ticks", 50),
                        sp.getDouble("degats", 4), sp.getInt("poison-ticks", 60));
                types.put(id, new InfectedDef(id, s.getString("nom", id), type,
                        s.getDouble("vie", 20), s.getDouble("degats", 3), s.getDouble("vitesse", 0.23),
                        s.getDouble("taille", 1), s.getDouble("resistance-recul", 0),
                        Math.max(0, s.getInt("poids", 10)), color(s.getString("couleur")),
                        s.getInt("butin-tirages", defaultLootRolls), spit,
                        Math.max(0, s.getLong("prime", 0)), List.copyOf(s.getStringList("skins")),
                        pose(s.getString("pose", "debout")), s.getDouble("taille-corps", 1)));
            }
        }
        plugin.getLogger().info(types.size() + " types d'infestés, " + loot.size() + " entrées de butin.");
    }

    private static Pose pose(String name) {
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "ramper", "rampant", "swimming" -> Pose.SWIMMING;
            case "accroupi", "sneaking", "crouching" -> Pose.SNEAKING;
            default -> Pose.STANDING;
        };
    }

    private static Color color(String hex) {
        if (hex == null) {
            return null;
        }
        try {
            return Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void start() {
        if (spawnTask != -1) {
            Bukkit.getScheduler().cancelTask(spawnTask);
        }
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnTick, 60L, spawnInterval).getTaskId();
    }

    public void startAi() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::aiTick, 20L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::bodyTick, 20L, 1L);
    }

    /** Une balle qui touche le corps visible blesse l'infecté qui le porte. */
    public LivingEntity hostOf(LivingEntity hit) {
        Infected inf = bodies.get(hit.getUniqueId());
        return inf == null ? hit : inf.mob;
    }

    public Collection<InfectedDef> types() {
        return types.values();
    }

    public InfectedDef type(String id) {
        return types.get(id);
    }

    public int count() {
        return live.size();
    }

    public boolean isInfected(Entity e) {
        return e != null && live.containsKey(e.getUniqueId());
    }

    // ------------------------------------------------------------------ apparition

    public Mob spawn(InfectedDef d, Location loc) {
        Entity raw = loc.getWorld().spawnEntity(loc, d.type(), CreatureSpawnEvent.SpawnReason.CUSTOM);
        if (!(raw instanceof Mob m)) {
            raw.remove();
            return null;
        }
        setup(m, d);
        Infected inf = new Infected(m, d);
        live.put(m.getUniqueId(), inf);
        if (useBodies && !d.skins().isEmpty()) {
            attachBody(inf, loc);
        }
        return m;
    }

    /** Corps visible : mannequin au skin du pack, recalé à chaque tick sur l'entité (invisible) qui porte l'IA. */
    private void attachBody(Infected inf, Location loc) {
        InfectedDef d = inf.def;
        String skin = d.skins().get(random.nextInt(d.skins().size()));
        try {
            Mannequin body = loc.getWorld().spawn(loc, Mannequin.class, b -> {
                b.setProfile(ResolvableProfile.resolvableProfile()
                        .skinPatch(sp -> sp.body(Key.key("orbitguns", "entity/infecte/" + skin))).build());
                b.setImmovable(true);
                b.setGravity(false);
                b.setSilent(true);
                b.setPersistent(false);
                b.setCollidable(false);
                b.setCanPickupItems(false);
                b.setCustomNameVisible(false);
                try {
                    b.setDescription(null);
                } catch (RuntimeException ex) {
                    b.setDescription(net.kyori.adventure.text.Component.empty());
                }
                if (d.pose() != Pose.STANDING && Mannequin.validPoses().contains(d.pose())) {
                    b.setPose(d.pose(), true);
                }
                attr(b, Attribute.SCALE, d.bodyScale());
            }, CreatureSpawnEvent.SpawnReason.CUSTOM);
            bodyTeam().addEntity(body);
            plugin.hud().panel().mirrorEntry(BODY_TEAM, body.getUniqueId().toString());    // tableaux des joueurs (hud/Panel)
            inf.body = body;
            bodies.put(body.getUniqueId(), inf);
            inf.mob.setInvisible(true);
            EntityEquipment eq = inf.mob.getEquipment();
            if (eq != null) {
                eq.clear(); // une armure resterait visible sur l'entité invisible
            }
        } catch (RuntimeException | LinkageError ex) {
            useBodies = false;
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Corps mannequin indisponible : les infectés gardent l'apparence de leur entité vanilla.", ex);
        }
    }

    /** Équipe sans collision ni nom : le client ne se fait pas pousser par le corps (l'entité invisible, elle, pousse). */
    private Team bodyTeam() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam(BODY_TEAM);
        if (t == null) {
            t = sb.registerNewTeam(BODY_TEAM);
            t.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        return t;
    }

    private void dropBody(Infected inf, boolean deathAnimation) {
        Mannequin body = inf.body;
        if (body == null) {
            return;
        }
        inf.body = null;
        bodies.remove(body.getUniqueId());
        Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(BODY_TEAM);
        if (t != null) {
            t.removeEntry(body.getUniqueId().toString());
        }
        if (!body.isValid()) {
            return;
        }
        if (deathAnimation && plugin.isEnabled()) {
            try {
                body.setHealth(0); // bascule et disparaît comme un mort
            } catch (RuntimeException ex) {
                body.remove();
                return;
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (body.isValid()) {
                    body.remove();
                }
            }, 25L);
        } else {
            body.remove();
        }
    }

    private void bodyTick() {
        for (Infected inf : live.values()) {
            Mannequin body = inf.body;
            if (body == null || !body.isValid() || !inf.mob.isValid()) {
                continue;
            }
            Location l = inf.mob.getLocation();
            l.setPitch(0);
            body.teleport(l);
        }
    }

    private void setup(Mob m, InfectedDef d) {
        m.getPersistentDataContainer().set(Keys.INFECTED, PersistentDataType.STRING, d.id());
        m.setPersistent(false);
        m.setRemoveWhenFarAway(true);
        m.setCanPickupItems(false);
        m.customName(Text.mm("<#7A8F5A>" + d.name()));
        m.setCustomNameVisible(false);
        if (m instanceof Ageable a) {
            a.setAdult();
        }
        if (m instanceof Zombie z) {
            z.setShouldBurnInDay(false);
        }
        EntityEquipment eq = m.getEquipment();
        if (eq != null) {
            eq.clear();
            if (d.color() != null) {
                eq.setChestplate(dyed(Material.LEATHER_CHESTPLATE, d.color()));
                if (random.nextBoolean()) {
                    eq.setLeggings(dyed(Material.LEATHER_LEGGINGS, d.color()));
                }
            }
            eq.setHelmetDropChance(0);
            eq.setChestplateDropChance(0);
            eq.setLeggingsDropChance(0);
            eq.setBootsDropChance(0);
            eq.setItemInMainHandDropChance(0);
            eq.setItemInOffHandDropChance(0);
        }
        attr(m, Attribute.MAX_HEALTH, d.health());
        m.setHealth(d.health());
        attr(m, Attribute.ATTACK_DAMAGE, d.damage());
        attr(m, Attribute.MOVEMENT_SPEED, d.speed());
        attr(m, Attribute.SCALE, d.scale());
        attr(m, Attribute.KNOCKBACK_RESISTANCE, d.knockbackRes());
        if (d.scale() < 0.8) {
            attr(m, Attribute.STEP_HEIGHT, 1.3);    // petit gabarit (rampant) : franchit quand même une marche
        }
        attr(m, Attribute.FOLLOW_RANGE, 28);
        attr(m, Attribute.SPAWN_REINFORCEMENTS, 0);
    }

    private ItemStack dyed(Material mat, Color base) {
        ItemStack it = new ItemStack(mat);
        // Légère variation de teinte : la horde n'est pas uniforme.
        int r = clamp(base.getRed() + random.nextInt(21) - 10);
        int g = clamp(base.getGreen() + random.nextInt(21) - 10);
        int b = clamp(base.getBlue() + random.nextInt(21) - 10);
        it.editMeta(LeatherArmorMeta.class, lm -> lm.setColor(Color.fromRGB(r, g, b)));
        return it;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static void attr(LivingEntity e, Attribute a, double v) {
        AttributeInstance inst = e.getAttribute(a);
        if (inst != null) {
            inst.setBaseValue(v);
        }
    }

    /** Un type d'infecté tiré selon les poids (null s'il n'y en a aucun) : pour les hordes et les gardes d'épave. */
    public InfectedDef randomType() {
        return pick();
    }

    private InfectedDef pick() {
        int total = 0;
        for (InfectedDef d : types.values()) {
            total += d.weight();
        }
        if (total <= 0) {
            return null;
        }
        int r = random.nextInt(total);
        for (InfectedDef d : types.values()) {
            r -= d.weight();
            if (r < 0) {
                return d;
            }
        }
        return null;
    }

    private void spawnTick() {
        List<Player> raiders = raiders();
        if (raiders.isEmpty() || types.isEmpty()) {
            return;
        }
        for (Player p : raiders) {
            if (live.size() >= maxTotal) {
                return;
            }
            if (near(p.getLocation(), 64) >= maxPerPlayer) {
                continue;
            }
            Location l = findSpot(p, raiders);
            InfectedDef d = pick();
            if (l != null && d != null) {
                spawn(d, l);
            }
        }
    }

    private Location findSpot(Player around, List<Player> raiders) {
        World w = around.getWorld();
        Location hideout = plugin.raid().hideout();
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = minDist + random.nextDouble() * (maxDist - minDist);
            int x = (int) Math.floor(around.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(around.getZ() + Math.sin(angle) * dist);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Block ground = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (!ground.getType().isSolid() || ground.isLiquid()) {
                continue;
            }
            Block feet = ground.getRelative(0, 1, 0);
            if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable() || feet.isLiquid()) {
                continue;
            }
            Location l = feet.getLocation().add(0.5, 0, 0.5);
            if (Math.abs(l.getY() - around.getY()) > 24) {
                continue;
            }
            boolean tooClose = false;
            for (Player o : raiders) {
                if (o.getWorld() == w && o.getLocation().distanceSquared(l) < minDist * minDist) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) {
                continue;
            }
            if (hideout != null && hideout.getWorld() == w && hideout.distanceSquared(l) < Math.pow(plugin.raid().hideoutRadius() + 24, 2)) {
                continue;
            }
            l.setYaw(random.nextFloat() * 360);
            return l;
        }
        return null;
    }

    private int near(Location l, double r) {
        int n = 0;
        double r2 = r * r;
        for (Infected i : live.values()) {
            if (i.mob.getWorld() == l.getWorld() && i.mob.getLocation().distanceSquared(l) <= r2) {
                n++;
            }
        }
        return n;
    }

    private List<Player> raiders() {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.raid().isTargetable(p)) {
                out.add(p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ IA

    private void aiTick() {
        int now = Bukkit.getCurrentTick();
        List<Player> raiders = raiders();
        double dd = despawnDist * despawnDist;
        Iterator<Infected> it = live.values().iterator();
        while (it.hasNext()) {
            Infected inf = it.next();
            Mob m = inf.mob;
            if (!m.isValid()) {
                dropBody(inf, false);
                it.remove();
                continue;
            }
            if (inf.body != null && !inf.body.isValid()) {
                dropBody(inf, false);       // corps perdu (/kill...) : l'entité redevient visible
                m.setInvisible(false);
            }
            boolean someoneNear = false;
            for (Player p : raiders) {
                if (p.getWorld() == m.getWorld() && p.getLocation().distanceSquared(m.getLocation()) <= dd) {
                    someoneNear = true;
                    break;
                }
            }
            if (!someoneNear) {
                dropBody(inf, false);
                m.remove();
                it.remove();
                continue;
            }
            LivingEntity target = m.getTarget();
            if (target instanceof Player tp && !plugin.raid().isTargetable(tp)) {
                m.setTarget(null);
                target = null;
            }
            if (target == null && inf.investigate != null) {
                if (now > inf.investigateUntil || inf.investigate.getWorld() != m.getWorld()
                        || m.getLocation().distanceSquared(inf.investigate) < 4) {
                    inf.investigate = null;
                } else if (!m.getPathfinder().hasPath()) {
                    m.getPathfinder().moveTo(inf.investigate, 1.0);
                }
            }
            Spit spit = inf.def.spit();
            if (spit != null && target instanceof Player tp && now >= inf.nextSpit) {
                double d = m.getLocation().distance(tp.getLocation());
                if (d >= 3 && d <= spit.range() && m.hasLineOfSight(tp)) {
                    spit(m, tp, spit);
                    inf.nextSpit = now + spit.cooldown();
                }
            }
        }
    }

    private void spit(Mob m, Player target, Spit spit) {
        Location from = m.getEyeLocation();
        Vector to = target.getEyeLocation().toVector().subtract(new Vector(0, 0.4, 0));
        Vector dir = to.subtract(from.toVector());
        double dist = dir.length();
        dir.normalize().multiply(1.1).add(new Vector(0, dist * 0.012, 0));
        Snowball s = m.launchProjectile(Snowball.class, dir);
        s.setItem(new ItemStack(Material.SLIME_BALL));
        s.getPersistentDataContainer().set(Keys.SPIT_DAMAGE, PersistentDataType.DOUBLE, spit.damage());
        s.getPersistentDataContainer().set(Keys.SPIT_POISON, PersistentDataType.INTEGER, spit.poisonTicks());
        m.getWorld().playSound(from, Sound.ENTITY_LLAMA_SPIT, 1f, 0.6f);
    }

    /** Un tir (ou autre bruit) : les infestés dans le rayon viennent voir ; les plus proches attaquent directement. */
    public void noise(Location at, double radius, Player source) {
        int now = Bukkit.getCurrentTick();
        double r2 = radius * radius;
        boolean targetable = source != null && plugin.raid().isTargetable(source);
        for (Infected inf : live.values()) {
            Mob m = inf.mob;
            if (m.getWorld() != at.getWorld() || m.getTarget() != null) {
                continue;
            }
            double d2 = m.getLocation().distanceSquared(at);
            if (d2 > r2) {
                continue;
            }
            if (targetable && d2 <= r2 * 0.12) {
                m.setTarget(source);
                continue;
            }
            inf.investigate = at.clone().add(random.nextGaussian() * 3, 0, random.nextGaussian() * 3);
            inf.investigateUntil = now + noiseMemory;
            m.getPathfinder().moveTo(inf.investigate, 1.0);
        }
    }

    public int purge() {
        int n = 0;
        for (Infected inf : live.values()) {
            dropBody(inf, false);
            if (inf.mob.isValid()) {
                inf.mob.remove();
                n++;
            }
        }
        live.clear();
        bodies.clear();
        return n;
    }

    // ------------------------------------------------------------------ événements

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Mannequin) {       // corps d'un infecté abattu : simple animation de mort
            e.getDrops().clear();
            e.setDroppedExp(0);
            return;
        }
        Infected inf = live.remove(e.getEntity().getUniqueId());
        if (inf == null) {
            return;
        }
        dropBody(inf, true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        for (int roll = 0; roll < Math.max(0, inf.def.lootRolls()); roll++) {
            for (LootEntry l : loot) {
                if (random.nextDouble() < l.chance()) {
                    e.getDrops().addAll(plugin.specs().create(l.spec(), false));
                }
            }
        }
        Player killer = e.getEntity().getKiller();
        if (killer != null) {
            plugin.raid().onInfectedKill(killer, inf.def.bounty());
        }
    }

    /** Le corps ne prend rien lui-même : un coup de mêlée d'un joueur est reporté sur l'infecté. */
    @EventHandler(priority = EventPriority.LOW)
    public void onBodyDamage(EntityDamageEvent e) {
        Infected inf = bodies.get(e.getEntity().getUniqueId());
        if (inf == null) {
            return;
        }
        e.setCancelled(true);
        if (e instanceof EntityDamageByEntityEvent ed && ed.getDamager() instanceof Player p
                && !plugin.gunService().isFiring(p) && inf.mob.isValid()
                && (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            inf.mob.damage(e.getDamage(), p);
        }
    }

    /** Retour visuel sur le corps : flash rouge quand l'infecté est touché, bras qui frappe quand il attaque. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHostDamage(EntityDamageEvent e) {
        Infected inf = live.get(e.getEntity().getUniqueId());
        if (inf != null && inf.body != null && inf.body.isValid()) {
            inf.body.playHurtAnimation(0);
        }
        if (e instanceof EntityDamageByEntityEvent ed) {
            Infected attacker = live.get(ed.getDamager().getUniqueId());
            if (attacker != null && attacker.body != null && attacker.body.isValid()) {
                attacker.body.swingMainHand();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) {
        if (live.containsKey(e.getEntity().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        if (live.containsKey(e.getEntity().getUniqueId()) && e.getClass() == EntityCombustEvent.class) {
            e.setCancelled(true); // soleil : non ; lave et feu : oui (sous-classes de l'événement)
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (live.containsKey(e.getEntity().getUniqueId()) && e.getTarget() instanceof Player p && !plugin.raid().isTargetable(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNaturalSpawn(CreatureSpawnEvent e) {
        if (blockNatural && e.getEntity() instanceof Monster && NATURAL.contains(e.getSpawnReason())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onSpitHit(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        Double dmg = pr.getPersistentDataContainer().get(Keys.SPIT_DAMAGE, PersistentDataType.DOUBLE);
        if (dmg == null) {
            return;
        }
        Location l = pr.getLocation();
        l.getWorld().spawnParticle(Particle.ITEM_SLIME, l, 12, 0.2, 0.2, 0.2, 0.05);
        if (e.getHitEntity() instanceof LivingEntity victim && !live.containsKey(victim.getUniqueId())) {
            if (victim instanceof Player p && (!plugin.raid().isTargetable(p) || p.getGameMode() == GameMode.CREATIVE)) {
                return;
            }
            Entity shooter = pr.getShooter() instanceof Entity en ? en : null;
            victim.damage(dmg, shooter);
            Integer poison = pr.getPersistentDataContainer().get(Keys.SPIT_POISON, PersistentDataType.INTEGER);
            if (poison != null && poison > 0) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poison, 0));
            }
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1f, 0.8f);
        }
    }
}
