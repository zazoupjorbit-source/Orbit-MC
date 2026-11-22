package fr.orbit.reborn.listener;

import fr.orbit.reborn.RebornMMO;
import fr.orbit.reborn.item.Items;
import fr.orbit.reborn.player.PlayerData;
import fr.orbit.reborn.player.Stats;
import fr.orbit.reborn.ui.Menus;
import fr.orbit.reborn.util.Fx;
import fr.orbit.reborn.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Locale;

/** Connexion (« Link Start ! »), mort (éclats), réapparition, consommables. */
public final class PlayerListener implements Listener {

    private static final String[] TUNNEL = {"#FF3B3B", "#FF8A3B", "#FFE066", "#5EE89A", "#22C1D6", "#3A7FFF", "#B36BFF", "#FF6BD8"};
    private final RebornMMO plugin;

    public PlayerListener(RebornMMO plugin) {
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
        plugin.cursor().update(p, d);
        Items.applyModels(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            if (d.classId == null) {
                plugin.floors().teleport(p, 1, false);
                p.setGameMode(GameMode.ADVENTURE);
                if (!d.introDone) {
                    intro(p, d);
                } else {
                    Menus.classSelect(plugin, p);
                }
                return;
            }
            plugin.floors().teleport(p, plugin.floors().unlocked(d.floor) ? d.floor : 1, false);
            prepareBody(p, d);
        }, 5L);
    }

    /** Remet le corps du joueur en accord avec son avatar (mode, PV, barre d'XP, vitesse). */
    public void prepareBody(Player p, PlayerData d) {
        if (!d.buildMode) {
            p.setGameMode(GameMode.SURVIVAL);
        }
        double max = Stats.maxHp(d);
        if (d.hp <= 0 || d.hp > max) {
            d.hp = max;
        }
        p.setFoodLevel(20);
        p.setSaturation(20);
        plugin.combat().sync(p, d);
        plugin.progression().updateExpBar(p, d);
        plugin.progression().applyAgility(p, d);
    }

    /** « Link Start ! » : tunnel de couleurs, puis choix de la classe. */
    private void intro(Player p, PlayerData d) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 110, 0, false, false, false));
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel();
                    return;
                }
                if (t == 0) {
                    Text.title(p, "<white><b>Link Start !</b></white>", "", 5, 30, 5);
                    p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f);
                } else if (t >= 22 && t < 62 && t % 3 == 0) {
                    int shift = t / 3;
                    StringBuilder bars = new StringBuilder();
                    for (int k = 0; k < 12; k++) {
                        String c = TUNNEL[(shift + k) % TUNNEL.length];
                        bars.append('<').append(c).append(">▌</").append(c).append('>');
                    }
                    Text.title(p, "", bars.toString(), 0, 6, 0);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 0.5f + (t - 22) / 40f);
                } else if (t == 66) {
                    Text.title(p, "<gradient:#7FB2FF:#B36BFF><b>Bienvenue</b></gradient>", "<#D7DCE2>dans le monde de Reborn", 10, 40, 10);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                } else if (t >= 100) {
                    d.introDone = true;
                    cancel();
                    Menus.classSelect(plugin, p);
                    return;
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.parties().quit(p);
        plugin.hud().quit(p);
        plugin.players().unload(p.getUniqueId());
    }

    // ------------------------------------------------------------------ Mort

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        PlayerData d = plugin.players().get(p);
        if (d.classId == null) {
            return;
        }
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        e.deathMessage(null);
        Fx.shatter(p.getLocation().add(0, 1, 0), 1.3);
        d.deaths++;
        d.hp = 0;

        long now = System.currentTimeMillis();
        if (d.lastAttacker != null && now - d.lastAttackerAt < 15000) {
            Player killer = Bukkit.getPlayer(d.lastAttacker);
            if (killer != null && !killer.equals(p)) {
                plugin.cursor().onKill(killer, plugin.players().get(killer), d);
            }
        }
        for (Player o : p.getWorld().getPlayers()) {
            Text.msg(o, "<#8C96A3>✦ L'avatar de <white>" + Text.esc(p.getName()) + "</white> a volé en éclats.");
        }
        d.orangeUntil = 0;
        d.lastAttacker = null;
        d.castingUntil = 0;
        d.postMotionUntil = 0;

        String mode = plugin.getConfig().getString("death.mode", "penalty").toLowerCase(Locale.ROOT);
        if (mode.equals("penalty")) {
            long lostXp = Math.round(d.xp * plugin.getConfig().getDouble("death.xp-loss", 0.5));
            long lostCol = Math.round(d.col * plugin.getConfig().getDouble("death.col-loss", 0.1));
            d.xp -= lostXp;
            d.col -= lostCol;
            Text.msg(p, "<#FF5C5C>Pénalité de mort : -" + Text.num(lostXp) + " XP, -" + Text.num(lostCol) + " Col.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.players().get(p);
        if (d.classId == null) {
            return;
        }
        int floor = plugin.floors().unlocked(d.floor) ? d.floor : 1;
        World w = plugin.floors().world(floor);
        if (w != null) {
            e.setRespawnLocation(plugin.floors().town(w));
        }
        String mode = plugin.getConfig().getString("death.mode", "penalty").toLowerCase(Locale.ROOT);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            if (mode.equals("hardcore")) {
                plugin.players().reset(p);
                p.getInventory().clear();
                p.kick(Text.mm("<#FF3B3B><b>GAME OVER</b></#FF3B3B>\n\n<#D7DCE2>Votre avatar a été détruit.\n<#8C96A3>Toute votre progression a été effacée."));
                return;
            }
            d.hp = Stats.maxHp(d);
            prepareBody(p, d);
            Text.title(p, "<#FF5C5C><b>Vous êtes mort.</b>", "<#8C96A3>Réapparition à " + Text.esc(plugin.floors().def(floor).town()), 5, 50, 15);
            Fx.teleport(p.getLocation());
        }, 2L);
    }

    @EventHandler
    public void onExp(PlayerExpChangeEvent e) {
        PlayerData d = plugin.players().loaded(e.getPlayer().getUniqueId());
        if (d != null && d.classId != null) {
            e.setAmount(0);
        }
    }

    // ------------------------------------------------------------------ Consommables

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack item = e.getItem();
        String type = Items.type(item);
        if (type == null || !(type.equals("potion") || type.equals("heal_crystal") || type.equals("tp_crystal"))) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        PlayerData d = plugin.players().get(p);
        if (d.classId == null || d.hp <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Location at = p.getLocation().add(0, 1, 0);
        switch (type) {
            case "potion" -> {
                if (d.potionUntil > now) {
                    plugin.hud().flash(p, "<#8C96A3>Une potion fait déjà effet.", 30);
                    return;
                }
                d.potionUntil = now + 5000;
                p.playSound(at, Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
                Fx.dust(at, Fx.color("#FF8AAE"), 1.2f, 15, 0.4);
            }
            case "heal_crystal" -> {
                plugin.combat().heal(p, d, Stats.maxHp(d));
                p.getWorld().spawnParticle(Particle.HEART, at.clone().add(0, 0.6, 0), 6, 0.4, 0.3, 0.4, 0);
                Fx.dust(at, Fx.color("#FF8AD8"), 1.6f, 30, 0.6);
                p.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.6f);
                plugin.hud().flash(p, "<#FF8AD8><b>Soin !</b>", 30);
            }
            case "tp_crystal" -> {
                if (plugin.cursor().state(d) > 0) {
                    plugin.hud().flash(p, "<#FF8A3B>Les cristaux ne mènent pas en ville avec un curseur orange.", 40);
                    return;
                }
                plugin.hud().flash(p, "<#7FD4FF><b>Téléportation !</b> <#D7DCE2>" + Text.esc(plugin.floors().def(d.floor).town()), 40);
                plugin.floors().teleport(p, plugin.floors().unlocked(d.floor) ? d.floor : 1, true);
            }
            default -> {
                return;
            }
        }
        item.setAmount(item.getAmount() - 1);
    }
}
