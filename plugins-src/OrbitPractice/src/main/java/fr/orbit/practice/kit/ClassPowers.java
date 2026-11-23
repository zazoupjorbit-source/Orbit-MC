package fr.orbit.practice.kit;

import fr.orbit.practice.Ladder;
import fr.orbit.practice.Msg;
import fr.orbit.practice.match.Match;
import fr.orbit.practice.match.MatchManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pouvoirs des classes HCF, actifs seulement pendant une manche du type « Classes HCF » :
 * effets passifs selon l'armure, énergie et objets du Barde, marque de l'Archer, coup dans le dos du Voleur.
 * Mêmes valeurs que le module Classes d'OrbitHCF.
 */
public final class ClassPowers implements Listener {

    /** Un objet de Barde : aura quand il est tenu, effet fort au clic droit contre de l'énergie. */
    private record BardItem(String label, PotionEffectType aura, int auraLevel, PotionEffectType active,
                            int activeLevel, int seconds, int cost, boolean offensive) {}

    private static final Map<Material, BardItem> BARD_ITEMS = Map.of(
            Material.BLAZE_POWDER, new BardItem("Force II", PotionEffectType.STRENGTH, 1, PotionEffectType.STRENGTH, 2, 8, 45, false),
            Material.SUGAR, new BardItem("Vitesse III", PotionEffectType.SPEED, 2, PotionEffectType.SPEED, 3, 8, 20, false),
            Material.FEATHER, new BardItem("Saut IV", PotionEffectType.JUMP_BOOST, 2, PotionEffectType.JUMP_BOOST, 4, 10, 25, false),
            Material.GHAST_TEAR, new BardItem("Régénération III", PotionEffectType.REGENERATION, 1, PotionEffectType.REGENERATION, 3, 6, 40, false),
            Material.IRON_INGOT, new BardItem("Résistance II", PotionEffectType.RESISTANCE, 1, PotionEffectType.RESISTANCE, 2, 6, 40, false),
            Material.SPIDER_EYE, new BardItem("Wither II", null, 0, PotionEffectType.WITHER, 2, 6, 50, true));

    private final Plugin plugin;
    private final MatchManager matches;
    private final Map<UUID, HcfClass> active = new HashMap<>();
    private final Map<UUID, Double> energy = new HashMap<>();
    private final Map<UUID, Long> bardReady = new HashMap<>();
    private final Map<UUID, Long> rogueReady = new HashMap<>();
    private final Map<UUID, Long> markedUntil = new HashMap<>();

    public ClassPowers(Plugin plugin, MatchManager matches) {
        this.plugin = plugin;
        this.matches = matches;
    }

    private double cfg(String path, double def) {
        return plugin.getConfig().getDouble("classes." + path, def);
    }

