package fr.orbit.mmo.mob;

import fr.orbit.mmo.player.Stats;
import fr.orbit.mmo.registry.Defs.BossDef;
import fr.orbit.mmo.registry.Defs.MobDef;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Un monstre en jeu : PV virtuels, niveau, contributeurs (pour les récompenses). */
public final class MobState {

    public final LivingEntity entity;
    public final MobDef def;
    public final int level;
    public final int floor;
    /** Non nul pour un boss d'étage. */
    public final BossDef boss;
    public double maxHp;
    public double hp;
    public double damageMult = 1;
    public final Map<UUID, Double> damagers = new HashMap<>();
    public UUID lastHitter;
    public UUID lastSkillBy;
    public long lastSkillAt;
    public boolean dead;
    /** Retiré sans avoir été tué (plus de joueur à portée, entrée en zone sûre). */
    public boolean despawned;
    public boolean revealed;
    /** Dernier coup de mêlée porté à un joueur (ms), pour le délai entre deux attaques dehors. */
    public long lastMeleeAt;

    MobState(LivingEntity entity, MobDef def, int level, int floor, BossDef boss, double maxHp) {
        this.entity = entity;
        this.def = def;
        this.level = level;
        this.floor = floor;
        this.boss = boss;
        this.maxHp = maxHp;
        this.hp = maxHp;
    }

    public double damage() {
        return Stats.mobDamage(level) * def.damage() * damageMult;
    }
}
