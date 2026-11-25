package fr.orbit.staff;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mode staff (inventaire et position mis de côté sur disque, outils en barre, vol, invincible)
 * et vanish (indépendant : /vanish marche aussi hors mode staff).
 *
 * <p>Les objets pris dans le catalogue pendant le mode staff ne sont pas perdus : à la sortie, tout
 * ce qui n'est pas un outil est rendu en plus de l'inventaire d'origine.</p>
 */
public final class StaffMode implements Listener {

    private final OrbitStaff plugin;
    private final File dossier;
    private final Set<UUID> actifs = ConcurrentHashMap.newKeySet();
    private final Set<UUID> invisibles = ConcurrentHashMap.newKeySet();

    public StaffMode(OrbitStaff plugin) {
        this.plugin = plugin;
        this.dossier = new File(plugin.getDataFolder(), "etats");
        dossier.mkdirs();
    }

    public boolean actif(Player p) {
        return actifs.contains(p.getUniqueId());
    }

    public boolean invisible(Player p) {
        return invisibles.contains(p.getUniqueId());
    }

    public Set<UUID> invisibles() {
        return invisibles;
    }

    private File fichier(UUID id) {
        return new File(dossier, id + ".yml");
    }

    public void basculer(Player p) {
        if (actif(p)) {
            sortir(p);
        } else {
            entrer(p);
        }
    }

