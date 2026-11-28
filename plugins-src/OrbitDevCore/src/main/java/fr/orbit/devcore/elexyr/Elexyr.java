package fr.orbit.devcore.elexyr;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.message.Messages;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Elxssir, le soldat casse-noisette squelette : un easter egg. Il ne sert à rien d'autre qu'à demander,
 * quand on s'approche ou qu'on lui clique dessus, si on a des noisettes.
 *
 * <p>Modèle en cubes du pack (tools/ElexyrGen.java) : une pièce par articulation, chacune portée par un
 * ItemDisplay ({@code orbitdev:elexyr_<pièce>}) posé à ses pieds et placé par sa matrice. Il suit le joueur
 * le plus proche du regard, respire, claque de la mâchoire, et tend la main quand il pose sa question.
 *
 * <p>Les entités ne sont pas sauvegardées avec le monde : les emplacements sont dans {@code elexyr.yml}
 * et le plugin recrée le PNJ dès que son chunk est chargé (rien ne traîne si le plugin est retiré).
 */
public final class Elexyr implements Listener {

    private static final String TAG = "orbit_elexyr";
    private static final String[] PIECES = {"tete", "oeil", "machoire", "torse", "bras_d", "bras_g", "jambe_d", "jambe_g"};
    private static final int TETE = 0, OEIL = 1, MACHOIRE = 2, TORSE = 3, BRAS_D = 4, BRAS_G = 5, JAMBE_D = 6, JAMBE_G = 7;
    /** L'ItemDisplay dessine l'objet retourné d'un demi-tour : on le compense. */
    private static final Matrix4f ITEM_FLIP = new Matrix4f().rotationY((float) Math.PI);
    /** À 1, une unité du modèle vaut 1/16 de bloc ; 0,9 : le crâne à hauteur de joueur, le shako par-dessus. */
    private static final float SCALE = 0.9f;
    private static final int STEP = 2;                 // ticks entre deux poses
    private static final int TALK_TICKS = 64;
    private static final double SEE = 12, NEAR = 3.2, AWAKE = 36;
    private static final long CLICK_COOLDOWN_MS = 2_500, NEAR_COOLDOWN_MS = 45_000;

    /** Il ne pose qu'une seule question, tournée de plusieurs façons. */
    private static final String[] LINES = {
            "Dis… t'aurais pas des noisettes ?",
            "Psst. Tu as des noisettes ?",
            "Halte ! … Tu as des noisettes ?",
            "Une question, une seule : tu as des noisettes ?",
            "Des noisettes. Est-ce que tu as des noisettes ?",
            "Tu n'aurais pas une noisette ou deux, par hasard ?"
    };

    /** Emplacement d'un Elxssir et son état d'animation. */
    public static final class Spot {
        private final int id;
        private final String world;
        private final double x, y, z;
        private final float yaw;
        private final ItemDisplay[] parts = new ItemDisplay[PIECES.length];
        private Interaction hitbox;
        private TextDisplay name, bubble;
        private float bodyYaw, headYaw, headPitch;
        private int talk, bubbleTicks, clack;
        private long nextClack;
        private boolean resting;
        private final double phase = ThreadLocalRandom.current().nextDouble(10);
        private final Map<UUID, Long> asked = new HashMap<>();

        private Spot(int id, String world, double x, double y, double z, float yaw) {
            this.id = id;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.bodyYaw = yaw;
        }

        public int id() {
            return id;
        }

        public String world() {
            return world;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }
    }

    private final OrbitDevCore plugin;
    private final File file;
    private final Vector3f[] pivots = new Vector3f[PIECES.length];
    private final NamespacedKey[] models = new NamespacedKey[PIECES.length];
    private final List<Spot> spots = new ArrayList<>();
    private float height = 46.8f;
    private BukkitTask task;
    private long tick;

