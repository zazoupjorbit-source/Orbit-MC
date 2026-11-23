package fr.orbitversus;

import fr.orbitversus.elo.EloStore;
import fr.orbitversus.match.Match;
import fr.orbitversus.match.MatchManager;
import fr.orbitversus.match.QueueManager;
import fr.orbitversus.npc.NpcManager;
import fr.orbitversus.npc.QueueHolder;
import fr.orbitversus.npc.QueueMenu;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

/** Branche le Mode Versus sur les événements du serveur. */
public final class VersusListener implements Listener {

    private final MatchManager matches;
    private final QueueManager queue;
    private final EloStore elo;
    private final NpcManager npc;
    private final QueueMenu menu;

    public VersusListener(MatchManager matches, QueueManager queue, EloStore elo, NpcManager npc, QueueMenu menu) {
        this.matches = matches;
        this.queue = queue;
        this.elo = elo;
        this.npc = npc;
        this.menu = menu;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        elo.load(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        matches.forfeit(id);            // forfait si en plein combat
        queue.purge(id);                // retire des files
        if (matches.isSpectating(id)) {
            matches.stopSpectating(id);
        }
        elo.unload(id);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Match match = matches.matchOf(victim.getUniqueId());
        if (match == null) {
            return;
        }
        if (match.state() != Match.State.LIVE) {
            event.setCancelled(true); // pas de dégâts hors manche active
            return;
        }
        if (event instanceof EntityDamageByEntityEvent ede) {
            Player attacker = resolveAttacker(ede);
            if (attacker != null) {
                Match am = matches.matchOf(attacker.getUniqueId());
                if (am != match || match.sameTeam(victim.getUniqueId(), attacker.getUniqueId())) {
                    event.setCancelled(true); // hors match ou tir allié
                    return;
                }
            }
        }
        // Dégâts létaux -> élimination propre (pas de vraie mort, pas de drop).
        if (victim.getHealth() - event.getFinalDamage() <= 0.0) {
            event.setCancelled(true);
            matches.eliminate(victim);
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent ede) {
        if (ede.getDamager() instanceof Player p) {
            return p;
        }
        if (ede.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    @EventHandler
    public void onNpcClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (npc.isNpc(event.getRightClicked())) {
            event.setCancelled(true);
            menu.open(event.getPlayer());
        }
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof QueueHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        switch (event.getRawSlot()) {
            case QueueHolder.SLOT_1V1 -> { player.closeInventory(); queue.queue1v1(player); }
            case QueueHolder.SLOT_2V2 -> {
                player.closeInventory();
                player.sendMessage(Msg.msg("<gray>Pour le 2v2, tape <white>/2v2 <nom d'équipe><gray> "
                        + "(ton coéquipier doit taper le même nom)."));
            }
            case QueueHolder.SLOT_LEAVE -> { player.closeInventory(); queue.leave(player); }
            default -> { }
        }
    }
}
