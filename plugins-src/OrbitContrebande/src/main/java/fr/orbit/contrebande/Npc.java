package fr.orbit.contrebande;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le PNJ de CONTREBANDE dans la salle d'arcade (monde du casino, mur est : réglage {@code pnj}). Posé quand le monde
 * du casino existe (OrbitCasino le crée un tick après son démarrage), non persistant, recréé s'il disparaît.
 */
final class Npc implements Listener {

    private final OrbitContrebande plugin;
    private final List<Entity> spawned = new ArrayList<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private UUID npc;
    private World world;
    private BukkitTask watch;
    private int tries;

    Npc(OrbitContrebande plugin) {
        this.plugin = plugin;
    }

    void startWhenReady() {
        watch = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (world == null) {
                world = Bukkit.getWorld("casino");
                if (world == null) {
                    if (++tries == 30) {
                        plugin.getLogger().warning("Monde du casino introuvable après une minute : PNJ de CONTREBANDE non posé.");
                    }
                    return;
                }
                Location l = location();
                world.getChunkAt(l.getBlockX() >> 4, l.getBlockZ() >> 4).addPluginChunkTicket(plugin);
                spawn();
                return;
            }
            Entity e = npc == null ? null : Bukkit.getEntity(npc);
            if (e == null || !e.isValid()) {
                spawn();
            }
        }, 40L, 40L);
    }

    private Location location() {
        String[] s = plugin.getConfig().getString("pnj", "19.5 64 392.5 90").trim().split("\\s+");
        double x = 19.5, y = 64, z = 392.5;
        float yaw = 90;
        try {
            x = Double.parseDouble(s[0]);
            y = Double.parseDouble(s[1]);
            z = Double.parseDouble(s[2]);
            yaw = Float.parseFloat(s[3]);
        } catch (RuntimeException ignored) {
            // valeurs par défaut
        }
        return new Location(world, x, y, z, yaw, 0f);
    }

    /** Devant le PNJ, face à lui (retour après une partie). */
    Location front() {
        if (world == null) {
            return null;
        }
        Location n = location();
        double yaw = Math.toRadians(n.getYaw());
        Location f = n.clone().add(-Math.sin(yaw) * 2.0, 0, Math.cos(yaw) * 2.0);
        f.setYaw(n.getYaw() + 180f);
        f.setPitch(0f);
        return f;
    }

    /** Dans la salle d'arcade ? */
    boolean near(Player p) {
        if (world == null || !p.getWorld().equals(world)) {
            return false;
        }
        Location l = p.getLocation();
        return Math.abs(l.getX()) <= 24 && l.getZ() >= 382 && l.getZ() <= 418 && l.getY() > 55 && l.getY() < 80;
    }

    void spawn() {
        remove();
        if (world == null) {
            return;
        }
        for (Entity e : world.getEntities()) {
            if (e.getPersistentDataContainer().has(plugin.key(), PersistentDataType.BYTE)) {
                e.remove();
            }
        }
        Location l = location();
        Villager v = world.spawn(l, Villager.class, CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
            e.setPersistent(false);
            e.getPersistentDataContainer().set(plugin.key(), PersistentDataType.BYTE, (byte) 1);
            e.setAI(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setCollidable(false);
            e.setRemoveWhenFarAway(false);
            e.customName(Txt.mm("<#E0B864>Borne · CONTREBANDE"));
            e.setCustomNameVisible(true);
            e.setProfession(Villager.Profession.LEATHERWORKER);
            e.setVillagerType(Villager.Type.SWAMP);
            e.setVillagerLevel(5);
            e.setRotation(l.getYaw(), 0f);
        });
        npc = v.getUniqueId();
        spawned.add(v);
        TextDisplay td = world.spawn(l.clone().add(0, 2.9, 0), TextDisplay.class, e -> {
            e.setPersistent(false);
            e.getPersistentDataContainer().set(plugin.key(), PersistentDataType.BYTE, (byte) 1);
            e.text(Txt.mm(Txt.TITLE + "\n<#E6EDF3>Bâtis ton empire aux Docks d'Orbite\n<#E6EDF3>Produis, mélange, livre… et sème la douane\n"
                    + "<#C79BFF>Seul ou jusqu'à 4 amis · tout est sauvegardé\n<#9AA4B2>Clic droit pour jouer"));
            e.setBillboard(Display.Billboard.CENTER);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setShadowed(true);
            e.setBackgroundColor(Color.fromARGB(150, 10, 12, 18));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setLineWidth(220);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.7f, 0.7f, 0.7f), new AxisAngle4f()));
        });
        spawned.add(td);
    }

    void remove() {
        for (Entity e : spawned) {
            e.remove();
        }
        spawned.clear();
        npc = null;
    }

    void stop() {
        if (watch != null) {
            watch.cancel();
        }
        remove();
    }

    private boolean isNpc(Entity e) {
        return npc != null && npc.equals(e.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (isNpc(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!isNpc(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        Long prev = lastClick.put(p.getUniqueId(), now);
        if (prev != null && now - prev < 800) {
            return;
        }
        plugin.sessions().accueil(p);
    }
}
