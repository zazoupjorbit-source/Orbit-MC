package fr.orbit.extraction.vehicle;

import fr.orbit.extraction.Keys;
import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.raid.AutoSpawns;
import fr.orbit.extraction.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Véhicules du raid : moto, voiture, Humvee, hélicoptère. Tout est calculé par le serveur (aucun mod côté client) :
 * le conducteur est assis sur une entité d'affichage, ses touches de déplacement sont lues à chaque tick, et le
 * véhicule (un modèle 3D du pack) est déplacé avec ses passagers.
 *
 * Chaque véhicule a un réservoir (jerricans pour refaire le plein) et des points de structure (balles, explosions,
 * chocs ; kit de réparation). Comme les caisses, un véhicule est d'abord logique : ses entités n'existent que
 * lorsqu'un joueur est assez près.
 *
 * Repère d'un véhicule : « avant » = (-sin lacet, cos lacet), « droite » = (-cos lacet, -sin lacet). Dans le modèle,
 * l'avant est vers -Z et la droite vers +X (une entité d'affichage d'objet retourne son modèle de 180°).
 */
public final class Vehicles implements Listener {

    /** Place assise, en blocs par rapport au centre du véhicule au sol. La première place est celle du conducteur. */
    public record Seat(double right, double up, double forward) {
    }

    /** Vitesses en blocs par tick, braquage en degrés par tick, consommation en litres par tick à plein régime. */
    public record Def(String id, String name, boolean flying, float scale, double shift, List<Seat> seats,
                      double maxSpeed, double reverse, double accel, double brake, double turn, double climb,
                      double hp, double armor, double tank, double burn, double noise,
                      double width, double length, double height, double step, double ram, int count, double rotorUp,
                      String sound, int soundEvery, float soundPitch, Material icon) {
    }

    /** Ce que la carte tactique a besoin de savoir d'un véhicule. */
    public record View(double x, double z, float yaw, boolean flying) {
    }

    /** Balle arrêtée par une carrosserie ({@code stopped}) ou qui atteint un occupant avec {@code factor} de ses dégâts. */
    public record Impact(Vector point, boolean stopped, double factor) {
    }

    private static final class Vehicle {
        final String id = UUID.randomUUID().toString();
        final Def def;
        double x, y, z;
        float yaw, pitch;
        double speed;           // le long de l'avant, blocs par tick (négatif en marche arrière)
        double vy;
        double hf, hr;          // hauteur du sol sous l'essieu avant / arrière
        double fuel, hp;
        boolean grounded = true;
        boolean asleep = true;  // à l'arrêt et vide : plus simulé
        boolean natural;        // garé par le serveur (compte dans le quota du type)
        boolean used;
        long lastUsed;
        float rotor, rotorYaw;
        UUID lastDamager;
        ItemDisplay body, rotorEnt;
        ItemDisplay[] seats;
        Interaction hit;
        BossBar bar;
        final Map<UUID, Integer> rammed = new HashMap<>();

        Vehicle(Def def) {
            this.def = def;
        }

        boolean shown() {
            return body != null;
        }

        Player rider(int seat) {
            if (seats == null || seat >= seats.length || seats[seat] == null) {
                return null;
            }
            for (Entity e : seats[seat].getPassengers()) {
                if (e instanceof Player p) {
                    return p;
                }
            }
            return null;
        }

        List<Player> riders() {
            List<Player> out = new ArrayList<>();
            if (seats != null) {
                for (int i = 0; i < seats.length; i++) {
                    Player p = rider(i);
                    if (p != null) {
                        out.add(p);
                    }
                }
            }
            return out;
        }

        int seatOf(Player p) {
            if (seats != null) {
                for (int i = 0; i < seats.length; i++) {
                    if (seats[i] != null && seats[i].getPassengers().contains(p)) {
                        return i;
                    }
                }
            }
            return -1;
        }
    }

    public static final String JERRICAN = "jerrican";
    public static final String REPAIR = "reparation";

    /** Vitesse (blocs par tick) à partir de laquelle un choc abîme le véhicule. */
    private static final double CRASH = 0.3;
    private static final double GRAVITY = 0.08;

    private final OrbitExtraction plugin;
    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final Map<String, Vehicle> vehicles = new LinkedHashMap<>();
    private final Map<UUID, Vehicle> occupants = new HashMap<>();
    private final Set<UUID> silent = new HashSet<>();
    private final Random random = new Random();
    private boolean enabled;
    private double showRange;
    private long abandonMs;
    private long respawnMs;
    private double minDistance;
    private double maxAltitude;
    private double margin;
    private double jerricanLitres;
    private double repairHp;
    private double bulletFactor;
    private double blastFactor;
    private long nextSpawn;
    private int pending;
    private int ticks;

    public Vehicles(OrbitExtraction plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ configuration

    public void load(ConfigurationSection c) {
        clear();
        defs.clear();
        enabled = c != null && c.getBoolean("actifs", true);
        showRange = c == null ? 110 : Math.max(48, c.getDouble("distance-affichage", 110));
        abandonMs = (long) ((c == null ? 10 : Math.max(1, c.getDouble("abandon-minutes", 10))) * 60_000);
        respawnMs = (long) ((c == null ? 60 : Math.max(0, c.getDouble("reapparition-secondes", 60))) * 1000);
        minDistance = c == null ? 70 : c.getDouble("distance-min", 70);
        maxAltitude = c == null ? 220 : c.getDouble("altitude-max", 220);
        margin = c == null ? 150 : c.getDouble("marge-zone", 150);
        jerricanLitres = c == null ? 15 : Math.max(1, c.getDouble("jerrican-litres", 15));
        repairHp = c == null ? 60 : Math.max(1, c.getDouble("reparation-pv", 60));
        bulletFactor = c == null ? 1 : Math.max(0, c.getDouble("degats-balles", 1));
        blastFactor = c == null ? 2.5 : Math.max(0, c.getDouble("degats-explosions", 2.5));
        ConfigurationSection ts = c == null ? null : c.getConfigurationSection("types");
        if (ts != null) {
            for (String key : ts.getKeys(false)) {
                ConfigurationSection s = ts.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                List<Seat> seats = new ArrayList<>();
                List<?> raw = s.getList("places");
                if (raw != null) {
                    for (Object o : raw) {
                        if (o instanceof List<?> l && l.size() >= 3 && l.get(0) instanceof Number a && l.get(1) instanceof Number b
                                && l.get(2) instanceof Number f) {
                            seats.add(new Seat(a.doubleValue(), b.doubleValue(), f.doubleValue()));
                        }
                    }
                }
                if (seats.isEmpty()) {
                    seats.add(new Seat(0, 0.6, 0));
                }
                String id = key.toLowerCase(Locale.ROOT);
                Material icon = Material.matchMaterial(s.getString("icone", "MINECART"));
                defs.put(id, new Def(id, s.getString("nom", key), s.getBoolean("volant", false), (float) s.getDouble("echelle", 1.75),
                        s.getDouble("decalage", 0), List.copyOf(seats),
                        Math.max(0.5, s.getDouble("vitesse", 14)) / 20, Math.max(0, s.getDouble("marche-arriere", 4)) / 20,
                        Math.max(0.5, s.getDouble("acceleration", 6)) / 400, Math.max(0.5, s.getDouble("freinage", 12)) / 400,
                        Math.max(1, s.getDouble("braquage", 90)) / 20, Math.max(0.5, s.getDouble("montee", 6)) / 20,
                        Math.max(1, s.getDouble("pv", 100)), Math.max(0, Math.min(0.95, s.getDouble("blindage", 0))),
                        Math.max(1, s.getDouble("reservoir", 40)), Math.max(0, s.getDouble("consommation", 5)) / 1200,
                        s.getDouble("bruit", 30), Math.max(0.5, s.getDouble("largeur", 1.9)), Math.max(0.5, s.getDouble("longueur", 4)),
                        Math.max(0.5, s.getDouble("hauteur", 1.9)), Math.max(0.5, s.getDouble("franchissement", 1.1)),
                        Math.max(0, s.getDouble("belier", 12)), Math.max(0, s.getInt("nombre", 0)), s.getDouble("rotor-hauteur", 3),
                        s.getString("son", "entity.minecart.inside"), Math.max(1, s.getInt("son-intervalle", 10)),
                        (float) s.getDouble("son-ton", 0.8), icon == null ? Material.MINECART : icon));
            }
        }
        nextSpawn = 0;
        int total = 0;
        for (Def d : defs.values()) {
            total += d.count();
        }
        plugin.getLogger().info("Véhicules : " + (enabled && !defs.isEmpty() ? defs.size() + " types, " + total + " garés sur l'île." : "désactivés."));
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 1L);
    }

