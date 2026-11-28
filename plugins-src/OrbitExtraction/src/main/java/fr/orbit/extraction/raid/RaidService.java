package fr.orbit.extraction.raid;

import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.gun.GunItems;
import fr.orbit.extraction.raid.RaidMap.Extract;
import fr.orbit.extraction.util.StoredLoc;
import fr.orbit.extraction.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Boucle d'un raid : planque → déploiement → minuteur → extraction (on garde tout) ou mort / temps écoulé (on perd tout).
 * PvPvE : les joueurs en raid peuvent s'entretuer ; la planque est une zone sûre.
 */
public final class RaidService implements Listener {

    private final OrbitExtraction plugin;
    private final ProfileStore profiles;
    private final RaidMap map;
    private final AutoSpawns auto;
    private final Random random = new Random();

    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Integer> extracting = new HashMap<>();
    /** Point d'extraction dans lequel se trouve chaque joueur en cours d'extraction (pour la console). */
    private final Map<UUID, Extract> extractingAt = new HashMap<>();
    private final Map<UUID, BukkitTask> deploying = new HashMap<>();
    /** Joueurs dont on cherche encore le point d'apparition (chargement du chunk). */
    private final Set<UUID> locating = new HashSet<>();

    private long durationMs;
    private int extractsPerRaid;
    private int countdown;
    private double hideoutRadius;
    private double minSpawnDist;
    private String raidWorld = "world";
    private long killBounty;
    private long extractBounty;
    private final Set<String> allowedCommands = new HashSet<>();
    private List<String> kit = List.of();
    private int ticks;

