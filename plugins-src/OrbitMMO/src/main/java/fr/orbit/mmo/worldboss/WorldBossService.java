package fr.orbit.mmo.worldboss;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.mob.MobState;
import fr.orbit.mmo.registry.Defs.MobDef;
import fr.orbit.mmo.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Boss du monde : NÉMÉSIS, le Gardien déchu, sur le continent.
 *
 * Il apparaît à son repaire (posé par {@code /mmoadmin nemesis ici}) toutes les {@code nemesis.intervalle-minutes},
 * annoncé quelques minutes avant, puis attend les joueurs. Rien n'apparaît tant que le repaire n'est pas posé.
 * Branché par deux lignes dans OrbitMMO (start/stop) et deux dans MobService (mort, IA) ; la sous-commande
 * {@code /mmoadmin nemesis} s'ajoute à la commande existante sans toucher AdminCommand.
 */
public final class WorldBossService implements Listener {

    private static WorldBossService instance;

    private final OrbitMMO plugin;
    private final AnimatedModel.Data data;
    private final File stateFile;
    private BukkitTask task;
    private Nemesis fight;
    private Location home;
    private long nextSpawn;
    private boolean announced;
    private final List<long[]> tickets = new ArrayList<>();

    private WorldBossService(OrbitMMO plugin, AnimatedModel.Data data) {
        this.plugin = plugin;
        this.data = data;
        this.stateFile = new File(plugin.getDataFolder(), "nemesis.yml");
    }

    public static void start(OrbitMMO plugin) {
        AnimatedModel.Data data;
        try (InputStream in = plugin.getResource("boss/nemesis-anim.json")) {
            if (in == null) {
                plugin.getLogger().warning("Boss du monde : boss/nemesis-anim.json absent du jar, NÉMÉSIS désactivé.");
                return;
            }
            data = AnimatedModel.Data.load(in);
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Boss du monde : modèle de NÉMÉSIS illisible, boss désactivé.", ex);
            return;
        }
        instance = new WorldBossService(plugin, data);
        instance.enable();
    }

    public static void stop() {
        if (instance != null) {
            instance.disable();
            instance = null;
        }
    }

    /** Le golem de NÉMÉSIS : MobService ne doit ni le faire disparaître ni lui appliquer l'IA des monstres. */
    public static boolean owns(MobState ms) {
        return instance != null && instance.fight != null && instance.fight.host == ms;
    }

    /** Mort du golem : récompenses et chute du boss. @return true si c'était NÉMÉSIS (pas de récompense de monstre). */
    public static boolean onKilled(MobState ms, Player killer) {
        if (!owns(ms)) {
            return false;
        }
        instance.fight.onKilled(killer);
        return true;
    }

    // ------------------------------------------------------------------ Cycle

