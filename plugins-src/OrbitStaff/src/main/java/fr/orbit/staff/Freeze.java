package fr.orbit.staff;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gel d'un joueur (vérification, screenshare). Il ne bouge plus, ne casse rien, ne prend pas de dégâts,
 * n'utilise que /msg et /r. Le gel survit à une déconnexion : se déconnecter gelé alerte le staff
 * avec un bouton pour bannir.
 */
public final class Freeze implements Listener {

    public record Gel(String staff, String motif, long depuis) {
    }

    private static final Set<String> COMMANDES_OK = Set.of("msg", "tell", "w", "r", "m", "whisper", "report", "signaler");

    private final OrbitStaff plugin;
    private final Map<UUID, Gel> geles = new ConcurrentHashMap<>();

    public Freeze(OrbitStaff plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tic, 40L, 40L);
    }

    public boolean gele(UUID id) {
        return geles.containsKey(id);
    }

    public Gel gel(UUID id) {
        return geles.get(id);
    }

    public void basculer(CommandSender staff, Player cible, String motif) {
        if (gele(cible.getUniqueId())) {
            degeler(staff, cible);
        } else {
            geler(staff, cible, motif);
        }
    }

    public void geler(CommandSender staff, Player cible, String motif) {
        if (cible.hasPermission("orbitstaff.immunite")) {
            Txt.err(staff, cible.getName() + " est protégé.");
            return;
        }
        geles.put(cible.getUniqueId(), new Gel(staff.getName(), motif, System.currentTimeMillis()));
        if (cible.isFlying() || !cible.isOnGround()) {
            // Posé au sol pour éviter de rester suspendu (et de « tomber » au dégel).
            Location sol = cible.getWorld().getHighestBlockAt(cible.getLocation()).getLocation().add(0.5, 1, 0.5);
            sol.setYaw(cible.getLocation().getYaw());
            sol.setPitch(cible.getLocation().getPitch());
            if (sol.getY() <= cible.getLocation().getY()) {
                cible.teleportAsync(sol);
            }
        }
        cible.setFlying(false);
        cible.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        afficher(cible);
        Txt.send(cible, "<#ff5555>Tu es gelé par le staff. <gray>Ne te déconnecte pas, tu peux répondre en /msg <white>" + Txt.esc(staff.getName()) + "<gray>.");
        plugin.flux().poster(Flux.INFO, cible.getUniqueId(), "<#f5c542>" + Txt.esc(staff.getName()) + " <gray>a gelé <white>"
                + Txt.esc(cible.getName()) + (motif.isEmpty() ? "" : " <gray>: " + Txt.esc(motif)));
        plugin.log(staff.getName(), "FREEZE", cible.getName(), motif);
    }

    public void degeler(CommandSender staff, Player cible) {
        if (geles.remove(cible.getUniqueId()) == null) {
            return;
        }
        cible.removePotionEffect(PotionEffectType.BLINDNESS);
        cible.clearTitle();
        Txt.send(cible, "<#55ff88>Tu es dégelé, merci de ta patience.");
        plugin.flux().poster(Flux.INFO, cible.getUniqueId(), "<#f5c542>" + Txt.esc(staff.getName()) + " <gray>a dégelé <white>"
                + Txt.esc(cible.getName()));
        plugin.log(staff.getName(), "UNFREEZE", cible.getName(), null);
    }

    private void afficher(Player p) {
        p.showTitle(Title.title(Txt.mm(plugin.getConfig().getString("freeze.titre", "<red>GELÉ")),
                Txt.mm(plugin.getConfig().getString("freeze.sous-titre", "")),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(2600), Duration.ZERO)));
    }

    private int compteur;

    private void tic() {
        compteur++;
        int rappel = Math.max(10, plugin.getConfig().getInt("freeze.rappel-secondes", 60)) / 2;
        for (Map.Entry<UUID, Gel> e : geles.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) {
                continue;
            }
            afficher(p);
            if (compteur % rappel == 0) {
                long min = (System.currentTimeMillis() - e.getValue().depuis()) / 60000;
                Txt.alerterStaff("orbitstaff.freeze", "<white>" + Txt.esc(p.getName()) + " <gray>est gelé depuis " + min
                        + " min (par " + Txt.esc(e.getValue().staff()) + "). <#4aa3ff><click:run_command:'/panel " + p.getName()
                        + "'>[Panel]</click>");
            }
        }
    }

    // ------------------------------------------------------------------ blocages

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!gele(e.getPlayer().getUniqueId()) || !e.hasChangedPosition()) {
            return;
        }
        Location from = e.getFrom();
        Location to = e.getTo().clone();
        // La tête tourne, le corps reste (chute autorisée pour ne pas flotter).
        to.setX(from.getX());
        to.setZ(from.getZ());
        if (to.getY() > from.getY()) {
            to.setY(from.getY());
        }
        e.setTo(to);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTp(PlayerTeleportEvent e) {
        if (gele(e.getPlayer().getUniqueId()) && (e.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || e.getCause() == PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (gele(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (gele(e.getWhoClicked().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (gele(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (gele(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (gele(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && gele(p.getUniqueId())) {
            e.setCancelled(true);
        }
        if (e instanceof EntityDamageByEntityEvent d && d.getDamager() instanceof Player p && gele(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!gele(e.getPlayer().getUniqueId())) {
            return;
        }
        String cmd = e.getMessage().substring(1).split(" ", 2)[0].toLowerCase();
        if (!COMMANDES_OK.contains(cmd.substring(cmd.indexOf(':') + 1))) {
            e.setCancelled(true);
            Txt.err(e.getPlayer(), "Tu es gelé : seules /msg et /r marchent.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Gel g = geles.get(p.getUniqueId());
        if (g == null) {
            return;
        }
        String n = p.getName();
        plugin.flux().poster(Flux.ALERTE, p.getUniqueId(), "<white>" + Txt.esc(n) + " <#ff5555>s'est déconnecté pendant un freeze ! "
                + "<#f5c542><click:suggest_command:'/ban " + n + " Déconnexion pendant une vérification'>[Bannir]</click>");
        plugin.log(n, "DECO_FREEZE", n, g.motif());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (gele(p.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, false, false, false));
                    afficher(p);
                    Txt.alerterStaff("orbitstaff.freeze", "<white>" + Txt.esc(p.getName()) + " <gray>est revenu et reste gelé.");
                }
            }, 10L);
        }
    }
}
