package fr.orbit.mmo.player;

import fr.orbit.mmo.Keys;
import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.classes.ClassDef;
import fr.orbit.mmo.classes.Mods;
import fr.orbit.mmo.classes.Resource;
import fr.orbit.mmo.classes.SpecDef;
import fr.orbit.mmo.classes.SpellDef;
import fr.orbit.mmo.classes.TalentDef;
import fr.orbit.mmo.clazz.WeaponType;
import fr.orbit.mmo.item.Boost;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.util.Text;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Niveaux, spécialisation, talents, sorts connus et bonus du personnage. */
public final class Progression {

    private final OrbitMMO plugin;

    public Progression(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    public int maxLevel() {
        return plugin.getConfig().getInt("progression.max-level", 80);
    }

    /** Niveau où l'on choisit sa spécialisation et où les talents commencent. */
    public int specLevel() {
        return plugin.getConfig().getInt("progression.spec-level", 10);
    }

    // ------------------------------------------------------------------ Accès

    public ClassDef cls(PlayerData d) {
        return plugin.classes().get(d.classId);
    }

    public SpecDef spec(PlayerData d) {
        SpecDef s = plugin.classes().spec(d.specId);
        return s != null && s.classId.equals(d.classId) ? s : null;
    }

    /** Personnage jouable : faction + classe connue. */
    public boolean valid(PlayerData d) {
        return d != null && d.faction != null && cls(d) != null;
    }

    public Resource resource(PlayerData d) {
        SpecDef s = spec(d);
        if (s != null && s.resource != null) {
            return s.resource;
        }
        ClassDef c = cls(d);
        return c == null ? Resource.MANA : c.resource;
    }

    public int talentPointsTotal(PlayerData d) {
        return Math.max(0, d.level - specLevel() + 1);
    }

    public int talentPointsFree(PlayerData d) {
        return d.specId == null ? 0 : Math.max(0, talentPointsTotal(d) - d.spent(d.specId));
    }

    // ------------------------------------------------------------------ Sorts connus

    public boolean knows(PlayerData d, SpellDef s) {
        if (s == null || !s.classId.equals(d.classId) || d.level < s.level) {
            return false;
        }
        if (s.faction != null && s.faction != d.faction) {
            return false;
        }
        if (!s.specs.isEmpty() && (d.specId == null || !s.specs.contains(d.specId))) {
            return false;
        }
        if (s.talentOnly) {
            SpecDef spec = spec(d);
            if (spec == null) {
                return false;
            }
            for (TalentDef t : spec.talents) {
                if (s.id.equals(t.spell) && d.rank(spec.id, t.id) > 0) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    public List<SpellDef> known(PlayerData d) {
        List<SpellDef> out = new ArrayList<>();
        ClassDef c = cls(d);
        if (c == null) {
            return out;
        }
        for (SpellDef s : c.spells.values()) {
            if (knows(d, s)) {
                out.add(s);
            }
        }
        return out;
    }

    private Set<String> knownIds(PlayerData d) {
        Set<String> ids = new HashSet<>();
        for (SpellDef s : known(d)) {
            ids.add(s.id);
        }
        return ids;
    }

    /** Annonce les nouveaux sorts ; ils rejoignent leur objet (Attaque, Déplacement ou Buff). */
    public void learnNew(Player p, PlayerData d, Set<String> before) {
        if (before.contains("*")) {
            plugin.bar().sync(p, d);
            return; // création du personnage : pas d'annonce sort par sort
        }
        for (SpellDef s : known(d)) {
            if (before.contains(s.id)) {
                continue;
            }
            Text.msg(p, "<#FFD66E>✦ Nouveau sort : <b>" + s.tag() + "</b> <#5A6270>(objet " + s.category.tag()
                    + "<#5A6270>, Accroupi + molette pour le choisir) <#8C96A3>— " + s.desc);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.5f);
        }
        plugin.bar().sync(p, d);
    }

    // ------------------------------------------------------------------ Bonus

    /** Recalcule les bonus (passifs + talents), les PV max et la vitesse. */
    public void recompute(Player p, PlayerData d) {
        Mods m = new Mods();
        ClassDef c = cls(d);
        SpecDef s = spec(d);
        if (s != null) {
            m.add(s.passives, 1);
            Map<String, Integer> tree = d.tree(s.id);
            for (TalentDef t : s.talents) {
                int r = tree.getOrDefault(t.id, 0);
                if (r > 0) {
                    m.add(t.effects, r);
                }
            }
        }
        long now = System.currentTimeMillis();
        for (Boost b : Boost.values()) {
            Long until = d.boosts.get(b.id);
            if (until != null && until > now) {
                m.add(Map.of(b.mod, b.value), 1);
            }
        }
        if (p != null) {
            // Bonus de l'équipement porté (raretés, runes), si le niveau requis est atteint.
            ItemStack weapon = plugin.bar().weapon(p, d);
            fr.orbit.mmo.item.Loot.addGearMods(m, p, d, weapon);
            d.gearSig = fr.orbit.mmo.item.Loot.signature(p, weapon);
        }
        d.mods = m;
        d.hpMult = c == null ? 1 : c.hpMult;
        if (p != null) {
            applySpeed(p, d);
            plugin.combat().sync(p, d);
        }
    }

    // ------------------------------------------------------------------ Effets achetés en ville

    /** Active (ou relance) un effet : il compte dans les bonus jusqu'à sa fin. */
    public void applyBoost(Player p, PlayerData d, Boost b) {
        boolean renew = d.boosts.getOrDefault(b.id, 0L) > System.currentTimeMillis();
        d.boosts.put(b.id, System.currentTimeMillis() + b.seconds * 1000L);
        recompute(p, d);
        p.playSound(p.getLocation(), b.material == Material.POTION ? Sound.ENTITY_GENERIC_DRINK : Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.3f);
        p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1.2, 0), 30, 0.4, 0.6, 0.4, 0.6);
        fr.orbit.mmo.util.Fx.dust(p.getLocation().add(0, 1, 0), fr.orbit.mmo.util.Fx.color(b.hex), 1.3f, 18, 0.5);
        plugin.hud().flash(p, "<" + b.hex + "><b>" + b.name + "</b> <#D7DCE2>" + b.effect + " <#8C96A3>· " + b.duration()
                + (renew ? " <#5A6270>(relancé)" : ""), 50);
    }

    /** Retire les effets terminés (appelé chaque seconde). */
    public void tickBoosts() {
        long now = System.currentTimeMillis();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            PlayerData d = plugin.players().loaded(p.getUniqueId());
            if (d == null) {
                continue;
            }
            // Équipement changé (arme, armure, bouclier) : bonus recalculés.
            if (valid(d) && fr.orbit.mmo.item.Loot.signature(p, plugin.bar().weapon(p, d)) != d.gearSig) {
                plugin.binding().checkWorn(p, d); // type d'armure, liaison, garde-robe
                recompute(p, d);
                d.hp = Math.min(d.hp, Stats.maxHp(d));
                plugin.combat().sync(p, d);
            }
            if (d.boosts.isEmpty()) {
                continue;
            }
            boolean changed = false;
            for (var it = d.boosts.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                if (e.getValue() > now) {
                    continue;
                }
                it.remove();
                changed = true;
                Boost b = Boost.byId(e.getKey());
                if (b != null) {
                    Text.msg(p, b.tag() + " <#8C96A3>: effet terminé.");
                }
            }
            if (changed && valid(d)) {
                recompute(p, d);
                d.hp = Math.min(d.hp, Stats.maxHp(d));
                plugin.combat().sync(p, d);
            }
        }
    }