    // ------------------------------------------------------------------ Tick (1 fois par seconde)

    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Match match = matches.matchOf(id);
            boolean playing = match != null && match.state() == Match.State.LIVE
                    && match.ladder() == Ladder.HCF && match.isAlive(id);
            if (!playing) {
                forget(id);
                continue;
            }
            HcfClass detected = detect(p);
            if (detected != active.get(id)) {
                if (detected == null) {
                    active.remove(id);
                } else {
                    active.put(id, detected);
                    p.sendMessage(Msg.msg("Classe " + Msg.OR + detected.label + " <gray>activée."));
                }
            }
            if (detected == null) {
                continue;
            }
            switch (detected) {
                case BARD -> {
                    passive(p, PotionEffectType.SPEED, 2);
                    passive(p, PotionEffectType.REGENERATION, 1);
                    tickBard(p, match);
                }
                case ARCHER, ROGUE -> {
                    passive(p, PotionEffectType.SPEED, 2);
                    passive(p, PotionEffectType.JUMP_BOOST, 2);
                }
                default -> { }
            }
        }
    }

    private void tickBard(Player p, Match match) {
        UUID id = p.getUniqueId();
        double max = cfg("barde.energie-max", 100);
        double value = Math.min(max, energy.getOrDefault(id, 0.0) + cfg("barde.energie-par-seconde", 1));
        energy.put(id, value);
        BardItem held = BARD_ITEMS.get(p.getInventory().getItemInMainHand().getType());
        if (held != null && held.aura() != null) {
            for (Player mate : inRange(p, match.teamOf(id), match)) {
                mate.addPotionEffect(new PotionEffect(held.aura(), 100, held.auraLevel() - 1, true, false, true));
            }
        }
        long wait = bardReady.getOrDefault(id, 0L) - System.currentTimeMillis();
        p.sendActionBar(Msg.raw(Msg.OR + "Énergie <white>" + (int) value + "<gray>/" + (int) max
                + (wait > 0 ? " <dark_gray>· <gray>recharge <white>" + (wait / 1000 + 1) + " s" : "")));
    }

    /** Membres vivants de la liste, dans le même monde et à portée du Barde (lui compris). */
    private List<Player> inRange(Player source, List<UUID> team, Match match) {
        double range = cfg("barde.portee", 20);
        List<Player> result = new java.util.ArrayList<>();
        for (UUID u : team) {
            Player t = Bukkit.getPlayer(u);
            if (t != null && match.isAlive(u) && t.getWorld() == source.getWorld()
                    && t.getLocation().distanceSquared(source.getLocation()) <= range * range) {
                result.add(t);
            }
        }
        return result;
    }

    private static void passive(Player p, PotionEffectType type, int level) {
        p.addPotionEffect(new PotionEffect(type, 60, level - 1, true, false, true));
    }

    private static HcfClass detect(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] pieces = {inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()};
        String[] suffixes = {"_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS"};
        for (HcfClass c : HcfClass.values()) {
            boolean full = true;
            for (int i = 0; i < 4 && full; i++) {
                full = pieces[i] != null && pieces[i].getType().name().equals(c.armor + suffixes[i]);
            }
            if (full) {
                return c;
            }
        }
        return null;
    }

    private void forget(UUID id) {
        active.remove(id);
        energy.remove(id);
        bardReady.remove(id);
        rogueReady.remove(id);
        markedUntil.remove(id);
    }

    // ------------------------------------------------------------------ Barde : effet au clic droit

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        ItemStack item = event.getItem();
        if (item == null || active.get(id) != HcfClass.BARD) {
            return;
        }
        BardItem power = BARD_ITEMS.get(item.getType());
        Match match = matches.matchOf(id);
        if (power == null || match == null || match.state() != Match.State.LIVE) {
            return;
        }
        event.setCancelled(true); // l'œil d'araignée ne se mange pas
        long now = System.currentTimeMillis();
        long wait = bardReady.getOrDefault(id, 0L) - now;
        if (wait > 0) {
            p.sendMessage(Msg.msg("<red>Pouvoir en recharge : encore " + (wait / 1000 + 1) + " s."));
            return;
        }
        double value = energy.getOrDefault(id, 0.0);
        if (value < power.cost()) {
            p.sendMessage(Msg.msg("<red>Pas assez d'énergie : " + power.cost() + " nécessaires, tu en as " + (int) value + "."));
            return;
        }
        List<Player> targets = inRange(p, power.offensive() ? match.enemiesOf(id) : match.teamOf(id), match);
        for (Player t : targets) {
            t.addPotionEffect(new PotionEffect(power.active(), power.seconds() * 20, power.activeLevel() - 1, false, true, true));
        }
        energy.put(id, value - power.cost());
        bardReady.put(id, now + (long) (cfg("barde.recharge-secondes", 10) * 1000));
        item.setAmount(item.getAmount() - 1);
        p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1.6f);
        p.sendMessage(Msg.msg(Msg.OR + power.label() + " <gray>pendant " + power.seconds() + " s"
                + (power.offensive() ? " sur " + targets.size() + " ennemi(s)." : " pour ton équipe.")));
    }

    // ------------------------------------------------------------------ Archer et Voleur : dégâts

    /**
     * Appelé pour chaque coup valide d'une manche en cours. Renvoie les dégâts de base corrigés :
     * cible marquée par un Archer, coup dans le dos d'un Voleur.
     */
    public double modify(Player victim, Player attacker, Entity damager, double damage) {
        long now = System.currentTimeMillis();
        UUID victimId = victim.getUniqueId();
        if (markedUntil.getOrDefault(victimId, 0L) > now) {
            damage *= cfg("archer.multiplicateur", 1.25);
        }
        if (attacker == null) {
            return damage;
        }
        UUID attackerId = attacker.getUniqueId();
        HcfClass c = active.get(attackerId);
        if (c == HcfClass.ARCHER && damager instanceof AbstractArrow) {
            int seconds = (int) cfg("archer.marque-secondes", 10);
            boolean fresh = markedUntil.getOrDefault(victimId, 0L) <= now;
            markedUntil.put(victimId, now + seconds * 1000L);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, seconds * 20, 0, false, false, false));
            if (fresh) {
                victim.sendMessage(Msg.msg("<red>Tu es marqué par un Archer : +25 % de dégâts subis pendant " + seconds + " s."));
                attacker.sendMessage(Msg.msg("Cible " + Msg.OR + "marquée<gray> pendant " + seconds + " s."));
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 0.6f);
            }
        } else if (c == HcfClass.ROGUE && damager == attacker
                && attacker.getInventory().getItemInMainHand().getType() == Material.GOLDEN_SWORD
                && rogueReady.getOrDefault(attackerId, 0L) <= now && isBehind(victim, attacker)) {
            damage += cfg("voleur.degats-bonus", 6.0);
            rogueReady.put(attackerId, now + (long) (cfg("voleur.recharge-secondes", 8) * 1000));
            attacker.getInventory().setItemInMainHand(null);
            attacker.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);
            attacker.sendMessage(Msg.msg(Msg.OR + "Coup dans le dos !"));
            victim.sendMessage(Msg.msg("<red>Un Voleur t'a frappé dans le dos."));
        }
        return damage;
    }

    /** Vrai si l'attaquant est hors du champ de vision de la victime (angle réglable, 110° par défaut). */
    private boolean isBehind(Player victim, Player attacker) {
        Vector look = victim.getLocation().getDirection().setY(0);
        Vector toAttacker = attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
        if (look.lengthSquared() < 1.0E-6 || toAttacker.lengthSquared() < 1.0E-6) {
            return false;
        }
        return Math.toDegrees(look.angle(toAttacker)) >= cfg("voleur.angle", 110);
    }
}
