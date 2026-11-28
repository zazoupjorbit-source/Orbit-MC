package fr.orbit.casino.arcade;

import fr.orbit.casino.Holo;
import fr.orbit.casino.OrbitCasino;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Bornes d'arcade 3D de la salle cachée : à l'emplacement de chaque PNJ de jeu (les siens et ceux d'OrbitBornes,
 * OrbitArene, OrbitRoyale, OrbitChasseurs, OrbitContrebande, OrbitBowl), une borne texturée (modèles orbitcasino:borne_&lt;id&gt; du pack du casino,
 * affiche HD sur l'écran et le fronton). Le PNJ reste là, invisible et un peu agrandi : c'est toujours lui qu'on
 * clique, donc les autres plugins n'ont rien à changer. Vérifié toutes les 2 s (PNJ reposés, /casino generer…).
 * Réglages : arcade.bornes.* (actif, echelle, lacet, pnj-taille, emplacements « x z lacet »).
 */
public final class Cabinets {

    private record Spot(String id, double x, double z, float yaw) {
    }

    private final OrbitCasino plugin;
    private final World world;
    private final List<Spot> spots = new ArrayList<>();
    private final Map<String, UUID[]> displays = new LinkedHashMap<>();
    private BukkitTask task;
    private float scale, yawOffset, npcScale;

    public Cabinets(OrbitCasino plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    public void start() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("arcade.bornes");
        if (c != null && !c.getBoolean("actif", true)) {
            return;
        }
        scale = c == null ? 1.25f : (float) c.getDouble("echelle", 1.25);
        yawOffset = c == null ? 0f : (float) c.getDouble("lacet", 0);
        npcScale = c == null ? 1.3f : (float) c.getDouble("pnj-taille", 1.3);
        Location rush = ArcadeRoom.rushNpc(world), vol = ArcadeRoom.volNpc(world), cas = ArcadeRoom.casinoNpc(world);
        add(c, "rush", rush.getX(), rush.getZ(), rush.getYaw());
        add(c, "vol", vol.getX(), vol.getZ(), vol.getYaw());
        add(c, "casino", cas.getX(), cas.getZ(), cas.getYaw());
        // emplacements du contrat docs/arcade-jeux.md
        add(c, "astroblocs", -17.5, 411.5, 180);
        add(c, "cristaux", -13.5, 411.5, 180);
        add(c, "defense", -9.5, 411.5, 180);
        add(c, "pulsar", -5.5, 411.5, 180);
        add(c, "nebuleuse", 6.5, 411.5, 180);
        add(c, "nova", 10.5, 411.5, 180);
        add(c, "palet", 14.5, 411.5, 180);
        add(c, "turbo", 18.5, 411.5, 180);
        add(c, "chasseurs", -12.5, 389.5, 0);
        add(c, "siege", 12.5, 389.5, 0);
        add(c, "contrebande", 19.5, 392.5, 90);   // PNJ d'OrbitContrebande (mur est)
        add(c, "bowl", -18.5, 392.5, -90);        // portier d'ORBIT BOWL (mur ouest)
        add(c, "breche", 19.5, 407.5, 90);        // instructrice de BRÈCHE (mur est)
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 60L, 40L);
        plugin.getLogger().info("Bornes d'arcade : " + spots.size() + " bornes 3D à la place des PNJ.");
    }

    private void add(ConfigurationSection c, String id, double x, double z, float yaw) {
        String over = c == null ? null : c.getString("emplacements." + id);
        if (over != null && !over.isBlank()) {
            String[] p = over.trim().split("\\s+");
            try {
                x = Double.parseDouble(p[0]);
                z = Double.parseDouble(p[1]);
                if (p.length > 2) {
                    yaw = Float.parseFloat(p[2]);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("arcade.bornes.emplacements." + id + " illisible : « " + over + " » (attendu « x z [lacet] »).");
            }
        }
        spots.add(new Spot(id, x, z, yaw));
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID[] ids : displays.values()) {
            for (UUID id : ids) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) {
                    e.remove();
                }
            }
        }
        displays.clear();
    }

    private void tick() {
        for (Spot s : spots) {
            Location at = new Location(world, s.x, ArcadeRoom.Y, s.z);
            if (!world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
                continue;
            }
            UUID[] ids = displays.get(s.id);
            if (ids == null || !valid(ids[0]) || !valid(ids[1])) {
                if (ids != null) {
                    for (UUID id : ids) {
                        Entity e = Bukkit.getEntity(id);
                        if (e != null) {
                            e.remove();
                        }
                    }
                }
                ItemDisplay body = spawn(at, s, "borne_" + s.id, 12);
                ItemDisplay lit = spawn(at, s, "borne_" + s.id + "_ecran", 15);
                displays.put(s.id, new UUID[]{body.getUniqueId(), lit.getUniqueId()});
            }
            for (Entity e : world.getNearbyEntities(at.clone().add(0, 1, 0), 0.8, 1.5, 0.8, en -> en instanceof Villager)) {
                Villager v = (Villager) e;
                if (!v.isInvisible()) {
                    v.setInvisible(true);
                }
                if (v.isCustomNameVisible()) {
                    v.setCustomNameVisible(false); // le texte au-dessus de la borne suffit
                }
                AttributeInstance a = v.getAttribute(Attribute.SCALE);
                if (a != null && Math.abs(a.getBaseValue() - npcScale) > 0.001) {
                    a.setBaseValue(npcScale); // boîte de clic à la taille de la borne
                }
            }
        }
    }

    private static boolean valid(UUID id) {
        Entity e = Bukkit.getEntity(id);
        return e != null && e.isValid();
    }

    private ItemDisplay spawn(Location at, Spot s, String model, int light) {
        Location l = at.clone();
        l.setYaw(s.yaw + yawOffset);
        l.setPitch(0f);
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("orbitcasino", model.toLowerCase(Locale.ROOT)));
        item.setItemMeta(meta);
        return world.spawn(l, ItemDisplay.class, e -> {
            Holo.tag(e);
            e.setItemStack(item);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBrightness(new Display.Brightness(light, 15));
            e.setViewRange(1.5f);
            e.setRotation(l.getYaw(), 0f);
            // modèle : bas à y = 0 (le centre du modèle est à 8/16) → remonter d'une demi-échelle
            e.setTransformation(new Transformation(new Vector3f(0, scale * 0.5f, 0), new AxisAngle4f(),
                    new Vector3f(scale, scale, scale), new AxisAngle4f()));
        });
    }
}
