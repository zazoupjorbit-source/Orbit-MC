package fr.orbit.breche;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Commandes de jeu des joueurs en partie : clic droit (tirer, pouvoir, boutique), clic gauche (lame), F (recharger),
 * molette / chiffres (arme ou pouvoir en main). Tout le reste est bloqué (dégâts vanilla, faim, objets, inventaire).
 */
final class Ecoute implements Listener {

    private final OrbitBreche pl;

    Ecoute(OrbitBreche pl) {
        this.pl = pl;
    }

    private Partie partie(Player p) {
        return pl.jeux() == null ? null : pl.jeux().de(p);
    }

    private void clicDroit(Player p, Partie pa) {
        Combattant c = pa.de(p);
        if (c == null) {
            return;
        }
        int slot = p.getInventory().getHeldItemSlot();
        if (pa.phase == Partie.Phase.ATTENTE) {
            if (slot == 4) {
                pl.menus().ouvrirAgents(p, pa);
            } else if (slot == 8) {
                pl.jeux().quitter(p, true);
                Txt.send(p, "Retour à la salle d'arcade.");
            }
            return;
        }
        if (!c.vivant) {
            return;
        }
        if (slot <= 1) {
            pa.tir.gachette(c);
        } else if (slot == 2) {
            pa.tir.lame(c);
        } else if (slot <= 6) {
            if (pa.pouvoirs.lancerJoueur(c, slot - 3)) {
                p.getInventory().setHeldItemSlot(c.dernierSlotArme);
            }
        } else if (slot == 8) {
            pl.menus().ouvrirBoutique(p, pa);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Partie pa = partie(e.getPlayer());
        if (pa == null) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            clicDroit(e.getPlayer(), pa);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Partie pa = partie(e.getPlayer());
        if (pa == null) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) {
            clicDroit(e.getPlayer(), pa);
        }
    }

    /** Clic gauche (dans le vide, sur un bloc ou sur une entité) : la lame, ou retour à l'arme si un pouvoir est en main. */
    @EventHandler
    public void onSwing(PlayerArmSwingEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = e.getPlayer();
        Partie pa = partie(p);
        Combattant c = pa == null ? null : pa.de(p);
        if (c == null || !c.vivant || pa.phase == Partie.Phase.ATTENTE) {
            return;
        }
        int slot = p.getInventory().getHeldItemSlot();
        if (slot == 2) {
            pa.tir.lame(c);
        } else if (slot >= 3 && slot <= 6) {
            p.getInventory().setHeldItemSlot(c.dernierSlotArme);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity().getWorld().equals(pl.monde())) {
            e.setCancelled(true);
            return;
        }
        if (e.getEntity() instanceof Player p && partie(p) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onRegain(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p && partie(p) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && partie(p) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Partie pa = partie(e.getPlayer());
        if (pa == null) {
            return;
        }
        e.setCancelled(true);
        Combattant c = pa.de(e.getPlayer());
        if (c != null && c.vivant) {
            pa.tir.recharger(c);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (partie(e.getPlayer()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        Partie pa = partie(e.getPlayer());
        Combattant c = pa == null ? null : pa.de(e.getPlayer());
        if (c == null) {
            return;
        }
        int n = e.getNewSlot();
        if (n <= 2) {
            c.dernierSlotArme = n;
        }
        c.gachette = -1;
        if (n >= 3 && n <= 6 && c.vivant && pa.phase != Partie.Phase.ATTENTE) {
            Agent.Pouvoir pv = c.agent.pouvoirs[n - 3];
            Txt.bar(e.getPlayer(), "<" + c.agent.couleur + "><b>" + pv.nom() + "</b></" + c.agent.couleur + "> <#9AA4B2>· clic droit : utiliser · clic gauche : ranger");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventory(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menus.Ecran) {
            return;
        }
        if (e.getWhoClicked() instanceof Player p && partie(p) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getWorld().equals(pl.monde()) && !e.getPlayer().hasPermission("orbitbreche.admin")) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlock().getWorld().equals(pl.monde()) && !e.getPlayer().hasPermission("orbitbreche.admin")) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (partie(e.getPlayer()) != null) {
            pl.jeux().quitter(e.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (pl.teleportInterne(p) || e.getTo().getWorld().equals(pl.monde())) {
            return;
        }
        if (partie(p) != null) {
            pl.jeux().quitter(p, false);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        pl.profils().charger(p);
        if (p.getWorld().equals(pl.monde())) {
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (p.isOnline() && partie(p) == null && p.getWorld().equals(pl.monde())) {
                    pl.versSalle(p);
                }
            }, 10L);
        }
    }
}
