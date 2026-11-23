package fr.orbit.practice;

import fr.orbit.practice.kit.ClassPowers;
import fr.orbit.practice.match.Match;
import fr.orbit.practice.match.MatchManager;
import fr.orbit.practice.match.QueueManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/** Branche le Practice sur les événements du serveur : hub protégé, dégâts de combat, perles, menus. */
public final class PracticeListener implements Listener {

    private final Plugin plugin;
    private final MatchManager matches;
    private final QueueManager queue;
    private final EloStore elo;
    private final Lobby lobby;
    private final QueueMenu menu;
    private final ClassPowers powers;
    private final Commands commands;

    public PracticeListener(Plugin plugin, MatchManager matches, QueueManager queue, EloStore elo, Lobby lobby,
                            QueueMenu menu, ClassPowers powers, Commands commands) {
        this.plugin = plugin;
        this.matches = matches;
        this.queue = queue;
        this.elo = elo;
        this.lobby = lobby;
        this.menu = menu;
        this.powers = powers;
        this.commands = commands;
    }

    private boolean builder(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("practice.admin");
    }

    // ------------------------------------------------------------------ Connexion

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        elo.load(p.getUniqueId(), p.getName());
        matches.resetAttackSpeed(p);
        // Deux ticks plus tard : après la téléportation au spawn faite par OrbitDevCore.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !builder(p) && !HcfZone.is(p)) { // reconnecté en HCF : il y reste
                lobby.send(p);
            }
        }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        matches.forfeit(id);
        if (matches.isSpectating(id)) {
            matches.stopSpectating(id);
        }
        queue.purge(p);
        elo.unload(id);
    }

    // ------------------------------------------------------------------ Dégâts

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (HcfZone.is(victim)) {
            return; // mode HCF : dégâts gérés par OrbitHCF
        }
        Match match = matches.matchOf(victim.getUniqueId());
        if (match == null) {
            // Hub et spectateurs : personne ne prend de dégâts ; une chute dans le vide ramène au hub.
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID && !builder(victim)) {
                lobby.send(victim);
            }
            return;
        }
        if (match.state() != Match.State.LIVE || !match.isAlive(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        Player attacker = null;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof Player p) {
                attacker = p;
            } else if (byEntity.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
                attacker = shooter;
            }
            if (attacker != null && attacker != victim) {
                UUID a = attacker.getUniqueId();
                if (matches.matchOf(a) != match || !match.isAlive(a) || match.sameTeam(a, victim.getUniqueId())) {
                    event.setCancelled(true); // étranger au combat, joueur à terre ou coéquipier
                    return;
                }
            }
            if (match.ladder() == Ladder.HCF && attacker != victim) {
                event.setDamage(powers.modify(victim, attacker, byEntity.getDamager(), event.getDamage()));
            }
        } else if (match.ladder() == Ladder.HCF) {
            event.setDamage(powers.modify(victim, null, null, event.getDamage()));
        }
        // Sumo et Boxing : aucun dégât, seul le recul compte (et le nombre de coups en Boxing).
        Ladder.Rule rule = match.ladder().rule;
        if (rule == Ladder.Rule.SUMO || rule == Ladder.Rule.BOXING) {
            if (event instanceof EntityDamageByEntityEvent hit && hit.getDamager() instanceof Player hitter
                    && hitter != victim) {
                event.setDamage(0.0);
                if (rule == Ladder.Rule.BOXING) {
                    matches.boxingHit(match, hitter, victim);
                }
            } else {
                event.setCancelled(true);
            }
            return;
        }
        // Coup mortel : élimination propre, sans vraie mort ni objets au sol.
        if (victim.getHealth() - event.getFinalDamage() <= 0.0) {
            event.setCancelled(true);
            matches.eliminate(victim);
        }
    }

    /** Filet de sécurité : si une mort passe quand même, elle ne laisse rien au sol et compte comme une élimination. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (HcfZone.is(event.getEntity())) {
            return; // vraie mort du HCF (objets au sol, deathban)
        }
        event.setCancelled(true);
        Player p = event.getEntity();
        if (matches.matchOf(p.getUniqueId()) != null) {
            matches.eliminate(p);
        } else if (!builder(p)) {
            lobby.send(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && !HcfZone.is(p)) {
            Match match = matches.matchOf(p.getUniqueId());
            if (match == null || match.state() != Match.State.LIVE || match.ladder().rule == Ladder.Rule.SOUP
                    || match.ladder().rule == Ladder.Rule.SUMO || match.ladder().rule == Ladder.Rule.BOXING) {
                event.setCancelled(true);
            }
        }
    }

    // ------------------------------------------------------------------ Règles des kits

    /** BuildUHC : pas de régénération par la faim (pommes d'or et potions seulement). */
    @EventHandler(ignoreCancelled = true)
    public void onRegen(org.bukkit.event.entity.EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        var reason = event.getRegainReason();
        if (reason != org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.SATIATED
                && reason != org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.REGEN) {
            return;
        }
        Match match = matches.matchOf(p.getUniqueId());
        if (match != null && match.ladder().noNaturalRegen()) {
            event.setCancelled(true);
        }
    }

    /** Sumo : tomber de la plateforme élimine. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (event.getTo().getBlockY() != event.getFrom().getBlockY()) {
            matches.checkSumoFall(event.getPlayer());
        }
    }

    /** Soup : clic droit avec une soupe = 3,5 cœurs rendus, la soupe disparaît. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSoup(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null
                || event.getItem().getType() != Material.MUSHROOM_STEW
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player p = event.getPlayer();
        Match match = matches.matchOf(p.getUniqueId());
        if (match == null || match.ladder().rule != Ladder.Rule.SOUP || match.state() != Match.State.LIVE) {
            return;
        }
        event.setCancelled(true);
        var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double maxHealth = max != null ? max.getValue() : 20.0;
        if (p.getHealth() >= maxHealth) {
            return;
        }
        p.setHealth(Math.min(maxHealth, p.getHealth() + 7.0));
        p.getInventory().setItemInMainHand(null);
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EAT, 0.6f, 1.2f);
    }

    // ------------------------------------------------------------------ Perles

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl) || !(event.getEntity().getShooter() instanceof Player p)) {
            return;
        }
        int ticks = plugin.getConfig().getInt("combat.perle-recharge-secondes", 16) * 20;
        // Un tick plus tard : le jeu pose d'abord sa propre recharge d'une seconde.
        Bukkit.getScheduler().runTask(plugin, () -> {
            Match match = matches.matchOf(p.getUniqueId());
            if (p.isOnline() && match != null && match.state() == Match.State.LIVE) {
                p.setCooldown(Material.ENDER_PEARL, ticks);
            }
        });
    }

    /** Une perle lancée juste avant de tomber ne ramène pas un joueur à terre dans l'arène. */
    @EventHandler(ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL || HcfZone.is(event.getPlayer())) {
            return;
        }
        UUID id = event.getPlayer().getUniqueId();
        Match match = matches.matchOf(id);
        if (match == null || match.state() != Match.State.LIVE || !match.isAlive(id)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ Objets

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!builder(event.getPlayer()) && !HcfZone.is(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && !builder(p) && !HcfZone.is(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArrowPickup(PlayerPickupArrowEvent event) {
        if (HcfZone.is(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player p = event.getPlayer();
        String action = lobby.action(event.getItem());
        if (action == null || matches.isBusy(p.getUniqueId()) || HcfZone.is(p)) {
            return;
        }
        event.setCancelled(true);
        switch (action) {
            case Lobby.ACTION_MENU -> menu.open(p, true);
            case Lobby.ACTION_CASUAL -> menu.open(p, false);
            case Lobby.ACTION_TOP -> commands.showProfile(p);
            case Lobby.ACTION_LEAVE -> queue.leave(p, true);
            case Lobby.ACTION_HCF -> p.performCommand("hcf");
            default -> { }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof QueueMenu.Holder holder) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                menu.click(p, event.getCurrentItem(), holder.ranked(), event.isRightClick());
            }
            return;
        }
        // Au hub, les objets de la barre d'action restent à leur place.
        if (matches.matchOf(p.getUniqueId()) == null && !builder(p) && !HcfZone.is(p)) {
            event.setCancelled(true);
        }
    }
}