    private void applySpeed(Player p, PlayerData d) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(Keys.SPEED);
        double bonus = Math.min(0.4, d.mods.get("vitesse") / 100.0);
        if (bonus != 0) {
            speed.addModifier(new AttributeModifier(Keys.SPEED, bonus, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
    }

    // ------------------------------------------------------------------ Niveaux

    public void addXp(Player p, PlayerData d, long amount) {
        if (amount <= 0 || !valid(d)) {
            return;
        }
        Set<String> before = knownIds(d);
        int max = maxLevel();
        d.xp += amount;
        int gained = 0;
        while (d.level < max && d.xp >= Stats.xpToNext(d.level)) {
            d.xp -= Stats.xpToNext(d.level);
            d.level++;
            gained++;
        }
        if (d.level >= max) {
            d.xp = 0;
        }
        if (gained > 0) {
            recompute(p, d);
            d.hp = Stats.maxHp(d);
            plugin.combat().sync(p, d);
            String sub = "<white>Niveau " + d.level + "</white>";
            if (d.specId != null && d.level >= specLevel()) {
                sub += " <#8C96A3>· +" + Math.min(gained, d.level - specLevel() + 1) + " point(s) de talent (/mmo)";
            }
            Text.title(p, "<gradient:#FFD66E:#FFF3B0><b>Niveau supérieur !</b></gradient>", sub, 5, 45, 15);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.1f);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.25);
            if (d.level - gained < specLevel() && d.level >= specLevel()) {
                Text.msg(p, "<#FFD66E>✦ Talents débloqués : 1 point par niveau, à placer dans <white>/mmo</white>.");
            }
            learnNew(p, d, before);
        }
        updateExpBar(p, d);
    }

    /** Fixe le niveau (commande d'admin). */
    public void setLevel(Player p, PlayerData d, int level) {
        Set<String> before = knownIds(d);
        d.level = Math.max(1, Math.min(maxLevel(), level));
        d.xp = 0;
        // Trop de points dépensés pour le nouveau niveau : l'arbre est remis à zéro.
        if (d.specId != null && d.spent(d.specId) > talentPointsTotal(d)) {
            d.tree(d.specId).clear();
        }
        recompute(p, d);
        d.hp = Stats.maxHp(d);
        learnNew(p, d, before);
        updateExpBar(p, d);
    }