    private void enable() {
        YamlConfiguration st = YamlConfiguration.loadConfiguration(stateFile);
        if (st.contains("repaire.monde")) {
            World w = Bukkit.getWorld(st.getString("repaire.monde", ""));
            if (w != null) {
                home = new Location(w, st.getDouble("repaire.x"), st.getDouble("repaire.y"), st.getDouble("repaire.z"),
                        (float) st.getDouble("repaire.lacet"), 0f);
            } else {
                plugin.getLogger().warning("Boss du monde : monde du repaire introuvable (" + st.getString("repaire.monde") + ").");
            }
        }
        long first = Math.round(cfg().getDouble("nemesis.premiere-minutes", 30) * 60_000);
        nextSpawn = Math.max(st.getLong("prochaine", 0), System.currentTimeMillis() + first);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        hookCommand();
        for (World w : Bukkit.getWorlds()) {
            purge(w.getEntities());
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
        plugin.getLogger().info("Boss du monde : NÉMÉSIS (" + data.bones.size() + " os, " + data.animations.size()
                + " animations) ; repaire " + (home == null ? "non posé (/mmoadmin nemesis ici)" : describe(home))
                + (auto() ? "" : " ; apparition automatique coupée"));
    }

    private void disable() {
        if (task != null) {
            task.cancel();
        }
        HandlerList.unregisterAll(this);
        despawn();
        save();
    }

    private void tick() {
        if (fight != null) {
            if (!fight.tick()) {
                fight = null;
                releaseChunks();
            }
            return;
        }
        if (Bukkit.getCurrentTick() % 20 != 0 || home == null || !auto()) {
            return;
        }
        long now = System.currentTimeMillis();
        long warn = Math.round(cfg().getDouble("nemesis.annonce-minutes", 5) * 60_000);
        if (!announced && now >= nextSpawn - warn && !home.getWorld().getPlayers().isEmpty()) {
            announced = true;
            long min = Math.max(1, Math.round((nextSpawn - now) / 60_000.0));
            broadcast("<#B98CFF>La terre tremble… <gradient:#FFD66E:#7FD4FF><b>NÉMÉSIS</b></gradient> <#D7DCE2>s'éveillera dans "
                    + min + " minute" + (min > 1 ? "s" : "") + " vers <#FFD66E>" + home.getBlockX() + " / " + home.getBlockZ() + "</#FFD66E>.");
        }
        if (now >= nextSpawn && !home.getWorld().getPlayers().isEmpty()) {
            spawn(home);
        }
    }

    private boolean spawn(Location at) {
        if (fight != null) {
            return false;
        }
        int level = cfg().getInt("nemesis.niveau", 40);
        float modelScale = (float) cfg().getDouble("nemesis.echelle", 1.6);
        double hpMult = cfg().getDouble("nemesis.pv", 60);
        double dmgMult = cfg().getDouble("nemesis.degats", 2.6);
        double speed = cfg().getDouble("nemesis.vitesse", 1.0);
        // Hitbox : golem de fer (2,7 blocs) agrandi à la hauteur du modèle.
        double modelHeight = data.height / 16.0 * modelScale;
        double golemScale = Math.max(0.5, Math.min(16, modelHeight / 2.7));
        MobDef def = new MobDef(Nemesis.ID, Nemesis.NAME, EntityType.IRON_GOLEM, hpMult, dmgMult, speed, golemScale,
                1, 1, null, null);
        holdChunks(at);
        MobState ms = plugin.mobs().spawn(def, level, at, 0, null, 1);
        if (ms == null) {
            releaseChunks();
            return false;
        }
        LivingEntity e = ms.entity;
        e.setInvisible(true);
        e.setSilent(true);
        e.addScoreboardTag(Nemesis.TAG);
        AttributeInstance kb = e.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.setBaseValue(1);
        }
        AttributeInstance range = e.getAttribute(Attribute.FOLLOW_RANGE);
        if (range != null) {
            range.setBaseValue(64);
        }
        fight = new Nemesis(plugin, this, data, ms, at, level, cfg().getDouble("nemesis.pv-par-joueur", 0.35),
                cfg().getDouble("nemesis.laisse", 40), modelScale);
        announced = false;
        broadcast("<gradient:#FFD66E:#7FD4FF><b>NÉMÉSIS</b></gradient> <#D7DCE2>s'est éveillé vers <#FFD66E>"
                + at.getBlockX() + " / " + at.getBlockZ() + "</#FFD66E>. Le Gardien déchu attend les braves.");
        return true;
    }

    /** Appelé par le combat quand NÉMÉSIS tombe : prochaine apparition. */
    void defeated() {
        long every = Math.round(cfg().getDouble("nemesis.intervalle-minutes", 180) * 60_000);
        nextSpawn = System.currentTimeMillis() + every;
        announced = false;
        save();
    }

    private void despawn() {
        if (fight != null) {
            if (!fight.host.dead) {
                plugin.mobs().remove(fight.host);
            }
            fight.remove();
            fight = null;
        }
        releaseChunks();
    }

