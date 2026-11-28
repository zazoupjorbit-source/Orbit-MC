package fr.orbitgames.hcf.combat.listener;

import fr.orbitgames.hcf.api.HcfWorlds;
import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.service.ClaimService;
import fr.orbitgames.hcf.api.service.DtrService;
import fr.orbitgames.hcf.api.service.EventsService;
import fr.orbitgames.hcf.api.service.FactionService;
import fr.orbitgames.hcf.api.util.Text;
import fr.orbitgames.hcf.combat.OrbitHCFCombatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PNJ de combat logger : un joueur qui se deconnecte hors de la Safezone laisse un villageois a son nom
 * pendant quelques secondes. Si le villageois est tue, le joueur est mort : ses objets tombent, sa faction
 * perd du DTR, il est deathban. S'il revient avant, il reprend sa place avec la vie qu'il reste au PNJ.
 */
public class CombatLogger implements Listener {

    private static final class Entry {
        UUID owner;
        String name;
        Player player;
        ItemStack[] contents;
        boolean deathbanBypass;
        Villager npc;
        BukkitTask task;
    }

    private final OrbitHCFCombatPlugin plugin;
    private final Map<UUID, Entry> byOwner = new HashMap<>();
    private final Map<UUID, Entry> byEntity = new HashMap<>();

    public CombatLogger(OrbitHCFCombatPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getCombatConfig().getBoolean("combat-logger.enabled", true);
    }

    /** Vrai si la deconnexion de ce joueur est prise en charge par un PNJ (plus de sanction immediate). */
    public boolean handles(Player player) {
        return byOwner.containsKey(player.getUniqueId());
    }

    // ------------------------------------------------------------------ Deconnexion

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!enabled() || Bukkit.isStopping() || !HcfWorlds.isHcf(player) || HcfWorlds.isExiting(uuid)
                || player.isDead() || player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        boolean tagged = plugin.getCombatManager().isCombatTagged(uuid);
        if (!tagged && plugin.getCombatConfig().getBoolean("combat-logger.only-when-tagged", false)) {
            return;
        }
        ClaimService claims = OrbitHCF.claims();
        if (claims != null && claims.isSafezone(player.getLocation())) {
            return;
        }
        // Un joueur encore protege (timer PvP, SOTW) ne peut pas etre tue : pas de PNJ.
        EventsService events = OrbitHCF.events();
        if (plugin.getCombatManager().hasPvpTimer(uuid) || (events != null && events.isSotwProtected(uuid))) {
            return;
        }
        long seconds = plugin.getCombatConfig().getLong("combat-logger.seconds", 30L);
        if (tagged) {
            seconds = Math.max(seconds, plugin.getCombatManager().getCombatTagRemaining(uuid).getSeconds());
        }

        Entry entry = new Entry();
        entry.owner = uuid;
        entry.name = player.getName();
        entry.player = player;
        entry.contents = player.getInventory().getContents().clone();
        entry.deathbanBypass = HcfWorlds.staffExempt(player, "orbithcf.combat.deathban.bypass");
        double health = Math.max(1.0, player.getHealth());
        entry.npc = player.getWorld().spawn(player.getLocation(), Villager.class, npc -> {
            npc.setCustomName(Text.color("&c" + player.getName() + " &7(deconnecte)"));
            npc.setCustomNameVisible(true);
            npc.setAI(false);
            npc.setSilent(true);
            npc.setAdult();
            npc.setProfession(Villager.Profession.NONE);
            npc.setRemoveWhenFarAway(false);
            npc.setPersistent(false);
            npc.setCanPickupItems(false);
            AttributeInstance max = npc.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                max.setBaseValue(Math.max(20.0, health));
            }
            npc.setHealth(health);
            EntityEquipment gear = npc.getEquipment();
            if (gear != null) {
                gear.setArmorContents(player.getInventory().getArmorContents());
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    gear.setDropChance(slot, 0f);
                }
            }
        });
        entry.task = Bukkit.getScheduler().runTaskLater(plugin, () -> expire(entry), seconds * 20L);
        byOwner.put(uuid, entry);
        byEntity.put(entry.npc.getUniqueId(), entry);
    }

    private void forget(Entry entry) {
        byOwner.remove(entry.owner);
        byEntity.remove(entry.npc.getUniqueId());
        if (entry.task != null) {
            entry.task.cancel();
        }
    }

    private void expire(Entry entry) {
        forget(entry);
        if (entry.npc.isValid()) {
            entry.npc.remove();
        }
    }

    // ------------------------------------------------------------------ Retour du joueur

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Entry entry = byOwner.get(event.getPlayer().getUniqueId());
        if (entry == null) {
            return;
        }
        forget(entry);
        if (!entry.npc.isValid()) {
            return;
        }
        Player player = event.getPlayer();
        Location at = entry.npc.getLocation();
        double health = entry.npc.getHealth();
        entry.npc.remove();
        player.teleport(at);
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(Math.max(1.0, Math.min(health, max != null ? max.getValue() : 20.0)));
    }

    // ------------------------------------------------------------------ Le PNJ

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entry entry = byEntity.get(event.getEntity().getUniqueId());
        if (entry == null) {
            return;
        }
        Player attacker = event.getDamager() instanceof Player p ? p
                : event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player s ? s : null;
        if (attacker == null) {
            return;
        }
        FactionService factions = OrbitHCF.factions();
        boolean friendly = factions != null && factions.getRelation(attacker, entry.player).isFriendly();
        if (friendly || plugin.getCombatManager().hasPvpTimer(attacker.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (byEntity.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (byEntity.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        Entry entry = byEntity.get(event.getEntity().getUniqueId());
        if (entry == null) {
            return;
        }
        forget(entry);
        event.getDrops().clear();
        event.setDroppedExp(0);
        Location at = event.getEntity().getLocation();
        for (ItemStack item : entry.contents) {
            if (item != null && !item.getType().isAir()) {
                at.getWorld().dropItemNaturally(at, item);
            }
        }
        // Le joueur est mort : DTR, deathban, et son inventaire sera vide a son retour.
        DtrService dtr = OrbitHCF.dtr();
        if (dtr != null) {
            dtr.applyDeathLoss(entry.player);
        }
        if (plugin.getCombatManager().isDeathbanEnabled()
                && !entry.deathbanBypass) {
            plugin.getCombatManager().deathban(entry.owner, Duration.ofSeconds(
                    plugin.getDeathbanConfig().getLong("default-duration-seconds", 3600L)));
        }
        plugin.getCombatManager().untag(entry.owner);
        plugin.getCombatDb().markLogger(entry.owner);
        Player killer = event.getEntity().getKiller();
        Bukkit.broadcastMessage(Text.color("&c" + entry.name + " &7(deconnecte) &ea ete tue"
                + (killer != null ? " par &c" + killer.getName() : "") + "&e."));
    }

    /** Arret du plugin : les PNJ restants sont retires sans sanction. */
    public void shutdown() {
        for (Entry entry : new ArrayList<>(byOwner.values())) {
            expire(entry);
        }
    }
}