    public Elexyr(OrbitDevCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "elexyr.yml");
    }

    // ------------------------------------------------------------------ démarrage / arrêt

    public void start() {
        try {
            loadRig();
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "elexyr.rig illisible : Elxssir désactivé", e);
            return;
        }
        load();
        purge();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::step, 20L, STEP);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Spot s : spots) {
            despawn(s);
        }
        purge();
    }

    public boolean enabled() {
        return task != null;
    }

    /** Pivots des pièces, écrits par tools/ElexyrGen.java. */
    private void loadRig() throws IOException {
        InputStream in = plugin.getResource("elexyr.rig");
        if (in == null) {
            throw new IOException("elexyr.rig absent du jar");
        }
        Map<String, Vector3f> read = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\s+");
                read.put(p[0], new Vector3f(Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])));
            }
        }
        for (int i = 0; i < PIECES.length; i++) {
            pivots[i] = read.get(PIECES[i]);
            if (pivots[i] == null) {
                throw new IOException("pièce absente de elexyr.rig : " + PIECES[i]);
            }
            models[i] = new NamespacedKey("orbitdev", "elexyr_" + PIECES[i]);
        }
        if (read.containsKey("hauteur")) {
            height = read.get("hauteur").x;
        }
    }

    private void load() {
        spots.clear();
        if (!file.exists()) {
            return;
        }
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("elexyr");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null || s.getString("monde") == null) {
                continue;
            }
            try {
                spots.add(new Spot(Integer.parseInt(key), s.getString("monde"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                        (float) s.getDouble("yaw")));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("elexyr.yml : emplacement ignoré (« " + key + " » n'est pas un numéro).");
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("elexyr");
        for (Spot s : spots) {
            ConfigurationSection c = root.createSection(String.valueOf(s.id));
            c.set("monde", s.world);
            c.set("x", s.x);
            c.set("y", s.y);
            c.set("z", s.z);
            c.set("yaw", (double) s.yaw);
        }
        plugin.fileWriter().writeAsync(file.toPath(), yaml.saveToString());
    }

    /** Retire d'éventuels restes (rechargement du plugin). */
    private void purge() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getScoreboardTags().contains(TAG)) {
                    e.remove();
                }
            }
        }
    }

    // ------------------------------------------------------------------ gestion

    public List<Spot> all() {
        return Collections.unmodifiableList(spots);
    }

    /** Fait apparaître un Elxssir, pieds en {@code at}, tourné vers le lacet de {@code at}. */
    public Spot add(Location at) {
        int id = 1;
        for (Spot s : spots) {
            id = Math.max(id, s.id + 1);
        }
        Spot s = new Spot(id, at.getWorld().getName(), round(at.getX()), round(at.getY()), round(at.getZ()), wrap(at.getYaw()));
        spots.add(s);
        save();
        return s;
    }

    /** Retire l'Elxssir le plus proche de {@code from} (dans le rayon donné). */
    public Spot removeNearest(Location from, double radius) {
        Spot best = null;
        double bestD = radius * radius;
        for (Spot s : spots) {
            if (!s.world.equals(from.getWorld().getName())) {
                continue;
            }
            double d = sq(s.x - from.getX()) + sq(s.y - from.getY()) + sq(s.z - from.getZ());
            if (d <= bestD) {
                bestD = d;
                best = s;
            }
        }
        if (best != null) {
            despawn(best);
            spots.remove(best);
            save();
        }
        return best;
    }

    public int removeAll() {
        int n = spots.size();
        for (Spot s : spots) {
            despawn(s);
        }
        spots.clear();
        save();
        return n;
    }

    // ------------------------------------------------------------------ entités

    private boolean alive(Spot s) {
        for (ItemDisplay d : s.parts) {
            if (d == null || !d.isValid()) {
                return false;
            }
        }
        return s.hitbox != null && s.hitbox.isValid() && s.name != null && s.name.isValid();
    }

    private void spawn(Spot s, World w) {
        Location base = new Location(w, s.x, s.y, s.z, 0f, 0f);
        float tall = height * SCALE / 16f;
        for (int i = 0; i < PIECES.length; i++) {
            final int piece = i;
            s.parts[i] = w.spawn(base, ItemDisplay.class, d -> {
                d.setPersistent(false);
                ItemStack item = new ItemStack(Material.PAPER);
                item.editMeta(m -> m.setItemModel(models[piece]));
                d.setItemStack(item);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.FIXED);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(STEP + 1);
                if (piece == OEIL) {
                    d.setBrightness(new Display.Brightness(15, 15));   // l'œil luit dans le noir
                }
                if (piece == TORSE) {
                    d.setShadowRadius(0.45f);
                    d.setShadowStrength(0.7f);
                }
                d.addScoreboardTag(TAG);
            });
        }
        s.hitbox = w.spawn(base, Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth(1.15f);
            i.setInteractionHeight(tall);
            i.setResponsive(true);
            i.addScoreboardTag(TAG);
        });
        s.name = w.spawn(base.clone().add(0, tall + 0.3, 0), TextDisplay.class, t -> {
            t.setPersistent(false);
            t.text(Messages.mini("<gradient:#FFE9A0:#E0B864><bold>Elxssir</bold></gradient>"));
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(90, 8, 12, 28));
            t.setShadowed(true);
            t.addScoreboardTag(TAG);
        });
        s.resting = false;
        pose(s, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private void despawn(Spot s) {
        for (int i = 0; i < s.parts.length; i++) {
            if (s.parts[i] != null) {
                s.parts[i].remove();
                s.parts[i] = null;
            }
        }
        if (s.hitbox != null) {
            s.hitbox.remove();
            s.hitbox = null;
        }
        if (s.name != null) {
            s.name.remove();
            s.name = null;
        }
        if (s.bubble != null) {
            s.bubble.remove();
            s.bubble = null;
        }
    }

    private Spot spotOf(Entity e) {
        if (!e.getScoreboardTags().contains(TAG)) {
            return null;
        }
        for (Spot s : spots) {
            if (e.equals(s.hitbox)) {
                return s;
            }
            for (ItemDisplay d : s.parts) {
                if (e.equals(d)) {
                    return s;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ animation

    private void step() {
        tick += STEP;
        for (Spot s : spots) {
            World w = Bukkit.getWorld(s.world);
            if (w == null || !w.isChunkLoaded(((int) Math.floor(s.x)) >> 4, ((int) Math.floor(s.z)) >> 4)) {
                continue;                               // ses entités sont parties avec le chunk
            }
            if (!alive(s)) {
                despawn(s);
                spawn(s, w);
            }
            Player near = null;
            double nearD = Double.MAX_VALUE;
            boolean awake = false;
            for (Player p : w.getPlayers()) {
                double d = sq(p.getX() - s.x) + sq(p.getY() - s.y) + sq(p.getZ() - s.z);
                awake |= d < AWAKE * AWAKE;
                if (d < nearD && d < SEE * SEE && p.getGameMode() != GameMode.SPECTATOR && !p.isInvisible()) {
                    nearD = d;
                    near = p;
                }
            }
            if (!awake) {
                if (!s.resting) {                       // personne : il reprend la pose, plus aucun paquet
                    s.bodyYaw = s.yaw;
                    s.headYaw = 0;
                    s.headPitch = 0;
                    pose(s, 0, 0, 0, 0, 0, 0, 0, 0);
                    s.resting = true;
                }
                continue;
            }
            s.resting = false;
            if (near != null && nearD < NEAR * NEAR) {
                ask(s, near, false);
            }
            animate(s, w, near);
        }
    }

    private void animate(Spot s, World w, Player near) {
        double t = tick / 20.0 + s.phase;
        float targetYaw = s.yaw, pitchT = 0;
        if (near != null) {
            Location eye = near.getEyeLocation();
            double dx = eye.getX() - s.x, dz = eye.getZ() - s.z;
            targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            double headY = s.y + 29.5 * SCALE / 16.0;
            pitchT = clamp((float) -Math.toDegrees(Math.atan2(eye.getY() - headY, Math.max(0.3, Math.hypot(dx, dz)))), -30, 35);
        }
        // Le corps ne pivote que si la tête ne suffit plus, ou pour reprendre sa place.
        float diff = wrap(targetYaw - s.bodyYaw);
        if (near == null || Math.abs(diff) > 38) {
            s.bodyYaw = wrap(s.bodyYaw + clamp(diff * 0.25f, -9, 9));
        }
        float headT = near != null ? clamp(wrap(targetYaw - s.bodyYaw), -62, 62) : (float) (Math.sin(t * 0.35) * 20);
        s.headYaw += (headT - s.headYaw) * 0.35f;
        s.headPitch += (pitchT - s.headPitch) * 0.35f;

        // Réplique : il tend la main droite, penche la tête et sa mâchoire de casse-noisette s'agite.
        float talkEnv = 0, jaw = 0;
        if (s.talk > 0) {
            s.talk -= STEP;
            float k = 1f - (float) s.talk / TALK_TICKS;
            talkEnv = smooth(0f, 0.14f, k) * smooth(1f, 0.82f, k);
            jaw = talkEnv * (5 + 13 * (float) Math.abs(Math.sin(k * TALK_TICKS * 0.5)));
            if (s.talk % 8 == 0 && s.talk > 12) {
                w.playSound(new Location(w, s.x, s.y + 1.6, s.z), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.35f, 0.75f);
            }
        } else if (s.clack > 0) {                       // au repos : deux petits claquements de temps en temps
            s.clack -= STEP;
            jaw = (s.clack / STEP) % 2 == 1 ? 11 : 0;
            if (jaw > 0) {
                w.playSound(new Location(w, s.x, s.y + 1.6, s.z), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.18f, 0.7f);
            }
        } else if (tick >= s.nextClack) {
            s.clack = 4 * STEP;
            s.nextClack = tick + 180 + ThreadLocalRandom.current().nextInt(260);
        }
        if (s.bubble != null && (s.bubbleTicks -= STEP) <= 0) {
            s.bubble.remove();
            s.bubble = null;
        }

        float breath = (float) Math.sin(t * 1.6);
        float torsoX = breath * 0.9f;
        float roll = (float) Math.sin(t * 0.7) * 1.5f + talkEnv * 8;
        float armR = (float) Math.sin(t * 1.6 + 0.6) * 2.5f + talkEnv * 58;
        float armRz = 2.5f + breath * 0.7f - talkEnv * 6;
        float armL = 5 + (float) Math.sin(t * 1.3) * 2f;      // le bras de l'arc, un peu en avant
        pose(s, torsoX, s.headYaw, s.headPitch - talkEnv * 6, roll, jaw, armR, armRz, armL);
    }

    /**
     * Pose les pièces. Angles en degrés, dans le repère du modèle (avant = -Z, sa droite = +X) :
     * rotation X positive = vers le haut pour un bras ou un regard, lacet de la tête comme en jeu (positif = à sa droite).
     */
    private void pose(Spot s, float torsoX, float headYaw, float headPitch, float headRoll, float jawOpen,
                      float armRightX, float armRightZ, float armLeftX) {
        Matrix4f root = new Matrix4f().rotateY((float) Math.PI - rad(s.bodyYaw)).scale(SCALE / 16f);
        Matrix4f torso = around(pivots[TORSE], new Matrix4f().rotateX(rad(torsoX)));
        Matrix4f head = new Matrix4f(torso).mul(around(pivots[TETE],
                new Matrix4f().rotateY(rad(-headYaw)).rotateX(rad(-headPitch)).rotateZ(rad(headRoll))));
        Matrix4f jaw = new Matrix4f(head).mul(around(pivots[MACHOIRE], new Matrix4f().rotateX(rad(-jawOpen))));
        Matrix4f right = new Matrix4f(torso).mul(around(pivots[BRAS_D], new Matrix4f().rotateX(rad(armRightX)).rotateZ(rad(armRightZ))));
        Matrix4f left = new Matrix4f(torso).mul(around(pivots[BRAS_G], new Matrix4f().rotateX(rad(armLeftX)).rotateZ(rad(-2.5f))));
        Matrix4f still = new Matrix4f();
        Matrix4f[] m = new Matrix4f[PIECES.length];
        m[TETE] = head;
        m[OEIL] = head;
        m[MACHOIRE] = jaw;
        m[TORSE] = torso;
        m[BRAS_D] = right;
        m[BRAS_G] = left;
        m[JAMBE_D] = still;
        m[JAMBE_G] = still;
        for (int i = 0; i < PIECES.length; i++) {
            ItemDisplay d = s.parts[i];
            if (d == null) {
                continue;
            }
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(STEP + 1);
            d.setTransformationMatrix(new Matrix4f(root).mul(m[i]).translate(pivots[i]).scale(16f).mul(ITEM_FLIP));
        }
    }

    /** Rotation {@code r} autour du pivot {@code p}. */
    private static Matrix4f around(Vector3f p, Matrix4f r) {
        return new Matrix4f().translate(p).mul(r).translate(-p.x, -p.y, -p.z);
    }

    // ------------------------------------------------------------------ la question

    private void ask(Spot s, Player player, boolean clicked) {
        long now = System.currentTimeMillis();
        Long last = s.asked.get(player.getUniqueId());
        if (last != null && now - last < (clicked ? CLICK_COOLDOWN_MS : NEAR_COOLDOWN_MS)) {
            return;
        }
        s.asked.put(player.getUniqueId(), now);
        s.asked.values().removeIf(v -> now - v > NEAR_COOLDOWN_MS * 4);
        String line = LINES[ThreadLocalRandom.current().nextInt(LINES.length)];
        player.sendMessage(Messages.mini("<gradient:#FFE9A0:#E0B864><bold>Elxssir</bold></gradient> <dark_gray>»</dark_gray> <white>" + line));
        World w = player.getWorld();
        Location mouth = new Location(w, s.x, s.y + 1.6, s.z);
        w.playSound(mouth, Sound.ENTITY_SKELETON_AMBIENT, 0.7f, 1.45f);
        s.talk = TALK_TICKS;
        s.bubbleTicks = 70;
        Component text = Messages.mini("<white>" + line);
        if (s.bubble != null && s.bubble.isValid()) {
            s.bubble.text(text);
            return;
        }
        float tall = height * SCALE / 16f;
        s.bubble = w.spawn(new Location(w, s.x, s.y + tall + 0.62, s.z), TextDisplay.class, t -> {
            t.setPersistent(false);
            t.text(text);
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(190, 8, 12, 28));
            t.setLineWidth(180);
            t.addScoreboardTag(TAG);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Spot s = spotOf(event.getRightClicked());
        if (s == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            ask(s, event.getPlayer(), true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Spot s = spotOf(event.getAttacked());
        if (s != null) {
            event.setCancelled(true);
            ask(s, event.getPlayer(), true);
        }
    }

    // ------------------------------------------------------------------ petits calculs

    private static float rad(float deg) {
        return (float) Math.toRadians(deg);
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    /** Ramène un angle entre -180 et 180. */
    private static float wrap(float deg) {
        deg %= 360f;
        return deg >= 180f ? deg - 360f : deg < -180f ? deg + 360f : deg;
    }

    /** Passage doux de 0 (x = a) à 1 (x = b), dans un sens comme dans l'autre. */
    private static float smooth(float a, float b, float x) {
        float k = clamp((x - a) / (b - a), 0, 1);
        return k * k * (3 - 2 * k);
    }
}
