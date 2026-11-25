package fr.orbit.mmo.listener;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.classes.Resource;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.player.Stats;
import fr.orbit.mmo.ui.Menus;
import fr.orbit.mmo.util.Fx;
import fr.orbit.mmo.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Connexion, création du personnage, mort, réapparition. */
public final class PlayerListener implements Listener {

    private final OrbitMMO plugin;

    public PlayerListener(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            plugin.players().preload(e.getUniqueId(), e.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        join(e.getPlayer());
    }

    public void join(Player p) {
        PlayerData d = plugin.players().get(p);
        d.name = p.getName();
        Items.applyModels(p);
        fr.orbit.mmo.item.Loot.migrate(p); // anciennes armes et armures → raretés et châsses, types d'armure, liaison
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && plugin.progression().valid(d)) {
                plugin.binding().checkWorn(p, d);
            }
        }, 40L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            if (!plugin.progression().valid(d)) {
                // Pas encore de personnage (ou ancien avatar d'avant la refonte) : création, aux Quatre-Vents.
                // Le choix du camp enverra ensuite à la capitale de la faction (voir Menus.create).
                org.bukkit.Location lobby = plugin.continent() == null ? null : plugin.continent().creation();
                if (lobby != null) {
                    p.teleportAsync(lobby);
                } else {
                    plugin.floors().teleport(p, 1, false);
                }
                p.setGameMode(GameMode.ADVENTURE);
                p.getInventory().clear();
                if (!d.introDone) {
                    d.introDone = true;
                    Text.title(p, "<gradient:#FFD66E:#B36BFF><b>Orbit MMO</b></gradient>", "<#D7DCE2>Lumière ou Ombre : choisissez votre camp", 10, 60, 15);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline()) {
                            Menus.factionSelect(plugin, p);
                        }
                    }, 50L);
                } else {
                    Menus.factionSelect(plugin, p);
                }
                return;
            }
            // Déconnecté sur le continent : on y reste. Dans le Pilier des Astres (les étages) : retour à la ville de l'étage. Ailleurs, et la première
            // fois depuis que les capitales sont le point de départ : la capitale de sa faction.
            boolean first = !d.capitalHome;
            int tower = plugin.floors().floorOf(p.getWorld());
            boolean onContinent = plugin.continent() != null && plugin.continent().isContinent(p.getWorld());
            if (!onContinent) {
                String home = tower > 0 && !first || plugin.continent() == null ? null : plugin.continent().sendHome(p, d.faction.id, false);
                if (home == null) {
                    int n = tower > 0 ? tower : d.floor;
                    plugin.floors().teleport(p, plugin.floors().unlocked(d, n) ? n : 1, false);
                } else if (first) {
                    Text.msg(p, "<#7FD4FF>Votre foyer est désormais <b>" + Text.esc(home) + "</b>, la capitale de votre faction. "
                            + "<#D7DCE2>Les cent étages sont devenus un donjon, le Pilier des Astres : son gardien se tient devant le donjon de la ville.");
                }
            }
            if (first && plugin.continent() != null && plugin.continent().ready()) {
                d.capitalHome = true;
            }
            prepareBody(p, d);
            if (plugin.progression().spec(d) == null && !d.buildMode) {
                // Personnage créé avant le choix de spé à la création.
                Menus.specSelect(plugin, p);
            }
        }, 5L);
    }

    /** Remet le corps du joueur en accord avec son personnage (mode, PV, barre d'XP, vitesse, objets de sort). */
    public void prepareBody(Player p, PlayerData d) {
        if (!d.buildMode) {
            p.setGameMode(GameMode.SURVIVAL);
        }
        plugin.progression().recompute(p, d);
        double max = Stats.maxHp(d);
        if (d.hp <= 0 || d.hp > max) {
            d.hp = max;
        }
        Resource r = plugin.progression().resource(d);
        if (r.startsFull && d.resource <= 0) {
            d.resource = r.max;
        }
        fr.orbit.mmo.player.Stamina.reset(d);
        p.setFoodLevel(20);
        p.setSaturation(20);
        plugin.combat().sync(p, d);
        plugin.progression().updateExpBar(p, d);
        plugin.bar().sync(p, d);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        if (d != null) {
            plugin.spells().interrupt(d);
        }
        plugin.auras().clear(p.getUniqueId());
        plugin.parties().quit(p);
        plugin.guilds().quit(p);
        plugin.hud().quit(p);
        plugin.tab().forget(p.getUniqueId());
        plugin.players().unload(p.getUniqueId());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        PlayerData d = plugin.players().loaded(e.getPlayer().getUniqueId());
        if (d != null) {
            plugin.spells().interrupt(d);
        }
    }

    // ------------------------------------------------------------------ Mort

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        PlayerData d = plugin.players().get(p);
        if (!plugin.progression().valid(d)) {
            return;
        }
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        e.deathMessage(null);
        d.deaths++;
        d.hp = 0;
        plugin.spells().interrupt(d);
        plugin.auras().clear(p.getUniqueId());

        long now = System.currentTimeMillis();
        String by = "";
        if (d.lastAttacker != null && now - d.lastAttackerAt < 15000) {
            Player killer = Bukkit.getPlayer(d.lastAttacker);
            if (killer != null && !killer.equals(p)) {
                PlayerData kd = plugin.players().get(killer);
                kd.playerKills++;
                by = " <#8C96A3>par " + kd.faction.tag() + " <white>" + Text.esc(killer.getName()) + "</white>";
                Text.msg(killer, "<#FFD66E>Victoire honorable</#FFD66E> <#8C96A3>sur " + d.faction.tag() + " <white>" + Text.esc(p.getName()));
            }
        }
        for (Player o : p.getWorld().getPlayers()) {
            Text.msg(o, "<#8C96A3>✦ <white>" + Text.esc(p.getName()) + "</white> est tombé au combat" + by + "<#8C96A3>.");
        }
        d.lastAttacker = null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.players().get(p);
        if (!plugin.progression().valid(d)) {
            return;
        }
        // Tombé sur le continent : retour au lieu sûr le plus proche qui n'est pas à la faction adverse.
        // Tombé dans le Pilier : la ville de l'étage. Ailleurs : la capitale de sa faction.
        String[] place = new String[1];
        org.bukkit.Location fallen = p.getLastDeathLocation() != null ? p.getLastDeathLocation() : p.getLocation();
        int tower = plugin.floors().floorOf(fallen.getWorld());
        int floor = plugin.floors().unlocked(d, tower > 0 ? tower : d.floor) ? (tower > 0 ? tower : d.floor) : 1;
        org.bukkit.Location back = plugin.continent() == null ? null : plugin.continent().respawn(fallen, d.faction.id, place);
        if (back == null && tower <= 0 && plugin.continent() != null) {
            back = plugin.continent().home(d.faction.id);
            fr.orbit.mmo.continent.ContinentData.Place capital = back == null ? null : plugin.continent().capital(d.faction.id);
            place[0] = capital == null ? null : capital.name();
        }
        if (back != null) {
            e.setRespawnLocation(back);
        } else {
            World w = plugin.floors().world(floor);
            if (w != null) {
                e.setRespawnLocation(plugin.floors().town(w));
            }
        }
        String where = back != null ? place[0] : plugin.floors().def(floor).town();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            d.hp = Stats.maxHp(d);
            Resource r = plugin.progression().resource(d);
            d.resource = r.startsFull ? r.max : 0;
            prepareBody(p, d);
            Text.title(p, "<#FF5C5C><b>Vous êtes mort.</b>", "<#8C96A3>Réapparition à " + Text.esc(where), 5, 50, 15);
            Fx.teleport(p.getLocation());
        }, 2L);
    }

    @EventHandler
    public void onExp(PlayerExpChangeEvent e) {
        PlayerData d = plugin.players().loaded(e.getPlayer().getUniqueId());
        if (d != null && plugin.progression().valid(d)) {
            e.setAmount(0);
        }
    }
}
