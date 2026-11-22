package fr.orbit.reborn.mob;

import fr.orbit.reborn.player.Stats;
import fr.orbit.reborn.registry.Defs.BossDef;
import fr.orbit.reborn.registry.Defs.MobDef;
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
    public boolean revealed;

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