    public RaidService(OrbitExtraction plugin, ProfileStore profiles, RaidMap map, AutoSpawns auto) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.map = map;
        this.auto = auto;
    }

    public void load(ConfigurationSection c) {
        if (c == null) {
            c = plugin.getConfig().createSection("raid");
        }
        durationMs = (long) (c.getDouble("duree-minutes", 25) * 60_000);
        extractsPerRaid = Math.max(1, c.getInt("extractions-par-raid", 2));
        countdown = Math.max(0, c.getInt("compte-a-rebours", 5));
        hideoutRadius = c.getDouble("rayon-planque", 40);
        minSpawnDist = c.getDouble("distance-min-spawn", 60);
        raidWorld = c.getString("monde", "world");
        killBounty = c.getLong("prime-joueur", 150);
        extractBounty = c.getLong("prime-extraction", 200);
        allowedCommands.clear();
        for (String s : c.getStringList("commandes-autorisees")) {
            allowedCommands.add(s.toLowerCase(Locale.ROOT));
        }
        kit = c.getStringList("kit-de-depart");
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
        for (Player p : Bukkit.getOnlinePlayers()) {
            Profile pr = profiles.get(p);
            if (pr.inRaid) {
                showBar(p);
            }
        }
    }

    public void shutdown() {
        for (Map.Entry<UUID, BossBar> e : bars.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                p.hideBossBar(e.getValue());
            }
        }
        bars.clear();
        deploying.values().forEach(BukkitTask::cancel);
        deploying.clear();
        locating.clear();
    }

    // ------------------------------------------------------------------ état

    public boolean inRaid(Player p) {
        Profile pr = profiles.loaded(p.getUniqueId());
        return pr != null && pr.inRaid;
    }

    /** Peut être pris pour cible par les infestés et touché par les crachats. */
    public boolean isTargetable(Player p) {
        GameMode gm = p.getGameMode();
        return (gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE) && inRaid(p);
    }

    public boolean isExtracting(Player p) {
        return extracting.containsKey(p.getUniqueId());
    }

    /** Extraction en cours pour la console du HUD, null s'il n'y en a pas. */
    public fr.orbit.extraction.hud.Cast extraction(Player p) {
        Integer t = extracting.get(p.getUniqueId());
        Extract ex = extractingAt.get(p.getUniqueId());
        if (t == null || ex == null) {
            return null;
        }
        int need = Math.max(1, ex.seconds() * 20);
        return new fr.orbit.extraction.hud.Cast("<#2ECC71>Extraction</#2ECC71> <#D7DCE2>" + Text.esc(ex.name()),
                t / (double) need, Math.max(0, (need - t) / 20.0), "#2ECC71");
    }

    /** Durée totale d'un raid (ms). */
    public long durationMs() {
        return durationMs;
    }

    public Location hideout() {
        return map.hideout == null ? null : map.hideout.toLocation();
    }

    public double hideoutRadius() {
        return hideoutRadius;
    }

    /** Monde du raid (config raid.monde). */
    public String worldName() {
        return raidWorld;
    }

    public void onInfectedKill(Player killer, long bounty) {
        Profile pr = profiles.loaded(killer.getUniqueId());
        if (pr != null) {
            pr.infectedKills++;
            bounty = plugin.ranks().boosted(killer, bounty);
            if (bounty > 0) {
                pr.credits += bounty;
                Text.bar(killer, "<#2ECC71>+" + bounty + " cr</#2ECC71>");
            }
        }
    }

    public boolean isDeploying(Player p) {
        return deploying.containsKey(p.getUniqueId()) || locating.contains(p.getUniqueId());
    }

    // ------------------------------------------------------------------ déploiement

    public void deploy(Player p) {
        Profile pr = profiles.get(p);
        if (pr.inRaid) {
            info(p);
            return;
        }
        if (deploying.containsKey(p.getUniqueId()) || locating.contains(p.getUniqueId())) {
            Text.msg(p, "Déploiement déjà en cours…");
            return;
        }
        if ((map.spawns.isEmpty() && auto.isEmpty()) || map.extracts.isEmpty()) {
            Text.msg(p, "<#C0392B>La map n'est pas prête</#C0392B> : il faut au moins un point d'apparition et un point d'extraction "
                    + "<#5A6270>(/extraction spawn add, /extraction extract add)");
            return;
        }
        p.closeInventory();
        if (countdown <= 0) {
            doDeploy(p);
            return;
        }
        int[] left = {countdown};
        BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) {
                cancelDeploy(p.getUniqueId());
                return;
            }
            if (left[0] <= 0) {
                cancelDeploy(p.getUniqueId());
                doDeploy(p);
                return;
            }
            Text.title(p, "<#C0392B><b>" + left[0] + "</b>", "<#8A919B>Déploiement en zone infestée", 0, 25, 0);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 0.8f);
            left[0]--;
        }, 0L, 20L);
        deploying.put(p.getUniqueId(), t);
    }

    private void cancelDeploy(UUID id) {
        BukkitTask t = deploying.remove(id);
        if (t != null) {
            t.cancel();
        }
    }

    /** Apparition aléatoire sur l'île si le fichier de points existe, sinon points posés à la main. */
    private void doDeploy(Player p) {
        org.bukkit.World w = Bukkit.getWorld(raidWorld);
        if (auto.isEmpty() || w == null) {
            finishDeploy(p, pickSpawn(p));
            return;
        }
        UUID id = p.getUniqueId();
        locating.add(id);
        Text.bar(p, "<#8A919B>Recherche d'une zone de largage…");
        auto.pick(w, l -> farFromRaiders(p, l)).whenComplete((loc, err) -> {
            locating.remove(id);
            if (!p.isOnline() || inRaid(p)) {
                return;
            }
            finishDeploy(p, loc != null ? loc : pickSpawn(p));
        });
    }

    private boolean farFromRaiders(Player self, Location l) {
        double min2 = minSpawnDist * minSpawnDist;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o != self && inRaid(o) && o.getWorld() == l.getWorld() && o.getLocation().distanceSquared(l) < min2) {
                return false;
            }
        }
        return true;
    }

    private void finishDeploy(Player p, Location spawn) {
        if (spawn == null) {
            Text.msg(p, "<#C0392B>Aucun point d'apparition utilisable</#C0392B> (monde non chargé ?).");
            return;
        }
        p.closeInventory();
        Profile pr = profiles.get(p);
        List<String> names = new ArrayList<>(map.extracts.keySet());
        Collections.shuffle(names, random);
        pr.extracts = new ArrayList<>(names.subList(0, Math.min(extractsPerRaid, names.size())));
        pr.inRaid = true;
        pr.raidStarted = System.currentTimeMillis();
        pr.raidEnds = pr.raidStarted + durationMs;
        pr.raids++;
        profiles.save(pr);

        p.teleport(spawn);
        p.setFallDistance(0);
        showBar(p);
        Text.title(p, "<#C0392B><b>RAID</b>", "<#D7DCE2>Survis. Pille. <b>Extrais-toi.</b>", 5, 50, 15);
        p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.4f);
        Text.msg(p, "Tes extractions : <#2ECC71>" + String.join("</#2ECC71>, <#2ECC71>", pr.extracts)
                + "</#2ECC71> <#5A6270>(colonnes de lumière, /raid info)");
    }

    /** Le point le plus éloigné des autres joueurs en raid, au hasard parmi ceux qui respectent la distance minimale. */
    private Location pickSpawn(Player self) {
        List<Location> cands = new ArrayList<>();
        for (StoredLoc s : map.spawns) {
            Location l = s.toLocation();
            if (l != null) {
                cands.add(l);
            }
        }
        if (cands.isEmpty()) {
            return null;
        }
        List<Location> good = new ArrayList<>();
        Location best = null;
        double bestDist = -1;
        for (Location l : cands) {
            double nearest = Double.MAX_VALUE;
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (o != self && inRaid(o) && o.getWorld() == l.getWorld()) {
                    nearest = Math.min(nearest, o.getLocation().distance(l));
                }
            }
            if (nearest >= minSpawnDist) {
                good.add(l);
            }
            if (nearest > bestDist) {
                bestDist = nearest;
                best = l;
            }
        }
        return good.isEmpty() ? best : good.get(random.nextInt(good.size()));
    }

    // ------------------------------------------------------------------ fin de raid

    /** Extraction réussie : on garde tout. */
    public void extract(Player p, String via) {
        Profile pr = profiles.get(p);
        if (!pr.inRaid) {
            return;
        }
        pr.survived++;
        long secs = (System.currentTimeMillis() - pr.raidStarted) / 1000;
        leaveRaid(p, pr);
        heal(p);
        toHideout(p);
        plugin.phone().reward(p, extractBounty, "extraction réussie");
        Text.title(p, "<#2ECC71><b>EXTRAIT</b>", "<#8A919B>" + Text.esc(via) + " • " + Text.clock(secs), 5, 60, 20);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        Text.msg(p, "Extraction réussie par <#2ECC71>" + Text.esc(via) + "</#2ECC71>. Ton équipement est sauf.");
    }

    /** Temps écoulé : porté disparu, l'inventaire est perdu. */
    private void missing(Player p) {
        Profile pr = profiles.get(p);
        pr.deaths++;
        leaveRaid(p, pr);
        p.getInventory().clear();
        p.setItemOnCursor(null);
        heal(p);
        toHideout(p);
        plugin.phone().give(p);
        plugin.miniMap().give(p);
        Text.title(p, "<#C0392B><b>PORTÉ DISPARU</b>", "<#8A919B>Le temps du raid est écoulé. Tout est perdu.", 5, 70, 20);
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.4f, 1.2f);
    }

    private void leaveRaid(Player p, Profile pr) {
        pr.inRaid = false;
        pr.extracts = new ArrayList<>();
        extracting.remove(p.getUniqueId());
        extractingAt.remove(p.getUniqueId());
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) {
            p.hideBossBar(bar);
        }
        profiles.save(pr);
    }

    /** Admin : termine le raid d'un joueur comme une extraction. */
    public boolean forceEnd(Player p) {
        if (!inRaid(p)) {
            return false;
        }
        extract(p, "Ordre du commandement");
        return true;
    }

    private void toHideout(Player p) {
        Location h = hideout();
        if (h != null) {
            plugin.vehicles().eject(p);     // le véhicule reste sur l'île
            p.teleport(h);
            p.setFallDistance(0);
        }
    }

    private static void heal(Player p) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.setFoodLevel(20);
        p.setSaturation(5);
        p.setFireTicks(0);
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
    }

    // ------------------------------------------------------------------ boucle

    private void tick() {
        ticks += 10;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Profile pr = profiles.loaded(p.getUniqueId());
            if (pr == null) {
                continue;
            }
            if (!pr.inRaid) {
                if (ticks % 20 == 0) {
                    guardHideout(p);
                }
                continue;
            }
            if (now >= pr.raidEnds) {
                missing(p);
                continue;
            }
            updateBar(p, pr, now);
            checkExtraction(p, pr);
            beacons(p, pr);
        }
    }

    private void guardHideout(Player p) {
        Location h = hideout();
        GameMode gm = p.getGameMode();
        if (h == null || gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR || p.isDead()) {
            return;
        }
        if (p.getWorld() != h.getWorld() || p.getLocation().distanceSquared(h) > hideoutRadius * hideoutRadius) {
            p.teleport(h);
            Text.bar(p, "<#8A919B>Ouvre ton <#D7DCE2>tablette</#D7DCE2> (ou <#D7DCE2>/raid</#D7DCE2>) pour partir en raid.");
        }
    }

    private void checkExtraction(Player p, Profile pr) {
        Extract inside = null;
        for (String name : pr.extracts) {
            Extract ex = map.extracts.get(name);
            Location l = ex == null ? null : ex.loc().toLocation();
            if (l != null && l.getWorld() == p.getWorld() && l.distanceSquared(p.getLocation()) <= ex.radius() * ex.radius()) {
                inside = ex;
                break;
            }
        }
        UUID id = p.getUniqueId();
        if (inside == null || p.isDead()) {
            extractingAt.remove(id);
            if (extracting.remove(id) != null) {
                Text.bar(p, "<#C0392B>Extraction interrompue");
            }
            return;
        }
        int t = extracting.merge(id, 10, Integer::sum);
        int need = inside.seconds() * 20;
        if (t >= need) {
            extract(p, inside.name());
            return;
        }
        extractingAt.put(id, inside);
        double left = (need - t) / 20.0;
        if (plugin.hud().drawn(p)) {
            // Avec le pack : barre de progression de la console ; seul le son marque le temps.
            if (t % 20 == 0) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 0.6f + (float) (t / (double) need));
            }
            return;
        }
        Text.bar(p, "<#2ECC71>Extraction</#2ECC71> <#D7DCE2>" + Text.esc(inside.name()) + "</#D7DCE2>  "
                + Text.gauge(t / (double) need, 14, "#2ECC71", "#3A3F47")
                + "  <#8A919B>" + String.format(Locale.ROOT, "%.1f", left).replace('.', ',') + " s");
        if (t % 20 == 0) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 0.6f + (float) (t / (double) need));
        }
    }

    /** Colonnes de lumière visibles seulement par le joueur, sur ses propres extractions. */
    private void beacons(Player p, Profile pr) {
        for (String name : pr.extracts) {
            Extract ex = map.extracts.get(name);
            Location l = ex == null ? null : ex.loc().toLocation();
            if (l == null || l.getWorld() != p.getWorld() || l.distanceSquared(p.getLocation()) > 120 * 120) {
                continue;
            }
            for (double y = 0; y < 14; y += 1.5) {
                p.spawnParticle(Particle.END_ROD, l.getX(), l.getY() + y, l.getZ(), 1, 0.08, 0.3, 0.08, 0);
            }
            if (l.distanceSquared(p.getLocation()) < 30 * 30) {
                double a = (ticks / 10.0) * 0.4;
                for (int i = 0; i < 8; i++) {
                    double ang = a + i * Math.PI / 4;
                    p.spawnParticle(Particle.HAPPY_VILLAGER, l.getX() + Math.cos(ang) * ex.radius(), l.getY() + 0.2,
                            l.getZ() + Math.sin(ang) * ex.radius(), 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private void showBar(Player p) {
        BossBar bar = bars.computeIfAbsent(p.getUniqueId(),
                k -> BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10));
        p.showBossBar(bar);
        Profile pr = profiles.get(p);
        updateBar(p, pr, System.currentTimeMillis());
    }

    private void updateBar(Player p, Profile pr, long now) {
        BossBar bar = bars.get(p.getUniqueId());
        if (bar == null) {
            return;
        }
        long left = Math.max(0, pr.raidEnds - now);
        bar.progress((float) Math.max(0, Math.min(1, left / (double) Math.max(1, durationMs))));
        bar.color(left < 120_000 ? BossBar.Color.RED : BossBar.Color.YELLOW);
        bar.name(Text.mm("<#C0392B>☣</#C0392B> <#D7DCE2>" + Text.clock(left / 1000) + "</#D7DCE2>  <#5A6270>•</#5A6270>  <#8A919B>Extractions : <#2ECC71>"
                + String.join("<#5A6270>, </#5A6270>", pr.extracts)));
    }

    public void info(Player p) {
        Profile pr = profiles.get(p);
        if (!pr.inRaid) {
            Text.msg(p, "Tu es à la planque. Ton <#D7DCE2>tablette</#D7DCE2> ouvre le raid, la boutique et ton coffre "
                    + "<#5A6270>(/raid, /planque, /tablette)");
            return;
        }
        long left = (pr.raidEnds - System.currentTimeMillis()) / 1000;
        Text.msg(p, "Temps restant : <#D7DCE2>" + Text.clock(left));
        for (String name : pr.extracts) {
            Extract ex = map.extracts.get(name);
            Location l = ex == null ? null : ex.loc().toLocation();
            String where = l == null || l.getWorld() != p.getWorld() ? "?"
                    : (int) l.distance(p.getLocation()) + " m " + arrow(p, l);
            p.sendMessage(Text.mm("  <#2ECC71>▶ " + Text.esc(name) + "</#2ECC71> <#8A919B>" + where));
        }
    }

    /** Flèche relative au regard du joueur vers une position. */
    public static String arrow(Player p, Location to) {
        double dx = to.getX() - p.getX();
        double dz = to.getZ() - p.getZ();
        double target = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = ((target - p.getLocation().getYaw()) % 360 + 540) % 360 - 180;
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        int idx = (int) Math.round(rel / 45.0);
        return arrows[((idx % 8) + 8) % 8];
    }

    public String stats(Profile pr) {
        StringJoiner j = new StringJoiner("  <#5A6270>•</#5A6270>  ");
        j.add("Raids <#D7DCE2>" + pr.raids);
        j.add("Extraits <#2ECC71>" + pr.survived);
        j.add("Morts <#C0392B>" + pr.deaths);
        j.add("Joueurs tués <#D7DCE2>" + pr.pvpKills);
        j.add("Infestés tués <#D7DCE2>" + pr.infectedKills);
        return j.toString();
    }

    public void giveKit(Player p) {
        for (String spec : kit) {
            for (ItemStack it : plugin.specs().create(spec, true)) {
                GunItems.give(p, it);
            }
        }
    }

    // ------------------------------------------------------------------ événements

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            profiles.preload(e.getUniqueId(), e.getName());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        profiles.get(p);
        // Après les téléportations de connexion des autres plugins.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            Profile pr = profiles.get(p);
            if (pr.inRaid) {
                if (System.currentTimeMillis() >= pr.raidEnds) {
                    missing(p);
                } else {
                    showBar(p);
                    Text.msg(p, "<#E67E22>Raid en cours</#E67E22> : il te reste <#D7DCE2>"
                            + Text.clock((pr.raidEnds - System.currentTimeMillis()) / 1000) + "</#D7DCE2> pour t'extraire.");
                }
                return;
            }
            toHideout(p);
            if (!pr.kitGiven && !kit.isEmpty()) {
                pr.kitGiven = true;
                giveKit(p);
                profiles.save(pr);
                Text.msg(p, "Bienvenue dans la zone. Voici de quoi survivre à ton premier raid. "
                        + "Clic droit avec ton <#D7DCE2>tablette</#D7DCE2> pour partir.");
            }
        }, 3L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                plugin.phone().give(p);
                plugin.miniMap().give(p);
                plugin.phone().notifyUnread(p);
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuitEarly(PlayerQuitEvent e) {
        cancelDeploy(e.getPlayer().getUniqueId());
        e.getPlayer().closeInventory(); // enregistre le coffre de planque s'il est ouvert
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        extracting.remove(id);
        extractingAt.remove(id);
        BossBar bar = bars.remove(id);
        if (bar != null) {
            e.getPlayer().hideBossBar(bar);
        }
        profiles.unload(id);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Profile pr = profiles.get(p);
        if (!pr.inRaid) {
            // Mort à la planque (chute, /kill...) : rien n'est perdu.
            e.setKeepInventory(true);
            e.getDrops().clear();
            e.setKeepLevel(true);
            e.setDroppedExp(0);
            return;
        }
        e.setKeepInventory(false);
        e.getDrops().removeIf(plugin.phone()::isPhone); // la tablette ne se perd pas
        e.getDrops().removeIf(plugin.miniMap()::isMap); // la carte tactique non plus
        pr.deaths++;
        Player killer = p.getKiller();
        if (killer != null && killer != p) {
            Profile kp = profiles.get(killer);
            kp.pvpKills++;
            Text.msg(killer, "Tu as abattu <#C0392B>" + Text.esc(p.getName()) + "</#C0392B>. Son équipement est au sol.");
            plugin.phone().reward(killer, killBounty, "joueur abattu");
        }
        leaveRaid(p, pr);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                Text.title(p, "<#C0392B><b>MORT EN RAID</b>", "<#8A919B>Ton équipement est resté sur place.", 5, 60, 20);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Location h = hideout();
        if (h != null) {
            e.setRespawnLocation(h);
        }
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                plugin.phone().give(p);
                plugin.miniMap().give(p);
            }
        });
    }

    private boolean insideHideout(Location l, Location hideout) {
        return l.getWorld() == hideout.getWorld() && l.distanceSquared(hideout) <= hideoutRadius * hideoutRadius;
    }

    /**
     * Le bouton de départ de la map (« Launch in Earth », un bloc de commande qui téléporte sur l'île) lance le raid :
     * sa téléportation fixe est remplacée par le déploiement normal (compte à rebours, largage aléatoire).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunchTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        Location h = hideout();
        GameMode gm = p.getGameMode();
        if (h == null || e.getTo() == null || gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR || inRaid(p)) {
            return;
        }
        if (!insideHideout(e.getFrom(), h) || insideHideout(e.getTo(), h)) {
            return;
        }
        e.setCancelled(true);
        if (!isDeploying(p)) {
            deploy(p);
        }
    }

    /** La planque est une zone sûre ; on ne tire pas depuis la planque sur quelqu'un en raid non plus. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim) || map.hideout == null) {
            return;
        }
        if (!inRaid(victim) && e.getCause() != EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent ed && ed.getDamager() instanceof Player attacker && !inRaid(attacker)
                && attacker.getGameMode() != GameMode.CREATIVE) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!inRaid(p) || p.hasPermission("extraction.admin")) {
            return;
        }
        String label = e.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (!allowedCommands.contains(label)) {
            e.setCancelled(true);
            Text.msg(p, "<#C0392B>Commande bloquée en raid.</#C0392B> Seule issue : une extraction.");
        }
    }
}
