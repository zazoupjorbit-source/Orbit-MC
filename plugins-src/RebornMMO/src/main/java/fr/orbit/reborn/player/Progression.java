package fr.orbit.reborn.player;

import fr.orbit.reborn.Keys;
import fr.orbit.reborn.RebornMMO;
import fr.orbit.reborn.skill.Skill;
import fr.orbit.reborn.skill.Skills;
import fr.orbit.reborn.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.HashSet;
import java.util.Set;

/** Niveaux, maîtrise des armes, déblocage des compétences et de la compétence unique. */
public final class Progression {

    private final RebornMMO plugin;

    public Progression(RebornMMO plugin) {
        this.plugin = plugin;
    }

    public int maxLevel() {
        return plugin.getConfig().getInt("progression.max-level", 150);
    }

    public void addXp(Player p, PlayerData d, long amount) {
        if (amount <= 0 || d.classId == null) {
            return;
        }
        Set<String> before = unlockedIds(d);
        int max = maxLevel();
        int perLevel = plugin.getConfig().getInt("progression.points-per-level", 3);
        d.xp += amount;
        int gained = 0;
        while (d.level < max && d.xp >= Stats.xpToNext(d.level)) {
            d.xp -= Stats.xpToNext(d.level);
            d.level++;
            d.points += perLevel;
            gained++;
        }
        if (d.level >= max) {
            d.xp = 0;
        }
        if (gained > 0) {
            d.hp = Stats.maxHp(d);
            plugin.combat().sync(p, d);
            Text.title(p, "<gradient:#FFD66E:#FFF3B0><b>Niveau supérieur !</b></gradient>",
                    "<white>Niveau " + d.level + "</white> <#8C96A3>· +" + (gained * perLevel) + " points de statut (/mmo)", 5, 45, 15);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.1f);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.6f);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.25);
            checkDualBlade(p, d);
            announceUnlocks(p, d, before);
        }
        updateExpBar(p, d);
    }

    public void addProficiency(Player p, PlayerData d, String tree, int amount) {
        int old = d.proficiency(tree);
        int now = Math.min(1000, old + amount);
        if (now == old) {
            return;
        }
        Set<String> before = unlockedIds(d);
        d.proficiency.put(tree, now);
        checkDualBlade(p, d);
        announceUnlocks(p, d, before);
    }

    public void setProficiency(Player p, PlayerData d, String tree, int value) {
        Set<String> before = unlockedIds(d);
        d.proficiency.put(tree, Math.max(0, Math.min(1000, value)));
        checkDualBlade(p, d);
        announceUnlocks(p, d, before);
    }

    private Set<String> unlockedIds(PlayerData d) {
        Set<String> ids = new HashSet<>();
        for (Skill s : Skills.available(d)) {
            ids.add(s.id);
        }
        return ids;
    }

    public void announceUnlocks(Player p, PlayerData d, Set<String> before) {
        for (Skill s : Skills.available(d)) {
            if (before.contains(s.id)) {
                continue;
            }
            Text.msg(p, "<#FFD66E>✦ Nouvelle compétence d'épée : <" + s.hex + "><b>" + s.name + "</b></" + s.hex + "> <#8C96A3>(" + s.desc + ")");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.4f);
            if (d.equipped.size() < 4 && !d.equipped.contains(s.id)) {
                d.equipped.add(s.id);
                Text.msg(p, "<#8C96A3>Équipée automatiquement. Gérez vos compétences avec <white>/mmo</white>.");
            }
        }
    }

    /** Compétence unique : réservée à l'Épéiste, éveillée au niveau et à la maîtrise configurés. */
    public void checkDualBlade(Player p, PlayerData d) {
        if (d.dualBlade || !"epeiste".equals(d.classId)) {
            return;
        }
        int level = plugin.getConfig().getInt("unique-skill.dual-blade.level", 25);
        int prof = plugin.getConfig().getInt("unique-skill.dual-blade.proficiency", 600);
        if (d.level < level || d.proficiency("epeiste") < prof) {
            return;
        }
        grantDualBlade(p, d);
    }

    public void grantDualBlade(Player p, PlayerData d) {
        Set<String> before = unlockedIds(d);
        d.dualBlade = true;
        Text.title(p, "<gradient:#7FD4FF:#FFFFFF><b>Compétence unique</b></gradient>", "<#D7DCE2>Double lame éveillée", 10, 70, 20);
        p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.7f, 1.4f);
        Bukkit.broadcast(Text.mm(Text.PREFIX + "<gradient:#7FD4FF:#FFFFFF><b>Compétence unique</b></gradient> <#D7DCE2>"
                + Text.esc(p.getName()) + " a éveillé la <b>Double lame</b>."));
        Text.msg(p, "<#8C96A3>Tenez une épée dans chaque main pour utiliser ses compétences.");
        announceUnlocks(p, d, before);
    }

    /** Barre d'expérience vanilla = progression du niveau MMO. */
    public void updateExpBar(Player p, PlayerData d) {
        p.setLevel(d.level);
        double need = Stats.xpToNext(d.level);
        p.setExp((float) Math.max(0, Math.min(0.999, d.xp / need)));
    }

    /** Agilité : +0,3 % de vitesse de déplacement par point (30 % maximum). */
    public void applyAgility(Player p, PlayerData d) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(Keys.AGILITY);
        double bonus = Math.min(0.3, d.agi * 0.003);
        if (bonus > 0) {
            speed.addModifier(new AttributeModifier(Keys.AGILITY, bonus, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
    }
}