    /** Garde chargés les tronçons autour du boss (un golem non persistant disparaîtrait au déchargement). */
    private void holdChunks(Location at) {
        releaseChunks();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;
        int r = (int) Math.ceil(cfg().getDouble("nemesis.laisse", 40) / 16.0) + 1;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                at.getWorld().addPluginChunkTicket(x, z, plugin);
                tickets.add(new long[]{x, z});
            }
        }
        ticketWorld = at.getWorld();
    }

    private World ticketWorld;

    private void releaseChunks() {
        if (ticketWorld != null) {
            for (long[] t : tickets) {
                ticketWorld.removePluginChunkTicket((int) t[0], (int) t[1], plugin);
            }
        }
        tickets.clear();
        ticketWorld = null;
    }

    private void save() {
        YamlConfiguration st = new YamlConfiguration();
        if (home != null) {
            st.set("repaire.monde", home.getWorld().getName());
            st.set("repaire.x", home.getX());
            st.set("repaire.y", home.getY());
            st.set("repaire.z", home.getZ());
            st.set("repaire.lacet", home.getYaw());
        }
        st.set("prochaine", nextSpawn);
        try {
            st.save(stateFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Boss du monde : nemesis.yml non écrit", ex);
        }
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private boolean auto() {
        return cfg().getBoolean("nemesis.auto", true);
    }

    private void broadcast(String mm) {
        World w = home != null ? home.getWorld() : fight != null ? fight.host.entity.getWorld() : null;
        if (w == null) {
            return;
        }
        for (Player p : w.getPlayers()) {
            Text.msg(p, Text.PREFIX + mm);
        }
    }

    private static String describe(Location l) {
        return l.getWorld().getName() + " " + l.getBlockX() + " / " + l.getBlockY() + " / " + l.getBlockZ();
    }

    // ------------------------------------------------------------------ Événements

    /** Le golem ne frappe jamais de lui-même : seules les capacités de NÉMÉSIS blessent. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHostAttack(EntityDamageByEntityEvent e) {
        if (fight != null && e.getDamager().equals(fight.host.entity)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        purge(e.getEntities());
    }

    /** Pièces de modèle restées d'un arrêt brutal du serveur. */
    private void purge(List<Entity> entities) {
        for (Entity en : entities) {
            if (en.getScoreboardTags().contains(Nemesis.TAG) && (fight == null || en != fight.host.entity)) {
                if (fight == null || !en.getType().equals(EntityType.ITEM_DISPLAY) || !en.isValid()) {
                    en.remove();
                }
            }
        }
    }

    // ------------------------------------------------------------------ Commande /mmoadmin nemesis

    private void hookCommand() {
        PluginCommand cmd = plugin.getCommand("mmoadmin");
        if (cmd == null) {
            plugin.getLogger().warning("Boss du monde : commande mmoadmin absente, /mmoadmin nemesis indisponible.");
            return;
        }
        CommandExecutor ex = cmd.getExecutor();
        TabCompleter tc = cmd.getTabCompleter();
        cmd.setExecutor((s, c, l, a) -> {
            if (a.length > 0 && a[0].equalsIgnoreCase("nemesis")) {
                return run(s, Arrays.copyOfRange(a, 1, a.length));
            }
            return ex.onCommand(s, c, l, a);
        });
        cmd.setTabCompleter((s, c, l, a) -> {
            if (a.length == 2 && a[0].equalsIgnoreCase("nemesis")) {
                return filter(List.of("etat", "ici", "invoquer", "retirer", "attaque", "pv", "prochaine"), a[1]);
            }
            if (a.length == 3 && a[0].equalsIgnoreCase("nemesis") && a[1].equalsIgnoreCase("attaque")) {
                List<String> names = new ArrayList<>();
                for (Nemesis.Attack at : Nemesis.Attack.values()) {
                    names.add(at.name().toLowerCase(Locale.ROOT));
                }
                return filter(names, a[2]);
            }
            if (a.length == 3 && a[0].equalsIgnoreCase("nemesis") && a[1].equalsIgnoreCase("invoquer")) {
                return filter(List.of("ici"), a[2]);
            }
            List<String> base = tc == null ? new ArrayList<>() : new ArrayList<>(tc.onTabComplete(s, c, l, a));
            if (a.length == 1 && "nemesis".startsWith(a[0].toLowerCase(Locale.ROOT))) {
                base.add("nemesis");
            }
            return base;
        });
    }

    private static List<String> filter(List<String> all, String start) {
        List<String> out = new ArrayList<>();
        for (String s : all) {
            if (s.startsWith(start.toLowerCase(Locale.ROOT))) {
                out.add(s);
            }
        }
        return out;
    }

    private boolean run(CommandSender s, String[] a) {
        if (!s.hasPermission("orbitmmo.admin")) {
            Text.msg(s, "<#FF5C5C>Réservé aux administrateurs.");
            return true;
        }
        String sub = a.length == 0 ? "etat" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "ici" -> {
                if (!(s instanceof Player p)) {
                    Text.msg(s, "<#FF5C5C>À faire en jeu, debout à l'endroit du repaire.");
                    return true;
                }
                home = p.getLocation().clone();
                home.setPitch(0f);
                save();
                Text.msg(s, "<#5EE89A>Repaire de NÉMÉSIS posé : " + describe(home) + " (il regardera dans votre direction).");
            }
            case "invoquer" -> {
                Location at;
                if (a.length > 1 && a[1].equalsIgnoreCase("ici") && s instanceof Player p) {
                    at = p.getLocation().add(Nemesis.forward(p.getLocation().getYaw()).multiply(8));
                    at.setYaw(p.getLocation().getYaw() + 180);
                } else if (home != null) {
                    at = home;
                } else {
                    Text.msg(s, "<#FF5C5C>Pas de repaire : /mmoadmin nemesis ici, ou /mmoadmin nemesis invoquer ici.");
                    return true;
                }
                if (fight != null) {
                    Text.msg(s, "<#FF5C5C>NÉMÉSIS est déjà là. /mmoadmin nemesis retirer d'abord.");
                    return true;
                }
                Text.msg(s, spawn(at) ? "<#5EE89A>NÉMÉSIS invoqué en " + describe(at) + "." : "<#FF5C5C>Apparition refusée.");
            }
            case "retirer" -> {
                boolean had = fight != null;
                despawn();
                Text.msg(s, had ? "<#5EE89A>NÉMÉSIS retiré (sans récompense)." : "<#8C96A3>NÉMÉSIS n'est pas là.");
            }
            case "attaque" -> {
                if (fight == null || a.length < 2) {
                    Text.msg(s, "<#8C96A3>/mmoadmin nemesis attaque <frappe|onde|etoiles|rayon|fissures|charge> (boss présent).");
                    return true;
                }
                Nemesis.Attack at;
                try {
                    at = Nemesis.Attack.valueOf(a[1].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    Text.msg(s, "<#FF5C5C>Attaque inconnue : " + Text.esc(a[1]));
                    return true;
                }
                Text.msg(s, fight.force(at) ? "<#5EE89A>" + at.label + " lancée." : "<#FF5C5C>Impossible : attaque en cours ou aucun joueur à portée.");
            }
            case "pv" -> {
                if (fight == null || a.length < 2) {
                    Text.msg(s, "<#8C96A3>/mmoadmin nemesis pv <pourcentage> (boss présent).");
                    return true;
                }
                try {
                    double pct = Math.max(1, Math.min(100, Double.parseDouble(a[1].replace(',', '.'))));
                    fight.host.hp = fight.host.maxHp * pct / 100.0;
                    Text.msg(s, "<#5EE89A>PV de NÉMÉSIS à " + pct + " %.");
                } catch (NumberFormatException ex) {
                    Text.msg(s, "<#FF5C5C>Nombre attendu.");
                }
            }
            case "prochaine" -> {
                double min = a.length > 1 ? parse(a[1]) : -1;
                if (min >= 0) {
                    nextSpawn = System.currentTimeMillis() + Math.round(min * 60_000);
                    announced = false;
                    save();
                }
                long left = Math.max(0, nextSpawn - System.currentTimeMillis());
                Text.msg(s, "<#D7DCE2>Prochaine apparition dans " + Math.round(left / 60_000.0) + " min"
                        + (home == null ? " <#FF8A3B>(aucun repaire : rien n'apparaîtra)" : "") + ".");
            }
            default -> {
                Text.msg(s, "<#FFD66E><b>NÉMÉSIS</b> <#8C96A3>(boss du monde)");
                Text.msg(s, "<#D7DCE2>Repaire : " + (home == null ? "<#FF8A3B>non posé" : describe(home))
                        + " · auto : " + (auto() ? "oui" : "non"));
                if (fight != null) {
                    LivingEntity e = fight.host.entity;
                    Text.msg(s, "<#D7DCE2>En jeu : " + describe(e.getLocation()) + " · PV " + Math.round(fight.host.hp) + " / "
                            + Math.round(fight.host.maxHp) + " · phase " + fight.phase() + " · attaque : " + fight.attackName()
                            + " · " + fight.host.damagers.size() + " participant(s)");
                } else {
                    long left = Math.max(0, nextSpawn - System.currentTimeMillis());
                    Text.msg(s, "<#D7DCE2>Absent · prochaine apparition dans " + Math.round(left / 60_000.0) + " min");
                }
                Text.msg(s, "<#8C96A3>ici · invoquer [ici] · retirer · attaque <nom> · pv <%> · prochaine [minutes]");
            }
        }
        return true;
    }

    private static double parse(String v) {
        try {
            return Double.parseDouble(v.replace(',', '.'));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