    public Def def(String id) {
        return id == null ? null : defs.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Def> defs() {
        return defs.values();
    }

    public int count() {
        return vehicles.size();
    }

    public Collection<View> views() {
        List<View> out = new ArrayList<>(vehicles.size());
        for (Vehicle v : vehicles.values()) {
            out.add(new View(v.x, v.z, v.yaw, v.def.flying()));
        }
        return out;
    }

    /** Résumé pour /extraction vehicule. */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        for (Def d : defs.values()) {
            int n = 0;
            for (Vehicle v : vehicles.values()) {
                if (v.def == d) {
                    n++;
                }
            }
            sb.append(sb.isEmpty() ? "" : "<#5A6270>, </#5A6270>").append("<#D7DCE2>").append(Text.esc(d.name())).append(" ×").append(n).append("</#D7DCE2>");
        }
        return sb.toString();
    }

    /** Lignes de description d'un type (écran des colis du téléphone). */
    public List<String> describe(Def d) {
        List<String> out = new ArrayList<>();
        out.add("<#8A919B>" + d.seats().size() + " place" + (d.seats().size() > 1 ? "s" : "") + " • <#D7DCE2>" + Math.round(d.maxSpeed() * 72)
                + " km/h</#D7DCE2>" + (d.flying() ? " • vole" : ""));
        out.add("<#8A919B>Structure <#D7DCE2>" + Math.round(d.hp()) + "</#D7DCE2> • blindage <#D7DCE2>" + Math.round(d.armor() * 100) + " %");
        out.add("<#8A919B>Réservoir <#D7DCE2>" + Math.round(d.tank()) + " L</#D7DCE2>, livré plein");
        return out;
    }

    /** Retire tous les véhicules : ceux du serveur sont regarés au prochain passage. */
    public void clear() {
        for (Vehicle v : new ArrayList<>(vehicles.values())) {
            remove(v, false);
        }
        vehicles.clear();
        occupants.clear();
        nextSpawn = 0;
    }

    public void shutdown() {
        clear();
    }

    // ------------------------------------------------------------------ objets (jerrican, kit de réparation)

    public List<String> toolIds() {
        return List.of(JERRICAN, REPAIR);
    }

    /** Null si l'identifiant n'est pas un outil de véhicule. */
    public ItemStack tool(String id, int amount) {
        boolean jerrican = JERRICAN.equalsIgnoreCase(id);
        if (!jerrican && !REPAIR.equalsIgnoreCase(id) && !"repairkit".equalsIgnoreCase(id)) {
            return null;
        }
        ItemStack it = new ItemStack(Material.PAPER, Math.max(1, Math.min(amount, toolStack())));
        it.editMeta(m -> {
            m.displayName(Text.item(jerrican ? "<#E8695C><b>Jerrican</b>" : "<#E8695C><b>Kit de réparation</b>"));
            m.lore(Text.lore(jerrican
                    ? List.of("<#8A919B>Remet <#F5C542>" + fmt(jerricanLitres) + " L</#F5C542> dans un réservoir.",
                    "<#5A6270>Clic droit sur un véhicule (ou depuis ton siège)")
                    : List.of("<#8A919B>Répare <#2ECC71>" + fmt(repairHp) + " points</#2ECC71> de structure.",
                    "<#5A6270>Clic droit sur un véhicule (ou depuis ton siège)")));
            m.getPersistentDataContainer().set(Keys.VEHICLE_TOOL, PersistentDataType.STRING, jerrican ? JERRICAN : REPAIR);
            m.setMaxStackSize(toolStack());
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        it.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData().addString(jerrican ? "orbitguns:jerrican" : "orbitguns:repairkit").build());
        return it;
    }

    public int toolStack() {
        return 4;
    }

    public String toolOf(ItemStack it) {
        if (it == null || it.getType() != Material.PAPER || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(Keys.VEHICLE_TOOL, PersistentDataType.STRING);
    }

    private static String fmt(double v) {
        return (v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v)).replace('.', ',');
    }

    private void useTool(Player p, Vehicle v, ItemStack hand, String tool) {
        Def d = v.def;
        if (JERRICAN.equals(tool)) {
            if (v.fuel >= d.tank() - 0.5) {
                Text.bar(p, "<#8A919B>Le réservoir est déjà plein.");
                return;
            }
            v.fuel = Math.min(d.tank(), v.fuel + jerricanLitres);
            p.getWorld().playSound(p.getLocation(), "item.bucket.empty", SoundCategory.PLAYERS, 0.9f, 0.8f);
            Text.bar(p, "<#F5C542>Plein : " + Math.round(v.fuel * 100 / d.tank()) + " %</#F5C542> <#8A919B>(" + Text.esc(d.name()) + ")");
        } else {
            if (v.hp >= d.hp() - 0.5) {
                Text.bar(p, "<#8A919B>Ce véhicule n'a rien à réparer.");
                return;
            }
            v.hp = Math.min(d.hp(), v.hp + repairHp);
            p.getWorld().playSound(p.getLocation(), "block.anvil.use", SoundCategory.PLAYERS, 0.6f, 1.3f);
            Text.bar(p, "<#2ECC71>Réparé : " + Math.round(v.hp * 100 / d.hp()) + " %</#2ECC71> <#8A919B>(" + Text.esc(d.name()) + ")");
        }
        hand.setAmount(hand.getAmount() - 1);
        v.lastUsed = System.currentTimeMillis();
        if (v.shown()) {
            hud(v);
        }
    }

    // ------------------------------------------------------------------ création, apparition

    private Vehicle create(Def d, double x, double y, double z, float yaw, double fuelRatio, double hpRatio, boolean natural) {
        Vehicle v = new Vehicle(d);
        v.x = x;
        v.y = y;
        v.z = z;
        v.hf = y;
        v.hr = y;
        v.yaw = wrap(yaw);
        v.fuel = d.tank() * Math.max(0, Math.min(1, fuelRatio));
        v.hp = d.hp() * Math.max(0.05, Math.min(1, hpRatio));
        v.natural = natural;
        v.lastUsed = System.currentTimeMillis();
        vehicles.put(v.id, v);
        return v;
    }

    /** Admin : fait apparaître un véhicule neuf, plein, devant le joueur. */
    public boolean spawnAt(Player p, Def d) {
        World w = world();
        if (w == null || p.getWorld() != w) {
            return false;
        }
        Location l = p.getLocation();
        Vector f = l.getDirection().setY(0);
        if (f.lengthSquared() < 1e-6) {
            f = new Vector(0, 0, 1);
        }
        f.normalize().multiply(d.length() / 2 + 2);
        double x = l.getX() + f.getX(), z = l.getZ() + f.getZ();
        double g = ground(w, x, z, l.getY() + 0.5, 2.5, 0.5, true);
        Vehicle v = create(d, x, Double.isNaN(g) ? l.getY() : g, z, l.getYaw(), 1, 1, false);
        show(w, v);
        return true;
    }

    /** Colis du téléphone : pose un véhicule neuf, plein, près du joueur. Faux s'il n'y a pas la place. */
    public boolean deliver(Player p, Def d) {
        World w = world();
        if (w == null || p.getWorld() != w) {
            return false;
        }
        Location around = p.getLocation();
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2, dist = 4 + random.nextDouble() * (attempt < 20 ? 6 : 14);
            double x = Math.floor(around.getX() + Math.cos(angle) * dist) + 0.5, z = Math.floor(around.getZ() + Math.sin(angle) * dist) + 0.5;
            double g = ground(w, x, z, around.getY() + 0.5, 1.5, d.height(), false);
            if (Double.isNaN(g) || !clear(w, x, g, z, d)) {
                continue;
            }
            Vehicle v = create(d, x, g, z, random.nextFloat() * 360f, 1, 1, false);
            v.used = true;      // un véhicule livré puis laissé là finit par disparaître
            show(w, v);
            Location at = new Location(w, x, g + 1, z);
            w.spawnParticle(Particle.CLOUD, at, 40, d.width() / 2, 0.6, d.width() / 2, 0.04);
            w.playSound(at, "block.anvil.land", SoundCategory.PLAYERS, 1.4f, 0.6f);
            plugin.infected().noise(at, d.noise(), null);
            Text.msg(p, "<#3FC46A>" + Text.esc(d.name()) + " livré</#3FC46A> à <#D7DCE2>" + (int) Math.round(at.distance(around))
                    + " m</#D7DCE2>. <#8A919B>Réservoir plein — clic droit pour monter.");
            return true;
        }
        return false;
    }

