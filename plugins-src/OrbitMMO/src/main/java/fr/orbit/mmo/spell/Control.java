package fr.orbit.mmo.spell;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Contrôle de foule : étourdissement, peur, désorientation (rompue par les dégâts), immobilisation et silence.
 * <ul>
 *   <li>un monstre étourdi ou désorienté ne bouge plus ; apeuré, il fuit ; immobilisé, il reste sur place mais frappe ;</li>
 *   <li>un joueur étourdi, apeuré ou désorienté ne peut ni frapper ni lancer de sort ; réduit au silence, il frappe
 *       mais ne lance plus rien (et son incantation est coupée) ; immobilisé, il ne peut plus bouger ni sauter ;</li>
 *   <li>entre joueurs, rendements décroissants par famille (étourdissements, incapacités, immobilisations, silences) :
 *       durée pleine, puis moitié, puis quart, puis immunité, remis à zéro 18 s après le dernier contrôle ;</li>
 *   <li>les boss y sont insensibles (décidé par l'appelant).</li>
 * </ul>
 * Les modificateurs d'attributs sont transitoires (jamais sauvegardés avec le joueur) et retirés à la connexion.
 */
public final class Control implements Listener {

    public enum Kind {
        STUN("Étourdi", "#FFE066", Family.STUN),
        FEAR("Apeuré", "#B36BFF", Family.INCAP),
        DISORIENT("Désorienté", "#FF9AE0", Family.INCAP),
        ROOT("Immobilisé", "#7FD48A", Family.ROOT),
        SILENCE("Réduit au silence", "#9AD8FF", Family.SILENCE);

        public final String label;
        public final String hex;
        final Family family;

        Kind(String label, String hex, Family family) {
            this.label = label;
            this.hex = hex;
            this.family = family;
        }
    }

    enum Family { STUN, INCAP, ROOT, SILENCE }

    /** Fenêtre des rendements décroissants (ms) et durées successives. */
    private static final long DR_WINDOW = 18_000;
    private static final double[] DR_STEPS = {1.0, 0.5, 0.25};

    private static final class State {
        final EnumMap<Kind, Long> until = new EnumMap<>(Kind.class);
        UUID fearSource;
        Location fearFrom;
        boolean madeUnaware;
        boolean slowed;
    }

    private static final class Dr {
        int count;
        long last;
    }

    private final OrbitMMO plugin;
    private final NamespacedKey speedKey;
    private final NamespacedKey jumpKey;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, EnumMap<Family, Dr>> dr = new HashMap<>();
    private BukkitTask task;
    private int ticks;

    public Control(OrbitMMO plugin) {
        this.plugin = plugin;
        this.speedKey = new NamespacedKey(plugin, "cc_speed");
        this.jumpKey = new NamespacedKey(plugin, "cc_jump");
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        for (UUID id : states.keySet()) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof LivingEntity le) {
                release(le, states.get(id));
            }
        }
        states.clear();
        dr.clear();
    }

    // ------------------------------------------------------------------ Application

    /**
     * Pose un contrôle. Renvoie la durée réellement appliquée (0 = immunisé).
     * @param source lanceur (pour la peur : on fuit loin de lui ; pour les messages d'immunité)
     */
    public double apply(Player source, LivingEntity t, Kind kind, double seconds) {
        if (seconds <= 0 || !t.isValid() || t.isDead()) {
            return 0;
        }
        double applied = seconds;
        if (t instanceof Player) {
            applied = seconds * diminish(t.getUniqueId(), kind.family);
            if (applied <= 0.05) {
                if (source != null) {
                    plugin.hud().flash(source, "<#8C96A3>" + t.getName() + " est <white>immunisé</white> (" + kind.label.toLowerCase(Locale.ROOT) + ")", 25);
                }
                t.getWorld().spawnParticle(Particle.ENCHANTED_HIT, t.getLocation().add(0, t.getHeight() + 0.2, 0), 8, 0.3, 0.2, 0.3, 0.05);
                return 0;
            }
        }
        long now = System.currentTimeMillis();
        State st = states.computeIfAbsent(t.getUniqueId(), k -> new State());
        long end = now + (long) (applied * 1000);
        st.until.merge(kind, end, Math::max);
        if (kind == Kind.FEAR && source != null) {
            st.fearSource = source.getUniqueId();
            st.fearFrom = source.getLocation();
        }
        onApply(t, st, kind, applied);
        return applied;
    }

    private double diminish(UUID id, Family f) {
        long now = System.currentTimeMillis();
        Dr d = dr.computeIfAbsent(id, k -> new EnumMap<>(Family.class)).computeIfAbsent(f, k -> new Dr());
        if (now - d.last > DR_WINDOW) {
            d.count = 0;
        }
        double mult = d.count < DR_STEPS.length ? DR_STEPS[d.count] : 0;
        d.count++;
        d.last = now;
        return mult;
    }

    private void onApply(LivingEntity t, State st, Kind kind, double seconds) {
        Location head = t.getLocation().add(0, t.getHeight() + 0.2, 0);
        switch (kind) {
            case STUN -> {
                t.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, head, 12, 0.3, 0.1, 0.3, 0.05);
                t.getWorld().playSound(t.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7f, 0.6f);
            }
            case FEAR -> {
                t.getWorld().spawnParticle(Particle.SOUL, head, 10, 0.3, 0.1, 0.3, 0.03);
                t.getWorld().playSound(t.getLocation(), Sound.ENTITY_VEX_CHARGE, 0.8f, 0.7f);
            }
            case DISORIENT -> {
                t.getWorld().spawnParticle(Particle.WITCH, head, 14, 0.3, 0.2, 0.3, 0.02);
                t.getWorld().playSound(t.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.6f);
                if (t instanceof Player) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, (int) (seconds * 20) + 40, 0, false, false, false));
                }
            }
            case ROOT -> {
                t.getWorld().spawnParticle(Particle.BLOCK, t.getLocation().add(0, 0.1, 0), 30, 0.4, 0.05, 0.4, 0.1,
                        org.bukkit.Material.MANGROVE_ROOTS.createBlockData());
                t.getWorld().playSound(t.getLocation(), Sound.BLOCK_ROOTS_PLACE, 1f, 0.7f);
            }
            case SILENCE -> {
                t.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, head, 12, 0.3, 0.1, 0.3, 0.02);
                t.getWorld().playSound(t.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.4f, 1.6f);
                if (t instanceof Player v) {
                    PlayerData d = plugin.players().loaded(v.getUniqueId());
                    if (d != null && d.cast != null) {
                        plugin.spells().interrupt(d);
                        plugin.hud().flash(v, "<#9AD8FF>Incantation interrompue", 25);
                    }
                }
            }
        }
        if (t instanceof Player v) {
            plugin.hud().flash(v, "<" + kind.hex + "><b>" + kind.label + "</b> <#D7DCE2>· " + fmt(seconds) + " s", (int) (seconds * 20));
        }
        refresh(t, st);
    }

    // ------------------------------------------------------------------ Questions

    private boolean active(UUID id, Kind k) {
        State st = states.get(id);
        if (st == null) {
            return false;
        }
        Long u = st.until.get(k);
        return u != null && u > System.currentTimeMillis();
    }

    /** Étourdi, apeuré ou désorienté : ni attaque ni sort. */
    public boolean incapacitated(Entity e) {
        if (e == null) {
            return false;
        }
        UUID id = e.getUniqueId();
        return active(id, Kind.STUN) || active(id, Kind.FEAR) || active(id, Kind.DISORIENT);
    }

    /** Raison pour laquelle le joueur ne peut pas lancer de sort (null = il peut). */
    public String castBlock(Player p) {
        UUID id = p.getUniqueId();
        for (Kind k : new Kind[]{Kind.STUN, Kind.FEAR, Kind.DISORIENT, Kind.SILENCE}) {
            if (active(id, k)) {
                return "Impossible : " + k.label.toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    /** Dégâts reçus : la désorientation est rompue. */
    public void damaged(LivingEntity t) {
        State st = states.get(t.getUniqueId());
        if (st != null && st.until.remove(Kind.DISORIENT) != null) {
            if (t instanceof Player) {
                t.removePotionEffect(PotionEffectType.NAUSEA);
            }
            refresh(t, st);
        }
    }

    // ------------------------------------------------------------------ Boucle

    private void tick() {
        ticks++;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, State>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> en = it.next();
            State st = en.getValue();
            Entity e = Bukkit.getEntity(en.getKey());
            st.until.values().removeIf(u -> u <= now);
            if (!(e instanceof LivingEntity t) || !t.isValid() || t.isDead()) {
                it.remove();
                continue;
            }
            if (st.until.isEmpty()) {
                release(t, st);
                it.remove();
                continue;
            }
            refresh(t, st);
            if (st.until.containsKey(Kind.FEAR) && t instanceof Mob m && ticks % 5 == 0) {
                flee(m, st);
            }
            if (ticks % 6 == 0) {
                Location head = t.getLocation().add(0, t.getHeight() + 0.25, 0);
                if (st.until.containsKey(Kind.STUN)) {
                    double a = ticks * 0.35;
                    t.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, head.clone().add(Math.cos(a) * 0.4, 0, Math.sin(a) * 0.4), 2, 0, 0, 0, 0);
                }
                if (st.until.containsKey(Kind.DISORIENT)) {
                    t.getWorld().spawnParticle(Particle.DUST, head, 3, 0.25, 0.05, 0.25, 0,
                            new Particle.DustOptions(Color.fromRGB(0xFF9AE0), 1.0f));
                }
                if (st.until.containsKey(Kind.FEAR)) {
                    t.getWorld().spawnParticle(Particle.SOUL, head, 1, 0.2, 0.05, 0.2, 0.01);
                }
                if (st.until.containsKey(Kind.ROOT)) {
                    t.getWorld().spawnParticle(Particle.BLOCK, t.getLocation().add(0, 0.1, 0), 4, 0.35, 0.02, 0.35, 0,
                            org.bukkit.Material.MANGROVE_ROOTS.createBlockData());
                }
                if (st.until.containsKey(Kind.SILENCE)) {
                    t.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, head, 1, 0.2, 0.05, 0.2, 0);
                }
            }
        }
        if (ticks % 200 == 0) {
            dr.values().forEach(m -> m.values().removeIf(d -> now - d.last > DR_WINDOW));
            dr.values().removeIf(Map::isEmpty);
        }
    }

    /** Met l'entité dans l'état voulu (attention du monstre, vitesse, saut). */
    private void refresh(LivingEntity t, State st) {
        long now = System.currentTimeMillis();
        boolean stun = st.until.getOrDefault(Kind.STUN, 0L) > now;
        boolean dis = st.until.getOrDefault(Kind.DISORIENT, 0L) > now;
        boolean fear = st.until.getOrDefault(Kind.FEAR, 0L) > now;
        boolean root = st.until.getOrDefault(Kind.ROOT, 0L) > now;
        if (t instanceof Mob m) {
            boolean frozen = stun || dis;
            if (frozen && m.isAware()) {
                m.setAware(false);
                st.madeUnaware = true;
            } else if (!frozen && st.madeUnaware) {
                m.setAware(true);
                st.madeUnaware = false;
            }
            if (fear || frozen) {
                m.setTarget(null);
            }
        }
        // Joueur étourdi/apeuré/désorienté ou toute entité immobilisée : ni marche ni saut.
        boolean still = root || (t instanceof Player && (stun || dis || fear));
        if (still != st.slowed) {
            setMods(t, still);
            st.slowed = still;
        }
    }

    private void flee(Mob m, State st) {
        Location from = st.fearFrom;
        Entity src = st.fearSource == null ? null : Bukkit.getEntity(st.fearSource);
        if (src != null && src.getWorld().equals(m.getWorld())) {
            from = src.getLocation();
        }
        if (from == null || !from.getWorld().equals(m.getWorld())) {
            return;
        }
        Vector away = m.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (away.lengthSquared() < 1.0E-4) {
            away = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
        }
        Location to = m.getLocation().add(away.normalize().multiply(7));
        m.setTarget(null);
        m.getPathfinder().moveTo(to, 1.3);
    }

    private void release(LivingEntity t, State st) {
        if (t instanceof Mob m && st.madeUnaware) {
            m.setAware(true);
        }
        if (st.slowed) {
            setMods(t, false);
        }
        if (t instanceof Player) {
            t.removePotionEffect(PotionEffectType.NAUSEA);
        }
    }

    private void setMods(LivingEntity t, boolean on) {
        mod(t.getAttribute(Attribute.MOVEMENT_SPEED), speedKey, on);
        mod(t.getAttribute(Attribute.JUMP_STRENGTH), jumpKey, on);
        if (on) {
            t.setVelocity(t.getVelocity().setX(0).setZ(0));
        }
    }

    private static void mod(AttributeInstance a, NamespacedKey key, boolean on) {
        if (a == null) {
            return;
        }
        a.removeModifier(key);
        if (on) {
            a.addTransientModifier(new AttributeModifier(key, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    // ------------------------------------------------------------------ Connexions

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Au cas où un ancien modificateur serait resté (arrêt brutal pendant un contrôle).
        mod(e.getPlayer().getAttribute(Attribute.MOVEMENT_SPEED), speedKey, false);
        mod(e.getPlayer().getAttribute(Attribute.JUMP_STRENGTH), jumpKey, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        State st = states.remove(e.getPlayer().getUniqueId());
        if (st != null) {
            release(e.getPlayer(), st);
        }
        // Les rendements décroissants restent : se déconnecter ne remet pas les compteurs à zéro.
    }

    static String fmt(double s) {
        return String.format(Locale.FRANCE, "%.1f", s);
    }
}
