package fr.orbit.mmo.ambient;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.continent.ContinentData;
import fr.orbit.mmo.continent.ContinentService;
import fr.orbit.mmo.continent.ContinentService.Town;
import fr.orbit.mmo.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Breedable;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Donkey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * PNJ d'ambiance du continent : gardes, villageois, fermiers, enfants, voyageurs, chiens, chats et bétail. Ils n'ont
 * ni quête ni menu : ils sont là pour que les villes, les villages et les routes vivent.
 * <p>
 * Rien n'est enregistré dans le monde. Chaque lieu habité a une liste fixe d'emplacements (tirée de son nom, donc la
 * même à chaque démarrage) ; un PNJ apparaît à son emplacement quand un joueur approche et disparaît quand plus
 * personne n'est là. Les entités ne sont pas persistantes et portent une étiquette : tout ce qui resterait d'un arrêt
 * brutal est retiré au chargement. Aucun bloc n'est touché.
 * <p>
 * Les humains sont des mannequins (skins du pack) que ce service déplace lui-même ; les animaux sont ceux du jeu,
 * privés de leurs comportements et promenés autour de leur emplacement.
 */
public final class AmbientService implements Listener {

    private enum Role {
        GARDE_POSTE(true, 0, 0), GARDE_RONDE(true, 26, 0.085), VILLAGEOIS(true, 14, 0.09), FERMIER(true, 6, 0.075),
        ENFANT(true, 16, 0.17), VOYAGEUR(true, 0, 0.095), PATROUILLE(true, 0, 0.09),
        CHAT(false, 12, 0.9), CHIEN(false, 14, 1.0), VACHE(false, 7, 0.8), COCHON(false, 7, 0.8), POULE(false, 6, 0.8),
        ANE(false, 0, 1.1);

        final boolean human;
        /** Rayon de promenade autour de l'emplacement, en blocs. */
        final double range;
        /** Humains : blocs par tic. Animaux : multiplicateur de la vitesse de marche du jeu. */
        final double speed;

        Role(boolean human, double range, double speed) {
            this.human = human;
            this.range = range;
            this.speed = speed;
        }
    }

    /** Un emplacement d'un lieu : toujours le même PNJ au même endroit. */
    private static final class Slot {
        final Place place;
        final Role role;
        final double x, z;
        final float yaw;
        final boolean outside;
        final int n;
        /** Présent aussi la nuit. */
        final boolean night;
        Actor live;
        long retryAt;
        int fails;
        boolean dead;

        Slot(Place place, Role role, double x, double z, float yaw, boolean outside, int n, boolean night) {
            this.place = place;
            this.role = role;
            this.x = x;
            this.z = z;
            this.yaw = yaw;
            this.outside = outside;
            this.n = n;
            this.night = night;
        }
    }

    /** Un lieu habité et ses emplacements. */
    private static final class Place {
        final Town town;
        final String culture;
        final String guards;
        final List<Slot> slots = new ArrayList<>();
        double groundY = Double.NaN;
        boolean active;

        Place(Town town) {
            this.town = town;
            String f = town.faction() == null ? "" : town.faction();
            String s = town.style() == null ? "" : town.style();
            this.culture = f.equals("lumiere") || f.equals("ombre") ? f : (s.equals("lumiere") || s.equals("ombre") ? s : "neutre");
            this.guards = f.equals("lumiere") || f.equals("ombre") ? f : "neutre";
        }
    }

    /** Un PNJ présent dans le monde. */
    private static final class Actor {
        final Role role;
        final LivingEntity entity;
        final Slot slot;
        final Random random;
        double hx, hy, hz;
        // Marche (humains).
        double x, y, z;
        float yaw, targetYaw;
        List<Vector> path;
        int seg;
        int wait;
        Actor facing;
        // Route.
        ContinentData.Road road;
        int sample, dir, group;
        double offset;
        Actor leader;
        // Animaux.
        long nextMove;

        Actor(Role role, LivingEntity entity, Slot slot, long seed) {
            this.role = role;
            this.entity = entity;
            this.slot = slot;
            this.random = new Random(seed);
        }
    }

    private final OrbitMMO plugin;
    private final ContinentService continent;
    private final NamespacedKey key;
    private final File settingsFile;
    private final List<Place> places = new ArrayList<>();
    private final List<Actor> actors = new ArrayList<>();
    private final Map<UUID, Actor> byEntity = new HashMap<>();
    private final Map<UUID, Long> talkCooldown = new HashMap<>();
    private final Random random = new Random();
    private String signature = "";
    private long tick;
    private int searches;
    private int groups;
    // Réglages (ambiance.yml).
    private boolean enabled = true, roads = true, nightRest = true;
    private int cap = 260, roadGroups = 1;
    private double spawnDist = 64, despawnDist = 96;
    private final Map<String, Integer> capitalCounts = new HashMap<>(), villageCounts = new HashMap<>();
    // Observateur d'essai (commande « /continent ambiance essai ») : compte comme un joueur à cet endroit.
    private Location observer;
    private long observerUntil;
    private final List<long[]> observerTickets = new ArrayList<>();

    public AmbientService(OrbitMMO plugin, ContinentService continent) {
        this.plugin = plugin;
        this.continent = continent;
        this.key = new NamespacedKey(plugin, "ambiance");
        this.settingsFile = new File(plugin.getDataFolder(), "ambiance.yml");
    }