    public void entrer(Player p) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("gamemode", p.getGameMode().name());
        y.set("inventaire", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(p.getInventory().getContents())));
        y.set("position", p.getLocation());
        y.set("vol", p.getAllowFlight());
        y.set("niveau", p.getLevel());
        y.set("xp", (double) p.getExp());
        y.set("vie", p.getHealth());
        y.set("faim", p.getFoodLevel());
        y.set("invisible-avant", invisible(p));
        try {
            y.save(fichier(p.getUniqueId()));
        } catch (Exception e) {
            Txt.err(p, "Impossible de sauvegarder ton inventaire : mode staff annulé.");
            plugin.getLogger().severe("Sauvegarde de l'état de " + p.getName() + " : " + e.getMessage());
            return;
        }
        actifs.add(p.getUniqueId());
        appliquer(p, true);
        Txt.send(p, "<#55ff88>Mode staff activé. <gray>Ton inventaire est mis de côté.");
        plugin.log(p.getName(), "STAFF_ON", null, null);
    }

    private void appliquer(Player p, boolean vider) {
        if (vider) {
            p.getInventory().clear();
        }
        try {
            p.setGameMode(GameMode.valueOf(plugin.getConfig().getString("mode-staff.gamemode", "SURVIVAL").toUpperCase()));
        } catch (IllegalArgumentException e) {
            p.setGameMode(GameMode.SURVIVAL);
        }
        if (plugin.getConfig().getBoolean("mode-staff.vol", true)) {
            p.setAllowFlight(true);
            p.setFlying(true);
        }
        p.setInvulnerable(plugin.getConfig().getBoolean("mode-staff.invincible", true));
        p.setFoodLevel(20);
        if (plugin.getConfig().getBoolean("mode-staff.invisible", true)) {
            setInvisible(p, true);
        }
        Outils.donner(plugin, p);
    }

    public void sortir(Player p) {
        actifs.remove(p.getUniqueId());
        File f = fichier(p.getUniqueId());
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        // Objets pris pendant le mode staff (catalogue...) : rendus en plus.
        List<ItemStack> extras = new ArrayList<>();
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && !it.isEmpty() && Outils.de(plugin, it) == null) {
                extras.add(it);
            }
        }
        p.getInventory().clear();
        p.setInvulnerable(false);
        p.setFlySpeed(0.1f);
        if (y.contains("inventaire")) {
            try {
                p.getInventory().setContents(ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(y.getString("inventaire"))));
            } catch (Exception e) {
                plugin.getLogger().severe("Inventaire de " + p.getName() + " illisible : fichier gardé dans " + f.getName());
                Txt.err(p, "Ton inventaire n'a pas pu être rendu : préviens un administrateur (fichier gardé).");
                return;
            }
            try {
                p.setGameMode(GameMode.valueOf(y.getString("gamemode", "SURVIVAL")));
            } catch (IllegalArgumentException e) {
                p.setGameMode(GameMode.SURVIVAL);
            }
            boolean vol = y.getBoolean("vol") || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
            p.setAllowFlight(vol);
            p.setFlying(vol && p.isFlying());
            p.setLevel(y.getInt("niveau"));
            p.setExp((float) y.getDouble("xp"));
            p.setFoodLevel(y.getInt("faim", 20));
            double vie = y.getDouble("vie", 20);
            var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            p.setHealth(Math.max(1, Math.min(vie, max == null ? 20 : max.getValue())));
            Location loc = y.getLocation("position");
            if (plugin.getConfig().getBoolean("mode-staff.retour-position", true) && loc != null && loc.getWorld() != null) {
                p.teleportAsync(loc);
            }
            if (!y.getBoolean("invisible-avant")) {
                setInvisible(p, false);
            }
        } else {
            p.setAllowFlight(p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR);
            setInvisible(p, false);
        }
        for (ItemStack reste : p.getInventory().addItem(extras.toArray(new ItemStack[0])).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), reste);
        }
        f.delete();
        Txt.send(p, "<#ff8c42>Mode staff désactivé. <gray>Inventaire rendu.");
        plugin.log(p.getName(), "STAFF_OFF", null, null);
    }

    public void toutRendre() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (actif(p)) {
                sortir(p);
            }
        }
    }

    // ------------------------------------------------------------------ vanish

    public void setInvisible(Player p, boolean on) {
        if (on) {
            invisibles.add(p.getUniqueId());
        } else {
            invisibles.remove(p.getUniqueId());
        }
        for (Player autre : Bukkit.getOnlinePlayers()) {
            if (autre.equals(p)) {
                continue;
            }
            if (on && !autre.hasPermission("orbitstaff.vanish")) {
                autre.hidePlayer(plugin, p);
            } else {
                autre.showPlayer(plugin, p);
            }
        }
        p.setSleepingIgnored(on);
        p.setCollidable(!on);
        p.setSilent(on);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoinTot(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Cacher les invisibles au nouveau venu.
        if (!p.hasPermission("orbitstaff.vanish")) {
            for (UUID id : invisibles) {
                Player v = Bukkit.getPlayer(id);
                if (v != null) {
                    p.hidePlayer(plugin, v);
                }
            }
        }
        if (fichier(p.getUniqueId()).isFile()) {
            // Revient en mode staff (crash, redémarrage) : sans message de connexion pour rester discret.
            e.joinMessage(null);
            actifs.add(p.getUniqueId());
            // Après OrbitDevCore (qui donne sa boussole à la connexion).
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && actif(p)) {
                    appliquer(p, false);
                    Txt.send(p, "Tu étais en mode staff : il est remis.");
                }
            }, 5L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (invisible(p)) {
            e.quitMessage(null);
        }
        // Le fichier d'état reste : le mode staff est remis à la prochaine connexion.
        actifs.remove(p.getUniqueId());
        invisibles.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------------ protections

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (actif(e.getPlayer()) && Outils.de(plugin, e.getItemDrop().getItemStack()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && (actif(p) || invisible(p))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {
        if (e.getTarget() instanceof Player p && (actif(p) || invisible(p))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && actif(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFaim(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && actif(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPose(BlockPlaceEvent e) {
        if (actif(e.getPlayer()) && Outils.de(plugin, e.getItemInHand()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCasse(BlockBreakEvent e) {
        if (actif(e.getPlayer()) && Outils.de(plugin, e.getPlayer().getInventory().getItemInMainHand()) != null) {
            e.setCancelled(true);
        }
    }

    /** Pseudos des staffs en mode staff (pour le livre / statut). */
    public Set<UUID> actifs() {
        return new HashSet<>(actifs);
    }
}