    /** Sol à peu près plat et dégagé sur toute l'emprise du véhicule (ciel ouvert pour un hélicoptère). */
    private boolean clear(World w, double x, double y, double z, Def d) {
        double r = Math.max(d.width(), d.length()) / 2 + (d.flying() ? 1.5 : 0.3);
        for (double dx = -r; dx <= r + 1e-6; dx += 1) {
            for (double dz = -r; dz <= r + 1e-6; dz += 1) {
                double g = ground(w, x + dx, z + dz, y, 0.6, d.height(), false);
                if (Double.isNaN(g) || Math.abs(g - y) > 0.6) {
                    return false;
                }
                if (d.flying() && w.getHighestBlockYAt((int) Math.floor(x + dx), (int) Math.floor(z + dz)) > y + 0.5) {
                    return false;
                }
            }
        }
        return true;
    }

    private void spawnNatural(World w, AutoSpawns auto, Def d) {
        int[] pt = null;
        for (int attempt = 0; attempt < 16 && pt == null; attempt++) {
            int[] cand = auto.randomPoint();
            if (cand == null) {
                return;
            }
            boolean ok = true;
            for (Vehicle o : vehicles.values()) {
                double dx = o.x - cand[0], dz = o.z - cand[2];
                if (dx * dx + dz * dz < minDistance * minDistance) {
                    ok = false;
                    break;
                }
            }
            for (Player p : w.getPlayers()) {
                double dx = p.getX() - cand[0], dz = p.getZ() - cand[2];
                if (dx * dx + dz * dz < 48 * 48) {
                    ok = false;     // rien n'apparaît sous les yeux d'un joueur
                    break;
                }
            }
            if (ok) {
                pt = cand;
            }
        }
        if (pt == null) {
            return;
        }
        int[] at = pt;
        pending++;
        w.getChunkAtAsync(at[0] >> 4, at[2] >> 4).whenComplete((chunk, err) -> {
            pending--;
            if (err != null || !enabled || !defs.containsValue(d)) {
                return;
            }
            Location l = AutoSpawns.check(w, at[0], at[1], at[2]);
            if (l == null || !clear(w, l.getX(), l.getY(), l.getZ(), d)) {
                return;     // pas la place ici : un autre point sera tiré au prochain passage
            }
            create(d, l.getX(), l.getY(), l.getZ(), random.nextInt(8) * 45f, 0.10 + random.nextDouble() * 0.25,
                    0.65 + random.nextDouble() * 0.35, true);
        });
    }

    // ------------------------------------------------------------------ entités

    private World world() {
        return Bukkit.getWorld(plugin.raid().worldName());
    }

    private static ItemStack model(String name) {
        ItemStack it = new ItemStack(Material.PAPER);
        it.editMeta(m -> m.setItemModel(new NamespacedKey("orbitguns", name)));
        return it;
    }

