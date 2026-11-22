package fr.orbit.mmo.spell;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.mob.MobState;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.util.Fx;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Effets temporaires (buffs et affaiblissements) sur les joueurs et les monstres.
 * Types : reduction, bonus-degats, bonus-crit, bouclier (absorption restante), soin-par-sec (PV/s),
 * affaiblir (% de dégâts en moins), saignement (dégâts/s).
 */
public final class Auras {

    public static final class Aura {
        public final String kind;
        public final String spell;
        public double value;
        public long until;
        public final UUID source;
        public final String hex;

        Aura(String kind, String spell, double value, long until, UUID source, String hex) {
            this.kind = kind;
            this.spell = spell;
            this.value = value;
            this.until = until;
            this.source = source;
            this.hex = hex;
        }
    }

    private final OrbitMMO plugin;
    private final Map<UUID, Map<String, Aura>> auras = new HashMap<>();

    public Auras(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** Pose (ou renouvelle) un effet. Le même sort ne se cumule pas avec lui-même. */
    public void apply(Entity target, String kind, String spell, double value, double seconds, UUID source, String hex) {
        long until = System.currentTimeMillis() + (long) (seconds * 1000);
        auras.computeIfAbsent(target.getUniqueId(), k -> new HashMap<>())
                .put(kind + ":" + spell, new Aura(kind, spell, value, until, source, hex));
    }

    public void clear(UUID id) {
        auras.remove(id);
    }

    public boolean has(Entity e, String kind) {
        return sum(e.getUniqueId(), kind) > 0;
    }

    /** Somme des valeurs actives d'un type d'effet. */
    public double sum(UUID id, String kind) {
        Map<String, Aura> m = auras.get(id);
        if (m == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        double s = 0;
        for (Aura a : m.values()) {
            if (a.kind.equals(kind) && a.until > now) {
                s += a.value;
            }
        }
        return s;
    }

    /** Absorbe des dégâts avec les boucliers actifs ; rend les dégâts restants. */
    public double absorb(UUID id, double dmg) {
        Map<String, Aura> m = auras.get(id);
        if (m == null) {
            return dmg;
        }
        long now = System.currentTimeMillis();
        for (Iterator<Aura> it = m.values().iterator(); it.hasNext() && dmg > 0; ) {
            Aura a = it.next();
            if (!a.kind.equals("bouclier") || a.until <= now) {
                continue;
            }
            double used = Math.min(a.value, dmg);
            a.value -= used;
            dmg -= used;
            if (a.value <= 0) {
                it.remove();
            }
        }
        return dmg;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Map<String, Aura>>> it = auras.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<String, Aura>> e = it.next();
            Entity ent = Bukkit.getEntity(e.getKey());
            if (!(ent instanceof LivingEntity le) || !le.isValid() || le.isDead()) {
                it.remove();
                continue;
            }
            // Copie : un dégât peut tuer la cible et vider ses effets pendant la boucle.
            for (Aura a : Map.copyOf(e.getValue()).values()) {
                if (a.until <= now) {
                    e.getValue().values().remove(a);
                    continue;
                }
                switch (a.kind) {
                    case "saignement" -> dot(le, a);
                    case "soin-par-sec" -> hot(le, a);
                    default -> {
                    }
                }
            }
            if (e.getValue().isEmpty()) {
                it.remove();
            }
        }
    }

    private void dot(LivingEntity target, Aura a) {
        Player src = a.source == null ? null : Bukkit.getPlayer(a.source);
        Fx.dust(target.getLocation().add(0, target.getHeight() * 0.6, 0), Fx.color(a.hex), 1.2f, 8, 0.3);
        MobState ms = plugin.mobs().state(target);
        if (ms != null) {
            plugin.combat().damageMob(src, ms, a.value);
        } else if (target instanceof Player v && src != null && plugin.combat().pvpAllowed(src, v)) {
            plugin.combat().damagePlayer(v, a.value, src, true);
        }
    }

    private void hot(LivingEntity target, Aura a) {
        if (!(target instanceof Player p)) {
            return;
        }
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        if (d != null && d.hp > 0) {
            plugin.combat().heal(p, d, a.value);
            Fx.dust(p.getLocation().add(0, 1, 0), Fx.color(a.hex), 1.0f, 5, 0.4);
        }
    }
}
