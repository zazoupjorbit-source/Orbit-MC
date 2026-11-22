package fr.orbit.reborn.floor;

import fr.orbit.reborn.Keys;
import fr.orbit.reborn.RebornMMO;
import fr.orbit.reborn.player.PlayerData;
import fr.orbit.reborn.registry.Defs.FloorDef;
import fr.orbit.reborn.ui.Menus;
import fr.orbit.reborn.util.Fx;
import fr.orbit.reborn.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Les étages : mondes, ville (zone sûre), porte de téléportation, salle du boss, progression du serveur. */
public final class FloorService implements Listener {

    public static final String PREFIX = "reborn_etage_";

    private final RebornMMO plugin;
    private final File stateFile;
    private int highestCleared;
    private final Set<Integer> built = new HashSet<>();
    private final Map<Integer, Long> bossReady = new HashMap<>();
    private final Map<UUID, Long> gateCooldown = new HashMap<>();
    private final Map<UUID, Long> guardWarn = new HashMap<>();

    public FloorService(RebornMMO plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "state.yml");
        loadState();
    }

    /** Charge les étages déjà ouverts (et crée l'étage 1 au premier démarrage). */
    public void enable() {
        for (int n = 1; n <= Math.min(maxFloor(), highestCleared + 1); n++) {
            world(n);
        }
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::gateFx, 40L, 10L);
    }

    public int radius() {
        return Math.max(90, plugin.getConfig().getInt("floors.radius", 160));
    }

    public int townRadius() {
        return 28;
    }

    public int arenaRadius() {
        return 18;
    }

    public int arenaX() {
        return (int) Math.round(radius() * 0.62);
    }

    public int maxFloor() {
        return plugin.registry().maxFloor();
    }

    public FloorDef def(int n) {
        return plugin.registry().floor(n);
    }

    public int highestCleared() {
        return highestCleared;
    }

    public void setHighestCleared(int n) {
        highestCleared = Math.max(0, Math.min(maxFloor(), n));
        saveState();
    }

    public boolean unlocked(int n) {
        return n >= 1 && n <= Math.min(maxFloor(), highestCleared + 1);
    }

    public long bossReadyAt(int floor) {
        return bossReady.getOrDefault(floor, 0L);
    }

    public void setBossReadyAt(int floor, long at) {
        bossReady.put(floor, at);
        saveState();
    }

    public int floorOf(World w) {
        if (w == null || !w.getName().startsWith(PREFIX)) {
            return 0;
        }
        try {
            return Integer.parseInt(w.getName().substring(PREFIX.length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Monde de l'étage, chargé ou généré à la demande. */
    public World world(int n) {
        String name = PREFIX + n;
        World w = Bukkit.getWorld(name);
        if (w != null) {
            return w;
        }
        FloorDef def = def(n);
        long seed = 7919L * n + 1337L;
        plugin.getLogger().info("Chargement de l'étage " + n + " (" + def.name() + ")…");
        w = new WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .generator(new FloorGenerator(def.theme(), radius(), arenaX(), seed))
                .generateStructures(false)
                .seed(seed)
                .createWorld();
        if (w == null) {
            plugin.getLogger().severe("Impossible de créer le monde " + name);
            return null;
        }
        setup(w);
        if (built.add(n)) {
            FloorBuilder.build(w, def, townRadius(), arenaX(), arenaRadius());
            saveState();
        }
        w.getChunkAt(0, 0).addPluginChunkTicket(plugin);
        return w;
    }

    private void setup(World w) {
        w.setDifficulty(Difficulty.NORMAL);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        w.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        w.setGameRule(GameRule.DO_WARDEN_SPAWNING, false);
        w.setGameRule(GameRule.DO_INSOMNIA, false);
        w.setGameRule(GameRule.NATURAL_REGENERATION, false);
        w.setGameRule(GameRule.KEEP_INVENTORY, true);
        w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        w.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
        w.setGameRule(GameRule.MOB_GRIEFING, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.SPAWN_RADIUS, 0);
        w.setStorm(false);
        w.setThundering(false);
        w.setSpawnLocation(0, FloorGenerator.SURFACE + 1, 5);
    }

    public Location town(World w) {
        return new Location(w, 0.5, FloorGenerator.SURFACE + 1, 5.5, 180f, 0f);
    }

    public Location arenaCenter(World w) {
        return new Location(w, arenaX() + 0.5, FloorGenerator.SURFACE + 1, 0.5);
    }

    public boolean isSafe(Location l) {
        if (l == null || floorOf(l.getWorld()) <= 0) {
            return false;
        }
        double r = townRadius();
        return l.getX() * l.getX() + l.getZ() * l.getZ() <= r * r;
    }

    public boolean inArena(Location l) {
        if (l == null || floorOf(l.getWorld()) <= 0) {
            return false;
        }
        double dx = l.getX() - (arenaX() + 0.5);
        double dz = l.getZ() - 0.5;
        return dx * dx + dz * dz <= arenaRadius() * arenaRadius();
    }

    /** Téléporte à la ville d'un étage, avec la colonne de lumière bleue. */
    public void teleport(Player p, int n, boolean effects) {
        World w = world(n);
        if (w == null) {
            Text.msg(p, "<#FF5C5C>Cet étage est indisponible.");
            return;
        }
        PlayerData d = plugin.players().get(p);
        d.floor = n;
        Location target = town(w);
        if (effects) {
            Fx.teleport(p.getLocation());
        }
        p.teleportAsync(target).thenAccept(ok -> {
            if (!ok || !p.isOnline() || !effects) {
                return;
            }
            Fx.teleport(target);
            FloorDef def = def(n);
            Text.title(p, "<#7FD4FF><b>Étage " + n + "</b>",
                    "<#D7DCE2>" + Text.esc(def.town()) + " <#5A6270>·</#5A6270> " + Text.esc(def.name()), 5, 50, 15);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        Location from = e.getFrom();
        if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ()) {
            return;
        }
        if (floorOf(to.getWorld()) <= 0) {
            return;
        }
        Player p = e.getPlayer();
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        if (d == null || d.classId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        // Les gardes refusent l'entrée de la ville aux curseurs orange et rouges.
        if (isSafe(to) && !isSafe(from) && plugin.cursor().state(d) > 0 && p.getGameMode() != GameMode.CREATIVE) {
            e.setTo(from);
            Vector out = from.toVector().setY(0);
            if (out.lengthSquared() > 1.0E-4) {
                p.setVelocity(out.normalize().multiply(0.6).setY(0.2));
            }
            Long warned = guardWarn.get(p.getUniqueId());
            if (warned == null || warned < now) {
                guardWarn.put(p.getUniqueId(), now + 3000);
                Text.msg(p, "<#FF8A3B>Les gardes de la ville vous refusent l'entrée tant que votre curseur n'est pas vert.");
            }
            return;
        }
        // Passage sous l'arche de la porte de téléportation.
        if (Math.abs(to.getX() - 0.5) <= 1.4 && Math.abs(to.getZ() - 0.5) <= 0.8
                && to.getY() >= FloorGenerator.SURFACE + 0.5 && to.getY() <= FloorGenerator.SURFACE + 5) {
            Long cd = gateCooldown.get(p.getUniqueId());
            if (cd == null || cd < now) {
                gateCooldown.put(p.getUniqueId(), now + 4000);
                Menus.floors(plugin, p, true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractNpc(PlayerInteractEntityEvent e) {
        String tag = e.getRightClicked().getPersistentDataContainer().get(Keys.NPC, PersistentDataType.STRING);
        if (tag == null) {
            return;
        }
        e.setCancelled(true);
        if ("merchant".equals(tag)) {
            Menus.shop(plugin, e.getPlayer());
        }
    }

    private void gateFx() {
        for (World w : Bukkit.getWorlds()) {
            if (floorOf(w) <= 0 || w.getPlayers().isEmpty()) {
                continue;
            }
            Location gate = new Location(w, 0.5, FloorGenerator.SURFACE + 3.5, 0.5);
            boolean near = false;
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(gate) < 48 * 48) {
                    near = true;
                    break;
                }
            }
            if (near) {
                w.spawnParticle(Particle.END_ROD, gate, 4, 1.0, 1.4, 0.15, 0.01);
                Fx.dust(gate, Fx.color("#7FD4FF"), 1.4f, 8, 0.9);
            }
        }
    }

    private void loadState() {
        if (!stateFile.isFile()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(stateFile);
        highestCleared = y.getInt("highest-cleared");
        built.addAll(y.getIntegerList("built"));
        ConfigurationSection br = y.getConfigurationSection("boss-ready");
        if (br != null) {
            for (String k : br.getKeys(false)) {
                try {
                    bossReady.put(Integer.parseInt(k), br.getLong(k));
                } catch (NumberFormatException ignored) {
                    // clé invalide
                }
            }
        }
    }

    public void saveState() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("highest-cleared", highestCleared);
        y.set("built", List.copyOf(built));
        for (Map.Entry<Integer, Long> e : bossReady.entrySet()) {
            y.set("boss-ready." + e.getKey(), e.getValue());
        }
        try {
            y.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Échec de la sauvegarde de state.yml", e);
        }
    }
}