    public void start() {
        Looks.init(plugin);
        loadSettings();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::onTick, 60L, 1L);
        for (World w : Bukkit.getWorlds()) {
            purge(w.getEntities());
        }
    }

    public void shutdown() {
        for (Actor a : new ArrayList<>(actors)) {
            remove(a);
        }
        endObserver();
    }

    // ------------------------------------------------------------------ Réglages

    private void loadSettings() {
        if (!settingsFile.isFile()) {
            plugin.saveResource("ambiance.yml", false);
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(settingsFile);
        enabled = y.getBoolean("actif", true);
        roads = y.getBoolean("routes", true);
        nightRest = y.getBoolean("nuit", true);
        cap = Math.max(0, y.getInt("plafond", 260));
        roadGroups = Math.max(0, y.getInt("groupes-par-joueur", 1));
        spawnDist = Math.max(24, y.getDouble("distance-apparition", 64));
        despawnDist = Math.max(spawnDist + 12, y.getDouble("distance-retrait", 96));
        capitalCounts.clear();
        villageCounts.clear();
        String[] keys = {"gardes-par-porte", "gardes-parvis", "rondes", "villageois", "enfants", "chats", "chiens", "poules",
                "fermiers", "vaches", "cochons"};
        int[] capital = {2, 2, 6, 40, 10, 6, 5, 8, 3, 4, 3};
        int[] village = {2, 0, 1, 7, 3, 1, 1, 3, 2, 2, 1};
        for (int i = 0; i < keys.length; i++) {
            capitalCounts.put(keys[i], Math.max(0, y.getInt("capitale." + keys[i], capital[i])));
            villageCounts.put(keys[i], Math.max(0, y.getInt("village." + keys[i], village[i])));
        }
    }

    /** Relit ambiance.yml et repart de zéro (tous les PNJ présents sont retirés puis réapparaissent). */
    public String reload() {
        loadSettings();
        for (Actor a : new ArrayList<>(actors)) {
            remove(a);
        }
        signature = "";
        return "<#7FD4FF>Ambiance rechargée : <#D7DCE2>" + (enabled ? "active" : "désactivée") + ", plafond " + cap + " PNJ.";
    }

    // ------------------------------------------------------------------ Lieux et emplacements

    private void refreshPlaces() {
        List<Town> towns = continent.towns();
        StringBuilder sb = new StringBuilder();
        for (Town t : towns) {
            sb.append(t.id()).append(':').append(t.x()).append(',').append(t.z()).append(',').append(t.radius()).append(',')
                    .append(t.points().size()).append(';');
        }
        if (sb.toString().equals(signature)) {
            return;
        }
        signature = sb.toString();
        for (Actor a : new ArrayList<>(actors)) {
            if (a.slot != null) {
                remove(a);
            }
        }
        places.clear();
        for (Town t : towns) {
            Place p = new Place(t);
            roster(p);
            places.add(p);
        }
    }

    private static float yawOf(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private void roster(Place p) {
        Town t = p.town;
        Random r = new Random(t.id().hashCode() * 31L + 20261123L);
        boolean big = t.capital() || t.radius() >= 45;
        Map<String, Integer> c = t.capital() ? capitalCounts : villageCounts;
        double scale = t.capital() ? 1 : (big ? 1.5 : 1);
        int n = 0;
        // Gardes en poste : de part et d'autre de chaque porte (capitales), sinon près de la tour de guet ou de l'arrivée.
        List<Location> gates = new ArrayList<>();
        for (String name : new java.util.TreeSet<>(t.points().keySet())) {
            // Un village ne garde que deux de ses portes : il n'a pas la garnison d'une capitale.
            if (name.startsWith("porte") && (t.capital() || gates.size() < 2)) {
                gates.add(t.points().get(name));
            }
        }
        int perGate = c.getOrDefault("gardes-par-porte", 2);
        if (!gates.isEmpty()) {
            for (Location g : gates) {
                double ux = g.getX() - (t.x() + 0.5), uz = g.getZ() - (t.z() + 0.5), len = Math.max(1, Math.hypot(ux, uz));
                ux /= len;
                uz /= len;
                for (int i = 0; i < perGate; i++) {
                    double side = (i % 2 == 0 ? 1 : -1) * (3.5 + (i / 2) * 2.0);
                    p.slots.add(new Slot(p, Role.GARDE_POSTE, g.getX() - uz * side, g.getZ() + ux * side, yawOf(ux, uz), false, n++, true));
                }
            }
        } else {
            Location post = t.points().get("tour") != null ? t.points().get("tour") : t.points().get("spawn");
            if (post != null) {
                double a = Math.toRadians(post.getYaw());
                double fx = -Math.sin(a), fz = Math.cos(a);
                for (int i = 0; i < perGate; i++) {
                    double side = (i % 2 == 0 ? 1 : -1) * (2.5 + (i / 2) * 2.0);
                    p.slots.add(new Slot(p, Role.GARDE_POSTE, post.getX() - fz * side + fx * 1.5, post.getZ() + fx * side + fz * 1.5,
                            post.getYaw(), false, n++, true));
                }
            }
        }
        Location parvis = t.points().get("parvis");
        if (parvis != null) {
            for (int i = 0; i < c.getOrDefault("gardes-parvis", 0); i++) {
                double side = (i % 2 == 0 ? 1 : -1) * (5 + (i / 2) * 3.0);
                p.slots.add(new Slot(p, Role.GARDE_POSTE, parvis.getX() + side, parvis.getZ() - 3, parvis.getYaw(), false, n++, true));
            }
        }
        int R = t.radius();
        n = inside(p, r, Role.GARDE_RONDE, count(c, "rondes", scale), Math.min(20, R * 0.4), R - 12, n, 1);
        n = inside(p, r, Role.VILLAGEOIS, count(c, "villageois", scale), 6, R - 10, n, 3);
        n = inside(p, r, Role.ENFANT, count(c, "enfants", scale), 6, R * 0.65, n, 0);
        n = inside(p, r, Role.CHAT, count(c, "chats", scale), 5, R - 10, n, 1);
        n = inside(p, r, Role.CHIEN, count(c, "chiens", scale), 6, R - 10, n, 2);
        n = inside(p, r, Role.POULE, count(c, "poules", scale), 8, R - 8, n, 0);
        // Le pré : hors des murs, du côté d'une porte s'il y en a une.
        double angle;
        if (!gates.isEmpty()) {
            Location g = gates.get(r.nextInt(gates.size()));
            angle = Math.atan2(g.getZ() - t.z(), g.getX() - t.x()) + Math.toRadians(r.nextBoolean() ? 24 : -24);
        } else {
            angle = r.nextDouble() * Math.PI * 2;
        }
        double far = R + (t.capital() ? 34 : 16);
        double px = t.x() + Math.cos(angle) * far, pz = t.z() + Math.sin(angle) * far;
        n = around(p, r, Role.FERMIER, count(c, "fermiers", scale), px, pz, 7, n);
        n = around(p, r, Role.VACHE, count(c, "vaches", scale), px, pz, 9, n);
        around(p, r, Role.COCHON, count(c, "cochons", scale), px, pz, 9, n);
    }

    private static int count(Map<String, Integer> c, String key, double scale) {
        return (int) Math.round(c.getOrDefault(key, 0) * scale);
    }

    /** Emplacements à l'intérieur des murs, répartis sur la surface. {@code nightEvery} : 1 = tous présents la nuit, 0 = aucun. */
    private int inside(Place p, Random r, Role role, int count, double rMin, double rMax, int n, int nightEvery) {
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            // Réparti sur le rayon et non sur la surface : plus de monde au centre, près de la place, qu'au pied des murs.
            double d = rMin + r.nextDouble() * Math.max(1, rMax - rMin);
            boolean night = nightEvery > 0 && i % nightEvery == 0;
            p.slots.add(new Slot(p, role, p.town.x() + 0.5 + Math.cos(a) * d, p.town.z() + 0.5 + Math.sin(a) * d, r.nextFloat() * 360f,
                    false, n++, night));
        }
        return n;
    }

    private int around(Place p, Random r, Role role, int count, double cx, double cz, double spread, int n) {
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 1.5 + r.nextDouble() * spread;
            p.slots.add(new Slot(p, role, cx + Math.cos(a) * d, cz + Math.sin(a) * d, r.nextFloat() * 360f, true, n++, false));
        }
        return n;
    }

    // ------------------------------------------------------------------ Boucle

    private void onTick() {
        tick++;
        searches = 0;
        if (!enabled || !continent.ready()) {
            return;
        }
        for (int i = actors.size() - 1; i >= 0; i--) {
            Actor a = actors.get(i);
            if (a.role.human && a.entity.isValid()) {
                walk(a);
            }
        }
        if (tick % 20 == 0) {
            manage();
        }
    }

    private List<Location> observers(World w) {
        List<Location> out = new ArrayList<>();
        for (Player p : w.getPlayers()) {
            out.add(p.getLocation());
        }
        if (observer != null) {
            if (tick > observerUntil) {
                endObserver();
            } else if (observer.getWorld().equals(w)) {
                out.add(observer);
            }
        }
        return out;
    }

    private static double nearest2(List<Location> eyes, double x, double z) {
        double best = Double.MAX_VALUE;
        for (Location l : eyes) {
            double dx = l.getX() - x, dz = l.getZ() - z;
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best;
    }

    private static boolean isNight(World w) {
        long t = w.getTime();
        return t >= 13000 && t < 23000;
    }

    private void manage() {
        World w = continent.world();
        if (w == null) {
            return;
        }
        refreshPlaces();
        List<Location> eyes = observers(w);
        boolean night = nightRest && isNight(w);
        int spawned = 0;
        for (Place p : places) {
            Town t = p.town;
            double reach = t.radius() + 60 + spawnDist;
            if (eyes.isEmpty() || nearest2(eyes, t.x(), t.z()) > reach * reach) {
                p.active = false;
                continue;
            }
            p.active = true;
            if (Double.isNaN(p.groundY)) {
                Location anchor = t.points().get("spawn");
                if (anchor == null) {
                    continue;
                }
                p.groundY = anchor.getY();
            }
            for (Slot s : p.slots) {
                if (s.live != null || s.dead || tick < s.retryAt || spawned >= 6 || actors.size() >= cap || (night && !s.night)) {
                    continue;
                }
                double d2 = nearest2(eyes, s.x, s.z);
                if (d2 > spawnDist * spawnDist) {
                    continue;
                }
                Actor a;
                try {
                    a = spawn(w, s);
                } catch (RuntimeException | LinkageError ex) {
                    // Une apparence que le serveur refuse ne doit pas arrêter les autres : cet emplacement est abandonné.
                    s.dead = true;
                    plugin.getLogger().log(java.util.logging.Level.WARNING, "Ambiance : impossible de poser un PNJ « "
                            + s.role.name().toLowerCase(Locale.ROOT) + " » à " + s.place.town.name(), ex);
                    continue;
                }
                if (a == null) {
                    s.fails++;
                    s.retryAt = tick + 20L * 15;
                    // Chunk pas encore chargé : on réessaiera ; emplacement impossible (mur, eau, toit) : abandonné.
                    if (s.fails >= 4 && Nav.loaded(w, (int) Math.floor(s.x), (int) Math.floor(s.z))) {
                        s.dead = true;
                    }
                } else {
                    spawned++;
                }
            }
        }
        for (Actor a : new ArrayList<>(actors)) {
            if (!a.entity.isValid()) {
                forget(a);
                continue;
            }
            Location l = a.entity.getLocation();
            double d2 = eyes.isEmpty() ? Double.MAX_VALUE : nearest2(eyes, l.getX(), l.getZ());
            if (d2 > despawnDist * despawnDist || (a.leader != null && !a.leader.entity.isValid())) {
                remove(a);
                continue;
            }
            // La nuit, ceux qui dorment rentrent chez eux dès que personne ne les regarde de près.
            if (night && a.slot != null && !a.slot.night && d2 > 14 * 14) {
                remove(a);
                continue;
            }
            if (!a.role.human) {
                animal(w, a);
            }
        }
        if (roads && roadGroups > 0) {
            travellers(w, night);
        }
        if (tick % 1200 == 0) {
            long now = System.currentTimeMillis();
            talkCooldown.values().removeIf(v -> v < now);
        }
    }

    // ------------------------------------------------------------------ Apparition

    /** Cherche où poser ce PNJ, au plus près de son emplacement : pieds au sol, à ciel ouvert, loin des points nommés. */
    private Location settle(World w, Slot s) {
        Place p = s.place;
        int bx = (int) Math.floor(s.x), bz = (int) Math.floor(s.z);
        for (int ring = 0; ring <= 5; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int x = bx + dx, z = bz + dz;
                    double y = s.outside ? Nav.ground(w, x, z) : Nav.feet(w, x, z, p.groundY);
                    if (Double.isNaN(y) || !Nav.open(w, x, z, y)) {
                        continue;
                    }
                    double dc = Math.hypot(x + 0.5 - (p.town.x() + 0.5), z + 0.5 - (p.town.z() + 0.5));
                    if (s.outside ? (dc < p.town.radius() + 6 || Math.abs(y - p.groundY) > 14) : dc > p.town.radius() - 3) {
                        continue;
                    }
                    if (s.role != Role.GARDE_POSTE && nearPoint(p.town, x + 0.5, z + 0.5, 3.2)) {
                        continue;
                    }
                    return new Location(w, x + 0.5, y, z + 0.5, s.yaw, 0);
                }
            }
        }
        return null;
    }

    private static boolean nearPoint(Town t, double x, double z, double within) {
        for (Location l : t.points().values()) {
            double dx = l.getX() - x, dz = l.getZ() - z;
            if (dx * dx + dz * dz < within * within) {
                return true;
            }
        }
        return false;
    }

    private Actor spawn(World w, Slot s) {
        Location at = settle(w, s);
        if (at == null) {
            return null;
        }
        long seed = s.place.town.id().hashCode() * 131L + s.n;
        Random r = new Random(seed);
        LivingEntity e = s.role.human ? human(at, s.role, s.place.culture, s.place.guards, s.n, r) : beast(at, s.role, s.place.culture, r);
        if (e == null) {
            return null;
        }
        Actor a = register(s.role, e, s, seed ^ tick);
        a.hx = at.getX();
        a.hy = at.getY();
        a.hz = at.getZ();
        a.wait = 20 + a.random.nextInt(120);
        s.live = a;
        s.fails = 0;
        return a;
    }

    private Actor register(Role role, LivingEntity e, Slot s, long seed) {
        Actor a = new Actor(role, e, s, seed);
        Location l = e.getLocation();
        a.x = l.getX();
        a.y = l.getY();
        a.z = l.getZ();
        a.yaw = a.targetYaw = l.getYaw();
        actors.add(a);
        byEntity.put(e.getUniqueId(), a);
        return a;
    }

    private static <T> T pick(List<T> list, int n) {
        return list.isEmpty() ? null : list.get(Math.floorMod(n, list.size()));
    }

    private LivingEntity human(Location at, Role role, String culture, String guards, int n, Random r) {
        String skin;
        ItemStack hand = null;
        double scale = 0.96 + r.nextDouble() * 0.09;
        switch (role) {
            case GARDE_POSTE, GARDE_RONDE, PATROUILLE -> {
                skin = pick(Looks.skins("garde_" + guards + "_"), n);
                hand = new ItemStack(Material.IRON_SWORD);
                scale = 1.0 + r.nextDouble() * 0.06;
            }
            case FERMIER -> {
                skin = pick(Looks.skins("fermier_" + culture + "_"), n);
                hand = new ItemStack(r.nextBoolean() ? Material.IRON_HOE : Material.WHEAT);
            }
            case ENFANT -> {
                skin = pick(Looks.skins("enfant_" + culture + "_"), n);
                scale = 0.54 + r.nextDouble() * 0.14;
            }
            case VOYAGEUR -> {
                skin = pick(Looks.skins("voyageur_"), n);
                hand = new ItemStack(Material.STICK);
            }
            default -> skin = pick(Looks.skins("villageois_" + culture + "_"), n);
        }
        if (skin == null) {
            return null;
        }
        ItemStack held = hand;
        double size = scale;
        return at.getWorld().spawn(at, Mannequin.class, m -> {
            tag(m, role);
            m.setSilent(true);
            m.setImmovable(true);
            m.setGravity(false);
            m.setCustomNameVisible(false);
            m.setDescription(Component.empty());
            try {
                m.setProfile(Looks.skin(skin));
                m.setSkinParts(com.destroystokyo.paper.SkinParts.allParts());
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Ambiance : skin « " + skin + " » refusé (" + ex.getMessage() + ").");
            }
            AttributeInstance sc = m.getAttribute(Attribute.SCALE);
            if (sc != null) {
                sc.setBaseValue(size);
            }
            if (held != null && m.getEquipment() != null) {
                m.getEquipment().setItemInMainHand(held);
            }
        });
    }

    private LivingEntity beast(Location at, Role role, String culture, Random r) {
        Class<? extends Mob> type = switch (role) {
            case CHAT -> Cat.class;
            case CHIEN -> Wolf.class;
            case VACHE -> Cow.class;
            case COCHON -> Pig.class;
            case POULE -> Chicken.class;
            default -> Donkey.class;
        };
        boolean baby = role != Role.ANE && r.nextInt(6) == 0;
        return at.getWorld().spawn(at, type, m -> {
            tag(m, role);
            m.setCanPickupItems(false);
            if (m instanceof Ageable a) {
                if (baby) {
                    a.setBaby();
                } else {
                    a.setAdult();
                }
            }
            if (m instanceof Breedable b) {
                b.setAgeLock(true);
                b.setBreed(false);
            }
            if (m instanceof Cat c) {
                Cat.Type v = Looks.cat(pick(Looks.ids("chat"), r.nextInt(64)));
                if (v != null) {
                    c.setCatType(v);
                }
            } else if (m instanceof Wolf wf) {
                Wolf.Variant v = Looks.wolf(pick(Looks.ids("chien"), r.nextInt(64)));
                if (v != null) {
                    wf.setVariant(v);
                }
                // Un chien de village : collier au cou, queue haute.
                wf.setTamed(true);
                org.bukkit.DyeColor[] collars = {org.bukkit.DyeColor.RED, org.bukkit.DyeColor.BLUE, org.bukkit.DyeColor.BROWN,
                        org.bukkit.DyeColor.GREEN, org.bukkit.DyeColor.YELLOW};
                wf.setCollarColor(collars[r.nextInt(collars.length)]);
            } else if (m instanceof Cow c) {
                Cow.Variant v = Looks.cow(culture.equals("ombre") ? "vache_morne" : culture.equals("lumiere") ? "vache_ardence"
                        : pick(Looks.ids("vache"), r.nextInt(64)));
                if (v != null) {
                    setVariant(c, v);
                }
            } else if (m instanceof Pig pg) {
                Pig.Variant v = Looks.pig(pick(Looks.ids("cochon"), r.nextInt(64)));
                if (v != null) {
                    pg.setVariant(v);
                }
            } else if (m instanceof Chicken ch) {
                Chicken.Variant v = Looks.chicken(pick(Looks.ids("poule"), r.nextInt(64)));
                if (v != null) {
                    ch.setVariant(v);
                }
                ch.setEggLayTime(Integer.MAX_VALUE);
            }
            // Plus aucun comportement du jeu (fuite, chasse, reproduction) : ce service les promène lui-même.
            Bukkit.getMobGoals().removeAllGoals(m);
        });
    }

    /**
     * Donne sa variante à une vache par réflexion. Un appel direct à Cow.setVariant est réécrit par Paper, pour ce
     * plugin (api-version 1.21), vers l'ancienne interface AbstractCow, où la méthode n'existe pas : erreur à l'exécution.
     */
    private void setVariant(Object entity, Object variant) {
        try {
            for (java.lang.reflect.Method mt : entity.getClass().getMethods()) {
                if (mt.getName().equals("setVariant") && mt.getParameterCount() == 1 && mt.getParameterTypes()[0].isInstance(variant)) {
                    mt.invoke(entity, variant);
                    return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Ambiance : variante d'animal refusée (" + ex + ").");
        }
    }

    private void tag(LivingEntity e, Role role) {
        e.getPersistentDataContainer().set(key, PersistentDataType.STRING, role.name());
        e.setPersistent(false);
        e.setRemoveWhenFarAway(false);
        e.setInvulnerable(true);
        e.setCollidable(false);
    }

    private void forget(Actor a) {
        actors.remove(a);
        byEntity.remove(a.entity.getUniqueId());
        if (a.slot != null && a.slot.live == a) {
            a.slot.live = null;
            a.slot.retryAt = tick + 40;
        }
    }

    private void remove(Actor a) {
        forget(a);
        a.entity.remove();
    }

    private boolean ours(Entity e) {
        return e != null && e.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    private void purge(Collection<? extends Entity> entities) {
        for (Entity e : entities) {
            if (ours(e) && !byEntity.containsKey(e.getUniqueId())) {
                e.remove();
            }
        }
    }

    // ------------------------------------------------------------------ Marche des humains

    private static float turn(float from, float to, float max) {
        float d = ((to - from) % 360 + 540) % 360 - 180;
        if (Math.abs(d) <= max) {
            return to;
        }
        return from + Math.signum(d) * max;
    }

    private void walk(Actor a) {
        World w = a.entity.getWorld();
        if (a.path != null) {
            double left = a.role.speed;
            while (left > 0 && a.seg < a.path.size()) {
                Vector t = a.path.get(a.seg);
                double dx = t.getX() - a.x, dz = t.getZ() - a.z, d = Math.hypot(dx, dz);
                if (d > 1.0E-3) {
                    a.targetYaw = yawOf(dx, dz);
                }
                if (d <= left) {
                    a.x = t.getX();
                    a.z = t.getZ();
                    a.y = t.getY();
                    a.seg++;
                    left -= d;
                } else {
                    a.x += dx / d * left;
                    a.z += dz / d * left;
                    a.y += (t.getY() - a.y) * Math.min(1, left / d);
                    left = 0;
                }
            }
            if (a.seg >= a.path.size()) {
                a.path = null;
                arrived(a);
            }
            a.yaw = turn(a.yaw, a.targetYaw, 22);
            a.entity.teleport(new Location(w, a.x, a.y, a.z, a.yaw, 0));
            return;
        }
        if (a.road != null) {
            if (!onward(w, a)) {
                remove(a);
            }
            return;
        }
        if ((tick + a.slot.n) % 4 == 0) {
            look(w, a);
        }
        if (--a.wait > 0) {
            if (a.role == Role.FERMIER && a.wait % 34 == 0 && a.random.nextInt(3) > 0) {
                a.entity.swingMainHand();
            }
            return;
        }
        if (a.role == Role.GARDE_POSTE) {
            a.wait = 200;
            return;
        }
        if (searches >= 2) {
            a.wait = 2 + a.random.nextInt(8);
            return;
        }
        searches++;
        plan(w, a);
    }

    private void arrived(Actor a) {
        Random r = a.random;
        a.wait = switch (a.role) {
            case ENFANT -> 15 + r.nextInt(70);
            case GARDE_RONDE -> 60 + r.nextInt(100);
            case FERMIER -> 120 + r.nextInt(240);
            default -> 70 + r.nextInt(220);
        };
        if (a.facing != null && a.facing.entity.isValid()) {
            a.targetYaw = yawOf(a.facing.x - a.x, a.facing.z - a.z);
            a.wait += 60;
        }
    }

    /** À l'arrêt : regarde le joueur qui passe, celui à qui il parle, ou reprend sa faction. */
    private void look(World w, Actor a) {
        Player near = null;
        double best = 6 * 6;
        for (Player p : w.getPlayers()) {
            double dx = p.getX() - a.x, dz = p.getZ() - a.z, d = dx * dx + dz * dz;
            if (d < best && Math.abs(p.getY() - a.y) < 4) {
                best = d;
                near = p;
            }
        }
        float pitch = 0;
        if (near != null) {
            a.targetYaw = yawOf(near.getX() - a.x, near.getZ() - a.z);
            double eye = a.y + a.entity.getHeight() * 0.85;
            pitch = (float) Math.max(-30, Math.min(30, -Math.toDegrees(Math.atan2(near.getEyeLocation().getY() - eye, Math.sqrt(best)))));
        } else if (a.role == Role.GARDE_POSTE) {
            a.targetYaw = a.slot.yaw;
        } else if (a.facing != null && a.facing.entity.isValid()) {
            a.targetYaw = yawOf(a.facing.x - a.x, a.facing.z - a.z);
        }
        float next = turn(a.yaw, a.targetYaw, 30);
        Location l = a.entity.getLocation();
        if (Math.abs(next - a.yaw) > 0.5 || Math.abs(l.getPitch() - pitch) > 2) {
            a.yaw = next;
            a.entity.teleport(new Location(w, a.x, a.y, a.z, a.yaw, pitch));
        }
    }

    /** Choisit où aller : un tour près de chez soi, vers un voisin (villageois), ou après un autre enfant. */
    private void plan(World w, Actor a) {
        Slot s = a.slot;
        Town t = s.place.town;
        a.facing = null;
        Random r = a.random;
        double tx = Double.NaN, tz = Double.NaN;
        if ((a.role == Role.VILLAGEOIS && r.nextInt(4) == 0) || (a.role == Role.ENFANT && r.nextInt(3) == 0)) {
            Actor other = neighbour(a, a.role == Role.ENFANT ? 20 : 14);
            if (other != null) {
                double ang = r.nextDouble() * Math.PI * 2;
                tx = other.x + Math.cos(ang) * 1.6;
                tz = other.z + Math.sin(ang) * 1.6;
                a.facing = other;
            }
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            if (Double.isNaN(tx)) {
                double ang = r.nextDouble() * Math.PI * 2, d = 3 + r.nextDouble() * (a.role.range - 3);
                tx = a.hx + Math.cos(ang) * d;
                tz = a.hz + Math.sin(ang) * d;
            }
            int bx = (int) Math.floor(tx), bz = (int) Math.floor(tz);
            tx = Double.NaN;
            double ty = s.outside ? Nav.ground(w, bx, bz) : Nav.feet(w, bx, bz, a.hy);
            if (Double.isNaN(ty) || !Nav.open(w, bx, bz, ty) || Math.abs(ty - a.hy) > (s.outside ? 6 : 1.6)) {
                continue;
            }
            double dc = Math.hypot(bx + 0.5 - (t.x() + 0.5), bz + 0.5 - (t.z() + 0.5));
            if (s.outside ? dc < t.radius() + 5 : dc > t.radius() - 3) {
                continue;
            }
            // Pas d'arrêt sur un point nommé : c'est là que se tiennent les PNJ de ville et de quête.
            if (nearPoint(t, bx + 0.5, bz + 0.5, 2.6)) {
                continue;
            }
            List<Vector> path = Nav.path(w, a.x, a.y, a.z, bx, bz, 420);
            if (path != null && path.size() > 1) {
                a.path = path;
                a.seg = 1;
                return;
            }
            break;
        }
        a.facing = null;
        a.wait = 40 + r.nextInt(60);
    }

    private Actor neighbour(Actor a, double within) {
        Actor best = null;
        int seen = 0;
        for (Actor o : actors) {
            if (o == a || o.slot == null || o.slot.place != a.slot.place || !o.role.human || o.slot.outside != a.slot.outside) {
                continue;
            }
            if (a.role == Role.ENFANT && o.role != Role.ENFANT) {
                continue;
            }
            double dx = o.x - a.x, dz = o.z - a.z;
            if (dx * dx + dz * dz < within * within && a.random.nextInt(++seen) == 0) {
                best = o;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ Animaux

    private void animal(World w, Actor a) {
        if (!(a.entity instanceof Mob m)) {
            return;
        }
        Location l = m.getLocation();
        if (a.leader != null) {
            // L'âne suit son maître.
            Location to = a.leader.entity.getLocation();
            double d2 = to.distanceSquared(l);
            if (d2 > 14 * 14) {
                m.teleport(to);
            } else if (d2 > 3 * 3) {
                m.getPathfinder().moveTo(to, a.role.speed);
            }
            return;
        }
        double dh = Math.hypot(l.getX() - a.hx, l.getZ() - a.hz);
        if (dh > a.role.range + 18 || l.getY() < a.hy - 12) {
            m.teleport(new Location(w, a.hx, a.hy, a.hz, l.getYaw(), 0));
            return;
        }
        if (tick < a.nextMove) {
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(l) < 5 * 5) {
                    m.lookAt(p);
                    break;
                }
            }
            return;
        }
        if (m.getPathfinder().hasPath()) {
            return;
        }
        Random r = a.random;
        if (m instanceof Sittable sit) {
            boolean rest = r.nextInt(100) < (a.role == Role.CHAT ? 50 : 30);
            sit.setSitting(rest);
            if (m instanceof Cat c) {
                c.setLyingDown(false);
            }
            if (rest) {
                a.nextMove = tick + 20L * (8 + r.nextInt(20));
                return;
            }
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            double ang = r.nextDouble() * Math.PI * 2, d = 2 + r.nextDouble() * (a.role.range - 2);
            int bx = (int) Math.floor(a.hx + Math.cos(ang) * d), bz = (int) Math.floor(a.hz + Math.sin(ang) * d);
            boolean out = a.slot != null && a.slot.outside;
            double ty = out ? Nav.ground(w, bx, bz) : Nav.feet(w, bx, bz, a.hy);
            if (Double.isNaN(ty) || Math.abs(ty - a.hy) > (out ? 6 : 1.6)) {
                continue;
            }
            if (a.slot != null) {
                Town t = a.slot.place.town;
                double dc = Math.hypot(bx + 0.5 - (t.x() + 0.5), bz + 0.5 - (t.z() + 0.5));
                if (out ? dc < t.radius() + 5 : dc > t.radius() - 3) {
                    continue;
                }
            }
            m.getPathfinder().moveTo(new Location(w, bx + 0.5, ty, bz + 0.5), a.role.speed);
            break;
        }
        a.nextMove = tick + 20L * (4 + r.nextInt(12));
    }

    // ------------------------------------------------------------------ Routes : voyageurs et patrouilles

    private Place placeAt(double x, double z, double margin) {
        for (Place p : places) {
            double dx = x - (p.town.x() + 0.5), dz = z - (p.town.z() + 0.5), r = p.town.radius() + margin;
            if (dx * dx + dz * dz < r * r) {
                return p;
            }
        }
        return null;
    }

    private void travellers(World w, boolean night) {
        for (Player p : w.getPlayers()) {
            Location pl = p.getLocation();
            if (actors.size() >= cap || random.nextInt(night ? 40 : 14) != 0) {
                continue;
            }
            int near = 0;
            java.util.Set<Integer> seen = new java.util.HashSet<>();
            for (Actor a : actors) {
                if (a.road != null && a.entity.getLocation().distanceSquared(pl) < 110 * 110 && seen.add(a.group)) {
                    near++;
                }
            }
            if (near < roadGroups) {
                send(w, pl, random.nextBoolean() ? 1 : -1, night);
            }
        }
    }

    /** La route la plus proche de ce point, cherchée dans les chunks voisins (l'index des routes est par chunk). */
    private static boolean nearRoad(ContinentData data, Location l, ContinentData.RoadHit hit) {
        for (int ring = 0; ring <= 2; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring
                            && data.road(l.getBlockX() + dx * 16, l.getBlockZ() + dz * 16, 10, hit)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Envoie un groupe sur la route la plus proche de ce point. Faux s'il n'y a pas de route ou pas la place. */
    private boolean send(World w, Location near, int dir, boolean night) {
        ContinentData data = continent.data();
        ContinentData.RoadHit hit = new ContinentData.RoadHit();
        if (!nearRoad(data, near, hit)) {
            return false;
        }
        ContinentData.Road road = hit.road;
        // Départ à une cinquantaine de blocs le long de la route, d'où ils viennent vers le joueur puis le dépassent.
        int k = hit.sample;
        double run = 0;
        while (run < 50 && k - dir >= 1 && k - dir < road.x.length - 1) {
            run += Math.hypot(road.x[k - dir] - road.x[k], road.z[k - dir] - road.z[k]);
            k -= dir;
        }
        if (run < 40 || placeAt(road.x[k], road.z[k], 4) != null || road.bridge[k]) {
            return false;
        }
        return group(w, road, k, dir, data, night);
    }

    private boolean group(World w, ContinentData.Road road, int k, int dir, ContinentData data, boolean night) {
        int bx = (int) Math.floor(road.x[k]), bz = (int) Math.floor(road.z[k]);
        if (!Nav.loaded(w, bx, bz)) {
            return false;
        }
        ContinentData.Region region = data.region(bx, bz);
        String faction = region == null || region.faction() == null ? "" : region.faction();
        String guards = faction.equals("lumiere") || faction.equals("ombre") ? faction : "neutre";
        int kind = random.nextInt(100);
        int id = ++groups;
        if (night || kind < 35) {
            Actor first = traveller(w, road, k, dir, 1.1, Role.PATROUILLE, guards, id);
            if (first != null && k - 2 * dir >= 0 && k - 2 * dir < road.x.length) {
                traveller(w, road, k - 2 * dir, dir, -1.1, Role.PATROUILLE, guards, id);
            }
            return first != null;
        }
        Actor walker = traveller(w, road, k, dir, 0.9, Role.VOYAGEUR, guards, id);
        if (walker != null && kind < 62) {
            Location at = walker.entity.getLocation();
            LivingEntity donkey = beast(at, Role.ANE, "neutre", new Random(id));
            if (donkey instanceof Donkey d) {
                d.setCarryingChest(true);
            }
            if (donkey != null) {
                Actor a = register(Role.ANE, donkey, null, id * 7L);
                a.leader = walker;
                a.group = id;
            }
        }
        return walker != null;
    }

    /** Pendant un essai : envoie tout de suite un groupe sur la route la plus proche de l'observateur. */
    public String testRoad() {
        if (observer == null) {
            return "<#FF8A3B>Lance d'abord « /continent ambiance essai <lieu> ».";
        }
        World w = observer.getWorld();
        int dir = random.nextBoolean() ? 1 : -1;
        if (send(w, observer, dir, false) || send(w, observer, -dir, false)) {
            int road = 0;
            for (Actor a : actors) {
                road += a.slot == null ? 1 : 0;
            }
            return "<#7FD4FF>Un groupe est parti sur la route. <#D7DCE2>Sur les routes : " + road + " PNJ.";
        }
        return "<#FF8A3B>Pas de route à moins de 40 blocs, ou pas la place d'y poser quelqu'un.";
    }

    private Actor traveller(World w, ContinentData.Road road, int k, int dir, double offset, Role role, String guards, int group) {
        double[] at = roadPoint(w, road, k, dir, offset, road.y[k] + 1);
        if (at == null) {
            return null;
        }
        Location l = new Location(w, at[0], at[1], at[2], 0, 0);
        LivingEntity e = human(l, role, "neutre", guards, group, new Random(group * 31L + k));
        if (e == null) {
            return null;
        }
        Actor a = register(role, e, null, group * 131L + k);
        a.road = road;
        a.sample = k;
        a.dir = dir;
        a.offset = offset;
        a.group = group;
        return a;
    }

    /** Point de marche à l'échantillon k d'une route, décalé sur le côté : {x, y des pieds, z}, ou null (hors des chunks chargés, obstacle). */
    private static double[] roadPoint(World w, ContinentData.Road road, int k, int dir, double offset, double nearY) {
        int k2 = Math.max(0, Math.min(road.x.length - 1, k + dir));
        double vx = (road.x[k2] - road.x[k]) * dir, vz = (road.z[k2] - road.z[k]) * dir;
        if (k2 == k) {
            int k0 = Math.max(0, Math.min(road.x.length - 1, k - dir));
            vx = (road.x[k] - road.x[k0]) * dir;
            vz = (road.z[k] - road.z[k0]) * dir;
        }
        double len = Math.hypot(vx, vz);
        double x = road.x[k], z = road.z[k];
        if (len > 1.0E-6) {
            // Chacun marche à sa droite.
            x += -vz / len * offset * dir;
            z += vx / len * offset * dir;
        }
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        double y = Nav.feet(w, bx, bz, nearY);
        if (Double.isNaN(y)) {
            y = Nav.feet(w, bx, bz, road.y[k] + 1);
        }
        return Double.isNaN(y) ? null : new double[]{x, y, z};
    }

    /** Prochain tronçon d'un voyageur. Faux quand son voyage s'arrête (bout de la route, ville atteinte, chunk déchargé). */
    private boolean onward(World w, Actor a) {
        for (int skip = 1; skip <= 3; skip++) {
            int k = a.sample + a.dir * skip;
            if (k < 0 || k >= a.road.x.length) {
                return false;
            }
            double[] at = roadPoint(w, a.road, k, a.dir, a.offset, a.y);
            if (at == null) {
                continue;
            }
            if (placeAt(at[0], at[2], -6) != null) {
                return false;
            }
            a.sample = k;
            List<Vector> path = new ArrayList<>(2);
            path.add(new Vector(a.x, a.y, a.z));
            path.add(new Vector(at[0], at[1], at[2]));
            a.path = path;
            a.seg = 1;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ Répliques

    private static final Map<Role, String[]> LINES = new EnumMap<>(Role.class), LINES_EN = new EnumMap<>(Role.class);

    static {
        LINES.put(Role.GARDE_POSTE, new String[]{"Circulez.", "Rien à signaler.", "Les routes ne sont plus sûres, restez prudent.",
                "Pas d'histoires en ville.", "Encore un Marqué… bienvenue."});
        LINES_EN.put(Role.GARDE_POSTE, new String[]{"Move along.", "Nothing to report.", "The roads are not safe anymore. Stay sharp.",
                "No trouble in town.", "Another Marked… welcome."});
        LINES.put(Role.VILLAGEOIS, new String[]{"Belle journée, non ?", "Vous venez de loin ?", "On dit que le ciel est tombé près d'ici.",
                "Le marché est par là.", "Que les étoiles vous gardent.", "J'ai encore mal dormi, avec ces lueurs."});
        LINES_EN.put(Role.VILLAGEOIS, new String[]{"Lovely day, isn't it?", "Have you come far?", "They say the sky fell near here.",
                "The market is that way.", "May the stars keep you.", "I slept badly again, with those lights."});
        LINES.put(Role.FERMIER, new String[]{"La terre est bonne, cette année.", "Les bêtes sont nerveuses depuis l'autre nuit.",
                "Pas le temps de causer, y a du travail.", "Ne marchez pas dans mes semis !"});
        LINES_EN.put(Role.FERMIER, new String[]{"The soil is good this year.", "The animals have been restless since that night.",
                "No time to chat, there's work to do.", "Mind my seedlings!"});
        LINES.put(Role.ENFANT, new String[]{"T'es un vrai héros ?", "Attrape-moi si tu peux !", "Maman dit de pas parler aux étrangers.",
                "Tu as une épée ! Je peux la voir ?", "Moi aussi je serai garde, plus tard."});
        LINES_EN.put(Role.ENFANT, new String[]{"Are you a real hero?", "Catch me if you can!", "Mum says not to talk to strangers.",
                "You have a sword! Can I see it?", "I'll be a guard too, one day."});
        LINES.put(Role.VOYAGEUR, new String[]{"La route est longue.", "Bon voyage à vous.", "Méfiez-vous des camps au bord du chemin.",
                "Je vais jusqu'à la prochaine ville."});
        LINES_EN.put(Role.VOYAGEUR, new String[]{"It's a long road.", "Safe travels.", "Beware of the camps by the roadside.",
                "I'm heading to the next town."});
        LINES.put(Role.GARDE_RONDE, LINES.get(Role.GARDE_POSTE));
        LINES_EN.put(Role.GARDE_RONDE, LINES_EN.get(Role.GARDE_POSTE));
        LINES.put(Role.PATROUILLE, new String[]{"Patrouille. Passez votre chemin.", "La route est dégagée jusqu'à la ville.",
                "Si vous voyez des brigands, prévenez-nous."});
        LINES_EN.put(Role.PATROUILLE, new String[]{"Patrol. Keep moving.", "The road is clear up to the town.",
                "If you see bandits, let us know."});
    }

    private void talk(Player p, Actor a) {
        long now = System.currentTimeMillis();
        Long next = talkCooldown.get(p.getUniqueId());
        if (next != null && next > now) {
            return;
        }
        talkCooldown.put(p.getUniqueId(), now + 1800);
        Location l = a.entity.getLocation();
        if (!a.role.human) {
            Sound s = switch (a.role) {
                case CHAT -> Sound.ENTITY_CAT_PURREOW;
                case CHIEN -> Sound.ENTITY_WOLF_AMBIENT;
                case VACHE -> Sound.ENTITY_COW_AMBIENT;
                case COCHON -> Sound.ENTITY_PIG_AMBIENT;
                case POULE -> Sound.ENTITY_CHICKEN_AMBIENT;
                default -> Sound.ENTITY_DONKEY_AMBIENT;
            };
            p.playSound(l, s, 0.7f, 1.1f);
            if (a.role == Role.CHAT || a.role == Role.CHIEN) {
                p.spawnParticle(Particle.HEART, l.clone().add(0, a.entity.getHeight() + 0.2, 0), 2, 0.2, 0.1, 0.2, 0);
            }
            return;
        }
        boolean en = plugin.quests() != null && "en".equals(plugin.quests().lang(p));
        String[] set = (en ? LINES_EN : LINES).get(a.role);
        if (set == null) {
            return;
        }
        String line = set[random.nextInt(set.length)];
        if (a.path == null && a.road == null) {
            a.wait = Math.max(a.wait, 70);
        }
        TextDisplay bubble = l.getWorld().spawn(l.clone().add(0, a.entity.getHeight() + 0.45, 0), TextDisplay.class, d -> {
            d.getPersistentDataContainer().set(key, PersistentDataType.STRING, "bulle");
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.text(Text.mm("<#F4EEDC>" + Text.esc(line)));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(150, 10, 18, 36));
            d.setShadowed(false);
            d.setSeeThrough(false);
            d.setLineWidth(150);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
        });
        p.showEntity(plugin, bubble);
        p.playSound(l, a.role == Role.ENFANT ? Sound.ENTITY_VILLAGER_YES : Sound.ENTITY_VILLAGER_AMBIENT, 0.5f, a.role == Role.ENFANT ? 1.7f : 1.0f);
        plugin.getServer().getScheduler().runTaskLater(plugin, bubble::remove, 70L);
    }

    // ------------------------------------------------------------------ Événements : on ne leur fait rien, ils ne font rien

    // Après la barre de sorts (priorité basse) : un sort lancé en visant un passant part quand même, et annule ce clic.
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!ours(e.getRightClicked())) {
            return;
        }
        e.setCancelled(true);
        Actor a = byEntity.get(e.getRightClicked().getUniqueId());
        if (a != null && e.getHand() == EquipmentSlot.HAND && !(e instanceof PlayerInteractAtEntityEvent)) {
            talk(e.getPlayer(), a);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractAt(PlayerInteractAtEntityEvent e) {
        if (ours(e.getRightClicked())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (ours(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (ours(e.getEntity()) || ours(e.getTarget())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onLeash(PlayerLeashEntityEvent e) {
        if (ours(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onLove(EntityEnterLoveModeEvent e) {
        if (ours(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreed(EntityBreedEvent e) {
        if (ours(e.getMother()) || ours(e.getFather())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(EntityDropItemEvent e) {
        if (ours(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (ours(e.getEntity())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        purge(e.getEntities());
    }

    // ------------------------------------------------------------------ Commande d'administration

    /** État pour « /continent ambiance » : lieux actifs, présents par rôle, emplacements abandonnés. */
    public String status(String townId) {
        StringBuilder sb = new StringBuilder("<#7FD4FF>Ambiance : <#D7DCE2>" + (enabled ? "active" : "désactivée") + ", " + actors.size()
                + " PNJ présents (plafond " + cap + "), " + places.size() + " lieux.");
        int road = 0;
        for (Actor a : actors) {
            road += a.slot == null ? 1 : 0;
        }
        sb.append(" Sur les routes : ").append(road).append('.');
        for (Place p : places) {
            if (townId != null ? !p.town.id().equalsIgnoreCase(townId) : !p.active) {
                continue;
            }
            int live = 0, dead = 0;
            Map<Role, Integer> by = new EnumMap<>(Role.class);
            for (Slot s : p.slots) {
                if (s.live != null) {
                    live++;
                    by.merge(s.role, 1, Integer::sum);
                }
                dead += s.dead ? 1 : 0;
            }
            sb.append("\n<#FFD66E>").append(Text.esc(p.town.name())).append(" <#8C96A3>(").append(p.town.id()).append(", ").append(p.culture)
                    .append(") : <#D7DCE2>").append(live).append('/').append(p.slots.size()).append(" présents, ").append(dead)
                    .append(" emplacements abandonnés.");
            if (!by.isEmpty()) {
                sb.append(" <#8C96A3>").append(by.toString().toLowerCase(Locale.ROOT));
            }
            if (townId != null) {
                for (Slot s : p.slots) {
                    if (s.live == null) {
                        continue;
                    }
                    Location l = s.live.entity.getLocation();
                    sb.append("\n<#8C96A3> ").append(s.role.name().toLowerCase(Locale.ROOT)).append(" #").append(s.n).append(" <#D7DCE2>")
                            .append(String.format(Locale.ROOT, "%.1f %.1f %.1f", l.getX(), l.getY(), l.getZ()))
                            .append(s.live.path != null || (s.live.entity instanceof Mob m && m.getPathfinder().hasPath()) ? " <#7FD4FF>en marche" : "");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Essai sans joueur : charge les chunks autour d'un point d'un lieu et fait comme si quelqu'un s'y tenait pendant
     * {@code seconds} secondes. Sert à vérifier par la console que les PNJ apparaissent et bougent.
     */
    public String test(String townId, String point, int seconds) {
        refreshPlaces();
        for (Place p : places) {
            if (!p.town.id().equalsIgnoreCase(townId)) {
                continue;
            }
            Location at = p.town.points().get(point == null ? "spawn" : point);
            if (at == null) {
                return "<#FF8A3B>Point inconnu. Points de " + Text.esc(p.town.name()) + " : " + String.join(", ", p.town.points().keySet());
            }
            endObserver();
            World w = at.getWorld();
            int cx = at.getBlockX() >> 4, cz = at.getBlockZ() >> 4;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    w.addPluginChunkTicket(cx + dx, cz + dz, plugin);
                    observerTickets.add(new long[]{cx + dx, cz + dz});
                }
            }
            observer = at.clone();
            observerUntil = tick + 20L * Math.max(5, Math.min(600, seconds));
            return "<#7FD4FF>Essai à " + Text.esc(p.town.name()) + " <#D7DCE2>(" + (point == null ? "spawn" : point) + ") pendant "
                    + Math.max(5, Math.min(600, seconds)) + " s. « /continent ambiance etat " + p.town.id() + " » pour voir qui est là.";
        }
        return "<#FF8A3B>Lieu inconnu. Lieux : " + String.join(", ", townIds());
    }

    public List<String> townIds() {
        List<String> out = new ArrayList<>();
        for (Place p : places) {
            out.add(p.town.id());
        }
        return out;
    }

    private void endObserver() {
        if (observer != null) {
            World w = observer.getWorld();
            for (long[] c : observerTickets) {
                w.removePluginChunkTicket((int) c[0], (int) c[1], plugin);
            }
        }
        observerTickets.clear();
        observer = null;
    }
}