    private void show(World w, Vehicle v) {
        hide(v);
        Def d = v.def;
        float s = d.scale();
        v.body = w.spawn(new Location(w, v.x, v.y, v.z, v.yaw, v.pitch), ItemDisplay.class, e -> {
            e.setItemStack(model("veh_" + d.id()));
            e.setPersistent(false);
            e.setTransformation(new Transformation(new Vector3f(0f, s * 0.5f, (float) d.shift()), new AxisAngle4f(),
                    new Vector3f(s, s, s), new AxisAngle4f()));
            e.setTeleportDuration(3);
            e.setViewRange(2.5f);
        });
        v.seats = new ItemDisplay[d.seats().size()];
        for (int i = 0; i < v.seats.length; i++) {
            Seat seat = d.seats().get(i);
            v.seats[i] = w.spawn(at(w, v, seat.right(), seat.up(), seat.forward(), v.yaw, 0), ItemDisplay.class, e -> {
                e.setPersistent(false);
                e.setTeleportDuration(3);
            });
        }
        if (d.flying()) {
            v.rotorEnt = w.spawn(at(w, v, 0, d.rotorUp(), 0, v.rotorYaw, 0), ItemDisplay.class, e -> {
                e.setItemStack(model("veh_" + d.id() + "_rotor"));
                e.setPersistent(false);
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f()));
                e.setTeleportDuration(2);
                e.setViewRange(2.5f);
            });
        }
        v.hit = w.spawn(new Location(w, v.x, v.y, v.z), Interaction.class, i -> {
            i.setInteractionWidth((float) ((d.width() + d.length()) / 2));
            i.setInteractionHeight((float) Math.min(d.height(), 2.2));
            i.setResponsive(true);
            i.setPersistent(false);
            i.getPersistentDataContainer().set(Keys.VEHICLE, PersistentDataType.STRING, v.id);
        });
        v.bar = BossBar.bossBar(Text.mm(Text.esc(d.name())), 1f, BossBar.Color.WHITE, BossBar.Overlay.NOTCHED_10);
    }

    private void hide(Vehicle v) {
        if (v.bar != null) {
            for (Player p : v.riders()) {
                p.hideBossBar(v.bar);
            }
        }
        if (v.seats != null) {
            for (ItemDisplay seat : v.seats) {
                if (seat != null) {
                    seat.remove();
                }
            }
        }
        for (Entity e : new Entity[]{v.body, v.rotorEnt, v.hit}) {
            if (e != null) {
                e.remove();
            }
        }
        v.body = null;
        v.rotorEnt = null;
        v.hit = null;
        v.seats = null;
        v.bar = null;
    }

    /** Fait descendre tout le monde (sans les replacer) puis retire le véhicule. */
    private void remove(Vehicle v, boolean puff) {
        for (Player p : v.riders()) {
            eject(p);
        }
        occupants.values().removeIf(o -> o == v);
        World w = world();
        if (puff && v.shown() && w != null) {
            w.spawnParticle(Particle.CLOUD, new Location(w, v.x, v.y + 0.8, v.z), 20, 0.8, 0.4, 0.8, 0.02);
        }
        hide(v);
        vehicles.remove(v.id);
    }

    /** Point du repère du véhicule (droite, haut, avant, en blocs) dans le monde, assiette comprise. */
    private static Location at(World w, Vehicle v, double right, double up, double forward, float yaw, float pitch) {
        double pr = Math.toRadians(v.pitch), yr = Math.toRadians(v.yaw);
        double u = up * Math.cos(pr) - forward * Math.sin(pr);
        double f = up * Math.sin(pr) + forward * Math.cos(pr);
        double fx = -Math.sin(yr), fz = Math.cos(yr), rx = -Math.cos(yr), rz = -Math.sin(yr);
        return new Location(w, v.x + rx * right + fx * f, v.y + u, v.z + rz * right + fz * f, yaw, pitch);
    }

    private static void move(Entity e, Location l) {
        if (e != null && e.isValid()) {
            e.teleport(l, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }
    }

    private void place(World w, Vehicle v) {
        Def d = v.def;
        move(v.body, new Location(w, v.x, v.y, v.z, v.yaw, v.pitch));
        for (int i = 0; i < v.seats.length; i++) {
            Seat seat = d.seats().get(i);
            move(v.seats[i], at(w, v, seat.right(), seat.up(), seat.forward(), v.yaw, 0));
        }
        if (v.rotorEnt != null) {
            move(v.rotorEnt, at(w, v, 0, d.rotorUp(), 0, v.rotorYaw, 0));
        }
        move(v.hit, new Location(w, v.x, v.y, v.z));
    }

    // ------------------------------------------------------------------ boucle

    private void tick() {
        ticks++;
        World w = world();
        if (w == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Vehicle v : new ArrayList<>(vehicles.values())) {
            if (!v.shown()) {
                continue;
            }
            if (!v.body.isValid()) {
                hide(v);        // chunk déchargé : les entités ont disparu avec lui
                continue;
            }
            Player driver = v.rider(0);
            boolean occupied = driver != null || !v.riders().isEmpty();
            if (!occupied && v.asleep) {
                continue;
            }
            boolean engine = driver != null && v.fuel > 0;
            Input in = driver == null ? null : driver.getCurrentInput();
            double load = v.def.flying() ? fly(w, v, in, engine) : drive(w, v, driver, in, engine);
            if (!vehicles.containsKey(v.id)) {
                continue;       // détruit pendant la simulation
            }
            if (engine) {
                v.fuel = Math.max(0, v.fuel - v.def.burn() * (0.12 + 0.88 * load));
                v.lastUsed = now;
                if (v.fuel <= 0) {
                    for (Player p : v.riders()) {
                        Text.title(p, "", "<#C0392B>Panne sèche</#C0392B> <#8A919B>— il faut un jerrican", 3, 40, 10);
                    }
                    w.playSound(new Location(w, v.x, v.y + 1, v.z), "block.fire.extinguish", SoundCategory.PLAYERS, 1f, 0.7f);
                }
            }
            place(w, v);
            effects(w, v, driver, engine, load);
            if (occupied && ticks % 4 == 0) {
                hud(v);
            }
            v.asleep = !occupied && v.grounded && Math.abs(v.speed) < 1e-3 && Math.abs(v.vy) < 1e-3 && v.rotor <= 0f;
        }
        if (ticks % 10 == 0) {
            housekeeping(w, now);
        }
    }

    private void housekeeping(World w, long now) {
        List<Player> players = w.getPlayers();
        for (Vehicle v : new ArrayList<>(vehicles.values())) {
            boolean occupied = v.shown() && !v.riders().isEmpty();
            if (v.used && !occupied && now - v.lastUsed > abandonMs) {
                remove(v, true);
                nextSpawn = Math.max(nextSpawn, now + respawnMs);
                continue;
            }
            boolean near = false;
            for (Player p : players) {
                double dx = p.getX() - v.x, dz = p.getZ() - v.z;
                if (dx * dx + dz * dz <= showRange * showRange) {
                    near = true;
                    break;
                }
            }
            boolean shown = v.shown() && v.body.isValid();
            if (near && !shown && w.isChunkLoaded((int) Math.floor(v.x) >> 4, (int) Math.floor(v.z) >> 4)) {
                show(w, v);
            } else if (!near && v.shown() && !occupied) {
                hide(v);
            }
        }
        AutoSpawns auto = plugin.autoSpawns();
        if (!enabled || ticks % 20 != 0 || now < nextSpawn || pending > 0 || auto.isEmpty()) {
            return;
        }
        for (Def d : defs.values()) {
            int n = 0;
            for (Vehicle v : vehicles.values()) {
                if (v.def == d && v.natural) {
                    n++;
                }
            }
            if (n < d.count()) {
                spawnNatural(w, auto, d);
                return;     // un à la fois
            }
        }
    }

    // ------------------------------------------------------------------ conduite au sol

    private double drive(World w, Vehicle v, Player driver, Input in, boolean engine) {
        Def d = v.def;
        int throttle = engine && in != null ? (in.isForward() ? 1 : in.isBackward() ? -1 : 0) : 0;
        int steer = in == null ? 0 : (in.isLeft() ? 1 : 0) - (in.isRight() ? 1 : 0);
        boolean handbrake = in != null && in.isJump();
        boolean wet = w.getBlockAt((int) Math.floor(v.x), (int) Math.floor(v.y + 0.1), (int) Math.floor(v.z)).isLiquid();
        double max = d.maxSpeed() * (wet ? 0.45 : 1);
        if (v.grounded) {
            if (handbrake) {
                v.speed = approach(v.speed, 0, d.brake() * 1.4);
            } else if (throttle > 0) {
                v.speed = v.speed < 0 ? approach(v.speed, 0, d.brake())
                        : v.speed > max ? approach(v.speed, max, d.brake() * 0.5) : Math.min(max, v.speed + d.accel());
            } else if (throttle < 0) {
                v.speed = v.speed > 0.01 ? approach(v.speed, 0, d.brake()) : Math.max(-d.reverse(), v.speed - d.accel() * 0.7);
            } else {
                v.speed = approach(v.speed, 0, d.accel() * 0.2 + Math.abs(v.speed) * 0.015);
            }
            if (steer != 0 && Math.abs(v.speed) > 0.005) {
                // Pas de demi-tour sur place : le braquage vient avec la vitesse, puis se durcit à fond.
                double ratio = Math.abs(v.speed) / d.maxSpeed();
                double grip = Math.min(1, ratio / 0.2) * (1 - 0.4 * Math.min(1, ratio));
                v.yaw = wrap(v.yaw - (float) (steer * d.turn() * grip * Math.signum(v.speed)));
            }
        }
        double yr = Math.toRadians(v.yaw);
        double fx = -Math.sin(yr), fz = Math.cos(yr), rx = -Math.cos(yr), rz = -Math.sin(yr);
        if (Math.abs(v.speed) > 1e-4) {
            double dir = Math.signum(v.speed);
            double nx = v.x + fx * v.speed, nz = v.z + fz * v.speed;
            double lead = d.length() / 2 * dir, hw = d.width() / 2 * 0.85;
            double ref = v.grounded ? (dir > 0 ? v.hf : v.hr) : v.y;
            boolean blocked = false, hard = true;
            if (leaving(v.x, v.z, nx, nz)) {
                blocked = true;
                hard = false;
                if (driver != null) {
                    Text.bar(driver, "<#C0392B>Limite de la zone de raid");
                }
            } else {
                for (double side : new double[]{0, -hw, hw}) {
                    double px = nx + fx * lead + rx * side, pz = nz + fz * lead + rz * side;
                    if (!w.isChunkLoaded((int) Math.floor(px) >> 4, (int) Math.floor(pz) >> 4)) {
                        blocked = true;
                        hard = false;
                        break;
                    }
                    if (Double.isNaN(ground(w, px, pz, ref, d.step(), d.height(), false))) {
                        blocked = true;
                        break;
                    }
                }
                if (!blocked && bumps(v, nx + fx * lead * 0.7, nz + fz * lead * 0.7)) {
                    blocked = true;
                }
            }
            if (blocked) {
                double hit = Math.abs(v.speed);
                v.speed = hard ? -v.speed * 0.25 : 0;
                if (hard && hit > CRASH) {
                    w.playSound(new Location(w, v.x, v.y + 1, v.z), "entity.zombie.attack_iron_door", SoundCategory.PLAYERS, 1.2f, 0.6f);
                    damage(v, (hit - CRASH) * 28, null);
                    if (!vehicles.containsKey(v.id)) {
                        return 0;
                    }
                }
            } else {
                v.x = nx;
                v.z = nz;
                if (Math.abs(v.speed) > 0.22) {
                    ram(w, v, driver, fx, fz);
                    if (!vehicles.containsKey(v.id)) {
                        return 0;
                    }
                }
            }
        }
        // Assiette : le véhicule repose sur ses deux essieux.
        double ax = d.length() * 0.35;
        double top = Math.max(Math.max(v.hf, v.hr), v.y);
        double hf = ground(w, v.x + fx * ax, v.z + fz * ax, top, d.step(), 0.4, true);
        double hr = ground(w, v.x - fx * ax, v.z - fz * ax, top, d.step(), 0.4, true);
        if (Double.isNaN(hf)) {
            hf = Double.isNaN(hr) ? v.y : hr;
        }
        if (Double.isNaN(hr)) {
            hr = hf;
        }
        v.hf = hf;
        v.hr = hr;
        double target = (hf + hr) / 2;
        double gap = v.y - target;
        if (gap <= 0.05) {
            v.y = Math.min(target, v.y + 0.5);
            v.vy = 0;
            v.grounded = true;
        } else if (gap <= 0.8 && v.vy == 0) {
            v.y -= Math.min(gap, 0.3);      // descente : les roues suivent la pente, on garde le contrôle
            v.grounded = true;
        } else {
            v.vy -= GRAVITY;
            v.y += v.vy;
            v.grounded = false;
            if (v.y <= target) {
                double fall = -v.vy;
                v.y = target;
                v.vy = 0;
                v.grounded = true;
                if (fall > 0.9) {
                    w.playSound(new Location(w, v.x, v.y + 1, v.z), "entity.zombie.attack_iron_door", SoundCategory.PLAYERS, 1.2f, 0.5f);
                    damage(v, (fall - 0.9) * 60, null);
                    if (!vehicles.containsKey(v.id)) {
                        return 0;
                    }
                }
            }
        }
        if (v.grounded) {
            float want = (float) -Math.toDegrees(Math.atan2(hf - hr, 2 * ax));
            v.pitch += (Math.max(-40f, Math.min(40f, want)) - v.pitch) * 0.35f;
        }
        return Math.abs(throttle);
    }

    /** Écrase ce qui se trouve devant : dégâts selon la vitesse, attribués au conducteur. */
    private void ram(World w, Vehicle v, Player driver, double fx, double fz) {
        Def d = v.def;
        double dir = Math.signum(v.speed), power = Math.abs(v.speed);
        Location front = new Location(w, v.x + fx * dir * d.length() * 0.35, v.y + 0.7, v.z + fz * dir * d.length() * 0.35);
        double r = d.width() / 2 + 0.7;
        if (v.rammed.size() > 64) {
            v.rammed.clear();
        }
        for (LivingEntity raw : w.getNearbyLivingEntities(front, r, 1.4, r)) {
            if (raw instanceof ArmorStand) {
                continue;
            }
            LivingEntity le = plugin.infected().hostOf(raw);
            if (le instanceof Player pl && (occupants.containsKey(pl.getUniqueId()) || pl.getGameMode() == GameMode.CREATIVE
                    || pl.getGameMode() == GameMode.SPECTATOR || !plugin.raid().inRaid(pl))) {
                continue;
            }
            Integer last = v.rammed.get(le.getUniqueId());
            if (last != null && ticks - last < 12) {
                continue;
            }
            v.rammed.put(le.getUniqueId(), ticks);
            plugin.gunService().hurt(driver, le, d.ram() * Math.min(1.2, power / d.maxSpeed()));
            le.setVelocity(new Vector(fx * dir, 0, fz * dir).multiply(Math.min(1.4, 0.5 + power)).setY(0.4));
            w.playSound(front, "entity.player.attack.knockback", SoundCategory.PLAYERS, 1f, 0.6f);
            v.speed *= 0.88;
            damage(v, 1.5, null);
            if (!vehicles.containsKey(v.id)) {
                return;
            }
        }
    }

    private boolean bumps(Vehicle v, double x, double z) {
        for (Vehicle o : vehicles.values()) {
            if (o == v || !o.shown() || Math.abs(o.y - v.y) > 3) {
                continue;
            }
            double r = Math.max(o.def.width(), o.def.length()) / 2 * 0.8;
            double dx = o.x - x, dz = o.z - z;
            double before = (o.x - v.x) * (o.x - v.x) + (o.z - v.z) * (o.z - v.z);
            if (dx * dx + dz * dz < r * r && dx * dx + dz * dz < before) {
                return true;
            }
        }
        return false;
    }

    /** Vrai si le déplacement fait sortir (ou s'éloigner encore) de la zone de jeu. */
    private boolean leaving(double x, double z, double nx, double nz) {
        int[] b = plugin.autoSpawns().bounds();
        if (b == null) {
            return false;
        }
        double out = outside(b, nx, nz);
        return out > 0 && out > outside(b, x, z);
    }

    private double outside(int[] b, double x, double z) {
        double dx = Math.max(Math.max(b[0] - margin - x, x - b[2] - margin), 0);
        double dz = Math.max(Math.max(b[1] - margin - z, z - b[3] - margin), 0);
        return dx + dz;
    }

    /**
     * Hauteur du sol sous (x, z), cherchée depuis {@code refY + step} vers le bas. NaN si le passage est bouché :
     * marche plus haute que {@code step}, pas {@code height} de libre au-dessus, eau profonde ou lave
     * (sauf {@code wade}), chunk absent.
     */
    private static double ground(World w, double x, double z, double refY, double step, double height, boolean wade) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) {
            return Double.NaN;
        }
        int min = Math.max(w.getMinHeight(), (int) Math.floor(refY) - 24);
        double surface = min;
        for (int yb = Math.min(w.getMaxHeight() - 1, (int) Math.floor(refY + step)); yb >= min; yb--) {
            Block b = w.getBlockAt(bx, yb, bz);
            if (b.isPassable()) {
                continue;
            }
            surface = b.getBoundingBox().getMaxY();
            break;
        }
        if (surface - refY > step + 1e-3) {
            return Double.NaN;
        }
        int from = (int) Math.floor(surface + 0.05), to = (int) Math.floor(surface + height - 0.05);
        for (int yb = from; yb <= to; yb++) {
            if (!w.getBlockAt(bx, yb, bz).isPassable()) {
                return Double.NaN;
            }
        }
        if (!wade) {
            Block feet = w.getBlockAt(bx, from, bz);
            if (feet.getType() == Material.LAVA || (feet.isLiquid() && w.getBlockAt(bx, from + 1, bz).isLiquid())) {
                return Double.NaN;
            }
        }
        return surface;
    }

    // ------------------------------------------------------------------ vol

    private double fly(World w, Vehicle v, Input in, boolean engine) {
        Def d = v.def;
        v.rotor = engine ? Math.min(1f, v.rotor + 0.02f) : Math.max(0f, v.rotor - 0.01f);
        v.rotorYaw = wrap(v.rotorYaw + 50f * v.rotor);
        boolean lift = engine && in != null && v.rotor >= 1f;
        double floor = floorUnder(w, v);
        boolean landed = v.y - floor < 0.06;
        double load = 0.3 * v.rotor;
        double targetVy;
        if (lift && in.isJump()) {
            targetVy = d.climb();
            load = 1;
        } else if (lift && in.isSneak() && !landed) {
            targetVy = -d.climb();
            load = 0.4;
        } else if (lift) {
            targetVy = 0;
            load = landed ? 0.3 : 0.7;
        } else {
            targetVy = landed ? 0 : -(0.75 - 0.4 * v.rotor);     // moteur coupé : chute freinée par le rotor qui tourne encore
        }
        v.vy = approach(v.vy, targetVy, lift ? d.climb() / 10 : 0.04);
        int throttle = lift && !landed ? (in.isForward() ? 1 : in.isBackward() ? -1 : 0) : 0;
        int steer = lift ? (in.isLeft() ? 1 : 0) - (in.isRight() ? 1 : 0) : 0;
        if (throttle > 0) {
            v.speed = Math.min(d.maxSpeed(), v.speed + d.accel());
            load = Math.max(load, 0.9);
        } else if (throttle < 0) {
            v.speed = Math.max(-d.reverse(), v.speed - d.accel());
            load = Math.max(load, 0.8);
        } else {
            v.speed = approach(v.speed, 0, landed ? 0.05 : d.accel() * 0.35);
        }
        if (steer != 0) {
            v.yaw = wrap(v.yaw - (float) (steer * d.turn()));
        }
        if (Math.abs(v.speed) > 1e-4) {
            double yr = Math.toRadians(v.yaw);
            double nx = v.x - Math.sin(yr) * v.speed, nz = v.z + Math.cos(yr) * v.speed;
            if (leaving(v.x, v.z, nx, nz)) {
                v.speed = 0;
                Player driver = v.rider(0);
                if (driver != null) {
                    Text.bar(driver, "<#C0392B>Limite de la zone de raid");
                }
            } else {
                int free = cabinClear(w, v, nx, nz, v.y + 0.35, v.y + d.height());
                if (free > 0) {
                    v.x = nx;
                    v.z = nz;
                } else {
                    double hit = Math.abs(v.speed);
                    v.speed = free == 0 ? -v.speed * 0.3 : 0;
                    if (free == 0 && hit > 0.25) {
                        w.playSound(new Location(w, v.x, v.y + 1, v.z), "entity.zombie.attack_iron_door", SoundCategory.PLAYERS, 1.5f, 0.5f);
                        damage(v, (hit - 0.2) * 90, null);
                        if (!vehicles.containsKey(v.id)) {
                            return 0;
                        }
                    }
                }
            }
        }
        double ny = v.y + v.vy;
        floor = floorUnder(w, v);
        if (ny <= floor) {
            double fall = -v.vy;
            ny = floor;
            v.vy = 0;
            if (fall > 0.35) {
                w.playSound(new Location(w, v.x, v.y + 1, v.z), "entity.zombie.attack_iron_door", SoundCategory.PLAYERS, 1.5f, 0.5f);
                damage(v, (fall - 0.35) * 150, null);
                if (!vehicles.containsKey(v.id)) {
                    return 0;
                }
            }
        } else if (v.vy > 0 && (ny > maxAltitude || cabinClear(w, v, v.x, v.z, v.y + d.height(), ny + d.height() + 0.2) <= 0)) {
            ny = v.y;
            v.vy = 0;
        }
        v.y = ny;
        v.grounded = v.y - floor < 0.06;
        v.pitch += ((float) (v.speed / d.maxSpeed() * 9) - v.pitch) * 0.2f;
        return load;
    }

    /** Appui le plus haut sous la cabine (centre et quatre coins). */
    private static double floorUnder(World w, Vehicle v) {
        Def d = v.def;
        double yr = Math.toRadians(v.yaw);
        double fx = -Math.sin(yr), fz = Math.cos(yr), rx = -Math.cos(yr), rz = -Math.sin(yr);
        double hl = d.length() / 2 * 0.8, hw = d.width() / 2 * 0.8;
        double best = floorBelow(w, v.x, v.z, v.y);
        for (int i = 0; i < 4; i++) {
            double a = (i & 1) == 0 ? hl : -hl, b = (i & 2) == 0 ? hw : -hw;
            best = Math.max(best, floorBelow(w, v.x + fx * a + rx * b, v.z + fz * a + rz * b, v.y));
        }
        return best;
    }

    private static double floorBelow(World w, double x, double z, double y) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) {
            return y;
        }
        int high = w.getHighestBlockYAt(bx, bz, HeightMap.MOTION_BLOCKING);
        if (y >= high + 0.99) {
            return high + 1;
        }
        int min = w.getMinHeight();
        for (int yb = (int) Math.floor(y + 0.3); yb >= min; yb--) {
            Block b = w.getBlockAt(bx, yb, bz);
            if (b.isLiquid()) {
                return yb + 1;
            }
            if (!b.isPassable()) {
                return b.getBoundingBox().getMaxY();
            }
        }
        return min;
    }

    /** 1 = libre, 0 = un bloc gêne, -1 = chunk absent (on s'arrête sans choc). */
    private static int cabinClear(World w, Vehicle v, double x, double z, double y0, double y1) {
        Def d = v.def;
        double yr = Math.toRadians(v.yaw);
        double fx = -Math.sin(yr), fz = Math.cos(yr), rx = -Math.cos(yr), rz = -Math.sin(yr);
        double hl = d.length() / 2, hw = d.width() / 2 * 0.9;
        double[][] pts = {{0, 0}, {hl, 0}, {-hl, 0}, {0, hw}, {0, -hw}, {hl, hw}, {hl, -hw}};
        for (double[] pt : pts) {
            int bx = (int) Math.floor(x + fx * pt[0] + rx * pt[1]), bz = (int) Math.floor(z + fz * pt[0] + rz * pt[1]);
            if (!w.isChunkLoaded(bx >> 4, bz >> 4)) {
                return -1;
            }
            for (int yb = (int) Math.floor(y0); yb <= (int) Math.floor(y1); yb++) {
                if (!w.getBlockAt(bx, yb, bz).isPassable()) {
                    return 0;
                }
            }
        }
        return 1;
    }

    // ------------------------------------------------------------------ bruit, fumée, tableau de bord

    private void effects(World w, Vehicle v, Player driver, boolean engine, double load) {
        Def d = v.def;
        Location at = new Location(w, v.x, v.y + d.height() * 0.5, v.z);
        double ratio = d.flying() ? v.rotor : Math.min(1, Math.abs(v.speed) / d.maxSpeed());
        boolean running = engine || (d.flying() && v.rotor > 0.2f);
        if (running && ticks % d.soundEvery() == 0) {
            w.playSound(at, d.sound(), SoundCategory.PLAYERS, (float) Math.max(0.6, d.noise() / 16 * (0.45 + 0.55 * ratio)),
                    (float) (d.soundPitch() * (0.75 + 0.5 * ratio)));
        }
        if (engine && ticks % 20 == 0 && (d.flying() ? v.rotor > 0.5f : load > 0 || Math.abs(v.speed) > 0.1)) {
            plugin.infected().noise(at, d.noise(), driver);
        }
        if (ticks % 3 == 0 && v.hp < d.hp() * 0.35) {
            w.spawnParticle(Particle.LARGE_SMOKE, at, 2, 0.3, 0.3, 0.3, 0.01);
            if (v.hp < d.hp() * 0.15) {
                w.spawnParticle(Particle.FLAME, at, 2, 0.3, 0.2, 0.3, 0.01);
            }
        }
    }

    private void hud(Vehicle v) {
        if (v.bar == null) {
            return;
        }
        Def d = v.def;
        double fuel = v.fuel / d.tank(), hp = Math.max(0, v.hp / d.hp());
        String fc = fuel <= 0.10 ? "#E23B3B" : fuel <= 0.25 ? "#F5C542" : "#D7DCE2";
        String hc = hp <= 0.15 ? "#E23B3B" : hp <= 0.35 ? "#F5C542" : "#D7DCE2";
        String sep = "  <#5A6270>|</#5A6270>  ";
        String extra = "";
        World w = world();
        if (d.flying() && w != null) {
            extra = sep + "<#8A919B>altitude <#D7DCE2>" + Math.max(0, Math.round(v.y - floorBelow(w, v.x, v.z, v.y))) + " m";
        }
        v.bar.name(Text.mm("<#E8695C><b>" + Text.esc(d.name()) + "</b></#E8695C>" + sep + "<#D7DCE2>" + Math.round(Math.abs(v.speed) * 72)
                + " km/h" + extra + sep + "<#8A919B>carburant <" + fc + ">" + Math.round(fuel * 100) + " %</" + fc + ">" + sep
                + "<#8A919B>état <" + hc + ">" + Math.round(hp * 100) + " %</" + hc + ">"));
        v.bar.progress((float) Math.max(0, Math.min(1, fuel)));
        v.bar.color(fuel <= 0.10 ? BossBar.Color.RED : fuel <= 0.25 ? BossBar.Color.YELLOW : BossBar.Color.WHITE);
    }

    // ------------------------------------------------------------------ dégâts

    private void damage(Vehicle v, double amount, Player source) {
        if (amount <= 0 || !vehicles.containsKey(v.id)) {
            return;
        }
        v.hp -= amount;
        if (source != null) {
            v.lastDamager = source.getUniqueId();
        }
        if (v.hp <= 0) {
            destroy(v);
        }
    }

    private void destroy(Vehicle v) {
        World w = world();
        Player source = v.lastDamager == null ? null : Bukkit.getPlayer(v.lastDamager);
        for (Player p : v.riders()) {
            Text.msg(p, "<#C0392B>" + Text.esc(v.def.name()) + " détruit.</#C0392B> <#8A919B>L'explosion s'entend de loin.");
        }
        boolean shown = v.shown();
        remove(v, false);
        if (w != null && shown) {
            Location at = new Location(w, v.x, v.y + v.def.height() * 0.5, v.z);
            w.spawnParticle(Particle.FLAME, at, 40, 1.0, 0.6, 1.0, 0.06);
            w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at, 12, 0.6, 0.4, 0.6, 0.03);
            plugin.gunService().explode(at, 4.5, 12, source, 50);
        }
    }

    /**
     * Une balle part de {@code eye} : si elle rencontre une carrosserie avant {@code reach}, le véhicule encaisse.
     * La balle s'y arrête, sauf si elle allait toucher un occupant de ce véhicule : il est blessé, protégé par le blindage.
     */
    public Impact bullet(Player shooter, Location eye, Vector dir, double reach, double damage, Entity hit) {
        if (vehicles.isEmpty()) {
            return null;
        }
        Vehicle own = shooter == null ? null : occupants.get(shooter.getUniqueId());
        Vehicle best = null;
        double bestT = reach;
        for (Vehicle v : vehicles.values()) {
            if (v == own || !v.shown()) {
                continue;
            }
            double dx = v.x - eye.getX(), dz = v.z - eye.getZ();
            double far = reach + v.def.length();
            if (dx * dx + dz * dz > far * far) {
                continue;
            }
            double t = rayBox(v, eye, dir);
            if (t >= 0 && t < bestT) {
                best = v;
                bestT = t;
            }
        }
        if (best == null) {
            return null;
        }
        Vector point = eye.toVector().add(dir.clone().multiply(bestT));
        World w = eye.getWorld();
        w.spawnParticle(Particle.CRIT, point.toLocation(w), 4, 0.05, 0.05, 0.05, 0.1);
        w.playSound(point.toLocation(w), "block.anvil.land", SoundCategory.PLAYERS, 0.25f, 1.9f);
        boolean inside = hit instanceof Player occ && occupants.get(occ.getUniqueId()) == best;
        double armor = best.def.armor();
        damage(best, damage * bulletFactor, shooter);
        return new Impact(point, !inside, inside ? 1 - armor : 0);
    }

    /** Distance d'entrée du rayon dans la boîte du véhicule (orientée selon son lacet), ou -1. */
    private static double rayBox(Vehicle v, Location eye, Vector dir) {
        Def d = v.def;
        double yr = Math.toRadians(v.yaw);
        double fx = -Math.sin(yr), fz = Math.cos(yr), rx = -Math.cos(yr), rz = -Math.sin(yr);
        double ox = eye.getX() - v.x, oy = eye.getY() - v.y, oz = eye.getZ() - v.z;
        double[] o = {ox * rx + oz * rz, oy, ox * fx + oz * fz};
        double[] k = {dir.getX() * rx + dir.getZ() * rz, dir.getY(), dir.getX() * fx + dir.getZ() * fz};
        double[] lo = {-d.width() / 2, 0.15, -d.length() / 2}, hi = {d.width() / 2, d.height(), d.length() / 2};
        double t0 = 0, t1 = Double.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(k[i]) < 1e-9) {
                if (o[i] < lo[i] || o[i] > hi[i]) {
                    return -1;
                }
                continue;
            }
            double a = (lo[i] - o[i]) / k[i], b = (hi[i] - o[i]) / k[i];
            t0 = Math.max(t0, Math.min(a, b));
            t1 = Math.min(t1, Math.max(a, b));
            if (t0 > t1) {
                return -1;
            }
        }
        return t0;
    }

    /** Explosion (grenade, lance-grenades, autre véhicule) : dégâts dégressifs aux véhicules proches. */
    public void blast(Location at, double radius, double damage, Player source) {
        for (Vehicle v : new ArrayList<>(vehicles.values())) {
            if (!v.shown() || !vehicles.containsKey(v.id)) {
                continue;
            }
            double dx = v.x - at.getX(), dy = v.y + v.def.height() / 2 - at.getY(), dz = v.z - at.getZ();
            double dist = Math.max(0, Math.sqrt(dx * dx + dy * dy + dz * dz) - Math.min(v.def.width(), v.def.length()) / 2);
            if (dist < radius) {
                damage(v, damage * (1 - 0.6 * dist / radius) * blastFactor, source);
            }
        }
    }

    // ------------------------------------------------------------------ monter, descendre

    public boolean riding(Player p) {
        return occupants.containsKey(p.getUniqueId());
    }

    private void mount(Player p, Vehicle v) {
        if (!v.shown() || p.isInsideVehicle() || occupants.containsKey(p.getUniqueId())) {
            return;
        }
        int seat = -1;
        for (int i = 0; i < v.seats.length; i++) {
            if (v.seats[i] != null && v.seats[i].isValid() && v.seats[i].getPassengers().isEmpty()) {
                seat = i;
                break;
            }
        }
        if (seat < 0) {
            Text.bar(p, "<#C0392B>Plus de place dans ce véhicule.");
            return;
        }
        if (!v.seats[seat].addPassenger(p)) {
            return;
        }
        occupants.put(p.getUniqueId(), v);
        v.used = true;
        v.asleep = false;
        v.lastUsed = System.currentTimeMillis();
        if (v.hit != null) {
            p.hideEntity(plugin, v.hit);    // sinon la boîte du véhicule intercepte les clics (tir, visée) de ses occupants
        }
        hud(v);
        p.showBossBar(v.bar);
        World w = p.getWorld();
        w.playSound(p.getLocation(), "block.iron_door.close", SoundCategory.PLAYERS, 0.7f, 1.2f);
        if (seat != 0) {
            Text.bar(p, "<#8A919B>Passager — tu peux tirer. <#D7DCE2>Maj</#D7DCE2> : descendre");
            return;
        }
        if (v.fuel <= 0) {
            Text.msg(p, "<#C0392B>Réservoir vide.</#C0392B> <#8A919B>Il faut un jerrican (caisses, boutique) : clic droit depuis le siège.");
        } else {
            w.playSound(p.getLocation(), "block.piston.extend", SoundCategory.PLAYERS, 0.8f, 0.6f);
        }
        Text.msg(p, v.def.flying()
                ? "<#E8695C>" + Text.esc(v.def.name()) + "</#E8695C> — <#D7DCE2>Espace</#D7DCE2> : monter • <#D7DCE2>Maj</#D7DCE2> : descendre (au sol : sortir) • "
                + "<#D7DCE2>Z/S</#D7DCE2> : avancer, reculer • <#D7DCE2>Q/D</#D7DCE2> : pivoter. <#8A919B>Le rotor met 2 s à se lancer."
                : "<#E8695C>" + Text.esc(v.def.name()) + "</#E8695C> — <#D7DCE2>Z/S</#D7DCE2> : accélérer, freiner • <#D7DCE2>Q/D</#D7DCE2> : tourner • "
                + "<#D7DCE2>Espace</#D7DCE2> : frein à main • <#D7DCE2>Maj</#D7DCE2> : descendre");
    }

    /** Fait descendre le joueur sans le replacer à côté du véhicule (téléportation vers la planque, déconnexion...). */
    public void eject(Player p) {
        UUID id = p.getUniqueId();
        Vehicle v = occupants.get(id);
        if (v == null) {
            return;
        }
        silent.add(id);
        try {
            p.leaveVehicle();
        } finally {
            silent.remove(id);
        }
        if (occupants.get(id) == v) {
            left(p, v, -1, true);
        }
    }

    private void left(Player p, Vehicle v, int seat, boolean quiet) {
        occupants.remove(p.getUniqueId());
        v.lastUsed = System.currentTimeMillis();
        if (v.bar != null) {
            p.hideBossBar(v.bar);
        }
        if (v.hit != null && v.hit.isValid() && p.isOnline()) {
            p.showEntity(plugin, v.hit);
        }
        if (!quiet && seat >= 0 && vehicles.containsKey(v.id)) {
            Bukkit.getScheduler().runTask(plugin, () -> placeBeside(p, v, seat));
        }
    }

    /** Pose le joueur debout à côté de sa portière (la descente vanilla le laisse au milieu du modèle). */
    private void placeBeside(Player p, Vehicle v, int seatIndex) {
        World w = world();
        if (w == null || !p.isOnline() || p.isDead() || p.isInsideVehicle() || p.getWorld() != w || !vehicles.containsKey(v.id)) {
            return;
        }
        if (v.def.flying() && !v.grounded) {
            return;     // saut en plein vol
        }
        Location here = p.getLocation();
        if (Math.abs(here.getX() - v.x) + Math.abs(here.getZ() - v.z) > v.def.length() + 4) {
            return;
        }
        Def d = v.def;
        Seat seat = d.seats().get(Math.min(seatIndex, d.seats().size() - 1));
        double side = seat.right() > 0.05 ? 1 : -1;
        double off = d.width() / 2 + 0.75;
        double[][] tries = {{side * off, seat.forward()}, {-side * off, seat.forward()}, {side * off, 0}, {-side * off, 0},
                {0, -d.length() / 2 - 0.8}, {0, d.length() / 2 + 0.8}};
        for (double[] t : tries) {
            Location l = at(w, v, t[0], 0, t[1], here.getYaw(), here.getPitch());
            double g = ground(w, l.getX(), l.getZ(), v.y + 0.3, 1.3, 1.8, true);
            if (!Double.isNaN(g) && g > v.y - 3) {
                l.setY(g);
                p.teleport(l);
                p.setFallDistance(0);
                return;
            }
        }
        Location roof = new Location(w, v.x, v.y + d.height() + 0.1, v.z, here.getYaw(), here.getPitch());
        p.teleport(roof);
        p.setFallDistance(0);
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Interaction i)) {
            return;
        }
        String id = i.getPersistentDataContainer().get(Keys.VEHICLE, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        Player p = e.getPlayer();
        Vehicle v = vehicles.get(id);
        if (v == null) {
            e.setCancelled(true);
            i.remove();     // boîte d'une session précédente
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (p.isSneaking() && plugin.items().gunOf(hand) != null) {
            return;         // accroupi, arme en main : on tire sur le véhicule au lieu d'y monter
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND || p.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        String tool = toolOf(hand);
        if (tool != null) {
            useTool(p, v, hand, tool);
            return;
        }
        mount(p, v);
    }

    /** Jerrican ou kit utilisé depuis son siège (la boîte du véhicule est masquée pour ses occupants). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        String tool = toolOf(e.getItem());
        if (tool == null) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        Vehicle v = occupants.get(p.getUniqueId());
        if (v == null) {
            Text.bar(p, "<#8A919B>Clic droit sur un véhicule pour t'en servir.");
            return;
        }
        useTool(p, v, e.getItem(), tool);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        Vehicle v = occupants.get(p.getUniqueId());
        if (v == null) {
            return;
        }
        boolean quiet = silent.contains(p.getUniqueId());
        int seat = v.seatOf(p);
        if (!quiet && !p.isDead() && seat == 0 && v.def.flying() && !v.grounded && v.fuel > 0 && v.rotor >= 1f
                && p.getCurrentInput().isSneak()) {
            e.setCancelled(true);       // en vol, Maj fait descendre l'appareil : le pilote ne saute pas
            return;
        }
        left(p, v, seat, quiet || p.isDead());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        eject(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        eject(e.getEntity());
    }

    /** Assis dans un véhicule qui frôle un mur, on ne s'étouffe pas dans le bloc. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSuffocate(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION && e.getEntity() instanceof Player p
                && occupants.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ outils

    /** /extraction vehicule : liste détaillée. */
    public void list(CommandSender to) {
        for (Vehicle v : vehicles.values()) {
            to.sendMessage(Text.mm("  <#D7DCE2>" + Text.esc(v.def.name()) + "</#D7DCE2> <#8A919B>" + (int) Math.floor(v.x) + ", "
                    + (int) Math.floor(v.y) + ", " + (int) Math.floor(v.z) + " • carburant " + Math.round(v.fuel * 100 / v.def.tank())
                    + " % • état " + Math.round(v.hp * 100 / v.def.hp()) + " %" + (v.natural ? "" : " • livré")
                    + (v.shown() && !v.riders().isEmpty() ? " • <#E67E22>occupé" : "")));
        }
    }

    private static double approach(double value, double target, double step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    private static float wrap(float yaw) {
        yaw %= 360f;
        return yaw > 180f ? yaw - 360f : yaw <= -180f ? yaw + 360f : yaw;
    }
}
