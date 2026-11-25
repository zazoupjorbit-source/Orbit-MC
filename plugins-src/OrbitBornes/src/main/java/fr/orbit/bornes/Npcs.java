package fr.orbit.bornes;

import fr.orbit.bornes.pulsar.Songs;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les 4 PNJ des bornes dans la salle d'arcade (monde du casino, emplacements du contrat : x = -17.5 / -13.5 / -9.5 / -5.5,
 * y 64, z 411.5, regard vers le nord). Créés quand le monde du casino existe (OrbitCasino le crée un tick après son
 * démarrage), non persistants, recréés s'ils disparaissent.
 */
public final class Npcs implements Listener {

    private final OrbitBornes plugin;
    private final Map<GameType, UUID> npcs = new EnumMap<>(GameType.class);
    private final List<Entity> spawned = new ArrayList<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private World world;
    private BukkitTask watch;
    private int tries;

    Npcs(OrbitBornes plugin) {
        this.plugin = plugin;
    }

    /** Attend ~40 ticks puis le monde du casino (jusqu'à une minute), pose les PNJ et les surveille. */
    void startWhenReady() {
        watch = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (world == null) {
                world = Bukkit.getWorld(plugin.getConfig().getString("pnj.monde", "casino"));
                if (world == null) {
                    if (++tries == 30) {
                        plugin.getLogger().warning("Monde du casino introuvable après une minute : PNJ des bornes non posés.");
                    }
                    return;
                }
                for (GameType t : GameType.values()) {
                    Location l = npcLocation(t);
                    world.getChunkAt(l.getBlockX() >> 4, l.getBlockZ() >> 4).addPluginChunkTicket(plugin);
                }
                spawnAll();
                return;
            }
            for (UUID id : npcs.values()) {
                Entity e = Bukkit.getEntity(id);
                if (e == null || !e.isValid()) {
                    spawnAll();
                    return;
                }
            }
        }, 40L, 40L);
    }

    World world() {
        return world;
    }

    private Location npcLocation(GameType t) {
        World w = world != null ? world : Bukkit.getWorld(plugin.getConfig().getString("pnj.monde", "casino"));
        return new Location(w, t.npcX, plugin.getConfig().getDouble("pnj.y", 64), plugin.getConfig().getDouble("pnj.z", 411.5),
                (float) plugin.getConfig().getDouble("pnj.yaw", 180), 0f);
    }

    /** Où l'on revient après une partie : devant le PNJ, face à lui. */
    public Location front(GameType t) {
        if (world == null) {
            return null;
        }
        Location n = npcLocation(t);
        double yaw = Math.toRadians(n.getYaw());
        // devant = dans la direction du regard du PNJ
        Location f = n.clone().add(-Math.sin(yaw) * 2.0, 0, Math.cos(yaw) * 2.0);
        f.setYaw(n.getYaw() + 180f);
        f.setPitch(0f);
        return f;
    }

    /** Dans la salle d'arcade ? */
    public boolean near(Player p) {
        if (world == null || !p.getWorld().equals(world)) {
            return false;
        }
        Location l = p.getLocation();
        return Math.abs(l.getX()) <= 24 && l.getZ() >= 382 && l.getZ() <= 418 && l.getY() > 55 && l.getY() < 80;
    }

    void spawnAll() {
        removeAll();
        if (world == null) {
            return;
        }
        for (Entity e : world.getEntities()) {
            if (e.getPersistentDataContainer().has(plugin.key(), PersistentDataType.BYTE)) {
                e.remove();
            }
        }
        for (GameType t : GameType.values()) {
            Location l = npcLocation(t);
            Villager v = world.spawn(l, Villager.class, CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
                e.setPersistent(false);
                e.getPersistentDataContainer().set(plugin.key(), PersistentDataType.BYTE, (byte) 1);
                e.setAI(false);
                e.setSilent(true);
                e.setInvulnerable(true);
                e.setCollidable(false);
                e.setRemoveWhenFarAway(false);
                e.customName(Txt.mm("<" + t.c1 + ">Borne · " + t.name));
                e.setCustomNameVisible(true);
                e.setProfession(t.profession);
                e.setVillagerLevel(5);
                e.setRotation(l.getYaw(), 0f);
            });
            spawned.add(v);
            npcs.put(t, v.getUniqueId());
            TextDisplay td = world.spawn(l.clone().add(0, 2.9, 0), TextDisplay.class, e -> {
                e.setPersistent(false);
                e.getPersistentDataContainer().set(plugin.key(), PersistentDataType.BYTE, (byte) 1);
                e.text(Txt.mm(t.title() + "\n<#E6EDF3>" + t.description.replace("\n", "\n<#E6EDF3>") + "\n<#9AA4B2>Clic droit pour jouer"));
                e.setBillboard(Display.Billboard.CENTER);
                e.setAlignment(TextDisplay.TextAlignment.CENTER);
                e.setShadowed(true);
                e.setBackgroundColor(Color.fromARGB(150, 10, 12, 18));
                e.setBrightness(new Display.Brightness(15, 15));
                e.setLineWidth(200);
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.7f, 0.7f, 0.7f), new AxisAngle4f()));
            });
            spawned.add(td);
        }
    }

    void removeAll() {
        for (Entity e : spawned) {
            e.remove();
        }
        spawned.clear();
        npcs.clear();
    }

    void stop() {
        if (watch != null) {
            watch.cancel();
        }
        removeAll();
    }

    private GameType which(Entity e) {
        for (Map.Entry<GameType, UUID> en : npcs.entrySet()) {
            if (en.getValue().equals(e.getUniqueId())) {
                return en.getKey();
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (which(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        GameType t = which(event.getRightClicked());
        if (t == null) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = lastClick.put(p.getUniqueId(), now);
        if (prev != null && now - prev < 800) {
            return;
        }
        Game g = plugin.sessions().game(p);
        if (g instanceof fr.orbit.bornes.cristaux.CristauxGame cg && t == GameType.CRISTAUX) {
            cg.reopen();
            return;
        }
        if (plugin.sessions().playing(p)) {
            Txt.send(p, "<#9AA4B2>Tu es déjà en partie (<#E6EDF3>/bornes quitter</#E6EDF3> pour abandonner).");
            return;
        }
        switch (t) {
            case ASTROBLOCS -> astroMenu(p);
            case PULSAR -> pulsarMenu(p);
            default -> plugin.sessions().play(p, t, new String[0]);
        }
    }

    void astroMenu(Player p) {
        Txt.send(p, GameType.ASTROBLOCS.title() + " <#9AA4B2>· Q/D déplacer · Z tourner · S descendre · Espace poser · Maj réserve");
        p.sendMessage(Txt.mm("  ").append(Txt.button("Solo", "#62CFF4", "/bornes jouer astroblocs solo", "Partie seul : le plus de lignes possible"))
                .append(Txt.mm(" ")).append(Txt.button("Duel", "#FF4FD8", "/bornes jouer astroblocs duel",
                        "Contre un joueur (ou le bot ORBI-7) : tes lignes montent chez l'autre"))
                .append(Txt.mm(" ")).append(Txt.button("Classement", "#E0B864", "/bornes top astroblocs", "Les 10 meilleurs")));
    }

    public void pulsarMenu(Player p) {
        Txt.send(p, GameType.PULSAR.title() + " <#9AA4B2>· choisis un morceau (Q S Z D ou A S W D pour frapper)");
        for (Songs.Song s : Songs.ALL) {
            p.sendMessage(Txt.mm("  <#E6EDF3>" + s.title() + " <#5A6270>· " + s.credit() + "  ")
                    .append(Txt.button("Normal", "#62CFF4", "/bornes jouer pulsar " + s.id() + " normal", "Notes principales"))
                    .append(Txt.mm(" "))
                    .append(Txt.button("Expert", "#FF4FD8", "/bornes jouer pulsar " + s.id() + " expert", "Toutes les notes, défilement rapide")));
        }
    }
}