    public void updateExpBar(Player p, PlayerData d) {
        if (plugin.pack().isLoaded(p.getUniqueId())) {
            // Pack chargé : niveau et expérience sont dans la console du HUD ; le chiffre vert du jeu est masqué.
            p.setLevel(0);
            p.setExp(0f);
            return;
        }
        p.setLevel(d.level);
        double need = Stats.xpToNext(d.level);
        p.setExp((float) Math.max(0, Math.min(0.999, d.xp / need)));
    }

    // ------------------------------------------------------------------ Spécialisation et talents

    /** Choisit (création) ou change (en ville) la spécialisation. */
    public void chooseSpec(Player p, PlayerData d, SpecDef spec) {
        if (spec.faction != null && spec.faction != d.faction) {
            return;
        }
        Set<String> before = knownIds(d);
        boolean first = d.specId == null;
        d.specId = spec.id;
        d.resource = resource(d).startsFull ? resource(d).max : 0;
        recompute(p, d);
        giveSpecGear(p, d, spec);
        Text.title(p, "<" + spec.hex + "><b>" + spec.name + "</b>", spec.role.tag(), 10, 50, 15);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.2f);
        Text.msg(p, "<#D7DCE2>Spécialisation : <b>" + spec.tag() + "</b> " + spec.role.tag()
                + (d.level < specLevel() ? " <#8C96A3>— talents à partir du niveau " + specLevel() + "."
                : first ? " <#8C96A3>— vos points de talent vous attendent dans /mmo." : ""));
        learnNew(p, d, before);
        plugin.players().save(d);
    }

    /**
     * Personnage créé avant les baguettes et grimoires : reçoit l'arme magique de sa spécialisation s'il ne l'a pas
     * (l'ancien bâton reste dans son inventaire).
     */
    public void ensureMagicWeapon(Player p, PlayerData d) {
        SpecDef spec = valid(d) ? spec(d) : null;
        if (spec != null && spec.weapon != null && spec.weapon.magic() && !hasWeapon(p, spec.weapon)) {
            giveSpecGear(p, d, spec);
        }
    }

    /** Donne l'arme (et le bouclier) de la spécialisation si le joueur n'en a pas. */
    private void giveSpecGear(Player p, PlayerData d, SpecDef spec) {
        if (spec.weapon != null && !hasWeapon(p, spec.weapon)) {
            ItemStack w = Items.weapon(spec.weapon, 0);
            ItemStack current = p.getInventory().getItem(0);
            p.getInventory().setItem(0, null);
            if (current != null && !current.getType().isAir()) {
                if (fr.orbit.mmo.spell.SpellBar.isLocked(current)) {
                    // Objet de sort ou « Mon personnage » : jamais lâché au sol.
                    p.getInventory().addItem(current);
                } else {
                    Items.give(p, current);
                }
            }
            p.getInventory().setItem(0, w);
            d.weaponSlot = 0;
            Text.msg(p, "<#8C96A3>Vous recevez : " + spec.weapon.label + ".");
        }
        if (spec.shield && p.getInventory().getItemInOffHand().getType() != Material.SHIELD) {
            p.getInventory().setItemInOffHand(Items.shield());
        }
    }

    private boolean hasWeapon(Player p, WeaponType type) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (fr.orbit.mmo.spell.SpellBar.isWeapon(it) && WeaponType.of(it) == type) {
                return true;
            }
        }
        return false;
    }

    /** Explique pourquoi un rang de talent ne peut pas être pris (null = possible). */
    public String cannotInvest(PlayerData d, SpecDef spec, TalentDef t) {
        if (!spec.id.equals(d.specId)) {
            return "Ce n'est pas votre spécialisation active.";
        }
        int rank = d.rank(spec.id, t.id);
        if (rank >= t.maxRank) {
            return "Rang maximum atteint.";
        }
        if (talentPointsFree(d) <= 0) {
            return d.level < specLevel() ? "Talents disponibles au niveau " + specLevel() + "." : "Aucun point de talent disponible.";
        }
        if (d.spent(spec.id) < t.requiredPoints()) {
            return "Palier verrouillé : " + t.requiredPoints() + " points à dépenser dans l'arbre.";
        }
        if (t.requires != null) {
            TalentDef req = spec.talent(t.requires);
            if (req != null && d.rank(spec.id, req.id) < req.maxRank) {
                return "Requiert " + req.name + " (" + req.maxRank + "/" + req.maxRank + ").";
            }
        }
        return null;
    }

    public boolean invest(Player p, PlayerData d, SpecDef spec, TalentDef t) {
        String why = cannotInvest(d, spec, t);
        if (why != null) {
            Text.msg(p, "<#FF5C5C>" + why);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return false;
        }
        Set<String> before = knownIds(d);
        d.tree(spec.id).merge(t.id, 1, Integer::sum);
        recompute(p, d);
        learnNew(p, d, before);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.4f);
        return true;
    }

    /** Prix d'une remise à zéro des talents (gratuit avant le niveau 20). */
    public long resetPrice(PlayerData d) {
        return d.level < 20 ? 0 : 10L * d.level;
    }

    public void resetTalents(Player p, PlayerData d) {
        if (d.specId == null) {
            return;
        }
        d.tree(d.specId).clear();
        recompute(p, d);
        plugin.bar().sync(p, d);
    }
}
